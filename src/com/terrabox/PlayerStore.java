/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.papermc.paper.threadedregions.scheduler.ScheduledTask
 *  org.bukkit.Bukkit
 *  org.bukkit.configuration.file.YamlConfiguration
 *  org.bukkit.plugin.Plugin
 */
package com.terrabox;

import com.terrabox.Rarity;
import com.terrabox.TerraBoxPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

public class PlayerStore {
    private final TerraBoxPlugin plugin;
    private final File dir;
    private final ConcurrentHashMap<UUID, PlayerData> cache = new ConcurrentHashMap();
    private ScheduledTask autosaveTask;

    public PlayerStore(TerraBoxPlugin terraBoxPlugin) {
        this.plugin = terraBoxPlugin;
        this.dir = new File(terraBoxPlugin.getDataFolder(), "playerdata");
    }

    public void start() {
        this.autosaveTask = Bukkit.getAsyncScheduler().runAtFixedRate((Plugin)this.plugin, scheduledTask -> {
            for (Map.Entry<UUID, PlayerData> entry : this.cache.entrySet()) {
                if (entry.getValue().touched.get() <= 0L) continue;
                this.saveAsync(entry.getKey(), entry.getValue());
            }
        }, 5L, 5L, TimeUnit.MINUTES);
    }

    public void shutdown() {
        if (this.autosaveTask != null) {
            this.autosaveTask.cancel();
        }
        for (Map.Entry<UUID, PlayerData> entry : this.cache.entrySet()) {
            this.saveSync(entry.getKey(), entry.getValue());
        }
        this.cache.clear();
    }

    public PlayerData getOrCreate(UUID uUID, String string) {
        return this.cache.computeIfAbsent(uUID, uUID2 -> new PlayerData(uUID, string));
    }

    public void loadAsync(UUID uUID, String string, Runnable runnable) {
        PlayerData playerData = this.getOrCreate(uUID, string);
        playerData.name = string != null ? string : playerData.name;
        Bukkit.getAsyncScheduler().runNow((Plugin)this.plugin, scheduledTask -> {
            PlayerData playerData2 = this.read(uUID);
            if (playerData2 != null) {
                playerData.mergeFrom(playerData2);
            }
            if (runnable != null) {
                runnable.run();
            }
        });
    }

    public void saveAndUnload(UUID uUID) {
        PlayerData playerData = this.cache.remove(uUID);
        if (playerData != null) {
            this.saveAsync(uUID, playerData);
        }
    }

    public void saveAsync(UUID uUID, PlayerData playerData) {
        playerData.touched.set(0L);
        Bukkit.getAsyncScheduler().runNow((Plugin)this.plugin, scheduledTask -> this.saveSync(uUID, playerData));
    }

