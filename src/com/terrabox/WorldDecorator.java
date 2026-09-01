package com.terrabox;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.ArrayDeque;
import java.util.function.Consumer;

/**
 * 世界地形装饰 (资源大陆风格):
 *  - 边界屏障墙: 四边 3 格高石砖墙 + 红混凝土警戒带 + 火把 (防止跑出地图)
 *  - 中心战场: 半径内清理植被, 开阔对战区
 *  - 四条主干道: 中心广场向四边界铺石板路 (水面垫高)
 *  - 四座瞭望塔: N/E/S/W 距中心 tower-distance, 塔顶灯笼 + 史诗物资箱
 *
 * 线程模型: 全局线程驱动区块任务队列 (每 tick 提交一批), 每个区块任务 force load 后
 *  在 RegionScheduler 区域线程执行方块操作, 完成后解除 force load。
 */
public class WorldDecorator {
    private final TerraBoxPlugin plugin;
    private volatile boolean done = false;
    private final ArrayDeque<Job> queue = new ArrayDeque<>();
    private ScheduledTask driver;

    /** 区块级任务: 在指定区块的区域线程执行 body */
    private record Job(int cx, int cz, Consumer<ScheduledTask> body) {}

    public WorldDecorator(TerraBoxPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isDone() {
        return done;
    }

    /** 异步构建全部装饰 (任意线程, 不阻塞) */
    public void build() {
        World w = plugin.worlds().world();
        if (w == null) return;
        if (!plugin.getConfig().getBoolean("decorator.enabled", true)) return;
        queue.clear();
        enqueueJobs(w);
        if (queue.isEmpty()) { done = true; return; }
        plugin.getLogger().info("开始地形装饰: " + queue.size() + " 个区块任务");
        final int batch = Math.max(2, plugin.getConfig().getInt("decorator.batch-per-tick", 6));
        driver = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> {
            if (queue.isEmpty()) {
                t.cancel();
                driver = null;
                done = true;
                plugin.getLogger().info("地形装饰完成: 边界墙/主干道/瞭望塔/中心战场已就绪");
                return;
            }
            for (int i = 0; i < batch && !queue.isEmpty(); i++) {
                Job j = queue.poll();
                if (j == null) break;
                submitJob(w, j);
            }
        }, 20L, 10L);
    }

    private void submitJob(World w, Job j) {
        w.getChunkAtAsync(j.cx, j.cz).whenComplete((chunk, err) -> {
            if (err != null) {
                plugin.getLogger().warning("装饰区块加载失败 (" + j.cx + "," + j.cz + "): " + err);
                return;
            }
            Bukkit.getGlobalRegionScheduler().run(plugin, t -> {
                try { w.setChunkForceLoaded(j.cx, j.cz, true); } catch (Throwable ignored) {}
                Bukkit.getRegionScheduler().run(plugin, w, j.cx, j.cz, task -> {
                    try {
                        j.body().accept(task);
                    } catch (Throwable ex) {
                        plugin.getLogger().warning("装饰任务异常 (" + j.cx + "," + j.cz + "): " + ex);
                    } finally {
                        try { w.setChunkForceLoaded(j.cx, j.cz, false); } catch (Throwable ignored) {}
                    }
                });
            });
        });
    }

    // ==================== 任务规划 ====================

