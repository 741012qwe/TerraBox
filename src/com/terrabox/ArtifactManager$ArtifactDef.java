/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Material
 *  org.bukkit.enchantments.Enchantment
 */
package com.terrabox;

import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;

public record ArtifactManager.ArtifactDef(Material material, String key, String name, List<String> lore, String effect, double procChance, double magnitude, Map<Enchantment, Integer> enchants) {
}
