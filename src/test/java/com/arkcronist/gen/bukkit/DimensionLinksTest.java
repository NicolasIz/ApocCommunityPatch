package com.arkcronist.gen.bukkit;

import com.arkcronist.gen.bukkit.world.DimensionLinks;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The naming that ties a world to its own Nether and End.
 *
 * <p>Everything else about companion dimensions needs a server: creating a world, routing a portal,
 * asking what generator made something. This does not, and it is where the mistakes that matter
 * live - a name read back wrongly sends a player to a world that does not exist, and the way to find
 * that out otherwise is for somebody to walk into a portal.</p>
 */
class DimensionLinksTest {

    private static final String NETHER = "_nether";
    private static final String END = "_the_end";

    @Test
    @DisplayName("a world's companions follow the server's own naming convention")
    void companionsAreNamedTheWayTheServerNamesThem() {
        assertEquals("insane_nether", DimensionLinks.netherOf("insane", NETHER));
        assertEquals("insane_the_end", DimensionLinks.endOf("insane", END));
        assertEquals("world_nether", DimensionLinks.netherOf("world", NETHER));
    }

    @Test
    @DisplayName("a companion's name reads back to the world it belongs to")
    void aCompanionKnowsItsWorld() {
        assertEquals("insane", DimensionLinks.baseOf("insane_nether", NETHER, END));
        assertEquals("insane", DimensionLinks.baseOf("insane_the_end", NETHER, END));
        assertEquals("chaotic", DimensionLinks.baseOf("chaotic_nether", NETHER, END));
    }

    @Test
    @DisplayName("an ordinary world is not mistaken for somebody's companion")
    void anOrdinaryWorldIsLeftAlone() {
        assertNull(DimensionLinks.baseOf("insane", NETHER, END));
        assertNull(DimensionLinks.baseOf("survival", NETHER, END));
        assertNull(DimensionLinks.baseOf("creative_flat", NETHER, END));
        // A name that is nothing but the suffix belongs to a world called "", which is not a world.
        assertNull(DimensionLinks.baseOf("_nether", NETHER, END));
    }

    @Test
    @DisplayName("overlapping suffixes read as the longer one, not the shorter")
    void theLongerSuffixWins() {
        // Nobody has to configure this, and somebody will. With "_end" and "_the_end" both in play,
        // reading the short one first turns insane_the_end into a world called "insane_the" - and a
        // portal that leads to a world that was never created is a player stuck in the End.
        assertEquals("insane", DimensionLinks.baseOf("insane_the_end", "_nether", "_the_end"));
        assertEquals("insane", DimensionLinks.baseOf("insane_the_end", "_the_end", "_end"));
        assertEquals("insane", DimensionLinks.baseOf("insane_end", "_nether", "_end"));
    }

    @Test
    @DisplayName("the round trip holds for every preset name")
    void namingAndReadingBackAgree() {
        for (String base : new String[]{"base", "chaotic", "insane", "prueba3", "mundo_raro"}) {
            assertEquals(base, DimensionLinks.baseOf(DimensionLinks.netherOf(base, NETHER), NETHER, END));
            assertEquals(base, DimensionLinks.baseOf(DimensionLinks.endOf(base, END), NETHER, END));
            assertTrue(DimensionLinks.isNetherOf(base + NETHER, base, NETHER));
            assertTrue(DimensionLinks.isEndOf(base + END, base, END));
        }
    }

    @Test
    @DisplayName("a companion's seed is fixed for its world, and is not the world's own")
    void seedsAreDerivedAndDistinct() {
        long world = 20260823L;
        long nether = DimensionLinks.seedFor(world, true);
        long end = DimensionLinks.seedFor(world, false);

        assertEquals(nether, DimensionLinks.seedFor(world, true), "the same world gives a different "
                + "Nether each time it is asked, so the world is not reproducible from its seed");
        assertNotEquals(world, nether, "the Nether is the overworld's terrain again in netherrack");
        assertNotEquals(world, end);
        assertNotEquals(nether, end, "the Nether and the End are the same world twice");

        // Two presets on the same server have different seeds, so their companions differ too -
        // which is the entire point of giving each world its own.
        assertNotEquals(DimensionLinks.seedFor(1L, true), DimensionLinks.seedFor(2L, true));
    }
}
