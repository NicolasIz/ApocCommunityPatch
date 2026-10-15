package com.arkcronist.content.core.hud;

import com.arkcronist.content.core.definition.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 3.1.1: a BetterHud set up as PrimeHUD is - health and hunger bars, 66 x 12 frames, at x 45 and 115
 * on row 140, armour and air a row lower at 155, in HUDs placed at the screen's top left - gets this
 * plugin's bars as more of the same, from row 170.
 */
class BetterHudPanelTest {

    @TempDir
    Path folder;

    private void write(String path, String text) throws IOException {
        Path file = folder.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    private void png(String path, int width, int height) throws IOException {
        Path file = folder.resolve(path);
        Files.createDirectories(file.getParent());
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png", file.toFile());
    }

    private void primeHudLike(int frameHeight) throws IOException {
        write("images/default-image.yml", """
                health_bar:
                  type: single
                  file: bars/health_bar.png
                hunger_bar:
                  type: single
                  file: bars/hunger_bar.png
                """);
        png("assets/bars/health_bar.png", 66, frameHeight);
        write("layouts/default-layout.yml", """
                health:
                  images:
                    1: { name: health_bar, x: 45, y: 140 }
                    2: { name: health, x: 46, y: 141 }
                hunger:
                  images:
                    1: { name: hunger_bar, x: 115, y: 140 }
                armor:
                  images:
                    1: { name: armor_bar, x: 45, y: 155 }
                air:
                  images:
                    1: { name: air_bar, x: 115, y: 155 }
                """);
        write("huds/health.yml", """
                health:
                  layouts:
                    9:
                      name: health
                      x: 0
                      y: 0
                """);
        // This plugin's own files are not BetterHud's bars.
        write("layouts/arkcontent.yml", """
                arkcontent_demo_mana:
                  images:
                    1: { name: health_bar, x: 900, y: 900 }
                """);
        write("huds/-skipped.yml", "nothing: {}\n");
    }

    @Test
    void primeHudsBarsAreFound() throws IOException {
        primeHudLike(12);
        BetterHudPanel.Panel panel = BetterHudPanel.find(folder).orElseThrow();
        assertEquals(new HudDefinition.Spot(0, 0), panel.gui(), "the HUD's legacy x and y");
        assertEquals(new HudDefinition.Spot(0, 0), panel.pixel());
        assertEquals(45, panel.left());
        assertEquals(115, panel.right());
        assertEquals(15, panel.step());
        assertEquals(170, panel.firstRow(), "under the armour and air row");
        assertEquals(185, panel.row(1));
        assertEquals(66, panel.width());
    }

    @Test
    void withoutBothBarsOrAtAnotherSizeThereIsNone() throws IOException {
        assertFalse(BetterHudPanel.find(folder).isPresent(), "an empty folder");
        primeHudLike(9);
        assertFalse(BetterHudPanel.find(folder).isPresent(), "frames 9 tall: not the art this plugin draws");
    }

    /** The frame and fill are PrimeHUD's shape: three pixels cut along the top, two up the sides. */
    @Test
    void theArtHasTheirShape() {
        BufferedImage icon = new BufferedImage(7, 6, BufferedImage.TYPE_INT_ARGB);
        icon.setRGB(3, 0, 0xFF112233);
        icon.setRGB(3, 5, 0xFF112233);
        BufferedImage frame = PanelArt.frame(66, 0xCD3232, icon);
        int outline = 0xFF000000 | PanelArt.outline(0xCD3232);
        String[] rows = {
                "...ooo", ".oooii", ".oiiii", "ooiiii", "oiiiii", "oiiiii"};
        for (int y = 0; y < 6; y++) {
            for (int x = 0; x < 6; x++) {
                int expected = switch (rows[y].charAt(x)) {
                    case 'o' -> outline;
                    case 'i' -> 0x99000000;
                    default -> 0;
                };
                assertEquals(expected, frame.getRGB(x, y), "frame at " + x + "," + y);
                assertEquals(expected, frame.getRGB(65 - x, 11 - y), "mirrored at " + (65 - x) + "," + (11 - y));
            }
        }
        assertEquals(0xFF112233, frame.getRGB(6, 3), "the icon, centred in its box from (3, 3)");
        BufferedImage fill = PanelArt.fill(66, 0xCD3232, icon);
        assertEquals(64, fill.getWidth(), "two narrower than the frame, drawn a pixel in");
        assertEquals(10, fill.getHeight());
        assertEquals(0xFFCD3232, fill.getRGB(3, 0));
        assertEquals(0, fill.getRGB(2, 0), "its corner cut");
        assertEquals(0, fill.getRGB(5, 2), "where the icon is, nothing: it shows through");
        assertEquals(0xFFCD3232, fill.getRGB(63, 4));
        assertEquals(0x0DFFFFFF, PanelArt.blank(66).getRGB(3, 0), "a blink's blank half: the outline, all but invisible");
        BufferedImage shine = PanelArt.shine(66, icon);
        assertEquals(0xFFFFFFFF, shine.getRGB(3, 0));
        assertEquals(0, shine.getRGB(30, 5));
        // PrimeHUD's own edges, near enough: #ff8383 for its red, #60aced for its blue.
        assertClose(0xFF8383, PanelArt.outline(0xCD3232));
        assertClose(0x60ACED, PanelArt.outline(0x1765A8));
    }

    @Test
    void theBarsGoUnderThemAsMoreOfThem() throws IOException {
        primeHudLike(12);
        BetterHudPanel.Panel panel = BetterHudPanel.find(folder).orElseThrow();
        HudRenderer.Glyphs glyphs = new HudRenderer.Glyphs('', 10, '', 10, '', 10);
        HudLayout thirst = layout("thirst", new HudAnchor(HudAnchor.Kind.ABOVE_FOOD, 1, 0, 0),
                new HudDefinition.BetterHud(null, null, null, null, 0x1C9EC0, false, 30), glyphs);
        HudLayout mana = layout("mana", new HudAnchor(HudAnchor.Kind.ABOVE_HEALTH, 1, 0, 0), null, glyphs);
        HudLayout shield = layout("shield", new HudAnchor(HudAnchor.Kind.ABOVE_HEALTH, 2, 0, 0),
                new HudDefinition.BetterHud(null, null, null, null, null, true, 0), glyphs);
        HudLayout placed = layout("placed", null,
                new HudDefinition.BetterHud(null, new HudDefinition.Spot(0, 0), new HudDefinition.Spot(60, 120), null, null,
                        false, 0), glyphs);
        BufferedImage icon = new BufferedImage(9, 9, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < 9; i++) {
            icon.setRGB(i, i, 0xFF8A3FD6);
        }
        Map<String, byte[]> files = BetterHudExport.files(List.of(
                new BetterHudExport.Bar(thirst, "thirst", icon, null, null),
                new BetterHudExport.Bar(mana, "mana", icon, null, null),
                new BetterHudExport.Bar(shield, "shield", icon, null, null),
                new BetterHudExport.Bar(placed, "placed", icon, null, null)), true, panel);

        assertTrue(files.containsKey("assets/arkcontent/demo_thirst_frame.png"));
        assertTrue(files.containsKey("assets/arkcontent/demo_thirst_fill.png"));
        assertTrue(files.containsKey("assets/arkcontent/demo_thirst_shine.png"), "it warns");
        assertTrue(files.containsKey("assets/arkcontent/blank.png"));
        assertFalse(files.containsKey("assets/arkcontent/demo_mana_shine.png"));
        assertTrue(files.containsKey("assets/arkcontent/demo_placed_bar.png"), "a place of its own: icons, as 3.1");
        BufferedImage fill = ImageIO.read(new ByteArrayInputStream(files.get("assets/arkcontent/demo_thirst_fill.png")));
        assertEquals(0xFF1C9EC0, fill.getRGB(40, 4), "its own colour");

        String layouts = new String(files.get("layouts/arkcontent.yml"), StandardCharsets.UTF_8);
        // The food side's bar under the hunger bar, the others under the health bar, in anchor order.
        assertTrue(layouts.contains("""
                arkcontent_demo_thirst:
                  images:
                    1:
                      name: arkcontent_demo_thirst_frame
                      x: 115
                      y: 170
                """), layouts);
        assertTrue(layouts.contains("      name: arkcontent_demo_mana_frame\n      x: 45\n      y: 170\n"), layouts);
        assertTrue(layouts.contains("      name: arkcontent_demo_mana\n      x: 46\n      y: 171\n"), "the fill a pixel in: " + layouts);
        assertTrue(layouts.contains("      name: arkcontent_demo_shield_frame\n      x: 45\n      y: 185\n"
                + "      conditions:\n        1:\n          first: \"(number)papi:arkcontent_hud_shield_value\"\n"
                + "          second: \"'(number)0'\"\n          operation: '>'\n"), "hidden while empty: " + layouts);
        assertTrue(layouts.contains("      name: arkcontent_demo_thirst_shine\n      x: 115\n      y: 170\n"
                + "      conditions:\n        1:\n          first: \"(number)papi:arkcontent_hud_thirst_percent\"\n"
                + "          second: \"'(number)30'\"\n          operation: '<='\n"), "blinks at 30%: " + layouts);

        String images = new String(files.get("images/arkcontent.yml"), StandardCharsets.UTF_8);
        assertTrue(images.contains("""
                arkcontent_demo_thirst:
                  type: listener
                  file: "arkcontent/demo_thirst_fill.png"
                  split: 32
                  split-type: left
                """), images);
        assertTrue(images.contains("""
                arkcontent_demo_thirst_shine:
                  type: sequence
                  files:
                    - "arkcontent/demo_thirst_shine.png:10"
                    - "arkcontent/blank.png:10"
                """), images);

        String huds = new String(files.get("huds/arkcontent.yml"), StandardCharsets.UTF_8);
        assertTrue(huds.contains("""
                arkcontent_demo_mana:
                  default: true
                  layouts:
                    1:
                      name: arkcontent_demo_mana
                      gui:
                        x: 0
                        y: 0
                      pixel:
                        x: 0
                        y: 0
                """), "placed as PrimeHUD's own, so its legacy offset moves both alike: " + huds);
        assertTrue(huds.contains("      pixel:\n        x: 60\n        y: 120\n"), huds);
    }

    private static void assertClose(int expected, int actual) {
        for (int shift : new int[]{16, 8, 0}) {
            int difference = Math.abs(((expected >> shift) & 0xFF) - ((actual >> shift) & 0xFF));
            assertTrue(difference <= 10, String.format("%06x is not near %06x", actual, expected));
        }
    }

    private static HudLayout layout(String id, HudAnchor anchor, HudDefinition.BetterHud betterHud,
                                    HudRenderer.Glyphs glyphs) {
        HudDefinition definition = new HudDefinition("demo", id, ResourceLocation.parse("demo:hud/full", "demo"), null,
                null, 9, 0, 10, -2, 0, new HudDefinition.Source.Stored(20, 20, 0, 0, Map.of()), true, Path.of("."),
                Path.of("huds.yml"), anchor, betterHud);
        return new HudLayout(definition, glyphs);
    }
}
