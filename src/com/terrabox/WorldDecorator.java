/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.papermc.paper.threadedregions.scheduler.ScheduledTask
 *  org.bukkit.Bukkit
 *  org.bukkit.Material
 *  org.bukkit.World
 *  org.bukkit.block.Block
 *  org.bukkit.plugin.Plugin
 */
package com.terrabox;

import com.terrabox.Rarity;
import com.terrabox.TerraBoxPlugin;
import com.terrabox.TerrainType;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

public class WorldDecorator {
    private final TerraBoxPlugin plugin;
    private volatile boolean done = false;
    private final ArrayDeque<Job> queue = new ArrayDeque();
    private ScheduledTask driver;

    public WorldDecorator(TerraBoxPlugin terraBoxPlugin) {
        this.plugin = terraBoxPlugin;
    }

    public boolean isDone() {
        return this.done;
    }

    public void build() {
        World world = this.plugin.worlds().world();
        if (world == null) {
            return;
        }
        if (!this.plugin.getConfig().getBoolean("decorator.enabled", true)) {
            return;
        }
        this.queue.clear();
        this.enqueueJobs(world);
        if (this.queue.isEmpty()) {
            this.done = true;
            return;
        }
        this.plugin.getLogger().info("\u5f00\u59cb\u5730\u5f62\u88c5\u9970: " + this.queue.size() + " \u4e2a\u533a\u5757\u4efb\u52a1");
        int n = Math.max(2, this.plugin.getConfig().getInt("decorator.batch-per-tick", 6));
        this.driver = Bukkit.getGlobalRegionScheduler().runAtFixedRate((Plugin)this.plugin, scheduledTask -> {
            Job job;
            if (this.queue.isEmpty()) {
                scheduledTask.cancel();
                this.driver = null;
                this.done = true;
                this.plugin.getLogger().info("\u5730\u5f62\u88c5\u9970\u5b8c\u6210: \u8fb9\u754c\u5899/\u4e3b\u5e72\u9053/\u77ad\u671b\u5854/\u4e2d\u5fc3\u6218\u573a\u5df2\u5c31\u7eea");
                return;
            }
            for (int i = 0; i < n && !this.queue.isEmpty() && (job = this.queue.poll()) != null; ++i) {
                this.submitJob(world, job);
            }
        }, 20L, 10L);
    }

