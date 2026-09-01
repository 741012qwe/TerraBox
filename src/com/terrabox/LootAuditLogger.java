package com.terrabox;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 道具审计日志器: 记录所有道具生成和分配事件
 * 
 * 线程安全: 所有方法可在任意线程调用 (CHM + CopyOnWriteArrayList + AtomicLong)
 * 
 * 日志策略: 不再逐条打印每个箱子的投放日志 (避免大量箱子时刷屏),
 *           改为仅在批次完成时通过 BoxManager 打印一条汇总。
 */
public class LootAuditLogger {
    private final TerraBoxPlugin plugin;
    // 系统级(无玩家)生成事件所用的哨兵 key —— 避免 null 作为 ConcurrentHashMap key(白皮书: CHM 禁止 null key)
    public static final UUID SYSTEM = new UUID(0L, 0L);
    private final ConcurrentHashMap<UUID, List<LootAuditEntry>> auditLog = new ConcurrentHashMap<>();
    private final AtomicLong totalLootGenerated = new AtomicLong(0);
    private final AtomicLong totalLootDistributed = new AtomicLong(0);
    
    public LootAuditLogger(TerraBoxPlugin plugin) {
        this.plugin = plugin;
    }
    
    /** 规范化玩家 key: null/无玩家 → SYSTEM 哨兵, 避免 CHM null key NPE */
    private UUID safeKey(UUID playerUUID) {
        return playerUUID == null ? SYSTEM : playerUUID;
    }
    
    /**
     * 记录物资箱道具生成事件 (聚合计数, 不逐条打印日志)
     * @param playerUUID 玩家 UUID (无玩家用 SYSTEM)
     * @param totalStacks 该次投放的总堆数
     */
    public void logBoxGeneration(UUID playerUUID, int totalStacks) {
        UUID key = safeKey(playerUUID);
        totalLootGenerated.addAndGet(totalStacks);
        // 不逐条打印日志, 由 BoxManager 在批次结束时打印汇总
    }
    
    /**
     * 记录玩家开箱事件
     */
    public void logPlayerOpening(UUID playerUUID, Rarity rarity, ItemStack item, int amount, long timestamp) {
        UUID key = safeKey(playerUUID);
        LootAuditEntry entry = new LootAuditEntry(
            key, rarity, item.getType(), amount, timestamp,
            "player_opening", "玩家开箱"
        );
        auditLog.computeIfAbsent(key, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(entry);
        totalLootDistributed.addAndGet(amount);
    }
    
    /**
     * 记录道具异常生成事件
     */
    public void logAnomaly(UUID playerUUID, Rarity rarity, ItemStack item, int amount, String reason, long timestamp) {
        UUID key = safeKey(playerUUID);
        LootAuditEntry entry = new LootAuditEntry(
            key, rarity, item.getType(), amount, timestamp,
            "anomaly", "异常生成: " + reason
        );
        auditLog.computeIfAbsent(key, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(entry);
        plugin.getLogger().warning("[审计] 异常生成: 稀有度=" + rarity.display
                + ", 物品=" + item.getType() + ", 数量=" + amount + ", 原因=" + reason);
    }
    
    /**
     * 获取玩家审计日志
     */
    public List<LootAuditEntry> getPlayerAuditLog(UUID playerUUID) {
        return auditLog.getOrDefault(safeKey(playerUUID), new java.util.concurrent.CopyOnWriteArrayList<>());
    }
    
    /** 获取总生成道具数量 */
    public long getTotalLootGenerated() { return totalLootGenerated.get(); }
    
    /** 获取总分配道具数量 */
    public long getTotalLootDistributed() { return totalLootDistributed.get(); }
    
    /**
     * 生成审计报告
     */
    public String generateAuditReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== 道具审计报告 ===\n");
        report.append("总生成道具数量: ").append(totalLootGenerated.get()).append("\n");
        report.append("总分配道具数量: ").append(totalLootDistributed.get()).append("\n");
        long gen = totalLootGenerated.get();
        if (gen > 0) {
            report.append("生成/分配比例: ").append(String.format("%.2f", (double) totalLootDistributed.get() / gen)).append("\n\n");
        }
        report.append("玩家审计统计:\n");
        for (UUID playerUUID : auditLog.keySet()) {
            List<LootAuditEntry> entries = auditLog.get(playerUUID);
            long playerGenerated = entries.stream().filter(e -> e.eventType.equals("box_generation")).mapToLong(e -> e.amount).sum();
            long playerDistributed = entries.stream().filter(e -> e.eventType.equals("player_opening")).mapToLong(e -> e.amount).sum();
            report.append("玩家 ").append(playerUUID).append(": \n");
            report.append("  生成道具: ").append(playerGenerated).append("\n");
            report.append("  分配道具: ").append(playerDistributed).append("\n");
            if (playerGenerated > 0)
                report.append("  比例: ").append(String.format("%.2f", (double) playerDistributed / playerGenerated)).append("\n");
        }
        return report.toString();
    }
    
    /** 道具审计条目 */
    private static class LootAuditEntry {
        final UUID playerUUID;
        final Rarity rarity;
        final Material material;
        final int amount;
        final long timestamp;
        final String eventType;
        final String description;
        
        LootAuditEntry(UUID playerUUID, Rarity rarity, Material material, int amount, long timestamp, String eventType, String description) {
            this.playerUUID = playerUUID;
            this.rarity = rarity;
            this.material = material;
            this.amount = amount;
            this.timestamp = timestamp;
            this.eventType = eventType;
            this.description = description;
        }
    }
}
