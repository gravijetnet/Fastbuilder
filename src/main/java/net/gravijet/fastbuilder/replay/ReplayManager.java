package net.gravijet.fastbuilder.replay;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.gameplay.RunSession;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Manages replay recording, storage, and playback.
 * Replays are stored as binary files in plugins/FastBuilder/replays/<uuid>/
 */
public class ReplayManager {

    private final FastBuilder plugin;
    private final File replaysDir;

    // Active recorders: playerUUID -> recorder
    private final Map<UUID, ReplayRecorder> activeRecorders = new HashMap<>();

    // Active playback sessions: viewerUUID -> session
    private final Map<UUID, ReplaySession> activeSessions = new HashMap<>();

    // Recording task id
    private int recordingTaskId = -1;

    // Max replays per player per map
    private static final int MAX_REPLAYS_PER_MAP = 20;

    // Binary format magic number
    private static final int MAGIC = 0x46425250; // "FBRP"
    private static final int VERSION = 1;

    public ReplayManager(FastBuilder plugin) {
        this.plugin = plugin;
        this.replaysDir = new File(plugin.getDataFolder(), "replays");
        if (!replaysDir.exists()) {
            replaysDir.mkdirs();
        }
        startRecordingTask();
    }

    /**
     * Start recording a player's run.
     */
    public void startRecording(Player player, String mapName, int islandIndex) {
        // Stop any existing recording
        stopRecording(player.getUniqueId(), false);

        ReplayRecorder recorder = new ReplayRecorder(
                player.getUniqueId(), player.getName(), mapName, islandIndex);
        activeRecorders.put(player.getUniqueId(), recorder);
    }

    /**
     * Record a block placement for a player.
     */
    public void recordBlockPlace(UUID playerUuid, Location loc, int blockId, byte blockData) {
        ReplayRecorder recorder = activeRecorders.get(playerUuid);
        if (recorder != null) {
            recorder.recordBlockPlace(loc, blockId, blockData);
        }
    }

