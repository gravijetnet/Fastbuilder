package net.gravijet.fastbuilder.replay;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.gameplay.RunSession;
import net.gravijet.fastbuilder.player.PlayerData;
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
 *
 * Replay limits are permission-based:
 *   fastbuilder.replays.unlimited  → no limit
 *   fastbuilder.replays.N          → N replays per map (checked 100 → 1)
 *   default                        → MAX_REPLAYS_PER_MAP
 *
 * The player's personal best and any favorited replays are always protected.
 */
public class ReplayManager {

    private final FastBuilder plugin;
    private final File replaysDir;

    private final Map<UUID, ReplayRecorder> activeRecorders = new HashMap<>();
    private final Map<UUID, ReplaySession>  activeSessions  = new HashMap<>();

    private int recordingTaskId = -1;

    private static final int MAX_REPLAYS_PER_MAP = 20;

    private static final int MAGIC   = 0x46425250; // "FBRP"
    private static final int VERSION = 1;

    public ReplayManager(FastBuilder plugin) {
        this.plugin = plugin;
        this.replaysDir = new File(plugin.getDataFolder(), "replays");
        if (!replaysDir.exists()) replaysDir.mkdirs();
        startRecordingTask();
    }

    // -------------------------------------------------------------------------
    // Recording
    // -------------------------------------------------------------------------

    public void startRecording(Player player, String mapName, int islandIndex) {
        stopRecording(player.getUniqueId(), false);
        activeRecorders.put(player.getUniqueId(),
                new ReplayRecorder(player.getUniqueId(), player.getName(), mapName, islandIndex));
    }

    public void recordBlockPlace(UUID playerUuid, Location loc, int blockId, byte blockData) {
        ReplayRecorder recorder = activeRecorders.get(playerUuid);
        if (recorder != null) recorder.recordBlockPlace(loc, blockId, blockData);
    }

    public void stopRecording(UUID playerUuid, boolean successful) {
        ReplayRecorder recorder = activeRecorders.remove(playerUuid);
        if (recorder == null) return;

        RunSession run = plugin.getGameplayManager().getSession(playerUuid);
        long runTime = run != null ? run.getElapsed() : 0;
        ReplayData data = recorder.build(successful, runTime);

        // Gather permission-based limit and favorites on the main thread before going async
        int limit = getReplayLimit(playerUuid);
        PlayerData pData = plugin.getPlayerManager().getCachedData(playerUuid);
        Set<String> favorites = pData != null
                ? new HashSet<>(pData.getFavoriteReplays())
                : Collections.<String>emptySet();

        Bukkit.getScheduler().runTaskAsynchronously(plugin,
                () -> saveReplay(data, limit, favorites));
    }

