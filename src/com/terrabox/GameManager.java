package com.terrabox;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 对局玩法 (吃鸡式资源争夺, PVP):
 *  - 模式: SOLO 单人(无PvP, 时间制积分结算, 管理员可开) / PVP 玩家对战(淘汰制) / TEAM 组队对战
 *  - 状态机: IDLE → COUNTDOWN(倒计时) → RUNNING(进行) → ENDING(结算) → 自动恢复地形 → IDLE
 *  - 计分板: 存活数/击杀/死亡/开箱/剩余时间实时统计 (ScoreboardManager)
 *  - 淘汰: 被淘汰玩家可回大厅或旁观 (观战)
 *  - 结束: 倒计时结束 / 仅剩1人 → 自动送剩余玩家回大厅
 *  - 结束自动恢复地形: 清空重放箱子 + 重建建筑/出生广场
 *
 * 线程模型 (白皮书 §4/§5/§6):
 *  - 集合全部并发安全; 倒计时/结束检测 GlobalRegionScheduler 每秒
 *  - 玩家传送 teleportAsync; 状态复位 EntityScheduler
 */
public class GameManager {
    public enum Mode {
        SOLO("单人模式", "§a"),
        PVP("玩家对战", "§c"),
        TEAM("组队对战", "§6");
        public final String display;
        public final String color;
        Mode(String display, String color) {
            this.display = display;
            this.color = color;
        }
        public static Mode parse(String s) {
            for (Mode m : values()) {
                if (m.name().equalsIgnoreCase(s) || m.display.equals(s)) return m;
            }
            return null;
        }
    }

    public enum State {
        IDLE("空闲", "§7"),
        COUNTDOWN("准备中", "§e"),
        RUNNING("进行中", "§a"),
        ENDING("结算中", "§d");
        public final String display;
        public final String color;
        State(String display, String color) {
            this.display = display;
            this.color = color;
        }
    }

    private final TerraBoxPlugin plugin;
    // 房间绑定: 每个房间绑定一个 arena 世界名 + 独立计分板 (多房间并存)
    private final String roomId;
    private final String roomWorldName;
    private volatile UUID owner; // 房间创建者 (可邀请玩家), null=系统/管理员房间
    private volatile ScoreboardManager roomScoreboard; // 独立计分板 (多房间), null 则用全局单例
    private volatile State state = State.IDLE;
    private volatile Mode mode = Mode.SOLO;
    private final CopyOnWriteArraySet<UUID> joined = new CopyOnWriteArraySet<>();
    private final CopyOnWriteArraySet<UUID> players = new CopyOnWriteArraySet<>();
    private final CopyOnWriteArraySet<UUID> eliminated = new CopyOnWriteArraySet<>();
    private final Map<UUID, Integer> teams = new ConcurrentHashMap<>();
    private final List<UUID> joinOrder = new java.util.concurrent.CopyOnWriteArrayList<>();
    private volatile long endAt = 0;
    private volatile long startAt = 0;          // 对局正式开始时刻 (用于10分钟追踪器发放)
    private volatile boolean trackerGiven = false; // 是否已发放过追踪器
    private volatile int countdownLeft = 0;
    private volatile String currentWinner = null;
    private ScheduledTask tickTask;
    // 毒圈 (每个房间一个实例, 对局运行中激活)
    private final ZoneManager storm;

    public GameManager(TerraBoxPlugin plugin) {
        this(plugin, "default", null);
    }

    /** 多房间构造: 绑定世界名 + 独立计分板 (roomScoreboard null 则用全局单例) */
    public GameManager(TerraBoxPlugin plugin, String roomId, String roomWorldName) {
        this.plugin = plugin;
        this.roomId = roomId;
        this.roomWorldName = roomWorldName;
        this.storm = new ZoneManager(plugin, this);
    }

    public ZoneManager storm() { return storm; }

    public String roomId() { return roomId; }
    public String roomWorldName() { return roomWorldName; }

    /** 本房间绑定的对局世界 (绑定则用之, 否则回退当前 arena 世界) */
    public World roomWorld() {
        if (roomWorldName != null) {
            World w = Bukkit.getWorld(roomWorldName);
            if (w != null) return w;
        }
        return plugin.worlds().world();
    }

