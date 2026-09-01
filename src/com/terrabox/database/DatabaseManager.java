package com.terrabox.database;

import org.bukkit.configuration.ConfigurationSection;

import java.sql.SQLException;

public class DatabaseManager {
    private final com.terrabox.TerraBoxPlugin plugin;
    private DatabaseProvider provider;
    private final String defaultDbPath;

    public DatabaseManager(com.terrabox.TerraBoxPlugin plugin) {
        this.plugin = plugin;
        this.defaultDbPath = plugin.getDataFolder() + "/database.db";
    }

    public void initialize() {
        ConfigurationSection dbConfig = plugin.getConfig().getConfigurationSection("database");
        if (dbConfig == null) {
            provider = new SQLiteProvider(defaultDbPath);
        } else {
            String type = dbConfig.getString("type", "sqlite").toLowerCase();
            switch (type) {
                case "mysql", "mariadb":
                    String url = dbConfig.getString("mysql.url", "jdbc:mysql://localhost:3306/terrabox");
                    String user = dbConfig.getString("mysql.user", "root");
                    String pass = dbConfig.getString("mysql.pass", "");
                    provider = new MySQLProvider(url, user, pass);
                    break;
                case "sqlite", "default":
                default:
                    String path = dbConfig.getString("sqlite.path", defaultDbPath);
                    provider = new SQLiteProvider(path);
                    break;
            }
        }
        try {
            provider.init();
            plugin.getLogger().info("数据库初始化成功: " + provider.getType());
        } catch (SQLException e) {
            plugin.getLogger().severe("数据库初始化失败: " + e.getMessage());
            plugin.getLogger().info("将使用 SQLite 作为后备方案");
            provider = new SQLiteProvider(defaultDbPath);
            try {
                provider.init();
            } catch (SQLException ex) {
                plugin.getLogger().severe("SQLite 后备方案也失败: " + ex.getMessage());
            }
        }
    }

    public DatabaseProvider getProvider() {
        return provider;
    }

    public void shutdown() {
        if (provider != null) {
            try {
                provider.close();
            } catch (SQLException e) {
                plugin.getLogger().warning("关闭数据库连接失败: " + e.getMessage());
            }
        }
    }

    public boolean isAvailable() {
        return provider != null && provider.isValid();
    }
}
