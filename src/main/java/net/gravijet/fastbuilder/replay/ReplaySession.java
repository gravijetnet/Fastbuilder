package net.gravijet.fastbuilder.replay;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.map.MapData;
import net.gravijet.fastbuilder.util.ColorUtil;
import net.gravijet.fastbuilder.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Active replay playback session.
 *
 * The replay is rendered in an isolated area at (REPLAY_AREA_X, Y, Z),
 * away from all live maps. All frame coordinates are offset from their
 * original island positions to the replay area.
 *
 * Smooth slow-motion: the NPC position is updated every server tick via
 * linear interpolation between the previous and next recorded frame.
 * This prevents the "jitter" caused by only moving the NPC at frame
 * boundaries (which left the entity idle for multiple ticks at slow speeds,
 * allowing gravity/Citizens corrections to create oscillation).
 */
public class ReplaySession {

    // Isolated replay area base - far from all live island grids
    private static final int REPLAY_BASE_X = -10000;
    private static final int REPLAY_AREA_Y = 10;
    private static final int REPLAY_BASE_Z = -10000;
    private static final int REPLAY_SLOT_SPACING = 1000;

    // Slot assigned to this session (determines X offset)
    private final int replaySlot;

    private final FastBuilder plugin;
    private final UUID viewerUuid;
    private final ReplayData replayData;

    // Integer index into replayData.getFrames() – the next frame to be processed
    private int currentTick = 0;
    // Fractional progress into the current frame interval (0.0 – <1.0).
    // Advances by playbackSpeed each server tick.  When it crosses 1.0 the
    // integer currentTick advances and the excess carries over.
    private double playbackFraction = 0.0;

    private double playbackSpeed = 1.0;
    private boolean paused = false;
    private boolean ended = false;
    private int taskId = -1;

    private int npcId = -1;

    // Whether the viewer is currently looking through the NPC's eyes
    private boolean inNpcCamera = false;
    // Last hand-item sent to the camera hotbar (for dirty-checking)
    private int  lastCameraHandItemId   = 0;
    private byte lastCameraHandItemData = 0;

    // Blocks placed during playback (offset-adjusted coordinates), for cleanup
    private final List<Location> placedBlocks = new ArrayList<>();

    private Location originalLocation;
    private Location viewerWatchLocation;

    // Translation from original island coordinates → replay area
    private int offsetX;
    private int offsetY;
    private int offsetZ;

    // Hotbar slot assignments — 3 controls centred in slots 3-5, stop at 8
    public static final int SLOT_TIMELINE     = 3;   // Stick   – L=Rewind / R=FastForward
    public static final int SLOT_PAUSE_RESUME = 4;   // Dye     – click to toggle
    public static final int SLOT_SPEED        = 5;   // BlazeRod – L=Slower / R=Faster
    public static final int SLOT_REPLAY_AGAIN = 7;
    public static final int SLOT_STOP         = 8;

    // Extra margin (blocks) beyond the schematic boundary that the viewer may roam
    private static final int REPLAY_BOUNDARY_MARGIN = 30;

    // Replay-area AABB, populated in start() once the map dimensions are known
    private int replayMinX, replayMaxX;
    private int replayMinY, replayMaxY;
    private int replayMinZ, replayMaxZ;

    // Saved XP bar state so we can restore it when the replay ends
    private float savedExp   = 0f;
    private int   savedLevel = 0;

    public ReplaySession(FastBuilder plugin, UUID viewerUuid, ReplayData replayData, int replaySlot) {
        this.plugin = plugin;
        this.viewerUuid = viewerUuid;
        this.replayData = replayData;
        this.replaySlot = replaySlot;
    }

    // -------------------------------------------------------------------------
    // Start
    // -------------------------------------------------------------------------

