package com.arkcronist.content.core.importer;

import java.util.Locale;
import java.util.Map;

/**
 * Turns legacy formatting codes - {@code &c}, {@code §l}, {@code &#ff8800},
 * {@code &x&f&f&8&8&0&0} - into the MiniMessage tags this plugin's content files are written in.
 *
 * <p>Legacy and MiniMessage disagree on one thing: in legacy text a colour code also clears bold,
 * italic and the rest, while a MiniMessage colour tag leaves them on. So a colour that follows a
 * decoration is written as {@code <reset>} and then the colour, and {@code &c&lHot &7cold} still
 * reads "cold" in plain grey. Anything else, MiniMessage tags already in the text included, is kept
 * as it is.</p>
 */
public final class LegacyText {

    private static final Map<Character, String> COLOURS = Map.ofEntries(
            Map.entry('0', "black"), Map.entry('1', "dark_blue"), Map.entry('2', "dark_green"),
            Map.entry('3', "dark_aqua"), Map.entry('4', "dark_red"), Map.entry('5', "dark_purple"),
            Map.entry('6', "gold"), Map.entry('7', "gray"), Map.entry('8', "dark_gray"),
            Map.entry('9', "blue"), Map.entry('a', "green"), Map.entry('b', "aqua"),
            Map.entry('c', "red"), Map.entry('d', "light_purple"), Map.entry('e', "yellow"),
            Map.entry('f', "white"));

    private static final Map<Character, String> DECORATIONS = Map.of(
            'k', "obfuscated", 'l', "bold", 'm', "strikethrough", 'n', "underlined", 'o', "italic");

    private LegacyText() {
    }

    public static String toMiniMessage(String legacy) {
        StringBuilder out = new StringBuilder(legacy.length() + 16);
        boolean decorated = false;
        int i = 0;
        while (i < legacy.length()) {
            char c = legacy.charAt(i);
            if ((c != '&' && c != '§') || i + 1 >= legacy.length()) {
                out.append(c);
                i++;
                continue;
            }
            char code = Character.toLowerCase(legacy.charAt(i + 1));

            String hex = hexAt(legacy, i);
            if (hex != null) {
                decorated = colour(out, "#" + hex, decorated);
                i += code == '#' ? 8 : 14;
            } else if (COLOURS.containsKey(code)) {
                decorated = colour(out, COLOURS.get(code), decorated);
                i += 2;
            } else if (DECORATIONS.containsKey(code)) {
                out.append('<').append(DECORATIONS.get(code)).append('>');
                decorated = true;
                i += 2;
            } else if (code == 'r') {
                out.append("<reset>");
                decorated = false;
                i += 2;
            } else {
                // "Salt & pepper": not a code, just an ampersand.
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static boolean colour(StringBuilder out, String colour, boolean decorated) {
        if (decorated) {
            out.append("<reset>");
        }
        out.append('<').append(colour).append('>');
        return false;
    }

    /**
     * The six hex digits of {@code &#rrggbb} or {@code &x&r&r&g&g&b&b} starting at {@code at}, or
     * null when there is no such code there.
     */
    private static String hexAt(String text, int at) {
        char marker = Character.toLowerCase(text.charAt(at + 1));
        if (marker == '#' && at + 8 <= text.length()) {
            String digits = text.substring(at + 2, at + 8);
            return isHex(digits) ? digits.toLowerCase(Locale.ROOT) : null;
        }
        if (marker == 'x' && at + 14 <= text.length()) {
            StringBuilder digits = new StringBuilder(6);
            for (int k = at + 2; k < at + 14; k += 2) {
                char prefix = text.charAt(k);
                if (prefix != '&' && prefix != '§') {
                    return null;
                }
                digits.append(text.charAt(k + 1));
            }
            return isHex(digits.toString()) ? digits.toString().toLowerCase(Locale.ROOT) : null;
        }
        return null;
    }

    private static boolean isHex(String digits) {
        for (int k = 0; k < digits.length(); k++) {
            if (Character.digit(digits.charAt(k), 16) < 0) {
                return false;
            }
        }
        return true;
    }
}
