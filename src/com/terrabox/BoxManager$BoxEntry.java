/*
 * Decompiled with CFR 0.152.
 */
package com.terrabox;

import com.terrabox.Rarity;

public static final class BoxManager.BoxEntry {
    public final int x;
    public final int y;
    public final int z;
    public final Rarity rarity;
    public final long born;
    public final boolean airdrop;

    public BoxManager.BoxEntry(int n, int n2, int n3, Rarity rarity, long l, boolean bl) {
        this.x = n;
        this.y = n2;
        this.z = n3;
        this.rarity = rarity;
        this.born = l;
        this.airdrop = bl;
    }
}
