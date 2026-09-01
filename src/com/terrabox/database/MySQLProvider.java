package com.terrabox.database;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class MySQLProvider implements DatabaseProvider {
    private final String url;
    private final String user;
    private final String pass;
    private Connection connection;

    public MySQLProvider(String url, String user, String pass) {
        this.url = url;
        this.user = user;
        this.pass = pass;
    }

    @Override
    public void init() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL JDBC驱动未找到: " + e.getMessage());
        }
        connection = DriverManager.getConnection(url, user, pass);
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
        return "MySQL";
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // 玩家数据表
            stmt.execute("CREATE TABLE IF NOT EXISTS players (" +
                "uuid VARCHAR(36) PRIMARY KEY," +
                "name VARCHAR(64)," +
                "kills INT DEFAULT 0," +
                "deaths INT DEFAULT 0," +
                "stats LONGTEXT," +
                "inventory LONGTEXT," +
                "last_login TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            // 箱子数据表
            stmt.execute("CREATE TABLE IF NOT EXISTS boxes (" +
                "id VARCHAR(36) PRIMARY KEY," +
                "world VARCHAR(64)," +
                "x INT," +
                "y INT," +
                "z INT," +
                "loot_data LONGTEXT," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "INDEX idx_world (world)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            // 对局数据表
            stmt.execute("CREATE TABLE IF NOT EXISTS games (" +
                "id VARCHAR(36) PRIMARY KEY," +
                "state VARCHAR(16)," +
                "mode VARCHAR(16)," +
                "players LONGTEXT," +
                "started_at TIMESTAMP," +
                "ended_at TIMESTAMP," +
                "INDEX idx_state (state)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
    }

    public ResultSet query(String sql) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(sql);
        return ps.executeQuery();
    }

    public int update(String sql) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(sql);
        return ps.executeUpdate();
    }

    public Map<String, Object> getPlayerData(String uuid) throws SQLException {
        Map<String, Object> data = new HashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM players WHERE uuid = ?")) {
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
}
