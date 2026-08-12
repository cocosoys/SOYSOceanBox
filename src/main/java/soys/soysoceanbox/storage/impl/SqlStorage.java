package soys.soysoceanbox.storage.impl;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import soys.soysoceanbox.SOYSOceanBox;
import soys.soysoceanbox.lottery.LotteryPlayer;
import soys.soysoceanbox.lottery.PendingReward;
import soys.soysoceanbox.storage.DataStorage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SQL 存储后端的公共实现。
 * <p>
 * SQLite 与 MySQL 共用同一套表结构与 CRUD 逻辑，子类只需提供：
 * 驱动类名、JDBC URL、连接创建方式与建表语句方言。
 * </p>
 * <p>
 * 连接策略：维持单个长连接并在每次使用前做有效性探测，配合对象锁串行化访问。
 * 插件的写操作本身已经被 StorageManager 收敛到异步队列，无需引入连接池。
 * </p>
 */
public abstract class SqlStorage implements DataStorage {

    protected final SOYSOceanBox plugin;
    protected final Object lock = new Object();

    protected String tablePrefix = "sob_";
    protected volatile boolean available = false;

    private Connection connection;

    protected SqlStorage(SOYSOceanBox plugin) {
        this.plugin = plugin;
    }

    // ================================================================
    //  子类需实现的方言部分
    // ================================================================

    /** JDBC 驱动类名 */
    protected abstract String getDriverClass();

    /** 创建一个全新的数据库连接 */
    protected abstract Connection createConnection() throws SQLException;

    /** 建表与建索引语句，按顺序执行 */
    protected abstract String[] getSchemaStatements();

    /**
     * 为已存在的旧表补充新增列的 ALTER 语句（容忍式执行，列已存在时忽略）。
     * 不同数据库方言的列类型不同，由子类提供。
     */
    protected abstract String[] getMigrationStatements();

    // ================================================================
    //  表名
    // ================================================================

    protected String playersTable() {
        return tablePrefix + "players";
    }

    // ================================================================
    //  生命周期
    // ================================================================

    @Override
    public void initialize() throws Exception {
        try {
            Class.forName(getDriverClass());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("未找到 JDBC 驱动 " + getDriverClass()
                    + "，请确认服务端已提供该驱动或手动放入 libraries 目录");
        }
        synchronized (lock) {
            connection = createConnection();
            try (Statement statement = connection.createStatement()) {
                for (String sql : getSchemaStatements()) {
                    statement.execute(sql);
                }
                // 容忍式迁移：为已存在的旧表补充新增列，列已存在则忽略错误
                for (String sql : getMigrationStatements()) {
                    try {
                        statement.execute(sql);
                    } catch (SQLException e) {
                        String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
                        if (msg.contains("duplicate") || msg.contains("already exists")
                                || msg.contains("exist") || msg.contains("重复")) {
                            // 列已存在，属于预期情况，忽略
                            continue;
                        }
                        plugin.getLogger().warning("[" + getType().getId()
                                + "] 表结构迁移跳过: " + e.getMessage());
                    }
                }
            }
        }
        available = true;
    }