    private void enqueueJobs(World w) {
        int half = (int) plugin.worlds().borderHalf(); // 392
        int wall = half - 2;    // 墙在边界内 2 格
        int clearR = plugin.getConfig().getInt("decorator.clear-center-radius", 30);
        int towerDist = Math.min(wall - 40, Math.max(80, plugin.getConfig().getInt("decorator.tower-distance", 320)));

        // 当前地形: 末地是浮空岛、下界是岩浆地形, 没有草地/地面, 跳过主干道/中心清理 (避免破坏特殊地貌)
        TerrainType cur = plugin.arenas() != null
                ? plugin.arenas().terrainOf(w.getName()) : TerrainType.DEFAULT;
        boolean isEnd = (cur == TerrainType.THE_END || cur == TerrainType.NETHER);

        java.util.Set<Long> seen = new java.util.HashSet<>();

        // 1) 中心战场清理: 覆盖半径 clearR 的区块 (末地跳过, 主岛保持末地石原貌)
        if (!isEnd) {
            int c0 = (int) Math.floor(-clearR / 16.0), c1 = (int) Math.ceil(clearR / 16.0);
            for (int cx = c0; cx < c1; cx++) {
                for (int cz = c0; cz < c1; cz++) {
                    if (seen.add(pack(cx, cz))) {
                        final int fcx = cx, fcz = cz;
                        queue.add(new Job(cx, cz, task -> clearCenter(w, fcx, fcz, clearR)));
                    }
                }
            }
        }

        // 2) 四条主干道: x 轴(z∈[-1,1], x∈[-wall,wall]) 与 z 轴 (末地跳过)
        int roadMin = Math.max(10, plugin.getConfig().getInt("game.spawn-area-radius", 7) + 2);
        if (!isEnd) {
        for (int cx = (int) Math.floor(-wall / 16.0); cx < (int) Math.ceil(wall / 16.0); cx++) {
            for (int cz = -1; cz <= 0; cz++) { // z=-1,0 区块覆盖 z∈[-1,1] 道路
                if (seen.add(pack(cx, cz))) {
                    final int fcx = cx, fcz = cz;
                    queue.add(new Job(cx, cz, task -> buildRoad(w, fcx, fcz, roadMin, wall)));
                }
            }
        }
        for (int cz = (int) Math.floor(-wall / 16.0); cz < (int) Math.ceil(wall / 16.0); cz++) {
            for (int cx = -1; cx <= 0; cx++) {
                if (seen.add(pack(cx, cz))) {
                    final int fcx = cx, fcz = cz;
                    queue.add(new Job(cx, cz, task -> buildRoadZ(w, fcx, fcz, roadMin, wall)));
                }
            }
        }
        }

        // 3) 边界屏障墙: 四边 (不去重: 角落区块需处理两条墙线)
        int wallMin = (int) Math.floor(-wall / 16.0), wallMax = (int) Math.ceil(wall / 16.0);
        for (int side = 0; side < 4; side++) {
            for (int c = wallMin; c < wallMax; c++) {
                int cx, cz;
                if (side == 0) { cx = c; cz = wall >> 4; }          // 北 z=+wall
                else if (side == 1) { cx = c; cz = -wall >> 4; }    // 南
                else if (side == 2) { cx = wall >> 4; cz = c; }     // 东
                else { cx = -wall >> 4; cz = c; }                   // 西
                final int fcx = cx, fcz = cz, fs = side;
                queue.add(new Job(cx, cz, task -> buildWall(w, fcx, fcz, wall, fs)));
            }
        }

        // 4) 瞭望塔: (0,±towerDist) 与 (±towerDist,0)
        int[][] towers = {{0, towerDist}, {0, -towerDist}, {towerDist, 0}, {-towerDist, 0}};
        for (int[] tp : towers) {
            int cx = tp[0] >> 4, cz = tp[1] >> 4;
            if (seen.add(pack(cx, cz))) {
                final int tx = tp[0], tz = tp[1];
                queue.add(new Job(cx, cz, task -> buildTower(w, tx, tz)));
            }
        }
    }

