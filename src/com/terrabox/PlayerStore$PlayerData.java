/*
 * Decompiled with CFR 0.152.
 */
package com.terrabox;

import com.terrabox.Rarity;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public static class PlayerStore.PlayerData {
    public final UUID uuid;
    public volatile String name;
    public final AtomicLong firstSeen = new AtomicLong();
    public final AtomicLong money = new AtomicLong();
    public final AtomicLong openedCommon = new AtomicLong();
    public final AtomicLong openedRare = new AtomicLong();
    public final AtomicLong openedEpic = new AtomicLong();
    public final AtomicLong openedLegendary = new AtomicLong();
    public final AtomicLong openedMythic = new AtomicLong();
    public final AtomicLong airdropLooted = new AtomicLong();
    public final AtomicLong soldValue = new AtomicLong();
    public final AtomicLong huntCount = new AtomicLong();
    final AtomicLong touched = new AtomicLong();
    final AtomicBoolean merged = new AtomicBoolean(false);

    PlayerStore.PlayerData(UUID uUID, String string) {
        this.uuid = uUID;
        this.name = string != null ? string : "?";
    }

    public boolean isNew() {
        return this.firstSeen.get() == 0L;
    }

    public void touch() {
        this.touched.incrementAndGet();
        if (this.firstSeen.get() == 0L) {
            this.firstSeen.compareAndSet(0L, System.currentTimeMillis());
        }
    }

    public long openedTotal() {
        return this.openedCommon.get() + this.openedRare.get() + this.openedEpic.get() + this.openedLegendary.get() + this.openedMythic.get();
    }

    public void addOpened(Rarity rarity) {
        this.touch();
        switch (rarity) {
            case COMMON: {
                this.openedCommon.incrementAndGet();
                break;
            }
            case RARE: {
                this.openedRare.incrementAndGet();
                break;
            }
            case EPIC: {
                this.openedEpic.incrementAndGet();
                break;
            }
            case LEGENDARY: {
                this.openedLegendary.incrementAndGet();
                break;
            }
            case MYTHIC: {
                this.openedMythic.incrementAndGet();
            }
        }
    }

    public double money() {
        return this.money.get();
    }

    public void addMoney(double d) {
        this.touch();
        this.money.addAndGet((long)d);
    }

    public boolean takeMoney(double d) {
        long l;
        long l2 = (long)Math.ceil(d);
        do {
            if ((l = this.money.get()) >= l2) continue;
            return false;
        } while (!this.money.compareAndSet(l, l - l2));
        this.touch();
        return true;
    }

    void mergeFrom(PlayerStore.PlayerData playerData) {
        if (this.merged.compareAndSet(false, true)) {
            if (this.touched.get() == 0L) {
                this.firstSeen.set(playerData.firstSeen.get());
                this.money.set(playerData.money.get());
                this.openedCommon.set(playerData.openedCommon.get());
                this.openedRare.set(playerData.openedRare.get());
                this.openedEpic.set(playerData.openedEpic.get());
                this.openedLegendary.set(playerData.openedLegendary.get());
                this.openedMythic.set(playerData.openedMythic.get());
                this.airdropLooted.set(playerData.airdropLooted.get());
                this.soldValue.set(playerData.soldValue.get());
                this.huntCount.set(playerData.huntCount.get());
            } else {
                if (this.firstSeen.get() == 0L && playerData.firstSeen.get() > 0L) {
                    this.firstSeen.set(playerData.firstSeen.get());
                }
                this.money.addAndGet(playerData.money.get());
                this.openedCommon.addAndGet(playerData.openedCommon.get());
                this.openedRare.addAndGet(playerData.openedRare.get());
                this.openedEpic.addAndGet(playerData.openedEpic.get());
                this.openedLegendary.addAndGet(playerData.openedLegendary.get());
                this.openedMythic.addAndGet(playerData.openedMythic.get());
                this.airdropLooted.addAndGet(playerData.airdropLooted.get());
                this.soldValue.addAndGet(playerData.soldValue.get());
                this.huntCount.addAndGet(playerData.huntCount.get());
            }
        }
    }
}
