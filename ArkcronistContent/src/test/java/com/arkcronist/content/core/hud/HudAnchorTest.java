package com.arkcronist.content.core.hud;

import com.arkcronist.content.core.definition.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 2.0: bars placed against the vanilla HUD. The pixel rows and columns are the 1.21.8 client's:
 * action bar text from {@code height - 72}, a glyph's top {@code 7 - ascent} below that, hearts and
 * food from {@code height - 39}, the rows above 10 higher, the hotbar 91 either side of the middle.
 */
class HudAnchorTest {

    /** 9 px icons with an advance of 10: the width of the hearts and the food icons, plus the font's gap. */
    private static final HudRenderer.Glyphs DROPS = new HudRenderer.Glyphs('', 10, '', 10, '', 10);

    @Test
    void aBarAboveTheFoodSitsOnTheAirRow() {
        HudAnchor above = new HudAnchor(HudAnchor.Kind.ABOVE_FOOD, 0, 0, 0);
        assertEquals(-49, above.top(), "the air bubbles' row: height - 49");
        assertEquals(-16, above.ascent(), "-72 + 7 - (-49)");
        // Ten icons 9 px wide, 8 px apart as vanilla's food: spacing -2 on an advance of 10.
        // Visible from the first icon's left edge to the last one's right edge: 9 + 9 * 8 = 81.
        List<HudRenderer.Icon> full = HudRenderer.icons(DROPS, 10, 20, 20, true);
        assertEquals(81, HudRenderer.width(full, -2));
        assertEquals(10, above.offset(81), "starts where the food starts: centre + 91 - 81");
    }

    @Test
    void rowsStackTenPixelsAndHealthStartsAtTheHotbarsLeftEnd() {
        HudAnchor second = new HudAnchor(HudAnchor.Kind.ABOVE_FOOD, 1, 0, 0);
        assertEquals(-59, second.top());
        assertEquals(-6, second.ascent());
        HudAnchor hearts = new HudAnchor(HudAnchor.Kind.HEALTH, 0, 0, 0);
        assertEquals(-39, hearts.top());
        assertEquals(-26, hearts.ascent());
        assertEquals(-91, hearts.offset(81), "left side: whatever its width");
        HudAnchor nudged = new HudAnchor(HudAnchor.Kind.ABOVE_HEALTH, 0, 3, 2);
        assertEquals(-51, nudged.top(), "2 up");
        assertEquals(-88, nudged.offset(50), "3 right");
    }

    /** A glyph's ascent may not exceed its height: a 9 px icon reaches three rows above the food at most. */
    @Test
    void howHighAShortIconCanGo() {
        assertEquals(3, HudAnchor.maxRow(HudAnchor.Kind.FOOD, 9, 0));
        assertEquals(2, HudAnchor.maxRow(HudAnchor.Kind.ABOVE_FOOD, 9, 0));
        assertEquals(9, new HudAnchor(HudAnchor.Kind.FOOD, 3, 0, 0).ascent() + 5, "row 3: ascent 4, under 9");
        assertEquals(11, HudAnchor.maxRow(HudAnchor.Kind.ABOVE_FOOD, 100, 0), "taller icons go higher: row 11 is ascent 94");
        assertThrows(IllegalArgumentException.class, () -> new HudAnchor(HudAnchor.Kind.FOOD, -1, 0, 0));
        assertEquals(HudAnchor.Kind.ABOVE_FOOD, HudAnchor.Kind.parse("above-food"));
        assertEquals("above-health", HudAnchor.Kind.ABOVE_HEALTH.key());
    }

    /**
     * On the food side a bar fills from the right like food: 7.5 of 10 shows its empty icons on the
     * left and its right end stays on the food bar's, every icon or only the drawn ones.
     */
    @Test
    void aFoodSideBarFillsFromTheRightAndKeepsItsRightEnd() {
        HudDefinition definition = new HudDefinition("demo", "thirst", ResourceLocation.parse("demo:hud/full", "demo"),
                ResourceLocation.parse("demo:hud/half", "demo"), ResourceLocation.parse("demo:hud/empty", "demo"), 9,
                -16, 10, -2, 0, new HudDefinition.Source.Stored(20, 20, 0, 0, Map.of()), true, Path.of("."),
                Path.of("hud.yml"), new HudAnchor(HudAnchor.Kind.ABOVE_FOOD, 0, 0, 0));
        HudLayout layout = new HudLayout(definition, DROPS);
        String bar = layout.render(15, 20);
        String icons = bar.replaceAll("[--]", "");
        assertEquals("", icons, "empty, half, then full");
        assertEquals(0, HudRenderer.netWidth(bar, DROPS), "width-neutral: the action bar stays centred");
        assertEquals(Spaces.of(10), bar.substring(0, Spaces.of(10).length()), "starts at centre + 10");

        // Without empty icons the bar shrinks - and still ends on the food bar's right end.
        HudRenderer.Glyphs noEmpty = new HudRenderer.Glyphs('', 10, '', 10, (char) 0, 0);
        HudLayout shrinking = new HudLayout(definition, noEmpty);
        String three = shrinking.render(6, 20);
        assertEquals("", three.replaceAll("[--]", ""));
        int visible = 3 * 10 - 2 * 2 - 1;
        assertEquals(Spaces.of(91 - visible), three.substring(0, Spaces.of(91 - visible).length()));
    }
}
