package com.terrabox;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 出生广场 (地图中央 0,0): 对局玩家初始聚集点
 *  - 圆形石砖平台 (垫高填平自然地形)
 *  - 边缘火把/灯笼装饰
 *  - 中央开局物资箱 (高稀有度, 开局争抢)
 *  - 平台中心高度缓存, 供对局开始传送使用
 *
 * 线程模型: getChunkAtAsync → force load → RegionScheduler 区域线程铺方块
 */
public class SpawnAreaBuilder {
    private final TerraBoxPlugin plugin;
    private volatile int centerY = 64;
    private volatile boolean built = false;

    public SpawnAreaBuilder(TerraBoxPlugin plugin) {
        this.plugin = plugin;
    }

    public int centerY() {
        return centerY;
    }

    public boolean isBuilt() {
        return built;
    }

    /** 构建/重建出生广场 (任意线程, 异步链) */
    public void build(Runnable done) {
        World w = plugin.worlds().world();
        if (w == null) { if (done != null) done.run(); return; }
        int radius = Math.max(4, plugin.getConfig().getInt("game.spawn-area-radius", 7));
        w.getChunkAtAsync(0, 0).whenComplete((chunk, err) -> {
            if (err != null) {
                plugin.getLogger().warning("出生广场区块加载失败: " + err);
                if (done != null) done.run();
                return;
            }
            Bukkit.getGlobalRegionScheduler().run(plugin, t -> {
                try { w.setChunkForceLoaded(0, 0, true); } catch (Throwable ignored) {}
                Bukkit.getRegionScheduler().run(plugin, w, 0, 0, task -> {
                    try {
                        buildPlatform(w, radius);
                        buildCenterBoxes(w);
                        plugin.getLogger().info("出生广场已就绪: 半径 " + radius + ", 平台高 y=" + centerY);
                    } catch (Throwable ex) {
                        plugin.getLogger().warning("出生广场构建异常: " + ex);
                    } finally {
                        try { w.setChunkForceLoaded(0, 0, false); } catch (Throwable ignored) {}
                    }
                    if (done != null) done.run();
                });
            });
        });
    }

    /** 区域线程: 铺圆形平台 (垫高到中心高度, 表面平滑石) */
    private void buildPlatform(World w, int radius) {
        int y0 = w.getHighestBlockYAt(0, 0);
        centerY = y0 + 1; // 平台表面高度
        int r2 = (radius * radius + radius + 1);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > r2) continue;
                int y1 = w.getHighestBlockYAt(dx, dz);
                // 垫高填平: 使用更丰富的方块组合，模拟MC原版地形
                for (int y = y1 + 1; y <= y0; y++) {
                    Block b = w.getBlockAt(dx, y, dz);
                    if (b.getType().isAir() || !b.getType().isSolid()) {
                        // 深度使用深板岩，中层使用石头，表层使用泥土
                        if (y < y0 - 4) {
                            b.setType(Material.DEEPSLATE, false);
                        } else if (y < y0 - 2) {
                            b.setType(Material.STONE, false);
                        } else {
                            b.setType(Material.DIRT, false);
                        }
                    }
                }
                // 平台表面: 使用平滑石砖和石砖混合，棋盘格图案
                Block surface = w.getBlockAt(dx, y0 + 1, dz);
                if (dx == 0 && dz == 0) {
                    surface.setType(Material.REDSTONE_BLOCK, false); // 中心标记
                } else {
                    // 棋盘格图案
                    if ((dx + dz) % 2 == 0) {
                        surface.setType(Material.SMOOTH_STONE, false);
                    } else {
                        surface.setType(Material.STONE_BRICKS, false);
                    }
                }
                // 移除表面以上残留(树/草等)
                Block above2 = w.getBlockAt(dx, y0 + 2, dz);
                if (above2.getType() != Material.AIR && !above2.getType().name().contains("TORCH")
                        && !above2.getType().name().contains("LANTERN")) {
                    above2.setType(Material.AIR, false);
                }
            }
        }
        // 边缘装饰: 使用火把和灯笼混合，增加多样性
        int[][] dirs = {{-radius, 0}, {radius, 0}, {0, -radius}, {0, radius}};
        for (int[] d : dirs) {
            Block edge = w.getBlockAt(d[0], y0 + 2, d[1]);
            if ((d[0] + d[1]) % 2 == 0) {
                edge.setType(Material.TORCH, false);
            } else {
                edge.setType(Material.LANTERN, false);
            }
        }
    }

    /** 区域线程: 中央开局物资箱 (高稀有度) */
    private void buildCenterBoxes(World w) {
        List<Rarity> rarities = new java.util.ArrayList<>();
        for (String s : plugin.getConfig().getStringList("game.spawn-area-box-rarity")) {
            Rarity r = Rarity.parse(s);
            if (r != null) rarities.add(r);
        }
        if (rarities.isEmpty()) rarities = List.of(Rarity.EPIC, Rarity.EPIC, Rarity.LEGENDARY, Rarity.LEGENDARY);
        int[][] offsets = {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
        for (int i = 0; i < Math.min(offsets.length, rarities.size()); i++) {
            int bx = offsets[i][0], bz = offsets[i][1];
            Rarity r = rarities.get(i);
            // 中央箱子直接放平台表面
            plugin.boxes().spawnBoxAt(bx, bz, r, false, null);
        }
    }

    /** 对局开始: 获取玩家出生位置 (平台边缘均匀分布, 区域线程外只算坐标) */
    public Location spawnPointFor(int playerIndex, int playerCount) {
        World w = plugin.worlds().world();
        if (w == null) return null;
        int radius = Math.max(4, plugin.getConfig().getInt("game.spawn-area-radius", 7));
        double angle = (playerCount <= 1) ? 0
                : (playerIndex * (Math.PI * 2) / playerCount) + ThreadLocalRandom.current().nextDouble(-0.3, 0.3);
        int r = Math.max(2, radius - 1);
        double x = Math.cos(angle) * r;
        double z = Math.sin(angle) * r;
        return new Location(w, x, centerY, z, (float) Math.toDegrees(angle) + 90, 0f);
    }
}
