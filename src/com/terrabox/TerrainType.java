package com.terrabox;

import org.bukkit.Material;
import org.bukkit.World;

/**
 * 对局世界地形模板: 决定自定义地形生成器如何生成地表
 *
 *   - DEFAULT  : 默认平原+丘陵 (中心平坦, 外围丘陵山地, 偶发树木)
 *   - DESERT   : 沙漠风格 (沙地/砂岩地表, 仙人掌点缀, 平坦沙丘)
 *   - ISLANDS  : 大岛屿风格 (多个随机大小岛屿, 中间海洋, 岛屿互不相连)
 *   - THE_END  : 末地岛屿风格 (浮空末地石岛群, 主岛+黑曜石柱+末地城塔楼, 黑色天空)
 *   - BADLANDS : 恶地风格 (红沙地表+彩陶瓦层, 被侵蚀的平顶山丘, 裸露金矿/洞穴)
 *   - NETHER   : 下界风格 (地狱岩地表起伏+岩浆湖+灵魂沙峡谷+玄武岩柱, 红色天空)
 *   - CITY     : 城市风格 (平坦地面+街道网格+建筑群+公园绿地)
 *   - NORMAL   : 正常主世界 (多样地形: 平原/丘陵/山地/河流, 大尺寸 2048x2048, 有边境围墙 + 地狱/末地传送门)
 *
 * 线程说明: 枚举不可变, 任意线程可读。
 */
public enum TerrainType {
    DEFAULT("默认平原", "&a", 2048),
    DESERT("沙漠风格", "&6", 2048),
    ISLANDS("大岛屿风格", "&b", 2048),
    THE_END("末地岛屿", "&d", 2048),
    BADLANDS("恶地", "&c", 2048),
    NETHER("下界", "&4", 2048),
    CITY("城市", "&3", 2048),
    NORMAL("正常主世界", "&2", 2048);

    public final String display;
    public final String colorCode;
    public final int defaultSize;

    TerrainType(String display, String colorCode, int defaultSize) {
        this.display = display;
        this.colorCode = colorCode;
        this.defaultSize = defaultSize;
    }

    public static TerrainType parse(String s) {
        if (s == null) return DEFAULT;
        for (TerrainType t : values()) {
            if (t.name().equalsIgnoreCase(s.trim()) || t.display.equals(s.trim())) return t;
        }
        return DEFAULT;
    }

    /** 该模板对应世界尺寸 (config arena.<key>.size) */
    public int worldSize() {
        try {
            return Math.max(512, org.bukkit.plugin.java.JavaPlugin.getPlugin(TerraBoxPlugin.class)
                    .getConfig().getInt("arena." + configKey() + ".size", defaultSize));
        } catch (Exception e) {
            return defaultSize;
        }
    }

    /** 世界环境: 末地岛屿使用 THE_END, 下界使用 NETHER (红色天空/火光), 其余 NORMAL */
    public World.Environment environment() {
        return switch (this) {
            case THE_END -> World.Environment.THE_END;
            case NETHER -> World.Environment.NETHER;
            default -> World.Environment.NORMAL;
        };
    }

    /** config 键名 (小写, 与 arena.<key> 对齐) */
    public String configKey() {
        return name().toLowerCase();
    }
}

