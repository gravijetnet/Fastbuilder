package net.gravijet.fastbuilder.listener;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.map.SetupSession;
import net.gravijet.fastbuilder.util.ColorUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Handles Blaze Rod interactions during admin map setup.
 */
public class SetupListener implements Listener {

    private final FastBuilder plugin;

    public SetupListener(FastBuilder plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getItemInHand();

        // Only process blaze rod clicks
        if (item == null || item.getType() != Material.BLAZE_ROD) return;

        SetupSession session = plugin.getMapManager().getSetupSession(player.getUniqueId());
        if (session == null) return;

        event.setCancelled(true);

        Action action = event.getAction();
        Block clicked = event.getClickedBlock();

        switch (session.getState()) {
            case SELECTING_ISLAND:
                handleIslandSelection(player, session, action, clicked);
                break;
            case SELECTING_SPAWN:
                handleSpawnSelection(player, session, action);
                break;
            case SELECTING_FINISH:
                handleFinishSelection(player, session, action, clicked);
                break;
            default:
                break;
        }
    }

    private void handleIslandSelection(Player player, SetupSession session, Action action, Block clicked) {
        if (clicked == null) return;

        Location loc = clicked.getLocation();

        if (action == Action.LEFT_CLICK_BLOCK) {
            session.setIslandPos1(loc);
            String raw = plugin.getConfigManager().getAdminMessage("setup-pos1");
            raw = raw.replace("%x%", String.valueOf(loc.getBlockX()))
                    .replace("%y%", String.valueOf(loc.getBlockY()))
                    .replace("%z%", String.valueOf(loc.getBlockZ()));
            player.sendMessage(ColorUtil.translate(raw));
        } else if (action == Action.RIGHT_CLICK_BLOCK) {
            session.setIslandPos2(loc);
            String raw = plugin.getConfigManager().getAdminMessage("setup-pos2");
            raw = raw.replace("%x%", String.valueOf(loc.getBlockX()))
                    .replace("%y%", String.valueOf(loc.getBlockY()))
                    .replace("%z%", String.valueOf(loc.getBlockZ()));
            player.sendMessage(ColorUtil.translate(raw));
        }

        // Notify if both positions are set
        if (session.getIslandPos1() != null && session.getIslandPos2() != null) {
            String msg = plugin.getConfigManager().getAdminMessage("setup-select-both");
            player.sendMessage(ColorUtil.translate(msg));
        }
    }

    private void handleSpawnSelection(Player player, SetupSession session, Action action) {
        // Right-click sets spawn at current player location
        if (action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR) {
            Location loc = player.getLocation();
            session.setSpawnPoint(loc);
            String raw = plugin.getConfigManager().getAdminMessage("setup-spawn-saved");
            player.sendMessage(ColorUtil.translate(raw));
        }
    }

    private void handleFinishSelection(Player player, SetupSession session, Action action, Block clicked) {
        if (clicked == null) return;

        Location loc = clicked.getLocation();

        if (action == Action.LEFT_CLICK_BLOCK) {
            session.setFinishPos1(loc);
            String raw = plugin.getConfigManager().getAdminMessage("setup-finish-pos1");
            raw = raw.replace("%x%", String.valueOf(loc.getBlockX()))
                    .replace("%y%", String.valueOf(loc.getBlockY()))
                    .replace("%z%", String.valueOf(loc.getBlockZ()));
            player.sendMessage(ColorUtil.translate(raw));
        } else if (action == Action.RIGHT_CLICK_BLOCK) {
            session.setFinishPos2(loc);
            String raw = plugin.getConfigManager().getAdminMessage("setup-finish-pos2");
            raw = raw.replace("%x%", String.valueOf(loc.getBlockX()))
                    .replace("%y%", String.valueOf(loc.getBlockY()))
                    .replace("%z%", String.valueOf(loc.getBlockZ()));
            player.sendMessage(ColorUtil.translate(raw));
        }

        // Notify if both positions are set
        if (session.getFinishPos1() != null && session.getFinishPos2() != null) {
            String msg = plugin.getConfigManager().getAdminMessage("setup-finish-ready");
            player.sendMessage(ColorUtil.translate(msg));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Clean up setup session on disconnect
        plugin.getMapManager().removeSetupSession(event.getPlayer().getUniqueId());
    }
}
