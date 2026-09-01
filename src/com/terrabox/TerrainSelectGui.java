package com.terrabox;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * 地形选择 GUI (对局世界地形模板):
 *  管理员选择下一局对局世界的地形 (默认平原 / 沙漠 / 大岛屿 / 末地岛屿 / 恶地 / 下界 / 城市),
 *  选择后由 ArenaManager 分配/创建对应世界, 并把玩家传送到该世界。
 *
 * 线程模型: open 由玩家所在区域线程调用, Inventory 构建为局部对象, 安全。
 */
public class TerrainSelectGui {
    public static final String TITLE = "§8[§6物资大陆§8] §b选择对局地形";
    private final TerraBoxPlugin plugin;

    public TerrainSelectGui(TerraBoxPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        GuiListener.GuiHolder holder = new GuiListener.GuiHolder(GuiListener.Type.TERRAIN);
        Inventory inv = Bukkit.createInventory(holder, 54, TITLE);
        holder.inv = inv;

        String current = plugin.arenas().current() != null ? plugin.arenas().current().getName() : "?";
        TerrainType curType = plugin.arenas().current() != null
                ? plugin.arenas().terrainOf(plugin.arenas().current().getName()) : TerrainType.DEFAULT;

        // 顶部描述
        inv.setItem(4, button(Material.COMPASS, "§b当前对局地形",
                List.of("§7当前世界: §e" + current,
                        "§7地形: " + curType.colorCode + curType.display,
                        "§7尺寸: §f" + curType.worldSize() + "x" + curType.worldSize(), "",
                        "§e点击下方切换地形")));

        // 8 种地形: 第一行 (item 10..17)
        inv.setItem(10, button(Material.GRASS_BLOCK, "§a默认平原地形",
                List.of("§7中心平坦草原, 外围丘陵山地",
                        "§7稀疏树木, 开阔对战",
                        "§7尺寸: §f" + TerrainType.DEFAULT.worldSize() + "x" + TerrainType.DEFAULT.worldSize(), "",
                        "§e点击选择")));
        inv.setItem(11, button(Material.SAND, "§6沙漠风格地形",
                List.of("§7沙丘起伏, 砂岩底层",
                        "§7稀疏仙人掌, 视野开阔",
                        "§7尺寸: §f" + TerrainType.DESERT.worldSize() + "x" + TerrainType.DESERT.worldSize(), "",
                        "§e点击选择")));
        inv.setItem(12, button(Material.OAK_BOAT, "§b大岛屿风格地形",
                List.of("§7随机大小岛屿群, 中间海洋",
                        "§7岛屿互不相连, 需游泳/划船",
                        "§7尺寸: §f" + TerrainType.ISLANDS.worldSize() + "x" + TerrainType.ISLANDS.worldSize(), "",
                        "§e点击选择")));
        inv.setItem(13, button(Material.END_STONE, "§d末地岛屿地形",
                List.of("§7浮空末地石岛群, 黑色天空",
                        "§7主岛+黑曜石柱+末地城塔楼",
                        "§7群系: 末地高地/中地/贫瘠/小岛",
                        "§7尺寸: §f" + TerrainType.THE_END.worldSize() + "x" + TerrainType.THE_END.worldSize(), "",
                        "§e点击选择")));
        inv.setItem(14, button(Material.RED_SAND, "§c恶地地形",
                List.of("§7红沙地表, 彩陶瓦层",
                        "§7被侵蚀的平顶山丘, 裸露金矿",
                        "§7群系: 恶地/侵蚀恶地/林地恶地",
                        "§7尺寸: §f" + TerrainType.BADLANDS.worldSize() + "x" + TerrainType.BADLANDS.worldSize(), "",
                        "§e点击选择")));
        inv.setItem(15, button(Material.NETHERRACK, "§4下界地形",
                List.of("§7地狱岩起伏, 岩浆湖, 红色天空",
                        "§7灵魂沙峡谷, 玄武岩柱",
                        "§7群系: 下界荒原/灵魂沙峡谷/玄武岩三角洲/绯红/诡异森林",
                        "§7尺寸: §f" + TerrainType.NETHER.worldSize() + "x" + TerrainType.NETHER.worldSize(), "",
                        "§e点击选择")));
        inv.setItem(16, button(Material.STONE_BRICKS, "§3城市地形",
                List.of("§7平坦街道网格, 建筑群",
                        "§7混凝土高楼, 公园绿地",
                        "§7群系: 平原",
                        "§7尺寸: §f" + TerrainType.CITY.worldSize() + "x" + TerrainType.CITY.worldSize(), "",
                        "§e点击选择")));
        inv.setItem(17, button(Material.OAK_SAPLING, "§2正常主世界",
                List.of("§7多样地形: 平原/丘陵/山地/河流",
                        "§7有边境墙/围墙, 可进地狱/末地传送门",
                        "§7箱子较少, 品质较低 (基础世界)",
                        "§7尺寸: §f" + TerrainType.NORMAL.worldSize() + "x" + TerrainType.NORMAL.worldSize(), "",
                        "§e点击选择")));

        // 操作按钮
        inv.setItem(22, button(Material.BARRIER, "§c取消",
                List.of("§7关闭选择界面", "", "§e点击取消")));

        inv.setItem(26, button(Material.EMERALD, "§a生成新对局世界",
                List.of("§7按当前选定地形额外创建新世界", "§7需要管理员权限", "", "§e点击生成并加入")));

        p.openInventory(inv);
    }

    private ItemStack button(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(new ArrayList<>(lore));
            it.setItemMeta(meta);
        }
        return it;
    }
}
