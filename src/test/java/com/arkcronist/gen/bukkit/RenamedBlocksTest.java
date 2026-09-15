package com.arkcronist.gen.bukkit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The table of blocks the game has renamed.
 *
 * <p>Resolving a block needs a running server, so what can be checked here is the table itself - and
 * the table is where the mistake would be. An entry pointing at a block that is not the same block
 * places the wrong one silently, in every prefab that uses it, with nothing in the log. That is
 * strictly worse than the warning it exists to remove.</p>
 */
class RenamedBlocksTest {

    @Test
    @DisplayName("grass is short_grass, which is why it was coming out as stone")
    void theOneThatBit() {
        // Five of the prefabs shipped with this plugin were built before 1.20.3. Their grass
        // resolved to nothing, the substitute for nothing is stone, and three castles, a wizard
        // tower and a desert temple were laying solid grey cubes where tufts of grass belong.
        assertEquals("minecraft:short_grass", BlockBridge.renamedBlocks().get("minecraft:grass"));
    }

    @Test
    @DisplayName("every entry is a namespaced id that renames to something else")
    void wellFormed() {
        Map<String, String> renames = BlockBridge.renamedBlocks();
        assertTrue(renames.size() >= 2);
        renames.forEach((from, to) -> {
            assertTrue(from.startsWith("minecraft:"), from + " is not a namespaced id");
            assertTrue(to.startsWith("minecraft:"), to + " is not a namespaced id");
            assertNotEquals(from, to, from + " renames to itself");
            assertTrue(from.indexOf('[') < 0, from + " carries a block state; keys are plain names");
            assertTrue(to.indexOf('[') < 0, to + " carries a block state; values are plain names");
        });
    }

    @Test
    @DisplayName("no entry renames to something that is itself renamed")
    void noChains() {
        // A chain would resolve to the middle of it and stop, which looks like it worked.
        Map<String, String> renames = BlockBridge.renamedBlocks();
        renames.values().forEach(to ->
                assertTrue(!renames.containsKey(to), to + " is both a new name and an old one"));
    }
}
