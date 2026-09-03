/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.format.TextColor
 *  org.bukkit.Bukkit
 *  org.bukkit.Material
 *  org.bukkit.World
 *  org.bukkit.block.Block
 *  org.bukkit.block.BlockState
 *  org.bukkit.block.Chest
 *  org.bukkit.plugin.Plugin
 */
package com.terrabox;

import com.terrabox.Rarity;
import com.terrabox.TerraBoxPlugin;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.plugin.Plugin;

public class BigBoxBuilding {
    private final TerraBoxPlugin plugin;

    public BigBoxBuilding(TerraBoxPlugin terraBoxPlugin) {
        this.plugin = terraBoxPlugin;
    }

    public void buildRandom(World world) {
        int n = (int)this.plugin.worlds().borderHalf();
        int n2 = Math.max(24, this.plugin.getConfig().getInt("boxes.edge-padding", 24));
        int n3 = Math.max(48, n - n2);
        for (int i = 0; i < 12; ++i) {
            int n4;
            int n5 = ThreadLocalRandom.current().nextInt(-n3, n3);
            if (Math.hypot(n5, n4 = ThreadLocalRandom.current().nextInt(-n3, n3)) < 60.0) continue;
            int n6 = n5;
            int n7 = n4;
            int n8 = n5 >> 4;
            int n9 = n4 >> 4;
            world.getChunkAtAsync(n8, n9).whenComplete((chunk, throwable) -> {
                if (throwable != null) {
                    return;
                }
                Bukkit.getGlobalRegionScheduler().run((Plugin)this.plugin, scheduledTask2 -> {
                    try {
                        world.setChunkForceLoaded(n8, n9, true);
                    }
                    catch (Throwable throwable) {
                        // empty catch block
                    }
                    Bukkit.getRegionScheduler().run((Plugin)this.plugin, world, n8, n9, scheduledTask -> {
                        try {
                            if (!this.openArea(world, n6, n7)) {
                                return;
                            }
                            this.build(world, n6, n7);
                        }
                        catch (Throwable throwable) {
                            this.plugin.getLogger().warning("\u5927\u578b\u7269\u8d44\u5efa\u7b51\u5efa\u9020\u5f02\u5e38: " + String.valueOf(throwable));
                        }
                        finally {
                            try {
                                world.setChunkForceLoaded(n8, n9, false);
                            }
                            catch (Throwable throwable) {}
                        }
                    });
                });
            });
            return;
        }
    }

    private boolean openArea(World world, int n, int n2) {
        try {
            int n3 = world.getHighestBlockYAt(n, n2);
            for (int i = -4; i <= 4; ++i) {
                for (int j = -4; j <= 4; ++j) {
                    int n4 = world.getHighestBlockYAt(n + i, n2 + j);
                    if (Math.abs(n4 - n3) <= 4) continue;
                    return false;
                }
            }
            return true;
        }
        catch (Throwable throwable) {
            return false;
        }
    }

