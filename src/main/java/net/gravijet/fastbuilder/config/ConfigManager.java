package net.gravijet.fastbuilder.config;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.economy.BoosterType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Manages all plugin configuration files.
 */
public class ConfigManager {

    private final FastBuilder plugin;
    private FileConfiguration mainConfig;
    private FileConfiguration messagesConfig;
    private FileConfiguration guisConfig;
    private FileConfiguration itemsConfig;

    // -------------------------------------------------------------------------
    // Config migration — renamed keys
    // -------------------------------------------------------------------------
    // When you RENAME a config option between plugin versions, add a
    // { "old.path", "new.path" } entry to the matching table below. On the next
    // server start the user's existing value is moved to the new key automatically
    // and the old key is removed.
    //
    // Adding a brand-NEW option needs NO entry here at all — it is copied from the
    // bundled default by addMissingDefaults() on its own. Dotted paths ("a.b.c")
    // target nested keys; an entry may rename a whole section too.
    private static final String[][] CONFIG_RENAMES = {
        // Example — rename "available-blocks:" to "blocks:" :
        // { "available-blocks", "blocks" },
        { "bungee.lobby-server", "leave-item.lobby-server" },
    };
    private static final String[][] MESSAGES_RENAMES = {};
    private static final String[][] GUIS_RENAMES = {};
    private static final String[][] ITEMS_RENAMES = {};

    public ConfigManager(FastBuilder plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        // Save defaults if they don't exist
        plugin.saveDefaultConfig();
        saveDefaultResource("messages.yml");
        saveDefaultResource("guis.yml");
        saveDefaultResource("items.yml");

        // Load configs
        plugin.reloadConfig();
        mainConfig = plugin.getConfig();
        messagesConfig = loadYaml("messages.yml");
        guisConfig = loadYaml("guis.yml");
        itemsConfig = loadYaml("items.yml");

        // Bring each server-side config up to date with the bundled version:
        //   1) migrate any renamed keys (moves the user's value to the new key name)
        //   2) add any brand-new keys that the bundled default has but the file lacks
        // New options therefore appear automatically on update with no code change;
        // only *renames* need a one-line entry in the *_RENAMES tables above.
        syncConfig(mainConfig,     "config.yml",   CONFIG_RENAMES);
        syncConfig(messagesConfig, "messages.yml", MESSAGES_RENAMES);
        syncConfig(guisConfig,     "guis.yml",     GUIS_RENAMES);
        syncConfig(itemsConfig,    "items.yml",    ITEMS_RENAMES);
    }

    /**
     * Migrates renamed keys, then copies any keys present in the bundled default
     * resource but missing from the server's file, saving once if anything changed.
     */
    private void syncConfig(FileConfiguration config, String resourceName, String[][] renames) {
        File file = new File(plugin.getDataFolder(), resourceName);
        boolean changed = applyRenames(config, renames);
        changed |= addMissingDefaults(config, resourceName);
        if (changed) {
            try {
                config.save(file);
            } catch (IOException e) {
                plugin.getLogger().warning("Could not save updated " + resourceName + ": " + e.getMessage());
            }
        }
    }

    /**
     * For each {oldKey, newKey} pair: if the old key still exists, move its value to the
     * new key (only when the new key isn't already set, so a value the user maintains under
     * the new name is never clobbered) and remove the old key. Returns true if anything changed.
     */
    private boolean applyRenames(FileConfiguration config, String[][] renames) {
        boolean changed = false;
        for (String[] pair : renames) {
            String oldKey = pair[0];
            String newKey = pair[1];
            if (!config.isSet(oldKey)) continue;
            if (!config.isSet(newKey)) {
                config.set(newKey, deepCopy(config.get(oldKey)));
            }
            config.set(oldKey, null); // drop the stale key either way
            changed = true;
        }
        return changed;
    }

