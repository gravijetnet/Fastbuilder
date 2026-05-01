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
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

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
    // Tracks which slot indices are currently in use
    private final java.util.Set<Integer> usedReplaySlots = new java.util.HashSet<>();

    private int recordingTaskId = -1;

    // Default fallback; actual value read from config at runtime
    private static final int MAX_REPLAYS_PER_MAP = 20;

    private static final int MAGIC   = 0x46425250; // "FBRP"
    private static final int VERSION = 6; // v6 adds customLength

    public ReplayManager(FastBuilder plugin) {
        this.plugin = plugin;
        this.replaysDir = new File(plugin.getDataFolder(), "replays");
        if (!replaysDir.exists()) replaysDir.mkdirs();
        startRecordingTask();
    }

    // -------------------------------------------------------------------------
    // Recording
    // -------------------------------------------------------------------------

    /**
     * Returns the name to display on the replay NPC based on the configured player-name-mode.
     * "recorded" = name as stored in the replay; "current" = current online name (if available).
     */
    public String getReplayDisplayName(ReplayData data) {
        String mode = plugin.getConfigManager().getReplayPlayerNameMode();
        if ("current".equalsIgnoreCase(mode)) {
            // Try to resolve the current name from the online player list or Bukkit offline player
            org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(data.getPlayerUuid());
            if (op.getName() != null && !op.getName().isEmpty()) {
                return op.getName();
            }
        }
        return data.getPlayerName();
    }

    @SuppressWarnings("deprecation")
    public void startRecording(Player player, String mapName, int islandIndex) {
        stopRecording(player.getUniqueId(), false);

        // Capture the EXACT world state of the island — scan the full island bounds so that
        // admin-placed blocks (via /build), practice blocks, and schematic blocks are all included.
        // This is the only reliable way to capture build-mode changes which bypass session tracking.
        List<ReplayFrame.BlockPlacement> initialBlocks = new ArrayList<>();
        net.gravijet.fastbuilder.map.MapData mapData = plugin.getMapManager().getMap(mapName);
        if (mapData != null && mapData.getWorld() != null) {
            Location islandMin = mapData.getIslandMin(islandIndex);
            Location islandMax = mapData.getIslandMax(islandIndex);
            org.bukkit.World world = mapData.getWorld();
            for (int x = islandMin.getBlockX(); x <= islandMax.getBlockX(); x++) {
                for (int y = islandMin.getBlockY(); y <= islandMax.getBlockY(); y++) {
                    for (int z = islandMin.getBlockZ(); z <= islandMax.getBlockZ(); z++) {
                        org.bukkit.block.Block b = world.getBlockAt(x, y, z);
                        if (b.getType() != org.bukkit.Material.AIR) {
                            initialBlocks.add(new ReplayFrame.BlockPlacement(
                                    x, y, z, b.getTypeId(), b.getData()));
                        }
                    }
                }
            }
        }

        // Capture the player's display tag (rank prefix + name) at the moment of recording.
        // player.getDisplayName() is set by rank plugins (LuckPerms, GroupManager, etc.) and
        // contains §-codes already — Citizens accepts §-codes directly for NPC names.
        String displayTag = player.getDisplayName();

        activeRecorders.put(player.getUniqueId(),
                new ReplayRecorder(player.getUniqueId(), player.getName(), displayTag, mapName, islandIndex, initialBlocks));
    }

    public void recordBlockPlace(UUID playerUuid, Location loc, int blockId, byte blockData) {
        ReplayRecorder recorder = activeRecorders.get(playerUuid);
        if (recorder != null) recorder.recordBlockPlace(loc, blockId, blockData);
    }

    public void recordArmSwing(UUID playerUuid) {
        ReplayRecorder recorder = activeRecorders.get(playerUuid);
        if (recorder != null) recorder.recordArmSwing();
    }

    public void stopRecording(UUID playerUuid, boolean successful) {
        ReplayRecorder recorder = activeRecorders.remove(playerUuid);
        if (recorder == null) return;

        RunSession run = plugin.getGameplayManager().getSession(playerUuid);
        long runTime = run != null ? run.getElapsed() : 0;

        // Capture the active custom length for this run (0 = normal mode)
        int customLength = 0;
        net.gravijet.fastbuilder.player.PlayerData pDataCL =
                plugin.getPlayerManager().getCachedData(playerUuid);
        if (pDataCL != null && run != null) {
            net.gravijet.fastbuilder.map.MapData mapCL =
                    plugin.getMapManager().getMap(run.getMapName());
            if (mapCL != null && mapCL.hasCustomLength()) {
                customLength = pDataCL.getCustomLength(run.getMapName());
                if (customLength <= 0) customLength = mapCL.getBaseCustomLength();
            }
        }

        ReplayData data = recorder.build(successful, runTime, customLength);

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
        int configDefault = plugin.getConfigManager().getMaxReplaysPerMap();
        if (player == null) return configDefault;

        if (player.hasPermission("fastbuilder.replays.unlimited")) return Integer.MAX_VALUE;

        // Check fastbuilder.replays.N (highest permission wins, checked 1000 → 1)
        for (int i = 1000; i >= 1; i--) {
            if (player.hasPermission("fastbuilder.replays." + i)) return i;
        }
        return configDefault;
    }

    // -------------------------------------------------------------------------
    // Playback
    // -------------------------------------------------------------------------

    public void startPlayback(Player viewer, ReplayData replayData) {
        stopPlayback(viewer.getUniqueId());

        // Clear any placed blocks on the player's current island before entering replay
        if (plugin.getGameplayManager() != null) {
            plugin.getGameplayManager().clearAllPlacedBlocks(viewer.getUniqueId());
        }
        plugin.getGameplayManager().removeSession(viewer.getUniqueId());

        // Allocate a free replay slot (1000-block spaced areas)
        int slot = 0;
        while (usedReplaySlots.contains(slot)) slot++;
        usedReplaySlots.add(slot);

        ReplaySession session = new ReplaySession(plugin, viewer.getUniqueId(), replayData, slot);
        activeSessions.put(viewer.getUniqueId(), session);
        session.start();
    }

    public void stopPlayback(UUID viewerUuid) {
        ReplaySession session = activeSessions.remove(viewerUuid);
        if (session == null) return;

        // Free the slot so other viewers can reuse it
        usedReplaySlots.remove(session.getReplaySlot());

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

    /**
     * Load and return the personal best replay for a given player + map.
     * Returns null if no successful replay exists on disk.
     * This is a blocking I/O call — invoke from an async thread.
     */
    public ReplayData getPbReplay(UUID playerUuid, String mapName) {
        File playerDir = new File(replaysDir, playerUuid.toString());
        if (!playerDir.exists()) return null;
        File[] files = playerDir.listFiles((dir, name) ->
                name.contains("_" + mapName.toLowerCase() + "_") && name.endsWith(".replay"));
        if (files == null || files.length == 0) return null;

        ReplayData best = null;
        long bestTime = Long.MAX_VALUE;
        for (File f : files) {
            try {
                ReplayData rd = loadReplay(f);
                if (rd == null || !rd.isSuccessful() || rd.getRunTimeMillis() <= 0) continue;
                if (rd.getRunTimeMillis() < bestTime) {
                    bestTime = rd.getRunTimeMillis();
                    best = rd;
                }
            } catch (Exception ignored) {}
        }
        return best;
    }

    /** Find a replay session by its NPC entity ID (used to route NPC right-click events). */
    public ReplaySession getPlaybackSessionByNpcId(int npcId) {
        for (ReplaySession session : activeSessions.values()) {
            if (session.getNpcId() == npcId) return session;
        }
        return null;
    }

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
        // Wrap with GZIP for significant space savings on replay binary data
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(file))))) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeUTF(data.getPlayerUuid().toString());
            out.writeUTF(data.getPlayerName());
            out.writeUTF(data.getPlayerDisplayTag()); // v5: rank-prefixed display tag
            out.writeUTF(data.getMapName());
            out.writeInt(data.getIslandIndex());
            out.writeLong(data.getTimestamp());
            out.writeBoolean(data.isSuccessful());
            out.writeLong(data.getRunTimeMillis());
            out.writeInt(data.getCustomLength()); // v6

            // Initial blocks (v2)
            out.writeInt(data.getInitialBlocks().size());
            for (ReplayFrame.BlockPlacement bp : data.getInitialBlocks()) {
                out.writeInt(bp.getBlockX());
                out.writeInt(bp.getBlockY());
                out.writeInt(bp.getBlockZ());
                out.writeInt(bp.getBlockId());
                out.writeByte(bp.getBlockData());
            }

            out.writeInt(data.getFrames().size());
            for (ReplayFrame frame : data.getFrames()) {
                out.writeInt(frame.getTick());
                out.writeDouble(frame.getX());
                out.writeDouble(frame.getY());
                out.writeDouble(frame.getZ());
                out.writeFloat(frame.getYaw());
                out.writeFloat(frame.getPitch());
                // v3 additions
                out.writeFloat(frame.getHeadYaw());
                out.writeBoolean(frame.isSneaking());
                out.writeBoolean(frame.isSprinting());
                out.writeBoolean(frame.isSwingingArm());
                // v4 additions
                out.writeInt(frame.getHandItemId());
                out.writeByte(frame.getHandItemData());

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
        // Detect GZIP vs. raw: check first two bytes for the GZIP magic (0x1f 0x8b)
        InputStream rawStream = new BufferedInputStream(new FileInputStream(file));
        rawStream.mark(2);
        int b1 = rawStream.read(), b2 = rawStream.read();
        rawStream.reset();
        InputStream decompressed;
        if (b1 == 0x1f && b2 == 0x8b) {
            decompressed = new GZIPInputStream(rawStream);
        } else {
            decompressed = rawStream; // legacy uncompressed format
        }
        try (DataInputStream in = new DataInputStream(decompressed)) {
            int magic = in.readInt();
            if (magic != MAGIC) throw new IOException("Invalid replay file magic");

            int version = in.readInt();
            if (version < 1 || version > VERSION) throw new IOException("Unsupported replay version: " + version); // currently supports v1–v6


            UUID uuid        = UUID.fromString(in.readUTF());
            String name      = in.readUTF();
            String displayTag = version >= 5 ? in.readUTF() : ""; // v5: rank-prefixed display tag
            String mapName   = in.readUTF();
            int islandIndex  = in.readInt();
            long timestamp   = in.readLong();
            boolean success  = in.readBoolean();
            long runTime     = in.readLong();
            int customLength = version >= 6 ? in.readInt() : 0; // v6

            // Read initial blocks (v2 only; v1 files have none)
            List<ReplayFrame.BlockPlacement> initialBlocks = new ArrayList<>();
            if (version >= 2) {
                int initCount = in.readInt();
                for (int i = 0; i < initCount; i++) {
                    int bx       = in.readInt();
                    int by       = in.readInt();
                    int bz       = in.readInt();
                    int blockId  = in.readInt();
                    byte blockData = in.readByte();
                    initialBlocks.add(new ReplayFrame.BlockPlacement(bx, by, bz, blockId, blockData));
                }
            }

            int frameCount = in.readInt();
            List<ReplayFrame> frames = new ArrayList<>(frameCount);

            for (int i = 0; i < frameCount; i++) {
                int tick    = in.readInt();
                double x    = in.readDouble();
                double y    = in.readDouble();
                double z    = in.readDouble();
                float yaw   = in.readFloat();
                float pitch = in.readFloat();

                // v3: extended state fields
                float headYaw    = yaw;
                boolean sneaking = false;
                boolean sprinting = false;
                boolean swingArm = false;
                if (version >= 3) {
                    headYaw   = in.readFloat();
                    sneaking  = in.readBoolean();
                    sprinting = in.readBoolean();
                    swingArm  = in.readBoolean();
                }

                // v4: hand item
                int handItemId   = 0;
                byte handItemData = 0;
                if (version >= 4) {
                    handItemId   = in.readInt();
                    handItemData = in.readByte();
                }

                boolean hasBlock = in.readBoolean();
                ReplayFrame.BlockPlacement placement = null;
                if (hasBlock) {
                    int bx         = in.readInt();
                    int by         = in.readInt();
                    int bz         = in.readInt();
                    int blockId    = in.readInt();
                    byte blockData = in.readByte();
                    placement = new ReplayFrame.BlockPlacement(bx, by, bz, blockId, blockData);
                }

                frames.add(new ReplayFrame(tick, x, y, z, yaw, pitch, headYaw,
                        sneaking, sprinting, swingArm, handItemId, handItemData, placement));
            }

            return new ReplayData(uuid, name, displayTag, mapName, islandIndex, timestamp, success, runTime, frames, initialBlocks, customLength);
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
