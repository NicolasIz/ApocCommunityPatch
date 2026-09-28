package com.arkcronist.enchants.gui;

import java.lang.reflect.Method;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

/** The server economy through Vault, reached by reflection so ArkEnchants runs fine without it. */
final class Money {

    private Object economy;
    private Method balance;
    private Method withdraw;
    private Method format;
    private boolean checked;
    private final Logger log;

    Money(Logger log) {
        this.log = log;
    }

    boolean ready() {
        if (!checked) {
            checked = true;
            hook();
        }
        return economy != null;
    }

    private void hook() {
        Plugin vault = Bukkit.getPluginManager().getPlugin("Vault");
        if (vault == null || !vault.isEnabled()) {
            log.warning("enchanter.currency is MONEY but Vault is not installed; charging experience levels instead.");
            return;
        }
        try {
            Class<?> eco = Class.forName("net.milkbowl.vault.economy.Economy", true, vault.getClass().getClassLoader());
            RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(eco);
            if (rsp == null) {
                log.warning("Vault has no economy plugin; charging experience levels instead.");
                return;
            }
            economy = rsp.getProvider();
            balance = eco.getMethod("getBalance", OfflinePlayer.class);
            withdraw = eco.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            format = eco.getMethod("format", double.class);
        } catch (ReflectiveOperationException | RuntimeException e) {
            economy = null;
            log.warning("Could not reach the Vault economy (" + e + "); charging experience levels instead.");
        }
    }

    double balance(OfflinePlayer p) {
        try {
            return ((Number) balance.invoke(economy, p)).doubleValue();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 0;
        }
    }

    boolean take(OfflinePlayer p, double amount) {
        try {
            Object r = withdraw.invoke(economy, p, amount);
            return (boolean) r.getClass().getMethod("transactionSuccess").invoke(r);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    String format(double amount) {
        try {
            return String.valueOf(format.invoke(economy, amount));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return String.format(java.util.Locale.ROOT, "%.2f", amount);
        }
    }
}
