package com.arkcronist.content.core.menu;

import com.arkcronist.content.core.hud.DefaultFontWidths;
import com.arkcronist.content.core.hud.Spaces;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The content browser's book: where the picture goes, where the title lands, and the picture itself. */
class BookMenuTest {

    private static final String GLYPH = "";

    @Test
    void theBookOverhangsTheChestOnEverySide() {
        GuiPicture book = GuiPicture.CONTENT_BROWSER;
        assertEquals(35, book.ascent(), "22 pixels above the chest's top: ascent 13 + 22");
        assertEquals(88, book.centre(), "centred on the chest, which is 176 wide");
        assertEquals(13, GuiPicture.QUEST_BOARD.ascent());
        assertEquals(book, GuiPicture.builtin("arkcontent:content_browser").orElseThrow());
        assertTrue(GuiPicture.builtin("arkcontent:something_else").isEmpty());
    }

    @Test
    void aPictureLeftOfTheChestStillLeavesThePenWhereTheTitleStarts() {
        String title = MenuTitle.background(GLYPH, 196, -10);
        int at = title.indexOf(GLYPH);
        assertEquals(-18, Spaces.width(title.substring(0, at)), "from x = 8 back to x = -10");
        int pen = MenuTitle.TITLE_X + Spaces.width(title.substring(0, at)) + (196 + 1) + Spaces.width(title.substring(at + 1));
        assertEquals(MenuTitle.TITLE_X, pen);
        assertEquals(MenuTitle.background(GLYPH, 176), MenuTitle.background(GLYPH, 176, 0));
    }

    @Test
    void theTitleIsCentredOnTheBook() {
        int advance = DefaultFontWidths.width("Categories", false);
        int start = MenuTitle.TITLE_X + Spaces.width(MenuTitle.centring(advance, 88));
        int drawnEnd = start + advance - 1;
        assertTrue(Math.abs((88 - start) - (drawnEnd - 88)) <= 1, "as much on each side, to the pixel: " + start);
        // A name too long to centre in the book still starts somewhere: the spaces never fail.
        assertNotNull(MenuTitle.centring(400, 88));
    }

    @Test
    void theBookIsOpaqueWhereverTheChestsGreyWouldShowAndRoundedOutside() throws IOException {
        GuiPicture book = GuiPicture.CONTENT_BROWSER;
        BufferedImage image = picture("gui/content_browser.png");
        assertEquals(book.width(), image.getWidth());
        assertEquals(book.height(), image.getHeight());
        int x0 = -book.left(), y0 = -book.top();
        for (int y = y0; y < y0 + 222; y++) {
            for (int x = x0; x < x0 + 176; x++) {
                assertEquals(255, image.getRGB(x, y) >>> 24, "opaque over the chest at " + (x - x0) + "," + (y - y0));
            }
        }
        for (int[] corner : new int[][]{{0, 0}, {195, 0}, {0, 245}, {195, 245}}) {
            assertEquals(0, image.getRGB(corner[0], corner[1]) >>> 24, "a rounded corner is see-through");
        }
        boolean lastColumnDrawn = false;
        for (int y = 0; y < image.getHeight(); y++) {
            lastColumnDrawn |= (image.getRGB(book.width() - 1, y) >>> 24) != 0;
        }
        assertTrue(lastColumnDrawn, "the client measures the glyph to its last drawn column: it must be the width");
    }

    @Test
    void theButtonsHaveTheirIcons() throws IOException {
        for (String name : List.of("previous", "next", "up", "home", "filter", "close", "page")) {
            BufferedImage icon = picture("menu/" + name + ".png");
            assertEquals(16, icon.getWidth(), name);
            assertEquals(16, icon.getHeight(), name);
        }
        BufferedImage board = picture("gui/quest_board.png");
        assertEquals(176, board.getWidth());
        assertEquals(222, board.getHeight());
    }

    @Test
    void contentSlotsReadAsRanges() {
        List<String> problems = new ArrayList<>();
        assertEquals(45, SlotList.parse("0-44", 44, problems).size());
        assertEquals(List.of(10, 11, 12, 19, 20, 21, 30), SlotList.parse("19-21, 10-12, 30, 11", 44, problems));
        assertTrue(problems.isEmpty());
        assertEquals(List.of(1), SlotList.parse("1, 50, x, 9-3", 44, problems));
        assertEquals(3, problems.size(), "out of range, unreadable and backwards are each reported: " + problems);
        assertTrue(SlotList.parse("", 44, new ArrayList<>()).isEmpty());
    }

    private static BufferedImage picture(String path) throws IOException {
        try (InputStream in = BookMenuTest.class.getResourceAsStream("/builtin/textures/" + path)) {
            assertNotNull(in, path);
            return ImageIO.read(in);
        }
    }
}
