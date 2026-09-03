/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
 *  org.bukkit.Material
 *  org.bukkit.NamespacedKey
 *  org.bukkit.Registry
 *  org.bukkit.configuration.ConfigurationSection
 *  org.bukkit.enchantments.Enchantment
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public class ArtifactManager {
    public static final String TITLE = "\u795e\u5668";
    private final TerraBoxPlugin plugin;
    private final NamespacedKey keyArtifact;
    private final NamespacedKey keyEffect;
    private final Map<String, ArtifactDef> defs = new HashMap<String, ArtifactDef>();

    public ArtifactManager(TerraBoxPlugin terraBoxPlugin) {
        this.plugin = terraBoxPlugin;
        this.keyArtifact = new NamespacedKey((Plugin)terraBoxPlugin, "artifact");
        this.keyEffect = new NamespacedKey((Plugin)terraBoxPlugin, "artifact_effect");
    }

    public void load() {
        this.defs.clear();
        ConfigurationSection configurationSection = this.plugin.getConfig().getConfigurationSection("artifacts");
        if (configurationSection == null || configurationSection.getKeys(false).isEmpty()) {
            this.plugin.getLogger().info("\u795e\u5668\u8868: \u672a\u68c0\u6d4b\u5230\u914d\u7f6e\u6bb5, \u5df2\u52a0\u8f7d\u5185\u7f6e\u9ed8\u8ba4\u795e\u5668");
            this.registerDefaults();
            return;
        }
        for (String string : configurationSection.getKeys(false)) {
            ConfigurationSection configurationSection2 = configurationSection.getConfigurationSection(string);
            if (configurationSection2 == null) continue;
            try {
                Material material = Material.matchMaterial((String)configurationSection2.getString("material", "DIAMOND_SWORD").toUpperCase(Locale.ROOT));
                if (material == null || !material.isItem()) {
                    this.plugin.getLogger().warning("\u795e\u5668 [" + string + "] \u6750\u8d28\u65e0\u6548: " + configurationSection2.getString("material"));
                    continue;
                }
                String string2 = configurationSection2.getString("effect", "").toUpperCase(Locale.ROOT);
                String string3 = configurationSection2.getString("name", "&6&l" + string);
                ArrayList<String> arrayList = new ArrayList<String>(configurationSection2.getStringList("lore"));
                double d = configurationSection2.getDouble("proc-chance", 0.25);
                double d2 = configurationSection2.getDouble("magnitude", 1.0);
                Map<Enchantment, Integer> map = this.parseEnchants(configurationSection2.get("enchants"));
                this.defs.put(string.toLowerCase(Locale.ROOT), new ArtifactDef(material, string.toLowerCase(Locale.ROOT), string3, arrayList, string2, d, d2, map));
            }
            catch (Exception exception) {
                this.plugin.getLogger().warning("\u795e\u5668 [" + string + "] \u89e3\u6790\u5931\u8d25: " + exception.getMessage());
            }
        }
        this.plugin.getLogger().info("\u795e\u5668\u8868\u52a0\u8f7d\u5b8c\u6210: " + this.defs.size() + " \u4ef6");
    }

    public int size() {
        return this.defs.size();
    }

    private void registerDefaults() {
        this.defs.put("celestial_blade", new ArtifactDef(Material.NETHERITE_SWORD, "celestial_blade", "&6&l\u82cd\u7a79\u4e4b\u5203", List.of("&7\u4f20\u8bf4\u4e2d\u65a9\u843d\u661f\u8fb0\u7684\u795e\u5251\u3002", "&7\u653b\u51fb\u5438\u8840, \u653b\u51fb\u547d\u4e2d\u6982\u7387\u8ba9\u76ee\u6807\u6d41\u8840\u3002"), "LIFESTEAL", 0.3, 1.0, Map.of(ArtifactManager.e("sharpeness"), 5, ArtifactManager.e("unbreaking"), 4, ArtifactManager.e("fire_aspect"), 2)));
        this.defs.put("aegis_axe", new ArtifactDef(Material.NETHERITE_AXE, "aegis_axe", "&c&l\u795e\u76fe\u91cd\u65a7", List.of("&7\u4e00\u65a7\u53ef\u64bc\u52a8\u5c71\u5cb3\u3002", "&7\u9ad8\u989d\u653b\u51fb, \u547d\u4e2d\u6982\u7387\u51fb\u9000\u51bb\u7ed3\u3002"), "FROST", 0.35, 1.2, Map.of(ArtifactManager.e("sharpness"), 5, ArtifactManager.e("unbreaking"), 4, ArtifactManager.e("efficiency"), 5)));
        this.defs.put("draco_bow", new ArtifactDef(Material.BOW, "draco_bow", "&e&l\u5c60\u9f99\u5723\u5f13", List.of("&7\u7bad\u65e0\u865a\u53d1, \u7bad\u7bad\u7d22\u547d\u3002", "&7\u5c04\u51fa\u7684\u7bad\u9644\u5e26\u706b\u7130\u4e0e\u9ad8\u989d\u4f24\u5bb3\u3002"), "STRING", 0.4, 1.0, Map.of(ArtifactManager.e("power"), 5, ArtifactManager.e("flame"), 2, ArtifactManager.e("infinity"), 1, ArtifactManager.e("punch"), 2)));
        this.defs.put("titan_chest", new ArtifactDef(Material.NETHERITE_CHESTPLATE, "titan_chest", "&d&l\u6cf0\u5766\u94e0\u7532", List.of("&7\u575a\u4e0d\u53ef\u6467\u7684\u5de8\u4eba\u62a4\u7532\u3002", "&7\u53d7\u51fb\u53cd\u5f39\u4f24\u5bb3, \u5927\u5e45\u51cf\u4f24\u3002"), "THORNS", 0.25, 2.0, Map.of(ArtifactManager.e("protection"), 5, ArtifactManager.e("unbreaking"), 4, ArtifactManager.e("thorns"), 3)));
        this.defs.put("wind_boots", new ArtifactDef(Material.NETHERITE_BOOTS, "wind_boots", "&b&l\u75be\u98ce\u4e4b\u9774", List.of("&7\u5982\u98ce\u822c\u8fc5\u6377\u3002", "&7\u5927\u5e45\u63d0\u5347\u79fb\u52a8\u901f\u5ea6\u3002"), "SPEED", 0.3, 0.25, Map.of(ArtifactManager.e("protection"), 4, ArtifactManager.e("feather_falling"), 4, ArtifactManager.e("unbreaking"), 4)));
        this.defs.put("vampire_fang", new ArtifactDef(Material.NETHERITE_AXE, "vampire_fang", "&d&l\u5438\u8840\u7360\u7259", List.of("&7\u996e\u8840\u800c\u730e, \u6108\u6218\u6108\u52c7\u3002", "&7\u6bcf\u6b21\u653b\u51fb\u5438\u53d6\u5927\u91cf\u751f\u547d\u3002"), "VAMPIRIC", 0.35, 2.0, Map.of(ArtifactManager.e("sharpness"), 5, ArtifactManager.e("unbreaking"), 4, ArtifactManager.e("looting"), 3)));
    }

    private static Enchantment e(String string) {
        try {
            String[] stringArray = (String[])Registry.ENCHANTMENT.get(NamespacedKey.minecraft((String)string));
            if (stringArray != null) {
                return stringArray;
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        for (String string2 : new String[]{"sharpness", "protection"}) {
            try {
                Enchantment enchantment = (Enchantment)Registry.ENCHANTMENT.get(NamespacedKey.minecraft((String)string2));
                if (enchantment == null) continue;
                return enchantment;
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
        return null;
    }

    public ItemStack buildItem(String string) {
        Object object;
        ArtifactDef artifactDef = this.defs.get(string.toLowerCase(Locale.ROOT));
        if (artifactDef == null) {
            return null;
        }
        ItemStack itemStack = new ItemStack(artifactDef.material, 1);
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta != null) {
            itemMeta.displayName((Component)LegacyComponentSerializer.legacyAmpersand().deserialize(artifactDef.name));
            object = new ArrayList();
            object.add(LegacyComponentSerializer.legacyAmpersand().deserialize("&8&m               &r&6&l \u795e \u5668 &8&m               "));
            for (String string2 : artifactDef.lore) {
                object.add(LegacyComponentSerializer.legacyAmpersand().deserialize(string2));
            }
            object.add(LegacyComponentSerializer.legacyAmpersand().deserialize("&7&o\u2014\u2014 \u88c5\u5907\u540e\u83b7\u5f97\u795e\u5668\u4e4b\u529b \u2014\u2014"));
            itemMeta.lore((List)object);
            itemStack.setItemMeta(itemMeta);
        }
        for (Map.Entry<Enchantment, Integer> entry : artifactDef.enchants.entrySet()) {
            try {
                itemStack.addUnsafeEnchantment(entry.getKey(), Math.min(127, entry.getValue()));
            }
            catch (Throwable throwable) {}
        }
        object = artifactDef.key;
        itemStack.editMeta(arg_0 -> this.lambda$buildItem$0((String)object, artifactDef, arg_0));
        return itemStack;
    }

    public String artifactKey(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return null;
        }
        try {
            return (String)itemStack.getItemMeta().getPersistentDataContainer().get(this.keyArtifact, PersistentDataType.STRING);
        }
        catch (Throwable throwable) {
            return null;
        }
    }

    public boolean isArtifact(ItemStack itemStack) {
        return this.artifactKey(itemStack) != null;
    }

    public String randomKey() {
        if (this.defs.isEmpty()) {
            return null;
        }
        ArrayList<ArtifactDef> arrayList = new ArrayList<ArtifactDef>(this.defs.values());
        return ((ArtifactDef)arrayList.get((int)ThreadLocalRandom.current().nextInt((int)arrayList.size()))).key;
    }

    public List<String> keys() {
        return new ArrayList<String>(this.defs.keySet());
    }

    public String nameOf(String string) {
        ArtifactDef artifactDef = this.defs.get(string.toLowerCase(Locale.ROOT));
        if (artifactDef == null) {
            return string;
        }
        return LegacyComponentSerializer.legacySection().serialize((Component)LegacyComponentSerializer.legacyAmpersand().deserialize(artifactDef.name));
    }

    public String effectOf(ItemStack itemStack) {
        if (itemStack == null) {
            return null;
        }
        try {
            return (String)itemStack.getItemMeta().getPersistentDataContainer().get(this.keyEffect, PersistentDataType.STRING);
        }
        catch (Throwable throwable) {
            return null;
        }
    }

    public double procChanceOf(ItemStack itemStack) {
        String string = this.artifactKey(itemStack);
        if (string == null) {
            return 0.0;
        }
        ArtifactDef artifactDef = this.defs.get(string);
        return artifactDef == null ? 0.0 : artifactDef.procChance;
    }

    public double magnitudeOf(ItemStack itemStack) {
        String string = this.artifactKey(itemStack);
        if (string == null) {
            return 0.0;
        }
        ArtifactDef artifactDef = this.defs.get(string);
        return artifactDef == null ? 0.0 : artifactDef.magnitude;
    }

    private Map<Enchantment, Integer> parseEnchants(Object object) {
        HashMap<Enchantment, Integer> hashMap;
        block3: {
            String string;
            block2: {
                hashMap = new HashMap<Enchantment, Integer>();
                if (!(object instanceof Map)) break block2;
                Map map = (Map)object;
                for (Map.Entry entry : map.entrySet()) {
                    this.addEnch(hashMap, String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                }
                break block3;
            }
            if (!(object instanceof String) || (string = (String)object).isBlank()) break block3;
            for (String string2 : string.split("[;,]")) {
                String[] stringArray = string2.trim().split(":");
                if (stringArray.length != 2) continue;
                this.addEnch(hashMap, stringArray[0].trim(), stringArray[1].trim());
            }
        }
        return hashMap;
    }

    private void addEnch(Map<Enchantment, Integer> map, String string, String string2) {
        try {
            Enchantment enchantment = (Enchantment)Registry.ENCHANTMENT.get(NamespacedKey.minecraft((String)string.toLowerCase(Locale.ROOT)));
            if (enchantment != null) {
                map.put(enchantment, Integer.parseInt(string2));
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    private /* synthetic */ void lambda$buildItem$0(String string, ArtifactDef artifactDef, ItemMeta itemMeta) {
        PersistentDataContainer persistentDataContainer = itemMeta.getPersistentDataContainer();
        persistentDataContainer.set(this.keyArtifact, PersistentDataType.STRING, (Object)string);
        if (artifactDef.effect != null && !artifactDef.effect.isEmpty()) {
            persistentDataContainer.set(this.keyEffect, PersistentDataType.STRING, (Object)artifactDef.effect);
        }
    }

    public record ArtifactDef(Material material, String key, String name, List<String> lore, String effect, double procChance, double magnitude, Map<Enchantment, Integer> enchants) {
    }
}
