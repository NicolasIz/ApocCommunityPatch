package com.arkcronist.content.core.pack;

import com.arkcronist.content.core.definition.ItemBehaviour;
import com.arkcronist.content.core.definition.ItemDefinition;
import com.arkcronist.content.core.definition.ModelSource;
import com.arkcronist.content.core.definition.ResourceLocation;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.arkcronist.content.core.pack.PackCompilerTest.SETTINGS;
import static com.arkcronist.content.core.pack.PackCompilerTest.item;
import static com.arkcronist.content.core.pack.PackCompilerTest.png;
import static com.arkcronist.content.core.pack.PackCompilerTest.provided;
import static com.arkcronist.content.core.pack.PackCompilerTest.write;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Files that go into the pack exactly as they are: merged packs' overlays, item definition files,
 * content packs' sounds.
 */
class PackOverlayTest {

    @TempDir
    Path temp;

    /** A pack shaped like ItemsAdder's output: numbers for 1.21.6+ in an overlay, armour for 1.21.4+ in another. */
    private Path generatedPack() throws IOException {
        Path pack = temp.resolve("packs/generated");
        write(pack, "pack.mcmeta", """
                {"pack":{"pack_format":64,"description":"ItemsAdder"},"overlays":{"entries":[
                  {"directory":"ia_overlay_1_21_4_plus","formats":[46,64],"min_format":46,"max_format":9999},
                  {"directory":"ia_overlay_1_21_6_plus","formats":[63,64],"min_format":63,"max_format":9999}]}}
                """);
        write(pack, "assets/holy_knight/models/rapier.json",
                "{\"textures\":{\"0\":\"holy_knight:item/rapier\"},\"elements\":[{\"from\":[7.5,0,7.5],\"to\":[8.5,16,8.5],"
                        + "\"faces\":{\"north\":{\"uv\":[0.25,0,0.5,16],\"texture\":\"#0\"}}}]}");
        write(pack, "assets/holy_knight/textures/item/rapier.png", png("32x32 rapier"));
        write(pack, "assets/holy_knight/textures/item/rapier.png.mcmeta", "{\"animation\":{\"frametime\":2}}");
        write(pack, "assets/holy_knight/sounds.json",
                "{\"swing\":{\"sounds\":[{\"name\":\"holy_knight:swing\",\"stream\":false}]}}");
        write(pack, "assets/holy_knight/sounds/swing.ogg", ogg("48 kHz stereo"));
        write(pack, "ia_overlay_1_21_4_plus/assets/holy_knight/equipment/knight.json", "{\"layers\":{}}");
        // ItemsAdder spells the type and property without minecraft: - the same to the client.
        write(pack, "ia_overlay_1_21_6_plus/assets/minecraft/items/paper.json", """
                {"oversized_in_gui":true,"model":{"type":"range_dispatch","property":"custom_model_data",
                 "index":0,"entries":[
                  {"threshold":10000,"model":{"type":"minecraft:model","model":"holy_knight:rapier"}},
                  {"threshold":10001,"model":{"type":"minecraft:model","model":"holy_knight:shield"}}],
                 "fallback":{"type":"minecraft:model","model":"minecraft:item/paper"}}}
                """);
        write(pack, "not_an_overlay/assets/minecraft/evil.json", "{}");
        // Its own negative spaces, on the characters ours use, and an icon.
        write(pack, "assets/minecraft/font/default.json", """
                {"providers":[{"file":"char/black.png","chars":["\\uf801"],"height":-3,"ascent":-5000,"type":"bitmap"},
                 {"file":"char/black.png","chars":["\\uf822"],"height":1,"ascent":-5000,"type":"bitmap"},
                 {"file":"holy_knight:icons/crest.png","chars":["\\ue000"],"height":9,"ascent":8,"type":"bitmap"}]}
                """);
        write(pack, "ia_overlay_1_21_6_plus/assets/minecraft/font/default.json", """
                {"providers":[{"file":"char/black.png","chars":["\\uf801"],"height":-3,"ascent":-5000,"type":"bitmap"}]}
                """);
        return pack;
    }

