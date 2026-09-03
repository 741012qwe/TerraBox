/*
 * Decompiled with CFR 0.152.
 */
package com.terrabox;

public static enum GameManager.State {
    IDLE("\u7a7a\u95f2", "\u00a77"),
    COUNTDOWN("\u51c6\u5907\u4e2d", "\u00a7e"),
    RUNNING("\u8fdb\u884c\u4e2d", "\u00a7a"),
    ENDING("\u7ed3\u7b97\u4e2d", "\u00a7d");

    public final String display;
    public final String color;

    private GameManager.State(String string2, String string3) {
        this.display = string2;
        this.color = string3;
    }
}
