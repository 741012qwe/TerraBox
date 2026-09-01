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
 * 对局房间 GUI (54格) — 查看所有在线房间 + 加入 + 创建
 *
 * 展示每个房间的状态/模式/参战人数, 点击可加入报名。
 * 底部: 创建房间 / 邀请玩家 / 返回主菜单 / 关闭。
 *
 * 线程模型: 打开由玩家区域线程调用, Inventory 构建为局部对象, 安全。
 */
public class RoomGui {
    public static final String TITLE = "§8[§6物资大陆§8] §2对局房间";
    public static final int CREATE_SLOT = 28;
    public static final int INVITE_SLOT = 30;
    public static final int BACK_SLOT = 22;
    public static final int CLOSE_SLOT = 49;
    public static final int[] SHOW_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21};

    private final TerraBoxPlugin plugin;

    public RoomGui(TerraBoxPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        GuiListener.GuiHolder holder = new GuiListener.GuiHolder(GuiListener.Type.ROOM);
        Inventory inv = Bukkit.createInventory(holder, 54, TITLE);
        holder.inv = inv;
        render(p, inv, holder);
        p.openInventory(inv);
    }

    public void render(Player p, Inventory inv, GuiListener.GuiHolder holder) {
        inv.clear();
        var mgr = plugin.rooms();
        // 装饰边缘
        for (int i = 0; i < 54; i++) {
            if (i <= 8 || (i >= 36 && i <= 44)) {
                inv.setItem(i, MainMenuGui.button(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
            }
        }
        // 标题
        int total = mgr.roomIds().size();
        inv.setItem(4, MainMenuGui.button(Material.CHEST, "§b对局房间",
                List.of("§7当前房间: §e" + total + " §7个",
                        "§7点击房间报名加入, §a绿色 §7= 已报名",
                        "", "§e点击房间加入 / 参见房")));

        // 房间列表
        int idx = 0;
        for (String id : mgr.roomIds()) {
            if (idx >= SHOW_SLOTS.length) break;
            GameManager g = mgr.get(id);
            if (g == null) continue;
            int slot = SHOW_SLOTS[idx++];
            boolean joined = g.isInGame(p.getUniqueId());
            List<String> lore = new ArrayList<>();
            lore.add("§7世界: §b" + (g.roomWorldName() != null ? g.roomWorldName()
                    : (g.roomWorld() != null ? g.roomWorld().getName() : "?")));
            lore.add("§7模式: " + g.modeDisplay() + "  §7状态: " + g.stateDisplay());
            lore.add("§7参战: §e" + g.playerCount() + " §7人  §7存活: §a" + g.aliveCount() + " §7人");
            lore.add("");
            lore.add(joined ? "§c已报名, 点击退出" : "§e点击报名加入");
            Material mat = g.isRunning() ? Material.GOLD_BLOCK : Material.CHEST_MINECART;
            inv.setItem(slot, MainMenuGui.button(mat, (joined ? "§a" : "§b") + "房间 §f" + id, lore));
        }
        if (idx == 0) {
            inv.setItem(20, MainMenuGui.button(Material.BARRIER, "§c暂无房间",
                    List.of("§7点击下方 §e创建房间 §7(或 /box room create <id>)")));
        }

        // 底部按钮
        inv.setItem(CREATE_SLOT, MainMenuGui.button(Material.EMERALD, "§a创建房间",
                List.of("§7创建新的对局房间", "§7绑定当前对局世界", "", "§e点击创建 (自定义名请用 /box room create <id>)")));
        inv.setItem(INVITE_SLOT, MainMenuGui.button(Material.PLAYER_HEAD, "§d邀请玩家",
                List.of("§7邀请在线玩家加入你的房间", "§7对方点击聊天框 [接受] 加入", "", "§e点击打开邀请面板")));
        inv.setItem(BACK_SLOT, MainMenuGui.button(Material.OAK_DOOR, "§b返回主菜单",
                List.of("", "§e点击返回")));
        inv.setItem(CLOSE_SLOT, MainMenuGui.button(Material.BARRIER, "§c关闭", List.of("§7关闭")));
    }
}
