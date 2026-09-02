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
 * 邀请玩家 GUI (54格) — 房间主人选择在线玩家, 点击邀请加入指定房间
 *
 * 展示除自己外的所有在线玩家, 点击即邀请其加入指定房间 (InviteManager)。
 * 只读展示: 玩家头 + 名称 + 在线状态; 点击邀请。底部显示当前房间名。
 *
 * 线程模型: 打开由玩家区域线程调用, Inventory 构建为局部对象, 安全。
 */
public class InviteGui {
    public static final String TITLE = "§8[§6物资大陆§8] §d邀请玩家";
    public static final int CLOSE_SLOT = 49;
    public static final int[] SHOW_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    private final TerraBoxPlugin plugin;
    private String roomId; // 邀请到的目标房间

    public InviteGui(TerraBoxPlugin plugin) {
        this.plugin = plugin;
    }

    /** 打开邀请 GUI (inviteTo=目标房间id), 玩家区域线程 */
    public void open(Player p, String inviteTo) {
        GuiListener.GuiHolder holder = new GuiListener.GuiHolder(GuiListener.Type.INVITE);
        Inventory inv = Bukkit.createInventory(holder, 54, TITLE);
        holder.inv = inv;
        holder.inviteRoom = inviteTo;
        render(p, inv, holder);
        p.openInventory(inv);
    }

    public void render(Player p, Inventory inv, GuiListener.GuiHolder holder) {
        inv.clear();
        String targetRoom = holder.inviteRoom != null ? holder.inviteRoom : "default";
        // 装饰边缘
        for (int i = 0; i < 54; i++) {
            if (i <= 8 || (i >= 36 && i <= 44)) {
                inv.setItem(i, MainMenuGui.button(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
            }
        }
        // 标题
        inv.setItem(4, MainMenuGui.button(Material.PLAYER_HEAD, "§d邀请玩家加入房间",
                List.of("§7目标房间: §e" + targetRoom,
                        "§7点击玩家即发送邀请",
                        "§7对方点击聊天框 [接受] 后加入",
                        "", "§e选择要邀请的玩家")));

        // 在线玩家列表 (除自己外)
        int idx = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(p.getUniqueId())) continue;
            if (idx >= SHOW_SLOTS.length) break;
            int slot = SHOW_SLOTS[idx++];
            ItemStack head = MainMenuGui.button(Material.PLAYER_HEAD, "§e" + online.getName(),
                    List.of("§7点击邀请 " + online.getName() + " 加入房间 " + targetRoom,
                            "§7在线状态: §a在线"));
            inv.setItem(slot, head);
        }
        if (idx == 0) {
            inv.setItem(22, MainMenuGui.button(Material.BARRIER, "§c暂无其他在线玩家",
                    List.of("§7没有其他玩家可邀请。")));
        }

        // 底部
        inv.setItem(31, MainMenuGui.button(Material.CHEST, "§b当前房间: §e" + targetRoom, List.of("")));
        inv.setItem(CLOSE_SLOT, MainMenuGui.button(Material.BARRIER, "§c关闭", List.of("§7关闭邀请面板")));
    }
}
