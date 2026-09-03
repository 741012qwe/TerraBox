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
 *  org.bukkit.inventory.meta.ItemMeta
 */
package com.terrabox;

import com.terrabox.GuiListener;
import com.terrabox.MainMenuGui;
import com.terrabox.TerraBoxPlugin;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class ArtifactGui {
    public static final String TITLE = "\u00a78[\u00a76\u7269\u8d44\u5927\u9646\u00a78] \u00a7d\u795e\u5668\u56fe\u9274";
    public static final int CLOSE_SLOT = 49;
    public static final int[] SHOW_SLOTS = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
    private final TerraBoxPlugin plugin;

    public ArtifactGui(TerraBoxPlugin terraBoxPlugin) {
        this.plugin = terraBoxPlugin;
    }

    public void open(Player player) {
        Inventory inventory;
        GuiListener.GuiHolder guiHolder = new GuiListener.GuiHolder(GuiListener.Type.ARTIFACT);
        guiHolder.inv = inventory = Bukkit.createInventory((InventoryHolder)guiHolder, (int)54, (String)TITLE);
        this.render(player, inventory, guiHolder);
        player.openInventory(inventory);
    }

    public void render(Player player, Inventory inventory, GuiListener.GuiHolder guiHolder) {
        inventory.clear();
        for (int i = 0; i < 54; ++i) {
            if (i > 8 && (i < 36 || i > 44)) continue;
            inventory.setItem(i, MainMenuGui.button(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
        }
        inventory.setItem(4, MainMenuGui.button(Material.NETHER_STAR, "\u00a7d\u795e\u5668\u56fe\u9274", List.of("\u00a77\u6240\u6709\u795e\u5668\u4e00\u89c8, \u88c5\u5907\u540e\u83b7\u5f97\u5f3a\u5927\u88ab\u52a8/\u4e3b\u52a8\u6548\u679c\u3002", "", "\u00a77\u795e\u5668\u53ef\u4ece \u00a7e\u4f20\u8bf4/\u7edd\u4e16\u7269\u8d44\u7bb1 \u00a77\u4f4e\u6982\u7387\u83b7\u5f97,", "\u00a77\u6216\u6536\u96c6\u788e\u7247/\u6750\u6599\u5230 \u00a7e\u795e\u5668\u5de5\u4f5c\u53f0 \u00a77\u5408\u6210\u3002", "\u00a77\u795e\u5668\u4e3a\u7ec8\u6781\u88c5\u5907, \u00a7c\u4e0d\u53ef\u56de\u6536(\u5546\u5e97\u62d2\u7edd)\u3002")));
        List<String> list = this.plugin.artifacts().keys();
        int n = 0;
        for (int n2 : SHOW_SLOTS) {
            if (n >= list.size()) {
                inventory.setItem(n2, MainMenuGui.button(Material.AIR, " ", List.of()));
                continue;
            }
            String string = list.get(n++);
            ItemStack itemStack = this.plugin.artifacts().buildItem(string);
            if (itemStack == null) continue;
            ItemMeta itemMeta = itemStack.getItemMeta();
            if (itemMeta != null) {
                ArrayList<String> arrayList = new ArrayList<String>();
                if (itemMeta.getLore() != null) {
                    arrayList.addAll(itemMeta.getLore());
                }
                arrayList.add("");
                arrayList.add("\u00a77\u83b7\u53d6: \u00a7e\u6536\u96c6\u4e13\u5c5e\u788e\u7247+\u6750\u6599\u5728\u5de5\u4f5c\u53f0\u5408\u6210");
                arrayList.add("\u00a77\u6216 \u00a7e\u4f20\u8bf4/\u7edd\u4e16\u7269\u8d44\u7bb1 \u00a77\u6389\u843d");
                itemMeta.setLore(arrayList);
                itemStack.setItemMeta(itemMeta);
            }
            inventory.setItem(n2, itemStack);
        }
        inventory.setItem(31, MainMenuGui.button(Material.CRAFTING_TABLE, "\u00a7b\u5982\u4f55\u83b7\u5f97\u795e\u5668?", List.of("\u00a77 1. \u6536\u96c6\u4e13\u5c5e\u788e\u7247 + \u795e\u5668\u6838\u5fc3 + \u79d8\u94f6\u952d + \u661f\u8fb0\u7c89\u5c18", "\u00a77 2. \u5230 \u00a7e\u795e\u5668\u5de5\u4f5c\u53f0 \u00a77(\u4e3b\u83dc\u5355/\u00a7e/box craft\u00a77) \u5408\u6210", "\u00a77 3. \u6216\u5728 \u00a7e\u4f20\u8bf4/\u7edd\u4e16\u7269\u8d44\u7bb1 \u00a77\u4e2d\u4f4e\u6982\u7387\u5f00\u7bb1\u83b7\u5f97", "", "\u00a7e\u788e\u7247/\u6750\u6599\u4e3a\u5408\u6210\u6750\u6599, \u5546\u5e97\u4e0d\u53ef\u56de\u6536\u3002")));
        inventory.setItem(49, MainMenuGui.button(Material.BARRIER, "\u00a7c\u5173\u95ed", List.of("\u00a77\u5173\u95ed\u56fe\u9274")));
    }
}
