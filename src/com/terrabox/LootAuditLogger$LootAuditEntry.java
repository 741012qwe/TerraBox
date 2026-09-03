/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Material
 */
package com.terrabox;

import com.terrabox.Rarity;
import java.util.UUID;
import org.bukkit.Material;

private static class LootAuditLogger.LootAuditEntry {
    final UUID playerUUID;
    final Rarity rarity;
    final Material material;
    final int amount;
    final long timestamp;
    final String eventType;
    final String description;

    LootAuditLogger.LootAuditEntry(UUID uUID, Rarity rarity, Material material, int n, long l, String string, String string2) {
        this.playerUUID = uUID;
        this.rarity = rarity;
        this.material = material;
        this.amount = n;
        this.timestamp = l;
        this.eventType = string;
        this.description = string2;
    }
}