    /**
     * Copies every key present in the bundled default resource but missing from
     * {@code config}. Returns true if the config was modified.
     */
    private boolean addMissingDefaults(FileConfiguration config, String resourceName) {
        InputStream in = plugin.getResource(resourceName);
        if (in == null) return false;
        FileConfiguration defaults;
        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            defaults = YamlConfiguration.loadConfiguration(reader);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not read default " + resourceName + ": " + e.getMessage());
            return false;
        }
        boolean changed = false;
        for (String key : defaults.getKeys(true)) {
            if (!config.isSet(key)) {
                config.set(key, defaults.get(key));
                changed = true;
            }
        }
        return changed;
    }

    /** Deep-copies a config value so a moved subtree doesn't share references with the old key. */
    private Object deepCopy(Object value) {
        if (value instanceof ConfigurationSection) {
            Map<String, Object> copy = new LinkedHashMap<>();
            ConfigurationSection section = (ConfigurationSection) value;
            for (String k : section.getKeys(false)) {
                copy.put(k, deepCopy(section.get(k)));
            }
            return copy;
        }
        return value;
    }

    public void reload() {
        boosterTypeCache = null;
        boosterTypeMapCache = null;
        loadAll();
    }

    private void saveDefaultResource(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
    }

    private FileConfiguration loadYaml(String name) {
        File file = new File(plugin.getDataFolder(), name);
        return YamlConfiguration.loadConfiguration(file);
    }

    // --- Main Config Accessors ---

    public String getPrefix() {
        return mainConfig.getString("prefix", "&c&lFastbuilder &7» &f");
    }

    public String getDefaultMap() {
        return mainConfig.getString("default-map", "");
    }

    public int getResetStatsCost() {
        return mainConfig.getInt("reset-stats-cost", 100);
    }

    public int getCoinsPerHour() {
        return mainConfig.getInt("coins-per-hour", 150);
    }

    /** Minimum seconds between random playtime coin drops (default 120 = 2 min). */
    public int getCoinsDropIntervalMin() {
        return mainConfig.getInt("coins-drop-interval-min", 120);
    }

    /** Maximum seconds between random playtime coin drops (default 600 = 10 min). */
    public int getCoinsDropIntervalMax() {
        return mainConfig.getInt("coins-drop-interval-max", 600);
    }

    public int getFinishHeightTolerance() {
        return mainConfig.getInt("finish-height-tolerance", 5);
    }

    public long getMinValidTime() {
        return mainConfig.getLong("min-valid-time", 500);
    }

    public double getCoinsPerCompletion() {
        return mainConfig.getDouble("coins-per-completion", 10);
    }

    public int getMaxDistance() {
        return mainConfig.getInt("max-distance", 4);
    }

    public boolean isHologramsEnabled() {
        return mainConfig.getBoolean("holograms.enabled", true);
    }

    public List<String> getHologramLines() {
        return mainConfig.getStringList("holograms.lines");
    }

    public boolean isNpcsEnabled() {
        return mainConfig.getBoolean("npcs.enabled", true);
    }

    public String getNpcName() {
        return mainConfig.getString("npcs.name", "&cMap Selector");
    }

    public String getScoreboardTitle() {
        return mainConfig.getString("scoreboard.title", "&c&lFastBuilder");
    }

    /**
     * Returns scoreboard lines in top-to-bottom display order (index 0 = top).
     * Supports both the new YAML list format and the legacy numbered-key format
     * (where key=1 maps to the top line, key=N to the bottom).
     */
    public List<String> getScoreboardLines() {
        // New format: a YAML list where first entry = top, last entry = bottom
        if (mainConfig.isList("scoreboard.lines")) {
            List<String> lines = mainConfig.getStringList("scoreboard.lines");
            return lines != null ? lines : Collections.<String>emptyList();
        }
        // Legacy format: integer-keyed section; TreeMap ascending gives key=1 first = top
        if (mainConfig.isConfigurationSection("scoreboard.lines")) {
            TreeMap<Integer, String> map = new TreeMap<>();
            for (String key : mainConfig.getConfigurationSection("scoreboard.lines").getKeys(false)) {
                try {
                    int slot = Integer.parseInt(key);
                    map.put(slot, mainConfig.getString("scoreboard.lines." + key));
                } catch (NumberFormatException ignored) {}
            }
            return new ArrayList<>(map.values());
        }
        return Collections.<String>emptyList();
    }

    public boolean isIslandHoppingEnabled() {
        return mainConfig.getBoolean("island-hopping.enabled", true);
    }

    public boolean isIslandJumpSwitchEnabled() {
        return mainConfig.getBoolean("island-jump-switch.enabled", true);
    }

    public boolean isResetLockEnabled() {
        return mainConfig.getBoolean("reset-lock", true);
    }

    public int getMaxConcurrentGenerations() {
        return mainConfig.getInt("scale-generation.max-concurrent", 4);
    }

    public int getAutoscaleMinIslands() {
        return mainConfig.getInt("autoscale.min-islands", 15);
    }

    public int getAutoscaleThreshold() {
        return mainConfig.getInt("autoscale.scale-threshold", 80);
    }

    public int getMapSpacing() {
        return mainConfig.getInt("map-spacing", 2000);
    }

    public long getMaxSchematicVolume() {
        return mainConfig.getLong("max-schematic-volume", 16_000_000L);
    }

    public int getDefaultY() {
        return mainConfig.getInt("default-y", 64);
    }

    /** @deprecated Replaced by leave-item system. Use getLeaveAction() instead. */
    @Deprecated
    public boolean isBungeeEnabled() {
        return "BUNGEE".equalsIgnoreCase(getLeaveAction());
    }

    public String getLeaveAction() {
        return mainConfig.getString("leave-item.action", "SPAWN").toUpperCase();
    }

    public String getLobbyServer() {
        // Support both old bungee.lobby-server and new leave-item.lobby-server
        String v = mainConfig.getString("leave-item.lobby-server", null);
        if (v == null || v.isEmpty()) v = mainConfig.getString("bungee.lobby-server", "Lobby-1");
        return v;
    }

    public String getLeaveCommand() {
        return mainConfig.getString("leave-item.leave-command", "");
    }

    // --- Messages Config Accessors ---

    /**
     * Returns the actionbar format string.
     * Priority: config.yml actionbar.format → messages.yml action-bar
     * Placeholders: %time% / %timer%
     */
    public String getActionBar() {
        String cfg = mainConfig.getString("actionbar.format", "");
        if (cfg != null && !cfg.isEmpty()) return cfg;
        return messagesConfig.getString("action-bar", "");
    }

    /**
     * If true, the actionbar is only shown when a run is actively timing.
     */
    public boolean isActionBarOnlyWhenRunning() {
        return mainConfig.getBoolean("actionbar.only-when-running", true);
    }

    public List<String> getEndTimeMessages() {
        return messagesConfig.getStringList("end-messages.time");
    }

    public List<String> getEndPBMessages() {
        return messagesConfig.getStringList("end-messages.personal-best");
    }

    public String getMessage(String key) {
        return messagesConfig.getString("messages." + key, "");
    }

    public String getAdminMessage(String key) {
        return messagesConfig.getString("admin." + key, "");
    }

    public String getBoosterMessage(String key) {
        return messagesConfig.getString("booster." + key, "");
    }

    /** Run-finish notice templates (the {@code finish.*} section of messages.yml). */
    public String getFinishMessage(String key) {
        return messagesConfig.getString("finish." + key, "");
    }

    public String getTitle() {
        return messagesConfig.getString("title", "");
    }

    public String getSubtitle() {
        return messagesConfig.getString("subtitle", "");
    }

    public int getScoreboardUpdateInterval() {
        // Clamp to >= 1: a 0/negative period is rejected by the Bukkit scheduler
        return Math.max(1, mainConfig.getInt("scoreboard.update-interval", 20));
    }

    public boolean isFinishTouchMode() {
        return "touch".equalsIgnoreCase(mainConfig.getString("finish-trigger-mode", "zone"));
    }

    /**
     * "recorded" = use name from replay file; "current" = resolve current username.
     */
    public String getReplayPlayerNameMode() {
        return mainConfig.getString("replay.player-name-mode", "recorded");
    }

    // -------------------------------------------------------------------------
    // Storage backend
    // -------------------------------------------------------------------------

    /** Storage backend type: "yaml", "sqlite", or "mysql". */
    public String getStorageType() {
        return mainConfig.getString("storage.type", "yaml");
    }

    /** Generic MySQL config value accessor. */
    public String getStorageMySQL(String key, String def) {
        return mainConfig.getString("storage.mysql." + key, def);
    }

    /**
     * How often every cached player is flushed to storage, in seconds. 0 disables the
     * periodic sweep and leaves only the event-driven saves (quit, finish, purchase).
     */
    public int getAutoSaveIntervalSeconds() {
        Object raw = mainConfig.get("storage.auto-save-interval-seconds");
        int seconds;
        if (raw instanceof Number)      seconds = ((Number) raw).intValue();
        else if (raw instanceof String) {
            try { seconds = Integer.parseInt(((String) raw).trim()); }
            catch (NumberFormatException e) { seconds = 30; }
        } else return 30;

        if (seconds <= 0) return 0;
        // A sweep rewrites every online player's rows; below ~5s that is pure churn.
        if (seconds < 5) {
            plugin.getLogger().warning("[Config] storage.auto-save-interval-seconds: "
                    + seconds + " is too aggressive — clamped to 5");
            return 5;
        }
        return seconds;
    }

    /**
     * Whether to require an encrypted MySQL connection. Accepts a real boolean or a
     * quoted string, so neither {@code use-ssl: true} nor {@code use-ssl: "true"} is
     * silently read as false.
     */
    public boolean isStorageMySQLSSL() {
        Object raw = mainConfig.get("storage.mysql.use-ssl");
        if (raw instanceof Boolean) return (Boolean) raw;
        if (raw instanceof String)  return Boolean.parseBoolean(((String) raw).trim());
        return false;
    }

    /**
     * MySQL port. Accepts a plain number or a quoted string — Bukkit's getInt() silently
     * returns the default for a quoted value, which would send everyone to 3306 no matter
     * what the file says.
     */
    public int getStorageMySQLPort() {
        Object raw = mainConfig.get("storage.mysql.port");
        if (raw instanceof Number) return ((Number) raw).intValue();
        if (raw instanceof String) {
            try {
                return Integer.parseInt(((String) raw).trim());
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("[Config] storage.mysql.port is not a number: '"
                        + raw + "' — falling back to 3306");
            }
        }
        return 3306;
    }

    // -------------------------------------------------------------------------
    // Animation / reset speeds
    // -------------------------------------------------------------------------

    /** Blocks processed per tick in sequential block-clear animations. */
    public int getAnimationBlocksPerTick() {
        return Math.max(1, mainConfig.getInt("animation.blocks-per-tick", 3));
    }

    /** Ticks between batches in sequential block-clear animations. */
    public int getAnimationTickInterval() {
        return Math.max(1, mainConfig.getInt("animation.tick-interval", 1));
    }

    // -------------------------------------------------------------------------
    // Booster Types
    // -------------------------------------------------------------------------

    /** Cache so we don't re-parse YAML on every GUI open. Cleared on reload. */
    private volatile List<BoosterType> boosterTypeCache = null;
    private volatile Map<String, BoosterType> boosterTypeMapCache = null;

    /**
     * Returns all configured booster types in config order.
     * Parsed once and cached until the next {@link #reload()}.
     */
    public List<BoosterType> getBoosterTypes() {
        if (boosterTypeCache != null) return boosterTypeCache;

        List<BoosterType> list = new ArrayList<>();
        ConfigurationSection sec = mainConfig.getConfigurationSection("booster-types");
        if (sec == null) {
            boosterTypeCache = list;
            return list;
        }
        for (String id : sec.getKeys(false)) {
            ConfigurationSection entry = sec.getConfigurationSection(id);
            if (entry == null) continue;
            String name       = entry.getString("name", "&7" + id);
            String desc       = entry.getString("description", "");
            // Clamp misconfigured values: multiplier < 1 would shrink rewards,
            // duration/price <= 0 would create free or instantly-expired boosters.
            double mult       = Math.max(1.0, entry.getDouble("multiplier", 1.5));
            int    dur        = Math.max(1, entry.getInt("duration-minutes", 30));
            int    price      = Math.max(0, entry.getInt("price", 200));
            short  potionData = (short) entry.getInt("potion-data", 0);
            list.add(new BoosterType(id, name, desc, mult, dur, price, potionData));
        }
        boosterTypeCache = list;
        return list;
    }

    /**
     * Look up a single booster type by its ID key (e.g. "SMALL").
     * Returns null if not found.
     */
    public BoosterType getBoosterType(String id) {
        if (boosterTypeMapCache == null) {
            Map<String, BoosterType> map = new LinkedHashMap<>();
            for (BoosterType t : getBoosterTypes()) map.put(t.id, t);
            boosterTypeMapCache = map;
        }
        return boosterTypeMapCache.get(id);
    }

    // -------------------------------------------------------------------------
    // Coin reward settings
    // -------------------------------------------------------------------------

    /** Whether to award coins for failed runs (falls/resets). */
    public boolean isCoinsOnFailed() {
        return mainConfig.getBoolean("coins-on-failed", false);
    }

    /** Flat coin amount to award on a failed run (only used when coins-on-failed is true). */
    public int getCoinsOnFailedAmount() {
        return mainConfig.getInt("coins-on-failed-amount", 2);
    }

    /**
     * Fallback average completion time in seconds used for coin calculations until
     * enough real samples have been gathered from this server's players.
     */
    public double getCoinsFallbackAverageSeconds() {
        return mainConfig.getDouble("coins-average-fallback-seconds", 30.0);
    }

    /**
     * Minimum number of completion samples required before the server-wide average
     * replaces the fallback value in coin calculations.
     */
    public int getCoinsAverageMinSamples() {
        return mainConfig.getInt("coins-average-min-samples", 10);
    }

    // -------------------------------------------------------------------------
    // Shop visibility permissions
    // -------------------------------------------------------------------------

    /**
     * Returns true if the shop category identified by {@code key} (e.g.
     * "blocks", "pickaxes", "animations", "sounds") is visible for {@code player}.
     * If no permission is configured, the category is always visible.
     */
    public boolean isShopCategoryVisible(org.bukkit.entity.Player player, String key) {
        String perm = mainConfig.getString("shop.permissions." + key, "");
        return perm.isEmpty() || player.hasPermission(perm);
    }

    // -------------------------------------------------------------------------
    // Admin workflow
    // -------------------------------------------------------------------------

    /** Whether interactive SUGGEST_COMMAND hints should be sent after admin actions. */
    public boolean isAdminHintsEnabled() {
        return mainConfig.getBoolean("admin.hints", true);
    }

    /** Maximum replays stored per player per map. -1 = unlimited (permission overrides this). */
    public int getMaxReplaysPerMap() {
        int v = mainConfig.getInt("replay.max-replays", 20);
        return v < 0 ? Integer.MAX_VALUE : v;
    }

    /** Coins required to unlock the Infinite Blocks perk. */
    public int getInfiniteBlocksUnlockCost() {
        return mainConfig.getInt("infinite-blocks-unlock-cost", 1000);
    }

    /** X-axis offset applied to infinite-map island origins to isolate them from normal maps. */
    public int getInfiniteMapOffsetX() {
        return mainConfig.getInt("infinite-map-offset.x", 2000);
    }

    /** Z-axis offset applied to infinite-map island origins to isolate them from normal maps. */
    public int getInfiniteMapOffsetZ() {
        return mainConfig.getInt("infinite-map-offset.z", -10000);
    }

    // -------------------------------------------------------------------------
    // Custom Length — end-island platform
    // -------------------------------------------------------------------------

    /**
     * Block material string for the end-island platform (e.g. "STAINED_GLASS_PANE:5").
     */
    public String getEndPlatformMaterial() {
        return mainConfig.getString("custom-length.end-platform.material", "STAINED_GLASS_PANE:5");
    }

    /**
     * Fixed Z-depth of the end-island platform in blocks.
     * 0 or negative → span the full finish-zone Z range.
     */
    public int getEndPlatformDepth() {
        return mainConfig.getInt("custom-length.end-platform.depth", 3);
    }

    /** Minimum Y offset allowed in the Custom Length sub-menu (default -50). */
    public int getCustomLengthMinY() {
        return mainConfig.getInt("custom-length.y-min", -50);
    }

    /** Maximum Y offset allowed in the Custom Length sub-menu (default +50). */
    public int getCustomLengthMaxY() {
        return mainConfig.getInt("custom-length.y-max", 50);
    }

    // -------------------------------------------------------------------------
    // CPS hologram visibility
    // -------------------------------------------------------------------------

    /**
     * Returns "global" (default) or "private".
     * "private" means only the clicking player sees their own CPS hologram.
     */
    public String getCpsHologramVisibility() {
        return mainConfig.getString("cps-hologram.visibility", "global");
    }

    /**
     * Duration in seconds to keep the hologram visible after CPS drops to zero.
     * Configurable via cps-hologram.zero-cps-display-duration in config.yml.
     */
    public double getCpsZeroCpsDuration() {
        return mainConfig.getDouble("cps-hologram.zero-cps-display-duration", 1.0);
    }

    // -------------------------------------------------------------------------
    // GUI Config Accessors
    // -------------------------------------------------------------------------

    public FileConfiguration getGuisConfig() {
        return guisConfig;
    }

    // -------------------------------------------------------------------------
    // Items Config Accessors
    // -------------------------------------------------------------------------

    public FileConfiguration getItemsConfig() {
        return itemsConfig;
    }
}
