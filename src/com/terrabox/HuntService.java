package com.terrabox;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 寻宝玩法: 花费货币换取某个高稀有度物资箱的方位提示 (八方位 + 距离)
 * 线程模型: 只读注册表快照 + 纯计算, 事件/命令线程直接执行
 */
public class HuntService {
    private final TerraBoxPlugin plugin;

    public HuntService(TerraBoxPlugin plugin) {
        this.plugin = plugin;
    }

    public void hunt(Player p) {
        double cost = plugin.getConfig().getDouble("hunt.cost", 120);
        if (cost > 0 && !plugin.econ().withdraw(p, cost)) {
            p.sendMessage(plugin.msg("prefix") + "§c货币不足, 寻宝需要 §e" + (long) cost + " §c元。");
            return;
        }
        List<Rarity> targets = new ArrayList<>();
        for (String s : plugin.getConfig().getStringList("hunt.rarities")) {
            Rarity r = Rarity.parse(s);
            if (r != null) targets.add(r);
        }
        if (targets.isEmpty()) targets = List.of(Rarity.EPIC, Rarity.LEGENDARY);
        BoxManager.BoxEntry e = plugin.boxes().randomOf(targets);
        if (e == null) {
            if (cost > 0) plugin.econ().deposit(p, cost);
            p.sendMessage(plugin.msg("hunt-none"));
            return;
        }
        plugin.players().getOrCreate(p.getUniqueId(), p.getName()).huntCount.incrementAndGet();
        int dx = e.x - p.getLocation().getBlockX();
        int dz = e.z - p.getLocation().getBlockZ();
        int dist = (int) Math.sqrt((double) dx * dx + (double) dz * dz);
        p.sendMessage(plugin.msg("hunt-found")
                .replace("{world}", plugin.worlds().world() != null ? plugin.worlds().world().getName() : "?")
                .replace("{direction}", direction(dx, dz))
                .replace("{distance}", String.valueOf(dist)));
        plugin.players().getOrCreate(p.getUniqueId(), p.getName()).touch();
    }

    /** Minecraft 方位: 北=-Z, 东=+X */
    private String direction(int dx, int dz) {
        String[] names = {"北", "东北", "东", "东南", "南", "西南", "西", "西北"};
        double deg = Math.toDegrees(Math.atan2(dx, -dz));
        deg = (deg + 360) % 360;
        int idx = (int) Math.floor((deg + 22.5) / 45.0) % 8;
        return names[idx] + "方向(" + (int) deg + "°)";
    }
}