    public void start() {
        Player viewer = Bukkit.getPlayer(viewerUuid);
        if (viewer == null) return;

        originalLocation = viewer.getLocation().clone();

        MapData map = plugin.getMapManager().getMap(replayData.getMapName());
        if (map == null) return;

        // Calculate per-slot replay area coordinates (1000 blocks apart per viewer)
        int replayAreaX = REPLAY_BASE_X - replaySlot * REPLAY_SLOT_SPACING;
        int replayAreaZ = REPLAY_BASE_Z;

        // Offset: translate original island origin → replay area origin
        int islandOriginX = map.getOriginX();
        int islandOriginY = map.getOriginY();
        int islandOriginZ = map.getOriginZ() + replayData.getIslandIndex() * map.getActualZStep();

        offsetX = replayAreaX - islandOriginX;
        offsetY = REPLAY_AREA_Y - islandOriginY;
        offsetZ = replayAreaZ - islandOriginZ;

        // Store the schematic AABB so onMove() can enforce the roam boundary
        replayMinX = replayAreaX;
        replayMaxX = replayAreaX + map.getIslandWidth();
        replayMinY = REPLAY_AREA_Y;
        replayMaxY = REPLAY_AREA_Y + map.getIslandHeight();
        replayMinZ = replayAreaZ;
        replayMaxZ = replayAreaZ + map.getIslandLength();

        // Viewer watches from slightly above-behind the replay island spawn
        Location islandSpawn = map.getIslandSpawn(replayData.getIslandIndex());
        viewerWatchLocation = new Location(
                viewer.getWorld(),
                islandSpawn.getX() + offsetX,
                islandSpawn.getY() + offsetY + 5,
                islandSpawn.getZ() + offsetZ - 10,
                0f, -20f
        );

        viewer.setAllowFlight(true);
        viewer.setFlying(true);

        // Save XP bar so we can restore it when the replay ends
        savedExp   = viewer.getExp();
        savedLevel = viewer.getLevel();
        viewer.setExp(0f);
        viewer.setLevel(0);

        giveControlItems(viewer);

        // Paste template in replay area, then teleport and begin playback.
        // After the start island is placed, also paste the end island (if the map uses one)
        // so it is visible during the replay regardless of how far away it normally sits.
        final int replayAreaXFinal = replayAreaX;
        plugin.getFawePaster().pasteIslands(
                map.getWorld(),
                map.getTemplateFile(),
                replayAreaX, REPLAY_AREA_Y, replayAreaZ,
                map.getActualZStep(),
                0, 1,
                new Runnable() {
                    @Override
                    public void run() {
                        Player v = Bukkit.getPlayer(viewerUuid);
                        if (v == null || !v.isOnline()) return;

                        placeInitialBlocks(map.getWorld());

                        // Force-paste the end island at its replay-area position.
                        // The base custom length positions it the same distance away as
                        // it sits in the live world.  Chunks this far out are not normally
                        // loaded, so we also force-load every chunk between the start and
                        // end islands so the viewer can see the full bridging distance.
                        if (map.hasEndIsland() && map.getEndIslandTemplateFile() != null) {
                            int len = map.getBaseCustomLength() > 0
                                    ? map.getBaseCustomLength()
                                    : map.getEffectiveMinCustomLength();
                            int endX = replayAreaXFinal + map.getIslandWidth() - 1 + len;
                            int endY = REPLAY_AREA_Y + map.getEndIslandYOffset();
                            int endZ = replayAreaZ + map.getEndIslandZOffset();
                            forceLoadChunksInLine(map.getWorld(),
                                    replayAreaXFinal, REPLAY_AREA_Y, replayAreaZ,
                                    endX + map.getEndIslandWidth(), endY, endZ + map.getEndIslandLength());
                            plugin.getFawePaster().pasteTemplate(
                                    map.getWorld(), map.getEndIslandTemplateFile(),
                                    endX, endY, endZ, null);
                        }

                        v.teleport(viewerWatchLocation);
                        spawnReplayNpc(v);
                        startPlaybackLoop();
                    }
                }
        );
    }

