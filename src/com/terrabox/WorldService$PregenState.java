/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.papermc.paper.threadedregions.scheduler.ScheduledTask
 */
package com.terrabox;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

private static final class WorldService.PregenState {
    final ArrayDeque<long[]> queue = new ArrayDeque();
    final AtomicInteger done = new AtomicInteger();
    final AtomicInteger queuedCount = new AtomicInteger();
    final AtomicLong start = new AtomicLong();
    ScheduledTask task;
    final boolean isMain;
    ExecutorService executor;

    WorldService.PregenState(boolean bl) {
        this.isMain = bl;
    }
}
