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
import com.terrabox.TerrainType;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class TerrainSelectGui {
    public static final String TITLE = "\u00a78[\u00a76\u7269\u8d44\u5927\u9646\u00a78] \u00a7b\u9009\u62e9\u5bf9\u5c40\u5730\u5f62";
    private final TerraBoxPlugin plugin;

    public TerrainSelectGui(TerraBoxPlugin terraBoxPlugin) {
        this.plugin = terraBoxPlugin;
    }

    public void open(Player player) {
        Inventory inventory;
        GuiListener.GuiHolder guiHolder = new GuiListener.GuiHolder(GuiListener.Type.TERRAIN);
        guiHolder.inv = inventory = Bukkit.createInventory((InventoryHolder)guiHolder, (int)54, (String)TITLE);
        String string = this.plugin.arenas().current() != null ? this.plugin.arenas().current().getName() : "?";
        TerrainType terrainType = this.plugin.arenas().current() != null ? this.plugin.arenas().terrainOf(this.plugin.arenas().current().getName()) : TerrainType.DEFAULT;
        inventory.setItem(4, this.button(Material.COMPASS, "\u00a7b\u5f53\u524d\u5bf9\u5c40\u5730\u5f62", List.of("\u00a77\u5f53\u524d\u4e16\u754c: \u00a7e" + string, "\u00a77\u5730\u5f62: " + terrainType.colorCode + terrainType.display, "\u00a77\u5c3a\u5bf8: \u00a7f" + terrainType.worldSize() + "x" + terrainType.worldSize(), "", "\u00a7e\u70b9\u51fb\u4e0b\u65b9\u5207\u6362\u5730\u5f62")));
        inventory.setItem(10, this.button(Material.GRASS_BLOCK, "\u00a7a\u9ed8\u8ba4\u5e73\u539f\u5730\u5f62", List.of("\u00a77\u4e2d\u5fc3\u5e73\u5766\u8349\u539f, \u5916\u56f4\u4e18\u9675\u5c71\u5730", "\u00a77\u7a00\u758f\u6811\u6728, \u5f00\u9614\u5bf9\u6218", "\u00a77\u5c3a\u5bf8: \u00a7f" + TerrainType.DEFAULT.worldSize() + "x" + TerrainType.DEFAULT.worldSize(), "", "\u00a7e\u70b9\u51fb\u9009\u62e9")));
        inventory.setItem(11, this.button(Material.SAND, "\u00a76\u6c99\u6f20\u98ce\u683c\u5730\u5f62", List.of("\u00a77\u6c99\u4e18\u8d77\u4f0f, \u7802\u5ca9\u5e95\u5c42", "\u00a77\u7a00\u758f\u4ed9\u4eba\u638c, \u89c6\u91ce\u5f00\u9614", "\u00a77\u5c3a\u5bf8: \u00a7f" + TerrainType.DESERT.worldSize() + "x" + TerrainType.DESERT.worldSize(), "", "\u00a7e\u70b9\u51fb\u9009\u62e9")));
        inventory.setItem(12, this.button(Material.OAK_BOAT, "\u00a7b\u5927\u5c9b\u5c7f\u98ce\u683c\u5730\u5f62", List.of("\u00a77\u968f\u673a\u5927\u5c0f\u5c9b\u5c7f\u7fa4, \u4e2d\u95f4\u6d77\u6d0b", "\u00a77\u5c9b\u5c7f\u4e92\u4e0d\u76f8\u8fde, \u9700\u6e38\u6cf3/\u5212\u8239", "\u00a77\u5c3a\u5bf8: \u00a7f" + TerrainType.ISLANDS.worldSize() + "x" + TerrainType.ISLANDS.worldSize(), "", "\u00a7e\u70b9\u51fb\u9009\u62e9")));
        inventory.setItem(13, this.button(Material.END_STONE, "\u00a7d\u672b\u5730\u5c9b\u5c7f\u5730\u5f62", List.of("\u00a77\u6d6e\u7a7a\u672b\u5730\u77f3\u5c9b\u7fa4, \u9ed1\u8272\u5929\u7a7a", "\u00a77\u4e3b\u5c9b+\u9ed1\u66dc\u77f3\u67f1+\u672b\u5730\u57ce\u5854\u697c", "\u00a77\u7fa4\u7cfb: \u672b\u5730\u9ad8\u5730/\u4e2d\u5730/\u8d2b\u7620/\u5c0f\u5c9b", "\u00a77\u5c3a\u5bf8: \u00a7f" + TerrainType.THE_END.worldSize() + "x" + TerrainType.THE_END.worldSize(), "", "\u00a7e\u70b9\u51fb\u9009\u62e9")));
        inventory.setItem(14, this.button(Material.RED_SAND, "\u00a7c\u6076\u5730\u5730\u5f62", List.of("\u00a77\u7ea2\u6c99\u5730\u8868, \u5f69\u9676\u74e6\u5c42", "\u00a77\u88ab\u4fb5\u8680\u7684\u5e73\u9876\u5c71\u4e18, \u88f8\u9732\u91d1\u77ff", "\u00a77\u7fa4\u7cfb: \u6076\u5730/\u4fb5\u8680\u6076\u5730/\u6797\u5730\u6076\u5730", "\u00a77\u5c3a\u5bf8: \u00a7f" + TerrainType.BADLANDS.worldSize() + "x" + TerrainType.BADLANDS.worldSize(), "", "\u00a7e\u70b9\u51fb\u9009\u62e9")));
        inventory.setItem(15, this.button(Material.NETHERRACK, "\u00a74\u4e0b\u754c\u5730\u5f62", List.of("\u00a77\u5730\u72f1\u5ca9\u8d77\u4f0f, \u5ca9\u6d46\u6e56, \u7ea2\u8272\u5929\u7a7a", "\u00a77\u7075\u9b42\u6c99\u5ce1\u8c37, \u7384\u6b66\u5ca9\u67f1", "\u00a77\u7fa4\u7cfb: \u4e0b\u754c\u8352\u539f/\u7075\u9b42\u6c99\u5ce1\u8c37/\u7384\u6b66\u5ca9\u4e09\u89d2\u6d32/\u7eef\u7ea2/\u8be1\u5f02\u68ee\u6797", "\u00a77\u5c3a\u5bf8: \u00a7f" + TerrainType.NETHER.worldSize() + "x" + TerrainType.NETHER.worldSize(), "", "\u00a7e\u70b9\u51fb\u9009\u62e9")));
        inventory.setItem(16, this.button(Material.STONE_BRICKS, "\u00a73\u57ce\u5e02\u5730\u5f62", List.of("\u00a77\u5e73\u5766\u8857\u9053\u7f51\u683c, \u5efa\u7b51\u7fa4", "\u00a77\u6df7\u51dd\u571f\u9ad8\u697c, \u516c\u56ed\u7eff\u5730", "\u00a77\u7fa4\u7cfb: \u5e73\u539f", "\u00a77\u5c3a\u5bf8: \u00a7f" + TerrainType.CITY.worldSize() + "x" + TerrainType.CITY.worldSize(), "", "\u00a7e\u70b9\u51fb\u9009\u62e9")));
        inventory.setItem(17, this.button(Material.OAK_SAPLING, "\u00a72\u6b63\u5e38\u4e3b\u4e16\u754c", List.of("\u00a77\u591a\u6837\u5730\u5f62: \u5e73\u539f/\u4e18\u9675/\u5c71\u5730/\u6cb3\u6d41", "\u00a77\u6709\u8fb9\u5883\u5899/\u56f4\u5899, \u53ef\u8fdb\u5730\u72f1/\u672b\u5730\u4f20\u9001\u95e8", "\u00a77\u7bb1\u5b50\u8f83\u5c11, \u54c1\u8d28\u8f83\u4f4e (\u57fa\u7840\u4e16\u754c)", "\u00a77\u5c3a\u5bf8: \u00a7f" + TerrainType.NORMAL.worldSize() + "x" + TerrainType.NORMAL.worldSize(), "", "\u00a7e\u70b9\u51fb\u9009\u62e9")));
        inventory.setItem(22, this.button(Material.BARRIER, "\u00a7c\u53d6\u6d88", List.of("\u00a77\u5173\u95ed\u9009\u62e9\u754c\u9762", "", "\u00a7e\u70b9\u51fb\u53d6\u6d88")));
        inventory.setItem(26, this.button(Material.EMERALD, "\u00a7a\u751f\u6210\u65b0\u5bf9\u5c40\u4e16\u754c", List.of("\u00a77\u6309\u5f53\u524d\u9009\u5b9a\u5730\u5f62\u989d\u5916\u521b\u5efa\u65b0\u4e16\u754c", "\u00a77\u9700\u8981\u7ba1\u7406\u5458\u6743\u9650", "", "\u00a7e\u70b9\u51fb\u751f\u6210\u5e76\u52a0\u5165")));
        player.openInventory(inventory);
    }

    private ItemStack button(Material material, String string, List<String> list) {
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
