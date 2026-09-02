package com.terrabox;

import net.kyori.adventure.text.format.NamedTextColor;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 物资箱稀有度五档: 普通/精良/稀有/传说/绝世
 * 线程说明: 枚举不可变, 可在任意线程使用 (Global/Region/Async)
 */
public enum Rarity {
    COMMON("普通", "&f", NamedTextColor.WHITE, 50),
    RARE("精良", "&a", NamedTextColor.GREEN, 27),
    EPIC("稀有", "&b", NamedTextColor.AQUA, 15),
    LEGENDARY("传说", "&6", NamedTextColor.GOLD, 7),
    MYTHIC("绝世", "&d", NamedTextColor.LIGHT_PURPLE, 1);

    public final String display;
    public final String colorCode;
    public final NamedTextColor color;
    public final int defaultWeight;

    Rarity(String display, String colorCode, NamedTextColor color, int defaultWeight) {
        this.display = display;
        this.colorCode = colorCode;
        this.color = color;
        this.defaultWeight = defaultWeight;
    }

    /** 按配置权重随机抽取一档 (任意线程可调用) */
    public static Rarity weightedPick() {
        int total = 0;
        int[] weights = new int[values().length];
        for (int i = 0; i < values().length; i++) {
            weights[i] = Math.max(0, values()[i].weight());
            total += weights[i];
        }
        if (total <= 0) return COMMON;
        int r = ThreadLocalRandom.current().nextInt(total);
        for (int i = 0; i < weights.length; i++) {
            r -= weights[i];
            if (r < 0) return values()[i];
        }
        return COMMON;
    }

    /** 每箱目标堆数下限 (平衡性默认, 可用 loot.<key>.min-stacks 覆盖) */
    public int minStacks() {
        return switch (this) {
            case COMMON -> 3;
            case RARE -> 4;
            case EPIC -> 5;
            case LEGENDARY -> 6;
            case MYTHIC -> 7;
        };
    }

    /** 每箱目标堆数上限 (平衡性默认, 可用 loot.<key>.max-stacks 覆盖) */
    public int maxStacks() {
        return switch (this) {
            case COMMON -> 6;
            case RARE -> 7;
            case EPIC -> 8;
            case LEGENDARY -> 9;
            case MYTHIC -> 10;
        };
    }

    /** 该档配置权重 (config loot.<key>.weight, 失败用默认) */
    public int weight() {
        try {
            return org.bukkit.plugin.java.JavaPlugin.getPlugin(TerraBoxPlugin.class)
                    .getConfig().getInt("loot." + key() + ".weight", defaultWeight);
        } catch (Exception e) {
            return defaultWeight;
        }
    }

    /** 配置键 (与 config.yml 段名一致: 大写枚举名) */
    public String key() {
        return name();
    }

    /** 从字符串解析 (大小写不敏感), 失败返回 null */
    public static Rarity parse(String s) {
        if (s == null) return null;
        for (Rarity r : values()) {
            if (r.name().equalsIgnoreCase(s.trim()) || r.display.equals(s.trim())) return r;
        }
        return null;
    }
}