    @Test
    void aMergedPacksOverlaysComeWithItAndAreDeclaredInOurPackMcmeta() throws IOException {
        Path generated = generatedPack();
        Path out = temp.resolve("pack");

        PackCompiler.Result result = new PackCompiler(SETTINGS).compile(out, List.of(), Map.of(),
                List.of(new ExternalPack("packs/generated", generated)));

        assertEquals(List.of(), result.problems());
        for (String file : List.of("assets/holy_knight/models/rapier.json", "assets/holy_knight/textures/item/rapier.png",
                "assets/holy_knight/textures/item/rapier.png.mcmeta", "assets/holy_knight/sounds.json",
                "assets/holy_knight/sounds/swing.ogg", "ia_overlay_1_21_4_plus/assets/holy_knight/equipment/knight.json",
                "ia_overlay_1_21_6_plus/assets/minecraft/items/paper.json")) {
            assertArrayEquals(Files.readAllBytes(generated.resolve(file)), Files.readAllBytes(out.resolve(file)), file);
        }
        assertFalse(Files.exists(out.resolve("not_an_overlay")), "a folder no overlay entry names is no client's");

        JsonObject mcmeta = JsonParser.parseString(Files.readString(out.resolve("pack.mcmeta"))).getAsJsonObject();
        assertEquals(46, mcmeta.getAsJsonObject("pack").get("pack_format").getAsInt(), "our pack.mcmeta");
        JsonArray entries = mcmeta.getAsJsonObject("overlays").getAsJsonArray("entries");
        assertEquals(2, entries.size());
        assertEquals("ia_overlay_1_21_4_plus", entries.get(0).getAsJsonObject().get("directory").getAsString());
        assertEquals(JsonParser.parseString("[63,64]"), entries.get(1).getAsJsonObject().get("formats"),
                "each entry as its pack wrote it");
    }

    @Test
    void aZippedPackSplitUnderAFolderKeepsItsOverlaysToo() throws IOException {
        Path zip = temp.resolve("generated.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            entry(out, "generated/pack.mcmeta", "{\"pack\":{},\"overlays\":{\"entries\":[{\"directory\":\"over\",\"formats\":[63,64]}]}}");
            entry(out, "generated/over/assets/demo/equipment/a.json", "{}");
            entry(out, "generated/over/../../escape.json", "{}");
        }
        Path out = temp.resolve("pack");
        PackCompiler.Result result = new PackCompiler(SETTINGS).compile(out, List.of(), Map.of(),
                List.of(new ExternalPack("generated.zip", zip)));

        assertEquals(List.of(), result.problems());
        assertTrue(Files.isRegularFile(out.resolve("over/assets/demo/equipment/a.json")));
        assertFalse(Files.exists(temp.resolve("escape.json")));
    }

    @Test
    void ourNumbersAreJoinedIntoAnOverlayThatWouldHideThem() throws IOException {
        Path generated = generatedPack();
        Path demo = temp.resolve("contents/demo");
        write(demo, "models/item/ruby.json", "{\"parent\":\"minecraft:item/generated\"}");
        Path out = temp.resolve("pack");

        PackCompiler.Result result = new PackCompiler(SETTINGS).compile(out,
                List.of(item("ruby", provided(demo, "demo:item/ruby"))), Map.of(), Map.of(), List.of(),
                Map.of("demo:ruby", 10002), List.of(new ExternalPack("packs/generated", generated)));

        assertEquals(List.of(), result.problems());
        JsonObject overlay = JsonParser.parseString(Files.readString(
                out.resolve("ia_overlay_1_21_6_plus/assets/minecraft/items/paper.json"))).getAsJsonObject();
        assertTrue(overlay.get("oversized_in_gui").getAsBoolean(), "the overlay's own settings stay");
        List<String> entries = new ArrayList<>();
        overlay.getAsJsonObject("model").getAsJsonArray("entries").forEach(entry -> entries.add(
                entry.getAsJsonObject().get("threshold").getAsInt() + "="
                        + entry.getAsJsonObject().getAsJsonObject("model").get("model").getAsString()));
        // Theirs, ours, and the step back to vanilla after ours.
        assertEquals(List.of("10000=holy_knight:rapier", "10001=holy_knight:shield", "10002=demo:item/ruby",
                "10003=minecraft:item/paper"), entries);
    }

