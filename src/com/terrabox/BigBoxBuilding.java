package com.terrabox;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 大型物资建筑 / 物资聚集点:
 *  - 在地图关键位置 (随机选址) 建造一个石砖建筑, 内部布置多个高稀有度物资箱
 *  - "大箱子" 概念: 建筑内多个箱子集中, 玩家探索争夺
 *  - 建筑自动规划: 固定建筑 + 随机散布建筑
 *
 * 线程模型: 与 WorldDecorator 一致 — 区块任务 force load 后在 RegionScheduler 铺方块。
 */
public class BigBoxBuilding {
    private final TerraBoxPlugin plugin;

    public BigBoxBuilding(TerraBoxPlugin plugin) {
        this.plugin = plugin;
    }

    /** 在地图内随机一个开阔位置建造一座物资建筑 (异步, fire-and-forget) */
    public void buildRandom(World w) {
        int half = (int) plugin.worlds().borderHalf();
        int pad = Math.max(24, plugin.getConfig().getInt("boxes.edge-padding", 24));
        int limit = Math.max(48, half - pad);
        for (int attempt = 0; attempt < 12; attempt++) {
            int x = ThreadLocalRandom.current().nextInt(-limit, limit);
            int z = ThreadLocalRandom.current().nextInt(-limit, limit);
            if (Math.hypot(x, z) < 60) continue; // 离中心太近, 留给出生广场
            final int fx = x, fz = z;
            int cx = x >> 4, cz = z >> 4;
            // 异步加载候选区块, 在区域线程内检测开阔地 + 建造 (避免同步 getHighestBlockYAt 跨区块 syncLoad 阻塞触发 Watchdog)
            w.getChunkAtAsync(cx, cz).whenComplete((chunk, err) -> {
                if (err != null) return;
                Bukkit.getGlobalRegionScheduler().run(plugin, t -> {
                    try { w.setChunkForceLoaded(cx, cz, true); } catch (Throwable ignored) {}
                    Bukkit.getRegionScheduler().run(plugin, w, cx, cz, task -> {
                        try {
                            if (!openArea(w, fx, fz)) return;
                            build(w, fx, fz);
                        } catch (Throwable ex) {
                            plugin.getLogger().warning("大型物资建筑建造异常: " + ex);
                        } finally {
                            try { w.setChunkForceLoaded(cx, cz, false); } catch (Throwable ignored) {}
                        }
                    });
                });
            });
            return; // 一次只尝试一座 (fire-and-forget)
        }
    }

