package com.arkcronist.content.bukkit.hooks.shopgui;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Which custom item a ShopGUI+ item section names: {@code arkcontent: <namespace:id>}, or - with
 * ItemsAdder gone - the {@code itemsAdder: <namespace:id>} a shop or button written for it still
 * says, since imported items keep ItemsAdder's namespace and id. Kept apart from the hook, which
 * names ShopGUI+'s classes, so it can be tested without them.
 */
final class ShopItemKeys {

    static final String OWN = "arkcontent";
    /** ShopGUI+'s key for ItemsAdder items, as its documentation writes it, and as people type it. */
    static final List<String> ITEMS_ADDER = List.of("itemsAdder", "itemsadder", "ItemsAdder");

    private ShopItemKeys() {
    }

    /**
     * @param itemsAdderInstalled whether ItemsAdder is there to answer its own key
     * @return the id, or null when the section names no item of this plugin
     */
    static @Nullable String id(ConfigurationSection section, boolean itemsAdderInstalled) {
        String own = section.getString(OWN);
        if (own != null && !own.isBlank()) {
            return own.trim();
        }
        if (itemsAdderInstalled) {
            return null;
        }
        for (String key : ITEMS_ADDER) {
            String id = section.getString(key);
            if (id != null && !id.isBlank()) {
                return id.trim();
            }
        }
        return null;
    }
}