    @Test
    void ourSpaceCharactersGiveWayToTheMergedPacksGlyphsAndOurFontReachesItsOverlay() throws IOException {
        Path generated = generatedPack();
        Path out = temp.resolve("pack");

        PackCompiler.Result result = new PackCompiler(SETTINGS).compile(out, new PackCompiler.Input(List.of(), Map.of(),
                Map.of(), List.of(), Map.of(), Map.of(), List.of(), true,
                List.of(new ExternalPack("packs/generated", generated)), null));

        JsonObject font = JsonParser.parseString(Files.readString(out.resolve("assets/minecraft/font/default.json")))
                .getAsJsonObject();
        JsonObject spaces = font.getAsJsonArray("providers").get(0).getAsJsonObject();
        assertEquals("space", spaces.get("type").getAsString());
        assertFalse(spaces.getAsJsonObject("advances").has("\uf801"), "theirs draws U+F801");
        assertFalse(spaces.getAsJsonObject("advances").has("\uf822"));
        assertEquals(-2, spaces.getAsJsonObject("advances").get("\uf802").getAsInt(), "the rest stay ours");
        assertTrue(font.toString().contains("holy_knight:icons/crest.png"));
        assertEquals(List.of("packs/generated pack: assets/minecraft/font/default.json: 2 space character(s) U+F801,"
                + " U+F822 are drawn by the other pack too - its glyphs are kept, as its menus were laid out with"
                + " them; HUDs here move by that pack's widths for these",
                "packs/generated pack: ia_overlay_1_21_6_plus/assets/minecraft/font/default.json: 1 character(s)"
                        + " U+F801 are drawn by both packs - keeping the pack's own; free them in one of the two"),
                result.problems());
        // The 1.21.6+ copy of the font would hide ours from those clients: our spaces are joined into it,
        // after its own glyphs, which keep drawing their characters.
        JsonArray overlay = JsonParser.parseString(Files.readString(
                out.resolve("ia_overlay_1_21_6_plus/assets/minecraft/font/default.json"))).getAsJsonObject()
                .getAsJsonArray("providers");
        assertEquals("char/black.png", overlay.get(0).getAsJsonObject().get("file").getAsString());
        assertEquals("space", overlay.get(1).getAsJsonObject().get("type").getAsString());

        try (PackSource source = PackSource.open(new ExternalPack("generated", generated))) {
            assertEquals(Set.of(0xF801, 0xF822, 0xE000), FontCharacters.usedBy(source));
        }
    }

    @Test
    void onANumberBothUseTheOverlayKeepsItsOwnEntry() throws IOException {
        Path generated = generatedPack();
        Path demo = temp.resolve("contents/demo");
        write(demo, "models/item/ruby.json", "{\"parent\":\"minecraft:item/generated\"}");
        Path out = temp.resolve("pack");

        // demo:ruby was given 10001 before the pack came, and the pack draws its shield with that number.
        PackCompiler.Result result = new PackCompiler(SETTINGS).compile(out,
                List.of(item("ruby", provided(demo, "demo:item/ruby"))), Map.of(), Map.of(), List.of(),
                Map.of("demo:ruby", 10001), List.of(new ExternalPack("packs/generated", generated)));

        JsonArray entries = JsonParser.parseString(Files.readString(
                out.resolve("ia_overlay_1_21_6_plus/assets/minecraft/items/paper.json"))).getAsJsonObject()
                .getAsJsonObject("model").getAsJsonArray("entries");
        assertEquals("holy_knight:shield", entries.get(1).getAsJsonObject().getAsJsonObject("model").get("model")
                .getAsString());
        assertEquals(List.of("packs/generated pack: ia_overlay_1_21_6_plus/assets/minecraft/items/paper.json: custom_model_data"
                + " 10001 is drawn by both packs - keeping the pack's own; give one of them another number"),
                result.problems());
    }

