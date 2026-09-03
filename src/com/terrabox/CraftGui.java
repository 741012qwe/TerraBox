/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.Material
 *  org.bukkit.Sound
 *  org.bukkit.entity.Player
 *  org.bukkit.inventory.Inventory
 *  org.bukkit.inventory.InventoryHolder
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 */
package com.terrabox;

import com.terrabox.CraftManager;
import com.terrabox.GuiListener;
import com.terrabox.MainMenuGui;
import com.terrabox.TerraBoxPlugin;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class CraftGui {
    public static final String TITLE = "\u00a78[\u00a76\u7269\u8d44\u5927\u9646\u00a78] \u00a7b\u795e\u5668\u5de5\u4f5c\u53f0";
    public static final int[] MAT_SLOTS = new int[]{10, 11, 12, 19, 20, 21, 28, 29, 30};
    public static final int OUTPUT_SLOT = 24;
    public static final int PREV_SLOT = 46;
    public static final int NEXT_SLOT = 47;
    public static final int CRAFT_SLOT = 49;
    public static final int CLOSE_SLOT = 50;
    private final TerraBoxPlugin plugin;

    public CraftGui(TerraBoxPlugin terraBoxPlugin) {
        this.plugin = terraBoxPlugin;
    }

    public void open(Player player) {
        Inventory inventory;
        GuiListener.GuiHolder guiHolder = new GuiListener.GuiHolder(GuiListener.Type.CRAFT);
        guiHolder.inv = inventory = Bukkit.createInventory((InventoryHolder)guiHolder, (int)54, (String)TITLE);
        guiHolder.craftIndex = 0;
        this.render(player, inventory, guiHolder);
        player.openInventory(inventory);
    }

    public void render(Player player, Inventory inventory, GuiListener.GuiHolder guiHolder) {
        Object object;
        int n;
        List<CraftManager.Recipe> list = this.plugin.crafts().recipes();
        inventory.clear();
        for (n = 0; n < 54; ++n) {
            if (!this.isDeco(n)) continue;
            inventory.setItem(n, MainMenuGui.button(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
        }
        inventory.setItem(4, MainMenuGui.button(Material.CRAFTING_TABLE, "\u00a7b\u795e\u5668\u5408\u6210", List.of("\u00a77\u6536\u96c6\u788e\u7247/\u6750\u6599, \u653e\u5165\u4e0b\u65b9\u683c\u5b50", "\u00a77\u70b9\u51fb\u5408\u6210\u6309\u94ae, \u91cd\u94f8\u4f20\u5947\u795e\u5668", "", "\u00a77\u603b\u5171 \u00a7e" + list.size() + " \u00a77\u6761\u914d\u65b9")));
        if (list.isEmpty()) {
            inventory.setItem(24, MainMenuGui.button(Material.BARRIER, "\u00a7c\u6682\u65e0\u914d\u65b9", List.of("\u00a77\u8bf7\u5728 config.yml \u7684 crafting.recipes \u6bb5\u914d\u7f6e")));
            inventory.setItem(46, MainMenuGui.button(Material.GRAY_DYE, "\u00a77\u2190 \u4e0a\u4e00\u914d\u65b9", List.of("")));
            inventory.setItem(47, MainMenuGui.button(Material.GRAY_DYE, "\u00a77\u4e0b\u4e00\u914d\u65b9 \u2192", List.of("")));
            inventory.setItem(49, MainMenuGui.button(Material.BARRIER, "\u00a7c\u5408\u6210", List.of("\u00a77\u65e0\u914d\u65b9\u53ef\u5408\u6210")));
            inventory.setItem(50, MainMenuGui.button(Material.BARRIER, "\u00a7c\u5173\u95ed", List.of("\u00a77\u5173\u95ed\u5de5\u4f5c\u53f0")));
            return;
        }
        n = guiHolder.craftIndex;
        if (n < 0 || n >= list.size()) {
            n = 0;
        }
        guiHolder.craftIndex = n;
        CraftManager.Recipe recipe = list.get(n);
        ItemStack itemStack = this.plugin.artifacts().buildItem(recipe.artifact());
        if (itemStack != null) {
            object = itemStack.getItemMeta();
            if (object != null) {
                ArrayList<Object> arrayList = new ArrayList<Object>();
                if (object.getLore() != null) {
                    arrayList.addAll(object.getLore());
                }
                arrayList.add("");
                arrayList.add("\u00a77\u2190 \u5408\u6210\u4ea7\u7269: \u00a7f" + this.nameOfart(recipe.artifact()));
                arrayList.add("\u00a7e\u53ef\u5408\u6210 \u00a7a" + this.canCraft(inventory, recipe) + " \u00a7e\u6b21");
                object.setLore(arrayList);
                itemStack.setItemMeta((ItemMeta)object);
            }
            inventory.setItem(24, itemStack);
        } else {
            inventory.setItem(24, MainMenuGui.button(Material.BARRIER, "\u00a7c\u672a\u77e5\u795e\u5668", List.of("\u00a77artifact key: \u00a7e" + recipe.artifact())));
        }
        object = new ArrayList<String>(recipe.ingredients().keySet());
        for (int i = 0; i < MAT_SLOTS.length; ++i) {
            int n2 = MAT_SLOTS[i];
            ItemStack itemStack2 = inventory.getItem(n2);
            if (itemStack2 != null && !itemStack2.getType().isAir()) continue;
            if (i < object.size()) {
                String string = (String)object.get(i);
                CraftManager.CraftDef craftDef = this.craftDef(string);
                int n3 = recipe.ingredients().get(string);
                int n4 = this.ownedInBag(player, string);
                Material material = craftDef != null ? craftDef.material() : Material.BARRIER;
                inventory.setItem(n2, MainMenuGui.button(material, (String)(craftDef != null ? "\u00a77\u9700\u8981: " + CraftGui.amp(craftDef.name()) : "\u00a77\u672a\u77e5\u6750\u6599"), List.of("\u00a77\u9700\u6c42: \u00a7e" + this.defName(string) + " \u00a77x" + n3, "\u00a77\u62e5\u6709: " + (n4 >= n3 ? "\u00a7a" : "\u00a7c") + n4, "", "\u00a77\u5c06\u788e\u7247/\u6750\u6599\u70b9\u51fb\u653e\u5165\u6b64\u683c")));
                continue;
            }
            inventory.setItem(n2, MainMenuGui.button(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
        }
        inventory.setItem(46, MainMenuGui.button(Material.ARROW, "\u00a77\u2190 \u4e0a\u4e00\u914d\u65b9", List.of("\u00a77\u5207\u6362\u5230\u4e00\u4e2a\u914d\u65b9")));
        inventory.setItem(47, MainMenuGui.button(Material.ARROW, "\u00a77\u4e0b\u4e00\u914d\u65b9 \u2192", List.of("\u00a77\u5207\u6362\u5230\u4e0b\u4e00\u4e2a\u914d\u65b9")));
        inventory.setItem(49, MainMenuGui.button(Material.ANVIL, "\u00a7a\u00a7l\u5408\u6210\u795e\u5668", List.of("\u00a77\u5c06\u4e0a\u65b9\u6750\u6599\u683c\u5b50\u4e2d\u7684\u788e\u7247/\u6750\u6599", "\u00a77\u5408\u6210\u5bf9\u5e94\u795e\u5668", "", "\u00a7e\u70b9\u51fb\u5408\u6210")));
        inventory.setItem(50, MainMenuGui.button(Material.BARRIER, "\u00a7c\u5173\u95ed", List.of("\u00a77\u5173\u95ed\u5de5\u4f5c\u53f0")));
    }

    public List<ItemStack> saveMats(Inventory inventory) {
        ArrayList<ItemStack> arrayList = new ArrayList<ItemStack>();
        for (int n : MAT_SLOTS) {
            ItemStack itemStack = inventory.getItem(n);
            arrayList.add(itemStack == null ? null : itemStack.clone());
        }
        return arrayList;
    }

    public void restoreMats(Inventory inventory, List<ItemStack> list) {
        if (list == null || list.size() != MAT_SLOTS.length) {
            return;
        }
        for (int i = 0; i < MAT_SLOTS.length; ++i) {
            ItemStack itemStack = list.get(i);
            if (itemStack == null) continue;
            inventory.setItem(MAT_SLOTS[i], itemStack.clone());
        }
    }

    /*
     * WARNING - void declaration
     */
    public void craft(Player player, Inventory inventory, GuiListener.GuiHolder guiHolder) {
        int n;
        List<CraftManager.Recipe> list = this.plugin.crafts().recipes();
        if (list.isEmpty()) {
            player.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u6682\u65e0\u53ef\u7528\u914d\u65b9\u3002");
            return;
        }
        int n2 = guiHolder.craftIndex;
        if (n2 < 0 || n2 >= list.size()) {
            return;
        }
        CraftManager.Recipe recipe = list.get(n2);
        LinkedHashMap<String, Integer> linkedHashMap = new LinkedHashMap<String, Integer>();
        ArrayList<ItemStack> arrayList = new ArrayList<ItemStack>();
        int[] nArray = MAT_SLOTS;
        int n3 = nArray.length;
        boolean bl = false;
        while (entry < n3) {
            n = nArray[entry];
            ItemStack itemStack = inventory.getItem(n);
            if (itemStack != null && !itemStack.getType().isAir()) {
                String itemStack2 = this.plugin.crafts().craftKey(itemStack);
                if (itemStack2 == null) {
                    arrayList.add(itemStack);
                    inventory.setItem(n, null);
                } else {
                    linkedHashMap.merge(itemStack2, itemStack.getAmount(), Integer::sum);
                }
            }
            ++entry;
        }
        boolean bl2 = true;
        for (Map.Entry<String, Integer> object : recipe.ingredients().entrySet()) {
            n = linkedHashMap.getOrDefault(object.getKey(), 0);
            if (n >= object.getValue()) continue;
            bl2 = false;
            break;
        }
        if (!bl2) {
            player.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u6750\u6599\u4e0d\u8db3\u6216\u7f3a\u5931, \u65e0\u6cd5\u5408\u6210 " + this.nameOfart(recipe.artifact()) + "\u3002");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.2f);
            this.returnAlloc(inventory, arrayList, linkedHashMap, player);
            this.render(player, inventory, guiHolder);
            return;
        }
        LinkedHashMap<String, Integer> linkedHashMap2 = new LinkedHashMap<String, Integer>(recipe.ingredients());
        for (int n4 : MAT_SLOTS) {
            ItemStack itemStack = inventory.getItem(n4);
            if (itemStack == null || itemStack.getType().isAir()) continue;
            String string = this.plugin.crafts().craftKey(itemStack);
            if (string == null) {
                arrayList.add(itemStack);
                inventory.setItem(n4, null);
                continue;
            }
            int n5 = linkedHashMap2.getOrDefault(string, 0);
            if (n5 <= 0) {
                arrayList.add(itemStack);
                inventory.setItem(n4, null);
                continue;
            }
            int n6 = Math.min(itemStack.getAmount(), n5);
            int n7 = itemStack.getAmount() - n6;
            if (n7 > 0) {
                itemStack.setAmount(n7);
                inventory.setItem(n4, itemStack);
            } else {
                inventory.setItem(n4, null);
            }
            linkedHashMap2.put(string, n5 - n6);
        }
        ItemStack itemStack = this.plugin.artifacts().buildItem(recipe.artifact());
        if (itemStack == null) {
            player.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u795e\u5668\u6784\u5efa\u5931\u8d25, \u6750\u6599\u5df2\u9000\u8fd8\u3002");
            this.returnAlloc(inventory, new ArrayList<ItemStack>(), linkedHashMap, player);
            this.render(player, inventory, guiHolder);
            return;
        }
        HashMap hashMap = player.getInventory().addItem(new ItemStack[]{itemStack});
        for (ItemStack itemStack3 : hashMap.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), itemStack3);
        }
        player.sendMessage(this.plugin.msg("prefix") + "\u00a7a\u6210\u529f\u5408\u6210\u795e\u5668: \u00a7f" + this.nameOfart(recipe.artifact()) + "!");
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 1.2f);
        this.returnAlloc(inventory, arrayList, new LinkedHashMap<String, Integer>(), player);
        for (Object object : (Object)MAT_SLOTS) {
            inventory.setItem((int)object, null);
        }
        this.render(player, inventory, guiHolder);
    }

    private void returnAlloc(Inventory inventory, List<ItemStack> list, Map<String, Integer> map, Player player) {
        for (ItemStack itemStack : list) {
            HashMap hashMap = player.getInventory().addItem(new ItemStack[]{itemStack});
            for (ItemStack itemStack2 : hashMap.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), itemStack2);
            }
        }
    }

    private int ownedInBag(Player player, String string) {
        int n = 0;
        for (ItemStack itemStack : player.getInventory().getContents()) {
            String string2;
            if (itemStack == null || !string.equals(string2 = this.plugin.crafts().craftKey(itemStack))) continue;
            n += itemStack.getAmount();
        }
        return n;
    }

    private int canCraft(Inventory inventory, CraftManager.Recipe recipe) {
        LinkedHashMap<String, Integer> linkedHashMap = new LinkedHashMap<String, Integer>();
        for (int n2 : MAT_SLOTS) {
            String string;
            ItemStack itemStack = inventory.getItem(n2);
            if (itemStack == null || itemStack.getType().isAir() || (string = this.plugin.crafts().craftKey(itemStack)) == null) continue;
            linkedHashMap.merge(string, itemStack.getAmount(), Integer::sum);
        }
        int n = Integer.MAX_VALUE;
        for (Map.Entry<String, Integer> entry : recipe.ingredients().entrySet()) {
            int n2;
            n2 = linkedHashMap.getOrDefault(entry.getKey(), 0);
            n = Math.min(n, n2 / Math.max(1, entry.getValue()));
        }
        return n == Integer.MAX_VALUE ? 0 : n;
    }

    private boolean isDeco(int n) {
        return n <= 8 || n >= 36 && n <= 44;
    }

    private CraftManager.CraftDef craftDef(String string) {
        for (CraftManager.CraftDef craftDef : this.plugin.crafts().defs()) {
            if (!craftDef.key().equalsIgnoreCase(string)) continue;
            return craftDef;
        }
        return null;
    }

    private String defName(String string) {
        CraftManager.CraftDef craftDef = this.craftDef(string);
        return craftDef != null ? CraftGui.amp(craftDef.name()) : string;
    }

    private String nameOfart(String string) {
        return this.plugin.artifacts().nameOf(string);
    }

    private static String amp(String string) {
        return string == null ? "" : string.replace('&', '\u00a7');
    }
}
