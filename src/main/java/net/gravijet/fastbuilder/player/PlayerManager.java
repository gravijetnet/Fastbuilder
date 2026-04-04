package net.gravijet.fastbuilder.player;

import net.gravijet.fastbuilder.FastBuilder;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    // Cache for global best times per map (scanned from all playerdata files)
    private final Map<String, long[]> globalBestTimesCache = new HashMap<>();
    private final Map<String, Long> globalBestTimesCacheTimestamp = new HashMap<>();
    private static final long GLOBAL_CACHE_TTL_MS = 60_000L; // 60 seconds

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
     * Return all recorded personal best times for a given map, scanned from every
     * playerdata file on disk. Results are cached for 60 seconds.
     * Used by HologramManager to compute a true global [Top x%] percentile.
     */
    public long[] getGlobalBestTimesForMap(String mapName) {
        Long cacheTime = globalBestTimesCacheTimestamp.get(mapName);
        if (cacheTime != null && System.currentTimeMillis() - cacheTime < GLOBAL_CACHE_TTL_MS) {
            long[] cached = globalBestTimesCache.get(mapName);
            if (cached != null) return cached;
        }

        List<Long> times = new ArrayList<>();
        File[] files = dataDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            // Also include online players from cache (in case they haven't been saved yet)
            for (PlayerData pd : cache.values()) {
                PlayerData.MapStats s = pd.getStats(mapName);
                if (s != null && s.hasBestTime()) times.add(s.bestTime);
            }
            for (File file : files) {
                // Skip files whose UUID is already in cache (already added above)
                String fileName = file.getName().replace(".yml", "");
                try {
                    UUID fileUuid = UUID.fromString(fileName);
                    if (cache.containsKey(fileUuid)) continue;
                } catch (IllegalArgumentException ignored) { continue; }

                try {
                    YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                    long t = config.getLong("stats." + mapName + ".best-time", -1);
                    if (t > 0) times.add(t);
                } catch (Exception ignored) {}
            }
        }

        long[] result = new long[times.size()];
        for (int i = 0; i < times.size(); i++) result[i] = times.get(i);
        globalBestTimesCache.put(mapName, result);
        globalBestTimesCacheTimestamp.put(mapName, System.currentTimeMillis());
        return result;
    }

    /** Force-expire the global best times cache for a map (call after a new PB is recorded). */
    public void invalidateGlobalBestTimesCache(String mapName) {
        globalBestTimesCacheTimestamp.remove(mapName);
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
