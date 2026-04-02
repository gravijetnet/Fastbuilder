package net.gravijet.fastbuilder.config;

import net.gravijet.fastbuilder.FastBuilder;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
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

    public double getCoinsPerSecond() {
        return mainConfig.getDouble("coins-per-second", 0.5);
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

    public Map<Integer, String> getScoreboardLines() {
        Map<Integer, String> lines = new TreeMap<>();
        if (mainConfig.isConfigurationSection("scoreboard.lines")) {
            for (String key : mainConfig.getConfigurationSection("scoreboard.lines").getKeys(false)) {
                try {
                    int slot = Integer.parseInt(key);
                    lines.put(slot, mainConfig.getString("scoreboard.lines." + key));
                } catch (NumberFormatException ignored) {}
            }
        }
        return lines;
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

    public boolean isBungeeEnabled() {
        return mainConfig.getBoolean("bungee.enabled", false);
    }

    public String getLobbyServer() {
        return mainConfig.getString("bungee.lobby-server", "lobby");
    }

    // --- Messages Config Accessors ---

    public String getActionBar() {
        return messagesConfig.getString("action-bar", "");
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

    // --- GUI Config Accessors ---

    public FileConfiguration getGuisConfig() {
        return guisConfig;
    }

    // --- Items Config Accessors ---

    public FileConfiguration getItemsConfig() {
        return itemsConfig;
    }
}
