/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.papermc.paper.threadedregions.scheduler.ScheduledTask
 *  org.bukkit.Bukkit
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.World
 *  org.bukkit.block.Block
 *  org.bukkit.entity.Player
 *  org.bukkit.plugin.Plugin
 */
package com.terrabox;

import com.terrabox.TerraBoxPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class LobbyBuilder {
    private final TerraBoxPlugin plugin;
    private volatile int centerY = 64;
    private volatile World lobby;
    private final AtomicBoolean building = new AtomicBoolean(false);

    public LobbyBuilder(TerraBoxPlugin terraBoxPlugin) {
        this.plugin = terraBoxPlugin;
    }

    public World lobby() {
        return this.plugin.worlds().lobby();
    }

    public int centerY() {
        return this.centerY;
    }

    public void build(Runnable runnable) {
        int n;
        int n2;
        int n3;
        int n4;
        int n5;
        World world = this.lobby();
        if (world == null) {
            if (runnable != null) {
                runnable.run();
            }
            return;
        }
        if (!this.building.compareAndSet(false, true)) {
            if (runnable != null) {
                runnable.run();
            }
            return;
        }
        int n6 = Math.max(8, this.plugin.getConfig().getInt("lobby.platform-radius", 24));
        int n7 = (int)this.plugin.worlds().lobbyHalf();
        HashSet<AbstractMap.SimpleEntry<Integer, Integer>> hashSet = new HashSet<AbstractMap.SimpleEntry<Integer, Integer>>();
        int n8 = (int)Math.floor((double)(-n6) / 16.0);
        int n9 = (int)Math.ceil((double)n6 / 16.0);
        for (n5 = n8; n5 < n9; ++n5) {
            for (n4 = n8; n4 < n9; ++n4) {
                hashSet.add(new AbstractMap.SimpleEntry<Integer, Integer>(n5, n4));
            }
        }
        n5 = (int)Math.floor((double)(-(n6 + 1)) / 16.0);
        n4 = (int)Math.ceil((double)(n6 + 1) / 16.0);
        for (n3 = n5; n3 < n4; ++n3) {
            for (n2 = n5; n2 < n4; ++n2) {
                hashSet.add(new AbstractMap.SimpleEntry<Integer, Integer>(n3, n2));
            }
        }
        n3 = (int)Math.floor((double)(-n7) / 16.0);
        n2 = (int)Math.ceil((double)n7 / 16.0);
        for (n = n3; n < n2; ++n) {
            hashSet.add(new AbstractMap.SimpleEntry<Integer, Integer>(n, n3));
            hashSet.add(new AbstractMap.SimpleEntry<Integer, Integer>(n, n2 - 1));
            hashSet.add(new AbstractMap.SimpleEntry<Integer, Integer>(n3, n));
            hashSet.add(new AbstractMap.SimpleEntry<Integer, Integer>(n2 - 1, n));
        }
        this.plugin.getLogger().info("\u5f00\u59cb\u6784\u5efa\u5927\u5385: " + hashSet.size() + " \u4e2a\u533a\u5757\u4efb\u52a1");
        n = Math.max(2, this.plugin.getConfig().getInt("lobby.batch-per-tick", 8));
        ArrayDeque arrayDeque = new ArrayDeque(hashSet);
        World world2 = world;
        ScheduledTask scheduledTask2 = Bukkit.getGlobalRegionScheduler().runAtFixedRate((Plugin)this.plugin, scheduledTask -> {
            if (arrayDeque.isEmpty()) {
                scheduledTask.cancel();
                this.building.set(false);
                this.centerY = this.computeCenterY(world2);
                this.plugin.getLogger().info("\u5927\u5385\u6784\u5efa\u5b8c\u6210: \u4e2d\u5fc3 y=" + this.centerY);
                if (runnable != null) {
                    runnable.run();
                }
                return;
            }
            for (int i = 0; i < n && !arrayDeque.isEmpty(); ++i) {
                Map.Entry entry = (Map.Entry)arrayDeque.poll();
                int n4 = (Integer)entry.getKey();
                int n5 = (Integer)entry.getValue();
                world2.getChunkAtAsync(n4, n5).whenComplete((chunk, throwable) -> {
                    if (throwable != null) {
                        this.plugin.getLogger().warning("\u5927\u5385\u533a\u5757\u52a0\u8f7d\u5931\u8d25 (" + n4 + "," + n5 + "): " + String.valueOf(throwable));
                        return;
                    }
                    Bukkit.getGlobalRegionScheduler().run((Plugin)this.plugin, scheduledTask2 -> {
                        try {
                            world2.setChunkForceLoaded(n4, n5, true);
                        }
                        catch (Throwable throwable) {
                            // empty catch block
                        }
                        Bukkit.getRegionScheduler().run((Plugin)this.plugin, world2, n4, n5, scheduledTask -> {
                            try {
                                this.buildPlatform(world2, n4, n5, n6);
                                this.buildWall(world2, n4, n5, n7);
                            }
                            catch (Throwable throwable) {
                                this.plugin.getLogger().warning("\u5927\u5385\u6784\u5efa\u5f02\u5e38 (" + n4 + "," + n5 + "): " + String.valueOf(throwable));
                            }
                            finally {
                                try {
                                    world2.setChunkForceLoaded(n4, n5, false);
                                }
                                catch (Throwable throwable) {}
                            }
                        });
                    });
                });
            }
        }, 20L, 8L);
    }

    private void buildPlatform(World world, int n, int n2, int n3) {
        int n4 = this.plugin.getConfig().getInt("lobby.platform-y", 64);
        int n5 = n3 * n3 + n3 + 1;
        int n6 = (n3 + 1) * (n3 + 1);
        for (int i = 0; i < 16; ++i) {
            for (int j = 0; j < 16; ++j) {
                int n7 = n * 16 + i;
                int n8 = n2 * 16 + j;
                if (n7 * n7 + n8 * n8 <= n5) {
                    for (int k = 1; k <= n4; ++k) {
                        Block block = world.getBlockAt(n7, k, n8);
                        if (k <= 2) {
                            block.setType(Material.BEDROCK, false);
                            continue;
                        }
                        if (k < n4) {
                            block.setType(Material.STONE, false);
                            continue;
                        }
                        block.setType(Material.STONE_BRICKS, false);
                    }
                    Block block = world.getBlockAt(n7, n4 + 1, n8);
                    if (!(block.getType().isAir() || block.getType().name().contains("GLASS") || block.getType().name().contains("BARRIER"))) {
                        block.setType(Material.AIR, false);
                    }
                    if (n7 * n7 + n8 * n8 <= (n3 - 1) * (n3 - 1) + 1) continue;
                    for (int k = 1; k <= 2; ++k) {
                        Block block2 = world.getBlockAt(n7, n4 + k, n8);
                        if (!block2.getType().isAir()) continue;
                        block2.setType(Material.GLASS, false);
                    }
                    continue;
                }
                if (n7 * n7 + n8 * n8 > n6) continue;
                for (int k = 1; k <= n4; ++k) {
                    Block block = world.getBlockAt(n7, k, n8);
                    if (block.getType() == Material.AIR) continue;
                    block.setType(Material.AIR, false);
                }
            }
        }
    }

    private void buildWall(World world, int n, int n2, int n3) {
        int n4 = this.plugin.getConfig().getInt("lobby.platform-y", 64);
        for (int i = 0; i < 16; ++i) {
            for (int j = 0; j < 16; ++j) {
                int n5;
                boolean bl;
                int n6 = n * 16 + i;
                int n7 = n2 * 16 + j;
                boolean bl2 = bl = Math.abs(n6) >= n3 - 2 || Math.abs(n7) >= n3 - 2;
                if (bl) {
                    for (n5 = 1; n5 < world.getMaxHeight(); ++n5) {
                        world.getBlockAt(n6, n5, n7).setType(Material.BARRIER, false);
                    }
                    continue;
                }
                if (Math.abs(n6) != n3 - 3 && Math.abs(n7) != n3 - 3) continue;
                for (n5 = n4; n5 <= n4 + 3; ++n5) {
                    world.getBlockAt(n6, n5, n7).setType(Material.GLASS, false);
                }
            }
        }
    }

    private int computeCenterY(World world) {
        return this.plugin.getConfig().getInt("lobby.platform-y", 64) + 1;
    }

    public Location spawnLocation() {
        World world = this.lobby();
        if (world == null) {
            return null;
        }
        int n = this.plugin.getConfig().getInt("lobby.platform-y", 64) + 1;
        return new Location(world, 0.5, (double)n, 0.5);
    }

    public void startSafetyWatch() {
        int n = this.plugin.getConfig().getInt("lobby.platform-y", 64);
        Bukkit.getGlobalRegionScheduler().runAtFixedRate((Plugin)this.plugin, scheduledTask -> {
            World world = this.lobby();
            if (world == null) {
                return;
            }
            for (Player player : world.getPlayers()) {
                if (!(player.getLocation().getY() < (double)(n - 2))) continue;
                player.teleportAsync(this.spawnLocation());
            }
        }, 20L, 20L);
    }
}
