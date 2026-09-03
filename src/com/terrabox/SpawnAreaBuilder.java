/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.World
 *  org.bukkit.block.Block
 *  org.bukkit.plugin.Plugin
 */
package com.terrabox;

import com.terrabox.Rarity;
import com.terrabox.TerraBoxPlugin;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

public class SpawnAreaBuilder {
    private final TerraBoxPlugin plugin;
    private volatile int centerY = 64;
    private volatile boolean built = false;

    public SpawnAreaBuilder(TerraBoxPlugin terraBoxPlugin) {
        this.plugin = terraBoxPlugin;
    }

    public int centerY() {
        return this.centerY;
    }

    public boolean isBuilt() {
        return this.built;
    }

    public void build(Runnable runnable) {
        World world = this.plugin.worlds().world();
        if (world == null) {
            if (runnable != null) {
                runnable.run();
            }
            return;
        }
        int n = Math.max(4, this.plugin.getConfig().getInt("game.spawn-area-radius", 7));
        world.getChunkAtAsync(0, 0).whenComplete((chunk, throwable) -> {
            if (throwable != null) {
                this.plugin.getLogger().warning("\u51fa\u751f\u5e7f\u573a\u533a\u5757\u52a0\u8f7d\u5931\u8d25: " + String.valueOf(throwable));
                if (runnable != null) {
                    runnable.run();
                }
                return;
            }
            Bukkit.getGlobalRegionScheduler().run((Plugin)this.plugin, scheduledTask2 -> {
                try {
                    world.setChunkForceLoaded(0, 0, true);
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
                Bukkit.getRegionScheduler().run((Plugin)this.plugin, world, 0, 0, scheduledTask -> {
                    try {
                        this.buildPlatform(world, n);
                        this.buildCenterBoxes(world);
                        this.plugin.getLogger().info("\u51fa\u751f\u5e7f\u573a\u5df2\u5c31\u7eea: \u534a\u5f84 " + n + ", \u5e73\u53f0\u9ad8 y=" + this.centerY);
                    }
                    catch (Throwable throwable) {
                        this.plugin.getLogger().warning("\u51fa\u751f\u5e7f\u573a\u6784\u5efa\u5f02\u5e38: " + String.valueOf(throwable));
                    }
                    finally {
                        try {
                            world.setChunkForceLoaded(0, 0, false);
                        }
                        catch (Throwable throwable) {}
                    }
                    if (runnable != null) {
                        runnable.run();
                    }
                });
            });
        });
    }

    private void buildPlatform(World world, int n) {
        int[][] nArrayArray;
        Object object;
        int n2;
        int n3 = world.getHighestBlockYAt(0, 0);
        this.centerY = n3 + 1;
        int n4 = n * n + n + 1;
        for (int i = -n; i <= n; ++i) {
            for (int j = -n; j <= n; ++j) {
                if (i * i + j * j > n4) continue;
                n2 = world.getHighestBlockYAt(i, j);
                for (int k = n2 + 1; k <= n3; ++k) {
                    object = world.getBlockAt(i, k, j);
                    if (!object.getType().isAir() && object.getType().isSolid()) continue;
                    if (k < n3 - 4) {
                        object.setType(Material.DEEPSLATE, false);
                        continue;
                    }
                    if (k < n3 - 2) {
                        object.setType(Material.STONE, false);
                        continue;
                    }
                    object.setType(Material.DIRT, false);
                }
                Block block = world.getBlockAt(i, n3 + 1, j);
                if (i == 0 && j == 0) {
                    block.setType(Material.REDSTONE_BLOCK, false);
                } else if ((i + j) % 2 == 0) {
                    block.setType(Material.SMOOTH_STONE, false);
                } else {
                    block.setType(Material.STONE_BRICKS, false);
                }
                object = world.getBlockAt(i, n3 + 2, j);
                if (object.getType() == Material.AIR || object.getType().name().contains("TORCH") || object.getType().name().contains("LANTERN")) continue;
                object.setType(Material.AIR, false);
            }
        }
        int[][] nArrayArray2 = nArrayArray = new int[][]{{-n, 0}, {n, 0}, {0, -n}, {0, n}};
        n2 = nArrayArray2.length;
        for (int i = 0; i < n2; ++i) {
            object = nArrayArray2[i];
            Block block = world.getBlockAt((int)object[0], n3 + 2, (int)object[1]);
            if ((object[0] + object[1]) % 2 == false) {
                block.setType(Material.TORCH, false);
                continue;
            }
            block.setType(Material.LANTERN, false);
        }
    }

    private void buildCenterBoxes(World world) {
        List<Object> list = new ArrayList();
        for (String string : this.plugin.getConfig().getStringList("game.spawn-area-box-rarity")) {
            Rarity rarity = Rarity.parse(string);
            if (rarity == null) continue;
            list.add((Object)rarity);
        }
        if (list.isEmpty()) {
            list = List.of(Rarity.EPIC, Rarity.EPIC, Rarity.LEGENDARY, Rarity.LEGENDARY);
        }
        Object object = new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
        for (int i = 0; i < Math.min(((Object)object).length, list.size()); ++i) {
            Object object2 = object[i][0];
            Object object3 = object[i][1];
            Rarity rarity = (Rarity)((Object)list.get(i));
            this.plugin.boxes().spawnBoxAt((int)object2, (int)object3, rarity, false, null);
        }
    }

    public Location spawnPointFor(int n, int n2) {
        World world = this.plugin.worlds().world();
        if (world == null) {
            return null;
        }
        int n3 = Math.max(4, this.plugin.getConfig().getInt("game.spawn-area-radius", 7));
        double d = n2 <= 1 ? 0.0 : (double)n * (Math.PI * 2) / (double)n2 + ThreadLocalRandom.current().nextDouble(-0.3, 0.3);
        int n4 = Math.max(2, n3 - 1);
        double d2 = Math.cos(d) * (double)n4;
        double d3 = Math.sin(d) * (double)n4;
        return new Location(world, d2, (double)this.centerY, d3, (float)Math.toDegrees(d) + 90.0f, 0.0f);
    }
}
