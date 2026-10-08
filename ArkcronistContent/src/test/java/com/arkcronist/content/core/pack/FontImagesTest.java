package com.arkcronist.content.core.pack;

import com.arkcronist.content.core.definition.FontImageDefinition;
import com.arkcronist.content.core.hud.Spaces;
import com.arkcronist.content.core.importer.ImportReport;
import com.arkcronist.content.core.importer.ItemsAdderImporter;
import com.arkcronist.content.core.loader.ContentLoader;
import com.arkcronist.content.core.loader.LoadReport;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ItemsAdder's font images found by name - with a server's own configs (spectra_aurelium_skills'
 * AuraSkills menus, ginko_fantasy_shop's shop) and the providers its generated pack really has.
 */
class FontImagesTest {

    @TempDir
    Path temp;

    /** The providers of ItemsAdder's generated pack for these two packs, as it wrote them. */
    private static List<FontImages.Font> generatedPack() {
        JsonArray providers = new JsonArray();
        Object[][] rows = {
            {"spectra_aurelium_skills:skills_menu_book.png", 0xeb10, 48, 256},
            {"spectra_aurelium_skills:abilities_menu.png", 0xeb11, 48, 256},
            {"spectra_aurelium_skills:stats_menu.png", 0xeb13, 48, 256},
            {"spectra_aurelium_skills:stat_hearts.png", 0xeb20, 9, 11},
            {"spectra_aurelium_skills:book_all_squares.png", 0xeb15, 48, 256},
            {"spectra_aurelium_skills:xp_bar_white.png", 0xeb29, 6, 7},
            {"ginko_fantasy_shop:gui/shopmenu.png", 0xea51, 47, 256},
            {"ginko_fantasy_shop:gui/buying-1.png", 0xea55, 47, 256},
            {"hmccosmetics:icons/shade.png", 0xe03b, -5, 16},
            {"hmccosmetics:icons/shade.png", 0xe03c, -23, 16},
            {"scenes:ui/scenes_spawner_small.png", 0xa412, 41, 256},
        };
        for (Object[] row : rows) {
            providers.add(provider((String) row[0], (int) row[1], (int) row[2], (int) row[3]));
        }
        JsonObject font = new JsonObject();
        font.add("providers", providers);
        // ItemsAdder copies every image into minecraft:uniform as well.
        JsonObject uniform = font.deepCopy();
        return List.of(new FontImages.Font("minecraft:default", font), new FontImages.Font("minecraft:uniform", uniform));
    }

    private static JsonObject provider(String file, int character, int ascent, int height) {
        JsonObject provider = new JsonObject();
        provider.addProperty("type", "bitmap");
        provider.addProperty("file", file);
        provider.addProperty("ascent", ascent);
        provider.addProperty("height", height);
        JsonArray chars = new JsonArray();
        chars.add(new String(Character.toChars(character)));
        provider.add("chars", chars);
        return provider;
    }

    @Test
    void theServersOwnConfigsAreImportedLoadedAndFoundOnTheGeneratedPacksCharacters() throws IOException {
        Path spectra = temp.resolve("import/spectra_aurelium_skills");
        write(spectra, "configs/spectra_aurelium_skills.yml", """
                info:
                  namespace: spectra_aurelium_skills
                font_images:
                  skills_menu_book:
                    show_in_gui: false
                    suggest_in_command: false
                    path: skills_menu_book.png
                    y_position: 48
                    scale_ratio: 256
                  stats_menu: {show_in_gui: false, path: stats_menu.png, y_position: 48, scale_ratio: 256}
                  stat_hearts: {show_in_gui: false, path: stat_hearts.png, y_position: 9, scale_ratio: 11}
                  skill_book_sources: {path: book_all_squares.png, y_position: 48, scale_ratio: 256}
                  xp_bar_white_lore: {path: xp_bar_white, scale_ratio: 7, y_position: 6}
                """);
        for (String picture : List.of("skills_menu_book", "stats_menu", "stat_hearts", "book_all_squares", "xp_bar_white")) {
            write(spectra, "resourcepack/assets/spectra_aurelium_skills/textures/" + picture + ".png", png(picture));
        }
        // A pack of only font images - the configs of most menu packs - with fixed symbols.
        Path ginko = temp.resolve("import/ginko_fantasy_shop");
        write(ginko, "configs/skin.yml", """
                info:
                  namespace: ginko_fantasy_shop
                font_images:
                  fantasy_shop_main: {path: gui/shopmenu, y_position: 47, symbol: "\\uea51"}
                  fantasy_shop_buying_one: {path: gui/buying-1, y_position: 47, symbol: "\\uea55"}
                """);
        write(ginko, "resourcepack/assets/ginko_fantasy_shop/textures/gui/shopmenu.png", png("shopmenu"));
        write(ginko, "resourcepack/assets/ginko_fantasy_shop/textures/gui/buying-1.png", png("buying-1"));

        ImportReport report = new ItemsAdderImporter().run(temp.resolve("import"), temp.resolve("contents"));
        assertEquals(List.of(), report.problems());
        assertTrue(report.notes().stream().anyMatch(note -> note.startsWith("7 font image(s) imported")), report.notes()
                .toString());
        assertTrue(report.notes().stream().noneMatch(note -> note.contains("not imported - font_images")));
        assertTrue(Files.isRegularFile(temp.resolve("contents/ginko_fantasy_shop/textures/gui/shopmenu.png")));

        LoadReport loaded = new ContentLoader().load(temp.resolve("contents"));
        assertEquals(List.of(), loaded.problems());
        Map<String, FontImageDefinition> definitions = new LinkedHashMap<>();
        loaded.fontImages().forEach(definition -> definitions.put(definition.fullId(), definition));
        assertEquals(7, definitions.size());
        FontImageDefinition main = definitions.get("ginko_fantasy_shop:fantasy_shop_main");
        assertEquals(0xea51, main.symbol());
        assertEquals(47, main.ascent());
        assertEquals(null, main.height(), "not given: any height a pack draws it at");

        List<FontImages.Image> provided = FontImages.provided(generatedPack());
        List<FontImages.Image> defined = new ArrayList<>();
        for (FontImageDefinition definition : loaded.fontImages()) {
            defined.add(FontImages.match(definition, provided).orElseThrow(() -> new AssertionError(definition)));
        }
        FontImages images = FontImages.of(defined, provided);

        assertEquals("", glyph(images, "skills_menu_book"));
        assertEquals("", glyph(images, "skill_book_sources"), "named otherwise than its picture");
        assertEquals("", glyph(images, "xp_bar_white_lore"), "a path written without .png");
        assertEquals("", glyph(images, "spectra_aurelium_skills:stat_hearts"));
        assertEquals("", glyph(images, "fantasy_shop_main"));
        assertEquals("", glyph(images, "fantasy_shop_buying_one"));
        assertEquals(FontImages.DEFAULT_FONT, images.find("skills_menu_book").orElseThrow().font(),
                "the default font's copy, not uniform's");
        // Pictures of packs whose configs were not imported: by their file's name.
        assertEquals("ꐒ", glyph(images, "scenes_spawner_small"));
        assertEquals("", glyph(images, "abilities_menu"));
        assertEquals("", glyph(images, "shopmenu"));
        // One picture on two characters: which one a name means is not known.
        assertFalse(images.find("shade").isPresent());
        assertTrue(images.ambiguous().contains("hmccosmetics:shade"));
        assertEquals(7, images.defined());
    }

    @Test
    void namesAreReadStraightFromItemsAddersFolderWhenItsConfigsWereNotImported() throws IOException {
        Path itemsAdder = temp.resolve("plugins/ItemsAdder");
        // ItemsAdder 4: contents/<pack>/configs/, as the server's spectra_aurelium_skills is.
        write(itemsAdder, "contents/spectra_aurelium_skills/configs/spectra_aurelium_skills.yml", """
                info:
                  namespace: spectra_aurelium_skills
                font_images:
                  xp_bar_white_lore: {path: xp_bar_white, scale_ratio: 7, y_position: 6}
                  skill_book_sources: {path: book_all_squares.png, y_position: 48, scale_ratio: 256}
                  xp_bar_white_lore: {path: xp_bar_white, scale_ratio: 7, y_position: 6, show_in_gui: false}
                  not_drawn: {path: nowhere, y_position: 1}
                """);
        // ItemsAdder 3: data/items_packs/<pack>/.
        write(itemsAdder, "data/items_packs/ginko_fantasy_shop/skin.yml", """
                info: {namespace: ginko_fantasy_shop}
                font_images:
                  fantasy_shop_main: {path: gui/shopmenu, y_position: 47, symbol: "\\uea51"}
                """);
        write(itemsAdder, "contents/broken/configs/broken.yml", "font_images: [unclosed");
        write(itemsAdder, "contents/no_info/configs/a.yml", "font_images: {x: {path: x}}");
        write(itemsAdder, "contents/spectra_aurelium_skills/configs/items.yml", "info: {namespace: other}\nitems: {}");

        List<FontImageDefinition> definitions = new ContentLoader().itemsAdderFontImages(itemsAdder);
        assertEquals(List.of("ginko_fantasy_shop:fantasy_shop_main", "spectra_aurelium_skills:not_drawn",
                        "spectra_aurelium_skills:skill_book_sources", "spectra_aurelium_skills:xp_bar_white_lore"),
                definitions.stream().map(FontImageDefinition::fullId).sorted().toList());

        List<FontImages.Image> provided = FontImages.provided(generatedPack());
        List<FontImages.Image> defined = definitions.stream()
                .flatMap(definition -> FontImages.match(definition, provided).stream()).toList();
        assertEquals(3, defined.size(), "the one no pack draws is left out");
        FontImages images = FontImages.of(defined, provided);
        // The lore bar of AuraSkills' Levels menu, as its menus write it.
        assertEquals("<#ffd556>" + Spaces.of(-2) + "\uEB29 ",
                GlyphText.replace("<#ffd556>%img_offset_-2%%img_xp_bar_white_lore% ", images));
        assertEquals("\uEA51", glyph(images, "fantasy_shop_main"));
        assertTrue(new ContentLoader().itemsAdderFontImages(temp.resolve("plugins/nothing")).isEmpty());
    }

    @Test
    void ascentAndHeightPickTheRightCharacterOfAPictureDrawnSeveralTimes() {
        List<FontImages.Image> provided = FontImages.provided(generatedPack());
        FontImageDefinition shadeLow = definition("hmccosmetics", "shade_2", "hmccosmetics:icons/shade", -23, null, null);
        assertEquals("", FontImages.match(shadeLow, provided).orElseThrow().glyph());
        FontImageDefinition wrongHeight = definition("spectra_aurelium_skills", "x", "skills_menu_book", 48, 128, null);
        assertTrue(FontImages.match(wrongHeight, provided).isEmpty(), "drawn at 256, not 128: this plugin draws it");
        FontImageDefinition nowhere = definition("mine", "logo", "mine:gui/logo", null, null, 0xe999);
        assertTrue(FontImages.match(nowhere, provided).isEmpty());
    }

    @Test
    void theHeightOfAPictureIsReadFromItsHeader() throws IOException {
        byte[] header = new byte[24];
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R'};
        System.arraycopy(signature, 0, header, 0, signature.length);
        header[19] = (byte) 176;
        header[22] = 1;
        header[23] = 0;
        Path png = temp.resolve("menu.png");
        Files.write(png, header);
        assertEquals(256, FontImages.pngHeight(png));
        Files.writeString(png, "not a picture");
        assertEquals(null, FontImages.pngHeight(png));
        assertEquals(null, FontImages.pngHeight(temp.resolve("missing.png")));
    }

    @Test
    void textWrittenForItemsAdderGetsItsCharacters() {
        FontImages images = FontImages.of(List.of(), FontImages.provided(generatedPack()));
        // AuraSkills' skills menu title and its lore bar, as the server's menus write them.
        assertEquals("<white>" + Spaces.of(-20) + " ",
                GlyphText.replace("<white>:offset_-20: %img_skills_menu_book%", images));
        assertEquals("<#ffd556>" + Spaces.of(-2) + " ",
                GlyphText.replace("<#ffd556>%img_offset_-2%%img_xp_bar_white% ", images), "bare: the colour tints it");
        assertEquals(Spaces.of(-44) + "ꐒ", GlyphText.replace(":offset_-44::scenes_spawner_small:", images));
        assertEquals("", GlyphText.replace(":spectra_aurelium_skills:skills_menu_book:", images));
        // What is not a font image stays as written - and does not hide one right after it.
        assertEquals("Shop: Tools: 12:30 ", GlyphText.replace("Shop: Tools: 12:30 :skills_menu_book:", images));
        assertEquals("12:30", GlyphText.replace("12:30:skills_menu_book:", images));
        assertEquals(":nothing: %img_nothing%", GlyphText.replace(":nothing: %img_nothing%", images));
        assertEquals(":offset_99999:", GlyphText.replace(":offset_99999:", images), "a typo, not a move");
        assertEquals("plain text", GlyphText.replace("plain text", images));
    }

    private static String glyph(FontImages images, String name) {
        return images.find(name).orElseThrow(() -> new AssertionError("no font image " + name)).glyph();
    }

    private static FontImageDefinition definition(String namespace, String name, String texture, Integer ascent,
                                                  Integer height, Integer symbol) {
        return new FontImageDefinition(namespace, name,
                com.arkcronist.content.core.definition.ResourceLocation.parse(texture, namespace), ascent, height, symbol,
                Path.of("contents", namespace), Path.of("contents", namespace, "a.yml"));
    }

    /** A PNG signature and a marker. */
    private static byte[] png(String marker) {
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        byte[] text = marker.getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[signature.length + text.length];
        System.arraycopy(signature, 0, bytes, 0, signature.length);
        System.arraycopy(text, 0, bytes, signature.length, text.length);
        return bytes;
    }

    private static void write(Path root, String relative, String text) throws IOException {
        write(root, relative, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void write(Path root, String relative, byte[] bytes) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
    }
}