    private void submitJob(World world, Job job) {
        world.getChunkAtAsync(job.cx, job.cz).whenComplete((chunk, throwable) -> {
            if (throwable != null) {
                this.plugin.getLogger().warning("\u88c5\u9970\u533a\u5757\u52a0\u8f7d\u5931\u8d25 (" + job.cx + "," + job.cz + "): " + String.valueOf(throwable));
                return;
            }
            Bukkit.getGlobalRegionScheduler().run((Plugin)this.plugin, scheduledTask2 -> {
                try {
                    world.setChunkForceLoaded(job.cx, job.cz, true);
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
                Bukkit.getRegionScheduler().run((Plugin)this.plugin, world, job.cx, job.cz, scheduledTask -> {
                    try {
                        job.body().accept((ScheduledTask)scheduledTask);
                    }
                    catch (Throwable throwable) {
                        this.plugin.getLogger().warning("\u88c5\u9970\u4efb\u52a1\u5f02\u5e38 (" + job.cx + "," + job.cz + "): " + String.valueOf(throwable));
                    }
                    finally {
                        try {
                            world.setChunkForceLoaded(job.cx, job.cz, false);
                        }
                        catch (Throwable throwable) {}
                    }
                });
            });
        });
    }

    private void enqueueJobs(World world) {
        int[][] nArrayArray;
        int n;
        int n2;
        int n3;
        int n4;
        int n5;
        int n6;
        int n7;
        int n8;
        int n9 = (int)this.plugin.worlds().borderHalf();
        int n10 = n9 - 2;
        int n11 = this.plugin.getConfig().getInt("decorator.clear-center-radius", 30);
        int n12 = Math.min(n10 - 40, Math.max(80, this.plugin.getConfig().getInt("decorator.tower-distance", 320)));
        TerrainType terrainType = this.plugin.arenas() != null ? this.plugin.arenas().terrainOf(world.getName()) : TerrainType.DEFAULT;
        boolean bl = terrainType == TerrainType.THE_END || terrainType == TerrainType.NETHER;
        HashSet<Long> hashSet = new HashSet<Long>();
        if (!bl) {
            n8 = (int)Math.floor((double)(-n11) / 16.0);
            n7 = (int)Math.ceil((double)n11 / 16.0);
            for (n6 = n8; n6 < n7; ++n6) {
                for (n5 = n8; n5 < n7; ++n5) {
                    if (!hashSet.add(WorldDecorator.pack(n6, n5))) continue;
                    n4 = n6;
                    n3 = n5;
                    this.queue.add(new Job(n6, n5, scheduledTask -> this.clearCenter(world, n4, n3, n11)));
                }
            }
        }
        n8 = Math.max(10, this.plugin.getConfig().getInt("game.spawn-area-radius", 7) + 2);
        if (!bl) {
            for (n7 = (int)Math.floor((double)(-n10) / 16.0); n7 < (int)Math.ceil((double)n10 / 16.0); ++n7) {
                for (n6 = -1; n6 <= 0; ++n6) {
                    if (!hashSet.add(WorldDecorator.pack(n7, n6))) continue;
                    n5 = n7;
                    n4 = n6;
                    this.queue.add(new Job(n7, n6, scheduledTask -> this.buildRoad(world, n5, n4, n8, n10)));
                }
            }
            for (n7 = (int)Math.floor((double)(-n10) / 16.0); n7 < (int)Math.ceil((double)n10 / 16.0); ++n7) {
                for (n6 = -1; n6 <= 0; ++n6) {
                    if (!hashSet.add(WorldDecorator.pack(n6, n7))) continue;
                    n5 = n6;
                    n4 = n7;
                    this.queue.add(new Job(n6, n7, scheduledTask -> this.buildRoadZ(world, n5, n4, n8, n10)));
                }
            }
        }
        n7 = (int)Math.floor((double)(-n10) / 16.0);
        n6 = (int)Math.ceil((double)n10 / 16.0);
        for (n5 = 0; n5 < 4; ++n5) {
            for (n4 = n7; n4 < n6; ++n4) {
                int n13;
                if (n5 == 0) {
                    n3 = n4;
                    n13 = n10 >> 4;
                } else if (n5 == 1) {
                    n3 = n4;
                    n13 = -n10 >> 4;
                } else if (n5 == 2) {
                    n3 = n10 >> 4;
                    n13 = n4;
                } else {
                    n3 = -n10 >> 4;
                    n13 = n4;
                }
                int n14 = n3;
                n2 = n13;
                n = n5;
                this.queue.add(new Job(n3, n13, scheduledTask -> this.buildWall(world, n14, n2, n10, n)));
            }
        }
        for (int[] nArray : nArrayArray = new int[][]{{0, n12}, {0, -n12}, {n12, 0}, {-n12, 0}}) {
            n2 = nArray[0] >> 4;
            n = nArray[1] >> 4;
            if (!hashSet.add(WorldDecorator.pack(n2, n))) continue;
            int n15 = nArray[0];
            int n16 = nArray[1];
            this.queue.add(new Job(n2, n, scheduledTask -> this.buildTower(world, n15, n16)));
        }
    }

    private static long pack(int n, int n2) {
        return (long)n << 32 | (long)n2 & 0xFFFFFFFFL;
    }

    private void clearCenter(World world, int n, int n2, int n3) {
        int n4 = n3 * n3;
        for (int i = 0; i < 16; ++i) {
            for (int j = 0; j < 16; ++j) {
                int n5 = n * 16 + i;
                int n6 = n2 * 16 + j;
                if (n5 * n5 + n6 * n6 > n4) continue;
                this.clearVegetationAt(world, n5, n6);
            }
        }
    }

    private void clearVegetationAt(World world, int n, int n2) {
        Material material;
        int n3 = world.getHighestBlockYAt(n, n2);
        if (n3 <= 0) {
            return;
        }
        Material material2 = world.getBlockAt(n, n3, n2).getType();
        if (material2.isAir() || material2 == Material.WATER || material2 == Material.LAVA) {
            return;
        }
        for (int i = n3; i > 0 && (material = world.getBlockAt(n, i, n2).getType()) != Material.GRASS_BLOCK && material != Material.DIRT && material != Material.STONE && material != Material.SAND && material != Material.GRAVEL && material != Material.SANDSTONE && material != Material.DEEPSLATE && material != Material.STONE_BRICKS && material != Material.SMOOTH_STONE && material != Material.MUD; --i) {
            world.getBlockAt(n, i, n2).setType(Material.AIR, false);
        }
    }

    private void buildRoad(World world, int n, int n2, int n3, int n4) {
        for (int i = 0; i < 16; ++i) {
            int n5 = n * 16 + i;
            if (Math.abs(n5) < n3 || Math.abs(n5) > n4) continue;
            for (int j = -1; j <= 1; ++j) {
                int n6 = n2 * 16 + j;
                if (n6 < -1 || n6 > 1) continue;
                this.pave(world, n5, n6, Material.SMOOTH_STONE);
            }
        }
    }

    private void buildRoadZ(World world, int n, int n2, int n3, int n4) {
        for (int i = 0; i < 16; ++i) {
            int n5 = n2 * 16 + i;
            if (Math.abs(n5) < n3 || Math.abs(n5) > n4) continue;
            for (int j = -1; j <= 1; ++j) {
                int n6 = n * 16 + j;
                if (n6 < -1 || n6 > 1) continue;
                this.pave(world, n6, n5, Material.SMOOTH_STONE);
            }
        }
    }

    private void pave(World world, int n, int n2, Material material) {
        int n3 = world.getHighestBlockYAt(n, n2);
        Material material2 = world.getBlockAt(n, n3, n2).getType();
        int n4 = n3;
        if (material2 == Material.WATER || material2 == Material.SEAGRASS || material2 == Material.KELP_PLANT || material2 == Material.KELP || material2 == Material.LILY_PAD) {
            for (int i = n3; i >= 62; --i) {
                Material material3 = world.getBlockAt(n, i, n2).getType();
                if (!material3.isAir() && material3 != Material.WATER && material3 != Material.SEAGRASS && material3 != Material.KELP_PLANT && material3 != Material.KELP) continue;
                world.getBlockAt(n, i, n2).setType(Material.STONE, false);
            }
            n4 = Math.max(n3, 62);
        }
        world.getBlockAt(n, n4 + 1, n2).setType(material, false);
        Block block = world.getBlockAt(n, n4 + 2, n2);
        if (block.getType() != Material.AIR && !block.getType().name().contains("TORCH") && !block.getType().name().contains("LANTERN")) {
            block.setType(Material.AIR, false);
        }
    }

    private void buildWall(World world, int n, int n2, int n3, int n4) {
        int n5 = Math.max(2, this.plugin.getConfig().getInt("decorator.wall-height", 3));
        for (int i = 0; i < 16; ++i) {
            for (int j = 0; j < 16; ++j) {
                int n6;
                int n7;
                Material material;
                int n8;
                boolean bl;
                int n9 = n * 16 + i;
                int n10 = n2 * 16 + j;
                if (n4 == 0) {
                    bl = n10 == n3;
                } else if (n4 == 1) {
                    bl = n10 == -n3;
                } else if (n4 == 2) {
                    bl = n9 == n3;
                } else {
                    boolean bl2 = bl = n9 == -n3;
                }
                if (!bl || Math.abs(n9) > n3 || Math.abs(n10) > n3) continue;
                int n11 = world.getHighestBlockYAt(n9, n10);
                Material material2 = world.getBlockAt(n9, n11, n10).getType();
                int n12 = n11;
                if (material2 == Material.WATER || material2 == Material.SEAGRASS || material2 == Material.KELP || material2 == Material.KELP_PLANT) {
                    for (n8 = n11; n8 >= 62; --n8) {
                        material = world.getBlockAt(n9, n8, n10).getType();
                        if (!material.isAir() && material != Material.WATER && material != Material.SEAGRASS && material != Material.KELP && material != Material.KELP_PLANT) continue;
                        world.getBlockAt(n9, n8, n10).setType(Material.STONE, false);
                    }
                    n12 = Math.max(n11, 62);
                }
                for (n8 = 1; n8 <= 4; ++n8) {
                    material = world.getBlockAt(n9, n12 - n8, n10);
                    if (!material.getType().isAir() && material.getType().isSolid()) continue;
                    material.setType(Material.STONE, false);
                }
                n8 = this.plugin.getConfig().getInt("decorator.wall-extra-height", 6);
                int n13 = n5 + Math.max(0, n8);
                for (n7 = 1; n7 <= n13; ++n7) {
                    world.getBlockAt(n9, n12 + n7, n10).setType(Material.STONE_BRICKS, false);
                }
                if ((n9 + n10) % 16 == 0) {
                    world.getBlockAt(n9, n12 + n13 + 1, n10).setType(Material.TORCH, false);
                }
                n7 = n9;
                int n14 = n10;
                if (n4 == 0) {
                    n14 = n10 - 2;
                } else if (n4 == 1) {
                    n14 = n10 + 2;
                } else {
                    n7 = n4 == 2 ? n9 - 2 : n9 + 2;
                }
                if (Math.abs(n7) > n3 || Math.abs(n14) > n3 || (n6 = world.getHighestBlockYAt(n7, n14)) <= 0) continue;
                world.getBlockAt(n7, n6 + 1, n14).setType(Material.RED_CONCRETE, false);
            }
        }
    }

    private void buildTower(World world, int n, int n2) {
        boolean bl;
        int n3;
        int n4;
        int n5;
        int n6 = world.getHighestBlockYAt(n, n2);
        for (n5 = -3; n5 <= 3; ++n5) {
            for (n4 = -3; n4 <= 3; ++n4) {
                n3 = world.getHighestBlockYAt(n + n5, n2 + n4);
                bl = Math.abs(n5) <= 2 && Math.abs(n4) <= 2;
                int n7 = bl ? n6 + 1 : n6 + 1;
                for (int i = n3 + 1; i <= n7; ++i) {
                    Material material = i == n7 ? Material.SMOOTH_STONE : Material.STONE_BRICKS;
                    world.getBlockAt(n + n5, i, n2 + n4).setType(material, false);
                }
                if (bl) continue;
                world.getBlockAt(n + n5, n6 + 1, n2 + n4).setType(Material.STONE_BRICKS, false);
            }
        }
        for (n5 = n6 + 2; n5 <= n6 + 9; ++n5) {
            for (n4 = -2; n4 <= 2; ++n4) {
                for (n3 = -2; n3 <= 2; ++n3) {
                    boolean bl2 = bl = Math.abs(n4) == 2 || Math.abs(n3) == 2;
                    if (!bl) continue;
                    world.getBlockAt(n + n4, n5, n2 + n3).setType(Material.STONE_BRICKS, false);
                }
            }
            if (n5 % 2 != 0) continue;
            world.getBlockAt(n - 2, n5, n2 - 2).setType(Material.SMOOTH_STONE, false);
            world.getBlockAt(n + 2, n5, n2 - 2).setType(Material.SMOOTH_STONE, false);
            world.getBlockAt(n - 2, n5, n2 + 2).setType(Material.SMOOTH_STONE, false);
            world.getBlockAt(n + 2, n5, n2 + 2).setType(Material.SMOOTH_STONE, false);
        }
        for (n5 = -2; n5 <= 2; ++n5) {
            for (n4 = -2; n4 <= 2; ++n4) {
                world.getBlockAt(n + n5, n6 + 10, n2 + n4).setType(Material.SMOOTH_STONE, false);
            }
        }
        for (n5 = -2; n5 <= 2; ++n5) {
            for (n4 = -2; n4 <= 2; ++n4) {
                int n8 = n3 = Math.abs(n5) == 2 || Math.abs(n4) == 2 ? 1 : 0;
                if (n3 == 0) continue;
                world.getBlockAt(n + n5, n6 + 11, n2 + n4).setType(Material.IRON_BARS, false);
            }
        }
        world.getBlockAt(n, n6 + 12, n2).setType(Material.LANTERN, false);
        String string = this.plugin.getConfig().getString("decorator.tower-rarity", "EPIC");
        Rarity rarity = Rarity.parse(string);
        if (rarity == null) {
            rarity = Rarity.EPIC;
        }
        this.plugin.boxes().spawnBoxAt(n + 1, n2 + 1, rarity, false, null);
        this.plugin.getLogger().info("\u77ad\u671b\u5854\u5df2\u5efa\u6210: (" + n + "," + n2 + ") \u5854\u9876 " + rarity.display + "\u7269\u8d44\u7bb1");
    }

    private record Job(int cx, int cz, Consumer<ScheduledTask> body) {
    }
}
