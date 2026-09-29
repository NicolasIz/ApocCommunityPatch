package com.arkcronist.content.core.pack;

import com.arkcronist.content.core.definition.ItemAssets;
import com.arkcronist.content.core.definition.ItemBehaviour;
import com.arkcronist.content.core.definition.ItemDefinition;
import com.arkcronist.content.core.definition.ResourceLocation;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
                item("ruby", demo, loc("demo:item/ruby"), null, ItemAssets.GENERATED),
                item("ruby_sword", demo, null, loc("demo:item/ruby_sword"), loc("minecraft:item/handheld")),
                item("letter", demo, null, null, ItemAssets.GENERATED),
                item("vanilla_look", demo, loc("minecraft:item/diamond"), null, ItemAssets.GENERATED)));

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
                item("ghost", demo, loc("demo:item/ghost"), null, ItemAssets.GENERATED),
                item("photo", demo, null, loc("demo:item/photo"), ItemAssets.GENERATED)));

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
                item("a", first, null, loc("demo:item/shared"), ItemAssets.GENERATED),
                item("b", second, null, loc("demo:item/shared"), ItemAssets.GENERATED),
                item("c", third, null, loc("demo:item/shared"), ItemAssets.GENERATED)));

        // Identical bytes are not a conflict; different ones are, and the first item keeps the path.
        assertEquals(1, result.problems().size(), result.problems().toString());
        assertTrue(result.problems().get(0).startsWith("demo:b:"), result.problems().get(0));
        assertTrue(result.problems().get(0).contains("already supplied by demo:a"), result.problems().get(0));
        assertArrayEquals(png("one"), Files.readAllBytes(pack.resolve("assets/demo/textures/item/shared.png")));
    }

    // ---------------------------------------------------------------- fixtures

    static ItemDefinition item(String id, Path root, ResourceLocation model, ResourceLocation texture,
                               ResourceLocation parent) {
        return new ItemDefinition("demo", id, "PAPER", null, List.of(),
                new ItemAssets(root, model, texture, parent), ItemBehaviour.DEFAULT, root.resolve("items.yml"));
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