    @Test
    void anItemDefinitionFileIsUsedAsItIsFromTheContentPackOrAMergedPack() throws IOException {
        Path generated = generatedPack();
        write(generated, "assets/holy_knight/items/shield.json",
                "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"holy_knight:shield\"}}");
        Path knight = temp.resolve("contents/holy_knight");
        String bow = "{\n  \"oversized_in_gui\": true,\n  \"model\": {\"type\": \"minecraft:condition\","
                + " \"property\": \"minecraft:using_item\",\n    \"on_false\": {\"type\": \"model\", \"model\":"
                + " \"holy_knight:bow\"},\n    \"on_true\": {\"type\": \"model\", \"model\": \"holy_knight:bow_pull\"}}\n}\n";
        write(knight, "items/bow.json", bow);
        write(knight, "models/bow.json", "{\"textures\":{\"0\":\"holy_knight:item/bow\"}}");
        write(knight, "textures/item/bow.png", png("bow"));
        Path out = temp.resolve("pack");

        PackCompiler.Result result = new PackCompiler(SETTINGS).compile(out, List.of(
                        definition(knight, "bow", "BOW"),
                        definition(knight, "shield", "PAPER"),
                        definition(knight, "missing", "PAPER")),
                Map.of(), Map.of(), List.of(), Map.of("holy_knight:shield", 10005),
                List.of(new ExternalPack("packs/generated", generated)));

        // Byte for byte, the condition and oversized_in_gui as written.
        assertEquals(bow, Files.readString(out.resolve("assets/holy_knight/items/bow.json")));
        assertTrue(Files.isRegularFile(out.resolve("assets/holy_knight/models/bow.json")), "its model follows it");
        assertTrue(Files.isRegularFile(out.resolve("assets/holy_knight/textures/item/bow.png")));
        // The bow_pull model is in neither: that is reported, as is the definition nobody has.
        assertEquals(2, result.problems().size(), result.problems().toString());
        assertTrue(result.problems().get(0).startsWith("holy_knight:bow: model holy_knight:bow_pull not found"));
        assertTrue(result.problems().get(1).startsWith("holy_knight:missing: item definition holy_knight:missing"
                + " not found"), result.problems().get(1));
        // The merged pack's own definition is the shield's, and its number draws what it draws.
        assertTrue(Files.isRegularFile(out.resolve("assets/holy_knight/items/shield.json")));
        assertTrue(Files.readString(out.resolve("assets/minecraft/items/paper.json")).contains("holy_knight:shield"));
        assertEquals(new ResourceLocation("holy_knight", "bow"),
                definition(knight, "bow", "BOW").itemModel());
    }

    @Test
    void aModelOrTextureAMergedPackSuppliesIsNotReportedMissing() throws IOException {
        Path generated = generatedPack();
        Path knight = temp.resolve("contents/holy_knight");
        Files.createDirectories(knight);
        Path out = temp.resolve("pack");

        PackCompiler.Result result = new PackCompiler(SETTINGS).compile(out,
                List.of(new ItemDefinition("holy_knight", "rapier", "NETHERITE_SWORD", null, List.of(),
                        provided(knight, "holy_knight:rapier"), ItemBehaviour.DEFAULT, null, Path.of("items.yml"))),
                Map.of(), List.of(new ExternalPack("packs/generated", generated)));

        assertEquals(List.of(), result.problems());
        assertArrayEquals(Files.readAllBytes(generated.resolve("assets/holy_knight/models/rapier.json")),
                Files.readAllBytes(out.resolve("assets/holy_knight/models/rapier.json")));
    }

