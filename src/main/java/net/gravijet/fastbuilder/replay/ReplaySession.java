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
 * Active replay playback session for a player watching a replay.
 */
public class ReplaySession {

    private final FastBuilder plugin;
    private final UUID viewerUuid;
    private final ReplayData replayData;

    private int currentTick = 0;
    private double playbackSpeed = 1.0;
    private boolean paused = false;
    private int taskId = -1;

    // NPC for replay
    private int npcId = -1;

    // Blocks placed during playback (for cleanup)
    private final List<Location> placedBlocks = new ArrayList<>();

    // Original player location before entering replay
    private Location originalLocation;

    // Hotbar slot assignments
    public static final int SLOT_REWIND = 0;
    public static final int SLOT_SLOW = 1;
    public static final int SLOT_PAUSE = 2;
    public static final int SLOT_FAST = 3;
    public static final int SLOT_FORWARD = 4;
    public static final int SLOT_STOP = 8;

    public ReplaySession(FastBuilder plugin, UUID viewerUuid, ReplayData replayData) {
        this.plugin = plugin;
        this.viewerUuid = viewerUuid;
        this.replayData = replayData;
    }

    /**
     * Start the replay playback.
     */
    public void start() {
        Player viewer = Bukkit.getPlayer(viewerUuid);
        if (viewer == null) return;

        originalLocation = viewer.getLocation().clone();

        // Put viewer in fly mode
        viewer.setAllowFlight(true);
        viewer.setFlying(true);

        // Give control items
        giveControlItems(viewer);

        // Spawn replay NPC
        spawnReplayNpc(viewer);

        // Start playback loop
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

                // End of replay
                if (currentTick >= replayData.getFrames().size()) {
                    stop();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L).getTaskId();
    }

    /**
     * Play a single frame.
     */
    @SuppressWarnings("deprecation")
    private void playFrame(Player viewer, int frameIndex) {
        if (frameIndex < 0 || frameIndex >= replayData.getFrames().size()) return;

        ReplayFrame frame = replayData.getFrames().get(frameIndex);
        MapData map = plugin.getMapManager().getMap(replayData.getMapName());
        if (map == null) return;

        World world = map.getWorld();
        if (world == null) return;

        // Move the NPC
        moveReplayNpc(new Location(world, frame.getX(), frame.getY(), frame.getZ(),
                frame.getYaw(), frame.getPitch()));

        // Place block if needed
        if (frame.hasBlockPlacement()) {
            ReplayFrame.BlockPlacement bp = frame.getBlockPlacement();
            Block block = world.getBlockAt(bp.getBlockX(), bp.getBlockY(), bp.getBlockZ());
            block.setTypeIdAndData(bp.getBlockId(), bp.getBlockData(), false);
            placedBlocks.add(block.getLocation().clone());
        }
    }

    /**
     * Stop the replay and clean up.
     */
    public void stop() {
        // Cancel task
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }

        // Cleanup placed blocks
        for (Location loc : placedBlocks) {
            Block block = loc.getBlock();
            if (block != null) {
                block.setType(Material.AIR);
            }
        }
        placedBlocks.clear();

        // Despawn replay NPC
        despawnReplayNpc();

