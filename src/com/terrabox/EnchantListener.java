/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Material
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.block.Action
 *  org.bukkit.event.inventory.ClickType
 *  org.bukkit.event.inventory.InventoryClickEvent
 *  org.bukkit.event.inventory.InventoryType
 *  org.bukkit.event.player.PlayerInteractEntityEvent
 *  org.bukkit.event.player.PlayerInteractEvent
 *  org.bukkit.event.player.PlayerQuitEvent
 *  org.bukkit.inventory.EquipmentSlot
 *  org.bukkit.inventory.InventoryView
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.PlayerInventory
 */
package com.terrabox;

import com.terrabox.CraftGui;
import com.terrabox.SpecialItemManager;
import com.terrabox.TerraBoxPlugin;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class EnchantListener
implements Listener {
    private final TerraBoxPlugin plugin;
    private final Map<UUID, Long> useCool = new ConcurrentHashMap<UUID, Long>();
    private static final long COOL_MS = 200L;

    public EnchantListener(TerraBoxPlugin terraBoxPlugin) {
        this.plugin = terraBoxPlugin;
    }

    private boolean inCooldown(Player player) {
        Long l = this.useCool.get(player.getUniqueId());
        return l != null && System.currentTimeMillis() - l < 200L;
    }

    void markUse(Player player) {
        this.useCool.put(player.getUniqueId(), System.currentTimeMillis());
    }

    boolean holdsStone(Player player) {
        if (this.plugin.enchants() == null) {
            return false;
        }
        PlayerInventory playerInventory = player.getInventory();
        return this.isStone(playerInventory.getItemInMainHand()) || this.isStone(playerInventory.getItemInOffHand());
    }

    private boolean hasSpecialInHand(Player player) {
        SpecialItemManager specialItemManager = this.plugin.specialItems();
        if (specialItemManager == null) {
            return false;
        }
        PlayerInventory playerInventory = player.getInventory();
        try {
            ItemStack itemStack = playerInventory.getItemInMainHand();
            ItemStack itemStack2 = playerInventory.getItemInOffHand();
            return itemStack != null && !itemStack.getType().isAir() && specialItemManager.isSpecial(itemStack) || itemStack2 != null && !itemStack2.getType().isAir() && specialItemManager.isSpecial(itemStack2);
        }
        catch (Throwable throwable) {
            return false;
        }
    }

    private boolean tryUseHeldStone(Player player) {
        if (this.plugin.enchants() == null) {
            return false;
        }
        if (this.inCooldown(player)) {
            return false;
        }
        if (this.hasSpecialInHand(player)) {
            return false;
        }
        PlayerInventory playerInventory = player.getInventory();
        ItemStack itemStack = playerInventory.getItemInMainHand();
        ItemStack itemStack2 = playerInventory.getItemInOffHand();
        if (this.isStone(itemStack2)) {
            this.applyAndConsume(player, itemStack2, true);
            this.markUse(player);
            return true;
        }
        if (this.isStone(itemStack)) {
            this.applyAndConsume(player, itemStack, false);
            this.markUse(player);
            return true;
        }
        return false;
    }

    private boolean tryApplyOnBagClick(Player player, InventoryView inventoryView, int n, ClickType clickType, ItemStack itemStack, ItemStack itemStack2) {
        ItemStack itemStack3;
        int n2;
        if (this.plugin.enchants() == null) {
            return false;
        }
        if (this.inCooldown(player)) {
            return false;
        }
        if (this.hasSpecialInHand(player)) {
            return false;
        }
        if (itemStack == null || itemStack.getType().isAir() || !this.isStone(itemStack)) {
            return false;
        }
        if (itemStack2 == null || itemStack2.getType().isAir()) {
            return false;
        }
        if (this.isStone(itemStack2)) {
            return false;
        }
        if (!this.canEnchant(itemStack2.getType())) {
            return false;
        }
        if (clickType != ClickType.RIGHT && clickType != ClickType.DOUBLE_CLICK) {
            return false;
        }
        if (n == 24) {
            return false;
        }
        for (int itemStack6 : CraftGui.MAT_SLOTS) {
            if (n != itemStack6) continue;
            return false;
        }
        PlayerInventory playerInventory = player.getInventory();
        ItemStack itemStack4 = playerInventory.getItemInMainHand();
        ItemStack itemStack5 = playerInventory.getItemInOffHand();
        ItemStack itemStack6 = null;
        if (itemStack4 != null && !itemStack4.getType().isAir() && this.canEnchant(itemStack4.getType()) && !this.isStone(itemStack4)) {
            itemStack6 = itemStack4;
        } else if (itemStack5 != null && !itemStack5.getType().isAir() && this.canEnchant(itemStack5.getType()) && !this.isStone(itemStack5)) {
            itemStack6 = itemStack5;
        } else {
            for (n2 = 9; n2 < 36; ++n2) {
                itemStack3 = playerInventory.getItem(n2);
                if (itemStack3 == null || itemStack3.getType().isAir() || !this.canEnchant(itemStack3.getType()) || this.isStone(itemStack3)) continue;
                itemStack6 = itemStack3;
                break;
            }
        }
        if (itemStack6 == null) {
            player.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u8bf7\u5728\u624b\u4e0a\u6216\u80cc\u5305\u4e2d\u51c6\u5907\u4e00\u4ef6\u53ef\u9644\u9b54\u7684\u88c5\u5907\u3002");
            return false;
        }
        if (this.isStone(itemStack6)) {
            player.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u76ee\u6807\u4e0d\u662f\u53ef\u9644\u9b54\u88c5\u5907\u3002");
            return false;
        }
        n2 = this.plugin.enchants().applyToTarget(player, itemStack, itemStack6) ? 1 : 0;
        if (n2 == 0) {
            return false;
        }
        itemStack3 = itemStack.clone();
        itemStack3.setAmount(itemStack3.getAmount() - 1);
        if (itemStack3.getAmount() <= 0) {
            inventoryView.setCursor(null);
        } else {
            inventoryView.setCursor(itemStack3);
        }
        this.markUse(player);
        return true;
    }

    private boolean canEnchant(Material material) {
        if (material == null || material.isAir()) {
            return false;
        }
        String string = material.name();
        return string.contains("_SWORD") || string.contains("_PICKAXE") || string.contains("_AXE") || string.contains("_SHOVEL") || string.contains("_HOE") || string.contains("_BOW") || string.contains("CROSSBOW") || string.contains("_HELMET") || string.contains("_CHESTPLATE") || string.contains("_LEGGINGS") || string.contains("_BOOTS") || string.contains("TRIDENT");
    }

    private boolean isStone(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return false;
        }
        try {
            return this.plugin.enchants().isEnchantStone(itemStack);
        }
        catch (Throwable throwable) {
            return false;
        }
    }

    private boolean applyAndConsume(Player player, ItemStack itemStack, boolean bl) {
        boolean bl2 = this.plugin.enchants().apply(player, itemStack);
        if (bl2) {
            itemStack.setAmount(itemStack.getAmount() - 1);
            PlayerInventory playerInventory = player.getInventory();
            if (itemStack.getAmount() <= 0) {
                if (bl) {
                    playerInventory.setItemInOffHand(null);
                } else {
                    playerInventory.setItemInMainHand(null);
                }
            } else if (bl) {
                playerInventory.setItemInOffHand(itemStack);
            } else {
                playerInventory.setItemInMainHand(itemStack);
            }
        }
        return true;
    }

    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=false)
    public void onInteract(PlayerInteractEvent playerInteractEvent) {
        Action action = playerInteractEvent.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (playerInteractEvent.getHand() != null && playerInteractEvent.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = playerInteractEvent.getPlayer();
        if (this.hasSpecialInHand(player)) {
            return;
        }
        if (!this.holdsStone(player)) {
            return;
        }
        this.tryUseHeldStone(player);
        playerInteractEvent.setCancelled(true);
    }

    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=false)
    public void onInteractEntity(PlayerInteractEntityEvent playerInteractEntityEvent) {
        if (playerInteractEntityEvent.getHand() != null && playerInteractEntityEvent.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = playerInteractEntityEvent.getPlayer();
        if (this.hasSpecialInHand(player)) {
            return;
        }
        if (!this.holdsStone(player)) {
            return;
        }
        this.tryUseHeldStone(player);
        playerInteractEntityEvent.setCancelled(true);
    }

    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=true)
    public void onInventoryClick(InventoryClickEvent inventoryClickEvent) {
        Player player = (Player)inventoryClickEvent.getWhoClicked();
        InventoryView inventoryView = inventoryClickEvent.getView();
        int n = inventoryClickEvent.getRawSlot();
        ClickType clickType = inventoryClickEvent.getClick();
        if (!(inventoryClickEvent.getInventory().getHolder() instanceof Player)) {
            return;
        }
        if (inventoryView.getType() == InventoryType.CRAFTING) {
            return;
        }
        ItemStack itemStack = inventoryClickEvent.getCursor();
        if (itemStack == null || itemStack.getType().isAir() || !this.isStone(itemStack)) {
            return;
        }
        if (this.hasSpecialInHand(player)) {
            return;
        }
        ItemStack itemStack2 = inventoryClickEvent.getCurrentItem();
        if (this.tryApplyOnBagClick(player, inventoryView, n, clickType, itemStack, itemStack2)) {
            inventoryClickEvent.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent playerQuitEvent) {
        this.useCool.remove(playerQuitEvent.getPlayer().getUniqueId());
    }
}