    @Test
    void aContentPacksSoundsGoInAsTheyAreAndJoinAMergedPacksSoundsJson() throws IOException {
        Path generated = generatedPack();
        Path contents = temp.resolve("contents");
        String index = "{\n  \"golem_ancestral.invocar\": {\"subtitle\": \"Golem\", \"sounds\": [\n"
                + "    {\"name\": \"holy_knight:golem_ancestral/invocar\", \"volume\": 0.8, \"pitch\": 1.0}]}\n}\n";
        write(contents, "holy_knight/sounds.json", index);
        byte[] invocar = ogg("44.1 kHz mono, as recorded");
        write(contents, "holy_knight/sounds/golem_ancestral/invocar.ogg", invocar);
        write(contents, "holy_knight/sounds/readme.txt", "not a sound");
        write(contents, "holy_knight/sounds/broken.ogg", "RIFF....WAVE");
        Path out = temp.resolve("pack");

        PackCompiler.Result result = new PackCompiler(SETTINGS).compile(out, new PackCompiler.Input(List.of(), Map.of(),
                Map.of(), List.of(), Map.of(), Map.of(), List.of(), false,
                List.of(new ExternalPack("packs/generated", generated)), contents));

        assertArrayEquals(invocar, Files.readAllBytes(out.resolve("assets/holy_knight/sounds/golem_ancestral/invocar.ogg")));
        assertFalse(Files.exists(out.resolve("assets/holy_knight/sounds/readme.txt")));
        assertFalse(Files.exists(out.resolve("assets/holy_knight/sounds/broken.ogg")));
        assertEquals(List.of("holy_knight sounds: holy_knight/sounds/broken.ogg is not an Ogg Vorbis file - left out"),
                result.problems());
        assertEquals(2, result.sounds());
        // Both packs' events, ours exactly as written.
        JsonObject sounds = JsonParser.parseString(Files.readString(out.resolve("assets/holy_knight/sounds.json")))
                .getAsJsonObject();
        assertEquals(Set.of("golem_ancestral.invocar", "swing"), sounds.keySet());
        assertEquals(JsonParser.parseString(index).getAsJsonObject().get("golem_ancestral.invocar"),
                sounds.get("golem_ancestral.invocar"));
    }

    @Test
    void aSoundNameWithNoNamespaceIsGivenTheOneItsFileIsIn() throws IOException {
        Path contents = temp.resolve("contents");
        // A pack renamed from old_bosses, whose names still say so.
        String index = "{\"golem.roar\":{\"subtitle\":\"bosses.roar\",\"sounds\":[\"golem/roar\","
                + "{\"name\":\"bosses:golem/step\"},{\"name\":\"golem/roar\",\"volume\":0.4,\"pitch\":1.2,\"stream\":true},"
                + "{\"name\":\"entity.warden.roar\",\"type\":\"event\"},\"ambient/cave/cave1\",\"old_bosses:golem/step\"]}}";
        write(contents, "bosses/sounds.json", index);
        byte[] roar = ogg("roar");
        write(contents, "bosses/sounds/golem/roar.ogg", roar);
        write(contents, "bosses/sounds/golem/step.ogg", ogg("step"));
        Path out = temp.resolve("pack");

        PackCompiler.Result result = new PackCompiler(SETTINGS).compile(out, new PackCompiler.Input(List.of(), Map.of(),
                Map.of(), List.of(), Map.of(), Map.of(), List.of(), false, List.of(), contents));

        // golem/roar is in bosses/sounds/: it gets bosses:, its volume, pitch and streaming as they were;
        // so does the step the old namespace names. The event is an event, the cave sound vanilla's.
        assertEquals(JsonParser.parseString("{\"golem.roar\":{\"subtitle\":\"bosses.roar\",\"sounds\":[\"bosses:golem/roar\","
                + "{\"name\":\"bosses:golem/step\"},{\"name\":\"bosses:golem/roar\",\"volume\":0.4,\"pitch\":1.2,\"stream\":true},"
                + "{\"name\":\"entity.warden.roar\",\"type\":\"event\"},\"ambient/cave/cave1\",\"bosses:golem/step\"]}}"),
                JsonParser.parseString(Files.readString(out.resolve("assets/bosses/sounds.json"))));
        assertArrayEquals(roar, Files.readAllBytes(out.resolve("assets/bosses/sounds/golem/roar.ogg")));
        assertEquals(index, Files.readString(contents.resolve("bosses/sounds.json")), "the source is left as it is");
        assertEquals(List.of(), result.problems());
        assertEquals(List.of("assets/bosses/sounds.json: 2 sound name(s) pointed where their file is not and now name"
                + " 'bosses:', where it is - e.g. 'golem/roar' is now 'bosses:golem/roar' (pack.fix-sound-names)"),
                result.notes());

        // Turned off, the file goes in as it is and the names are reported.
        PackCompiler.Result strict = new PackCompiler(SETTINGS, false).compile(out, new PackCompiler.Input(List.of(),
                Map.of(), Map.of(), List.of(), Map.of(), Map.of(), List.of(), false, List.of(), contents));
        assertEquals(index, Files.readString(out.resolve("assets/bosses/sounds.json")));
        assertEquals(List.of("bosses sounds: assets/bosses/sounds.json: 2 sound name(s) point where their file is not"
                + " - e.g. 'golem/roar', which the client plays from minecraft:sounds/golem/roar.ogg - while the files"
                + " are in bosses/sounds/; those events play nothing. Left as they are: write 'bosses:golem/roar' (and"
                + " the same for the rest) in sounds.json to make them play"), strict.problems());
    }

