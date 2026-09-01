package com.terrabox;

import org.bukkit.Bukkit;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 定时空投: 间隔投放高稀有度物资箱 + 全服广播坐标, 制造全服争夺玩法
 * 线程模型: GlobalRegionScheduler 检查时机; 投放由 BoxManager 的区域调度链完成
 */
public class AirdropService {
    private final TerraBoxPlugin plugin;
    private ScheduledTask task;
    private final AtomicLong nextAt = new AtomicLong();

    public AirdropService(TerraBoxPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("airdrop.enabled", true)) return;
        long intervalMs = Math.max(1, plugin.getConfig().getLong("airdrop.interval-minutes", 20)) * 60_000L;
        nextAt.set(System.currentTimeMillis() + intervalMs / 2); // 启动后半个周期投第一波
        task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> tick(), 20L * 30, 20L * 30);
    }

    public void shutdown() {
        if (task != null) task.cancel();
    }

    private void tick() {
        if (System.currentTimeMillis() < nextAt.get()) return;
        long intervalMs = Math.max(1, plugin.getConfig().getLong("airdrop.interval-minutes", 20)) * 60_000L;
        nextAt.set(System.currentTimeMillis() + intervalMs);
        dropNow(null);
    }

    /** 立即空投 (管理命令 / 定时), after 在区域线程回调 */
    public void dropNow(Runnable after) {
        if (plugin.worlds().world() == null) return;
        String rname = plugin.getConfig().getString("airdrop.rarity", "LEGENDARY");
        Rarity rarity = Rarity.parse(rname);
        if (rarity == null) rarity = Rarity.LEGENDARY;
        Rarity fr = rarity;
        plugin.boxes().spawnRandomBox(fr, true, entry -> {
            if (entry == null) return;
            if (plugin.getConfig().getBoolean("boxes.broadcast-airdrop", true)) {
                Bukkit.getGlobalRegionScheduler().execute(plugin, () ->
                        Bukkit.broadcast(plugin.component("airdrop-placed",
                                "{world}", plugin.worlds().world() != null ? plugin.worlds().world().getName() : "?",
                                "{x}", String.valueOf(entry.x),
                                "{z}", String.valueOf(entry.z))));
            }
            if (after != null) after.run();
        });
    }

    public long secondsUntilNext() {
        return Math.max(0, (nextAt.get() - System.currentTimeMillis()) / 1000);
    }
}
