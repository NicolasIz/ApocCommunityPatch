package com.arkcronist.content.core.pack;

import com.arkcronist.content.core.block.LeavesState;
import com.arkcronist.content.core.block.NoteBlockState;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static com.arkcronist.content.core.pack.PackCompilerTest.SETTINGS;
import static com.arkcronist.content.core.pack.PackCompilerTest.cube;
import static com.arkcronist.content.core.pack.PackCompilerTest.item;
import static com.arkcronist.content.core.pack.PackCompilerTest.png;
import static com.arkcronist.content.core.pack.PackCompilerTest.write;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 5.5: logs lying on their side, and custom leaves drawn over vanilla leaves blocks. */
class PackCompilerTreesTest {

    @TempDir
    Path temp;

    @Test
    void aLogOnItsSideIsItsUprightModelTurnedAsVanillasLogs() throws IOException {
        Path demo = temp.resolve("contents/demo");
        write(demo, "textures/block/oak.png", png("oak"));
        Path pack = temp.resolve("pack");
        PackCompiler.Result result = new PackCompiler(SETTINGS).compile(pack, List.of(item("oak", cube(demo, "oak"))),
                Map.of("demo:oak", NoteBlockState.fromIndex(1), "demo:oak#x", NoteBlockState.fromIndex(2),
                        "demo:oak#z", NoteBlockState.fromIndex(3)));
        assertEquals(List.of(), result.problems());
        assertEquals(3, result.customBlockStates());
        JsonObject variants = json(pack.resolve("assets/minecraft/blockstates/note_block.json")).getAsJsonObject("variants");
        JsonObject upright = variants.getAsJsonObject(NoteBlockState.fromIndex(1).variantKey());
        JsonObject east = variants.getAsJsonObject(NoteBlockState.fromIndex(2).variantKey());
        JsonObject north = variants.getAsJsonObject(NoteBlockState.fromIndex(3).variantKey());
        assertEquals("demo:block/oak", upright.get("model").getAsString());
        assertFalse(upright.has("x"));
        assertEquals("demo:block/oak", east.get("model").getAsString());
        assertEquals(90, east.get("x").getAsInt());
        assertEquals(90, east.get("y").getAsInt());
        assertEquals(90, north.get("x").getAsInt());
        assertFalse(north.has("y"));
    }

    @Test
    void leavesBlocksDrawTheirCustomLeavesAndOtherwiseThemselves() throws IOException {
        Path demo = temp.resolve("contents/demo");
        write(demo, "textures/block/willow_leaves.png", png("willow"));
        write(demo, "textures/block/maple_leaves.png", png("maple"));
        Path pack = temp.resolve("pack");
        PackCompiler.Result result = new PackCompiler(SETTINGS).compile(pack, new PackCompiler.Input(
                List.of(item("willow_leaves", cube(demo, "willow_leaves")), item("maple_leaves", cube(demo, "maple_leaves"))),
                Map.of(), Map.of(), List.of(), Map.of(), Map.of(), List.of(), false, List.of(), null, List.of(),
                Map.of("demo:willow_leaves", new LeavesState("flowering_azalea_leaves", 0),
                        "demo:maple_leaves", new LeavesState("flowering_azalea_leaves", 2))));
        assertEquals(List.of(), result.problems());
        assertFalse(Files.exists(pack.resolve("assets/minecraft/blockstates/azalea_leaves.json")), "a block not in use");
        JsonObject variants = json(pack.resolve("assets/minecraft/blockstates/flowering_azalea_leaves.json"))
                .getAsJsonObject("variants");
        assertEquals(28, variants.size(), "every state, or the client draws the rest as missing");
        for (boolean wet : new boolean[]{false, true}) {
            assertEquals("demo:block/willow_leaves", model(variants, 1, true, wet), "grown");
            assertEquals("demo:block/willow_leaves", model(variants, 2, true, wet), "placed");
            assertEquals("minecraft:block/flowering_azalea_leaves", model(variants, 3, true, wet), "a free slot");
            assertEquals("demo:block/maple_leaves", model(variants, 5, true, wet));
            assertEquals("demo:block/maple_leaves", model(variants, 6, true, wet));
            assertEquals("minecraft:block/flowering_azalea_leaves", model(variants, 7, true, wet), "vanilla's own, held at 7");
            for (int distance = 1; distance <= 7; distance++) {
                assertEquals("minecraft:block/flowering_azalea_leaves", model(variants, distance, false, wet),
                        "a tree's own leaves");
            }
        }
        // Drawn with a model of its own, never tinted by the biome as vanilla's leaves models are.
        JsonObject model = json(pack.resolve("assets/demo/models/block/willow_leaves.json"));
        assertEquals("minecraft:block/cube_all", model.get("parent").getAsString());
        assertTrue(result.customBlockStates() >= 8);
    }

    private static String model(JsonObject variants, int distance, boolean persistent, boolean waterlogged) {
        return variants.getAsJsonObject(LeavesState.variantKey(distance, persistent, waterlogged)).get("model").getAsString();
    }

    private static JsonObject json(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }
}
