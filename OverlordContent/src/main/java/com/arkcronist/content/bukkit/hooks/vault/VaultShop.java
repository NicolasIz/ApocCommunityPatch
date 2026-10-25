package com.arkcronist.content.bukkit.hooks.vault;

import com.arkcronist.content.bukkit.menu.Shop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.Nullable;

/**
 * Selling custom items for money through Vault, whatever economy plugin stands behind it -
 * EssentialsX, CMI, any other. An item is for sale once it has a {@code price}:
 *
 * <pre>
 * ruby:
 *   price: 250              # each; /arkcontent shop sells it
 * </pre>
 *
 * <p>The economy is looked up at every purchase rather than once: economy plugins register with
 * Vault as they enable, often after this plugin, and can be swapped by a reload.</p>
 */
public final class VaultShop implements Shop {

    private final Server server;

    public VaultShop(Server server) {
        this.server = server;
    }

    private @Nullable Economy economy() {
        RegisteredServiceProvider<Economy> registration = server.getServicesManager().getRegistration(Economy.class);
        return registration == null ? null : registration.getProvider();
    }

    /** Whether an economy plugin has registered with Vault. */
    public boolean ready() {
        Economy economy = economy();
        return economy != null && economy.isEnabled();
    }

    @Override
    public boolean charge(Player player, double amount) {
        Economy economy = economy();
        if (economy == null || !economy.isEnabled()) {
            player.sendActionBar(Component.text("The shop is closed: no economy plugin is installed", NamedTextColor.RED));
            return false;
        }
        if (!economy.has(player, amount)) {
            player.sendActionBar(Component.text("You need " + economy.format(amount) + " - you have "
                    + economy.format(economy.getBalance(player)), NamedTextColor.RED));
            return false;
        }
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        if (!response.transactionSuccess()) {
            player.sendActionBar(Component.text("Payment refused: " + response.errorMessage, NamedTextColor.RED));
            return false;
        }
        return true;
    }

    @Override
    public boolean deposit(Player player, double amount) {
        Economy economy = economy();
        return economy != null && economy.isEnabled() && economy.depositPlayer(player, amount).transactionSuccess();
    }

    @Override
    public String format(double amount) {
        Economy economy = economy();
        return economy == null ? String.format("%.2f", amount) : economy.format(amount);
    }
}
