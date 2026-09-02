package com.terrabox;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.UUID;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 毒圈 (Storm / 缩圈) 系统 —— 吃鸡式安全区
 *
 * 玩法: 对局开始后整张地图都是安全区; 每隔一段时间安全区收缩,
 *       圈外 (毒圈内) 的玩家持续受到递增伤害, 迫使玩家向圈内靠拢, 加速决战。
 *
 * 状态模型:
 *  - phase            当前阶段 (0 = 初始满图, 每收缩一次 +1)
 *  - cur 中心/半径      当前 (或正在收缩的) 安全区
 *  - target 中心/半径   本阶段的目标安全区
 *  - waitUntil       当前圈停留结束时间 (开始收缩时刻)
 *  - shrinkUntil     收缩完成时刻 (从 waitUntil 起 shrink-duration 后)
 *  - 收缩过程中圈外判定: 以 cur 半径插值到 target 的实时半径为准 (等比向圆心收缩)
 *
 * 线程模型 (白皮书 §4/§5):
 *  - 圈状态字段全部 volatile + 快照读取, 任意线程安全
 *  - GlobalRegionScheduler 定时 tick (缩圈阶段推进 / 圈外伤害判定)
 *  - 圈外伤害在玩家区域线程施加 (Player#damage 线程安全, Folia 允许直接调用)
 *  - BossBar/actionbar 提示经纯发包 API 线程安全
 */
public class ZoneManager {
    private final TerraBoxPlugin plugin;
    private final GameManager game;

    // 圈状态 (volatile, 任意线程可快照读)
    private volatile double curX, curZ, curR;       // 当前安全区 (收缩中为插值实时圆)
    private volatile double tgtX, tgtZ, tgtR;       // 目标安全区
    private volatile int phase;                      // 当前阶段
    private volatile long waitUntil;                 // 当前圈停留结束时间 (开始收缩)
    private volatile long shrinkUntil;               // 收缩完成时刻
    private volatile boolean shrinking;              // 是否正在收缩
    private volatile boolean active;                 // 毒圈是否激活 (对局运行中)

    private ScheduledTask tickTask;
    private ScheduledTask particleTask;
    private final AtomicBoolean running = new AtomicBoolean(false);
    // 缓存每个存活玩家的毒圈距离文本 (由玩家区域线程更新, 供 ScoreboardManager 合并到 ActionBar, 避免多系统抢 ActionBar)
    private final Map<UUID, String> distCache = new java.util.concurrent.ConcurrentHashMap<>();

    /** 读取指定玩家的毒圈距离文本 (任意线程; 无则空) */
    public String distanceText(UUID u) { return distCache.getOrDefault(u, ""); }

    public ZoneManager(TerraBoxPlugin plugin, GameManager game) {
        this.plugin = plugin;
        this.game = game;
    }

    // ==================== 配置读取 (任意线程) ====================

    private boolean enabled() { return plugin.getConfig().getBoolean("storm.enabled", true); }
    private int phases() { return Math.max(1, plugin.getConfig().getInt("storm.phases", 5)); }
    private double shrinkFactor() {
        return Math.max(0.05, Math.min(1.0, plugin.getConfig().getDouble("storm.shrink-factor", 0.6)));
    }
    private long waitSeconds() {
        return Math.max(5, plugin.getConfig().getLong("storm.wait-seconds", 60));
    }
    private long shrinkSeconds() {
        return Math.max(5, plugin.getConfig().getLong("storm.shrink-duration-seconds", 40));
    }
    private double baseDamage() {
        return Math.max(0.0, plugin.getConfig().getDouble("storm.damage-per-second", 1.0));
    }
    private double damageStepPerPhase() {
        return plugin.getConfig().getDouble("storm.damage-increase-per-phase", 1.0);
    }

    // ==================== 粒子配置 (任意线程) ====================

    private boolean particlesEnabled() {
        return plugin.getConfig().getBoolean("storm.particles-enabled", true);
    }
    private int particleIntervalTicks() {
        return Math.max(1, plugin.getConfig().getInt("storm.particles-interval-ticks", 4));
    }
    private int boundaryDensity() {
        return Math.max(1, plugin.getConfig().getInt("storm.particles-boundary-density", 3));
    }
    private double boundaryViewRange() {
        return Math.max(10, plugin.getConfig().getDouble("storm.particles-view-range", 48));
    }
    private boolean boundaryParticlesEnabled() {
        return plugin.getConfig().getBoolean("storm.particles-boundary-enabled", true);
    }
    private boolean fogParticlesEnabled() {
        return plugin.getConfig().getBoolean("storm.particles-fog-enabled", true);
    }
    private double fogDensity() {
        return Math.max(1, plugin.getConfig().getDouble("storm.particles-fog-density", 1.0));
    }

    // ==================== 生命周期 ====================

    /** 对局开始 (RUNNING) 时启动毒圈 */
    public void start() {
        if (!enabled()) return;
        World w = game.roomWorld();
        if (w == null) return;
        stop(); // 防重复 (上一局未清理干净)
        running.set(true);

        // 初始安全区 = 整张地图 (半径取地图边界的一半, 略缩一丁点保证圈在场内)
        double worldR = borderRadius(w);
        double initFactor = Math.max(0.2, Math.min(1.0,
                plugin.getConfig().getDouble("storm.initial-radius-factor", 0.95)));
        curX = 0; curZ = 0; curR = worldR * initFactor;
        tgtX = curX; tgtZ = curZ; tgtR = curR;
        phase = 0;
        shrinking = false;
        active = true;

        long wait = waitSeconds() * 1000L;
        waitUntil = System.currentTimeMillis() + wait;
        shrinkUntil = waitUntil;

        plugin.getLogger().info("[" + game.roomId() + "] 毒圈已启动: 初始半径 " + (int) curR
                + ", 每阶段收缩 x" + shrinkFactor() + ", 共 " + phases() + " 阶段");
        broadcast(plugin.raw("storm-start").replace("{phase}", "1")
                .replace("{seconds}", String.valueOf(waitSeconds())));

        tickTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> tick(), 20L, 20L);

        // 毒圈粒子特效任务: 更高频率独立运行 (每 particle-interval-ticks tick 刷新一次边界/毒雾)
        if (particlesEnabled()) {
            long interval = Math.max(1, particleIntervalTicks());
            particleTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin,
                    t -> spawnParticles(w), interval, interval);
        }
    }

    /** 对局结束/重置时停止毒圈 */
    public void stop() {
        active = false;
        if (tickTask != null) { tickTask.cancel(); tickTask = null; }
        if (particleTask != null) { particleTask.cancel(); particleTask = null; }
        running.set(false);
        distCache.clear();
    }

    public boolean isActive() { return active; }
    public int phase() { return phase; }
    public boolean shrinking() { return shrinking; }

    // ==================== 主循环 (Global 线程) ====================

    private void tick() {
        if (!active || !running.get()) return;
        World w = game.roomWorld();
        if (w == null) return;
        long now = System.currentTimeMillis();

        if (!shrinking) {
            // 停留期: 到点开始收缩
            if (now >= waitUntil) {
                if (phase >= phases()) {
                    // 已到最后阶段, 保持圈最小不再收缩 (终局圈), 持续高压
                    applyDamage(w, now);
                    return;
                }
                beginShrink(w, now);
            }
        } else {
            // 收缩期: 更新实时圆, 到点完成收缩 → 下一阶段开始等待
            double t = shrinkProgress(now);
            curR = lerp(prevR, tgtR, t); // prevR 见 beginShrink 设置
            curX = lerp(prevX, tgtX, t);
            curZ = lerp(prevZ, tgtZ, t);
            if (now >= shrinkUntil) {
                shrinking = false;
                phase++;
                long wait = waitSeconds() * 1000L;
                waitUntil = now + wait;
                shrinkUntil = waitUntil;
                broadcast(plugin.raw("storm-shrink-done").replace("{phase}", String.valueOf(phase)));
            }
        }
        applyDamage(w, now);
        showDistance(w, now); // 更新距离文本缓存 (供计分板合并显示, 自身不发ActionBar)
    }

    // 上一圈状态 (收缩起点), 用于插值
    private volatile double prevX, prevZ, prevR;

    private void beginShrink(World w, long now) {
        // 目标圈: 圆心在旧圈内随机偏移 (不超过旧圈半径 - 新圈半径, 保证新圈含于旧圈)
        double newR = Math.max(6.0, curR * shrinkFactor());
        double maxOff = Math.max(0, curR - newR);
        double ang = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
        double off = ThreadLocalRandom.current().nextDouble(maxOff);
        double nx = curX + Math.cos(ang) * off;
        double nz = curZ + Math.sin(ang) * off;

        prevX = curX; prevZ = curZ; prevR = curR;
        tgtX = nx; tgtZ = nz; tgtR = newR;
        shrinking = true;
        waitUntil = now;
        shrinkUntil = now + shrinkSeconds() * 1000L;

        broadcast(plugin.raw("storm-shrink").replace("{phase}", String.valueOf(phase + 1))
                .replace("{seconds}", String.valueOf(shrinkSeconds())));
    }

    private double shrinkProgress(long now) {
        long dur = Math.max(1, shrinkUntil - waitUntil);
        long el = now - waitUntil;
        return Math.max(0.0, Math.min(1.0, (double) el / dur));
    }

    // ==================== 圈外伤害 (Global 线程判定, 玩家区域线程施伤) ====================

    private void applyDamage(World w, long now) {
        if (!active) return;
        double dmg = damagePerSecond();
        // 对局内存活玩家逐一判定
        for (UUID u : game.inGamePlayers()) {
            Player p = Bukkit.getPlayer(u);
            if (p == null || !p.isOnline()) continue;
            if (game.isEliminated(u)) continue; // 淘汰玩家不再吃毒
            if (!p.getWorld().equals(w)) continue;
            Location loc = p.getLocation();
            if (insideSafeZone(loc.getX(), loc.getZ())) {
                // 在圈内: 提示不再掉血
                continue;
            }
            // 圈外: 施加伤害 (玩家区域线程); 方位/距离提示统一由 showDistance 显示
            p.getScheduler().run(plugin, task -> {
                try {
                    p.damage(dmg);
                } catch (Throwable ignored) {}
            }, () -> {});
        }
    }

    /**
     * 每个存活玩家实时显示安全区方位指引 (ActionBar):
     *  - 圈内: 圆心/半径 + 距边界距离 + 距下一次收缩倒计时
     *  - 圈外: 圆心方向 (罗盘方位) + 需移动距离 + 目标方位角
     * 保证玩家随时知道"安全区在哪、多远、往哪走" (Global 线程判定, 玩家区域线程发包)
     */
    /** 每个存活玩家实时计算毒圈方位/距离文本, 存入 distCache (供 ScoreboardManager 合并到 ActionBar) */
    private void showDistance(World w, long now) {
        if (!active) return;
        for (UUID u : game.inGamePlayers()) {
            Player p = Bukkit.getPlayer(u);
            if (p == null || !p.isOnline()) continue;
            if (game.isEliminated(u)) continue;
            if (!p.getWorld().equals(w)) continue;
            final Player fp = p;
            fp.getScheduler().run(plugin, task -> {
                try {
                    Location loc = fp.getLocation();
                    double px = loc.getX(), pz = loc.getZ();
                    double dx = px - curX, dz = pz - curZ;
                    double distCenter = Math.sqrt(dx * dx + dz * dz);
                    boolean inside = distCenter <= curR;

                    // 距安全区边界距离: 圈内 = R - distCenter(还剩多少余量), 圈外 = distCenter - R(需走多远)
                    double edgeDist = Math.abs(distCenter - curR);
                    // 方向: 圈外需朝圆心移动; 圈内给圆心方位 (配合罗盘)
                    String dir = compass(dx, dz);

                    StringBuilder sb = new StringBuilder();
                    sb.append("│ §8[§b毒圈§8] §7第§e").append(phase + 1).append("§7/").append(phases())
                            .append("§7 §7圆心(§a").append((int) curX).append("§7,§a").append((int) curZ).append("§7) ")
                            .append("§7半径§a").append((int) curR);
                    if (inside) {
                        sb.append(" §7你在§a圈内§7 距边界§a").append((int) edgeDist).append("§7格");
                    } else {
                        sb.append(" §c圈外! §7朝§e").append(dir)
                                .append("§7走§c").append((int) edgeDist).append("§7格入圈");
                    }
                    if (shrinking) {
                        long remain = Math.max(0, (shrinkUntil - now) / 1000);
                        sb.append(" §7| §6收缩中§e").append(remain).append("§7s");
                    } else if (phase < phases()) {
                        long remain = remainingSeconds();
                        sb.append(" §7| §e").append(remain).append("§7s后收缩");
                    }
                    distCache.put(fp.getUniqueId(), sb.toString());
                } catch (Throwable ignored) {}
            }, () -> {});
        }
    }

    /** 玩家相对于安全区圆心的罗盘方位 (北=自圆心看玩家? 返回玩家应走向的方向). 基于向量 (dx,dz)=玩家-圆心, 给出玩家相对圆心的方位 */
    private String compass(double dx, double dz) {
        // 需朝圆心走 → 方向 = -(dx,dz)。此处返回玩家到圆心的方位角对应名称
        double tx = -dx, tz = -dz; // 朝圆心向量
        String[] names = {"北", "东北", "东", "东南", "南", "西南", "西", "西北"};
        double deg = Math.toDegrees(Math.atan2(tx, -tz));
        deg = (deg + 360) % 360;
        int idx = (int) Math.floor((deg + 22.5) / 45.0) % 8;
        return names[idx] + "§7(§e" + (int) deg + "°§7)";
    }

    // ==================== 毒圈粒子特效 (Global 线程判定, 玩家区域线程发包) ====================

    /**
     * 为每个对局内存活玩家生成毒圈视觉:
     *  - 边界线: 沿当前安全区圆边界, 在玩家附近弧段生成一圈绿色光点 (勾勒圈轮廓)
     *  - 毒雾:   圈外玩家周围飘散绿色毒雾粒子, 强化"身处毒圈"的危险感
     * 采用 Player#spawnParticle 纯发包 (任意线程安全), 仅发给个别玩家, 性能可控。
     * 由独立 particleTask 高频调度 (每 particle-interval-ticks tick 一次)。
     */
    private void spawnParticles(World w) {
        if (!particlesEnabled() || !active) return;

        for (UUID u : game.inGamePlayers()) {
            Player p = Bukkit.getPlayer(u);
            if (p == null || !p.isOnline()) continue;
            if (game.isEliminated(u)) continue;
            if (!p.getWorld().equals(w)) continue;
            final Player fp = p;
            // 在玩家区域线程发包 (Folia 合规: Player#spawnParticle 只发客户端包, 不读世界状态)
            fp.getScheduler().run(plugin, task -> {
                try {
                    Location ploc = fp.getLocation();
                    double px = ploc.getX(), pz = ploc.getZ();
                    double py = ploc.getY();
                    boolean outside = !insideSafeZone(px, pz);
                    if (boundaryParticlesEnabled()) spawnBoundary(fp, px, pz, py);
                    if (outside && fogParticlesEnabled()) spawnFog(fp, px, pz, py);
                } catch (Throwable ignored) {}
            }, () -> {});
        }
    }

    /**
     * 沿安全区圆边界, 在玩家附近 (within viewRange) 的弧段生成绿色光点。
     * 只给该玩家看 (单人发包), 圈边界距离玩家近的部分清晰勾勒, 远处不刷避免浪费。
     * 收缩时粒子略微向内收缩流动, 提示圈在缩小。
     */
    private void spawnBoundary(Player p, double px, double pz, double py) {
        double r = curR;
        if (r <= 2) return;
        double view = boundaryViewRange();
        double viewSq = view * view;
        int density = boundaryDensity();
        // 采样间隔角度: 使弧长约 6~8 格 (大圆采样点少, 小圆采样点多)
        double step = Math.max(0.02, 7.0 / Math.max(1, r));
        // 玩家到圆心方向为中心, 开一个角度窗口 (只生成玩家附近的弧段)
        double dxc = px - curX, dzc = pz - curZ;
        double centerAng = Math.atan2(dzc, dxc);
        double winDeg = Math.min(Math.PI, (view / Math.max(1, r)) * 2.2 + 0.15);
        double ang0 = centerAng - winDeg;
        double angEnd = centerAng + winDeg;

        // 地表高度近似: 用玩家 y 做基准抬高显示 (避免跨区块读高, 保 Folia 合规)
        double baseY = py + 2.0;
        // 收缩时粒子轻微向内偏移, 增强"收缩"流动感
        double shrinkPull = shrinking ? Math.min(3.0, (r - tgtR) * 0.06) : 0;
        boolean doShrink = shrinkPull > 0.01;

        for (double a = ang0; a < angEnd; a += step) {
            double x = curX + Math.cos(a) * r;
            double z = curZ + Math.sin(a) * r;
            double ddx = x - px, ddz = z - pz;
            if (ddx * ddx + ddz * ddz > viewSq) continue; // 仅保留玩家附近弧段
            double offR = r - shrinkPull;
            double ox = curX + Math.cos(a) * offR;
            double oz = curZ + Math.sin(a) * offR;
            // 轻微上下浮动, 让边界更生动
            double oy = baseY + Math.sin(a * 3.0 + System.currentTimeMillis() * 0.002) * 0.5;
            for (int i = 0; i < density; i++) {
                double jitterY = (i - (density - 1) / 2.0) * 0.5;
                try {
                    p.spawnParticle(Particle.DUST, ox, oy + jitterY, oz, 1, 0.12, 0.12, 0.12, 0,
                            new Particle.DustOptions(org.bukkit.Color.LIME, 2.2f));
                } catch (Throwable ignore) {}
            }
        }
    }

    /**
     * 圈外毒雾: 玩家周围飘散绿色毒雾粒子 (NOXIOUS_GAS), 提示身处毒圈。
     * 毒雾浓度随阶段提升 (圈越靠后越浓).
     */
    private void spawnFog(Player p, double px, double pz, double py) {
        double dens = fogDensity() * (1.0 + phase * 0.5);
        int count = (int) Math.min(8, Math.max(2, dens));
        for (int i = 0; i < count; i++) {
            double ox = px + ThreadLocalRandom.current().nextDouble(-1.6, 1.6);
            double oz = pz + ThreadLocalRandom.current().nextDouble(-1.6, 1.6);
            double oy = py + ThreadLocalRandom.current().nextDouble(0.2, 2.6);
            try {
                p.spawnParticle(Particle.NOXIOUS_GAS, ox, oy, oz, 1, 0.3, 0.3, 0.3, 0.02);
                if (phase >= 2) {
                    p.spawnParticle(Particle.SMOKE, ox, oy, oz, 1, 0.2, 0.2, 0.2, 0.01);
                }
            } catch (Throwable ignore) {}
        }
    }

    /** 当前每秒伤害 (按阶段递增) */
    private double damagePerSecond() {
        if (!enabled()) return 0;
        return baseDamage() + phase * damageStepPerPhase();
    }

    /** 判点是否在安全区内 (水平圆) */
    private boolean insideSafeZone(double x, double z) {
        double dx = x - curX, dz = z - curZ;
        return (dx * dx + dz * dz) <= curR * curR;
    }

    // ==================== 工具 ====================

    /** 全服广播某条 & 码消息 (转换后 broadcast Component; Folia 全局安全) */
    private void broadcast(String raw) {
        try {
            Bukkit.broadcast(net.kyori.adventure.text.serializer.legacy
                    .LegacyComponentSerializer.legacyAmpersand().deserialize(raw));
        } catch (Throwable t) {
            plugin.getLogger().warning("毒圈广播失败: " + "错误";
        }
    }

    /** 对局世界有效半径 (取世界边界的一半, 兜底 512) */
    private double borderRadius(World w) {
        try {
            double size = w.getWorldBorder().getSize();
            if (size <= 0) size = 1024;
            return size / 2.0 - 8.0; // 留边距
        } catch (Throwable t) {
            return 504.0;
        }
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    // ==================== 调试/管理 ====================

    /** 当前安全区快照 (供 /box storm status) */
    public String status() {
        if (!active) return "§7未激活";
        return "§e第 " + (phase + 1) + "§e/" + phases() + " 阶段 | 圆心 (§b"
                + (int) curX + "§e, §b" + (int) curZ + "§e) | 半径 §a" + (int) curR
                + " | 每秒伤害 §c" + String.format("%.1f", damagePerSecond())
                + (shrinking ? " | §6收缩中..." : " | §e停留 " + remainingSeconds() + " 秒");
    }

    private long remainingSeconds() {
        long rem = (waitUntil - System.currentTimeMillis()) / 1000;
        return Math.max(0, rem);
    }

    /** 画圈粒子提示 (供管理员调试查看安全区边界) */
    public void showRing(World w) {
        if (!active || w == null) return;
        int steps = 64;
        try {
            for (int i = 0; i < steps; i++) {
                double a = i * Math.PI * 2 / steps;
                double x = curX + Math.cos(a) * curR;
                double z = curZ + Math.sin(a) * curR;
                int y = Math.max(w.getMinHeight() + 1, w.getHighestBlockYAt((int) x, (int) z) + 3);
                Location loc = new Location(w, x, y, z);
                w.spawnParticle(Particle.END_ROD, loc, 2, 0.0, 0.0, 0.0, 0.0);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("毒圈边界粒子显示失败: " + "错误";
        }
    }
}
