package com.arkcronist.content.core.liquid;

import com.arkcronist.content.core.storage.BlockKey;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How a liquid runs over a few hand-placed blocks: the floor is y 63, everything above it open. */
class FluidSolverTest {

    private final Set<Long> solid = new HashSet<>();

    private FluidSolver.Terrain terrain() {
        return (x, y, z) -> y > 63 && !solid.contains(BlockKey.pack(x, y, z));
    }

    private static long at(int x, int y, int z) {
        return BlockKey.pack(x, y, z);
    }

    @Test
    void onFlatGroundItSpreadsInADiamondOfItsFlowDistance() {
        FluidSolver solver = new FluidSolver(3, 16, -64, 320);
        Map<Long, FluidSolver.Cell> cells = solver.solve(List.of(at(0, 64, 0)), terrain());

        // Strength 3 at the source, 2 and 1 beyond: everything within two steps.
        assertEquals(1 + 4 + 8, cells.size());
        assertTrue(cells.get(at(0, 64, 0)).source());
        assertEquals(2, cells.get(at(1, 64, 0)).strength());
        assertEquals(1, cells.get(at(1, 64, 1)).strength());
        assertEquals(2, cells.get(at(0, 64, -2)).depth());
        assertFalse(cells.containsKey(at(3, 64, 0)));
        assertFalse(cells.containsKey(at(2, 64, 1)), "three steps away");
    }

    @Test
    void wallsStopItAndItFlowsAroundThem() {
        // A wall along x = 1, open at z = 3.
        for (int z = -3; z <= 2; z++) {
            solid.add(at(1, 64, z));
        }
        FluidSolver solver = new FluidSolver(6, 16, -64, 320);
        Map<Long, FluidSolver.Cell> cells = solver.solve(List.of(at(0, 64, 0)), terrain());

        assertFalse(cells.containsKey(at(1, 64, 0)));
        // Around the end of the wall: 0,0 -> 0,3 -> 1,3 -> 2,3 is five steps.
        assertTrue(cells.containsKey(at(2, 64, 3)));
        assertEquals(5, cells.get(at(2, 64, 3)).depth());
        assertEquals(1, cells.get(at(2, 64, 3)).strength());
        assertFalse(cells.containsKey(at(2, 64, 2)), "six steps round, and strength runs out at five");
    }

    @Test
    void overAnEdgeItFallsStraightDownAndSpreadsAfreshWhereItLands() {
        // A ledge: the source sits on a block at y 66, with open air down to the floor.
        solid.add(at(0, 65, 0));
        FluidSolver solver = new FluidSolver(2, 16, -64, 320);
        Map<Long, FluidSolver.Cell> cells = solver.solve(List.of(at(0, 66, 0)), terrain());

        // From the source one step east, over the edge, down two blocks to the floor...
        assertTrue(cells.containsKey(at(1, 66, 0)));
        assertTrue(cells.containsKey(at(1, 65, 0)));
        assertTrue(cells.containsKey(at(1, 64, 0)));
        // ...falling, it does not spread sideways...
        assertFalse(cells.containsKey(at(1, 65, 1)));
        // ...and where it lands it runs out again at full strength.
        assertEquals(2, cells.get(at(1, 64, 0)).strength());
        assertTrue(cells.containsKey(at(2, 64, 0)));
    }

    @Test
    void aSourceOverAirOnlyFallsAndAFallIsCappedAtMaxFall() {
        FluidSolver capped = new FluidSolver(4, 3, -64, 320);
        Map<Long, FluidSolver.Cell> cells = capped.solve(List.of(at(0, 80, 0)), terrain());

        assertEquals(Set.of(at(0, 80, 0), at(0, 79, 0), at(0, 78, 0), at(0, 77, 0)), cells.keySet());
    }

