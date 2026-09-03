/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.Material
 *  org.bukkit.OfflinePlayer
 *  org.bukkit.Sound
 *  org.bukkit.entity.Player
 *  org.bukkit.inventory.Inventory
 *  org.bukkit.inventory.InventoryHolder
 *  org.bukkit.inventory.ItemStack
 */
package com.terrabox;

import com.terrabox.GuiListener;
import com.terrabox.MainMenuGui;
import com.terrabox.PlayerStore;
import com.terrabox.TerraBoxPlugin;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class SellGui {
    public static final String TITLE = "\u00a78[\u00a76\u7269\u8d44\u5927\u9646\u00a78] \u00a7a\u7269\u8d44\u56de\u6536\u5546\u5e97";
    public static final int CONFIRM_SLOT = 49;
    private final TerraBoxPlugin plugin;

    public SellGui(TerraBoxPlugin terraBoxPlugin) {
        this.plugin = terraBoxPlugin;
    }

    public void open(Player player) {
        Inventory inventory;
        GuiListener.GuiHolder guiHolder = new GuiListener.GuiHolder(GuiListener.Type.SELL);
        guiHolder.inv = inventory = Bukkit.createInventory((InventoryHolder)guiHolder, (int)54, (String)TITLE);
        inventory.setItem(48, MainMenuGui.button(Material.BOOK, "\u00a7e\u56de\u6536\u8bf4\u660e", List.of("\u00a77\u628a\u8981\u51fa\u552e\u7684\u7269\u54c1\u653e\u5230\u4e0a\u65b9 45 \u683c\u4e2d", "\u00a77\u7136\u540e\u70b9\u51fb \u00a7a\u786e\u8ba4\u51fa\u552e \u00a77\u6309\u94ae", "\u00a77\u4ef7\u683c\u4e3a\u6bcf\u4ef6\u5355\u4ef7, \u652f\u6301\u6210\u7ec4\u7ed3\u7b97", "\u00a77\u65e0\u6cd5\u56de\u6536\u7684\u7269\u54c1\u4f1a\u7559\u5728\u683c\u5b50\u91cc", "", "\u00a7e\u8f93\u5165 /box prices \u67e5\u770b\u4ef7\u683c\u8868")));
        inventory.setItem(49, MainMenuGui.button(Material.EMERALD, "\u00a7a\u00a7l\u786e\u8ba4\u51fa\u552e", List.of("\u00a77\u70b9\u51fb\u7ed3\u7b97\u4e0a\u65b9\u6240\u6709\u53ef\u56de\u6536\u7269\u54c1")));
        inventory.setItem(53, MainMenuGui.button(Material.BARRIER, "\u00a7c\u5173\u95ed\u5546\u5e97", List.of("\u00a77\u672a\u51fa\u552e\u7684\u7269\u54c1\u4f1a\u9000\u56de\u80cc\u5305")));
        player.openInventory(inventory);
    }

    public void settle(Player player, Inventory inventory) {
        Map<String, Double> map = this.plugin.sellPrices();
        long l = 0L;
        int n = 0;
        ArrayList<Object> arrayList = new ArrayList<Object>();
        for (int i = 0; i < 45; ++i) {
            ItemStack itemStack = inventory.getItem(i);
            if (itemStack == null || itemStack.getType().isAir()) continue;
            if (this.plugin.specialItems() != null && this.plugin.specialItems().isSpecial(itemStack)) {
                if (arrayList.size() >= 5 || arrayList.contains(itemStack.getType().name())) continue;
                arrayList.add(itemStack.getType().name() + "(\u7279\u6b8a\u9053\u5177)");
                continue;
            }
            if (this.plugin.artifacts() != null && this.plugin.artifacts().isArtifact(itemStack)) {
                if (arrayList.size() >= 5 || arrayList.contains(itemStack.getType().name())) continue;
                arrayList.add(itemStack.getType().name() + "(\u795e\u5668)");
                continue;
            }
            if (this.plugin.enchants() != null && this.plugin.enchants().isEnchantStone(itemStack)) {
                if (arrayList.size() >= 5 || arrayList.contains(itemStack.getType().name())) continue;
                arrayList.add(itemStack.getType().name() + "(\u9644\u9b54\u77f3)");
                continue;
            }
            if (this.plugin.crafts() != null && this.plugin.crafts().isCraftItem(itemStack)) {
                if (arrayList.size() >= 5 || arrayList.contains(itemStack.getType().name())) continue;
                arrayList.add(itemStack.getType().name() + "(\u5408\u6210\u6750\u6599)");
                continue;
            }
            Double d = map.get(itemStack.getType().name());
            if (d == null || d <= 0.0) {
                if (arrayList.size() >= 5 || arrayList.contains(itemStack.getType().name())) continue;
                arrayList.add(itemStack.getType().name());
                continue;
            }
            double d2 = d * (double)itemStack.getAmount();
            l += (long)Math.floor(d2);
            inventory.setItem(i, null);
            n += itemStack.getAmount();
        }
        if (n == 0 && l == 0L) {
            player.sendMessage(this.plugin.msg("sell-empty"));
            return;
        }
        if (l > 0L) {
            this.plugin.econ().deposit((OfflinePlayer)player, l);
            PlayerStore.PlayerData playerData = this.plugin.players().getOrCreate(player.getUniqueId(), player.getName());
            playerData.soldValue.addAndGet(l);
            playerData.touch();
        }
        player.sendMessage(this.plugin.msg("sell-done").replace("{count}", String.valueOf(n)).replace("{money}", String.valueOf(l)));
        if (!arrayList.isEmpty()) {
            player.sendMessage(this.plugin.msg("prefix") + "\u00a77\u4ee5\u4e0b\u7269\u54c1\u6682\u4e0d\u56de\u6536: \u00a7c" + String.join((CharSequence)", ", arrayList));
        }
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0f, 1.2f);
    }

    public void returnItems(Player player, Inventory inventory) {
        ArrayList<ItemStack> arrayList = new ArrayList<ItemStack>();
        for (int i = 0; i < 45; ++i) {
            ItemStack itemStack = inventory.getItem(i);
            if (itemStack == null || itemStack.getType().isAir()) continue;
            arrayList.add(itemStack);
            inventory.setItem(i, null);
        }
        if (arrayList.isEmpty()) {
            return;
        }
        HashMap hashMap = player.getInventory().addItem(arrayList.toArray(new ItemStack[0]));
        for (ItemStack itemStack : hashMap.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), itemStack);
        }
        player.sendMessage(this.plugin.msg("prefix") + "\u00a77\u5546\u5e97\u7269\u54c1\u5df2\u9000\u56de\u80cc\u5305(\u6ea2\u51fa\u6389\u843d)\u3002" + hashMap.size());
    }
}
