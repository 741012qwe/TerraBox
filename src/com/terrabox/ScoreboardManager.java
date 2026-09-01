package com.terrabox;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对局信息显示 (BossBar + ActionBar + Title), 实时统计多项数据
 *
 * 重要: Lophine(Folia) 26.2 全面禁用 Bukkit Scoreboard API ——
 *   CraftScoreboardManager.getNewScoreboard() 即使在 Global(主)线程调用也抛
 *   UnsupportedOperationException(实测 latest_12.log: 1356 次刷屏)。
 *   因此本类【彻底不再使用 org.bukkit.scoreboard.*】, 改走纯发包通道:
 *   - BossBar  (顶部进度条, 显示对局状态/倒计时剩余时间)
 *   - ActionBar (底部, 显示模式/存活/击杀/开箱实时数据)
 *   - Title     (屏幕中央大屏, 倒计时/开赛/结束提示)
 *
 * 线程模型: 这些 API 均通过发包实现, 在 Folia 下无需特定区域线程, 可直接在
 *   GlobalRegionScheduler 的每秒 tick 中调用, 线程安全 (BossBar 是服务端对象,
 *   通过任意线程 addPlayer/setContent 发送)。
 */
public class ScoreboardManager {
    private final TerraBoxPlugin plugin;
    private final Map<UUID, Integer> kills = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> deaths = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> boxesOpened = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> bossBars = new ConcurrentHashMap<>();
    private ScheduledTask updateTask;
    private boolean running = false;
    private volatile boolean enabled = true;
    private boolean warnLogged = false;

