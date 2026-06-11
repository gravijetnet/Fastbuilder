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
                if (Bukkit.getPlayer(uuid) == null) {
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
        saveAll();
        provider.shutdown();
    }
}
