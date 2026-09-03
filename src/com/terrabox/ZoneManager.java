/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.papermc.paper.threadedregions.scheduler.ScheduledTask
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
 *  org.bukkit.Bukkit
 *  org.bukkit.Color
 *  org.bukkit.Location
 *  org.bukkit.Particle
 *  org.bukkit.Particle$DustOptions
 *  org.bukkit.World
 *  org.bukkit.entity.Player
 *  org.bukkit.plugin.Plugin
 */
package com.terrabox;

import com.terrabox.GameManager;
import com.terrabox.TerraBoxPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class ZoneManager {
    private final TerraBoxPlugin plugin;
    private final GameManager game;
    private volatile double curX;
    private volatile double curZ;
    private volatile double curR;
    private volatile double tgtX;
    private volatile double tgtZ;
    private volatile double tgtR;
    private volatile int phase;
    private volatile long waitUntil;
    private volatile long shrinkUntil;
    private volatile boolean shrinking;
    private volatile boolean active;
    private ScheduledTask tickTask;
    private ScheduledTask particleTask;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Map<UUID, String> distCache = new ConcurrentHashMap<UUID, String>();
    private volatile double prevX;
    private volatile double prevZ;
    private volatile double prevR;

    public String distanceText(UUID uUID) {
        return this.distCache.getOrDefault(uUID, "");
    }

    public ZoneManager(TerraBoxPlugin terraBoxPlugin, GameManager gameManager) {
        this.plugin = terraBoxPlugin;
        this.game = gameManager;
    }

    private boolean enabled() {
        return this.plugin.getConfig().getBoolean("storm.enabled", true);
    }

    private int phases() {
        return Math.max(1, this.plugin.getConfig().getInt("storm.phases", 5));
    }

    private double shrinkFactor() {
        return Math.max(0.05, Math.min(1.0, this.plugin.getConfig().getDouble("storm.shrink-factor", 0.6)));
    }

    private long waitSeconds() {
        return Math.max(5L, this.plugin.getConfig().getLong("storm.wait-seconds", 60L));
    }

    private long shrinkSeconds() {
        return Math.max(5L, this.plugin.getConfig().getLong("storm.shrink-duration-seconds", 40L));
    }

    private double baseDamage() {
        return Math.max(0.0, this.plugin.getConfig().getDouble("storm.damage-per-second", 1.0));
    }

    private double damageStepPerPhase() {
        return this.plugin.getConfig().getDouble("storm.damage-increase-per-phase", 1.0);
    }

    private boolean particlesEnabled() {
        return this.plugin.getConfig().getBoolean("storm.particles-enabled", true);
    }

    private int particleIntervalTicks() {
        return Math.max(1, this.plugin.getConfig().getInt("storm.particles-interval-ticks", 4));
    }

    private int boundaryDensity() {
        return Math.max(1, this.plugin.getConfig().getInt("storm.particles-boundary-density", 3));
    }

    private double boundaryViewRange() {
        return Math.max(10.0, this.plugin.getConfig().getDouble("storm.particles-view-range", 48.0));
    }

    private boolean boundaryParticlesEnabled() {
        return this.plugin.getConfig().getBoolean("storm.particles-boundary-enabled", true);
    }

    private boolean fogParticlesEnabled() {
        return this.plugin.getConfig().getBoolean("storm.particles-fog-enabled", true);
    }

    private double fogDensity() {
        return Math.max(1.0, this.plugin.getConfig().getDouble("storm.particles-fog-density", 1.0));
    }

    public void start() {
        if (!this.enabled()) {
            return;
        }
        World world = this.game.roomWorld();
        if (world == null) {
            return;
        }
        this.stop();
        this.running.set(true);
        double d = this.borderRadius(world);
        double d2 = Math.max(0.2, Math.min(1.0, this.plugin.getConfig().getDouble("storm.initial-radius-factor", 0.95)));
        this.curX = 0.0;
        this.curZ = 0.0;
        this.curR = d * d2;
        this.tgtX = this.curX;
        this.tgtZ = this.curZ;
        this.tgtR = this.curR;
        this.phase = 0;
        this.shrinking = false;
        this.active = true;
        long l = this.waitSeconds() * 1000L;
        this.shrinkUntil = this.waitUntil = System.currentTimeMillis() + l;
        this.plugin.getLogger().info("[" + this.game.roomId() + "] \u6bd2\u5708\u5df2\u542f\u52a8: \u521d\u59cb\u534a\u5f84 " + (int)this.curR + ", \u6bcf\u9636\u6bb5\u6536\u7f29 x" + this.shrinkFactor() + ", \u5171 " + this.phases() + " \u9636\u6bb5");
        this.broadcast(this.plugin.raw("storm-start").replace("{phase}", "1").replace("{seconds}", String.valueOf(this.waitSeconds())));
        this.tickTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate((Plugin)this.plugin, scheduledTask -> this.tick(), 20L, 20L);
        if (this.particlesEnabled()) {
            long l2 = Math.max(1, this.particleIntervalTicks());
            this.particleTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate((Plugin)this.plugin, scheduledTask -> this.spawnParticles(world), l2, l2);
        }
    }

    public void stop() {
        this.active = false;
        if (this.tickTask != null) {
            this.tickTask.cancel();
            this.tickTask = null;
        }
        if (this.particleTask != null) {
            this.particleTask.cancel();
            this.particleTask = null;
        }
        this.running.set(false);
        this.distCache.clear();
    }

    public boolean isActive() {
        return this.active;
    }

    public int phase() {
        return this.phase;
    }

    public boolean shrinking() {
        return this.shrinking;
    }

    private void tick() {
        if (!this.active || !this.running.get()) {
            return;
        }
        World world = this.game.roomWorld();
        if (world == null) {
            return;
        }
        long l = System.currentTimeMillis();
        if (!this.shrinking) {
            if (l >= this.waitUntil) {
                if (this.phase >= this.phases()) {
                    this.applyDamage(world, l);
                    return;
                }
                this.beginShrink(world, l);
            }
        } else {
            double d = this.shrinkProgress(l);
            this.curR = ZoneManager.lerp(this.prevR, this.tgtR, d);
            this.curX = ZoneManager.lerp(this.prevX, this.tgtX, d);
            this.curZ = ZoneManager.lerp(this.prevZ, this.tgtZ, d);
            if (l >= this.shrinkUntil) {
                this.shrinking = false;
                ++this.phase;
                long l2 = this.waitSeconds() * 1000L;
                this.shrinkUntil = this.waitUntil = l + l2;
                this.broadcast(this.plugin.raw("storm-shrink-done").replace("{phase}", String.valueOf(this.phase)));
            }
        }
        this.applyDamage(world, l);
        this.showDistance(world, l);
    }

    private void beginShrink(World world, long l) {
        double d = Math.max(6.0, this.curR * this.shrinkFactor());
        double d2 = Math.max(0.0, this.curR - d);
        double d3 = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
        double d4 = ThreadLocalRandom.current().nextDouble(d2);
        double d5 = this.curX + Math.cos(d3) * d4;
        double d6 = this.curZ + Math.sin(d3) * d4;
        this.prevX = this.curX;
        this.prevZ = this.curZ;
        this.prevR = this.curR;
        this.tgtX = d5;
        this.tgtZ = d6;
        this.tgtR = d;
        this.shrinking = true;
        this.waitUntil = l;
        this.shrinkUntil = l + this.shrinkSeconds() * 1000L;
        this.broadcast(this.plugin.raw("storm-shrink").replace("{phase}", String.valueOf(this.phase + 1)).replace("{seconds}", String.valueOf(this.shrinkSeconds())));
    }

    private double shrinkProgress(long l) {
        long l2 = Math.max(1L, this.shrinkUntil - this.waitUntil);
        long l3 = l - this.waitUntil;
        return Math.max(0.0, Math.min(1.0, (double)l3 / (double)l2));
    }

    private void applyDamage(World world, long l) {
        if (!this.active) {
            return;
        }
        double d = this.damagePerSecond();
        for (UUID uUID : this.game.inGamePlayers()) {
            Location location;
            Player player = Bukkit.getPlayer((UUID)uUID);
            if (player == null || !player.isOnline() || this.game.isEliminated(uUID) || !player.getWorld().equals((Object)world) || this.insideSafeZone((location = player.getLocation()).getX(), location.getZ())) continue;
            player.getScheduler().run((Plugin)this.plugin, scheduledTask -> {
                try {
                    player.damage(d);
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
            }, () -> {});
        }
    }

    private void showDistance(World world, long l) {
        if (!this.active) {
            return;
        }
        for (UUID uUID : this.game.inGamePlayers()) {
            Player player = Bukkit.getPlayer((UUID)uUID);
            if (player == null || !player.isOnline() || this.game.isEliminated(uUID) || !player.getWorld().equals((Object)world)) continue;
            Player player2 = player;
            player2.getScheduler().run((Plugin)this.plugin, scheduledTask -> {
                try {
                    Location location = player2.getLocation();
                    double d = location.getX();
                    double d2 = location.getZ();
                    double d3 = d - this.curX;
                    double d4 = d2 - this.curZ;
                    double d5 = Math.sqrt(d3 * d3 + d4 * d4);
                    boolean bl = d5 <= this.curR;
                    double d6 = Math.abs(d5 - this.curR);
                    String string = this.compass(d3, d4);
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append("\u2502 \u00a78[\u00a7b\u6bd2\u5708\u00a78] \u00a77\u7b2c\u00a7e").append(this.phase + 1).append("\u00a77/").append(this.phases()).append("\u00a77 \u00a77\u5706\u5fc3(\u00a7a").append((int)this.curX).append("\u00a77,\u00a7a").append((int)this.curZ).append("\u00a77) ").append("\u00a77\u534a\u5f84\u00a7a").append((int)this.curR);
                    if (bl) {
                        stringBuilder.append(" \u00a77\u4f60\u5728\u00a7a\u5708\u5185\u00a77 \u8ddd\u8fb9\u754c\u00a7a").append((int)d6).append("\u00a77\u683c");
                    } else {
                        stringBuilder.append(" \u00a7c\u5708\u5916! \u00a77\u671d\u00a7e").append(string).append("\u00a77\u8d70\u00a7c").append((int)d6).append("\u00a77\u683c\u5165\u5708");
                    }
                    if (this.shrinking) {
                        long l2 = Math.max(0L, (this.shrinkUntil - l) / 1000L);
                        stringBuilder.append(" \u00a77| \u00a76\u6536\u7f29\u4e2d\u00a7e").append(l2).append("\u00a77s");
                    } else if (this.phase < this.phases()) {
                        long l3 = this.remainingSeconds();
                        stringBuilder.append(" \u00a77| \u00a7e").append(l3).append("\u00a77s\u540e\u6536\u7f29");
                    }
                    this.distCache.put(player2.getUniqueId(), stringBuilder.toString());
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
            }, () -> {});
        }
    }

    private String compass(double d, double d2) {
        double d3 = -d;
        double d4 = -d2;
        String[] stringArray = new String[]{"\u5317", "\u4e1c\u5317", "\u4e1c", "\u4e1c\u5357", "\u5357", "\u897f\u5357", "\u897f", "\u897f\u5317"};
        double d5 = Math.toDegrees(Math.atan2(d3, -d4));
        d5 = (d5 + 360.0) % 360.0;
        int n = (int)Math.floor((d5 + 22.5) / 45.0) % 8;
        return stringArray[n] + "\u00a77(\u00a7e" + (int)d5 + "\u00b0\u00a77)";
    }

    private void spawnParticles(World world) {
        if (!this.particlesEnabled() || !this.active) {
            return;
        }
        for (UUID uUID : this.game.inGamePlayers()) {
            Player player = Bukkit.getPlayer((UUID)uUID);
            if (player == null || !player.isOnline() || this.game.isEliminated(uUID) || !player.getWorld().equals((Object)world)) continue;
            Player player2 = player;
            player2.getScheduler().run((Plugin)this.plugin, scheduledTask -> {
                try {
                    boolean bl;
                    Location location = player2.getLocation();
                    double d = location.getX();
                    double d2 = location.getZ();
                    double d3 = location.getY();
                    boolean bl2 = bl = !this.insideSafeZone(d, d2);
                    if (this.boundaryParticlesEnabled()) {
                        this.spawnBoundary(player2, d, d2, d3);
                    }
                    if (bl && this.fogParticlesEnabled()) {
                        this.spawnFog(player2, d, d2, d3);
                    }
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
            }, () -> {});
        }
    }

    private void spawnBoundary(Player player, double d, double d2, double d3) {
        double d4 = this.curR;
        if (d4 <= 2.0) {
            return;
        }
        double d5 = this.boundaryViewRange();
        double d6 = d5 * d5;
        int n = this.boundaryDensity();
        double d7 = Math.max(0.02, 7.0 / Math.max(1.0, d4));
        double d8 = d - this.curX;
        double d9 = d2 - this.curZ;
        double d10 = Math.atan2(d9, d8);
        double d11 = Math.min(Math.PI, d5 / Math.max(1.0, d4) * 2.2 + 0.15);
        double d12 = d10 - d11;
        double d13 = d10 + d11;
        double d14 = d3 + 2.0;
        double d15 = this.shrinking ? Math.min(3.0, (d4 - this.tgtR) * 0.06) : 0.0;
        boolean bl = d15 > 0.01;
        for (double d16 = d12; d16 < d13; d16 += d7) {
            double d17;
            double d18;
            double d19 = this.curX + Math.cos(d16) * d4;
            double d20 = d19 - d;
            if (d20 * d20 + (d18 = (d17 = this.curZ + Math.sin(d16) * d4) - d2) * d18 > d6) continue;
            double d21 = d4 - d15;
            double d22 = this.curX + Math.cos(d16) * d21;
            double d23 = this.curZ + Math.sin(d16) * d21;
            double d24 = d14 + Math.sin(d16 * 3.0 + (double)System.currentTimeMillis() * 0.002) * 0.5;
            for (int i = 0; i < n; ++i) {
                double d25 = ((double)i - (double)(n - 1) / 2.0) * 0.5;
                try {
                    player.spawnParticle(Particle.DUST, d22, d24 + d25, d23, 1, 0.12, 0.12, 0.12, 0.0, (Object)new Particle.DustOptions(Color.LIME, 2.2f));
                    continue;
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
            }
        }
    }

    private void spawnFog(Player player, double d, double d2, double d3) {
        double d4 = this.fogDensity() * (1.0 + (double)this.phase * 0.5);
        int n = (int)Math.min(8.0, Math.max(2.0, d4));
        for (int i = 0; i < n; ++i) {
            double d5 = d + ThreadLocalRandom.current().nextDouble(-1.6, 1.6);
            double d6 = d2 + ThreadLocalRandom.current().nextDouble(-1.6, 1.6);
            double d7 = d3 + ThreadLocalRandom.current().nextDouble(0.2, 2.6);
            try {
                player.spawnParticle(Particle.NOXIOUS_GAS, d5, d7, d6, 1, 0.3, 0.3, 0.3, 0.02);
                if (this.phase < 2) continue;
                player.spawnParticle(Particle.SMOKE, d5, d7, d6, 1, 0.2, 0.2, 0.2, 0.01);
                continue;
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
    }

    private double damagePerSecond() {
        if (!this.enabled()) {
            return 0.0;
        }
        return this.baseDamage() + (double)this.phase * this.damageStepPerPhase();
    }

    private boolean insideSafeZone(double d, double d2) {
        double d3 = d - this.curX;
        double d4 = d2 - this.curZ;
        return d3 * d3 + d4 * d4 <= this.curR * this.curR;
    }

    private void broadcast(String string) {
        try {
            Bukkit.broadcast((Component)LegacyComponentSerializer.legacyAmpersand().deserialize(string));
        }
        catch (Throwable throwable) {
            this.plugin.getLogger().warning("\u6bd2\u5708\u5e7f\u64ad\u5931\u8d25: " + throwable.getMessage());
        }
    }

    private double borderRadius(World world) {
        try {
            double d = world.getWorldBorder().getSize();
            if (d <= 0.0) {
                d = 1024.0;
            }
            return d / 2.0 - 8.0;
        }
        catch (Throwable throwable) {
            return 504.0;
        }
    }

    private static double lerp(double d, double d2, double d3) {
        return d + (d2 - d) * d3;
    }

    public String status() {
        if (!this.active) {
            return "\u00a77\u672a\u6fc0\u6d3b";
        }
        return "\u00a7e\u7b2c " + (this.phase + 1) + "\u00a7e/" + this.phases() + " \u9636\u6bb5 | \u5706\u5fc3 (\u00a7b" + (int)this.curX + "\u00a7e, \u00a7b" + (int)this.curZ + "\u00a7e) | \u534a\u5f84 \u00a7a" + (int)this.curR + " | \u6bcf\u79d2\u4f24\u5bb3 \u00a7c" + String.format("%.1f", this.damagePerSecond()) + (String)(this.shrinking ? " | \u00a76\u6536\u7f29\u4e2d..." : " | \u00a7e\u505c\u7559 " + this.remainingSeconds() + " \u79d2");
    }

    private long remainingSeconds() {
        long l = (this.waitUntil - System.currentTimeMillis()) / 1000L;
        return Math.max(0L, l);
    }

    public void showRing(World world) {
        if (!this.active || world == null) {
            return;
        }
        int n = 64;
        try {
            for (int i = 0; i < n; ++i) {
                double d = (double)i * Math.PI * 2.0 / (double)n;
                double d2 = this.curX + Math.cos(d) * this.curR;
                double d3 = this.curZ + Math.sin(d) * this.curR;
                int n2 = Math.max(world.getMinHeight() + 1, world.getHighestBlockYAt((int)d2, (int)d3) + 3);
                Location location = new Location(world, d2, (double)n2, d3);
                world.spawnParticle(Particle.END_ROD, location, 2, 0.0, 0.0, 0.0, 0.0);
            }
        }
        catch (Throwable throwable) {
            this.plugin.getLogger().warning("\u6bd2\u5708\u8fb9\u754c\u7c92\u5b50\u663e\u793a\u5931\u8d25: " + throwable.getMessage());
        }
    }
}