    public ScoreboardManager(TerraBoxPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (running) return;
        running = true;
        // 每 1 秒更新一次 (Global 线程; 纯发包 API 线程安全)
        updateTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin,
                t -> updateAll(), 20L, 20L);
    }

    public boolean isRunning() { return running; }

    public void shutdown() {
        running = false;
        if (updateTask != null) updateTask.cancel();
        for (BossBar bar : bossBars.values()) {
            try { bar.removeAll(); } catch (Throwable ignored) {}
        }
        bossBars.clear();
    }

    // ==================== 数据记录 ====================

    public void recordKill(UUID killer) { kills.merge(killer, 1, Integer::sum); }
    public void recordDeath(UUID victim) { deaths.merge(victim, 1, Integer::sum); }
    public void recordBox(UUID player) { boxesOpened.merge(player, 1, Integer::sum); }
    public void resetStats() { kills.clear(); deaths.clear(); boxesOpened.clear(); }
    public int getKills(UUID u) { return kills.getOrDefault(u, 0); }
    public int getDeaths(UUID u) { return deaths.getOrDefault(u, 0); }
    public int getBoxes(UUID u) { return boxesOpened.getOrDefault(u, 0); }

    // ==================== 对局信息显示 ====================

    private void updateAll() {
        if (!enabled) return;
        GameManager g = plugin.games();
        if (!g.isRunning() && g.state() != GameManager.State.RUNNING) {
            // 无对局: 移除所有对局 BossBar / actionbar
            for (UUID u : bossBars.keySet()) {
                removeBar(u);
            }
            return;
        }
        for (UUID u : g.inGamePlayers()) {
            Player p = Bukkit.getPlayer(u);
            if (p == null || !p.isOnline()) continue;
            try {
                apply(p, g);
            } catch (Throwable t) {
                // 纯发包极少失败; 万一失败只警告一次, 绝不刷屏
                if (!warnLogged) {
                    warnLogged = true;
                    plugin.getLogger().warning("对局信息显示更新失败: "
                            + t.getClass().getSimpleName() + " - " + t.getMessage());
                }
                return;
            }
        }
    }

    /** 对单个玩家更新 BossBar + ActionBar (任意线程可安全调用) */
    private void apply(Player p, GameManager g) {
        UUID uuid = p.getUniqueId();
        boolean eliminated = g.isEliminated(uuid);
        // ===== BossBar: 顶部对局状态 + 倒计时进度 =====
        BossBar bar = bossBars.get(uuid);
        if (bar == null) {
            bar = Bukkit.getServer().createBossBar("", BarColor.BLUE, BarStyle.SOLID);
            bar.addPlayer(p);
            bossBars.put(uuid, bar);
        }
        String title;
        double progress;
        if (g.state() == GameManager.State.COUNTDOWN) {
            int left = g.countdownLeft();
            title = "§6§l对局即将开始 §e" + left + " §l秒";
            int total = Math.max(1, plugin.getConfig().getInt("game.countdown-seconds", 30));
            progress = Math.max(0.0, Math.min(1.0, (double) left / total));
            bar.setColor(BarColor.YELLOW);
        } else if (g.state() == GameManager.State.RUNNING) {
            long remain = Math.max(0, (g.endAtMs() - System.currentTimeMillis()) / 1000);
            String remainText = formatTime(remain);
            long totalMs = plugin.getConfig().getLong("game.duration-minutes", 30) * 60_000L;
            if (totalMs <= 0) totalMs = 30 * 60_000L;
            progress = Math.max(0.0, Math.min(1.0, (double) remain / (totalMs / 1000.0)));
            if (g.mode() == GameManager.Mode.SOLO) {
                title = "§a对局进行中 §7| §e剩余 §b" + remainText
                        + " §7| §a存活 " + g.aliveCount() + "§7/§a" + g.playerCount();
            } else {
                title = "§c对局进行中 §7| §e击杀 §c" + getKills(uuid)
                        + " §7| §e死亡 " + getDeaths(uuid)
                        + " §7| §a存活 " + g.aliveCount() + "§7/§a" + g.playerCount();
            }
            bar.setColor(g.mode() == GameManager.Mode.SOLO ? BarColor.BLUE : BarColor.RED);
        } else {
            title = "§d对局结算中...";
            progress = 0.0;
            bar.setColor(BarColor.PURPLE);
        }
        // 追加追踪方向/距离到同一条 BossBar (存活玩家在追踪时显示, 避免多条 BossBar 叠加覆盖)
        if (!eliminated && plugin.specialItems() != null) {
            String track = plugin.specialItems().trackingText(uuid);
            if (track != null && !track.isEmpty()) {
                title = title + "  §7│  " + track;
            }
        }
        bar.setTitle(title);
        bar.setProgress(progress);
        bar.setVisible(true);

        // ===== ActionBar: 底部实时数据 (若淘汰显示观战提示) =====
        String action;
        if (eliminated) {
            action = "§c你已淘汰 §7| §a存活 " + g.aliveCount() + "§7/§a" + g.playerCount()
                    + " §7| §e/box lobby 回大厅 §7或 §e/box spectate 旁观";
        } else if (g.state() == GameManager.State.RUNNING) {
            action = "§7模式: §" + (g.mode() == GameManager.Mode.SOLO ? "a" : "c")
                    + g.mode().display + " §7| §e开箱 §a" + getBoxes(uuid)
                    + " §7| §e击杀 §c" + getKills(uuid)
                    + " §7| §e死亡 " + getDeaths(uuid);
        } else if (g.state() == GameManager.State.COUNTDOWN) {
            action = "§e距离开赛 §b" + g.countdownLeft() + " §e秒 §7| §e报名 " + g.playerCount() + " 人";
        } else {
            action = "§d结算中...";
        }
        // 附加毒圈距离文本 (缓存自 ZoneManager, 避免多系统抢 ActionBar 互相覆盖)
        if (!eliminated && g.storm() != null && g.storm().isActive()) {
            String dist = g.storm().distanceText(uuid);
            if (dist != null && !dist.isEmpty()) action = action + " " + dist;
        }
        try { p.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacyAmpersand().deserialize(action)); } catch (Throwable ignored) {}
    }

    /** 屏幕中央大屏提示 (Title): 倒计时/开赛/结束/淘汰等 一次性事件用 */
    public void showTitle(Player p, String main, String sub) {
        if (p == null || !p.isOnline()) return;
        try {
            p.sendTitle(main == null ? "" : main, sub == null ? "" : sub, 5, 25, 10);
        } catch (Throwable ignored) {}
    }

    /** 移除单个玩家的 BossBar + 清空 actionbar */
    public void clearPlayer(UUID u) {
        removeBar(u);
        Player p = Bukkit.getPlayer(u);
        if (p != null && p.isOnline()) {
            try { p.sendActionBar(net.kyori.adventure.text.Component.empty()); } catch (Throwable ignored) {}
        }
    }

    private void removeBar(UUID u) {
        BossBar bar = bossBars.remove(u);
        if (bar != null) {
            try { bar.removeAll(); } catch (Throwable ignored) {}
        }
    }

    public void clearAll() {
        for (UUID u : bossBars.keySet()) removeBar(u);
        for (Player p : Bukkit.getOnlinePlayers()) {
            try { p.sendActionBar(net.kyori.adventure.text.Component.empty()); } catch (Throwable ignored) {}
        }
    }

    private static String formatTime(long sec) {
        long m = sec / 60, s = sec % 60;
        return m + ":" + (s < 10 ? "0" : "") + s;
    }
}