    /** 本房间使用的计分板 (独立实例 / 全局单例) */
    public ScoreboardManager myScoreboard() {
        return roomScoreboard != null ? roomScoreboard : plugin.scoreboard();
    }

    /** 设置独立计分板 (多房间由 RoomManager 注入) */
    public void setRoomScoreboard(ScoreboardManager sb) { this.roomScoreboard = sb; }

    public State state() { return state; }
    public Mode mode() { return mode; }
    public boolean isRunning() { return state == State.RUNNING || state == State.COUNTDOWN; }
    public boolean isInGame(UUID uuid) { return players.contains(uuid) && !eliminated.contains(uuid); }
    public boolean isEliminated(UUID uuid) { return eliminated.contains(uuid); }
    public int joinedCount() { return joined.size(); }
    public int playerCount() { return players.size(); }
    public java.util.Set<UUID> playersSet() { return java.util.Collections.unmodifiableSet(players); }
    public java.util.Set<UUID> eliminatedSet() { return java.util.Collections.unmodifiableSet(eliminated); }
    public long endAtMs() { return endAt; }
    public int countdownLeft() { return Math.max(0, countdownLeft); }
    public List<UUID> inGamePlayers() { return List.copyOf(players); }

    public String stateDisplay() { return state.color + state.display; }
    public String modeDisplay() { return mode.color + mode.display; }

    public int aliveCount() {
        int alive = 0;
        for (UUID u : players) {
            if (!eliminated.contains(u)) alive++;
        }
        return alive;
    }

    private java.util.Set<Integer> aliveTeams() {
        java.util.Set<Integer> set = new java.util.HashSet<>();
        for (UUID u : players) {
            if (!eliminated.contains(u)) {
                Integer t = teams.get(u);
                if (t != null) set.add(t);
            }
        }
        return set;
    }

    // ==================== 报名 ====================

    public void toggleJoin(Player p) {
        if (state == State.RUNNING || state == State.ENDING) {
            p.sendMessage("§c对局正在进行中, 无法报名。");
            return;
        }
        UUID u = p.getUniqueId();
        if (joined.contains(u)) {
            joined.remove(u);
            joinOrder.remove(u);
            players.remove(u);
            eliminated.remove(u);
            p.sendMessage("§c已退出报名。");
        } else {
            joined.add(u);
            joinOrder.add(u);
            players.add(u);
            eliminated.remove(u);
            p.sendMessage("§a报名成功! 当前报名人数: §e" + joined.size()
                    + "§a, 等管理员开始对局 (模式: " + modeDisplay() + ")");
            playSound(p, Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.6f);
        }
    }

    /** 无条件加入报名 (用于接受邀请/强制加入); @return 是否成功 */
    public boolean join(Player p) {
        if (state == State.RUNNING || state == State.ENDING) {
            p.sendMessage("§c对局正在进行中, 无法加入。");
            return false;
        }
        UUID u = p.getUniqueId();
        if (joined.contains(u)) return true;
        joined.add(u);
        joinOrder.add(u);
        players.add(u);
        eliminated.remove(u);
        p.sendMessage("§a已加入房间 §e" + roomId + " §a报名! 当前报名人数: §e"
                + joined.size() + "§a, 等管理员开始 (模式: " + modeDisplay() + ")");
        playSound(p, Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.6f);
        return true;
    }

    /** 退出报名 (接受邀请/自由退出), 若非报名成员返回 false */
    public boolean leave(Player p) {
        UUID u = p.getUniqueId();
        if (!joined.contains(u)) return false;
        joined.remove(u);
        joinOrder.remove(u);
        players.remove(u);
        eliminated.remove(u);
        p.sendMessage("§c已退出房间 §e" + roomId + " §c报名。");
        return true;
    }

    /** 房间创建者 */
    public UUID owner() { return owner; }
    public void setOwner(UUID owner) { this.owner = owner; }
    public boolean isOwner(UUID u) { return owner != null && owner.equals(u); }

    // ==================== 开始 / 停止 ====================

