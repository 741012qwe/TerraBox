package com.terrabox;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 对局世界管理器 (多世界池):
 *  - 维护多个对局世界 (arena_1, arena_2, ...), 每个世界可配置地形模板
 *  - 预创建初始几个世界, 玩家对局时分配到空闲世界
 *  - 每个世界独立预生成, 各自的物资箱/装饰独立
 *
 * 线程模型: 世界创建必须在 Global Region 线程 (Folia 要求 createWorld off global region 抛异常);
 *   预生成由各世界独立驱动 GlobalRegionScheduler 批次。
 */
public class ArenaManager {
    private final TerraBoxPlugin plugin;
    private final Map<String, TerrainType> arenaTerrain = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<String> arenas = new CopyOnWriteArrayList<>();
    private volatile String currentId; // 当前对局使用的世界名
    private final AtomicInteger nextId = new AtomicInteger(1);

    public ArenaManager(TerraBoxPlugin plugin) {
        this.plugin = plugin;
    }

    public List<String> names() {
        return List.copyOf(arenas);
    }

    public String currentId() {
        return currentId;
    }

    public TerrainType terrainOf(String name) {
        return arenaTerrain.getOrDefault(name, TerrainType.DEFAULT);
    }

    public World world(String name) {
        return Bukkit.getWorld(name);
    }

    /** 当前对局世界 */
    public World current() {
        return currentId != null ? Bukkit.getWorld(currentId) : null;
    }

    /** 创建/加载指定名字+地形的世界 (必须在 Global 线程调用) */
    public World create(String name, TerrainType type) {
        return create(name, type, false);
    }

