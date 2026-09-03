/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.Material
 *  org.bukkit.entity.Player
 *  org.bukkit.inventory.Inventory
 *  org.bukkit.inventory.InventoryHolder
 */
package com.terrabox;

import com.terrabox.GameManager;
import com.terrabox.GuiListener;
import com.terrabox.MainMenuGui;
import com.terrabox.RoomManager;
import com.terrabox.TerraBoxPlugin;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class RoomGui {
    public static final String TITLE = "\u00a78[\u00a76\u7269\u8d44\u5927\u9646\u00a78] \u00a72\u5bf9\u5c40\u623f\u95f4";
    public static final int CREATE_SLOT = 28;
    public static final int INVITE_SLOT = 30;
    public static final int BACK_SLOT = 22;
    public static final int CLOSE_SLOT = 49;
    public static final int[] SHOW_SLOTS = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21};
    private final TerraBoxPlugin plugin;

    public RoomGui(TerraBoxPlugin terraBoxPlugin) {
        this.plugin = terraBoxPlugin;
    }

    public void open(Player player) {
        Inventory inventory;
        GuiListener.GuiHolder guiHolder = new GuiListener.GuiHolder(GuiListener.Type.ROOM);
        guiHolder.inv = inventory = Bukkit.createInventory((InventoryHolder)guiHolder, (int)54, (String)TITLE);
        this.render(player, inventory, guiHolder);
        player.openInventory(inventory);
    }

    public void render(Player player, Inventory inventory, GuiListener.GuiHolder guiHolder) {
        int n;
        inventory.clear();
        RoomManager roomManager = this.plugin.rooms();
        for (n = 0; n < 54; ++n) {
            if (n > 8 && (n < 36 || n > 44)) continue;
            inventory.setItem(n, MainMenuGui.button(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
        }
        n = roomManager.roomIds().size();
        inventory.setItem(4, MainMenuGui.button(Material.CHEST, "\u00a7b\u5bf9\u5c40\u623f\u95f4", List.of("\u00a77\u5f53\u524d\u623f\u95f4: \u00a7e" + n + " \u00a77\u4e2a", "\u00a77\u70b9\u51fb\u623f\u95f4\u62a5\u540d\u52a0\u5165, \u00a7a\u7eff\u8272 \u00a77= \u5df2\u62a5\u540d", "", "\u00a7e\u70b9\u51fb\u623f\u95f4\u52a0\u5165 / \u53c2\u89c1\u623f")));
        int n2 = 0;
        for (String string : roomManager.roomIds()) {
            if (n2 >= SHOW_SLOTS.length) break;
            GameManager gameManager = roomManager.get(string);
            if (gameManager == null) continue;
            int n3 = SHOW_SLOTS[n2++];
            boolean bl = gameManager.isInGame(player.getUniqueId());
            ArrayList<String> arrayList = new ArrayList<String>();
            arrayList.add("\u00a77\u4e16\u754c: \u00a7b" + (gameManager.roomWorldName() != null ? gameManager.roomWorldName() : (gameManager.roomWorld() != null ? gameManager.roomWorld().getName() : "?")));
            arrayList.add("\u00a77\u6a21\u5f0f: " + gameManager.modeDisplay() + "  \u00a77\u72b6\u6001: " + gameManager.stateDisplay());
            arrayList.add("\u00a77\u53c2\u6218: \u00a7e" + gameManager.playerCount() + " \u00a77\u4eba  \u00a77\u5b58\u6d3b: \u00a7a" + gameManager.aliveCount() + " \u00a77\u4eba");
            arrayList.add("");
            arrayList.add(bl ? "\u00a7c\u5df2\u62a5\u540d, \u70b9\u51fb\u9000\u51fa" : "\u00a7e\u70b9\u51fb\u62a5\u540d\u52a0\u5165");
            Material material = gameManager.isRunning() ? Material.GOLD_BLOCK : Material.CHEST_MINECART;
            inventory.setItem(n3, MainMenuGui.button(material, (bl ? "\u00a7a" : "\u00a7b") + "\u623f\u95f4 \u00a7f" + string, arrayList));
        }
        if (n2 == 0) {
            inventory.setItem(20, MainMenuGui.button(Material.BARRIER, "\u00a7c\u6682\u65e0\u623f\u95f4", List.of("\u00a77\u70b9\u51fb\u4e0b\u65b9 \u00a7e\u521b\u5efa\u623f\u95f4 \u00a77(\u6216 /box room create <id>)")));
        }
        inventory.setItem(28, MainMenuGui.button(Material.EMERALD, "\u00a7a\u521b\u5efa\u623f\u95f4", List.of("\u00a77\u521b\u5efa\u65b0\u7684\u5bf9\u5c40\u623f\u95f4", "\u00a77\u7ed1\u5b9a\u5f53\u524d\u5bf9\u5c40\u4e16\u754c", "", "\u00a7e\u70b9\u51fb\u521b\u5efa (\u81ea\u5b9a\u4e49\u540d\u8bf7\u7528 /box room create <id>)")));
        inventory.setItem(30, MainMenuGui.button(Material.PLAYER_HEAD, "\u00a7d\u9080\u8bf7\u73a9\u5bb6", List.of("\u00a77\u9080\u8bf7\u5728\u7ebf\u73a9\u5bb6\u52a0\u5165\u4f60\u7684\u623f\u95f4", "\u00a77\u5bf9\u65b9\u70b9\u51fb\u804a\u5929\u6846 [\u63a5\u53d7] \u52a0\u5165", "", "\u00a7e\u70b9\u51fb\u6253\u5f00\u9080\u8bf7\u9762\u677f")));
        inventory.setItem(22, MainMenuGui.button(Material.OAK_DOOR, "\u00a7b\u8fd4\u56de\u4e3b\u83dc\u5355", List.of("", "\u00a7e\u70b9\u51fb\u8fd4\u56de")));
        inventory.setItem(49, MainMenuGui.button(Material.BARRIER, "\u00a7c\u5173\u95ed", List.of("\u00a77\u5173\u95ed")));
    }
}
