package com.arkcronist.content.core.furniture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** 1.9.5: a particle's offset turns with its piece, the same way the piece's blocks do. */
class PieceAxesTest {

    private static final double EPSILON = 1e-9;

    @Test
    void facingSouthForwardIsSouthAndAcrossIsEast() {
        assertArrayEquals(new double[] {0, 0, 1}, PieceAxes.offset(0, 0, 0, 1), EPSILON);
        assertArrayEquals(new double[] {1, 0, 0}, PieceAxes.offset(0, 1, 0, 0), EPSILON);
        assertArrayEquals(new double[] {0, 1.4, 0}, PieceAxes.offset(0, 0, 1.4, 0), EPSILON);
    }

    @Test
    void eachQuarterTurnTurnsTheOffset() {
        // Facing west, north and east: forward is -x, -z, +x.
        assertArrayEquals(new double[] {-1, 0, 0}, PieceAxes.offset(90, 0, 0, 1), EPSILON);
        assertArrayEquals(new double[] {0, 0, -1}, PieceAxes.offset(180, 0, 0, 1), EPSILON);
        assertArrayEquals(new double[] {1, 0, 0}, PieceAxes.offset(270, 0, 0, 1), EPSILON);
    }

    /** "across" is the right hand Footprint puts a wide piece's other blocks on. */
    @Test
    void acrossMatchesTheFootprintsRightHand() {
        for (float yaw : new float[] {0, 90, 180, 270, -90, 450}) {
            int[] right = Footprint.right(yaw);
            double[] across = PieceAxes.offset(yaw, 1, 0, 0);
            assertEquals(right[0], across[0], EPSILON, "yaw " + yaw);
            assertEquals(right[1], across[2], EPSILON, "yaw " + yaw);
        }
    }
}