    private int getReplayLimit(UUID playerUuid) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null) return MAX_REPLAYS_PER_MAP;

        if (player.hasPermission("fastbuilder.replays.unlimited")) return Integer.MAX_VALUE;

        // Check fastbuilder.replays.N, highest wins
        for (int i = 100; i >= 1; i--) {
            if (player.hasPermission("fastbuilder.replays." + i)) return i;
        }
        return MAX_REPLAYS_PER_MAP;
    }

    // -------------------------------------------------------------------------
    // Playback
    // -------------------------------------------------------------------------

    public void startPlayback(Player viewer, ReplayData replayData) {
        stopPlayback(viewer.getUniqueId());
        plugin.getGameplayManager().removeSession(viewer.getUniqueId());

        ReplaySession session = new ReplaySession(plugin, viewer.getUniqueId(), replayData);
        activeSessions.put(viewer.getUniqueId(), session);
        session.start();
    }

    public void stopPlayback(UUID viewerUuid) {
        ReplaySession session = activeSessions.remove(viewerUuid);
        if (session == null) return;

        session.stop();

        // Re-create gameplay session so the player can continue building
        Player player = Bukkit.getPlayer(viewerUuid);
        if (player != null && player.isOnline()) {
            PlayerData data = plugin.getPlayerManager().getCachedData(viewerUuid);
            if (data != null && data.getLastMap() != null) {
                String mapName = data.getLastMap();
                int island = data.getLastIsland();
                net.gravijet.fastbuilder.map.MapData mapData = plugin.getMapManager().getMap(mapName);
                if (mapData != null) {
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

    public ReplaySession getPlaybackSession(UUID viewerUuid) { return activeSessions.get(viewerUuid); }
    public boolean isInPlayback(UUID uuid) { return activeSessions.containsKey(uuid); }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

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
                if (data != null) replays.add(data);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load replay: " + file.getName());
            }
        }

        replays.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        return replays;
    }

    // -------------------------------------------------------------------------
    // Periodic recording task
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // File I/O
    // -------------------------------------------------------------------------

    private void saveReplay(ReplayData data, int limit, Set<String> favorites) {
        File playerDir = new File(replaysDir, data.getPlayerUuid().toString());
        if (!playerDir.exists()) playerDir.mkdirs();

        File file = new File(playerDir, data.getFileName());
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(file)))) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeUTF(data.getPlayerUuid().toString());
            out.writeUTF(data.getPlayerName());
            out.writeUTF(data.getMapName());
            out.writeInt(data.getIslandIndex());
            out.writeLong(data.getTimestamp());
            out.writeBoolean(data.isSuccessful());
            out.writeLong(data.getRunTimeMillis());

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

        // Cleanup after saving
        cleanupOldReplays(playerDir, data.getMapName(), limit, favorites);
    }

    private ReplayData loadReplay(File file) throws IOException {
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new FileInputStream(file)))) {
            int magic = in.readInt();
            if (magic != MAGIC) throw new IOException("Invalid replay file magic");

            int version = in.readInt();
            if (version != VERSION) throw new IOException("Unsupported replay version: " + version);

            UUID uuid        = UUID.fromString(in.readUTF());
            String name      = in.readUTF();
            String mapName   = in.readUTF();
            int islandIndex  = in.readInt();
            long timestamp   = in.readLong();
            boolean success  = in.readBoolean();
            long runTime     = in.readLong();

            int frameCount = in.readInt();
            List<ReplayFrame> frames = new ArrayList<>(frameCount);

            for (int i = 0; i < frameCount; i++) {
                int tick    = in.readInt();
                double x    = in.readDouble();
                double y    = in.readDouble();
                double z    = in.readDouble();
                float yaw   = in.readFloat();
                float pitch = in.readFloat();
                boolean hasBlock = in.readBoolean();

                ReplayFrame.BlockPlacement placement = null;
                if (hasBlock) {
                    int bx       = in.readInt();
                    int by       = in.readInt();
                    int bz       = in.readInt();
                    int blockId  = in.readInt();
                    byte blockData = in.readByte();
                    placement = new ReplayFrame.BlockPlacement(bx, by, bz, blockId, blockData);
                }

                frames.add(new ReplayFrame(tick, x, y, z, yaw, pitch, placement));
            }

            return new ReplayData(uuid, name, mapName, islandIndex, timestamp, success, runTime, frames);
        }
    }

    /**
     * Remove oldest replays beyond the player's limit.
     * Always protects:
     *  - The replay with the lowest run time (personal best)
     *  - Any replays whose file names are in the favorites set
     */
    private void cleanupOldReplays(File playerDir, String mapName, int limit, Set<String> favorites) {
        if (limit == Integer.MAX_VALUE) return;

        File[] files = playerDir.listFiles((dir, name) ->
                name.contains("_" + mapName.toLowerCase() + "_") && name.endsWith(".replay"));
        if (files == null || files.length <= limit) return;

        // Load metadata to find the PB file
        File pbFile = null;
        long pbTime = Long.MAX_VALUE;

        List<File> deleteCandidates = new ArrayList<>();
        for (File f : files) {
            try {
                ReplayData rd = loadReplay(f);
                if (rd == null) continue;
                if (rd.isSuccessful() && rd.getRunTimeMillis() > 0 && rd.getRunTimeMillis() < pbTime) {
                    pbTime = rd.getRunTimeMillis();
                    pbFile = f;
                }
                deleteCandidates.add(f);
            } catch (Exception ignored) {}
        }

        // Sort candidates oldest-first (by last modified)
        deleteCandidates.sort(Comparator.comparingLong(File::lastModified));

        int toDelete = files.length - limit;
        for (File f : deleteCandidates) {
            if (toDelete <= 0) break;
            // Protect PB
            if (f.equals(pbFile)) continue;
            // Protect favorites
            if (favorites.contains(f.getName())) continue;
            f.delete();
            toDelete--;
        }
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    public static String formatTimestamp(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm");
        return sdf.format(new Date(timestamp));
    }

    public void shutdown() {
        if (recordingTaskId != -1) Bukkit.getScheduler().cancelTask(recordingTaskId);
        for (UUID uuid : new ArrayList<>(activeSessions.keySet())) stopPlayback(uuid);
        activeRecorders.clear();
    }
}
