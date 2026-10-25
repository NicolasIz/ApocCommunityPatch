package com.arkcronist.content.bukkit.text;

import com.arkcronist.content.core.pack.FontImages;
import com.arkcronist.content.core.pack.GlyphText;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

/**
 * ItemsAdder's font image syntax ({@link GlyphText}) in a text component - a menu title - with the
 * component's own styles kept around it.
 *
 * <p>A picture takes the colour of the text it is in, as ItemsAdder draws it; where that text has
 * no colour of its own - a title left to the game's dark grey - it is drawn white, in its own
 * colours. Bold and the other decorations are switched off on the inserted characters: bold would
 * widen every one of them by a pixel, and an offset would no longer land where it was asked to.</p>
 */
public final class GlyphComponents {

    private static final Key DEFAULT_FONT = Key.key("minecraft", "default");

    private GlyphComponents() {
    }

    /** {@code component} with every token replaced; the same instance when there was none. */
    public static Component replace(Component component, FontImages images) {
        return replace(component, null, images);
    }

    private static Component replace(Component component, @Nullable TextColor inherited, FontImages images) {
        TextColor colour = component.color() != null ? component.color() : inherited;
        List<Component> children = component.children();
        List<Component> replacedChildren = new ArrayList<>(children.size());
        boolean changed = false;
        for (Component child : children) {
            Component replaced = replace(child, colour, images);
            changed |= replaced != child;
            replacedChildren.add(replaced);
        }
        Component self = component;
        if (component instanceof TextComponent text) {
            List<Component> pieces = pieces(text.content(), colour, images);
            if (pieces != null) {
                self = Component.text().style(text.style()).append(pieces).append(replacedChildren).build();
                return self;
            }
        }
        return changed ? self.children(replacedChildren) : component;
    }

    /** {@code content} cut into literal text and inserted characters; null when nothing in it is a token. */
    private static @Nullable List<Component> pieces(String content, @Nullable TextColor colour, FontImages images) {
        if (content.indexOf(':') < 0 && !content.contains("%img_")) {
            return null;
        }
        Matcher match = GlyphText.TOKEN.matcher(content);
        List<Component> pieces = new ArrayList<>();
        StringBuilder literal = new StringBuilder();
        int at = 0;
        boolean any = false;
        while (at < content.length() && match.find(at)) {
            GlyphText.Glyph glyph = GlyphText.resolve(match, images);
            if (glyph == null) {
                // Keep its first character and look again from the next: 12:30:coin: still finds :coin:.
                literal.append(content, at, match.start() + 1);
                at = match.start() + 1;
                continue;
            }
            literal.append(content, at, match.start());
            if (!literal.isEmpty()) {
                pieces.add(Component.text(literal.toString()));
                literal.setLength(0);
            }
            pieces.add(glyph(glyph, colour));
            any = true;
            at = match.end();
        }
        if (!any) {
            return null;
        }
        literal.append(content, Math.min(at, content.length()), content.length());
        if (!literal.isEmpty()) {
            pieces.add(Component.text(literal.toString()));
        }
        return pieces;
    }

    private static Component glyph(GlyphText.Glyph glyph, @Nullable TextColor colour) {
        Style.Builder style = Style.style()
                .font(glyph.font().equals(FontImages.DEFAULT_FONT) ? DEFAULT_FONT : Key.key(glyph.font()))
                .decoration(TextDecoration.BOLD, false)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.UNDERLINED, false)
                .decoration(TextDecoration.STRIKETHROUGH, false)
                .decoration(TextDecoration.OBFUSCATED, false);
        if (glyph.image() && colour == null) {
            style.color(NamedTextColor.WHITE);
        }
        return Component.text(glyph.text(), style.build());
    }
}
