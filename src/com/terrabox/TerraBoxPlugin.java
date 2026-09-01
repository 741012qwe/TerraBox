package com.terrabox;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.World;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 物资大陆 TerraBox 主类 (多世界 PVP 吃鸡)
 *
 * 架构:
 *  - 大厅世界 (terra_lobby): 玩家聚集地, 512x512
 *  - 对局世界池 (arena_1~N): 多种地形 (默认/沙漠/大岛屿), 独立预生成
 *  - 对局: 报名→倒计时→出生广场聚集→搜刮物资→PVP→仅剩1人→自动送大厅→恢复地形
 *
 * 启动链 (Folia):
 *  onEnable → 注册监听/命令 → RegionizedServerInitEvent(Global) 创建大厅+对局世界
 *  → 自动预生成(1200区块/s)完成 → 物资箱/空投/装饰/计分板服务启动
 */
public final class TerraBoxPlugin extends JavaPlugin {
    private Econ econ;
    private PlayerStore players;
    private LootManager loot;
    private WorldService worlds;
    private ArenaManager arenas;
    private LobbyBuilder lobbyBuilder;
    private BigBoxBuilding bigBox;
    private ScoreboardManager scoreboard;
    private BoxManager boxes;
    private SpawnManager spawns;
    private HuntService hunts;
    private SellGui sells;
    private MainMenuGui menus;
    private GuiListener guis;
    private GameListener gameListener;
    private AirdropService airdrops;
    private RoomManager games;
    private SpawnAreaBuilder spawnArea;
    private WorldDecorator decorator;
    private TerrainSelectGui terrainSelect;
    private GameGui gameGui;
    private TerrainValidator terrainValidator;
    private LootAuditLogger lootAuditLogger;
    private SpecialItemManager specialItems;
    private SpecialItemListener specialListener;
    private ArtifactManager artifacts;
    private ArtifactListener artifactListener;
    private EnchantManager enchants;
    private EnchantListener enchantListener;
    private CraftManager crafts;
    private CraftGui craftsGui;
    private ArtifactGui artifactGui;
    private InviteManager invites;
    private InviteGui inviteGui;
    private RoomGui roomGui;
    private PortalManager portals;
    private TerraCommand cmd;

    private volatile Map<String, Double> sellPrices = Map.of();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSellPrices();

        econ = new Econ(this);
        econ.setup();

        players = new PlayerStore(this);
        players.start();

        specialItems = new SpecialItemManager(this);
        specialItems.load();
        specialListener = new SpecialItemListener(this);

        artifacts = new ArtifactManager(this);
        artifacts.load();
        artifactListener = new ArtifactListener(this);

        enchants = new EnchantManager(this);
        enchants.load();
        enchantListener = new EnchantListener(this);

        crafts = new CraftManager(this);
        crafts.load();
        craftsGui = new CraftGui(this);
        artifactGui = new ArtifactGui(this);
        invites = new InviteManager(this);
        inviteGui = new InviteGui(this);
        roomGui = new RoomGui(this);

        portals = new PortalManager(this);

        loot = new LootManager(this);
        loot.load();

        worlds = new WorldService(this);
        arenas = new ArenaManager(this);
        lobbyBuilder = new LobbyBuilder(this);
        bigBox = new BigBoxBuilding(this);
        scoreboard = new ScoreboardManager(this);
        boxes = new BoxManager(this);
        spawns = new SpawnManager(this);
        hunts = new HuntService(this);
        sells = new SellGui(this);
        menus = new MainMenuGui(this);
        airdrops = new AirdropService(this);
        games = new RoomManager(this);
        games.initDefault();
        specialItems.start(); // 追踪器后台任务 (需在 games 初始化后启动)
        spawnArea = new SpawnAreaBuilder(this);
        decorator = new WorldDecorator(this);
        terrainSelect = new TerrainSelectGui(this);
        gameGui = new GameGui(this);

        terrainValidator = new TerrainValidator(this);
        lootAuditLogger = new LootAuditLogger(this);

        worlds.whenReady(() -> terrainValidator.validateWorld());

        guis = new GuiListener(this);
        gameListener = new GameListener(this);
        Bukkit.getPluginManager().registerEvents(guis, this);
        Bukkit.getPluginManager().registerEvents(gameListener, this);
        Bukkit.getPluginManager().registerEvents(specialListener, this);
        Bukkit.getPluginManager().registerEvents(artifactListener, this);
        Bukkit.getPluginManager().registerEvents(enchantListener, this);
        Bukkit.getPluginManager().registerEvents(portals, this);

        cmd = new TerraCommand(this);
        PluginCommand pc = getCommand("box");
        if (pc != null) {
            pc.setExecutor(cmd);
            pc.setTabCompleter(cmd);
        }