    @Test
    void theNoteBlockStatesAMergedPackDrawsAsItsOwnBlocksAreKnown() throws IOException {
        Path generated = generatedPack();
        write(generated, "assets/minecraft/blockstates/note_block.json", """
                {"variants":{"instrument=harp":{"model":"block/original/note_block"},
                 "instrument=basedrum,note=0,powered=false":{"model":"fv:item/ia_auto/brick"},
                 "instrument=basedrum,note=3,powered=true":{"model":"fv:item/ia_auto/stone"}}}
                """);
        try (PackSource source = PackSource.open(new ExternalPack("generated", generated))) {
            assertEquals(Set.of(new com.arkcronist.content.core.block.NoteBlockState("basedrum", 0, false).index(),
                            new com.arkcronist.content.core.block.NoteBlockState("basedrum", 3, true).index()),
                    BlockStates.customNoteBlockStates(source));
        }
    }

    @Test
    void anArmourLayerSetAMergedPackHasIsWornAsItIs() throws IOException {
        Path generated = generatedPack();
        Path knight = temp.resolve("contents/holy_knight");
        Files.createDirectories(knight);
        Path out = temp.resolve("pack");
        java.util.function.Function<String, ItemDefinition> piece = asset -> new ItemDefinition("holy_knight",
                asset + "_chest", "NETHERITE_CHESTPLATE", null, List.of(), null, ItemBehaviour.DEFAULT, null,
                Path.of("items.yml")).withEquipment(new com.arkcronist.content.core.definition.Equipment(
                com.arkcronist.content.core.definition.Equipment.Slot.CHEST, new ResourceLocation("holy_knight", asset),
                List.of(), null, knight));

        PackCompiler.Result result = new PackCompiler(SETTINGS).compile(out, List.of(piece.apply("knight"),
                piece.apply("nobody")), Map.of(), List.of(new ExternalPack("packs/generated", generated)));

        // knight is in the pack's 1.21.4+ overlay: nothing to write, nothing missing.
        assertFalse(Files.exists(out.resolve("assets/holy_knight/equipment/knight.json")));
        assertTrue(Files.exists(out.resolve("ia_overlay_1_21_4_plus/assets/holy_knight/equipment/knight.json")));
        assertEquals(List.of("holy_knight:nobody_chest: worn on CHEST, holy_knight:nobody is drawn from"
                + " textures/entity/equipment/humanoid/nobody.png, which is not in holy_knight, and no merged pack has"
                + " assets/holy_knight/equipment/nobody.json - the piece would be invisible"), result.problems());
    }

    @Test
    void overlayEntriesAreReadForTheRightClientVersion() throws IOException {
        Path generated = generatedPack();
        try (PackSource source = PackSource.open(new ExternalPack("generated", generated))) {
            assertEquals(List.of("ia_overlay_1_21_4_plus", "ia_overlay_1_21_6_plus"),
                    List.copyOf(source.overlays().keySet()));
            assertTrue(source.has("ia_overlay_1_21_6_plus/assets/minecraft/items/paper.json"));
            assertFalse(source.has("not_an_overlay/assets/minecraft/evil.json"));
            assertFalse(source.has("pack.mcmeta"), "the pack's own mcmeta is not one of its files");
            assertEquals(Set.of(10000, 10001), ModelDataDispatch.numbersUsedBy(source));
        }
    }

    private static ItemDefinition definition(Path root, String id, String material) {
        return new ItemDefinition("holy_knight", id, material, null, List.of(),
                new ModelSource.Definition(root, new ResourceLocation("holy_knight", id)), ItemBehaviour.DEFAULT, null,
                Path.of("items.yml"));
    }

    static byte[] ogg(String marker) {
        return ("OggS" + marker).getBytes(StandardCharsets.UTF_8);
    }

    private static void entry(ZipOutputStream out, String name, String text) throws IOException {
        out.putNextEntry(new ZipEntry(name));
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }
}
