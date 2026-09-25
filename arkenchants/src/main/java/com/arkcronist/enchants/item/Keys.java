package com.arkcronist.enchants.item;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** PersistentDataContainer keys used on items and projectiles. */
public final class Keys {

    public static NamespacedKey ENCHANTS;
    public static NamespacedKey LORE;
    public static NamespacedKey SOULS;
    public static NamespacedKey SOUL_TRACKER;
    public static NamespacedKey BOOK;
    public static NamespacedKey SUCCESS;
    public static NamespacedKey DESTROY;
    public static NamespacedKey MYSTERY;
    public static NamespacedKey PROJECTILE;
    public static NamespacedKey SCROLL;
    public static NamespacedKey GUARD;

    private Keys() {
    }

    public static void init(Plugin plugin) {
        ENCHANTS = new NamespacedKey(plugin, "enchants");
        LORE = new NamespacedKey(plugin, "lore");
        SOULS = new NamespacedKey(plugin, "souls");
        SOUL_TRACKER = new NamespacedKey(plugin, "soul_tracker");
        BOOK = new NamespacedKey(plugin, "book");
        SUCCESS = new NamespacedKey(plugin, "success");
        DESTROY = new NamespacedKey(plugin, "destroy");
        MYSTERY = new NamespacedKey(plugin, "mystery");
        PROJECTILE = new NamespacedKey(plugin, "projectile");
        SCROLL = new NamespacedKey(plugin, "scroll");
        GUARD = new NamespacedKey(plugin, "guard");
    }
}
