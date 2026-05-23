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
 */
public class ReplaySession {

    // Isolated replay area base - far from all live island grids
    private static final int REPLAY_BASE_X = -10000;
    private static final int REPLAY_AREA_Y = 10;
    private static final int REPLAY_BASE_Z = -10000;
    private static final int REPLAY_SLOT_SPACING = 1000;

    private final int replaySlot;

    private final FastBuilder plugin;
    private final UUID viewerUuid;
    private final ReplayData replayData;

    // Integer index into replayData.getFrames() – the next frame to be processed
    private int currentTick = 0;
    // Fractional progress into the current frame interval (0.0 – <1.0).
    private double playbackFraction = 0.0;

    private double playbackSpeed = 1.0;
    private boolean paused = false;
    private boolean ended = false;
    private int taskId = -1;

    private boolean inNpcCamera = false;
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

    // Hotbar slot assignments
    public static final int SLOT_TIMELINE     = 3;
    public static final int SLOT_PAUSE_RESUME = 4;
    public static final int SLOT_SPEED        = 5;
    public static final int SLOT_REPLAY_AGAIN = 7;
    public static final int SLOT_STOP         = 8;

    private static final int REPLAY_BOUNDARY_MARGIN = 30;

    private int replayMinX, replayMaxX;
    private int replayMinY, replayMaxY;
    private int replayMinZ, replayMaxZ;
    private int precomputedMaxX;

    // Region of the pasted end island {x,y,z,w,h,l} so stop() can clear it.
    // Without this, the reusable replay slot accumulates stale end islands
    // when consecutive replays use different custom lengths.
    private int[] endIslandClearRegion = null;

    private float savedExp   = 0f;
    private int   savedLevel = 0;

    private ReplayNpcController npcController;

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

        int replayAreaX = REPLAY_BASE_X - replaySlot * REPLAY_SLOT_SPACING;
        int replayAreaZ = REPLAY_BASE_Z;

        // Recorded block coords are absolute, so the translation origin must include
        // the diagonal X shift of the slot the run was recorded on (0 for straight maps).
        int islandOriginX = map.getOriginX()
                + (int) ((long) replayData.getIslandIndex() * map.getDiagonalStepX());
        int islandOriginY = map.getOriginY();
        int islandOriginZ = map.getOriginZ() + (int) ((long) replayData.getIslandIndex() * map.getActualZStep());

        offsetX = replayAreaX - islandOriginX;
        offsetY = REPLAY_AREA_Y - islandOriginY;
        offsetZ = replayAreaZ - islandOriginZ;

        replayMinX = replayAreaX;
        replayMaxX = replayAreaX + map.getIslandWidth();
        replayMinY = REPLAY_AREA_Y;
        replayMaxY = REPLAY_AREA_Y + map.getIslandHeight();
        replayMinZ = replayAreaZ;
        replayMaxZ = replayAreaZ + map.getIslandLength();

        // For infinite maps, precompute the full bridge extent so the viewer boundary
        // is correct from the start (not just as blocks are placed during playback)
        precomputedMaxX = replayMaxX;
        if (map.isInfinite()) {
            for (ReplayFrame frame : replayData.getFrames()) {
                if (frame.hasBlockPlacement()) {
                    int bx = frame.getBlockPlacement().getBlockX() + offsetX;
                    if (bx > precomputedMaxX) precomputedMaxX = bx;
                }
            }
            for (ReplayFrame.BlockPlacement bp : replayData.getInitialBlocks()) {
                int bx = bp.getBlockX() + offsetX;
                if (bx > precomputedMaxX) precomputedMaxX = bx;
            }
        }

        npcController = new ReplayNpcController(plugin, replayData, offsetX, offsetY, offsetZ);

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

        savedExp   = viewer.getExp();
        savedLevel = viewer.getLevel();
        viewer.setExp(0f);
        viewer.setLevel(0);

        giveControlItems(viewer);

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

                        if (map.hasEndIsland() && map.getEndIslandTemplateFile() != null) {
                            int replayCustomLen = replayData.getCustomLength();
                            int len = replayCustomLen > 0
                                    ? replayCustomLen
                                    : (map.getBaseCustomLength() > 0
                                        ? map.getBaseCustomLength()
                                        : map.getEffectiveMinCustomLength());
                            // Use the same X formula as EndPlatformManager/GameplayListener
                            // (… + len - 2) so the replay's end island sits exactly where
                            // it was during the actual recorded run.
                            int endX = replayAreaXFinal + map.getIslandWidth() + len - 2;
                            int endY = REPLAY_AREA_Y + map.getEndIslandYOffset();
                            int endZ = replayAreaZ + map.getEndIslandZOffset();
                            endIslandClearRegion = new int[]{
                                endX, endY, endZ,
                                map.getEndIslandWidth(), map.getEndIslandHeight(),
                                map.getEndIslandLength()
                            };
                            forceLoadChunksInLine(map.getWorld(),
                                    replayAreaXFinal, REPLAY_AREA_Y, replayAreaZ,
                                    endX + map.getEndIslandWidth(), endY, endZ + map.getEndIslandLength());
                            plugin.getFawePaster().pasteTemplate(
                                    map.getWorld(), map.getEndIslandTemplateFile(),
                                    endX, endY, endZ, null);
                        }

