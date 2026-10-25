package com.arkcronist.content.core.block;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoteBlockStateTest {

    @Test
    void eightHundredDistinctStatesThatRoundTripThroughTheirIndex() {
        assertEquals(800, NoteBlockState.CAPACITY);
        Set<String> keys = new HashSet<>();
        for (int index = 0; index < NoteBlockState.CAPACITY; index++) {
            NoteBlockState state = NoteBlockState.fromIndex(index);
            assertEquals(index, state.index());
            assertTrue(keys.add(state.variantKey()), state.variantKey());
            assertEquals(Optional.of(state), NoteBlockState.parse(state.asBlockData()));
        }
    }

    @Test
    void indexZeroIsTheVanillaStateAndTheLayoutIsFixed() {
        assertEquals(NoteBlockState.VANILLA, NoteBlockState.fromIndex(0));
        assertEquals(new NoteBlockState("harp", 0, true), NoteBlockState.fromIndex(1));
        assertEquals(new NoteBlockState("harp", 1, false), NoteBlockState.fromIndex(2));
        assertEquals(new NoteBlockState("basedrum", 0, false), NoteBlockState.fromIndex(50));
        assertEquals(new NoteBlockState("pling", 24, true), NoteBlockState.fromIndex(799));
        assertThrows(IllegalArgumentException.class, () -> NoteBlockState.fromIndex(800));
    }

    @Test
    void readsTheServersBlockDataStringInAnyPropertyOrder() {
        assertEquals(Optional.of(new NoteBlockState("bell", 3, true)),
                NoteBlockState.parse("minecraft:note_block[powered=true,note=3,instrument=bell]"));
        assertEquals("minecraft:note_block[instrument=bell,note=3,powered=true]",
                new NoteBlockState("bell", 3, true).asBlockData());

        assertTrue(NoteBlockState.parse("minecraft:stone").isEmpty());
        assertTrue(NoteBlockState.parse("minecraft:note_block[instrument=bell,note=30,powered=true]").isEmpty());
        assertTrue(NoteBlockState.parse("minecraft:note_block[instrument=kazoo,note=3,powered=true]").isEmpty());
        assertTrue(NoteBlockState.parse("minecraft:note_block[note=3]").isEmpty());
    }

    /** Mob-head instruments exist - the blockstate file needs them - but no custom block uses one. */
    @Test
    void mobHeadInstrumentsHaveNoIndex() {
        NoteBlockState zombie = NoteBlockState.parse("minecraft:note_block[instrument=zombie,note=0,powered=false]")
                .orElseThrow();
        assertEquals(-1, zombie.index());
        assertEquals(23, NoteBlockState.ALL_INSTRUMENTS.size());
    }
}
