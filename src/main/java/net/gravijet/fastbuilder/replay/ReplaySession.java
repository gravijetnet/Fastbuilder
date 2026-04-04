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
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Active replay playback session.
 * The replay is rendered in an isolated area at (REPLAY_AREA_X, Y, Z),
 * away from all live maps. All frame coordinates are offset from their
 * original island positions to the replay area.
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

    private int currentTick = 0;
    private double playbackSpeed = 1.0;
    private boolean paused = false;
    private boolean ended = false;
    private int taskId = -1;

    private int npcId = -1;

    // Blocks placed during playback (offset-adjusted coordinates), for cleanup
    private final List<Location> placedBlocks = new ArrayList<>();

    private Location originalLocation;
    private Location viewerWatchLocation;

    // Translation from original island coordinates → replay area
    private int offsetX;
    private int offsetY;
    private int offsetZ;

    // Hotbar slot assignments
    public static final int SLOT_REWIND       = 0;
    public static final int SLOT_SLOW         = 1;
    public static final int SLOT_PAUSE        = 2;
    public static final int SLOT_FAST         = 3;
    public static final int SLOT_FORWARD      = 4;
    public static final int SLOT_REPLAY_AGAIN = 7;
    public static final int SLOT_STOP         = 8;

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
        int islandOriginZ = map.getOriginZ() + replayData.getIslandIndex() * map.getDistance();

        offsetX = replayAreaX - islandOriginX;
        offsetY = REPLAY_AREA_Y - islandOriginY;
        offsetZ = replayAreaZ - islandOriginZ;

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
        giveControlItems(viewer);

        // Paste template in replay area, then teleport and begin playback
        plugin.getFawePaster().pasteIslands(
                map.getWorld(),
                map.getTemplateFile(),
                replayAreaX, REPLAY_AREA_Y, replayAreaZ,
                map.getDistance(),
                0, 1,
                new Runnable() {
                    @Override
                    public void run() {
                        Player v = Bukkit.getPlayer(viewerUuid);
                        if (v == null || !v.isOnline()) return;

                        // Place initial blocks (blocks that were on the island at recording start)
                        placeInitialBlocks(map.getWorld());

                        v.teleport(viewerWatchLocation);
                        spawnReplayNpc(v);
                        startPlaybackLoop();
                    }
                }
        );
    }

    @SuppressWarnings("deprecation")
    private void placeInitialBlocks(org.bukkit.World world) {
        for (net.gravijet.fastbuilder.replay.ReplayFrame.BlockPlacement bp : replayData.getInitialBlocks()) {
            int bx = bp.getBlockX() + offsetX;
            int by = bp.getBlockY() + offsetY;
            int bz = bp.getBlockZ() + offsetZ;
            Block block = world.getBlockAt(bx, by, bz);
            block.setTypeIdAndData(bp.getBlockId(), bp.getBlockData(), false);
            placedBlocks.add(block.getLocation().clone());
        }
    }

    private void startPlaybackLoop() {
        taskId = new BukkitRunnable() {
            private double tickAccumulator = 0;

            @Override
            public void run() {
                if (paused) return;

                Player p = Bukkit.getPlayer(viewerUuid);
                if (p == null || !p.isOnline()) {
                    stop();
                    return;
                }

                tickAccumulator += playbackSpeed;

                while (tickAccumulator >= 1.0 && currentTick < replayData.getFrames().size()) {
                    playFrame(p, currentTick);
                    currentTick++;
                    tickAccumulator -= 1.0;
                }

                if (!ended && currentTick >= replayData.getFrames().size()) {
                    ended = true;
                    paused = true;
                    Player p2 = Bukkit.getPlayer(viewerUuid);
                    if (p2 != null) showReplayEndItems(p2);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L).getTaskId();
    }

    // -------------------------------------------------------------------------
    // Stop
    // -------------------------------------------------------------------------

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
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
                    map.getDistance(), 0, 1, null);
        }

        despawnReplayNpc();

        Player viewer = Bukkit.getPlayer(viewerUuid);
        if (viewer != null && viewer.isOnline()) {
            viewer.setFlying(false);
            viewer.setAllowFlight(false);
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

        // Clear blocks placed during this playback
        for (Location loc : placedBlocks) {
            Block block = loc.getBlock();
            if (block != null) block.setType(Material.AIR);
        }
        placedBlocks.clear();

        currentTick = 0;
        ended = false;
        paused = false;

        despawnReplayNpc();

        Player viewer = Bukkit.getPlayer(viewerUuid);
        if (viewer != null) {
            giveControlItems(viewer);
            viewer.teleport(viewerWatchLocation);
        }

        // Re-paste template then restart loop
        MapData map = plugin.getMapManager().getMap(replayData.getMapName());
        if (map != null) {
            int replayAreaX = REPLAY_BASE_X - replaySlot * REPLAY_SLOT_SPACING;
            plugin.getFawePaster().pasteIslands(
                    map.getWorld(),
                    map.getTemplateFile(),
                    replayAreaX, REPLAY_AREA_Y, REPLAY_BASE_Z,
                    map.getDistance(),
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

    @SuppressWarnings("deprecation")
    private void playFrame(Player viewer, int frameIndex) {
        if (frameIndex < 0 || frameIndex >= replayData.getFrames().size()) return;

        ReplayFrame frame = replayData.getFrames().get(frameIndex);
        MapData map = plugin.getMapManager().getMap(replayData.getMapName());
        if (map == null) return;

        World world = map.getWorld();
        if (world == null) return;

        // Move NPC with offset applied
        moveReplayNpc(new Location(world,
                frame.getX() + offsetX,
                frame.getY() + offsetY,
                frame.getZ() + offsetZ,
                frame.getYaw(), frame.getPitch()));

        // Place block with offset applied
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

    public void rewind(int ticks) {
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
        if (viewer != null) {
            for (int i = 0; i < currentTick && i < replayData.getFrames().size(); i++) {
                if (replayData.getFrames().get(i).hasBlockPlacement()) {
                    playFrame(viewer, i);
                }
            }
        }
    }

    public void fastForward(int ticks) {
        int targetTick = Math.min(replayData.getFrames().size() - 1, currentTick + ticks);
        Player viewer = Bukkit.getPlayer(viewerUuid);
        if (viewer == null) return;

        while (currentTick < targetTick) {
            playFrame(viewer, currentTick);
            currentTick++;
        }
    }

    public void togglePause() { paused = !paused; }

    public void setPlaybackSpeed(double speed) {
        this.playbackSpeed = Math.max(0.25, Math.min(4.0, speed));
    }

    public double getPlaybackSpeed()         { return playbackSpeed; }
    public boolean isPaused()                { return paused; }
    public UUID getViewerUuid()              { return viewerUuid; }
    public ReplayData getReplayData()        { return replayData; }
    public Location getViewerWatchLocation() { return viewerWatchLocation; }
    public int getReplaySlot()               { return replaySlot; }

    // -------------------------------------------------------------------------
    // Control items
    // -------------------------------------------------------------------------

    private void giveControlItems(Player player) {
        player.getInventory().clear();

        player.getInventory().setItem(SLOT_REWIND, new ItemBuilder(Material.STAINED_GLASS_PANE, (byte) 14)
                .name("&c<< Rewind (5s)").build());
        player.getInventory().setItem(SLOT_SLOW, new ItemBuilder(Material.STAINED_GLASS_PANE, (byte) 4)
                .name("&e< Slower").lore("&7Speed: " + String.format("%.2f", playbackSpeed) + "x").build());
        player.getInventory().setItem(SLOT_PAUSE, new ItemBuilder(Material.STAINED_GLASS_PANE, paused ? (byte) 5 : (byte) 1)
                .name(paused ? "&a> Resume" : "&6|| Pause").build());
        player.getInventory().setItem(SLOT_FAST, new ItemBuilder(Material.STAINED_GLASS_PANE, (byte) 4)
                .name("&e> Faster").lore("&7Speed: " + String.format("%.2f", playbackSpeed) + "x").build());
        player.getInventory().setItem(SLOT_FORWARD, new ItemBuilder(Material.STAINED_GLASS_PANE, (byte) 5)
                .name("&a» Forward (5s)").build());
        player.getInventory().setItem(SLOT_STOP, new ItemBuilder(Material.BARRIER)
                .name("&c&lLeave Replay").build());
    }

    private void showReplayEndItems(Player player) {
        // Keep all existing control items, just update pause button and add Play Again
        player.getInventory().setItem(SLOT_PAUSE, new ItemBuilder(Material.STAINED_GLASS_PANE, (byte) 5)
                .name("&a&lReplay Finished").build());
        player.getInventory().setItem(SLOT_REPLAY_AGAIN, new ItemBuilder(Material.EMERALD)
                .name("&a&lPlay Again").lore("&7Click to watch again").build());
        player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix()
                + "&fReplay finished. &aPlay Again &7(slot 8) or &cLeave Replay &7(slot 9)."));
    }

    public void updateControlItems() {
        Player viewer = Bukkit.getPlayer(viewerUuid);
        if (viewer != null) giveControlItems(viewer);
    }

    // -------------------------------------------------------------------------
    // NPC Management
    // -------------------------------------------------------------------------

    private void spawnReplayNpc(Player viewer) {
        try {
            net.citizensnpcs.api.npc.NPCRegistry registry = net.citizensnpcs.api.CitizensAPI.getNPCRegistry();
            net.citizensnpcs.api.npc.NPC npc = registry.createNPC(
                    org.bukkit.entity.EntityType.PLAYER,
                    ColorUtil.translate("&5" + replayData.getPlayerName())
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
