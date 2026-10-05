package com.arkcronist.content.bukkit.hooks.placeholderapi;

import com.arkcronist.content.bukkit.emoji.EmojiRegistry;
import com.arkcronist.content.bukkit.hooks.PlaceholderSource;
import com.arkcronist.content.bukkit.item.ItemFactory;
import com.arkcronist.content.bukkit.item.ItemRegistry;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.OfflinePlayer;

import java.util.List;

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
                                              ItemFactory factory, List<PlaceholderSource> sources) {
        ArkContentExpansion expansion = new ArkContentExpansion(version, emojis, items, factory, sources);
        if (!expansion.register()) {
            throw new IllegalStateException("PlaceholderAPI refused the arkcontent expansion");
        }
        return new PlaceholderApiHook();
    }

    /** {@code text} with every placeholder in it replaced - for HUDs fed by another plugin's value. */
    public String resolve(OfflinePlayer player, String text) {
        return PlaceholderAPI.setPlaceholders(player, text);
    }
}
