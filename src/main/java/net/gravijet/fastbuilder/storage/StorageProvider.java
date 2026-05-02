package net.gravijet.fastbuilder.storage;

import net.gravijet.fastbuilder.player.PlayerData;

import java.util.UUID;

/**
 * Unified storage interface for all player-persistent data.
 *
 * Implementations must be thread-safe — callers may invoke methods from async
 * tasks (e.g. Bukkit scheduler async threads). All implementations cache global
 * best-time arrays internally and expose cache invalidation so callers can
 * force a fresh scan after a new personal best is recorded.
 */
public interface StorageProvider {

    /**
     * Initialise the backend (create tables, open connections, etc.).
     * Called once from the main thread during plugin enable.
     *
     * @throws Exception if initialisation fails fatally
     */
    void init() throws Exception;

    /**
     * Load a player's data from the backend.
     * If no record exists a fresh {@link PlayerData} with {@code name} is returned
     * (never null).
     *
     * @param uuid player UUID
     * @param name current in-game name
     * @return the player's data object (always non-null)
     */
    PlayerData loadPlayerData(UUID uuid, String name);

    /**
     * Persist a player's data to the backend.
     * Must be safe to call from an async thread.
     *
     * @param data the player data to persist
     */
    void savePlayerData(PlayerData data);

    /**
     * Return all recorded personal-best times (in milliseconds) for a given map,
     * across all players. Used by {@link net.gravijet.fastbuilder.hologram.HologramManager}
     * to compute the global percentile rank.
     *
     * Implementations should cache the result and refresh no more often than
     * every 60 seconds, unless {@link #invalidateBestTimesCache(String)} is called.
     *
     * @param mapName case-sensitive map name
     * @return array of best times (may be empty, never null)
     */
    long[] getGlobalBestTimesForMap(String mapName);

    /**
     * Force-expire the cached global best-times for {@code mapName} so the next
     * call to {@link #getGlobalBestTimesForMap(String)} performs a fresh scan.
     * Call this immediately after a new personal best is recorded.
     *
     * @param mapName case-sensitive map name
     */
    void invalidateBestTimesCache(String mapName);

    /**
     * Return the top {@code limit} players for {@code mapName} sorted by personal-best
     * time ascending (fastest first).
     *
     * <p>Each entry is a {@code (playerName, bestTimeMs)} pair.  Returns an empty list
     * if no records exist.  Safe to call from an async thread.
     *
     * @param mapName case-sensitive map name
     * @param limit   maximum number of entries to return
     * @return ordered list of (name, ms) entries, never null
     */
    java.util.List<java.util.Map.Entry<String, Long>> getTopPlayerTimesForMap(String mapName, int limit);

    /**
     * Return the top {@code limit} players for infinite-mode {@code mapName}, sorted by
     * blocks-placed-before-dying descending (highest first).
     *
     * <p>Each entry is a {@code (playerName, blocksPlaced)} pair.  Returns an empty list
     * if no records exist.  Safe to call from an async thread.
     *
     * @param mapName case-sensitive map name
     * @param limit   maximum number of entries to return
     * @return ordered list of (name, blocksPlaced) entries, never null
     */
    java.util.List<java.util.Map.Entry<String, long[]>> getTopInfiniteDistancesForMap(String mapName, int limit);

    /**
     * Cleanly shut down the backend (flush pending writes, close connections).
     * Called from the main thread during plugin disable.
     */
    void shutdown();
}
