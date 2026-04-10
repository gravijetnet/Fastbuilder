package net.gravijet.fastbuilder.config;

import net.gravijet.fastbuilder.FastBuilder;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    }

    public void reload() {
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

    public int getAutoscaleMinIslands() {
        return mainConfig.getInt("autoscale.min-islands", 15);
    }

    public int getAutoscaleThreshold() {
        return mainConfig.getInt("autoscale.scale-threshold", 80);
    }

    public int getMapSpacing() {
        return mainConfig.getInt("map-spacing", 2000);
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

    public String getTitle() {
        return messagesConfig.getString("title", "");
    }

    public String getSubtitle() {
        return messagesConfig.getString("subtitle", "");
    }

    public int getScoreboardUpdateInterval() {
        return mainConfig.getInt("scoreboard.update-interval", 20);
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

    public int getStorageMySQLPort() {
        return mainConfig.getInt("storage.mysql.port", 3306);
    }

    // -------------------------------------------------------------------------
    // Animation / reset speeds
    // -------------------------------------------------------------------------

    /** Blocks processed per tick in sequential block-clear animations. */
    public int getAnimationBlocksPerTick() {
        return mainConfig.getInt("animation.blocks-per-tick", 3);
    }

    /** Ticks between batches in sequential block-clear animations. */
    public int getAnimationTickInterval() {
        return mainConfig.getInt("animation.tick-interval", 1);
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
