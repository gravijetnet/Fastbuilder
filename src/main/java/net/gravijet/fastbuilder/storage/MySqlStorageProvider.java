package net.gravijet.fastbuilder.storage;

import net.gravijet.fastbuilder.FastBuilder;
import net.gravijet.fastbuilder.player.PlayerData;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * MySQL / MariaDB storage provider.
 * Java 8 Compliant.
 */
public class MySqlStorageProvider implements StorageProvider {

    private static final long   CACHE_TTL_MS   = 60000L;
    private static final String DRIVER         = "com.mysql.jdbc.Driver";
    private static final String DRIVER_NEW     = "com.mysql.cj.jdbc.Driver";
    private static final int    POOL_SIZE      = 5;
    private static final long   POOL_TIMEOUT   = 30000L;

    private final FastBuilder plugin;
    private final String host;
    private final int    port;
    private final String database;
    private final String user;
    private final String password;

    private final Connection[] pool    = new Connection[POOL_SIZE];
    private final boolean[]    in_use  = new boolean[POOL_SIZE];
    private String jdbcUrl;
    private java.util.Properties connectionProps;
    private java.sql.Driver mysqlDriver;

    private final Map<String, long[]> bestTimesCache     = new java.util.concurrent.ConcurrentHashMap<String, long[]>();
    private final Map<String, Long>   bestTimesCacheTime = new java.util.concurrent.ConcurrentHashMap<String, Long>();
    private final java.util.Set<String> refreshing       = java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    public MySqlStorageProvider(FastBuilder plugin) {
        this.plugin   = plugin;
        this.host     = plugin.getConfigManager().getStorageMySQL("host",     "localhost");
        this.port     = plugin.getConfigManager().getStorageMySQLPort();
        this.database = plugin.getConfigManager().getStorageMySQL("database", "fastbuilder");
        this.user     = plugin.getConfigManager().getStorageMySQL("user",     "root");
        this.password = plugin.getConfigManager().getStorageMySQL("password", "");
    }

