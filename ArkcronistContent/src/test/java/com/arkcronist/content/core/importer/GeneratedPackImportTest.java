package com.arkcronist.content.core.importer;

import com.arkcronist.content.core.definition.ItemDefinition;
import com.arkcronist.content.core.definition.ModelSource;
import com.arkcronist.content.core.definition.ResourceLocation;
import com.arkcronist.content.core.loader.ContentLoader;
import com.arkcronist.content.core.loader.LoadReport;
import com.arkcronist.content.core.pack.ExternalPack;
import com.arkcronist.content.core.pack.PackCompiler;
import com.arkcronist.content.core.pack.PackSettings;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ItemsAdder's generated resource pack, split in two zips, through import, load and compile. */
class GeneratedPackImportTest {

    private static final PackSettings SETTINGS = new PackSettings(new JsonPrimitive("test"), 64, 46, 99);

    @TempDir
    Path temp;

    private Path importDir() {
        return temp.resolve("import");
    }

    private Path contents() {
        return temp.resolve("contents");
    }

    private Path packs() {
        return temp.resolve("packs");
    }

    /** Every file of the pack, as /iazip would write it; split across two parts below. */
    private static Map<String, byte[]> generatedPack() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        text(files, "pack.mcmeta", """
                {"pack":{"pack_format":64,"description":"ItemsAdder"},"overlays":{"entries":[
                 {"directory":"ia_overlay_1_21_4_to_5","formats":[46,55],"min_format":46,"max_format":55},
                 {"directory":"ia_overlay_1_21_4_plus","formats":[46,64],"min_format":46,"max_format":9999},
                 {"directory":"ia_overlay_1_21_6_plus","formats":[63,64],"min_format":63,"max_format":9999}]}}""");
        // A Blockbench model: HD texture, odd UVs - none of it may change.
        text(files, "assets/dragones_epicos/models/armor/voltharion_yelmo.json", """
                {"credit":"Made with Blockbench","texture_size":[64,64],
                 "textures":{"0":"dragones_epicos:armor/voltharion","particle":"dragones_epicos:armor/voltharion"},
                 "elements":[{"from":[3.75,7.9,3.75],"to":[12.25,16.4,12.25],
                  "faces":{"north":{"uv":[2.125,2.125,4.25,4.25],"texture":"#0"}}}],
                 "display":{"head":{"translation":[0,-6.25,0],"scale":[1.6,1.6,1.6]}}}""");
        files.put("assets/dragones_epicos/textures/armor/voltharion.png", png("64x64 HD"));
        text(files, "assets/medieval_rpg/models/crate_1.json",
                "{\"textures\":{\"1\":\"medieval_rpg:crate\"},\"elements\":[]}");
        files.put("assets/medieval_rpg/textures/crate.png", png("16x16"));
        files.put("assets/medieval_rpg/textures/crate.png.mcmeta",
                "{\"animation\":{\"frametime\":3,\"interpolate\":true}}".getBytes(StandardCharsets.UTF_8));
        text(files, "assets/nazgul/models/greatbow.json", "{\"parent\":\"minecraft:item/bow\"}");
        text(files, "assets/nazgul/models/greatbow_0.json", "{\"parent\":\"minecraft:item/bow\"}");
        // An item definition ItemsAdder wrote itself for an item that also has a number.
        text(files, "assets/darksteel/items/katana.json",
                "{\n \"model\": {\n  \"type\": \"minecraft:model\",\n  \"model\": \"darksteel:item/katana\"\n }\n}");
        text(files, "assets/darksteel/models/item/katana.json", "{\"parent\":\"minecraft:item/handheld\"}");
        text(files, "assets/arkcronist_jefes/sounds.json", """
                {"golem_ancestral.invocar":{"sounds":[{"name":"arkcronist_jefes:golem_ancestral/invocar","stream":true}]}}""");
        files.put("assets/arkcronist_jefes/sounds/golem_ancestral/invocar.ogg",
                "OggS\0\2 vorbis 48000 Hz 2 channels".getBytes(StandardCharsets.UTF_8));
        text(files, "assets/modelengine/models/golem/bone.json", "{}");
        text(files, "ia_overlay_1_21_4_plus/assets/dragones_epicos/equipment/voltharion.json", "{\"layers\":{}}");
        // The 1.21.4-1.21.5 copy names other models; a 1.21.8 client never reads it.
        text(files, "ia_overlay_1_21_4_to_5/assets/minecraft/items/paper.json", dispatch(false,
                "{\"threshold\":10000,\"model\":{\"type\":\"minecraft:model\",\"model\":\"old:wrong\"}}"));
        text(files, "ia_overlay_1_21_6_plus/assets/minecraft/items/paper.json", dispatch(true,
                "{\"threshold\":10000,\"model\":{\"type\":\"model\",\"model\":\"dragones_epicos:armor/voltharion_yelmo\"}},"
                        + "{\"threshold\":10001,\"model\":{\"type\":\"model\",\"model\":\"medieval_rpg:crate_1\","
                        + "\"tints\":[{\"type\":\"dye\",\"default\":-6265536}]}},"
                        + "{\"threshold\":10002,\"model\":{\"type\":\"model\",\"model\":\"modelengine:golem/bone\"}},"
                        + "{\"threshold\":10003,\"model\":{\"type\":\"model\",\"model\":\"item/iron_hoe\"}},"
                        + "{\"threshold\":10004,\"model\":{\"type\":\"model\",\"model\":\"_iainternal:icon\"}}"));
        text(files, "ia_overlay_1_21_6_plus/assets/minecraft/items/bow.json", dispatch(true,
                "{\"threshold\":10000,\"model\":{\"type\":\"minecraft:condition\",\"property\":\"minecraft:using_item\","
                        + "\"on_false\":{\"type\":\"minecraft:model\",\"model\":\"nazgul:greatbow\"},"
                        + "\"on_true\":{\"type\":\"minecraft:range_dispatch\",\"property\":\"minecraft:use_duration\","
                        + "\"scale\":0.05,\"entries\":[{\"threshold\":0.65,\"model\":{\"type\":\"model\",\"model\":\"nazgul:greatbow_0\"}}],"
                        + "\"fallback\":{\"type\":\"minecraft:model\",\"model\":\"nazgul:greatbow_0\"}}}}"));
        text(files, "ia_overlay_1_21_6_plus/assets/minecraft/items/stick.json", dispatch(true,
                "{\"threshold\":8191036,\"model\":{\"type\":\"minecraft:model\",\"model\":\"darksteel:item/katana\"}}"));
        return files;
    }

    private void zipParts(Map<String, byte[]> files) throws IOException {
        Files.createDirectories(importDir());
        List<String> names = List.copyOf(files.keySet());
        try (ZipOutputStream one = new ZipOutputStream(Files.newOutputStream(importDir().resolve("generated_4 - parte 1.zip")));
             ZipOutputStream two = new ZipOutputStream(Files.newOutputStream(importDir().resolve("generated_4 - parte 2.zip")))) {
            for (int i = 0; i < names.size(); i++) {
                // pack.mcmeta in the second part only, as the attached split has it.
                ZipOutputStream out = i == 0 || i % 2 == 1 ? two : one;
                out.putNextEntry(new ZipEntry(names.get(i)));
                out.write(files.get(names.get(i)));
                out.closeEntry();
            }
            two.putNextEntry(new ZipEntry("../../outside.txt"));
            two.write(1);
            two.closeEntry();
        }
    }

    @Test
    void aSplitGeneratedPackIsTakenWholeAndItsNumberedItemsBecomeItemModelItems() throws IOException {
        Map<String, byte[]> files = generatedPack();
        zipParts(files);

        ImportReport report = new ItemsAdderImporter().run(importDir(), contents(), packs());

        assertEquals(List.of(), report.problems());
        assertEquals(1, report.packs());
        assertEquals(files.size(), report.packFiles());
        // Every file, byte for byte, at the same path: models, HD textures, animations, sounds, overlays.
        Path absorbed = packs().resolve("generated_4");
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            assertArrayEquals(file.getValue(), Files.readAllBytes(absorbed.resolve(file.getKey())), file.getKey());
        }
        assertFalse(Files.exists(temp.resolve("outside.txt")));
        assertTrue(Files.readString(absorbed.resolve(GeneratedPacks.MARKER_FILE)).contains("import/generated_4 - parte 1.zip + generated_4 - parte 2.zip"));

        // Helmet and crate from the 1.21.6+ copy, the bow, the katana through the pack's own definition.
        assertEquals(4, report.items());
        assertEquals(List.of("darksteel/imported/generated_4-pack.yml", "dragones_epicos/imported/generated_4-pack.yml",
                "medieval_rpg/imported/generated_4-pack.yml", "nazgul/imported/generated_4-pack.yml"), report.written());
        assertNote(report, "1 custom_model_data entry not made items - bones of ModelEngine models");
        assertNote(report, "1 custom_model_data entry not made items - vanilla models");
        assertNote(report, "1 custom_model_data entry not made items - ItemsAdder's own internal icons");

        // The definition is the pack's entry as it was, tints and all, with oversized_in_gui.
        JsonObject crate = json(contents().resolve("medieval_rpg/items/crate_1.json"));
        assertTrue(crate.get("oversized_in_gui").getAsBoolean());
        assertEquals(JsonParser.parseString("{\"type\":\"model\",\"model\":\"medieval_rpg:crate_1\","
                + "\"tints\":[{\"type\":\"dye\",\"default\":-6265536}]}"), crate.get("model"));
        assertFalse(Files.exists(contents().resolve("darksteel/items/katana.json")), "the pack's own one is used");
        assertFalse(Files.exists(contents().resolve("dragones_epicos/models")), "nothing copied out of the pack");

        LoadReport loaded = new ContentLoader().load(contents());
        assertEquals(List.of(), loaded.problems());
        Map<String, ItemDefinition> items = loaded.items().stream()
                .collect(Collectors.toMap(ItemDefinition::fullId, Function.identity()));
        assertEquals(List.of("darksteel:katana", "dragones_epicos:voltharion_yelmo", "medieval_rpg:crate_1",
                "nazgul:greatbow"), items.keySet().stream().sorted().toList());
        ItemDefinition helmet = items.get("dragones_epicos:voltharion_yelmo");
        assertEquals("PAPER", helmet.material());
        assertEquals("Voltharion Yelmo", helmet.displayName());
        assertTrue(helmet.model() instanceof ModelSource.Definition);
        assertEquals(new ResourceLocation("dragones_epicos", "voltharion_yelmo"), helmet.itemModel());
        assertEquals("BOW", items.get("nazgul:greatbow").material());
        assertEquals("STICK", items.get("darksteel:katana").material());

        Path out = temp.resolve("pack");
        PackCompiler.Result compiled = new PackCompiler(SETTINGS).compile(out, new PackCompiler.Input(loaded.items(),
                Map.of(), Map.of(), List.of(), Map.of(), Map.of(), List.of(), false,
                List.of(new ExternalPack("packs/generated_4", absorbed)), contents()));
        assertEquals(List.of(), compiled.problems());
        // In the pack that reaches players: every file of theirs, byte for byte.
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            if (!file.getKey().equals("pack.mcmeta")) {
                assertArrayEquals(file.getValue(), Files.readAllBytes(out.resolve(file.getKey())), file.getKey());
            }
        }
        JsonObject mcmeta = json(out.resolve("pack.mcmeta"));
        assertEquals(3, mcmeta.getAsJsonObject("overlays").getAsJsonArray("entries").size());
        // The helmet's item_model leads to the model whose textures point at the texture the pack carries.
        JsonObject definition = json(out.resolve("assets/dragones_epicos/items/voltharion_yelmo.json"));
        String model = definition.getAsJsonObject("model").get("model").getAsString();
        JsonObject helmetModel = json(out.resolve(ResourceLocation.parse(model, "minecraft").assetPath("models", ".json")));
        String texture = helmetModel.getAsJsonObject("textures").get("0").getAsString();
        assertTrue(Files.isRegularFile(out.resolve(ResourceLocation.parse(texture, "minecraft")
                .assetPath("textures", ".png"))), texture);
    }

    @Test
    void importingAgainGivesTheSameFilesAndAFolderNotOursIsLeftAlone() throws IOException {
        zipParts(generatedPack());
        new ItemsAdderImporter().run(importDir(), contents(), packs());
        Map<String, String> first = snapshot(temp);

        ImportReport again = new ItemsAdderImporter().run(importDir(), contents(), packs());
        assertEquals(List.of(), again.problems());
        assertEquals(first, snapshot(temp));

        Files.delete(packs().resolve("generated_4").resolve(GeneratedPacks.MARKER_FILE));
        ImportReport third = new ItemsAdderImporter().run(importDir(), contents(), packs());
        assertTrue(third.problems().get(0).endsWith("packs/generated_4 already exists and was not written by the"
                + " importer - left alone; move it away and import again to replace it"), third.problems().toString());
    }

    @Test
    void aConfigsItemWinsOverTheSameItemFromThePackAndKeepsItsName() throws IOException {
        zipParts(generatedPack());
        write(importDir(), "contents/medieval_rpg/configs/props.yml", """
                info:
                  namespace: medieval_rpg
                items:
                  crate:
                    name: "&6Old Crate"
                    lore: ["&7Smells of hay"]
                    resource:
                      material: PAPER
                      model_id: 10001
                      model_path: crate_1
                """);

        ImportReport report = new ItemsAdderImporter().run(importDir(), contents(), packs());

        assertEquals(List.of(), report.problems());
        assertNote(report, "custom_model_data 10001 (resource.model_id) is not carried over - the item is drawn by"
                + " item_model medieval_rpg:crate");
        assertNote(report, "1 custom_model_data entry not made items - items an ItemsAdder config under import/ gives");
        LoadReport loaded = new ContentLoader().load(contents());
        assertEquals(List.of(), loaded.problems());
        ItemDefinition crate = loaded.items().stream().filter(item -> item.fullId().equals("medieval_rpg:crate"))
                .findFirst().orElseThrow();
        assertEquals("<gold>Old Crate", crate.displayName());
        assertEquals(new ModelSource.Provided(contents().resolve("medieval_rpg"),
                new ResourceLocation("medieval_rpg", "crate_1")), crate.model());
        assertFalse(loaded.items().stream().anyMatch(item -> item.fullId().equals("medieval_rpg:crate_1")));
        // The model is the pack's, not copied a second time.
        assertFalse(Files.exists(contents().resolve("medieval_rpg/models/crate_1.json")));
    }

    @Test
    void soundNamesThatWouldPlayNothingAreReportedButCopiedAsTheyAre() throws IOException {
        Map<String, byte[]> files = generatedPack();
        byte[] index = ("{\"golem_ancestral.invocar\":{\"sounds\":[\"golem_ancestral/invocar\"]}}")
                .getBytes(StandardCharsets.UTF_8);
        files.put("assets/arkcronist_jefes/sounds.json", index);
        zipParts(files);

        ImportReport report = new ItemsAdderImporter().run(importDir(), contents(), packs());

        assertArrayEquals(index, Files.readAllBytes(packs().resolve("generated_4/assets/arkcronist_jefes/sounds.json")));
        assertEquals(List.of("packs/generated_4/assets/arkcronist_jefes/sounds.json: 1 sound name(s) have no namespace,"
                + " so the client plays them from minecraft:sounds/ - e.g. 'golem_ancestral/invocar' is"
                + " minecraft:sounds/golem_ancestral/invocar.ogg - while the files are in arkcronist_jefes/sounds/;"
                + " those events play nothing. Left as they are: write 'arkcronist_jefes:golem_ancestral/invocar' (and"
                + " the same for the rest) in sounds.json to make them play"), report.problems());
    }

    @Test
    void overlaysAreReadForTheClientVersionTheyNameAndTheLastActiveOneWins() {
        JsonObject both = JsonParser.parseString("{\"formats\":[63,64],\"min_format\":63,\"max_format\":9999}")
                .getAsJsonObject();
        assertTrue(GeneratedPacks.covers(both, 64));
        assertFalse(GeneratedPacks.covers(both, 62));
        assertTrue(GeneratedPacks.covers(both, 88), "from 1.21.9 on, min_format and max_format");
        assertFalse(GeneratedPacks.covers(JsonParser.parseString("{\"formats\":{\"min_inclusive\":46,\"max_inclusive\":55}}")
                .getAsJsonObject(), 64));
        assertTrue(GeneratedPacks.covers(JsonParser.parseString("{\"min_format\":[63,0]}").getAsJsonObject(), 64));
        assertEquals("nazgul:greatbow", GeneratedPacks.primary(JsonParser.parseString(
                "{\"type\":\"minecraft:condition\",\"on_false\":{\"type\":\"model\",\"model\":\"nazgul:greatbow\"},"
                        + "\"on_true\":{\"type\":\"model\",\"model\":\"nazgul:pull\"}}")));
        assertEquals("generated_4", GeneratedPacks.folderName("Generated 4 - "));
        assertEquals("Nm Plushie Shulker", GeneratedPacks.displayName("nm_plushie_shulker"));
    }

    // ---------------------------------------------------------------- fixtures

    private static String dispatch(boolean oversized, String entries) {
        return "{" + (oversized ? "\"oversized_in_gui\":true," : "") + "\"model\":{\"type\":\"minecraft:range_dispatch\","
                + "\"property\":\"minecraft:custom_model_data\",\"index\":0,\"entries\":[" + entries + "],"
                + "\"fallback\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:item/paper\"}}}";
    }

    private static Map<String, String> snapshot(Path root) throws IOException {
        Map<String, String> files = new java.util.TreeMap<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                files.put(root.relativize(file).toString(), java.util.Arrays.toString(Files.readAllBytes(file)));
            }
        }
        return files;
    }

    private static void assertNote(ImportReport report, String text) {
        assertTrue(report.notes().stream().anyMatch(note -> note.contains(text)), text + " in " + report.notes());
    }

    private static JsonObject json(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    private static void text(Map<String, byte[]> files, String name, String text) {
        files.put(name, text.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] png(String marker) {
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        byte[] tail = marker.getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[signature.length + tail.length];
        System.arraycopy(signature, 0, bytes, 0, signature.length);
        System.arraycopy(tail, 0, bytes, signature.length, tail.length);
        return bytes;
    }

    private static void write(Path root, String relative, String text) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }
}
