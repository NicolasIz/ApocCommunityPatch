package com.extendedclip.deluxemenus.hooks;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Signature copied from DeluxeMenus 1.14.1 - see build.gradle. Never packaged. */
public interface ItemHook {

    default ItemStack getItem(String... arguments) {
        return new ItemStack(Material.STONE);
    }

    default ItemStack getItem(Player holder, String... arguments) {
        return getItem(arguments);
    }

    boolean itemMatchesIdentifiers(ItemStack item, String... arguments);

    String getPrefix();
}
