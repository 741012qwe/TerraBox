/*
 * Decompiled with CFR 0.152.
 */
package com.terrabox;

import java.util.UUID;

private record InviteManager.Invite(UUID owner, UUID target, String roomId, long expires) {
}
