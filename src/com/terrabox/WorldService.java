package com.terrabox;

import io.papermc.paper.threadedregions.RegionizedServerInitEvent;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.GameRule;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 专属世界服务 (多世界):
 *  - 大厅世界 (terra_lobby, 512x512): 玩家聚集地, 自动生成, 屏障+虚空+玻璃
 *  - 对局世界池 (arena_1~N): 由 ArenaManager 管理, 支持多种地形模板
 *
 * 线程模型 (白皮书 §6.3):
 *  - 世界创建: RegionizedServerInitEvent (调度到 Global 线程; createWorld 强制 Global)
 *  - 预生成: GlobalRegionScheduler 高吞吐批处理 (默认 1200 区块/秒)
 *  - 永远白天: 创建后设 doDaylightCycle=false + setTime(6000)
 */
public class WorldService implements Listener {
    private final TerraBoxPlugin plugin;
    private volatile World lobby;          // 大厅世界
    private volatile double lobbyHalf = 256.0; // 512/2
    private final Map<String, World> arenaWorlds = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicBoolean worldInit = new java.util.concurrent.atomic.AtomicBoolean(false);
    private volatile boolean ready = false;
    private final Map<String, PregenState> pregenStates = new ConcurrentHashMap<>();
    private final java.util.List<Runnable> onReadyHooks = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** 单世界预生成状态 */
    private static final class PregenState {
        final ArrayDeque<long[]> queue = new ArrayDeque<>();
        final AtomicInteger done = new AtomicInteger();
        final AtomicInteger queuedCount = new AtomicInteger();
        final AtomicLong start = new AtomicLong();
        ScheduledTask task;
        final boolean isMain;
        java.util.concurrent.ExecutorService executor; // 并发加载线程池
        PregenState(boolean isMain) { this.isMain = isMain; }
    }

