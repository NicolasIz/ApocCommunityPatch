package com.arkcronist.content.bukkit.hooks.placeholderapi;

import com.arkcronist.content.bukkit.emoji.EmojiRegistry;
import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemFactory;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Placeholders, so plugins that only know PlaceholderAPI - TAB, DeluxeMenus, scoreboards, holograms
 * - can show this plugin's content:
 *
 * <pre>
 * %arkcontent_emoji_ruby%    the ruby emoji's character: drawn as its image by anyone with the pack
 * %arkcontent_cmd_demo:ruby%  the ruby's custom_model_data number (pack.custom-model-data)
 * %arkcontent_held%          id of the custom item in the player's main hand, or nothing
 * %arkcontent_items%         how many custom items are loaded
 * </pre>
 *
 * <p>An emoji placeholder returns the bare character, so the text around it decides its colour;
 * put {@code &f} before it where that text is coloured, to keep the image's own colours.</p>
 */
public final class ArkContentExpansion extends PlaceholderExpansion {

    private final String version;
    private final EmojiRegistry emojis;
    private final ItemRegistry items;
    private final ItemFactory factory;

    public ArkContentExpansion(String version, EmojiRegistry emojis, ItemRegistry items, ItemFactory factory) {
        this.version = version;
        this.emojis = emojis;
        this.items = items;
        this.factory = factory;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "arkcontent";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Arkcronist";
    }

    @Override
    public @NotNull String getVersion() {
        return version;
    }

    /** Registered by this plugin, not downloaded: it must survive /papi reload. */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (params.startsWith("emoji_")) {
            // Permissions are not checked: a menu or tab list is the server speaking, not the player.
            return emojis.get(params.substring("emoji_".length())).map(EmojiRegistry.Emoji::glyph).orElse(null);
        }
        if (params.startsWith("cmd_")) {
            // An item's custom_model_data number, for menus that take a material and a number.
            return items.find(params.substring("cmd_".length())).flatMap(factory::modelData)
                    .map(String::valueOf).orElse("");
        }
        return switch (params) {
            case "held" -> player instanceof Player online
                    ? factory.identify(online.getInventory().getItemInMainHand()).map(CustomItem::id).orElse("")
                    : "";
            case "items" -> String.valueOf(items.size());
            default -> null;
        };
    }
}