    @Test
    void twoSourcesShareTheirFlowAndEachBlockKeepsItsNearestSource() {
        FluidSolver solver = new FluidSolver(3, 16, -64, 320);
        long west = at(0, 64, 0);
        long east = at(4, 64, 0);
        Map<Long, FluidSolver.Cell> cells = solver.solve(List.of(west, east), terrain());

        assertEquals(west, cells.get(at(1, 64, 0)).origin());
        assertEquals(east, cells.get(at(3, 64, 0)).origin());
        assertEquals(3, cells.get(at(2, 64, 0)).depth() + 1, "two steps from either");
        assertTrue(cells.get(east).source());
    }

    @Test
    void theChangesPourNearestFirstAndDrainOnlyWhereTheSolveSpeaks() {
        FluidSolver solver = new FluidSolver(3, 16, -64, 320);
        Map<Long, FluidSolver.Cell> wanted = solver.solve(List.of(at(0, 64, 0)), terrain());
        // Already there: one block it still wants, one it no longer does, and one outside its say.
        Set<Long> present = Set.of(at(1, 64, 0), at(9, 64, 9), at(30, 64, 30));

        List<FluidSolver.Change> changes = FluidSolver.changes(present, wanted, key -> BlockKey.x(key) < 20);

        List<FluidSolver.Change> drains = changes.stream()
                .filter(change -> change.kind() == FluidSolver.Change.Kind.DRAIN).toList();
        assertEquals(List.of(at(9, 64, 9)), drains.stream().map(FluidSolver.Change::key).toList());
        List<FluidSolver.Change> flows = changes.stream()
                .filter(change -> change.kind() == FluidSolver.Change.Kind.FLOW).toList();
        assertEquals(wanted.size() - 2, flows.size(), "everything but the source and what is there");
        for (int i = 1; i < flows.size(); i++) {
            assertTrue(flows.get(i).depth() >= flows.get(i - 1).depth());
        }
    }

    @Test
    void tripwireStatesPairUpAndKeepVanillaForEveryOtherState() {
        // Attached states first: stepping into those can be cancelled.
        assertEquals(16, TripwireState.source(0).index());
        assertEquals(17, TripwireState.flowing(0).index());
        assertEquals(31, TripwireState.flowing(7).index());
        assertEquals(0, TripwireState.source(8).index());
        assertEquals(15, TripwireState.flowing(15).index());
        java.util.Set<Integer> used = new java.util.HashSet<>();
        for (int slot = 0; slot < TripwireState.LIQUIDS; slot++) {
            assertTrue(used.add(TripwireState.source(slot).index()));
            assertTrue(used.add(TripwireState.flowing(slot).index()));
        }
        assertEquals(32, used.size(), "every slot its own two states");
        assertEquals(TripwireState.fromIndex(21), new TripwireState(true, false, true, false, true));
        assertEquals("minecraft:tripwire[attached=true,disarmed=true,east=true,north=false,powered=false,south=false,"
                + "west=true]", TripwireState.fromIndex(21).blockData());

        var models = Map.of(TripwireState.source(0), new com.arkcronist.content.core.definition.ResourceLocation(
                "demo", "block/acid_source"));
        var blockstate = LiquidModels.blockstate(models).getAsJsonObject("variants");
        assertEquals(128, blockstate.size());
        assertEquals("demo:block/acid_source", blockstate.getAsJsonObject(
                TripwireState.source(0).variantKey(true, false)).get("model").getAsString());
        // The same connections armed, or powered, are a string as vanilla draws it.
        assertEquals("minecraft:block/tripwire_attached_ns", blockstate.getAsJsonObject(
                TripwireState.source(0).variantKey(false, false)).get("model").getAsString());
        assertEquals("minecraft:block/tripwire_attached_ns", blockstate.getAsJsonObject(
                TripwireState.source(0).variantKey(true, true)).get("model").getAsString());
        // A free state no liquid uses keeps vanilla's look and rotation.
        var free = blockstate.getAsJsonObject(TripwireState.fromIndex(8 + 1).variantKey(true, false));
        assertEquals("minecraft:block/tripwire_ne", free.get("model").getAsString());
        assertEquals(270, free.get("y").getAsInt());
    }
}
