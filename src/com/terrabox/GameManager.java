/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.papermc.paper.threadedregions.scheduler.ScheduledTask
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
 *  org.bukkit.Bukkit
 *  org.bukkit.GameMode
 *  org.bukkit.OfflinePlayer
 *  org.bukkit.Sound
 *  org.bukkit.World
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.Player
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.plugin.Plugin
 */
package com.terrabox;

import com.terrabox.ScoreboardManager;
import com.terrabox.TerraBoxPlugin;
import com.terrabox.ZoneManager;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public class GameManager {
    private final TerraBoxPlugin plugin;
    private final String roomId;
    private final String roomWorldName;
    private volatile UUID owner;
    private volatile ScoreboardManager roomScoreboard;
    private volatile State state = State.IDLE;
    private volatile Mode mode = Mode.SOLO;
    private final CopyOnWriteArraySet<UUID> joined = new CopyOnWriteArraySet();
    private final CopyOnWriteArraySet<UUID> players = new CopyOnWriteArraySet();
    private final CopyOnWriteArraySet<UUID> eliminated = new CopyOnWriteArraySet();
    private final Map<UUID, Integer> teams = new ConcurrentHashMap<UUID, Integer>();
    private final List<UUID> joinOrder = new CopyOnWriteArrayList<UUID>();
    private volatile long endAt = 0L;
    private volatile long startAt = 0L;
    private volatile boolean trackerGiven = false;
    private volatile int countdownLeft = 0;
    private volatile String currentWinner = null;
    private ScheduledTask tickTask;
    private final ZoneManager storm;

    public GameManager(TerraBoxPlugin terraBoxPlugin) {
        this(terraBoxPlugin, "default", null);
    }

    public GameManager(TerraBoxPlugin terraBoxPlugin, String string, String string2) {
        this.plugin = terraBoxPlugin;
        this.roomId = string;
        this.roomWorldName = string2;
        this.storm = new ZoneManager(terraBoxPlugin, this);
    }

    public ZoneManager storm() {
        return this.storm;
    }

    public String roomId() {
        return this.roomId;
    }

    public String roomWorldName() {
        return this.roomWorldName;
    }

    public World roomWorld() {
        World world;
        if (this.roomWorldName != null && (world = Bukkit.getWorld((String)this.roomWorldName)) != null) {
            return world;
        }
        return this.plugin.worlds().world();
    }

    public ScoreboardManager myScoreboard() {
        return this.roomScoreboard != null ? this.roomScoreboard : this.plugin.scoreboard();
    }

    public void setRoomScoreboard(ScoreboardManager scoreboardManager) {
        this.roomScoreboard = scoreboardManager;
    }

    public State state() {
        return this.state;
    }

    public Mode mode() {
        return this.mode;
    }

    public boolean isRunning() {
        return this.state == State.RUNNING || this.state == State.COUNTDOWN;
    }

    public boolean isInGame(UUID uUID) {
        return this.players.contains(uUID) && !this.eliminated.contains(uUID);
    }

    public boolean isEliminated(UUID uUID) {
        return this.eliminated.contains(uUID);
    }

    public int joinedCount() {
        return this.joined.size();
    }

    public int playerCount() {
        return this.players.size();
    }

    public Set<UUID> playersSet() {
        return Collections.unmodifiableSet(this.players);
    }

    public Set<UUID> eliminatedSet() {
        return Collections.unmodifiableSet(this.eliminated);
    }

    public long endAtMs() {
        return this.endAt;
    }

    public int countdownLeft() {
        return Math.max(0, this.countdownLeft);
    }

    public List<UUID> inGamePlayers() {
        return List.copyOf(this.players);
    }

    public String stateDisplay() {
        return this.state.color + this.state.display;
    }

    public String modeDisplay() {
        return this.mode.color + this.mode.display;
    }

    public int aliveCount() {
        int n = 0;
        for (UUID uUID : this.players) {
            if (this.eliminated.contains(uUID)) continue;
            ++n;
        }
        return n;
    }

    private Set<Integer> aliveTeams() {
        HashSet<Integer> hashSet = new HashSet<Integer>();
        for (UUID uUID : this.players) {
            Integer n;
            if (this.eliminated.contains(uUID) || (n = this.teams.get(uUID)) == null) continue;
            hashSet.add(n);
        }
        return hashSet;
    }

    public void toggleJoin(Player player) {
        if (this.state == State.RUNNING || this.state == State.ENDING) {
            player.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u5bf9\u5c40\u6b63\u5728\u8fdb\u884c\u4e2d, \u65e0\u6cd5\u62a5\u540d\u3002");
            return;
        }
        UUID uUID = player.getUniqueId();
        if (this.joined.contains(uUID)) {
            this.joined.remove(uUID);
            this.joinOrder.remove(uUID);
            this.players.remove(uUID);
            this.eliminated.remove(uUID);
            player.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u5df2\u9000\u51fa\u62a5\u540d\u3002");
        } else {
            this.joined.add(uUID);
            this.joinOrder.add(uUID);
            this.players.add(uUID);
            this.eliminated.remove(uUID);
            player.sendMessage(this.plugin.msg("prefix") + "\u00a7a\u62a5\u540d\u6210\u529f! \u5f53\u524d\u62a5\u540d\u4eba\u6570: \u00a7e" + this.joined.size() + "\u00a7a, \u7b49\u7ba1\u7406\u5458\u5f00\u59cb\u5bf9\u5c40 (\u6a21\u5f0f: " + this.modeDisplay() + ")");
            this.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.6f);
        }
    }

    public boolean join(Player player) {
        if (this.state == State.RUNNING || this.state == State.ENDING) {
            player.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u5bf9\u5c40\u6b63\u5728\u8fdb\u884c\u4e2d, \u65e0\u6cd5\u52a0\u5165\u3002");
            return false;
        }
        UUID uUID = player.getUniqueId();
        if (this.joined.contains(uUID)) {
            return true;
        }
        this.joined.add(uUID);
        this.joinOrder.add(uUID);
        this.players.add(uUID);
        this.eliminated.remove(uUID);
        player.sendMessage(this.plugin.msg("prefix") + "\u00a7a\u5df2\u52a0\u5165\u623f\u95f4 \u00a7e" + this.roomId + " \u00a7a\u62a5\u540d! \u5f53\u524d\u62a5\u540d\u4eba\u6570: \u00a7e" + this.joined.size() + "\u00a7a, \u7b49\u7ba1\u7406\u5458\u5f00\u59cb (\u6a21\u5f0f: " + this.modeDisplay() + ")");
        this.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.6f);
        return true;
    }

    public boolean leave(Player player) {
        UUID uUID = player.getUniqueId();
        if (!this.joined.contains(uUID)) {
            return false;
        }
        this.joined.remove(uUID);
        this.joinOrder.remove(uUID);
        this.players.remove(uUID);
        this.eliminated.remove(uUID);
        player.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u5df2\u9000\u51fa\u623f\u95f4 \u00a7e" + this.roomId + " \u00a7c\u62a5\u540d\u3002");
        return true;
    }

    public UUID owner() {
        return this.owner;
    }

    public void setOwner(UUID uUID) {
        this.owner = uUID;
    }

    public boolean isOwner(UUID uUID) {
        return this.owner != null && this.owner.equals(uUID);
    }

    public void startGame(CommandSender commandSender, Mode mode) {
        if (this.state == State.COUNTDOWN || this.state == State.RUNNING) {
            commandSender.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u5df2\u6709\u5bf9\u5c40\u5728\u8fdb\u884c\u4e2d (" + this.stateDisplay() + ")");
            return;
        }
        this.mode = mode;
        if (this.joined.isEmpty()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                this.joined.add(player.getUniqueId());
                this.joinOrder.add(player.getUniqueId());
            }
        }
        this.players.clear();
        this.players.addAll(this.joined);
        this.eliminated.clear();
        this.teams.clear();
        this.myScoreboard().resetStats();
        int n = Math.max(2, this.plugin.getConfig().getInt("game.min-players", 2));
        if (this.players.size() < 1) {
            commandSender.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u53c2\u6218\u4eba\u6570\u4e0d\u8db3, \u8bf7\u5148 /box game join \u62a5\u540d\u3002");
            this.joined.clear();
            this.joinOrder.clear();
            this.players.clear();
            return;
        }
        if (this.mode != Mode.SOLO && this.players.size() < n) {
            commandSender.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u53c2\u6218\u4eba\u6570\u4e0d\u8db3 (" + this.players.size() + "/" + n + "), \u5bf9\u6218/\u7ec4\u961f\u6a21\u5f0f\u81f3\u5c11\u9700 2 \u4eba\u3002");
            this.joined.clear();
            this.joinOrder.clear();
            this.players.clear();
            return;
        }
        if (this.mode == Mode.TEAM) {
            int n2 = Math.max(2, this.plugin.getConfig().getInt("game.team-count", 2));
            int n3 = 0;
            for (UUID uUID : this.joinOrder) {
                this.teams.put(uUID, n3 % n2);
                ++n3;
            }
        }
        this.countdownLeft = Math.max(5, this.plugin.getConfig().getInt("game.countdown-seconds", 30));
        this.state = State.COUNTDOWN;
        this.currentWinner = null;
        Bukkit.broadcast((Component)this.plugin.component("game-start", "{mode}", this.mode.display, "{count}", String.valueOf(this.players.size()), "{seconds}", String.valueOf(this.countdownLeft)));
        this.tickTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate((Plugin)this.plugin, scheduledTask -> this.tick(), 20L, 20L);
        this.startScoreboard();
    }

    private void startScoreboard() {
        if (this.myScoreboard() != null) {
            this.myScoreboard().start();
        }
    }

    public void stopGame(CommandSender commandSender) {
        if (this.state == State.IDLE) {
            commandSender.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u5f53\u524d\u6ca1\u6709\u8fdb\u884c\u4e2d\u7684\u5bf9\u5c40\u3002");
            return;
        }
        commandSender.sendMessage(this.plugin.msg("prefix") + "\u00a7e\u7ba1\u7406\u5458\u7ec8\u6b62\u5bf9\u5c40, \u6b63\u5728\u6062\u590d\u5730\u5f62...");
        this.finish("\u7ba1\u7406\u5458\u7ec8\u6b62\u5bf9\u5c40", null, List.of());
    }

    private void tick() {
        if (this.state == State.COUNTDOWN) {
            if (this.countdownLeft <= 0) {
                this.startRunning();
            } else {
                if (this.countdownLeft <= 5 || this.countdownLeft % 5 == 0) {
                    Bukkit.broadcast((Component)this.plugin.component("game-countdown", "{seconds}", String.valueOf(this.countdownLeft)));
                    Bukkit.getGlobalRegionScheduler().run((Plugin)this.plugin, scheduledTask -> Bukkit.getOnlinePlayers().forEach(player -> this.playSound((Player)player, this.countdownLeft <= 3 ? Sound.BLOCK_NOTE_BLOCK_PLING : Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, this.countdownLeft <= 3 ? 2.0f : 1.0f)));
                }
                if (this.countdownLeft <= 5) {
                    int n = this.countdownLeft;
                    Bukkit.getGlobalRegionScheduler().run((Plugin)this.plugin, scheduledTask -> Bukkit.getOnlinePlayers().forEach(player -> {
                        if (this.players.contains(player.getUniqueId())) {
                            this.myScoreboard().showTitle((Player)player, (n <= 3 ? "\u00a7c\u00a7l" : "\u00a76\u00a7l") + n, "\u00a77\u5bf9\u5c40\u5373\u5c06\u5f00\u59cb");
                        }
                    }));
                }
                --this.countdownLeft;
            }
            return;
        }
        if (this.state == State.RUNNING) {
            this.maybeGrantTracker();
            if (this.mode == Mode.SOLO) {
                if (System.currentTimeMillis() >= this.endAt) {
                    UUID uUID = null;
                    long l = -1L;
                    for (UUID uUID2 : this.players) {
                        long l2;
                        if (this.eliminated.contains(uUID2) || (l2 = this.plugin.players().getOrCreate(uUID2, null).openedTotal() + (long)this.myScoreboard().getKills(uUID2) * 5L) <= l) continue;
                        l = l2;
                        uUID = uUID2;
                    }
                    String uUID3 = uUID != null ? this.plugin.players().getOrCreate(uUID, null).name : "?";
                    this.finish("\u5bf9\u5c40\u65f6\u95f4\u7ed3\u675f", uUID3 + " \u00a7e\u4ee5 \u00a7a" + l + " \u00a7e\u5206\u593a\u51a0!", uUID != null ? List.of(uUID) : List.of());
                }
            } else {
                if (System.currentTimeMillis() >= this.endAt && !this.plugin.getConfig().getBoolean("game.no-timeout", false)) {
                    UUID uUID = null;
                    int n = -1;
                    for (UUID object : this.players) {
                        int n2;
                        if (this.eliminated.contains(object) || (n2 = this.myScoreboard().getKills(object)) <= n) continue;
                        n = n2;
                        uUID = object;
                    }
                    String uUID4 = uUID != null ? this.plugin.players().getOrCreate(uUID, null).name : "?";
                    this.finish("\u5bf9\u5c40\u65f6\u95f4\u7ed3\u675f", (String)uUID4 + " \u00a7e\u4ee5 \u00a7a" + n + " \u00a7e\u51fb\u6740\u593a\u51a0!", uUID != null ? List.of(uUID) : List.of());
                    return;
                }
                if (this.mode == Mode.TEAM) {
                    Set<Integer> set = this.aliveTeams();
                    if (set.size() <= 1) {
                        ArrayList<UUID> arrayList = new ArrayList<UUID>();
                        for (UUID uUID : this.players) {
                            if (this.eliminated.contains(uUID)) continue;
                            arrayList.add(uUID);
                        }
                        StringBuilder stringBuilder = new StringBuilder();
                        for (UUID uUID : arrayList) {
                            if (stringBuilder.length() > 0) {
                                stringBuilder.append("\u00a77\u3001");
                            }
                            stringBuilder.append("\u00a7a").append(this.plugin.players().getOrCreate((UUID)uUID, null).name);
                        }
                        this.finish("\u961f\u4f0d\u5bf9\u51b3\u7ed3\u675f", String.valueOf(stringBuilder) + " \u00a7e\u6240\u5728\u7684\u961f\u4f0d\u83b7\u80dc!", arrayList);
                    }
                } else if (this.aliveCount() <= 1) {
                    UUID uUID = null;
                    for (UUID uUID3 : this.players) {
                        if (this.eliminated.contains(uUID3)) continue;
                        uUID = uUID3;
                        break;
                    }
                    String string = uUID != null ? this.plugin.players().getOrCreate(uUID, null).name : "?";
                    this.finish("\u5bf9\u6218\u7ed3\u675f", (String)string + " \u00a7e\u662f\u6700\u540e\u7684\u5e78\u5b58\u8005!", uUID != null ? List.of(uUID) : List.of());
                }
            }
        }
    }

    private void maybeGrantTracker() {
        if (this.trackerGiven) {
            return;
        }
        long l = Math.max(1L, this.plugin.getConfig().getLong("game.tracker-grant-minutes", 10L)) * 60000L;
        long l2 = System.currentTimeMillis() - this.startAt;
        if (l2 < l) {
            return;
        }
        this.trackerGiven = true;
        if (this.plugin.specialItems() == null) {
            return;
        }
        for (UUID uUID : this.players) {
            Player player;
            if (this.eliminated.contains(uUID) || (player = Bukkit.getPlayer((UUID)uUID)) == null || !player.isOnline()) continue;
            player.getScheduler().run((Plugin)this.plugin, scheduledTask -> {
                try {
                    ItemStack itemStack = this.plugin.specialItems().buildItem("enemy_tracker");
                    if (itemStack != null) {
                        player.getInventory().addItem(new ItemStack[]{itemStack});
                        player.sendMessage(this.plugin.msg("prefix") + "\u00a7e\u5bf9\u5c40\u5df2\u8fdb\u884c \u00a7a" + l / 60000L + " \u00a7e\u5206\u949f! \u00a7f\u53d1\u653e \u00a7a\u8ffd\u8e2a\u7f57\u76d8 \u00a7f\u00d71, \u53f3\u952e\u9501\u5b9a\u4e00\u540d\u654c\u4eba\u4ee5\u8ffd\u7f09\u5176\u65b9\u4f4d!");
                        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RETURN, 1.0f, 1.6f);
                    }
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
            }, () -> {});
        }
        Bukkit.broadcast((Component)this.plugin.component("game-tracker-grant", "{minutes}", String.valueOf(l / 60000L)));
    }

    private void startRunning() {
        this.state = State.RUNNING;
        long l = Math.max(1L, this.plugin.getConfig().getLong("game.duration-minutes", 30L)) * 60000L;
        this.endAt = System.currentTimeMillis() + l;
        this.startAt = System.currentTimeMillis();
        this.trackerGiven = false;
        World world = this.roomWorld();
        if (world != null) {
            world.setPVP(this.mode != Mode.SOLO);
        }
        if (this.mode != Mode.SOLO) {
            this.storm.start();
        }
        ArrayList<UUID> arrayList = new ArrayList<UUID>(this.players);
        for (int i = 0; i < arrayList.size(); ++i) {
            UUID uUID = (UUID)arrayList.get(i);
            Player player = Bukkit.getPlayer((UUID)uUID);
            if (player == null || !player.isOnline()) continue;
            this.plugin.spawns().randomLand(location -> player.teleportAsync(location), () -> {});
            player.getScheduler().run((Plugin)this.plugin, scheduledTask -> {
                try {
                    player.getInventory().clear();
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
                player.setHealth(20.0);
                player.setFoodLevel(20);
                player.setSaturation(10.0f);
                player.clearActivePotionEffects();
                if (player.getGameMode() != GameMode.SURVIVAL) {
                    player.setGameMode(GameMode.SURVIVAL);
                }
            }, () -> {});
        }
        this.myScoreboard().start();
        Bukkit.broadcast((Component)this.plugin.component("game-running", "{mode}", this.mode.display, "{minutes}", String.valueOf(this.plugin.getConfig().getLong("game.duration-minutes", 30L))));
        Bukkit.getGlobalRegionScheduler().run((Plugin)this.plugin, scheduledTask -> Bukkit.getOnlinePlayers().forEach(player -> {
            this.playSound((Player)player, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            if (this.players.contains(player.getUniqueId())) {
                player.getScheduler().runDelayed((Plugin)this.plugin, scheduledTask -> this.playSound((Player)player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.6f), () -> {}, 10L);
            }
        }));
    }

    public void onPlayerDeath(Player player, Player player2) {
        if (this.state != State.RUNNING) {
            return;
        }
        UUID uUID = player.getUniqueId();
        if (this.eliminated.contains(uUID)) {
            return;
        }
        if (player2 != null && player2 != player && this.players.contains(player2.getUniqueId()) && !this.eliminated.contains(player2.getUniqueId())) {
            this.myScoreboard().recordKill(player2.getUniqueId());
        }
        this.myScoreboard().recordDeath(uUID);
        if (!this.players.contains(uUID)) {
            return;
        }
        if (this.mode == Mode.SOLO) {
            return;
        }
        this.eliminated.add(uUID);
        this.myScoreboard().clearPlayer(uUID);
        Bukkit.broadcast((Component)this.plugin.component("game-eliminated", "{player}", player.getName(), "{alive}", String.valueOf(this.aliveCount())));
        player.getScheduler().run((Plugin)this.plugin, scheduledTask -> this.playSound(player, Sound.ENTITY_PLAYER_HURT, 1.0f, 0.8f), () -> {});
        this.broadcastSound(Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.6f);
    }

    public boolean canDamage(Player player, Player player2) {
        if (this.mode == Mode.SOLO) {
            return false;
        }
        if (this.eliminated.contains(player.getUniqueId())) {
            return false;
        }
        if (this.eliminated.contains(player2.getUniqueId())) {
            return false;
        }
        if (this.mode == Mode.TEAM) {
            Integer n = this.teams.get(player.getUniqueId());
            Integer n2 = this.teams.get(player2.getUniqueId());
            if (n != null && n.equals(n2)) {
                return false;
            }
        }
        return true;
    }

    private void offerSpectateOrLobby(Player player) {
        if (!player.isOnline()) {
            return;
        }
    }

    public void sendToLobby(Player player) {
        World world = this.plugin.worlds().lobby();
        if (world != null) {
            player.getScheduler().run((Plugin)this.plugin, scheduledTask -> {
                try {
                    player.getInventory().clear();
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
            }, () -> {});
            player.teleportAsync(this.plugin.lobbyBuilder().spawnLocation());
            player.getScheduler().run((Plugin)this.plugin, scheduledTask -> {
                if (player.getGameMode() != GameMode.SURVIVAL) {
                    player.setGameMode(GameMode.SURVIVAL);
                }
                player.setHealth(20.0);
                player.setFoodLevel(20);
                player.clearActivePotionEffects();
            }, () -> {});
        }
    }

    public boolean canLeaveToLobby(Player player) {
        return this.state != State.RUNNING && this.state != State.COUNTDOWN || !this.isInGame(player.getUniqueId());
    }

    public boolean requestReturnToLobby(Player player) {
        if (!this.canLeaveToLobby(player)) {
            player.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u5bf9\u5c40\u8fdb\u884c\u4e2d, \u4e0d\u80fd\u8fd4\u56de\u5927\u5385! " + (this.mode == Mode.SOLO ? "" : "\u00a77(\u88ab\u6dd8\u6c70\u540e\u53ef\u7528 \u00a7e/box lobby \u00a77\u56de\u5927\u5385)"));
            return false;
        }
        this.sendToLobby(player);
        return true;
    }

    public void spectate(Player player) {
        World world = this.roomWorld();
        if (world != null && this.state == State.RUNNING) {
            player.getScheduler().run((Plugin)this.plugin, scheduledTask -> {
                if (player.getGameMode() != GameMode.SPECTATOR) {
                    player.setGameMode(GameMode.SPECTATOR);
                }
            }, () -> {});
        }
    }

    public void autoSpectateAfterDeath(Player player) {
    }

    private World mainWorld() {
        World world = this.plugin.worlds().lobby();
        if (world != null) {
            return world;
        }
        World world2 = this.roomWorld();
        if (world2 != null) {
            return world2;
        }
        return Bukkit.getWorlds().isEmpty() ? null : (World)Bukkit.getWorlds().get(0);
    }

    private void finish(String string, String string2, List<UUID> list) {
        Player player;
        Player player2;
        if (this.state == State.IDLE) {
            return;
        }
        this.state = State.ENDING;
        this.currentWinner = string2;
        if (this.tickTask != null) {
            this.tickTask.cancel();
            this.tickTask = null;
        }
        this.storm.stop();
        if (this.plugin.specialItems() != null) {
            Bukkit.getGlobalRegionScheduler().run((Plugin)this.plugin, scheduledTask -> this.plugin.specialItems().trackingPlayers().forEach(uUID -> this.plugin.specialItems().stopTracking((UUID)uUID)));
        }
        double d = this.plugin.getConfig().getDouble("game.win-reward", 1000.0);
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(this.plugin.raw("game-end")).append(" \u00a77[").append(this.mode.color).append(this.mode.display).append("\u00a77] ").append("\u00a7f").append(string).append(" \u00a77\u80dc\u8005: ").append(string2 != null ? string2 : "\u65e0");
        Bukkit.broadcast((Component)LegacyComponentSerializer.legacyAmpersand().deserialize(stringBuilder.toString()));
        this.broadcastSound(Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 0.7f);
        for (UUID uUID : list) {
            player2 = Bukkit.getPlayer((UUID)uUID);
            if (player2 == null || !player2.isOnline()) continue;
            player2.getScheduler().run((Plugin)this.plugin, scheduledTask -> this.playSound(player2, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f), () -> {});
        }
        if (d > 0.0) {
            for (UUID uUID : list) {
                player2 = Bukkit.getOfflinePlayer((UUID)uUID);
                this.plugin.econ().deposit((OfflinePlayer)player2, d);
                player = player2.getPlayer();
                if (player == null || !player.isOnline()) continue;
                player.sendMessage(this.plugin.msg("prefix") + "\u00a7a\u83b7\u80dc\u5956\u52b1 \u00a7e" + (long)d + " \u00a7a\u5143\u5df2\u53d1\u653e!");
            }
        }
        for (UUID uUID : this.players) {
            player2 = Bukkit.getPlayer((UUID)uUID);
            if (player2 == null || !player2.isOnline()) continue;
            player = player2;
            player.getScheduler().run((Plugin)this.plugin, scheduledTask -> {
                try {
                    player.getInventory().clear();
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
            }, () -> {});
            this.sendToLobby(player);
        }
        Bukkit.getGlobalRegionScheduler().run((Plugin)this.plugin, scheduledTask -> this.resetWorld());
    }

    private void resetWorld() {
        this.storm.stop();
        World world = this.roomWorld();
        if (world != null) {
            world.setPVP(false);
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Player) continue;
                entity.getScheduler().run((Plugin)this.plugin, scheduledTask -> {
                    try {
                        entity.remove();
                    }
                    catch (Throwable throwable) {
                        // empty catch block
                    }
                }, () -> {});
            }
        }
        this.plugin.boxes().wipeAll(() -> {
            int n = this.plugin.boxInitialFill();
            for (int i = 0; i < n; ++i) {
                this.plugin.boxes().spawnRandomBox(this.plugin.weightedPickForWorld(), false, null);
            }
            this.plugin.spawnArea().build(null);
            this.plugin.bigBox().buildRandom(world);
            for (UUID uUID : this.players) {
                Player player = Bukkit.getPlayer((UUID)uUID);
                if (player == null || !player.isOnline()) continue;
                player.getScheduler().run((Plugin)this.plugin, scheduledTask -> {
                    try {
                        player.getInventory().clear();
                    }
                    catch (Throwable throwable) {
                        // empty catch block
                    }
                    player.setHealth(20.0);
                    player.setFoodLevel(20);
                    player.setSaturation(10.0f);
                    player.clearActivePotionEffects();
                    if (player.getGameMode() != GameMode.SURVIVAL) {
                        player.setGameMode(GameMode.SURVIVAL);
                    }
                }, () -> {});
            }
            this.players.clear();
            this.joined.clear();
            this.joinOrder.clear();
            this.eliminated.clear();
            this.teams.clear();
            this.trackerGiven = false;
            this.myScoreboard().clearAll();
            this.state = State.IDLE;
            this.currentWinner = null;
            Bukkit.broadcast((Component)this.plugin.component("game-reset", new String[0]));
        });
    }

    public void onPlayerQuit(UUID uUID) {
        if (this.state == State.RUNNING && this.players.contains(uUID) && this.mode != Mode.SOLO && this.eliminated.add(uUID)) {
            Player player = Bukkit.getPlayer((UUID)uUID);
            this.myScoreboard().clearPlayer(uUID);
            Bukkit.broadcast((Component)this.plugin.component("game-eliminated", "{player}", player != null ? player.getName() : "?", "{alive}", String.valueOf(this.aliveCount())));
        }
        this.players.remove(uUID);
        this.joined.remove(uUID);
        this.joinOrder.remove(uUID);
    }

    private void playSound(Player player, Sound sound, float f, float f2) {
        try {
            if (player != null && player.isOnline()) {
                player.playSound(player.getLocation(), sound, f, f2);
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    private void broadcastSound(Sound sound, float f, float f2) {
        Bukkit.getGlobalRegionScheduler().run((Plugin)this.plugin, scheduledTask -> Bukkit.getOnlinePlayers().forEach(player -> this.playSound((Player)player, sound, f, f2)));
    }

    public static enum State {
        IDLE("\u7a7a\u95f2", "\u00a77"),
        COUNTDOWN("\u51c6\u5907\u4e2d", "\u00a7e"),
        RUNNING("\u8fdb\u884c\u4e2d", "\u00a7a"),
        ENDING("\u7ed3\u7b97\u4e2d", "\u00a7d");

        public final String display;
        public final String color;

        private State(String string2, String string3) {
            this.display = string2;
            this.color = string3;
        }
    }

    public static enum Mode {
        SOLO("\u5355\u4eba\u6a21\u5f0f", "\u00a7a"),
        PVP("\u73a9\u5bb6\u5bf9\u6218", "\u00a7c"),
        TEAM("\u7ec4\u961f\u5bf9\u6218", "\u00a76");

        public final String display;
        public final String color;

        private Mode(String string2, String string3) {
            this.display = string2;
            this.color = string3;
        }

        public static Mode parse(String string) {
            for (Mode mode : Mode.values()) {
                if (!mode.name().equalsIgnoreCase(string) && !mode.display.equals(string)) continue;
                return mode;
            }
            return null;
        }
    }
}
