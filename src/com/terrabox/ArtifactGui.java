package com.terrabox;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * 神器图鉴 GUI (54格, 全只读) — /box artifacts
 *
 * 展示所有神器及其名称/描述/效果, 供玩家了解神器之力与获取途径。
 * 全部物品禁止点击/拖动 (GuiListener Type.ARTIFACT 统一拦截)。
 *
 * 布局:
 *   4   : 标题
 *   10..16, 19..25 : 神器图标 (只读展示, 最多 14 件, 超出翻页可扩展)
 *   31   : 获取途径说明
 *   49   : 关闭
 *
 * 线程模型: open 由玩家区域线程调用; 展示为纯 ItemStack 构建。
 */
public class ArtifactGui {
    public static final String TITLE = "§8[§6物资大陆§8] §d神器图鉴";
    public static final int CLOSE_SLOT = 49;
    /** 神器图标展示槽 */
    public static final int[] SHOW_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25
    };

    private final TerraBoxPlugin plugin;

    public ArtifactGui(TerraBoxPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        GuiListener.GuiHolder holder = new GuiListener.GuiHolder(GuiListener.Type.ARTIFACT);
        Inventory inv = Bukkit.createInventory(holder, 54, TITLE);
        holder.inv = inv;
        render(p, inv, holder);
        p.openInventory(inv);
    }

    public void render(Player p, Inventory inv, GuiListener.GuiHolder holder) {
        inv.clear();
        // 装饰边缘 (灰玻璃)
        for (int i = 0; i < 54; i++) {
            if (i <= 8 || (i >= 36 && i <= 44)) {
                inv.setItem(i, MainMenuGui.button(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
            }
        }

        // 标题
        inv.setItem(4, MainMenuGui.button(Material.NETHER_STAR, "§d神器图鉴",
                List.of("§7所有神器一览, 装备后获得强大被动/主动效果。",
                        "",
                        "§7神器可从 §e传说/绝世物资箱 §7低概率获得,",
                        "§7或收集碎片/材料到 §e神器工作台 §7合成。",
                        "§7神器为终极装备, §c不可回收(商店拒绝)。")));

        // 神器图标 (只读展示)
        List<String> keys = plugin.artifacts().keys();
        int idx = 0;
        for (int slot : SHOW_SLOTS) {
            if (idx >= keys.size()) {
                inv.setItem(slot, MainMenuGui.button(Material.AIR, " ", List.of()));
                continue;
            }
            String key = keys.get(idx++);
            ItemStack art = plugin.artifacts().buildItem(key);
            if (art == null) continue;
            ItemMeta m = art.getItemMeta();
            if (m != null) {
                List<String> lore = new ArrayList<>();
                if (m.getLore() != null) lore.addAll(m.getLore());
                lore.add("");
                lore.add("§7获取: §e收集专属碎片+材料在工作台合成");
                lore.add("§7或 §e传说/绝世物资箱 §7掉落");
                m.setLore(lore);
                art.setItemMeta(m);
            }
            inv.setItem(slot, art);
        }

        // 获取途径
        inv.setItem(31, MainMenuGui.button(Material.CRAFTING_TABLE, "§b如何获得神器?",
                List.of("§7 1. 收集专属碎片 + 神器核心 + 秘银锭 + 星辰粉尘",
                        "§7 2. 到 §e神器工作台 §7(主菜单/§e/box craft§7) 合成",
                        "§7 3. 或在 §e传说/绝世物资箱 §7中低概率开箱获得",
                        "",
                        "§e碎片/材料为合成材料, 商店不可回收。")));

        // 关闭
        inv.setItem(CLOSE_SLOT, MainMenuGui.button(Material.BARRIER, "§c关闭", List.of("§7关闭图鉴")));
    }
}
