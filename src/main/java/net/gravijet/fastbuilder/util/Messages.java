package net.gravijet.fastbuilder.util;

import net.gravijet.fastbuilder.FastBuilder;
import org.bukkit.command.CommandSender;

/**
 * Central helper for resolving messages from messages.yml and sending them to players.
 * Keeps the prefix/translate/replace boilerplate out of caller sites and gives every
 * message a single source of truth.
 *
 * Lookup order for {@link #get(String)}:
 *   1. messages.&lt;key&gt;
 *   2. admin.&lt;key&gt;
 *   3. booster.&lt;key&gt;
 *   4. {@code fallback} if none match (or a clear "[missing-key]" if no fallback given)
 *
 * Placeholders: %prefix% is always replaced; extra placeholders are passed as
 * alternating key/value varargs to {@link #send(CommandSender, String, String...)}.
 */
public final class Messages {

    private Messages() {}

    /**
     * Resolve a message by key. Returns the raw template (no prefix substitution).
     * Falls back to a "[missing:key]" marker when the key is absent everywhere.
     */
    public static String get(String key) {
        return get(key, null);
    }

    public static String get(String key, String fallback) {
        net.gravijet.fastbuilder.config.ConfigManager cm = FastBuilder.getInstance().getConfigManager();
        String raw = cm.getMessage(key);
        if (raw == null || raw.isEmpty()) raw = cm.getAdminMessage(key);
        if (raw == null || raw.isEmpty()) raw = cm.getBoosterMessage(key);
        if (raw == null || raw.isEmpty()) {
            return fallback != null ? fallback : "&c[missing:" + key + "]";
        }
        return raw;
    }

    /**
     * Send a message by key, with optional key/value placeholder pairs.
     * Example: {@code Messages.send(player, "coins-added", "amount", "100", "player", "Steve");}
     */
    public static void send(CommandSender to, String key, String... placeholders) {
        sendRaw(to, get(key), placeholders);
    }

    /**
     * Send a message by key with a custom fallback if the key is missing.
     */
    public static void sendOrFallback(CommandSender to, String key, String fallback, String... placeholders) {
        sendRaw(to, get(key, fallback), placeholders);
    }

    /**
     * Send a raw template (already resolved) with placeholder substitution.
     */
    public static void sendRaw(CommandSender to, String template, String... placeholders) {
        if (to == null || template == null || template.isEmpty()) return;
        String prefix = FastBuilder.getInstance().getConfigManager().getPrefix();
        String out = template.replace("%prefix%", prefix);
        if (placeholders != null) {
            for (int i = 0; i + 1 < placeholders.length; i += 2) {
                out = out.replace("%" + placeholders[i] + "%", placeholders[i + 1]);
            }
        }
        to.sendMessage(ColorUtil.translate(out));
    }
}
