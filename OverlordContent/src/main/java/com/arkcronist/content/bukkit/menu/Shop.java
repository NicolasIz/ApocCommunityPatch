package com.arkcronist.content.bukkit.menu;

import org.bukkit.entity.Player;

/**
 * Money, for the content browser's shop mode: the items with a {@code price} are sold for it.
 * Supplied by the economy hook - Vault - when one is installed.
 */
public interface Shop {

    /** Permission to open the shop, {@code /arkcontent shop}. */
    String PERMISSION = "arkcontent.shop";

    /**
     * Takes {@code amount} from the player.
     *
     * @return false - having told the player why - if they cannot pay, or there is no economy
     */
    boolean charge(Player player, double amount);

    /**
     * Gives {@code amount} to the player: a job's pay.
     *
     * @return false if there is no economy, or it refused
     */
    boolean deposit(Player player, double amount);

    /** An amount as the economy writes it: {@code $1,250.00}, {@code 1250 coins}. */
    String format(double amount);
}
