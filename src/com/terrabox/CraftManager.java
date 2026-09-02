package com.terrabox;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 神器合成系统 —— 碎片/材料 + 工作台配方
 *
 * 玩法:
 *  - 碎片/材料从物资箱掉落 (loot 表 items 里用 craft: <key> 引用)
 *  - 玩家收集碎片+材料, 打开工作台配方 GUI (/box craft), 放入材料点击合成
 *  - 达到配方要求, 即合成对应神器 (ArtifactManager.buildItem 构建)
 *
 * 碎片/材料标记: PDC terrabox_craft = key。碎片可归属某件神器 (terrabox_craft_artifact=artifactKey),
 *   通用材料不归属 (为 null)。每个配方 = 若干碎片/材料条目 {fragment:key, count:n}。
 *
 * 线程模型: 构建物品为纯对象; 合成在玩家区域线程 (InventoryClickEvent) 校验并收走材料。
 */
public class CraftManager {
    private final TerraBoxPlugin plugin;
    private final NamespacedKey keyCraft;
    private final NamespacedKey keyArtifact;
    private final Map<String, CraftDef> defs = new HashMap<>();
    private final List<Recipe> recipes = new ArrayList<>();

    /** 碎片/材料定义 */
    public record CraftDef(Material material, String key, String name, List<String> lore, String artifact) {}

    /** 配方: 产出神器 artifact, 需要 ingredients (fragment->count) */
    public record Recipe(String artifact, LinkedHashMap<String, Integer> ingredients) {}

    public CraftManager(TerraBoxPlugin plugin) {
        this.plugin = plugin;
        this.keyCraft = new NamespacedKey(plugin, "craft");
        this.keyArtifact = new NamespacedKey(plugin, "craft_artifact");
    }

