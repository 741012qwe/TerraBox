package com.terrabox;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /box 命令: 玩家执行在玩家区域线程, 控制台在 Global Region 线程 (白皮书 §7.1)
 * 仅做: 参数分发 / 消息发送 / 注册表快照读取, 方块与实体操作全部走各服务调度链
 */
public class TerraCommand implements CommandExecutor, TabCompleter {
    private final TerraBoxPlugin plugin;

    public TerraCommand(TerraBoxPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(java.util.Locale.ROOT);

        if (sub.equals("reload") && hasAdmin(sender)) {
            plugin.reloadConfig();
            plugin.specialItems().load();
            plugin.loot().load();
            sender.sendMessage(plugin.msg("reloaded"));
            return true;
        }
        if (sub.equals("admin")) {
            return admin(sender, args);
        }

        if (!(sender instanceof Player p)) {
            sender.sendMessage(plugin.msg("player-only"));
            return true;
        }

        switch (sub) {
            case "" -> plugin.menus().open(p);
            case "spawn" -> {
                if (!plugin.worlds().isReady()) { p.sendMessage(plugin.msg("not-ready")); return true; }
                plugin.spawns().spawnPlayer(p, true);
            }
            case "sell" -> plugin.sells().open(p);
            case "hunt" -> plugin.hunts().hunt(p);
            case "top" -> sendTop(p);
            case "stats" -> sendStats(p, p);
            case "prices" -> sendPrices(p);
            case "balance" -> sendBalance(p);
            case "join" -> plugin.rooms().toggleJoin(p);
            case "lobby" -> plugin.rooms().requestReturnToLobby(p);
            case "spectate" -> plugin.rooms().spectate(p);
            case "storm" -> storm(p);
            case "invite" -> invite(p, args);
            case "room" -> room(p, args);
            case "terrain" -> {
                if (!hasAdmin(p)) return true;
                plugin.terrainSelect().open(p);
            }
            case "game" -> {
                if (args.length < 2) { sendGameStatus(p); return true; }
                String gsub = args[1].toLowerCase(java.util.Locale.ROOT);
                switch (gsub) {
                    case "join" -> plugin.rooms().toggleJoin(p);
                    case "status" -> sendGameStatus(p);
                    case "start" -> {
                        if (!hasAdmin(p)) return true;
                        GameManager.Mode m = args.length >= 3 ? GameManager.Mode.parse(args[2]) : GameManager.Mode.PVP;
                        if (m == null) {
                            p.sendMessage(plugin.msg("prefix") + "§c模式无效, 可选: solo / pvp / team");
                            return true;
                        }
                        if (!plugin.worlds().isReady()) { p.sendMessage(plugin.msg("not-ready")); return true; }
                        plugin.games().startGame(p, m);
                    }
                    case "stop" -> {
                        if (!hasAdmin(p)) return true;
                        plugin.games().stopGame(p);
                    }
                    default -> sendGameStatus(p);
                }
            }
            case "help" -> sendHelp(p);
            default -> p.sendMessage(plugin.msg("prefix") + "§7未知子命令, 输入 §e/box help §7查看帮助。");
        }
        return true;
    }

    /** /box storm: 查看/显示当前毒圈状态 (管理圈显示) */
    private void storm(Player p) {
        GameManager g = plugin.games();
        if (!p.hasPermission("terrabox.admin") && g.storm() != null && g.storm().isActive()) {
            // 普通玩家: 只显示自己所在房间毒圈状态
            p.sendMessage(plugin.msg("prefix") + "§6毒圈: " + (g.storm() != null ? g.storm().status() : "§7未激活"));
            return;
        }
        if (!hasAdmin(p)) return;
        if (g.storm() == null) { p.sendMessage(plugin.msg("prefix") + "§c毒圈未初始化。"); return; }
        p.sendMessage(plugin.msg("prefix") + "§6===== 毒圈状态 =====");
        p.sendMessage(" " + (g.storm().isActive() ? g.storm().status() : "§7未激活"));
        p.sendMessage(" §7阶段数: §e" + plugin.getConfig().getInt("storm.phases", 5)
                + " §7| 每阶段等待: §e" + plugin.getConfig().getLong("storm.wait-seconds", 60)
                + " §7秒 | 收缩时长: §e" + plugin.getConfig().getLong("storm.shrink-duration-seconds", 40) + " §7秒");
        if (g.storm().isActive() && g.roomWorld() != null) {
            g.storm().showRing(g.roomWorld());
            p.sendMessage(" §a已在当前世界显示安全区边界粒子。");
        }
    }

