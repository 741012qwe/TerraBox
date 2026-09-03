/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.papermc.paper.threadedregions.RegionizedServerInitEvent
 *  io.papermc.paper.threadedregions.scheduler.ScheduledTask
 *  org.bukkit.Bukkit
 *  org.bukkit.GameRule
 *  org.bukkit.World
 *  org.bukkit.World$Environment
 *  org.bukkit.WorldCreator
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.generator.ChunkGenerator
 *  org.bukkit.plugin.Plugin
 */
package com.terrabox;

import com.terrabox.CustomTerrainGenerator;
import com.terrabox.TerraBoxPlugin;
import com.terrabox.TerrainType;
import io.papermc.paper.threadedregions.RegionizedServerInitEvent;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.io.File;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;

public class WorldService
implements Listener {
    private final TerraBoxPlugin plugin;
    private volatile World lobby;
    private volatile double lobbyHalf = 256.0;
    private final Map<String, World> arenaWorlds = new ConcurrentHashMap<String, World>();
    private final AtomicBoolean worldInit = new AtomicBoolean(false);
    private volatile boolean ready = false;
    private final Map<String, PregenState> pregenStates = new ConcurrentHashMap<String, PregenState>();
    private final List<Runnable> onReadyHooks = new CopyOnWriteArrayList<Runnable>();

    public WorldService(TerraBoxPlugin terraBoxPlugin) {
        this.plugin = terraBoxPlugin;
        Bukkit.getPluginManager().registerEvents((Listener)this, (Plugin)terraBoxPlugin);
    }

    public void whenReady(Runnable runnable) {
        if (this.ready) {
            runnable.run();
            return;
        }
        this.onReadyHooks.add(runnable);
    }

    public World world() {
        return this.plugin.arenas() != null ? this.plugin.arenas().current() : null;
    }

    public World lobby() {
        return this.lobby;
    }

    public boolean isReady() {
        return this.ready;
    }

    public int pregenTotal() {
        int n = 0;
        for (PregenState pregenState : this.pregenStates.values()) {
            n += pregenState.queuedCount.get();
        }
        return n;
    }

    public int pregenDone() {
        int n = 0;
        for (PregenState pregenState : this.pregenStates.values()) {
            n += pregenState.done.get();
        }
        return n;
    }

    public boolean pregenRunning() {
        for (PregenState pregenState : this.pregenStates.values()) {
            if (pregenState.task == null) continue;
            return true;
        }
        return false;
    }

    public double borderHalf() {
        return this.plugin.arenas() != null && this.plugin.arenas().current() != null ? this.plugin.arenas().current().getWorldBorder().getSize() / 2.0 : 512.0;
    }

    public double lobbyHalf() {
        return this.lobbyHalf;
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onServerInit(RegionizedServerInitEvent regionizedServerInitEvent) {
        Bukkit.getGlobalRegionScheduler().run((Plugin)this.plugin, scheduledTask -> this.initWorld());
    }

    public void initWorld() {
        if (!this.worldInit.compareAndSet(false, true)) {
            this.plugin.getLogger().info("\u4e16\u754c\u521d\u59cb\u5316\u5df2\u5728\u8fdb\u884c\u4e2d, \u8df3\u8fc7\u91cd\u590d\u8c03\u7528");
            return;
        }
        try {
            this.doInitWorld();
        }
        catch (Throwable throwable) {
            this.plugin.getLogger().severe("\u4e16\u754c\u521d\u59cb\u5316\u5931\u8d25: " + String.valueOf(throwable));
            this.worldInit.set(false);
        }
    }

    private void doInitWorld() {
        CustomTerrainGenerator customTerrainGenerator;
        String string = this.plugin.getConfig().getString("lobby.name", "terra_lobby");
        if (Bukkit.getWorld((String)string) != null) {
            this.lobby = Bukkit.getWorld((String)string);
        } else {
            customTerrainGenerator = new CustomTerrainGenerator(this.plugin, TerrainType.DEFAULT);
            WorldCreator worldCreator = WorldCreator.name((String)string).environment(World.Environment.NORMAL).generator((ChunkGenerator)customTerrainGenerator);
            long l = this.plugin.getConfig().getLong("world.seed", 0L);
            if (l != 0L) {
                worldCreator.seed(l + 9999L);
            }
            this.lobby = Bukkit.createWorld((WorldCreator)worldCreator);
        }
        if (this.lobby != null) {
            double d = this.plugin.getConfig().getDouble("lobby.border-size", 512.0);
            this.lobbyHalf = d / 2.0;
            this.lobby.getWorldBorder().setCenter(0.0, 0.0);
            this.lobby.getWorldBorder().setSize(d);
            this.lobby.getWorldBorder().setWarningDistance(8);
            this.lobby.getWorldBorder().setDamageAmount(1.0);
            try {
                this.lobby.setPVP(false);
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            this.forceDaytime(this.lobby);
            this.plugin.getLogger().info("\u5927\u5385\u4e16\u754c\u5df2\u521b\u5efa/\u52a0\u8f7d: " + string + " (\u8fb9\u754c " + (int)d + "x" + (int)d + ")");
        }
        if (this.plugin.arenas() != null) {
            this.plugin.arenas().createInitial();
            customTerrainGenerator = this.plugin.arenas().current();
            if (customTerrainGenerator != null) {
                this.forceDaytime((World)customTerrainGenerator);
                this.applyBorder((World)customTerrainGenerator);
            }
            for (String string2 : this.plugin.arenas().names()) {
                World world = Bukkit.getWorld((String)string2);
                if (world == null) continue;
                this.forceDaytime(world);
                this.applyBorder(world);
            }
        }
        if ((customTerrainGenerator = this.world()) != null) {
            this.startPregenIfNeeded((World)customTerrainGenerator, true);
        }
    }

    private void applyBorder(World world) {
        double d = world.getWorldBorder().getSize();
        world.getWorldBorder().setCenter(0.0, 0.0);
        world.getWorldBorder().setSize(d);
        world.getWorldBorder().setWarningDistance(8);
        world.getWorldBorder().setDamageAmount(1.0);
    }

    private void forceDaytime(World world) {
        try {
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, (Object)false);
        }
        catch (Throwable throwable) {
            this.plugin.getLogger().warning("\u8bbe\u7f6e \u7981\u663c\u591c\u5faa\u73af \u5931\u8d25: " + throwable.getMessage());
        }
        try {
            world.setTime(6000L);
        }
        catch (Throwable throwable) {
            this.plugin.getLogger().warning("\u8bbe\u7f6e\u65f6\u95f4\u9501\u5b9a\u5931\u8d25: " + throwable.getMessage());
        }
    }

    public void ensurePregen(World world) {
        if (world == null) {
            return;
        }
        PregenState pregenState = this.pregenStates.get(world.getName());
        if (pregenState != null && pregenState.task != null) {
            return;
        }
        File file = new File(this.plugin.getDataFolder(), "pregen_" + world.getName() + ".done");
        if (file.isFile()) {
            return;
        }
        this.pregenWorld(world, false);
    }

    public boolean worldReady(String string) {
        File file = new File(this.plugin.getDataFolder(), "pregen_" + string + ".done");
        if (file.isFile()) {
            return true;
        }
        PregenState pregenState = this.pregenStates.get(string);
        return pregenState != null && pregenState.task == null && pregenState.queuedCount.get() > 0;
    }

    private void startPregenIfNeeded(World world, boolean bl) {
        if (!this.plugin.getConfig().getBoolean("world.pregen", true)) {
            this.finish("\u5df2\u8df3\u8fc7\u9884\u751f\u6210(\u914d\u7f6e\u5173\u95ed)");
            return;
        }
        File file = new File(this.plugin.getDataFolder(), "pregen_" + world.getName() + ".done");
        if (file.isFile() && !bl) {
            this.finish("[" + world.getName() + "] \u9884\u751f\u6210\u6807\u8bb0\u5b58\u5728, \u8df3\u8fc7");
            return;
        }
        this.pregenWorld(world, true);
    }

    private void pregenWorld(World world, boolean bl) {
        int n;
        int n2;
        PregenState pregenState = this.pregenStates.computeIfAbsent(world.getName(), string -> new PregenState(bl));
        if (pregenState.task != null) {
            return;
        }
        double d = world.getWorldBorder().getSize() / 2.0;
        int n3 = (int)Math.floor(-d / 16.0) - 1;
        int n4 = (int)Math.ceil(d / 16.0);
        long l = 0L;
        pregenState.queue.clear();
        pregenState.done.set(0);
        for (n2 = n3; n2 < n4; ++n2) {
            for (n = n3; n < n4; ++n) {
                pregenState.queue.add(new long[]{n2, n});
                ++l;
            }
        }
        pregenState.queuedCount.set((int)l);
        pregenState.start.set(System.currentTimeMillis());
        n2 = Math.max(1, this.plugin.getConfig().getInt("world.pregen-batch", 5000));
        n = Math.max(1, this.plugin.getConfig().getInt("world.pregen-interval-ticks", 1));
        pregenState.executor = Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors()));
        this.plugin.getLogger().info("\u5f00\u59cb\u81ea\u52a8\u9884\u751f\u6210 [" + world.getName() + "]: " + l + " \u533a\u5757 (\u5e76\u53d1 " + pregenState.executor.toString() + ", \u6bcf\u6279 " + n2 + ", \u95f4\u9694 " + n + " tick)");
        World world2 = world;
        File file = new File(this.plugin.getDataFolder(), "pregen_" + world.getName() + ".done");
        pregenState.task = Bukkit.getGlobalRegionScheduler().runAtFixedRate((Plugin)this.plugin, scheduledTask -> {
            if (pregenState.queue.isEmpty() && pregenState.done.get() >= pregenState.queuedCount.get()) {
                scheduledTask.cancel();
                pregenState.task = null;
                try {
                    if (!this.plugin.getDataFolder().exists()) {
                        this.plugin.getDataFolder().mkdirs();
                    }
                    file.createNewFile();
                }
                catch (Exception exception) {
                    // empty catch block
                }
                long l = (System.currentTimeMillis() - pregenState.start.get()) / 1000L;
                this.plugin.getLogger().info("[" + world2.getName() + "] \u9884\u751f\u6210\u5b8c\u6210: " + pregenState.queuedCount.get() + " \u533a\u5757, \u8017\u65f6 " + l + " \u79d2 (\u7ea6 " + (long)pregenState.queuedCount.get() / Math.max(1L, l) + " \u533a\u5757/\u79d2)");
                if (bl) {
                    this.finish("[" + world2.getName() + "] \u9884\u751f\u6210\u5b8c\u6210");
                }
                return;
            }
            if (pregenState.queue.isEmpty()) {
                return;
            }
            for (int i = 0; i < n2 && !pregenState.queue.isEmpty(); ++i) {
                long[] lArray = pregenState.queue.poll();
                int n2 = (int)lArray[0];
                int n3 = (int)lArray[1];
                pregenState.executor.execute(() -> world2.getChunkAtAsync(n2, n3).whenComplete((chunk, throwable) -> {
                    pregenState.done.incrementAndGet();
                    if (pregenState.done.get() % 2048 == 0) {
                        this.plugin.getLogger().info("\u9884\u751f\u6210\u8fdb\u5ea6 [" + world2.getName() + "]: " + pregenState.done.get() + "/" + pregenState.queuedCount.get());
                    }
                }));
            }
        }, 20L, (long)n);
    }

    private void finish(String string) {
        this.ready = true;
        this.plugin.getLogger().info(string + " \u2014 \u7269\u8d44\u5bf9\u6218\u670d\u52a1\u542f\u52a8");
        List<Runnable> list = List.copyOf(this.onReadyHooks);
        this.onReadyHooks.clear();
        for (Runnable runnable : list) {
            try {
                runnable.run();
            }
            catch (Exception exception) {
                this.plugin.getLogger().warning("\u4e16\u754c\u5c31\u7eea\u56de\u8c03\u6267\u884c\u5931\u8d25: " + exception.getMessage());
            }
        }
    }

    private static final class PregenState {
        final ArrayDeque<long[]> queue = new ArrayDeque();
        final AtomicInteger done = new AtomicInteger();
        final AtomicInteger queuedCount = new AtomicInteger();
        final AtomicLong start = new AtomicLong();
        ScheduledTask task;
        final boolean isMain;
        ExecutorService executor;

        PregenState(boolean bl) {
            this.isMain = bl;
        }
    }
}
