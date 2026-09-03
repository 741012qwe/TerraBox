/*
 * Decompiled with CFR 0.152.
 */
package com.terrabox;

public static enum GameManager.Mode {
    SOLO("\u5355\u4eba\u6a21\u5f0f", "\u00a7a"),
    PVP("\u73a9\u5bb6\u5bf9\u6218", "\u00a7c"),
    TEAM("\u7ec4\u961f\u5bf9\u6218", "\u00a76");

    public final String display;
    public final String color;

    private GameManager.Mode(String string2, String string3) {
        this.display = string2;
        this.color = string3;
    }

    public static GameManager.Mode parse(String string) {
        for (GameManager.Mode mode : GameManager.Mode.values()) {
            if (!mode.name().equalsIgnoreCase(string) && !mode.display.equals(string)) continue;
            return mode;
        }
        return null;
    }
}
