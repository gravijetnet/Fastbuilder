package net.gravijet.fastbuilder.command;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.util.ColorUtil;
import org.bukkit.entity.Player;

/**
 * Shared message utilities for MapCommand handler classes.
 */
class MapCommandMessages {

    private final FastBuilder plugin;

    MapCommandMessages(FastBuilder plugin) {
        this.plugin = plugin;
    }

    void msg(Player player, String text) {
        if (text == null || text.isEmpty()) return;
        // Resolve %prefix% here so callers passing a raw messages.yml template
        // (e.g. getMessage("no-permission")) never leak a literal "%prefix%".
        player.sendMessage(ColorUtil.translate(
                text.replace("%prefix%", plugin.getConfigManager().getPrefix())));
    }

    void msgAdmin(Player player, String key) {
        String raw = plugin.getConfigManager().getAdminMessage(key);
        if (raw == null || raw.isEmpty()) raw = plugin.getConfigManager().getMessage(key);
        if (raw == null) raw = "";
        raw = raw.replace("%prefix%", plugin.getConfigManager().getPrefix());
        player.sendMessage(ColorUtil.translate(raw));
    }

    void msgAdmin(Player player, String key, String placeholder, String value) {
        String raw = plugin.getConfigManager().getAdminMessage(key);
        if (raw == null || raw.isEmpty()) raw = plugin.getConfigManager().getMessage(key);
        if (raw == null) raw = "";
        raw = raw.replace(placeholder, value);
        raw = raw.replace("%prefix%", plugin.getConfigManager().getPrefix());
        player.sendMessage(ColorUtil.translate(raw));
    }

    void msgMap(Player player, String key, String mapName) {
        String raw = plugin.getConfigManager().getMessage(key);
        if (raw == null) raw = "";
        raw = raw.replace("%map%", mapName);
        raw = raw.replace("%prefix%", plugin.getConfigManager().getPrefix());
        player.sendMessage(ColorUtil.translate(raw));
    }

    void sendClickableContinue(Player player) {
        if (!plugin.getConfigManager().isAdminHintsEnabled()) return;
        String prefix = plugin.getConfigManager().getPrefix();
        net.md_5.bungee.api.chat.TextComponent msg = new net.md_5.bungee.api.chat.TextComponent(
                ColorUtil.translate(prefix + "&7» &c/map setup continue"));
        msg.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                net.md_5.bungee.api.chat.ClickEvent.Action.SUGGEST_COMMAND,
                "/map setup continue"));
        msg.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                new net.md_5.bungee.api.chat.BaseComponent[]{
                        new net.md_5.bungee.api.chat.TextComponent(
                                ColorUtil.translate("&7Click to fill the command in your chat bar"))}));
        player.spigot().sendMessage(msg);
    }

    void sendClickableFinish(Player player) {
        if (!plugin.getConfigManager().isAdminHintsEnabled()) return;
        net.md_5.bungee.api.chat.TextComponent msg = new net.md_5.bungee.api.chat.TextComponent(
                ColorUtil.translate("&f/map setup finish <name>"));
        msg.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                net.md_5.bungee.api.chat.ClickEvent.Action.SUGGEST_COMMAND,
                "/map setup finish "));
        msg.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                new net.md_5.bungee.api.chat.BaseComponent[]{
                        new net.md_5.bungee.api.chat.TextComponent(
                                ColorUtil.translate("&7Click to fill — then type the map name and press Enter"))}));
        player.spigot().sendMessage(msg);
    }

    void sendPostCreationHints(Player player, String mapName) {
        if (!plugin.getConfigManager().isAdminHintsEnabled()) return;
        String prefix = plugin.getConfigManager().getPrefix();
        player.sendMessage(ColorUtil.translate(prefix + "&fMap &c" + mapName + " &fcreated. Next steps:"));
        sendSuggestHint(player, "&7Enable the map: ", "/map enable " + mapName);
        sendSuggestHint(player, "&7Set island count: ", "/map scale " + mapName + " 15");
        sendSuggestHint(player, "&7Test it: ", "/fb join " + mapName);
    }

    void sendSuggestHint(Player player, String label, String command) {
        net.md_5.bungee.api.chat.TextComponent line =
                new net.md_5.bungee.api.chat.TextComponent(ColorUtil.translate("  &7» " + label));
        net.md_5.bungee.api.chat.TextComponent cmd =
                new net.md_5.bungee.api.chat.TextComponent(ColorUtil.translate("&e&n" + command));
        cmd.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                net.md_5.bungee.api.chat.ClickEvent.Action.SUGGEST_COMMAND, command));
        cmd.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                new net.md_5.bungee.api.chat.BaseComponent[]{
                        new net.md_5.bungee.api.chat.TextComponent(
                                ColorUtil.translate("&7Click to fill this command in your chat bar"))}));
        line.addExtra(cmd);
        player.spigot().sendMessage(line);
    }

    void sendDeleteConfirm(Player player, String mapName) {
        net.md_5.bungee.api.chat.TextComponent line =
                new net.md_5.bungee.api.chat.TextComponent(
                        ColorUtil.translate("&7Click to confirm: "));
        net.md_5.bungee.api.chat.TextComponent btn =
                new net.md_5.bungee.api.chat.TextComponent(
                        ColorUtil.translate("&c&l[Delete " + mapName + "]"));
        btn.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND,
                "/map delete " + mapName + " confirm"));
        btn.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                new net.md_5.bungee.api.chat.BaseComponent[]{
                        new net.md_5.bungee.api.chat.TextComponent(
                                ColorUtil.translate("&cClick to permanently delete &f" + mapName))}));
        line.addExtra(btn);
        player.spigot().sendMessage(line);
    }
}
