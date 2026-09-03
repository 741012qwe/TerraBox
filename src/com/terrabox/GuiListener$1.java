/*
 * Decompiled with CFR 0.152.
 */
package com.terrabox;

import com.terrabox.GameManager;

static class GuiListener.1 {
    static final /* synthetic */ int[] $SwitchMap$com$terrabox$GameManager$Mode;

    static {
        $SwitchMap$com$terrabox$GameManager$Mode = new int[GameManager.Mode.values().length];
        try {
            GuiListener.1.$SwitchMap$com$terrabox$GameManager$Mode[GameManager.Mode.SOLO.ordinal()] = 1;
        }
        catch (NoSuchFieldError noSuchFieldError) {
            // empty catch block
        }
        try {
            GuiListener.1.$SwitchMap$com$terrabox$GameManager$Mode[GameManager.Mode.TEAM.ordinal()] = 2;
        }
        catch (NoSuchFieldError noSuchFieldError) {
            // empty catch block
        }
    }
}
