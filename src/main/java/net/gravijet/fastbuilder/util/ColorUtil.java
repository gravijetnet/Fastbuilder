package net.gravijet.fastbuilder.util;

import org.bukkit.ChatColor;

public final class ColorUtil {

    private ColorUtil() {}

    public static String translate(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public static String strip(String text) {
        if (text == null) return "";
        return ChatColor.stripColor(translate(text));
    }
}
