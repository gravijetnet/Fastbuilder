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

    /** Live in-memory cache — main-thread access only for mutations. */
    private final Map<UUID, PlayerData> cache = Collections.synchronizedMap(new HashMap<>());

    public PlayerManager(FastBuilder plugin) {
        this.plugin = plugin;
        this.provider = buildProvider();
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
            plugin.getLogger().info("[PlayerManager] Storage backend: " + type.toUpperCase());
        } catch (Exception e) {
            plugin.getLogger().severe("[PlayerManager] Failed to init '" + type
                    + "' backend — falling back to YAML. Cause: " + e.getMessage());
            p = new YamlStorageProvider(plugin, cache);
            try { p.init(); } catch (Exception ignored) {}
        }

        return p;
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

    /** Load player data asynchronously, running {@code callback} on the main thread when done. */
    public void getPlayerDataAsync(UUID uuid, String name, java.util.function.Consumer<PlayerData> callback) {
        PlayerData cached = cache.get(uuid);
        if (cached != null) {
            cached.setName(name);
            callback.accept(cached);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            PlayerData data = provider.loadPlayerData(uuid, name);
            Bukkit.getScheduler().runTask(plugin, () -> {
                cache.put(uuid, data);
                callback.accept(data);
            });
        });
    }

    /** Get cached data without loading. Returns null if not in cache. */
    public PlayerData getCachedData(UUID uuid) {
        return cache.get(uuid);
    }

    /** Save a single player's data asynchronously. */
    public void savePlayerData(UUID uuid) {
        PlayerData data = cache.get(uuid);
        if (data == null) return;
        final PlayerData snapshot = data; // reference is safe — only fields mutated on main thread
        Bukkit.getScheduler().runTaskAsynchronously(plugin,
                () -> provider.savePlayerData(snapshot));
    }

    /** Save all cached player data synchronously (called on plugin disable). */
    public void saveAll() {
        for (PlayerData data : cache.values()) {
            provider.savePlayerData(data);
        }
    }

    /**
     * Save the player's data and remove from cache (on player quit).
     * Save is asynchronous.
     */
    public void unload(UUID uuid) {
        savePlayerData(uuid);
        cache.remove(uuid);
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

    // -------------------------------------------------------------------------
    // Offline lookup (no cache)
    // -------------------------------------------------------------------------

    /**
     * Load a player's data from the backend by UUID without caching.
     * Returns null if no record exists (provider will return empty-name data when
     * the backend has no entry for this UUID — we treat that as "not found").
     */
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
        saveAll();
        provider.shutdown();
    }
}