    /**
     * Synchronously force-loads all chunks in the corridor between two positions.
     * Called from the async FAWE callback thread before the end-island template paste;
     * the paste itself is also scheduled on a worker thread so sync loading here is safe
     * and avoids cross-thread scheduling complexity.
     */
    private void forceLoadChunksInLine(org.bukkit.World world,
                                        int fromX, int fromY, int fromZ,
                                        int toX,   int toY,   int toZ) {
        int minCX = Math.min(fromX, toX) >> 4;
        int maxCX = Math.max(fromX, toX) >> 4;
        int minCZ = Math.min(fromZ, toZ) >> 4;
        int maxCZ = Math.max(fromZ, toZ) >> 4;
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    world.loadChunk(cx, cz, true);
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void placeInitialBlocks(org.bukkit.World world) {
        for (ReplayFrame.BlockPlacement bp : replayData.getInitialBlocks()) {
            int bx = bp.getBlockX() + offsetX;
            int by = bp.getBlockY() + offsetY;
            int bz = bp.getBlockZ() + offsetZ;
            Block block = world.getBlockAt(bx, by, bz);
            block.setTypeIdAndData(bp.getBlockId(), bp.getBlockData(), false);
            placedBlocks.add(block.getLocation().clone());
        }
    }

    // -------------------------------------------------------------------------
    // Playback loop
    // -------------------------------------------------------------------------

    private void startPlaybackLoop() {
        taskId = new BukkitRunnable() {
            @Override
            public void run() {
                Player p = Bukkit.getPlayer(viewerUuid);
                if (p == null || !p.isOnline()) {
                    stop();
                    return;
                }

                // When paused: freeze the NPC in place (prevents gravity pulling it down)
                // and keep the XP bar at the current position, then exit early.
                if (paused) {
                    updateNpcPosition(p);
                    return;
                }

                // Advance fractional playback position
                playbackFraction += playbackSpeed;

                // Process all complete frames that fall inside this tick's advance
                while (playbackFraction >= 1.0 && currentTick < replayData.getFrames().size()) {
                    processFrameEffects(p, currentTick);
                    currentTick++;
                    playbackFraction -= 1.0;
                }

                // Move NPC every tick using interpolated position (smooth at any speed)
                updateNpcPosition(p);

                // If viewer is in NPC-camera mode, keep the hotbar hand-item current
                if (inNpcCamera) {
                    refreshCameraHandItem(p);
                }

                // Update XP bar timeline (0% = start, 100% = end)
                int totalFrames = replayData.getFrames().size();
                if (totalFrames > 0) {
                    float progress = Math.min(1.0f,
                            (float)(currentTick + Math.min(playbackFraction, 1.0)) / totalFrames);
                    p.setExp(progress);
                }

                if (!ended && currentTick >= replayData.getFrames().size()) {
                    ended = true;
                    paused = true;
                    p.setExp(1.0f);
                    showReplayEndItems(p);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L).getTaskId();
    }

    // -------------------------------------------------------------------------
    // Per-frame effects  (block placements, arm-swing, hand item, NPC metadata)
    // Does NOT move the NPC – movement is handled by updateNpcPosition() every tick.
    // -------------------------------------------------------------------------

    @SuppressWarnings("deprecation")
    private void processFrameEffects(Player viewer, int frameIndex) {
        if (frameIndex < 0 || frameIndex >= replayData.getFrames().size()) return;

        ReplayFrame frame = replayData.getFrames().get(frameIndex);
        MapData map = plugin.getMapManager().getMap(replayData.getMapName());
        if (map == null) return;

        World world = map.getWorld();
        if (world == null) return;

        // Head rotation, sneak/sprint metadata, arm-swing
        applyNmsState(viewer, frame);

        // Hand item equipment packet
        if (frame.getHandItemId() != 0) {
            applyHandItem(viewer, frame.getHandItemId(), frame.getHandItemData());
        }

        // Block placement
        if (frame.hasBlockPlacement()) {
            ReplayFrame.BlockPlacement bp = frame.getBlockPlacement();
            int bx = bp.getBlockX() + offsetX;
            int by = bp.getBlockY() + offsetY;
            int bz = bp.getBlockZ() + offsetZ;
            Block block = world.getBlockAt(bx, by, bz);
            block.setTypeIdAndData(bp.getBlockId(), bp.getBlockData(), false);
            placedBlocks.add(block.getLocation().clone());
        }
    }

    // -------------------------------------------------------------------------
    // Smooth NPC position (called every tick)
    // -------------------------------------------------------------------------

    /**
     * Interpolates the NPC position between the previous and next recorded frame
     * using the current playbackFraction (0 = at previous frame, 1 = at next frame).
     * Calling this every tick produces smooth movement at any playback speed.
     */
    private void updateNpcPosition(Player viewer) {
        if (npcId < 0) return;

        List<ReplayFrame> frames = replayData.getFrames();
        if (frames.isEmpty()) return;

        MapData map = plugin.getMapManager().getMap(replayData.getMapName());
        if (map == null || map.getWorld() == null) return;

        // prevIdx = last processed frame; nextIdx = next frame to process
        int prevIdx = Math.max(0, currentTick - 1);
        int nextIdx = Math.min(currentTick, frames.size() - 1);

        ReplayFrame prev = frames.get(prevIdx);
        ReplayFrame next = frames.get(nextIdx);

        // t=0 means "at previous frame position"; t=1 means "at next frame position"
        double t = (prevIdx == nextIdx) ? 0.0 : Math.min(1.0, Math.max(0.0, playbackFraction));

        double x = lerp(prev.getX(), next.getX(), t) + offsetX;
        double y = lerp(prev.getY(), next.getY(), t) + offsetY;
        double z = lerp(prev.getZ(), next.getZ(), t) + offsetZ;
        float  yaw   = lerpAngle(prev.getYaw(),   next.getYaw(),   (float) t);
        float  pitch = lerpAngle(prev.getPitch(), next.getPitch(), (float) t);

        moveReplayNpc(new Location(map.getWorld(), x, y, z, yaw, pitch));
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /**
     * Lerp that takes the shortest arc through angle wrap-around (±180°).
     * Prevents the NPC spinning the long way when yaw crosses the ±180 boundary.
     */
    private static float lerpAngle(float a, float b, float t) {
        float diff = b - a;
        while (diff >  180f) diff -= 360f;
        while (diff < -180f) diff += 360f;
        return a + diff * t;
    }

    // -------------------------------------------------------------------------
    // Stop
    // -------------------------------------------------------------------------

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }

        // If the viewer is in NPC-camera mode, reset the camera first
        if (inNpcCamera) {
            Player v = Bukkit.getPlayer(viewerUuid);
            if (v != null) sendCameraFollowPacket(v, false);
            inNpcCamera = false;
        }

        // Clear blocks placed during playback
        for (Location loc : placedBlocks) {
            Block block = loc.getBlock();
            if (block != null) block.setType(Material.AIR);
        }
        placedBlocks.clear();

        // Clear the pasted template from the replay area
        MapData map = plugin.getMapManager().getMap(replayData.getMapName());
        if (map != null && map.getWorld() != null) {
            int replayAreaX = REPLAY_BASE_X - replaySlot * REPLAY_SLOT_SPACING;
            plugin.getFawePaster().clearIslands(
                    map.getWorld(),
                    replayAreaX, REPLAY_AREA_Y, REPLAY_BASE_Z,
                    map.getIslandWidth(), map.getIslandHeight(), map.getIslandLength(),
                    map.getActualZStep(), 0, 1, null);
        }

        despawnReplayNpc();

        Player viewer = Bukkit.getPlayer(viewerUuid);
        if (viewer != null && viewer.isOnline()) {
            viewer.setFlying(false);
            viewer.setAllowFlight(false);
            viewer.setExp(savedExp);
            viewer.setLevel(savedLevel);
            viewer.getInventory().clear();

            net.gravijet.fastbuilder.player.PlayerData pData =
                    plugin.getPlayerManager().getCachedData(viewerUuid);
            boolean teleported = false;
            if (pData != null && pData.getLastMap() != null) {
                MapData pMap = plugin.getMapManager().getMap(pData.getLastMap());
                if (pMap != null) {
                    viewer.teleport(pMap.getIslandSpawn(pData.getLastIsland()));
                    teleported = true;
                }
            }
            if (!teleported && originalLocation != null) {
                viewer.teleport(originalLocation);
            }

            if (plugin.getHotbarManager() != null) {
                plugin.getHotbarManager().giveItems(viewer);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Restart (Play Again)
    // -------------------------------------------------------------------------

    public void restart() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }

        // Exit camera mode if active
        if (inNpcCamera) {
            Player v = Bukkit.getPlayer(viewerUuid);
            if (v != null) sendCameraFollowPacket(v, false);
            inNpcCamera = false;
        }

        // Clear blocks placed during this playback
        for (Location loc : placedBlocks) {
            Block block = loc.getBlock();
            if (block != null) block.setType(Material.AIR);
        }
        placedBlocks.clear();

        currentTick = 0;
        playbackFraction = 0.0;
        ended = false;
        paused = false;

        despawnReplayNpc();

        Player viewer = Bukkit.getPlayer(viewerUuid);
        if (viewer != null) {
            viewer.setExp(0f);
            viewer.setLevel(0);
            giveControlItems(viewer);
            // Player stays at current position — no teleport on restart
        }

        // Re-paste template then restart loop
        MapData map = plugin.getMapManager().getMap(replayData.getMapName());
        if (map != null) {
            int replayAreaX = REPLAY_BASE_X - replaySlot * REPLAY_SLOT_SPACING;
            plugin.getFawePaster().pasteIslands(
                    map.getWorld(),
                    map.getTemplateFile(),
                    replayAreaX, REPLAY_AREA_Y, REPLAY_BASE_Z,
                    map.getActualZStep(),
                    0, 1,
                    new Runnable() {
                        @Override
                        public void run() {
                            placeInitialBlocks(map.getWorld());
                            Player v = Bukkit.getPlayer(viewerUuid);
                            if (v != null) spawnReplayNpc(v);
                            startPlaybackLoop();
                        }
                    }
            );
        } else {
            if (viewer != null) spawnReplayNpc(viewer);
            startPlaybackLoop();
        }
    }

    // -------------------------------------------------------------------------
    // Playback controls
    // -------------------------------------------------------------------------

    public void rewind(int ticks) {
        playbackFraction = 0.0;
        int newTick = Math.max(0, currentTick - ticks);
        MapData map = plugin.getMapManager().getMap(replayData.getMapName());

        // Undo blocks placed after newTick
        for (int i = currentTick - 1; i >= newTick; i--) {
            if (i < 0 || i >= replayData.getFrames().size()) continue;
            ReplayFrame frame = replayData.getFrames().get(i);
            if (frame.hasBlockPlacement()) {
                ReplayFrame.BlockPlacement bp = frame.getBlockPlacement();
                if (map != null && map.getWorld() != null) {
                    map.getWorld().getBlockAt(
                            bp.getBlockX() + offsetX,
                            bp.getBlockY() + offsetY,
                            bp.getBlockZ() + offsetZ)
                            .setType(Material.AIR);
                }
            }
        }

        currentTick = newTick;
        ended = false;

        // Re-apply blocks up to new position
        Player viewer = Bukkit.getPlayer(viewerUuid);
        if (viewer != null && map != null && map.getWorld() != null) {
            for (int i = 0; i < currentTick && i < replayData.getFrames().size(); i++) {
                ReplayFrame frame = replayData.getFrames().get(i);
                if (frame.hasBlockPlacement()) {
                    ReplayFrame.BlockPlacement bp = frame.getBlockPlacement();
                    map.getWorld().getBlockAt(
                            bp.getBlockX() + offsetX,
                            bp.getBlockY() + offsetY,
                            bp.getBlockZ() + offsetZ)
                            .setTypeIdAndData(bp.getBlockId(), bp.getBlockData(), false);
                }
            }
            updateNpcPosition(viewer);
            if (inNpcCamera) refreshCameraHandItem(viewer);
        }
    }

    @SuppressWarnings("deprecation")
    public void fastForward(int ticks) {
        playbackFraction = 0.0;
        int targetTick = Math.min(replayData.getFrames().size(), currentTick + ticks);
        Player viewer = Bukkit.getPlayer(viewerUuid);
        if (viewer == null) return;

        MapData map = plugin.getMapManager().getMap(replayData.getMapName());
        if (map == null || map.getWorld() == null) {
            currentTick = targetTick;
            return;
        }

        // Apply block placements for all skipped frames without spamming NPC teleports
        for (int i = currentTick; i < targetTick; i++) {
            ReplayFrame frame = replayData.getFrames().get(i);
            if (frame.hasBlockPlacement()) {
                ReplayFrame.BlockPlacement bp = frame.getBlockPlacement();
                int bx = bp.getBlockX() + offsetX;
                int by = bp.getBlockY() + offsetY;
                int bz = bp.getBlockZ() + offsetZ;
                Block block = map.getWorld().getBlockAt(bx, by, bz);
                block.setTypeIdAndData(bp.getBlockId(), bp.getBlockData(), false);
                placedBlocks.add(block.getLocation().clone());
            }
        }

        currentTick = targetTick;
        ended = false;

        // Snap NPC immediately to the target position
        updateNpcPosition(viewer);
        if (inNpcCamera) refreshCameraHandItem(viewer);
    }

    public void togglePause() { paused = !paused; }

    /**
     * Set playback speed.  Supported range: 0.05× (1 frame/second) to 4×.
     * The interpolated-position system makes any speed below 1× smooth.
     */
    public void setPlaybackSpeed(double speed) {
        this.playbackSpeed = Math.max(0.05, Math.min(4.0, speed));
    }

    public double getPlaybackSpeed()         { return playbackSpeed; }
    public boolean isPaused()                { return paused; }
    public boolean isEnded()                 { return ended; }
    public UUID getViewerUuid()              { return viewerUuid; }
    public ReplayData getReplayData()        { return replayData; }
    public Location getViewerWatchLocation() { return viewerWatchLocation; }
    public int getReplaySlot()               { return replaySlot; }
    public int getNpcId()                    { return npcId; }
    public boolean isInNpcCamera()           { return inNpcCamera; }

    /**
     * Returns true when {@code loc} is further than {@link #REPLAY_BOUNDARY_MARGIN} blocks
     * outside the replay schematic AABB.  Called every move-event by ProtectionListener.
     */
    public boolean isOutsideReplayBounds(Location loc) {
        return loc.getX() < replayMinX - REPLAY_BOUNDARY_MARGIN
            || loc.getX() > replayMaxX + REPLAY_BOUNDARY_MARGIN
            || loc.getY() < replayMinY - REPLAY_BOUNDARY_MARGIN
            || loc.getY() > replayMaxY + REPLAY_BOUNDARY_MARGIN
            || loc.getZ() < replayMinZ - REPLAY_BOUNDARY_MARGIN
            || loc.getZ() > replayMaxZ + REPLAY_BOUNDARY_MARGIN;
    }

    // -------------------------------------------------------------------------
    // Control items
    // -------------------------------------------------------------------------

    private void giveControlItems(Player player) {
        player.getInventory().clear();

        // Slot 3 – Timeline (Stick)
        // Instructions are shown in BOTH the display name and the lore.
        player.getInventory().setItem(SLOT_TIMELINE,
                new ItemBuilder(Material.STICK)
                        .name("&e\u2190 Rewind  &8|  &aFast-Forward \u2192")
                        .lore("&7Left-Click:  &eRewind 0.5s",
                              "&7Right-Click: &aFast-Forward 0.5s")
                        .build());

        // Slot 4 – Play / Pause (Gray Dye = playing, Lime Dye = paused/stopped)
        boolean playing = !paused;
        player.getInventory().setItem(SLOT_PAUSE_RESUME,
                new ItemBuilder(Material.INK_SACK, playing ? (byte) 8 : (byte) 10)
                        .name(playing
                                ? "&7\u25BA Playing  &7\u2014 Click to Pause"
                                : "&a\u25A0 Paused  &7\u2014 Click to Play")
                        .lore("&7Click to toggle playback")
                        .build());

        // Slot 5 – Replay Speed (Blaze Rod)
        // Instructions are shown in BOTH the display name and the lore.
        String speedStr = formatSpeed(playbackSpeed);
        player.getInventory().setItem(SLOT_SPEED,
                new ItemBuilder(Material.BLAZE_ROD)
                        .name("&c- Slower  &8|  &a+ Faster  &7(&e" + speedStr + "x&7)")
                        .lore("&7Left-Click:  &cDecrease Speed",
                              "&7Right-Click: &aIncrease Speed",
                              "&7Current:     &e" + speedStr + "x")
                        .build());

        // Slot 8 – Leave
        player.getInventory().setItem(SLOT_STOP,
                new ItemBuilder(Material.BARRIER)
                        .name("&c&lLeave Replay")
                        .build());
    }

    private void showReplayEndItems(Player player) {
        // Exit NPC camera if active before showing end items
        if (inNpcCamera) {
            sendCameraFollowPacket(player, false);
            inNpcCamera = false;
        }

        // Lime Dye in SLOT_PAUSE_RESUME restarts the replay when clicked (isEnded() == true)
        player.getInventory().setItem(SLOT_PAUSE_RESUME,
                new ItemBuilder(Material.INK_SACK, (byte) 10)  // lime dye = restart
                        .name("&a■ Replay Finished  &7— Click to watch again")
                        .lore("&7Click to restart the replay")
                        .build());
        player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix()
                + "&fReplay finished. &aClick Lime Dye &7(slot 5) to restart or &cLeave Replay &7(slot 9)."));

    }

    public void updateControlItems() {
        Player viewer = Bukkit.getPlayer(viewerUuid);
        if (viewer == null) return;
        if (inNpcCamera) {
            refreshCameraHandItem(viewer);
        } else {
            giveControlItems(viewer);
        }
    }

    /** Format a speed value with no redundant trailing zeros: 1.0→"1", 0.5→"0.5", 0.05→"0.05". */
    private static String formatSpeed(double speed) {
        String s = String.format("%.2f", speed);
        // Strip trailing zeros after the decimal point
        s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s;
    }

    // -------------------------------------------------------------------------
    // NPC Camera (spectator perspective)
    // -------------------------------------------------------------------------

    /**
     * Toggle first-person camera through the replay NPC's eyes.
     * Triggered when the viewer right-clicks the replay NPC.
     */
    public void toggleNpcCamera(Player viewer) {
        inNpcCamera = !inNpcCamera;
        sendCameraFollowPacket(viewer, inNpcCamera);

        if (inNpcCamera) {
            giveCameraHotbar(viewer);
        } else {
            giveControlItems(viewer);
        }
    }

    /**
     * Send PacketPlayOutCamera to the viewer to lock/unlock their camera to the NPC.
     * {@code followNpc = true}  → viewer sees through the NPC's eyes.
     * {@code followNpc = false} → camera returns to the viewer's own entity.
     */
    private void sendCameraFollowPacket(Player viewer, boolean followNpc) {
        if (npcId < 0 && followNpc) return;
        try {
            String ver = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];

            Object viewerHandle = viewer.getClass().getMethod("getHandle").invoke(viewer);
            Object viewerConn   = viewerHandle.getClass().getField("playerConnection").get(viewerHandle);
            Class<?> packetIface = nmsClass(ver, "Packet");

            Object targetNmsEntity;
            if (followNpc) {
                net.citizensnpcs.api.npc.NPC npc = net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getById(npcId);
                if (npc == null || !npc.isSpawned()) return;
                targetNmsEntity = npc.getEntity().getClass().getMethod("getHandle").invoke(npc.getEntity());
            } else {
                // Reset to viewer's own entity
                targetNmsEntity = viewerHandle;
            }

            Class<?> entityClass = nmsClass(ver, "Entity");
            Object camPacket = nmsClass(ver, "PacketPlayOutCamera")
                    .getConstructor(entityClass)
                    .newInstance(targetNmsEntity);

            sendPacketViaConn(viewerConn, packetIface, camPacket);
        } catch (Exception ignored) {}
    }

