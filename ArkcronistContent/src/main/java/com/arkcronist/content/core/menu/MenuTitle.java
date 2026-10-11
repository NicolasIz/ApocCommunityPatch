package com.arkcronist.content.core.menu;

import com.arkcronist.content.core.hud.Spaces;

/**
 * A container title that draws a whole picture behind the inventory - a custom GUI - before its
 * own text, the way resource-pack menus do it: the picture is one character of a font image, and
 * negative spaces put it at the inventory's corner and bring the pen back for the text.
 *
 * <pre>
 *   the client starts a title  8 px right of the inventory's left edge
 *   Spaces.of(-8)              back to the edge
 *   the picture's character    drawn from the edge, the pen moves on width + 1
 *   Spaces.of(8 - width - 1)   back to where the title starts
 *   the title text             where a plain title would be
 * </pre>
 *
 * <p>How far a bitmap character moves the pen is its width up to its last column with any pixel in
 * it, plus one; a picture that covers the whole inventory is 176 wide. The picture's top lines up
 * with the inventory's when its font image has an ascent of 13: the title is drawn 6 px down, with
 * its baseline 7 below that.</p>
 */
public final class MenuTitle {

    /** Where the client starts a container's title, from the inventory's left edge. */
    public static final int TITLE_X = 8;
    /** A chest inventory's width: what a picture covering it is wide. */
    public static final int CHEST_WIDTH = 176;
    /** The ascent that puts a picture's top at the inventory's top. */
    public static final int FULL_ASCENT = 13;

    private MenuTitle() {
    }

    /**
     * The characters that draw {@code glyph} behind the inventory and leave the pen where the title
     * text starts.
     *
     * @param width the picture's drawn width, up to its last column with a pixel in it
     */
    public static String background(String glyph, int width) {
        return background(glyph, width, 0);
    }

    /**
     * As {@link #background(String, int)}, for a picture whose left edge is {@code left} pixels from
     * the inventory's - negative for one wider than the chest.
     */
    public static String background(String glyph, int width, int left) {
        return Spaces.of(left - TITLE_X) + glyph + Spaces.of(TITLE_X - left - (width + 1));
    }

    /**
     * The spaces that take the pen from where the title starts to where text {@code advance} pixels
     * wide - as {@code DefaultFontWidths} counts it, the last character's spacing pixel included -
     * sits centred on {@code centre}, measured from the inventory's left edge.
     */
    public static String centring(int advance, int centre) {
        int drawn = Math.max(0, advance - 1);
        return Spaces.of(centre - drawn / 2 - TITLE_X);
    }
}