    /** 对局状态输出 */
    public void sendGameStatus(org.bukkit.command.CommandSender to) {
        GameManager g = plugin.games();
        to.sendMessage(plugin.msg("prefix") + "§e===== 对局状态 =====");
        to.sendMessage(" §7模式: " + g.modeDisplay() + "  §7状态: " + g.stateDisplay());
        to.sendMessage(" §7参战: §e" + g.playerCount() + " §7人" + (g.state() == GameManager.State.RUNNING
                ? "  §7存活: §a" + g.aliveCount() + " §7人" : "  §7已报名: §e" + g.joinedCount() + " §7人"));
        if (g.state() == GameManager.State.COUNTDOWN) {
            to.sendMessage(" §7即将开始, 报名中... 管理员可用 §e/box game start <solo|pvp|team> §7开赛");
        } else if (g.state() == GameManager.State.IDLE) {
            to.sendMessage(" §7空闲. 输入 §e/box game join §7报名, 管理员 §e/box game start <solo|pvp|team> §7开赛");
        }
    }

    private boolean admin(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) return true;
        String sub = args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : "";
        switch (sub) {
            case "boxes" -> {
                sender.sendMessage(plugin.msg("prefix") + "§e物资箱总数: §a" + plugin.boxes().count()
                        + "§e/" + plugin.getConfig().getInt("boxes.max-count", 320));
                for (Map.Entry<Rarity, Integer> en : plugin.boxes().countByRarity().entrySet()) {
                    sender.sendMessage(" §7" + en.getKey().display + ": " + en.getValue() + " 个");
                }
                sender.sendMessage(" §7预生成: " + (plugin.worlds().pregenRunning()
                        ? "进行中 " + plugin.worlds().pregenDone() + "/" + plugin.worlds().pregenTotal()
                        : (plugin.worlds().isReady() ? "完成" : "等待世界...")));
            }
            case "fill" -> {
                if (!plugin.worlds().isReady()) { sender.sendMessage(plugin.msg("not-ready")); return true; }
                int n = Math.min(40, plugin.boxMaxCount() - plugin.boxes().count());
                for (int i = 0; i < n; i++) plugin.boxes().spawnRandomBox(plugin.weightedPickForWorld(), false, null);
                sender.sendMessage(plugin.msg("prefix") + "§a已投放 " + n + " 个随机物资箱。");
            }
            case "airdrop" -> {
                if (!plugin.worlds().isReady()) { sender.sendMessage(plugin.msg("not-ready")); return true; }
                sender.sendMessage(plugin.msg("prefix") + "§d立即空投!");
                plugin.airdrops().dropNow(null);
            }
            case "wipe" -> handleWipe(sender);
            default -> sender.sendMessage(plugin.msg("prefix")
                    + "§e/box admin boxes|fill|airdrop|wipe");
        }
        return true;
    }

    private boolean wipeArmed = false;

    /** /box room 房间管理 (玩家可用: create/list/invite/join/leave/info; 管理员: start/stop/remove/force) */
    private void room(Player p, String[] args) {
        RoomManager mgr = plugin.rooms();
        // 无参数: 打开房间 GUI (查看所有在线房间)
        if (args.length < 2) {
            plugin.roomGui().open(p);
            return;
        }
        String sub = args[1].toLowerCase(java.util.Locale.ROOT);
        switch (sub) {
            case "list" -> sendRoomList(p);
            case "create" -> createRoomFor(p, args.length >= 3 ? args[2] : null);
            case "invite" -> {
                // /box room invite <玩家> [房间] — 邀请玩家加入自己的房间
                if (args.length < 3) { p.sendMessage(plugin.msg("prefix") + "§e用法: /box room invite <玩家> [房间]"); return; }
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) { p.sendMessage(plugin.msg("prefix") + "§c玩家不在线: " + args[2]); return; }
                String roomId = args.length >= 4 ? args[3] : myRoom(p);
                plugin.invites().invite(p, target, roomId);
            }
            case "join" -> {
                if (args.length < 3) { p.sendMessage(plugin.msg("prefix") + "§e用法: /box room join <id>"); return; }
                GameManager g = mgr.get(args[2]);
                if (g == null) { p.sendMessage(plugin.msg("prefix") + "§c房间 " + args[2] + " 不存在"); return; }
                g.join(p);
            }
            case "leave" -> {
                if (args.length < 3) { p.sendMessage(plugin.msg("prefix") + "§e用法: /box room leave <id>"); return; }
                GameManager g = mgr.get(args[2]);
                if (g != null) g.leave(p);
            }
            case "info" -> {
                if (args.length < 3) { sendRoomList(p); return; }
                sendRoomInfo(p, mgr.get(args[2]));
            }
            // ===== 管理员功能 =====
            case "remove" -> {
                if (!hasAdmin(p)) return;
                if (args.length < 3) { p.sendMessage(plugin.msg("prefix") + "§e用法: /box room remove <id>"); return; }
                String id = args[2];
                if (!mgr.hasRoom(id)) { p.sendMessage(plugin.msg("prefix") + "§c房间 " + id + " 不存在"); return; }
                if ("default".equalsIgnoreCase(id)) { p.sendMessage(plugin.msg("prefix") + "§c不能删除默认房间"); return; }
                GameManager g = mgr.get(id);
                if (g != null && g.isRunning()) { p.sendMessage(plugin.msg("prefix") + "§c房间 " + id + " 对局进行中, 先停止"); return; }
                mgr.removeRoom(id);
                p.sendMessage(plugin.msg("prefix") + "§a已删除对局房间 §e" + id);
            }
            case "start" -> {
                if (!hasAdmin(p)) return;
                if (args.length < 4) { p.sendMessage(plugin.msg("prefix") + "§e用法: /box room start <id> <solo|pvp|team>"); return; }
                String id = args[2];
                GameManager.Mode m = args.length >= 4 ? GameManager.Mode.parse(args[3]) : GameManager.Mode.PVP;
                GameManager g = mgr.get(id);
                if (g == null) { p.sendMessage(plugin.msg("prefix") + "§c房间 " + id + " 不存在"); return; }
                if (m != null) g.startGame(p, m);
            }
            case "stop" -> {
                if (!hasAdmin(p)) return;
                if (args.length < 3) { p.sendMessage(plugin.msg("prefix") + "§c用法: /box room stop <id>"); return; }
                GameManager g = mgr.get(args[2]);
                if (g == null) { p.sendMessage(plugin.msg("prefix") + "§c房间 " + args[2] + " 不存在"); return; }
                g.stopGame(p);
            }
            case "force" -> {
                if (!hasAdmin(p)) return;
                // 管理员强制把玩家加入某房间
                if (args.length < 4) { p.sendMessage(plugin.msg("prefix") + "§e用法: /box room force <玩家> <房间>"); return; }
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) { p.sendMessage(plugin.msg("prefix") + "§c玩家不在线: " + args[2]); return; }
                GameManager g = mgr.get(args[3]);
                if (g == null) { p.sendMessage(plugin.msg("prefix") + "§c房间 " + args[3] + " 不存在"); return; }
                if (g.join(target)) {
                    p.sendMessage(plugin.msg("prefix") + "§a已强制 §e" + target.getName() + " §a加入房间 §e" + args[3]);
                    target.sendMessage(plugin.msg("prefix") + "§e管理员将你加入了房间 §b" + args[3]);
                }
            }
            case "status" -> {
                if (args.length < 3) { sendRoomList(p); return; }
                if (!hasAdmin(p)) return;
                sendRoomInfo(p, mgr.get(args[2]));
            }
            default -> sendRoomList(p);
        }
    }

    /** 创建房间 (玩家可用), id 为空则自动命名 room_<时间戳末位> */
    public void createRoomFor(Player p, String id) {
        RoomManager mgr = plugin.rooms();
        String roomId = id;
        if (roomId == null || roomId.isBlank()) {
            int n = (int) (System.currentTimeMillis() % 1000);
            roomId = "room_" + n;
            while (mgr.hasRoom(roomId)) { roomId = "room_" + (n++); }
        }
        roomId = roomId.toLowerCase(java.util.Locale.ROOT);
        if (mgr.hasRoom(roomId)) { p.sendMessage(plugin.msg("prefix") + "§c房间 " + roomId + " 已存在"); return; }
        String worldName = plugin.worlds().world() != null ? plugin.worlds().world().getName() : null;
        GameManager g = mgr.createRoom(roomId, worldName);
        g.setOwner(p.getUniqueId());
        p.sendMessage(plugin.msg("prefix") + "§a已创建对局房间 §e" + roomId + " §a(你是房主, 可用 /box room invite <玩家> 邀请)");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.3f);
    }

    /** /box invite accept|decline — 聊天框点击接受/拒绝房间邀请 */
    private void invite(Player p, String[] args) {
        if (args.length < 2) {
            p.sendMessage(plugin.msg("prefix") + "§e用法: §f/box invite accept|decline §7(点击聊天框邀请文本即可)");
            return;
        }
        switch (args[1].toLowerCase(java.util.Locale.ROOT)) {
            case "accept" -> plugin.invites().accept(p);
            case "decline" -> plugin.invites().decline(p);
            default -> p.sendMessage(plugin.msg("prefix") + "§e用法: §f/box invite accept|decline");
        }
    }

    /** 玩家报名最多的房间 (邀请默认目标) */
    private String myRoom(Player p) {
        for (GameManager g : plugin.rooms().joinedRooms(p.getUniqueId())) return g.roomId();
        return plugin.rooms().defaultRoom().roomId();
    }

    private void sendRoomInfo(Player p, GameManager g) {
        if (g == null) { p.sendMessage(plugin.msg("prefix") + "§c房间不存在"); return; }
        p.sendMessage(plugin.msg("prefix") + "§e===== 房间 [" + g.roomId() + "] =====");
        p.sendMessage(" §7房主: " + (g.owner() != null ? plugin.players().getOrCreate(g.owner(), null).name : "系统"));
        p.sendMessage(" §7世界: §b" + (g.roomWorldName() != null ? g.roomWorldName()
                : (g.roomWorld() != null ? g.roomWorld().getName() : "?")));
        p.sendMessage(" §7模式: " + g.modeDisplay() + "  §7状态: " + g.stateDisplay());
        p.sendMessage(" §7参战: §e" + g.playerCount() + " §7人  §7存活: §a" + g.aliveCount() + " §7人");
        StringBuilder members = new StringBuilder();
        for (UUID u : g.inGamePlayers()) {
            String nm = plugin.players().getOrCreate(u, null).name;
            if (members.length() > 0) members.append("§7, ");
            members.append("§f").append(nm);
        }
        p.sendMessage(" §7成员: " + (members.length() > 0 ? members.toString() : "§7(空)"));
        p.sendMessage(" §7加入: §e/box room join " + g.roomId() + " §7| 退出: §e/box room leave " + g.roomId());
    }

    private void sendRoomList(Player p) {
        RoomManager mgr = plugin.rooms();
        p.sendMessage(plugin.msg("prefix") + "§e===== 在线对局房间 =====");
        for (String id : mgr.roomIds()) {
            GameManager g = mgr.get(id);
            if (g == null) continue;
            String owner = g.owner() != null ? plugin.players().getOrCreate(g.owner(), null).name : "系统";
            int n = 0; StringBuilder names = new StringBuilder();
            for (Player op : mgr.onlinePlayersIn(id)) {
                if (n++ < 8) { if (names.length() > 0) names.append("§7,"); names.append("§f").append(op.getName()); }
            }
            p.sendMessage(" §e" + id + " §7[房主 " + owner + "] -> §b"
                    + (g.roomWorldName() != null ? g.roomWorldName()
                    : (g.roomWorld() != null ? g.roomWorld().getName() : "?"))
                    + " §7状态: " + g.stateDisplay() + " §7参战: §e" + g.playerCount()
                    + (names.length() > 0 ? " §7成员: " + names : ""));
        }
        p.sendMessage(" §7加入: §e/box room join <id> §7| 建房: §e/box room create <id>"
                + " §7| 邀请: §e/box room invite <玩家> [房间]");
        if (p.hasPermission("terrabox.admin")) {
            p.sendMessage(" §7管理员: §e/box room start|stop|remove|force <玩家> <房间>");
        }
    }

    private void handleWipe(CommandSender sender) {
        if (wipeArmed) {
            wipeArmed = false;
            sender.sendMessage(plugin.msg("prefix") + "§e正在清空全部物资箱...");
            plugin.boxes().wipeAll(() -> sender.sendMessage(plugin.msg("prefix") + "§a物资箱已全部清空。"));
        } else {
            wipeArmed = true;
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> wipeArmed = false, 200L);
            sender.sendMessage(plugin.msg("prefix") + "§e再次输入 /box admin wipe 以确认清空全部物资箱 (10秒内)。");
        }
    }

    // ==================== 信息输出 ====================

    public void sendTop(CommandSender to) {
        plugin.players().topAsync(list -> {
            to.sendMessage(plugin.msg("top-header"));
            if (list.isEmpty()) {
                to.sendMessage(plugin.msg("prefix") + "§7暂无数据, 快去开箱吧!");
                return;
            }
            int rank = 1;
            for (PlayerStore.TopEntry e : list) {
                to.sendMessage(plugin.msg("top-line")
                        .replace("{rank}", String.valueOf(rank++))
                        .replace("{player}", e.name())
                        .replace("{count}", String.valueOf(e.count())));
            }
        });
    }

    public void sendStats(CommandSender to, Player target) {
        PlayerStore.PlayerData d = plugin.players().getOrCreate(target.getUniqueId(), target.getName());
        to.sendMessage(plugin.msg("prefix") + "§e===== " + d.name + " 的物资统计 =====");
        to.sendMessage(" §f普通: §7" + d.openedCommon.get()
                + "  §a精良: §7" + d.openedRare.get()
                + "  §b稀有: §7" + d.openedEpic.get());
        to.sendMessage(" §6传说: §7" + d.openedLegendary.get()
                + "  §d绝世: §7" + d.openedMythic.get()
                + "  §f合计: §a" + d.openedTotal());
        to.sendMessage(" §7空投搜刮: §d" + d.airdropLooted.get()
                + "  §7寻宝次数: §b" + d.huntCount.get()
                + "  §7累计回收: §e" + d.soldValue.get() + " 元");
        to.sendMessage(" §7余额: §e" + (long) plugin.econ().balance(target)
                + " (" + plugin.econ().name() + ")");
    }

    private void sendPrices(CommandSender to) {
        to.sendMessage(plugin.msg("prefix") + "§e===== 回收价格表 (元/件) =====");
        List<Map.Entry<String, Double>> entries = new ArrayList<>(plugin.sellPrices().entrySet());
        entries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        StringBuilder line = new StringBuilder();
        for (Map.Entry<String, Double> e : entries) {
            String item = "§f" + e.getKey() + " §e" + e.getValue() + "  ";
            if (line.length() + item.length() > 80) {
                to.sendMessage(line.toString());
                line = new StringBuilder();
            }
            line.append(item);
        }
        if (line.length() > 0) to.sendMessage(line.toString());
    }

    /** 物资平衡性概览: 各档权重/箱子分布/刷新周期/投放上限/特殊道具 */
    private void sendBalance(CommandSender to) {
        to.sendMessage(plugin.msg("prefix") + "§e===== 物资平衡性 =====");
        // 各档权重
        StringBuilder w = new StringBuilder();
        for (Rarity r : Rarity.values()) {
            w.append(r.colorCode.replace('&', '\u00A7')).append(r.display).append("§7:").append(r.weight()).append("  ");
        }
        to.sendMessage(" §7五档权重: " + w);
        // 箱子分布
        var counts = plugin.boxes().countByRarity();
        for (Rarity r : Rarity.values()) {
            to.sendMessage(" §7" + r.display + "箱: " + r.colorCode.replace('&', '\u00A7')
                    + counts.getOrDefault(r, 0) + "§7 个");
        }
        to.sendMessage(" §7箱子总数/上限: §e" + plugin.boxes().count()
                + "§7/§e" + plugin.getConfig().getInt("boxes.max-count", 320));
        to.sendMessage(" §7刷新周期: §e" + plugin.getConfig().getLong("boxes.refresh-minutes", 45)
                + " §7分钟 | 每周期补充: §e" + plugin.getConfig().getInt("boxes.refill-per-cycle", 8)
                + " §7个 | 最小距离: §e" + plugin.getConfig().getDouble("boxes.min-distance", 18.0) + " 格");
        to.sendMessage(" §7首次投放: §e" + plugin.getConfig().getInt("boxes.initial-fill", 150) + " §7个");
        if (plugin.specialItems() != null && plugin.specialItems().size() > 0) {
            to.sendMessage(" §7特殊道具: §e" + plugin.specialItems().size() + " §7种");
        }
        if (to instanceof Player p) {
            to.sendMessage(" §7剩余空投: §e约 " + (plugin.airdrops().secondsUntilNext() / 60 + 1) + " 分钟后");
        }
    }

    private void sendHelp(Player p) {
        p.sendMessage(plugin.msg("prefix") + "§e===== 物资大陆 帮助 =====");
        p.sendMessage(" §f/box §7- 打开主菜单");
        p.sendMessage(" §f/box spawn §7- 传送到随机陆地出生点 (有冷却)");
        p.sendMessage(" §f/box sell §7- 打开物资回收商店");
        p.sendMessage(" §f/box prices §7- 查看回收价格表");
        p.sendMessage(" §f/box balance §7- 查看物资平衡性");
        p.sendMessage(" §f/box hunt §7- 花钱购买高稀有箱方位提示");
        p.sendMessage(" §f/box top §7- 开箱排行榜");
        p.sendMessage(" §f/box stats §7- 我的统计");
        p.sendMessage(" §f/box join §7- 报名参加对局");
        p.sendMessage(" §f/box lobby §7- 返回大厅");
        p.sendMessage(" §f/box spectate §7- 对局淘汰后旁观");
        p.sendMessage(" §f/box storm §7- 查看毒圈状态");
        p.sendMessage(" §f/box hunt §7- 花钱购买高稀有物资箱方位");
        p.sendMessage(" §f/box room §7- 对局房间 (查看/创建/邀请/加入)");
        p.sendMessage(" §f/box game status §7- 对局状态");
        if (p.hasPermission("terrabox.admin")) {
            p.sendMessage(" §c/box game start <solo|pvp|team> §7- 开始对局 (单人模式任意人数可开)");
            p.sendMessage(" §c/box game stop §7- 终止对局");
            p.sendMessage(" §c/box room list §7- 对局房间列表");
            p.sendMessage(" §c/box room create <id> §7- 创建多世界对局房间");
            p.sendMessage(" §c/box room start <id> <solo|pvp|team> §7- 在某房间开对局");
            p.sendMessage(" §c/box room stop <id> §7- 终止某房间对局");
            p.sendMessage(" §c/box room force <玩家> <房间> §7- 强制把玩家加入房间");
            p.sendMessage(" §c/box terrain §7- 选择对局地形 (默认/沙漠/大岛屿)");
            p.sendMessage(" §c/box admin boxes|fill|airdrop|wipe §7- 管理");
            p.sendMessage(" §c/box reload §7- 重载配置");
        }
    }

    private boolean hasAdmin(CommandSender s) {
        if (!s.hasPermission("terrabox.admin")) {
            s.sendMessage(plugin.msg("no-permission"));
            return false;
        }
        return true;
    }

    // ==================== Tab 补全 (命令执行线程, 只读常量表) ====================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("spawn", "sell", "hunt", "top", "stats", "prices", "balance", "join", "game", "help", "lobby", "spectate", "storm", "room", "invite"));
            if (sender.hasPermission("terrabox.admin")) {
                subs.add("admin");
                subs.add("reload");
                subs.add("terrain");
            }
            for (String s : subs) if (s.startsWith(args[0].toLowerCase())) out.add(s);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("game")) {
            for (String s : List.of("join", "status", "start", "stop")) {
                if (s.startsWith(args[1].toLowerCase())) out.add(s);
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("game")
                && args[1].equalsIgnoreCase("start") && sender.hasPermission("terrabox.admin")) {
            for (String s : List.of("solo", "pvp", "team")) {
                if (s.startsWith(args[2].toLowerCase())) out.add(s);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("terrabox.admin")) {
            for (String s : List.of("boxes", "fill", "airdrop", "wipe")) {
                if (s.startsWith(args[1].toLowerCase())) out.add(s);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("invite")) {
            for (String s : List.of("accept", "decline")) {
                if (s.startsWith(args[1].toLowerCase())) out.add(s);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("room")) {
            List<String> rs = new ArrayList<>(List.of("list", "join", "leave", "invite", "info", "create"));
            if (sender.hasPermission("terrabox.admin")) {
                rs.add("start"); rs.add("stop"); rs.add("remove"); rs.add("force"); rs.add("status");
            }
            for (String s : rs) if (s.startsWith(args[1].toLowerCase())) out.add(s);
        } else if (args.length >= 3 && args[0].equalsIgnoreCase("room")) {
            String rsub = args[1].toLowerCase(java.util.Locale.ROOT);
            // 补全房间 id
            if (rsub.equals("join") || rsub.equals("leave") || rsub.equals("info")
                    || rsub.equals("start") || rsub.equals("stop") || rsub.equals("remove")
                    || rsub.equals("status") || rsub.equals("force")) {
                // force 第二个参数是房间, 第三个是玩家 — 处理
                if (rsub.equals("force")) {
                    if (args.length == 3) {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            if (p.getName().toLowerCase().startsWith(args[2].toLowerCase())) out.add(p.getName());
                        }
                        return out;
                    } else if (args.length == 4) {
                        for (String rid : plugin.rooms().roomIds()) {
                            if (rid.toLowerCase().startsWith(args[3].toLowerCase())) out.add(rid);
                        }
                        return out;
                    }
                }
                // 其余: 补全房间 id (最后一个参数)
                String last = args[args.length - 1].toLowerCase(java.util.Locale.ROOT);
                for (String rid : plugin.rooms().roomIds()) {
                    if (rid.toLowerCase().startsWith(last)) out.add(rid);
                }
                return out;
            }
            // invite/join 等: 补全在线玩家名
            if (rsub.equals("invite")) {
                String last = args[args.length - 1].toLowerCase(java.util.Locale.ROOT);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(last)) out.add(p.getName());
                }
                // invite 的第二个参数(房间)补全房间
                if (args.length == 4) {
                    for (String rid : plugin.rooms().roomIds()) {
                        if (rid.toLowerCase().startsWith(last)) out.add(rid);
                    }
                }
                return out;
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("room")
                && args[1].equalsIgnoreCase("start") && sender.hasPermission("terrabox.admin")) {
            for (String s : List.of("solo", "pvp", "team")) if (s.startsWith(args[2].toLowerCase())) out.add(s);
        }
        return out;
    }
}
