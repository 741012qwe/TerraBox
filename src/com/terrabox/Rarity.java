/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.kyori.adventure.text.format.NamedTextColor
 *  org.bukkit.plugin.java.JavaPlugin
 */
package com.terrabox;

import com.terrabox.TerraBoxPlugin;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.plugin.java.JavaPlugin;

public enum Rarity {
    COMMON("\u666e\u901a", "&f", NamedTextColor.WHITE, 50),
    RARE("\u7cbe\u826f", "&a", NamedTextColor.GREEN, 27),
    EPIC("\u7a00\u6709", "&b", NamedTextColor.AQUA, 15),
    LEGENDARY("\u4f20\u8bf4", "&6", NamedTextColor.GOLD, 7),
    MYTHIC("\u7edd\u4e16", "&d", NamedTextColor.LIGHT_PURPLE, 1);

    public final String display;
    public final String colorCode;
    public final NamedTextColor color;
    public final int defaultWeight;

    private Rarity(String string2, String string3, NamedTextColor namedTextColor, int n2) {
        this.display = string2;
        this.colorCode = string3;
        this.color = namedTextColor;
        this.defaultWeight = n2;
    }

    public static Rarity weightedPick() {
        int n;
        int n2 = 0;
        int[] nArray = new int[Rarity.values().length];
        for (n = 0; n < Rarity.values().length; ++n) {
            nArray[n] = Math.max(0, Rarity.values()[n].weight());
            n2 += nArray[n];
        }
        if (n2 <= 0) {
            return COMMON;
        }
        n = ThreadLocalRandom.current().nextInt(n2);
        for (int i = 0; i < nArray.length; ++i) {
            if ((n -= nArray[i]) >= 0) continue;
            return Rarity.values()[i];
        }
        return COMMON;
    }

    public int minStacks() {
        return switch (this.ordinal()) {
            default -> throw new MatchException(null, null);
            case 0 -> 3;
            case 1 -> 4;
            case 2 -> 5;
            case 3 -> 6;
            case 4 -> 7;
        };
    }

    public int maxStacks() {
        return switch (this.ordinal()) {
            default -> throw new MatchException(null, null);
            case 0 -> 6;
            case 1 -> 7;
            case 2 -> 8;
            case 3 -> 9;
            case 4 -> 10;
        };
    }

    public int weight() {
        try {
            return ((TerraBoxPlugin)JavaPlugin.getPlugin(TerraBoxPlugin.class)).getConfig().getInt("loot." + this.key() + ".weight", this.defaultWeight);
        }
        catch (Exception exception) {
            return this.defaultWeight;
        }
    }

    public String key() {
        return this.name();
    }

    public static Rarity parse(String string) {
        if (string == null) {
            return null;
        }
        for (Rarity rarity : Rarity.values()) {
            if (!rarity.name().equalsIgnoreCase(string.trim()) && !rarity.display.equals(string.trim())) continue;
            return rarity;
        }
        return null;
    }
}
