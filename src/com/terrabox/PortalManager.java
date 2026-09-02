package com.terrabox;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 跨世界传送门系统 (正常主世界 ↔ 地狱 / 末地)
 *
 * 玩法:
 *  - 正常主世界 (NORMAL): 出生广场附近放置"地狱传送门"和"末地传送门"
 *  - 地狱 (arena_nether): 放置"回主世界传送门"
 *  - 末地 (arena_the_end): 放置"回主世界传送门"
 *  - 玩家走进传送门区域 (XZ 距离) → 传送到目标世界中央地表
 *
 * 线程模型: 传送门放置走 getChunkAtAsync → RegionScheduler (区域线程);
 *   玩家进入检测在玩家区域线程 (PlayerMoveEvent), teleportAsync 安全。
 */
public class PortalManager implements Listener {
    private final TerraBoxPlugin plugin;
    private final Map<String, List<Portal>> portals = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastPortal = new ConcurrentHashMap<>();

    /** 传送门定义: 目标世界名 + 传送门中心 XZ + 触发半径 */
    private record Portal(String targetWorld, int x, int z, double radius) {}

    public PortalManager(TerraBoxPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("portals.enabled", true);
    }

    /** 为当前世界放置传送门 (有传送门配置的世界), 任意线程异步链 */
    public void buildPortals(World w) {
        if (w == null || !isEnabled()) return;
        TerrainType type = plugin.arenas() != null ? plugin.arenas().terrainOf(w.getName()) : TerrainType.DEFAULT;
        if (type != TerrainType.NORMAL && type != TerrainType.NETHER && type != TerrainType.THE_END) return;

        portals.remove(w.getName());
        List<Portal> list = new ArrayList<>();
        if (type == TerrainType.NORMAL) {
            // 主世界: 地狱门 + 末地门
            addPortal(w, list, "地狱传送门", targetFor("nether"), Material.NETHERRACK, 16);
            addPortal(w, list, "末地传送门", targetFor("the_end"), Material.END_PORTAL_FRAME, -16);
        } else {
            // 地狱/末地: 回主世界门
            addPortal(w, list, "回主世界传送门", normalTarget(), Material.OBSIDIAN, 0);
        }
        portals.put(w.getName(), list);
    }

    private void addPortal(World w, List<Portal> list, String label, String targetWorld, Material marker, int offsetX) {
        int z = plugin.getConfig().getInt("portals.offset-z", 120);
        int x = plugin.getConfig().getInt("portals.offset-x", 0) + offsetX;
        list.add(new Portal(targetWorld, x, z, plugin.getConfig().getDouble("portals.radius", 3.0)));
        placeStruct(w, label, x, z, marker);
    }

    /** 异步构建传送门方块结构 */
    private void placeStruct(World w, String label, int x, int z, Material marker) {
        int cx = x >> 4, cz = z >> 4;
        w.getChunkAtAsync(cx, cz).whenComplete((chunk, err) -> {
            if (err != null) { plugin.getLogger().warning("传送门区块加载失败: " + err); return; }
            Bukkit.getRegionScheduler().run(plugin, w, cx, cz, task -> {
                try { w.setChunkForceLoaded(cx, cz, true); } catch (Throwable ignored) {}
                try {
                    int groundY = w.getHighestBlockYAt(x, z);
                    int y0 = groundY + 1;
                    // 平台 7x7 石砖
                    for (int dx = -3; dx <= 3; dx++)
                        for (int dz = -3; dz <= 3; dz++)
                            w.getBlockAt(x + dx, y0, z + dz).setType(Material.STONE_BRICKS, false);
                    // 门框: 左右柱 + 顶梁 (黑曜石)
                    int h = 4, w2 = 1;
                    for (int dy = 0; dy < h; dy++) {
                        w.getBlockAt(x - w2 - 1, y0 + 1 + dy, z).setType(Material.OBSIDIAN, false);
                        w.getBlockAt(x + w2 + 1, y0 + 1 + dy, z).setType(Material.OBSIDIAN, false);
                    }
                    for (int dx = -w2 - 1; dx <= w2 + 1; dx++)
                        w.getBlockAt(x + dx, y0 + 1 + h, z).setType(Material.OBSIDIAN, false);
                    // 门洞内 marker (传送点)
                    w.getBlockAt(x, y0 + 1, z).setType(marker, false);
                    w.getBlockAt(x, y0 + 2, z).setType(marker, false);
                    w.getBlockAt(x, y0 + 1 + 2, z).setType(Material.TORCH, false);
                    // 顶部灯笼
                    w.getBlockAt(x, y0 + 1 + h + 1, z).setType(Material.LANTERN, false);
                    try { w.setChunkForceLoaded(cx, cz, false); } catch (Throwable ignored) {}
                    plugin.getLogger().info("传送门已建成: " + label + " (" + w.getName() + " " + x + "," + y0 + "," + z + ")");
                } catch (Throwable ex) {
                    plugin.getLogger().warning("传送门构建异常 (" + label + "): " + ex);
                    try { w.setChunkForceLoaded(cx, cz, false); } catch (Throwable ignored) {}
                }
            });
        });
    }

