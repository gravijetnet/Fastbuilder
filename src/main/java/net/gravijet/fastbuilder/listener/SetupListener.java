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
            case SELECTING_NPC:
                handleNpcSelection(player, session, action);
                break;
            case SELECTING_HOLOGRAM:
                handleHologramSelection(player, session, action);
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

        String prefix = plugin.getConfigManager().getPrefix();
        if (action == Action.LEFT_CLICK_BLOCK) {
            session.setIslandPos1(loc);
            String raw = plugin.getConfigManager().getAdminMessage("setup-pos1");
            raw = raw.replace("%x%", String.valueOf(loc.getBlockX()))
                    .replace("%y%", String.valueOf(loc.getBlockY()))
                    .replace("%z%", String.valueOf(loc.getBlockZ()))
                    .replace("%prefix%", prefix);
            player.sendMessage(ColorUtil.translate(raw));
        } else if (action == Action.RIGHT_CLICK_BLOCK) {
            session.setIslandPos2(loc);
            String raw = plugin.getConfigManager().getAdminMessage("setup-pos2");
            raw = raw.replace("%x%", String.valueOf(loc.getBlockX()))
                    .replace("%y%", String.valueOf(loc.getBlockY()))
                    .replace("%z%", String.valueOf(loc.getBlockZ()))
                    .replace("%prefix%", prefix);
            player.sendMessage(ColorUtil.translate(raw));
        }

        if (session.getIslandPos1() != null && session.getIslandPos2() != null) {
            String msg = plugin.getConfigManager().getAdminMessage("setup-select-both")
                    .replace("%prefix%", prefix);
            player.sendMessage(ColorUtil.translate(msg));
            sendClickableContinue(player);
        }
    }

    private void handleSpawnSelection(Player player, SetupSession session, Action action) {
        if (action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR) {
            Location loc = player.getLocation();
            session.setSpawnPoint(loc);
            String raw = plugin.getConfigManager().getAdminMessage("setup-spawn-saved");
            player.sendMessage(ColorUtil.translate(raw));
            if (!session.isSpawnFacingEast()) {
                player.sendMessage(ColorUtil.translate(plugin.getConfigManager().getPrefix()
                        + "&eWarning: Spawn is not facing East. Use &f/map setup continue --force-spawn-location &eto bypass."));
            }
            sendClickableContinue(player);
        }
    }

    private void handleNpcSelection(Player player, SetupSession session, Action action) {
        if (action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR) {
            Location loc = player.getLocation();
            session.setNpcPoint(loc);
            String raw = plugin.getConfigManager().getAdminMessage("setup-npc-saved");
            if (raw == null || raw.isEmpty()) raw = "&aNPC location saved at your position.";
            player.sendMessage(ColorUtil.translate(raw));
            sendClickableContinue(player);
        }
    }

    private void handleHologramSelection(Player player, SetupSession session, Action action) {
        if (action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR) {
            Location loc = player.getLocation().clone();
            loc.setY(loc.getY() + 3);
            session.setHologramPoint(loc);
            String prefix = plugin.getConfigManager().getPrefix();
            player.sendMessage(ColorUtil.translate(
                    prefix + "&fHologram location saved at &c" +
                    loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "&f."));
            sendClickableContinue(player);
        }
    }

    private void handleFinishSelection(Player player, SetupSession session, Action action, Block clicked) {
        if (clicked == null) return;
        Location loc = clicked.getLocation();

        String prefix = plugin.getConfigManager().getPrefix();
        if (action == Action.LEFT_CLICK_BLOCK) {
            session.setFinishPos1(loc);
            String raw = plugin.getConfigManager().getAdminMessage("setup-finish-pos1");
            raw = raw.replace("%x%", String.valueOf(loc.getBlockX()))
                    .replace("%y%", String.valueOf(loc.getBlockY()))
                    .replace("%z%", String.valueOf(loc.getBlockZ()))
                    .replace("%prefix%", prefix);
            player.sendMessage(ColorUtil.translate(raw));
        } else if (action == Action.RIGHT_CLICK_BLOCK) {
            session.setFinishPos2(loc);
            String raw = plugin.getConfigManager().getAdminMessage("setup-finish-pos2");
            raw = raw.replace("%x%", String.valueOf(loc.getBlockX()))
                    .replace("%y%", String.valueOf(loc.getBlockY()))
                    .replace("%z%", String.valueOf(loc.getBlockZ()))
                    .replace("%prefix%", prefix);
            player.sendMessage(ColorUtil.translate(raw));
        }

        if (session.getFinishPos1() != null && session.getFinishPos2() != null) {
            String msg = plugin.getConfigManager().getAdminMessage("setup-finish-ready")
                    .replace("%prefix%", prefix);
            player.sendMessage(ColorUtil.translate(msg));
            sendClickableFinish(player);
        }
    }

    /**
     * Send a clickable "/map setup continue" message.
     */
    private void sendClickableContinue(Player player) {
        try {
            net.md_5.bungee.api.chat.TextComponent prefix = new net.md_5.bungee.api.chat.TextComponent(
                    net.md_5.bungee.api.ChatColor.GRAY + "When ready: ");
            net.md_5.bungee.api.chat.TextComponent btn = new net.md_5.bungee.api.chat.TextComponent(
                    net.md_5.bungee.api.ChatColor.RED + "" + net.md_5.bungee.api.ChatColor.BOLD + "[/map setup continue]");
            btn.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                    net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/map setup continue"));
            btn.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                    net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                    new net.md_5.bungee.api.chat.BaseComponent[]{
                            new net.md_5.bungee.api.chat.TextComponent(
                                    net.md_5.bungee.api.ChatColor.YELLOW + "Click to advance to the next step")}));
            prefix.addExtra(btn);
            player.spigot().sendMessage(prefix);
        } catch (Exception e) {
            player.sendMessage(ColorUtil.translate("&7When ready, type: &c/map setup continue"));
        }
    }

    /**
     * Send a clickable "/map setup finish" message.
     */
    private void sendClickableFinish(Player player) {
        try {
            net.md_5.bungee.api.chat.TextComponent prefix = new net.md_5.bungee.api.chat.TextComponent(
                    net.md_5.bungee.api.ChatColor.GRAY + "When ready: ");
            net.md_5.bungee.api.chat.TextComponent btn = new net.md_5.bungee.api.chat.TextComponent(
                    net.md_5.bungee.api.ChatColor.RED + "" + net.md_5.bungee.api.ChatColor.BOLD + "[/map setup finish]");
            btn.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                    net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/map setup finish"));
            btn.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                    net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                    new net.md_5.bungee.api.chat.BaseComponent[]{
                            new net.md_5.bungee.api.chat.TextComponent(
                                    net.md_5.bungee.api.ChatColor.YELLOW + "Click to save the island template")}));
            prefix.addExtra(btn);
            player.spigot().sendMessage(prefix);
        } catch (Exception e) {
            player.sendMessage(ColorUtil.translate("&7When ready, type: &c/map setup finish"));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getMapManager().removeSetupSession(event.getPlayer().getUniqueId());
    }
}
