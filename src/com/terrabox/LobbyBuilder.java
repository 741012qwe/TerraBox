package com.terrabox;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

/**
 * 大厅世界 (terra_lobby, 512x512): 玩家聚集地 / 自动生成
 *
 * 结构:
 *  - 中心大型石砖广场平台 (垫高到基准高度, 玩家聚集)
 *  - 平台边缘玻璃围栏 (防跌落)
 *  - 世界外围屏障 (barrier) 墙 + 底部基岩填充, 防止离开大厅
 *  - 中心建筑: 出生点 + 信息公告牌 + 对局入口按钮
 *
 * 线程模型: 与 WorldDecorator 一致 — 全局驱动队列, 区块任务 force load 后
 *   在 RegionScheduler 区域线程铺方块。
 */
public class LobbyBuilder {
    private final TerraBoxPlugin plugin;
    private volatile int centerY = 64;
    private volatile World lobby;
    private final java.util.concurrent.atomic.AtomicBoolean building = new java.util.concurrent.atomic.AtomicBoolean(false);

    public LobbyBuilder(TerraBoxPlugin plugin) {
        this.plugin = plugin;
    }

    public World lobby() {
        // 从 WorldService 获取
        return plugin.worlds().lobby();
    }

    public int centerY() {
        return centerY;
    }

