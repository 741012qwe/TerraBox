/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.block.Action
 *  org.bukkit.event.player.PlayerInteractEntityEvent
 *  org.bukkit.event.player.PlayerInteractEvent
 *  org.bukkit.event.player.PlayerItemConsumeEvent
 *  org.bukkit.event.player.PlayerQuitEvent
 *  org.bukkit.inventory.EquipmentSlot
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.PlayerInventory
 */
package com.terrabox;

import com.terrabox.TerraBoxPlugin;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class SpecialItemListener
implements Listener {
    private final TerraBoxPlugin plugin;
    private final Map<UUID, Long> useCool = new ConcurrentHashMap<UUID, Long>();
    private static final long COOL_MS = 200L;

    public SpecialItemListener(TerraBoxPlugin terraBoxPlugin) {
        this.plugin = terraBoxPlugin;
    }

    long lastUse(UUID uUID) {
        Long l = this.useCool.get(uUID);
        return l == null ? 0L : l;
    }

    void markUse(UUID uUID) {
        this.useCool.put(uUID, System.currentTimeMillis());
    }

    private boolean inCooldown(Player player) {
        long l = this.lastUse(player.getUniqueId());
        return l != 0L && System.currentTimeMillis() - l < 200L;
    }

    boolean holdsSpecial(Player player) {
        if (this.plugin.specialItems() == null) {
            return false;
        }
        PlayerInventory playerInventory = player.getInventory();
        return this.isSpecial(playerInventory.getItemInMainHand()) || this.isSpecial(playerInventory.getItemInOffHand());
    }

    boolean tryUse(Player player) {
        ItemStack itemStack;
        ItemStack itemStack2;
        if (this.plugin.specialItems() == null) {
            return false;
        }
        if (this.inCooldown(player)) {
            return false;
        }
        PlayerInventory playerInventory = player.getInventory();
        ItemStack itemStack3 = itemStack2 = playerInventory.getItemInMainHand();
        boolean bl = false;
        if (!this.isSpecial(itemStack2) && this.isSpecial(itemStack = playerInventory.getItemInOffHand())) {
            itemStack3 = itemStack;
            bl = true;
        }
        if (!this.isSpecial(itemStack3)) {
            return false;
        }
        if (!this.plugin.specialItems().trigger(player, itemStack3, bl)) {
            return false;
        }
        this.markUse(player.getUniqueId());
        return true;
    }

    private boolean isSpecial(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return false;
        }
        try {
            return this.plugin.specialItems().isSpecial(itemStack);
        }
        catch (Throwable throwable) {
            return false;
        }
    }

    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=false)
    public void onInteract(PlayerInteractEvent playerInteractEvent) {
        Action action = playerInteractEvent.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = playerInteractEvent.getPlayer();
        if (!this.holdsSpecial(player)) {
            return;
        }
        this.tryUse(player);
        playerInteractEvent.setCancelled(true);
    }

    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=false)
    public void onInteractEntity(PlayerInteractEntityEvent playerInteractEntityEvent) {
        Player player = playerInteractEntityEvent.getPlayer();
        if (playerInteractEntityEvent.getHand() != null && playerInteractEntityEvent.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!this.holdsSpecial(player)) {
            return;
        }
        this.tryUse(player);
        playerInteractEntityEvent.setCancelled(true);
    }

    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=true)
    public void onConsume(PlayerItemConsumeEvent playerItemConsumeEvent) {
        if (this.plugin.specialItems() == null) {
            return;
        }
        ItemStack itemStack = playerItemConsumeEvent.getItem();
        if (itemStack != null && !itemStack.getType().isAir() && this.plugin.specialItems().isSpecial(itemStack)) {
            playerItemConsumeEvent.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent playerQuitEvent) {
        this.useCool.remove(playerQuitEvent.getPlayer().getUniqueId());
    }
}
