package com.terrabox;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.Registry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 附魔系统 —— 附魔石 (从物资箱掉落), 右键对随机附魔星石应用到手持装备上
 *
 * 玩法:
 *  - 附魔石是一种可消耗道具 (PDC 标记 terrabox_enchant=附魔名)
 *  - 玩家右键附魔石: 将该附魔应用到手持物品 (武器/工具/护甲), 消耗附魔石
 *  - 附魔石从物资箱掉落 (loot 表里用 enchant-stone: <附魔或ALL> 引用)
 *  - 高级附魔石 (EXOTIC) 应用时不需经验; 普通石需要读本
 *
 * 线程模型: 构建物品为纯对象; 右键触发在玩家区域线程, 直接操作合法。
 */
public class EnchantManager {
    public static final String KEY = "terrabox_enchant";
    private final TerraBoxPlugin plugin;
    private final NamespacedKey keyEnchant;
    private final NamespacedKey keyLevel;

    /** 附魔池 (config enchant-stones 段定义; 无则内置默认) */
    private final Map<String, EnchantPool> pools = new HashMap<>();

    public record EnchantPool(Material material, String name, List<String> lore,
                              boolean exotic) {}

    public EnchantManager(TerraBoxPlugin plugin) {
        this.plugin = plugin;
        this.keyEnchant = new NamespacedKey(plugin, "enchant");
        this.keyLevel = new NamespacedKey(plugin, "enchant_level");
    }

    public void load() {
        pools.clear();
        var sec = plugin.getConfig().getConfigurationSection("enchant-stones");
        if (sec == null || sec.getKeys(false).isEmpty()) {
            registerDefaults();
            return;
        }
        for (String key : sec.getKeys(false)) {
            var s = sec.getConfigurationSection(key);
            if (s == null) continue;
            try {
                Material mat = Material.matchMaterial(s.getString("material", "LAPIS_LAZULI"));
                String name = s.getString("name", "&b附魔石");
                List<String> lore = new ArrayList<>(s.getStringList("lore"));
                boolean exotic = s.getBoolean("exotic", false);
                pools.put(key.toLowerCase(Locale.ROOT), new EnchantPool(mat, name, lore, exotic));
            } catch (Exception ignored) {}
        }
        plugin.getLogger().info("附魔石表加载完成: " + pools.size() + " 种");
    }

    private void registerDefaults() {
        pools.put("normal", new EnchantPool(Material.LAPIS_LAZULI, "&b&l附魔石",
                List.of("&7右键对手持的武器/工具/护甲施加一个随机附魔。", "&7右键使用前请手持目标装备。"), false));
        pools.put("exotic", new EnchantPool(Material.AMETHYST_SHARD, "&d&l稀有附魔石",
                List.of("&7右键对手持物品施加一个 &d高级附魔&7 (概率双倍强度)。", "&7右键使用前请手持目标装备。"), true));
    }

    /** 构建一枚随机附魔石: 大部分普通(1-2级), 小部分高级(3-4级) (任意线程) */
    public ItemStack buildRandomStone() {
        boolean exotic = Math.random() < 0.30; // 30% 概率高级石
        if (exotic) {
            return buildStoneEnch(randomEnchant(), randomLevel(3, 4), true);
        }
        return buildStoneEnch(randomEnchant(), randomLevel(1, 2), false);
    }

    /** 构建指定附魔石 (任意线程) */
    public ItemStack buildStone(Enchantment ench, int level) {
        return buildStoneEnch(ench, level, level >= 3);
    }