    public void load() {
        defs.clear();
        recipes.clear();
        var dsec = plugin.getConfig().getConfigurationSection("crafting.fragments");
        if (dsec != null) {
            for (String key : dsec.getKeys(false)) {
                var s = dsec.getConfigurationSection(key);
                if (s == null) continue;
                try {
                    Material mat = Material.matchMaterial(s.getString("material", "DIAMOND").toUpperCase(Locale.ROOT));
                    if (mat == null || !mat.isItem()) {
                        plugin.getLogger().warning("合成材料 [" + key + "] 材质无效: " + s.getString("material"));
                        continue;
                    }
                    String name = s.getString("name", "&e&l" + key);
                    List<String> lore = new ArrayList<>(s.getStringList("lore"));
                    String artifact = s.getString("artifact", null);
                    defs.put(key.toLowerCase(Locale.ROOT), new CraftDef(mat, key.toLowerCase(Locale.ROOT), name, lore, artifact));
                } catch (Exception e) {
                    plugin.getLogger().warning("合成材料 [" + key + "] 解析失败: " + "错误";
                }
            }
        }
        // 配置缺失时内置默认碎片/材料 (兜底)
        if (defs.isEmpty()) registerDefaultDefs();

        // 配方
        var rsec = plugin.getConfig().getConfigurationSection("crafting.recipes");
        if (rsec != null) {
            for (String key : rsec.getKeys(false)) {
                var s = rsec.getConfigurationSection(key);
                if (s == null) continue;
                try {
                    String artifact = s.getString("artifact", key);
                    LinkedHashMap<String, Integer> ing = new LinkedHashMap<>();
                    for (String ingKey : s.getKeys(false)) {
                        if (ingKey.equals("artifact")) continue;
                        int count = Math.max(1, s.getInt(ingKey, 1));
                        ing.put(ingKey.toLowerCase(Locale.ROOT), count);
                    }
                    if (!ing.isEmpty()) recipes.add(new Recipe(artifact, ing));
                } catch (Exception e) {
                    plugin.getLogger().warning("配方 [" + key + "] 解析失败: " + "错误";
                }
            }
        }
        // 配置缺失或无配方时内置默认配方 (兜底)
        if (recipes.isEmpty()) registerDefaultRecipes();
        plugin.getLogger().info("合成系统加载完成: " + defs.size() + " 种碎片/材料, " + recipes.size() + " 条配方");
    }

    /** 内置默认碎片/材料 (config 缺失兜底) */
    private void registerDefaultDefs() {
        // 无 crafting 配置时, 不注册任何碎片/材料 (合成系统禁用)
        // 碎片/材料仅在 config.yml 中有 crafting.fragments 段时才生效
    }

    /** 内置默认配方 (config 缺失兜底) */
    private void registerDefaultRecipes() {
        // 无 crafting 配置时, 不注册任何配方 (合成系统禁用)
    }

    private static LinkedHashMap<String, Integer> linked(Object... kv) {
        LinkedHashMap<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(String.valueOf(kv[i]), Integer.parseInt(String.valueOf(kv[i + 1])));
        return m;
    }

    /** 按 key 构建碎片/材料 (任意线程) */
    public ItemStack buildItem(String key) {
        CraftDef def = defs.get(key.toLowerCase(Locale.ROOT));
        if (def == null) return null;
        ItemStack it = new ItemStack(def.material(), 1);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize(def.name()));
            List<Component> lore = new ArrayList<>();
            lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize("&8&m            &r&b&l 合成材料 &8&m            "));
            for (String line : def.lore())
                lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize(line));
            lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize("&7&o可用于工作台合成神器"));
            meta.lore(lore);
            it.setItemMeta(meta);
        }
        it.editMeta(m -> {
            var pdc = m.getPersistentDataContainer();
            pdc.set(keyCraft, PersistentDataType.STRING, def.key());
            if (def.artifact() != null)
                pdc.set(keyArtifact, PersistentDataType.STRING, def.artifact());
        });
        return it;
    }

    /** 判断是否为碎片/材料, 返回 key (非则 null) */
    public String craftKey(ItemStack it) {
        if (it == null || it.getType() == Material.AIR) return null;
        try {
            return it.getItemMeta().getPersistentDataContainer()
                    .get(keyCraft, PersistentDataType.STRING);
        } catch (Throwable t) { return null; }
    }

    public boolean isCraftItem(ItemStack it) { return craftKey(it) != null; }

    /** 碎片归属的神器 key (通用材料返回 null) */
    public String fragmentArtifact(ItemStack it) {
        if (it == null) return null;
        try {
            return it.getItemMeta().getPersistentDataContainer()
                    .get(keyArtifact, PersistentDataType.STRING);
        } catch (Throwable t) { return null; }
    }

    /** 所有碎片/材料 key (管理/展示用) */
    public List<String> keys() { return new ArrayList<>(defs.keySet()); }

    /** 所有碎片/材料定义 (管理/展示用) */
    public List<CraftDef> defs() { return new ArrayList<>(defs.values()); }

    /** 碎片/材料显示名 (未找到返回 key), 返回 § 码文本 */
    public String nameOf(String key) {
        CraftDef d = defs.get(key.toLowerCase(Locale.ROOT));
        if (d == null) return key;
        return LegacyComponentSerializer.legacySection()
                .serialize(LegacyComponentSerializer.legacyAmpersand().deserialize(d.name()));
    }

    /** 所有配方 (管理/展示用) */
    public List<Recipe> recipes() { return new ArrayList<>(recipes); }

    public Recipe recipeFor(String artifactKey) {
        for (Recipe r : recipes)
            if (r.artifact().equalsIgnoreCase(artifactKey)) return r;
        return null;
    }

    /**
     * 校验玩家背包中是否有足够材料满足配方 (不含 workbench 材料槽, 直接查询背包)
     * 供配方 GUI 展示"当前拥有"数量用。
     */
    public Map<String, Integer> ownedCounts(ItemStack[] bag, Recipe recipe) {
        Map<String, Integer> have = new HashMap<>();
        for (ItemStack it : bag) {
            if (it == null) continue;
            String k = craftKey(it);
            if (k == null) continue;
            have.merge(k, it.getAmount(), Integer::sum);
        }
        return have;
    }
}
