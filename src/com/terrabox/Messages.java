package com.terrabox;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * 统一消息发送工具
 * 自动处理前缀、颜色代码转换和格式参数
 */
public class Messages {
    private final TerraBoxPlugin plugin;
    
    public Messages(TerraBoxPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * 发送带前缀的消息
     */
    public void sendPrefix(CommandSender sender, String message) {
        String prefix = plugin.getConfig().getString("prefix", "");
        if (prefix.isEmpty()) {
            prefix = "&b物资大陆 &8| ";
        }
        sender.sendMessage(convert(prefix + message));
    }
    
    /**
     * 发送带前缀的消息（玩家专属，显示插件名）
     */
    public void sendToPlayer(Player player, String message) {
        String prefix = plugin.getConfig().getString("prefix", "");
        if (prefix.isEmpty()) {
            prefix = "&b[物资大陆]&r ";
        }
        player.sendMessage(plugin.convertColorCode(prefix + message));
    }
    
    /**
     * 格式化消息（替换%key%为值）
     */
    public String format(String message, Object... args) {
        String result = convert(message);
        for (int i = 0; i < args.length; i += 2) {
            if (i + 1 < args.length) {
                result = result.replace("%" + args[i] + "%", String.valueOf(args[i + 1]));
            }
        }
        return result;
    }
    
    /**
     * 转换颜色代码（& → §）
     */
    public String convert(String text) {
        if (text == null) return null;
        return plugin.convertColorCode(text);
    }
    
    /**
     * 发送组件消息
     */
    public void sendComponent(CommandSender sender, Component component) {
        plugin.send(sender, component);
    }
}
