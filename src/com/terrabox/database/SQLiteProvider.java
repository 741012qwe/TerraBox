package com.terrabox.database;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class SQLiteProvider implements DatabaseProvider {
    private final String dbPath;
    private Connection connection;

    public SQLiteProvider(String dbPath) {
        this.dbPath = dbPath;
    }

    @Override
    public void init() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC驱动未找到: " + e.getMessage());
        }
        // 启用外键和 WAL 模式（提升并发写入性能）
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
            stmt.execute("PRAGMA journal_mode = WAL");
            stmt.execute("PRAGMA synchronous = NORMAL");
        }
        createTables();
    }

    @Override
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Override
    public boolean isValid() {
        try {
            return connection != null && !connection.isClosed() && connection.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getType() {
        return "SQLite";
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // 玩家数据表
            stmt.execute("CREATE TABLE IF NOT EXISTS players (" +
                "uuid VARCHAR(36) PRIMARY KEY," +
                "name VARCHAR(32) NOT NULL," +
                "kills INTEGER DEFAULT 0," +
                "deaths INTEGER DEFAULT 0," +
                "stats TEXT," +
                "inventory TEXT," +
                "last_login TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")");
            // 箱子数据表
            stmt.execute("CREATE TABLE IF NOT EXISTS boxes (" +
                "id VARCHAR(36) PRIMARY KEY," +
                "world VARCHAR(64) NOT NULL," +
                "x INTEGER NOT NULL," +
                "y INTEGER NOT NULL," +
                "z INTEGER NOT NULL," +
                "loot_data TEXT," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")");
            // 对局数据表
            stmt.execute("CREATE TABLE IF NOT EXISTS games (" +
                "id VARCHAR(36) PRIMARY KEY," +
                "state VARCHAR(16) NOT NULL," +
                "mode VARCHAR(16) NOT NULL," +
                "players TEXT," +
                "started_at TIMESTAMP," +
                "ended_at TIMESTAMP" +
                ")");
            // 索引优化（避免全表扫描）
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_players_last_login ON players(last_login)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_boxes_world ON boxes(world)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_games_state ON games(state)");
        }
    }

    /** 安全查询：返回 ResultSet（调用方负责关闭） */
    public ResultSet query(String sql) throws SQLException {
        return connection.prepareStatement(sql).executeQuery();
    }

    /** 安全更新：返回影响行数 */
    public int update(String sql) throws SQLException {
        return connection.prepareStatement(sql).executeUpdate();
    }

    /** 参数化查询：查找玩家 */
    public Map<String, Object> getPlayerData(String uuid) throws SQLException {
        Map<String, Object> data = new HashMap<>();
        String sql = "SELECT uuid, name, kills, deaths, stats, inventory, last_login " +
                     "FROM players WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    data.put("uuid", rs.getString("uuid"));
                    data.put("name", rs.getString("name"));
                    data.put("kills", rs.getInt("kills"));
                    data.put("deaths", rs.getInt("deaths"));
                    data.put("stats", rs.getString("stats"));
                    data.put("inventory", rs.getString("inventory"));
                    data.put("last_login", rs.getTimestamp("last_login"));
                }
            }
        }
        return data;
    }

    /** 参数化插入/更新玩家 */
    public void savePlayer(String uuid, String name, int kills, int deaths, String stats, String inventory) throws SQLException {
        String sql = "INSERT INTO players (uuid, name, kills, deaths, stats, inventory, last_login) " +
                     "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP) " +
                     "ON CONFLICT(uuid) DO UPDATE SET " +
                     "name=excluded.name, kills=excluded.kills, deaths=excluded.deaths, " +
                     "stats=excluded.stats, inventory=excluded.inventory, last_login=CURRENT_TIMESTAMP";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, name);
            ps.setInt(3, kills);
            ps.setInt(4, deaths);
            ps.setString(5, stats);
            ps.setString(6, inventory);
            ps.executeUpdate();
        }
    }
}