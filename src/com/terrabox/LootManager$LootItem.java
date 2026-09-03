/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Material
 *  org.bukkit.enchantments.Enchantment
 */
package com.terrabox;

import java.util.Map;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;

private record LootManager.LootItem(Material material, int min, int max, double chance, Map<Enchantment, int[]> enchants, String name, String lore, String special, String artifact, String enchantStone, String craft) {
}
