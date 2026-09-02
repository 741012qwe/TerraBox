package com.terrabox;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对局房间邀请系统 —— 房间主人邀请在线玩家加入自己的房间
 *
 * 玩法:
 *  - 房间 owner 通过指令/GUI 邀请在线玩家 → 被邀请者收到可点击文本 "[接受] [拒绝]"
 *  - 点击接受: 执行 /box invite accept → 玩家加入对应房间报名; 拒绝取消
 *  - 邀请 30 秒未处理自动过期; 每玩家同一时刻仅保留一个待处理邀请
 *
 * 线程模型: 邀请由命令/GUI 在玩家区域线程调用; 发送可点击文本安全 (sendMessage 跨线程);
 *   邀请状态用并发 Map 存储; 过期清理惰性(每次邀请时清理已过期)。
 */
public class InviteManager {
    private final TerraBoxPlugin plugin;
    private final Map<UUID, Invite> invites = new ConcurrentHashMap<>();
    private static final long TTL = 30_000L; // 30秒有效期

    /** 邀请定义 */
    private record Invite(UUID owner, UUID target, String roomId, long expires) {}

    public InviteManager(TerraBoxPlugin plugin) {
        this.plugin = plugin;
    }

    /** 邀请某玩家加入房间 (房间 owner 调用) */
    public boolean invite(Player owner, Player target, String roomId) {
        if (owner == null || target == null) return false;
        if (!plugin.rooms().hasRoom(roomId)) {
            owner.sendMessage(plugin.msg("prefix") + "§c房间 " + roomId + " 不存在。");
            return false;
        }
        GameManager rg = plugin.rooms().get(roomId);
        if (rg != null && rg.owner() != null && !rg.owner().equals(owner.getUniqueId())) {
            owner.sendMessage(plugin.msg("prefix") + "§c你不是房间 §e" + roomId + " §c的房主, 无法邀请。");
            return false;
        }
        if (owner.getUniqueId().equals(target.getUniqueId())) {
            owner.sendMessage(plugin.msg("prefix") + "§c不能邀请自己。");
            return false;
        }
        if (target.getUniqueId().equals(owner.getUniqueId())) return false;
        if (plugin.rooms().isInGame(target.getUniqueId())) {
            owner.sendMessage(plugin.msg("prefix") + "§c" + target.getName() + " 正在对局中, 无法邀请。");
            return false;
        }
        // 清理过期
        cleanExpired();
        invites.put(target.getUniqueId(), new Invite(owner.getUniqueId(), target.getUniqueId(), roomId,
                System.currentTimeMillis() + TTL));
        // 通知被邀请者 (可点击文本)
        sendInviteMessage(owner, target, roomId);
        owner.sendMessage(plugin.msg("prefix") + "§a已邀请 §e" + target.getName() + " §a加入房间 §e"
                + roomId + "§a, 等待回应...");
        owner.playSound(owner.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.4f);
        return true;
    }

    /** 发送可点击的邀请文本到被邀请者 */
    private void sendInviteMessage(Player owner, Player target, String roomId) {
        String prefix = plugin.msg("prefix");
        Component msg = LegacyComponentSerializer.legacyAmpersand().deserialize(
                prefix + "§e" + owner.getName() + " §7邀请你加入对局房间 §b" + roomId + "§7。");
        Component accept = LegacyComponentSerializer.legacyAmpersand().deserialize("§a[ 接受 ]")
                .clickEvent(ClickEvent.runCommand("/box invite accept"))
                .hoverEvent(HoverEvent.showText(LegacyComponentSerializer.legacyAmpersand().deserialize("§a点击接受邀请")));
        Component decline = LegacyComponentSerializer.legacyAmpersand().deserialize("§c[ 拒绝 ]")
                .clickEvent(ClickEvent.runCommand("/box invite decline"))
                .hoverEvent(HoverEvent.showText(LegacyComponentSerializer.legacyAmpersand().deserialize("§c点击拒绝邀请")));
        Component msg2 = LegacyComponentSerializer.legacyAmpersand().deserialize("  ");
        target.sendMessage(msg.append(msg2).append(accept).append(msg2).append(decline));
        target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.5f);
    }

    /** 接受最近的有效邀请: 来源房间 + 把玩家加入报名 */
    public void accept(Player p) {
        Invite inv = invites.remove(p.getUniqueId());
        if (inv == null || System.currentTimeMillis() > inv.expires()) {
            p.sendMessage(plugin.msg("prefix") + "§c当前没有待处理的邀请, 或邀请已过期。");
            return;
        }
        GameManager g = plugin.rooms().get(inv.roomId());
        if (g == null) {
            p.sendMessage(plugin.msg("prefix") + "§c邀请的房间已不存在。");
            return;
        }
        if (g.isRunning()) {
            p.sendMessage(plugin.msg("prefix") + "§c该房间对局已开始, 无法加入。");
            return;
        }
        if (g.join(p)) {
            Player owner = Bukkit.getPlayer(inv.owner());
            if (owner != null && owner.isOnline()) {
                owner.sendMessage(plugin.msg("prefix") + "§a" + p.getName() + " §a接受了邀请, 已加入房间 §e"
                        + inv.roomId() + "§a!");
                owner.playSound(owner.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.3f);
            }
        }
    }

    /** 拒绝邀请 */
    public void decline(Player p) {
        Invite inv = invites.remove(p.getUniqueId());
        if (inv == null) {
            p.sendMessage(plugin.msg("prefix") + "§c当前没有待处理的邀请。");
            return;
        }
        p.sendMessage(plugin.msg("prefix") + "§7已拒绝房间邀请。");
        Player owner = Bukkit.getPlayer(inv.owner());
        if (owner != null && owner.isOnline()) {
            owner.sendMessage(plugin.msg("prefix") + "§c" + p.getName() + " §c拒绝了你的邀请。");
        }
    }

    /** 移除某玩家所有邀请 (退出/离线/对局开始等) */
    public void clear(UUID uuid) {
        invites.remove(uuid);
        // 移除别人发给该玩家的邀请
        invites.entrySet().removeIf(e -> e.getValue().target().equals(uuid)
                || e.getValue().owner().equals(uuid));
    }

    /** 清理过期邀请 */
    private void cleanExpired() {
        long now = System.currentTimeMillis();
        invites.entrySet().removeIf(e -> e.getValue().expires() < now);
    }

    /** 是否有待处理邀请 (调试/校验用) */
    public boolean hasPending(UUID uuid) {
        return invites.containsKey(uuid);
    }
}
