/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.TextComponent
 *  net.kyori.adventure.text.event.ClickEvent
 *  net.kyori.adventure.text.event.HoverEvent
 *  net.kyori.adventure.text.event.HoverEventSource
 *  net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
 *  org.bukkit.Bukkit
 *  org.bukkit.Sound
 *  org.bukkit.entity.Player
 */
package com.terrabox;

import com.terrabox.GameManager;
import com.terrabox.TerraBoxPlugin;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.event.HoverEventSource;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class InviteManager {
    private final TerraBoxPlugin plugin;
    private final Map<UUID, Invite> invites = new ConcurrentHashMap<UUID, Invite>();
    private static final long TTL = 30000L;

    public InviteManager(TerraBoxPlugin terraBoxPlugin) {
        this.plugin = terraBoxPlugin;
    }

    public boolean invite(Player player, Player player2, String string) {
        if (player == null || player2 == null) {
            return false;
        }
        if (!this.plugin.rooms().hasRoom(string)) {
            player.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u623f\u95f4 " + string + " \u4e0d\u5b58\u5728\u3002");
            return false;
        }
        GameManager gameManager = this.plugin.rooms().get(string);
        if (gameManager != null && gameManager.owner() != null && !gameManager.owner().equals(player.getUniqueId())) {
            player.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u4f60\u4e0d\u662f\u623f\u95f4 \u00a7e" + string + " \u00a7c\u7684\u623f\u4e3b, \u65e0\u6cd5\u9080\u8bf7\u3002");
            return false;
        }
        if (player.getUniqueId().equals(player2.getUniqueId())) {
            player.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u4e0d\u80fd\u9080\u8bf7\u81ea\u5df1\u3002");
            return false;
        }
        if (player2.getUniqueId().equals(player.getUniqueId())) {
            return false;
        }
        if (this.plugin.rooms().isInGame(player2.getUniqueId())) {
            player.sendMessage(this.plugin.msg("prefix") + "\u00a7c" + player2.getName() + " \u6b63\u5728\u5bf9\u5c40\u4e2d, \u65e0\u6cd5\u9080\u8bf7\u3002");
            return false;
        }
        this.cleanExpired();
        this.invites.put(player2.getUniqueId(), new Invite(player.getUniqueId(), player2.getUniqueId(), string, System.currentTimeMillis() + 30000L));
        this.sendInviteMessage(player, player2, string);
        player.sendMessage(this.plugin.msg("prefix") + "\u00a7a\u5df2\u9080\u8bf7 \u00a7e" + player2.getName() + " \u00a7a\u52a0\u5165\u623f\u95f4 \u00a7e" + string + "\u00a7a, \u7b49\u5f85\u56de\u5e94...");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.4f);
        return true;
    }

    private void sendInviteMessage(Player player, Player player2, String string) {
        String string2 = this.plugin.msg("prefix");
        TextComponent textComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(string2 + "\u00a7e" + player.getName() + " \u00a77\u9080\u8bf7\u4f60\u52a0\u5165\u5bf9\u5c40\u623f\u95f4 \u00a7b" + string + "\u00a77\u3002");
        Component component = ((TextComponent)LegacyComponentSerializer.legacyAmpersand().deserialize("\u00a7a[ \u63a5\u53d7 ]").clickEvent(ClickEvent.runCommand((String)"/box invite accept"))).hoverEvent((HoverEventSource)HoverEvent.showText((Component)LegacyComponentSerializer.legacyAmpersand().deserialize("\u00a7a\u70b9\u51fb\u63a5\u53d7\u9080\u8bf7")));
        Component component2 = ((TextComponent)LegacyComponentSerializer.legacyAmpersand().deserialize("\u00a7c[ \u62d2\u7edd ]").clickEvent(ClickEvent.runCommand((String)"/box invite decline"))).hoverEvent((HoverEventSource)HoverEvent.showText((Component)LegacyComponentSerializer.legacyAmpersand().deserialize("\u00a7c\u70b9\u51fb\u62d2\u7edd\u9080\u8bf7")));
        TextComponent textComponent2 = LegacyComponentSerializer.legacyAmpersand().deserialize("  ");
        player2.sendMessage(textComponent.append((Component)textComponent2).append(component).append((Component)textComponent2).append(component2));
        player2.playSound(player2.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.5f);
    }

    public void accept(Player player) {
        Player player2;
        Invite invite = this.invites.remove(player.getUniqueId());
        if (invite == null || System.currentTimeMillis() > invite.expires()) {
            player.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u5f53\u524d\u6ca1\u6709\u5f85\u5904\u7406\u7684\u9080\u8bf7, \u6216\u9080\u8bf7\u5df2\u8fc7\u671f\u3002");
            return;
        }
        GameManager gameManager = this.plugin.rooms().get(invite.roomId());
        if (gameManager == null) {
            player.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u9080\u8bf7\u7684\u623f\u95f4\u5df2\u4e0d\u5b58\u5728\u3002");
            return;
        }
        if (gameManager.isRunning()) {
            player.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u8be5\u623f\u95f4\u5bf9\u5c40\u5df2\u5f00\u59cb, \u65e0\u6cd5\u52a0\u5165\u3002");
            return;
        }
        if (gameManager.join(player) && (player2 = Bukkit.getPlayer((UUID)invite.owner())) != null && player2.isOnline()) {
            player2.sendMessage(this.plugin.msg("prefix") + "\u00a7a" + player.getName() + " \u00a7a\u63a5\u53d7\u4e86\u9080\u8bf7, \u5df2\u52a0\u5165\u623f\u95f4 \u00a7e" + invite.roomId() + "\u00a7a!");
            player2.playSound(player2.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.3f);
        }
    }

    public void decline(Player player) {
        Invite invite = this.invites.remove(player.getUniqueId());
        if (invite == null) {
            player.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u5f53\u524d\u6ca1\u6709\u5f85\u5904\u7406\u7684\u9080\u8bf7\u3002");
            return;
        }
        player.sendMessage(this.plugin.msg("prefix") + "\u00a77\u5df2\u62d2\u7edd\u623f\u95f4\u9080\u8bf7\u3002");
        Player player2 = Bukkit.getPlayer((UUID)invite.owner());
        if (player2 != null && player2.isOnline()) {
            player2.sendMessage(this.plugin.msg("prefix") + "\u00a7c" + player.getName() + " \u00a7c\u62d2\u7edd\u4e86\u4f60\u7684\u9080\u8bf7\u3002");
        }
    }

    public void clear(UUID uUID) {
        this.invites.remove(uUID);
        this.invites.entrySet().removeIf(entry -> ((Invite)entry.getValue()).target().equals(uUID) || ((Invite)entry.getValue()).owner().equals(uUID));
    }

    private void cleanExpired() {
        long l = System.currentTimeMillis();
        this.invites.entrySet().removeIf(entry -> ((Invite)entry.getValue()).expires() < l);
    }

    public boolean hasPending(UUID uUID) {
        return this.invites.containsKey(uUID);
    }

    private record Invite(UUID owner, UUID target, String roomId, long expires) {
    }
}
