/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.papermc.paper.threadedregions.scheduler.ScheduledTask
 */
package com.terrabox;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.function.Consumer;

private record WorldDecorator.Job(int cx, int cz, Consumer<ScheduledTask> body) {
}
