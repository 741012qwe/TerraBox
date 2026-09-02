package com.terrabox;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 特殊道具使用监听: 玩家右键手持特殊道具时触发对应效果 (玩家区域线程)
 * 白皮书 §6.1: 事件在所属区域线程触发, 玩家实体操作合法
 *
 * 触发通道 (全部走同一个 tryUse, 主手优先 → 副手回退):
 *   1. 对空气右键  PlayerInteractEvent(RIGHT_CLICK_AIR)
 *   2. 对方块右键  PlayerInteractEvent(RIGHT_CLICK_BLOCK)
 *   3. 对实体右键  PlayerInteractEntityEvent
 *
 * 去重: 双手各持物品时一次右键, Bukkit 会先后投递 HAND 与 OFF_HAND 两个
 * PlayerInteractEvent; 对实体右键也可能连带触发空气右键事件。因此每次成功
 * 消费后记录时间戳, 冷却窗口内的重复事件一律跳过 —— 保证"一次右键只消耗一个"。
 */
public class SpecialItemListener implements Listener {
    private final TerraBoxPlugin plugin;
    /** 玩家UUID → 上次成功消费的时刻(ms), 用于同一次右键的多事件去重 */
    private final Map<UUID, Long> useCool = new ConcurrentHashMap<>();
    /** 去重窗口: 同 tick 内的 HAND/OFF_HAND 双事件必然落在其中, 又不影响正常连点 */
    private static final long COOL_MS = 200L;

    public SpecialItemListener(TerraBoxPlugin plugin) {
        this.plugin = plugin;
    }

    /** 玩家UUID → 上次消费时刻 (供其他"自定义道具"监听器共享同一去重窗口) */
    long lastUse(UUID u) {
        Long t = useCool.get(u);
        return t == null ? 0L : t;
    }

    void markUse(UUID u) {
        useCool.put(u, System.currentTimeMillis());
    }

    /** 冷却窗口内是否已消费过 (同一次右键的重复事件) */
    private boolean inCooldown(Player p) {
        long last = lastUse(p.getUniqueId());
        return last != 0L && System.currentTimeMillis() - last < COOL_MS;
    }

    /** 主手或副手是否持有特殊道具 */
    boolean holdsSpecial(Player p) {
        if (plugin.specialItems() == null) return false;
        var inv = p.getInventory();
        return isSpecial(inv.getItemInMainHand()) || isSpecial(inv.getItemInOffHand());
    }

    /**
     * 在玩家区域线程尝试使用特殊道具: 主手持有则用主手, 否则副手持有用副手。
     * 冷却窗口内不重复触发 (同一次右键的 HAND/OFF_HAND 双事件去重)。
     * @return true=已触发并消费一个
     */
    boolean tryUse(Player p) {
        if (plugin.specialItems() == null) return false;
        if (inCooldown(p)) return false;
        var inv = p.getInventory();
        ItemStack main = inv.getItemInMainHand();
        ItemStack item = main;
        boolean offHand = false;
        if (!isSpecial(main)) {
            ItemStack off = inv.getItemInOffHand();
            if (isSpecial(off)) {
                item = off;
                offHand = true;
            }
        }
        if (!isSpecial(item)) return false;
        if (!plugin.specialItems().trigger(p, item, offHand)) return false;
        markUse(p.getUniqueId());
        return true;
    }

    /** 物品是否为特殊道具 (null/空气/异常一律 false) */
    private boolean isSpecial(ItemStack it) {
        if (it == null || it.getType().isAir()) return false;
        try {
            return plugin.specialItems().isSpecial(it);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 对空气/方块右键 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent e) {
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        // 不再过滤已取消事件, 确保特殊道具在各种情况下都能触发
        Player p = e.getPlayer();
        if (!holdsSpecial(p)) return;
        // 持有特殊道具就吞掉原版行为 (投掷/放置/食用), 冷却内的重复事件不再触发效果
        tryUse(p);
        e.setCancelled(true);
    }

    /** 对实体右键 (让玩家/生物也可作为目标使用道具) */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        // 副手事件跳过: 主手事件已覆盖同一动作, 避免重复
        if (e.getHand() != null && e.getHand() != EquipmentSlot.HAND) return;
        if (!holdsSpecial(p)) return;
        tryUse(p);
        e.setCancelled(true); // 用道具效果替代原版交互
    }

    /** 阻止特殊道具被原版饮用/食用 (如 POTION 材质道具被当药水喝掉) */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        if (plugin.specialItems() == null) return;
        ItemStack item = e.getItem();
        if (item != null && !item.getType().isAir() && plugin.specialItems().isSpecial(item)) {
            e.setCancelled(true); // 特殊道具不参与饮用, 效果由右键触发逻辑处理
        }
    }

    /** 玩家下线清理去重记录, 防内存泄漏 */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent e) {
        useCool.remove(e.getPlayer().getUniqueId());
    }
}
