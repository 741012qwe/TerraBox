/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Material
 */
package com.terrabox;

import java.util.List;
import org.bukkit.Material;

public record EnchantManager.EnchantPool(Material material, String name, List<String> lore, boolean exotic) {
}
