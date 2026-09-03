/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.TextComponent
 *  net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
 *  org.bukkit.Material
 *  org.bukkit.NamespacedKey
 *  org.bukkit.configuration.ConfigurationSection
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.bukkit.persistence.PersistentDataContainer
 *  org.bukkit.persistence.PersistentDataType
 *  org.bukkit.plugin.Plugin
 */
package com.terrabox;

import com.terrabox.TerraBoxPlugin;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public class CraftManager {
    private final TerraBoxPlugin plugin;
    private final NamespacedKey keyCraft;
    private final NamespacedKey keyArtifact;
    private final Map<String, CraftDef> defs = new HashMap<String, CraftDef>();
    private final List<Recipe> recipes = new ArrayList<Recipe>();

    public CraftManager(TerraBoxPlugin terraBoxPlugin) {
        this.plugin = terraBoxPlugin;
        this.keyCraft = new NamespacedKey((Plugin)terraBoxPlugin, "craft");
        this.keyArtifact = new NamespacedKey((Plugin)terraBoxPlugin, "craft_artifact");
    }

    public void load() {
        Cloneable cloneable;
        String string;
        Material material;
        ConfigurationSection configurationSection;
        this.defs.clear();
        this.recipes.clear();
        ConfigurationSection configurationSection2 = this.plugin.getConfig().getConfigurationSection("crafting.fragments");
        if (configurationSection2 != null) {
            configurationSection = configurationSection2.getKeys(false).iterator();
            while (configurationSection.hasNext()) {
                String string2 = (String)configurationSection.next();
                Object object = configurationSection2.getConfigurationSection(string2);
                if (object == null) continue;
                try {
                    material = Material.matchMaterial((String)object.getString("material", "DIAMOND").toUpperCase(Locale.ROOT));
                    if (material == null || !material.isItem()) {
                        this.plugin.getLogger().warning("\u5408\u6210\u6750\u6599 [" + (String)string2 + "] \u6750\u8d28\u65e0\u6548: " + object.getString("material"));
                        continue;
                    }
                    string = object.getString("name", "&e&l" + (String)string2);
                    cloneable = new ArrayList(object.getStringList("lore"));
                    String string3 = object.getString("artifact", null);
                    this.defs.put(string2.toLowerCase(Locale.ROOT), new CraftDef(material, string2.toLowerCase(Locale.ROOT), string, (List<String>)((Object)cloneable), string3));
                }
                catch (Exception exception) {
                    this.plugin.getLogger().warning("\u5408\u6210\u6750\u6599 [" + (String)string2 + "] \u89e3\u6790\u5931\u8d25: " + exception.getMessage());
                }
            }
        }
        if (this.defs.isEmpty()) {
            this.registerDefaultDefs();
        }
        if ((configurationSection = this.plugin.getConfig().getConfigurationSection("crafting.recipes")) != null) {
            for (Object object : configurationSection.getKeys(false)) {
                material = configurationSection.getConfigurationSection((String)object);
                if (material == null) continue;
                try {
                    string = material.getString("artifact", (String)object);
                    cloneable = new LinkedHashMap();
                    for (String string4 : material.getKeys(false)) {
                        if (string4.equals("artifact")) continue;
                        int n = Math.max(1, material.getInt(string4, 1));
                        ((HashMap)cloneable).put(string4.toLowerCase(Locale.ROOT), n);
                    }
                    if (((HashMap)cloneable).isEmpty()) continue;
                    this.recipes.add(new Recipe(string, (LinkedHashMap<String, Integer>)cloneable));
                }
                catch (Exception exception) {
                    this.plugin.getLogger().warning("\u914d\u65b9 [" + (String)object + "] \u89e3\u6790\u5931\u8d25: " + exception.getMessage());
                }
            }
        }
        if (this.recipes.isEmpty()) {
            this.registerDefaultRecipes();
        }
        this.plugin.getLogger().info("\u5408\u6210\u7cfb\u7edf\u52a0\u8f7d\u5b8c\u6210: " + this.defs.size() + " \u79cd\u788e\u7247/\u6750\u6599, " + this.recipes.size() + " \u6761\u914d\u65b9");
    }

    private void registerDefaultDefs() {
    }

    private void registerDefaultRecipes() {
    }

    private static LinkedHashMap<String, Integer> linked(Object ... objectArray) {
        LinkedHashMap<String, Integer> linkedHashMap = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < objectArray.length; i += 2) {
            linkedHashMap.put(String.valueOf(objectArray[i]), Integer.parseInt(String.valueOf(objectArray[i + 1])));
        }
        return linkedHashMap;
    }

    public ItemStack buildItem(String string) {
        CraftDef craftDef = this.defs.get(string.toLowerCase(Locale.ROOT));
        if (craftDef == null) {
            return null;
        }
        ItemStack itemStack = new ItemStack(craftDef.material(), 1);
        ItemMeta itemMeta2 = itemStack.getItemMeta();
        if (itemMeta2 != null) {
            itemMeta2.displayName((Component)LegacyComponentSerializer.legacyAmpersand().deserialize(craftDef.name()));
            ArrayList<TextComponent> arrayList = new ArrayList<TextComponent>();
            arrayList.add(LegacyComponentSerializer.legacyAmpersand().deserialize("&8&m            &r&b&l \u5408\u6210\u6750\u6599 &8&m            "));
            for (String string2 : craftDef.lore()) {
                arrayList.add(LegacyComponentSerializer.legacyAmpersand().deserialize(string2));
            }
            arrayList.add(LegacyComponentSerializer.legacyAmpersand().deserialize("&7&o\u53ef\u7528\u4e8e\u5de5\u4f5c\u53f0\u5408\u6210\u795e\u5668"));
            itemMeta2.lore(arrayList);
            itemStack.setItemMeta(itemMeta2);
        }
        itemStack.editMeta(itemMeta -> {
            PersistentDataContainer persistentDataContainer = itemMeta.getPersistentDataContainer();
            persistentDataContainer.set(this.keyCraft, PersistentDataType.STRING, (Object)craftDef.key());
            if (craftDef.artifact() != null) {
                persistentDataContainer.set(this.keyArtifact, PersistentDataType.STRING, (Object)craftDef.artifact());
            }
        });
        return itemStack;
    }

    public String craftKey(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return null;
        }
        try {
            return (String)itemStack.getItemMeta().getPersistentDataContainer().get(this.keyCraft, PersistentDataType.STRING);
        }
        catch (Throwable throwable) {
            return null;
        }
    }

    public boolean isCraftItem(ItemStack itemStack) {
        return this.craftKey(itemStack) != null;
    }

    public String fragmentArtifact(ItemStack itemStack) {
        if (itemStack == null) {
            return null;
        }
        try {
            return (String)itemStack.getItemMeta().getPersistentDataContainer().get(this.keyArtifact, PersistentDataType.STRING);
        }
        catch (Throwable throwable) {
            return null;
        }
    }

    public List<String> keys() {
        return new ArrayList<String>(this.defs.keySet());
    }

    public List<CraftDef> defs() {
        return new ArrayList<CraftDef>(this.defs.values());
    }

    public String nameOf(String string) {
        CraftDef craftDef = this.defs.get(string.toLowerCase(Locale.ROOT));
        if (craftDef == null) {
            return string;
        }
        return LegacyComponentSerializer.legacySection().serialize((Component)LegacyComponentSerializer.legacyAmpersand().deserialize(craftDef.name()));
    }

    public List<Recipe> recipes() {
        return new ArrayList<Recipe>(this.recipes);
    }

    public Recipe recipeFor(String string) {
        for (Recipe recipe : this.recipes) {
            if (!recipe.artifact().equalsIgnoreCase(string)) continue;
            return recipe;
        }
        return null;
    }

    public Map<String, Integer> ownedCounts(ItemStack[] itemStackArray, Recipe recipe) {
        HashMap<String, Integer> hashMap = new HashMap<String, Integer>();
        for (ItemStack itemStack : itemStackArray) {
            String string;
            if (itemStack == null || (string = this.craftKey(itemStack)) == null) continue;
            hashMap.merge(string, itemStack.getAmount(), Integer::sum);
        }
        return hashMap;
    }

    public record CraftDef(Material material, String key, String name, List<String> lore, String artifact) {
    }

    public record Recipe(String artifact, LinkedHashMap<String, Integer> ingredients) {
    }
}
