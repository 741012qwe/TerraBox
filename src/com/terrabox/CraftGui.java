package com.terrabox;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作台配方 GUI (54格): 收集碎片/材料 → 合成神器
 *
 * 布局:
 *   0..44 为可交互区, 45..53 为按钮区
 *   材料放置槽: 9 格 (10,11,12, 19,20,21, 28,29,30), 玩家放入碎片/材料
 *   24: 当前配方产出神器预览
 *   46/47: 上/下一个配方 (翻页)
 *   49: 合成按钮  50: 关闭
 *
 * 线程模型: open 由玩家区域线程调用; 点击结算 (收材料/发神器) 也在玩家区域线程 (InventoryClickEvent)。
 */
public class CraftGui {
    public static final String TITLE = "§8[§6物资大陆§8] §b神器工作台";
    // 材料放置槽 (0..44)
    public static final int[] MAT_SLOTS = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    public static final int OUTPUT_SLOT = 24;
    public static final int PREV_SLOT = 46;
    public static final int NEXT_SLOT = 47;
    public static final int CRAFT_SLOT = 49;
    public static final int CLOSE_SLOT = 50;

    private final TerraBoxPlugin plugin;

    public CraftGui(TerraBoxPlugin plugin) {
        this.plugin = plugin;
    }

    /** 打开工作台 GUI, 默认显示第一个配方 */
    public void open(Player p) {
        GuiListener.GuiHolder holder = new GuiListener.GuiHolder(GuiListener.Type.CRAFT);
        Inventory inv = Bukkit.createInventory(holder, 54, TITLE);
        holder.inv = inv;
        holder.craftIndex = 0;
        render(p, inv, holder);
        p.openInventory(inv);
    }

