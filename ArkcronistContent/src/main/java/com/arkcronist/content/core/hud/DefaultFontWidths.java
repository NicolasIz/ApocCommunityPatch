package com.arkcronist.content.core.hud;

import java.text.Normalizer;

/**
 * How wide the client draws text in the default font, character by character - its ASCII sheet,
 * where most letters take six pixels with their spacing and the thin ones less. Accented letters
 * count as their base letter, and anything else as six: close enough to centre a short message,
 * which is all it is used for.
 */
public final class DefaultFontWidths {

    private DefaultFontWidths() {
    }

    /** Pixels {@code text} takes; bold adds one per character, as the client draws it. */
    public static int width(String text, boolean bold) {
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLowSurrogate(c)) {
                continue;
            }
            width += advance(c) + (bold && c != ' ' ? 1 : 0);
        }
        return width;
    }

    static int advance(char c) {
        if (c > 127) {
            String base = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD);
            char plain = base.isEmpty() ? c : base.charAt(0);
            return plain <= 127 ? advance(plain) : 6;
        }
        return switch (c) {
            case '!', '\'', ',', '.', ':', ';', 'i', '|' -> 2;
            case 'l', '`' -> 3;
            case ' ', '"', '(', ')', '*', 'I', '[', ']', 't', '{', '}' -> 4;
            case '<', '>', 'f', 'k' -> 5;
            case '@', '~' -> 7;
            default -> 6;
        };
    }
}