    /**
     * Replace the viewer's hotbar with a minimal "camera mode" layout:
     * – Slot 4 (centre): the replay player's current hand item.
     * – Slot 8 (SLOT_STOP): an "Exit Camera" button.
     */
    @SuppressWarnings("deprecation")
    private void giveCameraHotbar(Player viewer) {
        viewer.getInventory().clear();

        // Show the currently-held item from the most recently processed frame
        int frameIdx = Math.max(0, Math.min(currentTick, replayData.getFrames().size() - 1));
        if (!replayData.getFrames().isEmpty()) {
            ReplayFrame frame = replayData.getFrames().get(frameIdx);
            int  itemId   = frame.getHandItemId();
            byte itemData = frame.getHandItemData();
            lastCameraHandItemId   = itemId;
            lastCameraHandItemData = itemData;

            if (itemId != 0) {
                Material mat = Material.getMaterial(itemId);
                if (mat != null && mat != Material.AIR) {
                    viewer.getInventory().setItem(4, new ItemStack(mat, 1, itemData));
                }
            }
        }

        viewer.getInventory().setItem(SLOT_STOP,
                new ItemBuilder(Material.BARRIER)
                        .name("&c&lExit Camera &7(or right-click NPC)")
                        .build());
    }

    /**
     * Called every tick while in camera mode to keep the displayed hand item
     * in sync with the currently playing frame.  Uses dirty-checking so the
     * inventory packet is only sent when the item actually changes.
     */
    @SuppressWarnings("deprecation")
    private void refreshCameraHandItem(Player viewer) {
        if (replayData.getFrames().isEmpty()) return;
        int frameIdx = Math.max(0, currentTick - 1);
        if (frameIdx >= replayData.getFrames().size()) return;

        ReplayFrame frame = replayData.getFrames().get(frameIdx);
        int  itemId   = frame.getHandItemId();
        byte itemData = frame.getHandItemData();

        if (itemId == lastCameraHandItemId && itemData == lastCameraHandItemData) return;
        lastCameraHandItemId   = itemId;
        lastCameraHandItemData = itemData;

        if (itemId == 0) {
            viewer.getInventory().setItem(4, null);
        } else {
            Material mat = Material.getMaterial(itemId);
            if (mat != null && mat != Material.AIR) {
                viewer.getInventory().setItem(4, new ItemStack(mat, 1, itemData));
            } else {
                viewer.getInventory().setItem(4, null);
            }
        }
    }

