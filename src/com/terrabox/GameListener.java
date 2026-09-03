/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.kyori.adventure.text.Component
 *  org.bukkit.Bukkit
 *  org.bukkit.GameMode
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.World
 *  org.bukkit.block.Block
 *  org.bukkit.block.Chest
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.HumanEntity
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.block.BlockBreakEvent
 *  org.bukkit.event.block.BlockExplodeEvent
 *  org.bukkit.event.block.BlockPistonExtendEvent
 *  org.bukkit.event.block.BlockPistonRetractEvent
 *  org.bukkit.event.entity.EntityDamageByEntityEvent
 *  org.bukkit.event.entity.EntityExplodeEvent
 *  org.bukkit.event.entity.PlayerDeathEvent
 *  org.bukkit.event.inventory.InventoryCloseEvent
 *  org.bukkit.event.inventory.InventoryMoveItemEvent
 *  org.bukkit.event.inventory.InventoryOpenEvent
 *  org.bukkit.event.player.PlayerJoinEvent
 *  org.bukkit.event.player.PlayerQuitEvent
 *  org.bukkit.event.player.PlayerRespawnEvent
 *  org.bukkit.inventory.InventoryHolder
 *  org.bukkit.plugin.Plugin
 */
package com.terrabox;

import com.terrabox.BoxManager;
import com.terrabox.GameManager;
import com.terrabox.PlayerStore;
import com.terrabox.Rarity;
import com.terrabox.TerraBoxPlugin;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;

