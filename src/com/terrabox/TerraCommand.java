/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.OfflinePlayer
 *  org.bukkit.Sound
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.command.TabCompleter
 *  org.bukkit.entity.Player
 *  org.bukkit.plugin.Plugin
 */
package com.terrabox;

import com.terrabox.GameManager;
import com.terrabox.PlayerStore;
import com.terrabox.Rarity;
import com.terrabox.RoomManager;
import com.terrabox.TerraBoxPlugin;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class TerraCommand
implements CommandExecutor,
TabCompleter {
    private final TerraBoxPlugin plugin;
    private boolean wipeArmed = false;

    public TerraCommand(TerraBoxPlugin terraBoxPlugin) {
        this.plugin = terraBoxPlugin;
    }

    public boolean onCommand(CommandSender commandSender, Command command, String string, String[] stringArray) {
        String string2;
        String string3 = string2 = stringArray.length == 0 ? "" : stringArray[0].toLowerCase(Locale.ROOT);
        if (string2.equals("reload") && this.hasAdmin(commandSender)) {
            this.plugin.reloadConfig();
            this.plugin.specialItems().load();
            this.plugin.loot().load();
            commandSender.sendMessage(this.plugin.msg("reloaded"));
            return true;
        }
        if (string2.equals("admin")) {
            return this.admin(commandSender, stringArray);
        }
        if (!(commandSender instanceof Player)) {
            commandSender.sendMessage(this.plugin.msg("player-only"));
            return true;
        }
        Player player = (Player)commandSender;
        block19 : switch (string2) {
            case "": {
                this.plugin.menus().open(player);
                break;
            }
            case "spawn": {
                if (!this.plugin.worlds().isReady()) {
                    player.sendMessage(this.plugin.msg("not-ready"));
                    return true;
                }
                this.plugin.spawns().spawnPlayer(player, true);
                break;
            }
            case "sell": {
                this.plugin.sells().open(player);
                break;
            }
            case "hunt": {
                this.plugin.hunts().hunt(player);
                break;
            }
            case "top": {
                this.sendTop((CommandSender)player);
                break;
            }
            case "stats": {
                this.sendStats((CommandSender)player, player);
                break;
            }
            case "prices": {
                this.sendPrices((CommandSender)player);
                break;
            }
            case "balance": {
                this.sendBalance((CommandSender)player);
                break;
            }
            case "join": {
                this.plugin.rooms().toggleJoin(player);
                break;
            }
            case "lobby": {
                this.plugin.rooms().requestReturnToLobby(player);
                break;
            }
            case "spectate": {
                this.plugin.rooms().spectate(player);
                break;
            }
            case "storm": {
                this.storm(player);
                break;
            }
            case "invite": {
                this.invite(player, stringArray);
                break;
            }
            case "room": {
                this.room(player, stringArray);
                break;
            }
            case "terrain": {
                if (!this.hasAdmin((CommandSender)player)) {
                    return true;
                }
                this.plugin.terrainSelect().open(player);
                break;
            }
            case "game": {
                String string4;
                if (stringArray.length < 2) {
                    this.sendGameStatus((CommandSender)player);
                    return true;
                }
                switch (string4 = stringArray[1].toLowerCase(Locale.ROOT)) {
                    case "join": {
                        this.plugin.rooms().toggleJoin(player);
                        break block19;
                    }
                    case "status": {
                        this.sendGameStatus((CommandSender)player);
                        break block19;
                    }
                    case "start": {
                        GameManager.Mode mode;
                        if (!this.hasAdmin((CommandSender)player)) {
                            return true;
                        }
                        GameManager.Mode mode2 = mode = stringArray.length >= 3 ? GameManager.Mode.parse(stringArray[2]) : GameManager.Mode.PVP;
                        if (mode == null) {
                            player.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u6a21\u5f0f\u65e0\u6548, \u53ef\u9009: solo / pvp / team");
                            return true;
                        }
                        if (!this.plugin.worlds().isReady()) {
                            player.sendMessage(this.plugin.msg("not-ready"));
                            return true;
                        }
                        this.plugin.games().startGame((CommandSender)player, mode);
                        break block19;
                    }
                    case "stop": {
                        if (!this.hasAdmin((CommandSender)player)) {
                            return true;
                        }
                        this.plugin.games().stopGame((CommandSender)player);
                        break block19;
                    }
                }
                this.sendGameStatus((CommandSender)player);
                break;
            }
            case "help": {
                this.sendHelp(player);
                break;
            }
            default: {
                player.sendMessage(this.plugin.msg("prefix") + "\u00a77\u672a\u77e5\u5b50\u547d\u4ee4, \u8f93\u5165 \u00a7e/box help \u00a77\u67e5\u770b\u5e2e\u52a9\u3002");
            }
        }
        return true;
    }

    private void storm(Player player) {
        GameManager gameManager = this.plugin.games();
        if (!player.hasPermission("terrabox.admin") && gameManager.storm() != null && gameManager.storm().isActive()) {
            player.sendMessage(this.plugin.msg("prefix") + "\u00a76\u6bd2\u5708: " + (gameManager.storm() != null ? gameManager.storm().status() : "\u00a77\u672a\u6fc0\u6d3b"));
            return;
        }
        if (!this.hasAdmin((CommandSender)player)) {
            return;
        }
        if (gameManager.storm() == null) {
            player.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u6bd2\u5708\u672a\u521d\u59cb\u5316\u3002");
            return;
        }
        player.sendMessage(this.plugin.msg("prefix") + "\u00a76===== \u6bd2\u5708\u72b6\u6001 =====");
        player.sendMessage(" " + (gameManager.storm().isActive() ? gameManager.storm().status() : "\u00a77\u672a\u6fc0\u6d3b"));
        player.sendMessage(" \u00a77\u9636\u6bb5\u6570: \u00a7e" + this.plugin.getConfig().getInt("storm.phases", 5) + " \u00a77| \u6bcf\u9636\u6bb5\u7b49\u5f85: \u00a7e" + this.plugin.getConfig().getLong("storm.wait-seconds", 60L) + " \u00a77\u79d2 | \u6536\u7f29\u65f6\u957f: \u00a7e" + this.plugin.getConfig().getLong("storm.shrink-duration-seconds", 40L) + " \u00a77\u79d2");
        if (gameManager.storm().isActive() && gameManager.roomWorld() != null) {
            gameManager.storm().showRing(gameManager.roomWorld());
            player.sendMessage(" \u00a7a\u5df2\u5728\u5f53\u524d\u4e16\u754c\u663e\u793a\u5b89\u5168\u533a\u8fb9\u754c\u7c92\u5b50\u3002");
        }
    }

    public void sendGameStatus(CommandSender commandSender) {
        GameManager gameManager = this.plugin.games();
        commandSender.sendMessage(this.plugin.msg("prefix") + "\u00a7e===== \u5bf9\u5c40\u72b6\u6001 =====");
        commandSender.sendMessage(" \u00a77\u6a21\u5f0f: " + gameManager.modeDisplay() + "  \u00a77\u72b6\u6001: " + gameManager.stateDisplay());
        commandSender.sendMessage(" \u00a77\u53c2\u6218: \u00a7e" + gameManager.playerCount() + " \u00a77\u4eba" + (gameManager.state() == GameManager.State.RUNNING ? "  \u00a77\u5b58\u6d3b: \u00a7a" + gameManager.aliveCount() + " \u00a77\u4eba" : "  \u00a77\u5df2\u62a5\u540d: \u00a7e" + gameManager.joinedCount() + " \u00a77\u4eba"));
        if (gameManager.state() == GameManager.State.COUNTDOWN) {
            commandSender.sendMessage(" \u00a77\u5373\u5c06\u5f00\u59cb, \u62a5\u540d\u4e2d... \u7ba1\u7406\u5458\u53ef\u7528 \u00a7e/box game start <solo|pvp|team> \u00a77\u5f00\u8d5b");
        } else if (gameManager.state() == GameManager.State.IDLE) {
            commandSender.sendMessage(" \u00a77\u7a7a\u95f2. \u8f93\u5165 \u00a7e/box game join \u00a77\u62a5\u540d, \u7ba1\u7406\u5458 \u00a7e/box game start <solo|pvp|team> \u00a77\u5f00\u8d5b");
        }
    }

    private boolean admin(CommandSender commandSender, String[] stringArray) {
        String string;
        if (!this.hasAdmin(commandSender)) {
            return true;
        }
        switch (string = stringArray.length > 1 ? stringArray[1].toLowerCase(Locale.ROOT) : "") {
            case "boxes": {
                commandSender.sendMessage(this.plugin.msg("prefix") + "\u00a7e\u7269\u8d44\u7bb1\u603b\u6570: \u00a7a" + this.plugin.boxes().count() + "\u00a7e/" + this.plugin.getConfig().getInt("boxes.max-count", 320));
                for (Map.Entry<Rarity, Integer> entry : this.plugin.boxes().countByRarity().entrySet()) {
                    commandSender.sendMessage(" \u00a77" + entry.getKey().display + ": " + String.valueOf(entry.getValue()) + " \u4e2a");
                }
                commandSender.sendMessage(" \u00a77\u9884\u751f\u6210: " + (String)(this.plugin.worlds().pregenRunning() ? "\u8fdb\u884c\u4e2d " + this.plugin.worlds().pregenDone() + "/" + this.plugin.worlds().pregenTotal() : (this.plugin.worlds().isReady() ? "\u5b8c\u6210" : "\u7b49\u5f85\u4e16\u754c...")));
                break;
            }
            case "fill": {
                if (!this.plugin.worlds().isReady()) {
                    commandSender.sendMessage(this.plugin.msg("not-ready"));
                    return true;
                }
                int n = Math.min(40, this.plugin.boxMaxCount() - this.plugin.boxes().count());
                for (int i = 0; i < n; ++i) {
                    this.plugin.boxes().spawnRandomBox(this.plugin.weightedPickForWorld(), false, null);
                }
                commandSender.sendMessage(this.plugin.msg("prefix") + "\u00a7a\u5df2\u6295\u653e " + n + " \u4e2a\u968f\u673a\u7269\u8d44\u7bb1\u3002");
                break;
            }
            case "airdrop": {
                if (!this.plugin.worlds().isReady()) {
                    commandSender.sendMessage(this.plugin.msg("not-ready"));
                    return true;
                }
                commandSender.sendMessage(this.plugin.msg("prefix") + "\u00a7d\u7acb\u5373\u7a7a\u6295!");
                this.plugin.airdrops().dropNow(null);
                break;
            }
            case "wipe": {
                this.handleWipe(commandSender);
                break;
            }
            default: {
                commandSender.sendMessage(this.plugin.msg("prefix") + "\u00a7e/box admin boxes|fill|airdrop|wipe");
            }
        }
        return true;
    }

    private void room(Player player, String[] stringArray) {
        String string;
        RoomManager roomManager = this.plugin.rooms();
        if (stringArray.length < 2) {
            this.plugin.roomGui().open(player);
            return;
        }
        switch (string = stringArray[1].toLowerCase(Locale.ROOT)) {
            case "list": {
                this.sendRoomList(player);
                break;
            }
            case "create": {
                this.createRoomFor(player, stringArray.length >= 3 ? stringArray[2] : null);
                break;
            }
            case "invite": {
                if (stringArray.length < 3) {
                    player.sendMessage(this.plugin.msg("prefix") + "\u00a7e\u7528\u6cd5: /box room invite <\u73a9\u5bb6> [\u623f\u95f4]");
                    return;
                }
                Player player2 = Bukkit.getPlayer((String)stringArray[2]);
                if (player2 == null) {
                    player.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u73a9\u5bb6\u4e0d\u5728\u7ebf: " + stringArray[2]);
                    return;
                }
                String string2 = stringArray.length >= 4 ? stringArray[3] : this.myRoom(player);
                this.plugin.invites().invite(player, player2, string2);
                break;
            }
            case "join": {
                if (stringArray.length < 3) {
                    player.sendMessage(this.plugin.msg("prefix") + "\u00a7e\u7528\u6cd5: /box room join <id>");
                    return;
                }
                GameManager gameManager = roomManager.get(stringArray[2]);
                if (gameManager == null) {
                    player.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u623f\u95f4 " + stringArray[2] + " \u4e0d\u5b58\u5728");
                    return;
                }
                gameManager.join(player);
                break;
            }
            case "leave": {
                if (stringArray.length < 3) {
                    player.sendMessage(this.plugin.msg("prefix") + "\u00a7e\u7528\u6cd5: /box room leave <id>");
                    return;
                }
                GameManager gameManager = roomManager.get(stringArray[2]);
                if (gameManager == null) break;
                gameManager.leave(player);
                break;
            }
            case "info": {
                if (stringArray.length < 3) {
                    this.sendRoomList(player);
                    return;
                }
                this.sendRoomInfo(player, roomManager.get(stringArray[2]));
                break;
            }
            case "remove": {
                if (!this.hasAdmin((CommandSender)player)) {
                    return;
                }
                if (stringArray.length < 3) {
                    player.sendMessage(this.plugin.msg("prefix") + "\u00a7e\u7528\u6cd5: /box room remove <id>");
                    return;
                }
                String string3 = stringArray[2];
                if (!roomManager.hasRoom(string3)) {
                    player.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u623f\u95f4 " + string3 + " \u4e0d\u5b58\u5728");
                    return;
                }
                if ("default".equalsIgnoreCase(string3)) {
                    player.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u4e0d\u80fd\u5220\u9664\u9ed8\u8ba4\u623f\u95f4");
                    return;
                }
                GameManager gameManager = roomManager.get(string3);
                if (gameManager != null && gameManager.isRunning()) {
                    player.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u623f\u95f4 " + string3 + " \u5bf9\u5c40\u8fdb\u884c\u4e2d, \u5148\u505c\u6b62");
                    return;
                }
                roomManager.removeRoom(string3);
                player.sendMessage(this.plugin.msg("prefix") + "\u00a7a\u5df2\u5220\u9664\u5bf9\u5c40\u623f\u95f4 \u00a7e" + string3);
                break;
            }
            case "start": {
                if (!this.hasAdmin((CommandSender)player)) {
                    return;
                }
                if (stringArray.length < 4) {
                    player.sendMessage(this.plugin.msg("prefix") + "\u00a7e\u7528\u6cd5: /box room start <id> <solo|pvp|team>");
                    return;
                }
                String string4 = stringArray[2];
                GameManager.Mode mode = stringArray.length >= 4 ? GameManager.Mode.parse(stringArray[3]) : GameManager.Mode.PVP;
                GameManager gameManager = roomManager.get(string4);
                if (gameManager == null) {
                    player.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u623f\u95f4 " + string4 + " \u4e0d\u5b58\u5728");
                    return;
                }
                if (mode == null) break;
                gameManager.startGame((CommandSender)player, mode);
                break;
            }
            case "stop": {
                if (!this.hasAdmin((CommandSender)player)) {
                    return;
                }
                if (stringArray.length < 3) {
                    player.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u7528\u6cd5: /box room stop <id>");
                    return;
                }
                GameManager gameManager = roomManager.get(stringArray[2]);
                if (gameManager == null) {
                    player.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u623f\u95f4 " + stringArray[2] + " \u4e0d\u5b58\u5728");
                    return;
                }
                gameManager.stopGame((CommandSender)player);
                break;
            }
            case "force": {
                if (!this.hasAdmin((CommandSender)player)) {
                    return;
                }
                if (stringArray.length < 4) {
                    player.sendMessage(this.plugin.msg("prefix") + "\u00a7e\u7528\u6cd5: /box room force <\u73a9\u5bb6> <\u623f\u95f4>");
                    return;
                }
                Player player3 = Bukkit.getPlayer((String)stringArray[2]);
                if (player3 == null) {
                    player.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u73a9\u5bb6\u4e0d\u5728\u7ebf: " + stringArray[2]);
                    return;
                }
                GameManager gameManager = roomManager.get(stringArray[3]);
                if (gameManager == null) {
                    player.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u623f\u95f4 " + stringArray[3] + " \u4e0d\u5b58\u5728");
                    return;
                }
                if (!gameManager.join(player3)) break;
                player.sendMessage(this.plugin.msg("prefix") + "\u00a7a\u5df2\u5f3a\u5236 \u00a7e" + player3.getName() + " \u00a7a\u52a0\u5165\u623f\u95f4 \u00a7e" + stringArray[3]);
                player3.sendMessage(this.plugin.msg("prefix") + "\u00a7e\u7ba1\u7406\u5458\u5c06\u4f60\u52a0\u5165\u4e86\u623f\u95f4 \u00a7b" + stringArray[3]);
                break;
            }
            case "status": {
                if (stringArray.length < 3) {
                    this.sendRoomList(player);
                    return;
                }
                if (!this.hasAdmin((CommandSender)player)) {
                    return;
                }
                this.sendRoomInfo(player, roomManager.get(stringArray[2]));
                break;
            }
            default: {
                this.sendRoomList(player);
            }
        }
    }

    public void createRoomFor(Player player, String string) {
        RoomManager roomManager = this.plugin.rooms();
        Object object = string;
        if (object == null || ((String)object).isBlank()) {
            int n = (int)(System.currentTimeMillis() % 1000L);
            object = "room_" + n;
            while (roomManager.hasRoom((String)object)) {
                object = "room_" + n++;
            }
        }
        if (roomManager.hasRoom((String)(object = ((String)object).toLowerCase(Locale.ROOT)))) {
            player.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u623f\u95f4 " + (String)object + " \u5df2\u5b58\u5728");
            return;
        }
        String string2 = this.plugin.worlds().world() != null ? this.plugin.worlds().world().getName() : null;
        GameManager gameManager = roomManager.createRoom((String)object, string2);
        gameManager.setOwner(player.getUniqueId());
        player.sendMessage(this.plugin.msg("prefix") + "\u00a7a\u5df2\u521b\u5efa\u5bf9\u5c40\u623f\u95f4 \u00a7e" + (String)object + " \u00a7a(\u4f60\u662f\u623f\u4e3b, \u53ef\u7528 /box room invite <\u73a9\u5bb6> \u9080\u8bf7)");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.3f);
    }

    private void invite(Player player, String[] stringArray) {
        if (stringArray.length < 2) {
            player.sendMessage(this.plugin.msg("prefix") + "\u00a7e\u7528\u6cd5: \u00a7f/box invite accept|decline \u00a77(\u70b9\u51fb\u804a\u5929\u6846\u9080\u8bf7\u6587\u672c\u5373\u53ef)");
            return;
        }
        switch (stringArray[1].toLowerCase(Locale.ROOT)) {
            case "accept": {
                this.plugin.invites().accept(player);
                break;
            }
            case "decline": {
                this.plugin.invites().decline(player);
                break;
            }
            default: {
                player.sendMessage(this.plugin.msg("prefix") + "\u00a7e\u7528\u6cd5: \u00a7f/box invite accept|decline");
            }
        }
    }

    private String myRoom(Player player) {
        Iterator<GameManager> iterator = this.plugin.rooms().joinedRooms(player.getUniqueId()).iterator();
        if (iterator.hasNext()) {
            GameManager gameManager = iterator.next();
            return gameManager.roomId();
        }
        return this.plugin.rooms().defaultRoom().roomId();
    }

    private void sendRoomInfo(Player player, GameManager gameManager) {
        if (gameManager == null) {
            player.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u623f\u95f4\u4e0d\u5b58\u5728");
            return;
        }
        player.sendMessage(this.plugin.msg("prefix") + "\u00a7e===== \u623f\u95f4 [" + gameManager.roomId() + "] =====");
        player.sendMessage(" \u00a77\u623f\u4e3b: " + (gameManager.owner() != null ? this.plugin.players().getOrCreate((UUID)gameManager.owner(), null).name : "\u7cfb\u7edf"));
        player.sendMessage(" \u00a77\u4e16\u754c: \u00a7b" + (gameManager.roomWorldName() != null ? gameManager.roomWorldName() : (gameManager.roomWorld() != null ? gameManager.roomWorld().getName() : "?")));
        player.sendMessage(" \u00a77\u6a21\u5f0f: " + gameManager.modeDisplay() + "  \u00a77\u72b6\u6001: " + gameManager.stateDisplay());
        player.sendMessage(" \u00a77\u53c2\u6218: \u00a7e" + gameManager.playerCount() + " \u00a77\u4eba  \u00a77\u5b58\u6d3b: \u00a7a" + gameManager.aliveCount() + " \u00a77\u4eba");
        StringBuilder stringBuilder = new StringBuilder();
        for (UUID uUID : gameManager.inGamePlayers()) {
            String string = this.plugin.players().getOrCreate((UUID)uUID, null).name;
            if (stringBuilder.length() > 0) {
                stringBuilder.append("\u00a77, ");
            }
            stringBuilder.append("\u00a7f").append(string);
        }
        player.sendMessage(" \u00a77\u6210\u5458: " + (stringBuilder.length() > 0 ? stringBuilder.toString() : "\u00a77(\u7a7a)"));
        player.sendMessage(" \u00a77\u52a0\u5165: \u00a7e/box room join " + gameManager.roomId() + " \u00a77| \u9000\u51fa: \u00a7e/box room leave " + gameManager.roomId());
    }

    private void sendRoomList(Player player) {
        RoomManager roomManager = this.plugin.rooms();
        player.sendMessage(this.plugin.msg("prefix") + "\u00a7e===== \u5728\u7ebf\u5bf9\u5c40\u623f\u95f4 =====");
        for (String string : roomManager.roomIds()) {
            GameManager gameManager = roomManager.get(string);
            if (gameManager == null) continue;
            String string2 = gameManager.owner() != null ? this.plugin.players().getOrCreate((UUID)gameManager.owner(), null).name : "\u7cfb\u7edf";
            int n = 0;
            StringBuilder stringBuilder = new StringBuilder();
            for (Player player2 : roomManager.onlinePlayersIn(string)) {
                if (n++ >= 8) continue;
                if (stringBuilder.length() > 0) {
                    stringBuilder.append("\u00a77,");
                }
                stringBuilder.append("\u00a7f").append(player2.getName());
            }
            player.sendMessage(" \u00a7e" + string + " \u00a77[\u623f\u4e3b " + string2 + "] -> \u00a7b" + (gameManager.roomWorldName() != null ? gameManager.roomWorldName() : (gameManager.roomWorld() != null ? gameManager.roomWorld().getName() : "?")) + " \u00a77\u72b6\u6001: " + gameManager.stateDisplay() + " \u00a77\u53c2\u6218: \u00a7e" + gameManager.playerCount() + (String)(stringBuilder.length() > 0 ? " \u00a77\u6210\u5458: " + String.valueOf(stringBuilder) : ""));
        }
        player.sendMessage(" \u00a77\u52a0\u5165: \u00a7e/box room join <id> \u00a77| \u5efa\u623f: \u00a7e/box room create <id> \u00a77| \u9080\u8bf7: \u00a7e/box room invite <\u73a9\u5bb6> [\u623f\u95f4]");
        if (player.hasPermission("terrabox.admin")) {
            player.sendMessage(" \u00a77\u7ba1\u7406\u5458: \u00a7e/box room start|stop|remove|force <\u73a9\u5bb6> <\u623f\u95f4>");
        }
    }

    private void handleWipe(CommandSender commandSender) {
        if (this.wipeArmed) {
            this.wipeArmed = false;
            commandSender.sendMessage(this.plugin.msg("prefix") + "\u00a7e\u6b63\u5728\u6e05\u7a7a\u5168\u90e8\u7269\u8d44\u7bb1...");
            this.plugin.boxes().wipeAll(() -> commandSender.sendMessage(this.plugin.msg("prefix") + "\u00a7a\u7269\u8d44\u7bb1\u5df2\u5168\u90e8\u6e05\u7a7a\u3002"));
        } else {
            this.wipeArmed = true;
            Bukkit.getGlobalRegionScheduler().runDelayed((Plugin)this.plugin, scheduledTask -> {
                this.wipeArmed = false;
            }, 200L);
            commandSender.sendMessage(this.plugin.msg("prefix") + "\u00a7e\u518d\u6b21\u8f93\u5165 /box admin wipe \u4ee5\u786e\u8ba4\u6e05\u7a7a\u5168\u90e8\u7269\u8d44\u7bb1 (10\u79d2\u5185)\u3002");
        }
    }

    public void sendTop(CommandSender commandSender) {
        this.plugin.players().topAsync(list -> {
            commandSender.sendMessage(this.plugin.msg("top-header"));
            if (list.isEmpty()) {
                commandSender.sendMessage(this.plugin.msg("prefix") + "\u00a77\u6682\u65e0\u6570\u636e, \u5feb\u53bb\u5f00\u7bb1\u5427!");
                return;
            }
            int n = 1;
            for (PlayerStore.TopEntry topEntry : list) {
                commandSender.sendMessage(this.plugin.msg("top-line").replace("{rank}", String.valueOf(n++)).replace("{player}", topEntry.name()).replace("{count}", String.valueOf(topEntry.count())));
            }
        });
    }

    public void sendStats(CommandSender commandSender, Player player) {
        PlayerStore.PlayerData playerData = this.plugin.players().getOrCreate(player.getUniqueId(), player.getName());
        commandSender.sendMessage(this.plugin.msg("prefix") + "\u00a7e===== " + playerData.name + " \u7684\u7269\u8d44\u7edf\u8ba1 =====");
        commandSender.sendMessage(" \u00a7f\u666e\u901a: \u00a77" + playerData.openedCommon.get() + "  \u00a7a\u7cbe\u826f: \u00a77" + playerData.openedRare.get() + "  \u00a7b\u7a00\u6709: \u00a77" + playerData.openedEpic.get());
        commandSender.sendMessage(" \u00a76\u4f20\u8bf4: \u00a77" + playerData.openedLegendary.get() + "  \u00a7d\u7edd\u4e16: \u00a77" + playerData.openedMythic.get() + "  \u00a7f\u5408\u8ba1: \u00a7a" + playerData.openedTotal());
        commandSender.sendMessage(" \u00a77\u7a7a\u6295\u641c\u522e: \u00a7d" + playerData.airdropLooted.get() + "  \u00a77\u5bfb\u5b9d\u6b21\u6570: \u00a7b" + playerData.huntCount.get() + "  \u00a77\u7d2f\u8ba1\u56de\u6536: \u00a7e" + playerData.soldValue.get() + " \u5143");
        commandSender.sendMessage(" \u00a77\u4f59\u989d: \u00a7e" + (long)this.plugin.econ().balance((OfflinePlayer)player) + " (" + this.plugin.econ().name() + ")");
    }

    private void sendPrices(CommandSender commandSender) {
        commandSender.sendMessage(this.plugin.msg("prefix") + "\u00a7e===== \u56de\u6536\u4ef7\u683c\u8868 (\u5143/\u4ef6) =====");
        ArrayList<Map.Entry<String, Double>> arrayList = new ArrayList<Map.Entry<String, Double>>(this.plugin.sellPrices().entrySet());
        arrayList.sort((entry, entry2) -> Double.compare((Double)entry2.getValue(), (Double)entry.getValue()));
        StringBuilder stringBuilder = new StringBuilder();
        for (Map.Entry entry3 : arrayList) {
            String string = "\u00a7f" + (String)entry3.getKey() + " \u00a7e" + String.valueOf(entry3.getValue()) + "  ";
            if (stringBuilder.length() + string.length() > 80) {
                commandSender.sendMessage(stringBuilder.toString());
                stringBuilder = new StringBuilder();
            }
            stringBuilder.append(string);
        }
        if (stringBuilder.length() > 0) {
            commandSender.sendMessage(stringBuilder.toString());
        }
    }

    private void sendBalance(CommandSender commandSender) {
        commandSender.sendMessage(this.plugin.msg("prefix") + "\u00a7e===== \u7269\u8d44\u5e73\u8861\u6027 =====");
        StringBuilder stringBuilder = new StringBuilder();
        for (Rarity rarity : Rarity.values()) {
            stringBuilder.append(rarity.colorCode.replace('&', '\u00a7')).append(rarity.display).append("\u00a77:").append(rarity.weight()).append("  ");
        }
        commandSender.sendMessage(" \u00a77\u4e94\u6863\u6743\u91cd: " + String.valueOf(stringBuilder));
        Map<Rarity, Integer> map = this.plugin.boxes().countByRarity();
        for (Rarity rarity : Rarity.values()) {
            commandSender.sendMessage(" \u00a77" + rarity.display + "\u7bb1: " + rarity.colorCode.replace('&', '\u00a7') + String.valueOf(map.getOrDefault((Object)rarity, 0)) + "\u00a77 \u4e2a");
        }
        commandSender.sendMessage(" \u00a77\u7bb1\u5b50\u603b\u6570/\u4e0a\u9650: \u00a7e" + this.plugin.boxes().count() + "\u00a77/\u00a7e" + this.plugin.getConfig().getInt("boxes.max-count", 320));
        commandSender.sendMessage(" \u00a77\u5237\u65b0\u5468\u671f: \u00a7e" + this.plugin.getConfig().getLong("boxes.refresh-minutes", 45L) + " \u00a77\u5206\u949f | \u6bcf\u5468\u671f\u8865\u5145: \u00a7e" + this.plugin.getConfig().getInt("boxes.refill-per-cycle", 8) + " \u00a77\u4e2a | \u6700\u5c0f\u8ddd\u79bb: \u00a7e" + this.plugin.getConfig().getDouble("boxes.min-distance", 18.0) + " \u683c");
        commandSender.sendMessage(" \u00a77\u9996\u6b21\u6295\u653e: \u00a7e" + this.plugin.getConfig().getInt("boxes.initial-fill", 150) + " \u00a77\u4e2a");
        if (this.plugin.specialItems() != null && this.plugin.specialItems().size() > 0) {
            commandSender.sendMessage(" \u00a77\u7279\u6b8a\u9053\u5177: \u00a7e" + this.plugin.specialItems().size() + " \u00a77\u79cd");
        }
        if (commandSender instanceof Player) {
            Player player = (Player)commandSender;
            commandSender.sendMessage(" \u00a77\u5269\u4f59\u7a7a\u6295: \u00a7e\u7ea6 " + (this.plugin.airdrops().secondsUntilNext() / 60L + 1L) + " \u5206\u949f\u540e");
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage(this.plugin.msg("prefix") + "\u00a7e===== \u7269\u8d44\u5927\u9646 \u5e2e\u52a9 =====");
        player.sendMessage(" \u00a7f/box \u00a77- \u6253\u5f00\u4e3b\u83dc\u5355");
        player.sendMessage(" \u00a7f/box spawn \u00a77- \u4f20\u9001\u5230\u968f\u673a\u9646\u5730\u51fa\u751f\u70b9 (\u6709\u51b7\u5374)");
        player.sendMessage(" \u00a7f/box sell \u00a77- \u6253\u5f00\u7269\u8d44\u56de\u6536\u5546\u5e97");
        player.sendMessage(" \u00a7f/box prices \u00a77- \u67e5\u770b\u56de\u6536\u4ef7\u683c\u8868");
        player.sendMessage(" \u00a7f/box balance \u00a77- \u67e5\u770b\u7269\u8d44\u5e73\u8861\u6027");
        player.sendMessage(" \u00a7f/box hunt \u00a77- \u82b1\u94b1\u8d2d\u4e70\u9ad8\u7a00\u6709\u7bb1\u65b9\u4f4d\u63d0\u793a");
        player.sendMessage(" \u00a7f/box top \u00a77- \u5f00\u7bb1\u6392\u884c\u699c");
        player.sendMessage(" \u00a7f/box stats \u00a77- \u6211\u7684\u7edf\u8ba1");
        player.sendMessage(" \u00a7f/box join \u00a77- \u62a5\u540d\u53c2\u52a0\u5bf9\u5c40");
        player.sendMessage(" \u00a7f/box lobby \u00a77- \u8fd4\u56de\u5927\u5385");
        player.sendMessage(" \u00a7f/box spectate \u00a77- \u5bf9\u5c40\u6dd8\u6c70\u540e\u65c1\u89c2");
        player.sendMessage(" \u00a7f/box storm \u00a77- \u67e5\u770b\u6bd2\u5708\u72b6\u6001");
        player.sendMessage(" \u00a7f/box hunt \u00a77- \u82b1\u94b1\u8d2d\u4e70\u9ad8\u7a00\u6709\u7269\u8d44\u7bb1\u65b9\u4f4d");
        player.sendMessage(" \u00a7f/box room \u00a77- \u5bf9\u5c40\u623f\u95f4 (\u67e5\u770b/\u521b\u5efa/\u9080\u8bf7/\u52a0\u5165)");
        player.sendMessage(" \u00a7f/box game status \u00a77- \u5bf9\u5c40\u72b6\u6001");
        if (player.hasPermission("terrabox.admin")) {
            player.sendMessage(" \u00a7c/box game start <solo|pvp|team> \u00a77- \u5f00\u59cb\u5bf9\u5c40 (\u5355\u4eba\u6a21\u5f0f\u4efb\u610f\u4eba\u6570\u53ef\u5f00)");
            player.sendMessage(" \u00a7c/box game stop \u00a77- \u7ec8\u6b62\u5bf9\u5c40");
            player.sendMessage(" \u00a7c/box room list \u00a77- \u5bf9\u5c40\u623f\u95f4\u5217\u8868");
            player.sendMessage(" \u00a7c/box room create <id> \u00a77- \u521b\u5efa\u591a\u4e16\u754c\u5bf9\u5c40\u623f\u95f4");
            player.sendMessage(" \u00a7c/box room start <id> <solo|pvp|team> \u00a77- \u5728\u67d0\u623f\u95f4\u5f00\u5bf9\u5c40");
            player.sendMessage(" \u00a7c/box room stop <id> \u00a77- \u7ec8\u6b62\u67d0\u623f\u95f4\u5bf9\u5c40");
            player.sendMessage(" \u00a7c/box room force <\u73a9\u5bb6> <\u623f\u95f4> \u00a77- \u5f3a\u5236\u628a\u73a9\u5bb6\u52a0\u5165\u623f\u95f4");
            player.sendMessage(" \u00a7c/box terrain \u00a77- \u9009\u62e9\u5bf9\u5c40\u5730\u5f62 (\u9ed8\u8ba4/\u6c99\u6f20/\u5927\u5c9b\u5c7f)");
            player.sendMessage(" \u00a7c/box admin boxes|fill|airdrop|wipe \u00a77- \u7ba1\u7406");
            player.sendMessage(" \u00a7c/box reload \u00a77- \u91cd\u8f7d\u914d\u7f6e");
        }
    }

    private boolean hasAdmin(CommandSender commandSender) {
        if (!commandSender.hasPermission("terrabox.admin")) {
            commandSender.sendMessage(this.plugin.msg("no-permission"));
            return false;
        }
        return true;
    }

    public List<String> onTabComplete(CommandSender commandSender, Command command, String string, String[] stringArray) {
        ArrayList<String> arrayList;
        block21: {
            block27: {
                block26: {
                    block25: {
                        block24: {
                            block23: {
                                block22: {
                                    block20: {
                                        arrayList = new ArrayList<String>();
                                        if (stringArray.length != 1) break block20;
                                        ArrayList<String> arrayList2 = new ArrayList<String>(List.of("spawn", "sell", "hunt", "top", "stats", "prices", "balance", "join", "game", "help", "lobby", "spectate", "storm", "room", "invite"));
                                        if (commandSender.hasPermission("terrabox.admin")) {
                                            arrayList2.add("admin");
                                            arrayList2.add("reload");
                                            arrayList2.add("terrain");
                                        }
                                        for (String string2 : arrayList2) {
                                            if (!string2.startsWith(stringArray[0].toLowerCase())) continue;
                                            arrayList.add(string2);
                                        }
                                        break block21;
                                    }
                                    if (stringArray.length != 2 || !stringArray[0].equalsIgnoreCase("game")) break block22;
                                    for (String string3 : List.of("join", "status", "start", "stop")) {
                                        if (!string3.startsWith(stringArray[1].toLowerCase())) continue;
                                        arrayList.add(string3);
                                    }
                                    break block21;
                                }
                                if (stringArray.length != 3 || !stringArray[0].equalsIgnoreCase("game") || !stringArray[1].equalsIgnoreCase("start") || !commandSender.hasPermission("terrabox.admin")) break block23;
                                for (String string4 : List.of("solo", "pvp", "team")) {
                                    if (!string4.startsWith(stringArray[2].toLowerCase())) continue;
                                    arrayList.add(string4);
                                }
                                break block21;
                            }
                            if (stringArray.length != 2 || !stringArray[0].equalsIgnoreCase("admin") || !commandSender.hasPermission("terrabox.admin")) break block24;
                            for (String string5 : List.of("boxes", "fill", "airdrop", "wipe")) {
                                if (!string5.startsWith(stringArray[1].toLowerCase())) continue;
                                arrayList.add(string5);
                            }
                            break block21;
                        }
                        if (stringArray.length != 2 || !stringArray[0].equalsIgnoreCase("invite")) break block25;
                        for (String string6 : List.of("accept", "decline")) {
                            if (!string6.startsWith(stringArray[1].toLowerCase())) continue;
                            arrayList.add(string6);
                        }
                        break block21;
                    }
                    if (stringArray.length != 2 || !stringArray[0].equalsIgnoreCase("room")) break block26;
                    ArrayList<String> arrayList3 = new ArrayList<String>(List.of("list", "join", "leave", "invite", "info", "create"));
                    if (commandSender.hasPermission("terrabox.admin")) {
                        arrayList3.add("start");
                        arrayList3.add("stop");
                        arrayList3.add("remove");
                        arrayList3.add("force");
                        arrayList3.add("status");
                    }
                    for (String string7 : arrayList3) {
                        if (!string7.startsWith(stringArray[1].toLowerCase())) continue;
                        arrayList.add(string7);
                    }
                    break block21;
                }
                if (stringArray.length < 3 || !stringArray[0].equalsIgnoreCase("room")) break block27;
                String string8 = stringArray[1].toLowerCase(Locale.ROOT);
                if (string8.equals("join") || string8.equals("leave") || string8.equals("info") || string8.equals("start") || string8.equals("stop") || string8.equals("remove") || string8.equals("status") || string8.equals("force")) {
                    if (string8.equals("force")) {
                        if (stringArray.length == 3) {
                            for (Player player : Bukkit.getOnlinePlayers()) {
                                if (!player.getName().toLowerCase().startsWith(stringArray[2].toLowerCase())) continue;
                                arrayList.add(player.getName());
                            }
                            return arrayList;
                        }
                        if (stringArray.length == 4) {
                            for (String string9 : this.plugin.rooms().roomIds()) {
                                if (!string9.toLowerCase().startsWith(stringArray[3].toLowerCase())) continue;
                                arrayList.add(string9);
                            }
                            return arrayList;
                        }
                    }
                    String string10 = stringArray[stringArray.length - 1].toLowerCase(Locale.ROOT);
                    for (String string11 : this.plugin.rooms().roomIds()) {
                        if (!string11.toLowerCase().startsWith(string10)) continue;
                        arrayList.add(string11);
                    }
                    return arrayList;
                }
                if (string8.equals("invite")) {
                    String string12 = stringArray[stringArray.length - 1].toLowerCase(Locale.ROOT);
                    for (Player object : Bukkit.getOnlinePlayers()) {
                        if (!object.getName().toLowerCase().startsWith(string12)) continue;
                        arrayList.add(object.getName());
                    }
                    if (stringArray.length == 4) {
                        for (String string2 : this.plugin.rooms().roomIds()) {
                            if (!string2.toLowerCase().startsWith(string12)) continue;
                            arrayList.add(string2);
                        }
                    }
                    return arrayList;
                }
                break block21;
            }
            if (stringArray.length != 3 || !stringArray[0].equalsIgnoreCase("room") || !stringArray[1].equalsIgnoreCase("start") || !commandSender.hasPermission("terrabox.admin")) break block21;
            for (String string13 : List.of("solo", "pvp", "team")) {
                if (!string13.startsWith(stringArray[2].toLowerCase())) continue;
                arrayList.add(string13);
            }
        }
        return arrayList;
    }
}
