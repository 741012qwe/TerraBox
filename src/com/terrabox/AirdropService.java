/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.papermc.paper.threadedregions.scheduler.ScheduledTask
 *  net.kyori.adventure.text.Component
 *  org.bukkit.Bukkit
 *  org.bukkit.plugin.Plugin
 */
package com.terrabox;

import com.terrabox.Rarity;
import com.terrabox.TerraBoxPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.concurrent.atomic.AtomicLong;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public class AirdropService {
    private final TerraBoxPlugin plugin;
    private ScheduledTask task;
    private final AtomicLong nextAt = new AtomicLong();

    public AirdropService(TerraBoxPlugin terraBoxPlugin) {
        this.plugin = terraBoxPlugin;
    }

    public void start() {
        if (!this.plugin.getConfig().getBoolean("airdrop.enabled", true)) {
            return;
        }
        long l = Math.max(1L, this.plugin.getConfig().getLong("airdrop.interval-minutes", 20L)) * 60000L;
        this.nextAt.set(System.currentTimeMillis() + l / 2L);
        this.task = Bukkit.getGlobalRegionScheduler().runAtFixedRate((Plugin)this.plugin, scheduledTask -> this.tick(), 600L, 600L);
    }

    public void shutdown() {
        if (this.task != null) {
            this.task.cancel();
        }
    }

    private void tick() {
        if (System.currentTimeMillis() < this.nextAt.get()) {
            return;
        }
        long l = Math.max(1L, this.plugin.getConfig().getLong("airdrop.interval-minutes", 20L)) * 60000L;
        this.nextAt.set(System.currentTimeMillis() + l);
        this.dropNow(null);
    }

    public void dropNow(Runnable runnable) {
        if (this.plugin.worlds().world() == null) {
            return;
        }
        String string = this.plugin.getConfig().getString("airdrop.rarity", "LEGENDARY");
        Rarity rarity = Rarity.parse(string);
        if (rarity == null) {
            rarity = Rarity.LEGENDARY;
        }
        Rarity rarity2 = rarity;
        this.plugin.boxes().spawnRandomBox(rarity2, true, boxEntry -> {
            if (boxEntry == null) {
                return;
            }
            if (this.plugin.getConfig().getBoolean("boxes.broadcast-airdrop", true)) {
                Bukkit.getGlobalRegionScheduler().execute((Plugin)this.plugin, () -> Bukkit.broadcast((Component)this.plugin.component("airdrop-placed", "{world}", this.plugin.worlds().world() != null ? this.plugin.worlds().world().getName() : "?", "{x}", String.valueOf(boxEntry.x), "{z}", String.valueOf(boxEntry.z))));
            }
            if (runnable != null) {
                runnable.run();
            }
        });
    }

    public long secondsUntilNext() {
        return Math.max(0L, (this.nextAt.get() - System.currentTimeMillis()) / 1000L);
    }
}
