package net.gravijet.fastbuilder.player;

import net.gravijet.fastbuilder.FastBuilder;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Manages player data loading, caching, and persistence.
 * Player data files are stored in plugins/FastBuilder/playerdata/<uuid>.yml
 */
public class PlayerManager {

    private final FastBuilder plugin;
    private final File dataDir;
    private final Map<UUID, PlayerData> cache = new HashMap<>();

    public PlayerManager(FastBuilder plugin) {
        this.plugin = plugin;
        this.dataDir = new File(plugin.getDataFolder(), "playerdata");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
    }

    /**
     * Get or load player data. Creates new data if none exists.
     */
    public PlayerData getPlayerData(UUID uuid, String name) {
        PlayerData data = cache.get(uuid);
        if (data != null) {
            data.setName(name);
            return data;
        }

        data = loadPlayerData(uuid, name);
        cache.put(uuid, data);
        return data;
    }

    /**
     * Get cached player data (no disk load).
     */
    public PlayerData getCachedData(UUID uuid) {
        return cache.get(uuid);
    }

    /**
     * Load player data from disk, or create new.
     */
    private PlayerData loadPlayerData(UUID uuid, String name) {
        File file = new File(dataDir, uuid.toString() + ".yml");
        PlayerData data = new PlayerData(uuid, name);

        if (file.exists()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            data.loadFrom(config);
        }

        return data;
    }

    /**
     * Save a single player's data to disk.
     */
    public void savePlayerData(UUID uuid) {
        PlayerData data = cache.get(uuid);
        if (data == null) return;

        File file = new File(dataDir, uuid.toString() + ".yml");
        YamlConfiguration config = new YamlConfiguration();
        data.saveTo(config);

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save player data: " + uuid, e);
        }
    }

    /**
     * Save all cached player data.
     */
    public void saveAll() {
        for (UUID uuid : cache.keySet()) {
            savePlayerData(uuid);
        }
    }

    /**
     * Unload a player from cache (typically on quit).
     */
    public void unload(UUID uuid) {
        savePlayerData(uuid);
        cache.remove(uuid);
    }

    /**
     * Load player data from disk by UUID only (for offline lookups).
     * Does not cache the result.
     */
    public PlayerData loadOfflineData(UUID uuid) {
        File file = new File(dataDir, uuid.toString() + ".yml");
        if (!file.exists()) return null;

        PlayerData data = new PlayerData(uuid, "");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        data.loadFrom(config);
        return data;
    }
}
