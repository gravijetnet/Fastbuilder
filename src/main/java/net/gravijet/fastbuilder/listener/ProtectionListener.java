package net.gravijet.fastbuilder.listener;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.gameplay.RunSession;
import net.gravijet.fastbuilder.map.GridCalculator;
import net.gravijet.fastbuilder.map.MapData;
import net.gravijet.fastbuilder.map.MapManager;
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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Prevents players from building or interacting outside their assigned island.
 */
public class ProtectionListener implements Listener {

    private final FastBuilder plugin;

    public ProtectionListener(FastBuilder plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("fastbuilder.admin") && plugin.getMapManager().hasSetupSession(player.getUniqueId())) {
            return; // Allow admins in setup mode
        }

        if (!isOnOwnIsland(player, event.getBlock().getLocation())) {
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

        // Can only break blocks the player placed themselves, not map blocks
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

    /**
     * Cancel ALL damage for players (no fall damage, no void death, etc.).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();

        // Skip admins in setup
        if (player.hasPermission("fastbuilder.admin") && plugin.getMapManager().hasSetupSession(player.getUniqueId())) {
            return;
        }

        // Cancel all damage for Fastbuilder players with active sessions
        if (plugin.getGameplayManager() != null && plugin.getGameplayManager().getSession(player.getUniqueId()) != null) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevent hunger loss.
     */
    @EventHandler
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player) {
            event.setCancelled(true);
            ((Player) event.getEntity()).setFoodLevel(20);
        }
    }

    /**
     * Boundary check: if a player leaves their island bounds or falls into the void,
     * teleport them back to their island spawn.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onMove(PlayerMoveEvent event) {
        // Only check actual block movement
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();

        // Skip admins in setup and replay mode
        if (player.hasPermission("fastbuilder.admin") && plugin.getMapManager().hasSetupSession(player.getUniqueId())) {
            return;
        }
        if (plugin.getReplayManager() != null && plugin.getReplayManager().isInPlayback(player.getUniqueId())) {
            return;
        }

        RunSession session = plugin.getGameplayManager() != null
                ? plugin.getGameplayManager().getSession(player.getUniqueId()) : null;
        if (session == null) return;

        MapData map = plugin.getMapManager().getMap(session.getMapName());
        if (map == null) return;

        Location to = event.getTo();
        int[] bounds = GridCalculator.getIslandBounds(map, session.getIslandIndex());
        int maxDist = plugin.getConfigManager().getMaxDistance();

        // Check if player is outside island bounds + tolerance
        boolean outOfBounds = to.getBlockX() < bounds[0] - maxDist
                || to.getBlockX() > bounds[3] + maxDist
                || to.getBlockZ() < bounds[2] - maxDist
                || to.getBlockZ() > bounds[5] + maxDist;

        // Check void (Y below island floor - tolerance)
        boolean inVoid = to.getBlockY() < bounds[1] - maxDist;

        if (outOfBounds || inVoid) {
            plugin.getGameplayManager().onFall(player);
        }
    }

    /**
     * Check if the player is interacting on their own assigned island.
     */
    private boolean isOnOwnIsland(Player player, Location blockLoc) {
        MapManager mm = plugin.getMapManager();

        for (MapData map : mm.getAllMaps()) {
            if (!map.isEnabled()) continue;
            if (!blockLoc.getWorld().getName().equals(map.getWorldName())) continue;

            int blockIsland = GridCalculator.getIslandIndex(map, blockLoc);
            if (blockIsland < 0) continue;

            // Found which island this block is on. Check if the player owns it.
            int playerIsland = mm.getPlayerIsland(map.getName(), player.getUniqueId());
            return blockIsland == playerIsland;
        }

        // Block is not within any map's island grid. Allow if admin, deny otherwise.
        return player.hasPermission("fastbuilder.admin");
    }
}
