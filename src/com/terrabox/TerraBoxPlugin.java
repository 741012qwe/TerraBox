/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
 *  org.bukkit.Bukkit
 *  org.bukkit.GameRule
 *  org.bukkit.Material
 *  org.bukkit.World
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.PluginCommand
 *  org.bukkit.command.TabCompleter
 *  org.bukkit.configuration.ConfigurationSection
 *  org.bukkit.event.Listener
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 */
package com.terrabox;

import com.terrabox.AirdropService;
import com.terrabox.ArenaManager;
import com.terrabox.ArtifactGui;
import com.terrabox.ArtifactListener;
import com.terrabox.ArtifactManager;
import com.terrabox.BigBoxBuilding;
import com.terrabox.BoxManager;
import com.terrabox.CraftGui;
import com.terrabox.CraftManager;
import com.terrabox.Econ;
import com.terrabox.EnchantListener;
import com.terrabox.EnchantManager;
import com.terrabox.GameGui;
import com.terrabox.GameListener;
import com.terrabox.GameManager;
import com.terrabox.GuiListener;
import com.terrabox.HuntService;
import com.terrabox.InviteGui;
import com.terrabox.InviteManager;
import com.terrabox.LobbyBuilder;
import com.terrabox.LootAuditLogger;
import com.terrabox.LootManager;
import com.terrabox.MainMenuGui;
import com.terrabox.PlayerStore;
import com.terrabox.PortalManager;
import com.terrabox.Rarity;
import com.terrabox.RoomGui;
import com.terrabox.RoomManager;
import com.terrabox.ScoreboardManager;
import com.terrabox.SellGui;
import com.terrabox.SpawnAreaBuilder;
import com.terrabox.SpawnManager;
import com.terrabox.SpecialItemListener;
import com.terrabox.SpecialItemManager;
import com.terrabox.TerraCommand;
import com.terrabox.TerrainSelectGui;
import com.terrabox.TerrainType;
import com.terrabox.TerrainValidator;
import com.terrabox.WorldDecorator;
import com.terrabox.WorldService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class TerraBoxPlugin
extends JavaPlugin {
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

    public void onEnable() {
        this.saveDefaultConfig();
        this.loadSellPrices();
        this.econ = new Econ(this);
        this.econ.setup();
        this.players = new PlayerStore(this);
        this.players.start();
        this.specialItems = new SpecialItemManager(this);
        this.specialItems.load();
        this.specialListener = new SpecialItemListener(this);
        this.artifacts = new ArtifactManager(this);
        this.artifacts.load();
        this.artifactListener = new ArtifactListener(this);
        this.enchants = new EnchantManager(this);
        this.enchants.load();
        this.enchantListener = new EnchantListener(this);
        this.crafts = new CraftManager(this);
        this.crafts.load();
        this.craftsGui = new CraftGui(this);
        this.artifactGui = new ArtifactGui(this);
        this.invites = new InviteManager(this);
        this.inviteGui = new InviteGui(this);
        this.roomGui = new RoomGui(this);
        this.portals = new PortalManager(this);
        this.loot = new LootManager(this);
        this.loot.load();
        this.worlds = new WorldService(this);
        this.arenas = new ArenaManager(this);
        this.lobbyBuilder = new LobbyBuilder(this);
        this.bigBox = new BigBoxBuilding(this);
        this.scoreboard = new ScoreboardManager(this);
        this.boxes = new BoxManager(this);
        this.spawns = new SpawnManager(this);
        this.hunts = new HuntService(this);
        this.sells = new SellGui(this);
        this.menus = new MainMenuGui(this);
        this.airdrops = new AirdropService(this);
        this.games = new RoomManager(this);
        this.games.initDefault();
        this.specialItems.start();
        this.spawnArea = new SpawnAreaBuilder(this);
        this.decorator = new WorldDecorator(this);
        this.terrainSelect = new TerrainSelectGui(this);
        this.gameGui = new GameGui(this);
        this.terrainValidator = new TerrainValidator(this);
        this.lootAuditLogger = new LootAuditLogger(this);
        this.worlds.whenReady(() -> this.terrainValidator.validateWorld());
        this.guis = new GuiListener(this);
        this.gameListener = new GameListener(this);
        Bukkit.getPluginManager().registerEvents((Listener)this.guis, (Plugin)this);
        Bukkit.getPluginManager().registerEvents((Listener)this.gameListener, (Plugin)this);
        Bukkit.getPluginManager().registerEvents((Listener)this.specialListener, (Plugin)this);
        Bukkit.getPluginManager().registerEvents((Listener)this.artifactListener, (Plugin)this);
        Bukkit.getPluginManager().registerEvents((Listener)this.enchantListener, (Plugin)this);
        Bukkit.getPluginManager().registerEvents((Listener)this.portals, (Plugin)this);
        this.cmd = new TerraCommand(this);
        PluginCommand pluginCommand = this.getCommand("box");
        if (pluginCommand != null) {
            pluginCommand.setExecutor((CommandExecutor)this.cmd);
            pluginCommand.setTabCompleter((TabCompleter)this.cmd);
        }
        this.worlds.whenReady(() -> {
            this.boxes.start();
            this.airdrops.start();
            this.decorator.build();
            this.portals.buildPortals(this.worlds.world());
            this.lobbyBuilder.build(() -> {
                this.getLogger().info("\u5927\u5385\u6784\u5efa\u5b8c\u6210, \u5f00\u59cb\u9996\u6279\u6295\u653e\u7269\u8d44\u7bb1...");
                this.lobbyBuilder.startSafetyWatch();
                AtomicBoolean atomicBoolean = new AtomicBoolean(false);
                this.spawnArea.build(() -> {
                    if (atomicBoolean.compareAndSet(false, true)) {
                        int n;
                        int n2 = this.boxInitialFill();
                        this.getLogger().info("\u51fa\u751f\u5e7f\u573a\u5c31\u7eea, \u5f00\u59cb\u9996\u6279\u6295\u653e " + n2 + " \u4e2a\u7269\u8d44\u7bb1...");
                        for (n = 0; n < n2; ++n) {
                            this.boxes.spawnRandomBox(this.weightedPickForWorld(), false, null);
                        }
                        n = this.getConfig().getInt("arena.big-buildings", 3);
                        World world = this.worlds.world();
                        if (world != null) {
                            if (this.isEndWorld(world)) {
                                this.getLogger().info("\u672b\u5730\u5c9b\u5c7f: \u8df3\u8fc7\u5927\u578b\u7269\u8d44\u5efa\u7b51 (\u6d6e\u7a7a\u5c9b\u5730\u8c8c)");
                            } else {
                                this.getLogger().info("\u5f00\u59cb\u5efa\u9020 " + n + " \u5ea7\u5927\u578b\u7269\u8d44\u5efa\u7b51...");
                                for (int i = 0; i < n; ++i) {
                                    this.bigBox.buildRandom(world);
                                }
                            }
                        }
                    }
                });
            });
        });
        if (this.worlds.world() == null && !Bukkit.getWorlds().isEmpty()) {
            Bukkit.getGlobalRegionScheduler().run((Plugin)this, scheduledTask -> this.worlds.initWorld());
        }
        this.getLogger().info("\u7269\u8d44\u5927\u9646\u5df2\u542f\u7528! \u7ecf\u6d4e: " + this.econ.name() + " | \u5bf9\u5c40\u4e16\u754c\u6c60: " + this.arenas.names().size() + " \u4e2a | \u5927\u5385: " + this.getConfig().getString("lobby.name", "terra_lobby"));
    }

    public void onDisable() {
        try {
            Bukkit.getAsyncScheduler().cancelTasks((Plugin)this);
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        try {
            Bukkit.getGlobalRegionScheduler().cancelTasks((Plugin)this);
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        if (this.airdrops != null) {
            this.airdrops.shutdown();
        }
        if (this.players != null) {
            this.players.shutdown();
        }
        if (this.boxes != null) {
            this.boxes.shutdown();
        }
        if (this.scoreboard != null) {
            this.scoreboard.shutdown();
        }
        if (this.specialItems != null) {
            this.specialItems.stop();
        }
        this.getLogger().info("\u7269\u8d44\u5927\u9646\u5df2\u5173\u95ed, \u6570\u636e\u5df2\u4fdd\u5b58\u3002");
    }

    public void loadSellPrices() {
        ConcurrentHashMap<String, Double> concurrentHashMap = new ConcurrentHashMap<String, Double>();
        ConfigurationSection configurationSection = this.getConfig().getConfigurationSection("sell.prices");
        if (configurationSection != null) {
            for (String string : configurationSection.getKeys(false)) {
                try {
                    double d = configurationSection.getDouble(string, 0.0);
                    if (!(d > 0.0) || Material.matchMaterial((String)string) == null) continue;
                    concurrentHashMap.put(string, d);
                }
                catch (Exception exception) {}
            }
        }
        this.sellPrices = concurrentHashMap;
        this.getLogger().info("\u56de\u6536\u4ef7\u683c\u8868\u52a0\u8f7d: " + concurrentHashMap.size() + " \u79cd\u7269\u54c1");
    }

    private String prefix() {
        return TerraBoxPlugin.amp(this.getConfig().getString("messages.prefix", "&8[&6\u7269\u8d44\u5927\u9646&8] &r"));
    }

    public String msg(String string) {
        String string2 = this.getConfig().getString("messages." + string, string);
        return this.prefix() + TerraBoxPlugin.amp(string2);
    }

    public String raw(String string) {
        return TerraBoxPlugin.amp(this.getConfig().getString("messages." + string, string));
    }

    public Component component(String string, String ... stringArray) {
        String string2 = this.getConfig().getString("messages." + string, string);
        int n = 0;
        while (n + 1 < stringArray.length) {
            string2 = string2.replace(stringArray[n], stringArray[n + 1] == null ? "?" : stringArray[n + 1]);
            n += 2;
        }
        return LegacyComponentSerializer.legacyAmpersand().deserialize(string2);
    }

    private static String amp(String string) {
        return string == null ? "" : string.replace('&', '\u00a7');
    }

    public Econ econ() {
        return this.econ;
    }

    public PlayerStore players() {
        return this.players;
    }

    public LootManager loot() {
        return this.loot;
    }

    public WorldService worlds() {
        return this.worlds;
    }

    public ArenaManager arenas() {
        return this.arenas;
    }

    public LobbyBuilder lobbyBuilder() {
        return this.lobbyBuilder;
    }

    public BigBoxBuilding bigBox() {
        return this.bigBox;
    }

    public ScoreboardManager scoreboard() {
        return this.scoreboard;
    }

    public BoxManager boxes() {
        return this.boxes;
    }

    public SpawnManager spawns() {
        return this.spawns;
    }

    public HuntService hunts() {
        return this.hunts;
    }

    public SellGui sells() {
        return this.sells;
    }

    public MainMenuGui menus() {
        return this.menus;
    }

    public GuiListener guis() {
        return this.guis;
    }

    public AirdropService airdrops() {
        return this.airdrops;
    }

    public GameManager games() {
        if (this.games != null) {
            return this.games.defaultRoom();
        }
        return null;
    }

    public RoomManager rooms() {
        return this.games;
    }

    public SpawnAreaBuilder spawnArea() {
        return this.spawnArea;
    }

    public WorldDecorator decorator() {
        return this.decorator;
    }

    public TerrainSelectGui terrainSelect() {
        return this.terrainSelect;
    }

    public GameGui gameGui() {
        return this.gameGui;
    }

    public TerrainValidator terrainValidator() {
        return this.terrainValidator;
    }

    public LootAuditLogger lootAuditLogger() {
        return this.lootAuditLogger;
    }

    public SpecialItemManager specialItems() {
        return this.specialItems;
    }

    public ArtifactManager artifacts() {
        return this.artifacts;
    }

    public EnchantManager enchants() {
        return this.enchants;
    }

    public CraftManager crafts() {
        return this.crafts;
    }

    public CraftGui craftsGui() {
        return this.craftsGui;
    }

    public ArtifactGui artifactGui() {
        return this.artifactGui;
    }

    public InviteManager invites() {
        return this.invites;
    }

    public InviteGui inviteGui() {
        return this.inviteGui;
    }

    public RoomGui roomGui() {
        return this.roomGui;
    }

    public PortalManager portals() {
        return this.portals;
    }

    public TerraCommand cmd() {
        return this.cmd;
    }

    public Map<String, Double> sellPrices() {
        return this.sellPrices;
    }

    private String worldLootKey() {
        World world;
        World world2 = world = this.worlds == null ? null : this.worlds.world();
        if (world == null || this.arenas == null) {
            return "default";
        }
        return this.arenas.terrainOf(world.getName()).configKey();
    }

    public int worldBoxConfig(String string, int n) {
        String string2 = this.worldLootKey();
        int n2 = this.getConfig().getInt("world-loot." + string2 + "." + string, Integer.MIN_VALUE);
        if (n2 == Integer.MIN_VALUE) {
            n2 = this.getConfig().getInt("world-loot.default." + string, Integer.MIN_VALUE);
        }
        return n2 == Integer.MIN_VALUE ? n : n2;
    }

    public int boxMaxCount() {
        return this.worldBoxConfig("max-count", this.getConfig().getInt("boxes.max-count", 320));
    }

    public int boxInitialFill() {
        return this.worldBoxConfig("initial-fill", this.getConfig().getInt("boxes.initial-fill", 150));
    }

    public int boxRefillPerCycle() {
        return this.worldBoxConfig("refill-per-cycle", this.getConfig().getInt("boxes.refill-per-cycle", 12));
    }

    public boolean hasWorldLoot() {
        String string = this.worldLootKey();
        return this.getConfig().getConfigurationSection("world-loot." + string) != null;
    }

    public Rarity weightedPickForWorld() {
        int n;
        String string = this.worldLootKey();
        int n2 = 0;
        int[] nArray = new int[Rarity.values().length];
        for (n = 0; n < Rarity.values().length; ++n) {
            Rarity rarity = Rarity.values()[n];
            int n3 = this.getConfig().getInt("world-loot." + string + ".weight." + rarity.key(), Integer.MIN_VALUE);
            if (n3 == Integer.MIN_VALUE) {
                n3 = this.getConfig().getInt("world-loot.default.weight." + rarity.key(), Integer.MIN_VALUE);
            }
            if (n3 == Integer.MIN_VALUE) {
                n3 = rarity.defaultWeight;
            }
            nArray[n] = Math.max(0, n3);
            n2 += nArray[n];
        }
        if (n2 <= 0) {
            return Rarity.COMMON;
        }
        n = ThreadLocalRandom.current().nextInt(n2);
        for (int i = 0; i < nArray.length; ++i) {
            if ((n -= nArray[i]) >= 0) continue;
            return Rarity.values()[i];
        }
        return Rarity.COMMON;
    }

    public void switchArena() {
        World world = this.worlds.world();
        if (world == null) {
            return;
        }
        this.worlds.ensurePregen(world);
        boolean bl = this.isEndWorld(world);
        Bukkit.getGlobalRegionScheduler().run((Plugin)this, scheduledTask -> {
            try {
                world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, (Object)false);
                world.setTime(6000L);
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            this.boxes().wipeAll(() -> {
                int n = this.boxInitialFill();
                for (int i = 0; i < n; ++i) {
                    this.boxes().spawnRandomBox(this.weightedPickForWorld(), false, null);
                }
                this.spawnArea().build(() -> {
                    this.portals.buildPortals(world);
                    if (bl) {
                        this.getLogger().info("\u672b\u5730\u5c9b\u5c7f: \u8df3\u8fc7\u5927\u578b\u7269\u8d44\u5efa\u7b51 (\u6d6e\u7a7a\u5c9b\u5730\u8c8c)");
                    } else {
                        int n = this.getConfig().getInt("arena.big-buildings", 3);
                        for (int i = 0; i < n; ++i) {
                            this.bigBox().buildRandom(world);
                        }
                    }
                    this.getLogger().info("\u5bf9\u5c40\u4e16\u754c\u5207\u6362\u5b8c\u6210: " + world.getName());
                });
            });
        });
    }

    public boolean isEndWorld(World world) {
        if (world == null || this.arenas == null) {
            return false;
        }
        TerrainType terrainType = this.arenas.terrainOf(world.getName());
        return terrainType == TerrainType.THE_END || terrainType == TerrainType.NETHER;
    }
}
