/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.milkbowl.vault.economy.Economy
 *  net.milkbowl.vault.economy.EconomyResponse
 *  org.bukkit.OfflinePlayer
 *  org.bukkit.plugin.RegisteredServiceProvider
 */
package com.terrabox;

import com.terrabox.PlayerStore;
import com.terrabox.TerraBoxPlugin;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

public class Econ {
    private final TerraBoxPlugin plugin;
    private Economy vault;
    private boolean tried = false;

    public Econ(TerraBoxPlugin terraBoxPlugin) {
        this.plugin = terraBoxPlugin;
    }

    public void setup() {
        if (this.tried) {
            return;
        }
        this.tried = true;
        try {
            if (!this.plugin.getConfig().getBoolean("economy.use-vault", true)) {
                return;
            }
            if (this.plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
                return;
            }
            RegisteredServiceProvider registeredServiceProvider = this.plugin.getServer().getServicesManager().getRegistration(Economy.class);
            if (registeredServiceProvider != null) {
                this.vault = (Economy)registeredServiceProvider.getProvider();
                this.plugin.getLogger().info("\u7ecf\u6d4e: \u5df2\u63a5\u5165 Vault (" + this.vault.getName() + ")");
            }
        }
        catch (Throwable throwable) {
            this.vault = null;
            this.plugin.getLogger().warning("Vault \u7ecf\u6d4e\u63a5\u5165\u5931\u8d25, \u56de\u9000\u5185\u7f6e\u79ef\u5206: " + throwable.getMessage());
        }
    }

    public boolean useVault() {
        return this.vault != null;
    }

    public String name() {
        return this.useVault() ? "Vault" : "\u5185\u7f6e\u79ef\u5206";
    }

    public double balance(OfflinePlayer offlinePlayer) {
        if (this.vault != null) {
            try {
                return this.vault.getBalance(offlinePlayer);
            }
            catch (Throwable throwable) {
                return 0.0;
            }
        }
        return this.plugin.players().getOrCreate(offlinePlayer.getUniqueId(), offlinePlayer.getName()).money();
    }

    public void deposit(OfflinePlayer offlinePlayer, double d) {
        if (d <= 0.0) {
            return;
        }
        if (this.vault != null) {
            try {
                EconomyResponse economyResponse = this.vault.depositPlayer(offlinePlayer, d);
                if (economyResponse != null && !economyResponse.transactionSuccess()) {
                    this.plugin.getLogger().warning("Vault \u5b58\u6b3e\u5931\u8d25: " + economyResponse.errorMessage);
                }
                return;
            }
            catch (Throwable throwable) {
                this.plugin.getLogger().warning("Vault \u5b58\u6b3e\u5f02\u5e38, \u56de\u9000\u5185\u7f6e\u79ef\u5206: " + throwable.getMessage());
            }
        }
        this.plugin.players().getOrCreate(offlinePlayer.getUniqueId(), offlinePlayer.getName()).addMoney(d);
    }

    public boolean withdraw(OfflinePlayer offlinePlayer, double d) {
        if (d <= 0.0) {
            return true;
        }
        if (this.vault != null) {
            try {
                EconomyResponse economyResponse = this.vault.withdrawPlayer(offlinePlayer, d);
                return economyResponse != null && economyResponse.transactionSuccess();
            }
            catch (Throwable throwable) {
                this.plugin.getLogger().warning("Vault \u53d6\u6b3e\u5f02\u5e38, \u56de\u9000\u5185\u7f6e\u79ef\u5206: " + throwable.getMessage());
            }
        }
        PlayerStore.PlayerData playerData = this.plugin.players().getOrCreate(offlinePlayer.getUniqueId(), offlinePlayer.getName());
        return playerData.takeMoney(d);
    }
}