                        v.teleport(viewerWatchLocation);
                        npcController.spawn(v);
                        startPlaybackLoop();
                    }
                }
        );
    }

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

                if (paused) {
                    updateNpcPosition(p);
                    return;
                }

                playbackFraction += playbackSpeed;

                List<ReplayFrame> loopFrames = replayData.getFrames();
                while (playbackFraction >= 1.0 && currentTick < loopFrames.size()) {
                    int recTick = loopFrames.get(currentTick).getTick();
                    processFrameEffects(p, currentTick);
                    currentTick++;
                    // Collapse extra frames recorded in the SAME server tick (multiple
                    // blocks placed within one 50ms tick) into this single playback
                    // step so the replay matches the real run's duration. Same-tick
                    // frames share an identical position, so NPC motion is unaffected.
                    while (currentTick < loopFrames.size()
                            && loopFrames.get(currentTick).getTick() == recTick) {
                        processFrameEffects(p, currentTick);
                        currentTick++;
                    }
                    playbackFraction -= 1.0;
                }

                updateNpcPosition(p);

                if (inNpcCamera) {
                    refreshCameraHandItem(p);
                }

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

    @SuppressWarnings("deprecation")
    private void processFrameEffects(Player viewer, int frameIndex) {
        if (frameIndex < 0 || frameIndex >= replayData.getFrames().size()) return;

        ReplayFrame frame = replayData.getFrames().get(frameIndex);
        MapData map = plugin.getMapManager().getMap(replayData.getMapName());
        if (map == null) return;

        World world = map.getWorld();
        if (world == null) return;

        npcController.applyNmsState(viewer, frame);

        if (frame.getHandItemId() != 0) {
            npcController.applyHandItem(viewer, frame.getHandItemId(), frame.getHandItemData());
        }

        if (frame.hasBlockPlacement()) {
            ReplayFrame.BlockPlacement bp = frame.getBlockPlacement();
            int bx = bp.getBlockX() + offsetX;
            int by = bp.getBlockY() + offsetY;
            int bz = bp.getBlockZ() + offsetZ;
            Block block = world.getBlockAt(bx, by, bz);
            block.setTypeIdAndData(bp.getBlockId(), bp.getBlockData(), false);
            placedBlocks.add(block.getLocation().clone());
            if (bx > precomputedMaxX) precomputedMaxX = bx;
        }
    }

    // -------------------------------------------------------------------------
    // Smooth NPC position (called every tick)
    // -------------------------------------------------------------------------

    private void updateNpcPosition(Player viewer) {
        List<ReplayFrame> frames = replayData.getFrames();
        if (frames.isEmpty()) return;

        MapData map = plugin.getMapManager().getMap(replayData.getMapName());
        if (map == null || map.getWorld() == null) return;

        int prevIdx = Math.max(0, currentTick - 1);
        int nextIdx = Math.min(currentTick, frames.size() - 1);

        ReplayFrame prev = frames.get(prevIdx);
        ReplayFrame next = frames.get(nextIdx);

        double t = (prevIdx == nextIdx) ? 0.0 : Math.min(1.0, Math.max(0.0, playbackFraction));

        double x = lerp(prev.getX(), next.getX(), t) + offsetX;
        double y = lerp(prev.getY(), next.getY(), t) + offsetY;
        double z = lerp(prev.getZ(), next.getZ(), t) + offsetZ;
        float  yaw   = lerpAngle(prev.getYaw(),   next.getYaw(),   (float) t);
        float  pitch = lerpAngle(prev.getPitch(), next.getPitch(), (float) t);

        npcController.move(new Location(map.getWorld(), x, y, z, yaw, pitch));
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

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

        if (inNpcCamera) {
            Player v = Bukkit.getPlayer(viewerUuid);
            if (v != null) npcController.sendCameraFollowPacket(v, false);
            inNpcCamera = false;
        }

        for (Location loc : placedBlocks) {
            Block block = loc.getBlock();
            if (block != null) block.setType(Material.AIR);
        }
        placedBlocks.clear();

        MapData map = plugin.getMapManager().getMap(replayData.getMapName());
        if (map != null && map.getWorld() != null) {
            int replayAreaX = REPLAY_BASE_X - replaySlot * REPLAY_SLOT_SPACING;
            plugin.getFawePaster().clearIslands(
                    map.getWorld(),
                    replayAreaX, REPLAY_AREA_Y, REPLAY_BASE_Z,
                    map.getIslandWidth(), map.getIslandHeight(), map.getIslandLength(),
                    map.getActualZStep(), 0, 1, null);

            // Clear the pasted end island too — otherwise the reusable replay slot
            // keeps a stale end island when the next replay uses a different length.
            if (endIslandClearRegion != null) {
                int[] r = endIslandClearRegion;
                plugin.getFawePaster().clearRegion(map.getWorld(),
                        r[0], r[1], r[2],
                        r[0] + r[3] - 1, r[1] + r[4] - 1, r[2] + r[5] - 1, null);
                endIslandClearRegion = null;
            }
        }

        npcController.despawn();

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
    // Restart
    // -------------------------------------------------------------------------

    public void restart() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }

        if (inNpcCamera) {
            Player v = Bukkit.getPlayer(viewerUuid);
            if (v != null) npcController.sendCameraFollowPacket(v, false);
            inNpcCamera = false;
        }

        for (Location loc : placedBlocks) {
            Block block = loc.getBlock();
            if (block != null) block.setType(Material.AIR);
        }
        placedBlocks.clear();

        currentTick = 0;
        playbackFraction = 0.0;
        ended = false;
        paused = false;

        npcController.despawn();

        Player viewer = Bukkit.getPlayer(viewerUuid);
        if (viewer != null) {
            viewer.setExp(0f);
            viewer.setLevel(0);
            giveControlItems(viewer);
        }

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
                            if (v != null) npcController.spawn(v);
                            startPlaybackLoop();
                        }
                    }
            );
        } else {
            if (viewer != null) npcController.spawn(viewer);
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

        updateNpcPosition(viewer);
        if (inNpcCamera) refreshCameraHandItem(viewer);
    }

    public void togglePause() { paused = !paused; }

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
    public int getNpcId()                    { return npcController != null ? npcController.getNpcId() : -1; }
    public boolean isInNpcCamera()           { return inNpcCamera; }

    public boolean isOutsideReplayBounds(Location loc) {
        return loc.getX() < replayMinX - REPLAY_BOUNDARY_MARGIN
            || loc.getX() > precomputedMaxX + REPLAY_BOUNDARY_MARGIN
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

        player.getInventory().setItem(SLOT_TIMELINE,
                new ItemBuilder(Material.STICK)
                        .name("&e← Rewind  &8|  &aFast-Forward →")
                        .lore("&7Left-Click:  &eRewind 0.5s",
                              "&7Right-Click: &aFast-Forward 0.5s")
                        .build());

        boolean playing = !paused;
        player.getInventory().setItem(SLOT_PAUSE_RESUME,
                new ItemBuilder(Material.INK_SACK, playing ? (byte) 8 : (byte) 10)
                        .name(playing
                                ? "&7► Playing  &7— Click to Pause"
                                : "&a■ Paused  &7— Click to Play")
                        .lore("&7Click to toggle playback")
                        .build());

        String speedStr = formatSpeed(playbackSpeed);
        player.getInventory().setItem(SLOT_SPEED,
                new ItemBuilder(Material.BLAZE_ROD)
                        .name("&c- Slower  &8|  &a+ Faster  &7(&e" + speedStr + "x&7)")
                        .lore("&7Left-Click:  &cDecrease Speed",
                              "&7Right-Click: &aIncrease Speed",
                              "&7Current:     &e" + speedStr + "x")
                        .build());

        player.getInventory().setItem(SLOT_STOP,
                new ItemBuilder(Material.BARRIER)
                        .name("&c&lLeave Replay")
                        .build());
    }

    private void showReplayEndItems(Player player) {
        if (inNpcCamera) {
            npcController.sendCameraFollowPacket(player, false);
            inNpcCamera = false;
        }

        player.getInventory().setItem(SLOT_PAUSE_RESUME,
                new ItemBuilder(Material.INK_SACK, (byte) 10)
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

    private static String formatSpeed(double speed) {
        String s = String.format("%.2f", speed);
        s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s;
    }

    // -------------------------------------------------------------------------
    // NPC Camera
    // -------------------------------------------------------------------------

    public void toggleNpcCamera(Player viewer) {
        inNpcCamera = !inNpcCamera;
        npcController.sendCameraFollowPacket(viewer, inNpcCamera);

        if (inNpcCamera) {
            giveCameraHotbar(viewer);
        } else {
            giveControlItems(viewer);
        }
    }

    @SuppressWarnings("deprecation")
    private void giveCameraHotbar(Player viewer) {
        viewer.getInventory().clear();

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
}
