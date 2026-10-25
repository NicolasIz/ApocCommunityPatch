package com.arkcronist.content.bukkit;

import org.bukkit.NamespacedKey;

/**
 * The keys this plugin stores data under - on items, entities and chunks - and names its recipes
 * with.
 *
 * <p>5.0: the plugin is called OverlordContent now, but its keys keep the namespace they always had,
 * {@code arkcronistcontent}: a key made from the plugin ({@code new NamespacedKey(plugin, ...)})
 * would have followed the name, and every item, piece of furniture, crop and vehicle already in a
 * world would have stopped being recognised.</p>
 */
public final class Keys {

    public static final String NAMESPACE = "arkcronistcontent";

    private Keys() {
    }

    public static NamespacedKey of(String key) {
        return new NamespacedKey(NAMESPACE, key);
    }
}
