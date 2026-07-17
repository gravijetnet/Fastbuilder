package net.gravijet.fastbuilder.player;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.storage.MySqlStorageProvider;
import net.gravijet.fastbuilder.storage.SqliteStorageProvider;
import net.gravijet.fastbuilder.storage.StorageProvider;
import net.gravijet.fastbuilder.storage.YamlStorageProvider;
import org.bukkit.Bukkit;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Central player-data layer.
 *
 * Manages the in-memory cache and delegates all persistence to the configured
 * {@link StorageProvider}. The backend is chosen from {@code config.yml}
 * under {@code storage.type}: {@code yaml} (default), {@code sqlite}, or
 * {@code mysql}.
 *
 * <p>All disk/database I/O is dispatched to async threads; the cache is always
 * mutated on the calling thread (safe as long as callers use the Bukkit main
 * thread for mutations).
 */
public class PlayerManager {

    private final FastBuilder plugin;
    private final StorageProvider provider;

    /**
     * The backend actually in use — not necessarily what config.yml asked for, since a
     * failed init falls back to YAML. The migrator relies on this being the truth.
     */
    private String activeType;

    /** Task id of the periodic autosave, or -1 when it is not running. */
    private int autoSaveTask = -1;

    /** Live in-memory cache — main-thread access only for mutations. */
    private final Map<UUID, PlayerData> cache = Collections.synchronizedMap(new HashMap<>());

    public PlayerManager(FastBuilder plugin) {
        this.plugin = plugin;
        this.provider = buildProvider();
        startAutoSave();
    }

    /** The storage backend currently in use: "yaml", "sqlite" or "mysql". */
    public String getActiveStorageType() {
        return activeType;
    }

    /** The live storage provider. Exposed for {@link StorageProvider}-level tooling (migration). */
    public StorageProvider getProvider() {
        return provider;
    }

    // -------------------------------------------------------------------------
    // Provider bootstrap
    // -------------------------------------------------------------------------

    private StorageProvider buildProvider() {
        String type = plugin.getConfigManager().getStorageType();
        StorageProvider p;

        switch (type.toLowerCase()) {
            case "sqlite":
                p = new SqliteStorageProvider(plugin);
                break;
            case "mysql":
                p = new MySqlStorageProvider(plugin);
                break;
            default:
                p = new YamlStorageProvider(plugin, cache);
                break;
        }

        try {
            p.init();
            activeType = type.toLowerCase();
            // Normalise anything unrecognised: the switch above already routed it to YAML.
            if (!activeType.equals("sqlite") && !activeType.equals("mysql")) activeType = "yaml";
            plugin.getLogger().info("[PlayerManager] Storage backend: " + activeType.toUpperCase());
            return p;
        } catch (Exception e) {
            // A silent fallback here means the server keeps running happily while every write
            // goes to YAML files and the configured database stays empty. Make it impossible
            // to miss, and log the full cause rather than just getMessage().
            java.util.logging.Logger log = plugin.getLogger();
            log.severe("###############################################################");
            log.severe("#  FastBuilder: the '" + type.toUpperCase() + "' storage backend FAILED to start.");
            log.severe("#");
            log.severe("#  Player data is now being written to YAML files instead —");
            log.severe("#  your database will stay EMPTY until the error below is fixed.");
            log.severe("#");
            log.severe("#  Fix the cause, then restart the server.");
            log.severe("###############################################################");
            log.log(java.util.logging.Level.SEVERE, "[PlayerManager] Backend init failed:", e);

            StorageProvider fallback = new YamlStorageProvider(plugin, cache);
            try {
                fallback.init();
            } catch (Exception fatal) {
                log.log(java.util.logging.Level.SEVERE,
                        "[PlayerManager] YAML fallback also failed to init:", fatal);
            }
            activeType = "yaml";
            return fallback;
        }
    }

    // -------------------------------------------------------------------------
    // Periodic autosave
    // -------------------------------------------------------------------------

