/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Chunk
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.World
 *  org.bukkit.block.Biome
 *  org.bukkit.block.Block
 *  org.bukkit.generator.BlockPopulator
 *  org.bukkit.generator.ChunkGenerator
 *  org.bukkit.generator.ChunkGenerator$BiomeGrid
 *  org.bukkit.generator.ChunkGenerator$ChunkData
 */
package com.terrabox;

import com.terrabox.TerraBoxPlugin;
import com.terrabox.TerrainType;
import java.util.List;
import java.util.Random;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;

public class CustomTerrainGenerator
extends ChunkGenerator {
    private final TerraBoxPlugin plugin;
    private final TerrainType type;

    public CustomTerrainGenerator(TerraBoxPlugin terraBoxPlugin, TerrainType terrainType) {
        this.plugin = terraBoxPlugin;
        this.type = terrainType;
    }

    public TerrainType type() {
        return this.type;
    }

    public ChunkGenerator.ChunkData generateChunkData(World world, Random random, int n, int n2, ChunkGenerator.BiomeGrid biomeGrid) {
        ChunkGenerator.ChunkData chunkData = this.createChunkData(world);
        int n3 = chunkData.getMinHeight();
        int n4 = chunkData.getMaxHeight();
        int n5 = n * 16 + 8;
        int n6 = n2 * 16 + 8;
        switch (this.type) {
            case DESERT: {
                this.generateDesert(world, chunkData, random, n, n2, n3, n4, n5, n6);
                break;
            }
            case ISLANDS: {
                this.generateIslands(world, chunkData, random, n, n2, n3, n4, n5, n6);
                break;
            }
            case THE_END: {
                this.generateTheEnd(world, chunkData, random, n, n2, n3, n4, n5, n6, biomeGrid);
                break;
            }
            case BADLANDS: {
                this.generateBadlands(world, chunkData, random, n, n2, n3, n4, n5, n6, biomeGrid);
                break;
            }
            case NETHER: {
                this.generateNether(world, chunkData, random, n, n2, n3, n4, n5, n6, biomeGrid);
                break;
            }
            case CITY: {
                this.generateCity(world, chunkData, random, n, n2, n3, n4, n5, n6, biomeGrid);
                break;
            }
            case NORMAL: {
                this.generateNormal(world, chunkData, random, n, n2, n3, n4, n5, n6, biomeGrid);
                break;
            }
            default: {
                this.generateDefault(world, chunkData, random, n, n2, n3, n4, n5, n6);
            }
        }
        if (this.type != TerrainType.THE_END && this.type != TerrainType.CITY && this.type != TerrainType.NETHER && this.plugin.getConfig().getBoolean("world.cover", true)) {
            this.addCover(chunkData, random, n, n2, n3, n4);
        }
        return chunkData;
    }

    private void generateDefault(World world, ChunkGenerator.ChunkData chunkData, Random random, int n, int n2, int n3, int n4, int n5, int n6) {
        double d = Math.max(80.0, this.plugin.getConfig().getDouble("world.plain-radius", 120.0));
        int n7 = this.plugin.getConfig().getInt("world.base-height", 64);
        for (int i = 0; i < 16; ++i) {
            for (int j = 0; j < 16; ++j) {
                int n8 = n * 16 + i;
                int n9 = n2 * 16 + j;
                double d2 = Math.sqrt(n8 * n8 + n9 * n9);
                int n10 = n7;
                if (d2 > d) {
                    double d3 = (d2 - d) / Math.max(1.0, d);
                    n10 = n7 + (int)(d3 * 24.0);
                }
                int n11 = (int)(Math.sin((double)n8 * 0.17) * Math.cos((double)n9 * 0.19) * 2.0);
                n10 = Math.max(n3 + 2, Math.min(n4 - 1, n10 + n11));
                for (int k = n3; k <= n10; ++k) {
                    Material material = k <= n3 + 1 ? Material.BEDROCK : (k <= n10 - 4 ? Material.STONE : (k < n10 ? Material.DIRT : Material.GRASS_BLOCK));
                    chunkData.setBlock(i, k, j, material);
                }
            }
        }
    }

    private void generateDesert(World world, ChunkGenerator.ChunkData chunkData, Random random, int n, int n2, int n3, int n4, int n5, int n6) {
        int n7 = this.plugin.getConfig().getInt("world.base-height", 64);
        for (int i = 0; i < 16; ++i) {
            for (int j = 0; j < 16; ++j) {
                int n8 = n * 16 + i;
                int n9 = n2 * 16 + j;
                double d = Math.sin((double)n8 * 0.09) * Math.cos((double)n9 * 0.11) * 3.0 + Math.sin((double)(n8 + n9) * 0.05) * 2.0;
                int n10 = Math.max(n3 + 2, Math.min(n4 - 1, n7 + (int)d));
                for (int k = n3; k <= n10; ++k) {
                    Material material = k <= n3 + 1 ? Material.BEDROCK : (k <= n10 - 3 ? Material.SANDSTONE : Material.SAND);
                    chunkData.setBlock(i, k, j, material);
                }
            }
        }
    }

    private void generateIslands(World world, ChunkGenerator.ChunkData chunkData, Random random, int n, int n2, int n3, int n4, int n5, int n6) {
        double d;
        int n7;
        int n8 = 63;
        int n9 = 64;
        int n10 = -64;
        int n11 = Math.floorDiv(n5, 16);
        long l = CustomTerrainGenerator.mix64((long)n11 * -7046029254386353131L + (long)(n7 = Math.floorDiv(n6, 16)) * -4658895280553007687L);
        double d2 = (double)(l & 0xFFFFL) / 65535.0;
        boolean bl = d2 < (d = this.plugin.getConfig().getDouble("arena.islands.island-chance", 0.55));
        double d3 = n5;
        double d4 = n6;
        if (bl) {
            double[] dArray = this.islandCenter(n11, n7, d2);
            d3 = Math.max((double)n11 * 16.0, Math.min((double)(n11 + 1) * 16.0 - 1.0, dArray[0]));
            d4 = Math.max((double)n7 * 16.0, Math.min((double)(n7 + 1) * 16.0 - 1.0, dArray[1]));
        }
        for (int i = 0; i < 16; ++i) {
            for (int j = 0; j < 16; ++j) {
                int n12;
                int n13;
                double d5;
                int n14 = n * 16 + i;
                int n15 = n2 * 16 + j;
                double d6 = Math.hypot((double)n14 - d3, (double)n15 - d4);
                double d7 = d5 = bl ? 16.0 + (double)(l >> 16 & 0xFFL) / 255.0 * 32.0 : 0.0;
                if (bl && d6 < d5) {
                    double d8 = d6 / Math.max(1.0, d5);
                    n13 = (int)((double)(n9 + 4) + (1.0 - d8) * 4.0 - d8 * d8 * 2.0);
                } else {
                    n13 = n9 - 8 - (int)(l >> 8 & 7L);
                }
                n13 = Math.max(n10 + 2, Math.min(n8 + 10, n13));
                for (n12 = n10; n12 <= n13; ++n12) {
                    Material material = n12 <= n10 + 1 ? Material.BEDROCK : (n12 <= n13 - 5 ? Material.STONE : (n12 <= n13 - 1 ? Material.DIRT : (n12 < n8 ? Material.GRASS_BLOCK : Material.GRASS_BLOCK)));
                    if (n12 <= n8 && n13 >= n8 && n12 < n13) {
                        material = Material.SAND;
                    }
                    chunkData.setBlock(i, n12, j, material);
                }
                if (bl && d6 >= d5 - 4.0) {
                    for (int k = n12 = Math.max(n13 + 1, n10 + 2); k <= n8; ++k) {
                        chunkData.setBlock(i, k, j, Material.WATER);
                    }
                    continue;
                }
                if (bl) continue;
                for (n12 = Math.max(n13 + 1, n10 + 2); n12 <= n8; ++n12) {
                    chunkData.setBlock(i, n12, j, Material.WATER);
                }
            }
        }
    }

    private void generateTheEnd(World world, ChunkGenerator.ChunkData chunkData, Random random, int n, int n2, int n3, int n4, int n5, int n6, ChunkGenerator.BiomeGrid biomeGrid) {
        int n7;
        int n8;
        int n9;
        int n10;
        double d;
        double d2;
        int n11;
        int n12;
        int n13 = this.plugin.getConfig().getInt("world.base-height", 64);
        int n14 = 46;
        double d3 = 6.0;
        long l = world.getSeed();
        double d4 = 8.0;
        double d5 = 8.0;
        for (n12 = 0; n12 < 16; ++n12) {
            for (n11 = 0; n11 < 16; ++n11) {
                chunkData.setBlock(n12, n3, n11, Material.BEDROCK);
                chunkData.setBlock(n12, n3 + 1, n11, Material.BEDROCK);
                chunkData.setBlock(n12, n3 + 2, n11, Material.END_STONE);
            }
        }
        for (n12 = 0; n12 < 16; ++n12) {
            for (n11 = 0; n11 < 16; ++n11) {
                int n15 = n * 16 + n12;
                int n16 = n2 * 16 + n11;
                d2 = Math.hypot((double)n15 - d4, (double)n16 - d5);
                if (d2 >= (double)n14) continue;
                d = d2 / (double)n14;
                double d6 = Math.sin((double)n15 * 0.11) * Math.cos((double)n16 * 0.13) * 2.5;
                n10 = (int)((double)n13 + d3 + (1.0 - d) * 7.0 + d6);
                for (n9 = n8 = (int)((double)n13 + d3 - 10.0); n9 <= n10; ++n9) {
                    Material material = Material.END_STONE;
                    if (n9 == n10) {
                        material = Material.END_STONE;
                    } else if (random.nextDouble() < 0.004) {
                        material = Material.OBSIDIAN;
                    }
                    chunkData.setBlock(n12, n9, n11, material);
                }
            }
        }
        n12 = Math.floorDiv(n5, 16);
        n11 = Math.floorDiv(n6, 16);
        long l2 = CustomTerrainGenerator.mix64((long)n12 * -7046029254386353131L + (long)n11 * -4658895280553007687L);
        d2 = (double)(l2 & 0xFFFFL) / 65535.0;
        d = this.plugin.getConfig().getDouble("arena.the_end.island-chance", 0.42);
        double[] dArray = this.endIslandCenter(n12, n11, d2, l);
        for (n7 = 0; n7 < 16; ++n7) {
            for (n10 = 0; n10 < 16; ++n10) {
                int n17;
                double d7;
                double d8;
                n8 = n * 16 + n7;
                n9 = n2 * 16 + n10;
                if (Math.hypot((double)n8 - d4, (double)n9 - d5) < (double)(n14 + 6) || (d8 = Math.hypot((double)n8 - dArray[0], (double)n9 - dArray[1])) >= (d7 = 7.0 + (double)(l2 >> 16 & 0xFFL) / 255.0 * 16.0) || d2 > d && d8 > 8.0) continue;
                float f = (float)((double)(l2 >> 8 & 0xFFL) / 255.0);
                int n18 = (int)((float)(n13 - 6) + f * 5.0f);
                double d9 = d8 / Math.max(1.0, d7);
                int n19 = (int)((double)n18 + (1.0 - d9) * 4.0 + Math.sin((double)n8 * 0.07) * Math.cos((double)n9 * 0.09) * 1.5);
                for (int i = n17 = n18 - 6; i <= n19; ++i) {
                    Material material = Material.END_STONE;
                    if (i == n19 && random.nextDouble() < 0.02) {
                        material = Material.PURPUR_BLOCK;
                    }
                    chunkData.setBlock(n7, i, n10, material);
                }
            }
        }
        if (n == 0 && n2 == 0) {
            n7 = (int)((double)n13 + d3 + 7.0);
            for (n10 = 1; n10 <= 12; ++n10) {
                chunkData.setBlock(8, n7 + n10, 8, Material.OBSIDIAN);
            }
            for (n10 = -1; n10 <= 1; ++n10) {
                for (n8 = -1; n8 <= 1; ++n8) {
                    chunkData.setBlock(8 + n10, n7, 8 + n8, Material.OBSIDIAN);
                }
            }
            n10 = 13;
            n8 = 8;
            if (Math.hypot((double)n10 - d4, (double)n8 - d5) < (double)(n14 - 4)) {
                int n20;
                int n21;
                n9 = (int)((double)n13 + d3 + (1.0 - Math.hypot((double)n10 - d4, (double)n8 - d5) / (double)n14) * 7.0);
                for (n21 = -2; n21 <= 2; ++n21) {
                    for (n20 = -2; n20 <= 2; ++n20) {
                        chunkData.setBlock(n10 + n21, n9 + 1, n8 + n20, Material.END_STONE_BRICKS);
                    }
                }
                for (n21 = 2; n21 <= 9; ++n21) {
                    for (n20 = -1; n20 <= 1; ++n20) {
                        for (int i = -1; i <= 1; ++i) {
                            boolean bl = Math.abs(n20) == 1 || Math.abs(i) == 1;
                            Material material = bl ? Material.PURPUR_PILLAR : Material.PURPUR_BLOCK;
                            chunkData.setBlock(n10 + n20, n9 + n21, n8 + i, material);
                        }
                    }
                }
                for (n21 = -1; n21 <= 1; ++n21) {
                    for (n20 = -1; n20 <= 1; ++n20) {
                        chunkData.setBlock(n10 + n21, n9 + 10, n8 + n20, Material.END_STONE_BRICKS);
                    }
                }
                chunkData.setBlock(n10, n9 + 11, n8, Material.END_ROD);
            }
        }
        for (n7 = 0; n7 < 16; ++n7) {
            for (n10 = 0; n10 < 16; ++n10) {
                n8 = n * 16 + n7;
                n9 = n2 * 16 + n10;
                double d10 = Math.hypot((double)n8 - d4, (double)n9 - d5);
                Biome biome = d10 < (double)n14 * 0.5 ? Biome.THE_END : (d10 < (double)n14 ? Biome.END_MIDLANDS : (d10 < (double)(n14 + 40) ? Biome.END_HIGHLANDS : Biome.SMALL_END_ISLANDS));
                biomeGrid.setBiome(n7, n10, biome);
            }
        }
    }

    private double[] endIslandCenter(int n, int n2, double d, long l) {
        double d2 = d * 8.0 - 4.0;
        double d3 = ((double)(CustomTerrainGenerator.mix64((long)n * 2246822507L + (long)n2 * 3266489909L + l) & 0xFFFFL) / 65535.0 - 0.5) * 8.0;
        return new double[]{(double)(n * 16 + 8) + d2, (double)(n2 * 16 + 8) + d3};
    }

    private void generateBadlands(World world, ChunkGenerator.ChunkData chunkData, Random random, int n, int n2, int n3, int n4, int n5, int n6, ChunkGenerator.BiomeGrid biomeGrid) {
        double d;
        long l;
        int n7;
        int n8;
        int n9;
        int n10;
        int n11 = this.plugin.getConfig().getInt("world.base-height", 64);
        for (n10 = 0; n10 < 16; ++n10) {
            for (n9 = 0; n9 < 16; ++n9) {
                int n12;
                n8 = n * 16 + n10;
                n7 = n2 * 16 + n9;
                l = CustomTerrainGenerator.mix64((long)n8 * -7046029254386353131L + (long)n7 * -4658895280553007687L);
                d = Math.sin((double)n8 * 0.045) * Math.cos((double)n7 * 0.05) * 8.0 + Math.sin((double)(n8 + n7) * 0.02) * 4.0;
                double d2 = ((double)(l & 0xFFL) / 255.0 - 0.5) * 5.0;
                int n13 = Math.max(n3 + 4, Math.min(n4 - 1, n11 + 4 + (int)(d + d2)));
                int n14 = 3 + (int)(l >> 8 & 3L);
                for (n12 = n3; n12 <= n13; ++n12) {
                    Material material = n12 <= n3 + 1 ? Material.BEDROCK : (n12 <= n13 - n14 ? Material.STONE : (n12 < n13 ? this.terracottaBlock(l, n12) : Material.RED_SAND));
                    chunkData.setBlock(n10, n12, n9, material);
                }
                if (!(random.nextDouble() < 0.012) || n13 <= n3 + 3) continue;
                n12 = n3 + 3 + random.nextInt(Math.max(1, n13 - n3 - 3));
                chunkData.setBlock(n10, n12, n9, Material.GOLD_ORE);
            }
        }
        for (n10 = 0; n10 < 16; ++n10) {
            for (n9 = 0; n9 < 16; ++n9) {
                n8 = n * 16 + n10;
                n7 = n2 * 16 + n9;
                l = CustomTerrainGenerator.mix64((long)n8 * 2462622107L + (long)n7 * 219913781L);
                d = (double)(l & 0xFFFFL) / 65535.0;
                Biome biome = d < 0.18 ? Biome.ERODED_BADLANDS : (d < 0.28 ? Biome.WOODED_BADLANDS : Biome.BADLANDS);
                biomeGrid.setBiome(n10, n9, biome);
            }
        }
    }

    private Material terracottaBlock(long l, int n) {
        long l2 = CustomTerrainGenerator.mix64(l ^ (long)n * -7046029254386353131L);
        return switch ((int)(l2 & 7L)) {
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

    private void generateNether(World world, ChunkGenerator.ChunkData chunkData, Random random, int n, int n2, int n3, int n4, int n5, int n6, ChunkGenerator.BiomeGrid biomeGrid) {
        int n7;
        int n8;
        int n9;
        int n10;
        int n11 = this.plugin.getConfig().getInt("world.base-height", 64);
        long l = world.getSeed();
        for (n10 = 0; n10 < 16; ++n10) {
            for (n9 = 0; n9 < 16; ++n9) {
                int n12;
                boolean bl;
                n8 = n * 16 + n10;
                n7 = n2 * 16 + n9;
                long l2 = CustomTerrainGenerator.mix64((long)n8 * -7046029254386353131L + (long)n7 * -4658895280553007687L + l);
                int n13 = Math.floorDiv(n5, 16);
                int n14 = Math.floorDiv(n6, 16);
                long l3 = CustomTerrainGenerator.mix64((long)n13 * 2246822507L + (long)n14 * 3266489909L + l);
                double d = (double)(l3 & 0xFFFFL) / 65535.0;
                double d2 = Math.sin((double)n8 * 0.08) * Math.cos((double)n7 * 0.09) * 4.0 + Math.sin((double)(n8 + n7) * 0.04) * 3.0;
                int n15 = Math.max(n3 + 4, Math.min(n4 - 1, n11 + 6 + (int)d2));
                boolean bl2 = bl = d > 0.72;
                if (bl) {
                    n15 = Math.max(n3 + 4, (int)((double)n15 * 0.78));
                }
                for (n12 = n3; n12 <= n15; ++n12) {
                    Material material = n12 <= n3 + 1 ? Material.BEDROCK : (n12 <= n15 - 4 ? Material.NETHERRACK : (n12 < n15 ? this.netherSubBlock(l2, n12, bl) : this.netherSurfaceBlock(l2, d)));
                    chunkData.setBlock(n10, n12, n9, material);
                }
                if (random.nextDouble() < 0.05 && !bl && (n12 = n15) > n3 + 2) {
                    chunkData.setBlock(n10, n12, n9, Material.LAVA);
                }
                if (d > 0.9 && random.nextDouble() < 0.1) {
                    n12 = 4 + random.nextInt(6);
                    int n16 = n15 + 1;
                    for (int i = 0; i < n12; ++i) {
                        if (n16 + i > n4 - 2) continue;
                        chunkData.setBlock(n10, n16 + i, n9, Material.BASALT);
                    }
                }
                if (random.nextDouble() < 0.03 && (n12 = n15) > n3 + 2) {
                    chunkData.setBlock(n10, n12, n9, Material.MAGMA_BLOCK);
                }
                if (!(random.nextDouble() < 0.04) || n15 <= n3 + 4) continue;
                n12 = n3 + 4 + random.nextInt(Math.max(1, n15 - n3 - 4));
                chunkData.setBlock(n10, n12, n9, Material.NETHER_QUARTZ_ORE);
            }
        }
        for (n10 = 0; n10 < 16; ++n10) {
            for (n9 = 0; n9 < 16; ++n9) {
                int n17;
                n8 = n * 16 + n10;
                n7 = n2 * 16 + n9;
                int n18 = Math.floorDiv(n5, 16);
                long l4 = CustomTerrainGenerator.mix64((long)n18 * 2246822507L + (long)(n17 = Math.floorDiv(n6, 16)) * 3266489909L + l);
                double d = (double)(l4 & 0xFFFFL) / 65535.0;
                Biome biome = d > 0.9 ? Biome.BASALT_DELTAS : (d > 0.8 ? Biome.CRIMSON_FOREST : (d > 0.72 ? Biome.SOUL_SAND_VALLEY : (d > 0.64 ? Biome.WARPED_FOREST : Biome.NETHER_WASTES)));
                biomeGrid.setBiome(n10, n9, biome);
            }
        }
    }

    private Material netherSubBlock(long l, int n, boolean bl) {
        long l2 = CustomTerrainGenerator.mix64(l ^ (long)n * -7046029254386353131L);
        if (bl) {
            return (l2 & 1L) == 0L ? Material.SOUL_SAND : Material.SOUL_SOIL;
        }
        return (l2 & 1L) == 0L ? Material.NETHERRACK : Material.BLACKSTONE;
    }

    private Material netherSurfaceBlock(long l, double d) {
        if (d > 0.9) {
            return Material.BASALT;
        }
        if (d > 0.8) {
            return Material.CRIMSON_NYLIUM;
        }
        if (d > 0.72) {
            return Material.SOUL_SAND;
        }
        if (d > 0.64) {
            return Material.WARPED_NYLIUM;
        }
        return Material.NETHERRACK;
    }

    private void generateCity(World world, ChunkGenerator.ChunkData chunkData, Random random, int n, int n2, int n3, int n4, int n5, int n6, ChunkGenerator.BiomeGrid biomeGrid) {
        int n7;
        int n8;
        int n9;
        int n10;
        int n11;
        int n12 = this.plugin.getConfig().getInt("world.base-height", 64);
        long l = world.getSeed();
        int n13 = n12;
        for (n11 = 0; n11 < 16; ++n11) {
            for (n10 = 0; n10 < 16; ++n10) {
                int n14 = n * 16 + n11;
                int n15 = n2 * 16 + n10;
                boolean bl = Math.floorMod(n14, 32) >= 14 && Math.floorMod(n14, 32) <= 17;
                boolean bl2 = Math.floorMod(n15, 32) >= 14 && Math.floorMod(n15, 32) <= 17;
                n9 = bl || bl2 ? 1 : 0;
                n8 = bl && bl2 ? 1 : 0;
                for (n7 = n3; n7 <= n13; ++n7) {
                    Material material = n7 <= n3 + 1 ? Material.BEDROCK : (n7 <= n13 - 3 ? Material.STONE : (n7 < n13 ? Material.DIRT : (n9 != 0 ? (n8 != 0 ? Material.LIGHT_GRAY_CONCRETE : Material.BLACK_CONCRETE) : Material.GRASS_BLOCK)));
                    chunkData.setBlock(n11, n7, n10, material);
                }
            }
        }
        n11 = Math.floorDiv(n5, 32);
        n10 = Math.floorDiv(n6, 32);
        long l2 = CustomTerrainGenerator.mix64((long)n11 * -7046029254386353131L + (long)n10 * -4658895280553007687L + l);
        double d = (double)(l2 & 0xFFFFL) / 65535.0;
        n9 = n11 * 32 + 8 >> 4;
        n8 = n10 * 32 + 8 >> 4;
        if (n == n9 && n2 == n8) {
            n7 = 8;
            int n16 = 8;
            if (d > 0.82) {
                this.buildingCols(chunkData, n7, n16, n13, n4, "park", random);
            } else if (d > 0.3) {
                String string = d > 0.62 ? "tower" : "block";
                this.buildingCols(chunkData, n7, n16, n13, n4, string, random);
            } else if (d > 0.15) {
                chunkData.setBlock(n7, n13 + 1, n16, Material.SEA_LANTERN);
            }
        }
        for (n7 = 0; n7 < 16; ++n7) {
            for (int i = 0; i < 16; ++i) {
                biomeGrid.setBiome(n7, i, Biome.PLAINS);
            }
        }
    }

    private void buildingCols(ChunkGenerator.ChunkData chunkData, int n, int n2, int n3, int n4, String string, Random random) {
        int n5;
        int n6;
        int n7;
        int n8 = "tower".equals(string) ? 12 + random.nextInt(8) : ("park".equals(string) ? -1 : 6 + random.nextInt(4));
        if ("park".equals(string)) {
            for (int i = -3; i <= 3; ++i) {
                for (int j = -3; j <= 3; ++j) {
                    int n9 = n + i;
                    int n10 = n2 + j;
                    if (n9 < 0 || n9 > 15 || n10 < 0 || n10 > 15) continue;
                    if (Math.abs(i) <= 1 && Math.abs(j) <= 1) {
                        chunkData.setBlock(n9, n3 + 1, n10, Material.WATER);
                        continue;
                    }
                    if (!(random.nextDouble() < 0.3)) continue;
                    chunkData.setBlock(n9, n3 + 2, n10, Material.OAK_LOG);
                    chunkData.setBlock(n9, n3 + 3, n10, Material.OAK_LEAVES);
                }
            }
            return;
        }
        int n11 = Math.max(0, n - 2);
        int n12 = Math.min(15, n + 2);
        int n13 = Math.max(0, n2 - 2);
        int n14 = Math.min(15, n2 + 2);
        for (n7 = n11; n7 <= n12; ++n7) {
            for (n6 = n13; n6 <= n14; ++n6) {
                int n15;
                int n16;
                n5 = Math.abs(n7 - n) == 2 || Math.abs(n6 - n2) == 2 ? 1 : 0;
                chunkData.setBlock(n7, n3 + 1, n6, n5 != 0 ? this.materialLow(random) : Material.SMOOTH_STONE);
                for (n16 = 2; n16 <= n8; ++n16) {
                    if (n5 == 0) continue;
                    n15 = n3 + n16;
                    if (n15 >= n4 - 1) break;
                    Material material = this.materialWall(random);
                    chunkData.setBlock(n7, n15, n6, material);
                }
                if (n5 == 0) continue;
                for (n16 = 3; n16 <= n8 && (n15 = n3 + n16) < n4 - 1; n16 += 3) {
                    chunkData.setBlock(n7, n15, n6, Material.GLASS_PANE);
                }
            }
        }
        n7 = n3 + n8;
        if (n7 < n4 - 1) {
            for (n6 = n11; n6 <= n12; ++n6) {
                for (n5 = n13; n5 <= n14; ++n5) {
                    chunkData.setBlock(n6, n7 + 1, n5, Material.SMOOTH_STONE);
                }
            }
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

    private void generateNormal(World world, ChunkGenerator.ChunkData chunkData, Random random, int n, int n2, int n3, int n4, int n5, int n6, ChunkGenerator.BiomeGrid biomeGrid) {
        double d;
        int n7;
        int n8;
        int n9;
        int n10;
        int n11 = this.plugin.getConfig().getInt("world.base-height", 64);
        long l = world.getSeed();
        double d2 = Math.max(80.0, this.plugin.getConfig().getDouble("world.plain-radius", 120.0));
        for (n10 = 0; n10 < 16; ++n10) {
            for (n9 = 0; n9 < 16; ++n9) {
                int n12;
                int n13;
                boolean bl;
                double d3;
                int n14;
                n8 = n * 16 + n10;
                n7 = n2 * 16 + n9;
                long l2 = CustomTerrainGenerator.mix64((long)n8 * -7046029254386353131L + (long)n7 * -4658895280553007687L + l);
                double d4 = Math.hypot(n8, n7);
                d = Math.sin((double)n8 * 0.03) * Math.cos((double)n7 * 0.035) * 5.0 + Math.sin((double)(n8 + n7) * 0.015) * 3.0;
                double d5 = ((double)(l2 & 0xFFL) / 255.0 - 0.5) * 2.0;
                if (d4 < d2) {
                    n14 = n11 + (int)d + (int)d5;
                } else {
                    d3 = (d4 - d2) / Math.max(1.0, d2);
                    n14 = n11 + (int)(d + d5 + d3 * 26.0);
                }
                d3 = Math.sin((double)n8 * 0.05 + (double)(l % 100L)) + Math.cos((double)n7 * 0.05);
                boolean bl2 = bl = Math.abs(d3) < 0.6 && d4 > d2 * 0.6;
                if (bl) {
                    n14 = Math.max(n3 + 3, n14 - 8);
                }
                n14 = Math.max(n3 + 2, Math.min(n4 - 1, n14));
                for (n13 = n3; n13 <= n14; ++n13) {
                    Material material;
                    if (n13 <= n3 + 1) {
                        material = Material.BEDROCK;
                    } else if (n13 <= n14 - 4) {
                        material = Material.STONE;
                    } else if (n13 < n14) {
                        material = n14 > n11 + 14 ? Material.STONE : Material.DIRT;
                    } else {
                        int n15 = n12 = n14 > n11 + 14 ? 1 : 0;
                        material = n12 != 0 ? Material.STONE : (bl ? Material.SAND : Material.GRASS_BLOCK);
                    }
                    chunkData.setBlock(n10, n13, n9, material);
                }
                if (!bl || (n13 = n11 - 4) >= n4 - 1) continue;
                int n16 = Math.min(n14 + 1, n13);
                for (n12 = n14 + 1; n12 <= n13; ++n12) {
                    chunkData.setBlock(n10, n12, n9, Material.WATER);
                }
            }
        }
        for (n10 = 0; n10 < 16; ++n10) {
            for (n9 = 0; n9 < 16; ++n9) {
                n8 = n * 16 + n10;
                n7 = n2 * 16 + n9;
                double d6 = Math.hypot(n8, n7);
                long l3 = CustomTerrainGenerator.mix64((long)n8 * 2462622107L + (long)n7 * 219913781L + l);
                d = (double)(l3 & 0xFFFFL) / 65535.0;
                Biome biome = d6 < d2 ? Biome.PLAINS : (d < 0.35 ? Biome.FOREST : (d < 0.55 ? Biome.WINDSWEPT_HILLS : Biome.PLAINS));
                biomeGrid.setBiome(n10, n9, biome);
            }
        }
    }

    private double[] islandCenter(int n, int n2, double d) {
        double d2 = d * 16.0 - 8.0;
        double d3 = ((double)(CustomTerrainGenerator.mix64((long)n * 2246822507L + (long)n2 * 3266489909L) & 0xFFFFL) / 65535.0 - 0.5) * 16.0;
        return new double[]{(double)n * 16.0 + 8.0 + d2, (double)n2 * 16.0 + 8.0 + d3};
    }

    private static long mix64(long l) {
        l = (l ^ l >>> 33) * -49064778989728563L;
        l = (l ^ l >>> 33) * -4265267296055464877L;
        return l ^ l >>> 33;
    }

    public boolean shouldGenerateCaves() {
        return false;
    }

    public boolean shouldGenerateStructures() {
        return false;
    }

    public boolean shouldGenerateDecorations() {
        return false;
    }

    public boolean shouldGenerateMobs() {
        return false;
    }

    public boolean shouldGenerateBedrock() {
        return false;
    }

    private void addCover(ChunkGenerator.ChunkData chunkData, Random random, int n, int n2, int n3, int n4) {
        double d = this.plugin.getConfig().getDouble("world.cover-chance", 0.28);
        if (d <= 0.0) {
            return;
        }
        long l = CustomTerrainGenerator.mix64((long)n * -7046029254386353131L + (long)n2 * -4658895280553007687L);
        double d2 = (double)(l & 0xFFFFL) / 65535.0;
        if (d2 > d) {
            return;
        }
        int n5 = 1 + (int)(l >> 16 & 1L);
        for (int i = 0; i < n5; ++i) {
            int n6;
            int n7;
            long l2 = CustomTerrainGenerator.mix64(l ^ (long)i * 2246822507L);
            int n8 = 2 + (int)(l2 >> 8 & 0xBL);
            int n9 = 2 + (int)(l2 >> 20 & 0xBL);
            int n10 = -1;
            for (int j = n4 - 1; j >= n3; --j) {
                if (chunkData.getType(n8, j, n9) == Material.AIR) continue;
                n10 = j;
                break;
            }
            if (n10 < n3) continue;
            Material material = this.coverBlock(l2);
            int n11 = 2 + (int)(l2 >> 30 & 3L);
            for (n7 = 1; n7 <= n11 && (n6 = n10 + n7) < n4 - 1; ++n7) {
                chunkData.setBlock(n8, n6, n9, material);
            }
            n7 = Math.min(n4 - 2, n10 + n11);
            n6 = Math.min(n4 - 1, n7 + 1);
            Material material2 = this.coverTop(l2);
            if (material2 == Material.AIR || !chunkData.getType(n8, n6, n9).isAir()) continue;
            chunkData.setBlock(n8, n6, n9, material2);
        }
    }

    private Material coverBlock(long l) {
        return switch (this.type) {
            case TerrainType.DESERT -> {
                if ((l >> 40 & 1L) == 0L) {
                    yield Material.SANDSTONE;
                }
                yield Material.SMOOTH_SANDSTONE;
            }
            case TerrainType.ISLANDS -> {
                if ((l >> 40 & 1L) == 0L) {
                    yield Material.STONE;
                }
                yield Material.ANDESITE;
            }
            case TerrainType.BADLANDS -> {
                if ((l >> 40 & 1L) == 0L) {
                    yield Material.RED_SANDSTONE;
                }
                yield Material.TERRACOTTA;
            }
            case TerrainType.THE_END -> Material.END_STONE;
            case TerrainType.NETHER -> Material.NETHERRACK;
            case TerrainType.CITY -> Material.STONE_BRICKS;
            case TerrainType.NORMAL -> {
                if ((l >> 40 & 1L) == 0L) {
                    yield Material.STONE;
                }
                yield Material.COBBLESTONE;
            }
            default -> (l >> 40 & 1L) == 0L ? Material.STONE : Material.COBBLESTONE;
        };
    }

    private Material coverTop(long l) {
        return switch (this.type) {
            case TerrainType.DESERT -> {
                if ((l >> 44 & 1L) == 0L) {
                    yield Material.CACTUS;
                }
                yield Material.DEAD_BUSH;
            }
            case TerrainType.ISLANDS -> {
                if ((l >> 44 & 1L) == 0L) {
                    yield Material.SHORT_GRASS;
                }
                yield Material.FERN;
            }
            case TerrainType.BADLANDS -> {
                if ((l >> 44 & 1L) == 0L) {
                    yield Material.DEAD_BUSH;
                }
                yield Material.CACTUS;
            }
            case TerrainType.THE_END -> Material.AIR;
            case TerrainType.NETHER -> Material.AIR;
            case TerrainType.CITY -> Material.AIR;
            case TerrainType.NORMAL -> {
                if ((l >> 44 & 1L) == 0L) {
                    yield Material.SHORT_GRASS;
                }
                yield Material.OAK_LEAVES;
            }
            default -> (l >> 44 & 1L) == 0L ? Material.SHORT_GRASS : Material.OAK_LEAVES;
        };
    }

    public List<BlockPopulator> getDefaultPopulators(World world) {
        return List.of(new SparseTreePopulator());
    }

    private static class SparseTreePopulator
    extends BlockPopulator {
        private SparseTreePopulator() {
        }

        public void populate(World world, Random random, Chunk chunk) {
            if (random.nextDouble() > 0.18) {
                return;
            }
            int n = 1;
            for (int i = 0; i < n; ++i) {
                int n2 = random.nextInt(16);
                int n3 = random.nextInt(16);
                int n4 = world.getMinHeight();
                Block block = world.getBlockAt(chunk.getX() * 16 + n2, n4 + 60, chunk.getZ() * 16 + n3);
                Material material = block.getType();
                if (material == Material.GRASS_BLOCK) {
                    this.growOak(world, block.getLocation().add(0.0, 1.0, 0.0), random);
                    continue;
                }
                if (material != Material.SAND || !(random.nextDouble() < 0.4)) continue;
                world.getBlockAt(block.getX(), block.getY() + 1, block.getZ()).setType(Material.CACTUS, false);
            }
        }

        private void growOak(World world, Location location, Random random) {
            int n;
            int n2 = 2 + random.nextInt(3);
            for (n = 0; n < n2; ++n) {
                Block block = world.getBlockAt(location.getBlockX(), location.getBlockY() + n, location.getBlockZ());
                if (!block.getType().isAir() && block.getType().isSolid()) continue;
                block.setType(Material.OAK_LOG, false);
            }
            n = location.getBlockY() + n2;
            for (int i = -2; i <= 2; ++i) {
                for (int j = -2; j <= 2; ++j) {
                    for (int k = -1; k <= 1; ++k) {
                        int n3 = n + k;
                        Block block = world.getBlockAt(location.getBlockX() + i, n3, location.getBlockZ() + j);
                        if (!block.getType().isAir() && block.getType().isSolid() || Math.abs(i) == 2 && Math.abs(j) == 2 && random.nextDouble() < 0.5) continue;
                        block.setType(Material.OAK_LEAVES, false);
                    }
                }
            }
        }
    }
}
