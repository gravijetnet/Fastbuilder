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

    private final Map<UUID, ReplayRecorder> activeRecorders = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, ReplaySession>  activeSessions  = new java.util.concurrent.ConcurrentHashMap<>();
    // Tracks which slot indices are currently in use
    private final java.util.Set<Integer> usedReplaySlots = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    private int recordingTaskId = -1;

    // Default fallback; actual value read from config at runtime
    private static final int MAX_REPLAYS_PER_MAP = 20;

    private static final int MAGIC   = 0x46425250; // "FBRP"
    private static final int VERSION = 8; // v8 adds practice flag

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

        // Capture skin texture at recording time for cracked-server-safe replay skins.
        String skinValue = "";
        String skinSignature = "";
        if (plugin.getSkinManager() != null) {
            String sv = plugin.getSkinManager().getSkinValue(player.getUniqueId());
            String ss = plugin.getSkinManager().getSkinSignature(player.getUniqueId());
            if (sv != null) { skinValue = sv; skinSignature = ss != null ? ss : ""; }
        }

        activeRecorders.put(player.getUniqueId(),
                new ReplayRecorder(player.getUniqueId(), player.getName(), displayTag,
                        mapName, islandIndex, initialBlocks, skinValue, skinSignature));
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

        RunSession run = plugin.getGameplayManager() != null
                ? plugin.getGameplayManager().getSession(playerUuid) : null;
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

        // Mark practice runs so they don't count toward the personal best.
        // practiceUsedThisRun() is sticky — covers the case where the player toggles
        // practice off mid-run (which clears practiceBlocks but leaves the flag set).
        boolean practice = run != null && (run.isPracticeMode()
                || run.hasPracticeBlocks()
                || run.practiceUsedThisRun());

        ReplayData data = recorder.build(successful, runTime, customLength, practice);

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

        // Check fastbuilder.replays.N (highest value wins); scan all values 1–1000.
        int best = configDefault;
        for (int v = 1; v <= 1000; v++) {
            if (player.hasPermission("fastbuilder.replays." + v)) best = v;
        }
        return best;
    }

    // -------------------------------------------------------------------------
    // Playback
    // -------------------------------------------------------------------------

    public void startPlayback(Player viewer, ReplayData replayData) {
        stopPlayback(viewer.getUniqueId());

        // Clear any placed blocks on the player's current island before entering replay
        if (plugin.getGameplayManager() != null) {
            plugin.getGameplayManager().clearAllPlacedBlocks(viewer.getUniqueId());
            plugin.getGameplayManager().removeSession(viewer.getUniqueId());
        }

        // Allocate a free replay slot (1000-block spaced areas)
        int slot = 0;
        while (usedReplaySlots.contains(slot)) {
            if (slot == Integer.MAX_VALUE) {
                plugin.getLogger().severe("ReplayManager: all replay slots exhausted, cannot start playback.");
                return;
            }
            slot++;
        }
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
            String lastMap = data != null ? data.getLastMap() : null;
            if (lastMap == null || lastMap.isEmpty()) {
                lastMap = plugin.getConfigManager().getDefaultMap();
            }
            if (data != null && lastMap != null && !lastMap.isEmpty()) {
                final String mapName = lastMap;
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
                    if (plugin.getGameplayManager() != null) {
                        plugin.getGameplayManager().createSession(viewerUuid, mapName, island);
                    }
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
                name.toLowerCase().contains("_" + ReplayData.fileKey(mapName) + "_") && name.endsWith(".replay"));
        if (files == null || files.length == 0) return null;

        ReplayData best = null;
        long bestTime = Long.MAX_VALUE;
        for (File f : files) {
            try {
                ReplayData rd = loadReplay(f);
                if (rd == null || !rd.isSuccessful() || rd.getRunTimeMillis() <= 0) continue;
                if (rd.isPractice()) continue; // practice runs never count for personal best
                if (rd.getRunTimeMillis() < bestTime) {
                    bestTime = rd.getRunTimeMillis();
                    best = rd;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load replay for PB check: " + f.getName() + " — " + e.getMessage());
            }
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
                name.toLowerCase().contains("_" + ReplayData.fileKey(mapName) + "_") && name.endsWith(".replay"));
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
            out.writeInt(data.getCustomLength());   // v6
            out.writeUTF(data.getSkinValue());      // v7
            out.writeUTF(data.getSkinSignature());  // v7
            out.writeBoolean(data.isPractice());    // v8

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
        // rawStream is owned by DataInputStream (or GZIPInputStream) and closed via try-with-resources.
        BufferedInputStream rawStream = new BufferedInputStream(new FileInputStream(file));
        InputStream decompressed;
        try {
            rawStream.mark(2);
            int b1 = rawStream.read(), b2 = rawStream.read();
            rawStream.reset();
            if (b1 == 0x1f && b2 == 0x8b) {
                decompressed = new GZIPInputStream(rawStream);
            } else {
                decompressed = rawStream; // legacy uncompressed format
            }
        } catch (IOException e) {
            rawStream.close();
            throw e;
        }
        try (DataInputStream in = new DataInputStream(decompressed)) {
            int magic = in.readInt();
            if (magic != MAGIC) throw new IOException("Invalid replay file magic");

            int version = in.readInt();
            if (version < 1 || version > VERSION) throw new IOException("Unsupported replay version: " + version); // currently supports v1–v7

            UUID uuid        = UUID.fromString(in.readUTF());
            String name      = in.readUTF();
            String displayTag  = version >= 5 ? in.readUTF() : "";
            String mapName   = in.readUTF();
            int islandIndex  = in.readInt();
            long timestamp   = in.readLong();
            boolean success  = in.readBoolean();
            long runTime     = in.readLong();
            int customLength   = version >= 6 ? in.readInt() : 0;
            String skinValue   = version >= 7 ? in.readUTF() : "";
            String skinSig     = version >= 7 ? in.readUTF() : "";
            boolean practice   = version >= 8 && in.readBoolean();

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

            return new ReplayData(uuid, name, displayTag, mapName, islandIndex, timestamp, success, runTime, frames, initialBlocks, customLength, skinValue, skinSig, practice);
        }
    }

    /**
     * Read only the header fields needed for cleanup: success flag, run time, and practice flag.
     * Returns {successful (0/1), runTimeMillis, practice (0/1)} or null on any read error.
     * Much cheaper than a full loadReplay() since no frame data is parsed.
     */
    private long[] readReplayHeader(File f) {
        try (FileInputStream raw = new FileInputStream(f)) {
            int b1 = raw.read(), b2 = raw.read();
            if (b1 < 0 || b2 < 0) return null;
            InputStream decompressed;
            if (b1 == 0x1f && b2 == 0x8b) {
                byte[] prefix = new byte[]{(byte) b1, (byte) b2};
                InputStream combined = new java.io.SequenceInputStream(
                        new java.io.ByteArrayInputStream(prefix), raw);
                decompressed = new GZIPInputStream(combined);
            } else {
                byte[] prefix = new byte[]{(byte) b1, (byte) b2};
                decompressed = new java.io.SequenceInputStream(
                        new java.io.ByteArrayInputStream(prefix), raw);
            }
            try (DataInputStream in = new DataInputStream(decompressed)) {
                if (in.readInt() != MAGIC) return null;
                int version = in.readInt();
                if (version < 1 || version > VERSION) return null;
                in.readUTF(); // uuid
                in.readUTF(); // name
                if (version >= 5) in.readUTF(); // displayTag
                in.readUTF(); // mapName
                in.readInt(); // islandIndex
                in.readLong(); // timestamp
                boolean success = in.readBoolean();
                long runTime = in.readLong();
                boolean practice = false;
                if (version >= 8) {
                    if (version >= 6) in.readInt();   // customLength
                    if (version >= 7) { in.readUTF(); in.readUTF(); } // skinValue, skinSig
                    practice = in.readBoolean();
                }
                return new long[]{success ? 1 : 0, runTime, practice ? 1 : 0};
            }
        } catch (Exception ignored) {
            return null;
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
                name.toLowerCase().contains("_" + ReplayData.fileKey(mapName) + "_") && name.endsWith(".replay"));
        if (files == null || files.length <= limit) return;

        // Use header-only reads to find the PB — avoids full GZIP decompression of every file.
        File pbFile = null;
        long pbTime = Long.MAX_VALUE;

        List<File> deleteCandidates = new ArrayList<>();
        for (File f : files) {
            long[] header = readReplayHeader(f);
            if (header == null) continue;
            boolean successful = header[0] == 1;
            long runTime = header[1];
            boolean practice = header.length > 2 && header[2] == 1;
            // Practice runs are never the PB regardless of time.
            if (successful && !practice && runTime > 0 && runTime < pbTime) {
                pbTime = runTime;
                pbFile = f;
            }
            deleteCandidates.add(f);
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
            if (!f.delete()) {
                plugin.getLogger().warning("Could not delete old replay: " + f.getName());
            }
            toDelete--;
        }
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private static final SimpleDateFormat TIMESTAMP_FMT = new SimpleDateFormat("dd.MM.yyyy HH:mm");

    public static String formatTimestamp(long timestamp) {
        synchronized (TIMESTAMP_FMT) {
            return TIMESTAMP_FMT.format(new Date(timestamp));
        }
    }

    public void shutdown() {
        if (recordingTaskId != -1) Bukkit.getScheduler().cancelTask(recordingTaskId);
        for (UUID uuid : new ArrayList<>(activeSessions.keySet())) stopPlayback(uuid);
        activeRecorders.clear();
    }
}
