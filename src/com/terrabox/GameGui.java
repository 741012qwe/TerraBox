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

import com.terrabox.GameManager;
import com.terrabox.GuiListener;
import com.terrabox.RoomManager;
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

public class GameGui {
    public static final String TITLE = "\u00a78[\u00a76\u7269\u8d44\u5927\u9646\u00a78] \u00a72\u9009\u62e9\u5bf9\u5c40\u6a21\u5f0f";
    private final TerraBoxPlugin plugin;

    public GameGui(TerraBoxPlugin terraBoxPlugin) {
        this.plugin = terraBoxPlugin;
    }

    public void open(Player player) {
        Inventory inventory;
        GuiListener.GuiHolder guiHolder = new GuiListener.GuiHolder(GuiListener.Type.GAME);
        guiHolder.inv = inventory = Bukkit.createInventory((InventoryHolder)guiHolder, (int)27, (String)TITLE);
        RoomManager roomManager = this.plugin.rooms();
        GameManager gameManager = roomManager.get("solo");
        inventory.setItem(10, this.modeButton(player, gameManager, Material.EMERALD, "\u00a7a\u5355\u4eba\u6a21\u5f0f", "\u00a77\u72ec\u81ea\u641c\u522e, \u65e0 PvP, \u65f6\u95f4\u5230\u6309\u5f00\u7bb1+\u51fb\u6740\u79ef\u5206\u7ed3\u7b97", "\u00a77\u65e0\u4eba\u6570\u9650\u5236 (\u7ba1\u7406\u5458\u53ef\u5f00)"));
        GameManager gameManager2 = roomManager.get("pvp");
        inventory.setItem(13, this.modeButton(player, gameManager2, Material.DIAMOND_SWORD, "\u00a7c\u591a\u4ebaPVP", "\u00a77\u5168\u56fe PvP, \u6700\u540e\u5b58\u6d3b\u8005\u83b7\u80dc", "\u00a77\u9700 \u22652 \u4eba"));
        GameManager gameManager3 = roomManager.get("team");
        inventory.setItem(16, this.modeButton(player, gameManager3, Material.GOLDEN_APPLE, "\u00a76\u7ec4\u961f\u5bf9\u6218", "\u00a77\u81ea\u52a8\u5206\u961f, \u540c\u961f\u514d\u4f24, \u6700\u540e\u5b58\u6d3b\u961f\u4f0d\u83b7\u80dc", "\u00a77\u9700 \u22652 \u4eba"));
        inventory.setItem(4, this.info(player));
        inventory.setItem(18, GameGui.button(Material.CLOCK, "\u00a7e\u67e5\u770b\u5bf9\u5c40\u72b6\u6001", List.of("", "\u00a7e\u70b9\u51fb\u67e5\u770b")));
        inventory.setItem(22, GameGui.button(Material.BARRIER, "\u00a7c\u8fd4\u56de\u4e3b\u83dc\u5355", List.of("", "\u00a7e\u70b9\u51fb\u8fd4\u56de")));
        if (player.hasPermission("terrabox.admin")) {
            inventory.setItem(24, GameGui.button(Material.NETHER_STAR, "\u00a76\u5f00\u59cb\u5bf9\u5c40 (\u7ba1\u7406\u5458)", List.of("\u00a77\u70b9\u51fb\u540e\u9009\u62e9\u5f00\u59cb\u67d0\u6a21\u5f0f\u5bf9\u5c40", "\u00a77\u4e5f\u53ef\u7528 \u00a7e/box room start <solo|pvp|team> <mode>", "", "\u00a7e\u70b9\u51fb\u6253\u5f00\u6a21\u5f0f\u9009\u62e9")));
        }
        player.openInventory(inventory);
    }

    private ItemStack modeButton(Player player, GameManager gameManager, Material material, String string, String ... stringArray) {
        ArrayList<String> arrayList = new ArrayList<String>();
        for (String string2 : stringArray) {
            arrayList.add(string2);
        }
        if (gameManager != null) {
            boolean bl = gameManager.isInGame(player.getUniqueId());
            arrayList.add("");
            arrayList.add("\u00a77\u6a21\u5f0f\u623f\u95f4\u72b6\u6001: " + gameManager.stateDisplay());
            arrayList.add("\u00a77\u5f53\u524d\u53c2\u6218: \u00a7e" + gameManager.playerCount() + " \u00a77\u4eba  \u5b58\u6d3b: \u00a7a" + gameManager.aliveCount() + " \u00a77\u4eba");
            arrayList.add(bl ? "\u00a7c\u4f60\u5df2\u62a5\u540d, \u70b9\u51fb\u9000\u51fa" : "\u00a7e\u70b9\u51fb\u62a5\u540d");
        } else {
            arrayList.add("");
            arrayList.add("\u00a7e\u70b9\u51fb\u62a5\u540d (\u9996\u6b21\u521b\u5efa\u623f\u95f4)");
        }
        return GameGui.button(material, string, arrayList);
    }

    private ItemStack info(Player player) {
        return GameGui.button(Material.GRASS_BLOCK, "\u00a76\u00a7l\u5bf9\u5c40\u5927\u5385", List.of("\u00a77\u5f53\u524d\u9ed8\u8ba4\u623f\u95f4: " + this.plugin.games().stateDisplay(), "\u00a77\u5bf9\u5c40\u4e16\u754c: \u00a7b" + (this.plugin.worlds().world() != null ? this.plugin.worlds().world().getName() : "?"), "\u00a77\u7ecf\u6d4e: \u00a7e" + this.plugin.econ().name(), "", "\u00a77\u7ba1\u7406\u5458: \u70b9\u51fb\u6a21\u5f0f\u540e\u53ef\u7528 \u00a7e/box room start <id> <mode> \u00a77\u5f00\u8d5b"));
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
