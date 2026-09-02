package com.terrabox;

import org.bukkit.Bukkit;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
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

/**
 * 游戏事件 (白皮书 §6.1): 每个事件在所属区域线程触发, 区域内方块/实体直接操作合法
 *  - 开箱: 统计 + 高稀有度广播 + 计分板记录
 *  - 搬空: 拆箱 + 异地补货
 *  - 保护: 禁破坏/禁漏斗/禁爆炸/禁活塞
 *  - 出生: 新玩家/非对局玩家 → 大厅; 对局内死亡淘汰/击杀统计
 */
public class GameListener implements Listener {
    private final TerraBoxPlugin plugin;
    // 记录玩家最近死亡位置 (用于淘汰玩家重生到死亡点旁观, 而非原版出生点)
    private final java.util.Map<UUID, Location> lastDeathLoc = new java.util.concurrent.ConcurrentHashMap<>();

    public GameListener(TerraBoxPlugin plugin) {
        this.plugin = plugin;
    }

    // ==================== 开箱 ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        InventoryHolder holder = e.getInventory().getHolder();
        if (!(holder instanceof Chest chest)) return;
        org.bukkit.Location loc = chest.getLocation();
        Bukkit.getRegionScheduler().run(plugin, loc, task -> {
            BoxManager.BoxEntry entry = plugin.boxes().registeredAt(chest.getBlock());
            if (entry == null) return;
            if (e.getPlayer() instanceof Player p) {
                PlayerStore.PlayerData d = plugin.players().getOrCreate(p.getUniqueId(), p.getName());
                d.addOpened(entry.rarity);
                if (entry.airdrop) d.airdropLooted.incrementAndGet();
                // 计分板记录开箱数
                if (plugin.rooms().isRunning() && plugin.rooms().isInGame(p.getUniqueId())) {
                    plugin.scoreboard().recordBox(p.getUniqueId());
                }
                boolean broadcast = entry.rarity == Rarity.MYTHIC
                        && plugin.getConfig().getBoolean("boxes.broadcast-mythic", true);
                if (broadcast) {
                    Bukkit.getGlobalRegionScheduler().execute(plugin, () ->
                            Bukkit.broadcast(plugin.component("open-broadcast",
                                    "{player}", p.getName(),
                                    "{rarity}", "§d§l" + entry.rarity.display)));
                }
            }
        });
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        InventoryHolder holder = e.getInventory().getHolder();
        if (!(holder instanceof Chest chest)) return;
        org.bukkit.Location loc = chest.getLocation();
        Bukkit.getRegionScheduler().run(plugin, loc, task -> {
            BoxManager.BoxEntry entry = plugin.boxes().registeredAt(chest.getBlock());
            if (entry == null) return;
            if (e.getInventory().isEmpty()) {
                plugin.boxes().handleChestEmptied(chest.getBlock(), entry);
                if (e.getPlayer() instanceof Player p) {
                    p.sendMessage(plugin.msg("box-emptied-self"));
                }
            }
        });
    }

    // ==================== 保护 ====================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!plugin.getConfig().getBoolean("boxes.protect-break", true)) return;
        if (isBoxWorld(e.getBlock().getWorld().getName()) && plugin.boxes().registeredAt(e.getBlock()) != null) {
            if (e.getPlayer().hasPermission("terrabox.admin")
                    && e.getPlayer().isSneaking()) return;
            e.setCancelled(true);
            e.getPlayer().sendMessage(plugin.msg("protected-block"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMoveItem(InventoryMoveItemEvent e) {
        if (!plugin.getConfig().getBoolean("boxes.protect-hopper", true)) return;
        if (e.getSource() == null) return;
        InventoryHolder holder = e.getSource().getHolder();
        if (holder instanceof Chest chest) {
            if (plugin.boxes().registeredAt(chest.getBlock()) != null) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> isProtectedBox(b));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> isProtectedBox(b));
    }

    private boolean isProtectedBox(Block b) {
        if (b.getType() != Material.CHEST) return false;
        return plugin.boxes().registeredAt(b) != null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        for (Block b : e.getBlocks()) {
            if (b.getType() == Material.CHEST && plugin.boxes().registeredAt(b) != null) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        for (Block b : e.getBlocks()) {
            if (b.getType() == Material.CHEST && plugin.boxes().registeredAt(b) != null) {
                e.setCancelled(true);
                return;
            }
        }
    }

    // ==================== 出生 ====================

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        PlayerStore.PlayerData d = plugin.players().getOrCreate(uuid, p.getName());
        plugin.players().loadAsync(uuid, p.getName(), () -> {
            p.getScheduler().runDelayed(plugin, task -> {
                if (d.isNew()) {
                    double start = plugin.getConfig().getDouble("economy.start-money", 0);
                    if (start > 0 && !plugin.econ().useVault()) d.addMoney(start);
                }
                // 对局玩家(参战且未淘汰)重连 → 传回对局世界; 其余所有情况(淘汰/退出/非对局) → 大厅
                if (plugin.rooms().isInGame(uuid)) {
                    World arena = plugin.worlds().world();
                    if (arena != null && !p.getWorld().getName().equals(arena.getName())) {
                        p.teleportAsync(plugin.spawnArea().spawnPointFor(0, 1));
                    }
                } else {
                    // 淘汰/退出/非对局玩家一律送回大厅 (防止滞留对局世界)
                    World lobby = plugin.worlds().lobby();
                    if (lobby != null && !p.getWorld().getName().equals(lobby.getName())) {
                        p.teleportAsync(plugin.lobbyBuilder().spawnLocation());
                    }
                }
            }, () -> {}, 30L);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        // 对局中(含淘汰/参战)玩家退出: 自动清空背包数据 (防止对局物品被带离)
        if (plugin.rooms().isRunning() && plugin.rooms().inGamePlayers().contains(uuid)) {
            try { p.getInventory().clear(); } catch (Throwable ignored) {}
        }
        plugin.players().saveAndUnload(uuid);
        plugin.rooms().onPlayerQuit(uuid);
        // 清理该玩家的房间邀请
        plugin.invites().clear(uuid);
    }

    // ==================== 对局 ====================

    /**
     * 玩家死亡事件: 取消死亡, 改为满血后自动切换旁观模式
     * 玩家可选择 /box lobby 返回大厅 或 /box spectate 继续旁观
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (!plugin.rooms().isInGame(p.getUniqueId())) return; // 仅对局内玩家
        if (!plugin.rooms().isRunning()) return; // 对局未开始不处理

        // 取消死亡: 满血 + 清除死亡掉落
        e.setCancelled(true);
        p.setHealth(20.0);
        p.setFoodLevel(20);
        p.clearActivePotionEffects();
        e.setDroppedExp(0);
        e.setDeathMessage(null);

        // 记录死亡位置 (用于旁观起点)
        try {
            lastDeathLoc.put(p.getUniqueId(), p.getLocation().clone());
        } catch (Throwable ignored) {}

        // 标记为淘汰（但不阻止重生）
        plugin.rooms().onPlayerDeath(p, null);

        // 立即切换到旁观模式, 避免原版死亡动画/重生流程
        p.setGameMode(org.bukkit.GameMode.SPECTATOR);
        p.sendMessage(plugin.msg("spectate-mode"));
        p.sendMessage(plugin.msg("spectate-info"));
    }

    /** PvP 伤害控制: 仅对局世界生效; SOLO 禁互伤; TEAM 同队免伤; 需双方都在本房间对局内 */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        if (!(e.getDamager() instanceof Player damager)) return;
        if (!isBoxWorld(victim.getWorld().getName())) return;
        // 找到受害玩家所属房间 (默认房间)
        GameManager g = plugin.rooms().roomOf(victim.getUniqueId());
        if (g == null || !g.isRunning()) return;
        // 双方都必须正在对局内 (未淘汰), 淘汰/旁观玩家不参与 PVP 结算
        if (!g.isInGame(damager.getUniqueId()) || !g.isInGame(victim.getUniqueId())) {
            e.setCancelled(true);
            return;
        }
        // 模式判定: SOLO 禁互伤, TEAM 同队免伤, PVP 正常
        if (!g.canDamage(damager, victim)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        UUID u = p.getUniqueId();
        // 玩家所属房间 (可能是非默认房间 — 多房间对局)
        GameManager g = plugin.rooms().roomOf(u);
        boolean eliminated = plugin.rooms().isEliminated(u);
        World lobby = plugin.worlds().lobby();

        // --- 淘汰玩家: 对局运行中 → 重生到死亡位置并自动旁观; 对局结束 → 重生到大厅 ---
        if (eliminated) {
            if (g != null && g.isRunning()) {
                World w = g.roomWorld();
                Location death = lastDeathLoc.get(u);
                Location base;
                if (death != null && w != null && death.getWorld() != null
                        && death.getWorld().getName().equals(w.getName())) {
                    // 重生在同一世界 XZ 的死亡点, Y 抬升至安全高度(不卡地形)
                    base = death.clone();
                    base.setY(Math.max(base.getY(), 60) + 3);
                } else {
                    base = w != null ? w.getSpawnLocation() : p.getWorld().getSpawnLocation();
                    base.setY(Math.max(base.getY(), 60) + 3);
                }
                e.setRespawnLocation(base);
                // 重生后自动进入旁观者模式 (留在死亡处观战)
                plugin.rooms().autoSpectateAfterDeath(p);
            } else {
                // 对局已结束 → 重生到大厅正常生存
                if (lobby != null) {
                    e.setRespawnLocation(lobby.getSpawnLocation());
                    p.getScheduler().run(plugin, t -> plugin.rooms().sendToLobby(p), () -> {});
                }
            }
            return;
        }

        // --- 对局运行中且未淘汰(SOLO等) → 重生回出生广场防卡 ---
        if (g != null && g.isRunning() && plugin.rooms().isInGame(u)) {
            World w = g.roomWorld();
            org.bukkit.Location safe = (w != null ? w.getSpawnLocation() : p.getWorld().getSpawnLocation()).clone();
            safe.setY(Math.max(safe.getY(), 80) + 3);
            e.setRespawnLocation(safe);
            return;
        }

        // --- 玩家当前在对局世界, 但对局已结束/空闲 → 重生到大厅 (防止滞留对局世界) ---
        if (isArenaWorld(p.getWorld()) && lobby != null) {
            e.setRespawnLocation(lobby.getSpawnLocation());
            p.getScheduler().run(plugin, t -> plugin.rooms().sendToLobby(p), () -> {});
            return;
        }

        // 其余情况按 spawn.on-respawn 配置处理
        if (!plugin.getConfig().getBoolean("spawn.on-respawn", false)) return;
        p.getScheduler().runDelayed(plugin, task ->
                        plugin.spawns().spawnPlayer(p, false), () -> {}, 15L);
    }

    /** 是否对局世界 (arena_* 或主资源世界) */
    private boolean isArenaWorld(World w) {
        if (w == null) return false;
        return w.getName().startsWith("arena");
    }

    /** 是否为对局世界 (arena_*或主资源世界) */
    private boolean isBoxWorld(String name) {
        return name.startsWith("arena") || name.equals(plugin.getConfig().getString("world.name", "resource_land"));
    }
}
