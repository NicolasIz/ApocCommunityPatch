package com.arkcronist.content.bukkit.hooks.placeholderapi;

import com.arkcronist.content.core.hud.Spaces;
import com.arkcronist.content.core.pack.FontImages;
import com.arkcronist.content.core.pack.GlyphText;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * ItemsAdder's font image placeholders, for menus written for it - AuraSkills', DeluxeMenus' - with
 * ItemsAdder gone:
 *
 * <pre>
 * %img_skills_menu_book%     the image's character (also %img_namespace:name%)
 * %img_offset_-2%            2 pixels back; positive moves forward
 * </pre>
 *
 * <p>The character is bare, as ItemsAdder's is: the colour written before it tints the picture.
 * Registered only when ItemsAdder is not installed - the identifier is its own.</p>
 */
public final class ImgExpansion extends PlaceholderExpansion {

    private final String version;
    private final Supplier<FontImages> images;

    public ImgExpansion(String version, Supplier<FontImages> images) {
        this.version = version;
        this.images = images;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "img";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Arkcronist";
    }

    @Override
    public @NotNull String getVersion() {
        return version;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (params.startsWith("offset_")) {
            try {
                int pixels = Integer.parseInt(params.substring("offset_".length()).trim());
                return Math.abs(pixels) > GlyphText.MAX_OFFSET ? null : Spaces.of(pixels);
            } catch (NumberFormatException notAnOffset) {
                // An image named offset_something, then.
            }
        }
        return images.get().find(params).filter(FontImages.Image::inDefaultFont).map(FontImages.Image::glyph)
                .orElse(null);
    }
}
