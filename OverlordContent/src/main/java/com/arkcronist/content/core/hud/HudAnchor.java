package com.arkcronist.content.core.hud;

import java.util.Locale;

/**
 * Where on the screen a bar on the action bar is drawn, by the vanilla HUD it sits against - above
 * the food bar, over the hearts - instead of by hand-counted {@code ascent} and {@code offset}.
 *
 * <p>The numbers are the 1.21.8 client's own, read from its {@code Gui}: the action bar line is
 * translated to {@code (width / 2, height - 68)} and written 4 pixels above that, so its text starts
 * 72 pixels from the bottom, centred; a bitmap glyph's top is drawn {@code 7 - ascent} below where
 * its line starts; hearts and food sit 39 pixels from the bottom, the armour and air rows 10 above
 * them, and the hotbar reaches 91 pixels either side of the middle - hearts from its left end, food
 * drawn leftwards from its right end. Everything is in GUI pixels, so it holds at any window size and
 * GUI scale.</p>
 *
 * <p>The plugin's action bar is laid out width-neutral (see {@link HudRenderer}), so the client
 * centres it on nothing and a bar starts exactly {@code offset} pixels right of the middle.</p>
 *
 * @param kind   what it sits against
 * @param row    rows further up, 10 pixels each - a second bar above the first
 * @param nudgeX pixels right (negative: left), for a final touch
 * @param nudgeY pixels up (negative: down)
 */
public record HudAnchor(Kind kind, int row, int nudgeX, int nudgeY) {

    /** Where the action bar's text starts, from the bottom of the screen: {@code height - 68 - 4}. */
    public static final int ACTION_BAR_TOP = -72;
    /** A bitmap glyph's top is drawn this far below its line's top, less its ascent. */
    public static final int GLYPH_BASELINE = 7;
    /** Hearts and food: {@code height - 39}. */
    public static final int STATUS_ROW = -39;
    /** One row of the status bars: armour above hearts, air above food. */
    public static final int ROW = 10;
    /** Half the hotbar: hearts start at {@code width / 2 - 91}, food ends at {@code width / 2 + 91}. */
    public static final int HALF_HOTBAR = 91;

    public enum Kind {
        /** On the hearts' row, from their left end. */
        HEALTH(STATUS_ROW, false),
        /** On the food row, against its right end, filling leftwards as food does. */
        FOOD(STATUS_ROW, true),
        /** The armour row, over the hearts. */
        ABOVE_HEALTH(STATUS_ROW - ROW, false),
        /** The air row, over the food - where bubbles show under water. */
        ABOVE_FOOD(STATUS_ROW - ROW, true);

        private final int top;
        private final boolean right;

        Kind(int top, boolean right) {
            this.top = top;
            this.right = right;
        }

        /** The anchor's name as written in YAML: {@code above-food}. */
        public String key() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }

        public static Kind parse(String text) {
            return valueOf(text.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        }
    }

    public HudAnchor {
        if (row < 0) {
            throw new IllegalArgumentException("row counts up from the anchor, from 0");
        }
    }

    /** Whether the bar hugs the right end of the hotbar, and fills from the right like food. */
    public boolean right() {
        return kind.right;
    }

    /** The top of the bar, in pixels from the bottom of the screen (negative). */
    public int top() {
        return kind.top - ROW * row - nudgeY;
    }

    /** The ascent the icons need for their top to land on {@link #top()}. */
    public int ascent() {
        return ACTION_BAR_TOP + GLYPH_BASELINE - top();
    }

    /**
     * Where the bar starts, in pixels right of the middle of the screen, for a bar {@code width}
     * pixels wide as drawn (its last pixel included, the font's gap after it not).
     */
    public int offset(int width) {
        return (right() ? HALF_HOTBAR - width : -HALF_HOTBAR) + nudgeX;
    }

    /**
     * How many rows up a bar of icons {@code height} tall can go here: a bitmap glyph's ascent may
     * not exceed its height, so a short icon cannot be lifted far above the line it is written on.
     */
    public static int maxRow(Kind kind, int height, int nudgeY) {
        // ascent = ACTION_BAR_TOP + GLYPH_BASELINE - (kind.top - ROW * row - nudgeY) <= height
        return Math.floorDiv(height - ACTION_BAR_TOP - GLYPH_BASELINE + kind.top - nudgeY, ROW);
    }
}
