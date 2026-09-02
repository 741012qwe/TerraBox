package com.terrabox;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.TileState;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 物资箱管理: 随机陆地投放 / 注册表持久化 / 到期换位 / 搬空补货 / 粒子标记
 *
 * 线程模型 (白皮书 §4.3/§5.1):
 *  - 方块读写: 先 getChunkAtAsync 加载, 再 RegionScheduler 在归属区域线程执行
 *  - 注册表: CopyOnWriteArrayList (读多写少), 任意线程可读
 *  - 磁盘: AsyncScheduler 节流落盘
 *  - 广播: GlobalRegionScheduler
 */
public class BoxManager {
    private final TerraBoxPlugin plugin;
    private final NamespacedKey keyRarity;
    private final NamespacedKey keyBorn;
    private final NamespacedKey keyAirdrop;

    private final CopyOnWriteArrayList<BoxEntry> registry = new CopyOnWriteArrayList<>();
    private final AtomicBoolean saveQueued = new AtomicBoolean(false);
    private final AtomicBoolean loading = new AtomicBoolean(false);
    /** 本批次已投放箱子计数, 每 BATCH_LOG_INTERVAL 条打印一条汇总日志 */
    private final java.util.concurrent.atomic.AtomicInteger placedSinceBatchLog = new java.util.concurrent.atomic.AtomicInteger(0);
    private static final int BATCH_LOG_INTERVAL = 20; // 每 20 个箱子打印一条汇总, 避免刷屏
    private ScheduledTask maintainTask;
    private ScheduledTask particleTask;

    public BoxManager(TerraBoxPlugin plugin) {
        this.plugin = plugin;
        this.keyRarity = new NamespacedKey(plugin, "rarity");
        this.keyBorn = new NamespacedKey(plugin, "born");
        this.keyAirdrop = new NamespacedKey(plugin, "airdrop");
        this.registry = new CopyOnWriteArrayList<>();
    }

    /** 获取稀有度PDC键 */
    public NamespacedKey keyRarity() { return keyRarity; }
    /** 获取出生时间PDC键 */
    public NamespacedKey keyBorn() { return keyBorn; }
    /** 获取箱子注册表 */
    public CopyOnWriteArrayList<BoxEntry> registry() { return registry; }
    /** 标记需要保存 */
    public void markDirty() { if (saveQueued.compareAndSet(false, true)) scheduleSave(); }

    // ==================== 数据结构 ====================

    public static final class BoxEntry {
        public final int x, y, z;
        public final Rarity rarity;
        public final long born;
        public final boolean airdrop;

        public BoxEntry(int x, int y, int z, Rarity rarity, long born, boolean airdrop) {
            this.x = x; this.y = y; this.z = z;
            this.rarity = rarity; this.born = born; this.airdrop = airdrop;
        }
    }

    // ==================== 生命周期 ====================

    public void start() {
        loading.set(true);
        Bukkit.getAsyncScheduler().runNow(plugin, t -> {
            registry.addAll(loadRegistry());
            plugin.getLogger().info("物资箱注册表加载完成: " + registry.size() + " 个");
            loading.set(false);
        });

        long cycleTicks = 20L * 60L; // 每 60 秒维护一次
        maintainTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin,
                t -> maintain(), cycleTicks, cycleTicks);

