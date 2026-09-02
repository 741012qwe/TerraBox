package com.terrabox;

import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 战利品表: 从 config.yml loot 段加载五档物品配置, 随机填充物资箱
 * 线程说明: 只做配置读取与 ItemStack 构建(纯对象), 可在 Region 线程直接调用;
 *          load() 在启用阶段调用一次, 每档列表构建后只读。
 */
public class LootManager {
    private final TerraBoxPlugin plugin;
    private final Map<Rarity, List<LootItem>> tables = new HashMap<>();
    private final Map<Rarity, MoneyReward> moneyRewards = new HashMap<>();

    public LootManager(TerraBoxPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        tables.clear();
        moneyRewards.clear();
        for (Rarity r : Rarity.values()) {
            List<LootItem> list = new ArrayList<>();
            // items 是 YAML 列表(数组), 用 getMapList 读取, 不能用 getConfigurationSection
            List<?> itemList = plugin.getConfig().getMapList("loot." + r.key() + ".items");
            int idx = 0;
            for (Object o : itemList) {
                idx++;
                if (!(o instanceof Map<?, ?> raw)) continue;
                try {
                    Object mObj = raw.get("material");
                    if (mObj == null) continue;
                    Material mat = Material.matchMaterial(String.valueOf(mObj));
                    if (mat == null || !mat.isItem()) {
                        plugin.getLogger().warning("战利品配置无效: " + r.key() + "#" + idx
                                + " material=" + mObj);
                        continue;
                    }
                    int min = intOf(raw.get("min"), 1);
                    int max = Math.max(min, intOf(raw.get("max"), min));
                    double chance = doubleOf(raw.get("chance"), 100.0);
                    String name = stringOf(raw.get("name"), null);
                    String lore = stringOf(raw.get("lore"), null);
                    String special = stringOf(raw.get("special"), null);
                    String artifact = stringOf(raw.get("artifact"), null);
                    String enchantStone = stringOf(raw.get("enchant-stone"), null);
                    String craft = stringOf(raw.get("craft"), null);
                    LootItem li = new LootItem(mat, min, max, chance,
                            parseEnchants(raw.get("enchants")), name, lore, special, artifact, enchantStone, craft);
                    list.add(li);
                } catch (Exception e) {
                    plugin.getLogger().warning("战利品解析失败 " + r.key() + "#" + idx + ": " + "错误";
                }
            }
            tables.put(r, List.copyOf(list));
            // money 是对象/内联map, 单独读取
            Object moneyObj = plugin.getConfig().get("loot." + r.key() + ".money");
            if (moneyObj instanceof Map<?, ?> mm) {
                moneyRewards.put(r, new MoneyReward(
                        longOf(mm.get("min"), 0), longOf(mm.get("max"), 0),
                        doubleOf(mm.get("chance"), 0)));
            }
        }
        int total = tables.values().stream().mapToInt(List::size).sum();
        plugin.getLogger().info("战利品表加载完成: 5 档共 " + total + " 种条目");
    }

    private static int intOf(Object o, int def) {
        return (o instanceof Number n) ? n.intValue() : def;
    }
    private static long longOf(Object o, long def) {
        return (o instanceof Number n) ? n.longValue() : def;
    }
    private static double doubleOf(Object o, double def) {
        return (o instanceof Number n) ? n.doubleValue()
                : (o instanceof String s ? Double.parseDouble(s) : def);
    }
    private static String stringOf(Object o, String def) {
        return o == null ? def : String.valueOf(o);
    }

    /** 解析附魔: 支持内联map {POWER:1, SHARPNESS:2-3} 或字符串 "POWER:1;SHARPNESS:2-3" */
    private Map<Enchantment, int[]> parseEnchants(Object raw) {
        Map<Enchantment, int[]> map = new java.util.HashMap<>();
        if (raw instanceof Map<?, ?> em) {
            for (Map.Entry<?, ?> e : em.entrySet()) {
                addEnchant(map, String.valueOf(e.getKey()), String.valueOf(e.getValue()));
            }
        } else if (raw instanceof String s && !s.isBlank()) {
            for (String part : s.split("[;,]")) {
                String[] seg = part.trim().split(":");
                if (seg.length == 2) addEnchant(map, seg[0].trim(), seg[1].trim());
            }
        }
        return map;
    }

    private void addEnchant(Map<Enchantment, int[]> map, String name, String lv) {
        try {
            Enchantment ench = findEnchant(name);
            if (ench == null) {
                plugin.getLogger().warning("未知附魔: " + name + " (已跳过)");
                return;
            }
            if (lv.contains("-")) {
                String[] ab = lv.split("-");
                map.put(ench, new int[]{Integer.parseInt(ab[0]), Integer.parseInt(ab[1])});
            } else {
                int n = Integer.parseInt(lv);
                map.put(ench, new int[]{n, n});
            }
        } catch (Exception ignored) {}
    }

    private Enchantment findEnchant(String name) {
        try {
            return Registry.ENCHANTMENT.get(NamespacedKey.minecraft(name.toLowerCase(java.util.Locale.ROOT)));
        } catch (Throwable t) {
            return null;
        }
    }

    /** 把一档战利品随机填进容器 (必须在物资箱所在区域线程调用), 返回填充的物品堆数 */
    public int fillInventory(Inventory inv, Rarity rarity) {
        inv.clear();
        List<LootItem> table = tables.getOrDefault(rarity, List.of());
        if (table.isEmpty()) return 0;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        // 平衡性: 每箱目标堆数区间 (按稀有度, 可用 loot.<key>.min-stacks/max-stacks 覆盖)
        int minStacks = plugin.getConfig().getInt("loot." + rarity.key() + ".min-stacks", rarity.minStacks());
        int maxStacks = plugin.getConfig().getInt("loot." + rarity.key() + ".max-stacks", rarity.maxStacks());
        maxStacks = Math.max(maxStacks, minStacks);
        // 打乱表格, 保证填充多样性 (不同箱子里出现物品不同)
        List<LootItem> shuffled = new ArrayList<>(table);
        java.util.Collections.shuffle(shuffled, rnd);
        int placed = 0;
        for (LootItem li : shuffled) {
            if (placed >= maxStacks && maxStacks > 0) break; // 达到上限停止, 防爆
            if (rnd.nextDouble(100) >= li.chance) continue;
            int amount = li.min >= li.max ? li.min : rnd.nextInt(li.min, li.max + 1);
            if (amount <= 0) continue;
            // 特殊道具: 每格仅 1 个 (避免按堆叠重复生成) — 碎片/材料可堆叠, 允许一次开多个
            if (li.special != null || li.artifact != null || li.enchantStone != null) amount = 1;
            int perStack = Math.min(amount, li.material.getMaxStackSize());
            int remaining = amount;
            while (remaining > 0 && perStack > 0) {
                int n = Math.min(perStack, remaining);
                ItemStack stack = new ItemStack(li.material, n);
                if (!li.enchants.isEmpty()) {
                    if (li.material == Material.ENCHANTED_BOOK) {
                        // 附魔书的词条必须存到 EnchantmentStorageMeta (storedEnchants), addEnchant 不生效
                        org.bukkit.inventory.meta.EnchantmentStorageMeta sm =
                                (org.bukkit.inventory.meta.EnchantmentStorageMeta) stack.getItemMeta();
                        for (Map.Entry<Enchantment, int[]> e : li.enchants.entrySet()) {
                            int lo = e.getValue()[0], hi = Math.max(e.getValue()[0], e.getValue()[1]);
                            int lv = lo >= hi ? lo : rnd.nextInt(lo, hi + 1);
                            if (lv > 0) {
                                try { sm.addStoredEnchant(e.getKey(), lv, true); } catch (Exception ignored) {}
                            }
                        }
                        stack.setItemMeta(sm);
                    } else {
                        for (Map.Entry<Enchantment, int[]> e : li.enchants.entrySet()) {
                            int lo = e.getValue()[0], hi = Math.max(e.getValue()[0], e.getValue()[1]);
                            int lv = lo >= hi ? lo : rnd.nextInt(lo, hi + 1);
                            if (lv > 0) {
                                try { stack.addUnsafeEnchantment(e.getKey(), lv); } catch (Exception ignored) {}
                            }
                        }
                    }
                }
                // 自定义道具名/描述 (品质道具)
                if (li.name != null || li.lore != null) {
                    org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
                    if (meta != null) {
                        if (li.name != null) meta.setDisplayName(amp(li.name));
                        if (li.lore != null) {
                            meta.setLore(java.util.Arrays.stream(li.lore.split("\\n"))
                                    .map(LootManager::amp).collect(java.util.stream.Collectors.toList()));
                        }
                        stack.setItemMeta(meta);
                    }
                }
                // 特殊道具: 以配置的特殊道具 key 构建 (覆盖普通材质为特殊道具)
                if (li.special != null && plugin.specialItems() != null) {
                    ItemStack sp = plugin.specialItems().buildItem(li.special);
                    if (sp != null) stack = sp;
                }
                // 神器: 以配置的神器 key 构建 (覆盖为神器)
                if (li.artifact != null && plugin.artifacts() != null) {
                    ItemStack ar = plugin.artifacts().buildItem(li.artifact);
                    if (ar != null) stack = ar;
                }
                // 附魔石: 构建一枚随机附魔石
                if (li.enchantStone != null && plugin.enchants() != null) {
                    ItemStack es = plugin.enchants().buildRandomStone();
                    if (es != null) stack = es;
                }
                // 碎片/材料: 合成材料 (craft: <key> 引用), 保持 n 数量 (可堆叠)
                if (li.craft != null && plugin.crafts() != null) {
                    ItemStack cm = plugin.crafts().buildItem(li.craft);
                    if (cm != null) {
                        cm.setAmount(n);
                        stack = cm;
                    }
                }
                inv.addItem(stack);
                placed++;
                remaining -= n;
            }
        }
        // 保底: 若未达到最小堆数, 循环采样补充 (直到 minStacks 或表格遍历完)
        int guard = 0;
        while (placed < minStacks && placed < 54 && guard < 40) {
            LootItem li = shuffled.get(rnd.nextInt(shuffled.size()));
            if (li.special != null || li.artifact != null || li.enchantStone != null || li.craft != null) { guard++; continue; } // 保底不补特殊道具
            int amount = Math.max(1, li.min >= li.max ? li.min : rnd.nextInt(li.min, li.max + 1));
            ItemStack stack = new ItemStack(li.material, Math.min(amount, li.material.getMaxStackSize()));
            inv.addItem(stack);
            placed++;
            guard++;
        }
        if (placed == 0) {
            LootItem li = table.get(0);
            ItemStack stack = new ItemStack(li.material, Math.max(1, li.min));
            inv.addItem(stack);
            placed = 1;
        }
        return placed;
    }

    /** 开箱货币奖励, 无奖励返回 0 (任意线程) */
    public long rollMoney(Rarity rarity) {
        MoneyReward mr = moneyRewards.get(rarity);
        if (mr == null || !plugin.getConfig().getBoolean("economy.loot-money", true)) return 0;
        if (ThreadLocalRandom.current().nextDouble(100) >= mr.chance) return 0;
        if (mr.max <= mr.min) return Math.max(0, mr.min);
        return ThreadLocalRandom.current().nextLong(mr.min, mr.max + 1);
    }

    public int tableSize(Rarity r) {
        return tables.getOrDefault(r, List.of()).size();
    }

    private record MoneyReward(long min, long max, double chance) {}

    private record LootItem(Material material, int min, int max, double chance,
                            Map<Enchantment, int[]> enchants, String name, String lore,
                            String special, String artifact, String enchantStone, String craft) {}

    /** & 码 → § 码 (使用Adventure序列化器) */
    private static String amp(String s) {
        if (s == null) return "";
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacyAmpersand().serialize(
                    net.kyori.adventure.text.Component.text(s));
    }
}
