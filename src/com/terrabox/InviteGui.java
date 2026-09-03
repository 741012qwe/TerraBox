/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.Material
 *  org.bukkit.entity.Player
 *  org.bukkit.inventory.Inventory
 *  org.bukkit.inventory.InventoryHolder
 *  org.bukkit.inventory.ItemStack
 */
package com.terrabox;

import com.terrabox.GuiListener;
import com.terrabox.MainMenuGui;
import com.terrabox.TerraBoxPlugin;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class InviteGui {
    public static final String TITLE = "\u00a78[\u00a76\u7269\u8d44\u5927\u9646\u00a78] \u00a7d\u9080\u8bf7\u73a9\u5bb6";
    public static final int CLOSE_SLOT = 49;
    public static final int[] SHOW_SLOTS = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
    private final TerraBoxPlugin plugin;
    private String roomId;

    public InviteGui(TerraBoxPlugin terraBoxPlugin) {
        this.plugin = terraBoxPlugin;
    }

    public void open(Player player, String string) {
        Inventory inventory;
        GuiListener.GuiHolder guiHolder = new GuiListener.GuiHolder(GuiListener.Type.INVITE);
        guiHolder.inv = inventory = Bukkit.createInventory((InventoryHolder)guiHolder, (int)54, (String)TITLE);
        guiHolder.inviteRoom = string;
        this.render(player, inventory, guiHolder);
        player.openInventory(inventory);
    }

    public void render(Player player, Inventory inventory, GuiListener.GuiHolder guiHolder) {
        int n;
        inventory.clear();
        String string = guiHolder.inviteRoom != null ? guiHolder.inviteRoom : "default";
        for (n = 0; n < 54; ++n) {
            if (n > 8 && (n < 36 || n > 44)) continue;
            inventory.setItem(n, MainMenuGui.button(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
        }
        inventory.setItem(4, MainMenuGui.button(Material.PLAYER_HEAD, "\u00a7d\u9080\u8bf7\u73a9\u5bb6\u52a0\u5165\u623f\u95f4", List.of("\u00a77\u76ee\u6807\u623f\u95f4: \u00a7e" + string, "\u00a77\u70b9\u51fb\u73a9\u5bb6\u5373\u53d1\u9001\u9080\u8bf7", "\u00a77\u5bf9\u65b9\u70b9\u51fb\u804a\u5929\u6846 [\u63a5\u53d7] \u540e\u52a0\u5165", "", "\u00a7e\u9009\u62e9\u8981\u9080\u8bf7\u7684\u73a9\u5bb6")));
        n = 0;
        for (Player player2 : Bukkit.getOnlinePlayers()) {
            if (player2.getUniqueId().equals(player.getUniqueId())) continue;
            if (n >= SHOW_SLOTS.length) break;
            int n2 = SHOW_SLOTS[n++];
            ItemStack itemStack = MainMenuGui.button(Material.PLAYER_HEAD, "\u00a7e" + player2.getName(), List.of("\u00a77\u70b9\u51fb\u9080\u8bf7 " + player2.getName() + " \u52a0\u5165\u623f\u95f4 " + string, "\u00a77\u5728\u7ebf\u72b6\u6001: \u00a7a\u5728\u7ebf"));
            inventory.setItem(n2, itemStack);
        }
        if (n == 0) {
            inventory.setItem(22, MainMenuGui.button(Material.BARRIER, "\u00a7c\u6682\u65e0\u5176\u4ed6\u5728\u7ebf\u73a9\u5bb6", List.of("\u00a77\u6ca1\u6709\u5176\u4ed6\u73a9\u5bb6\u53ef\u9080\u8bf7\u3002")));
        }
        inventory.setItem(31, MainMenuGui.button(Material.CHEST, "\u00a7b\u5f53\u524d\u623f\u95f4: \u00a7e" + string, List.of("")));
        inventory.setItem(49, MainMenuGui.button(Material.BARRIER, "\u00a7c\u5173\u95ed", List.of("\u00a77\u5173\u95ed\u9080\u8bf7\u9762\u677f")));
    }
}