public class GameListener
implements Listener {
    private final TerraBoxPlugin plugin;
    private final Map<UUID, Location> lastDeathLoc = new ConcurrentHashMap<UUID, Location>();

    public GameListener(TerraBoxPlugin terraBoxPlugin) {
        this.plugin = terraBoxPlugin;
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onOpen(InventoryOpenEvent inventoryOpenEvent) {
        InventoryHolder inventoryHolder = inventoryOpenEvent.getInventory().getHolder();
        if (!(inventoryHolder instanceof Chest)) {
            return;
        }
        Chest chest = (Chest)inventoryHolder;
        Location location = chest.getLocation();
        Bukkit.getRegionScheduler().run((Plugin)this.plugin, location, scheduledTask -> {
            BoxManager.BoxEntry boxEntry = this.plugin.boxes().registeredAt(chest.getBlock());
            if (boxEntry == null) {
                return;
            }
            Object object = inventoryOpenEvent.getPlayer();
            if (object instanceof Player) {
                boolean bl;
                Player player = (Player)object;
                object = this.plugin.players().getOrCreate(player.getUniqueId(), player.getName());
                ((PlayerStore.PlayerData)object).addOpened(boxEntry.rarity);
                if (boxEntry.airdrop) {
                    ((PlayerStore.PlayerData)object).airdropLooted.incrementAndGet();
                }
                if (this.plugin.rooms().isRunning() && this.plugin.rooms().isInGame(player.getUniqueId())) {
                    this.plugin.scoreboard().recordBox(player.getUniqueId());
                }
                boolean bl2 = bl = boxEntry.rarity == Rarity.MYTHIC && this.plugin.getConfig().getBoolean("boxes.broadcast-mythic", true);
                if (bl) {
                    Bukkit.getGlobalRegionScheduler().execute((Plugin)this.plugin, () -> Bukkit.broadcast((Component)this.plugin.component("open-broadcast", "{player}", player.getName(), "{rarity}", "\u00a7d\u00a7l" + boxEntry.rarity.display)));
                }
            }
        });
    }

    @EventHandler
    public void onClose(InventoryCloseEvent inventoryCloseEvent) {
        InventoryHolder inventoryHolder = inventoryCloseEvent.getInventory().getHolder();
        if (!(inventoryHolder instanceof Chest)) {
            return;
        }
        Chest chest = (Chest)inventoryHolder;
        Location location = chest.getLocation();
        Bukkit.getRegionScheduler().run((Plugin)this.plugin, location, scheduledTask -> {
            BoxManager.BoxEntry boxEntry = this.plugin.boxes().registeredAt(chest.getBlock());
            if (boxEntry == null) {
                return;
            }
            if (inventoryCloseEvent.getInventory().isEmpty()) {
                this.plugin.boxes().handleChestEmptied(chest.getBlock(), boxEntry);
                HumanEntity humanEntity = inventoryCloseEvent.getPlayer();
                if (humanEntity instanceof Player) {
                    Player player = (Player)humanEntity;
                    player.sendMessage(this.plugin.msg("box-emptied-self"));
                }
            }
        });
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onBreak(BlockBreakEvent blockBreakEvent) {
        if (!this.plugin.getConfig().getBoolean("boxes.protect-break", true)) {
            return;
        }
        if (this.isBoxWorld(blockBreakEvent.getBlock().getWorld().getName()) && this.plugin.boxes().registeredAt(blockBreakEvent.getBlock()) != null) {
            if (blockBreakEvent.getPlayer().hasPermission("terrabox.admin") && blockBreakEvent.getPlayer().isSneaking()) {
                return;
            }
            blockBreakEvent.setCancelled(true);
            blockBreakEvent.getPlayer().sendMessage(this.plugin.msg("protected-block"));
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void onMoveItem(InventoryMoveItemEvent inventoryMoveItemEvent) {
        if (!this.plugin.getConfig().getBoolean("boxes.protect-hopper", true)) {
            return;
        }
        if (inventoryMoveItemEvent.getSource() == null) {
            return;
        }
        InventoryHolder inventoryHolder = inventoryMoveItemEvent.getSource().getHolder();
        if (inventoryHolder instanceof Chest) {
            Chest chest = (Chest)inventoryHolder;
            if (this.plugin.boxes().registeredAt(chest.getBlock()) != null) {
                inventoryMoveItemEvent.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void onEntityExplode(EntityExplodeEvent entityExplodeEvent) {
        entityExplodeEvent.blockList().removeIf(block -> this.isProtectedBox((Block)block));
    }

    @EventHandler(ignoreCancelled=true)
    public void onBlockExplode(BlockExplodeEvent blockExplodeEvent) {
        blockExplodeEvent.blockList().removeIf(block -> this.isProtectedBox((Block)block));
    }

    private boolean isProtectedBox(Block block) {
        if (block.getType() != Material.CHEST) {
            return false;
        }
        return this.plugin.boxes().registeredAt(block) != null;
    }

    @EventHandler(ignoreCancelled=true)
    public void onPistonExtend(BlockPistonExtendEvent blockPistonExtendEvent) {
        for (Block block : blockPistonExtendEvent.getBlocks()) {
            if (block.getType() != Material.CHEST || this.plugin.boxes().registeredAt(block) == null) continue;
            blockPistonExtendEvent.setCancelled(true);
            return;
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void onPistonRetract(BlockPistonRetractEvent blockPistonRetractEvent) {
        for (Block block : blockPistonRetractEvent.getBlocks()) {
            if (block.getType() != Material.CHEST || this.plugin.boxes().registeredAt(block) == null) continue;
            blockPistonRetractEvent.setCancelled(true);
            return;
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent playerJoinEvent) {
        Player player = playerJoinEvent.getPlayer();
        UUID uUID = player.getUniqueId();
        PlayerStore.PlayerData playerData = this.plugin.players().getOrCreate(uUID, player.getName());
        this.plugin.players().loadAsync(uUID, player.getName(), () -> player.getScheduler().runDelayed((Plugin)this.plugin, scheduledTask -> {
            double d;
            if (playerData.isNew() && (d = this.plugin.getConfig().getDouble("economy.start-money", 0.0)) > 0.0 && !this.plugin.econ().useVault()) {
                playerData.addMoney(d);
            }
            if (this.plugin.rooms().isInGame(uUID)) {
                World world = this.plugin.worlds().world();
                if (world != null && !player.getWorld().getName().equals(world.getName())) {
                    player.teleportAsync(this.plugin.spawnArea().spawnPointFor(0, 1));
                }
            } else {
                World world = this.plugin.worlds().lobby();
                if (world != null && !player.getWorld().getName().equals(world.getName())) {
                    player.teleportAsync(this.plugin.lobbyBuilder().spawnLocation());
                }
            }
        }, () -> {}, 30L));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent playerQuitEvent) {
        Player player = playerQuitEvent.getPlayer();
        UUID uUID = player.getUniqueId();
        if (this.plugin.rooms().isRunning() && this.plugin.rooms().inGamePlayers().contains(uUID)) {
            try {
                player.getInventory().clear();
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
        this.plugin.players().saveAndUnload(uUID);
        this.plugin.rooms().onPlayerQuit(uUID);
        this.plugin.invites().clear(uUID);
    }

    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=false)
    public void onDeath(PlayerDeathEvent playerDeathEvent) {
        Player player = playerDeathEvent.getEntity();
        if (!this.plugin.rooms().isInGame(player.getUniqueId())) {
            return;
        }
        if (!this.plugin.rooms().isRunning()) {
            return;
        }
        playerDeathEvent.setCancelled(true);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.clearActivePotionEffects();
        playerDeathEvent.setDroppedExp(0);
        playerDeathEvent.setDeathMessage(null);
        try {
            this.lastDeathLoc.put(player.getUniqueId(), player.getLocation().clone());
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        this.plugin.rooms().onPlayerDeath(player, null);
        player.setGameMode(GameMode.SPECTATOR);
        player.sendMessage(this.plugin.msg("prefix") + "\u00a7e\u4f60\u5df2\u53d7\u4f24\u5012\u5730, \u8fdb\u5165\u65c1\u89c2\u6a21\u5f0f!");
        player.sendMessage(this.plugin.msg("prefix") + "\u00a77\u8f93\u5165 \u00a7a/box lobby \u00a77\u8fd4\u56de\u5927\u5385, \u6216 \u00a7f/box spectate \u00a77\u7ee7\u7eed\u65c1\u89c2\u3002");
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onDamage(EntityDamageByEntityEvent entityDamageByEntityEvent) {
        Entity entity = entityDamageByEntityEvent.getEntity();
        if (!(entity instanceof Player)) {
            return;
        }
        Player player = (Player)entity;
        Object object = entityDamageByEntityEvent.getDamager();
        if (!(object instanceof Player)) {
            return;
        }
        entity = (Player)object;
        if (!this.isBoxWorld(player.getWorld().getName())) {
            return;
        }
        object = this.plugin.rooms().roomOf(player.getUniqueId());
        if (object == null || !((GameManager)object).isRunning()) {
            return;
        }
        if (!((GameManager)object).isInGame(entity.getUniqueId()) || !((GameManager)object).isInGame(player.getUniqueId())) {
            entityDamageByEntityEvent.setCancelled(true);
            return;
        }
        if (!((GameManager)object).canDamage((Player)entity, player)) {
            entityDamageByEntityEvent.setCancelled(true);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent playerRespawnEvent) {
        Player player = playerRespawnEvent.getPlayer();
        UUID uUID = player.getUniqueId();
        GameManager gameManager = this.plugin.rooms().roomOf(uUID);
        boolean bl = this.plugin.rooms().isEliminated(uUID);
        World world = this.plugin.worlds().lobby();
        if (bl) {
            if (gameManager != null && gameManager.isRunning()) {
                Location location;
                World world2 = gameManager.roomWorld();
                Location location2 = this.lastDeathLoc.get(uUID);
                if (location2 != null && world2 != null && location2.getWorld() != null && location2.getWorld().getName().equals(world2.getName())) {
                    location = location2.clone();
                    location.setY(Math.max(location.getY(), 60.0) + 3.0);
                } else {
                    location = world2 != null ? world2.getSpawnLocation() : player.getWorld().getSpawnLocation();
                    location.setY(Math.max(location.getY(), 60.0) + 3.0);
                }
                playerRespawnEvent.setRespawnLocation(location);
                this.plugin.rooms().autoSpectateAfterDeath(player);
            } else if (world != null) {
                playerRespawnEvent.setRespawnLocation(world.getSpawnLocation());
                player.getScheduler().run((Plugin)this.plugin, scheduledTask -> this.plugin.rooms().sendToLobby(player), () -> {});
            }
            return;
        }
        if (gameManager != null && gameManager.isRunning() && this.plugin.rooms().isInGame(uUID)) {
            World world3 = gameManager.roomWorld();
            Location location = (world3 != null ? world3.getSpawnLocation() : player.getWorld().getSpawnLocation()).clone();
            location.setY(Math.max(location.getY(), 80.0) + 3.0);
            playerRespawnEvent.setRespawnLocation(location);
            return;
        }
        if (this.isArenaWorld(player.getWorld()) && world != null) {
            playerRespawnEvent.setRespawnLocation(world.getSpawnLocation());
            player.getScheduler().run((Plugin)this.plugin, scheduledTask -> this.plugin.rooms().sendToLobby(player), () -> {});
            return;
        }
        if (!this.plugin.getConfig().getBoolean("spawn.on-respawn", false)) {
            return;
        }
        player.getScheduler().runDelayed((Plugin)this.plugin, scheduledTask -> this.plugin.spawns().spawnPlayer(player, false), () -> {}, 15L);
    }

    private boolean isArenaWorld(World world) {
        if (world == null) {
            return false;
        }
        return world.getName().startsWith("arena");
    }

    private boolean isBoxWorld(String string) {
        return string.startsWith("arena") || string.equals(this.plugin.getConfig().getString("world.name", "resource_land"));
    }
}
