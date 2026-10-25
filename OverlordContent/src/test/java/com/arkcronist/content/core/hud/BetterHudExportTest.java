package com.arkcronist.content.core.hud;

import com.arkcronist.content.core.definition.ResourceLocation;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 3.1: the demo's thirst, mana and shield bars, handed over to BetterHud where the action bar drew them. */
class BetterHudExportTest {

    /** 9 px icons, an advance of 10. */
    private static final HudRenderer.Glyphs ICONS = new HudRenderer.Glyphs('\uE000', 10, '\uE001', 10, '\uE002', 10);

    private static HudLayout hud(String id, HudAnchor anchor, int spacing, HudDefinition.BetterHud betterHud) {
        HudDefinition definition = new HudDefinition("demo", id, ResourceLocation.parse("demo:hud/full", "demo"),
                ResourceLocation.parse("demo:hud/half", "demo"), ResourceLocation.parse("demo:hud/empty", "demo"), 9,
                anchor.ascent(), 10, spacing, 0, new HudDefinition.Source.Stored(20, 20, 0, 0, Map.of()), true,
                Path.of("."), Path.of("huds.yml"), anchor, betterHud);
        return new HudLayout(definition, ICONS);
    }

    private static BufferedImage icon(int rgb) {
        BufferedImage icon = new BufferedImage(9, 9, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 9; y++) {
            for (int x = 0; x < 8; x++) {
                icon.setRGB(x, y, rgb);
            }
        }
        return icon;
    }

    @Test
    void theBarsKeepTheirPlacesAgainstTheHotbar() {
        // Thirst: one row over the air bubbles, its right end on the food bar's - 8 px apart, 80 px drawn.
        HudLayout thirst = hud("thirst", new HudAnchor(HudAnchor.Kind.ABOVE_FOOD, 1, 0, 0), -2, null);
        assertEquals(8, BetterHudExport.step(thirst));
        assertEquals(81, BetterHudExport.drawnWidth(thirst));
        BetterHudExport.Place place = BetterHudExport.place(thirst, false, 81);
        assertEquals(new HudDefinition.Spot(50, 100), place.gui(), "the middle of the bottom of the screen");
        assertEquals(new HudDefinition.Spot(10 + 40, -59), place.pixel(),
                "its left end 91 - 81 right of it - BetterHud centres the image on x - and 59 up");
        // Mana from the hearts' left end, the shield a row over it.
        assertEquals(new HudDefinition.Spot(-91 + 40, -59),
                BetterHudExport.place(hud("mana", new HudAnchor(HudAnchor.Kind.ABOVE_HEALTH, 1, 0, 0), -2, null), false, 81)
                        .pixel());
        assertEquals(new HudDefinition.Spot(-91 + 40, -69),
                BetterHudExport.place(hud("shield", new HudAnchor(HudAnchor.Kind.ABOVE_HEALTH, 2, 0, 0), -2, null), false,
                        81).pixel());
        // PrimeHUD's BetterHud keeps the legacy offset, which lifts every HUD 54 px: put back down.
        assertEquals(new HudDefinition.Spot(50, -5), BetterHudExport.place(thirst, true, 81).pixel());
        // A place of its own wins.
        HudLayout moved = hud("mana", new HudAnchor(HudAnchor.Kind.ABOVE_HEALTH, 1, 0, 0), -2,
                new HudDefinition.BetterHud(true, new HudDefinition.Spot(0, 0), new HudDefinition.Spot(45, 172)));
        assertEquals(new BetterHudExport.Place(new HudDefinition.Spot(0, 0), new HudDefinition.Spot(45, 172)),
                BetterHudExport.place(moved, true, 81), "BetterHud's own terms, as written");
    }

