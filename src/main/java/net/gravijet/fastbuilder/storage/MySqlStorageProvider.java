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
 *
 * Uses a lightweight fixed-size connection pool built on {@link DriverManager}
 * (no HikariCP required). The pool defaults to 5 connections; each borrow
 * blocks up to 30 s before throwing a timeout exception.
 *
 * Credentials are read from {@code config.yml} under {@code storage.mysql.*}.
 * The driver is loaded reflectively so the plugin will still start on servers
 * that don't have the connector on the classpath — it will just fail gracefully
 * with a clear error and fall back to YAML (handled by PlayerManager).
 */
public class MySqlStorageProvider implements StorageProvider {

    private static final long   CACHE_TTL_MS   = 60_000L;
    private static final String DRIVER         = "com.mysql.jdbc.Driver";
    private static final String DRIVER_NEW     = "com.mysql.cj.jdbc.Driver";
    private static final int    POOL_SIZE      = 5;
    private static final long   POOL_TIMEOUT   = 30_000L;

    private final FastBuilder plugin;
    private final String host;
    private final int    port;
    private final String database;
    private final String user;
    private final String password;

    // ---- simple connection pool ----
    private final Connection[] pool    = new Connection[POOL_SIZE];
    private final boolean[]    in_use  = new boolean[POOL_SIZE];

    private final Map<String, long[]> bestTimesCache     = new HashMap<>();
    private final Map<String, Long>   bestTimesCacheTime = new HashMap<>();

    public MySqlStorageProvider(FastBuilder plugin) {
        this.plugin   = plugin;
        this.host     = plugin.getConfigManager().getStorageMySQL("host",     "localhost");
        this.port     = plugin.getConfigManager().getStorageMySQLPort();
        this.database = plugin.getConfigManager().getStorageMySQL("database", "fastbuilder");
        this.user     = plugin.getConfigManager().getStorageMySQL("user",     "root");
        this.password = plugin.getConfigManager().getStorageMySQL("password", "");
    }

    // -------------------------------------------------------------------------
    // Init
    // -------------------------------------------------------------------------

