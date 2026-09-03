/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Material
 *  org.bukkit.NamespacedKey
 *  org.bukkit.Registry
 *  org.bukkit.enchantments.Enchantment
 *  org.bukkit.inventory.Inventory
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.EnchantmentStorageMeta
 *  org.bukkit.inventory.meta.ItemMeta
 */
package com.terrabox;

import com.terrabox.Rarity;
import com.terrabox.TerraBoxPlugin;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

public class LootManager {
    private final TerraBoxPlugin plugin;
    private final Map<Rarity, List<LootItem>> tables = new HashMap<Rarity, List<LootItem>>();
    private final Map<Rarity, MoneyReward> moneyRewards = new HashMap<Rarity, MoneyReward>();

    public LootManager(TerraBoxPlugin terraBoxPlugin) {
        this.plugin = terraBoxPlugin;
    }

    public void load() {
        this.tables.clear();
        this.moneyRewards.clear();
        for (Rarity rarity : Rarity.values()) {
            Object object2;
            ArrayList<LootItem> arrayList = new ArrayList<LootItem>();
            List list = this.plugin.getConfig().getMapList("loot." + rarity.key() + ".items");
            int n = 0;
            for (Object object2 : list) {
                ++n;
                if (!(object2 instanceof Map)) continue;
                Map map = (Map)object2;
                try {
                    Object v = map.get("material");
                    if (v == null) continue;
                    Material material = Material.matchMaterial((String)String.valueOf(v));
                    if (material == null || !material.isItem()) {
                        this.plugin.getLogger().warning("\u6218\u5229\u54c1\u914d\u7f6e\u65e0\u6548: " + rarity.key() + "#" + n + " material=" + String.valueOf(v));
                        continue;
                    }
                    int n2 = LootManager.intOf(map.get("min"), 1);
                    int n3 = Math.max(n2, LootManager.intOf(map.get("max"), n2));
                    double d = LootManager.doubleOf(map.get("chance"), 100.0);
                    String string = LootManager.stringOf(map.get("name"), null);
                    String string2 = LootManager.stringOf(map.get("lore"), null);
                    String string3 = LootManager.stringOf(map.get("special"), null);
                    String string4 = LootManager.stringOf(map.get("artifact"), null);
                    String string5 = LootManager.stringOf(map.get("enchant-stone"), null);
                    String string6 = LootManager.stringOf(map.get("craft"), null);
                    LootItem lootItem = new LootItem(material, n2, n3, d, this.parseEnchants(map.get("enchants")), string, string2, string3, string4, string5, string6);
                    arrayList.add(lootItem);
                }
                catch (Exception exception) {
                    this.plugin.getLogger().warning("\u6218\u5229\u54c1\u89e3\u6790\u5931\u8d25 " + rarity.key() + "#" + n + ": " + exception.getMessage());
                }
            }
            this.tables.put(rarity, List.copyOf(arrayList));
            Object object3 = this.plugin.getConfig().get("loot." + rarity.key() + ".money");
            if (!(object3 instanceof Map)) continue;
            object2 = (Map)object3;
            this.moneyRewards.put(rarity, new MoneyReward(LootManager.longOf(object2.get("min"), 0L), LootManager.longOf(object2.get("max"), 0L), LootManager.doubleOf(object2.get("chance"), 0.0)));
        }
        int n = this.tables.values().stream().mapToInt(List::size).sum();
        this.plugin.getLogger().info("\u6218\u5229\u54c1\u8868\u52a0\u8f7d\u5b8c\u6210: 5 \u6863\u5171 " + n + " \u79cd\u6761\u76ee");
    }

    private static int intOf(Object object, int n) {
        int n2;
        if (object instanceof Number) {
            Number number = (Number)object;
            n2 = number.intValue();
        } else {
            n2 = n;
        }
        return n2;
    }

    private static long longOf(Object object, long l) {
        long l2;
        if (object instanceof Number) {
            Number number = (Number)object;
            l2 = number.longValue();
        } else {
            l2 = l;
        }
        return l2;
    }

    private static double doubleOf(Object object, double d) {
        double d2;
        if (object instanceof Number) {
            Number number = (Number)object;
            d2 = number.doubleValue();
        } else if (object instanceof String) {
            String string = (String)object;
            d2 = Double.parseDouble(string);
        } else {
            d2 = d;
        }
        return d2;
    }

