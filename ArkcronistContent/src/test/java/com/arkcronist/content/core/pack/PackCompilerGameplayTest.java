package com.arkcronist.content.core.pack;

import com.arkcronist.content.core.definition.ItemBehaviour;
import com.arkcronist.content.core.definition.ItemDefinition;
import com.arkcronist.content.core.definition.ModelSource;
import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.definition.ResourceLocation;
import com.arkcronist.content.core.hud.HudGlyph;
import com.arkcronist.content.core.liquid.LiquidModels;
import com.arkcronist.content.core.liquid.TripwireState;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 1.7: liquids' tripwire states and HUD glyphs in the pack. */
class PackCompilerGameplayTest {

    @TempDir
    Path temp;

    private static ResourceLocation loc(String text) {
        return ResourceLocation.parse(text, ResourceLocation.MINECRAFT);
    }

    @Test
    void aLiquidGetsItsTwoTripwireStatesTheParentsAndItsTexture() throws IOException {
        Path demo = temp.resolve("contents/demo");
        PackCompilerTest.write(demo, "textures/block/acid.png", PackCompilerTest.png("acid"));
        PackCompilerTest.write(demo, "textures/block/acid.png.mcmeta", "{\"animation\":{}}");
        ModelSource source = new ModelSource.Generated(demo, loc("demo:block/acid_source"), LiquidModels.SOURCE_PARENT,
                Map.of(LiquidModels.TEXTURE_VARIABLE, loc("demo:block/acid"), "particle", loc("demo:block/acid")));
        ModelSource flowing = new ModelSource.Generated(demo, loc("demo:block/acid_flowing"), LiquidModels.FLOWING_PARENT,
                Map.of(LiquidModels.TEXTURE_VARIABLE, loc("demo:block/acid"), "particle", loc("demo:block/acid")));
        ItemDefinition acid = new ItemDefinition("demo", "acid", "PAPER", null, List.of(),
                new ModelSource.Provided(demo, loc("minecraft:item/water_bucket")), new ItemBehaviour(true, false),
                new Placement.Liquid(4, 8, 32, source, flowing, Placement.Contact.HARMLESS), demo.resolve("l.yml"));
        Path pack = temp.resolve("pack");

        PackCompiler.Result result = new PackCompiler(PackCompilerTest.SETTINGS).compile(pack, new PackCompiler.Input(
                List.of(acid), Map.of(), Map.of(), List.of(), Map.of(), Map.of("demo:acid", 3), List.of(), false,
                List.of()));

        assertEquals(List.of(), result.problems());
        JsonObject variants = json(pack.resolve("assets/minecraft/blockstates/tripwire.json")).getAsJsonObject("variants");
        assertEquals(128, variants.size());
        assertEquals("demo:block/acid_source",
                variants.getAsJsonObject(TripwireState.source(3).variantKey(true, false)).get("model").getAsString());
        assertEquals("demo:block/acid_flowing",
                variants.getAsJsonObject(TripwireState.flowing(3).variantKey(true, false)).get("model").getAsString());
        assertEquals("minecraft:block/tripwire_attached_ns",
                variants.getAsJsonObject(TripwireState.source(0).variantKey(true, false)).get("model").getAsString(),
                "a free state keeps vanilla's look");

        JsonObject parent = json(pack.resolve("assets/arkcontent/models/block/liquid_source.json"));
        JsonArray to = parent.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonArray("to");
        assertEquals(LiquidModels.SOURCE_HEIGHT, to.get(1).getAsInt());
        assertEquals("#liquid", parent.getAsJsonObject("textures").get("particle").getAsString());
        assertTrue(Files.exists(pack.resolve("assets/arkcontent/models/block/liquid_flowing.json")));
        JsonObject model = json(pack.resolve("assets/demo/models/block/acid_source.json"));
        assertEquals("arkcontent:block/liquid_source", model.get("parent").getAsString());
        assertTrue(Files.exists(pack.resolve("assets/demo/textures/block/acid.png")));
        assertTrue(Files.exists(pack.resolve("assets/demo/textures/block/acid.png.mcmeta")), "animated");
        // The bucket draws vanilla's water bucket: referenced, not copied.
        assertFalse(Files.exists(pack.resolve("assets/minecraft/models/item/water_bucket.json")));
    }

    @Test
    void noLiquidsLeaveTripwireAlone() throws IOException {
        Path pack = temp.resolve("pack");
        new PackCompiler(PackCompilerTest.SETTINGS).compile(pack, new PackCompiler.Input(List.of(), Map.of(), Map.of(),
                List.of(), Map.of(), Map.of(), List.of(), false, List.of()));
        assertFalse(Files.exists(pack.resolve("assets/minecraft/blockstates/tripwire.json")));
        assertFalse(Files.exists(pack.resolve("assets/minecraft/font/default.json")));
    }

    @Test
    void hudIconsAndTheSpaceCharactersGoIntoTheDefaultFont() throws IOException {
        Path demo = temp.resolve("contents/demo");
        PackCompilerTest.write(demo, "textures/hud/drop.png", PackCompilerTest.png("drop"));
        Path pack = temp.resolve("pack");
        List<HudGlyph> glyphs = List.of(
                new HudGlyph("hud demo:thirst empty", "demo", loc("demo:hud/drop"), demo, 9, -5, 0xF001),
                new HudGlyph("hud demo:thirst full", "demo", loc("demo:hud/drop"), demo, 9, -5, 0xF000));

        PackCompiler.Result result = new PackCompiler(PackCompilerTest.SETTINGS).compile(pack, new PackCompiler.Input(
                List.of(), Map.of(), Map.of(), List.of(), Map.of(), Map.of(), glyphs, false, List.of()));

        assertEquals(List.of(), result.problems());
        assertEquals(2, result.glyphs());
        JsonArray providers = json(pack.resolve("assets/minecraft/font/default.json")).getAsJsonArray("providers");
        assertEquals(3, providers.size());
        JsonObject first = providers.get(0).getAsJsonObject();
        assertEquals("demo:hud/drop.png", first.get("file").getAsString());
        assertEquals(-5, first.get("ascent").getAsInt());
        assertEquals("", first.getAsJsonArray("chars").get(0).getAsString(), "in character order");
        JsonObject spaces = providers.get(2).getAsJsonObject();
        assertEquals("space", spaces.get("type").getAsString());
        assertEquals(-1024, spaces.getAsJsonObject("advances").get("").getAsInt());
        assertEquals(30, spaces.getAsJsonObject("advances").size());
        assertTrue(Files.exists(pack.resolve("assets/demo/textures/hud/drop.png")));
    }

    @Test
    void theSpaceCharactersAloneWhenAskedFor() throws IOException {
        Path pack = temp.resolve("pack");
        new PackCompiler(PackCompilerTest.SETTINGS).compile(pack, new PackCompiler.Input(List.of(), Map.of(), Map.of(),
                List.of(), Map.of(), Map.of(), List.of(), true, List.of()));
        JsonArray providers = json(pack.resolve("assets/minecraft/font/default.json")).getAsJsonArray("providers");
        assertEquals(1, providers.size());
        assertEquals("space", providers.get(0).getAsJsonObject().get("type").getAsString());
    }

    private static JsonObject json(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }
}
