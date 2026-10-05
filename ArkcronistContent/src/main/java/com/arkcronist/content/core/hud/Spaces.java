package com.arkcronist.content.core.hud;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Characters that draw nothing and move the text on by a set number of pixels - forwards, or
 * backwards - written into the default font as a {@code space} provider. With them text can be
 * placed anywhere on a line, and drawn over itself: the base of every HUD bar.
 *
 * <p>The characters are the ones the community's "negative space font" made usual, so menus already
 * written with them work as they are:</p>
 *
 * <pre>
 *   U+F801..U+F808   -1 .. -8       U+F821..U+F828   1 .. 8
 *   U+F809..U+F80F   -16 .. -1024   U+F829..U+F82F   16 .. 1024   (doubling)
 * </pre>
 */
public final class Spaces {

    private static final int NEGATIVE = 0xF800;
    private static final int POSITIVE = 0xF820;
    /** Larger steps than this are written as several of the largest. */
    private static final int LARGEST = 1024;

    private Spaces() {
    }

    /** Every space character and how far it moves, for the font's {@code space} provider. */
    public static Map<String, Integer> advances() {
        Map<String, Integer> advances = new LinkedHashMap<>();
        for (int sign : new int[]{-1, 1}) {
            int base = sign < 0 ? NEGATIVE : POSITIVE;
            for (int pixels = 1; pixels <= 8; pixels++) {
                advances.put(String.valueOf((char) (base + pixels)), sign * pixels);
            }
            int index = 9;
            for (int pixels = 16; pixels <= LARGEST; pixels *= 2) {
                advances.put(String.valueOf((char) (base + index++)), sign * pixels);
            }
        }
        return advances;
    }

    /**
     * Characters moving the text on by {@code pixels}: right when positive, left when negative,
     * nothing for 0. The fewest characters that add up to it - the largest steps first.
     */
    public static String of(int pixels) {
        if (pixels == 0) {
            return "";
        }
        int base = pixels < 0 ? NEGATIVE : POSITIVE;
        int left = Math.abs(pixels);
        StringBuilder text = new StringBuilder();
        while (left >= 16) {
            int step = Math.min(LARGEST, Integer.highestOneBit(left));
            text.append((char) (base + 9 + Integer.numberOfTrailingZeros(step) - 4));
            left -= step;
        }
        while (left > 0) {
            int step = Math.min(8, left);
            text.append((char) (base + step));
            left -= step;
        }
        return text.toString();
    }

    /** How far a string of these characters moves the text, for tests and checks. */
    public static int width(String spaces) {
        Map<String, Integer> advances = advances();
        int width = 0;
        for (int i = 0; i < spaces.length(); i++) {
            Integer advance = advances.get(String.valueOf(spaces.charAt(i)));
            if (advance == null) {
                throw new IllegalArgumentException("not a space character: U+"
                        + Integer.toHexString(spaces.charAt(i)).toUpperCase());
            }
            width += advance;
        }
        return width;
    }
}
