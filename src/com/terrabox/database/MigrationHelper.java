package com.terrabox.database;

import java.sql.*;
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
            String sql = "SELECT uuid, kills, deaths FROM players WHERE uuid = ?";
            try (PreparedStatement ps = provider.getConnection().prepareStatement(sql)) {
                ps.setString(1, uuid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        stats.put("uuid", rs.getString("uuid"));
                        stats.put("kills", rs.getInt("kills"));
                        stats.put("deaths", rs.getInt("deaths"));
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("查询玩家数据失败: " + e.getMessage());
        }
        return stats;
    }
}
