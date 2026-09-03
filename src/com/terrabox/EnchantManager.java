/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.TextComponent
 *  net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
 *  org.bukkit.Material
 *  org.bukkit.NamespacedKey
 *  org.bukkit.Registry
 *  org.bukkit.Sound
 *  org.bukkit.configuration.ConfigurationSection
 *  org.bukkit.enchantments.Enchantment
 *  org.bukkit.entity.Player
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
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public class EnchantManager {
    public static final String KEY = "terrabox_enchant";
    private final TerraBoxPlugin plugin;
    private final NamespacedKey keyEnchant;
    private final NamespacedKey keyLevel;
    private final Map<String, EnchantPool> pools = new HashMap<String, EnchantPool>();

    public EnchantManager(TerraBoxPlugin terraBoxPlugin) {
        this.plugin = terraBoxPlugin;
        this.keyEnchant = new NamespacedKey((Plugin)terraBoxPlugin, "enchant");
        this.keyLevel = new NamespacedKey((Plugin)terraBoxPlugin, "enchant_level");
    }

    public void load() {
        this.pools.clear();
        ConfigurationSection configurationSection = this.plugin.getConfig().getConfigurationSection("enchant-stones");
        if (configurationSection == null || configurationSection.getKeys(false).isEmpty()) {
            this.registerDefaults();
            return;
        }
        for (String string : configurationSection.getKeys(false)) {
            ConfigurationSection configurationSection2 = configurationSection.getConfigurationSection(string);
            if (configurationSection2 == null) continue;
            try {
                Material material = Material.matchMaterial((String)configurationSection2.getString("material", "LAPIS_LAZULI"));
                String string2 = configurationSection2.getString("name", "&b\u9644\u9b54\u77f3");
                ArrayList<String> arrayList = new ArrayList<String>(configurationSection2.getStringList("lore"));
                boolean bl = configurationSection2.getBoolean("exotic", false);
                this.pools.put(string.toLowerCase(Locale.ROOT), new EnchantPool(material, string2, arrayList, bl));
            }
            catch (Exception exception) {}
        }
        this.plugin.getLogger().info("\u9644\u9b54\u77f3\u8868\u52a0\u8f7d\u5b8c\u6210: " + this.pools.size() + " \u79cd");
    }

    private void registerDefaults() {
        this.pools.put("normal", new EnchantPool(Material.LAPIS_LAZULI, "&b&l\u9644\u9b54\u77f3", List.of("&7\u53f3\u952e\u5bf9\u624b\u6301\u7684\u6b66\u5668/\u5de5\u5177/\u62a4\u7532\u65bd\u52a0\u4e00\u4e2a\u968f\u673a\u9644\u9b54\u3002", "&7\u53f3\u952e\u4f7f\u7528\u524d\u8bf7\u624b\u6301\u76ee\u6807\u88c5\u5907\u3002"), false));
        this.pools.put("exotic", new EnchantPool(Material.AMETHYST_SHARD, "&d&l\u7a00\u6709\u9644\u9b54\u77f3", List.of("&7\u53f3\u952e\u5bf9\u624b\u6301\u7269\u54c1\u65bd\u52a0\u4e00\u4e2a &d\u9ad8\u7ea7\u9644\u9b54&7 (\u6982\u7387\u53cc\u500d\u5f3a\u5ea6)\u3002", "&7\u53f3\u952e\u4f7f\u7528\u524d\u8bf7\u624b\u6301\u76ee\u6807\u88c5\u5907\u3002"), true));
    }

    public ItemStack buildRandomStone() {
        boolean bl;
        boolean bl2 = bl = Math.random() < 0.3;
        if (bl) {
            return this.buildStoneEnch(this.randomEnchant(), this.randomLevel(3, 4), true);
        }
        return this.buildStoneEnch(this.randomEnchant(), this.randomLevel(1, 2), false);
    }

    public ItemStack buildStone(Enchantment enchantment, int n) {
        return this.buildStoneEnch(enchantment, n, n >= 3);
    }

    private ItemStack buildStoneEnch(Enchantment enchantment, int n, boolean bl) {
        Object object;
        ItemStack itemStack;
        ItemMeta itemMeta;
        EnchantPool enchantPool = this.pools.get(bl ? "exotic" : "normal");
        if (enchantPool == null) {
            enchantPool = this.pools.get("normal");
        }
        if (enchantPool == null) {
            enchantPool = new EnchantPool(Material.LAPIS_LAZULI, "&b&l\u9644\u9b54\u77f3", List.of(), false);
        }
        if ((itemMeta = (itemStack = new ItemStack(enchantPool.material(), 1)).getItemMeta()) != null) {
            object = LegacyComponentSerializer.legacyAmpersand().deserialize(enchantPool.name());
            itemMeta.displayName((Component)object);
            ArrayList<TextComponent> arrayList = new ArrayList<TextComponent>();
            for (String string : enchantPool.lore()) {
                arrayList.add(LegacyComponentSerializer.legacyAmpersand().deserialize(string));
            }
            String string = EnchantManager.nameOf(enchantment);
            arrayList.add(LegacyComponentSerializer.legacyAmpersand().deserialize("&7\u9644\u9b54: &f" + (String)string + " &7(\u7b49\u7ea7 &e" + n + "&7)"));
            itemMeta.lore(arrayList);
            itemStack.setItemMeta(itemMeta);
        }
        object = enchantment == null ? "SHARPNESS" : enchantment.getKey().getKey().toUpperCase(Locale.ROOT);
        int n2 = n;
        itemStack.editMeta(arg_0 -> this.lambda$buildStoneEnch$0((String)object, n2, arg_0));
        return itemStack;
    }

    public String enchantOf(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return null;
        }
        try {
            return (String)itemStack.getItemMeta().getPersistentDataContainer().get(this.keyEnchant, PersistentDataType.STRING);
        }
        catch (Throwable throwable) {
            return null;
        }
    }

    public boolean isEnchantStone(ItemStack itemStack) {
        return this.enchantOf(itemStack) != null;
    }

    public int levelOf(ItemStack itemStack) {
        if (itemStack == null) {
            return 1;
        }
        try {
            Integer n = (Integer)itemStack.getItemMeta().getPersistentDataContainer().get(this.keyLevel, PersistentDataType.INTEGER);
            return n == null ? 1 : n;
        }
        catch (Throwable throwable) {
            return 1;
        }
    }

    public boolean apply(Player player, ItemStack itemStack) {
        ItemStack itemStack2;
        String string = this.enchantOf(itemStack);
        if (string == null) {
            return false;
        }
        Enchantment enchantment = null;
        try {
            enchantment = (Enchantment)Registry.ENCHANTMENT.get(NamespacedKey.minecraft((String)string.toLowerCase(Locale.ROOT)));
        }
        catch (Throwable throwable) {
            enchantment = null;
        }
        if (enchantment == null) {
            player.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u672a\u77e5\u9644\u9b54: " + string);
            return false;
        }
        int n = this.levelOf(itemStack);
        boolean bl = this.isOffHandStone(player, itemStack);
        ItemStack itemStack3 = itemStack2 = bl ? player.getInventory().getItemInMainHand() : player.getInventory().getItemInOffHand();
        if (this.isEnchantStone(itemStack2)) {
            player.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u8bf7\u5728\u53e6\u4e00\u53ea\u624b\u6301\u6709\u8981\u9644\u9b54\u7684\u88c5\u5907 (\u4e0d\u8981\u4e24\u624b\u90fd\u62ff\u9644\u9b54\u77f3)\u3002");
            return false;
        }
        if (!(itemStack2 != null && !itemStack2.getType().isAir() && (itemStack2.getType().name().contains("_SWORD") || itemStack2.getType().name().contains("_PICKAXE") || itemStack2.getType().name().contains("_AXE") || itemStack2.getType().name().contains("_SHOVEL") || itemStack2.getType().name().contains("_HOE") || itemStack2.getType().name().contains("_BOW") || itemStack2.getType().name().contains("CROSSBOW") || itemStack2.getType().name().contains("_HELMET") || itemStack2.getType().name().contains("_CHESTPLATE") || itemStack2.getType().name().contains("_LEGGINGS") || itemStack2.getType().name().contains("_BOOTS") || itemStack2.getType().name().contains("TRIDENT")))) {
            player.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u8bf7\u624b\u6301\u4e00\u4ef6\u53ef\u9644\u9b54\u7684\u88c5\u5907 (\u6b66\u5668/\u5de5\u5177/\u76d4\u7532)\u3002");
            return false;
        }
        try {
            itemStack2.addUnsafeEnchantment(enchantment, n);
            if (bl) {
                player.getInventory().setItemInMainHand(itemStack2);
            } else {
                player.getInventory().setItemInOffHand(itemStack2);
            }
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
            player.sendMessage(this.plugin.msg("prefix") + "\u00a7a\u5df2\u4e3a\u624b\u6301\u88c5\u5907\u9644\u9b54: \u00a7f" + EnchantManager.nameOf(enchantment) + " \u00a77(\u7b49\u7ea7 \u00a7e" + n + "\u00a77)");
            return true;
        }
        catch (Throwable throwable) {
            player.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u9644\u9b54\u5931\u8d25: " + throwable.getMessage());
            return false;
        }
    }

    private boolean isOffHandStone(Player player, ItemStack itemStack) {
        try {
            ItemStack itemStack2 = player.getInventory().getItemInOffHand();
            if (itemStack2 != null && !itemStack2.getType().isAir() && this.isEnchantStone(itemStack2)) {
                ItemStack itemStack3 = player.getInventory().getItemInMainHand();
                return itemStack3 == null || itemStack3.getType().isAir() || !this.isEnchantStone(itemStack3);
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return false;
    }

    public boolean isMainHandStone(Player player, ItemStack itemStack) {
        try {
            ItemStack itemStack2 = player.getInventory().getItemInMainHand();
            return itemStack2 != null && !itemStack2.getType().isAir() && this.isEnchantStone(itemStack2);
        }
        catch (Throwable throwable) {
            return false;
        }
    }

    public boolean applyToTarget(Player player, ItemStack itemStack, ItemStack itemStack2) {
        String string = this.enchantOf(itemStack);
        if (string == null) {
            return false;
        }
        Enchantment enchantment = null;
        try {
            enchantment = (Enchantment)Registry.ENCHANTMENT.get(NamespacedKey.minecraft((String)string.toLowerCase(Locale.ROOT)));
        }
        catch (Throwable throwable) {
            enchantment = null;
        }
        if (enchantment == null) {
            player.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u672a\u77e5\u9644\u9b54: " + string);
            return false;
        }
        int n = this.levelOf(itemStack);
        if (itemStack2 == null || itemStack2.getType().isAir()) {
            player.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u76ee\u6807\u88c5\u5907\u4e3a\u7a7a\u3002");
            return false;
        }
        if (this.isEnchantStone(itemStack2)) {
            player.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u76ee\u6807\u4e0d\u662f\u53ef\u9644\u9b54\u88c5\u5907\u3002");
            return false;
        }
        try {
            itemStack2.addUnsafeEnchantment(enchantment, n);
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
            player.sendMessage(this.plugin.msg("prefix") + "\u00a7a\u5df2\u4e3a\u88c5\u5907\u9644\u9b54: \u00a7f" + EnchantManager.nameOf(enchantment) + " \u00a77(\u7b49\u7ea7 \u00a7e" + n + "\u00a77)");
            return true;
        }
        catch (Throwable throwable) {
            player.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u9644\u9b54\u5931\u8d25: " + throwable.getMessage());
            return false;
        }
    }

    public Enchantment randomEnchant() {
        Enchantment[] enchantmentArray;
        ArrayList<Enchantment> arrayList;
        try {
            arrayList = new ArrayList<Enchantment>();
            Registry.ENCHANTMENT.forEach(arrayList::add);
            enchantmentArray = arrayList.toArray(new Enchantment[0]);
        }
        catch (Throwable throwable) {
            enchantmentArray = new Enchantment[]{this.fallbackEnch()};
        }
        if (enchantmentArray.length == 0) {
            return this.fallbackEnch();
        }
        arrayList = new ArrayList();
        for (Enchantment enchantment : enchantmentArray) {
            String string;
            if (enchantment == null || !(string = enchantment.getKey().getKey().toLowerCase(Locale.ROOT)).contains("sharpness") && !string.contains("smite") && !string.contains("bane") && !string.contains("protection") && !string.contains("power") && !string.contains("unbreaking") && !string.contains("efficiency") && !string.contains("fortune") && !string.contains("fire") && !string.contains("looting") && !string.contains("thorns") && !string.contains("respiration") && !string.contains("feather") && !string.contains("mending") && !string.contains("punch") && !string.contains("flame") && !string.contains("lure") && !string.contains("luck") && !string.contains("sweeping") && !string.contains("knockback") && !string.contains("impaling") && !string.contains("infinity") && !string.contains("depth") && !string.contains("aqua") && !string.contains("silktouch") && !string.contains("piercing") && !string.contains("quick_charge") && !string.contains("loyalty") && !string.contains("riptide") && !string.contains("channeling") && !string.contains("multishot") && !string.contains("swift_sneak") && !string.contains("soul_speed")) continue;
            arrayList.add(enchantment);
        }
        if (arrayList.isEmpty()) {
            return this.fallbackEnch();
        }
        return (Enchantment)arrayList.get(ThreadLocalRandom.current().nextInt(arrayList.size()));
    }

    private Enchantment fallbackEnch() {
        try {
            return (Enchantment)Registry.ENCHANTMENT.get(NamespacedKey.minecraft((String)"sharpness"));
        }
        catch (Throwable throwable) {
            return null;
        }
    }

    private int randomLevel(int n, int n2) {
        return ThreadLocalRandom.current().nextInt(n, n2 + 1);
    }

    private static String nameOf(Enchantment enchantment) {
        if (enchantment == null) {
            return "?";
        }
        String string = enchantment.getKey().getKey();
        String[] stringArray = string.split("_");
        StringBuilder stringBuilder = new StringBuilder();
        for (String string2 : stringArray) {
            stringBuilder.append(Character.toUpperCase(string2.charAt(0))).append(string2.substring(1)).append(' ');
        }
        return stringBuilder.toString().trim();
    }

    private /* synthetic */ void lambda$buildStoneEnch$0(String string, int n, ItemMeta itemMeta) {
        PersistentDataContainer persistentDataContainer = itemMeta.getPersistentDataContainer();
        persistentDataContainer.set(this.keyEnchant, PersistentDataType.STRING, (Object)string);
        persistentDataContainer.set(this.keyLevel, PersistentDataType.INTEGER, (Object)n);
    }

    public record EnchantPool(Material material, String name, List<String> lore, boolean exotic) {
    }
}
