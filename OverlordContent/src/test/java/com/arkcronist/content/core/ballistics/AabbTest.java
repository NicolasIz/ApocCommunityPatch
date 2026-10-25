package com.arkcronist.content.core.ballistics;

import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The slab test every hit comes down to. */
class AabbTest {

    private static final double DELTA = 1e-9;
    private static final Aabb UNIT = new Aabb(0, 0, 0, 1, 1, 1);

    @Test
    void aRayStraightAtTheBoxEntersAtItsNearFace() {
        Ray ray = new Ray(new Vec3(-5, 0.5, 0.5), new Vec3(1, 0, 0));
        assertEquals(5, UNIT.intersect(ray, 100).getAsDouble(), DELTA);
    }

    @Test
    void aRayFromInsideHitsAtOnce() {
        Ray ray = new Ray(new Vec3(0.5, 0.5, 0.5), new Vec3(0.3, -1, 0.2));
        assertEquals(0, UNIT.intersect(ray, 100).getAsDouble(), DELTA);
    }

    @Test
    void aBoxBehindTheRayIsNotHit() {
        Ray ray = new Ray(new Vec3(5, 0.5, 0.5), new Vec3(1, 0, 0));
        assertTrue(UNIT.intersect(ray, 100).isEmpty());
    }

    @Test
    void aBoxBeyondTheRangeIsNotHit() {
        Ray ray = new Ray(new Vec3(-5, 0.5, 0.5), new Vec3(1, 0, 0));
        assertTrue(UNIT.intersect(ray, 4.99).isEmpty());
        assertTrue(UNIT.intersect(ray, 5).isPresent());
    }

    /** A direction with a zero component must not divide its way into NaN. */
    @Test
    void aRayParallelToAFaceHitsOnlyFromBetweenItsPlanes() {
        Ray inside = new Ray(new Vec3(-2, 0.5, 0.5), new Vec3(1, 0, 0));
        Ray above = new Ray(new Vec3(-2, 1.5, 0.5), new Vec3(1, 0, 0));
        Ray grazing = new Ray(new Vec3(-2, 1.0, 0.5), new Vec3(1, 0, 0));
        assertEquals(2, UNIT.intersect(inside, 10).getAsDouble(), DELTA);
        assertTrue(UNIT.intersect(above, 10).isEmpty());
        // Exactly along the top face still touches it.
        assertEquals(2, UNIT.intersect(grazing, 10).getAsDouble(), DELTA);
    }

    @Test
    void aDiagonalRayEntersAtTheLatestOfItsThreeEntries() {
        Aabb box = new Aabb(2, 2, 2, 3, 3, 3);
        Ray ray = new Ray(Vec3.ZERO, new Vec3(1, 1, 1));
        // Enters the corner at (2,2,2): 2*sqrt(3) along the ray.
        assertEquals(2 * Math.sqrt(3), box.intersect(ray, 100).getAsDouble(), DELTA);

        Ray miss = new Ray(Vec3.ZERO, new Vec3(1, 1, 0.2));
        assertTrue(box.intersect(miss, 100).isEmpty());
    }

    @Test
    void aRayPassingTheCornerMisses() {
        // Crosses x=0..1 while z is already past the box.
        Ray ray = new Ray(new Vec3(-1, 0.5, 1.5), new Vec3(1, 0, 0.1));
        OptionalDouble distance = UNIT.intersect(ray, 100);
        assertTrue(distance.isEmpty());
    }

    @Test
    void cornersAreSortedAndHeadsSitOnTop() {
        Aabb box = new Aabb(1, 2, 3, 0, 0, 0);
        assertEquals(new Aabb(0, 0, 0, 1, 2, 3), box);
        assertEquals(new Aabb(0, 1.5, 0, 1, 2, 3), box.above(1.5));
        // A head line above the box still leaves a box, flat at the top.
        assertEquals(2, box.above(9).minY(), DELTA);
        assertTrue(Aabb.block(4, -2, 7).isFullBlock());
        assertFalse(new Aabb(4, -2, 7, 5, -1.5, 8).isFullBlock());
        assertTrue(box.contains(new Vec3(0.5, 1, 1)));
        assertTrue(box.intersects(new Aabb(0.9, 1.9, 2.9, 5, 5, 5)));
        assertFalse(box.intersects(new Aabb(1.1, 0, 0, 5, 5, 5)));
    }
}