    private void saveSync(UUID uUID, PlayerData playerData) {
        try {
            if (!this.dir.exists()) {
                this.dir.mkdirs();
            }
            File file = new File(this.dir, String.valueOf(uUID) + ".yml");
            YamlConfiguration yamlConfiguration = new YamlConfiguration();
            yamlConfiguration.set("name", (Object)playerData.name);
            yamlConfiguration.set("first-seen", (Object)playerData.firstSeen.get());
            yamlConfiguration.set("last-seen", (Object)System.currentTimeMillis());
            yamlConfiguration.set("money", (Object)playerData.money.get());
            yamlConfiguration.set("opened-common", (Object)playerData.openedCommon.get());
            yamlConfiguration.set("opened-rare", (Object)playerData.openedRare.get());
            yamlConfiguration.set("opened-epic", (Object)playerData.openedEpic.get());
            yamlConfiguration.set("opened-legendary", (Object)playerData.openedLegendary.get());
            yamlConfiguration.set("opened-mythic", (Object)playerData.openedMythic.get());
            yamlConfiguration.set("airdrop-looted", (Object)playerData.airdropLooted.get());
            yamlConfiguration.set("sold-value", (Object)playerData.soldValue.get());
            yamlConfiguration.set("hunt-count", (Object)playerData.huntCount.get());
            File file2 = new File(this.dir, String.valueOf(uUID) + ".yml.tmp");
            yamlConfiguration.save(file2);
            try {
                Files.move(file2.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (IOException iOException) {
                Files.move(file2.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        catch (Exception exception) {
            this.plugin.getLogger().warning("\u73a9\u5bb6\u6570\u636e\u4fdd\u5b58\u5931\u8d25 " + playerData.name + ": " + exception.getMessage());
        }
    }

    private PlayerData read(UUID uUID) {
        File file = new File(this.dir, String.valueOf(uUID) + ".yml");
        if (!file.isFile()) {
            return null;
        }
        try {
            YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration((File)file);
            PlayerData playerData = new PlayerData(uUID, yamlConfiguration.getString("name", "?"));
            playerData.firstSeen.set(yamlConfiguration.getLong("first-seen", 0L));
            playerData.money.set(yamlConfiguration.getLong("money", 0L));
            playerData.openedCommon.set(yamlConfiguration.getLong("opened-common", 0L));
            playerData.openedRare.set(yamlConfiguration.getLong("opened-rare", 0L));
            playerData.openedEpic.set(yamlConfiguration.getLong("opened-epic", 0L));
            playerData.openedLegendary.set(yamlConfiguration.getLong("opened-legendary", 0L));
            playerData.openedMythic.set(yamlConfiguration.getLong("opened-mythic", 0L));
            playerData.airdropLooted.set(yamlConfiguration.getLong("airdrop-looted", 0L));
            playerData.soldValue.set(yamlConfiguration.getLong("sold-value", 0L));
            playerData.huntCount.set(yamlConfiguration.getLong("hunt-count", 0L));
            playerData.merged.set(true);
            return playerData;
        }
        catch (Exception exception) {
            this.plugin.getLogger().warning("\u73a9\u5bb6\u6570\u636e\u8bfb\u53d6\u5931\u8d25 " + String.valueOf(uUID) + ": " + exception.getMessage());
            return null;
        }
    }

    public void topAsync(TopCallback topCallback) {
        Bukkit.getAsyncScheduler().runNow((Plugin)this.plugin, scheduledTask -> {
            ArrayList<TopEntry> arrayList = new ArrayList<TopEntry>();
            File[] fileArray = this.dir.listFiles((file, string) -> string.endsWith(".yml"));
            if (fileArray != null) {
                for (File file2 : fileArray) {
                    try {
                        YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration((File)file2);
                        long l = yamlConfiguration.getLong("opened-common", 0L) + yamlConfiguration.getLong("opened-rare", 0L) + yamlConfiguration.getLong("opened-epic", 0L) + yamlConfiguration.getLong("opened-legendary", 0L) + yamlConfiguration.getLong("opened-mythic", 0L);
                        if (l <= 0L) continue;
                        arrayList.add(new TopEntry(yamlConfiguration.getString("name", "?"), l));
                    }
                    catch (Exception exception) {
                        // empty catch block
                    }
                }
            }
            for (PlayerData playerData : this.cache.values()) {
                long l = playerData.openedTotal();
                boolean bl = false;
                for (int i = 0; i < arrayList.size(); ++i) {
                    if (!((TopEntry)arrayList.get(i)).name().equals(playerData.name)) continue;
                    arrayList.set(i, new TopEntry(playerData.name, l));
                    bl = true;
                    break;
                }
                if (bl || l <= 0L) continue;
                arrayList.add(new TopEntry(playerData.name, l));
            }
            arrayList.sort(Comparator.comparingLong(TopEntry::count).reversed());
            List list = arrayList.subList(0, Math.min(10, arrayList.size()));
            Bukkit.getGlobalRegionScheduler().execute((Plugin)this.plugin, () -> topCallback.accept(list));
        });
    }

    public static class PlayerData {
        public final UUID uuid;
        public volatile String name;
        public final AtomicLong firstSeen = new AtomicLong();
        public final AtomicLong money = new AtomicLong();
        public final AtomicLong openedCommon = new AtomicLong();
        public final AtomicLong openedRare = new AtomicLong();
        public final AtomicLong openedEpic = new AtomicLong();
        public final AtomicLong openedLegendary = new AtomicLong();
        public final AtomicLong openedMythic = new AtomicLong();
        public final AtomicLong airdropLooted = new AtomicLong();
        public final AtomicLong soldValue = new AtomicLong();
        public final AtomicLong huntCount = new AtomicLong();
        final AtomicLong touched = new AtomicLong();
        final AtomicBoolean merged = new AtomicBoolean(false);

        PlayerData(UUID uUID, String string) {
            this.uuid = uUID;
            this.name = string != null ? string : "?";
        }

        public boolean isNew() {
            return this.firstSeen.get() == 0L;
        }

        public void touch() {
            this.touched.incrementAndGet();
            if (this.firstSeen.get() == 0L) {
                this.firstSeen.compareAndSet(0L, System.currentTimeMillis());
            }
        }

        public long openedTotal() {
            return this.openedCommon.get() + this.openedRare.get() + this.openedEpic.get() + this.openedLegendary.get() + this.openedMythic.get();
        }

        public void addOpened(Rarity rarity) {
            this.touch();
            switch (rarity) {
                case COMMON: {
                    this.openedCommon.incrementAndGet();
                    break;
                }
                case RARE: {
                    this.openedRare.incrementAndGet();
                    break;
                }
                case EPIC: {
                    this.openedEpic.incrementAndGet();
                    break;
                }
                case LEGENDARY: {
                    this.openedLegendary.incrementAndGet();
                    break;
                }
                case MYTHIC: {
                    this.openedMythic.incrementAndGet();
                }
            }
        }

        public double money() {
            return this.money.get();
        }

        public void addMoney(double d) {
            this.touch();
            this.money.addAndGet((long)d);
        }

        public boolean takeMoney(double d) {
            long l;
            long l2 = (long)Math.ceil(d);
            do {
                if ((l = this.money.get()) >= l2) continue;
                return false;
            } while (!this.money.compareAndSet(l, l - l2));
            this.touch();
            return true;
        }

        void mergeFrom(PlayerData playerData) {
            if (this.merged.compareAndSet(false, true)) {
                if (this.touched.get() == 0L) {
                    this.firstSeen.set(playerData.firstSeen.get());
                    this.money.set(playerData.money.get());
                    this.openedCommon.set(playerData.openedCommon.get());
                    this.openedRare.set(playerData.openedRare.get());
                    this.openedEpic.set(playerData.openedEpic.get());
                    this.openedLegendary.set(playerData.openedLegendary.get());
                    this.openedMythic.set(playerData.openedMythic.get());
                    this.airdropLooted.set(playerData.airdropLooted.get());
                    this.soldValue.set(playerData.soldValue.get());
                    this.huntCount.set(playerData.huntCount.get());
                } else {
                    if (this.firstSeen.get() == 0L && playerData.firstSeen.get() > 0L) {
                        this.firstSeen.set(playerData.firstSeen.get());
                    }
                    this.money.addAndGet(playerData.money.get());
                    this.openedCommon.addAndGet(playerData.openedCommon.get());
                    this.openedRare.addAndGet(playerData.openedRare.get());
                    this.openedEpic.addAndGet(playerData.openedEpic.get());
                    this.openedLegendary.addAndGet(playerData.openedLegendary.get());
                    this.openedMythic.addAndGet(playerData.openedMythic.get());
                    this.airdropLooted.addAndGet(playerData.airdropLooted.get());
                    this.soldValue.addAndGet(playerData.soldValue.get());
                    this.huntCount.addAndGet(playerData.huntCount.get());
                }
            }
        }
    }

    public static interface TopCallback {
        public void accept(List<TopEntry> var1);
    }

    public record TopEntry(String name, long count) {
    }
}
