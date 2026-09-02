package com.terrabox;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * 随机陆地出生点: 在固定地图范围内随机选点, 校验为陆地后传送
 * 线程模型:
 *  - 选点: 纯数学, 任意线程
 *  - 地形校验: getChunkAtAsync → RegionScheduler (区域线程读方块)
 *  - 传送: Player#teleportAsync (Folia 安全)
 */
public class SpawnManager {
    private final TerraBoxPlugin plugin;
    private final ConcurrentHashMap<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public SpawnManager(TerraBoxPlugin plugin) {
        this.plugin = plugin;
    }

    /** 随机找一个陆地坐标 (异步), 找到后回调 (区域线程) */
    public void randomLand(Consumer<Location> onFound, Runnable onFail) {
        randomLand(onFound, onFail, 0);
    }

    private void randomLand(Consumer<Location> onFound, Runnable onFail, int attempt) {
        World w = plugin.worlds().world();
        if (w == null) {
            if (onFail != null) onFail.run();
            return;
        }
        int tries = Math.max(4, plugin.getConfig().getInt("spawn.tries", 10));
        if (attempt >= tries) {
            if (onFail != null) onFail.run();
            return;
        }
        int minR = plugin.getConfig().getInt("spawn.min-radius", 120);
        int maxR = Math.min(plugin.getConfig().getInt("spawn.max-radius", 950),
                (int) plugin.worlds().borderHalf() - 24);
        if (maxR <= minR) minR = Math.max(16, maxR / 2);

        double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
        double radius = minR + ThreadLocalRandom.current().nextInt(Math.max(1, maxR - minR));
        int x = (int) (Math.cos(angle) * radius);
        int z = (int) (Math.sin(angle) * radius);

        int cx = x >> 4, cz = z >> 4;
        final int fx = x, fz = z, fa = attempt;
        w.getChunkAtAsync(cx, cz).whenComplete((chunk, err) -> {
            if (err != null) {
                plugin.getLogger().warning("出生点区块加载失败 (" + cx + "," + cz + "): " + err);
                if (onFail != null) onFail.run();
                return;
            }
            // force load 保持区块, 防 Folia 激进卸载导致区域任务被丢弃
            Bukkit.getGlobalRegionScheduler().run(plugin, t -> {
                try { w.setChunkForceLoaded(cx, cz, true); } catch (Throwable ignored) {}
                Bukkit.getRegionScheduler().run(plugin, w, cx, cz, task -> {
                    try {
                        Location found = landAt(w, fx, fz);
                        if (found != null) onFound.accept(found);
                        else randomLand(onFound, onFail, fa + 1);
                    } catch (Throwable ex) {
                        plugin.getLogger().warning("出生点地形校验异常 (" + fx + "," + fz + "): " + ex);
                        randomLand(onFound, onFail, fa + 1);
                    } finally {
                        try { w.setChunkForceLoaded(cx, cz, false); } catch (Throwable ignored) {}
                    }
                });
            });
        });
    }

    /** 区域线程: 校验 (x,z) 是否适合出生, 返回出生 Location 或 null */
    private Location landAt(World w, int x, int z) {
        Block ground = w.getHighestBlockAt(x, z);
        Material m = ground.getType();
        if (!m.isSolid()) return null;
        String n = m.name();
        if (n.contains("LEAVES") || n.contains("LOG") || n.contains("ICE")
                || n.contains("WATER") || n.contains("CACTUS") || n.contains("MAGMA")) return null;
        if (!ground.getRelative(0, 1, 0).getType().isAir()) return null;
        if (!ground.getRelative(0, 2, 0).getType().isAir()) return null;
        Location loc = ground.getLocation().add(0.5, 1.2, 0.5);
        loc.setYaw(ThreadLocalRandom.current().nextFloat() * 360f);
        if (!w.getWorldBorder().isInside(loc)) return null;
        return loc;
    }

    /** 传送玩家到随机陆地 (带冷却, 管理员免冷却) */
    public void spawnPlayer(Player p, boolean respectCooldown) {
        if (respectCooldown && !p.hasPermission("terrabox.admin")) {
            long cd = plugin.getConfig().getLong("spawn.command-cooldown-seconds", 300) * 1000L;
            long now = System.currentTimeMillis();
            Long last = cooldowns.get(p.getUniqueId());
            if (last != null && now - last < cd) {
                long remain = (cd - (now - last)) / 1000;
                p.sendMessage(plugin.msg("cooldown").replace("{seconds}", String.valueOf(remain)));
                return;
            }
            cooldowns.put(p.getUniqueId(), now);
        }
        World w = plugin.worlds().world();
        if (w == null) {
            p.sendMessage(plugin.msg("not-ready"));
            return;
        }
        p.sendMessage(plugin.msg("prefix") + "§e正在为你寻找随机陆地出生点...");
        randomLand(loc -> {
            p.teleportAsync(loc).thenAccept(ok -> {
                if (ok) p.sendMessage(plugin.msg("respawn-found"));
            });
        }, () -> p.sendMessage(plugin.msg("respawn-fail")));
    }
}
