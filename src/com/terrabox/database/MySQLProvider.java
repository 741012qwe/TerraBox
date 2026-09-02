package com.terrabox.database;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class MySQLProvider implements DatabaseProvider {
    private final String url;
    private final String username;
    private final String password;
    private Connection connection;

    public MySQLProvider(String url, String username, String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    @Override
    public void init() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL JDBC驱动未找到: " + e.getMessage());
        }
        String connUrl = url + "?connectTimeout=5000&socketTimeout=10000&autoReconnect=true";
        connection = DriverManager.getConnection(connUrl, username, password);
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SET NAMES utf8mb4");
            stmt.execute("SET CHARACTER SET utf8mb4");
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
        return "MySQL";
    }

    @Override
    public Connection getConnection() {
        return connection;
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS players (" +
                "uuid VARCHAR(36) PRIMARY KEY," +
                "name VARCHAR(32) NOT NULL," +
                "kills INTEGER DEFAULT 0," +
                "deaths INTEGER DEFAULT 0," +
                "stats TEXT," +
                "inventory TEXT," +
                "last_login TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            stmt.execute("CREATE TABLE IF NOT EXISTS boxes (" +
                "id VARCHAR(36) PRIMARY KEY," +
                "world VARCHAR(64) NOT NULL," +
                "x INTEGER NOT NULL," +
                "y INTEGER NOT NULL," +
                "z INTEGER NOT NULL," +
                "loot_data TEXT," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "INDEX idx_world (world)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            stmt.execute("CREATE TABLE IF NOT EXISTS games (" +
                "id VARCHAR(36) PRIMARY KEY," +
                "state VARCHAR(16) NOT NULL," +
                "mode VARCHAR(16) NOT NULL," +
                "players TEXT," +
                "started_at TIMESTAMP," +
                "ended_at TIMESTAMP," +
                "INDEX idx_state (state)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
    }

    public ResultSet query(String sql) throws SQLException {
        return connection.prepareStatement(sql).executeQuery();
    }

    public int update(String sql) throws SQLException {
        return connection.prepareStatement(sql).executeUpdate();
    }

    public Map<String, Object> getPlayerData(String uuid) throws SQLException {
        Map<String, Object> data = new HashMap<>();
        String sql = "SELECT uuid, name, kills, deaths, stats, inventory, last_login FROM players WHERE uuid = ?";
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

    public void savePlayer(String uuid, String name, int kills, int deaths, String stats, String inventory) throws SQLException {
        String sql = "INSERT INTO players (uuid, name, kills, deaths, stats, inventory, last_login) VALUES (?, ?, ?, ?, ?, ?, NOW()) ON DUPLICATE KEY UPDATE name=VALUES(name), kills=VALUES(kills), deaths=VALUES(deaths), stats=VALUES(stats), inventory=VALUES(inventory), last_login=NOW()";
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
