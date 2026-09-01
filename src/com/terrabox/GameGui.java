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
 * 对局模式选择 GUI (27格): 单人 / 多人PVP / 组队报名入口
 *
 * 每个模式对应一个独立房间 (GameManager 实例, 绑定当前 arena 世界):
 *   点击模式 → 报名到该模式的房间, 并在 GUI 显示该房间当前状态。
 * 管理员可在此 GUI 直接开始对应模式的对局。
 *
 * 线程模型: 打开由玩家区域线程调用, Inventory 构建为局部对象, 安全。
 */
public class GameGui {
    public static final String TITLE = "§8[§6物资大陆§8] §2选择对局模式";

    private final TerraBoxPlugin plugin;

    public GameGui(TerraBoxPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        GuiListener.GuiHolder holder = new GuiListener.GuiHolder(GuiListener.Type.GAME);
        Inventory inv = Bukkit.createInventory(holder, 27, TITLE);
        holder.inv = inv;
        var rooms = plugin.rooms();

        // 单人模式房间
        GameManager solo = rooms.get("solo");
        inv.setItem(10, modeButton(p, solo, Material.EMERALD, "§a单人模式",
                "§7独自搜刮, 无 PvP, 时间到按开箱+击杀积分结算",
                "§7无人数限制 (管理员可开)"));
        // 多人PVP模式房间
        GameManager pvp = rooms.get("pvp");
        inv.setItem(13, modeButton(p, pvp, Material.DIAMOND_SWORD, "§c多人PVP",
                "§7全图 PvP, 最后存活者获胜",
                "§7需 ≥2 人"));
        // 组队模式房间
        GameManager team = rooms.get("team");
        inv.setItem(16, modeButton(p, team, Material.GOLDEN_APPLE, "§6组队对战",
                "§7自动分队, 同队免伤, 最后存活队伍获胜",
                "§7需 ≥2 人"));

        // 状态与返回
        inv.setItem(4, info(p));
        inv.setItem(18, button(Material.CLOCK, "§e查看对局状态",
                List.of("", "§e点击查看")));
        inv.setItem(22, button(Material.BARRIER, "§c返回主菜单",
                List.of("", "§e点击返回")));

        // 管理员: 开始指定模式对局
        if (p.hasPermission("terrabox.admin")) {
            inv.setItem(24, button(Material.NETHER_STAR, "§6开始对局 (管理员)",
                    List.of("§7点击后选择开始某模式对局",
                            "§7也可用 §e/box room start <solo|pvp|team> <mode>",
                            "", "§e点击打开模式选择")));
        }

        p.openInventory(inv);
    }

    private ItemStack modeButton(Player p, GameManager room, Material mat, String name, String... lines) {
        List<String> lore = new ArrayList<>();
        for (String l : lines) lore.add(l);
        if (room != null) {
            boolean joined = room.isInGame(p.getUniqueId());
            lore.add("");
            lore.add("§7模式房间状态: " + room.stateDisplay());
            lore.add("§7当前参战: §e" + room.playerCount() + " §7人  存活: §a" + room.aliveCount() + " §7人");
            lore.add(joined ? "§c你已报名, 点击退出" : "§e点击报名");
        } else {
            lore.add("");
            lore.add("§e点击报名 (首次创建房间)");
        }
        return button(mat, name, lore);
    }

    private ItemStack info(Player p) {
        return button(Material.GRASS_BLOCK, "§6§l对局大厅",
                List.of("§7当前默认房间: " + plugin.games().stateDisplay(),
                        "§7对局世界: §b" + (plugin.worlds().world() != null
                                ? plugin.worlds().world().getName() : "?"),
                        "§7经济: §e" + plugin.econ().name(),
                        "",
                        "§7管理员: 点击模式后可用 §e/box room start <id> <mode> §7开赛"));
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