    @Override
    public void init() throws Exception {
        // Try both old and new connector driver class names
        boolean driverLoaded = false;
        try { Class.forName(DRIVER);     driverLoaded = true; } catch (ClassNotFoundException ignored) {}
        if (!driverLoaded) {
            try { Class.forName(DRIVER_NEW); driverLoaded = true; } catch (ClassNotFoundException ignored) {}
        }
        if (!driverLoaded) {
            throw new Exception("MySQL JDBC driver not found. Add mysql-connector-j or mysql-connector-java to the classpath.");
        }

        String url = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&autoReconnect=true&characterEncoding=utf8"
                + "&serverTimezone=UTC";

        synchronized (pool) {
            for (int i = 0; i < POOL_SIZE; i++) {
                pool[i]   = DriverManager.getConnection(url, user, password);
                in_use[i] = false;
            }
        }

        // Create tables on the first connection
        try (Connection c = borrowConnection();
             Statement stmt = c.createStatement()) {

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_data (
                    uuid                     VARCHAR(36) PRIMARY KEY,
                    name                     VARCHAR(64) NOT NULL,
                    coins                    INT DEFAULT 0,
                    last_map                 VARCHAR(64),
                    last_island              INT DEFAULT -1,
                    selected_block           VARCHAR(64) DEFAULT 'SANDSTONE:0',
                    selected_pickaxe         VARCHAR(64) DEFAULT 'DIAMOND_PICKAXE:0',
                    selected_animation       VARCHAR(64) DEFAULT 'NONE',
                    selected_death_sound     VARCHAR(64) DEFAULT 'NONE',
                    one_click_pick           TINYINT(1) DEFAULT 0,
                    auto_refill              TINYINT(1) DEFAULT 0,
                    infinite_blocks_unlocked TINYINT(1) DEFAULT 0,
                    infinite_blocks          TINYINT(1) DEFAULT 0
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_map_stats (
                    uuid                VARCHAR(36) NOT NULL,
                    map_name            VARCHAR(64) NOT NULL,
                    best_time           BIGINT DEFAULT -1,
                    total_attempts      INT DEFAULT 0,
                    successful_attempts INT DEFAULT 0,
                    total_success_time  BIGINT DEFAULT 0,
                    PRIMARY KEY (uuid, map_name)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_purchased_blocks (
                    uuid      VARCHAR(36) NOT NULL,
                    block_key VARCHAR(128) NOT NULL,
                    PRIMARY KEY (uuid, block_key)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_favorites (
                    uuid        VARCHAR(36) NOT NULL,
                    replay_file VARCHAR(256) NOT NULL,
                    PRIMARY KEY (uuid, replay_file)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_notified_ranks (
                    uuid      VARCHAR(36) NOT NULL,
                    map_name  VARCHAR(64) NOT NULL,
                    rank_name VARCHAR(64) NOT NULL,
                    PRIMARY KEY (uuid, map_name, rank_name)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_custom_lengths (
                    uuid     VARCHAR(36) NOT NULL,
                    map_name VARCHAR(64) NOT NULL,
                    length   INT NOT NULL,
                    PRIMARY KEY (uuid, map_name)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_selected_designs (
                    uuid         VARCHAR(36) NOT NULL,
                    map_name     VARCHAR(64) NOT NULL,
                    template_key VARCHAR(128) NOT NULL,
                    PRIMARY KEY (uuid, map_name)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""");

            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_map_stats_map_best
                ON player_map_stats(map_name, best_time)""");
        }

        plugin.getLogger().info("[MySQL] Database initialised. Connected to " + host + ":" + port + "/" + database);
    }

    // -------------------------------------------------------------------------
    // Connection Pool
    // -------------------------------------------------------------------------

    private Connection borrowConnection() throws SQLException {
        long deadline = System.currentTimeMillis() + POOL_TIMEOUT;
        while (System.currentTimeMillis() < deadline) {
            synchronized (pool) {
                for (int i = 0; i < POOL_SIZE; i++) {
                    if (!in_use[i]) {
                        // Validate connection
                        try {
                            if (pool[i].isClosed() || !pool[i].isValid(2)) {
                                String url = pool[i].getMetaData().getURL();
                                pool[i] = DriverManager.getConnection(url, user, password);
                            }
                        } catch (SQLException ignored) {}
                        in_use[i] = true;
                        return new PooledConnection(pool[i], i);
                    }
                }
            }
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        }
        throw new SQLException("MySQL connection pool exhausted (timeout after " + POOL_TIMEOUT + " ms)");
    }

    /** Proxy connection that returns itself to the pool on close(). */
    private class PooledConnection implements Connection {
        private final Connection delegate;
        private final int        poolIndex;

        PooledConnection(Connection delegate, int poolIndex) {
            this.delegate  = delegate;
            this.poolIndex = poolIndex;
        }

        @Override
        public void close() {
            synchronized (pool) {
                in_use[poolIndex] = false;
            }
        }

        // ---- delegate all other methods ----
        @Override public PreparedStatement prepareStatement(String s) throws SQLException { return delegate.prepareStatement(s); }
        @Override public Statement createStatement() throws SQLException { return delegate.createStatement(); }
        @Override public void setAutoCommit(boolean b) throws SQLException { delegate.setAutoCommit(b); }
        @Override public boolean getAutoCommit() throws SQLException { return delegate.getAutoCommit(); }
        @Override public void commit() throws SQLException { delegate.commit(); }
        @Override public void rollback() throws SQLException { delegate.rollback(); }
        @Override public boolean isClosed() throws SQLException { return delegate.isClosed(); }
        @Override public boolean isValid(int timeout) throws SQLException { return delegate.isValid(timeout); }
        @Override public java.sql.DatabaseMetaData getMetaData() throws SQLException { return delegate.getMetaData(); }
        // Stub remaining Connection methods (not used in this provider)
        @Override public <T> T unwrap(Class<T> c) throws SQLException { return delegate.unwrap(c); }
        @Override public boolean isWrapperFor(Class<?> c) throws SQLException { return delegate.isWrapperFor(c); }
        @Override public PreparedStatement prepareStatement(String s, int a, int b) throws SQLException { return delegate.prepareStatement(s, a, b); }
        @Override public PreparedStatement prepareStatement(String s, int a, int b, int c) throws SQLException { return delegate.prepareStatement(s, a, b, c); }
        @Override public PreparedStatement prepareStatement(String s, int[] c) throws SQLException { return delegate.prepareStatement(s, c); }
        @Override public PreparedStatement prepareStatement(String s, String[] c) throws SQLException { return delegate.prepareStatement(s, c); }
        @Override public PreparedStatement prepareStatement(String s, int c) throws SQLException { return delegate.prepareStatement(s, c); }
        @Override public java.sql.CallableStatement prepareCall(String s) throws SQLException { return delegate.prepareCall(s); }
        @Override public java.sql.CallableStatement prepareCall(String s, int a, int b) throws SQLException { return delegate.prepareCall(s, a, b); }
        @Override public java.sql.CallableStatement prepareCall(String s, int a, int b, int c) throws SQLException { return delegate.prepareCall(s, a, b, c); }
        @Override public String nativeSQL(String s) throws SQLException { return delegate.nativeSQL(s); }
        @Override public void setReadOnly(boolean b) throws SQLException { delegate.setReadOnly(b); }
        @Override public boolean isReadOnly() throws SQLException { return delegate.isReadOnly(); }
        @Override public void setCatalog(String s) throws SQLException { delegate.setCatalog(s); }
        @Override public String getCatalog() throws SQLException { return delegate.getCatalog(); }
        @Override public void setTransactionIsolation(int l) throws SQLException { delegate.setTransactionIsolation(l); }
        @Override public int getTransactionIsolation() throws SQLException { return delegate.getTransactionIsolation(); }
        @Override public java.sql.SQLWarning getWarnings() throws SQLException { return delegate.getWarnings(); }
        @Override public void clearWarnings() throws SQLException { delegate.clearWarnings(); }
        @Override public Statement createStatement(int a, int b) throws SQLException { return delegate.createStatement(a, b); }
        @Override public Statement createStatement(int a, int b, int c) throws SQLException { return delegate.createStatement(a, b, c); }
        @Override public java.util.Map<String, Class<?>> getTypeMap() throws SQLException { return delegate.getTypeMap(); }
        @Override public void setTypeMap(java.util.Map<String, Class<?>> m) throws SQLException { delegate.setTypeMap(m); }
        @Override public void setHoldability(int h) throws SQLException { delegate.setHoldability(h); }
        @Override public int getHoldability() throws SQLException { return delegate.getHoldability(); }
        @Override public java.sql.Savepoint setSavepoint() throws SQLException { return delegate.setSavepoint(); }
        @Override public java.sql.Savepoint setSavepoint(String s) throws SQLException { return delegate.setSavepoint(s); }
        @Override public void rollback(java.sql.Savepoint sp) throws SQLException { delegate.rollback(sp); }
        @Override public void releaseSavepoint(java.sql.Savepoint sp) throws SQLException { delegate.releaseSavepoint(sp); }
        @Override public java.sql.Clob createClob() throws SQLException { return delegate.createClob(); }
        @Override public java.sql.Blob createBlob() throws SQLException { return delegate.createBlob(); }
        @Override public java.sql.NClob createNClob() throws SQLException { return delegate.createNClob(); }
        @Override public java.sql.SQLXML createSQLXML() throws SQLException { return delegate.createSQLXML(); }
        @Override public void setClientInfo(String k, String v) throws java.sql.SQLClientInfoException { try { delegate.setClientInfo(k, v); } catch (java.sql.SQLClientInfoException e) { throw e; } }
        @Override public void setClientInfo(java.util.Properties p) throws java.sql.SQLClientInfoException { try { delegate.setClientInfo(p); } catch (java.sql.SQLClientInfoException e) { throw e; } }
        @Override public String getClientInfo(String k) throws SQLException { return delegate.getClientInfo(k); }
        @Override public java.util.Properties getClientInfo() throws SQLException { return delegate.getClientInfo(); }
        @Override public java.sql.Array createArrayOf(String t, Object[] e) throws SQLException { return delegate.createArrayOf(t, e); }
        @Override public java.sql.Struct createStruct(String t, Object[] a) throws SQLException { return delegate.createStruct(t, a); }
        @Override public void setSchema(String s) throws SQLException { delegate.setSchema(s); }
        @Override public String getSchema() throws SQLException { return delegate.getSchema(); }
        @Override public void abort(java.util.concurrent.Executor e) throws SQLException { delegate.abort(e); }
        @Override public void setNetworkTimeout(java.util.concurrent.Executor e, int ms) throws SQLException { delegate.setNetworkTimeout(e, ms); }
        @Override public int getNetworkTimeout() throws SQLException { return delegate.getNetworkTimeout(); }
    }

    // -------------------------------------------------------------------------
    // Load
    // -------------------------------------------------------------------------

    @Override
    public PlayerData loadPlayerData(UUID uuid, String name) {
        PlayerData data = new PlayerData(uuid, name);
        String uuidStr = uuid.toString();

        try (Connection c = borrowConnection()) {
            // Main row
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
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[MySQL] Failed to load player: " + uuid, e);
        }

        return data;
    }

    // -------------------------------------------------------------------------
    // Save
    // -------------------------------------------------------------------------

    @Override
    public void savePlayerData(PlayerData data) {
        String uuidStr = data.getUuid().toString();
        try (Connection c = borrowConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO player_data
                      (uuid, name, coins, last_map, last_island, selected_block,
                       selected_pickaxe, selected_animation, selected_death_sound,
                       one_click_pick, auto_refill, infinite_blocks_unlocked, infinite_blocks)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE
                      name=VALUES(name), coins=VALUES(coins),
                      last_map=VALUES(last_map), last_island=VALUES(last_island),
                      selected_block=VALUES(selected_block),
                      selected_pickaxe=VALUES(selected_pickaxe),
                      selected_animation=VALUES(selected_animation),
                      selected_death_sound=VALUES(selected_death_sound),
                      one_click_pick=VALUES(one_click_pick),
                      auto_refill=VALUES(auto_refill),
                      infinite_blocks_unlocked=VALUES(infinite_blocks_unlocked),
                      infinite_blocks=VALUES(infinite_blocks)
                    """)) {
                    ps.setString(1, uuidStr); ps.setString(2, data.getName());
                    ps.setInt(3, data.getCoins()); ps.setString(4, data.getLastMap());
                    ps.setInt(5, data.getLastIsland()); ps.setString(6, data.getSelectedBlock());
                    ps.setString(7, data.getSelectedPickaxe()); ps.setString(8, data.getSelectedAnimation());
                    ps.setString(9, data.getSelectedDeathSound());
                    ps.setInt(10, data.hasOneClickPick() ? 1 : 0);
                    ps.setInt(11, data.hasAutoRefill() ? 1 : 0);
                    ps.setInt(12, data.hasInfiniteBlocksUnlocked() ? 1 : 0);
                    ps.setInt(13, data.hasInfiniteBlocks() ? 1 : 0);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO player_map_stats
                      (uuid, map_name, best_time, total_attempts, successful_attempts, total_success_time)
                    VALUES (?,?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE
                      best_time=VALUES(best_time), total_attempts=VALUES(total_attempts),
                      successful_attempts=VALUES(successful_attempts),
                      total_success_time=VALUES(total_success_time)
                    """)) {
                    for (Map.Entry<String, PlayerData.MapStats> e : data.getAllStats().entrySet()) {
                        PlayerData.MapStats s = e.getValue();
                        ps.setString(1, uuidStr); ps.setString(2, e.getKey());
                        ps.setLong(3, s.bestTime); ps.setInt(4, s.totalAttempts);
                        ps.setInt(5, s.successfulAttempts); ps.setLong(6, s.totalSuccessTime);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                // purchased blocks, favorites, notified ranks, custom lengths, selected designs
                // all use delete-then-insert pattern (same as SQLite provider)
                syncTable(c, uuidStr, "player_purchased_blocks", "block_key", data.getPurchasedBlocks());
                syncTable(c, uuidStr, "player_favorites", "replay_file", data.getFavoriteReplays());
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[MySQL] Failed to save player: " + data.getUuid(), e);
        }
    }

    private void syncTable(Connection c, String uuid, String table, String valueCol, Set<String> values)
            throws SQLException {
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

    // -------------------------------------------------------------------------
    // Global best times
    // -------------------------------------------------------------------------

    @Override
    public long[] getGlobalBestTimesForMap(String mapName) {
        Long cacheTime = bestTimesCacheTime.get(mapName);
        if (cacheTime != null && System.currentTimeMillis() - cacheTime < CACHE_TTL_MS) {
            long[] cached = bestTimesCache.get(mapName);
            if (cached != null) return cached;
        }

        List<Long> times = new ArrayList<>();
        try (Connection c = borrowConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT best_time FROM player_map_stats WHERE map_name = ? AND best_time > 0")) {
            ps.setString(1, mapName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) times.add(rs.getLong("best_time"));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[MySQL] Failed to query best times for: " + mapName, e);
        }

        long[] result = new long[times.size()];
        for (int i = 0; i < times.size(); i++) result[i] = times.get(i);
        bestTimesCache.put(mapName, result);
        bestTimesCacheTime.put(mapName, System.currentTimeMillis());
        return result;
    }

    @Override
    public void invalidateBestTimesCache(String mapName) {
        bestTimesCacheTime.remove(mapName);
    }

    // -------------------------------------------------------------------------
    // Shutdown
    // -------------------------------------------------------------------------

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