    @Test
    void eachBarIsTwoImagesAndAListenerOnItsPlaceholders() throws IOException {
        HudLayout thirst = hud("thirst", new HudAnchor(HudAnchor.Kind.ABOVE_FOOD, 1, 0, 0), -2, null);
        HudLayout mana = hud("mana", new HudAnchor(HudAnchor.Kind.ABOVE_HEALTH, 1, 0, 0), -2, null);
        Map<String, byte[]> files = BetterHudExport.files(List.of(
                new BetterHudExport.Bar(thirst, "thirst", icon(0xFF3080FF), icon(0xFF3080FF), icon(0xFF202020)),
                new BetterHudExport.Bar(mana, "demo:mana", icon(0xFF80FFFF), null, null)));
        assertEquals(List.of("assets/arkcontent/demo_thirst_bar.png", "assets/arkcontent/demo_thirst_empty.png",
                "assets/arkcontent/demo_mana_bar.png", "images/arkcontent.yml", "layouts/arkcontent.yml",
                "huds/arkcontent.yml"), List.copyOf(files.keySet()));

        BufferedImage bar = ImageIO.read(new ByteArrayInputStream(files.get("assets/arkcontent/demo_thirst_bar.png")));
        assertEquals(9 * 8 + 9, bar.getWidth(), "ten icons 8 px apart, the last one whole");
        assertEquals(9, bar.getHeight());
        assertEquals(0xFF3080FF, bar.getRGB(72, 4), "the tenth icon starts at 72");
        assertEquals(0, bar.getRGB(80, 4) >>> 24, "its own transparent column stays transparent");

        String images = new String(files.get("images/arkcontent.yml"), StandardCharsets.UTF_8);
        assertTrue(images.contains("""
                arkcontent_demo_thirst:
                  type: listener
                  file: "arkcontent/demo_thirst_bar.png"
                  split: 20
                  split-type: right
                  setting:
                    listener:
                      class: placeholder
                      value: "(number)papi:arkcontent_hud_thirst_value"
                      max: "(number)papi:arkcontent_hud_thirst_max"
                """), images);
        assertTrue(images.contains("arkcontent_demo_thirst_empty:\n  type: single\n"), images);
        // No half icon: whole icons, ten steps; the hearts' side empties from the right.
        assertTrue(images.contains("split: 10\n  split-type: left"), images);
        assertTrue(images.contains("\"(number)papi:arkcontent_hud_demo:mana_value\""), images);
        assertFalse(images.contains("arkcontent_demo_mana_empty"), "no empty icon, no background");

        String huds = new String(files.get("huds/arkcontent.yml"), StandardCharsets.UTF_8);
        assertTrue(huds.contains("""
                arkcontent_demo_thirst:
                  default: true
                  layouts:
                    1:
                      name: arkcontent_demo_thirst
                      gui:
                        x: 50
                        y: 100
                      pixel:
                        x: 50
                        y: -59
                """), huds);
        String layouts = new String(files.get("layouts/arkcontent.yml"), StandardCharsets.UTF_8);
        assertTrue(layouts.contains("      name: arkcontent_demo_thirst_empty\n"), layouts);
        assertTrue(layouts.contains("      name: arkcontent_demo_thirst\n      x: 0\n      y: 0\n      layer: 1\n"), layouts);

        assertTrue(BetterHudExport.files(List.of()).isEmpty(), "no bars: nothing to write, the old files go");
    }

    /** An icon drawn at another size than its texture's is scaled as the font scales it, pixels kept sharp. */
    @Test
    void largerIconsAreDrawnAtTheBarsHeight() {
        BufferedImage big = new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB);
        big.setRGB(0, 0, 0xFFFF0000);
        big.setRGB(1, 1, 0xFFFF0000);
        BufferedImage strip = BetterHudExport.strip(big, 9, 8, 3);
        assertEquals(9, strip.getHeight());
        assertEquals(2 * 8 + 9, strip.getWidth());
        assertEquals(0xFFFF0000, strip.getRGB(0, 0));
        assertEquals(0xFFFF0000, strip.getRGB(8, 0));
    }
}
