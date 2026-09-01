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
 * 主菜单 GUI (27格): 玩法入口总览
 * 线程模型: open 由玩家所在区域线程调用 (命令/事件), Inventory 构建为局部对象, 安全
 */
public class MainMenuGui {
    public static final String TITLE = "§8[§6物资大陆§8] §2主菜单";

    private final TerraBoxPlugin plugin;

    public MainMenuGui(TerraBoxPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        GuiListener.GuiHolder holder = new GuiListener.GuiHolder(GuiListener.Type.MENU);
        Inventory inv = Bukkit.createInventory(holder, 27, TITLE);
        holder.inv = inv;
        inv.setItem(4, info());

        inv.setItem(10, button(Material.ENDER_PEARL, "§a随机陆地出生",
                List.of("§7传送到地图内随机陆地坐标", "§7冷却 " + plugin.getConfig()
                        .getInt("spawn.command-cooldown-seconds", 300) + " 秒", "", "§e点击传送")));
        inv.setItem(12, button(Material.EMERALD, "§2物资回收商店",
                List.of("§7把矿资源和战利品卖给商店换钱", "§7支持不可堆叠装备逐件结算", "", "§e点击打开")));
        inv.setItem(14, button(Material.COMPASS, "§b寻宝",
                List.of("§7花费 §e" + (long) plugin.getConfig().getDouble("hunt.cost", 120)
                        + " §7元购买一个", "§7高稀有度物资箱的方位提示", "", "§e点击寻宝")));
        inv.setItem(16, button(Material.GOLD_INGOT, "§6开箱排行榜",
                List.of("§7查看全服开箱次数 TOP10", "", "§e点击查看")));

        inv.setItem(11, button(Material.CHEST, "§d地图物资箱分布",
                List.of("§7当前地图各稀有度箱子数量", "", "§e点击查看")));
        inv.setItem(15, button(Material.NETHER_STAR, "§f我的统计",
                List.of("§7查看你的开箱/回收/寻宝统计", "", "§e点击查看")));
        inv.setItem(13, button(Material.BOOK, "§e玩法说明",
                List.of("§f1. §7全图随机投放五档物资箱", "§f2. §7定时空投+全服坐标广播, 争抢传说箱",
                        "§f3. §7寻宝购买高稀有箱方位", "§f4. §7物资卖商店换钱",
                        "§f5. §7地图 " + (int)plugin.getConfig().getDouble("world.border-size", 2048) + "x" + (int)plugin.getConfig().getDouble("world.border-size", 2048) + " 固定边界", "", "§7箱子被搬空后会在别处重生, 手快有手慢无!")));

        // 对局玩法
        boolean inGame = plugin.rooms().isInGame(p.getUniqueId());
        inv.setItem(18, button(Material.CHEST_MINECART,
                inGame ? "§c退出对局" : "§a参加对局",
                List.of("§7单人 / 多人PVP / 组队 三种模式",
                        "§7当前模式: §e" + plugin.games().modeDisplay(),
                        "§7状态: §b" + plugin.games().stateDisplay(),
                        "", "§e点击选择模式报名")));
        inv.setItem(22, button(Material.CLOCK, "§e对局状态",
                List.of("§7查看当前对局信息", "", "§e点击查看")));

        // 多世界入口
        inv.setItem(19, button(Material.OAK_DOOR, "§b返回大厅",
                List.of("§7传送回玩家聚集地大厅", "", "§e点击返回")));
        inv.setItem(21, button(Material.FILLED_MAP, "§d选择对局地形",
                List.of("§7选择对局世界的地形模板:",
                        "§7默认平原 / 沙漠风格 / 大岛屿风格",
                        "§7(仅管理员)", "", "§e点击选择")));
        inv.setItem(17, button(Material.CHEST_MINECART, "§a对局房间",
                List.of("§7查看所有在线房间, 加入/创建/邀请",
                        "§7邀请在线玩家加入你的房间",
                        "", "§e点击打开房间列表")));

        p.openInventory(inv);
    }

    private ItemStack info() {
        double size = plugin.getConfig().getDouble("world.border-size", 2048);
        ItemStack it = button(Material.GRASS_BLOCK, "§6§l物资大陆",
                List.of("§7固定地图: §b" + (int)size + "x" + (int)size,
                        "§7当前物资箱: §a" + plugin.boxes().count() + " 个",
                        "§7经济: §e" + plugin.econ().name(),
                        "§7空投: §d" + (plugin.airdrops().secondsUntilNext() / 60) + " 分钟后"));
        return it;
    }

    static ItemStack button(Material mat, String name, List<String> lore) {
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