    private static long pack(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    // ==================== 中心战场 ====================

    private void clearCenter(World w, int cx, int cz, int radius) {
        int r2 = radius * radius;
        for (int bx = 0; bx < 16; bx++) {
            for (int bz = 0; bz < 16; bz++) {
                int x = cx * 16 + bx, z = cz * 16 + bz;
                if (x * x + z * z > r2) continue;
                clearVegetationAt(w, x, z);
            }
        }
    }

    /** 清除一列的树/草/花, 保留地面 */
    private void clearVegetationAt(World w, int x, int z) {
        int y = w.getHighestBlockYAt(x, z);
        if (y <= 0) return;
        Material top = w.getBlockAt(x, y, z).getType();
        if (top.isAir() || top == Material.WATER || top == Material.LAVA) return;
        for (int yy = y; yy > 0; yy--) {
            Material m = w.getBlockAt(x, yy, z).getType();
            if (m == Material.GRASS_BLOCK || m == Material.DIRT || m == Material.STONE
                    || m == Material.SAND || m == Material.GRAVEL || m == Material.SANDSTONE
                    || m == Material.DEEPSLATE || m == Material.STONE_BRICKS
                    || m == Material.SMOOTH_STONE || m == Material.MUD) break;
            w.getBlockAt(x, yy, z).setType(Material.AIR, false);
        }
    }

    // ==================== 主干道 ====================

    private void buildRoad(World w, int cx, int cz, int roadMin, int wall) {
        for (int bx = 0; bx < 16; bx++) {
            int x = cx * 16 + bx;
            if (Math.abs(x) < roadMin || Math.abs(x) > wall) continue;
            for (int dz = -1; dz <= 1; dz++) {
                int z = cz * 16 + dz;
                if (z < -1 || z > 1) continue;
                pave(w, x, z, Material.SMOOTH_STONE);
            }
        }
    }

    private void buildRoadZ(World w, int cx, int cz, int roadMin, int wall) {
        for (int bz = 0; bz < 16; bz++) {
            int z = cz * 16 + bz;
            if (Math.abs(z) < roadMin || Math.abs(z) > wall) continue;
            for (int dx = -1; dx <= 1; dx++) {
                int x = cx * 16 + dx;
                if (x < -1 || x > 1) continue;
                pave(w, x, z, Material.SMOOTH_STONE);
            }
        }
    }

    /** 铺路: 水面垫石头到海平面, 再铺表面方块 */
    private void pave(World w, int x, int z, Material surface) {
        int y = w.getHighestBlockYAt(x, z);
        Material m = w.getBlockAt(x, y, z).getType();
        int targetY = y;
        if (m == Material.WATER || m == Material.SEAGRASS || m == Material.KELP_PLANT
                || m == Material.KELP || m == Material.LILY_PAD) {
            for (int yy = y; yy >= 62; yy--) {
                Material mm = w.getBlockAt(x, yy, z).getType();
                if (mm.isAir() || mm == Material.WATER || mm == Material.SEAGRASS
                        || mm == Material.KELP_PLANT || mm == Material.KELP) {
                    w.getBlockAt(x, yy, z).setType(Material.STONE, false);
                }
            }
            targetY = Math.max(y, 62);
        }
        w.getBlockAt(x, targetY + 1, z).setType(surface, false);
        // 路两侧保留, 路面上方 1 格清理 (防树挡路)
        Block above = w.getBlockAt(x, targetY + 2, z);
        if (above.getType() != Material.AIR && !above.getType().name().contains("TORCH")
                && !above.getType().name().contains("LANTERN")) {
            above.setType(Material.AIR, false);
        }
    }

    // ==================== 边界屏障墙 ====================

    private void buildWall(World w, int cx, int cz, int wall, int side) {
        int h = Math.max(2, plugin.getConfig().getInt("decorator.wall-height", 3));
        for (int bx = 0; bx < 16; bx++) {
            for (int bz = 0; bz < 16; bz++) {
                int x = cx * 16 + bx, z = cz * 16 + bz;
                boolean onLine;
                if (side == 0) onLine = (z == wall);
                else if (side == 1) onLine = (z == -wall);
                else if (side == 2) onLine = (x == wall);
                else onLine = (x == -wall);
                if (!onLine) continue;
                if (Math.abs(x) > wall || Math.abs(z) > wall) continue;
                // 墙: 从地表向下夯实一段(防穿透), 再向上建墙到高出土表
                int y = w.getHighestBlockYAt(x, z);
                Material m = w.getBlockAt(x, y, z).getType();
                int ground = y;
                if (m == Material.WATER || m == Material.SEAGRASS || m == Material.KELP
                        || m == Material.KELP_PLANT) {
                    for (int yy = y; yy >= 62; yy--) {
                        Material mm = w.getBlockAt(x, yy, z).getType();
                        if (mm.isAir() || mm == Material.WATER || mm == Material.SEAGRASS
                                || mm == Material.KELP || mm == Material.KELP_PLANT) {
                            w.getBlockAt(x, yy, z).setType(Material.STONE, false);
                        }
                    }
                    ground = Math.max(y, 62);
                }
                // 向下夯实 4 格, 堵住土表以下穿透路径 (region 加载边界, 防绕过)
                for (int d = 1; d <= 4; d++) {
                    Block below = w.getBlockAt(x, ground - d, z);
                    if (below.getType().isAir() || !below.getType().isSolid()) {
                        below.setType(Material.STONE, false);
                    }
                }
                // 墙向上: 相对土表 h 格 (h=3 固定) 再额外按外围地形抬升补偿, 保证墙顶高于周边最高可跳高度
                int extra = plugin.getConfig().getInt("decorator.wall-extra-height", 6);
                int total = h + Math.max(0, extra);
                for (int i = 1; i <= total; i++) {
                    w.getBlockAt(x, ground + i, z).setType(Material.STONE_BRICKS, false);
                }
                // 顶部火把 (每 16 格) — 放在墙顶
                if (((x + z) % 16) == 0) {
                    w.getBlockAt(x, ground + total + 1, z).setType(Material.TORCH, false);
                }
                // 警戒带: 墙内侧 2 格, 放在地表上
                int inX = x, inZ = z;
                if (side == 0) inZ = z - 2;
                else if (side == 1) inZ = z + 2;
                else if (side == 2) inX = x - 2;
                else inX = x + 2;
                if (Math.abs(inX) <= wall && Math.abs(inZ) <= wall) {
                    int iy = w.getHighestBlockYAt(inX, inZ);
                    if (iy > 0) {
                        w.getBlockAt(inX, iy + 1, inZ).setType(Material.RED_CONCRETE, false);
                    }
                }
            }
        }
    }

    // ==================== 瞭望塔 ====================

    private void buildTower(World w, int tx, int tz) {
        int y0 = w.getHighestBlockYAt(tx, tz);
        // 塔顶箱子会由 BoxManager 异步放置 (放在塔顶平台上方)
        // 底座: 外圈 7x7 垫平, 内部夯实整块(防掉入空洞)
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int y = w.getHighestBlockYAt(tx + dx, tz + dz);
                // 内部(塔身5x5覆盖区)夯实到地板: 从土表向上一直填到 y0+1, 形成实心平台
                boolean inner = Math.abs(dx) <= 2 && Math.abs(dz) <= 2;
                int fillTop = inner ? (y0 + 1) : (y0 + 1);
                for (int yy = y + 1; yy <= fillTop; yy++) {
                    Material f = (yy == fillTop) ? Material.SMOOTH_STONE : Material.STONE_BRICKS;
                    w.getBlockAt(tx + dx, yy, tz + dz).setType(f, false);
                }
                // 底座外圈垫平只用石头
                if (!inner) {
                    w.getBlockAt(tx + dx, y0 + 1, tz + dz).setType(Material.STONE_BRICKS, false);
                }
            }
        }
        // 塔身 5x5 外墙 (y0+2 .. y0+9)
        for (int yy = y0 + 2; yy <= y0 + 9; yy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                    if (edge) {
                        w.getBlockAt(tx + dx, yy, tz + dz).setType(Material.STONE_BRICKS, false);
                    }
                }
            }
            // 每层四角窗台装饰: 隔层放火把在四角
            if (yy % 2 == 0) {
                w.getBlockAt(tx - 2, yy, tz - 2).setType(Material.SMOOTH_STONE, false);
                w.getBlockAt(tx + 2, yy, tz - 2).setType(Material.SMOOTH_STONE, false);
                w.getBlockAt(tx - 2, yy, tz + 2).setType(Material.SMOOTH_STONE, false);
                w.getBlockAt(tx + 2, yy, tz + 2).setType(Material.SMOOTH_STONE, false);
            }
        }
        // 顶部平台 5x5 (y0+10) + 围栏防掉落 + 中心灯笼 + 塔顶箱
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                w.getBlockAt(tx + dx, y0 + 10, tz + dz).setType(Material.SMOOTH_STONE, false);
            }
        }
        // 平台四周围栏 (留一个可进入的缺口天然即可, 箱在角上)
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                if (edge) {
                    // 围栏用铁栏杆, 视线通透同时防跌落
                    w.getBlockAt(tx + dx, y0 + 11, tz + dz).setType(Material.IRON_BARS, false);
                }
            }
        }
        w.getBlockAt(tx, y0 + 12, tz).setType(Material.LANTERN, false);
        // 塔顶史诗物资箱 (放平台角落)
        String rName = plugin.getConfig().getString("decorator.tower-rarity", "EPIC");
        Rarity r = Rarity.parse(rName);
        if (r == null) r = Rarity.EPIC;
        plugin.boxes().spawnBoxAt(tx + 1, tz + 1, r, false, null);
        plugin.getLogger().info("瞭望塔已建成: (" + tx + "," + tz + ") 塔顶 " + r.display + "物资箱");
    }
}
