package com.terrabox;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
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
 * 神器系统 —— 吃鸡模式中的终极装备 (从传说/绝世物资箱低概率掉落)
 *
 * 每件神器: 独特名称/描述, 超强附魔, 属性加成 (攻击伤害/护甲/攻速/移速),
 *   并通过 PDC 标记 terrabox_artifact=key, 供 ArtifactListener 识别额外主动/被动效果。
 *
 * 效果类型 (artifact.<key>.effect):
 *   BLEED       攻击命中概率让目标流血 (持续伤害)
 *   LIFESTEAL   攻击命中回复生命
 *   THORNS      受击反弹伤害
 *   SPEED       被动移速加成
 *   STRENGTH    被动攻击力加成
 *   VAMPIRIC    吸血 (同 LIFESTEAL 更强)
 *   FROST       攻击概率冻结 (减速+发光)
 *
 * 线程模型: 构建物品为纯对象 (任意线程); 效果触发由 ArtifactListener 在实体区域线程处理。
 */
public class ArtifactManager {
    public static final String TITLE = "神器";
    private final TerraBoxPlugin plugin;
    private final NamespacedKey keyArtifact;
    private final NamespacedKey keyEffect;
    private final Map<String, ArtifactDef> defs = new HashMap<>();

    /** 神器定义 */
    public record ArtifactDef(Material material, String key, String name, List<String> lore,
                              String effect, double procChance, double magnitude,
                              Map<Enchantment, Integer> enchants) {}

    public ArtifactManager(TerraBoxPlugin plugin) {
        this.plugin = plugin;
        this.keyArtifact = new NamespacedKey(plugin, "artifact");
        this.keyEffect = new NamespacedKey(plugin, "artifact_effect");
    }

    public void load() {
        defs.clear();
        var sec = plugin.getConfig().getConfigurationSection("artifacts");
        if (sec == null || sec.getKeys(false).isEmpty()) {
            plugin.getLogger().info("神器表: 未检测到配置段, 已加载内置默认神器");
            registerDefaults();
            return;
        }
        for (String key : sec.getKeys(false)) {
            var s = sec.getConfigurationSection(key);
            if (s == null) continue;
            try {
                Material mat = Material.matchMaterial(s.getString("material", "DIAMOND_SWORD").toUpperCase(Locale.ROOT));
                if (mat == null || !mat.isItem()) {
                    plugin.getLogger().warning("神器 [" + key + "] 材质无效: " + s.getString("material"));
                    continue;
                }
                String effect = s.getString("effect", "").toUpperCase(Locale.ROOT);
                String name = s.getString("name", "&6&l" + key);
                List<String> lore = new ArrayList<>(s.getStringList("lore"));
                double procChance = s.getDouble("proc-chance", 0.25);
                double magnitude = s.getDouble("magnitude", 1.0);
                // 附魔: 支持内联 map 或字符串 "POWER:5;UNBREAKING:3"
                Map<Enchantment, Integer> ench = parseEnchants(s.get("enchants"));
                defs.put(key.toLowerCase(Locale.ROOT),
                        new ArtifactDef(mat, key.toLowerCase(Locale.ROOT), name, lore,
                                effect, procChance, magnitude, ench));
            } catch (Exception e) {
                plugin.getLogger().warning("神器 [" + key + "] 解析失败: " + e.getMessage());
            }
        }
        plugin.getLogger().info("神器表加载完成: " + defs.size() + " 件");
    }

    public int size() { return defs.size(); }

    /** 内置默认神器 (config 缺失兜底) */
    private void registerDefaults() {
        defs.put("celestial_blade", new ArtifactDef(Material.NETHERITE_SWORD, "celestial_blade",
                "&6&l苍穹之刃", List.of("&7传说中斩落星辰的神剑。", "&7攻击吸血, 攻击命中概率让目标流血。"),
                "LIFESTEAL", 0.30, 1.0, Map.of(e("sharpeness"), 5, e("unbreaking"), 4, e("fire_aspect"), 2)));
        defs.put("aegis_axe", new ArtifactDef(Material.NETHERITE_AXE, "aegis_axe",
                "&c&l神盾重斧", List.of("&7一斧可撼动山岳。", "&7高额攻击, 命中概率击退冻结。"),
                "FROST", 0.35, 1.2, Map.of(e("sharpness"), 5, e("unbreaking"), 4, e("efficiency"), 5)));
        defs.put("draco_bow", new ArtifactDef(Material.BOW, "draco_bow",
                "&e&l屠龙圣弓", List.of("&7箭无虚发, 箭箭索命。", "&7射出的箭附带火焰与高额伤害。"),
                "STRING", 0.40, 1.0, Map.of(e("power"), 5, e("flame"), 2, e("infinity"), 1, e("punch"), 2)));
        defs.put("titan_chest", new ArtifactDef(Material.NETHERITE_CHESTPLATE, "titan_chest",
                "&d&l泰坦铠甲", List.of("&7坚不可摧的巨人护甲。", "&7受击反弹伤害, 大幅减伤。"),
                "THORNS", 0.25, 2.0, Map.of(e("protection"), 5, e("unbreaking"), 4, e("thorns"), 3)));
        defs.put("wind_boots", new ArtifactDef(Material.NETHERITE_BOOTS, "wind_boots",
                "&b&l疾风之靴", List.of("&7如风般迅捷。", "&7大幅提升移动速度。"),
                "SPEED", 0.30, 0.25, Map.of(e("protection"), 4, e("feather_falling"), 4, e("unbreaking"), 4)));
        defs.put("vampire_fang", new ArtifactDef(Material.NETHERITE_AXE, "vampire_fang",
                "&d&l吸血獠牙", List.of("&7饮血而猎, 愈战愈勇。", "&7每次攻击吸取大量生命。"),
                "VAMPIRIC", 0.35, 2.0, Map.of(e("sharpness"), 5, e("unbreaking"), 4, e("looting"), 3)));
    }