    private String targetFor(String kind) {
        return plugin.getConfig().getString("portals.target-" + kind, "arena_" + kind + "_1");
    }

    /** 当前正常主世界名 (或默认 arena_normal_1) */
    private String normalTarget() {
        if (plugin.arenas() != null) {
            for (String n : plugin.arenas().names()) {
                if (plugin.arenas().terrainOf(n) == TerrainType.NORMAL) return n;
            }
        }
        return plugin.getConfig().getString("portals.target-normal", "arena_normal_1");
    }

    /** 玩家进入传送门检测 (玩家区域线程) */
    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (!isEnabled()) return;
        Player p = e.getPlayer();
        if (p == null || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        World w = e.getTo() == null ? null : e.getTo().getWorld();
        if (w == null) return;
        List<Portal> list = portals.get(w.getName());
        if (list == null || list.isEmpty()) return;
        Location to = e.getTo();
        for (Portal port : list) {
            double dx = to.getX() - port.x(), dz = to.getZ() - port.z();
            if (dx * dx + dz * dz > port.radius() * port.radius()) continue;
            long now = System.currentTimeMillis();
            Long last = lastPortal.get(p.getUniqueId());
            if (last != null && now - last < 3000) return;
            transfer(p, w, port);
            return;
        }
    }

    /** 执行跨世界传送 */
    private void transfer(Player p, World from, Portal port) {
        lastPortal.put(p.getUniqueId(), System.currentTimeMillis());
        ensureLoaded(port.targetWorld(), tw -> {
            if (tw == null) {
                p.sendMessage("§c目标世界不可用。");
                return;
            }
            int cx = 0, cz = 0; // 目标世界中央传送
            tw.getChunkAtAsync(cx, cz).thenAccept(chunk -> {
                Bukkit.getRegionScheduler().run(plugin, tw, cx, cz, task -> {
                    int ty = Math.max(tw.getMinHeight() + 1, tw.getHighestBlockYAt(cx, cz));
                    // 主世界/末地目标地面 y 可能为虚空上方, 兜底用基岩层
                    Location loc = new Location(tw, cx + 0.5, ty + 1.2, cz + 0.5);
                    p.teleportAsync(loc).thenAccept(ok -> {
                        if (ok) p.sendMessage("§a欢迎来到: §f" + tw.getName());
                        else p.sendMessage("§c传送失败。");
                    });
                });
            });
        });
    }

    /** 确保目标世界已加载 (不存在则在 Global 线程创建) */
    public void ensureLoaded(String name, Consumer<World> cb) {
        World w = Bukkit.getWorld(name);
        if (w != null) { cb.accept(w); return; }
        TerrainType type = inferType(name);
        Bukkit.getGlobalRegionScheduler().run(plugin, t -> {
            World created = plugin.arenas().create(name, type);
            if (created != null) {
                try {
                    created.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
                    created.setTime(6000);
                } catch (Throwable ignored) {}
                plugin.worlds().ensurePregen(created);
                buildPortals(created);
            }
            cb.accept(created);
        });
    }

    private TerrainType inferType(String name) {
        String n = name.toLowerCase();
        if (n.contains("nether")) return TerrainType.NETHER;
        if (n.contains("the_end") || n.contains("end")) return TerrainType.THE_END;
        if (n.contains("normal")) return TerrainType.NORMAL;
        return TerrainType.DEFAULT;
    }

    /** 当前世界是否为正常主世界 */
    public boolean isNormalWorld(World w) {
        return w != null && plugin.arenas() != null
                && plugin.arenas().terrainOf(w.getName()) == TerrainType.NORMAL;
    }
}
