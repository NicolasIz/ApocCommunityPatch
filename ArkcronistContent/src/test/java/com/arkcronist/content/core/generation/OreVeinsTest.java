package com.arkcronist.content.core.generation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OreVeinsTest {

    private static final long SEED = 8_675_309L;

    private static OreGeneration ore(int minY, int maxY, int veins, int size, double chance) {
        return new OreGeneration(Set.of(), Set.of(), Set.of(), minY, maxY, veins, size, chance, OreGeneration.STONES);
    }

    private static List<String> keys(List<int[]> blocks) {
        return blocks.stream().map(b -> b[0] + "," + b[1] + "," + b[2]).toList();
    }

    /** The same world gets the same veins, whatever order its chunks are generated in. */
    @Test
    void theSameSeedChunkAndOreAlwaysGiveTheSameVeins() {
        OreGeneration ruby = ore(-48, 24, 3, 5, 1);
        List<String> first = keys(OreVeins.plan(ruby, "demo:ruby_ore", SEED, 7, -3, -64, 320));
        OreVeins.plan(ruby, "demo:ruby_ore", SEED, 8, -3, -64, 320);
        assertEquals(first, keys(OreVeins.plan(ruby, "demo:ruby_ore", SEED, 7, -3, -64, 320)));
        assertNotEquals(first, keys(OreVeins.plan(ruby, "demo:ruby_ore", SEED, 8, -3, -64, 320)), "another chunk");
        assertNotEquals(first, keys(OreVeins.plan(ruby, "demo:ruby_ore", SEED + 1, 7, -3, -64, 320)), "another world");
        assertNotEquals(first, keys(OreVeins.plan(ruby, "demo:star_ore", SEED, 7, -3, -64, 320)),
                "each ore has a stream of its own");
    }

    @Test
    void everyBlockIsInsideTheChunkAndTheHeightsTheWorldHas() {
        OreGeneration deep = ore(-200, -40, 8, 12, 1);
        for (int chunk = -20; chunk < 20; chunk++) {
            List<int[]> blocks = OreVeins.plan(deep, "demo:deep", SEED, chunk, -chunk * 3, -64, 320);
            assertFalse(blocks.isEmpty());
            for (int[] block : blocks) {
                assertEquals(chunk, block[0] >> 4, "x in the chunk");
                assertEquals(-chunk * 3, block[2] >> 4, "z in the chunk");
                assertTrue(block[1] >= -64 && block[1] <= -40, "y " + block[1] + " in -64..-40");
            }
            assertEquals(blocks.size(), new HashSet<>(keys(blocks)).size(), "each block once");
        }
    }

    /** A vein is one piece: every block of it touches another by a face, and it is never bigger than asked. */
    @Test
    void aVeinIsConnectedAndNoBiggerThanItsSize() {
        OreGeneration one = ore(0, 60, 1, 6, 1);
        for (int chunk = 0; chunk < 200; chunk++) {
            List<int[]> vein = OreVeins.plan(one, "demo:ruby_ore", SEED, chunk, chunk, -64, 320);
            assertTrue(vein.size() >= 1 && vein.size() <= 6, vein.size() + " blocks");
            Set<String> reached = new HashSet<>();
            List<int[]> frontier = new ArrayList<>(List.of(vein.get(0)));
            reached.add(keys(List.of(vein.get(0))).get(0));
            while (!frontier.isEmpty()) {
                int[] at = frontier.remove(frontier.size() - 1);
                for (int[] other : vein) {
                    int distance = Math.abs(at[0] - other[0]) + Math.abs(at[1] - other[1]) + Math.abs(at[2] - other[2]);
                    if (distance == 1 && reached.add(keys(List.of(other)).get(0))) {
                        frontier.add(other);
                    }
                }
            }
            assertEquals(vein.size(), reached.size(), "connected");
        }
    }

    @Test
    void theChanceIsHowOftenATryMakesAVein() {
        OreGeneration half = ore(0, 60, 1, 1, 0.5);
        int made = 0;
        for (int chunk = 0; chunk < 4000; chunk++) {
            made += OreVeins.plan(half, "demo:ruby_ore", SEED, chunk, 0, -64, 320).size();
        }
        assertTrue(made > 1800 && made < 2200, made + " of 4000");
        assertTrue(OreVeins.plan(ore(0, 60, 4, 4, 0), "demo:x", SEED, 1, 1, -64, 320).isEmpty(), "chance 0");
        assertTrue(OreVeins.plan(ore(0, 60, 0, 4, 1), "demo:x", SEED, 1, 1, -64, 320).isEmpty(), "no tries");
    }

    /** Heights the world does not have: the nether's ores asked for below its floor make nothing. */
    @Test
    void heightsOutsideTheWorldMakeNothing() {
        assertTrue(OreVeins.plan(ore(-64, -10, 4, 4, 1), "demo:x", SEED, 0, 0, 0, 256).isEmpty());
        assertTrue(OreVeins.plan(ore(320, 400, 4, 4, 1), "demo:x", SEED, 0, 0, -64, 320).isEmpty(), "320 is the limit");
        for (int[] block : OreVeins.plan(ore(250, 400, 6, 8, 1), "demo:x", SEED, 0, 0, 0, 256)) {
            assertTrue(block[1] >= 250 && block[1] <= 255, String.valueOf(block[1]));
        }
    }

    @Test
    void worldsAndEnvironmentsDecideWhereAnOreIsLaid() {
        OreGeneration anywhereNormal = ore(0, 10, 1, 1, 1);
        assertTrue(anywhereNormal.appliesTo("world", "NORMAL"));
        assertFalse(anywhereNormal.appliesTo("world_nether", "NETHER"), "the overworld unless told");

        OreGeneration named = new OreGeneration(Set.of("mining"), Set.of(), Set.of(), 0, 10, 1, 1, 1,
                OreGeneration.STONES);
        assertTrue(named.appliesTo("Mining", "NETHER"), "a named world, whatever it is");
        assertFalse(named.appliesTo("world", "NORMAL"));

        OreGeneration nether = new OreGeneration(Set.of(), Set.of("nether"), Set.of("minecraft:crimson_forest"), 0, 10,
                1, 1, 1, Set.of("minecraft:netherrack"));
        assertTrue(nether.appliesTo("world_nether", "NETHER"));
        assertFalse(nether.appliesTo("world", "NORMAL"));
        assertTrue(nether.inBiome("minecraft:crimson_forest"));
        assertFalse(nether.inBiome("minecraft:warped_forest"));
        assertTrue(anywhereNormal.inBiome("minecraft:anything"), "no biomes: every biome");

        assertEquals("minecraft:deepslate", OreGeneration.key(" DEEPSLATE "));
        assertEquals("terralith:volcanic_peaks", OreGeneration.key("terralith:volcanic_peaks"));
    }
}
