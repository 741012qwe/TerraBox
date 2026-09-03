/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Material
 */
package com.terrabox;

import java.util.List;
import org.bukkit.Material;

public record SpecialItemManager.SpecialDef(Material material, String key, String name, List<String> lore, String effect, double radius, double damage, double velocity, int durationSeconds) {
}
