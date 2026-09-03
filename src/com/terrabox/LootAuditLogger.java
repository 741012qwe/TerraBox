/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Material
 *  org.bukkit.inventory.ItemStack
 */
package com.terrabox;

import com.terrabox.Rarity;
import com.terrabox.TerraBoxPlugin;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class LootAuditLogger {
    private final TerraBoxPlugin plugin;
    public static final UUID SYSTEM = new UUID(0L, 0L);
    private final ConcurrentHashMap<UUID, List<LootAuditEntry>> auditLog = new ConcurrentHashMap();
    private final AtomicLong totalLootGenerated = new AtomicLong(0L);
    private final AtomicLong totalLootDistributed = new AtomicLong(0L);

    public LootAuditLogger(TerraBoxPlugin terraBoxPlugin) {
        this.plugin = terraBoxPlugin;
    }

    private UUID safeKey(UUID uUID) {
        return uUID == null ? SYSTEM : uUID;
    }

    public void logBoxGeneration(UUID uUID, int n) {
        UUID uUID2 = this.safeKey(uUID);
        this.totalLootGenerated.addAndGet(n);
    }

    public void logPlayerOpening(UUID uUID2, Rarity rarity, ItemStack itemStack, int n, long l) {
        UUID uUID3 = this.safeKey(uUID2);
        LootAuditEntry lootAuditEntry = new LootAuditEntry(uUID3, rarity, itemStack.getType(), n, l, "player_opening", "\u73a9\u5bb6\u5f00\u7bb1");
        this.auditLog.computeIfAbsent(uUID3, uUID -> new CopyOnWriteArrayList()).add(lootAuditEntry);
        this.totalLootDistributed.addAndGet(n);
    }

    public void logAnomaly(UUID uUID2, Rarity rarity, ItemStack itemStack, int n, String string, long l) {
        UUID uUID3 = this.safeKey(uUID2);
        LootAuditEntry lootAuditEntry = new LootAuditEntry(uUID3, rarity, itemStack.getType(), n, l, "anomaly", "\u5f02\u5e38\u751f\u6210: " + string);
        this.auditLog.computeIfAbsent(uUID3, uUID -> new CopyOnWriteArrayList()).add(lootAuditEntry);
        this.plugin.getLogger().warning("[\u5ba1\u8ba1] \u5f02\u5e38\u751f\u6210: \u7a00\u6709\u5ea6=" + rarity.display + ", \u7269\u54c1=" + String.valueOf(itemStack.getType()) + ", \u6570\u91cf=" + n + ", \u539f\u56e0=" + string);
    }

    public List<LootAuditEntry> getPlayerAuditLog(UUID uUID) {
        return this.auditLog.getOrDefault(this.safeKey(uUID), new CopyOnWriteArrayList());
    }

    public long getTotalLootGenerated() {
        return this.totalLootGenerated.get();
    }

    public long getTotalLootDistributed() {
        return this.totalLootDistributed.get();
    }

    public String generateAuditReport() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("=== \u9053\u5177\u5ba1\u8ba1\u62a5\u544a ===\n");
        stringBuilder.append("\u603b\u751f\u6210\u9053\u5177\u6570\u91cf: ").append(this.totalLootGenerated.get()).append("\n");
        stringBuilder.append("\u603b\u5206\u914d\u9053\u5177\u6570\u91cf: ").append(this.totalLootDistributed.get()).append("\n");
        long l = this.totalLootGenerated.get();
        if (l > 0L) {
            stringBuilder.append("\u751f\u6210/\u5206\u914d\u6bd4\u4f8b: ").append(String.format("%.2f", (double)this.totalLootDistributed.get() / (double)l)).append("\n\n");
        }
        stringBuilder.append("\u73a9\u5bb6\u5ba1\u8ba1\u7edf\u8ba1:\n");
        for (UUID uUID : this.auditLog.keySet()) {
            List<LootAuditEntry> list = this.auditLog.get(uUID);
            long l2 = list.stream().filter(lootAuditEntry -> lootAuditEntry.eventType.equals("box_generation")).mapToLong(lootAuditEntry -> lootAuditEntry.amount).sum();
            long l3 = list.stream().filter(lootAuditEntry -> lootAuditEntry.eventType.equals("player_opening")).mapToLong(lootAuditEntry -> lootAuditEntry.amount).sum();
            stringBuilder.append("\u73a9\u5bb6 ").append(uUID).append(": \n");
            stringBuilder.append("  \u751f\u6210\u9053\u5177: ").append(l2).append("\n");
            stringBuilder.append("  \u5206\u914d\u9053\u5177: ").append(l3).append("\n");
            if (l2 <= 0L) continue;
            stringBuilder.append("  \u6bd4\u4f8b: ").append(String.format("%.2f", (double)l3 / (double)l2)).append("\n");
        }
        return stringBuilder.toString();
    }

    private static class LootAuditEntry {
        final UUID playerUUID;
        final Rarity rarity;
        final Material material;
        final int amount;
        final long timestamp;
        final String eventType;
        final String description;

        LootAuditEntry(UUID uUID, Rarity rarity, Material material, int n, long l, String string, String string2) {
            this.playerUUID = uUID;
            this.rarity = rarity;
            this.material = material;
            this.amount = n;
            this.timestamp = l;
            this.eventType = string;
            this.description = string2;
        }
    }
}
