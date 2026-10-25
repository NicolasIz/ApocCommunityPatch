package com.arkcronist.content.core.text;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Emojis in text written by an admin for another plugin - a LuckPerms prefix such as
 * {@code &c:crown: Admin} - turned into their glyphs, keeping that text in the form it came in.
 *
 * <p>An emoji is drawn white, as in chat, so the image keeps its own colours; the text after it
 * goes back to the colour and formats it had. Prefixes come in legacy codes ({@code &c},
 * {@code §c}, {@code &#ff0000}) or in MiniMessage tags: legacy text gets {@code &f} or {@code §f}
 * around each glyph and its colour put back after; MiniMessage text gets the glyph wrapped in
 * {@code <white>}. Nothing else in the text is touched.</p>
 */
public final class PrefixGlyphs {

    private static final Pattern KEYWORD = Pattern.compile(":([a-z0-9_]+):");

    private PrefixGlyphs() {
    }

    /**
     * @param glyph the glyph for an emoji name, or null for a name that is not an emoji - left as
     *              it was written
     */
    public static String replace(String text, Function<String, String> glyph) {
        if (text.indexOf(':') < 0) {
            return text;
        }
        boolean section = text.indexOf('§') >= 0;
        boolean legacy = section || hasAmpersandCode(text);
        if (!legacy && text.indexOf('<') >= 0) {
            return replaceMiniMessage(text, glyph);
        }
        return replaceLegacy(text, glyph, section ? '§' : '&');
    }

    private static String replaceMiniMessage(String text, Function<String, String> glyph) {
        Matcher matcher = KEYWORD.matcher(text);
        StringBuilder out = new StringBuilder(text.length() + 16);
        while (matcher.find()) {
            String found = glyph.apply(matcher.group(1));
            matcher.appendReplacement(out, Matcher.quoteReplacement(found == null ? matcher.group()
                    : "<white>" + found + "</white>"));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** Walks the text keeping the colour and formats in force, so each can be put back after a glyph. */
    private static String replaceLegacy(String text, Function<String, String> glyph, char code) {
        StringBuilder out = new StringBuilder(text.length() + 16);
        String colour = null;
        StringBuilder formats = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < text.length()) {
                int length = codeLength(text, i);
                if (length > 0) {
                    String sequence = text.substring(i, i + length);
                    char kind = Character.toLowerCase(text.charAt(i + 1));
                    if (kind >= 'k' && kind <= 'o') {
                        formats.append(sequence);
                    } else if (kind == 'r') {
                        colour = null;
                        formats.setLength(0);
                    } else {
                        colour = sequence;
                        formats.setLength(0);
                    }
                    out.append(sequence);
                    i += length;
                    continue;
                }
            }
            if (c == ':') {
                Matcher matcher = KEYWORD.matcher(text).region(i, text.length());
                if (matcher.lookingAt()) {
                    String found = glyph.apply(matcher.group(1));
                    if (found != null) {
                        out.append(code).append('f').append(found);
                        out.append(colour != null ? colour : code + "r").append(formats);
                        i = matcher.end();
                        continue;
                    }
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /**
     * The length of the colour or format code at {@code i}: 2 for {@code &c}, 8 for {@code &#rrggbb},
     * 14 for {@code §x§r§r§g§g§b§b}; 0 if there is none.
     */
    private static int codeLength(String text, int i) {
        char marker = text.charAt(i);
        char next = Character.toLowerCase(text.charAt(i + 1));
        if (next == '#' && i + 8 <= text.length() && isHex(text, i + 2, 6)) {
            return 8;
        }
        if (next == 'x' && i + 14 <= text.length()) {
            for (int k = 0; k < 6; k++) {
                int at = i + 2 + k * 2;
                if (text.charAt(at) != marker || !isHex(text, at + 1, 1)) {
                    return 0;
                }
            }
            return 14;
        }
        return "0123456789abcdefklmnor".indexOf(next) >= 0 ? 2 : 0;
    }

    private static boolean isHex(String text, int from, int count) {
        for (int k = from; k < from + count; k++) {
            if (Character.digit(text.charAt(k), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasAmpersandCode(String text) {
        for (int i = text.indexOf('&'); i >= 0 && i + 1 < text.length(); i = text.indexOf('&', i + 1)) {
            if (codeLength(text, i) > 0) {
                return true;
            }
        }
        return false;
    }
}
