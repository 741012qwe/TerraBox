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

public class MainMenuGui {
    public static final String TITLE = "\u00a78[\u00a76\u7269\u8d44\u5927\u9646\u00a78] \u00a72\u4e3b\u83dc\u5355";
    private final TerraBoxPlugin plugin;

    public MainMenuGui(TerraBoxPlugin terraBoxPlugin) {
        this.plugin = terraBoxPlugin;
    }

    public void open(Player player) {
        Inventory inventory;
        GuiListener.GuiHolder guiHolder = new GuiListener.GuiHolder(GuiListener.Type.MENU);
        guiHolder.inv = inventory = Bukkit.createInventory((InventoryHolder)guiHolder, (int)27, (String)TITLE);
        inventory.setItem(4, this.info());
        inventory.setItem(10, MainMenuGui.button(Material.ENDER_PEARL, "\u00a7a\u968f\u673a\u9646\u5730\u51fa\u751f", List.of("\u00a77\u4f20\u9001\u5230\u5730\u56fe\u5185\u968f\u673a\u9646\u5730\u5750\u6807", "\u00a77\u51b7\u5374 " + this.plugin.getConfig().getInt("spawn.command-cooldown-seconds", 300) + " \u79d2", "", "\u00a7e\u70b9\u51fb\u4f20\u9001")));
        inventory.setItem(12, MainMenuGui.button(Material.EMERALD, "\u00a72\u7269\u8d44\u56de\u6536\u5546\u5e97", List.of("\u00a77\u628a\u77ff\u8d44\u6e90\u548c\u6218\u5229\u54c1\u5356\u7ed9\u5546\u5e97\u6362\u94b1", "\u00a77\u652f\u6301\u4e0d\u53ef\u5806\u53e0\u88c5\u5907\u9010\u4ef6\u7ed3\u7b97", "", "\u00a7e\u70b9\u51fb\u6253\u5f00")));
        inventory.setItem(14, MainMenuGui.button(Material.COMPASS, "\u00a7b\u5bfb\u5b9d", List.of("\u00a77\u82b1\u8d39 \u00a7e" + (long)this.plugin.getConfig().getDouble("hunt.cost", 120.0) + " \u00a77\u5143\u8d2d\u4e70\u4e00\u4e2a", "\u00a77\u9ad8\u7a00\u6709\u5ea6\u7269\u8d44\u7bb1\u7684\u65b9\u4f4d\u63d0\u793a", "", "\u00a7e\u70b9\u51fb\u5bfb\u5b9d")));
        inventory.setItem(16, MainMenuGui.button(Material.GOLD_INGOT, "\u00a76\u5f00\u7bb1\u6392\u884c\u699c", List.of("\u00a77\u67e5\u770b\u5168\u670d\u5f00\u7bb1\u6b21\u6570 TOP10", "", "\u00a7e\u70b9\u51fb\u67e5\u770b")));
        inventory.setItem(11, MainMenuGui.button(Material.CHEST, "\u00a7d\u5730\u56fe\u7269\u8d44\u7bb1\u5206\u5e03", List.of("\u00a77\u5f53\u524d\u5730\u56fe\u5404\u7a00\u6709\u5ea6\u7bb1\u5b50\u6570\u91cf", "", "\u00a7e\u70b9\u51fb\u67e5\u770b")));
        inventory.setItem(15, MainMenuGui.button(Material.NETHER_STAR, "\u00a7f\u6211\u7684\u7edf\u8ba1", List.of("\u00a77\u67e5\u770b\u4f60\u7684\u5f00\u7bb1/\u56de\u6536/\u5bfb\u5b9d\u7edf\u8ba1", "", "\u00a7e\u70b9\u51fb\u67e5\u770b")));
        inventory.setItem(13, MainMenuGui.button(Material.BOOK, "\u00a7e\u73a9\u6cd5\u8bf4\u660e", List.of("\u00a7f1. \u00a77\u5168\u56fe\u968f\u673a\u6295\u653e\u4e94\u6863\u7269\u8d44\u7bb1", "\u00a7f2. \u00a77\u5b9a\u65f6\u7a7a\u6295+\u5168\u670d\u5750\u6807\u5e7f\u64ad, \u4e89\u62a2\u4f20\u8bf4\u7bb1", "\u00a7f3. \u00a77\u5bfb\u5b9d\u8d2d\u4e70\u9ad8\u7a00\u6709\u7bb1\u65b9\u4f4d", "\u00a7f4. \u00a77\u7269\u8d44\u5356\u5546\u5e97\u6362\u94b1", "\u00a7f5. \u00a77\u5730\u56fe " + (int)this.plugin.getConfig().getDouble("world.border-size", 2048.0) + "x" + (int)this.plugin.getConfig().getDouble("world.border-size", 2048.0) + " \u56fa\u5b9a\u8fb9\u754c", "", "\u00a77\u7bb1\u5b50\u88ab\u642c\u7a7a\u540e\u4f1a\u5728\u522b\u5904\u91cd\u751f, \u624b\u5feb\u6709\u624b\u6162\u65e0!")));
        boolean bl = this.plugin.rooms().isInGame(player.getUniqueId());
        inventory.setItem(18, MainMenuGui.button(Material.CHEST_MINECART, bl ? "\u00a7c\u9000\u51fa\u5bf9\u5c40" : "\u00a7a\u53c2\u52a0\u5bf9\u5c40", List.of("\u00a77\u5355\u4eba / \u591a\u4ebaPVP / \u7ec4\u961f \u4e09\u79cd\u6a21\u5f0f", "\u00a77\u5f53\u524d\u6a21\u5f0f: \u00a7e" + this.plugin.games().modeDisplay(), "\u00a77\u72b6\u6001: \u00a7b" + this.plugin.games().stateDisplay(), "", "\u00a7e\u70b9\u51fb\u9009\u62e9\u6a21\u5f0f\u62a5\u540d")));
        inventory.setItem(22, MainMenuGui.button(Material.CLOCK, "\u00a7e\u5bf9\u5c40\u72b6\u6001", List.of("\u00a77\u67e5\u770b\u5f53\u524d\u5bf9\u5c40\u4fe1\u606f", "", "\u00a7e\u70b9\u51fb\u67e5\u770b")));
        inventory.setItem(19, MainMenuGui.button(Material.OAK_DOOR, "\u00a7b\u8fd4\u56de\u5927\u5385", List.of("\u00a77\u4f20\u9001\u56de\u73a9\u5bb6\u805a\u96c6\u5730\u5927\u5385", "", "\u00a7e\u70b9\u51fb\u8fd4\u56de")));
        inventory.setItem(21, MainMenuGui.button(Material.FILLED_MAP, "\u00a7d\u9009\u62e9\u5bf9\u5c40\u5730\u5f62", List.of("\u00a77\u9009\u62e9\u5bf9\u5c40\u4e16\u754c\u7684\u5730\u5f62\u6a21\u677f:", "\u00a77\u9ed8\u8ba4\u5e73\u539f / \u6c99\u6f20\u98ce\u683c / \u5927\u5c9b\u5c7f\u98ce\u683c", "\u00a77(\u4ec5\u7ba1\u7406\u5458)", "", "\u00a7e\u70b9\u51fb\u9009\u62e9")));
        inventory.setItem(17, MainMenuGui.button(Material.CHEST_MINECART, "\u00a7a\u5bf9\u5c40\u623f\u95f4", List.of("\u00a77\u67e5\u770b\u6240\u6709\u5728\u7ebf\u623f\u95f4, \u52a0\u5165/\u521b\u5efa/\u9080\u8bf7", "\u00a77\u9080\u8bf7\u5728\u7ebf\u73a9\u5bb6\u52a0\u5165\u4f60\u7684\u623f\u95f4", "", "\u00a7e\u70b9\u51fb\u6253\u5f00\u623f\u95f4\u5217\u8868")));
        player.openInventory(inventory);
    }

    private ItemStack info() {
        double d = this.plugin.getConfig().getDouble("world.border-size", 2048.0);
        ItemStack itemStack = MainMenuGui.button(Material.GRASS_BLOCK, "\u00a76\u00a7l\u7269\u8d44\u5927\u9646", List.of("\u00a77\u56fa\u5b9a\u5730\u56fe: \u00a7b" + (int)d + "x" + (int)d, "\u00a77\u5f53\u524d\u7269\u8d44\u7bb1: \u00a7a" + this.plugin.boxes().count() + " \u4e2a", "\u00a77\u7ecf\u6d4e: \u00a7e" + this.plugin.econ().name(), "\u00a77\u7a7a\u6295: \u00a7d" + this.plugin.airdrops().secondsUntilNext() / 60L + " \u5206\u949f\u540e"));
        return itemStack;
    }

    static ItemStack button(Material material, String string, List<String> list) {
        ItemStack itemStack = new ItemStack(material);
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta != null) {
            itemMeta.setDisplayName(string);
            itemMeta.setLore(new ArrayList<String>(list));
            itemStack.setItemMeta(itemMeta);
        }
        return itemStack;
    }
}
