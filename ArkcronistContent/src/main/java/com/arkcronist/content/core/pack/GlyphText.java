package com.arkcronist.content.core.pack;

import com.arkcronist.content.core.hud.Spaces;
import org.jetbrains.annotations.Nullable;

import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ItemsAdder's text syntax for font images, read the way ItemsAdder reads it, so menus, titles and
 * messages written for it draw the same:
 *
 * <pre>
 *   :offset_-44:              44 pixels back (forward when positive): space characters
 *   %img_offset_-44%          the same, as a PlaceholderAPI placeholder
 *   :skills_menu_book:        a font image by name - or :namespace:name:
 *   %img_skills_menu_book%    the same, as a PlaceholderAPI placeholder
 * </pre>
 *
 * <p>A name that is no font image ({@code 12:30:00}, {@code Shop: Tools:}) is left as it was
 * written.</p>
 */
public final class GlyphText {

    /** Any of the forms above; group 1/2 an offset, group 3/4 a name. */
    public static final Pattern TOKEN = Pattern.compile(
            ":offset_(-?\\d{1,5}):|%img_offset_(-?\\d{1,5})%|%img_([A-Za-z0-9_.\\-]+(?::[A-Za-z0-9_.\\-/]+)?)%"
                    + "|:([a-z0-9_.\\-]+(?::[a-z0-9_.\\-/]+)?):");

    /** The largest move an offset may ask for; more is surely a typo, and is left as written. */
    public static final int MAX_OFFSET = 4096;

    /** What a token stands for: the characters to draw, and in which font. */
    public record Glyph(String text, String font, boolean image) {
    }

    private GlyphText() {
    }

    /** What one match of {@link #TOKEN} stands for, or null to leave it as written. */
    public static @Nullable Glyph resolve(MatchResult match, FontImages images) {
        String offset = match.group(1) != null ? match.group(1) : match.group(2);
        if (offset != null) {
            int pixels = Integer.parseInt(offset);
            return Math.abs(pixels) > MAX_OFFSET ? null : new Glyph(Spaces.of(pixels), FontImages.DEFAULT_FONT, false);
        }
        String name = match.group(3) != null ? match.group(3) : match.group(4);
        return images.find(name).map(image -> new Glyph(image.glyph(), image.font(), true)).orElse(null);
    }

    /**
     * {@code text} with every token that stands for something in the default font replaced: what a
     * plain string - a legacy title, a message - can carry. The character is bare, as ItemsAdder's
     * is: the colour before it tints the picture, so {@code <#ffd556>%img_bar%} draws a white bar
     * yellow, and {@code <white>} draws a picture in its own colours.
     */
    public static String replace(String text, FontImages images) {
        if (text.indexOf(':') < 0 && !text.contains("%img_")) {
            return text;
        }
        Matcher match = TOKEN.matcher(text);
        StringBuilder out = new StringBuilder(text.length());
        int at = 0;
        while (at < text.length() && match.find(at)) {
            Glyph glyph = resolve(match, images);
            if (glyph == null || !glyph.font().equals(FontImages.DEFAULT_FONT)) {
                // Not one of ours: keep its first character and look again from the next, so a token
                // right after it (12:30:coin:) is still found.
                out.append(text, at, match.start() + 1);
                at = match.start() + 1;
                continue;
            }
            out.append(text, at, match.start());
            out.append(glyph.text());
            at = match.end();
        }
        out.append(text, Math.min(at, text.length()), text.length());
        return out.toString();
    }
}
