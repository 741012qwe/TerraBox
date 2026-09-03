/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.papermc.paper.threadedregions.scheduler.ScheduledTask
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
 *  org.bukkit.Bukkit
 *  org.bukkit.boss.BarColor
 *  org.bukkit.boss.BarFlag
 *  org.bukkit.boss.BarStyle
 *  org.bukkit.boss.BossBar
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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class ScoreboardManager {
    private final TerraBoxPlugin plugin;
    private final Map<UUID, Integer> kills = new ConcurrentHashMap<UUID, Integer>();
    private final Map<UUID, Integer> deaths = new ConcurrentHashMap<UUID, Integer>();
    private final Map<UUID, Integer> boxesOpened = new ConcurrentHashMap<UUID, Integer>();
    private final Map<UUID, BossBar> bossBars = new ConcurrentHashMap<UUID, BossBar>();
    private ScheduledTask updateTask;
    private boolean running = false;
    private volatile boolean enabled = true;
    private boolean warnLogged = false;

    public ScoreboardManager(TerraBoxPlugin terraBoxPlugin) {
        this.plugin = terraBoxPlugin;
    }

    public void start() {
        if (this.running) {
            return;
        }
        this.running = true;
        this.updateTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate((Plugin)this.plugin, scheduledTask -> this.updateAll(), 20L, 20L);
    }

    public boolean isRunning() {
        return this.running;
    }

    public void shutdown() {
        this.running = false;
        if (this.updateTask != null) {
            this.updateTask.cancel();
        }
        for (BossBar bossBar : this.bossBars.values()) {
            try {
                bossBar.removeAll();
            }
            catch (Throwable throwable) {}
        }
        this.bossBars.clear();
    }

    public void recordKill(UUID uUID) {
        this.kills.merge(uUID, 1, Integer::sum);
    }

    public void recordDeath(UUID uUID) {
        this.deaths.merge(uUID, 1, Integer::sum);
    }

    public void recordBox(UUID uUID) {
        this.boxesOpened.merge(uUID, 1, Integer::sum);
    }

    public void resetStats() {
        this.kills.clear();
        this.deaths.clear();
        this.boxesOpened.clear();
    }

    public int getKills(UUID uUID) {
        return this.kills.getOrDefault(uUID, 0);
    }

    public int getDeaths(UUID uUID) {
        return this.deaths.getOrDefault(uUID, 0);
    }

    public int getBoxes(UUID uUID) {
        return this.boxesOpened.getOrDefault(uUID, 0);
    }

    private void updateAll() {
        if (!this.enabled) {
            return;
        }
        GameManager gameManager = this.plugin.games();
        if (!gameManager.isRunning() && gameManager.state() != GameManager.State.RUNNING) {
            for (UUID uUID : this.bossBars.keySet()) {
                this.removeBar(uUID);
            }
            return;
        }
        for (UUID uUID : gameManager.inGamePlayers()) {
            Player player = Bukkit.getPlayer((UUID)uUID);
            if (player == null || !player.isOnline()) continue;
            try {
                this.apply(player, gameManager);
            }
            catch (Throwable throwable) {
                if (!this.warnLogged) {
                    this.warnLogged = true;
                    this.plugin.getLogger().warning("\u5bf9\u5c40\u4fe1\u606f\u663e\u793a\u66f4\u65b0\u5931\u8d25: " + throwable.getClass().getSimpleName() + " - " + throwable.getMessage());
                }
                return;
            }
        }
    }

    private void apply(Player player, GameManager gameManager) {
        String string;
        String string2;
        double d;
        Object object;
        UUID uUID = player.getUniqueId();
        boolean bl = gameManager.isEliminated(uUID);
        BossBar bossBar = this.bossBars.get(uUID);
        if (bossBar == null) {
            bossBar = Bukkit.getServer().createBossBar("", BarColor.BLUE, BarStyle.SOLID, new BarFlag[0]);
            bossBar.addPlayer(player);
            this.bossBars.put(uUID, bossBar);
        }
        if (gameManager.state() == GameManager.State.COUNTDOWN) {
            int n = gameManager.countdownLeft();
            object = "\u00a76\u00a7l\u5bf9\u5c40\u5373\u5c06\u5f00\u59cb \u00a7e" + n + " \u00a7l\u79d2";
            int n2 = Math.max(1, this.plugin.getConfig().getInt("game.countdown-seconds", 30));
            d = Math.max(0.0, Math.min(1.0, (double)n / (double)n2));
            bossBar.setColor(BarColor.YELLOW);
        } else if (gameManager.state() == GameManager.State.RUNNING) {
            long l = Math.max(0L, (gameManager.endAtMs() - System.currentTimeMillis()) / 1000L);
            String string3 = ScoreboardManager.formatTime(l);
            long l2 = this.plugin.getConfig().getLong("game.duration-minutes", 30L) * 60000L;
            if (l2 <= 0L) {
                l2 = 1800000L;
            }
            d = Math.max(0.0, Math.min(1.0, (double)l / ((double)l2 / 1000.0)));
            object = gameManager.mode() == GameManager.Mode.SOLO ? "\u00a7a\u5bf9\u5c40\u8fdb\u884c\u4e2d \u00a77| \u00a7e\u5269\u4f59 \u00a7b" + string3 + " \u00a77| \u00a7a\u5b58\u6d3b " + gameManager.aliveCount() + "\u00a77/\u00a7a" + gameManager.playerCount() : "\u00a7c\u5bf9\u5c40\u8fdb\u884c\u4e2d \u00a77| \u00a7e\u51fb\u6740 \u00a7c" + this.getKills(uUID) + " \u00a77| \u00a7e\u6b7b\u4ea1 " + this.getDeaths(uUID) + " \u00a77| \u00a7a\u5b58\u6d3b " + gameManager.aliveCount() + "\u00a77/\u00a7a" + gameManager.playerCount();
            bossBar.setColor(gameManager.mode() == GameManager.Mode.SOLO ? BarColor.BLUE : BarColor.RED);
        } else {
            object = "\u00a7d\u5bf9\u5c40\u7ed3\u7b97\u4e2d...";
            d = 0.0;
            bossBar.setColor(BarColor.PURPLE);
        }
        if (!bl && this.plugin.specialItems() != null && (string2 = this.plugin.specialItems().trackingText(uUID)) != null && !string2.isEmpty()) {
            object = (String)object + "  \u00a77\u2502  " + string2;
        }
        bossBar.setTitle((String)object);
        bossBar.setProgress(d);
        bossBar.setVisible(true);
        Object object2 = bl ? "\u00a7c\u4f60\u5df2\u6dd8\u6c70 \u00a77| \u00a7a\u5b58\u6d3b " + gameManager.aliveCount() + "\u00a77/\u00a7a" + gameManager.playerCount() + " \u00a77| \u00a7e/box lobby \u56de\u5927\u5385 \u00a77\u6216 \u00a7e/box spectate \u65c1\u89c2" : (gameManager.state() == GameManager.State.RUNNING ? "\u00a77\u6a21\u5f0f: \u00a7" + (gameManager.mode() == GameManager.Mode.SOLO ? "a" : "c") + gameManager.mode().display + " \u00a77| \u00a7e\u5f00\u7bb1 \u00a7a" + this.getBoxes(uUID) + " \u00a77| \u00a7e\u51fb\u6740 \u00a7c" + this.getKills(uUID) + " \u00a77| \u00a7e\u6b7b\u4ea1 " + this.getDeaths(uUID) : (gameManager.state() == GameManager.State.COUNTDOWN ? "\u00a7e\u8ddd\u79bb\u5f00\u8d5b \u00a7b" + gameManager.countdownLeft() + " \u00a7e\u79d2 \u00a77| \u00a7e\u62a5\u540d " + gameManager.playerCount() + " \u4eba" : "\u00a7d\u7ed3\u7b97\u4e2d..."));
        if (!bl && gameManager.storm() != null && gameManager.storm().isActive() && (string = gameManager.storm().distanceText(uUID)) != null && !string.isEmpty()) {
            object2 = (String)object2 + " " + string;
        }
        try {
            player.sendActionBar((Component)LegacyComponentSerializer.legacyAmpersand().deserialize((String)object2));
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    public void showTitle(Player player, String string, String string2) {
        if (player == null || !player.isOnline()) {
            return;
        }
        try {
            player.sendTitle(string == null ? "" : string, string2 == null ? "" : string2, 5, 25, 10);
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    public void clearPlayer(UUID uUID) {
        this.removeBar(uUID);
        Player player = Bukkit.getPlayer((UUID)uUID);
        if (player != null && player.isOnline()) {
            try {
                player.sendActionBar((Component)Component.empty());
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
    }

    private void removeBar(UUID uUID) {
        BossBar bossBar = this.bossBars.remove(uUID);
        if (bossBar != null) {
            try {
                bossBar.removeAll();
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
    }

    public void clearAll() {
        for (UUID uUID : this.bossBars.keySet()) {
            this.removeBar(uUID);
        }
        for (UUID uUID : Bukkit.getOnlinePlayers()) {
            try {
                uUID.sendActionBar((Component)Component.empty());
            }
            catch (Throwable throwable) {}
        }
    }

    private static String formatTime(long l) {
        long l2 = l / 60L;
        long l3 = l % 60L;
        return l2 + ":" + (l3 < 10L ? "0" : "") + l3;
    }
}
