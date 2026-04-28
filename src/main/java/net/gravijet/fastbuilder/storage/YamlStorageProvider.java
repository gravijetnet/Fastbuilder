package net.gravijet.fastbuilder.storage;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.player.PlayerData;
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
 * YAML flat-file storage provider.
 * One {@code <uuid>.yml} file per player inside {@code plugins/FastBuilder/playerdata/}.
 *
 * Global best-time scans are cached for {@value #CACHE_TTL_MS} ms; the cache
 * is invalidated eagerly when a new personal best is recorded.
 */
public class YamlStorageProvider implements StorageProvider {

    private static final long CACHE_TTL_MS = 60_000L;

    private final FastBuilder plugin;
    private final File dataDir;

    /** Map of mapName → sorted array of best times (ms). */
    private final Map<String, long[]>  bestTimesCache     = new HashMap<>();
    private final Map<String, Long>    bestTimesCacheTime = new HashMap<>();

    /** In-memory player cache — managed by PlayerManager (not this class). */
    private final Map<UUID, PlayerData> liveCache;

    public YamlStorageProvider(FastBuilder plugin, Map<UUID, PlayerData> liveCache) {
        this.plugin    = plugin;
        this.liveCache = liveCache;
        this.dataDir   = new File(plugin.getDataFolder(), "playerdata");
    }

    @Override
    public void init() {
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
    }

    @Override
    public PlayerData loadPlayerData(UUID uuid, String name) {
        File file = new File(dataDir, uuid.toString() + ".yml");
        PlayerData data = new PlayerData(uuid, name);
        if (file.exists()) {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            data.loadFrom(cfg);
        }
        return data;
    }

    @Override
    public synchronized void savePlayerData(PlayerData data) {
        File file = new File(dataDir, data.getUuid().toString() + ".yml");
        YamlConfiguration cfg = new YamlConfiguration();
        data.saveTo(cfg);
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "[YAML] Failed to save player data: " + data.getUuid(), e);
        }
    }

    @Override
    public synchronized long[] getGlobalBestTimesForMap(String mapName) {
        Long cacheTime = bestTimesCacheTime.get(mapName);
        if (cacheTime != null && System.currentTimeMillis() - cacheTime < CACHE_TTL_MS) {
            long[] cached = bestTimesCache.get(mapName);
            if (cached != null) return cached;
        }

        List<Long> times = new ArrayList<>();

        // Include currently-online players from the live cache first
        synchronized (liveCache) {
            for (PlayerData pd : liveCache.values()) {
                PlayerData.MapStats s = pd.getStats(mapName);
                if (s != null && s.hasBestTime()) times.add(s.bestTime);
            }
        }

        // Scan all on-disk files, skipping UUIDs already in the live cache
        File[] files = dataDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String fileName = file.getName().replace(".yml", "");
                try {
                    UUID uuid = UUID.fromString(fileName);
                    synchronized (liveCache) {
                        if (liveCache.containsKey(uuid)) continue; // already counted above
                    }
                    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
                    long t = cfg.getLong("stats." + mapName + ".best-time", -1);
                    if (t > 0) times.add(t);
                } catch (IllegalArgumentException ignored) {
                    // Not a UUID file — skip
                } catch (Exception e) {
                    plugin.getLogger().warning("[YAML] Could not read playerdata file: " + fileName);
                }
            }
        }

        long[] result = new long[times.size()];
        for (int i = 0; i < times.size(); i++) result[i] = times.get(i);
        bestTimesCache.put(mapName, result);
        bestTimesCacheTime.put(mapName, System.currentTimeMillis());
        return result;
    }

    @Override
    public synchronized void invalidateBestTimesCache(String mapName) {
        bestTimesCacheTime.remove(mapName);
    }

    @Override
    public synchronized java.util.List<java.util.Map.Entry<String, Long>> getTopPlayerTimesForMap(
            String mapName, int limit) {

        // (name → best time) — deduplicates if a player appears in both cache and on disk
        java.util.Map<String, Long> best = new java.util.LinkedHashMap<>();

        // Online players first (live cache is authoritative)
        synchronized (liveCache) {
            for (PlayerData pd : liveCache.values()) {
                PlayerData.MapStats s = pd.getStats(mapName);
                if (s != null && s.hasBestTime()) {
                    best.merge(pd.getName(), s.bestTime, Math::min);
                }
            }
        }

        // Scan all on-disk files, skipping UUIDs already in the live cache
        File[] files = dataDir.listFiles((dir, n) -> n.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String fileName = file.getName().replace(".yml", "");
                try {
                    UUID uuid = UUID.fromString(fileName);
                    synchronized (liveCache) {
                        if (liveCache.containsKey(uuid)) continue;
                    }
                    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
                    long t = cfg.getLong("stats." + mapName + ".best-time", -1);
                    if (t > 0) {
                        String playerName = cfg.getString("name", fileName);
                        best.merge(playerName, t, Math::min);
                    }
                } catch (IllegalArgumentException ignored) {
                } catch (Exception e) {
                    plugin.getLogger().warning("[YAML] Could not read playerdata file: " + fileName);
                }
            }
        }

        List<java.util.Map.Entry<String, Long>> sorted = new ArrayList<>(best.entrySet());
        sorted.sort(java.util.Comparator.comparingLong(java.util.Map.Entry::getValue));
        return sorted.subList(0, Math.min(limit, sorted.size()));
    }

    @Override
    public synchronized java.util.List<java.util.Map.Entry<String, Integer>> getTopInfiniteDistancesForMap(
            String mapName, int limit) {

        // Values: long[] { distance, time } — distance desc, time asc on tie
        java.util.Map<String, long[]> best = new java.util.LinkedHashMap<>();

        // Online players first (live cache is authoritative)
        synchronized (liveCache) {
            for (PlayerData pd : liveCache.values()) {
                int dist = pd.getInfiniteDistance(mapName);
                if (dist > 0) {
                    long t = pd.getInfiniteDistanceTime(mapName);
                    long[] cur = best.get(pd.getName());
                    if (cur == null || dist > cur[0] || (dist == cur[0] && t < cur[1]))
                        best.put(pd.getName(), new long[]{dist, t > 0 ? t : Long.MAX_VALUE});
                }
            }
        }

        // Scan on-disk files, skip UUIDs already in the live cache
        File[] files = dataDir.listFiles((dir, n) -> n.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String fileName = file.getName().replace(".yml", "");
                try {
                    UUID uuid = UUID.fromString(fileName);
                    synchronized (liveCache) {
                        if (liveCache.containsKey(uuid)) continue;
                    }
                    YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
                    int dist = cfg.getInt("infinite-distances." + mapName.toLowerCase(), 0);
                    if (dist > 0) {
                        String playerName = cfg.getString("name", fileName);
                        long t = cfg.getLong("infinite-distance-times." + mapName.toLowerCase(), 0);
                        long[] cur = best.get(playerName);
                        if (cur == null || dist > cur[0] || (dist == cur[0] && t > 0 && t < cur[1]))
                            best.put(playerName, new long[]{dist, t > 0 ? t : Long.MAX_VALUE});
                    }
                } catch (IllegalArgumentException ignored) {
                } catch (Exception e) {
                    plugin.getLogger().warning("[YAML] Could not read playerdata file: " + fileName);
                }
            }
        }

        List<java.util.Map.Entry<String, long[]>> sorted = new ArrayList<>(best.entrySet());
        sorted.sort((a, b) -> {
            int cmp = Long.compare(b.getValue()[0], a.getValue()[0]); // distance desc
            if (cmp != 0) return cmp;
            return Long.compare(a.getValue()[1], b.getValue()[1]); // time asc
        });
        List<java.util.Map.Entry<String, Integer>> result = new ArrayList<>();
        for (java.util.Map.Entry<String, long[]> e : sorted.subList(0, Math.min(limit, sorted.size()))) {
            result.add(new java.util.AbstractMap.SimpleEntry<>(e.getKey(), (int) e.getValue()[0]));
        }
        return result;
    }

    @Override
    public void shutdown() {
        // No persistent connections to close
    }
}