        // 世界就绪 → 构建大厅 → 构建出生广场+装饰 → 启动物资箱/空投/计分板
        worlds.whenReady(() -> {
            boxes.start();
            airdrops.start();
            decorator.build();
            portals.buildPortals(worlds.world());  // 构建传送门 (主世界/地狱/末地)
            lobbyBuilder.build(() -> {
                getLogger().info("大厅构建完成, 开始首批投放物资箱...");
                lobbyBuilder.startSafetyWatch();
                final java.util.concurrent.atomic.AtomicBoolean once =
                        new java.util.concurrent.atomic.AtomicBoolean(false);
                spawnArea.build(() -> {
                    if (once.compareAndSet(false, true)) {
                        int initial = boxInitialFill();
                        getLogger().info("出生广场就绪, 开始首批投放 " + initial + " 个物资箱...");
                        for (int i = 0; i < initial; i++) {
                            boxes.spawnRandomBox(weightedPickForWorld(), false, null);
                        }
                        // 首批大型物资建筑 (末地浮空岛跳过)
                        int buildings = getConfig().getInt("arena.big-buildings", 3);
                        org.bukkit.World arena = worlds.world();
                        if (arena != null) {
                            if (isEndWorld(arena)) {
                                getLogger().info("末地岛屿: 跳过大型物资建筑 (浮空岛地貌)");
                            } else {
                                getLogger().info("开始建造 " + buildings + " 座大型物资建筑...");
                                for (int i = 0; i < buildings; i++) {
                                    bigBox.buildRandom(arena);
                                }
                            }
                        }
                    }
                });
            });
        });

        // Folia: 世界创建不能在主线程, 交给 RegionizedServerInitEvent(Global);
        // 若已运行且世界未就绪(热载), 调度到 Global 兜底
        if (worlds.world() == null && !Bukkit.getWorlds().isEmpty()) {
            Bukkit.getGlobalRegionScheduler().run(this, t -> worlds.initWorld());
        }

