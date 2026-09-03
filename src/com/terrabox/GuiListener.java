/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.ChatColor
 *  org.bukkit.GameRule
 *  org.bukkit.Material
 *  org.bukkit.Sound
 *  org.bukkit.World
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.inventory.ClickType
 *  org.bukkit.event.inventory.InventoryClickEvent
 *  org.bukkit.event.inventory.InventoryCloseEvent
 *  org.bukkit.event.inventory.InventoryDragEvent
 *  org.bukkit.inventory.Inventory
 *  org.bukkit.inventory.InventoryHolder
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.bukkit.plugin.Plugin
 */
package com.terrabox;

import com.terrabox.CraftGui;
import com.terrabox.GameManager;
import com.terrabox.Rarity;
import com.terrabox.TerraBoxPlugin;
import com.terrabox.TerrainType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameRule;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

public class GuiListener
implements Listener {
    private final TerraBoxPlugin plugin;

    public GuiListener(TerraBoxPlugin terraBoxPlugin) {
        this.plugin = terraBoxPlugin;
    }

    private GuiHolder holderOf(Inventory inventory) {
        GuiHolder guiHolder;
        InventoryHolder inventoryHolder;
        return inventory != null && (inventoryHolder = inventory.getHolder()) instanceof GuiHolder ? (guiHolder = (GuiHolder)inventoryHolder) : null;
    }

    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=true)
    public void onClick(InventoryClickEvent inventoryClickEvent) {
        Inventory inventory = inventoryClickEvent.getView().getTopInventory();
        GuiHolder guiHolder = this.holderOf(inventory);
        if (guiHolder == null) {
            return;
        }
        Player player = (Player)inventoryClickEvent.getWhoClicked();
        int n = inventoryClickEvent.getRawSlot();
        if (guiHolder.type == Type.MENU) {
            inventoryClickEvent.setCancelled(true);
            if (n < 0 || n >= inventory.getSize()) {
                return;
            }
            ItemStack itemStack = inventoryClickEvent.getCurrentItem();
            if (itemStack == null || itemStack.getType() == Material.AIR) {
                return;
            }
            switch (n) {
                case 10: {
                    this.plugin.spawns().spawnPlayer(player, true);
                    break;
                }
                case 12: {
                    this.plugin.sells().open(player);
                    break;
                }
                case 14: {
                    this.plugin.hunts().hunt(player);
                    break;
                }
                case 16: {
                    this.plugin.cmd().sendTop((CommandSender)player);
                    break;
                }
                case 11: {
                    this.sendDistribution(player);
                    break;
                }
                case 15: {
                    this.plugin.cmd().sendStats((CommandSender)player, player);
                    break;
                }
                case 13: {
                    player.sendMessage(this.plugin.msg("prefix") + "\u00a7e\u73a9\u6cd5\u8bf4\u660e\u5df2\u5217\u5728\u83dc\u5355\u56fe\u6807\u4e2d, /box \u4e5f\u53ef\u968f\u65f6\u67e5\u770b\u3002");
                    break;
                }
                case 18: {
                    this.plugin.gameGui().open(player);
                    break;
                }
                case 22: {
                    this.plugin.cmd().sendGameStatus((CommandSender)player);
                    break;
                }
                case 19: {
                    this.plugin.rooms().requestReturnToLobby(player);
                    break;
                }
                case 21: {
                    this.plugin.terrainSelect().open(player);
                    break;
                }
                case 17: {
                    this.plugin.roomGui().open(player);
                    break;
                }
            }
            return;
        }
        if (guiHolder.type == Type.GAME) {
            inventoryClickEvent.setCancelled(true);
            if (n < 0 || n >= inventory.getSize()) {
                return;
            }
            ItemStack itemStack = inventoryClickEvent.getCurrentItem();
            if (itemStack == null || itemStack.getType() == Material.AIR) {
                return;
            }
            switch (n) {
                case 10: {
                    this.joinMode(player, GameManager.Mode.SOLO);
                    break;
                }
                case 13: {
                    this.joinMode(player, GameManager.Mode.PVP);
                    break;
                }
                case 16: {
                    this.joinMode(player, GameManager.Mode.TEAM);
                    break;
                }
                case 18: {
                    this.plugin.cmd().sendGameStatus((CommandSender)player);
                    break;
                }
                case 22: {
                    this.plugin.menus().open(player);
                    break;
                }
                case 24: {
                    if (!player.hasPermission("terrabox.admin")) break;
                    player.sendMessage(this.plugin.msg("prefix") + "\u00a7e\u7ba1\u7406\u5458\u5f00\u8d5b\u547d\u4ee4: \u00a76/box room start <solo|pvp|team> <solo|pvp|team>");
                    player.sendMessage(this.plugin.msg("prefix") + "\u00a77\u4f8b: \u00a7e/box room start pvp pvp \u00a77(\u5728pvp\u623f\u95f4\u5f00\u591a\u4eba\u5bf9\u6218)");
                    break;
                }
            }
            return;
        }
        if (guiHolder.type == Type.TERRAIN) {
            inventoryClickEvent.setCancelled(true);
            if (n < 0 || n >= inventory.getSize()) {
                return;
            }
            ItemStack itemStack = inventoryClickEvent.getCurrentItem();
            if (itemStack == null || itemStack.getType() == Material.AIR) {
                return;
            }
            switch (n) {
                case 10: {
                    this.selectTerrain(player, TerrainType.DEFAULT);
                    break;
                }
                case 11: {
                    this.selectTerrain(player, TerrainType.DESERT);
                    break;
                }
                case 12: {
                    this.selectTerrain(player, TerrainType.ISLANDS);
                    break;
                }
                case 13: {
                    this.selectTerrain(player, TerrainType.THE_END);
                    break;
                }
                case 14: {
                    this.selectTerrain(player, TerrainType.BADLANDS);
                    break;
                }
                case 15: {
                    this.selectTerrain(player, TerrainType.NETHER);
                    break;
                }
                case 16: {
                    this.selectTerrain(player, TerrainType.CITY);
                    break;
                }
                case 17: {
                    this.selectTerrain(player, TerrainType.NORMAL);
                    break;
                }
                case 22: {
                    player.closeInventory();
                    break;
                }
                case 26: {
                    this.createNewTerrainWorld(player);
                    break;
                }
            }
            return;
        }
        if (guiHolder.type == Type.CRAFT) {
            if (n >= 0 && n < inventory.getSize() && this.isCraftMatSlot(n)) {
                boolean bl;
                if (inventoryClickEvent.getClick().isShiftClick() || inventoryClickEvent.getClick() == ClickType.DOUBLE_CLICK || inventoryClickEvent.getClick() == ClickType.NUMBER_KEY || inventoryClickEvent.getClick() == ClickType.SWAP_OFFHAND) {
                    inventoryClickEvent.setCancelled(true);
                    return;
                }
                inventoryClickEvent.setCancelled(true);
                ItemStack itemStack = inventoryClickEvent.getCursor();
                ItemStack itemStack2 = inventoryClickEvent.getCurrentItem();
                boolean bl2 = bl = itemStack == null || itemStack.getType().isAir() || this.plugin.crafts().isCraftItem(itemStack);
                if (!bl) {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.2f);
                    player.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u53ea\u80fd\u653e\u5165\u788e\u7247/\u6750\u6599, \u65e0\u6cd5\u653e\u7f6e\u6b64\u7269\u54c1\u3002");
                    return;
                }
                ItemStack itemStack3 = itemStack;
                ItemStack itemStack4 = itemStack2;
                if (itemStack2 != null && !itemStack2.getType().isAir() && !this.plugin.crafts().isCraftItem(itemStack2)) {
                    player.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u6750\u6599\u69fd\u5b58\u5728\u5f02\u5e38\u7269\u54c1, \u8bf7\u5173\u95ed\u91cd\u5f00\u3002");
                    return;
                }
                inventoryClickEvent.setCurrentItem((ItemStack)(itemStack3 != null && itemStack3.getType().isAir() ? null : itemStack3.clone()));
                inventoryClickEvent.getView().setCursor((ItemStack)(itemStack4 != null && itemStack4.getType().isAir() ? null : itemStack4.clone()));
                return;
            }
            if (n < 0 || n >= inventory.getSize()) {
                return;
            }
            inventoryClickEvent.setCancelled(true);
            if (n == 24) {
                return;
            }
            switch (n) {
                case 46: {
                    GuiHolder guiHolder2 = guiHolder;
                    guiHolder2.craftIndex = (guiHolder2.craftIndex - 1 + this.plugin.crafts().recipes().size()) % Math.max(1, this.plugin.crafts().recipes().size());
                    this.plugin.craftsGui().render(player, inventory, guiHolder2);
                    break;
                }
                case 47: {
                    GuiHolder guiHolder3 = guiHolder;
                    guiHolder3.craftIndex = (guiHolder3.craftIndex + 1) % Math.max(1, this.plugin.crafts().recipes().size());
                    this.plugin.craftsGui().render(player, inventory, guiHolder3);
                    break;
                }
                case 49: {
                    this.plugin.craftsGui().craft(player, inventory, guiHolder);
                    break;
                }
                case 50: {
                    player.closeInventory();
                    break;
                }
            }
            return;
        }
        if (guiHolder.type == Type.ARTIFACT) {
            inventoryClickEvent.setCancelled(true);
            return;
        }
        if (guiHolder.type == Type.INVITE) {
            String string;
            inventoryClickEvent.setCancelled(true);
            if (n < 0 || n >= inventory.getSize()) {
                return;
            }
            ItemStack itemStack = inventoryClickEvent.getCurrentItem();
            if (itemStack == null || itemStack.getType() == Material.AIR) {
                return;
            }
            String string2 = string = guiHolder.inviteRoom != null ? guiHolder.inviteRoom : "default";
            if (inventoryClickEvent.getCurrentItem().getType() == Material.PLAYER_HEAD && inventoryClickEvent.getCurrentItem().getItemMeta() != null) {
                String string3 = ChatColor.stripColor((String)inventoryClickEvent.getCurrentItem().getItemMeta().getDisplayName());
                Player player2 = Bukkit.getPlayer((String)string3);
                if (player2 != null && player2.isOnline()) {
                    this.plugin.invites().invite(player, player2, string);
                } else {
                    player.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u8be5\u73a9\u5bb6\u4e0d\u5728\u7ebf\u3002");
                }
            }
            return;
        }
        if (guiHolder.type == Type.ROOM) {
            GameManager gameManager;
            inventoryClickEvent.setCancelled(true);
            if (n < 0 || n >= inventory.getSize()) {
                return;
            }
            if (n == 28) {
                this.plugin.cmd().createRoomFor(player, null);
                return;
            }
            if (n == 30) {
                String string = this.plugin.rooms().defaultRoom().roomId();
                Iterator<GameManager> iterator = this.plugin.rooms().joinedRooms(player.getUniqueId()).iterator();
                if (iterator.hasNext()) {
                    GameManager gameManager2 = iterator.next();
                    string = gameManager2.roomId();
                }
                this.plugin.inviteGui().open(player, string);
                return;
            }
            if (n == 22) {
                this.plugin.menus().open(player);
                return;
            }
            if (n == 49) {
                player.closeInventory();
                return;
            }
            ItemStack itemStack = inventoryClickEvent.getCurrentItem();
            if (itemStack == null || itemStack.getType() == Material.AIR) {
                return;
            }
            String string = ChatColor.stripColor((String)itemStack.getItemMeta().getDisplayName());
            String string4 = string.replace("\u623f\u95f4", "").trim();
            if (!string4.isEmpty() && (gameManager = this.plugin.rooms().get(string4)) != null) {
                if (gameManager.isInGame(player.getUniqueId())) {
                    gameManager.leave(player);
                } else {
                    gameManager.join(player);
                }
                this.plugin.roomGui().render(player, inventory, guiHolder);
            }
            return;
        }
        if (n < inventory.getSize()) {
            if (n == 49) {
                inventoryClickEvent.setCancelled(true);
                if (inventoryClickEvent.getClick().isLeftClick() || inventoryClickEvent.getClick().isRightClick()) {
                    this.plugin.sells().settle(player, inventory);
                    this.refreshSellInfo(inventory);
                }
                return;
            }
            if (n >= 45) {
                inventoryClickEvent.setCancelled(true);
            }
            return;
        }
    }

    private void refreshSellInfo(Inventory inventory) {
        ItemStack itemStack;
        long l = 0L;
        int n = 0;
        Map<String, Double> map = this.plugin.sellPrices();
        for (int i = 0; i < 45; ++i) {
            Double d;
            itemStack = inventory.getItem(i);
            if (itemStack == null || itemStack.getType().isAir() || (d = map.get(itemStack.getType().name())) == null || !(d > 0.0)) continue;
            l += (long)Math.floor(d * (double)itemStack.getAmount());
            n += itemStack.getAmount();
        }
        ItemStack itemStack2 = inventory.getItem(49);
        if (itemStack2 != null && itemStack2.getItemMeta() != null) {
            itemStack = itemStack2.getItemMeta();
            itemStack.setDisplayName("\u00a7a\u00a7l\u786e\u8ba4\u51fa\u552e");
            itemStack.setLore(Arrays.asList("\u00a77\u5f85\u56de\u6536: \u00a7e" + n + " \u4ef6", "\u00a77\u9884\u8ba1\u83b7\u5f97: \u00a7e" + l + " \u5143", "", "\u00a7e\u70b9\u51fb\u7ed3\u7b97"));
            itemStack2.setItemMeta((ItemMeta)itemStack);
        }
    }

    private boolean isCraftMatSlot(int n) {
        for (int n2 : CraftGui.MAT_SLOTS) {
            if (n != n2) continue;
            return true;
        }
        return false;
    }

    private void sendDistribution(Player player) {
        Map<Rarity, Integer> map = this.plugin.boxes().countByRarity();
        player.sendMessage(this.plugin.msg("prefix") + "\u00a7e\u5730\u56fe\u7269\u8d44\u7bb1\u5206\u5e03 (\u5171 \u00a7a" + this.plugin.boxes().count() + "\u00a7e \u4e2a):");
        for (Rarity rarity : Rarity.values()) {
            player.sendMessage(" \u00a77" + rarity.display + ": " + rarity.colorCode.replace('&', '\u00a7') + String.valueOf(map.getOrDefault((Object)rarity, 0)) + " \u4e2a");
        }
        player.sendMessage(this.plugin.msg("prefix") + "\u00a77\u4e0b\u4e00\u6ce2\u7a7a\u6295: \u00a7d\u7ea6 " + (this.plugin.airdrops().secondsUntilNext() / 60L + 1L) + " \u5206\u949f\u540e");
    }

    private void joinMode(Player player, GameManager.Mode mode) {
        String string = switch (mode) {
            case GameManager.Mode.SOLO -> "solo";
            case GameManager.Mode.TEAM -> "team";
            default -> "pvp";
        };
        String string2 = this.plugin.worlds().world() != null ? this.plugin.worlds().world().getName() : null;
        GameManager gameManager = this.plugin.rooms().createRoom(string, string2);
        gameManager.toggleJoin(player);
        player.closeInventory();
        this.plugin.gameGui().open(player);
    }

    private void selectTerrain(Player player, TerrainType terrainType) {
        if (!player.hasPermission("terrabox.admin")) {
            player.sendMessage(this.plugin.msg("no-permission"));
            return;
        }
        boolean bl = this.plugin.arenas().selectByTerrain(terrainType);
        if (bl) {
            String string = this.plugin.arenas().current() != null ? this.plugin.arenas().current().getName() : terrainType.display;
            player.sendMessage(this.plugin.msg("prefix") + "\u00a7a\u5df2\u9009\u62e9\u5730\u5f62: " + terrainType.colorCode + terrainType.display + " \u00a77(\u4e16\u754c: \u00a7e" + string + "\u00a77)");
            player.sendMessage(this.plugin.msg("prefix") + "\u00a76\u6b63\u5728\u91cd\u65b0\u521d\u59cb\u5316\u8be5\u4e16\u754c\u5730\u5f62\u4e0e\u7269\u8d44\u7bb1...");
            this.plugin.switchArena();
            player.sendMessage(this.plugin.msg("prefix") + "\u00a7e\u5730\u56fe\u5df2\u5c31\u7eea, \u7528 \u00a7a/box game start <solo|pvp|team> \u00a7e\u5f00\u8d5b\u3002");
            player.closeInventory();
        } else {
            player.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u5730\u5f62\u4e16\u754c\u521b\u5efa\u5931\u8d25, \u8bf7\u68c0\u67e5\u63a7\u5236\u53f0\u65e5\u5fd7\u3002");
        }
    }

    private void createNewTerrainWorld(Player player) {
        if (!player.hasPermission("terrabox.admin")) {
            player.sendMessage(this.plugin.msg("no-permission"));
            return;
        }
        TerrainType terrainType = TerrainType.DEFAULT;
        String string = "default";
        World world = this.plugin.arenas().createNew(TerrainType.parse(string));
        if (world != null) {
            Bukkit.getGlobalRegionScheduler().run((Plugin)this.plugin, scheduledTask -> {
                try {
                    world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, (Object)false);
                    world.setTime(6000L);
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
                this.plugin.arenas().select(world.getName());
            });
            player.sendMessage(this.plugin.msg("prefix") + "\u00a7a\u5df2\u751f\u6210\u65b0\u5bf9\u5c40\u4e16\u754c: \u00a7e" + world.getName());
            player.closeInventory();
        } else {
            player.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u65b0\u5bf9\u5c40\u4e16\u754c\u521b\u5efa\u5931\u8d25\u3002");
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent inventoryDragEvent) {
        Inventory inventory = inventoryDragEvent.getView().getTopInventory();
        GuiHolder guiHolder = this.holderOf(inventory);
        if (guiHolder == null) {
            return;
        }
        if (guiHolder.type == Type.MENU || guiHolder.type == Type.TERRAIN || guiHolder.type == Type.GAME) {
            inventoryDragEvent.setCancelled(true);
            return;
        }
        if (guiHolder.type == Type.CRAFT) {
            inventoryDragEvent.setCancelled(true);
            return;
        }
        if (guiHolder.type == Type.ARTIFACT) {
            inventoryDragEvent.setCancelled(true);
            return;
        }
        if (guiHolder.type == Type.INVITE || guiHolder.type == Type.ROOM) {
            inventoryDragEvent.setCancelled(true);
            return;
        }
        Iterator iterator = inventoryDragEvent.getRawSlots().iterator();
        while (iterator.hasNext()) {
            int n = (Integer)iterator.next();
            if (n >= inventory.getSize() || n >= 0 && n < 45 && n != 49) continue;
            inventoryDragEvent.setCancelled(true);
            return;
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent inventoryCloseEvent) {
        Object object = inventoryCloseEvent.getPlayer();
        if (!(object instanceof Player)) {
            return;
        }
        Player player = (Player)object;
        object = this.holderOf(inventoryCloseEvent.getInventory());
        if (object == null) {
            return;
        }
        if (object.type == Type.SELL) {
            this.plugin.sells().returnItems(player, inventoryCloseEvent.getInventory());
        }
        if (object.type == Type.CRAFT) {
            for (int n : CraftGui.MAT_SLOTS) {
                ItemStack itemStack = inventoryCloseEvent.getInventory().getItem(n);
                if (itemStack == null || itemStack.getType().isAir()) continue;
                HashMap hashMap = player.getInventory().addItem(new ItemStack[]{itemStack});
                for (ItemStack itemStack2 : hashMap.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), itemStack2);
                }
                inventoryCloseEvent.getInventory().setItem(n, null);
            }
        }
    }

    public static class GuiHolder
    implements InventoryHolder {
        public final Type type;
        public Inventory inv;
        public int craftIndex = 0;
        public String inviteRoom = null;

        public GuiHolder(Type type) {
            this.type = type;
        }

        public Inventory getInventory() {
            return this.inv;
        }
    }

    static enum Type {
        MENU,
        SELL,
        TERRAIN,
        GAME,
        CRAFT,
        ARTIFACT,
        INVITE,
        ROOM;

    }
}