    private static String stringOf(Object object, String string) {
        return object == null ? string : String.valueOf(object);
    }

    private Map<Enchantment, int[]> parseEnchants(Object object) {
        HashMap<Enchantment, int[]> hashMap;
        block3: {
            String string;
            block2: {
                hashMap = new HashMap<Enchantment, int[]>();
                if (!(object instanceof Map)) break block2;
                Map map = (Map)object;
                for (Map.Entry entry : map.entrySet()) {
                    this.addEnchant(hashMap, String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                }
                break block3;
            }
            if (!(object instanceof String) || (string = (String)object).isBlank()) break block3;
            for (String string2 : string.split("[;,]")) {
                String[] stringArray = string2.trim().split(":");
                if (stringArray.length != 2) continue;
                this.addEnchant(hashMap, stringArray[0].trim(), stringArray[1].trim());
            }
        }
        return hashMap;
    }

    private void addEnchant(Map<Enchantment, int[]> map, String string, String string2) {
        try {
            Enchantment enchantment = this.findEnchant(string);
            if (enchantment == null) {
                this.plugin.getLogger().warning("\u672a\u77e5\u9644\u9b54: " + string + " (\u5df2\u8df3\u8fc7)");
                return;
            }
            if (string2.contains("-")) {
                String[] stringArray = string2.split("-");
                map.put(enchantment, new int[]{Integer.parseInt(stringArray[0]), Integer.parseInt(stringArray[1])});
            } else {
                int n = Integer.parseInt(string2);
                map.put(enchantment, new int[]{n, n});
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    private Enchantment findEnchant(String string) {
        try {
            return (Enchantment)Registry.ENCHANTMENT.get(NamespacedKey.minecraft((String)string.toLowerCase(Locale.ROOT)));
        }
        catch (Throwable throwable) {
            return null;
        }
    }

    public int fillInventory(Inventory inventory, Rarity rarity) {
        int n;
        LootItem lootItem22;
        inventory.clear();
        List list = this.tables.getOrDefault((Object)rarity, List.of());
        if (list.isEmpty()) {
            return 0;
        }
        ThreadLocalRandom threadLocalRandom = ThreadLocalRandom.current();
        int n2 = this.plugin.getConfig().getInt("loot." + rarity.key() + ".min-stacks", rarity.minStacks());
        int n3 = this.plugin.getConfig().getInt("loot." + rarity.key() + ".max-stacks", rarity.maxStacks());
        n3 = Math.max(n3, n2);
        ArrayList arrayList = new ArrayList(list);
        Collections.shuffle(arrayList, threadLocalRandom);
        int n4 = 0;
        for (LootItem lootItem22 : arrayList) {
            int n5;
            if (n4 >= n3 && n3 > 0) break;
            if (threadLocalRandom.nextDouble(100.0) >= lootItem22.chance || (n = lootItem22.min >= lootItem22.max ? lootItem22.min : threadLocalRandom.nextInt(lootItem22.min, lootItem22.max + 1)) <= 0) continue;
            if (lootItem22.special != null || lootItem22.artifact != null || lootItem22.enchantStone != null) {
                n = 1;
            }
            int n6 = Math.min(n, lootItem22.material.getMaxStackSize());
            for (int i = n; i > 0 && n6 > 0; i -= n5) {
                Object object;
                n5 = Math.min(n6, i);
                Object object2 = new ItemStack(lootItem22.material, n5);
                if (!lootItem22.enchants.isEmpty()) {
                    if (lootItem22.material == Material.ENCHANTED_BOOK) {
                        object = (EnchantmentStorageMeta)object2.getItemMeta();
                        for (Map.Entry entry : lootItem22.enchants.entrySet()) {
                            var19_23 = ((int[])entry.getValue())[0];
                            int n7 = var19_23 >= (var20_24 = Math.max(((int[])entry.getValue())[0], ((int[])entry.getValue())[1])) ? var19_23 : threadLocalRandom.nextInt(var19_23, var20_24 + 1);
                            if (n7 <= 0) continue;
                            try {
                                object.addStoredEnchant((Enchantment)entry.getKey(), n7, true);
                            }
                            catch (Exception exception) {}
                        }
                        object2.setItemMeta((ItemMeta)object);
                    } else {
                        object = lootItem22.enchants.entrySet().iterator();
                        while (object.hasNext()) {
                            Map.Entry entry = (Map.Entry)object.next();
                            int n8 = ((int[])entry.getValue())[0];
                            var20_24 = n8 >= (var19_23 = Math.max(((int[])entry.getValue())[0], ((int[])entry.getValue())[1])) ? n8 : threadLocalRandom.nextInt(n8, var19_23 + 1);
                            if (var20_24 <= 0) continue;
                            try {
                                object2.addUnsafeEnchantment((Enchantment)entry.getKey(), var20_24);
                            }
                            catch (Exception exception) {}
                        }
                    }
                }
                if ((lootItem22.name != null || lootItem22.lore != null) && (object = object2.getItemMeta()) != null) {
                    if (lootItem22.name != null) {
                        object.setDisplayName(LootManager.amp(lootItem22.name));
                    }
                    if (lootItem22.lore != null) {
                        object.setLore(Arrays.stream(lootItem22.lore.split("\\n")).map(LootManager::amp).collect(Collectors.toList()));
                    }
                    object2.setItemMeta((ItemMeta)object);
                }
                if (lootItem22.special != null && this.plugin.specialItems() != null && (object = this.plugin.specialItems().buildItem(lootItem22.special)) != null) {
                    object2 = object;
                }
                if (lootItem22.artifact != null && this.plugin.artifacts() != null && (object = this.plugin.artifacts().buildItem(lootItem22.artifact)) != null) {
                    object2 = object;
                }
                if (lootItem22.enchantStone != null && this.plugin.enchants() != null && (object = this.plugin.enchants().buildRandomStone()) != null) {
                    object2 = object;
                }
                if (lootItem22.craft != null && this.plugin.crafts() != null && (object = this.plugin.crafts().buildItem(lootItem22.craft)) != null) {
                    object.setAmount(n5);
                    object2 = object;
                }
                inventory.addItem(new ItemStack[]{object2});
                ++n4;
            }
        }
        int n9 = 0;
        while (n4 < n2 && n4 < 54 && n9 < 40) {
            lootItem22 = (LootItem)arrayList.get(threadLocalRandom.nextInt(arrayList.size()));
            if (lootItem22.special != null || lootItem22.artifact != null || lootItem22.enchantStone != null || lootItem22.craft != null) {
                ++n9;
                continue;
            }
            n = Math.max(1, lootItem22.min >= lootItem22.max ? lootItem22.min : threadLocalRandom.nextInt(lootItem22.min, lootItem22.max + 1));
            ItemStack itemStack = new ItemStack(lootItem22.material, Math.min(n, lootItem22.material.getMaxStackSize()));
            inventory.addItem(new ItemStack[]{itemStack});
            ++n4;
            ++n9;
        }
        if (n4 == 0) {
            lootItem22 = (LootItem)list.get(0);
            ItemStack itemStack = new ItemStack(lootItem22.material, Math.max(1, lootItem22.min));
            inventory.addItem(new ItemStack[]{itemStack});
            n4 = 1;
        }
        return n4;
    }

    public long rollMoney(Rarity rarity) {
        MoneyReward moneyReward = this.moneyRewards.get((Object)rarity);
        if (moneyReward == null || !this.plugin.getConfig().getBoolean("economy.loot-money", true)) {
            return 0L;
        }
        if (ThreadLocalRandom.current().nextDouble(100.0) >= moneyReward.chance) {
            return 0L;
        }
        if (moneyReward.max <= moneyReward.min) {
            return Math.max(0L, moneyReward.min);
        }
        return ThreadLocalRandom.current().nextLong(moneyReward.min, moneyReward.max + 1L);
    }

    public int tableSize(Rarity rarity) {
        return this.tables.getOrDefault((Object)rarity, List.of()).size();
    }

    private static String amp(String string) {
        return string == null ? "" : string.replace('&', '\u00a7');
    }

    private record LootItem(Material material, int min, int max, double chance, Map<Enchantment, int[]> enchants, String name, String lore, String special, String artifact, String enchantStone, String craft) {
    }

    private record MoneyReward(long min, long max, double chance) {
    }
}
