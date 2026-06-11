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

    private final Map<String, long[]> bestTimesCache     = new java.util.concurrent.ConcurrentHashMap<String, long[]>();
    private final Map<String, Long>   bestTimesCacheTime = new java.util.concurrent.ConcurrentHashMap<String, Long>();
    private final java.util.Set<String> refreshing       = java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

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

        try {
            createSchema();
        } catch (Exception e) {
            // Don't leak the connection if schema creation fails — the caller
            // falls back to the YAML provider and never calls shutdown() on us.
            try { connection.close(); } catch (SQLException ignored) {}
            connection = null;
            throw e;
        }

        plugin.getLogger().info("[SQLite] Database initialised: " + dbFile.getName());
    }

    private void createSchema() throws SQLException {
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
            // Migration: add time column if missing (safe to ignore if already exists)
            try { stmt.execute("ALTER TABLE player_infinite_distances ADD COLUMN time BIGINT NOT NULL DEFAULT 0"); }
            catch (Exception ignored) {}

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_map_stats_map_best " +
                    "ON player_map_stats(map_name, best_time)");

            stmt.execute("CREATE TABLE IF NOT EXISTS player_booster_inventory (" +
                    "uuid     TEXT NOT NULL," +
                    "type_id  TEXT NOT NULL," +
                    "quantity INTEGER NOT NULL," +
                    "PRIMARY KEY (uuid, type_id)" +
                    ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS player_purchased_designs (" +
                    "uuid       TEXT NOT NULL," +
                    "design_key TEXT NOT NULL," +
                    "PRIMARY KEY (uuid, design_key)" +
                    ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS player_custom_length_bests (" +
                    "uuid      TEXT NOT NULL," +
                    "map_name  TEXT NOT NULL," +
                    "distance  INTEGER NOT NULL," +
                    "best_time INTEGER NOT NULL," +
                    "PRIMARY KEY (uuid, map_name, distance)" +
                    ")");

            // Schema migrations for existing databases
            try { stmt.execute("ALTER TABLE player_data ADD COLUMN booster_expiry INTEGER DEFAULT 0"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE player_data ADD COLUMN booster_multiplier REAL DEFAULT 1.0"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE player_data ADD COLUMN experience INTEGER DEFAULT 0"); } catch (SQLException ignored) {}
        }
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
                        try { data.setBoosterExpiry(rs.getLong("booster_expiry")); } catch (SQLException ignored) {}
                        try { data.setBoosterMultiplier(rs.getDouble("booster_multiplier")); } catch (SQLException ignored) {}
                        try { data.setExperience(rs.getInt("experience")); } catch (SQLException ignored) {}
                    }
                }
            }
            // Load remaining sub-tables (already Java 8 compatible)
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
                "SELECT block_key FROM player_purchased_blocks WHERE uuid = ?")) {
            ps.setString(1, uuidStr);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) data.purchaseBlock(rs.getString("block_key"));
            }
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT replay_file FROM player_favorites WHERE uuid = ?")) {
            ps.setString(1, uuidStr);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) data.addFavoriteReplay(rs.getString("replay_file"));
            }
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT map_name, rank_name FROM player_notified_ranks WHERE uuid = ?")) {
            ps.setString(1, uuidStr);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) data.markRankNotified(rs.getString("map_name"), rs.getString("rank_name"));
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
                "SELECT map_name, template_key FROM player_selected_designs WHERE uuid = ?")) {
            ps.setString(1, uuidStr);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) data.setSelectedDesign(rs.getString("map_name"), rs.getString("template_key"));
            }
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT map_name, distance, time FROM player_infinite_distances WHERE uuid = ?")) {
            ps.setString(1, uuidStr);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) data.updateInfiniteDistance(rs.getString("map_name"), rs.getInt("distance"), rs.getLong("time"));
            }
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT type_id, quantity FROM player_booster_inventory WHERE uuid = ?")) {
            ps.setString(1, uuidStr);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) data.addBooster(rs.getString("type_id"), rs.getInt("quantity"));
            }
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT design_key FROM player_purchased_designs WHERE uuid = ?")) {
            ps.setString(1, uuidStr);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) data.purchaseDesign(rs.getString("design_key"));
            }
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT map_name, distance, best_time FROM player_custom_length_bests WHERE uuid = ?")) {
            ps.setString(1, uuidStr);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    data.updateCustomLengthBest(rs.getString("map_name"), rs.getInt("distance"), rs.getLong("best_time"));
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
                            "one_click_pick, auto_refill, infinite_blocks_unlocked, infinite_blocks, " +
                            "booster_expiry, booster_multiplier, experience) " +
                            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) " +
                            "ON CONFLICT(uuid) DO UPDATE SET " +
                            "name=excluded.name, coins=excluded.coins, last_map=excluded.last_map, " +
                            "last_island=excluded.last_island, selected_block=excluded.selected_block, " +
                            "selected_pickaxe=excluded.selected_pickaxe, selected_animation=excluded.selected_animation, " +
                            "selected_death_sound=excluded.selected_death_sound, one_click_pick=excluded.one_click_pick, " +
                            "auto_refill=excluded.auto_refill, infinite_blocks_unlocked=excluded.infinite_blocks_unlocked, " +
                            "infinite_blocks=excluded.infinite_blocks, booster_expiry=excluded.booster_expiry, " +
                            "booster_multiplier=excluded.booster_multiplier, experience=excluded.experience")) {
                ps.setString(1, uuidStr); ps.setString(2, data.getName());
                ps.setInt(3, data.getCoins()); ps.setString(4, data.getLastMap());
                ps.setInt(5, data.getLastIsland()); ps.setString(6, data.getSelectedBlock());
                ps.setString(7, data.getSelectedPickaxe()); ps.setString(8, data.getSelectedAnimation());
                ps.setString(9, data.getSelectedDeathSound());
                ps.setInt(10, data.hasOneClickPick() ? 1 : 0);
                ps.setInt(11, data.hasAutoRefill() ? 1 : 0);
                ps.setInt(12, data.hasInfiniteBlocksUnlocked() ? 1 : 0);
                ps.setInt(13, data.hasInfiniteBlocks() ? 1 : 0);
                ps.setLong(14, data.getBoosterExpiry());
                ps.setDouble(15, data.getBoosterMultiplier());
                ps.setInt(16, data.getExperience());
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

            // Purchased blocks (sync: delete+reinsert)
            syncTable(uuidStr, "player_purchased_blocks", "block_key", data.getPurchasedBlocks());

            // Favorites (sync)
            syncTable(uuidStr, "player_favorites", "replay_file", data.getFavoriteReplays());

            // Notified ranks
            try (PreparedStatement del = connection.prepareStatement(
                    "DELETE FROM player_notified_ranks WHERE uuid = ?")) {
                del.setString(1, uuidStr); del.executeUpdate();
            }
            try (PreparedStatement ins = connection.prepareStatement(
                    "INSERT OR IGNORE INTO player_notified_ranks (uuid, map_name, rank_name) VALUES (?,?,?)")) {
                for (Map.Entry<String, java.util.Set<String>> mapEntry : data.getNotifiedRanks().entrySet()) {
                    for (String rankName : mapEntry.getValue()) {
                        ins.setString(1, uuidStr); ins.setString(2, mapEntry.getKey()); ins.setString(3, rankName);
                        ins.addBatch();
                    }
                }
                ins.executeBatch();
            }

            // Custom lengths (saved, persistent)
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO player_custom_lengths (uuid, map_name, length) VALUES (?,?,?) " +
                    "ON CONFLICT(uuid, map_name) DO UPDATE SET length=excluded.length")) {
                for (Map.Entry<String, Integer> e : data.getSavedCustomLengthsMap().entrySet()) {
                    if (e.getValue() > 0) {
                        ps.setString(1, uuidStr); ps.setString(2, e.getKey()); ps.setInt(3, e.getValue());
                        ps.addBatch();
                    }
                }
                ps.executeBatch();
            }

            // Selected designs
            try (PreparedStatement del = connection.prepareStatement(
                    "DELETE FROM player_selected_designs WHERE uuid = ?")) {
                del.setString(1, uuidStr); del.executeUpdate();
            }
            try (PreparedStatement ins = connection.prepareStatement(
                    "INSERT OR IGNORE INTO player_selected_designs (uuid, map_name, template_key) VALUES (?,?,?)")) {
                for (Map.Entry<String, String> e : data.getSelectedDesigns().entrySet()) {
                    ins.setString(1, uuidStr); ins.setString(2, e.getKey()); ins.setString(3, e.getValue());
                    ins.addBatch();
                }
                ins.executeBatch();
            }

            // Infinite distances upsert
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO player_infinite_distances (uuid, map_name, distance, time) VALUES (?,?,?,?) " +
                    "ON CONFLICT(uuid, map_name) DO UPDATE SET distance=excluded.distance, time=excluded.time")) {
                for (Map.Entry<String, Integer> e : data.getInfiniteDistances().entrySet()) {
                    if (e.getValue() > 0) {
                        long t = data.getInfiniteDistanceTime(e.getKey());
                        ps.setString(1, uuidStr); ps.setString(2, e.getKey()); ps.setInt(3, e.getValue());
                        ps.setLong(4, t > 0 ? t : 0);
                        ps.addBatch();
                    }
                }
                ps.executeBatch();
            }

            // Booster inventory
            try (PreparedStatement del = connection.prepareStatement(
                    "DELETE FROM player_booster_inventory WHERE uuid = ?")) {
                del.setString(1, uuidStr); del.executeUpdate();
            }
            try (PreparedStatement ins = connection.prepareStatement(
                    "INSERT OR IGNORE INTO player_booster_inventory (uuid, type_id, quantity) VALUES (?,?,?)")) {
                for (Map.Entry<String, Integer> e : data.getBoosterInventory().entrySet()) {
                    if (e.getValue() > 0) {
                        ins.setString(1, uuidStr); ins.setString(2, e.getKey()); ins.setInt(3, e.getValue());
                        ins.addBatch();
                    }
                }
                ins.executeBatch();
            }

            // Purchased designs
            syncTable(uuidStr, "player_purchased_designs", "design_key", data.getPurchasedDesigns());

            // Custom-length all-time bests upsert
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO player_custom_length_bests (uuid, map_name, distance, best_time) VALUES (?,?,?,?) " +
                    "ON CONFLICT(uuid, map_name, distance) DO UPDATE SET best_time=excluded.best_time")) {
                for (Map.Entry<String, Map<Integer, Long>> mapEntry : data.getCustomLengthAllTimeBests().entrySet()) {
                    for (Map.Entry<Integer, Long> distEntry : mapEntry.getValue().entrySet()) {
                        if (distEntry.getValue() > 0) {
                            ps.setString(1, uuidStr); ps.setString(2, mapEntry.getKey());
                            ps.setInt(3, distEntry.getKey()); ps.setLong(4, distEntry.getValue());
                            ps.addBatch();
                        }
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

    private static final java.util.Set<String> ALLOWED_SYNC_TABLES = new HashSet<>(
            java.util.Arrays.asList("player_purchased_blocks", "player_favorites", "player_purchased_designs"));
    private static final java.util.Set<String> ALLOWED_SYNC_COLS = new HashSet<>(
            java.util.Arrays.asList("block_key", "replay_file", "design_key"));

    private void syncTable(String uuid, String table, String valueCol, Set<String> values) throws SQLException {
        if (!ALLOWED_SYNC_TABLES.contains(table) || !ALLOWED_SYNC_COLS.contains(valueCol)) {
            throw new SQLException("Rejected unsafe table/column in syncTable: " + table + "/" + valueCol);
        }
        try (PreparedStatement del = connection.prepareStatement(
                "DELETE FROM " + table + " WHERE uuid = ?")) {
            del.setString(1, uuid); del.executeUpdate();
        }
        if (!values.isEmpty()) {
            try (PreparedStatement ins = connection.prepareStatement(
                    "INSERT OR IGNORE INTO " + table + " (uuid, " + valueCol + ") VALUES (?,?)")) {
                for (String v : values) {
                    ins.setString(1, uuid); ins.setString(2, v); ins.addBatch();
                }
                ins.executeBatch();
            }
        }
    }

    @Override
    public long[] getGlobalBestTimesForMap(String mapName) {
        Long cacheTime = bestTimesCacheTime.get(mapName);
        long[] cached = bestTimesCache.get(mapName);
        boolean expired = cacheTime == null || System.currentTimeMillis() - cacheTime >= CACHE_TTL_MS;

        if (expired && refreshing.add(mapName)) {
            org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    List<Long> times = new ArrayList<Long>();
                    synchronized (SqliteStorageProvider.this) {
                        try (PreparedStatement ps = connection.prepareStatement(
                                "SELECT best_time FROM player_map_stats WHERE map_name = ? AND best_time > 0")) {
                            ps.setString(1, mapName);
                            try (ResultSet rs = ps.executeQuery()) {
                                while (rs.next()) times.add(rs.getLong("best_time"));
                            }
                        } catch (SQLException e) {
                            plugin.getLogger().log(Level.WARNING, "[SQLite] Failed to query best times.", e);
                        }
                    }
                    long[] result = new long[times.size()];
                    for (int i = 0; i < times.size(); i++) result[i] = times.get(i);
                    bestTimesCache.put(mapName, result);
                    bestTimesCacheTime.put(mapName, System.currentTimeMillis());
                } finally {
                    refreshing.remove(mapName);
                }
            });
        }
        return cached != null ? cached : new long[0];
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
    public synchronized java.util.List<java.util.Map.Entry<String, long[]>> getTopInfiniteDistancesForMap(
            String mapName, int limit) {

        List<java.util.Map.Entry<String, long[]>> results = new ArrayList<java.util.Map.Entry<String, long[]>>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT pd.name, pid.distance, pid.time " +
                "FROM player_infinite_distances pid " +
                "JOIN player_data pd ON pid.uuid = pd.uuid " +
                "WHERE pid.map_name = ? AND pid.distance > 0 " +
                "ORDER BY pid.distance DESC, pid.time ASC LIMIT ?")) {
            ps.setString(1, mapName);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new java.util.AbstractMap.SimpleEntry<>(
                            rs.getString("name"),
                            new long[]{rs.getLong("distance"), rs.getLong("time")}));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[SQLite] Failed to query top infinite distances.", e);
        }
        return results;
    }
}