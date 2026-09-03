/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class SpawnManager {
    private final TerraBoxPlugin plugin;
    private final ConcurrentHashMap<UUID, Long> cooldowns = new ConcurrentHashMap();

    public SpawnManager(TerraBoxPlugin terraBoxPlugin) {
        this.plugin = terraBoxPlugin;
    }

    public void randomLand(Consumer<Location> consumer, Runnable runnable) {
        this.randomLand(consumer, runnable, 0);
    }

    private void randomLand(Consumer<Location> consumer, Runnable runnable, int n) {
        World world = this.plugin.worlds().world();
        if (world == null) {
            if (runnable != null) {
                runnable.run();
            }
            return;
        }
        int n2 = Math.max(4, this.plugin.getConfig().getInt("spawn.tries", 10));
        if (n >= n2) {
            if (runnable != null) {
                runnable.run();
            }
            return;
        }
        int n3 = this.plugin.getConfig().getInt("spawn.min-radius", 120);
        int n4 = Math.min(this.plugin.getConfig().getInt("spawn.max-radius", 950), (int)this.plugin.worlds().borderHalf() - 24);
        if (n4 <= n3) {
            n3 = Math.max(16, n4 / 2);
        }
        double d = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
        double d2 = n3 + ThreadLocalRandom.current().nextInt(Math.max(1, n4 - n3));
        int n5 = (int)(Math.cos(d) * d2);
        int n6 = (int)(Math.sin(d) * d2);
        int n7 = n5 >> 4;
        int n8 = n6 >> 4;
        int n9 = n5;
        int n10 = n6;
        int n11 = n;
        world.getChunkAtAsync(n7, n8).whenComplete((chunk, throwable) -> {
            if (throwable != null) {
                this.plugin.getLogger().warning("\u51fa\u751f\u70b9\u533a\u5757\u52a0\u8f7d\u5931\u8d25 (" + n7 + "," + n8 + "): " + String.valueOf(throwable));
                if (runnable != null) {
                    runnable.run();
                }
                return;
            }
            Bukkit.getGlobalRegionScheduler().run((Plugin)this.plugin, scheduledTask2 -> {
                try {
                    world.setChunkForceLoaded(n7, n8, true);
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
                Bukkit.getRegionScheduler().run((Plugin)this.plugin, world, n7, n8, scheduledTask -> {
                    try {
                        Location location = this.landAt(world, n9, n10);
                        if (location != null) {
                            consumer.accept(location);
                        } else {
                            this.randomLand(consumer, runnable, n11 + 1);
                        }
                    }
                    catch (Throwable throwable) {
                        this.plugin.getLogger().warning("\u51fa\u751f\u70b9\u5730\u5f62\u6821\u9a8c\u5f02\u5e38 (" + n9 + "," + n10 + "): " + String.valueOf(throwable));
                        this.randomLand(consumer, runnable, n11 + 1);
                    }
                    finally {
                        try {
                            world.setChunkForceLoaded(n7, n8, false);
                        }
                        catch (Throwable throwable) {}
                    }
                });
            });
        });
    }

    private Location landAt(World world, int n, int n2) {
        Block block = world.getHighestBlockAt(n, n2);
        Material material = block.getType();
        if (!material.isSolid()) {
            return null;
        }
        String string = material.name();
        if (string.contains("LEAVES") || string.contains("LOG") || string.contains("ICE") || string.contains("WATER") || string.contains("CACTUS") || string.contains("MAGMA")) {
            return null;
        }
        if (!block.getRelative(0, 1, 0).getType().isAir()) {
            return null;
        }
        if (!block.getRelative(0, 2, 0).getType().isAir()) {
            return null;
        }
        Location location = block.getLocation().add(0.5, 1.2, 0.5);
        location.setYaw(ThreadLocalRandom.current().nextFloat() * 360.0f);
        if (!world.getWorldBorder().isInside(location)) {
            return null;
        }
        return location;
    }

    public void spawnPlayer(Player player, boolean bl) {
        World world;
        if (bl && !player.hasPermission("terrabox.admin")) {
            long l = this.plugin.getConfig().getLong("spawn.command-cooldown-seconds", 300L) * 1000L;
            long l2 = System.currentTimeMillis();
            Long l3 = this.cooldowns.get(player.getUniqueId());
            if (l3 != null && l2 - l3 < l) {
                long l4 = (l - (l2 - l3)) / 1000L;
                player.sendMessage(this.plugin.msg("cooldown").replace("{seconds}", String.valueOf(l4)));
                return;
            }
            this.cooldowns.put(player.getUniqueId(), l2);
        }
        if ((world = this.plugin.worlds().world()) == null) {
            player.sendMessage(this.plugin.msg("not-ready"));
            return;
        }
        player.sendMessage(this.plugin.msg("prefix") + "\u00a7e\u6b63\u5728\u4e3a\u4f60\u5bfb\u627e\u968f\u673a\u9646\u5730\u51fa\u751f\u70b9...");
        this.randomLand(location -> player.teleportAsync(location).thenAccept(bl -> {
            if (bl.booleanValue()) {
                player.sendMessage(this.plugin.msg("respawn-found"));
            }
        }), () -> player.sendMessage(this.plugin.msg("respawn-fail")));
    }
}