    public void build(World world, int n, int n2) {
        int n3;
        int n4;
        int n5;
        int n6;
        int n7 = world.getHighestBlockYAt(n, n2) + 1;
        int n8 = 9;
        int n9 = n8 / 2;
        for (n6 = -n9; n6 <= n9; ++n6) {
            for (n5 = -n9; n5 <= n9; ++n5) {
                world.getBlockAt(n + n6, n7, n2 + n5).setType(Material.STONE_BRICKS, false);
            }
        }
        for (n6 = 1; n6 <= 3; ++n6) {
            for (n5 = -n9; n5 <= n9; ++n5) {
                for (n4 = -n9; n4 <= n9; ++n4) {
                    boolean bl;
                    boolean bl2 = bl = Math.abs(n5) == n9 || Math.abs(n4) == n9;
                    if (!bl) continue;
                    int n10 = n3 = n4 == n9 && n5 == 0 && n6 <= 2 ? 1 : 0;
                    if (n3 != 0) {
                        if (n6 > 2) continue;
                        world.getBlockAt(n + n5, n7 + n6, n2 + n4).setType(Material.AIR, false);
                        continue;
                    }
                    world.getBlockAt(n + n5, n7 + n6, n2 + n4).setType(Material.STONE_BRICKS, false);
                }
            }
        }
        for (n6 = -n9; n6 <= n9; ++n6) {
            for (n5 = -n9; n5 <= n9; ++n5) {
                if (Math.abs(n6) == 0 && Math.abs(n5) == 0) continue;
                world.getBlockAt(n + n6, n7 + 4, n2 + n5).setType(Material.STONE_BRICKS, false);
            }
        }
        int[][] nArrayArray = new int[][]{{-n9 + 1, -n9 + 1}, {n9 - 1, -n9 + 1}, {-n9 + 1, n9 - 1}, {n9 - 1, n9 - 1}};
        n5 = nArrayArray.length;
        for (n4 = 0; n4 < n5; ++n4) {
            int[] nArray = nArrayArray[n4];
            world.getBlockAt(n + nArray[0], n7 + 1, n2 + nArray[1]).setType(Material.TORCH, false);
        }
        int n11 = 4 + ThreadLocalRandom.current().nextInt(3);
        List<Rarity> list = List.of(this.plugin.weightedPickForWorld(), this.plugin.weightedPickForWorld(), this.plugin.weightedPickForWorld(), this.plugin.weightedPickForWorld(), this.plugin.weightedPickForWorld(), this.plugin.weightedPickForWorld());
        for (n4 = 0; n4 < n11; ++n4) {
            int n12 = n + ThreadLocalRandom.current().nextInt(-n9 + 2, n9);
            n3 = n2 + ThreadLocalRandom.current().nextInt(-n9 + 2, n9);
            Rarity rarity = this.upgrade(list.get(n4));
            int n13 = n12 >> 4;
            int n14 = n3 >> 4;
            World world2 = world;
            int n15 = n12;
            int n16 = n3;
            int n17 = n7 + 1;
            world2.getChunkAtAsync(n13, n14).whenComplete((chunk, throwable) -> {
                if (throwable != null) {
                    return;
                }
                Bukkit.getGlobalRegionScheduler().run((Plugin)this.plugin, scheduledTask2 -> {
                    try {
                        world2.setChunkForceLoaded(n13, n14, true);
                    }
                    catch (Throwable throwable) {
                        // empty catch block
                    }
                    Bukkit.getRegionScheduler().run((Plugin)this.plugin, world2, n13, n14, scheduledTask -> {
                        try {
                            Block block = world2.getBlockAt(n15, n17 - 1, n16);
                            Block block2 = world2.getBlockAt(n15, n17, n16);
                            if (block2.getType().isAir() && block.getType().isSolid()) {
                                block2.setType(Material.CHEST, false);
                                BlockState blockState = block2.getState();
                                if (blockState instanceof Chest) {
                                    Chest chest = (Chest)blockState;
                                    chest.customName((Component)Component.text((String)(rarity.display + "\u7269\u8d44\u7bb1"), (TextColor)rarity.color));
                                    chest.update();
                                    int n6 = this.plugin.loot().fillInventory(((Chest)world2.getBlockAt(n15, n17, n16).getState()).getBlockInventory(), rarity);
                                    this.plugin.getLogger().info("\u5927\u5efa\u7b51\u7269\u8d44\u7bb1: \u5185\u90e8 " + rarity.display + " \u00d7" + n15 + "," + n17 + "," + n16 + " \u6218\u5229\u54c1 " + n6 + " \u5806");
                                }
                            }
                        }
                        catch (Throwable throwable) {
                            this.plugin.getLogger().warning("\u5efa\u7b51\u7269\u8d44\u7bb1\u653e\u7f6e\u5f02\u5e38: " + String.valueOf(throwable));
                        }
                        finally {
                            try {
                                world2.setChunkForceLoaded(n13, n14, false);
                            }
                            catch (Throwable throwable) {}
                        }
                    });
                });
            });
        }
        this.plugin.getLogger().info("\u5927\u578b\u7269\u8d44\u5efa\u7b51\u5df2\u5efa\u6210: (" + n + "," + n2 + ") \u5ba4\u5185 " + n11 + " \u7bb1");
    }

    private Rarity upgrade(Rarity rarity) {
        return switch (rarity) {
            case Rarity.COMMON -> Rarity.RARE;
            case Rarity.RARE -> Rarity.EPIC;
            default -> rarity;
        };
    }
}
