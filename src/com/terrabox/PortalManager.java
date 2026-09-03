/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.GameMode
 *  org.bukkit.GameRule
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.World
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.player.PlayerMoveEvent
 *  org.bukkit.plugin.Plugin
 */
package com.terrabox;

import com.terrabox.TerraBoxPlugin;
import com.terrabox.TerrainType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.Plugin;

public class PortalManager
implements Listener {
    private final TerraBoxPlugin plugin;
    private final Map<String, List<Portal>> portals = new ConcurrentHashMap<String, List<Portal>>();
    private final Map<UUID, Long> lastPortal = new ConcurrentHashMap<UUID, Long>();

    public PortalManager(TerraBoxPlugin terraBoxPlugin) {
        this.plugin = terraBoxPlugin;
    }

    public boolean isEnabled() {
        return this.plugin.getConfig().getBoolean("portals.enabled", true);
    }

    public void buildPortals(World world) {
        TerrainType terrainType;
        if (world == null || !this.isEnabled()) {
            return;
        }
        TerrainType terrainType2 = terrainType = this.plugin.arenas() != null ? this.plugin.arenas().terrainOf(world.getName()) : TerrainType.DEFAULT;
        if (terrainType != TerrainType.NORMAL && terrainType != TerrainType.NETHER && terrainType != TerrainType.THE_END) {
            return;
        }
        this.portals.remove(world.getName());
        ArrayList<Portal> arrayList = new ArrayList<Portal>();
        if (terrainType == TerrainType.NORMAL) {
            this.addPortal(world, arrayList, "\u5730\u72f1\u4f20\u9001\u95e8", this.targetFor("nether"), Material.NETHERRACK, 16);
            this.addPortal(world, arrayList, "\u672b\u5730\u4f20\u9001\u95e8", this.targetFor("the_end"), Material.END_PORTAL_FRAME, -16);
        } else {
            this.addPortal(world, arrayList, "\u56de\u4e3b\u4e16\u754c\u4f20\u9001\u95e8", this.normalTarget(), Material.OBSIDIAN, 0);
        }
        this.portals.put(world.getName(), arrayList);
    }

    private void addPortal(World world, List<Portal> list, String string, String string2, Material material, int n) {
        int n2 = this.plugin.getConfig().getInt("portals.offset-z", 120);
        int n3 = this.plugin.getConfig().getInt("portals.offset-x", 0) + n;
        list.add(new Portal(string2, n3, n2, this.plugin.getConfig().getDouble("portals.radius", 3.0)));
        this.placeStruct(world, string, n3, n2, material);
    }

    private void placeStruct(World world, String string, int n, int n2, Material material) {
        int n3 = n >> 4;
        int n4 = n2 >> 4;
        world.getChunkAtAsync(n3, n4).whenComplete((chunk, throwable) -> {
            if (throwable != null) {
                this.plugin.getLogger().warning("\u4f20\u9001\u95e8\u533a\u5757\u52a0\u8f7d\u5931\u8d25: " + String.valueOf(throwable));
                return;
            }
            Bukkit.getRegionScheduler().run((Plugin)this.plugin, world, n3, n4, scheduledTask -> {
                try {
                    world.setChunkForceLoaded(n3, n4, true);
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
                try {
                    int n5;
                    int n6;
                    int n7;
                    int n8 = world.getHighestBlockYAt(n, n2);
                    int n9 = n8 + 1;
                    for (n7 = -3; n7 <= 3; ++n7) {
                        for (n6 = -3; n6 <= 3; ++n6) {
                            world.getBlockAt(n + n7, n9, n2 + n6).setType(Material.STONE_BRICKS, false);
                        }
                    }
                    n7 = 4;
                    n6 = 1;
                    for (n5 = 0; n5 < n7; ++n5) {
                        world.getBlockAt(n - n6 - 1, n9 + 1 + n5, n2).setType(Material.OBSIDIAN, false);
                        world.getBlockAt(n + n6 + 1, n9 + 1 + n5, n2).setType(Material.OBSIDIAN, false);
                    }
                    for (n5 = -n6 - 1; n5 <= n6 + 1; ++n5) {
                        world.getBlockAt(n + n5, n9 + 1 + n7, n2).setType(Material.OBSIDIAN, false);
                    }
                    world.getBlockAt(n, n9 + 1, n2).setType(material, false);
                    world.getBlockAt(n, n9 + 2, n2).setType(material, false);
                    world.getBlockAt(n, n9 + 1 + 2, n2).setType(Material.TORCH, false);
                    world.getBlockAt(n, n9 + 1 + n7 + 1, n2).setType(Material.LANTERN, false);
                    try {
                        world.setChunkForceLoaded(n3, n4, false);
                    }
                    catch (Throwable throwable) {
                        // empty catch block
                    }
                    this.plugin.getLogger().info("\u4f20\u9001\u95e8\u5df2\u5efa\u6210: " + string + " (" + world.getName() + " " + n + "," + n9 + "," + n2 + ")");
                }
                catch (Throwable throwable) {
                    this.plugin.getLogger().warning("\u4f20\u9001\u95e8\u6784\u5efa\u5f02\u5e38 (" + string + "): " + String.valueOf(throwable));
                    try {
                        world.setChunkForceLoaded(n3, n4, false);
                    }
                    catch (Throwable throwable2) {
                        // empty catch block
                    }
                }
            });
        });
    }

    private String targetFor(String string) {
        return this.plugin.getConfig().getString("portals.target-" + string, "arena_" + string + "_1");
    }

    private String normalTarget() {
        if (this.plugin.arenas() != null) {
            for (String string : this.plugin.arenas().names()) {
                if (this.plugin.arenas().terrainOf(string) != TerrainType.NORMAL) continue;
                return string;
            }
        }
        return this.plugin.getConfig().getString("portals.target-normal", "arena_normal_1");
    }

    @EventHandler
    public void onMove(PlayerMoveEvent playerMoveEvent) {
        World world;
        if (!this.isEnabled()) {
            return;
        }
        Player player = playerMoveEvent.getPlayer();
        if (player == null || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        World world2 = world = playerMoveEvent.getTo() == null ? null : playerMoveEvent.getTo().getWorld();
        if (world == null) {
            return;
        }
        List<Portal> list = this.portals.get(world.getName());
        if (list == null || list.isEmpty()) {
            return;
        }
        Location location = playerMoveEvent.getTo();
        for (Portal portal : list) {
            double d;
            double d2 = location.getX() - (double)portal.x();
            if (d2 * d2 + (d = location.getZ() - (double)portal.z()) * d > portal.radius() * portal.radius()) continue;
            long l = System.currentTimeMillis();
            Long l2 = this.lastPortal.get(player.getUniqueId());
            if (l2 != null && l - l2 < 3000L) {
                return;
            }
            this.transfer(player, world, portal);
            return;
        }
    }

    private void transfer(Player player, World world2, Portal portal) {
        this.lastPortal.put(player.getUniqueId(), System.currentTimeMillis());
        this.ensureLoaded(portal.targetWorld(), world -> {
            if (world == null) {
                player.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u76ee\u6807\u4e16\u754c\u4e0d\u53ef\u7528\u3002");
                return;
            }
            int n = 0;
            int n2 = 0;
            world.getChunkAtAsync(n, n2).thenAccept(chunk -> Bukkit.getRegionScheduler().run((Plugin)this.plugin, world, n, n2, scheduledTask -> {
                int n3 = Math.max(world.getMinHeight() + 1, world.getHighestBlockYAt(n, n2));
                Location location = new Location(world, (double)n + 0.5, (double)n3 + 1.2, (double)n2 + 0.5);
                player.teleportAsync(location).thenAccept(bl -> {
                    if (bl.booleanValue()) {
                        player.sendMessage(this.plugin.msg("prefix") + "\u00a7a\u6b22\u8fce\u6765\u5230: \u00a7f" + world.getName());
                    } else {
                        player.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u4f20\u9001\u5931\u8d25\u3002");
                    }
                });
            }));
        });
    }

    public void ensureLoaded(String string, Consumer<World> consumer) {
        World world = Bukkit.getWorld((String)string);
        if (world != null) {
            consumer.accept(world);
            return;
        }
        TerrainType terrainType = this.inferType(string);
        Bukkit.getGlobalRegionScheduler().run((Plugin)this.plugin, scheduledTask -> {
            World world = this.plugin.arenas().create(string, terrainType);
            if (world != null) {
                try {
                    world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, (Object)false);
                    world.setTime(6000L);
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
                this.plugin.worlds().ensurePregen(world);
                this.buildPortals(world);
            }
            consumer.accept(world);
        });
    }

    private TerrainType inferType(String string) {
        String string2 = string.toLowerCase();
        if (string2.contains("nether")) {
            return TerrainType.NETHER;
        }
        if (string2.contains("the_end") || string2.contains("end")) {
            return TerrainType.THE_END;
        }
        if (string2.contains("normal")) {
            return TerrainType.NORMAL;
        }
        return TerrainType.DEFAULT;
    }

    public boolean isNormalWorld(World world) {
        return world != null && this.plugin.arenas() != null && this.plugin.arenas().terrainOf(world.getName()) == TerrainType.NORMAL;
    }

    private record Portal(String targetWorld, int x, int z, double radius) {
    }
}