    @Override
    public void shutdown() {
        synchronized (lock) {
            available = false;
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ignored) {
                    // 关闭失败无需处理
                }
                connection = null;
            }
        }
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    /**
     * 获取一个可用连接，失效时自动重建。调用方必须持有 {@link #lock}。
     */
    protected Connection connection() throws SQLException {
        if (connection == null || connection.isClosed() || !connection.isValid(3)) {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ignored) {
                    // 旧连接关闭失败可忽略
                }
            }
            connection = createConnection();
        }
        return connection;
    }

    /**
     * 主动探测连接（保活任务使用）。
     */
    public void keepAlive() {
        synchronized (lock) {
            try {
                connection().isValid(3);
            } catch (SQLException e) {
                plugin.getLogger().warning("[" + getType().getId() + "] 保活探测失败: " + e.getMessage());
            }
        }
    }

    // ================================================================
    //  读
    // ================================================================

    @Override
    public LotteryPlayer loadPlayer(UUID uuid) throws Exception {
        synchronized (lock) {
            Connection conn = connection();
            String sql = "SELECT * FROM " + playersTable() + " WHERE uuid = ?";
            try (PreparedStatement statement = conn.prepareStatement(sql)) {
                statement.setString(1, uuid.toString());
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        return readPlayer(rs);
                    }
                }
            }
            return null;
        }
    }

    @Override
    public Collection<LotteryPlayer> loadAllPlayers() throws Exception {
        synchronized (lock) {
            Connection conn = connection();
            Map<UUID, LotteryPlayer> players = new HashMap<>();
            String sql = "SELECT * FROM " + playersTable();
            try (Statement statement = conn.createStatement();
                 ResultSet rs = statement.executeQuery(sql)) {
                while (rs.next()) {
                    LotteryPlayer player = readPlayer(rs);
                    if (player != null) {
                        players.put(player.getUuid(), player);
                    }
                }
            }
            return new ArrayList<>(players.values());
        }
    }

    @Override
    public int countPlayers() throws Exception {
        synchronized (lock) {
            try (Statement statement = connection().createStatement();
                 ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + playersTable())) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    // ================================================================
    //  写
    // ================================================================

    @Override
    public void savePlayer(LotteryPlayer player) throws Exception {
        synchronized (lock) {
            Connection conn = connection();
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                writePlayer(conn, player);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(autoCommit);
            }
        }
    }

    @Override
    public void savePlayers(Collection<LotteryPlayer> players) throws Exception {
        if (players.isEmpty()) {
            return;
        }
        synchronized (lock) {
            Connection conn = connection();
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                for (LotteryPlayer player : players) {
                    writePlayer(conn, player);
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(autoCommit);
            }
        }
    }

    @Override
    public void deletePlayer(UUID uuid) throws Exception {
        synchronized (lock) {
            try (PreparedStatement statement =
                         connection().prepareStatement("DELETE FROM " + playersTable() + " WHERE uuid = ?")) {
                statement.setString(1, uuid.toString());
                statement.executeUpdate();
            }
        }
    }

    @Override
    public void clear() throws Exception {
        synchronized (lock) {
            try (Statement statement = connection().createStatement()) {
                statement.executeUpdate("DELETE FROM " + playersTable());
            }
        }
    }

    // ================================================================
    //  内部
    // ================================================================

    private void writePlayer(Connection conn, LotteryPlayer player) throws SQLException {
        String sql = "REPLACE INTO " + playersTable()
                + " (uuid, name, last_draw, total_draws, daily_key, daily_count,"
                + " weekly_key, weekly_count, draws_since_big_win, active_pool, pending)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, player.getUuid().toString());
            statement.setString(2, player.getName());
            statement.setLong(3, player.getLastDrawTime());
            statement.setInt(4, player.getTotalDraws());
            statement.setString(5, player.getDailyKey());
            statement.setInt(6, player.getDailyCount());
            statement.setString(7, player.getWeeklyKey());
            statement.setInt(8, player.getWeeklyCount());
            statement.setInt(9, player.getDrawsSinceBigWin());
            statement.setString(10, player.getActivePool());
            statement.setString(11, serializePending(player.getPending()));
            statement.executeUpdate();
        }
    }

    private LotteryPlayer readPlayer(ResultSet rs) throws SQLException {
        UUID uuid = parseUuid(rs.getString("uuid"));
        if (uuid == null) {
            return null;
        }
        LotteryPlayer player = new LotteryPlayer(uuid, rs.getString("name"));
        player.setLastDrawTime(rs.getLong("last_draw"));
        player.setTotalDraws(rs.getInt("total_draws"));
        if (hasColumn(rs, "daily_key")) {
            player.setDailyKey(rs.getString("daily_key"));
            player.setDailyCount(rs.getInt("daily_count"));
            player.setWeeklyKey(rs.getString("weekly_key"));
            player.setWeeklyCount(rs.getInt("weekly_count"));
            player.setDrawsSinceBigWin(rs.getInt("draws_since_big_win"));
            player.setActivePool(rs.getString("active_pool"));
        }
        String pendingText = rs.getString("pending");
        for (PendingReward reward : deserializePending(pendingText)) {
            player.addPending(reward);
        }
        return player;
    }

    /**
     * 判断结果集中是否存在某列，兼容旧版本表结构（缺少新增列时安全降级）。
     */
    private boolean hasColumn(ResultSet rs, String column) {
        try {
            ResultSetMetaData meta = rs.getMetaData();
            int count = meta.getColumnCount();
            for (int i = 1; i <= count; i++) {
                if (column.equalsIgnoreCase(meta.getColumnLabel(i))
                        || column.equalsIgnoreCase(meta.getColumnName(i))) {
                    return true;
                }
            }
            return false;
        } catch (SQLException e) {
            return false;
        }
    }

    // ================================================================
    //  待领取奖励的序列化（YAML 文本列）
    // ================================================================

    protected String serializePending(List<PendingReward> pending) {
        if (pending == null || pending.isEmpty()) {
            return "";
        }
        YamlConfiguration config = new YamlConfiguration();
        for (PendingReward reward : pending) {
            reward.write(config.createSection(reward.getClaimId().toString()));
        }
        return config.saveToString();
    }

    protected List<PendingReward> deserializePending(String text) {
        List<PendingReward> result = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return result;
        }
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(text);
        } catch (Exception e) {
            plugin.getLogger().warning("[" + getType().getId() + "] 解析待领取奖励失败: " + e.getMessage());
            return result;
        }
        ConfigurationSection root = config;
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section != null) {
                result.add(PendingReward.read(section));
            }
        }
        return result;
    }

    protected UUID parseUuid(String input) {
        if (input == null) {
            return null;
        }
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