    public void startGame(org.bukkit.command.CommandSender sender, Mode m) {
        if (state == State.COUNTDOWN || state == State.RUNNING) {
            sender.sendMessage("§c已有对局在进行中 (" + stateDisplay() + ")");
            return;
        }
        this.mode = m;
        if (joined.isEmpty()) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                joined.add(p.getUniqueId());
                joinOrder.add(p.getUniqueId());
            }
        }
        players.clear();
        players.addAll(joined);
        eliminated.clear();
        teams.clear();
        myScoreboard().resetStats();

        // 单人模式: 管理元可开 (不限人数); 对战/组队需至少 2 人
        int minPlayers = Math.max(2, plugin.getConfig().getInt("game.min-players", 2));
        if (players.size() < 1) {
            sender.sendMessage("§c参战人数不足, 请先 /box game join 报名。");
            joined.clear();
            joinOrder.clear();
            players.clear();
            return;
        }
        if (mode != Mode.SOLO && players.size() < minPlayers) {
            sender.sendMessage("§c参战人数不足 (" + players.size() + "/" + minPlayers
                    + "), 对战/组队模式至少需 2 人。");
            joined.clear();
            joinOrder.clear();
            players.clear();
            return;
        }
        if (mode == Mode.TEAM) {
            int teamCount = Math.max(2, plugin.getConfig().getInt("game.team-count", 2));
            int i = 0;
            for (UUID u : joinOrder) {
                teams.put(u, i % teamCount);
                i++;
            }
        }
        countdownLeft = Math.max(5, plugin.getConfig().getInt("game.countdown-seconds", 30));
        state = State.COUNTDOWN;
        currentWinner = null;
        // 报名玩家传送到世界出生点等待 (对局世界)
        Bukkit.broadcast(plugin.component("game-start",
                "{mode}", mode.display, "{count}", String.valueOf(players.size()),
                "{seconds}", String.valueOf(countdownLeft)));
        tickTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> tick(), 20L, 20L);
        startScoreboard();
    }

    private void startScoreboard() {
        // 计分板启动 (ScoreboardManager.updateAll 每秒钟更新)
        if (myScoreboard() != null) {
            myScoreboard().start();
        }
    }

    public void stopGame(org.bukkit.command.CommandSender sender) {
        if (state == State.IDLE) {
            sender.sendMessage("§c当前没有进行中的对局。");
            return;
        }
        sender.sendMessage("§e管理员终止对局, 正在恢复地形...");
        finish("管理员终止对局", null, List.of());
    }

    private void tick() {
        if (state == State.COUNTDOWN) {
            if (countdownLeft <= 0) {
                startRunning();
            } else {
                if (countdownLeft <= 5 || countdownLeft % 5 == 0) {
                    Bukkit.broadcast(plugin.component("game-countdown", "{seconds}", String.valueOf(countdownLeft)));
                    // 倒计时报数音效 (全服)
                    Bukkit.getGlobalRegionScheduler().run(plugin, t ->
                            Bukkit.getOnlinePlayers().forEach(p ->
                                    playSound(p, countdownLeft <= 3 ? Sound.BLOCK_NOTE_BLOCK_PLING
                                            : Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f,
                                            countdownLeft <= 3 ? 2.0f : 1.0f)));
                }
                // 屏幕大屏倒计时 (Title): 5 秒内每秒大屏显示, 其余显示 boss bar 数字
                if (countdownLeft <= 5) {
                    final int cd = countdownLeft;
                    Bukkit.getGlobalRegionScheduler().run(plugin, t ->
                            Bukkit.getOnlinePlayers().forEach(p -> {
                                if (players.contains(p.getUniqueId())) {
                                    myScoreboard().showTitle(p,
                                            (cd <= 3 ? "§c§l" : "§6§l") + cd,
                                            "§7对局即将开始");
                                }
                            }));
                }
                countdownLeft--;
            }
            return;
        }
        if (state == State.RUNNING) {
            // 对局超过10分钟: 自动给每个存活玩家发放一枚追踪器 (只能发一次)
            maybeGrantTracker();
            if (mode == Mode.SOLO) {
                if (System.currentTimeMillis() >= endAt) {
                    UUID winner = null;
                    long best = -1;
                    for (UUID u : players) {
                        if (eliminated.contains(u)) continue;
                        long score = plugin.players().getOrCreate(u, null).openedTotal()
                                + myScoreboard().getKills(u) * 5L;
                        if (score > best) { best = score; winner = u; }
                    }
                    String name = winner != null ? plugin.players().getOrCreate(winner, null).name : "?";
                    finish("对局时间结束", name + " §e以 §a" + best + " §e分夺冠!", winner != null ? List.of(winner) : List.of());
                }
            } else {
                // 所有对战模式共享: 时间到 (若配置了 timeout) 则提前结算; 否则按淘汰制
                if (System.currentTimeMillis() >= endAt && !plugin.getConfig().getBoolean("game.no-timeout", false)) {
                    // 时间到: 存活多人时按击杀数选冠军
                    UUID winner = null; int bestKills = -1;
                    for (UUID u : players) {
                        if (eliminated.contains(u)) continue;
                        int k = myScoreboard().getKills(u);
                        if (k > bestKills) { bestKills = k; winner = u; }
                    }
                    String name = winner != null ? plugin.players().getOrCreate(winner, null).name : "?";
                    finish("对局时间结束", name + " §e以 §a" + bestKills + " §e击杀夺冠!", winner != null ? List.of(winner) : List.of());
                    return;
                }
                if (mode == Mode.TEAM) {
                    var aliveTeams = aliveTeams();
                    if (aliveTeams.size() <= 1) {
                        List<UUID> winners = new ArrayList<>();
                        for (UUID u : players) {
                            if (!eliminated.contains(u)) winners.add(u);
                        }
                        StringBuilder names = new StringBuilder();
                        for (UUID u : winners) {
                            if (names.length() > 0) names.append("§7、");
                            names.append("§a").append(plugin.players().getOrCreate(u, null).name);
                        }
                        finish("队伍对决结束", names + " §e所在的队伍获胜!", winners);
                    }
                } else {
                    // 仅剩1人 → 自动结束并送回大厅
                    if (aliveCount() <= 1) {
                        UUID winner = null;
                        for (UUID u : players) {
                            if (!eliminated.contains(u)) { winner = u; break; }
                        }
                        String name = winner != null ? plugin.players().getOrCreate(winner, null).name : "?";
                        finish("对战结束", name + " §e是最后的幸存者!", winner != null ? List.of(winner) : List.of());
                    }
                }
            }
        }
    }

    /** 对局超过指定分钟(默认10)自动给每个存活玩家发放追踪器 (只发一次), 促进追击 */
    private void maybeGrantTracker() {
        if (trackerGiven) return;
        long grantAfterMs = Math.max(1, plugin.getConfig().getLong("game.tracker-grant-minutes", 10)) * 60_000L;
        long elapsed = System.currentTimeMillis() - startAt;
        if (elapsed < grantAfterMs) return;
        trackerGiven = true;
        if (plugin.specialItems() == null) return;
        for (UUID u : players) {
            if (eliminated.contains(u)) continue;
            Player p = Bukkit.getPlayer(u);
            if (p == null || !p.isOnline()) continue;
            // 给存活玩家背包加一枚追踪罗盘 (enemy_tracker)
            p.getScheduler().run(plugin, task -> {
                try {
                    var it = plugin.specialItems().buildItem("enemy_tracker");
                    if (it != null) {
                        p.getInventory().addItem(it);
                        p.sendMessage("§e对局已进行 §a" + (grantAfterMs / 60_000L) + " §e分钟! §f发放 §a追踪罗盘 §f×1, "
                                + "右键锁定一名敌人以追缉其方位!");
                        p.playSound(p.getLocation(), Sound.ITEM_TRIDENT_RETURN, 1.0f, 1.6f);
                    }
                } catch (Throwable ignored) {}
            }, () -> {});
        }
        Bukkit.broadcast(plugin.component("game-tracker-grant",
                "{minutes}", String.valueOf(grantAfterMs / 60_000L)));
    }

    private void startRunning() {
        state = State.RUNNING;
        long durationMs = Math.max(1, plugin.getConfig().getLong("game.duration-minutes", 30)) * 60_000L;
        endAt = System.currentTimeMillis() + durationMs;
        startAt = System.currentTimeMillis();
        trackerGiven = false;
        World w = roomWorld();
        if (w != null) {
            w.setPVP(mode != Mode.SOLO);
        }
        // 启动毒圈 (缩圈机制, 吃鸡核心玩法; SOLO 时间制不启用)
        if (mode != Mode.SOLO) storm.start();
        final List<UUID> order = new ArrayList<>(players);
        for (int i = 0; i < order.size(); i++) {
            UUID u = order.get(i);
            Player p = Bukkit.getPlayer(u);
            if (p != null && p.isOnline()) {
                // 对局开始玩家分散到地图各处随机出生点 (吃鸡落地, 搭配遮挡地形)
                plugin.spawns().randomLand(l -> p.teleportAsync(l), () -> {});
                // 清除对局玩家状态: 满血/满饱/清效果/生存模式 + 清空背包 (吃鸡从零开始, 公平)
                p.getScheduler().run(plugin, task -> {
                    try { p.getInventory().clear(); } catch (Throwable ignored) {}
                    p.setHealth(20.0);
                    p.setFoodLevel(20);
                    p.setSaturation(10f);
                    p.clearActivePotionEffects();
                    if (p.getGameMode() != GameMode.SURVIVAL) p.setGameMode(GameMode.SURVIVAL);
                }, () -> {});
            }
        }
        myScoreboard().start();
        Bukkit.broadcast(plugin.component("game-running", "{mode}", mode.display,
                "{minutes}", String.valueOf(plugin.getConfig().getLong("game.duration-minutes", 30))));
        // 对局开始音效: 全服大气音 + 参战者升级音
        Bukkit.getGlobalRegionScheduler().run(plugin, t ->
                Bukkit.getOnlinePlayers().forEach(p -> {
                    playSound(p, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                    if (players.contains(p.getUniqueId())) {
                        p.getScheduler().runDelayed(plugin, task ->
                                playSound(p, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.6f),
                                () -> {}, 10L);
                    }
                }));
    }

    // ==================== 死亡 / 淘汰 ====================

    /** 玩家死亡 (玩家区域线程): PVP/TEAM 淘汰制; 记录击杀; 淘汰可回大厅或旁观 */
    public void onPlayerDeath(Player victim, Player killer) {
        if (state != State.RUNNING) return;
        UUID u = victim.getUniqueId();

        // 如果已淘汰则不重复处理
        if (eliminated.contains(u)) return;

        // 记录击杀数 (如果有击杀者且仍在游戏中)
        if (killer != null && killer != victim && players.contains(killer.getUniqueId())
                && !eliminated.contains(killer.getUniqueId())) {
            myScoreboard().recordKill(killer.getUniqueId());
        }
        // 记录死亡数
        myScoreboard().recordDeath(u);

        if (!players.contains(u)) return;
        if (mode == Mode.SOLO) return; // 单人模式不淘汰

        // 标记为淘汰
        eliminated.add(u);
        myScoreboard().clearPlayer(u);
        Bukkit.broadcast(plugin.component("game-eliminated", "{player}", victim.getName(),
                "{alive}", String.valueOf(aliveCount())));
        // 淘汰音效: 受害者受伤音 + 全服低沉提示
        victim.getScheduler().run(plugin, task -> playSound(victim, Sound.ENTITY_PLAYER_HURT, 1.0f, 0.8f), () -> {});
        broadcastSound(Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.6f);
    }

    /** PvP 伤害检查 (区域线程): SOLO 禁玩家互伤; TEAM 同队免伤; 淘汰玩家不能伤害他人 */
    public boolean canDamage(Player damager, Player victim) {
        if (mode == Mode.SOLO) return false;
        // 淘汰玩家不能伤害他人
        if (eliminated.contains(damager.getUniqueId())) return false;
        // 被伤害者必须是存活玩家
        if (eliminated.contains(victim.getUniqueId())) return false;
        if (mode == Mode.TEAM) {
            Integer a = teams.get(damager.getUniqueId());
            Integer b = teams.get(victim.getUniqueId());
            if (a != null && a.equals(b)) return false;
        }
        return true;
    }

    /** 淘汰玩家: 提供回大厅 / 旁观选择 (被淘汰区域线程) */
    private void offerSpectateOrLobby(Player p) {
        if (!p.isOnline()) return;
        // 已满血并进入旁观, 不再需要此方法
    }

    /** 传送玩家到大厅 */
    public void sendToLobby(Player p) {
        World lobby = plugin.worlds().lobby();
        if (lobby != null) {
            // 加入大厅: 自动清理背包数据 (对局搜刮的战利品不带出)
            p.getScheduler().run(plugin, task -> {
                try { p.getInventory().clear(); } catch (Throwable ignored) {}
            }, () -> {});
            p.teleportAsync(plugin.lobbyBuilder().spawnLocation());
            p.getScheduler().run(plugin, task -> {
                if (p.getGameMode() != GameMode.SURVIVAL) p.setGameMode(GameMode.SURVIVAL);
                p.setHealth(20.0);
                p.setFoodLevel(20);
                p.clearActivePotionEffects();
            }, () -> {});
        }
    }

    /** 玩家当前是否允许主动返回大厅 (对局进行中且是参战未淘汰玩家 → 禁止) */
    public boolean canLeaveToLobby(Player p) {
        if (state == State.RUNNING || state == State.COUNTDOWN) {
            // 参战且未淘汰玩家禁止主动回大厅 (防逃跑); 淘汰/旁观/非参战允许
            if (isInGame(p.getUniqueId())) return false;
        }
        return true;
    }

    /**
     * 主动返回大厅 (命令/GUI 用): 若对局中且参战未淘汰则阻止并提示, 否则传送
     * @return true 表示已执行传送, false 表示被阻止
     */
    public boolean requestReturnToLobby(Player p) {
        if (!canLeaveToLobby(p)) {
            p.sendMessage("§c对局进行中, 不能返回大厅! " + (mode == Mode.SOLO
                    ? "" : "§7(被淘汰后可用 §e/box lobby §7回大厅)"));
            return false;
        }
        sendToLobby(p);
        return true;
    }

    /** 观战: 设为旁观模式, 继续观战 (被淘汰区域线程) */
    public void spectate(Player p) {
        World w = roomWorld();
        if (w != null && state == State.RUNNING) {
            p.getScheduler().run(plugin, task -> {
                if (p.getGameMode() != GameMode.SPECTATOR) p.setGameMode(GameMode.SPECTATOR);
            }, () -> {});
        }
    }

    /** 玩家死亡重生后: 若已淘汰则自动进入旁观 (留在死亡位置附近观战, 不送大厅) */
    public void autoSpectateAfterDeath(Player p) {
        // 已在 onDeath 中直接切换到旁观, 此方法保留但不再需要
    }

    private World mainWorld() {
        World lobby = plugin.worlds().lobby();
        if (lobby != null) return lobby;
        World arena = roomWorld();
        if (arena != null) return arena;
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
    }

    // ==================== 结算与自动恢复地形 ====================

    private void finish(String reason, String winnerText, List<UUID> winners) {
        if (state == State.IDLE) return;
        state = State.ENDING;
        currentWinner = winnerText;
        if (tickTask != null) { tickTask.cancel(); tickTask = null; }
        storm.stop(); // 结束对局, 停止毒圈
        // 清除所有玩家追踪状态 (对局结束)
        if (plugin.specialItems() != null) {
            Bukkit.getGlobalRegionScheduler().run(plugin, t ->
                    plugin.specialItems().trackingPlayers().forEach(u -> plugin.specialItems().stopTracking(u)));
        }
        double reward = plugin.getConfig().getDouble("game.win-reward", 1000.0);
        StringBuilder sb = new StringBuilder();
        sb.append(plugin.raw("game-end")).append(" §7[").append(mode.color).append(mode.display).append("§7] ")
                .append("§f").append(reason).append(" §7胜者: ").append(winnerText != null ? winnerText : "无");
        Bukkit.broadcast(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacyAmpersand().deserialize(sb.toString()));
        // 对局结束音效: 全服号角 + 胜者专属
        broadcastSound(Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 0.7f);
        for (UUID u : winners) {
            Player wp = Bukkit.getPlayer(u);
            if (wp != null && wp.isOnline()) {
                wp.getScheduler().run(plugin, task ->
                        playSound(wp, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f), () -> {});
            }
        }
        if (reward > 0) {
            for (UUID u : winners) {
                org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(u);
                plugin.econ().deposit(op, reward);
                Player online = op.getPlayer();
                if (online != null && online.isOnline()) {
                    online.sendMessage("§a获胜奖励 §e" + (long) reward + " §a元已发放!");
                }
            }
        }
        // 自动: 把剩余存活玩家送回大厅 (对战结束所有参战者回大厅) + 清空背包
        for (UUID u : players) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && p.isOnline()) {
                final Player fp = p;
                fp.getScheduler().run(plugin, task -> {
                    try { fp.getInventory().clear(); } catch (Throwable ignored) {}
                }, () -> {});
                sendToLobby(fp);
            }
        }
        // 自动恢复地形 (Global 线程)
        Bukkit.getGlobalRegionScheduler().run(plugin, t -> resetWorld());
    }

    /** 自动重置: 箱子清空重投 + 建筑重建 + 掉落清理 (Global 线程) */
    private void resetWorld() {
        storm.stop(); // 重置前确保毒圈停止
        World w = roomWorld();
        if (w != null) {
            w.setPVP(false);
            // 清空所有实体(非玩家): 掉落物/子弹/TNT/建筑实体等, 防止残留
            for (org.bukkit.entity.Entity e : w.getEntities()) {
                if (e instanceof Player) continue;
                e.getScheduler().run(plugin, task -> {
                    try { e.remove(); } catch (Throwable ignored) {}
                }, () -> {});
            }
        }
        plugin.boxes().wipeAll(() -> {
            int initial = plugin.boxInitialFill();
            for (int i = 0; i < initial; i++) {
                plugin.boxes().spawnRandomBox(plugin.weightedPickForWorld(), false, null);
            }
            plugin.spawnArea().build(null);
            // 重建大型物资建筑
            plugin.bigBox().buildRandom(w);
            // 参战玩家复位: 清空背包 + 满血/满饱/清效果/生存模式 (已送大厅)
            for (UUID u : players) {
                Player p = Bukkit.getPlayer(u);
                if (p != null && p.isOnline()) {
                    p.getScheduler().run(plugin, task -> {
                        try { p.getInventory().clear(); } catch (Throwable ignored) {}
                        p.setHealth(20.0);
                        p.setFoodLevel(20);
                        p.setSaturation(10f);
                        p.clearActivePotionEffects();
                        if (p.getGameMode() != GameMode.SURVIVAL) p.setGameMode(GameMode.SURVIVAL);
                    }, () -> {});
                }
            }
            players.clear();
            joined.clear();
            joinOrder.clear();
            eliminated.clear();
            teams.clear();
            trackerGiven = false;
            myScoreboard().clearAll();
            state = State.IDLE;
            currentWinner = null;
            Bukkit.broadcast(plugin.component("game-reset"));
        });
    }

    public void onPlayerQuit(UUID u) {
        if (state == State.RUNNING && players.contains(u) && mode != Mode.SOLO) {
            if (eliminated.add(u)) {
                Player p = Bukkit.getPlayer(u);
                myScoreboard().clearPlayer(u);
                Bukkit.broadcast(plugin.component("game-eliminated",
                        "{player}", p != null ? p.getName() : "?",
                        "{alive}", String.valueOf(aliveCount())));
            }
        }
        players.remove(u);
        joined.remove(u);
        joinOrder.remove(u);
    }

    // ==================== 音效工具 ====================

    /** 给单个玩家播放客户端音效 (任意线程安全, Player#playSound 跨线程可调用) */
    private void playSound(Player p, Sound sound, float vol, float pitch) {
        try {
            if (p != null && p.isOnline()) p.playSound(p.getLocation(), sound, vol, pitch);
        } catch (Throwable ignored) {}
    }

    /** 全服广播音效 (Global 线程调度) */
    private void broadcastSound(Sound sound, float vol, float pitch) {
        Bukkit.getGlobalRegionScheduler().run(plugin, t ->
                Bukkit.getOnlinePlayers().forEach(p -> playSound(p, sound, vol, pitch)));
    }
}