    @Override
    public void init() throws Exception {
        // Try loading the MySQL JDBC driver — prefer the modern one first.
        boolean driverLoaded = false;
        try { Class.forName(DRIVER_NEW); driverLoaded = true; } catch (ClassNotFoundException ignored) {}
        if (!driverLoaded) {
            try { Class.forName(DRIVER); driverLoaded = true; } catch (ClassNotFoundException ignored) {}
        }
        if (!driverLoaded) {
            throw new Exception("MySQL JDBC driver not found. Add mysql-connector-j or mysql-connector-java to the classpath.");
        }

        // Obtain the driver instance directly via DriverManager to bypass its
        // URL-type‑detection, which can reject valid URLs under certain
        // driver / JDK combinations.
        this.mysqlDriver = DriverManager.getDriver("jdbc:mysql://");

        // Build the base JDBC URL (host:port/database) and pass all parameters
        // via Properties to avoid URL‑parsing issues with modern JDBC drivers.
        boolean useSSL = plugin.getConfigManager().getStorageMySQL("use-ssl", "false").equalsIgnoreCase("true");
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database;
        this.jdbcUrl = url;

        java.util.Properties props = new java.util.Properties();
        props.setProperty("user", user);
        props.setProperty("password", password);
        props.setProperty("useSSL", String.valueOf(useSSL));
        props.setProperty("characterEncoding", "utf8");
        props.setProperty("serverTimezone", "UTC");
        this.connectionProps = props;

        synchronized (pool) {
            for (int i = 0; i < POOL_SIZE; i++) {
                Connection c = mysqlDriver.connect(url, props);
                if (c == null) {
                    throw new SQLException("MySQL driver refused the URL (returned null): " + url);
                }
                pool[i]   = c;
                in_use[i] = false;
            }
        }

        try (Connection c = borrowConnection();
             Statement stmt = c.createStatement()) {

            stmt.execute("CREATE TABLE IF NOT EXISTS player_data (" +
                    "uuid                     VARCHAR(36) PRIMARY KEY," +
                    "name                     VARCHAR(64) NOT NULL," +
                    "coins                    INT DEFAULT 0," +
                    "last_map                 VARCHAR(64)," +
                    "last_island              INT DEFAULT -1," +
                    "selected_block           VARCHAR(64) DEFAULT 'SANDSTONE:0'," +
                    "selected_pickaxe         VARCHAR(64) DEFAULT 'DIAMOND_PICKAXE:0'," +
                    "selected_animation       VARCHAR(64) DEFAULT 'NONE'," +
                    "selected_death_sound     VARCHAR(64) DEFAULT 'NONE'," +
                    "one_click_pick           TINYINT(1) DEFAULT 0," +
                    "auto_refill              TINYINT(1) DEFAULT 0," +
                    "infinite_blocks_unlocked TINYINT(1) DEFAULT 0," +
                    "infinite_blocks          TINYINT(1) DEFAULT 0" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            stmt.execute("CREATE TABLE IF NOT EXISTS player_map_stats (" +
                    "uuid                VARCHAR(36) NOT NULL," +
                    "map_name            VARCHAR(64) NOT NULL," +
                    "best_time           BIGINT DEFAULT -1," +
                    "total_attempts      INT DEFAULT 0," +
                    "successful_attempts INT DEFAULT 0," +
                    "total_success_time  BIGINT DEFAULT 0," +
                    "PRIMARY KEY (uuid, map_name)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            stmt.execute("CREATE TABLE IF NOT EXISTS player_purchased_blocks (" +
                    "uuid      VARCHAR(36) NOT NULL," +
                    "block_key VARCHAR(128) NOT NULL," +
                    "PRIMARY KEY (uuid, block_key)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            stmt.execute("CREATE TABLE IF NOT EXISTS player_favorites (" +
                    "uuid        VARCHAR(36) NOT NULL," +
                    "replay_file VARCHAR(256) NOT NULL," +
                    "PRIMARY KEY (uuid, replay_file)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            stmt.execute("CREATE TABLE IF NOT EXISTS player_notified_ranks (" +
                    "uuid      VARCHAR(36) NOT NULL," +
                    "map_name  VARCHAR(64) NOT NULL," +
                    "rank_name VARCHAR(64) NOT NULL," +
                    "PRIMARY KEY (uuid, map_name, rank_name)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            stmt.execute("CREATE TABLE IF NOT EXISTS player_custom_lengths (" +
                    "uuid     VARCHAR(36) NOT NULL," +
                    "map_name VARCHAR(64) NOT NULL," +
                    "length   INT NOT NULL," +
                    "PRIMARY KEY (uuid, map_name)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            stmt.execute("CREATE TABLE IF NOT EXISTS player_selected_designs (" +
                    "uuid         VARCHAR(36) NOT NULL," +
                    "map_name     VARCHAR(64) NOT NULL," +
                    "template_key VARCHAR(128) NOT NULL," +
                    "PRIMARY KEY (uuid, map_name)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            stmt.execute("CREATE TABLE IF NOT EXISTS player_infinite_distances (" +
                    "uuid     VARCHAR(36) NOT NULL," +
                    "map_name VARCHAR(64) NOT NULL," +
                    "distance INT NOT NULL," +
                    "time     BIGINT NOT NULL DEFAULT 0," +
                    "PRIMARY KEY (uuid, map_name)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            // Migration: add time column if missing (safe to ignore if already exists)
            try { stmt.execute("ALTER TABLE player_infinite_distances ADD COLUMN time BIGINT NOT NULL DEFAULT 0"); }
            catch (Exception ignored) {}

            try { stmt.execute("CREATE INDEX idx_map_stats_map_best ON player_map_stats(map_name, best_time)"); }
            catch (SQLException ignored) {} // silently skip if the index already exists

            stmt.execute("CREATE TABLE IF NOT EXISTS player_booster_inventory (" +
                    "uuid     VARCHAR(36) NOT NULL," +
                    "type_id  VARCHAR(64) NOT NULL," +
                    "quantity INT NOT NULL," +
                    "PRIMARY KEY (uuid, type_id)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            stmt.execute("CREATE TABLE IF NOT EXISTS player_purchased_designs (" +
                    "uuid       VARCHAR(36) NOT NULL," +
                    "design_key VARCHAR(128) NOT NULL," +
                    "PRIMARY KEY (uuid, design_key)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            stmt.execute("CREATE TABLE IF NOT EXISTS player_custom_length_bests (" +
                    "uuid      VARCHAR(36) NOT NULL," +
                    "map_name  VARCHAR(64) NOT NULL," +
                    "distance  INT NOT NULL," +
                    "best_time BIGINT NOT NULL," +
                    "PRIMARY KEY (uuid, map_name, distance)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            // Schema migrations — catch duplicate-column errors for existing deployments
            try { stmt.execute("ALTER TABLE player_data ADD COLUMN booster_expiry BIGINT DEFAULT 0"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE player_data ADD COLUMN booster_multiplier DOUBLE DEFAULT 1.0"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE player_data ADD COLUMN experience INT DEFAULT 0"); } catch (SQLException ignored) {}
        }

        plugin.getLogger().info("[MySQL] Database initialised. Connected to " + host + ":" + port + "/" + database);
    }

    private Connection borrowConnection() throws SQLException {
        long deadline = System.currentTimeMillis() + POOL_TIMEOUT;
        while (System.currentTimeMillis() < deadline) {
            synchronized (pool) {
                for (int i = 0; i < POOL_SIZE; i++) {
                    if (!in_use[i]) {
                        try {
                            if (pool[i] == null || pool[i].isClosed() || !pool[i].isValid(2)) {
                                // Attempt to get the JDBC URL from the old connection; if that also fails,
                                // fall back to the stored connection URL fields.
                                String url;
                                try {
                                    url = pool[i] != null ? pool[i].getMetaData().getURL() : null;
                                } catch (SQLException e) {
                                    url = null;
                                }
                                if (url == null) url = this.jdbcUrl;
                                pool[i] = mysqlDriver.connect(url, connectionProps);
                                if (pool[i] == null) {
                                    throw new SQLException("MySQL driver refused the URL (returned null): " + url);
                                }
                            }
                        } catch (SQLException e) {
                            // Connection is dead and reconnect failed — skip this slot
                            plugin.getLogger().warning("[MySQL] Failed to reconnect pool slot " + i + ": " + e.getMessage());
                            continue;
                        }
                        in_use[i] = true;
                        return new PooledConnection(pool[i], i, in_use, pool);
                    }
                }
            }
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        }
        throw new SQLException("MySQL connection pool exhausted (timeout after " + POOL_TIMEOUT + " ms)");
    }

    @Override
    public PlayerData loadPlayerData(UUID uuid, String name) {
        PlayerData data = new PlayerData(uuid, name);
        String uuidStr = uuid.toString();

        try (Connection c = borrowConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
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
                        try { data.setBoosterExpiry(rs.getLong("booster_expiry")); } catch (SQLException ignored) {}
                        try { data.setBoosterMultiplier(rs.getDouble("booster_multiplier")); } catch (SQLException ignored) {}
                        try { data.setExperience(rs.getInt("experience")); } catch (SQLException ignored) {}
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT * FROM player_map_stats WHERE uuid = ?")) {
                ps.setString(1, uuidStr);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        PlayerData.MapStats stats = data.getOrCreateStats(rs.getString("map_name"));
                        stats.bestTime           = rs.getLong("best_time");
                        stats.totalAttempts      = rs.getInt("total_attempts");
                        stats.successfulAttempts = rs.getInt("successful_attempts");
                        stats.totalSuccessTime   = rs.getLong("total_success_time");
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT block_key FROM player_purchased_blocks WHERE uuid = ?")) {
                ps.setString(1, uuidStr);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) data.purchaseBlock(rs.getString("block_key"));
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT replay_file FROM player_favorites WHERE uuid = ?")) {
                ps.setString(1, uuidStr);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) data.addFavoriteReplay(rs.getString("replay_file"));
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT map_name, rank_name FROM player_notified_ranks WHERE uuid = ?")) {
                ps.setString(1, uuidStr);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next())
                        data.markRankNotified(rs.getString("map_name"), rs.getString("rank_name"));
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT map_name, length FROM player_custom_lengths WHERE uuid = ?")) {
                ps.setString(1, uuidStr);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next())
                        data.setCustomLength(rs.getString("map_name"), rs.getInt("length"));
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT map_name, template_key FROM player_selected_designs WHERE uuid = ?")) {
                ps.setString(1, uuidStr);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next())
                        data.setSelectedDesign(rs.getString("map_name"), rs.getString("template_key"));
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT map_name, distance, time FROM player_infinite_distances WHERE uuid = ?")) {
                ps.setString(1, uuidStr);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) data.updateInfiniteDistance(rs.getString("map_name"), rs.getInt("distance"), rs.getLong("time"));
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT type_id, quantity FROM player_booster_inventory WHERE uuid = ?")) {
                ps.setString(1, uuidStr);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) data.addBooster(rs.getString("type_id"), rs.getInt("quantity"));
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT design_key FROM player_purchased_designs WHERE uuid = ?")) {
                ps.setString(1, uuidStr);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) data.purchaseDesign(rs.getString("design_key"));
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT map_name, distance, best_time FROM player_custom_length_bests WHERE uuid = ?")) {
                ps.setString(1, uuidStr);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next())
                        data.updateCustomLengthBest(rs.getString("map_name"), rs.getInt("distance"), rs.getLong("best_time"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[MySQL] Failed to load player: " + uuid, e);
        }

        return data;
    }

    @Override
    public void savePlayerData(PlayerData data) {
        String uuidStr = data.getUuid().toString();
        try (Connection c = borrowConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO player_data " +
                                "(uuid, name, coins, last_map, last_island, selected_block, " +
                                "selected_pickaxe, selected_animation, selected_death_sound, " +
                                "one_click_pick, auto_refill, infinite_blocks_unlocked, infinite_blocks, " +
                                "booster_expiry, booster_multiplier, experience) " +
                                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) " +
                                "ON DUPLICATE KEY UPDATE " +
                                "name=VALUES(name), coins=VALUES(coins), " +
                                "last_map=VALUES(last_map), last_island=VALUES(last_island), " +
                                "selected_block=VALUES(selected_block), " +
                                "selected_pickaxe=VALUES(selected_pickaxe), " +
                                "selected_animation=VALUES(selected_animation), " +
                                "selected_death_sound=VALUES(selected_death_sound), " +
                                "one_click_pick=VALUES(one_click_pick), " +
                                "auto_refill=VALUES(auto_refill), " +
                                "infinite_blocks_unlocked=VALUES(infinite_blocks_unlocked), " +
                                "infinite_blocks=VALUES(infinite_blocks), " +
                                "booster_expiry=VALUES(booster_expiry), " +
                                "booster_multiplier=VALUES(booster_multiplier), " +
                                "experience=VALUES(experience)")) {
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
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO player_map_stats " +
                                "(uuid, map_name, best_time, total_attempts, successful_attempts, total_success_time) " +
                                "VALUES (?,?,?,?,?,?) " +
                                "ON DUPLICATE KEY UPDATE " +
                                "best_time=VALUES(best_time), total_attempts=VALUES(total_attempts), " +
                                "successful_attempts=VALUES(successful_attempts), " +
                                "total_success_time=VALUES(total_success_time)")) {
                    for (Map.Entry<String, PlayerData.MapStats> e : data.getAllStats().entrySet()) {
                        PlayerData.MapStats s = e.getValue();
                        ps.setString(1, uuidStr); ps.setString(2, e.getKey());
                        ps.setLong(3, s.bestTime); ps.setInt(4, s.totalAttempts);
                        ps.setInt(5, s.successfulAttempts); ps.setLong(6, s.totalSuccessTime);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                syncTable(c, uuidStr, "player_purchased_blocks", "block_key", data.getPurchasedBlocks());
                syncTable(c, uuidStr, "player_favorites", "replay_file", data.getFavoriteReplays());
                syncTable(c, uuidStr, "player_purchased_designs", "design_key", data.getPurchasedDesigns());

                // Notified ranks
                try (PreparedStatement del = c.prepareStatement(
                        "DELETE FROM player_notified_ranks WHERE uuid = ?")) {
                    del.setString(1, uuidStr); del.executeUpdate();
                }
                try (PreparedStatement ins = c.prepareStatement(
                        "INSERT IGNORE INTO player_notified_ranks (uuid, map_name, rank_name) VALUES (?,?,?)")) {
                    for (Map.Entry<String, java.util.Set<String>> mapEntry : data.getNotifiedRanks().entrySet()) {
                        for (String rankName : mapEntry.getValue()) {
                            ins.setString(1, uuidStr); ins.setString(2, mapEntry.getKey()); ins.setString(3, rankName);
                            ins.addBatch();
                        }
                    }
                    ins.executeBatch();
                }

                // Custom lengths (saved, persistent)
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO player_custom_lengths (uuid, map_name, length) VALUES (?,?,?) " +
                        "ON DUPLICATE KEY UPDATE length=VALUES(length)")) {
                    for (Map.Entry<String, Integer> e : data.getSavedCustomLengthsMap().entrySet()) {
                        if (e.getValue() > 0) {
                            ps.setString(1, uuidStr); ps.setString(2, e.getKey()); ps.setInt(3, e.getValue());
                            ps.addBatch();
                        }
                    }
                    ps.executeBatch();
                }

                // Selected designs
                try (PreparedStatement del = c.prepareStatement(
                        "DELETE FROM player_selected_designs WHERE uuid = ?")) {
                    del.setString(1, uuidStr); del.executeUpdate();
                }
                try (PreparedStatement ins = c.prepareStatement(
                        "INSERT IGNORE INTO player_selected_designs (uuid, map_name, template_key) VALUES (?,?,?)")) {
                    for (Map.Entry<String, String> e : data.getSelectedDesigns().entrySet()) {
                        ins.setString(1, uuidStr); ins.setString(2, e.getKey()); ins.setString(3, e.getValue());
                        ins.addBatch();
                    }
                    ins.executeBatch();
                }

                // Infinite distances upsert
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO player_infinite_distances (uuid, map_name, distance, time) VALUES (?,?,?,?) " +
                        "ON DUPLICATE KEY UPDATE distance=VALUES(distance), time=VALUES(time)")) {
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
                try (PreparedStatement del = c.prepareStatement(
                        "DELETE FROM player_booster_inventory WHERE uuid = ?")) {
                    del.setString(1, uuidStr); del.executeUpdate();
                }
                try (PreparedStatement ins = c.prepareStatement(
                        "INSERT IGNORE INTO player_booster_inventory (uuid, type_id, quantity) VALUES (?,?,?)")) {
                    for (Map.Entry<String, Integer> e : data.getBoosterInventory().entrySet()) {
                        if (e.getValue() > 0) {
                            ins.setString(1, uuidStr); ins.setString(2, e.getKey()); ins.setInt(3, e.getValue());
                            ins.addBatch();
                        }
                    }
                    ins.executeBatch();
                }

                // Custom-length all-time bests upsert
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO player_custom_length_bests (uuid, map_name, distance, best_time) VALUES (?,?,?,?) " +
                        "ON DUPLICATE KEY UPDATE best_time=VALUES(best_time)")) {
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

                c.commit();
            } catch (SQLException e) {
                try { c.rollback(); } catch (SQLException ignored) {}
                throw e;
            } finally {
                // Never let a failed reset leak a transaction-mode connection back to the pool
                try { c.setAutoCommit(true); } catch (SQLException ignored) {}
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[MySQL] Failed to save player: " + data.getUuid(), e);
        }
    }

    private static final java.util.Set<String> ALLOWED_SYNC_TABLES = new java.util.HashSet<>(
            java.util.Arrays.asList("player_purchased_blocks", "player_favorites", "player_purchased_designs"));
    private static final java.util.Set<String> ALLOWED_SYNC_COLS = new java.util.HashSet<>(
            java.util.Arrays.asList("block_key", "replay_file", "design_key"));

    private void syncTable(Connection c, String uuid, String table, String valueCol, Set<String> values)
            throws SQLException {
        if (!ALLOWED_SYNC_TABLES.contains(table) || !ALLOWED_SYNC_COLS.contains(valueCol)) {
            throw new SQLException("Rejected unsafe table/column in syncTable: " + table + "/" + valueCol);
        }
        try (PreparedStatement del = c.prepareStatement(
                "DELETE FROM " + table + " WHERE uuid = ?")) {
            del.setString(1, uuid);
            del.executeUpdate();
        }
        if (!values.isEmpty()) {
            try (PreparedStatement ins = c.prepareStatement(
                    "INSERT IGNORE INTO " + table + " (uuid, " + valueCol + ") VALUES (?,?)")) {
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
            // Refresh asynchronously — stale cache is returned until the query completes
            org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                List<Long> times = new ArrayList<Long>();
                try (Connection c = borrowConnection();
                     PreparedStatement ps = c.prepareStatement(
                             "SELECT best_time FROM player_map_stats WHERE map_name = ? AND best_time > 0")) {
                    ps.setString(1, mapName);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) times.add(rs.getLong("best_time"));
                    }
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "[MySQL] Failed to query best times for: " + mapName, e);
                } finally {
                    refreshing.remove(mapName);
                }
                long[] result = new long[times.size()];
                for (int i = 0; i < times.size(); i++) result[i] = times.get(i);
                bestTimesCache.put(mapName, result);
                bestTimesCacheTime.put(mapName, System.currentTimeMillis());
            });
        }

        return cached != null ? cached : new long[0];
    }

    @Override
    public void invalidateBestTimesCache(String mapName) {
        bestTimesCacheTime.remove(mapName);
    }

    @Override
    public java.util.List<java.util.Map.Entry<String, Long>> getTopPlayerTimesForMap(
            String mapName, int limit) {

        List<java.util.Map.Entry<String, Long>> results = new ArrayList<java.util.Map.Entry<String, Long>>();
        try (Connection c = borrowConnection();
             PreparedStatement ps = c.prepareStatement(
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
            plugin.getLogger().log(Level.WARNING, "[MySQL] Failed to query top players for: " + mapName, e);
        }
        return results;
    }

    @Override
    public java.util.List<java.util.Map.Entry<String, long[]>> getTopInfiniteDistancesForMap(
            String mapName, int limit) {

        List<java.util.Map.Entry<String, long[]>> results = new ArrayList<java.util.Map.Entry<String, long[]>>();
        try (Connection c = borrowConnection();
             PreparedStatement ps = c.prepareStatement(
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
            plugin.getLogger().log(Level.WARNING, "[MySQL] Failed to query top infinite distances for: " + mapName, e);
        }
        return results;
    }

    @Override
    public void shutdown() {
        synchronized (pool) {
            for (int i = 0; i < POOL_SIZE; i++) {
                try {
                    if (pool[i] != null && !pool[i].isClosed()) pool[i].close();
                } catch (SQLException ignored) {}
                pool[i] = null;
            }
        }
    }
}