package net.gravijet.fastbuilder.listener;

import net.gravijet.fastbuilder.FastBuilder;
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
import org.bukkit.event.player.PlayerInteractEvent;

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
