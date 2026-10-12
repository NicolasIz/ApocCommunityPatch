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
     * 3.0.4: how many of a bar's segments a plain-text bar - Bedrock's, with no icons - shows filled:
     * a half segment counts as filled, so a bar with anything left in it never reads empty.
     */
    public static int plainFilled(double value, double max, int segments) {
        return (halves(value, max, segments) + 1) / 2;
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

    /** One icon of a bar as drawn: its character and how far it moves the pen. */
    public record Icon(char character, int advance) {
    }

    /**
     * 2.0: the icons a bar draws for {@code value}, in the order they stand on the screen from left
     * to right. Filling from the right, as the food bar does, the full ones come last.
     */
    public static java.util.List<Icon> icons(Glyphs glyphs, int segments, double value, double max, boolean fromRight) {
        int halves = halves(value, max, segments);
        if (!glyphs.hasHalf()) {
            halves = halves / 2 * 2;
        }
        java.util.List<Icon> icons = new java.util.ArrayList<>(segments);
        for (int segment = 0; segment < segments; segment++) {
            int filled = Math.min(2, Math.max(0, halves - segment * 2));
            if (filled == 2) {
                icons.add(new Icon(glyphs.full(), glyphs.fullAdvance()));
            } else if (filled == 1) {
                icons.add(new Icon(glyphs.half(), glyphs.halfAdvance()));
            } else if (glyphs.hasEmpty()) {
                icons.add(new Icon(glyphs.empty(), glyphs.emptyAdvance()));
            }
        }
        if (fromRight) {
            java.util.Collections.reverse(icons);
        }
        return icons;
    }

    /**
     * How wide a run of icons is drawn: from the first one's left edge to the last one's right edge,
     * the font's one-pixel gap after it left out.
     */
    public static int width(java.util.List<Icon> icons, int spacing) {
        if (icons.isEmpty()) {
            return 0;
        }
        int width = spacing * (icons.size() - 1) - 1;
        for (Icon icon : icons) {
            width += icon.advance();
        }
        return width;
    }

    /** The text of a run of icons drawn from {@code offset}, ending where it started. */
    public static String place(java.util.List<Icon> icons, int spacing, int offset) {
        StringBuilder text = new StringBuilder(Spaces.of(offset));
        int moved = 0;
        boolean first = true;
        for (Icon icon : icons) {
            if (!first && spacing != 0) {
                text.append(Spaces.of(spacing));
                moved += spacing;
            }
            text.append(icon.character());
            moved += icon.advance();
            first = false;
        }
        text.append(Spaces.of(-(offset + moved)));
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
