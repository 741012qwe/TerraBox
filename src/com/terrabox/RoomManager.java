package com.terrabox;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 对局房间管理器 —— 支持多个对局房间并存 (每个房间绑定独立 arena 世界)
 *
 * 设计:
 *  - 每个房间是一个 GameManager 实例, 有独立状态机/报名字单/绑定的对局世界
 *  - 默认房间 "default" 绑定当前 arena 世界 (行为与原单对局完全一致)
 *  - 额外房间由管理员 /box room create <id> <world> 创建, 绑定指定世界
 *  - 计分板: 每个房间有其独立 ScoreboardManager 实例 (多房间互不干扰)
 *
 * 兼容转发: 插件内大量调用 plugin.games().xxx(), 本类提供同名方法并转发到
 *   主房间 (default), 避免改造所有调用点。多房间专用命令走独立方法。
 */
public class RoomManager {
    private final TerraBoxPlugin plugin;
    private final Map<String, GameManager> rooms = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<String> roomOrder = new CopyOnWriteArrayList<>();
    private final Map<String, ScoreboardManager> roomScoreboards = new ConcurrentHashMap<>();

    public RoomManager(TerraBoxPlugin plugin) {
        this.plugin = plugin;
    }

    /** 启动/创建默认房间 (绑定当前 arena 世界, 全局单例计分板) */
    public void initDefault() {
        if (rooms.containsKey("default")) return;
        GameManager def = new GameManager(plugin, "default", null);
        rooms.put("default", def);
        roomOrder.addIfAbsent("default");
    }

    /** 创建/获取指定房间 (绑定 worldName), 若不存在则创建 */
    public GameManager createRoom(String id, String worldName) {
        String key = id.toLowerCase(java.util.Locale.ROOT);
        GameManager existing = rooms.get(key);
        if (existing != null) return existing;
        GameManager g = new GameManager(plugin, key, worldName);
        rooms.put(key, g);
        roomOrder.addIfAbsent(key);
        // 每个房间独立计分板 (仅非默认房间; 默认用全局单例)
        if (!"default".equals(key)) {
            ScoreboardManager sb = new ScoreboardManager(plugin);
            roomScoreboards.put(key, sb);
            g.setRoomScoreboard(sb);
        }
        return g;
    }

    public List<String> roomIds() { return List.copyOf(roomOrder); }
    public GameManager get(String id) { return rooms.get(id == null ? "default" : id.toLowerCase(java.util.Locale.ROOT)); }
    public GameManager defaultRoom() { return get("default"); }

    public boolean hasRoom(String id) { return rooms.containsKey(id == null ? "default" : id.toLowerCase(java.util.Locale.ROOT)); }
    public void removeRoom(String id) {
        String key = id == null ? "default" : id.toLowerCase(java.util.Locale.ROOT);
        GameManager g = rooms.get(key);
        if (g != null && g.isRunning()) return; // 运行中不可删除
        GameManager removed = rooms.remove(key);
        if (removed != null) {
            roomOrder.remove(key);
            ScoreboardManager sb = roomScoreboards.remove(key);
            if (sb != null) sb.shutdown();
        }
    }

    /** 玩家所在房间 (加入过的房间中任一, 含淘汰玩家), 无则 default */
    public GameManager roomOf(UUID uuid) {
        for (GameManager g : rooms.values()) {
            if (g != null && (g.isInGame(uuid) || g.playersSet().contains(uuid) || g.eliminatedSet().contains(uuid))) {
                return g;
            }
        }
        return defaultRoom();
    }

    /** 玩家已报名的房间列表 (可能多个) */
    public List<GameManager> joinedRooms(UUID uuid) {
        List<GameManager> out = new java.util.ArrayList<>();
        for (GameManager g : rooms.values()) {
            if (g != null && g.isInGame(uuid)) out.add(g);
        }
        return out;
    }

    /** 房间内在线玩家名单 (状态显示/邀请) */
    public List<Player> onlinePlayersIn(String roomId) {
        GameManager g = get(roomId);
        if (g == null) return List.of();
        List<Player> out = new java.util.ArrayList<>();
        for (UUID u : g.inGamePlayers()) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && p.isOnline()) out.add(p);
        }
        return out;
    }

    /** 房间创建者 */
    public UUID ownerOf(String roomId) {
        GameManager g = get(roomId);
        return g == null ? null : g.owner();
    }

    // ==================== 兼容转发 (转到默认房间) ====================

    private GameManager def() { return defaultRoom(); }

    public boolean isRunning() {
        for (GameManager g : rooms.values()) if (g != null && g.isRunning()) return true;
        return false;
    }
    public boolean isInGame(UUID uuid) {
        for (GameManager g : rooms.values()) if (g.isInGame(uuid)) return true;
        return false;
    }
    public boolean isEliminated(UUID uuid) {
        for (GameManager g : rooms.values()) if (g.isEliminated(uuid)) return true;
        return false;
    }
    public GameManager.State state() { return def().state(); }
    public GameManager.Mode mode() { return def().mode(); }
    public int joinedCount() { return def().joinedCount(); }
    public int playerCount() { return def().playerCount(); }
    public int aliveCount() { return def().aliveCount(); }
    public List<UUID> inGamePlayers() { return def().inGamePlayers(); }
    public long endAtMs() { return def().endAtMs(); }
    public int countdownLeft() { return def().countdownLeft(); }
    public String modeDisplay() { return def().modeDisplay(); }
    public String stateDisplay() { return def().stateDisplay(); }

    public void toggleJoin(Player p) { def().toggleJoin(p); }
    public void startGame(org.bukkit.command.CommandSender sender, GameManager.Mode m) {
        // 若在非默认房间开赛, 交给对应房间; 否则默认房间
        def().startGame(sender, m);
    }
    public void stopGame(org.bukkit.command.CommandSender sender) { def().stopGame(sender); }

    /** 玩家所在房间执行传送大厅 (玩家可能在与默认房间不同的房间) */
    public void sendToLobby(Player p) {
        GameManager g = roomOf(p.getUniqueId());
        g.sendToLobby(p);
    }
    /** 玩家所在房间执行旁观 */
    public void spectate(Player p) {
        GameManager g = roomOf(p.getUniqueId());
        g.spectate(p);
    }
    /** 死亡淘汰: 转发到玩家【实际所在房间】(而非默认房间) — 修复非默认房间对局淘汰判定失效 */
    public void onPlayerDeath(Player victim, Player killer) {
        GameManager g = roomOf(victim.getUniqueId());
        g.onPlayerDeath(victim, killer);
    }
    /** 退出: 转发到玩家实际所在房间 */
    public void onPlayerQuit(UUID u) {
        GameManager g = roomOf(u);
        g.onPlayerQuit(u);
    }
    /** PvP 伤害判定: 用受害玩家所在房间 */
    public boolean canDamage(Player damager, Player victim) {
        GameManager g = roomOf(victim.getUniqueId());
        return g.canDamage(damager, victim);
    }

    /** 玩家所在房间的自动旁观 (淘汰后) */
    public void autoSpectateAfterDeath(Player p) {
        GameManager g = roomOf(p.getUniqueId());
        g.autoSpectateAfterDeath(p);
    }
    /** 玩家所在房间的返回大厅判定 */
    public boolean requestReturnToLobby(Player p) {
        GameManager g = roomOf(p.getUniqueId());
        return g.requestReturnToLobby(p);
    }
}
