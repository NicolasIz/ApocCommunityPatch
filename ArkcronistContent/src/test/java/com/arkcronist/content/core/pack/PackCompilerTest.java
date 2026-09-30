package com.arkcronist.content.core.pack;

import com.arkcronist.content.core.block.NoteBlockState;
import com.arkcronist.content.core.definition.ItemBehaviour;
import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.definition.EmojiDefinition;
import com.arkcronist.content.core.definition.ItemDefinition;
import com.arkcronist.content.core.definition.ModelSource;
import com.arkcronist.content.core.definition.ResourceLocation;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackCompilerTest {

    static final PackSettings SETTINGS = new PackSettings(new JsonPrimitive("test"), 46, 46, 99);

    @TempDir
    Path temp;

    @Test
    void writesTheVanillaLayoutAndOnlyWhatItemsReference() throws IOException {
        Path demo = temp.resolve("contents/demo");
        write(demo, "models/item/ruby.json", """
                { "parent": "demo:item/gem_base",
                  "textures": { "layer0": "demo:item/ruby", "layer1": "#layer0", "particle": "item/diamond" } }
                """);
        write(demo, "models/item/gem_base.json", """
                { "parent": "minecraft:item/generated", "textures": { "overlay": "demo:item/sparkle" } }
                """);
        write(demo, "textures/item/ruby.png", png("ruby"));
        write(demo, "textures/item/sparkle.png", png("sparkle"));
        write(demo, "textures/item/sparkle.png.mcmeta", "{ \"animation\": {} }");
        write(demo, "textures/item/ruby_sword.png", png("sword"));
        write(demo, "textures/item/unused_draft.png", png("draft"));

        Path pack = temp.resolve("pack");
        PackCompiler.Result result = new PackCompiler(SETTINGS).compile(pack, List.of(
                item("ruby", provided(demo, "demo:item/ruby")),
                item("ruby_sword", flat(demo, "ruby_sword", "demo:item/ruby_sword", "minecraft:item/handheld")),
                item("letter", null),
                item("vanilla_look", provided(demo, "minecraft:item/diamond"))), Map.of());

        assertEquals(List.of(), result.problems());
        assertEquals(3, result.itemDefinitions());
        assertEquals(3, result.models());
        assertEquals(3, result.textures());

        JsonObject meta = json(pack.resolve("pack.mcmeta")).getAsJsonObject("pack");
        assertEquals(46, meta.get("pack_format").getAsInt());
        assertEquals(99, meta.getAsJsonObject("supported_formats").get("max_inclusive").getAsInt());
        assertEquals(46, meta.get("min_format").getAsInt());
        assertEquals(99, meta.get("max_format").getAsInt());

        Path assets = pack.resolve("assets/demo");
        assertTrue(Files.isDirectory(assets.resolve("items")));
        assertTrue(Files.isDirectory(assets.resolve("models")));
        assertTrue(Files.isDirectory(assets.resolve("textures")));

        // The item definition the stack's item_model points at, and the model it names.
        assertEquals("demo:item/ruby", itemModelTarget(assets.resolve("items/ruby.json")));
        assertArrayEquals(Files.readAllBytes(demo.resolve("models/item/ruby.json")),
                Files.readAllBytes(assets.resolve("models/item/ruby.json")));
        // Followed through the parent, and through the textures of both.
        assertTrue(Files.exists(assets.resolve("models/item/gem_base.json")));
        assertTrue(Files.exists(assets.resolve("textures/item/ruby.png")));
        assertTrue(Files.exists(assets.resolve("textures/item/sparkle.png")));
        assertTrue(Files.exists(assets.resolve("textures/item/sparkle.png.mcmeta")));
        assertFalse(Files.exists(assets.resolve("textures/item/unused_draft.png")));

        // A lone texture gets a flat model generated for it.
        assertEquals("demo:item/ruby_sword", itemModelTarget(assets.resolve("items/ruby_sword.json")));
        JsonObject generated = json(assets.resolve("models/item/ruby_sword.json"));
        assertEquals("minecraft:item/handheld", generated.get("parent").getAsString());
        assertEquals("demo:item/ruby_sword", generated.getAsJsonObject("textures").get("layer0").getAsString());
        assertTrue(Files.exists(assets.resolve("textures/item/ruby_sword.png")));

        // No look: no definition. A vanilla model: a definition, nothing copied.
        assertFalse(Files.exists(assets.resolve("items/letter.json")));
        assertEquals("minecraft:item/diamond", itemModelTarget(assets.resolve("items/vanilla_look.json")));
        assertFalse(Files.exists(pack.resolve("assets/minecraft")));
    }

    @Test
    void reportsMissingAndMisnamedFilesAndClearsWhatAnEarlierBuildLeft() throws IOException {
        Path demo = temp.resolve("contents/demo");
        write(demo, "textures/item/photo.png", "JFIF pretending to be a PNG".getBytes(StandardCharsets.UTF_8));
        Path pack = temp.resolve("pack");
        write(pack, "assets/demo/items/removed_item.json", "{}");

        PackCompiler.Result result = new PackCompiler(SETTINGS).compile(pack, List.of(
                item("ghost", provided(demo, "demo:item/ghost")),
                item("photo", flat(demo, "photo", "demo:item/photo", "minecraft:item/generated"))), Map.of());

        assertEquals(2, result.problems().size(), result.problems().toString());
        assertTrue(result.problems().get(0).startsWith("demo:ghost: model demo:item/ghost not found"),
                result.problems().get(0));
        assertTrue(result.problems().get(1).contains("is not a PNG file"), result.problems().get(1));
        assertFalse(Files.exists(pack.resolve("assets/demo/items/removed_item.json")));
        assertFalse(Files.exists(pack.resolve("assets/demo/textures/item/photo.png")));
    }

    @Test
    void twoPacksFightingOverOnePathKeepTheFirstAndSaySo() throws IOException {
        Path first = temp.resolve("contents/first");
        Path second = temp.resolve("contents/second");
        Path third = temp.resolve("contents/third");
        write(first, "textures/item/shared.png", png("one"));
        write(second, "textures/item/shared.png", png("two"));
        write(third, "textures/item/shared.png", png("one"));

        Path pack = temp.resolve("pack");
        PackCompiler.Result result = new PackCompiler(SETTINGS).compile(pack, List.of(
                item("a", flat(first, "a", "demo:item/shared", "minecraft:item/generated")),
                item("b", flat(second, "b", "demo:item/shared", "minecraft:item/generated")),
                item("c", flat(third, "c", "demo:item/shared", "minecraft:item/generated"))), Map.of());

        // Identical bytes are not a conflict; different ones are, and the first item keeps the path.
        assertEquals(1, result.problems().size(), result.problems().toString());
        assertTrue(result.problems().get(0).startsWith("demo:b:"), result.problems().get(0));
        assertTrue(result.problems().get(0).contains("already supplied by demo:a"), result.problems().get(0));
        assertArrayEquals(png("one"), Files.readAllBytes(pack.resolve("assets/demo/textures/item/shared.png")));
    }

    @Test
    void customBlocksGetACubeModelAndTheirNoteBlockStates() throws IOException {
        Path demo = temp.resolve("contents/demo");
        write(demo, "textures/block/ruby_block.png", png("ruby block"));
        write(demo, "textures/block/amber_block.png", png("amber block"));
        Path pack = temp.resolve("pack");

        PackCompiler.Result result = new PackCompiler(SETTINGS).compile(pack, List.of(
                item("ruby_block", cube(demo, "ruby_block")),
                item("amber_block", cube(demo, "amber_block"))), Map.of(
                "demo:ruby_block", NoteBlockState.fromIndex(1),
                "demo:amber_block", NoteBlockState.fromIndex(51)));

        assertEquals(List.of(), result.problems());
        assertEquals(2, result.customBlockStates());

        JsonObject cube = json(pack.resolve("assets/demo/models/block/ruby_block.json"));
        assertEquals("minecraft:block/cube_all", cube.get("parent").getAsString());
        assertEquals("demo:block/ruby_block", cube.getAsJsonObject("textures").get("all").getAsString());
        assertEquals("demo:block/ruby_block", itemModelTarget(pack.resolve("assets/demo/items/ruby_block.json")));

        // Written where the client looks for the note block's states - the block's own namespace.
        assertFalse(Files.exists(pack.resolve("assets/demo/blockstates")));
        JsonObject variants = json(pack.resolve("assets/minecraft/blockstates/note_block.json"))
                .getAsJsonObject("variants");
        assertEquals(23 * 25 * 2, variants.size());
        assertEquals("demo:block/ruby_block", model(variants, "instrument=harp,note=0,powered=true"));
        assertEquals("demo:block/amber_block", model(variants, "instrument=basedrum,note=0,powered=true"));
        assertEquals("minecraft:block/note_block", model(variants, "instrument=harp,note=0,powered=false"));
        assertEquals("minecraft:block/note_block", model(variants, "instrument=piglin,note=24,powered=true"));
    }

    @Test
    void theNoteBlockFileIsTheSameBytesEveryBuildAndAbsentWithoutBlocks() throws IOException {
        Path demo = temp.resolve("contents/demo");
        write(demo, "textures/block/ruby_block.png", png("ruby block"));
        List<ItemDefinition> items = List.of(item("ruby_block", cube(demo, "ruby_block")));
        Map<String, NoteBlockState> states = Map.of("demo:ruby_block", NoteBlockState.fromIndex(7));
        Path first = temp.resolve("first");
        Path second = temp.resolve("second");

        new PackCompiler(SETTINGS).compile(first, items, states);
        new PackCompiler(SETTINGS).compile(second, items, new HashMap<>(states));
        assertArrayEquals(Files.readAllBytes(first.resolve("assets/minecraft/blockstates/note_block.json")),
                Files.readAllBytes(second.resolve("assets/minecraft/blockstates/note_block.json")));

        Path none = temp.resolve("none");
        new PackCompiler(SETTINGS).compile(none, items, Map.of());
        assertFalse(Files.exists(none.resolve("assets/minecraft")));
    }

    @Test
    void generatedModelsKeepTheirTextureVariablesInAFixedOrder() throws IOException {
        Path demo = temp.resolve("contents/demo");
        write(demo, "textures/block/log_side.png", png("side"));
        write(demo, "textures/block/log_top.png", png("top"));
        Map<String, ResourceLocation> textures = new LinkedHashMap<>();
        textures.put("side", loc("demo:block/log_side"));
        textures.put("end", loc("demo:block/log_top"));
        Path pack = temp.resolve("pack");

        new PackCompiler(SETTINGS).compile(pack, List.of(item("log", new ModelSource.Generated(demo,
                loc("demo:block/log"), loc("minecraft:block/cube_column"), textures))), Map.of());

        String written = Files.readString(pack.resolve("assets/demo/models/block/log.json"));
        assertTrue(written.indexOf("\"end\"") < written.indexOf("\"side\""), written);
        assertTrue(Files.exists(pack.resolve("assets/demo/textures/block/log_side.png")));
        assertTrue(Files.exists(pack.resolve("assets/demo/textures/block/log_top.png")));
    }

    @Test
    void anotherPluginsPackIsMergedAndOurFilesWinClashes() throws IOException {
        Path demo = temp.resolve("contents/demo");
        write(demo, "textures/item/ruby.png", png("ours"));
        Path armor = temp.resolve("plugins/MythicArmor/pack");
        write(armor, "pack.mcmeta", "{ \"pack\": { \"pack_format\": 1 } }");
        write(armor, "assets/mythicarmor/equipment/ruby.json", "{ \"layers\": {} }");
        write(armor, "assets/mythicarmor/textures/entity/equipment/humanoid/ruby.png", png("armor"));
        write(armor, "assets/demo/textures/item/ruby.png", png("theirs"));
        Path pack = temp.resolve("pack");

        PackCompiler.Result result = new PackCompiler(SETTINGS).compile(pack,
                List.of(item("ruby", flat(demo, "ruby", "demo:item/ruby", "minecraft:item/generated"))), Map.of(),
                List.of(new ExternalPack("MythicArmor", armor), new ExternalPack("Missing", temp.resolve("nowhere"))));

        assertEquals(2, result.externalFiles());
        assertTrue(Files.exists(pack.resolve("assets/mythicarmor/equipment/ruby.json")));
        assertTrue(Files.exists(pack.resolve("assets/mythicarmor/textures/entity/equipment/humanoid/ruby.png")));
        // Our pack.mcmeta, and our texture where both supply one.
        assertEquals(46, json(pack.resolve("pack.mcmeta")).getAsJsonObject("pack").get("pack_format").getAsInt());
        assertArrayEquals(png("ours"), Files.readAllBytes(pack.resolve("assets/demo/textures/item/ruby.png")));
        assertEquals(2, result.problems().size(), result.problems().toString());
        assertTrue(result.problems().stream().anyMatch(p -> p.startsWith("MythicArmor pack: assets/demo/textures/item/ruby.png")));
        assertTrue(result.problems().stream().anyMatch(p -> p.startsWith("Missing pack: no assets folder")));
    }

    @Test
    void anotherPluginsPackCannotLinkToFilesOutsideIt() throws IOException {
        write(temp, "server/secret.txt", "password");
        Path secret = temp.resolve("server/secret.txt");
        Path armor = temp.resolve("plugins/MythicArmor/pack");
        write(armor, "assets/mythicarmor/equipment/ruby.json", "{ \"layers\": {} }");
        Files.createSymbolicLink(armor.resolve("assets/mythicarmor/leak.txt"), secret);
        Path pack = temp.resolve("pack");

        PackCompiler.Result result = new PackCompiler(SETTINGS).compile(pack, List.of(), Map.of(),
                List.of(new ExternalPack("MythicArmor", armor)));

        assertEquals(1, result.externalFiles());
        assertFalse(Files.exists(pack.resolve("assets/mythicarmor/leak.txt")));
    }

    @Test
    void everyCropStageGetsItsModelAndAnItemDefinition() throws IOException {
        Path demo = temp.resolve("contents/demo");
        write(demo, "textures/crop/berry_0.png", png("0"));
        write(demo, "textures/crop/berry_1.png", png("1"));
        write(demo, "models/crop/berry_grown.json", "{\"parent\": \"block/cross\", \"textures\": {\"cross\": \"demo:crop/berry_1\"}}");
        List<ModelSource> stages = List.of(
                new ModelSource.Generated(demo, loc("demo:block/berry_stage_0"), loc("block/cross"), Map.of("cross", loc("demo:crop/berry_0"))),
                new ModelSource.Provided(demo, loc("demo:crop/berry_grown")));
        ItemDefinition berry = new ItemDefinition("demo", "berry", "PAPER", null, List.of(), stages.get(1),
                ItemBehaviour.DEFAULT, new Placement.Crop(stages, 60, 9, List.of("FARMLAND"), true, List.of()),
                Path.of("crops.yml"));
        Path pack = temp.resolve("pack");

        PackCompiler.Result result = new PackCompiler(SETTINGS).compile(pack, List.of(berry), Map.of());

        assertEquals(List.of(), result.problems());
        assertEquals("demo:block/berry_stage_0",
                json(pack.resolve("assets/demo/items/berry/stage_0.json")).getAsJsonObject("model").get("model").getAsString());
        assertEquals("demo:crop/berry_grown",
                json(pack.resolve("assets/demo/items/berry/stage_1.json")).getAsJsonObject("model").get("model").getAsString());
        assertEquals("demo:crop/berry_grown",
                json(pack.resolve("assets/demo/items/berry.json")).getAsJsonObject("model").get("model").getAsString());
        assertEquals("demo:crop/berry_0",
                json(pack.resolve("assets/demo/models/block/berry_stage_0.json")).getAsJsonObject("textures").get("cross").getAsString());
        assertTrue(Files.exists(pack.resolve("assets/demo/textures/crop/berry_1.png")));
        assertEquals(3, result.itemDefinitions());
    }

    @Test
    void emojisBecomeBitmapGlyphsOfTheDefaultFontInCharacterOrder() throws IOException {
        Path demo = temp.resolve("contents/demo");
        write(demo, "textures/emoji/ruby.png", png("ruby"));
        write(demo, "textures/emoji/heart.png", png("heart"));
        EmojiDefinition ruby = new EmojiDefinition("demo", "ruby", loc("demo:emoji/ruby"), 8, 7, null, demo, Path.of("e.yml"));
        EmojiDefinition heart = new EmojiDefinition("demo", "heart", loc("demo:emoji/heart"), 10, 8, null, demo, Path.of("e.yml"));
        EmojiDefinition missing = new EmojiDefinition("demo", "gone", loc("demo:emoji/gone"), 8, 7, null, demo, Path.of("e.yml"));
        Path pack = temp.resolve("pack");

        PackCompiler.Result result = new PackCompiler(SETTINGS).compile(pack, List.of(), Map.of(),
                Map.of(ruby, 0xE001, heart, 0xE000, missing, 0xE002), List.of());

        assertEquals(3, result.glyphs());
        assertEquals(1, result.problems().size(), result.problems().toString());
        assertTrue(result.problems().get(0).startsWith("emoji :gone:: texture demo:emoji/gone not found"));
        JsonArray providers = json(pack.resolve("assets/minecraft/font/default.json")).getAsJsonArray("providers");
        JsonObject first = providers.get(0).getAsJsonObject();
        assertEquals("bitmap", first.get("type").getAsString());
        assertEquals("demo:emoji/heart.png", first.get("file").getAsString());
        assertEquals(10, first.get("height").getAsInt());
        assertEquals(8, first.get("ascent").getAsInt());
        assertEquals("\uE000", first.getAsJsonArray("chars").get(0).getAsString());
        assertEquals("\uE001", providers.get(1).getAsJsonObject().getAsJsonArray("chars").get(0).getAsString());
        assertTrue(Files.exists(pack.resolve("assets/demo/textures/emoji/ruby.png")));
    }

    @Test
    void noEmojisMeansNoFontFile() throws IOException {
        new PackCompiler(SETTINGS).compile(temp.resolve("pack"), List.of(), Map.of());

        assertFalse(Files.exists(temp.resolve("pack/assets/minecraft/font")));
    }

    // ---------------------------------------------------------------- fixtures

    /** What the loader makes of {@code texture:} on a custom block. */
    static ModelSource cube(Path root, String id) {
        return new ModelSource.Generated(root, loc("demo:block/" + id), ModelSource.CUBE_ALL,
                Map.of("all", loc("demo:block/" + id)));
    }

    private static String model(JsonObject variants, String key) {
        return variants.getAsJsonObject(key).get("model").getAsString();
    }

    static ItemDefinition item(String id, ModelSource model) {
        return new ItemDefinition("demo", id, "PAPER", null, List.of(), model, ItemBehaviour.DEFAULT, null,
                Path.of("items.yml"));
    }

    static ModelSource provided(Path root, String model) {
        return new ModelSource.Provided(root, loc(model));
    }

    /** What the loader makes of {@code texture:} on a plain item. */
    static ModelSource flat(Path root, String id, String texture, String parent) {
        return new ModelSource.Generated(root, loc("demo:item/" + id), loc(parent), Map.of("layer0", loc(texture)));
    }

    static ResourceLocation loc(String text) {
        return ResourceLocation.parse(text, ResourceLocation.MINECRAFT);
    }

    /** The PNG signature followed by a marker; the compiler checks only the signature. */
    static byte[] png(String marker) {
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        byte[] tail = marker.getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[signature.length + tail.length];
        System.arraycopy(signature, 0, bytes, 0, signature.length);
        System.arraycopy(tail, 0, bytes, signature.length, tail.length);
        return bytes;
    }

    static void write(Path root, String relative, String text) throws IOException {
        write(root, relative, text.getBytes(StandardCharsets.UTF_8));
    }

    static void write(Path root, String relative, byte[] bytes) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
    }

    private static JsonObject json(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    private static String itemModelTarget(Path definition) throws IOException {
        JsonObject model = json(definition).getAsJsonObject("model");
        assertEquals("minecraft:model", model.get("type").getAsString());
        return model.get("model").getAsString();
    }
}
