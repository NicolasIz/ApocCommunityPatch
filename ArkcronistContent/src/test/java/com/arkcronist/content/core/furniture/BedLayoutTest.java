package com.arkcronist.content.core.furniture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** A two-block bed: its halves, its display, and where the sleeper lies. */
class BedLayoutTest {

    @Test
    void eitherHalfGivesTheSameBed() {
        for (BedLayout.Facing facing : BedLayout.Facing.values()) {
            BedLayout fromFoot = BedLayout.from(10, 64, -3, BedLayout.Part.FOOT, facing);
            BedLayout fromHead = BedLayout.from(10 + facing.dx, 64, -3 + facing.dz, BedLayout.Part.HEAD, facing);
            assertEquals(fromFoot, fromHead, facing.name());
        }
    }

    @Test
    void theHeadIsOneStepTheWayTheBedPoints() {
        BedLayout bed = new BedLayout(0, 70, 0, BedLayout.Facing.EAST);
        assertEquals(1, bed.headX());
        assertEquals(0, bed.headZ());
        assertArrayEquals(new int[] {1, 70, 0}, bed.other(BedLayout.Part.FOOT));
        assertArrayEquals(new int[] {0, 70, 0}, bed.other(BedLayout.Part.HEAD));
    }

    @Test
    void theDisplayStandsBetweenTheHalvesOnTheFloor() {
        BedLayout north = new BedLayout(4, 64, 4, BedLayout.Facing.NORTH);
        assertEquals(4.5, north.displayX());
        assertEquals(4.0, north.displayZ(), "between z 4 and z 3: the shared edge");
        assertEquals(64.0, north.displayY());

        BedLayout west = new BedLayout(4, 64, 4, BedLayout.Facing.WEST);
        assertEquals(4.0, west.displayX());
        assertEquals(4.5, west.displayZ());
    }

    @Test
    void theModelFrontPointsAtTheHead() {
        // An entity at yaw 0 looks south; 90 west; 180 north; 270 east - the head end of the bed.
        assertEquals(0f, new BedLayout(0, 0, 0, BedLayout.Facing.SOUTH).yaw());
        assertEquals(90f, new BedLayout(0, 0, 0, BedLayout.Facing.WEST).yaw());
        assertEquals(180f, new BedLayout(0, 0, 0, BedLayout.Facing.NORTH).yaw());
        assertEquals(270f, new BedLayout(0, 0, 0, BedLayout.Facing.EAST).yaw());
        for (BedLayout.Facing facing : BedLayout.Facing.values()) {
            double radians = Math.toRadians(facing.yaw);
            // Minecraft's look vector for a yaw: (-sin, cos).
            assertEquals(facing.dx, -Math.sin(radians), 1e-9, facing.name());
            assertEquals(facing.dz, Math.cos(radians), 1e-9, facing.name());
        }
    }

    @Test
    void theSleeperLiesAlongTheModel() {
        for (BedLayout.Facing facing : BedLayout.Facing.values()) {
            BedLayout bed = new BedLayout(-7, 64, 12, facing);
            double[] pillow = bed.pillow();
            // Half a block from the display's centre, straight along the bed: never off to a side.
            assertEquals(facing.dx * 0.5, pillow[0] - bed.displayX(), 1e-9, facing.name());
            assertEquals(facing.dz * 0.5, pillow[2] - bed.displayZ(), 1e-9, facing.name());
            assertEquals(64 + 9 / 16.0, pillow[1], 1e-9);
        }
    }

    @Test
    void oppositesAreOpposite() {
        for (BedLayout.Facing facing : BedLayout.Facing.values()) {
            assertEquals(-facing.dx, facing.opposite().dx);
            assertEquals(-facing.dz, facing.opposite().dz);
        }
    }
}
