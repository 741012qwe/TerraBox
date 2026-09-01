package com.terrabox;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.BlockPopulator;

import java.util.List;
import java.util.Random;

/**
 * 自定义地形生成器: 完全取代原版自然生成, 支持多种地形模板
 *
 *   - DEFAULT  : 中心平坦草原, 外围丘陵/山地, 稀疏橡树
 *   - DESERT   : 沙地/砂岩地表, 平缓沙丘, 稀疏仙人掌
 *   - ISLANDS  : 以每个 16 格宏区块为单位, 随机生成岛或海, 岛屿间互不相连
 *   - THE_END  : 模拟原版末地 — 浮空末地石岛群, 主岛+黑曜石柱+末地城塔楼, 黑色天空
 *   - BADLANDS : 模拟原版恶地 — 红沙地表+彩陶瓦层, 被侵蚀的平顶山丘, 裸露金矿/洞穴
 *
 * 树木密度大幅调低 (每 16x16 宏区块最多稀疏几棵), 避免玩家被树卡住/过度密集。
 * 群系 (BiomeGrid) 按地形模板设置正确的原版群系, 使 F3 调试/地图/渲染与原版一致。
 *
 * 线程模型: 世界创建阶段由服务端生成线程顺序调用, 无并发问题。
 *
 * 注意: 使用 generateChunkData(World, Random, int, int, BiomeGrid) 签名
 *       (Lophine 26.2 API — 不能用 WorldInfo 重载)。
 */
public class CustomTerrainGenerator extends ChunkGenerator {

    private final TerraBoxPlugin plugin;
    private final TerrainType type;

    public CustomTerrainGenerator(TerraBoxPlugin plugin, TerrainType type) {
        this.plugin = plugin;
        this.type = type;
    }

    public TerrainType type() {
        return type;
    }

    @Override
    public ChunkData generateChunkData(World world, Random random, int chunkX, int chunkZ, BiomeGrid biome) {
        ChunkData data = createChunkData(world);
        int minY = data.getMinHeight();
        int maxY = data.getMaxHeight();
        int cx = chunkX * 16 + 8, cz = chunkZ * 16 + 8;

        switch (type) {
            case DESERT -> generateDesert(world, data, random, chunkX, chunkZ, minY, maxY, cx, cz);
            case ISLANDS -> generateIslands(world, data, random, chunkX, chunkZ, minY, maxY, cx, cz);
            case THE_END -> generateTheEnd(world, data, random, chunkX, chunkZ, minY, maxY, cx, cz, biome);
            case BADLANDS -> generateBadlands(world, data, random, chunkX, chunkZ, minY, maxY, cx, cz, biome);
            case NETHER -> generateNether(world, data, random, chunkX, chunkZ, minY, maxY, cx, cz, biome);
            case CITY -> generateCity(world, data, random, chunkX, chunkZ, minY, maxY, cx, cz, biome);
            case NORMAL -> generateNormal(world, data, random, chunkX, chunkZ, minY, maxY, cx, cz, biome);
            default -> generateDefault(world, data, random, chunkX, chunkZ, minY, maxY, cx, cz);
        }
        // 地形遮挡 (掩体/遮蔽结构): 基于逐块确定性随机, 在地表放置岩石/柱体/灌木
        // 末地岛屿为浮空岛地貌, 不加遮挡 (避免破坏浮空岛结构); 城市/下界由生成器自带结构, 不加遮挡
        if (type != TerrainType.THE_END && type != TerrainType.CITY && type != TerrainType.NETHER
                && plugin.getConfig().getBoolean("world.cover", true)) {
            addCover(data, random, chunkX, chunkZ, minY, maxY);
        }
        return data;
    }

    // ==================== DEFAULT: 中央平原 + 外围丘陵 ====================

