package com.arkcronist.content.bukkit.hooks.placeholderapi;

import com.arkcronist.content.bukkit.emoji.EmojiRegistry;
import com.arkcronist.content.bukkit.item.ItemFactory;
import com.arkcronist.content.bukkit.item.ItemRegistry;

/**
 * Registers the {@code arkcontent} placeholders. Kept apart from {@link ArkContentExpansion} so the
 * hook manager names only this class, which extends nothing of PlaceholderAPI's: the expansion
 * does, and a class whose parent is missing cannot even be loaded - naming it where the manager's
 * code is resolved would stop this plugin enabling on a server without PlaceholderAPI.
 */
public final class PlaceholderApiHook {

    private PlaceholderApiHook() {
    }

    /** Registers the expansion with PlaceholderAPI, which is installed. */
    public static PlaceholderApiHook register(String version, EmojiRegistry emojis, ItemRegistry items,
                                              ItemFactory factory) {
        ArkContentExpansion expansion = new ArkContentExpansion(version, emojis, items, factory);
        if (!expansion.register()) {
            throw new IllegalStateException("PlaceholderAPI refused the arkcontent expansion");
        }
        return new PlaceholderApiHook();
    }
}
