package net.gravijet.fastbuilder.storage;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.player.PlayerData;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * SQLite storage provider using the bundled JDBC driver.
 *
 * Schema uses 7 normalised tables to avoid large BLOB columns and enable
 * efficient per-column queries. WAL journal mode is enabled for concurrent
 * read performance. All write operations go through a single serialised
 * connection; reads use the same connection with synchronised access.
 *
 * Requires the sqlite-jdbc driver on the classpath (shaded into the plugin JAR).
 */
public class SqliteStorageProvider implements StorageProvider {

    private static final long CACHE_TTL_MS = 60_000L;
    private static final String DRIVER    = "org.sqlite.JDBC";

    private final FastBuilder plugin;
    private Connection connection;

    private final Map<String, long[]> bestTimesCache     = new HashMap<>();
    private final Map<String, Long>   bestTimesCacheTime = new HashMap<>();

    public SqliteStorageProvider(FastBuilder plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Init & Shutdown
    // -------------------------------------------------------------------------

    @Override
    public void init() throws Exception {
        try {
            Class.forName(DRIVER);
        } catch (ClassNotFoundException e) {
            throw new Exception("sqlite-jdbc driver not found. Add sqlite-jdbc to the plugin JAR.", e);
        }

        File dbFile = new File(plugin.getDataFolder(), "fastbuilder.db");
        String url  = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        connection  = DriverManager.getConnection(url);

        try (Statement stmt = connection.createStatement()) {
            // WAL mode → concurrent readers, single writer
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA synchronous=NORMAL");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_data (
                    uuid                     TEXT PRIMARY KEY,
                    name                     TEXT NOT NULL,
                    coins                    INTEGER DEFAULT 0,
                    last_map                 TEXT,
                    last_island              INTEGER DEFAULT -1,
                    selected_block           TEXT DEFAULT 'SANDSTONE:0',
                    selected_pickaxe         TEXT DEFAULT 'DIAMOND_PICKAXE:0',
                    selected_animation       TEXT DEFAULT 'NONE',
                    selected_death_sound     TEXT DEFAULT 'NONE',
                    one_click_pick           INTEGER DEFAULT 0,
                    auto_refill              INTEGER DEFAULT 0,
                    infinite_blocks_unlocked INTEGER DEFAULT 0,
                    infinite_blocks          INTEGER DEFAULT 0
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_map_stats (
                    uuid                TEXT NOT NULL,
                    map_name            TEXT NOT NULL,
                    best_time           INTEGER DEFAULT -1,
                    total_attempts      INTEGER DEFAULT 0,
                    successful_attempts INTEGER DEFAULT 0,
                    total_success_time  INTEGER DEFAULT 0,
                    PRIMARY KEY (uuid, map_name)
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_purchased_blocks (
                    uuid      TEXT NOT NULL,
                    block_key TEXT NOT NULL,
                    PRIMARY KEY (uuid, block_key)
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_favorites (
                    uuid        TEXT NOT NULL,
                    replay_file TEXT NOT NULL,
                    PRIMARY KEY (uuid, replay_file)
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_notified_ranks (
                    uuid      TEXT NOT NULL,
                    map_name  TEXT NOT NULL,
                    rank_name TEXT NOT NULL,
                    PRIMARY KEY (uuid, map_name, rank_name)
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_custom_lengths (
                    uuid     TEXT NOT NULL,
                    map_name TEXT NOT NULL,
                    length   INTEGER NOT NULL,
                    PRIMARY KEY (uuid, map_name)
                )""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_selected_designs (
                    uuid         TEXT NOT NULL,
                    map_name     TEXT NOT NULL,
                    template_key TEXT NOT NULL,
                    PRIMARY KEY (uuid, map_name)
                )""");

            // Index for fast global best-time scans
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_map_stats_map_best
                ON player_map_stats(map_name, best_time)""");
        }

        plugin.getLogger().info("[SQLite] Database initialised: " + dbFile.getName());
    }

    @Override
    public synchronized void shutdown() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[SQLite] Failed to close connection.", e);
        }
    }

    // -------------------------------------------------------------------------
    // Load
    // -------------------------------------------------------------------------

    @Override
    public synchronized PlayerData loadPlayerData(UUID uuid, String name) {
        PlayerData data = new PlayerData(uuid, name);
        String uuidStr = uuid.toString();

        try {
            // --- Main row ---
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM player_data WHERE uuid = ?")) {
                ps.setString(1, uuidStr);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        data.setName(rs.getString("name"));
                        data.setCoins(rs.getInt("coins"));
                        data.setLastMap(rs.getString("last_map"));
                        data.setLastIsland(rs.getInt("last_island"));
                        data.setSelectedBlock(rs.getString("selected_block"));
                        data.setSelectedPickaxe(rs.getString("selected_pickaxe"));
                        data.setSelectedAnimation(rs.getString("selected_animation"));
                        data.setSelectedDeathSound(rs.getString("selected_death_sound"));
                        data.setOneClickPick(rs.getInt("one_click_pick") == 1);
                        data.setAutoRefill(rs.getInt("auto_refill") == 1);
                        data.setInfiniteBlocksUnlocked(rs.getInt("infinite_blocks_unlocked") == 1);
                        data.setInfiniteBlocks(rs.getInt("infinite_blocks") == 1);
                    }
                }
            }

            // --- Per-map stats ---
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM player_map_stats WHERE uuid = ?")) {
                ps.setString(1, uuidStr);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String mapName = rs.getString("map_name");
                        PlayerData.MapStats stats = data.getOrCreateStats(mapName);
                        stats.bestTime           = rs.getLong("best_time");
                        stats.totalAttempts      = rs.getInt("total_attempts");
                        stats.successfulAttempts = rs.getInt("successful_attempts");
                        stats.totalSuccessTime   = rs.getLong("total_success_time");
                    }
                }
            }

            // --- Purchased blocks ---
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT block_key FROM player_purchased_blocks WHERE uuid = ?")) {
                ps.setString(1, uuidStr);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) data.purchaseBlock(rs.getString("block_key"));
                }
            }

            // --- Favourites ---
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT replay_file FROM player_favorites WHERE uuid = ?")) {
                ps.setString(1, uuidStr);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) data.addFavoriteReplay(rs.getString("replay_file"));
                }
            }

            // --- Notified ranks ---
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT map_name, rank_name FROM player_notified_ranks WHERE uuid = ?")) {
                ps.setString(1, uuidStr);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next())
                        data.markRankNotified(rs.getString("map_name"), rs.getString("rank_name"));
                }
            }

            // --- Custom lengths ---
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT map_name, length FROM player_custom_lengths WHERE uuid = ?")) {
                ps.setString(1, uuidStr);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next())
                        data.setCustomLength(rs.getString("map_name"), rs.getInt("length"));
                }
            }

            // --- Selected designs ---
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT map_name, template_key FROM player_selected_designs WHERE uuid = ?")) {
                ps.setString(1, uuidStr);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next())
                        data.setSelectedDesign(rs.getString("map_name"), rs.getString("template_key"));
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQLite] Failed to load player: " + uuid, e);
        }

        return data;
    }

    // -------------------------------------------------------------------------
    // Save  (upsert everything)
    // -------------------------------------------------------------------------

    @Override
    public synchronized void savePlayerData(PlayerData data) {
        String uuidStr = data.getUuid().toString();
        try {
            connection.setAutoCommit(false);

            // --- Main row (upsert) ---
            try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO player_data
                  (uuid, name, coins, last_map, last_island, selected_block,
                   selected_pickaxe, selected_animation, selected_death_sound,
                   one_click_pick, auto_refill, infinite_blocks_unlocked, infinite_blocks)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(uuid) DO UPDATE SET
                  name=excluded.name, coins=excluded.coins,
                  last_map=excluded.last_map, last_island=excluded.last_island,
                  selected_block=excluded.selected_block,
                  selected_pickaxe=excluded.selected_pickaxe,
                  selected_animation=excluded.selected_animation,
                  selected_death_sound=excluded.selected_death_sound,
                  one_click_pick=excluded.one_click_pick,
                  auto_refill=excluded.auto_refill,
                  infinite_blocks_unlocked=excluded.infinite_blocks_unlocked,
                  infinite_blocks=excluded.infinite_blocks
                """)) {
                ps.setString(1, uuidStr);
                ps.setString(2, data.getName());
                ps.setInt(3, data.getCoins());
                ps.setString(4, data.getLastMap());
                ps.setInt(5, data.getLastIsland());
                ps.setString(6, data.getSelectedBlock());
                ps.setString(7, data.getSelectedPickaxe());
                ps.setString(8, data.getSelectedAnimation());
                ps.setString(9, data.getSelectedDeathSound());
                ps.setInt(10, data.hasOneClickPick() ? 1 : 0);
                ps.setInt(11, data.hasAutoRefill() ? 1 : 0);
                ps.setInt(12, data.hasInfiniteBlocksUnlocked() ? 1 : 0);
                ps.setInt(13, data.hasInfiniteBlocks() ? 1 : 0);
                ps.executeUpdate();
            }

            // --- Map stats (upsert) ---
            try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO player_map_stats
                  (uuid, map_name, best_time, total_attempts, successful_attempts, total_success_time)
                VALUES (?,?,?,?,?,?)
                ON CONFLICT(uuid, map_name) DO UPDATE SET
                  best_time=excluded.best_time,
                  total_attempts=excluded.total_attempts,
                  successful_attempts=excluded.successful_attempts,
                  total_success_time=excluded.total_success_time
                """)) {
                for (Map.Entry<String, PlayerData.MapStats> e : data.getAllStats().entrySet()) {
                    PlayerData.MapStats stats = e.getValue();
                    ps.setString(1, uuidStr);
                    ps.setString(2, e.getKey());
                    ps.setLong(3, stats.bestTime);
                    ps.setInt(4, stats.totalAttempts);
                    ps.setInt(5, stats.successfulAttempts);
                    ps.setLong(6, stats.totalSuccessTime);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            // --- Purchased blocks (delete-all + re-insert) ---
            try (PreparedStatement del = connection.prepareStatement(
                    "DELETE FROM player_purchased_blocks WHERE uuid = ?")) {
                del.setString(1, uuidStr);
                del.executeUpdate();
            }
            Set<String> purchased = data.getPurchasedBlocks();
            if (!purchased.isEmpty()) {
                try (PreparedStatement ins = connection.prepareStatement(
                        "INSERT OR IGNORE INTO player_purchased_blocks (uuid, block_key) VALUES (?,?)")) {
                    for (String block : purchased) {
                        ins.setString(1, uuidStr);
                        ins.setString(2, block);
                        ins.addBatch();
                    }
                    ins.executeBatch();
                }
            }

            // --- Favourites ---
            try (PreparedStatement del = connection.prepareStatement(
                    "DELETE FROM player_favorites WHERE uuid = ?")) {
                del.setString(1, uuidStr);
                del.executeUpdate();
            }
            Set<String> favs = data.getFavoriteReplays();
            if (!favs.isEmpty()) {
                try (PreparedStatement ins = connection.prepareStatement(
                        "INSERT OR IGNORE INTO player_favorites (uuid, replay_file) VALUES (?,?)")) {
                    for (String fav : favs) {
                        ins.setString(1, uuidStr);
                        ins.setString(2, fav);
                        ins.addBatch();
                    }
                    ins.executeBatch();
                }
            }

            // --- Notified ranks (sync) ---
            try (PreparedStatement del = connection.prepareStatement(
                    "DELETE FROM player_notified_ranks WHERE uuid = ?")) {
                del.setString(1, uuidStr);
                del.executeUpdate();
            }
            // Re-insert from PlayerData internals
            try (PreparedStatement ins = connection.prepareStatement(
                    "INSERT OR IGNORE INTO player_notified_ranks (uuid, map_name, rank_name) VALUES (?,?,?)")) {
                for (Map.Entry<String, Set<String>> e : getNotifiedRanksReflective(data).entrySet()) {
                    for (String rankName : e.getValue()) {
                        ins.setString(1, uuidStr);
                        ins.setString(2, e.getKey());
                        ins.setString(3, rankName);
                        ins.addBatch();
                    }
                }
                ins.executeBatch();
            }

            // --- Custom lengths ---
            try (PreparedStatement del = connection.prepareStatement(
                    "DELETE FROM player_custom_lengths WHERE uuid = ?")) {
                del.setString(1, uuidStr);
                del.executeUpdate();
            }
            try (PreparedStatement ins = connection.prepareStatement(
                    "INSERT OR IGNORE INTO player_custom_lengths (uuid, map_name, length) VALUES (?,?,?)")) {
                for (Map.Entry<String, Integer> e : getCustomLengthsReflective(data).entrySet()) {
                    ins.setString(1, uuidStr);
                    ins.setString(2, e.getKey());
                    ins.setInt(3, e.getValue());
                    ins.addBatch();
                }
                ins.executeBatch();
            }

            // --- Selected designs ---
            try (PreparedStatement del = connection.prepareStatement(
                    "DELETE FROM player_selected_designs WHERE uuid = ?")) {
                del.setString(1, uuidStr);
                del.executeUpdate();
            }
            try (PreparedStatement ins = connection.prepareStatement(
                    "INSERT OR IGNORE INTO player_selected_designs (uuid, map_name, template_key) VALUES (?,?,?)")) {
                for (Map.Entry<String, String> e : data.getSelectedDesigns().entrySet()) {
                    ins.setString(1, uuidStr);
                    ins.setString(2, e.getKey());
                    ins.setString(3, e.getValue());
                    ins.addBatch();
                }
                ins.executeBatch();
            }

            connection.commit();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQLite] Failed to save player: " + data.getUuid(), e);
            try { connection.rollback(); } catch (SQLException ignored) {}
        } finally {
            try { connection.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    // -------------------------------------------------------------------------
    // Global best times
    // -------------------------------------------------------------------------

    @Override
    public synchronized long[] getGlobalBestTimesForMap(String mapName) {
        Long cacheTime = bestTimesCacheTime.get(mapName);
        if (cacheTime != null && System.currentTimeMillis() - cacheTime < CACHE_TTL_MS) {
            long[] cached = bestTimesCache.get(mapName);
            if (cached != null) return cached;
        }

        List<Long> times = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT best_time FROM player_map_stats WHERE map_name = ? AND best_time > 0")) {
            ps.setString(1, mapName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) times.add(rs.getLong("best_time"));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[SQLite] Failed to query best times for: " + mapName, e);
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

    // -------------------------------------------------------------------------
    // Reflection helpers for private fields in PlayerData
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private Map<String, Set<String>> getNotifiedRanksReflective(PlayerData data) {
        try {
            java.lang.reflect.Field f = PlayerData.class.getDeclaredField("notifiedRanks");
            f.setAccessible(true);
            return (Map<String, Set<String>>) f.get(data);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> getCustomLengthsReflective(PlayerData data) {
        try {
            java.lang.reflect.Field f = PlayerData.class.getDeclaredField("customLengths");
            f.setAccessible(true);
            return (Map<String, Integer>) f.get(data);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}
