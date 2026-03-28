package net.gravijet.fastbuilder.util;

import org.bukkit.ChatColor;

public final class CC {

    private CC() {}

    public static String c(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    /** &c&lFastBuilder &7» &f */
    public static final String PREFIX  = c("&c&lFastBuilder &7\u00bb &f");
    /** &c&lFastBuilder &7» &c */
    public static final String ERROR   = c("&c&lFastBuilder &7\u00bb &c");
    /** &c&lFastBuilder &7» &a */
    public static final String SUCCESS = c("&c&lFastBuilder &7\u00bb &a");
}