        // Teleport viewer back
        Player viewer = Bukkit.getPlayer(viewerUuid);
        if (viewer != null && viewer.isOnline()) {
            viewer.setFlying(false);
            viewer.setAllowFlight(false);
            viewer.getInventory().clear();

            if (originalLocation != null) {
                viewer.teleport(originalLocation);
            }

            // Re-give hotbar items
            if (plugin.getHotbarManager() != null) {
                plugin.getHotbarManager().giveItems(viewer);
            }
        }
    }

    /**
     * Rewind by a number of ticks.
     */
    public void rewind(int ticks) {
        // Undo blocks placed between new position and current
        int newTick = Math.max(0, currentTick - ticks);

        // Remove blocks placed after the new tick
        for (int i = replayData.getFrames().size() - 1; i >= newTick; i--) {
            if (i < replayData.getFrames().size()) {
                ReplayFrame frame = replayData.getFrames().get(i);
                if (frame.hasBlockPlacement()) {
                    ReplayFrame.BlockPlacement bp = frame.getBlockPlacement();
                    MapData map = plugin.getMapManager().getMap(replayData.getMapName());
                    if (map != null && map.getWorld() != null) {
                        map.getWorld().getBlockAt(bp.getBlockX(), bp.getBlockY(), bp.getBlockZ())
                                .setType(Material.AIR);
                    }
                }
            }
        }

        currentTick = newTick;

        // Replay blocks up to new position
        for (int i = 0; i < currentTick && i < replayData.getFrames().size(); i++) {
            ReplayFrame frame = replayData.getFrames().get(i);
            if (frame.hasBlockPlacement()) {
                Player viewer = Bukkit.getPlayer(viewerUuid);
                if (viewer != null) playFrame(viewer, i);
            }
        }
    }

    /**
     * Fast forward by a number of ticks.
     */
    public void fastForward(int ticks) {
        int targetTick = Math.min(replayData.getFrames().size() - 1, currentTick + ticks);
        Player viewer = Bukkit.getPlayer(viewerUuid);
        if (viewer == null) return;

        while (currentTick < targetTick) {
            playFrame(viewer, currentTick);
            currentTick++;
        }
    }

    public void togglePause() {
        paused = !paused;
    }

    public void setPlaybackSpeed(double speed) {
        this.playbackSpeed = Math.max(0.25, Math.min(4.0, speed));
    }

    public double getPlaybackSpeed() { return playbackSpeed; }
    public boolean isPaused() { return paused; }
    public UUID getViewerUuid() { return viewerUuid; }
    public ReplayData getReplayData() { return replayData; }

    // --- Control Items ---

    private void giveControlItems(Player player) {
        player.getInventory().clear();

        player.getInventory().setItem(SLOT_REWIND, new ItemBuilder(Material.SKULL_ITEM, (byte) 3)
                .name("&c<< Rewind (5s)").build());
        player.getInventory().setItem(SLOT_SLOW, new ItemBuilder(Material.SKULL_ITEM, (byte) 3)
                .name("&e< Slower").lore("&7Current: " + playbackSpeed + "x").build());
        player.getInventory().setItem(SLOT_PAUSE, new ItemBuilder(Material.SKULL_ITEM, (byte) 3)
                .name(paused ? "&a> Resume" : "&6|| Pause").build());
        player.getInventory().setItem(SLOT_FAST, new ItemBuilder(Material.SKULL_ITEM, (byte) 3)
                .name("&e> Faster").lore("&7Current: " + playbackSpeed + "x").build());
        player.getInventory().setItem(SLOT_FORWARD, new ItemBuilder(Material.SKULL_ITEM, (byte) 3)
                .name("&a>> Forward (5s)").build());
        player.getInventory().setItem(SLOT_STOP, new ItemBuilder(Material.BARRIER)
                .name("&c&lStop Replay").build());
    }

    public void updateControlItems() {
        Player viewer = Bukkit.getPlayer(viewerUuid);
        if (viewer != null) giveControlItems(viewer);
    }

    // --- NPC Management ---

    private void spawnReplayNpc(Player viewer) {
        try {
            net.citizensnpcs.api.npc.NPCRegistry registry = net.citizensnpcs.api.CitizensAPI.getNPCRegistry();
            net.citizensnpcs.api.npc.NPC npc = registry.createNPC(
                    org.bukkit.entity.EntityType.PLAYER,
                    ColorUtil.translate("&5" + replayData.getPlayerName())
            );

            // Set skin to the recorded player
            npc.data().set("player-skin-uuid", replayData.getPlayerUuid().toString());
            npc.data().set("player-skin-name", replayData.getPlayerName());

            MapData map = plugin.getMapManager().getMap(replayData.getMapName());
            if (map != null && map.getWorld() != null && !replayData.getFrames().isEmpty()) {
                ReplayFrame first = replayData.getFrames().get(0);
                Location spawnLoc = new Location(map.getWorld(),
                        first.getX(), first.getY(), first.getZ(), first.getYaw(), first.getPitch());
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
            if (npc != null) {
                npc.destroy();
            }
        } catch (NoClassDefFoundError | Exception ignored) {}
        npcId = -1;
    }
}
