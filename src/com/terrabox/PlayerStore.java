package com.terrabox;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

/**
 * 玩家数据仓库 (开箱统计/内置积分/寻宝记录)
 * 线程模型 (白皮书 §5.2/§8.2):
 *  - 内存: ConcurrentHashMap + AtomicLong, 任意区域线程可直接读写
 *  - 磁盘: 全部 AsyncScheduler; 写盘用 临时文件 + ATOMIC_MOVE 原子替换
 *  - 合并: 异步加载文件时若玩家已有实时改动 (touched>0), 用加法合并基线, 不丢增量
 */
public class PlayerStore {
    private final TerraBoxPlugin plugin;
    private final File dir;
    private final ConcurrentHashMap<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private ScheduledTask autosaveTask;

    public PlayerStore(TerraBoxPlugin plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "playerdata");
        // 目录创建放 IO 阶段
    }

    public void start() {
        // 周期自动保存 (AsyncScheduler, 每 5 分钟)
        autosaveTask = org.bukkit.Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> {
            for (Map.Entry<UUID, PlayerData> e : cache.entrySet()) {
                if (e.getValue().touched.get() > 0) saveAsync(e.getKey(), e.getValue());
            }
        }, 5, 5, TimeUnit.MINUTES);
    }

    public void shutdown() {
        if (autosaveTask != null) autosaveTask.cancel();
        // onDisable: 区域调度器仍活跃, 同步落盘 (白皮书 §3.3)
        for (Map.Entry<UUID, PlayerData> e : cache.entrySet()) saveSync(e.getKey(), e.getValue());
        cache.clear();
    }

    /** 取数据, 不存在则创建占位 (任意线程安全, 统计递增由此进入) */
    public PlayerData getOrCreate(UUID uuid, String name) {
        return cache.computeIfAbsent(uuid, u -> new PlayerData(uuid, name));
    }

    /** 进服加载: 有文件则异步读取并合并 (增量不丢失); 完成后回调 (异步线程) */
    public void loadAsync(UUID uuid, String name, Runnable afterLoad) {
        PlayerData d = getOrCreate(uuid, name);
        d.name = (name != null ? name : d.name);
        Bukkit.getAsyncScheduler().runNow(plugin, t -> {
            PlayerData loaded = read(uuid);
            if (loaded != null) d.mergeFrom(loaded);
            if (afterLoad != null) afterLoad.run();
        });
    }

    /** 退出: 异步落盘并清理缓存 */
    public void saveAndUnload(UUID uuid) {
        PlayerData d = cache.remove(uuid);
        if (d != null) saveAsync(uuid, d);
    }

    public void saveAsync(UUID uuid, PlayerData d) {
        d.touched.set(0);
        Bukkit.getAsyncScheduler().runNow(plugin, t -> saveSync(uuid, d));
    }

    private void saveSync(UUID uuid, PlayerData d) {
        try {
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, uuid + ".yml");
            YamlConfiguration y = new YamlConfiguration();
            y.set("name", d.name);
            y.set("first-seen", d.firstSeen.get());
            y.set("last-seen", System.currentTimeMillis());
            y.set("money", d.money.get());
            y.set("opened-common", d.openedCommon.get());
            y.set("opened-rare", d.openedRare.get());
            y.set("opened-epic", d.openedEpic.get());
            y.set("opened-legendary", d.openedLegendary.get());
            y.set("opened-mythic", d.openedMythic.get());
            y.set("airdrop-looted", d.airdropLooted.get());
            y.set("sold-value", d.soldValue.get());
            y.set("hunt-count", d.huntCount.get());
            File tmp = new File(dir, uuid + ".yml.tmp");
            y.save(tmp);
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("玩家数据保存失败 " + d.name + ": " + e.getMessage());
        }
    }

    private PlayerData read(UUID uuid) {
        File file = new File(dir, uuid + ".yml");
        if (!file.isFile()) return null;
        try {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
            PlayerData d = new PlayerData(uuid, y.getString("name", "?"));
            d.firstSeen.set(y.getLong("first-seen", 0));
            d.money.set(y.getLong("money", 0));
            d.openedCommon.set(y.getLong("opened-common", 0));
            d.openedRare.set(y.getLong("opened-rare", 0));
            d.openedEpic.set(y.getLong("opened-epic", 0));
            d.openedLegendary.set(y.getLong("opened-legendary", 0));
            d.openedMythic.set(y.getLong("opened-mythic", 0));
            d.airdropLooted.set(y.getLong("airdrop-looted", 0));
            d.soldValue.set(y.getLong("sold-value", 0));
            d.huntCount.set(y.getLong("hunt-count", 0));
            d.merged.set(true);
            return d;
        } catch (Exception e) {
            plugin.getLogger().warning("玩家数据读取失败 " + uuid + ": " + e.getMessage());
            return null;
        }
    }

    /** 异步扫描全部数据文件, 返回开箱总数前10 (AsyncScheduler, 回调 Global) */
    public interface TopCallback { void accept(List<TopEntry> list); }

    public record TopEntry(String name, long count) {}

    public void topAsync(TopCallback cb) {
        Bukkit.getAsyncScheduler().runNow(plugin, t -> {
            List<TopEntry> list = new ArrayList<>();
            File[] files = dir.listFiles((f, n) -> n.endsWith(".yml"));
            if (files != null) {
                for (File f : files) {
                    try {
                        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
                        long total = y.getLong("opened-common", 0) + y.getLong("opened-rare", 0)
                                + y.getLong("opened-epic", 0) + y.getLong("opened-legendary", 0)
                                + y.getLong("opened-mythic", 0);
                        if (total > 0) list.add(new TopEntry(y.getString("name", "?"), total));
                    } catch (Exception ignored) {}
                }
            }
            // 在线玩家内存数据优先 (比文件新)
            for (PlayerData d : cache.values()) {
                long total = d.openedTotal();
                boolean replaced = false;
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i).name().equals(d.name)) {
                        list.set(i, new TopEntry(d.name, total));
                        replaced = true;
                        break;
                    }
                }
                if (!replaced && total > 0) list.add(new TopEntry(d.name, total));
            }
            list.sort(Comparator.comparingLong(TopEntry::count).reversed());
            List<TopEntry> top = list.subList(0, Math.min(10, list.size()));
            Bukkit.getGlobalRegionScheduler().execute(plugin, () -> cb.accept(top));
        });
    }

    /** 玩家数据 (字段并发安全) */
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

        PlayerData(UUID uuid, String name) {
            this.uuid = uuid;
            this.name = name != null ? name : "?";
        }

        public boolean isNew() {
            return firstSeen.get() == 0;
        }

        public void touch() {
            touched.incrementAndGet();
            if (firstSeen.get() == 0) firstSeen.compareAndSet(0, System.currentTimeMillis());
        }

        public long openedTotal() {
            return openedCommon.get() + openedRare.get() + openedEpic.get()
                    + openedLegendary.get() + openedMythic.get();
        }

        public void addOpened(Rarity r) {
            touch();
            switch (r) {
                case COMMON -> openedCommon.incrementAndGet();
                case RARE -> openedRare.incrementAndGet();
                case EPIC -> openedEpic.incrementAndGet();
                case LEGENDARY -> openedLegendary.incrementAndGet();
                case MYTHIC -> openedMythic.incrementAndGet();
            }
        }

        public double money() {
            return money.get();
        }

        public void addMoney(double amount) {
            touch();
            money.addAndGet((long) amount);
        }

        /** 内置积分取款: CAS 防负余额 */
        public boolean takeMoney(double amount) {
            long cost = (long) Math.ceil(amount);
            while (true) {
                long cur = money.get();
                if (cur < cost) return false;
                if (money.compareAndSet(cur, cur - cost)) {
                    touch();
                    return true;
                }
            }
        }

        /** 异步加载后的基线合并: 计数器相加, 不覆盖实时增量 */
        void mergeFrom(PlayerData file) {
            if (merged.compareAndSet(false, true)) {
                // 玩家无实时改动 → 直接采用文件值
                if (touched.get() == 0) {
                    firstSeen.set(file.firstSeen.get());
                    money.set(file.money.get());
                    openedCommon.set(file.openedCommon.get());
                    openedRare.set(file.openedRare.get());
                    openedEpic.set(file.openedEpic.get());
                    openedLegendary.set(file.openedLegendary.get());
                    openedMythic.set(file.openedMythic.get());
                    airdropLooted.set(file.airdropLooted.get());
                    soldValue.set(file.soldValue.get());
                    huntCount.set(file.huntCount.get());
                } else {
                    // 有实时增量 → 文件值为基线, 加法合并
                    if (firstSeen.get() == 0 && file.firstSeen.get() > 0)
                        firstSeen.set(file.firstSeen.get());
                    money.addAndGet(file.money.get());
                    openedCommon.addAndGet(file.openedCommon.get());
                    openedRare.addAndGet(file.openedRare.get());
                    openedEpic.addAndGet(file.openedEpic.get());
                    openedLegendary.addAndGet(file.openedLegendary.get());
                    openedMythic.addAndGet(file.openedMythic.get());
                    airdropLooted.addAndGet(file.airdropLooted.get());
                    soldValue.addAndGet(file.soldValue.get());
                    huntCount.addAndGet(file.huntCount.get());
                }
            }
        }
    }
}