    /** 异步构建大厅 (任意线程, 不阻塞); done 在 Global 线程回调 */
    public void build(Runnable done) {
        World w = lobby();
        if (w == null) { if (done != null) done.run(); return; }
        if (!building.compareAndSet(false, true)) {
            if (done != null) done.run();
            return;
        }

        int radius = Math.max(8, plugin.getConfig().getInt("lobby.platform-radius", 24));
        int half = (int) plugin.worlds().lobbyHalf(); // 512/2
        // 规划任务: 中心平台(半径 radius 内区块), 外围屏障墙(四周), 底部基岩
        java.util.Set<java.util.Map.Entry<Integer, Integer>> tasks = new java.util.HashSet<>();

        // 中心平台覆盖区块
        int c0 = (int) Math.floor(-radius / 16.0), c1 = (int) Math.ceil(radius / 16.0);
        for (int cx = c0; cx < c1; cx++) {
            for (int cz = c0; cz < c1; cz++) tasks.add(new java.util.AbstractMap.SimpleEntry<>(cx, cz));
        }
        // 建筑 (直径比平台小一点): 用一个额外区块覆盖
        int b0 = (int) Math.floor(-(radius + 1) / 16.0), b1 = (int) Math.ceil((radius + 1) / 16.0);
        for (int cx = b0; cx < b1; cx++) {
            for (int cz = b0; cz < b1; cz++) tasks.add(new java.util.AbstractMap.SimpleEntry<>(cx, cz));
        }
        // 外围屏障墙区块 (四周一圈)
        int wMin = (int) Math.floor(-half / 16.0), wMax = (int) Math.ceil(half / 16.0);
        for (int c = wMin; c < wMax; c++) {
            tasks.add(new java.util.AbstractMap.SimpleEntry<>(c, wMin)); // 南
            tasks.add(new java.util.AbstractMap.SimpleEntry<>(c, wMax - 1)); // 北
            tasks.add(new java.util.AbstractMap.SimpleEntry<>(wMin, c)); // 西
            tasks.add(new java.util.AbstractMap.SimpleEntry<>(wMax - 1, c)); // 东
        }

        plugin.getLogger().info("开始构建大厅: " + tasks.size() + " 个区块任务");
        final int batch = Math.max(2, plugin.getConfig().getInt("lobby.batch-per-tick", 8));
        final java.util.ArrayDeque<java.util.Map.Entry<Integer, Integer>> queue =
                new java.util.ArrayDeque<>(tasks);
        final World fw = w;

        // 先加载所有相关区块 (异步), 再铺方块 — 分批
        ScheduledTask driver = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> {
            if (queue.isEmpty()) {
                t.cancel();
                building.set(false);
                centerY = computeCenterY(fw);
                plugin.getLogger().info("大厅构建完成: 中心 y=" + centerY);
                if (done != null) done.run();
                return;
            }
            for (int i = 0; i < batch && !queue.isEmpty(); i++) {
                var task = queue.poll();
                int cx = task.getKey(), cz = task.getValue();
                fw.getChunkAtAsync(cx, cz).whenComplete((chunk, err) -> {
                    if (err != null) {
                        plugin.getLogger().warning("大厅区块加载失败 (" + cx + "," + cz + "): " + err);
                        return;
                    }
                    Bukkit.getGlobalRegionScheduler().run(plugin, tt -> {
                        try { fw.setChunkForceLoaded(cx, cz, true); } catch (Throwable ignored) {}
                        Bukkit.getRegionScheduler().run(plugin, fw, cx, cz, task2 -> {
                            try {
                                buildPlatform(fw, cx, cz, radius);
                                buildWall(fw, cx, cz, half);
                            } catch (Throwable ex) {
                                plugin.getLogger().warning("大厅构建异常 (" + cx + "," + cz + "): " + ex);
                            } finally {
                                try { fw.setChunkForceLoaded(cx, cz, false); } catch (Throwable ignored) {}
                            }
                        });
                    });
                });
            }
        }, 20L, 8L);
    }

    /** 区域线程: 铺中心平台 (垫高到基准高度 + 边缘玻璃围栏) */
    private void buildPlatform(World w, int cx, int cz, int radius) {
        int base = plugin.getConfig().getInt("lobby.platform-y", 64);
        int r2 = (radius * radius + radius + 1);
        int r2edge = (radius + 1) * (radius + 1);
        for (int bx = 0; bx < 16; bx++) {
            for (int bz = 0; bz < 16; bz++) {
                int x = cx * 16 + bx, z = cz * 16 + bz;
                if (x * x + z * z <= r2) {
                    // 平台: 底部基岩, 上填平整, 表面石砖
                    for (int y = 1; y <= base; y++) {
                        Block b = w.getBlockAt(x, y, z);
                        if (y <= 2) b.setType(Material.BEDROCK, false);
                        else if (y < base) b.setType(Material.STONE, false);
                        else b.setType(Material.STONE_BRICKS, false);
                    }
                    // 表面以上清空 (防残留植被)
                    Block above = w.getBlockAt(x, base + 1, z);
                    if (!above.getType().isAir() && !above.getType().name().contains("GLASS")
                            && !above.getType().name().contains("BARRIER")) {
                        above.setType(Material.AIR, false);
                    }
                    // 平台边缘一圈玻璃围栏 (防跌落平台 → 虚空)
                    if (x * x + z * z > (radius - 1) * (radius - 1) + 1) {
                        for (int h = 1; h <= 2; h++) {
                            Block gl = w.getBlockAt(x, base + h, z);
                            if (gl.getType().isAir()) gl.setType(Material.GLASS, false);
                        }
                    }
                }
                // 平台外 (界内但半径外): 留空 (虚空视觉, 玩家掉下平台坠向虚空重生)
                else if (x * x + z * z <= r2edge) {
                    for (int y = 1; y <= base; y++) {
                        Block b = w.getBlockAt(x, y, z);
                        if (b.getType() != Material.AIR) b.setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    /** 区域线程: 外围屏障墙 + 底部基岩 (防止离开大厅) */
    private void buildWall(World w, int cx, int cz, int half) {
        int base = plugin.getConfig().getInt("lobby.platform-y", 64);
        for (int bx = 0; bx < 16; bx++) {
            for (int bz = 0; bz < 16; bz++) {
                int x = cx * 16 + bx, z = cz * 16 + bz;
                boolean boundary = Math.abs(x) >= half - 2 || Math.abs(z) >= half - 2;
                if (boundary) {
                    // 屏障墙: 从底部到高空全填 barrier, 防止离开
                    for (int y = 1; y < w.getMaxHeight(); y++) {
                        w.getBlockAt(x, y, z).setType(Material.BARRIER, false);
                    }
                } else if (Math.abs(x) == half - 3 || Math.abs(z) == half - 3) {
                    // 内侧环形: 检视玻璃围栏 (距边界3格, 提示边界)
                    for (int y = base; y <= base + 3; y++) {
                        w.getBlockAt(x, y, z).setType(Material.GLASS, false);
                    }
                }
            }
        }
    }

    /** 计算大厅中心高度 (不读世界, 直接用配置基准, 避免 Global 线程异步读区块) */
    private int computeCenterY(World w) {
        return plugin.getConfig().getInt("lobby.platform-y", 64) + 1;
    }

    public Location spawnLocation() {
        World w = lobby();
        if (w == null) return null;
        int y = plugin.getConfig().getInt("lobby.platform-y", 64) + 1;
        return new Location(w, 0.5, y, 0.5);
    }

    /** 大厅坠落保护: 定期把掉入虚空/平台以下的玩家传回大厅出生点 */
    public void startSafetyWatch() {
        int platformY = plugin.getConfig().getInt("lobby.platform-y", 64);
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> {
            World w = lobby();
            if (w == null) return;
            for (Player p : w.getPlayers()) {
                if (p.getLocation().getY() < platformY - 2) {
                    p.teleportAsync(spawnLocation());
                }
            }
        }, 20L, 20L);
    }
}
