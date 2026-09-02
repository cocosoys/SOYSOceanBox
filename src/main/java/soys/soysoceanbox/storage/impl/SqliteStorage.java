package soys.soysoceanbox.storage.impl;

import org.bukkit.configuration.ConfigurationSection;
import soys.soysoceanbox.SOYSOceanBox;
import soys.soysoceanbox.storage.StorageType;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * SQLite 存储后端。
 * <p>单文件数据库，无需额外服务，适合需要 SQL 查询能力但不想部署 MySQL 的场景。</p>
 */
public class SqliteStorage extends SqlStorage {

    private File databaseFile;

    public SqliteStorage(SOYSOceanBox plugin) {
        super(plugin);
    }

    @Override
    public StorageType getType() {
        return StorageType.SQLITE;
    }

    @Override
    public void initialize() throws Exception {
        ConfigurationSection section = plugin.getConfigManager().getBackendSection("sqlite");
        String path = section == null ? "data/players.db" : section.getString("file", "data/players.db");
        this.tablePrefix = section == null ? "mc_sob_" : section.getString("table-prefix", "mc_sob_");

        this.databaseFile = new File(plugin.getDataFolder(), path);
        File parent = databaseFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("无法创建数据目录: " + parent.getAbsolutePath());
        }
        super.initialize();
    }

    @Override
    public String describe() {
        return databaseFile == null ? "未初始化" : databaseFile.getPath().replace('\\', '/');
    }

    @Override
    protected String getDriverClass() {
        return "org.sqlite.JDBC";
    }

    @Override
    protected Connection createConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + databaseFile.getAbsolutePath());
        // 开启外键与 WAL，提升并发读性能
        try (java.sql.Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA journal_mode = WAL");
        }
        return connection;
    }

    @Override
    protected String[] getSchemaStatements() {
        return new String[]{
                "CREATE TABLE IF NOT EXISTS " + playersTable() + " ("
                        + "uuid TEXT NOT NULL PRIMARY KEY,"  // uuid 即主键索引，按 uuid 查找天然高效
                        + "name TEXT,"
                        + "last_draw INTEGER NOT NULL DEFAULT 0,"
                        + "total_draws INTEGER NOT NULL DEFAULT 0,"
                        + "daily_key TEXT NOT NULL DEFAULT '',"
                        + "daily_count INTEGER NOT NULL DEFAULT 0,"
                        + "weekly_key TEXT NOT NULL DEFAULT '',"
                        + "weekly_count INTEGER NOT NULL DEFAULT 0,"
                        + "draws_since_big_win INTEGER NOT NULL DEFAULT 0,"
                        + "active_pool TEXT NOT NULL DEFAULT '',"
                        + "pending TEXT,"
                        + "history TEXT"
                        + ")",
                "CREATE INDEX IF NOT EXISTS idx_" + tablePrefix + "players_name"
                        + " ON " + playersTable() + " (name)",
                "CREATE INDEX IF NOT EXISTS idx_" + tablePrefix + "players_active_pool"
                        + " ON " + playersTable() + " (active_pool)" // 按奖池筛选/统计（大数据量性能）
        };
    }

    @Override
    protected String[] getMigrationStatements() {
        String t = playersTable();
        return new String[]{
                "ALTER TABLE " + t + " ADD COLUMN daily_key TEXT NOT NULL DEFAULT ''",
                "ALTER TABLE " + t + " ADD COLUMN daily_count INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE " + t + " ADD COLUMN weekly_key TEXT NOT NULL DEFAULT ''",
                "ALTER TABLE " + t + " ADD COLUMN weekly_count INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE " + t + " ADD COLUMN draws_since_big_win INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE " + t + " ADD COLUMN active_pool TEXT NOT NULL DEFAULT ''",
                "ALTER TABLE " + t + " ADD COLUMN history TEXT",
                // 二级索引：提升按奖池筛选/统计的查询性能；索引已存在时忽略
                "CREATE INDEX IF NOT EXISTS idx_" + tablePrefix + "players_active_pool ON " + t + " (active_pool)"
        };
    }
}