    /** 从 Registry 查附魔 (兜底 SHARPNESS), 避免依赖已移除的静态字段 */
    private static Enchantment e(String name) {
        try {
            Enchantment en = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(name));
            if (en != null) return en;
        } catch (Throwable ignored) {}
        // 兜底: 尝试常见名
        for (String alt : new String[]{"sharpness", "protection"}) {
            try {
                Enchantment en = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(alt));
                if (en != null) return en;
            } catch (Throwable ignored2) {}
        }
        return null;
    }

    /** 按 key 构建神器 ItemStack (任意线程, 纯对象) */
    public ItemStack buildItem(String key) {
        ArtifactDef def = defs.get(key.toLowerCase(Locale.ROOT));
        if (def == null) return null;
        ItemStack it = new ItemStack(def.material, 1);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize(def.name));
            List<Component> lore = new ArrayList<>();
            lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize("&8&m               &r&6&l 神 器 &8&m               "));
            for (String line : def.lore)
                lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize(line));
            lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize("&7&o—— 装备后获得神器之力 ——"));
            meta.lore(lore);
            it.setItemMeta(meta);
        }
        // 应用神器附魔
        for (Map.Entry<Enchantment, Integer> e : def.enchants.entrySet()) {
            try { it.addUnsafeEnchantment(e.getKey(), Math.min(127, e.getValue())); } catch (Throwable ignored) {}
        }
        // PDC 标记
        final String k = def.key;
        it.editMeta(m -> {
            var pdc = m.getPersistentDataContainer();
            pdc.set(keyArtifact, PersistentDataType.STRING, k);
            if (def.effect != null && !def.effect.isEmpty())
                pdc.set(keyEffect, PersistentDataType.STRING, def.effect);
        });
        return it;
    }

    /** 判断是否为神器, 返回 key (非神器返回 null) */
    public String artifactKey(ItemStack it) {
        if (it == null || it.getType() == Material.AIR) return null;
        try {
            return it.getItemMeta().getPersistentDataContainer()
                    .get(keyArtifact, PersistentDataType.STRING);
        } catch (Throwable t) { return null; }
    }

    public boolean isArtifact(ItemStack it) { return artifactKey(it) != null; }

    /** 随机取一件神器 key */
    public String randomKey() {
        if (defs.isEmpty()) return null;
        List<ArtifactDef> list = new ArrayList<>(defs.values());
        return list.get(ThreadLocalRandom.current().nextInt(list.size())).key;
    }

    /** 所有神器 key (管理/展示用) */
    public List<String> keys() {
        return new ArrayList<>(defs.keySet());
    }

    /** 神器显示名 (未找到返回 key), 返回 § 码文本 */
    public String nameOf(String key) {
        ArtifactDef d = defs.get(key.toLowerCase(Locale.ROOT));
        if (d == null) return key;
        return LegacyComponentSerializer.legacySection()
                .serialize(LegacyComponentSerializer.legacyAmpersand().deserialize(d.name));
    }

    /** 读取神器效果 key (任意线程) */
    public String effectOf(ItemStack it) {
        if (it == null) return null;
        try {
            return it.getItemMeta().getPersistentDataContainer()
                    .get(keyEffect, PersistentDataType.STRING);
        } catch (Throwable t) { return null; }
    }

    /** 读取神器 proc 概率 (任意线程) */
    public double procChanceOf(ItemStack it) {
        String k = artifactKey(it);
        if (k == null) return 0;
        ArtifactDef d = defs.get(k);
        return d == null ? 0 : d.procChance;
    }

    public double magnitudeOf(ItemStack it) {
        String k = artifactKey(it);
        if (k == null) return 0;
        ArtifactDef d = defs.get(k);
        return d == null ? 0 : d.magnitude;
    }

    private Map<Enchantment, Integer> parseEnchants(Object raw) {
        Map<Enchantment, Integer> map = new HashMap<>();
        if (raw instanceof Map<?, ?> em) {
            for (Map.Entry<?, ?> e : em.entrySet()) {
                addEnch(map, String.valueOf(e.getKey()), String.valueOf(e.getValue()));
            }
        } else if (raw instanceof String s && !s.isBlank()) {
            for (String part : s.split("[;,]")) {
                String[] seg = part.trim().split(":");
                if (seg.length == 2) addEnch(map, seg[0].trim(), seg[1].trim());
            }
        }
        return map;
    }

    private void addEnch(Map<Enchantment, Integer> map, String name, String lv) {
        try {
            Enchantment ench = Registry.ENCHANTMENT.get(
                    org.bukkit.NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT)));
            if (ench != null) map.put(ench, Integer.parseInt(lv));
        } catch (Exception ignored) {}
    }
}
