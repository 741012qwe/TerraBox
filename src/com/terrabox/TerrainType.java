/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.World$Environment
 *  org.bukkit.plugin.java.JavaPlugin
 */
package com.terrabox;

import com.terrabox.TerraBoxPlugin;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

public enum TerrainType {
    DEFAULT("\u9ed8\u8ba4\u5e73\u539f", "&a", 2048),
    DESERT("\u6c99\u6f20\u98ce\u683c", "&6", 2048),
    ISLANDS("\u5927\u5c9b\u5c7f\u98ce\u683c", "&b", 2048),
    THE_END("\u672b\u5730\u5c9b\u5c7f", "&d", 2048),
    BADLANDS("\u6076\u5730", "&c", 2048),
    NETHER("\u4e0b\u754c", "&4", 2048),
    CITY("\u57ce\u5e02", "&3", 2048),
    NORMAL("\u6b63\u5e38\u4e3b\u4e16\u754c", "&2", 2048);

    public final String display;
    public final String colorCode;
    public final int defaultSize;

    private TerrainType(String string2, String string3, int n2) {
        this.display = string2;
        this.colorCode = string3;
        this.defaultSize = n2;
    }

    public static TerrainType parse(String string) {
        if (string == null) {
            return DEFAULT;
        }
        for (TerrainType terrainType : TerrainType.values()) {
            if (!terrainType.name().equalsIgnoreCase(string.trim()) && !terrainType.display.equals(string.trim())) continue;
            return terrainType;
        }
        return DEFAULT;
    }

    public int worldSize() {
        try {
            return Math.max(512, ((TerraBoxPlugin)JavaPlugin.getPlugin(TerraBoxPlugin.class)).getConfig().getInt("arena." + this.configKey() + ".size", this.defaultSize));
        }
        catch (Exception exception) {
            return this.defaultSize;
        }
    }

    public World.Environment environment() {
        return switch (this.ordinal()) {
            case 3 -> World.Environment.THE_END;
            case 5 -> World.Environment.NETHER;
            default -> World.Environment.NORMAL;
        };
    }

    public String configKey() {
        return this.name().toLowerCase();
    }
}