        getLogger().info("物资大陆已启用! 经济: " + econ.name()
                + " | 对局世界池: " + arenas.names().size() + " 个 | 大厅: " +
                getConfig().getString("lobby.name", "terra_lobby"));
    }

    @Override
    public void onDisable() {
        try { Bukkit.getAsyncScheduler().cancelTasks(this); } catch (Throwable ignored) {}
        try { Bukkit.getGlobalRegionScheduler().cancelTasks(this); } catch (Throwable ignored) {}
        if (airdrops != null) airdrops.shutdown();
        if (players != null) players.shutdown();
        if (boxes != null) boxes.shutdown();
        if (scoreboard != null) scoreboard.shutdown();
        if (specialItems != null) specialItems.stop();
        getLogger().info("物资大陆已关闭, 数据已保存。");
    }

    public void loadSellPrices() {
        Map<String, Double> map = new ConcurrentHashMap<>();
        ConfigurationSection sec = getConfig().getConfigurationSection("sell.prices");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                try {
                    double v = sec.getDouble(key, 0);
                    if (v > 0 && org.bukkit.Material.matchMaterial(key) != null) map.put(key, v);
                } catch (Exception ignored) {}
            }
        }
        this.sellPrices = map;
        getLogger().info("回收价格表加载: " + map.size() + " 种物品");
    }

    // ==================== 消息工具 ====================

    private String prefix() {
        return amp(getConfig().getString("messages.prefix", "&8[&6物资大陆&8] &r"));
    }

    public String msg(String key) {
        String raw = getConfig().getString("messages." + key, key);
        return prefix() + amp(raw);
    }

    public String raw(String key) {
        return amp(getConfig().getString("messages." + key, key));
    }

    public net.kyori.adventure.text.Component component(String key, String... kv) {
        String raw = getConfig().getString("messages." + key, key);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            raw = raw.replace(kv[i], kv[i + 1] == null ? "?" : kv[i + 1]);
        }
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacyAmpersand().deserialize(raw);
    }

    private static String amp(String s) {
        return s == null ? "" : s.replace('&', '\u00A7');
    }

    // ==================== 组件访问器 ====================

    public Econ econ() { return econ; }
    public PlayerStore players() { return players; }
    public LootManager loot() { return loot; }
    public WorldService worlds() { return worlds; }
    public ArenaManager arenas() { return arenas; }
    public LobbyBuilder lobbyBuilder() { return lobbyBuilder; }
    public BigBoxBuilding bigBox() { return bigBox; }
    public ScoreboardManager scoreboard() { return scoreboard; }
    public BoxManager boxes() { return boxes; }
    public SpawnManager spawns() { return spawns; }
    public HuntService hunts() { return hunts; }
    public SellGui sells() { return sells; }
    public MainMenuGui menus() { return menus; }
    public GuiListener guis() { return guis; }
    public AirdropService airdrops() { return airdrops; }
    public GameManager games() { if (games != null) return games.defaultRoom(); return null; }
    public RoomManager rooms() { return games; }
    public SpawnAreaBuilder spawnArea() { return spawnArea; }
    public WorldDecorator decorator() { return decorator; }
    public TerrainSelectGui terrainSelect() { return terrainSelect; }
    public GameGui gameGui() { return gameGui; }
    public TerrainValidator terrainValidator() { return terrainValidator; }
    public LootAuditLogger lootAuditLogger() { return lootAuditLogger; }
    public SpecialItemManager specialItems() { return specialItems; }
    public ArtifactManager artifacts() { return artifacts; }
    public EnchantManager enchants() { return enchants; }
    public CraftManager crafts() { return crafts; }
    public CraftGui craftsGui() { return craftsGui; }

    public ArtifactGui artifactGui() { return artifactGui; }

    public InviteManager invites() { return invites; }

    public InviteGui inviteGui() { return inviteGui; }

    public RoomGui roomGui() { return roomGui; }
    public PortalManager portals() { return portals; }
    public TerraCommand cmd() { return cmd; }
    public Map<String, Double> sellPrices() { return sellPrices; }

    // ==================== 各世界战利品差异化配置 ====================

    /** 当前世界的地形类型键 (用于 world-loot.<key>) */
    private String worldLootKey() {
        World w = worlds == null ? null : worlds.world();
        if (w == null || arenas == null) return "default";
        return arenas.terrainOf(w.getName()).configKey();
    }

    /** 读取某世界类型的箱子参数 (不足回退 default → 全局) */
    public int worldBoxConfig(String key, int globalDefault) {
        String wk = worldLootKey();
        int v = getConfig().getInt("world-loot." + wk + "." + key, Integer.MIN_VALUE);
        if (v == Integer.MIN_VALUE) {
            v = getConfig().getInt("world-loot.default." + key, Integer.MIN_VALUE);
        }
        return v == Integer.MIN_VALUE ? globalDefault : v;
    }

    /** 当前世界的箱子数量上限 */
    public int boxMaxCount() {
        return worldBoxConfig("max-count", getConfig().getInt("boxes.max-count", 320));
    }

    /** 当前世界的首批投放数量 */
    public int boxInitialFill() {
        return worldBoxConfig("initial-fill", getConfig().getInt("boxes.initial-fill", 150));
    }

    /** 当前世界的每周期补充数量 */
    public int boxRefillPerCycle() {
        return worldBoxConfig("refill-per-cycle", getConfig().getInt("boxes.refill-per-cycle", 12));
    }

    /** 当前世界是否启用该箱子参数覆盖 */
    public boolean hasWorldLoot() {
        String wk = worldLootKey();
        return getConfig().getConfigurationSection("world-loot." + wk) != null;
    }

    /** 按当前世界类型抽取品质 (world-loot.<key>.weight 覆盖全局, 主世界品质低下) */
    public Rarity weightedPickForWorld() {
        String wk = worldLootKey();
        int total = 0;
        int[] weights = new int[Rarity.values().length];
        for (int i = 0; i < Rarity.values().length; i++) {
            Rarity r = Rarity.values()[i];
            int w = getConfig().getInt("world-loot." + wk + ".weight." + r.key(), Integer.MIN_VALUE);
            if (w == Integer.MIN_VALUE) {
                w = getConfig().getInt("world-loot.default.weight." + r.key(), Integer.MIN_VALUE);
            }
            if (w == Integer.MIN_VALUE) w = r.defaultWeight; // 回退默认
            weights[i] = Math.max(0, w);
            total += weights[i];
        }
        if (total <= 0) return Rarity.COMMON;
        int rnv = java.util.concurrent.ThreadLocalRandom.current().nextInt(total);
        for (int i = 0; i < weights.length; i++) {
            rnv -= weights[i];
            if (rnv < 0) return Rarity.values()[i];
        }
        return Rarity.COMMON;
    }

    /** 切换对局世界后重新初始化 (清箱重投 + 重建广场/装饰/建筑), 任意线程安全 */
    public void switchArena() {
        org.bukkit.World arena = worlds.world();
        if (arena == null) return;
        // 确保该世界已预生成
        worlds.ensurePregen(arena);
        boolean isEnd = isEndWorld(arena);
        Bukkit.getGlobalRegionScheduler().run(this, t -> {
            try {
                arena.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
                arena.setTime(6000);
            } catch (Throwable ignored) {}
            // 清空旧世界箱子 + 重投新世界
            boxes().wipeAll(() -> {
                int initial = boxInitialFill();
                for (int i = 0; i < initial; i++) {
                    boxes().spawnRandomBox(weightedPickForWorld(), false, null);
                }
                spawnArea().build(() -> {
                    // 构建传送门 (主世界/地狱/末地)
                    portals.buildPortals(arena);
                    // 末地浮空岛跳过大型物资建筑 (避免破坏浮空岛地貌)
                    if (isEnd) {
                        getLogger().info("末地岛屿: 跳过大型物资建筑 (浮空岛地貌)");
                    } else {
                        int buildings = getConfig().getInt("arena.big-buildings", 3);
                        for (int i = 0; i < buildings; i++) {
                            bigBox().buildRandom(arena);
                        }
                    }
                    getLogger().info("对局世界切换完成: " + arena.getName());
                });
            });
        });
    }

    /** 判断世界是否为特殊地貌 (末地浮空岛 / 下界岩浆地形), 此类世界跳过大型物资建筑 */
    public boolean isEndWorld(org.bukkit.World w) {
        if (w == null || arenas == null) return false;
        TerrainType t = arenas.terrainOf(w.getName());
        return t == TerrainType.THE_END || t == TerrainType.NETHER;
    }
}
