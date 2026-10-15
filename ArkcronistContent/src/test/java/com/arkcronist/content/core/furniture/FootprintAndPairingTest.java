package com.arkcronist.content.core.furniture;

import com.arkcronist.content.core.furniture.BedLayout.Facing;
import com.arkcronist.content.core.furniture.ChestPairing.Half;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pieces bigger than a block, and chests joined as vanilla joins them. */
class FootprintAndPairingTest {

    private static List<String> offsets(Footprint footprint, float yaw) {
        return footprint.offsets(yaw).stream().map(Arrays::toString).toList();
    }

    @Test
    void aQuestBoardCoversThreeAcrossItsFrontAndTwoUp() {
        Footprint board = new Footprint(3, 2);
        assertEquals(6, board.blocks());
        // Facing south: across is east-west, centred on the block it was placed on.
        assertEquals(List.of("[0, 0, 0]", "[-1, 0, 0]", "[1, 0, 0]", "[-1, 1, 0]", "[0, 1, 0]", "[1, 1, 0]"),
                offsets(board, 0));
        // Facing west: across is north-south.
        assertEquals(List.of("[0, 0, 0]", "[0, 0, -1]", "[0, 0, 1]", "[0, 1, -1]", "[0, 1, 0]", "[0, 1, 1]"),
                offsets(board, 90));
        assertArrayEquals(new double[] {0, 0}, board.centreShift(0), 1e-9);
        // A yaw a little off a quarter turn snaps to it.
        assertEquals(offsets(board, 180), offsets(board, 183));
    }

    @Test
    void anEvenWidthGrowsToTheRightOfSomeoneLookingAtIt() {
        Footprint wardrobe = new Footprint(2, 1);
        // Facing south, the viewer looks north and their right is east (+x).
        assertEquals(List.of("[0, 0, 0]", "[1, 0, 0]"), offsets(wardrobe, 0));
        assertArrayEquals(new double[] {0.5, 0}, wardrobe.centreShift(0), 1e-9);
        // Facing north, their right is west.
        assertEquals(List.of("[0, 0, 0]", "[-1, 0, 0]"), offsets(wardrobe, 180));
        assertArrayEquals(new double[] {-0.5, 0}, wardrobe.centreShift(180), 1e-9);
        // Facing east, their right is north.
        assertEquals(List.of("[0, 0, 0]", "[0, 0, -1]"), offsets(wardrobe, 270));
        assertTrue(Footprint.SINGLE.single());
        assertFalse(wardrobe.single());
        assertThrows(IllegalArgumentException.class, () -> new Footprint(7, 1));
    }

    /**
     * 3.1: a well six blocks square runs back from the row it was placed on - away from whoever
     * placed it - and its model is centred on all six rows.
     */
    @Test
    void aDeepFootprintRunsBackFromTheFrontRow() {
        Footprint well = new Footprint(6, 2, 6);
        assertEquals(72, well.blocks());
        assertEquals(72, well.offsets(0).stream().map(Arrays::toString).distinct().count());
        // Facing south, back is north (-z): every block is at z 0 or less, from -5 to 0.
        assertTrue(well.offsets(0).stream().allMatch(o -> o[2] <= 0 && o[2] >= -5));
        assertTrue(well.offsets(0).stream().allMatch(o -> o[0] >= -2 && o[0] <= 3));
        // Half a block right for the even width, two and a half back.
        assertArrayEquals(new double[] {0.5, -2.5}, well.centreShift(0), 1e-9);
        // Facing west, back is east.
        assertArrayEquals(new double[] {2.5, 0.5}, well.centreShift(90), 1e-9);
        assertEquals(new Footprint(3, 2), new Footprint(3, 2, 1));
        assertThrows(IllegalArgumentException.class, () -> new Footprint(1, 1, 7));
    }

    @Test
    void chestHalvesAreNamedAndOrderedAsVanillaDoesIt() {
        // Vanilla's ChestBlock.getConnectedDirection.
        assertEquals(Facing.EAST, ChestPairing.partnerDirection(Facing.NORTH, Half.LEFT));
        assertEquals(Facing.WEST, ChestPairing.partnerDirection(Facing.NORTH, Half.RIGHT));
        assertEquals(Facing.SOUTH, ChestPairing.partnerDirection(Facing.EAST, Half.LEFT));
        assertEquals(Facing.WEST, ChestPairing.partnerDirection(Facing.SOUTH, Half.LEFT));
        assertEquals(Facing.NORTH, ChestPairing.partnerDirection(Facing.WEST, Half.LEFT));
        for (Facing facing : Facing.values()) {
            for (Half half : Half.values()) {
                assertEquals(half, ChestPairing.halfFor(facing, ChestPairing.partnerDirection(facing, half)));
            }
            assertEquals(2, ChestPairing.sides(facing).size());
            assertFalse(ChestPairing.sides(facing).contains(facing), "never in front");
            assertFalse(ChestPairing.sides(facing).contains(facing.opposite()), "never behind");
        }
        // A chest facing north with its partner to the west is the RIGHT half: rows 1-3.
        assertTrue(ChestPairing.firstIs(Facing.NORTH, Facing.WEST));
        assertFalse(ChestPairing.firstIs(Facing.NORTH, Facing.EAST));
        assertThrows(IllegalArgumentException.class, () -> ChestPairing.halfFor(Facing.NORTH, Facing.SOUTH));
    }
}
