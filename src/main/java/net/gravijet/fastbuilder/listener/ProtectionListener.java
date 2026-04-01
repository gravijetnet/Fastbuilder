package net.gravijet.fastbuilder.listener;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.gameplay.RunSession;
import net.gravijet.fastbuilder.map.GridCalculator;
import net.gravijet.fastbuilder.map.MapData;
import net.gravijet.fastbuilder.map.MapManager;
import net.gravijet.fastbuilder.replay.ReplaySession;
import net.gravijet.fastbuilder.util.ColorUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Prevents players from building or interacting outside their assigned island.
 */
public class ProtectionListener implements Listener {

    private final FastBuilder plugin;
    private final java.util.Map<java.util.UUID, Long> fallCooldown = new java.util.HashMap<>();

    public ProtectionListener(FastBuilder plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("fastbuilder.admin") && plugin.getMapManager().hasSetupSession(player.getUniqueId())) {
            return;
        }

        // Use Z-corridor check for building: allows placing blocks along the X axis
        // (the build direction) beyond the island template's defined width.
        if (!canBuildAtLocation(player, event.getBlock().getLocation())) {
            event.setCancelled(true);
            String raw = plugin.getConfigManager().getMessage("cannot-build-here");
            raw = raw.replace("%prefix%", plugin.getConfigManager().getPrefix());
            player.sendMessage(ColorUtil.translate(raw));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("fastbuilder.admin") && plugin.getMapManager().hasSetupSession(player.getUniqueId())) {
            return;
        }

        if (!isOnOwnIsland(player, event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }

        RunSession session = plugin.getGameplayManager() != null
                ? plugin.getGameplayManager().getSession(player.getUniqueId()) : null;
        if (session != null) {
            Location blockLoc = event.getBlock().getLocation();
            boolean playerPlaced = false;
            for (Location placed : session.getPlacedBlocks()) {
                if (placed.getBlockX() == blockLoc.getBlockX()
                        && placed.getBlockY() == blockLoc.getBlockY()
                        && placed.getBlockZ() == blockLoc.getBlockZ()) {
                    playerPlaced = true;
                    break;
                }
            }
            if (!playerPlaced) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;

        Player player = event.getPlayer();
        if (player.hasPermission("fastbuilder.admin") && plugin.getMapManager().hasSetupSession(player.getUniqueId())) {
            return;
        }

        if (!isOnOwnIsland(player, event.getClickedBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();

        if (player.hasPermission("fastbuilder.admin") && plugin.getMapManager().hasSetupSession(player.getUniqueId())) {
            return;
        }

        if (plugin.getGameplayManager() != null && plugin.getGameplayManager().getSession(player.getUniqueId()) != null) {
            event.setCancelled(true);
        }
        // Also protect replay viewers
        if (plugin.getReplayManager() != null && plugin.getReplayManager().isInPlayback(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player) {
            event.setCancelled(true);
            ((Player) event.getEntity()).setFoodLevel(20);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();

        if (player.hasPermission("fastbuilder.admin") && plugin.getMapManager().hasSetupSession(player.getUniqueId())) {
            return;
        }

        // Handle replay viewer void protection
        if (plugin.getReplayManager() != null && plugin.getReplayManager().isInPlayback(player.getUniqueId())) {
            if (event.getTo().getY() < 0) {
                ReplaySession replaySession = plugin.getReplayManager().getPlaybackSession(player.getUniqueId());
                if (replaySession != null) {
                    Location safeSpot = replaySession.getViewerWatchLocation();
                    if (safeSpot != null) {
                        player.teleport(safeSpot);
                    } else {
                        // Fall back: stop the replay and return to island
                        plugin.getReplayManager().stopPlayback(player.getUniqueId());
                    }
                }
            }
            return; // Skip normal boundary checks for replay viewers
        }

        RunSession session = plugin.getGameplayManager() != null
                ? plugin.getGameplayManager().getSession(player.getUniqueId()) : null;
        if (session == null) return;

        MapData map = plugin.getMapManager().getMap(session.getMapName());
        if (map == null) return;

        Location to = event.getTo();
        int[] bounds = GridCalculator.getIslandBounds(map, session.getIslandIndex());
        int maxDist = plugin.getConfigManager().getMaxDistance();

        boolean outOfBounds = to.getBlockX() < bounds[0] - maxDist
                || to.getBlockZ() < bounds[2] - maxDist
                || to.getBlockZ() > bounds[5] + maxDist;

        boolean inVoid;
        MapData mapForVoid = plugin.getMapManager().getMap(session.getMapName());
        if (mapForVoid != null && mapForVoid.hasDeathY()) {
            int absoluteDeathY = mapForVoid.getOriginY() + mapForVoid.getDeathY();
            inVoid = to.getBlockY() < absoluteDeathY;
        } else {
            inVoid = to.getBlockY() < bounds[1] - maxDist;
        }

        if (outOfBounds || inVoid) {
            long now = System.currentTimeMillis();
            Long lastFall = fallCooldown.get(player.getUniqueId());
            if (lastFall == null || now - lastFall > 2000) {
                fallCooldown.put(player.getUniqueId(), now);
                plugin.getGameplayManager().onFall(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (plugin.getGameplayManager() != null && plugin.getGameplayManager().getSession(player.getUniqueId()) != null) {
            event.setCancelled(true);
        }
        if (plugin.getReplayManager() != null && plugin.getReplayManager().isInPlayback(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /**
     * Check if a player can place a block at a given location.
     * Uses Z-corridor check to allow building along the X axis (build direction)
     * beyond the island template's defined width.
     */
    private boolean canBuildAtLocation(Player player, Location blockLoc) {
        if (plugin.getGameplayManager() != null && plugin.getGameplayManager().isInBuildMode(player.getUniqueId())) {
            return isOnOwnIsland(player, blockLoc);
        }

        MapManager mm = plugin.getMapManager();

        for (MapData map : mm.getAllMaps()) {
            if (!map.isEnabled()) continue;
            if (!blockLoc.getWorld().getName().equals(map.getWorldName())) continue;

            int playerIsland = mm.getPlayerIsland(map.getName(), player.getUniqueId());
            if (playerIsland < 0) continue;

            // Z-corridor: allow blocks anywhere in the player's Z strip
            long islandMinZ = map.getOriginZ() + (long) playerIsland * map.getDistance();
            long islandMaxZ = islandMinZ + map.getIslandLength();
            int blockZ = blockLoc.getBlockZ();

            // Also enforce X >= originX - 1 (don't build behind the start)
            // X is the build direction and is otherwise unlimited
            if (blockZ >= islandMinZ - 1 && blockZ <= islandMaxZ + 1
                    && blockLoc.getBlockX() >= map.getOriginX() - 1) {
                return true;
            }
        }

        return player.hasPermission("fastbuilder.admin");
    }

    /**
     * Standard island bounds check for block breaking and interaction.
     */
    private boolean isOnOwnIsland(Player player, Location blockLoc) {
        MapManager mm = plugin.getMapManager();

        for (MapData map : mm.getAllMaps()) {
            if (!map.isEnabled()) continue;
            if (!blockLoc.getWorld().getName().equals(map.getWorldName())) continue;

            int blockIsland = GridCalculator.getIslandIndex(map, blockLoc);
            if (blockIsland < 0) continue;

            int playerIsland = mm.getPlayerIsland(map.getName(), player.getUniqueId());
            return blockIsland == playerIsland;
        }

        return player.hasPermission("fastbuilder.admin");
    }
}