    /**
     * Stop recording and save the replay.
     */
    public void stopRecording(UUID playerUuid, boolean successful) {
        ReplayRecorder recorder = activeRecorders.remove(playerUuid);
        if (recorder == null) return;

        RunSession run = plugin.getGameplayManager().getSession(playerUuid);
        long runTime = run != null ? run.getElapsed() : 0;

        ReplayData data = recorder.build(successful, runTime);

        // Save async
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> saveReplay(data));
    }

    /**
     * Start a replay playback for a viewer.
     */
    public void startPlayback(Player viewer, ReplayData replayData) {
        // Stop any existing playback
        stopPlayback(viewer.getUniqueId());

        // Remove the player's gameplay session temporarily
        plugin.getGameplayManager().removeSession(viewer.getUniqueId());

        ReplaySession session = new ReplaySession(plugin, viewer.getUniqueId(), replayData);
        activeSessions.put(viewer.getUniqueId(), session);
        session.start();
    }

    /**
     * Stop a replay playback.
     */
    public void stopPlayback(UUID viewerUuid) {
        ReplaySession session = activeSessions.remove(viewerUuid);
        if (session != null) {
            session.stop();

            // Re-create gameplay session
            Player player = Bukkit.getPlayer(viewerUuid);
            if (player != null && player.isOnline()) {
                net.gravijet.fastbuilder.player.PlayerData data =
                        plugin.getPlayerManager().getCachedData(viewerUuid);
                if (data != null && data.getLastMap() != null) {
                    String mapName = data.getLastMap();
                    int island = data.getLastIsland();
                    net.gravijet.fastbuilder.map.MapData mapData = plugin.getMapManager().getMap(mapName);
                    if (mapData != null) {
                        // Re-assign island if not already assigned
                        if (plugin.getMapManager().getPlayerIsland(mapName, viewerUuid) < 0) {
                            int newIsland = plugin.getMapManager().assignFreeIsland(
                                    mapName, viewerUuid, player.getName());
                            if (newIsland >= 0) {
                                island = newIsland;
                                data.setLastIsland(island);
                            }
                        }
                        plugin.getGameplayManager().createSession(viewerUuid, mapName, island);
                    }
                }
            }
        }
    }

    public ReplaySession getPlaybackSession(UUID viewerUuid) {
        return activeSessions.get(viewerUuid);
    }

    public boolean isInPlayback(UUID uuid) {
        return activeSessions.containsKey(uuid);
    }

    /**
     * Get all replays for a player on a specific map.
     */
    public List<ReplayData> getPlayerReplays(UUID playerUuid, String mapName) {
        List<ReplayData> replays = new ArrayList<>();
        File playerDir = new File(replaysDir, playerUuid.toString());
        if (!playerDir.exists()) return replays;

        File[] files = playerDir.listFiles((dir, name) ->
                name.contains("_" + mapName.toLowerCase() + "_") && name.endsWith(".replay"));
        if (files == null) return replays;

        for (File file : files) {
            try {
                ReplayData data = loadReplay(file);
                if (data != null) {
                    replays.add(data);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load replay: " + file.getName());
            }
        }

        // Sort by timestamp descending (newest first)
        replays.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        return replays;
    }

    // --- Periodic Recording ---

    private void startRecordingTask() {
        recordingTaskId = new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<UUID, ReplayRecorder> entry : new HashMap<>(activeRecorders).entrySet()) {
                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player == null || !player.isOnline()) {
                        activeRecorders.remove(entry.getKey());
                        continue;
                    }
                    entry.getValue().recordTick(player);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L).getTaskId();
    }

    // --- File I/O ---

    /**
     * Save a replay to disk in binary format.
     */
    private void saveReplay(ReplayData data) {
        File playerDir = new File(replaysDir, data.getPlayerUuid().toString());
        if (!playerDir.exists()) playerDir.mkdirs();

        // Clean up old replays if too many
        cleanupOldReplays(playerDir, data.getMapName());

        File file = new File(playerDir, data.getFileName());
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            // Header
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeUTF(data.getPlayerUuid().toString());
            out.writeUTF(data.getPlayerName());
            out.writeUTF(data.getMapName());
            out.writeInt(data.getIslandIndex());
            out.writeLong(data.getTimestamp());
            out.writeBoolean(data.isSuccessful());
            out.writeLong(data.getRunTimeMillis());

            // Frames
            out.writeInt(data.getFrames().size());
            for (ReplayFrame frame : data.getFrames()) {
                out.writeInt(frame.getTick());
                out.writeDouble(frame.getX());
                out.writeDouble(frame.getY());
                out.writeDouble(frame.getZ());
                out.writeFloat(frame.getYaw());
                out.writeFloat(frame.getPitch());
                out.writeBoolean(frame.hasBlockPlacement());

                if (frame.hasBlockPlacement()) {
                    ReplayFrame.BlockPlacement bp = frame.getBlockPlacement();
                    out.writeInt(bp.getBlockX());
                    out.writeInt(bp.getBlockY());
                    out.writeInt(bp.getBlockZ());
                    out.writeInt(bp.getBlockId());
                    out.writeByte(bp.getBlockData());
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save replay: " + e.getMessage());
        }
    }

    /**
     * Load a replay from disk.
     */
    private ReplayData loadReplay(File file) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            int magic = in.readInt();
            if (magic != MAGIC) throw new IOException("Invalid replay file magic");

            int version = in.readInt();
            if (version != VERSION) throw new IOException("Unsupported replay version: " + version);

            UUID uuid = UUID.fromString(in.readUTF());
            String name = in.readUTF();
            String mapName = in.readUTF();
            int islandIndex = in.readInt();
            long timestamp = in.readLong();
            boolean successful = in.readBoolean();
            long runTime = in.readLong();

            int frameCount = in.readInt();
            List<ReplayFrame> frames = new ArrayList<>(frameCount);

            for (int i = 0; i < frameCount; i++) {
                int tick = in.readInt();
                double x = in.readDouble();
                double y = in.readDouble();
                double z = in.readDouble();
                float yaw = in.readFloat();
                float pitch = in.readFloat();
                boolean hasBlock = in.readBoolean();

                ReplayFrame.BlockPlacement placement = null;
                if (hasBlock) {
                    int bx = in.readInt();
                    int by = in.readInt();
                    int bz = in.readInt();
                    int blockId = in.readInt();
                    byte blockData = in.readByte();
                    placement = new ReplayFrame.BlockPlacement(bx, by, bz, blockId, blockData);
                }

                frames.add(new ReplayFrame(tick, x, y, z, yaw, pitch, placement));
            }

            return new ReplayData(uuid, name, mapName, islandIndex, timestamp, successful, runTime, frames);
        }
    }

    /**
     * Remove oldest replays if player has too many for a map.
     */
    private void cleanupOldReplays(File playerDir, String mapName) {
        File[] files = playerDir.listFiles((dir, name) ->
                name.contains("_" + mapName.toLowerCase() + "_") && name.endsWith(".replay"));
        if (files == null || files.length < MAX_REPLAYS_PER_MAP) return;

        // Sort by last modified (oldest first)
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));

        // Delete oldest until under limit
        int toDelete = files.length - MAX_REPLAYS_PER_MAP + 1;
        for (int i = 0; i < toDelete; i++) {
            files[i].delete();
        }
    }

    /**
     * Format a timestamp for display.
     */
    public static String formatTimestamp(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm");
        return sdf.format(new Date(timestamp));
    }

    public void shutdown() {
        if (recordingTaskId != -1) {
            Bukkit.getScheduler().cancelTask(recordingTaskId);
        }
        // Stop all active playbacks
        for (UUID uuid : new ArrayList<>(activeSessions.keySet())) {
            stopPlayback(uuid);
        }
        activeRecorders.clear();
    }
}