    /** 开阔地校验 (区域线程, 已 force load 目标区块): 8x8 高度差 <=4, 跨区块访问异常降级为不平坦 */
    private boolean openArea(World w, int x, int z) {
        try {
            int c = w.getHighestBlockYAt(x, z);
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    int y = w.getHighestBlockYAt(x + dx, z + dz);
                    if (Math.abs(y - c) > 4) return false;
                }
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 建造一座 9x9 石砖建筑, 内部放置 4~6 个物资箱 (区域线程) */
    public void build(World w, int orgX, int orgZ) {
        int y0 = w.getHighestBlockYAt(orgX, orgZ) + 1;
        int size = 9;
        int half = size / 2;

        // 地基 (9x9)
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                w.getBlockAt(orgX + dx, y0, orgZ + dz).setType(Material.STONE_BRICKS, false);
            }
        }
        // 墙体: 四周一圈 3 格高, 留一个门洞 (南墙中央)
        for (int h = 1; h <= 3; h++) {
            for (int dx = -half; dx <= half; dx++) {
                for (int dz = -half; dz <= half; dz++) {
                    boolean edge = Math.abs(dx) == half || Math.abs(dz) == half;
                    if (!edge) continue;
                    boolean door = (dz == half && dx == 0 && h <= 2);
                    if (door) {
                        if (h <= 2) w.getBlockAt(orgX + dx, y0 + h, orgZ + dz).setType(Material.AIR, false);
                        continue;
                    }
                    w.getBlockAt(orgX + dx, y0 + h, orgZ + dz).setType(Material.STONE_BRICKS, false);
                }
            }
        }
        // 屋顶: 顶格铺满, 中央留通光
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                if (Math.abs(dx) == 0 && Math.abs(dz) == 0) continue;
                w.getBlockAt(orgX + dx, y0 + 4, orgZ + dz).setType(Material.STONE_BRICKS, false);
            }
        }
        // 室内火把照明 (四角)
        for (int[] c : new int[][]{{-half + 1, -half + 1}, {half - 1, -half + 1},
                {-half + 1, half - 1}, {half - 1, half - 1}}) {
            w.getBlockAt(orgX + c[0], y0 + 1, orgZ + c[1]).setType(Material.TORCH, false);
        }

        // 建筑内放置物资箱 (4~6 个, 高稀有度倾向)
        int boxCount = 4 + ThreadLocalRandom.current().nextInt(3); // 4..6
        List<Rarity> weights = List.of(
                plugin.weightedPickForWorld(), plugin.weightedPickForWorld(), plugin.weightedPickForWorld(),
                plugin.weightedPickForWorld(), plugin.weightedPickForWorld(), plugin.weightedPickForWorld());
        for (int i = 0; i < boxCount; i++) {
            int bx = orgX + (ThreadLocalRandom.current().nextInt(-half + 2, half));
            int bz = orgZ + (ThreadLocalRandom.current().nextInt(-half + 2, half));
            // 提升建筑内箱子稀有度 (加权到 EPIC/LEGENDARY)
            Rarity r = upgrade(weights.get(i));
            // 直接将箱子放在室内 y0+1 高度
            int cxx = bx >> 4, czz = bz >> 4;
            World fw = w;
            int finalBx = bx, finalBz = bz, boxY = y0 + 1;
            fw.getChunkAtAsync(cxx, czz).whenComplete((chunk, err) -> {
                if (err != null) return;
                Bukkit.getGlobalRegionScheduler().run(plugin, t -> {
                    try { fw.setChunkForceLoaded(cxx, czz, true); } catch (Throwable ignored) {}
                    Bukkit.getRegionScheduler().run(plugin, fw, cxx, czz, task -> {
                        try {
                            Block ground = fw.getBlockAt(finalBx, boxY - 1, finalBz);
                            Block above = fw.getBlockAt(finalBx, boxY, finalBz);
                            if (above.getType().isAir() && ground.getType().isSolid()) {
                                above.setType(Material.CHEST, false);
                                // 使用RegionScheduler在区域线程内操作箱子
                                Location boxLoc = new Location(fw, finalBx, boxY, finalBz);
                                Bukkit.getRegionScheduler().run(plugin, boxLoc, task -> {
                                    Block placed = fw.getBlockAt(finalBx, boxY, finalBz);
                                    if (placed.getState() instanceof Chest chest) {
                                        // PDC标记
                                        PersistentDataContainer pdc = chest.getPersistentDataContainer();
                                        pdc.set(plugin.boxes().keyRarity(), PersistentDataType.STRING, r.name());
                                        pdc.set(plugin.boxes().keyBorn(), PersistentDataType.LONG, System.currentTimeMillis());
                                        // 自定义名
                                        chest.customName(net.kyori.adventure.text.Component.text(
                                                r.display + "物资箱", r.color));
                                        chest.update();
                                        // 登记到保护系统
                                        BoxEntry entry = new BoxEntry(finalBx, boxY, finalBz, r, System.currentTimeMillis(), false);
                                        plugin.boxes().registry().add(entry);
                                        plugin.boxes().markDirty();
                                        // 填充战利品
                                        int filled = plugin.loot().fillInventory(
                                                chest.getBlockInventory(), r);
                                        plugin.getLogger().info("大建筑物资箱: 内部 " + r.display
                                                + " ×" + finalBx + "," + boxY + "," + finalBz + " 战利品 " + filled + " 堆");
                                    }
                                });
                            }
                        } catch (Throwable ex) {
                            plugin.getLogger().warning("建筑物资箱放置异常: " + ex);
                        } finally {
                            try { fw.setChunkForceLoaded(cxx, czz, false); } catch (Throwable ignored) {}
                        }
                    });
                });
            });
        }
        plugin.getLogger().info("大型物资建筑已建成: (" + orgX + "," + orgZ + ") 室内 " + boxCount + " 箱");
    }

    /** 屋内箱稀有度提升一档 */
    private Rarity upgrade(Rarity r) {
        return switch (r) {
            case COMMON -> Rarity.RARE;
            case RARE -> Rarity.EPIC;
            default -> r;
        };
    }
}
