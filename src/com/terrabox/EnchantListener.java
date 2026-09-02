package com.terrabox;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

/**
 * 附魔石使用监听: 支持两种触发方式
 *   1. 手持附魔石右键 → 附魔另一只手的装备 (原有逻辑)
 *   2. 鼠标拿取附魔石 → 右键背包中的可附魔装备 (新增)
 *
 * 优先级: 特殊道具 > 附魔石
 *
 * 线程模型: 玩家区域线程 (白皮书 §6.1)
 */
public class EnchantListener implements Listener {
    private final TerraBoxPlugin plugin;
    /** 玩家UUID → 上次附魔应用时刻(ms), 防同一次右键的双事件重复应用 */
    private final java.util.Map<java.util.UUID, Long> useCool = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long COOL_MS = 200L;

    public EnchantListener(TerraBoxPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean inCooldown(Player p) {
        Long last = useCool.get(p.getUniqueId());
        return last != null && System.currentTimeMillis() - last < COOL_MS;
    }

    void markUse(Player p) {
        useCool.put(p.getUniqueId(), System.currentTimeMillis());
    }

    /** 主手或副手是否持有附魔石 */
    boolean holdsStone(Player p) {
        if (plugin.enchants() == null) return false;
        var inv = p.getInventory();
        return isStone(inv.getItemInMainHand()) || isStone(inv.getItemInOffHand());
    }

    /** 主手或副手是否持有特殊道具 (若是, 本次右键让路给 SpecialItemListener) */
    private boolean hasSpecialInHand(Player p) {
        var si = plugin.specialItems();
        if (si == null) return false;
        var inv = p.getInventory();
        try {
            ItemStack m = inv.getItemInMainHand();
            ItemStack o = inv.getItemInOffHand();
            return (m != null && !m.getType().isAir() && si.isSpecial(m))
                    || (o != null && !o.getType().isAir() && si.isSpecial(o));
        } catch (Throwable t) {
            return false;
        }
    }

    /** 尝试使用附魔石 (手持模式): 优先副手石→附魔主手装备; 否则主手石→附魔副手装备 */
    private boolean tryUseHeldStone(Player p) {
        if (plugin.enchants() == null) return false;
        if (inCooldown(p)) return false;
        if (hasSpecialInHand(p)) return false;
        var inv = p.getInventory();
        ItemStack main = inv.getItemInMainHand();
        ItemStack off = inv.getItemInOffHand();
        if (isStone(off)) { applyAndConsume(p, off, true); markUse(p); return true; }
        if (isStone(main)) { applyAndConsume(p, main, false); markUse(p); return true; }
        return false;
    }

    /**
     * 背包点击模式: 玩家鼠标持有附魔石 → 右键背包中可附魔的装备 → 应用附魔并消耗
     * @return true=已处理本次点击
     */
    private boolean tryApplyOnBagClick(Player p, InventoryView view, int slot,
                                        ClickType click, ItemStack cursor, ItemStack target) {
        if (plugin.enchants() == null) return false;
        if (inCooldown(p)) return false;
        if (hasSpecialInHand(p)) return false;
        // 校验光标持有附魔石
        if (cursor == null || cursor.getType().isAir() || !isStone(cursor)) return false;
        // 校验目标格非空且非附魔石
        if (target == null || target.getType().isAir()) return false;
        if (isStone(target)) return false; // 不能对附魔石本身附魔
        if (!canEnchant(target.getType())) return false;

        // 只响应右键 (双击也允许, 普通左键不触发以避免误操作)
        if (click != ClickType.RIGHT && click != ClickType.DOUBLE_CLICK) return false;

        // 工作台输出槽和材料槽不触发
        if (slot == CraftGui.OUTPUT_SLOT) return false;
        for (int matSlot : CraftGui.MAT_SLOTS) { if (slot == matSlot) return false; }

        // 背包点击模式: 根据光标(附魔石)判断"另一只手"的装备作为目标
        // 如果光标来自副手(玩家用副手拿起), 则目标 = 主手装备
        // 如果光标来自主手(玩家用主手拿起), 则目标 = 副手装备
        // 简化处理: 先检查手上装备, 再遍历背包找第一个可附魔装备
        var inv = p.getInventory();
        ItemStack mainHand = inv.getItemInMainHand();
        ItemStack offHand = inv.getItemInOffHand();
        ItemStack targetEquip = null;
        // 优先使用手上装备
        if (mainHand != null && !mainHand.getType().isAir() && canEnchant(mainHand.getType()) && !isStone(mainHand)) {
            targetEquip = mainHand;
        } else if (offHand != null && !offHand.getType().isAir() && canEnchant(offHand.getType()) && !isStone(offHand)) {
            targetEquip = offHand;
        } else {
            // 遍历背包找第一个可附魔装备 (不含快捷栏0-8的盔甲槽, 因为那是装备槽)
            for (int i = 9; i < 36; i++) {
                ItemStack it = inv.getItem(i);
                if (it != null && !it.getType().isAir() && canEnchant(it.getType()) && !isStone(it)) {
                    targetEquip = it;
                    break;
                }
            }
        }
        if (targetEquip == null) {
            // TODO: 使用 plugin.msg();
            return false;
        }
        if (isStone(targetEquip)) {
            // TODO: 使用 plugin.msg();
            return false;
        }

        // 执行附魔 (传入装备作为目标)
        boolean applied = plugin.enchants().applyToTarget(p, cursor, targetEquip);
        if (!applied) return false;

        // 消耗附魔石: 从光标扣减
        // cursor 是事件对象中的引用, 修改后会同步到游戏内光标显示
        // 先 clone 一份再修改, 避免污染原对象
        ItemStack newCursor = cursor.clone();
        newCursor.setAmount(newCursor.getAmount() - 1);
        if (newCursor.getAmount() <= 0) {
            view.setCursor(null); // 附魔石用完, 清空光标
        } else {
            view.setCursor(newCursor); // 还有剩余, 更新光标显示
        }
        markUse(p);
        return true;
    }

    /** 判断材质是否可附魔 */
    private boolean canEnchant(Material mat) {
        if (mat == null || mat.isAir()) return false;
        String name = mat.name();
        return name.contains("_SWORD") || name.contains("_PICKAXE")
                || name.contains("_AXE") || name.contains("_SHOVEL") || name.contains("_HOE")
                || name.contains("_BOW") || name.contains("CROSSBOW")
                || name.contains("_HELMET") || name.contains("_CHESTPLATE")
                || name.contains("_LEGGINGS") || name.contains("_BOOTS")
                || name.contains("TRIDENT");
    }

    private boolean isStone(ItemStack it) {
        if (it == null || it.getType().isAir()) return false;
        try { return plugin.enchants().isEnchantStone(it); } catch (Throwable t) { return false; }
    }

    /** 应用附魔并消耗附魔石 (手持模式, 对应手数量-1) */
    private boolean applyAndConsume(Player p, ItemStack stone, boolean stoneInOffHand) {
        boolean applied = plugin.enchants().apply(p, stone);
        if (applied) {
            stone.setAmount(stone.getAmount() - 1);
            var inv = p.getInventory();
            if (stone.getAmount() <= 0) {
                if (stoneInOffHand) inv.setItemInOffHand(null);
                else inv.setItemInMainHand(null);
            } else {
                if (stoneInOffHand) inv.setItemInOffHand(stone);
                else inv.setItemInMainHand(stone);
            }
        }
        return true;
    }

    // ==================== 事件监听 ====================

    /** 手持模式: 对空气/方块右键 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent e) {
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        // 只处理 HAND 事件, OFF_HAND 事件已含在主手事件中 (去重)
        if (e.getHand() != null && e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (hasSpecialInHand(p)) return;          // 让路特殊道具监听器
        if (!holdsStone(p)) return;               // 无附魔石, 不干预
        tryUseHeldStone(p);
        e.setCancelled(true);                     // 只要持石就吞掉, 防止被放置/食用
    }

    /** 手持模式: 对实体右键 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (e.getHand() != null && e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (hasSpecialInHand(p)) return;
        if (!holdsStone(p)) return;
        tryUseHeldStone(p);
        e.setCancelled(true);
    }

    /**
     * 背包点击模式: 鼠标拿取附魔石 → 右键背包中的装备 → 应用附魔并消耗
     * 优先级高于原版移动物品行为, 由 event.setCancelled(true) 接管
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        InventoryView view = e.getView();
        int slot = e.getRawSlot();
        ClickType click = e.getClick();

        // 只处理玩家自己的界面 (排除其他实体的容器)
        if (!(e.getInventory().getHolder() instanceof Player)) return;
        if (view.getType() == InventoryType.CRAFTING) return; // 工作台交给 CraftManager

        // 检查鼠标是否持有附魔石 (cursor = 玩家当前拿着的物品)
        ItemStack cursor = e.getCursor();
        if (cursor == null || cursor.getType().isAir() || !isStone(cursor)) return;
        if (hasSpecialInHand(p)) return;

        // 目标格 = 被点击的格子中的物品 (点击前状态, 由 Bukkit 在事件前更新)
        // 注意: InventoryClickEvent 在 modify 之前触发, e.getCurrentItem() 返回原内容
        ItemStack target = e.getCurrentItem();

        if (tryApplyOnBagClick(p, view, slot, click, cursor, target)) {
            e.setCancelled(true); // 阻止原版移动物品
        }
    }

    /** 玩家下线清理冷却记录 */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        useCool.remove(e.getPlayer().getUniqueId());
    }
}