    private void generateDefault(World world, ChunkData data, Random random,
                                 int chunkX, int chunkZ, int minY, int maxY, int cx, int cz) {
        double plainRadius = Math.max(80, plugin.getConfig().getDouble("world.plain-radius", 120));
        int baseY = plugin.getConfig().getInt("world.base-height", 64);

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int wx = chunkX * 16 + lx, wz = chunkZ * 16 + lz;
                double d = Math.sqrt(wx * wx + wz * wz);

                int surface = baseY;
                if (d > plainRadius) {
                    double t = (d - plainRadius) / Math.max(1, plainRadius);
                    surface = baseY + (int) (t * 24);
                }
                int wobble = (int) (Math.sin(wx * 0.17) * Math.cos(wz * 0.19) * 2);
                surface = Math.max(minY + 2, Math.min(maxY - 1, surface + wobble));

                for (int y = minY; y <= surface; y++) {
                    Material m;
                    if (y <= minY + 1) m = Material.BEDROCK;
                    else if (y <= surface - 4) m = Material.STONE;
                    else if (y < surface) m = Material.DIRT;
                    else m = Material.GRASS_BLOCK;
                    data.setBlock(lx, y, lz, m);
                }
            }
        }
    }

    // ==================== DESERT: 沙丘 + 沙岩底层 ====================

    private void generateDesert(World world, ChunkData data, Random random,
                                int chunkX, int chunkZ, int minY, int maxY, int cx, int cz) {
        int baseY = plugin.getConfig().getInt("world.base-height", 64);

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int wx = chunkX * 16 + lx, wz = chunkZ * 16 + lz;
                // 平缓沙丘: 正弦叠加, 起伏较小 (沙漠较平坦)
                double s = Math.sin(wx * 0.09) * Math.cos(wz * 0.11) * 3
                        + Math.sin((wx + wz) * 0.05) * 2;
                int surface = Math.max(minY + 2, Math.min(maxY - 1, baseY + (int) s));

                for (int y = minY; y <= surface; y++) {
                    Material m;
                    if (y <= minY + 1) m = Material.BEDROCK;
                    else if (y <= surface - 3) m = Material.SANDSTONE;
                    else m = Material.SAND;
                    data.setBlock(lx, y, lz, m);
                }
            }
        }
    }

    // ==================== ISLANDS: 随机岛屿群 + 海洋 ====================
    /**
     * 基于 Minecraft 原版岛屿生成参数:
     *   - 岛屿半径: 16~48 格 (原版 jigsaw 特征)
     *   - 海面: Y=63 (原版 sea level)
     *   - 地表: Y=64~70 (原版 island surface)
     *   - 石层: Y=60~63 (原版 stone layer)
     *   - 基岩: Y=-64~-63 (原版 bedrock layer)
     *   - 群系: Warm Ocean / Lukewarm Ocean / Ocean / Cold Ocean
     */
    private void generateIslands(World world, ChunkData data, Random random,
                                 int chunkX, int chunkZ, int minY, int maxY, int cx, int cz) {
        // 原版参数
        int seaLevel = 63;              // 原版海平面 Y=63
        int baseY = 64;                 // 原版基准高度 (岛面)
        int bedrockY = -64;             // 原版基岩层 Y=-64

        // 以 16x16 格为宏区块单元, 用确定性伪随机决定该区块是岛心还是海
        int zoneX = Math.floorDiv(cx, 16);
        int zoneZ = Math.floorDiv(cz, 16);
        long h = mix64(zoneX * 0x9E3779B97F4A7C15L + zoneZ * 0xBF58476D1CE4E5B9L);
        double zoneFactor = (h & 0xFFFF) / 65535.0; // 0..1
        double islandChance = plugin.getConfig().getDouble("arena.islands.island-chance", 0.55);

        // 决定是否生成岛屿 (按宏区块概率)
        boolean isIsland = zoneFactor < islandChance;

        // 计算岛心 (如果在岛屿区)
        // 原版参数: 岛心在宏区块内随机偏移 (偏移量 < 8 格, 确保岛心在区块内)
        double islandCX = cx; // 默认使用区块中心
        double islandCZ = cz;
        if (isIsland) {
            double[] center = islandCenter(zoneX, zoneZ, zoneFactor);
            // 限制偏移在宏区块内 (-8 ~ +8 格)
            islandCX = Math.max(zoneX * 16.0, Math.min((zoneX + 1) * 16.0 - 1.0, center[0]));
            islandCZ = Math.max(zoneZ * 16.0, Math.min((zoneZ + 1) * 16.0 - 1.0, center[1]));
        }

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int wx = chunkX * 16 + lx, wz = chunkZ * 16 + lz;
                double distToCenter = Math.hypot(wx - islandCX, wz - islandCZ);

                // 原版参数: 岛屿半径 16~48 格 (jigsaw 特征默认值)
                double islandRadius = isIsland ? (16 + ((h >> 16) & 0xFF) / 255.0 * 32) : 0;

                int surface;
                if (isIsland && distToCenter < islandRadius) {
                    // 岛上: 中心高, 边缘渐入海面
                    // 原版参数: 中心高度 Y=68~72, 边缘 Y=64
                    double t = distToCenter / Math.max(1, islandRadius);
                    surface = (int) (baseY + 4 + (1 - t) * 4 - (t * t) * 2);
                } else {
                    // 海底: 原版参数 Y=55~60
                    surface = baseY - 8 - (int)((h >> 8) & 0x7);
                }

                // 确保 surface 在合理范围内
                surface = Math.max(bedrockY + 2, Math.min(seaLevel + 10, surface));

                for (int y = bedrockY; y <= surface; y++) {
                    Material m;
                    if (y <= bedrockY + 1) m = Material.BEDROCK;
                    else if (y <= surface - 5) m = Material.STONE;
                    else if (y <= surface - 1) m = Material.DIRT;
                    else if (y < seaLevel) m = Material.GRASS_BLOCK;
                    else m = Material.GRASS_BLOCK;
                    // 水下到岛面边缘过渡: 沙质
                    if (y <= seaLevel && surface >= seaLevel && y < surface) m = Material.SAND;
                    data.setBlock(lx, y, lz, m);
                }
                // 海面填水: 在 surface 上方到海面之间补水
                // 原版参数: 水面覆盖到 Y=63
                if (isIsland && distToCenter >= islandRadius - 4) {
                    // 岛屿边缘过渡带: 填充水
                    int fillFrom = Math.max(surface + 1, bedrockY + 2);
                    for (int y = fillFrom; y <= seaLevel; y++) {
                        data.setBlock(lx, y, lz, Material.WATER);
                    }
                } else if (!isIsland) {
                    // 非岛屿区域: 全海面以下填水
                    for (int y = Math.max(surface + 1, bedrockY + 2); y <= seaLevel; y++) {
                        data.setBlock(lx, y, lz, Material.WATER);
                    }
                }
            }
        }
    }

    // ==================== THE_END: 浮空末地岛群 + 黑曜石柱 + 末地城塔楼 ====================

    /**
     * 模拟原版末地: 中央大型浮空主岛 + 外围随机浮空小岛。
     *  - 方块: 末地石 (END_STONE), 岛底裸露, 偶尔黑曜石/紫珀块点缀
     *  - 建筑: 主岛中央黑曜石柱 + 末地城塔楼 (紫珀块+末地石砖小塔, 简化版)
     *  - 群系: 主岛 THE_END, 高地 END_HIGHLANDS, 中地 END_MIDLANDS, 贫瘠 END_BARRENS, 小岛 SMALL_END_ISLANDS
     *  - 底部铺一层基岩地壳兜底, 掉下岛落在基岩上 (对局防虚空死), 岛上保持浮空地貌
     */
    private void generateTheEnd(World world, ChunkData data, Random random,
                                int chunkX, int chunkZ, int minY, int maxY, int cx, int cz, BiomeGrid biome) {
        int baseY = plugin.getConfig().getInt("world.base-height", 64);
        int mainIslandR = 46;                    // 主岛半径
        double mainLift = 6;                     // 主岛抬升
        long seed = world.getSeed();
        // 主岛圆心: 世界坐标 (8,8) = chunk (0,0) 的正中心, 便于建筑稳定落位
        double mainCX = 8, mainCZ = 8;

        // 全列: 先铺底部兜底地壳 (基岩2层 + 末地石1层), 防掉入虚空
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                data.setBlock(lx, minY, lz, Material.BEDROCK);
                data.setBlock(lx, minY + 1, lz, Material.BEDROCK);
                data.setBlock(lx, minY + 2, lz, Material.END_STONE);
            }
        }

        // 主岛: 以 (8,8) 为圆心的大浮空末地石岛, 顶面缓丘, 边缘下探成岛底
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int wx = chunkX * 16 + lx, wz = chunkZ * 16 + lz;
                double d = Math.hypot(wx - mainCX, wz - mainCZ);
                if (d >= mainIslandR) continue;
                double t = d / mainIslandR;
                double wobble = Math.sin(wx * 0.11) * Math.cos(wz * 0.13) * 2.5;
                int islandTop = (int) (baseY + mainLift + (1 - t) * 7 + wobble);
                int islandBottom = (int) (baseY + mainLift - 10); // 岛底厚度
                for (int y = islandBottom; y <= islandTop; y++) {
                    Material m = Material.END_STONE;
                    if (y == islandTop) m = Material.END_STONE;
                    else if (random.nextDouble() < 0.004) m = Material.OBSIDIAN; // 偶发黑曜石
                    data.setBlock(lx, y, lz, m);
                }
            }
        }

        // 外围浮空小岛: 以 16 格宏区块单元, 确定性随机生成小岛
        int zoneX = Math.floorDiv(cx, 16);
        int zoneZ = Math.floorDiv(cz, 16);
        long zh = mix64(zoneX * 0x9E3779B97F4A7C15L + zoneZ * 0xBF58476D1CE4E5B9L);
        double zoneFactor = (zh & 0xFFFF) / 65535.0;
        double isleChance = plugin.getConfig().getDouble("arena.the_end.island-chance", 0.42);
        double[] cen = endIslandCenter(zoneX, zoneZ, zoneFactor, seed);

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                // 跳过主岛范围内的列 (主岛已生成)
                int wx = chunkX * 16 + lx, wz = chunkZ * 16 + lz;
                if (Math.hypot(wx - mainCX, wz - mainCZ) < mainIslandR + 6) continue;

                double distToCenter = Math.hypot(wx - cen[0], wz - cen[1]);
                double isleRadius = 7 + ((zh >> 16) & 0xFF) / 255.0 * 16; // 7..23 半径
                if (distToCenter >= isleRadius) continue;
                if (zoneFactor > isleChance && distToCenter > 8) continue; // 低概率岛稀疏化

                // 小岛高度: 在主岛层面下方浮动
                float yb = (float) (((zh >> 8) & 0xFF) / 255.0);
                int islandBase = (int) (baseY - 6 + yb * 5); // 略低于主岛
                double t = distToCenter / Math.max(1, isleRadius);
                int islandTop = (int) (islandBase + (1 - t) * 4 + Math.sin(wx * 0.07) * Math.cos(wz * 0.09) * 1.5);
                int islandBottom = islandBase - 6;
                for (int y = islandBottom; y <= islandTop; y++) {
                    Material m = Material.END_STONE;
                    if (y == islandTop && random.nextDouble() < 0.02) m = Material.PURPUR_BLOCK;
                    data.setBlock(lx, y, lz, m);
                }
            }
        }

        // 主岛建筑 (仅在 chunk 0,0 生成, 因为它落在主岛中心附近):
        if (chunkX == 0 && chunkZ == 0) {
            // 黑曜石柱: 主岛中心 (8,8), 从岛顶向上 12 格
            int py = (int) (baseY + mainLift + 7); // 主岛中心顶面
            for (int i = 1; i <= 12; i++) data.setBlock(8, py + i, 8, Material.OBSIDIAN);
            // 黑曜石柱基座 (3x3)
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++)
                    data.setBlock(8 + dx, py, 8 + dz, Material.OBSIDIAN);

            // 末地城塔楼: 紫珀块小塔 (简化), 主岛中央偏侧, 局部坐标 (13,8) 落在 chunk0,0 内
            int twrX = 13, twrZ = 8;
            if (Math.hypot(twrX - mainCX, twrZ - mainCZ) < mainIslandR - 4) {
                int ty = (int) (baseY + mainLift + (1 - Math.hypot(twrX - mainCX, twrZ - mainCZ) / mainIslandR) * 7);
                // 底座 5x5 末地石砖 (中心 13,8 → 范围 11..15, 0..15 内)
                for (int dx = -2; dx <= 2; dx++)
                    for (int dz = -2; dz <= 2; dz++)
                        data.setBlock(twrX + dx, ty + 1, twrZ + dz, Material.END_STONE_BRICKS);
                // 塔身 3x3 紫珀块, 高 8
                for (int dy = 2; dy <= 9; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            boolean edge = Math.abs(dx) == 1 || Math.abs(dz) == 1;
                            Material m = edge ? Material.PURPUR_PILLAR : Material.PURPUR_BLOCK;
                            data.setBlock(twrX + dx, ty + dy, twrZ + dz, m);
                        }
                    }
                }
                // 顶部末地石砖尖顶 + 末地烛
                for (int dx = -1; dx <= 1; dx++)
                    for (int dz = -1; dz <= 1; dz++)
                        data.setBlock(twrX + dx, ty + 10, twrZ + dz, Material.END_STONE_BRICKS);
                data.setBlock(twrX, ty + 11, twrZ, Material.END_ROD);
            }
        }

        // 群系设置
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int wx = chunkX * 16 + lx, wz = chunkZ * 16 + lz;
                double d = Math.hypot(wx - mainCX, wz - mainCZ);
                Biome b;
                if (d < mainIslandR * 0.5) b = Biome.THE_END;
                else if (d < mainIslandR) b = Biome.END_MIDLANDS;
                else if (d < mainIslandR + 40) b = Biome.END_HIGHLANDS;
                else b = Biome.SMALL_END_ISLANDS;
                biome.setBiome(lx, lz, b);
            }
        }
    }

    /** 末地小岛中心 (确定性, 含种子) */
    private double[] endIslandCenter(int zoneX, int zoneZ, double zoneFactor, long seed) {
        double ox = ((zoneFactor * 8) - 4);
        double oz = (((mix64(zoneX * 0x85EBCA6BL + zoneZ * 0xC2B2AE35L + seed) & 0xFFFF) / 65535.0 - 0.5) * 8);
        return new double[]{zoneX * 16 + 8 + ox, zoneZ * 16 + 8 + oz};
    }

    // ==================== BADLANDS: 红沙+彩陶瓦层, 被侵蚀的平顶山丘 ====================

    /**
     * 模拟原版恶地 (Badlands): 红沙地表 + 陶瓦层 (各色) + 平顶/侵蚀山丘。
     *  - 地表: RED_SAND (红沙), 红沙层下是彩陶瓦层 (TERRACOTTA 各色)
     *  - 地形: 平顶高原台地 + 被侵蚀的尖峰/沟壑 (恶地标志性侵蚀地貌)
     *  - 建筑/矿产: 裸露的金矿脉 (GOLD_ORE), 偶发稀疏死灌木/仙人掌
     *  - 群系: BADLANDS / ERODED_BADLANDS / WOODED_BADLANDS
     */
    private void generateBadlands(World world, ChunkData data, Random random,
                                  int chunkX, int chunkZ, int minY, int maxY, int cx, int cz, BiomeGrid biome) {
        int baseY = plugin.getConfig().getInt("world.base-height", 64);

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int wx = chunkX * 16 + lx, wz = chunkZ * 16 + lz;
                // 恶地平顶台地 + 侵蚀沟壑: 用低频正弦 + 哈希抖动模拟台地起伏
                long h = mix64(wx * 0x9E3779B97F4A7C15L + wz * 0xBF58476D1CE4E5B9L);
                double plateau = Math.sin(wx * 0.045) * Math.cos(wz * 0.05) * 8
                        + Math.sin((wx + wz) * 0.02) * 4;
                double erode = ((h & 0xFF) / 255.0 - 0.5) * 5;   // 侵蚀抖动 -2.5..2.5
                int surface = Math.max(minY + 4, Math.min(maxY - 1,
                        baseY + 4 + (int) (plateau + erode)));

                // 恶地分层: 顶部红沙, 其下陶瓦层, 再下石头
                int terracottaDepth = 3 + (int) ((h >> 8) & 3); // 3..6 层
                for (int y = minY; y <= surface; y++) {
                    Material m;
                    if (y <= minY + 1) m = Material.BEDROCK;
                    else if (y <= surface - terracottaDepth) m = Material.STONE;
                    else if (y < surface) m = terracottaBlock(h, y);
                    else m = Material.RED_SAND;   // 顶部红沙
                    data.setBlock(lx, y, lz, m);
                }

                // 裸露金矿脉: 在陶瓦层或石层中偶发生成金矿
                if (random.nextDouble() < 0.012 && surface > minY + 3) {
                    int gy = minY + 3 + random.nextInt(Math.max(1, surface - minY - 3));
                    data.setBlock(lx, gy, lz, Material.GOLD_ORE);
                }
            }
        }

        // 群系设置
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int wx = chunkX * 16 + lx, wz = chunkZ * 16 + lz;
                long h = mix64(wx * 0x92C8A19BL + wz * 0x0D1B9E35L);
                double r = (h & 0xFFFF) / 65535.0;
                Biome b;
                if (r < 0.18) b = Biome.ERODED_BADLANDS;      // 侵蚀恶地
                else if (r < 0.28) b = Biome.WOODED_BADLANDS; // 稀疏林地恶地
                else b = Biome.BADLANDS;
                biome.setBiome(lx, lz, b);
            }
        }
    }

    /** 恶地陶瓦层方块 (按高度和哈希选色) */
    private Material terracottaBlock(long h, int y) {
        long r = mix64(h ^ (y * 0x9E3779B97F4A7C15L));
        return switch ((int) (r & 7)) {
            case 0 -> Material.RED_TERRACOTTA;
            case 1 -> Material.ORANGE_TERRACOTTA;
            case 2 -> Material.YELLOW_TERRACOTTA;
            case 3 -> Material.WHITE_TERRACOTTA;
            case 4 -> Material.GRAY_TERRACOTTA;
            case 5 -> Material.BROWN_TERRACOTTA;
            case 6 -> Material.TERRACOTTA;
            default -> Material.RED_SANDSTONE;
        };
    }

    // ==================== NETHER: 下界 (地狱岩起伏 + 岩浆湖 + 灵魂沙峡谷 + 玄武岩柱) ====================

    /**
     * 模拟原版下界 (Nether): 地狱岩地表起伏 + 岩浆湖 + 灵魂沙峡谷 + 玄武岩柱。
     *  - 地表: NETHERRACK (地狱岩) 起伏, 偶发岩浆湖/岩浆坑
     *  - 灵魂沙峡谷: SOUL_SAND/SOUL_SOIL 层 + 骨骼遗迹柱
     *  - 玄武岩柱: BASALT 柱状节理 (原版玄武岩三角洲)
     *  - 发光岩浆块/荧石点缀, 地狱石英矿脉
     *  - 群系: NETHER_WASTES / SOUL_SAND_VALLEY / CRIMSON_FOREST / WARPED_FOREST / BASALT_DELTAS
     */
    private void generateNether(World world, ChunkData data, Random random,
                                int chunkX, int chunkZ, int minY, int maxY, int cx, int cz, BiomeGrid biome) {
        int baseY = plugin.getConfig().getInt("world.base-height", 64);
        long seed = world.getSeed();

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int wx = chunkX * 16 + lx, wz = chunkZ * 16 + lz;
                long h = mix64(wx * 0x9E3779B97F4A7C15L + wz * 0xBF58476D1CE4E5B9L + seed);

                // 判断宏区块类型 (下界荒原/灵魂沙峡谷/玄武岩三角洲...)
                int zoneX = Math.floorDiv(cx, 16), zoneZ = Math.floorDiv(cz, 16);
                long zh = mix64(zoneX * 0x85EBCA6BL + zoneZ * 0xC2B2AE35L + seed);
                double zr = (zh & 0xFFFF) / 65535.0; // 0..1 决定该区块子群系

                // 地狱岩起伏
                double terrain = Math.sin(wx * 0.08) * Math.cos(wz * 0.09) * 4
                        + Math.sin((wx + wz) * 0.04) * 3;
                int surface = Math.max(minY + 4, Math.min(maxY - 1, baseY + 6 + (int) terrain));

                // 灵魂沙峡谷: 地形下切 (低洼)
                boolean soulValley = zr > 0.72;
                if (soulValley) {
                    surface = Math.max(minY + 4, (int) (surface * 0.78));
                }

                // 逐列填充
                for (int y = minY; y <= surface; y++) {
                    Material m;
                    if (y <= minY + 1) m = Material.BEDROCK;
                    else if (y <= surface - 4) m = Material.NETHERRACK; // 深部地狱岩
                    else if (y < surface) m = netherSubBlock(h, y, soulValley);
                    else m = netherSurfaceBlock(h, zr);
                    data.setBlock(lx, y, lz, m);
                }

                // 岩浆湖: 偶发低洼区填入岩浆 (列级判断, 就地填表层)
                if (random.nextDouble() < 0.05 && !soulValley) {
                    int poolY = surface;
                    if (poolY > minY + 2) data.setBlock(lx, poolY, lz, Material.LAVA);
                }
                // 玄武岩柱: 玄武岩三角洲区生成柱状节理
                if (zr > 0.90) {
                    if (random.nextDouble() < 0.10) {
                        int colH = 4 + random.nextInt(6);
                        int startY = surface + 1;
                        for (int dy = 0; dy < colH; dy++) {
                            if (startY + dy <= maxY - 2) data.setBlock(lx, startY + dy, lz, Material.BASALT);
                        }
                    }
                }
                // 发光岩浆块: 地表点缀
                if (random.nextDouble() < 0.03) {
                    int glowY = surface;
                    if (glowY > minY + 2) data.setBlock(lx, glowY, lz, Material.MAGMA_BLOCK);
                }
                // 地狱石英矿脉
                if (random.nextDouble() < 0.04 && surface > minY + 4) {
                    int qy = minY + 4 + random.nextInt(Math.max(1, surface - minY - 4));
                    data.setBlock(lx, qy, lz, Material.NETHER_QUARTZ_ORE);
                }
            }
        }

        // 群系设置
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int wx = chunkX * 16 + lx, wz = chunkZ * 16 + lz;
                int zoneX = Math.floorDiv(cx, 16), zoneZ = Math.floorDiv(cz, 16);
                long zh = mix64(zoneX * 0x85EBCA6BL + zoneZ * 0xC2B2AE35L + seed);
                double zr = (zh & 0xFFFF) / 65535.0;
                Biome b;
                if (zr > 0.90) b = Biome.BASALT_DELTAS;
                else if (zr > 0.80) b = Biome.CRIMSON_FOREST;
                else if (zr > 0.72) b = Biome.SOUL_SAND_VALLEY;
                else if (zr > 0.64) b = Biome.WARPED_FOREST;
                else b = Biome.NETHER_WASTES;
                biome.setBiome(lx, lz, b);
            }
        }
    }

    /** 下界次表层 (表面下) */
    private Material netherSubBlock(long h, int y, boolean soulValley) {
        long r = mix64(h ^ (y * 0x9E3779B97F4A7C15L));
        if (soulValley) return (r & 1) == 0 ? Material.SOUL_SAND : Material.SOUL_SOIL;
        return (r & 1) == 0 ? Material.NETHERRACK : Material.BLACKSTONE;
    }

    /** 下界地表方块 (按群系) */
    private Material netherSurfaceBlock(long h, double zr) {
        if (zr > 0.90) return Material.BASALT;
        if (zr > 0.80) return Material.CRIMSON_NYLIUM;
        if (zr > 0.72) return Material.SOUL_SAND;
        if (zr > 0.64) return Material.WARPED_NYLIUM;
        return Material.NETHERRACK;
    }

    // ==================== CITY: 城市 (平坦地面 + 街道网格 + 建筑群 + 公园) ====================

    /**
     * 模拟现代城市: 平地街道网格 + 建筑群 + 公园绿地。
     *  - 地面: 平坦草地/混凝土广场, 街道用黑色混凝土+石板铺设
     *  - 建筑: 以宏区块为单位生成一栋建筑 (混凝土墙+玻璃窗+屋顶), 街道交叉处留空
     *  - 公园: 偶发公园广场 (草地+树木+水源)
     *  - 群系: PLAINS (城市所在主世界)
     */
    private void generateCity(World world, ChunkData data, Random random,
                              int chunkX, int chunkZ, int minY, int maxY, int cx, int cz, BiomeGrid biome) {
        int baseY = plugin.getConfig().getInt("world.base-height", 64);
        long seed = world.getSeed();

        // 城市街区: 以 32x32 宏区块为单元, 街道网格 (每 32 格一条 3 格宽街道)
        int baseSurface = baseY;

        // 平坦地面
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int wx = chunkX * 16 + lx, wz = chunkZ * 16 + lz;
                // 街道判定: 世界坐标是 32 的倍数附近 (街道网格交叉)
                boolean isRoadX = (Math.floorMod(wx, 32) >= 14 && Math.floorMod(wx, 32) <= 17);
                boolean isRoadZ = (Math.floorMod(wz, 32) >= 14 && Math.floorMod(wz, 32) <= 17);
                boolean isRoad = isRoadX || isRoadZ;
                boolean isCross = isRoadX && isRoadZ;

                for (int y = minY; y <= baseSurface; y++) {
                    Material m;
                    if (y <= minY + 1) m = Material.BEDROCK;
                    else if (y <= baseSurface - 3) m = Material.STONE;
                    else if (y < baseSurface) m = Material.DIRT;
                    else if (isRoad) m = isCross ? Material.LIGHT_GRAY_CONCRETE : Material.BLACK_CONCRETE;
                    else m = Material.GRASS_BLOCK;
                    data.setBlock(lx, y, lz, m);
                }
            }
        }

        // 宏区块生成建筑/公园 (以 32x32 宏区块为单位, 建筑锚点固定落在宏区块中心局部(8,8))
        // 建筑锚点世界坐标 = (zoneX*32+8, zoneZ*32+8), 其 chunk = (zoneX*2, zoneZ*2)
        int zoneX = Math.floorDiv(cx, 32), zoneZ = Math.floorDiv(cz, 32);
        long zh = mix64(zoneX * 0x9E3779B97F4A7C15L + zoneZ * 0xBF58476D1CE4E5B9L + seed);
        double zr = (zh & 0xFFFF) / 65535.0;
        int anchorChunkX = (zoneX * 32 + 8) >> 4;   // = zoneX*2 (因为 32 倍数的 chunk 除数)
        int anchorChunkZ = (zoneZ * 32 + 8) >> 4;
        if (chunkX == anchorChunkX && chunkZ == anchorChunkZ) {
            int bx = 8, bz = 8;   // 宏区块中心局部坐标 (锚点)
            // 公园 (草地+树+水) 或 建筑
            if (zr > 0.82) {
                buildingCols(data, bx, bz, baseSurface, maxY, "park", random);
            } else if (zr > 0.30) {
                String style = (zr > 0.62) ? "tower" : "block";
                buildingCols(data, bx, bz, baseSurface, maxY, style, random);
            } else if (zr > 0.15) {
                // 中央喷泉/广场装饰
                data.setBlock(bx, baseSurface + 1, bz, Material.SEA_LANTERN);
            }
            // 否则留空 (空地/广场)
        }

        // 群系: 城市主世界 PLAINS
        for (int lx = 0; lx < 16; lx++)
            for (int lz = 0; lz < 16; lz++)
                biome.setBiome(lx, lz, Biome.PLAINS);
    }

    /** 城市建筑/公园生成 (局部坐标 0..15, 但可能跨 chunk 边界, 用世界坐标落位) */
    private void buildingCols(ChunkData data, int bx, int bz, int baseY, int maxY, String style, Random random) {
        // 建筑: 5x5 底, 高度按风格
        int h;
        if ("tower".equals(style)) h = 12 + random.nextInt(8);   // 高楼
        else if ("park".equals(style)) h = -1;                     // 公园特殊
        else h = 6 + random.nextInt(4);                            // 普通楼

        if ("park".equals(style)) {
            // 公园: 中央水景 + 外圈草地树
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    int x = bx + dx, z = bz + dz;
                    if (x < 0 || x > 15 || z < 0 || z > 15) continue;
                    if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                        data.setBlock(x, baseY + 1, z, Material.WATER);
                    } else if (random.nextDouble() < 0.3) {
                        data.setBlock(x, baseY + 2, z, Material.OAK_LOG);
                        data.setBlock(x, baseY + 3, z, Material.OAK_LEAVES);
                    }
                }
            }
            return;
        }

        // 建筑: 5x5 混凝土外墙 + 玻璃窗 + 平顶
        int bx0 = Math.max(0, bx - 2), bx1 = Math.min(15, bx + 2);
        int bz0 = Math.max(0, bz - 2), bz1 = Math.min(15, bz + 2);
        for (int dx = bx0; dx <= bx1; dx++) {
            for (int dz = bz0; dz <= bz1; dz++) {
                boolean edge = Math.abs(dx - bx) == 2 || Math.abs(dz - bz) == 2;
                // 底垫
                data.setBlock(dx, baseY + 1, dz, edge ? materialLow(random) : Material.SMOOTH_STONE);
                // 楼体
                for (int dy = 2; dy <= h; dy++) {
                    if (!edge) continue; // 内部空气
                    int wy = baseY + dy;
                    if (wy >= maxY - 1) break;
                    Material wall = materialWall(random);
                    data.setBlock(dx, wy, dz, wall);
                }
                // 玻璃窗: 每 3 层嵌入
                if (edge) {
                    for (int dy = 3; dy <= h; dy += 3) {
                        int wy = baseY + dy;
                        if (wy >= maxY - 1) break;
                        data.setBlock(dx, wy, dz, Material.GLASS_PANE);
                    }
                }
            }
        }
        // 平顶
        int roofY = baseY + h;
        if (roofY < maxY - 1) {
            for (int dx = bx0; dx <= bx1; dx++)
                for (int dz = bz0; dz <= bz1; dz++)
                    data.setBlock(dx, roofY + 1, dz, Material.SMOOTH_STONE);
        }
    }

    private Material materialLow(Random random) {
        return switch (random.nextInt(4)) {
            case 0 -> Material.STONE_BRICKS;
            case 1 -> Material.GRAY_CONCRETE;
            case 2 -> Material.WHITE_CONCRETE;
            default -> Material.SMOOTH_STONE;
        };
    }

    private Material materialWall(Random random) {
        return switch (random.nextInt(4)) {
            case 0 -> Material.LIGHT_GRAY_CONCRETE;
            case 1 -> Material.GRAY_CONCRETE;
            case 2 -> Material.WHITE_CONCRETE;
            default -> Material.STONE_BRICKS;
        };
    }

    // ==================== NORMAL: 正常主世界 (多样地形 + 河流) ====================

    /**
     * 正常主世界: 模拟原版主世界的多样地貌。
     *  - 地形: 平原 + 丘陵 + 山地 (多噪声叠加), 中央相对平坦适合出生
     *  - 河流: 蜿蜒水道, 从中心两端贯穿
     *  - 地表: 草地/石头/沙地, 树下/水畔过渡
     *  - 群系: PLAINS / FOREST / MOUNTAINS / RIVER 按地形高低分布
     */
    private void generateNormal(World world, ChunkData data, Random random,
                                int chunkX, int chunkZ, int minY, int maxY, int cx, int cz, BiomeGrid biome) {
        int baseY = plugin.getConfig().getInt("world.base-height", 64);
        long seed = world.getSeed();
        double plainRadius = Math.max(80, plugin.getConfig().getDouble("world.plain-radius", 120));

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int wx = chunkX * 16 + lx, wz = chunkZ * 16 + lz;
                long h = mix64(wx * 0x9E3779B97F4A7C15L + wz * 0xBF58476D1CE4E5B9L + seed);
                double d = Math.hypot(wx, wz);

                // 多噪声叠加地形: 平原 + 低频丘陵 + 偶尔山地
                double baseTerrain = Math.sin(wx * 0.03) * Math.cos(wz * 0.035) * 5
                        + Math.sin((wx + wz) * 0.015) * 3;
                double wobble = ((h & 0xFF) / 255.0 - 0.5) * 2;
                int surface;
                if (d < plainRadius) {
                    surface = baseY + (int) baseTerrain + (int) wobble;
                } else {
                    double t = (d - plainRadius) / Math.max(1, plainRadius);
                    surface = baseY + (int) (baseTerrain + wobble + t * 26); // 外围山地抬升
                }

                // 河流: 蜿蜒水道 (wx 与 wz 线性组合的 sin 判定), 下切地表
                double river = Math.sin(wx * 0.05 + seed % 100) + Math.cos(wz * 0.05);
                boolean isRiver = Math.abs(river) < 0.6 && d > plainRadius * 0.6;
                if (isRiver) surface = Math.max(minY + 3, surface - 8);

                surface = Math.max(minY + 2, Math.min(maxY - 1, surface));

                // 逐列填充
                for (int y = minY; y <= surface; y++) {
                    Material m;
                    if (y <= minY + 1) m = Material.BEDROCK;
                    else if (y <= surface - 4) m = Material.STONE;
                    else if (y < surface) m = surface > baseY + 14 ? Material.STONE : Material.DIRT;
                    else {
                        boolean highland = surface > baseY + 14;
                        m = highland ? Material.STONE
                                : isRiver ? Material.SAND
                                : Material.GRASS_BLOCK;
                    }
                    data.setBlock(lx, y, lz, m);
                }
                // 河流水面补水
                if (isRiver) {
                    int waterY = baseY - 4;
                    if (waterY < maxY - 1) {
                        int fillTop = Math.min(surface + 1, waterY);
                        for (int y = surface + 1; y <= waterY; y++) {
                            data.setBlock(lx, y, lz, Material.WATER);
                        }
                    }
                }
            }
        }

        // 群系设置
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int wx = chunkX * 16 + lx, wz = chunkZ * 16 + lz;
                double d = Math.hypot(wx, wz);
                long h = mix64(wx * 0x92C8A19BL + wz * 0x0D1B9E35L + seed);
                double r = (h & 0xFFFF) / 65535.0;
                Biome b;
                if (d < plainRadius) b = Biome.PLAINS;
                else if (r < 0.35) b = Biome.FOREST;
                else if (r < 0.55) b = Biome.WINDSWEPT_HILLS;
                else b = Biome.PLAINS;
                biome.setBiome(lx, lz, b);
            }
        }
    }

    /** 计算宏区块的岛心坐标 (确定性, 限制在宏区块内) */
    private double[] islandCenter(int zoneX, int zoneZ, double zoneFactor) {
        // 原版参数: 岛心在宏区块中心 ±8 格内偏移
        // zoneFactor 控制 X 偏移 (0~1 → -8~+8)
        // 另一个噪声控制 Z 偏移
        double ox = (zoneFactor * 16.0) - 8.0;
        double oz = (((mix64(zoneX * 0x85EBCA6BL + zoneZ * 0xC2B2AE35L) & 0xFFFF) / 65535.0 - 0.5) * 16.0);
        return new double[]{zoneX * 16.0 + 8.0 + ox, zoneZ * 16.0 + 8.0 + oz};
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 33)) * 0xFF51AFD7ED558CCDL;
        z = (z ^ (z >>> 33)) * 0xC4CEB9FE1A85EC53L;
        return z ^ (z >>> 33);
    }

    // ==================== 树木/装饰 (大幅降低密度) ====================

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false; // 自己控制装饰, 不依赖原版浓厚植被
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateBedrock() {
        return false; // 自己写基岩
    }

    // ==================== 地形遮挡 (掩体结构) ====================

    /**
     * 在地表放置遮挡/掩体结构 (岩石露头/石柱/灌木), 形成遮蔽地形, 供玩家躲避与战术走位。
     * 基于逐块确定性哈希 (mix64), 同一区块无论何时生成结果一致。
     * 结构全部落在本 chunk 内 (局部坐标 0..15), 无跨 chunk 边界问题。
     *
     * 遮挡率由 config world.cover-chance (0..1) 控制, 默认 0.28 (约每 3~4 个区块一处)。
     */
    private void addCover(ChunkData data, Random random, int chunkX, int chunkZ, int minY, int maxY) {
        double coverChance = plugin.getConfig().getDouble("world.cover-chance", 0.28);
        if (coverChance <= 0) return;

        // 确定性随机: 本 chunk 是否生成遮挡
        long h = mix64(chunkX * 0x9E3779B97F4A7C15L + chunkZ * 0xBF58476D1CE4E5B9L);
        double r = (h & 0xFFFF) / 65535.0;
        if (r > coverChance) return;

        // 遮挡数量: 1~2 处
        int structures = 1 + (int) ((h >> 16) & 1);
        for (int s = 0; s < structures; s++) {
            long h2 = mix64(h ^ (s * 0x85EBCA6BL));
            int lx = 2 + (int) ((h2 >> 8) & 11);   // 2..13
            int lz = 2 + (int) ((h2 >> 20) & 11);  // 2..13
            // 找该列地表高度 (从顶向下首个非空气)
            int surface = -1;
            for (int y = maxY - 1; y >= minY; y--) {
                if (data.getType(lx, y, lz) != Material.AIR) { surface = y; break; }
            }
            if (surface < minY) continue;

            // 结构类型按地形差异化 (由 h2 决定类型)
            Material block = coverBlock(h2);
            int height = 2 + (int) ((h2 >> 30) & 3); // 2..5
            // 柱状: 从地表向上叠 block, 顶部加装饰
            for (int dy = 1; dy <= height; dy++) {
                int y = surface + dy;
                if (y >= maxY - 1) break;
                data.setBlock(lx, y, lz, block);
            }
            int top = Math.min(maxY - 2, surface + height);
            // 顶部装饰: 放在柱顶上一格 (若是空气则放植物/灌木), 按地形差异化
            int decorY = Math.min(maxY - 1, top + 1);
            Material topMat = coverTop(h2);
            if (topMat != Material.AIR && data.getType(lx, decorY, lz).isAir()) {
                data.setBlock(lx, decorY, lz, topMat);
            }
        }
    }

    /** 地表遮挡主方块 (按地形差异化) */
    private Material coverBlock(long h) {
        return switch (type) {
            case DESERT -> ((h >> 40) & 1) == 0 ? Material.SANDSTONE : Material.SMOOTH_SANDSTONE;
            case ISLANDS -> ((h >> 40) & 1) == 0 ? Material.STONE : Material.ANDESITE;
            case BADLANDS -> ((h >> 40) & 1) == 0 ? Material.RED_SANDSTONE : Material.TERRACOTTA;
            case THE_END -> Material.END_STONE;
            case NETHER -> Material.NETHERRACK;
            case CITY -> Material.STONE_BRICKS;
            case NORMAL -> ((h >> 40) & 1) == 0 ? Material.STONE : Material.COBBLESTONE;
            default -> ((h >> 40) & 1) == 0 ? Material.STONE : Material.COBBLESTONE;
        };
    }

    /** 遮挡顶部装饰 (按地形差异化, AIR 表示无装饰) */
    private Material coverTop(long h) {
        return switch (type) {
            case DESERT -> ((h >> 44) & 1) == 0 ? Material.CACTUS : Material.DEAD_BUSH;
            case ISLANDS -> ((h >> 44) & 1) == 0 ? Material.SHORT_GRASS : Material.FERN;
            case BADLANDS -> ((h >> 44) & 1) == 0 ? Material.DEAD_BUSH : Material.CACTUS;
            case THE_END -> Material.AIR;
            case NETHER -> Material.AIR;
            case CITY -> Material.AIR;
            case NORMAL -> ((h >> 44) & 1) == 0 ? Material.SHORT_GRASS : Material.OAK_LEAVES;
            default -> ((h >> 44) & 1) == 0 ? Material.SHORT_GRASS : Material.OAK_LEAVES;
        };
    }

    /** 稀疏种树: 每个区块最多极少量, 按地形模板差异化 */
    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) {
        return java.util.List.of(new SparseTreePopulator());
    }

    /** 低密度树/仙人掌填充器: 每区块 0~2 棵, 不密集 */
    private static class SparseTreePopulator extends BlockPopulator {
        @Override
        public void populate(World world, Random random, org.bukkit.Chunk chunk) {
            // 决定该区块是否生树 (低概率)
            if (random.nextDouble() > 0.18) return; // 82% 区块无树
            int count = 1; // 每生树区块最多 1 棵
            for (int i = 0; i < count; i++) {
                int x = random.nextInt(16);
                int z = random.nextInt(16);
                int wx = chunk.getX() * 16 + x;
                int wz = chunk.getZ() * 16 + z;
                // 注意: 在 BlockPopulator 中禁止调用 getBlockAt/getHighestBlockYAt/getBiome
                // 这些调用会触发 chunk load, 导致线程阻塞甚至 StackOverflowError
                // 使用固定高度 + 确定性哈希判断地形 (避免chunk load)
                int baseY = world.getMinHeight() + 60;
                // 用 chunk 坐标哈希判断是否沙漠 (确定性, 不触发加载)
                long hash = mix64(wx * 31L + wz);
                boolean isDesert = (hash & 0xFF) < 60; // 约 23.5% 概率为沙漠
                if (isDesert) {
                    // 仙人掌: 简单放置, 无需查询地面类型
                    if (random.nextDouble() < 0.4) {
                        org.bukkit.block.Block b = world.getBlockAt(wx, baseY + 1, wz);
                        if (b.getType().isAir() || !b.getType().isSolid()) {
                            b.setType(Material.CACTUS, false);
                        }
                    }
                } else {
                    // 橡树
                    growOak(world, wx, baseY + 1, wz, random);
                }
            }
        }

        /** 小橡树: 树干 2-4 格 + 顶部稀疏树叶球 */
        private void growOak(World world, int x, int y, int z, Random random) {
            int trunk = 2 + random.nextInt(3); // 2..4
            for (int i = 0; i < trunk; i++) {
                org.bukkit.block.Block b = world.getBlockAt(x, y + i, z);
                if (b.getType().isAir() || !b.getType().isSolid()) b.setType(Material.OAK_LOG, false);
            }
            int top = y + trunk - 1;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        int yy = top + dy;
                        org.bukkit.block.Block b = world.getBlockAt(x + dx, yy, z + dz);
                        if (b.getType().isAir() || !b.getType().isSolid()) {
                            // 树叶球边缘随机保留, 内部填满, 低密度天然
                            if (Math.abs(dx) == 2 && Math.abs(dz) == 2 && random.nextDouble() < 0.5) continue;
                            b.setType(Material.OAK_LEAVES, false);
                        }
                    }
                }
            }
        }

        /** 确定性哈希: 用于在不触发 chunk load 的情况下判断地形 */
        private static long mix64(long z) {
            z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
            z = (z ^ (z >>> 27)) * 0x94d049bb133111eBL;
            return z ^ (z >>> 31);
        }
    }
}
