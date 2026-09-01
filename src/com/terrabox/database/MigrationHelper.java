package com.terrabox.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

public class MigrationHelper {
    private final com.terrabox.TerraBoxPlugin plugin;
    private final DatabaseProvider provider;

    public MigrationHelper(com.terrabox.TerraBoxPlugin plugin, DatabaseProvider provider) {
        this.plugin = plugin;
        this.provider = provider;
    }

    public void migrateFromYaml() {
        // TODO: 迁移现有 boxes.yml 数据到数据库
        plugin.getLogger().info("YAML迁移功能待实现");
    }

    public Map<String, Object> getPlayerStats(String uuid) {
        Map<String, Object> stats = new HashMap<>();
        try {
            ResultSet rs = provider.query("SELECT * FROM players WHERE uuid = '" + uuid + "'");
            if (rs.next()) {
                stats.put("kills", rs.getInt("kills"));
                stats.put("deaths", rs.getInt("deaths"));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("查询玩家数据失败: " + e.getMessage());
        }
        return stats;
    }
}
