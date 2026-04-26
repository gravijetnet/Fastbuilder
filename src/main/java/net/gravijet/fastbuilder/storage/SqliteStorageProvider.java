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

public class SqliteStorageProvider implements StorageProvider {

    private static final long CACHE_TTL_MS = 60000L;
    private static final String DRIVER    = "org.sqlite.JDBC";

    private final FastBuilder plugin;
    private Connection connection;

    private final Map<String, long[]> bestTimesCache     = new HashMap<String, long[]>();
    private final Map<String, Long>   bestTimesCacheTime = new HashMap<String, Long>();

    public SqliteStorageProvider(FastBuilder plugin) {
        this.plugin = plugin;
    }

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
            stmt.execute("PRAGMA journal_mode=WAL");
            stmt.execute("PRAGMA synchronous=NORMAL");

            stmt.execute("CREATE TABLE IF NOT EXISTS player_data (" +
                    "uuid                     TEXT PRIMARY KEY," +
                    "name                     TEXT NOT NULL," +
                    "coins                    INTEGER DEFAULT 0," +
                    "last_map                 TEXT," +
                    "last_island              INTEGER DEFAULT -1," +
                    "selected_block           TEXT DEFAULT 'SANDSTONE:0'," +
                    "selected_pickaxe         TEXT DEFAULT 'DIAMOND_PICKAXE:0'," +
                    "selected_animation       TEXT DEFAULT 'NONE'," +
                    "selected_death_sound     TEXT DEFAULT 'NONE'," +
                    "one_click_pick           INTEGER DEFAULT 0," +
                    "auto_refill              INTEGER DEFAULT 0," +
                    "infinite_blocks_unlocked INTEGER DEFAULT 0," +
                    "infinite_blocks          INTEGER DEFAULT 0" +
                    ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS player_map_stats (" +
                    "uuid                TEXT NOT NULL," +
                    "map_name            TEXT NOT NULL," +
                    "best_time           INTEGER DEFAULT -1," +
                    "total_attempts      INTEGER DEFAULT 0," +
                    "successful_attempts INTEGER DEFAULT 0," +
                    "total_success_time  INTEGER DEFAULT 0," +
                    "PRIMARY KEY (uuid, map_name)" +
                    ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS player_purchased_blocks (" +
                    "uuid      TEXT NOT NULL," +
                    "block_key TEXT NOT NULL," +
                    "PRIMARY KEY (uuid, block_key)" +
                    ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS player_favorites (" +
                    "uuid        TEXT NOT NULL," +
                    "replay_file TEXT NOT NULL," +
                    "PRIMARY KEY (uuid, replay_file)" +
                    ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS player_notified_ranks (" +
                    "uuid      TEXT NOT NULL," +
                    "map_name  TEXT NOT NULL," +
                    "rank_name TEXT NOT NULL," +
                    "PRIMARY KEY (uuid, map_name, rank_name)" +
                    ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS player_custom_lengths (" +
                    "uuid     TEXT NOT NULL," +
                    "map_name TEXT NOT NULL," +
                    "length   INTEGER NOT NULL," +
                    "PRIMARY KEY (uuid, map_name)" +
                    ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS player_selected_designs (" +
                    "uuid         TEXT NOT NULL," +
                    "map_name     TEXT NOT NULL," +
                    "template_key TEXT NOT NULL," +
                    "PRIMARY KEY (uuid, map_name)" +
                    ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS player_infinite_distances (" +
                    "uuid     TEXT NOT NULL," +
                    "map_name TEXT NOT NULL," +
                    "distance INTEGER NOT NULL," +
                    "PRIMARY KEY (uuid, map_name)" +
                    ")");

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_map_stats_map_best " +
                    "ON player_map_stats(map_name, best_time)");
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

    @Override
    public synchronized PlayerData loadPlayerData(UUID uuid, String name) {
        PlayerData data = new PlayerData(uuid, name);
        String uuidStr = uuid.toString();
        try {
            try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM player_data WHERE uuid = ?")) {
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
            // (Lade-Logik für andere Tabellen bleibt gleich, da sie bereits Java 8 konform war)
            loadSubTables(uuidStr, data);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQLite] Failed to load player: " + uuid, e);
        }
        return data;
    }

    private void loadSubTables(String uuidStr, PlayerData data) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM player_map_stats WHERE uuid = ?")) {
            ps.setString(1, uuidStr);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PlayerData.MapStats stats = data.getOrCreateStats(rs.getString("map_name"));
                    stats.bestTime = rs.getLong("best_time");
                    stats.totalAttempts = rs.getInt("total_attempts");
                    stats.successfulAttempts = rs.getInt("successful_attempts");
                    stats.totalSuccessTime = rs.getLong("total_success_time");
                }
            }
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT map_name, length FROM player_custom_lengths WHERE uuid = ?")) {
            ps.setString(1, uuidStr);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) data.setCustomLength(rs.getString("map_name"), rs.getInt("length"));
            }
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT map_name, distance FROM player_infinite_distances WHERE uuid = ?")) {
            ps.setString(1, uuidStr);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) data.updateInfiniteDistance(rs.getString("map_name"), rs.getInt("distance"));
            }
        }
    }

    @Override
    public synchronized void savePlayerData(PlayerData data) {
        String uuidStr = data.getUuid().toString();
        try {
            connection.setAutoCommit(false);

            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO player_data " +
                            "(uuid, name, coins, last_map, last_island, selected_block, " +
                            "selected_pickaxe, selected_animation, selected_death_sound, " +
                            "one_click_pick, auto_refill, infinite_blocks_unlocked, infinite_blocks) " +
                            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?) " +
                            "ON CONFLICT(uuid) DO UPDATE SET " +
                            "name=excluded.name, coins=excluded.coins, last_map=excluded.last_map, " +
                            "last_island=excluded.last_island, selected_block=excluded.selected_block, " +
                            "selected_pickaxe=excluded.selected_pickaxe, selected_animation=excluded.selected_animation, " +
                            "selected_death_sound=excluded.selected_death_sound, one_click_pick=excluded.one_click_pick, " +
                            "auto_refill=excluded.auto_refill, infinite_blocks_unlocked=excluded.infinite_blocks_unlocked, " +
                            "infinite_blocks=excluded.infinite_blocks")) {
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

            // Map Stats Upsert
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO player_map_stats " +
                            "(uuid, map_name, best_time, total_attempts, successful_attempts, total_success_time) " +
                            "VALUES (?,?,?,?,?,?) " +
                            "ON CONFLICT(uuid, map_name) DO UPDATE SET " +
                            "best_time=excluded.best_time, total_attempts=excluded.total_attempts, " +
                            "successful_attempts=excluded.successful_attempts, total_success_time=excluded.total_success_time")) {
                for (Map.Entry<String, PlayerData.MapStats> e : data.getAllStats().entrySet()) {
                    ps.setString(1, uuidStr); ps.setString(2, e.getKey());
                    ps.setLong(3, e.getValue().bestTime); ps.setInt(4, e.getValue().totalAttempts);
                    ps.setInt(5, e.getValue().successfulAttempts); ps.setLong(6, e.getValue().totalSuccessTime);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            // Infinite distances upsert
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO player_infinite_distances (uuid, map_name, distance) VALUES (?,?,?) " +
                    "ON CONFLICT(uuid, map_name) DO UPDATE SET distance=excluded.distance")) {
                for (Map.Entry<String, Integer> e : data.getInfiniteDistances().entrySet()) {
                    if (e.getValue() > 0) {
                        ps.setString(1, uuidStr); ps.setString(2, e.getKey()); ps.setInt(3, e.getValue());
                        ps.addBatch();
                    }
                }
                ps.executeBatch();
            }

            connection.commit();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[SQLite] Failed to save player: " + data.getUuid(), e);
            try { connection.rollback(); } catch (SQLException ignored) {}
        } finally {
            try { connection.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    @Override
    public synchronized long[] getGlobalBestTimesForMap(String mapName) {
        Long cacheTime = bestTimesCacheTime.get(mapName);
        if (cacheTime != null && System.currentTimeMillis() - cacheTime < CACHE_TTL_MS) {
            long[] cached = bestTimesCache.get(mapName);
            if (cached != null) return cached;
        }
        List<Long> times = new ArrayList<Long>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT best_time FROM player_map_stats WHERE map_name = ? AND best_time > 0")) {
            ps.setString(1, mapName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) times.add(rs.getLong("best_time"));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[SQLite] Failed to query best times.", e);
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

        List<java.util.Map.Entry<String, Long>> results = new ArrayList<java.util.Map.Entry<String, Long>>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT pd.name, pms.best_time " +
                "FROM player_map_stats pms " +
                "JOIN player_data pd ON pms.uuid = pd.uuid " +
                "WHERE pms.map_name = ? AND pms.best_time > 0 " +
                "ORDER BY pms.best_time ASC LIMIT ?")) {
            ps.setString(1, mapName);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new java.util.AbstractMap.SimpleEntry<>(
                            rs.getString("name"), rs.getLong("best_time")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[SQLite] Failed to query top players.", e);
        }
        return results;
    }

    @Override
    public synchronized java.util.List<java.util.Map.Entry<String, Integer>> getTopInfiniteDistancesForMap(
            String mapName, int limit) {

        List<java.util.Map.Entry<String, Integer>> results = new ArrayList<java.util.Map.Entry<String, Integer>>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT pd.name, pid.distance " +
                "FROM player_infinite_distances pid " +
                "JOIN player_data pd ON pid.uuid = pd.uuid " +
                "WHERE pid.map_name = ? AND pid.distance > 0 " +
                "ORDER BY pid.distance DESC LIMIT ?")) {
            ps.setString(1, mapName);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new java.util.AbstractMap.SimpleEntry<>(
                            rs.getString("name"), rs.getInt("distance")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[SQLite] Failed to query top infinite distances.", e);
        }
        return results;
    }
}