    public WorldService(TerraBoxPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void whenReady(Runnable r) {
        if (ready) { r.run(); return; }
        this.onReadyHooks.add(r);
    }

    /** 当前对局世界 */
    public World world() {
        return plugin.arenas() != null ? plugin.arenas().current() : null;
    }

    /** 大厅世界 */
    public World lobby() {
        return lobby;
    }

    public boolean isReady() { return ready; }
    public int pregenTotal() {
        int sum = 0; for (PregenState s : pregenStates.values()) sum += s.queuedCount.get(); return sum;
    }
    public int pregenDone() {
        int sum = 0; for (PregenState s : pregenStates.values()) sum += s.done.get(); return sum;
    }
    public boolean pregenRunning() {
        for (PregenState s : pregenStates.values()) if (s.task != null) return true; return false;
    }
    public double borderHalf() {
        return plugin.arenas() != null && plugin.arenas().current() != null
                ? plugin.arenas().current().getWorldBorder().getSize() / 2.0 : 512.0;
    }
    public double lobbyHalf() { return lobbyHalf; }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerInit(RegionizedServerInitEvent event) {
        // Lophine 26.2: 此事件在 Server thread 触发, createWorld 需 Global 线程
        Bukkit.getGlobalRegionScheduler().run(plugin, t -> initWorld());
    }

    public void initWorld() {
        if (!worldInit.compareAndSet(false, true)) {
            plugin.getLogger().info("世界初始化已在进行中, 跳过重复调用");
            return;
        }
        try {
            doInitWorld();
        } catch (Throwable t) {
            plugin.getLogger().severe("世界初始化失败: " + t);
            worldInit.set(false);
        }
    }

    private void doInitWorld() {
        // 1) 大厅世界
        String lobbyName = plugin.getConfig().getString("lobby.name", "terra_lobby");
        if (Bukkit.getWorld(lobbyName) != null) {
            lobby = Bukkit.getWorld(lobbyName);
        } else {
            CustomTerrainGenerator gen = new CustomTerrainGenerator(plugin, TerrainType.DEFAULT);
            WorldCreator lc = WorldCreator.name(lobbyName).environment(World.Environment.NORMAL).generator(gen);
            long seed = plugin.getConfig().getLong("world.seed", 0);
            if (seed != 0) lc.seed(seed + 9999);
            lobby = Bukkit.createWorld(lc);
        }
        if (lobby != null) {
            double lsize = plugin.getConfig().getDouble("lobby.border-size", 512.0);
            lobbyHalf = lsize / 2.0;
            lobby.getWorldBorder().setCenter(0, 0);
            lobby.getWorldBorder().setSize(lsize);
            lobby.getWorldBorder().setWarningDistance(8);
            lobby.getWorldBorder().setDamageAmount(1.0);
            try { lobby.setPVP(false); } catch (Throwable ignored) {} // 大厅禁PvP
            forceDaytime(lobby);
            plugin.getLogger().info("大厅世界已创建/加载: " + lobbyName + " (边界 " + (int) lsize + "x" + (int) lsize + ")");
        }

        // 2) 对局世界池 (由 ArenaManager 预创建, 但不自动预生成)
        if (plugin.arenas() != null) {
            plugin.arenas().createInitial();
            World cur = plugin.arenas().current();
            if (cur != null) {
                forceDaytime(cur);
                applyBorder(cur);
            }
            // 对其余初始世界强制白天+边界
            for (String name : plugin.arenas().names()) {
                World w = Bukkit.getWorld(name);
                if (w != null) {
                    forceDaytime(w);
                    applyBorder(w);
                }
            }
        }

        // 预生成大厅世界 (只预生成大厅, 对局世界由玩家选择时触发)
        if (lobby != null) {
            startPregenIfNeeded(lobby, true);
        }
    }

    private void applyBorder(World w) {
        double size = w.getWorldBorder().getSize();
        w.getWorldBorder().setCenter(0, 0);
        w.getWorldBorder().setSize(size);
        w.getWorldBorder().setWarningDistance(8);
        w.getWorldBorder().setDamageAmount(1.0);
    }

    /** 永远白天: 禁昼夜循环 + 锁定正午 (Folia兼容版本) */
    private void forceDaytime(World w) {
        try {
            w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        } catch (Throwable t) {
            // Folia可能不支持某些游戏规则, 静默忽略
        }
        // Folia不允许在global线程设置世界时间, 移除时间锁定
        // 改为通过插件监听器在每个区域线程内设置
    }

    // ==================== 自动预生成 (每世界独立, 高吞吐 1200/s) ====================

    /** 供 ArenaManager/GUI 切换地形时预生成指定世界 (任意线程安全, 不阻塞) */
    public void ensurePregen(World w) {
        if (w == null) return;
        PregenState st = pregenStates.get(w.getName());
        if (st != null && st.task != null) return; // 已在预生成
        java.io.File marker = new java.io.File(plugin.getDataFolder(), "pregen_" + w.getName() + ".done");
        if (marker.isFile()) return;
        pregenWorld(w, false);
    }

    private void startPregenIfNeeded(World w, boolean fresh) {
        if (!plugin.getConfig().getBoolean("world.pregen", true)) {
            // 大厅预生成可以触发服务就绪
            if (w == lobby) finish("已跳过预生成(配置关闭)");
            return;
        }
        java.io.File marker = new java.io.File(plugin.getDataFolder(), "pregen_" + w.getName() + ".done");
        if (marker.isFile() && !fresh) {
            // 大厅预生成标记存在时触发服务就绪
            if (w == lobby) finish("[" + w.getName() + "] 预生成标记存在, 跳过");
            return;
        }
        pregenWorld(w, w == lobby);
    }

    private void pregenWorld(World w, boolean isMain) {
        PregenState st = pregenStates.computeIfAbsent(w.getName(), k -> new PregenState(isMain));
        // 如果已有任务在运行, 先关闭旧线程池 (防泄漏)
        if (st.task != null || st.executor != null) {
            cancelPregen(st);
        }

        double half = w.getWorldBorder().getSize() / 2.0;
        int minCx = (int) Math.floor(-half / 16.0) - 1;
        int maxCx = (int) Math.ceil(half / 16.0);
        long count = 0;
        st.queue.clear();
        st.done.set(0);
        for (int cx = minCx; cx < maxCx; cx++) {
            for (int cz = minCx; cz < maxCx; cz++) {
                st.queue.add(new long[]{cx, cz});
                count++;
            }
        }
        st.queuedCount.set((int) count);
        st.start.set(System.currentTimeMillis());

        int batch = Math.max(1, plugin.getConfig().getInt("world.pregen-batch", 20000));
        int interval = Math.max(1, plugin.getConfig().getInt("world.pregen-interval-ticks", 1));
        // 创建并发加载线程池: 使用更多线程充分利用多核CPU
        int threadCount = Math.min(32, Math.max(8, Runtime.getRuntime().availableProcessors() * 2));
        st.executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
        plugin.getLogger().info("开始自动预生成 [" + w.getName() + "]: " + count
                + " 区块 (线程池 " + threadCount + "线程, 每批 " + batch + ", 间隔 " + interval + " tick)");
        final World fw = w;
        final java.io.File marker = new java.io.File(plugin.getDataFolder(), "pregen_" + w.getName() + ".done");
        // 用 done 计数判定真正完成 (所有区块异步生成结束), 而非 queue 空(请求发完≠生成完)
        st.task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> {
            // 完成标准: 队列已空 且 所有区块异步生成结束 (done == queued)
            if (st.queue.isEmpty() && st.done.get() >= st.queuedCount.get()) {
                t.cancel();
                st.task = null;
                try {
                    if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
                    marker.createNewFile();
                } catch (Exception ignored) {}
                long sec = (System.currentTimeMillis() - st.start.get()) / 1000;
                plugin.getLogger().info("[" + fw.getName() + "] 预生成完成: " + st.queuedCount.get()
                        + " 区块, 耗时 " + sec + " 秒 (约 " + (st.queuedCount.get() / Math.max(1, sec)) + " 区块/秒)");
                if (isMain) finish("[" + fw.getName() + "] 预生成完成");
                return;
            }
            if (st.queue.isEmpty()) {
                return; // 队列已空但仍有异步生成收尾, 下一 tick 再查
            }
            int n = 0;
            while (n < batch && !st.queue.isEmpty()) {
                long[] c = st.queue.poll();
                final int cx = (int) c[0], cz = (int) c[1];
                // 使用线程池并发加载多个区块
                st.executor.execute(() -> {
                    fw.getChunkAtAsync(cx, cz).whenComplete((chunk, err) -> {
                        st.done.incrementAndGet();
                        if (st.done.get() % 2048 == 0) {
                            plugin.getLogger().info("预生成进度 [" + fw.getName() + "]: " + st.done.get()
                                    + "/" + st.queuedCount.get());
                        }
                    });
                });
                n++;
            }
        }, 20L, (long) interval);
    }

    private void finish(String msg) {
        ready = true;
        plugin.getLogger().info(msg + " — 物资对战服务启动");
        java.util.List<Runnable> hooks = java.util.List.copyOf(onReadyHooks);
        onReadyHooks.clear();
        for (Runnable hook : hooks) {
            try {
                hook.run();
            } catch (Exception e) {
                plugin.getLogger().warning("世界就绪回调执行失败: " + e.getMessage());
            }
        }
    }

    /** 取消预生成并关闭线程池 (防泄漏) */
    private void cancelPregen(PregenState st) {
        if (st.task != null) {
            try { st.task.cancel(); } catch (Exception ignored) {}
            st.task = null;
        }
        if (st.executor != null) {
            try {
                st.executor.shutdownNow();
                // 等待线程池停止 (最多5秒)
                st.executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception ignored) {}
            st.executor = null;
        }
    }
}
