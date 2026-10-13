package com.arkcronist.content.core.hud;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Negative spaces, bars, and the widths a bar is laid out with. */
class HudTest {

    private static final HudRenderer.Glyphs THIRST = new HudRenderer.Glyphs('', 9, '', 9, '', 9);

    @Test
    void spacesUseTheUsualCharactersAndAddUpToAnyWidth() {
        Map<String, Integer> advances = Spaces.advances();
        assertEquals(30, advances.size());
        assertEquals(-1, advances.get(""));
        assertEquals(-8, advances.get(""));
        assertEquals(-16, advances.get(""));
        assertEquals(-1024, advances.get(""));
        assertEquals(1, advances.get(""));
        assertEquals(1024, advances.get(""));

        for (int pixels = -3000; pixels <= 3000; pixels++) {
            assertEquals(pixels, Spaces.width(Spaces.of(pixels)), "pixels " + pixels);
        }
        assertEquals("", Spaces.of(0));
        // Steps of 128 at most: ItemsAdder's font draws the three larger ones a pixel wider.
        assertEquals("\uf80c".repeat(8) + "\uf808\uf803", Spaces.of(-1035), "the largest steps first");
        assertEquals("\uf82c\uf82a\uf825", Spaces.of(165));
        assertTrue(Spaces.of(-3000).chars().allMatch(character -> character <= 0xF80C));
    }

    @Test
    void aBarIsFullOnlyWhenTheValueIs() {
        assertEquals(20, HudRenderer.halves(20, 20, 10));
        assertEquals(19, HudRenderer.halves(19.99, 20, 10));
        assertEquals(15, HudRenderer.halves(15, 20, 10));
        assertEquals(0, HudRenderer.halves(0.5, 20, 10), "less than half a segment shows nothing");
        assertEquals(0, HudRenderer.halves(-5, 20, 10));
        assertEquals(0, HudRenderer.halves(5, 0, 10), "no max, no bar");
        assertEquals(20, HudRenderer.halves(50, 20, 10), "over the max is full");
    }

    @Test
    void aBarDrawsItsIconsAndEndsWhereItStarted() {
        String bar = HudRenderer.render(THIRST, 10, -1, 10, 15, 20);

        // 7 full, 1 half, 2 empty.
        assertEquals(7, count(bar, THIRST.full()));
        assertEquals(1, count(bar, THIRST.half()));
        assertEquals(2, count(bar, THIRST.empty()));
        assertTrue(bar.startsWith(Spaces.of(10)));
        assertEquals(0, HudRenderer.netWidth(bar, THIRST), "the rest of the line does not move");
        // Icons are full, then half, then empty, left to right.
        String icons = bar.replaceAll("[-]", "");
        assertEquals("".repeat(7) + "" + "".repeat(2), icons);
    }

    @Test
    void withoutAHalfIconItRoundsDownAndWithoutAnEmptyOneTheBarShrinks() {
        HudRenderer.Glyphs noHalf = new HudRenderer.Glyphs('', 10, (char) 0, 0, '', 10);
        String rounded = HudRenderer.render(noHalf, 10, 0, -91, 15, 20);
        assertEquals(7, count(rounded, ''));
        assertEquals(3, count(rounded, ''));
        assertEquals(0, HudRenderer.netWidth(rounded, noHalf));

        HudRenderer.Glyphs onlyFull = new HudRenderer.Glyphs('', 10, (char) 0, 0, (char) 0, 0);
        String shrunk = HudRenderer.render(onlyFull, 10, -2, 0, 4, 20);
        assertEquals(2, count(shrunk, ''));
        assertEquals(0, HudRenderer.netWidth(shrunk, onlyFull));
        assertEquals(0, HudRenderer.netWidth(HudRenderer.render(onlyFull, 10, -2, 0, 0, 20), onlyFull));
    }

    @Test
    void anIconIsAsWideAsItsLastVisibleColumnScaledPlusOne() {
        BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        // Pixels in columns 0 to 11; 12 to 15 transparent.
        for (int x = 0; x < 12; x++) {
            icon.setRGB(x, 8, 0xFF3366CC);
        }
        assertEquals(12, GlyphMetrics.actualWidth(icon));
        assertEquals(13, GlyphMetrics.advance(icon, 16));
        // Drawn 9 high: 12 * 9 / 16 = 6.75, rounded to 7, plus one.
        assertEquals(8, GlyphMetrics.advance(icon, 9));
        assertEquals(1, GlyphMetrics.advance(new BufferedImage(9, 9, BufferedImage.TYPE_INT_ARGB), 9), "empty");
    }

    @Test
    void textWidthFollowsTheDefaultFont() {
        assertEquals(6 * 4 + 2, DefaultFontWidths.width("Ammo:", false), "A, m, m, o are six each, the colon two");
        assertEquals(2 + 3, DefaultFontWidths.width("il", false));
        assertEquals(DefaultFontWidths.width("Pistola", false) + 7, DefaultFontWidths.width("Pistola", true));
        assertEquals(DefaultFontWidths.width("i", false), DefaultFontWidths.width("í", false), "accents as their letter");
        assertFalse(DefaultFontWidths.width("12 / 12", false) <= 0);
    }

    private static int count(String text, char c) {
        return (int) text.chars().filter(ch -> ch == c).count();
    }
}
