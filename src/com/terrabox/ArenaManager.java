/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.GameRule
 *  org.bukkit.World
 *  org.bukkit.WorldCreator
 *  org.bukkit.WorldType
 *  org.bukkit.generator.ChunkGenerator
 */
package com.terrabox;

import com.terrabox.CustomTerrainGenerator;
import com.terrabox.TerraBoxPlugin;
import com.terrabox.TerrainType;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.generator.ChunkGenerator;

public class ArenaManager {
    private final TerraBoxPlugin plugin;
    private final Map<String, TerrainType> arenaTerrain = new ConcurrentHashMap<String, TerrainType>();
    private final CopyOnWriteArrayList<String> arenas = new CopyOnWriteArrayList();
    private volatile String currentId;
    private final AtomicInteger nextId = new AtomicInteger(1);

    public ArenaManager(TerraBoxPlugin terraBoxPlugin) {
        this.plugin = terraBoxPlugin;
    }

    public List<String> names() {
        return List.copyOf(this.arenas);
    }

    public String currentId() {
        return this.currentId;
    }

    public TerrainType terrainOf(String string) {
        return this.arenaTerrain.getOrDefault(string, TerrainType.DEFAULT);
    }

    public World world(String string) {
        return Bukkit.getWorld((String)string);
    }

    public World current() {
        return this.currentId != null ? Bukkit.getWorld((String)this.currentId) : null;
    }

    public World create(String string, TerrainType terrainType) {
        World world;
        World world2 = Bukkit.getWorld((String)string);
        if (world2 != null) {
            this.arenaTerrain.put(string, terrainType);
            if (!this.arenas.contains(string)) {
                this.arenas.add(string);
            }
            if (terrainType == TerrainType.NORMAL) {
                this.plugin.getLogger().warning("[TerraBox] \u4e16\u754c " + string + " \u5df2\u5b58\u5728(\u53ef\u80fd\u662f\u65e7\u5730\u5f62\u751f\u6210\u4ea7\u7269)\u3002 \u82e5\u9700\u4ee5\u539f\u7248\u751f\u6210\u5668\u91cd\u65b0\u751f\u6210\u5b8c\u6574\u5730\u8c8c, \u8bf7\u5220\u9664 world/" + string + " \u6587\u4ef6\u5939\u540e\u91cd\u542f\u3002");
            }
            return world2;
        }
        WorldCreator worldCreator = WorldCreator.name((String)string).environment(terrainType.environment());
        if (terrainType == TerrainType.NORMAL) {
            worldCreator.type(WorldType.NORMAL);
        } else {
            worldCreator.generator((ChunkGenerator)new CustomTerrainGenerator(this.plugin, terrainType));
        }
        long l = this.plugin.getConfig().getLong("world.seed", 0L);
        if (l != 0L) {
            worldCreator.seed(l + (long)string.hashCode());
        }
        if ((world = Bukkit.createWorld((WorldCreator)worldCreator)) != null) {
            this.arenaTerrain.put(string, terrainType);
            if (!this.arenas.contains(string)) {
                this.arenas.add(string);
            }
            this.applyBorder(world, terrainType);
            this.plugin.getLogger().info("\u5bf9\u5c40\u4e16\u754c\u5df2\u521b\u5efa: " + string + " [\u5730\u5f62: " + terrainType.display + (terrainType == TerrainType.NORMAL ? " (\u539f\u7248\u751f\u6210\u5668)" : "") + "]");
        }
        return world;
    }

    public void createInitial() {
        int n;
        World world = this.create("arena_1", TerrainType.DEFAULT);
        if (world != null && this.currentId == null) {
            this.currentId = "arena_1";
        }
        int n2 = this.plugin.getConfig().getInt("arena.default.worlds", 1);
        int n3 = this.plugin.getConfig().getInt("arena.desert.worlds", 0);
        int n4 = this.plugin.getConfig().getInt("arena.islands.worlds", 0);
        int n5 = this.plugin.getConfig().getInt("arena.the_end.worlds", 0);
        int n6 = this.plugin.getConfig().getInt("arena.badlands.worlds", 0);
        int n7 = this.plugin.getConfig().getInt("arena.nether.worlds", 0);
        int n8 = this.plugin.getConfig().getInt("arena.city.worlds", 0);
        int n9 = this.plugin.getConfig().getInt("arena.normal.worlds", 0);
        for (n = 0; n < Math.max(0, n2 - 1); ++n) {
            String string = "arena_default_" + (n + 1);
            this.create(string, TerrainType.DEFAULT);
        }
        for (n = 0; n < n3; ++n) {
            this.create("arena_desert_" + (n + 1), TerrainType.DESERT);
        }
        for (n = 0; n < n4; ++n) {
            this.create("arena_islands_" + (n + 1), TerrainType.ISLANDS);
        }
        for (n = 0; n < n5; ++n) {
            this.create("arena_the_end_" + (n + 1), TerrainType.THE_END);
        }
        for (n = 0; n < n6; ++n) {
            this.create("arena_badlands_" + (n + 1), TerrainType.BADLANDS);
        }
        for (n = 0; n < n7; ++n) {
            this.create("arena_nether_" + (n + 1), TerrainType.NETHER);
        }
        for (n = 0; n < n8; ++n) {
            this.create("arena_city_" + (n + 1), TerrainType.CITY);
        }
        for (n = 0; n < n9; ++n) {
            this.create("arena_normal_" + (n + 1), TerrainType.NORMAL);
        }
    }

    public World createNew(TerrainType terrainType) {
        String string;
        String string2 = switch (terrainType) {
            case TerrainType.DESERT -> "arena_desert";
            case TerrainType.ISLANDS -> "arena_islands";
            case TerrainType.THE_END -> "arena_the_end";
            case TerrainType.BADLANDS -> "arena_badlands";
            case TerrainType.NETHER -> "arena_nether";
            case TerrainType.CITY -> "arena_city";
            case TerrainType.NORMAL -> "arena_normal";
            default -> "arena";
        };
        boolean bl = true;
        while (Bukkit.getWorld((String)(string = string2 + "_" + this.nextId.getAndIncrement())) != null) {
        }
        return this.create(string, terrainType);
    }

    private void applyBorder(World world, TerrainType terrainType) {
        double d = terrainType.worldSize();
        world.getWorldBorder().setCenter(0.0, 0.0);
        world.getWorldBorder().setSize(d);
        world.getWorldBorder().setWarningDistance(8);
        world.getWorldBorder().setDamageAmount(1.0);
    }

    public boolean select(String string) {
        World world = Bukkit.getWorld((String)string);
        if (world == null) {
            return false;
        }
        this.currentId = string;
        this.plugin.worlds().ensurePregen(world);
        return true;
    }

    public boolean selectByTerrain(TerrainType terrainType) {
        for (String string : this.arenas) {
            if (this.arenaTerrain.getOrDefault(string, TerrainType.DEFAULT) != terrainType) continue;
            this.currentId = string;
            World world = Bukkit.getWorld((String)string);
            if (world != null) {
                this.plugin.worlds().ensurePregen(world);
            }
            return true;
        }
        World world = this.createNew(terrainType);
        if (world != null) {
            this.currentId = world.getName();
            this.plugin.worlds().ensurePregen(world);
            try {
                world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, (Object)false);
                world.setTime(6000L);
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            return true;
        }
        return false;
    }

    public int totalPregenDone() {
        return this.plugin.worlds().pregenDone();
    }
}
