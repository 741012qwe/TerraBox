/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.OfflinePlayer
 *  org.bukkit.entity.Player
 */
package com.terrabox;

import com.terrabox.BoxManager;
import com.terrabox.Rarity;
import com.terrabox.TerraBoxPlugin;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public class HuntService {
    private final TerraBoxPlugin plugin;

    public HuntService(TerraBoxPlugin terraBoxPlugin) {
        this.plugin = terraBoxPlugin;
    }

    public void hunt(Player player) {
        double d = this.plugin.getConfig().getDouble("hunt.cost", 120.0);
        if (d > 0.0 && !this.plugin.econ().withdraw((OfflinePlayer)player, d)) {
            player.sendMessage(this.plugin.msg("prefix") + "\u00a7c\u8d27\u5e01\u4e0d\u8db3, \u5bfb\u5b9d\u9700\u8981 \u00a7e" + (long)d + " \u00a7c\u5143\u3002");
            return;
        }
        ArrayList<Rarity> arrayList = new ArrayList();
        Object object = this.plugin.getConfig().getStringList("hunt.rarities").iterator();
        while (object.hasNext()) {
            String string = (String)object.next();
            Rarity rarity = Rarity.parse(string);
            if (rarity == null) continue;
            arrayList.add(rarity);
        }
        if (arrayList.isEmpty()) {
            arrayList = List.of(Rarity.EPIC, Rarity.LEGENDARY);
        }
        if ((object = this.plugin.boxes().randomOf(arrayList)) == null) {
            if (d > 0.0) {
                this.plugin.econ().deposit((OfflinePlayer)player, d);
            }
            player.sendMessage(this.plugin.msg("hunt-none"));
            return;
        }
        this.plugin.players().getOrCreate((UUID)player.getUniqueId(), (String)player.getName()).huntCount.incrementAndGet();
        int n = ((BoxManager.BoxEntry)object).x - player.getLocation().getBlockX();
        int n2 = ((BoxManager.BoxEntry)object).z - player.getLocation().getBlockZ();
        int n3 = (int)Math.sqrt((double)n * (double)n + (double)n2 * (double)n2);
        player.sendMessage(this.plugin.msg("hunt-found").replace("{world}", this.plugin.worlds().world() != null ? this.plugin.worlds().world().getName() : "?").replace("{direction}", this.direction(n, n2)).replace("{distance}", String.valueOf(n3)));
        this.plugin.players().getOrCreate(player.getUniqueId(), player.getName()).touch();
    }

    private String direction(int n, int n2) {
        String[] stringArray = new String[]{"\u5317", "\u4e1c\u5317", "\u4e1c", "\u4e1c\u5357", "\u5357", "\u897f\u5357", "\u897f", "\u897f\u5317"};
        double d = Math.toDegrees(Math.atan2(n, -n2));
        d = (d + 360.0) % 360.0;
        int n3 = (int)Math.floor((d + 22.5) / 45.0) % 8;
        return stringArray[n3] + "\u65b9\u5411(" + (int)d + "\u00b0)";
    }
}