    /** 渲染当前配方页 (玩家区域线程), 需要公开供 GuiListener 翻页重绘 */
    public void render(Player p, Inventory inv, GuiListener.GuiHolder holder) {
        List<CraftManager.Recipe> recipes = plugin.crafts().recipes();
        inv.clear();
        // 灰玻璃填充可交互区边缘 (0..8, 36..44) 装饰
        for (int i = 0; i < 54; i++) {
            if (isDeco(i)) inv.setItem(i, MainMenuGui.button(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
        }

        // 标题信息 (slot 4)
        inv.setItem(4, MainMenuGui.button(Material.CRAFTING_TABLE, "§b神器合成",
                List.of("§7收集碎片/材料, 放入下方格子",
                        "§7点击合成按钮, 重铸传奇神器",
                        "", "§7总共 §e" + recipes.size() + " §7条配方")));

        if (recipes.isEmpty()) {
            inv.setItem(OUTPUT_SLOT, MainMenuGui.button(Material.BARRIER, "§c暂无配方",
                    List.of("§7请在 config.yml 的 crafting.recipes 段配置")));
            // 按钮区
            inv.setItem(PREV_SLOT, MainMenuGui.button(Material.GRAY_DYE, "§7← 上一配方", List.of("")));
            inv.setItem(NEXT_SLOT, MainMenuGui.button(Material.GRAY_DYE, "§7下一配方 →", List.of("")));
            inv.setItem(CRAFT_SLOT, MainMenuGui.button(Material.BARRIER, "§c合成", List.of("§7无配方可合成")));
            inv.setItem(CLOSE_SLOT, MainMenuGui.button(Material.BARRIER, "§c关闭", List.of("§7关闭工作台")));
            return;
        }

        // 索引归一化
        int idx = holder.craftIndex;
        if (idx < 0 || idx >= recipes.size()) idx = 0;
        holder.craftIndex = idx;
        CraftManager.Recipe recipe = recipes.get(idx);

        // 产出神器预览 (slot 24)
        ItemStack out = plugin.artifacts().buildItem(recipe.artifact());
        if (out != null) {
            ItemMeta om = out.getItemMeta();
            if (om != null) {
                List<String> lore = new ArrayList<>();
                if (om.getLore() != null) lore.addAll(om.getLore());
                lore.add("");
                lore.add("§7← 合成产物: §f" + nameOfart(recipe.artifact()));
                lore.add("§e可合成 §a" + canCraft(inv, recipe) + " §e次");
                om.setLore(lore);
                out.setItemMeta(om);
            }
            inv.setItem(OUTPUT_SLOT, out);
        } else {
            inv.setItem(OUTPUT_SLOT, MainMenuGui.button(Material.BARRIER, "§c未知神器",
                    List.of("§7artifact key: §e" + recipe.artifact())));
        }

        // 需求预览 (显示材料需求 + 当前拥有) — 只读图标, 放在材料槽上方一行 (3,4,5, 21,22,23 占不了, 用 slot 4? 不行)
        // 改用材料槽右侧 3 列作需求预览 (14, 15, 23, 32) 或单独区域。这里把需求信息合并进材料槽懒加载说明。
        // 为直观, 在 slot 24 下方 slot 33 显示"材料需求合计提示"。

        // 材料槽引导: 在每个材料槽放一个提示图标 (空槽显示需求), 玩家放入后覆盖
        List<String> ingKeys = new ArrayList<>(recipe.ingredients().keySet());
        for (int i = 0; i < MAT_SLOTS.length; i++) {
            int slot = MAT_SLOTS[i];
            ItemStack cur = inv.getItem(slot);
            if (cur != null && !cur.getType().isAir()) continue; // 已有材料, 保留
            if (i < ingKeys.size()) {
                String k = ingKeys.get(i);
                CraftManager.CraftDef def = craftDef(k);
                int need = recipe.ingredients().get(k);
                int have = ownedInBag(p, k);
                Material placeholder = def != null ? def.material() : Material.BARRIER;
                inv.setItem(slot, MainMenuGui.button(placeholder,
                        def != null ? "§7需要: " + amp(def.name()) : "§7未知材料",
                        List.of("§7需求: §e" + defName(k) + " §7x" + need,
                                "§7拥有: " + (have >= need ? "§a" : "§c") + have,
                                "", "§7将碎片/材料点击放入此格")));
            } else {
                inv.setItem(slot, MainMenuGui.button(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
            }
        }

        // 按钮区
        inv.setItem(PREV_SLOT, MainMenuGui.button(Material.ARROW, "§7← 上一配方",
                List.of("§7切换到一个配方")));
        inv.setItem(NEXT_SLOT, MainMenuGui.button(Material.ARROW, "§7下一配方 →",
                List.of("§7切换到下一个配方")));
        inv.setItem(CRAFT_SLOT, MainMenuGui.button(Material.ANVIL, "§a§l合成神器",
                List.of("§7将上方材料格子中的碎片/材料",
                        "§7合成对应神器",
                        "", "§e点击合成")));
        inv.setItem(CLOSE_SLOT, MainMenuGui.button(Material.BARRIER, "§c关闭", List.of("§7关闭工作台")));
    }

    /** 翻页前保存当前材料槽中的物品 (供恢复, 避免翻页清空已放材料) */
    public List<ItemStack> saveMats(Inventory inv) {
        List<ItemStack> mats = new ArrayList<>();
        for (int slot : MAT_SLOTS) {
            ItemStack it = inv.getItem(slot);
            mats.add(it == null ? null : it.clone());
        }
        return mats;
    }

    /** 翻页后恢复材料槽内容 (raw 渲染已 clear, 重新放回) */
    public void restoreMats(Inventory inv, List<ItemStack> mats) {
        if (mats == null || mats.size() != MAT_SLOTS.length) return;
        for (int i = 0; i < MAT_SLOTS.length; i++) {
            ItemStack it = mats.get(i);
            if (it != null) inv.setItem(MAT_SLOTS[i], it.clone());
        }
    }

    /** 合成: 校验材料槽并退回多余/不匹配材料, 满足则收走并产出 (玩家区域线程) */
    public void craft(Player p, Inventory inv, GuiListener.GuiHolder holder) {
        List<CraftManager.Recipe> recipes = plugin.crafts().recipes();
        if (recipes.isEmpty()) {
            // TODO: 使用 plugin.msg();
            return;
        }
        int idx = holder.craftIndex;
        if (idx < 0 || idx >= recipes.size()) return;
        CraftManager.Recipe recipe = recipes.get(idx);

        // 统计材料槽中的碎片/材料
        Map<String, Integer> placed = new LinkedHashMap<>();
        List<ItemStack> leftovers = new ArrayList<>();
        for (int slot : MAT_SLOTS) {
            ItemStack it = inv.getItem(slot);
            if (it == null || it.getType().isAir()) continue;
            String k = plugin.crafts().craftKey(it);
            if (k == null) {
                leftovers.add(it); // 非碎片/材料, 退回
                inv.setItem(slot, null);
                continue;
            }
            placed.merge(k, it.getAmount(), Integer::sum);
        }

        // 校验是否满足配方
        boolean ok = true;
        for (Map.Entry<String, Integer> e : recipe.ingredients().entrySet()) {
            int have = placed.getOrDefault(e.getKey(), 0);
            if (have < e.getValue()) { ok = false; break; }
        }

        if (!ok) {
            p.sendMessage("§c材料不足或缺失, 无法合成 "
                    + nameOfart(recipe.artifact()) + "。");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1.2f);
            // 多余材料退回背包; 让玩家保留已放好的 (不清空)
            returnAlloc(inv, leftovers, placed, p);
            render(p, inv, holder);
            return;
        }

        // 满足: 从材料槽扣除所需材料
        Map<String, Integer> toConsume = new LinkedHashMap<>(recipe.ingredients());
        for (int slot : MAT_SLOTS) {
            ItemStack it = inv.getItem(slot);
            if (it == null || it.getType().isAir()) continue;
            String k = plugin.crafts().craftKey(it);
            if (k == null) { leftovers.add(it); inv.setItem(slot, null); continue; }
            int remainingNeed = toConsume.getOrDefault(k, 0);
            if (remainingNeed <= 0) {
                // 多余材料退回
                leftovers.add(it);
                inv.setItem(slot, null);
                continue;
            }
            int take = Math.min(it.getAmount(), remainingNeed);
            int rest = it.getAmount() - take;
            if (rest > 0) {
                it.setAmount(rest);
                inv.setItem(slot, it);
            } else {
                inv.setItem(slot, null);
            }
            toConsume.put(k, remainingNeed - take);
        }

        // 产出神器
        ItemStack out = plugin.artifacts().buildItem(recipe.artifact());
        if (out == null) {
            // TODO: 使用 plugin.msg();
            returnAlloc(inv, new ArrayList<>(), placed, p);
            render(p, inv, holder);
            return;
        }
        // 放入背包 (溢出丢弃)
        var map = p.getInventory().addItem(out);
        for (ItemStack rest : map.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
        p.sendMessage("§a成功合成神器: §f" + nameOfart(recipe.artifact()) + "!");
        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1f, 1.2f);
        // 退回多余材料
        returnAlloc(inv, leftovers, new LinkedHashMap<>(), p);
        // 清空材料槽并刷新
        for (int slot : MAT_SLOTS) inv.setItem(slot, null);
        render(p, inv, holder);
    }

    /** 把多余的碎片/材料退回到玩家背包或脚下 */
    private void returnAlloc(Inventory inv, List<ItemStack> leftovers, Map<String, Integer> placed, Player p) {
        for (ItemStack it : leftovers) {
            var m = p.getInventory().addItem(it);
            for (ItemStack r : m.values()) p.getWorld().dropItemNaturally(p.getLocation(), r);
        }
    }

    /** 玩家背包中某种碎片/材料的拥有量 */
    private int ownedInBag(Player p, String key) {
        int n = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (it == null) continue;
            String k = plugin.crafts().craftKey(it);
            if (key.equals(k)) n += it.getAmount();
        }
        return n;
    }

    /** 当前材料槽能满足配方的次数 (只读) */
    private int canCraft(Inventory inv, CraftManager.Recipe recipe) {
        Map<String, Integer> placed = new LinkedHashMap<>();
        for (int slot : MAT_SLOTS) {
            ItemStack it = inv.getItem(slot);
            if (it == null || it.getType().isAir()) continue;
            String k = plugin.crafts().craftKey(it);
            if (k != null) placed.merge(k, it.getAmount(), Integer::sum);
        }
        int times = Integer.MAX_VALUE;
        for (Map.Entry<String, Integer> e : recipe.ingredients().entrySet()) {
            int have = placed.getOrDefault(e.getKey(), 0);
            times = Math.min(times, have / Math.max(1, e.getValue()));
        }
        return times == Integer.MAX_VALUE ? 0 : times;
    }

    private boolean isDeco(int slot) {
        // 0..8 和 36..44 及按钮空位 = 装饰, 其余可交互
        return slot <= 8 || (slot >= 36 && slot <= 44);
    }

    private CraftManager.CraftDef craftDef(String key) {
        for (CraftManager.CraftDef d : plugin.crafts().defs()) if (d.key().equalsIgnoreCase(key)) return d;
        return null;
    }

    private String defName(String key) {
        CraftManager.CraftDef d = craftDef(key);
        return d != null ? amp(d.name()) : key;
    }

    private String nameOfart(String key) {
        return plugin.artifacts().nameOf(key);
    }

    /** & 码 → § 码 (使用Adventure序列化器) */
    private static String amp(String s) {
        if (s == null) return "";
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacyAmpersand().serialize(
                    net.kyori.adventure.text.Component.text(s));
    }
}
