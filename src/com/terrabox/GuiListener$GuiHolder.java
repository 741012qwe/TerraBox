/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.inventory.Inventory
 *  org.bukkit.inventory.InventoryHolder
 */
package com.terrabox;

import com.terrabox.GuiListener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public static class GuiListener.GuiHolder
implements InventoryHolder {
    public final GuiListener.Type type;
    public Inventory inv;
    public int craftIndex = 0;
    public String inviteRoom = null;

    public GuiListener.GuiHolder(GuiListener.Type type) {
        this.type = type;
    }

    public Inventory getInventory() {
        return this.inv;
    }
}