    /** 构建附魔石 (底层实现) */
    private ItemStack buildStoneEnch(Enchantment ench, int level, boolean exotic) {
        EnchantPool pool = pools.get(exotic ? "exotic" : "normal");
        if (pool == null) pool = pools.get("normal");
        if (pool == null) pool = new EnchantPool(Material.LAPIS_LAZULI, "&b&l附魔石", List.of(), false);
        ItemStack it = new ItemStack(pool.material(), 1);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            Component nameComp = LegacyComponentSerializer.legacyAmpersand().deserialize(pool.name());
            meta.displayName(nameComp);
            List<Component> lore = new ArrayList<>();
            for (String line : pool.lore()) lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize(line));
            String ename = nameOf(ench);
            lore.add(LegacyComponentSerializer.legacyAmpersand()
                    .deserialize("&7附魔: &f" + ename + " &7(等级 &e" + level + "&7)"));
            meta.lore(lore);
            it.setItemMeta(meta);
        }
        String finalEnch = ench == null ? "SHARPNESS" : ench.getKey().getKey().toUpperCase(Locale.ROOT);
        int finalLevel = level;
        it.editMeta(m -> {
            var pdc = m.getPersistentDataContainer();
            pdc.set(keyEnchant, PersistentDataType.STRING, finalEnch);
            pdc.set(keyLevel, PersistentDataType.INTEGER, finalLevel);
        });
        return it;
    }

    /** 判断是否为附魔石, 返回附魔名 (非附魔石返回 null) */
    public String enchantOf(ItemStack it) {
        if (it == null || it.getType() == Material.AIR) return null;
        try {
            return it.getItemMeta().getPersistentDataContainer()
                    .get(keyEnchant, PersistentDataType.STRING);
        } catch (Throwable t) { return null; }
    }

    public boolean isEnchantStone(ItemStack it) { return enchantOf(it) != null; }

    public int levelOf(ItemStack it) {
        if (it == null) return 1;
        try {
            Integer v = it.getItemMeta().getPersistentDataContainer()
                    .get(keyLevel, PersistentDataType.INTEGER);
            return v == null ? 1 : v;
        } catch (Throwable t) { return 1; }
    }

    /**
     * 应用附魔: 右键附魔石时触发。将附魔应用到玩家手持物品。
     * @return true=成功消耗附魔石, false=失败不消耗
     * 玩家区域线程调用 (PlayerInteractEvent 进入)
     */
    public boolean apply(Player player, ItemStack stone) {
        String enchName = enchantOf(stone);
        if (enchName == null) return false;
        // 从 Registry 查找附魔 (旧 API getByName 可能已移除, 统一走 Registry)
        Enchantment ench = null;
        try { ench = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(enchName.toLowerCase(Locale.ROOT))); }
        catch (Throwable t) { ench = null; }
        if (ench == null) {
            player.sendMessage("§c未知附魔: " + enchName);
            return false;
        }
        int level = levelOf(stone);
        // 目标 = 不持附魔石的那只手: 石在副手→附魔主手装备; 石在主手→附魔副手装备
        boolean stoneInOff = isOffHandStone(player, stone);
        ItemStack target = stoneInOff
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();
        // 不能对附魔石本身应用
        if (isEnchantStone(target)) {
            player.sendMessage("§c请在另一只手持有要附魔的装备 (不要两手都拿附魔石)。");
            return false;
        }
        if (target == null || target.getType().isAir()
                || !target.getType().name().contains("_SWORD")
                && !target.getType().name().contains("_PICKAXE")
                && !target.getType().name().contains("_AXE")
                && !target.getType().name().contains("_SHOVEL")
                && !target.getType().name().contains("_HOE")
                && !target.getType().name().contains("_BOW")
                && !target.getType().name().contains("CROSSBOW")
                && !target.getType().name().contains("_HELMET")
                && !target.getType().name().contains("_CHESTPLATE")
                && !target.getType().name().contains("_LEGGINGS")
                && !target.getType().name().contains("_BOOTS")
                && !target.getType().name().contains("TRIDENT")) {
            player.sendMessage("§c请手持一件可附魔的装备 (武器/工具/盔甲)。");
            return false;
        }
        // 应用附魔
        try {
            target.addUnsafeEnchantment(ench, level);
            // 写回目标所在的手 (石在副手→目标在主手; 石在主手→目标在副手)
            if (stoneInOff) player.getInventory().setItemInMainHand(target);
            else player.getInventory().setItemInOffHand(target);
            player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
            player.sendMessage("§a已为手持装备附魔: §f" + nameOf(ench)
                    + " §7(等级 §e" + level + "§7)");
            return true; // 消耗附魔石
        } catch (Throwable t) {
            player.sendMessage("§c附魔失败: " + t.getMessage());
            return false;
        }
    }

    /** 判断附魔石当前是否在玩家副手 (用于确定目标手) */
    private boolean isOffHandStone(Player player, ItemStack stone) {
        try {
            ItemStack off = player.getInventory().getItemInOffHand();
            if (off != null && !off.getType().isAir() && isEnchantStone(off)) {
                // 副手是附魔石: 主手也是附魔石时优先按"石在主手"处理, 否则副手为石
                ItemStack main = player.getInventory().getItemInMainHand();
                return !(main != null && !main.getType().isAir() && isEnchantStone(main));
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * 判断附魔石是否在主手 (用于背包点击模式确定目标手)
     * @return true=石在主手, false=石在副手或不在手上
     */
    public boolean isMainHandStone(Player player, ItemStack stone) {
        try {
            ItemStack main = player.getInventory().getItemInMainHand();
            return main != null && !main.getType().isAir() && isEnchantStone(main);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 应用附魔到指定装备 (背包点击模式专用)
     * @param player 玩家
     * @param stone 附魔石 (从光标扣减)
     * @param targetEquip 目标装备
     * @return true=成功
     */
    public boolean applyToTarget(Player player, ItemStack stone, ItemStack targetEquip) {
        String enchName = enchantOf(stone);
        if (enchName == null) return false;
        Enchantment ench = null;
        try { ench = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(enchName.toLowerCase(Locale.ROOT))); }
        catch (Throwable t) { ench = null; }
        if (ench == null) {
            player.sendMessage("§c未知附魔: " + enchName);
            return false;
        }
        int level = levelOf(stone);
        if (targetEquip == null || targetEquip.getType().isAir()) {
            player.sendMessage("§c目标装备为空。");
            return false;
        }
        if (isEnchantStone(targetEquip)) {
            player.sendMessage("§c目标不是可附魔装备。");
            return false;
        }
        try {
            targetEquip.addUnsafeEnchantment(ench, level);
            player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
            player.sendMessage("§a已为装备附魔: §f" + nameOf(ench)
                    + " §7(等级 §e" + level + "§7)");
            return true;
        } catch (Throwable t) {
            player.sendMessage("§c附魔失败: " + t.getMessage());
            return false;
        }
    }
    public Enchantment randomEnchant() {
        Enchantment[] es;
        try {
            java.util.List<Enchantment> list = new ArrayList<>();
            Registry.ENCHANTMENT.forEach(list::add);
            es = list.toArray(new Enchantment[0]);
        } catch (Throwable t) {
            es = new Enchantment[]{fallbackEnch()};
        }
        if (es.length == 0) return fallbackEnch();
        // 只取武器/工具/盔甲类可用附魔 (过滤无关)
        List<Enchantment> usable = new ArrayList<>();
        for (Enchantment e : es) {
            if (e == null) continue;
            String k = e.getKey().getKey().toLowerCase(Locale.ROOT);
            if (k.contains("sharpness") || k.contains("smite") || k.contains("bane")
                    || k.contains("protection") || k.contains("power")
                    || k.contains("unbreaking") || k.contains("efficiency")
                    || k.contains("fortune") || k.contains("fire")
                    || k.contains("looting") || k.contains("thorns")
                    || k.contains("respiration") || k.contains("feather")
                    || k.contains("mending") || k.contains("punch")
                    || k.contains("flame") || k.contains("lure")
                    || k.contains("luck") || k.contains("sweeping")
                    || k.contains("knockback") || k.contains("impaling")
                    || k.contains("infinity") || k.contains("depth")
                    || k.contains("aqua") || k.contains("silktouch")
                    || k.contains("piercing") || k.contains("quick_charge")
                    || k.contains("loyalty") || k.contains("riptide")
                    || k.contains("channeling") || k.contains("multishot")
                    || k.contains("swift_sneak") || k.contains("soul_speed")) {
                usable.add(e);
            }
        }
        if (usable.isEmpty()) return fallbackEnch();
        return usable.get(ThreadLocalRandom.current().nextInt(usable.size()));
    }

    /** 兜底附魔: Registry 查找, 失败返回 null (上层有判空) */
    private Enchantment fallbackEnch() {
        try { return Registry.ENCHANTMENT.get(NamespacedKey.minecraft("sharpness")); }
        catch (Throwable t) { return null; }
    }

    private int randomLevel(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private static String nameOf(Enchantment ench) {
        if (ench == null) return "?";
        String k = ench.getKey().getKey();
        String[] parts = k.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        return sb.toString().trim();
    }
}
