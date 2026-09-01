package com.terrabox;

import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * 经济层: 有 Vault 用 Vault, 没有则用内置积分 (PlayerStore.money)
 * 线程说明: 所有方法只做数据运算/ServicesManager 查询;
 *   Vault 调用发生在调用方所在的区域线程 (事件/命令线程), 满足白皮书归属规则。
 */
public class Econ {
    private final TerraBoxPlugin plugin;
    private net.milkbowl.vault.economy.Economy vault;
    private boolean tried = false;

    public Econ(TerraBoxPlugin plugin) {
        this.plugin = plugin;
    }

    /** 尝试接入 Vault (onEnable 调用, Global/启动线程) */
    public void setup() {
        if (tried) return;
        tried = true;
        try {
            if (!plugin.getConfig().getBoolean("economy.use-vault", true)) return;
            if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) return;
            RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> rsp =
                    plugin.getServer().getServicesManager()
                            .getRegistration(net.milkbowl.vault.economy.Economy.class);
            if (rsp != null) {
                vault = rsp.getProvider();
                plugin.getLogger().info("经济: 已接入 Vault (" + vault.getName() + ")");
            }
        } catch (Throwable t) {
            vault = null;
            plugin.getLogger().warning("Vault 经济接入失败, 回退内置积分: " + t.getMessage());
        }
    }

    public boolean useVault() {
        return vault != null;
    }

    public String name() {
        return useVault() ? "Vault" : "内置积分";
    }

    public double balance(OfflinePlayer p) {
        if (vault != null) {
            try { return vault.getBalance(p); } catch (Throwable t) { return 0; }
        }
        return plugin.players().getOrCreate(p.getUniqueId(), p.getName()).money();
    }

    /** 存款 (任意区域线程, 只操作数据) */
    public void deposit(OfflinePlayer p, double amount) {
        if (amount <= 0) return;
        if (vault != null) {
            try {
                net.milkbowl.vault.economy.EconomyResponse r = vault.depositPlayer(p, amount);
                if (r != null && !r.transactionSuccess())
                    plugin.getLogger().warning("Vault 存款失败: " + r.errorMessage);
                return;
            } catch (Throwable t) {
                plugin.getLogger().warning("Vault 存款异常, 回退内置积分: " + t.getMessage());
            }
        }
        plugin.players().getOrCreate(p.getUniqueId(), p.getName()).addMoney(amount);
    }

    /** 取款, 余额不足返回 false (内置模式同步判定) */
    public boolean withdraw(OfflinePlayer p, double amount) {
        if (amount <= 0) return true;
        if (vault != null) {
            try {
                net.milkbowl.vault.economy.EconomyResponse r = vault.withdrawPlayer(p, amount);
                return r != null && r.transactionSuccess();
            } catch (Throwable t) {
                plugin.getLogger().warning("Vault 取款异常, 回退内置积分: " + t.getMessage());
            }
        }
        PlayerStore.PlayerData d = plugin.players().getOrCreate(p.getUniqueId(), p.getName());
        return d.takeMoney(amount);
    }
}
