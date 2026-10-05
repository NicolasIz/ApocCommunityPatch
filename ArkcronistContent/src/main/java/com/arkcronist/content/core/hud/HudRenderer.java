package com.arkcronist.content.core.hud;

/**
 * Turns a bar's value into the text that draws it: a run of icon characters and space characters,
 * starting {@code offset} pixels along and ending where it started - so whatever follows on the
 * line is drawn as if the bar were not there, and two bars written one after the other each sit
 * at their own place.
 *
 * <p>Pure: the characters and their widths are worked out when the pack is built; this only
 * counts.</p>
 */
public final class HudRenderer {

    /**
     * A HUD's characters in the font, and how wide each is.
     *
     * @param half  0 when the HUD has no half icon
     * @param empty 0 when the HUD has no empty icon
     */
    public record Glyphs(char full, int fullAdvance, char half, int halfAdvance, char empty, int emptyAdvance) {

        public boolean hasHalf() {
            return half != 0;
        }

        public boolean hasEmpty() {
            return empty != 0;
        }
    }

    private HudRenderer() {
    }

    /**
     * How many segments are full, and whether one more is half: the value's share of the bar,
     * rounded down to half segments - a bar is full only when the value is.
     *
     * @return halves: segments * 2 at most
     */
    public static int halves(double value, double max, int segments) {
        if (!(max > 0) || !(value > 0) || segments <= 0) {
            return 0;
        }
        double share = Math.min(1, value / max);
        return (int) Math.floor(share * segments * 2 + 1e-9);
    }

    /**
     * The text of a bar.
     *
     * @param spacing pixels between icons, besides the font's own one
     * @param offset  where the bar starts, in pixels from where it is written
     */
    public static String render(Glyphs glyphs, int segments, int spacing, int offset, double value, double max) {
        int halves = halves(value, max, segments);
        if (!glyphs.hasHalf()) {
            halves = halves / 2 * 2;
        }
        StringBuilder text = new StringBuilder(Spaces.of(offset));
        int width = 0;
        boolean first = true;
        for (int segment = 0; segment < segments; segment++) {
            int filled = Math.min(2, Math.max(0, halves - segment * 2));
            char icon;
            int advance;
            if (filled == 2) {
                icon = glyphs.full();
                advance = glyphs.fullAdvance();
            } else if (filled == 1) {
                icon = glyphs.half();
                advance = glyphs.halfAdvance();
            } else if (glyphs.hasEmpty()) {
                icon = glyphs.empty();
                advance = glyphs.emptyAdvance();
            } else {
                continue;
            }
            if (!first && spacing != 0) {
                text.append(Spaces.of(spacing));
                width += spacing;
            }
            text.append(icon);
            width += advance;
            first = false;
        }
        // Back to where the bar was written: the rest of the line is not moved by it.
        text.append(Spaces.of(-(offset + width)));
        return text.toString();
    }

    /**
     * How far a rendered bar moves the text on, all of it counted: 0 for every bar this class
     * renders. For checks and tests.
     */
    public static int netWidth(String rendered, Glyphs glyphs) {
        int width = 0;
        for (int i = 0; i < rendered.length(); i++) {
            char c = rendered.charAt(i);
            if (c == glyphs.full()) {
                width += glyphs.fullAdvance();
            } else if (glyphs.hasHalf() && c == glyphs.half()) {
                width += glyphs.halfAdvance();
            } else if (glyphs.hasEmpty() && c == glyphs.empty()) {
                width += glyphs.emptyAdvance();
            } else {
                width += Spaces.width(String.valueOf(c));
            }
        }
        return width;
    }
}
