package com.arkcronist.content.core.block;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeavesStateTest {

    @Test
    void eachSlotIsTwoPersistentDistancesGrownThenPlaced() {
        for (int index = 0; index < LeavesState.CAPACITY; index++) {
            LeavesState state = LeavesState.fromIndex(index);
            assertEquals(index, state.index());
            assertEquals(1, state.naturalDistance() % 2, "grown leaves at an odd distance");
            assertEquals(state.naturalDistance() + 1, state.placedDistance());
            assertTrue(state.placedDistance() < LeavesState.VANILLA_DISTANCE, "never vanilla's own distance 7");
            assertEquals(Optional.of(new LeavesState.Reading(state, true)),
                    LeavesState.parse(state.asBlockData(true, false)));
            assertEquals(Optional.of(new LeavesState.Reading(state, false)),
                    LeavesState.parse(state.asBlockData(false, true)));
        }
        assertEquals(30, LeavesState.CAPACITY);
        assertFalse(LeavesState.POOL.contains("mangrove_leaves"), "bone meal grows a propagule under mangrove leaves");
        assertEquals("flowering_azalea_leaves", LeavesState.POOL.get(0));
        assertEquals("cherry_leaves", LeavesState.POOL.get(LeavesState.POOL.size() - 1),
                "last: its pink petals fall from custom leaves too, ten times as often as others'");
    }

    @Test
    void vanillasOwnLeavesAreNeverCustom() {
        assertEquals(Optional.empty(), LeavesState.read("minecraft:oak_leaves", 3, false), "a tree's own leaves");
        assertEquals(Optional.empty(), LeavesState.read("OAK_LEAVES", 7, true), "a player's, held at 7");
        assertEquals(Optional.empty(), LeavesState.read("mangrove_leaves", 1, true), "not in the pool");
        assertEquals(Optional.empty(), LeavesState.parse("minecraft:note_block[instrument=harp,note=1,powered=false]"));
        assertEquals(Optional.of(new LeavesState.Reading(new LeavesState("azalea_leaves", 2), false)),
                LeavesState.read("AZALEA_LEAVES", 6, true));
        assertEquals("distance=3,persistent=true,waterlogged=false", LeavesState.variantKey(3, true, false));
    }

    @Test
    void slotsAreKeptAndAPacksLeavesBlockIsLeftToIt() {
        LeavesAllocator.Allocation first = LeavesAllocator.allocate(Map.of(), List.of("w:b", "w:a"), Set.of());
        assertEquals(new LeavesState("flowering_azalea_leaves", 0), first.active().get("w:a"), "by id, lowest first");
        assertEquals(new LeavesState("flowering_azalea_leaves", 1), first.active().get("w:b"));
        assertTrue(first.changed());

        // A pack draws flowering azalea leaves itself: a new one goes past them; the old ones keep theirs.
        LeavesAllocator.Allocation second = LeavesAllocator.allocate(first.assignments(), List.of("w:a", "w:b", "w:c"),
                Set.of("flowering_azalea_leaves"));
        assertEquals(first.active().get("w:a"), second.active().get("w:a"));
        assertEquals(new LeavesState("azalea_leaves", 0), second.active().get("w:c"));

        // Removed from the contents: its slot is kept, retired, and its block still held.
        LeavesAllocator.Allocation third = LeavesAllocator.allocate(second.assignments(), List.of("w:c"), Set.of());
        assertEquals(Set.of("w:c"), third.active().keySet());
        assertTrue(third.assignments().containsKey("w:a"));
        assertEquals(Set.of("flowering_azalea_leaves", "azalea_leaves"), third.materials());
    }

    @Test
    void aLogLiesThreeWays() {
        assertEquals("demo:log#x", BlockAxis.X.key("demo:log"));
        assertEquals(BlockAxis.Z, BlockAxis.of("demo:log#z"));
        assertEquals(BlockAxis.Y, BlockAxis.of("demo:log"));
        assertEquals("demo:log", BlockAxis.base("demo:log#x"));
        assertEquals(List.of(90, 90), List.of(BlockAxis.X.rotation()[0], BlockAxis.X.rotation()[1]));
        assertEquals(List.of(90, 0), List.of(BlockAxis.Z.rotation()[0], BlockAxis.Z.rotation()[1]));
    }
}