    // -------------------------------------------------------------------------
    // NMS packet helpers
    // -------------------------------------------------------------------------

    /**
     * Sends NMS packets to the viewer to reflect the full visual state of the NPC:
     * head yaw, sneak/sprint metadata, and arm-swing animation.
     */
    private void applyNmsState(Player viewer, ReplayFrame frame) {
        if (npcId < 0) return;
        try {
            net.citizensnpcs.api.npc.NPC npc = net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getById(npcId);
            if (npc == null || !npc.isSpawned()) return;

            org.bukkit.entity.Entity entity = npc.getEntity();
            if (!(entity instanceof org.bukkit.entity.Player)) return;

            String ver = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];

            Object nmsEntity   = entity.getClass().getMethod("getHandle").invoke(entity);
            Object viewerHandle = viewer.getClass().getMethod("getHandle").invoke(viewer);
            Object viewerConn  = viewerHandle.getClass().getField("playerConnection").get(viewerHandle);

            Class<?> packetIface = nmsClass(ver, "Packet");
            Class<?> entityClass = nmsClass(ver, "Entity");

            // Head yaw
            byte headYawByte = (byte) (frame.getHeadYaw() * 256.0F / 360.0F);
            Object headPacket = nmsClass(ver, "PacketPlayOutEntityHeadRotation")
                    .getConstructor(entityClass, byte.class)
                    .newInstance(nmsEntity, headYawByte);
            sendPacketViaConn(viewerConn, packetIface, headPacket);

            // Sneak / sprint flags in entity metadata byte (DataWatcher index 0)
            Object dw = nmsEntity.getClass().getMethod("getDataWatcher").invoke(nmsEntity);
            byte flags = 0;
            try {
                flags = ((Number) dw.getClass().getMethod("getByte", int.class).invoke(dw, 0)).byteValue();
            } catch (Exception ignored) {}
            if (frame.isSneaking())  flags |= 0x02; else flags &= ~0x02;
            if (frame.isSprinting()) flags |= 0x08; else flags &= ~0x08;
            dw.getClass().getMethod("watch", int.class, Object.class).invoke(dw, 0, flags);

            int entityId = ((Number) nmsEntity.getClass().getMethod("getId").invoke(nmsEntity)).intValue();
            Class<?> dwClass = nmsClass(ver, "DataWatcher");
            Object metaPacket = nmsClass(ver, "PacketPlayOutEntityMetadata")
                    .getConstructor(int.class, dwClass, boolean.class)
                    .newInstance(entityId, dw, false);
            sendPacketViaConn(viewerConn, packetIface, metaPacket);

            // Arm-swing animation
            if (frame.isSwingingArm()) {
                Object animPacket = nmsClass(ver, "PacketPlayOutAnimation")
                        .getConstructor(entityClass, int.class)
                        .newInstance(nmsEntity, 0);
                sendPacketViaConn(viewerConn, packetIface, animPacket);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Sends a PacketPlayOutEntityEquipment so the NPC visually holds the
     * item the player had in hand during the recorded run.
     */
    @SuppressWarnings("deprecation")
    private void applyHandItem(Player viewer, int itemId, byte itemData) {
        if (npcId < 0) return;
        try {
            net.citizensnpcs.api.npc.NPC npc = net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getById(npcId);
            if (npc == null || !npc.isSpawned()) return;

            org.bukkit.entity.Entity entity = npc.getEntity();
            if (!(entity instanceof org.bukkit.entity.Player)) return;

            String ver = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];

            Object nmsEntity   = entity.getClass().getMethod("getHandle").invoke(entity);
            Object viewerHandle = viewer.getClass().getMethod("getHandle").invoke(viewer);
            Object viewerConn  = viewerHandle.getClass().getField("playerConnection").get(viewerHandle);

            org.bukkit.Material mat = org.bukkit.Material.getMaterial(itemId);
            if (mat == null || mat == org.bukkit.Material.AIR) return;
            org.bukkit.inventory.ItemStack bukkit = new org.bukkit.inventory.ItemStack(mat, 1, itemData);

            Class<?> craftItemStackClass = Class.forName("org.bukkit.craftbukkit." + ver + ".inventory.CraftItemStack");
            Object nmsItem = craftItemStackClass.getMethod("asNMSCopy", org.bukkit.inventory.ItemStack.class)
                    .invoke(null, bukkit);

            int entityId = ((Number) nmsEntity.getClass().getMethod("getId").invoke(nmsEntity)).intValue();
            Class<?> itemStackClass = nmsClass(ver, "ItemStack");
            Object equipPacket = nmsClass(ver, "PacketPlayOutEntityEquipment")
                    .getConstructor(int.class, int.class, itemStackClass)
                    .newInstance(entityId, 0, nmsItem);
            sendPacketViaConn(viewerConn, nmsClass(ver, "Packet"), equipPacket);
        } catch (Exception ignored) {}
    }

    private static Class<?> nmsClass(String version, String name) throws ClassNotFoundException {
        return Class.forName("net.minecraft.server." + version + "." + name);
    }

    private static void sendPacketViaConn(Object conn, Class<?> packetIface, Object packet)
            throws Exception {
        conn.getClass().getMethod("sendPacket", packetIface).invoke(conn, packet);
    }

    // -------------------------------------------------------------------------
    // NPC Management
    // -------------------------------------------------------------------------

    private void spawnReplayNpc(Player viewer) {
        try {
            String tag = replayData.getPlayerDisplayTag();
            String npcName;
            if (tag != null && !tag.isEmpty()) {
                npcName = tag;
            } else {
                npcName = plugin.getReplayManager().getReplayDisplayName(replayData);
            }

            net.citizensnpcs.api.npc.NPCRegistry registry = net.citizensnpcs.api.CitizensAPI.getNPCRegistry();
            net.citizensnpcs.api.npc.NPC npc = registry.createNPC(
                    org.bukkit.entity.EntityType.PLAYER,
                    npcName
            );

            npc.data().set("player-skin-uuid", replayData.getPlayerUuid().toString());
            npc.data().set("player-skin-name", replayData.getPlayerName());

            MapData map = plugin.getMapManager().getMap(replayData.getMapName());
            if (map != null && map.getWorld() != null && !replayData.getFrames().isEmpty()) {
                ReplayFrame first = replayData.getFrames().get(0);
                Location spawnLoc = new Location(map.getWorld(),
                        first.getX() + offsetX,
                        first.getY() + offsetY,
                        first.getZ() + offsetZ,
                        first.getYaw(), first.getPitch());
                npc.spawn(spawnLoc);
            }

            npcId = npc.getId();

            // Disable Citizens navigator so it doesn't fight our per-tick teleports
            try {
                npc.getNavigator().cancelNavigation();
            } catch (Exception ignored) {}

        } catch (NoClassDefFoundError | Exception e) {
            plugin.getLogger().warning("Could not spawn replay NPC: " + e.getMessage());
        }
    }

    private void moveReplayNpc(Location location) {
        if (npcId < 0) return;
        try {
            net.citizensnpcs.api.npc.NPC npc = net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getById(npcId);
            if (npc != null && npc.isSpawned()) {
                npc.getEntity().teleport(location);
            }
        } catch (NoClassDefFoundError | Exception ignored) {}
    }

    private void despawnReplayNpc() {
        if (npcId < 0) return;
        try {
            net.citizensnpcs.api.npc.NPC npc = net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getById(npcId);
            if (npc != null) npc.destroy();
        } catch (NoClassDefFoundError | Exception ignored) {}
        npcId = -1;
    }
}