    /** 创建/加载指定名字+地形的世界 (Global 线程调用) */
    public World create(String name, TerrainType type, boolean asyncSafe) {
        // 已在 Global 线程或异步安全模式: 直接创建
        if (!asyncSafe) {
            World existing = Bukkit.getWorld(name);
            if (existing != null) {
                arenaTerrain.put(name, type);
                if (!arenas.contains(name)) arenas.add(name);
                if (type == TerrainType.NORMAL) {
                    plugin.getLogger().warning("[TerraBox] 世界 " + name + " 已存在(可能是旧地形生成产物)。"
                            + " 若需以原版生成器重新生成完整地貌, 请删除 world/" + name + " 文件夹后重启。");
                }
                return existing;
            }
            return createWorld(name, type);
        }

        // 异步安全模式: 必须通过 GlobalRegionScheduler
        java.util.concurrent.atomic.AtomicReference<World> result = new java.util.concurrent.atomic.AtomicReference<>(null);
        java.util.concurrent.atomic.AtomicReference<Throwable> error = new java.util.concurrent.atomic.AtomicReference<>(null);

        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
            try {
                World existing = Bukkit.getWorld(name);
                if (existing != null) {
                    arenaTerrain.put(name, type);
                    if (!arenas.contains(name)) arenas.add(name);
                    result.set(existing);
                    return;
                }
                result.set(createWorld(name, type));
            } catch (Throwable t) {
                error.set(t);
                plugin.getLogger().severe("[TerraBox] 创建世界失败: " + name + " - " + t.getMessage());
                t.printStackTrace();
            }
        });

        // 等待任务完成 (阻塞当前线程, 带超时保护防死锁)
        long start = System.currentTimeMillis();
        while (result.get() == null && error.get() == null) {
            if (System.currentTimeMillis() - start > 5000) {
                throw new RuntimeException("创建世界超时: " + name + " (等待超过5秒)");
            }
            try { Thread.sleep(10); } catch (InterruptedException ignored) {}
        }

        if (error.get() != null) {
            throw new RuntimeException("创建世界失败: " + error.get().getMessage(), error.get());
        }

        return result.get();
    }

    /** 实际创建世界的内部方法 (必须在 Global 线程调用) */
    private World createWorld(String name, TerrainType type) {
        World existing = Bukkit.getWorld(name);
        if (existing != null) {
            arenaTerrain.put(name, type);
            if (!arenas.contains(name)) arenas.add(name);
            if (type == TerrainType.NORMAL) {
                plugin.getLogger().warning("[TerraBox] 世界 " + name + " 已存在(可能是旧地形生成产物)。"
                        + " 若需以原版生成器重新生成完整地貌, 请删除 world/" + name + " 文件夹后重启。");
            }
            return existing;
        }
        // 正常主世界: 直接使用原版自带生成器 (不设自定义 generator), 生成原版多样群系地貌。
        // 恶地: 使用自定义生成器生成纯正恶地 (红沙/彩陶瓦/平顶山丘), 确保是真正的恶地地貌。
        WorldCreator creator = WorldCreator.name(name).environment(type.environment());
        if (type == TerrainType.NORMAL) {
            creator.type(org.bukkit.WorldType.NORMAL);
        } else {
            creator.generator(new CustomTerrainGenerator(plugin, type));
        }
        long seed = plugin.getConfig().getLong("world.seed", 0);
        if (seed != 0) creator.seed(seed + name.hashCode());
        World w = Bukkit.createWorld(creator);
        if (w != null) {
            arenaTerrain.put(name, type);
            if (!arenas.contains(name)) arenas.add(name);
            applyBorder(w, type);
            plugin.getLogger().info("对局世界已创建: " + name + " [地形: " + type.display
                    + (type == TerrainType.NORMAL ? " (原版生成器)" : "") + "]");
        }
        return w;
    }

    /** 设计时预创建初始对局世界 (Global 线程, WorldService 就绪后调用) */
    public void createInitial() {
        // 第一个: 默认地形, 作为默认对局世界
        World main = create("arena_1", TerrainType.DEFAULT);
        if (main != null && currentId == null) currentId = "arena_1";

        // 按配置附加创建其他地形世界
        int defaultCount = plugin.getConfig().getInt("arena.default.worlds", 1);
        int desertCount = plugin.getConfig().getInt("arena.desert.worlds", 0);
        int islandsCount = plugin.getConfig().getInt("arena.islands.worlds", 0);
        int endCount = plugin.getConfig().getInt("arena.the_end.worlds", 0);
        int badlandsCount = plugin.getConfig().getInt("arena.badlands.worlds", 0);
        int netherCount = plugin.getConfig().getInt("arena.nether.worlds", 0);
        int cityCount = plugin.getConfig().getInt("arena.city.worlds", 0);
        int normalCount = plugin.getConfig().getInt("arena.normal.worlds", 0);

        for (int i = 0; i < Math.max(0, defaultCount - 1); i++) {
            String n = "arena_default_" + (i + 1);
            create(n, TerrainType.DEFAULT);
        }
        for (int i = 0; i < desertCount; i++) {
            create("arena_desert_" + (i + 1), TerrainType.DESERT);
        }
        for (int i = 0; i < islandsCount; i++) {
            create("arena_islands_" + (i + 1), TerrainType.ISLANDS);
        }
        for (int i = 0; i < endCount; i++) {
            create("arena_the_end_" + (i + 1), TerrainType.THE_END);
        }
        for (int i = 0; i < badlandsCount; i++) {
            create("arena_badlands_" + (i + 1), TerrainType.BADLANDS);
        }
        for (int i = 0; i < netherCount; i++) {
            create("arena_nether_" + (i + 1), TerrainType.NETHER);
        }
        for (int i = 0; i < cityCount; i++) {
            create("arena_city_" + (i + 1), TerrainType.CITY);
        }
        for (int i = 0; i < normalCount; i++) {
            create("arena_normal_" + (i + 1), TerrainType.NORMAL);
        }
    }

    /** 管理员创建新对局世界 (必须在 Global 线程调用) */
    public World createNew(TerrainType type) {
        String prefix = switch (type) {
            case DESERT -> "arena_desert";
            case ISLANDS -> "arena_islands";
            case THE_END -> "arena_the_end";
            case BADLANDS -> "arena_badlands";
            case NETHER -> "arena_nether";
            case CITY -> "arena_city";
            case NORMAL -> "arena_normal";
            default -> "arena";
        };
        int n = 1;
        String name;
        while (Bukkit.getWorld(name = prefix + "_" + (nextId.getAndIncrement())) != null) {}
        return create(name, type);
    }

    /** 管理员创建新对局世界 (Global 线程安全, 用于异步上下文) */
    public World createNewAsyncSafe(TerrainType type) {
        return createNew(type);
    }

    /** 应用世界边界 (与地形尺寸一致) */
    private void applyBorder(World w, TerrainType type) {
        double size = type.worldSize();
        w.getWorldBorder().setCenter(0, 0);
        w.getWorldBorder().setSize(size);
        w.getWorldBorder().setWarningDistance(8);
        w.getWorldBorder().setDamageAmount(1.0);
    }

    /** 选择当前对局世界 (管理员可从GUI/命令切换地形) */
    public boolean select(String name) {
        World w = Bukkit.getWorld(name);
        if (w == null) return false;
        currentId = name;
        // 确保该世界已预生成 (防玩家进入被卡)
        plugin.worlds().ensurePregen(w);
        return true;
    }

    /** 按地形模板选择当前对局世界 (GUI 用): 寻找或创建该类地形世界 */
    public boolean selectByTerrain(TerrainType type) {
        // Folia: 必须在 Global 线程执行 (可能调用 createWorld)
        java.util.concurrent.atomic.AtomicReference<Boolean> result = new java.util.concurrent.atomic.AtomicReference<>(false);
        java.util.concurrent.atomic.AtomicReference<Throwable> error = new java.util.concurrent.atomic.AtomicReference<>(null);

        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
            try {
                for (String n : arenas) {
                    if (arenaTerrain.getOrDefault(n, TerrainType.DEFAULT) == type) {
                        currentId = n;
                        World w = Bukkit.getWorld(n);
                        if (w != null) plugin.worlds().ensurePregen(w);
                        result.set(true);
                        return;
                    }
                }
                // 需要创建新世界
                World w = createNew(type);
                if (w != null) {
                    currentId = w.getName();
                    plugin.worlds().ensurePregen(w);
                    try {
                        w.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
                        w.setTime(6000);
                    } catch (Throwable ignored) {}
                    result.set(true);
                }
            } catch (Throwable t) {
                error.set(t);
            }
        });

        long start = System.currentTimeMillis();
        while (!result.get()) {
            if (error.get() != null) {
                plugin.getLogger().severe("[TerraBox] selectByTerrain 失败: " + error.get().getMessage());
                return false;
            }
            if (System.currentTimeMillis() - start > 5000) {
                plugin.getLogger().warning("[TerraBox] selectByTerrain 超时");
                return false;
            }
            try { Thread.sleep(10); } catch (InterruptedException ignored) {}
        }
        return result.get();
    }

    /** 总预生成进度 (所有世界) */
    public int totalPregenDone() {
        return plugin.worlds().pregenDone();
    }
}