        if (plugin.getConfig().getBoolean("particles.enabled", true)) {
            long pt = Math.max(20, plugin.getConfig().getInt("particles.interval-ticks", 60));
            particleTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin,
                    t -> spawnParticles(), pt, pt);
        }
    }

    public void shutdown() {
        if (maintainTask != null) maintainTask.cancel();
        if (particleTask != null) particleTask.cancel();
        // 确保最后一批日志被打印
        int remaining = placedSinceBatchLog.getAndSet(0);
        if (remaining > 0) {
            plugin.getLogger().info("物资箱投放汇总: 最近 " + remaining + " 个箱子已登记");
        }
        saveRegistryNow(); // onDisable 同步落盘
    }

    // ==================== 投放 ====================

    /**
     * 随机找一块陆地投放物资箱 (异步选点 → 区域线程放置)
     * after: 放置成功回调 (Region 线程), 可为 null
     */
    public void spawnRandomBox(Rarity rarity, boolean airdrop, Consumer<BoxEntry> after) {
        spawnRandomBox(rarity, airdrop, after, 0);
    }

    private void spawnRandomBox(Rarity rarity, boolean airdrop, Consumer<BoxEntry> after, int attempt) {
        World w = plugin.worlds().world();
        if (w == null) return;
        int tries = Math.max(4, plugin.getConfig().getInt("spawn.tries", 10));

        double edge = plugin.worlds().borderHalf();
        int pad = Math.max(8, plugin.getConfig().getInt("boxes.edge-padding", 24));
        double limit = Math.max(32, edge - pad);
        int x = ThreadLocalRandom.current().nextInt((int) -limit, (int) limit + 1);
        int z = ThreadLocalRandom.current().nextInt((int) -limit, (int) limit + 1);

        double minDist = plugin.getConfig().getDouble("boxes.min-distance", 24.0);
        if (tooClose(x, z, minDist)) {
            retryOrGiveUp(rarity, airdrop, after, attempt, tries, "距离过近");
            return;
        }

        int cx = x >> 4, cz = z >> 4;
        final int fx = x, fz = z, fa = attempt, ft = tries;
        w.getChunkAtAsync(cx, cz).whenComplete((chunk, err) -> {
            if (err != null) {
                plugin.getLogger().warning("物资箱区块加载失败 (" + cx + "," + cz + "): " + err);
                retryOrGiveUp(rarity, airdrop, after, fa, ft, "区块加载失败");
                return;
            }
            // force load 保持区块(防 Folia 激进卸载), 再调度区域任务; 任务执行后解除
            Bukkit.getGlobalRegionScheduler().run(plugin, t -> {
                try { w.setChunkForceLoaded(cx, cz, true); } catch (Throwable ignored) {}
                Bukkit.getRegionScheduler().run(plugin, w, cx, cz, task -> {
                    try {
                        // 每100个箱子打印一次进度, 避免刷屏
                        if (attempt == 1 && fx % 100 == 0) {
                            plugin.getLogger().info("物资箱投放进度: 已尝试 " + (fx / 100) + "00 个位置...");
                        }
                        tryPlace(w, fx, fz, rarity, airdrop, after, fa, ft);
                    } catch (Throwable ex) {
                        plugin.getLogger().warning("物资箱放置异常 (" + fx + "," + fz + "): " + ex);
                        ex.printStackTrace(); // 完整堆栈定位 NPE 行
                        retryOrGiveUp(rarity, airdrop, after, fa, ft, "放置异常");
                    } finally {
                        try { w.setChunkForceLoaded(cx, cz, false); } catch (Throwable ignored) {}
                    }
                });
            });
        });
    }

    /**
     * 指定坐标放置物资箱 (出生广场等固定位置, 不检查开放地)
     * after: 放置成功回调 (区域线程), 可为 null
     */
    public void spawnBoxAt(int x, int z, Rarity rarity, boolean airdrop, Consumer<BoxEntry> after) {
        World w = plugin.worlds().world();
        if (w == null) return;
        int cx = x >> 4, cz = z >> 4;
        w.getChunkAtAsync(cx, cz).whenComplete((chunk, err) -> {
            if (err != null) {
                plugin.getLogger().warning("固定物资箱区块加载失败 (" + cx + "," + cz + "): " + err);
                return;
            }
            Bukkit.getGlobalRegionScheduler().run(plugin, t -> {
                try { w.setChunkForceLoaded(cx, cz, true); } catch (Throwable ignored) {}
                Bukkit.getRegionScheduler().run(plugin, w, cx, cz, task -> {
                    try {
                        tryPlaceAt(w, x, z, rarity, airdrop, after);
                    } catch (Throwable ex) {
                        plugin.getLogger().warning("固定物资箱放置异常 (" + x + "," + z + "): " + ex);
                        ex.printStackTrace(); // 完整堆栈定位 NPE 行
                    } finally {
                        try { w.setChunkForceLoaded(cx, cz, false); } catch (Throwable ignored) {}
                    }
                });
            });
        });
    }

    private void retryOrGiveUp(Rarity rarity, boolean airdrop, Consumer<BoxEntry> after,
                               int attempt, int tries, String reason) {
        if (attempt < tries) {
            Bukkit.getAsyncScheduler().runNow(plugin, t -> spawnRandomBox(rarity, airdrop, after, attempt + 1));
        } else {
            plugin.getLogger().info("物资箱投放放弃(" + reason + ", 重试 " + attempt + " 次)");
        }
    }

    /** 区域线程: 校验地形并放置箱子 (随机投放版: 要求开放平地) */
    private void tryPlace(World w, int x, int z, Rarity rarity, boolean airdrop,
                          Consumer<BoxEntry> after, int attempt, int tries) {
        if (!w.getWorldBorder().isInside(new Location(w, x, 64, z))) {
            retryOrGiveUp(rarity, airdrop, after, attempt, tries, "越界");
            return;
        }
        // 放在合适的地方: 要求周围 3x3 平坦开阔 (非陡坡/树/水)
        if (!isOpenGround(w, x, z)) {
            retryOrGiveUp(rarity, airdrop, after, attempt, tries, "地形不平坦");
            return;
        }
        tryPlaceAt(w, x, z, rarity, airdrop, after);
    }

    /** 区域线程: 指定坐标放置箱子 (公共主体) */
    private void tryPlaceAt(World w, int x, int z, Rarity rarity, boolean airdrop, Consumer<BoxEntry> after) {
        // Folia线程安全: 使用getChunkAtAsync加载区块后读取高度
        Chunk chunk = w.getChunkAt(x >> 4, z >> 4);
        // 等待区块加载完成（在区域线程内执行）
        Bukkit.getRegionScheduler().run(plugin, chunk, task -> {
            int gy = w.getHighestBlockYAt(x & 0xF, z & 0xF);
            placeBoxAt(w, x, gy, z, rarity, airdrop, after);
        });
    }

    private void placeBoxAt(World w, int x, int gy, int z, Rarity rarity, boolean airdrop, Consumer<BoxEntry> after) {
        Block ground = w.getBlockAt(x, gy, z);
        if (!validGround(ground)) {
            return;
        }
        int boxY = gy + 1;
        Block boxBlock = w.getBlockAt(x, boxY, z);
        if (!boxBlock.getType().isAir()) {
            return;
        }
        // 阶段1: 放箱子并重新获取 fresh Block (避免 stale Block 引用导致 getState/库存 NPE)
        boxBlock.setType(Material.CHEST, false);
        Block placed = w.getBlockAt(x, boxY, z);
        if (!(placed.getState() instanceof Chest chest)) {
            plugin.getLogger().warning("物资箱放置失败(" + x + "," + z + "): 箱子状态无效");
            return;
        }
        // 写 PDC + 自定义名 (先 update 写回, 让 tile 真正成为箱子)
        PersistentDataContainer pdc = chest.getPersistentDataContainer();
        long born = System.currentTimeMillis();
        pdc.set(keyRarity, PersistentDataType.STRING, rarity.name());
        pdc.set(keyBorn, PersistentDataType.LONG, born);
        if (airdrop) pdc.set(keyAirdrop, PersistentDataType.BYTE, (byte) 1);
        chest.customName(net.kyori.adventure.text.Component.text(
                rarity.display + "物资箱", rarity.color));
        chest.update();
        // 阶段2: 重新获取 fresh state, 用实时方块库存填充战利品
        // getBlockInventory() 是绑定世界 tile 的实时视图, 填充立即生效, 不再 update(避免快照覆盖清空)
        if (!(w.getBlockAt(x, boxY, z).getState() instanceof Chest fresh)) {
            plugin.getLogger().warning("物资箱状态刷新失败(" + x + "," + z + ")");
            return;
        }
        int filled = plugin.loot().fillInventory(fresh.getBlockInventory(), rarity);

        // 先登记 registry + 聚合日志 (每 BATCH_LOG_INTERVAL 条箱子打印一次汇总, 避免刷屏)
        BoxEntry entry = new BoxEntry(x, boxY, z, rarity, born, airdrop);
        registry.add(entry);
        markDirty();
        int n = placedSinceBatchLog.incrementAndGet();
        if (n % BATCH_LOG_INTERVAL == 0) {
            // 每 20 条打印一次汇总, 并重置计数器
            int batch = placedSinceBatchLog.getAndSet(0);
            plugin.getLogger().info("物资箱投放汇总: 最近 " + batch + " 个箱子已登记");
        } else if (n == 1) {
            // 批次开始时不打印, 等凑齐 20 条再汇总
        }

        // 尾部审计 (辅助, 失败不影响箱子放置结果)
        try {
            auditLootGeneration(filled);
        } catch (Throwable auditEx) {
            plugin.getLogger().warning("道具审计失败: " + auditEx);
        }

        if (airdrop) {
            w.strikeLightningEffect(new Location(w, x + 0.5, boxY, z + 0.5));
        }
        if (after != null) after.accept(entry);
    }

    /** 审计道具生成 (批次聚合, 只记录统计不逐条打印) */
    private void auditLootGeneration(int totalStacks) {
        plugin.lootAuditLogger().logBoxGeneration(LootAuditLogger.SYSTEM, totalStacks);
    }

    /** 开放平地校验: 默认周围 3x3 高度差 <=3 且有效地面; 原版生成世界(恶地等)放宽到 <=6 (地形崎岖)
     *  (区域线程, 跨区块异常降级为不平坦) */
    private boolean isOpenGround(World w, int x, int z) {
        try {
            boolean relaxed = false;
            if (plugin.arenas() != null) {
                TerrainType tt = plugin.arenas().terrainOf(w.getName());
                relaxed = (tt == TerrainType.BADLANDS || tt == TerrainType.NORMAL
                        || tt == TerrainType.NETHER || tt == TerrainType.THE_END);
            }
            int tolerance = relaxed ? 6 : 3;
            int yCenter = w.getHighestBlockYAt(x, z);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int y = w.getHighestBlockYAt(x + dx, z + dz);
                    if (Math.abs(y - yCenter) > tolerance) return false;
                    Block g = w.getBlockAt(x + dx, y, z + dz);
                    if (!validGround(g)) return false;
                }
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 陆地校验: 固体 + 排除树叶树干 + 排除液面冰面 */
    private boolean validGround(Block b) {
        if (b == null) return false; // getHighestBlockAt 可能返回 null(虚空/无方块列)
        Material m = b.getType();
        if (!m.isSolid()) return false;
        String n = m.name();
        if (n.contains("LEAVES") || n.contains("LOG") || n.contains("STEM")
                || n.contains("WATER") || n.contains("ICE") || n.contains("LILY")) return false;
        return true;
    }

    private boolean tooClose(int x, int z, double minDist) {
        double minSq = minDist * minDist;
        for (BoxEntry e : registry) {
            long dx = e.x - x, dz = e.z - z;
            if (dx * dx + dz * dz < minSq) return true;
        }
        return false;
    }

    // ==================== 维护 ====================

    /** Global 线程: 到期换位 + 数量补充 + 失效自愈 */
    private void maintain() {
        World w = plugin.worlds().world();
        if (w == null || loading.get()) return;
        long refreshMs = plugin.getConfig().getLong("boxes.refresh-minutes", 45) * 60_000L;
        long now = System.currentTimeMillis();
        List<BoxEntry> expired = new ArrayList<>();
        for (BoxEntry e : registry) {
            if (now - e.born > refreshMs) expired.add(e);
        }
        // 每周期最多处理 30 个过期箱, 防止停机后一次性加载大量区块
        int cap = Math.min(30, expired.size());
        for (int i = 0; i < cap; i++) {
            BoxEntry e = expired.get(i);
            removeBoxAt(w, e, () -> {
                if (plugin.getConfig().getBoolean("boxes.refill-on-open", true))
                    spawnRandomBox(e.rarity, false, null);
            });
        }
        int refill = Math.min(plugin.boxRefillPerCycle(),
                plugin.boxMaxCount() - registry.size());
        for (int i = 0; i < refill; i++) {
            spawnRandomBox(plugin.weightedPickForWorld(), false, null);
        }
    }

    /** 区域线程调用: 判断方块是否为注册物资箱 (纯 PDC 读取, 不写注册表) */
    public BoxEntry registeredAt(Block block) {
        if (block.getType() != Material.CHEST) return null;
        if (!(block.getState() instanceof TileState ts)) return null;
        PersistentDataContainer pdc = ts.getPersistentDataContainer();
        String r = pdc.get(keyRarity, PersistentDataType.STRING);
        if (r == null) return null;
        Rarity rarity = Rarity.parse(r);
        if (rarity == null) return null;
        long born = pdc.getOrDefault(keyBorn, PersistentDataType.LONG, 0L);
        boolean airdrop = pdc.getOrDefault(keyAirdrop, PersistentDataType.BYTE, (byte) 0) == (byte) 1;
        Location loc = block.getLocation();
        return new BoxEntry(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), rarity, born, airdrop);
    }

    /** 搬空处理: 移除箱子并按需补货 (箱子所在区域线程) */
    public void handleChestEmptied(Block block, BoxEntry entry) {
        registry.removeIf(e -> e.x == entry.x && e.y == entry.y && e.z == entry.z);
        markDirty();
        block.setType(Material.AIR, false);
        if (plugin.getConfig().getBoolean("boxes.refill-on-open", true)) {
            spawnRandomBox(entry.rarity, false, null);
        }
    }

    /** 异步安全移除: 先加载区块 → 区域线程拆除 */
    private void removeBoxAt(World w, BoxEntry e, Runnable after) {
        registry.removeIf(o -> o.x == e.x && o.y == e.y && o.z == e.z);
        markDirty();
        int cx = e.x >> 4, cz = e.z >> 4;
        w.getChunkAtAsync(cx, cz).thenAccept(chunk ->
                Bukkit.getRegionScheduler().run(plugin, w, cx, cz, task -> {
                    Block b = w.getBlockAt(e.x, e.y, e.z);
                    if (b.getType() == Material.CHEST) {
                        BoxEntry live = registeredAt(b);
                        if (live != null && live.born == e.born) {
                            // 拆箱前清空库存, 防止物品弹出
                            if (b.getState() instanceof org.bukkit.block.Chest chest) {
                                try { chest.getBlockInventory().clear(); } catch (Throwable ignored) {}
                            }
                            b.setType(Material.AIR, false);
                            if (after != null) after.run();
                        }
                    }
                }));
    }

    /** 清空全部物资箱 (管理命令, 异步逐个拆) */
    public void wipeAll(Runnable done) {
        World w = plugin.worlds().world();
        if (w == null) return;
        List<BoxEntry> snapshot = new ArrayList<>(registry);
        registry.clear();
        markDirty();
        final int[] remain = {snapshot.size()};
        if (snapshot.isEmpty()) { if (done != null) done.run(); return; }
        for (BoxEntry e : snapshot) {
            int cx = e.x >> 4, cz = e.z >> 4;
            w.getChunkAtAsync(cx, cz).thenAccept(chunk ->
                    Bukkit.getRegionScheduler().run(plugin, w, cx, cz, task -> {
                        Block b = w.getBlockAt(e.x, e.y, e.z);
                        if (b.getType() == Material.CHEST) {
                            // 拆箱前先清空箱子库存, 防止战利品掉出 (拆箱(setType AIR)会把物品弹出)
                            if (b.getState() instanceof org.bukkit.block.Chest chest) {
                                try { chest.getBlockInventory().clear(); } catch (Throwable ignored) {}
                            }
                            b.setType(Material.AIR, false);
                        }
                        synchronized (remain) {
                            if (--remain[0] <= 0 && done != null) done.run();
                        }
                    }));
        }
    }

    // ==================== 粒子 ====================

    private void spawnParticles() {
        World w = plugin.worlds().world();
        if (w == null) return;
        List<Rarity> shown = new ArrayList<>();
        for (String s : plugin.getConfig().getStringList("particles.rarities")) {
            Rarity r = Rarity.parse(s);
            if (r != null) shown.add(r);
        }
        if (shown.isEmpty()) return;
        for (BoxEntry e : registry) {
            if (!shown.contains(e.rarity)) continue;
            Bukkit.getRegionScheduler().run(plugin, w, e.x >> 4, e.z >> 4, task -> {
                Location loc = new Location(w, e.x + 0.5, e.y + 1.2, e.z + 0.5);
                w.spawnParticle(Particle.END_ROD, loc, 3, 0.2, 0.3, 0.2, 0.01);
            });
        }
    }

    // ==================== 查询 ====================

    public int count() {
        return registry.size();
    }

    public Map<Rarity, Integer> countByRarity() {
        Map<Rarity, Integer> map = new EnumMap<>(Rarity.class);
        for (BoxEntry e : registry) map.merge(e.rarity, 1, Integer::sum);
        return map;
    }

    /** 随机取一个指定稀有度的箱子 (寻宝用, 任意线程) */
    public BoxEntry randomOf(List<Rarity> rarities) {
        List<BoxEntry> pool = new ArrayList<>();
        for (BoxEntry e : registry) if (rarities.contains(e.rarity)) pool.add(e);
        if (pool.isEmpty()) return null;
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    public World worldOf(BoxEntry e) {
        return plugin.worlds().world();
    }

    // ==================== 注册表持久化 ====================

    private void markDirty() {
        if (saveQueued.compareAndSet(false, true)) {
            Bukkit.getAsyncScheduler().runDelayed(plugin, t -> {
                saveQueued.set(false);
                saveRegistryNow();
            }, 3, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    private void saveRegistryNow() {
        try {
            File file = new java.io.File(plugin.getDataFolder(), "boxes.yml");
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            org.bukkit.configuration.file.YamlConfiguration y =
                    new org.bukkit.configuration.file.YamlConfiguration();
            List<String> lines = new ArrayList<>();
            for (BoxEntry e : registry) {
                lines.add(e.x + ";" + e.y + ";" + e.z + ";" + e.rarity.name() + ";" + e.born
                        + ";" + (e.airdrop ? 1 : 0));
            }
            y.set("boxes", lines);
            y.save(file);
        } catch (Exception ex) {
            plugin.getLogger().warning("物资箱注册表保存失败: " + ex.getMessage());
        }
    }

    private List<BoxEntry> loadRegistry() {
        List<BoxEntry> list = new ArrayList<>();
        File file = new java.io.File(plugin.getDataFolder(), "boxes.yml");
        if (!file.isFile()) return list;
        try {
            org.bukkit.configuration.file.YamlConfiguration y =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
            for (String line : y.getStringList("boxes")) {
                try {
                    String[] p = line.split(";");
                    Rarity r = Rarity.parse(p[3]);
                    if (r == null) continue;
                    list.add(new BoxEntry(Integer.parseInt(p[0]), Integer.parseInt(p[1]),
                            Integer.parseInt(p[2]), r, Long.parseLong(p[3 + 1]),
                            p.length > 5 && "1".equals(p[5])));
                } catch (Exception ignored) {}
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("物资箱注册表加载失败: " + ex.getMessage());
        }
        return list;
    }
}
