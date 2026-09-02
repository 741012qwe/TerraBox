package com.terrabox;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 物资回收商店 GUI (54格):
 *  - 0..44: 玩家放置待回收物品
 *  - 48 说明 / 49 确认出售 / 53 关闭
 * 线程模型: 点击结算发生在玩家区域线程 (InventoryClickEvent), 直接读写 Inventory 合法
 */
public class SellGui {
    public static final String TITLE = "§8[§6物资大陆§8] §a物资回收商店";
    public static final int CONFIRM_SLOT = 49;

    private final TerraBoxPlugin plugin;

    public SellGui(TerraBoxPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        GuiListener.GuiHolder holder = new GuiListener.GuiHolder(GuiListener.Type.SELL);
        Inventory inv = Bukkit.createInventory(holder, 54, TITLE);
        holder.inv = inv;
        inv.setItem(48, MainMenuGui.button(Material.BOOK, "§e回收说明",
                List.of("§7把要出售的物品放到上方 45 格中",
                        "§7然后点击 §a确认出售 §7按钮",
                        "§7价格为每件单价, 支持成组结算",
                        "§7无法回收的物品会留在格子里",
                        "", "§e输入 /box prices 查看价格表")));
        inv.setItem(CONFIRM_SLOT, MainMenuGui.button(Material.EMERALD, "§a§l确认出售",
                List.of("§7点击结算上方所有可回收物品")));
        inv.setItem(53, MainMenuGui.button(Material.BARRIER, "§c关闭商店",
                List.of("§7未出售的物品会退回背包")));

        p.openInventory(inv);
    }

    /** 结算 (玩家区域线程): 0..44 逐格按价回收 */
    public void settle(Player p, Inventory inv) {
        Map<String, Double> prices = plugin.sellPrices();
        long money = 0;
        int count = 0;
        List<String> rejected = new ArrayList<>();
        for (int slot = 0; slot < 45; slot++) {
            ItemStack it = inv.getItem(slot);
            if (it == null || it.getType().isAir()) continue;
            // 特殊道具不可回收 (功能性道具)
            if (plugin.specialItems() != null && plugin.specialItems().isSpecial(it)) {
                if (rejected.size() < 5 && !rejected.contains(it.getType().name())) {
                    rejected.add(it.getType().name() + "(特殊道具)");
                }
                continue;
            }
            // 神器不可回收 (终极装备)
            if (plugin.artifacts() != null && plugin.artifacts().isArtifact(it)) {
                if (rejected.size() < 5 && !rejected.contains(it.getType().name())) {
                    rejected.add(it.getType().name() + "(神器)");
                }
                continue;
            }
            // 附魔石不可回收
            if (plugin.enchants() != null && plugin.enchants().isEnchantStone(it)) {
                if (rejected.size() < 5 && !rejected.contains(it.getType().name())) {
                    rejected.add(it.getType().name() + "(附魔石)");
                }
                continue;
            }
            // 碎片/材料不可回收 (合成材料)
            if (plugin.crafts() != null && plugin.crafts().isCraftItem(it)) {
                if (rejected.size() < 5 && !rejected.contains(it.getType().name())) {
                    rejected.add(it.getType().name() + "(合成材料)");
                }
                continue;
            }
            Double price = prices.get(it.getType().name());
            if (price == null || price <= 0) {
                if (rejected.size() < 5 && !rejected.contains(it.getType().name())) {
                    rejected.add(it.getType().name());
                }
                continue;
            }
            double value = price * it.getAmount();
            money += (long) Math.floor(value);
            inv.setItem(slot, null);
            count += it.getAmount();
        }
        if (count == 0 && money == 0) {
            p.sendMessage(plugin.msg("sell-empty"));
            return;
        }
        if (money > 0) {
            plugin.econ().deposit(p, money);
            PlayerStore.PlayerData d = plugin.players().getOrCreate(p.getUniqueId(), p.getName());
            d.soldValue.addAndGet(money);
            d.touch();
        }
        p.sendMessage(plugin.msg("sell-done")
                .replace("{count}", String.valueOf(count))
                .replace("{money}", String.valueOf(money)));
        if (!rejected.isEmpty()) {
            p.sendMessage(plugin.msg("prefix") + "§7以下物品暂不回收: §c" + String.join(", ", rejected));
        }
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1.2f);
    }

    /** 关闭商店: 退回未出售物品 (玩家区域线程, 溢出掉落在脚边) */
    public void returnItems(Player p, Inventory inv) {
        List<ItemStack> leftover = new ArrayList<>();
        for (int slot = 0; slot < 45; slot++) {
            ItemStack it = inv.getItem(slot);
            if (it == null || it.getType().isAir()) continue;
            leftover.add(it);
            inv.setItem(slot, null);
        }
        if (leftover.isEmpty()) return;
        var map = p.getInventory().addItem(leftover.toArray(new ItemStack[0]));
        for (ItemStack rest : map.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
        p.sendMessage(plugin.msg("prefix") + "§7商店物品已退回背包(溢出掉落)。" + map.size());
    }
}