    /**
     * Flush every cached player to storage on a fixed interval, so a crash costs at most
     * one interval of progress instead of a whole session. Event-driven saves (quit, run
     * finish, purchases) still happen as before — this is a safety net underneath them.
     *
     * <p>The whole sweep runs on ONE async thread and saves players one after another.
     * Firing a task per player would hand dozens of writes to the scheduler at once and
     * exhaust the 5-connection MySQL pool for no benefit.
     */
    private void startAutoSave() {
        int seconds = plugin.getConfigManager().getAutoSaveIntervalSeconds();
        if (seconds <= 0) {
            plugin.getLogger().info("[PlayerManager] Periodic autosave disabled (storage.auto-save-interval-seconds: 0)");
            return;
        }
        long ticks = seconds * 20L;
        autoSaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            java.util.List<PlayerData> snapshot;
            synchronized (cache) {
                if (cache.isEmpty()) return;
                snapshot = new java.util.ArrayList<>(cache.values());
            }
            long start = System.currentTimeMillis();
            int saved = 0;
            for (PlayerData data : snapshot) {
                try {
                    provider.savePlayerData(data);
                    saved++;
                } catch (Exception e) {
                    plugin.getLogger().log(java.util.logging.Level.WARNING,
                            "[PlayerManager] Autosave failed for " + data.getUuid(), e);
                }
            }
            long took = System.currentTimeMillis() - start;
            // Only worth a line when it is slow enough to be worth knowing about.
            if (took > 1000) {
                plugin.getLogger().info("[PlayerManager] Autosave: " + saved
                        + " players in " + took + " ms");
            }
        }, ticks, ticks).getTaskId();

        plugin.getLogger().info("[PlayerManager] Periodic autosave every " + seconds + "s");
    }

    /** Stop the autosave sweep. Called on shutdown before the final saveAll(). */
    private void stopAutoSave() {
        if (autoSaveTask != -1) {
            Bukkit.getScheduler().cancelTask(autoSaveTask);
            autoSaveTask = -1;
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Get or load player data. Always returns a non-null object.
     * If the data is not in cache, loads synchronously (call from main thread or
     * accept the brief I/O block).
     */
    public PlayerData getPlayerData(UUID uuid, String name) {
        // Synchronized block makes the check-then-act atomic under the map's own lock
        synchronized (cache) {
            PlayerData data = cache.get(uuid);
            if (data != null) {
                data.setName(name); // keep name fresh
                return data;
            }
            // Load (potentially blocking — call only from async join handler or early startup)
            data = provider.loadPlayerData(uuid, name);
            cache.put(uuid, data);
            return data;
        }
    }

    /** Load player data asynchronously, running {@code callback} on the main thread when done. */
    public void getPlayerDataAsync(UUID uuid, String name, java.util.function.Consumer<PlayerData> callback) {
        synchronized (cache) {
            PlayerData cached = cache.get(uuid);
            if (cached != null) {
                cached.setName(name);
                callback.accept(cached);
                return;
            }
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            PlayerData data = provider.loadPlayerData(uuid, name);
            Bukkit.getScheduler().runTask(plugin, () -> {
                // Re-check under lock in case another load completed while we were in async
                synchronized (cache) {
                    if (!cache.containsKey(uuid)) {
                        cache.put(uuid, data);
                    }
                }
                callback.accept(cache.get(uuid));
            });
        });
    }

    /** Get cached data without loading. Returns null if not in cache. */
    public PlayerData getCachedData(UUID uuid) {
        return cache.get(uuid);
    }

    /** Save a single player's data asynchronously. Must be called from the main thread. */
    public void savePlayerData(UUID uuid) {
        PlayerData data = cache.get(uuid);
        if (data == null) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin,
                () -> provider.savePlayerData(data));
    }

    /** Save all cached player data synchronously (called on plugin disable). */
    public void saveAll() {
        // Snapshot under the map lock — async unload saves may still mutate the cache
        java.util.List<PlayerData> snapshot;
        synchronized (cache) {
            snapshot = new java.util.ArrayList<>(cache.values());
        }
        for (PlayerData data : snapshot) {
            provider.savePlayerData(data);
        }
    }

    /**
     * Save the player's data and remove from cache (on player quit).
     *
     * <p>The save runs async, and the cache entry is kept until that write
     * completes — so a quick quit→rejoin keeps using the live (correct)
     * object instead of loading a pre-save snapshot from disk. The post-save
     * eviction only happens if the player is still offline, so a rejoined
     * player is never evicted mid-session.</p>
     */
    public void unload(UUID uuid) {
        final PlayerData data = cache.get(uuid);
        if (data == null) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            provider.savePlayerData(data);
            Bukkit.getScheduler().runTask(plugin, () -> {
                // Only evict the exact object this save belongs to — if the player
                // rejoined and a newer PlayerData replaced it, leave that one alone.
                if (Bukkit.getPlayer(uuid) == null && cache.get(uuid) == data) {
                    cache.remove(uuid);
                }
            });
        });
    }

    // -------------------------------------------------------------------------
    // Global best times (delegated to provider)
    // -------------------------------------------------------------------------

    /**
     * Return all recorded personal-best times for {@code mapName} globally.
     * Result is cached; see {@link StorageProvider#getGlobalBestTimesForMap}.
     */
    public long[] getGlobalBestTimesForMap(String mapName) {
        return provider.getGlobalBestTimesForMap(mapName);
    }

    /**
     * Force-expire the global best-times cache for {@code mapName}.
     * Call after a new personal best is recorded so percentile updates instantly.
     */
    public void invalidateGlobalBestTimesCache(String mapName) {
        provider.invalidateBestTimesCache(mapName);
    }

    /**
     * Return the top {@code limit} players for {@code mapName} sorted by personal-best
     * time ascending (fastest first).  Safe to call from an async thread.
     */
    public java.util.List<java.util.Map.Entry<String, Long>> getTopPlayerTimesForMap(
            String mapName, int limit) {
        return provider.getTopPlayerTimesForMap(mapName, limit);
    }

    public java.util.List<java.util.Map.Entry<String, long[]>> getTopInfiniteDistancesForMap(
            String mapName, int limit) {
        return provider.getTopInfiniteDistancesForMap(mapName, limit);
    }

    // -------------------------------------------------------------------------
    // Offline lookup (no cache)
    // -------------------------------------------------------------------------

    /**
     * Load a player's data from the backend by UUID without caching.
     * Returns null if no record exists (provider will return empty-name data when
     * the backend has no entry for this UUID — we treat that as "not found").
     */
    /**
     * Look up the UUID for a player by name.
     * Checks online players first, then falls back to Bukkit's offline-player cache.
     * Returns null when no UUID can be determined.
     */
    public UUID getUuidForPlayerName(String name) {
        for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().equalsIgnoreCase(name)) return p.getUniqueId();
        }
        // Scan cache by name (iteration over a synchronizedMap needs the map lock)
        synchronized (cache) {
            for (Map.Entry<UUID, PlayerData> entry : cache.entrySet()) {
                if (name.equalsIgnoreCase(entry.getValue().getName())) return entry.getKey();
            }
        }
        // Fall back to Bukkit's offline-player registry — only reliable on online-mode servers.
        // On offline-mode servers getOfflinePlayer(name) generates a fake UUID that may not match
        // any real player record, so only use it if the player has genuinely played before.
        if (Bukkit.getOnlineMode()) {
            @SuppressWarnings("deprecation")
            org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(name);
            if (op.hasPlayedBefore()) return op.getUniqueId();
        }
        return null;
    }

    public PlayerData loadOfflineData(UUID uuid) {
        // First check live cache
        PlayerData cached = cache.get(uuid);
        if (cached != null) return cached;

        PlayerData data = provider.loadPlayerData(uuid, "");
        // All providers return a fresh PlayerData with name="" when no record exists
        return data.getName().isEmpty() ? null : data;
    }

    // -------------------------------------------------------------------------
    // Shutdown
    // -------------------------------------------------------------------------

    public void shutdown() {
        stopAutoSave();
        saveAll();
        provider.shutdown();
    }
}
