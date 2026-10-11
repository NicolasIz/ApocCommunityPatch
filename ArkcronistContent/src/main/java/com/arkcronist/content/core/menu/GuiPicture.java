package com.arkcronist.content.core.menu;

import java.util.List;
import java.util.Optional;

/**
 * A picture a menu is drawn over, as the client places a font image in a container's title: its
 * size, and where its top-left corner is from the inventory's own - left of it and above it when
 * negative, so a picture can be larger than the chest and frame it.
 *
 * <p>The two the plugin ships are here. Any other font image named as a menu's background is taken
 * to start at the inventory's left edge, with the width the config gives it.</p>
 *
 * @param width  drawn width, up to its last column with a pixel in it: what the client advances by
 * @param height the picture's height; the font draws it one to one
 * @param left   its left edge from the inventory's, in pixels
 * @param top    its top edge from the inventory's
 */
public record GuiPicture(String name, int width, int height, int left, int top) {

    /**
     * The content browser: a book with a spiral binding, ten pixels wider than the chest on each
     * side and 22 taller above it, covering the player's slots too. Its rounded corners are
     * transparent; everywhere the chest's own grey would show, it is opaque.
     */
    public static final GuiPicture CONTENT_BROWSER = new GuiPicture("content_browser", 196, 246, -10, -22);
    /** The quest board's parchment: exactly the chest. */
    public static final GuiPicture QUEST_BOARD = new GuiPicture("quest_board", 176, 222, 0, 0);
    public static final List<GuiPicture> BUILTIN = List.of(CONTENT_BROWSER, QUEST_BOARD);

    /** The font ascent that puts the picture's top {@link #top} pixels from the inventory's top. */
    public int ascent() {
        return MenuTitle.FULL_ASCENT - top;
    }

    /** The middle of the picture across, from the inventory's left edge: where a title is centred. */
    public int centre() {
        return left + width / 2;
    }

    public String fullId() {
        return "arkcontent:" + name;
    }

    /** One of the plugin's own pictures, by its font image name ({@code arkcontent:content_browser}). */
    public static Optional<GuiPicture> builtin(String fullId) {
        return BUILTIN.stream().filter(picture -> picture.fullId().equals(fullId)).findFirst();
    }
}
