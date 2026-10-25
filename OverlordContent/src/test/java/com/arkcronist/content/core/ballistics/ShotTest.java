package com.arkcronist.content.core.ballistics;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hitboxes, heads, walls, piercing, spread and falloff - what decides who a shot hurts and how much. */
class ShotTest {

    private static final double DELTA = 1e-9;

    /** A player-sized hitbox standing at x, z: 0.6 wide, 1.8 tall, eyes at 1.62. */
    private static RayCaster.Target<String> player(String name, double x, double z) {
        Aabb body = new Aabb(x - 0.3, 64, z - 0.3, x + 0.3, 65.8, z + 0.3);
        return new RayCaster.Target<>(name, body, body.above(64 + 1.62 - 0.25));
    }

    private static Ray look(Vec3 eye, Vec3 at) {
        return new Ray(eye, at.subtract(eye));
    }

    @Test
    void theNearestTargetOnTheLineIsHit() {
        Vec3 eye = new Vec3(0, 65.62, 0);
        RayCaster.Hit<String> hit = RayCaster.first(new Ray(eye, new Vec3(1, 0, 0)), 64,
                List.of(player("far", 20, 0), player("near", 10, 0), player("aside", 5, 3)));

        assertNotNull(hit);
        assertEquals("near", hit.handle());
        assertEquals(9.7, hit.distance(), DELTA);
        assertTrue(hit.headshot(), "eye level is head level");
    }

    @Test
    void aShotAtTheChestIsNoHeadshotAndOneAtTheHeadIs() {
        Vec3 eye = new Vec3(0, 65.62, 0);
        RayCaster.Target<String> target = player("zombie", 10, 0);
        RayCaster.Hit<String> chest = RayCaster.first(look(eye, new Vec3(10, 65.0, 0)), 64, List.of(target));
        RayCaster.Hit<String> head = RayCaster.first(look(eye, new Vec3(10, 65.6, 0)), 64, List.of(target));

        assertFalse(chest.headshot());
        assertTrue(head.headshot());
    }

    /** Rising through the chest and out through the top of the head hit the chest. */
    @Test
    void aShotEnteringLowAndLeavingThroughTheHeadIsNoHeadshot() {
        // In through the side at y 65.0, out through the top at x 10.23.
        Vec3 eye = new Vec3(9.0, 63.95, 0);
        RayCaster.Hit<String> hit = RayCaster.first(look(eye, new Vec3(10.0, 65.45, 0)), 64,
                List.of(player("tall", 10, 0)));

        assertNotNull(hit);
        assertFalse(hit.headshot());
    }

    @Test
    void aWallInBetweenStopsTheShot() {
        Vec3 eye = new Vec3(0.5, 65.62, 0.5);
        Ray ray = new Ray(eye, new Vec3(1, 0, 0));
        List<Aabb> wall = List.of(Aabb.block(5, 65, 0));

        RayCaster.Trace<String> trace = RayCaster.trace(ray, 64, wall, List.of(player("behind", 10.5, 0.5)), 0);

        assertTrue(trace.hits().isEmpty());
        assertTrue(trace.blocked());
        assertEquals(4.5, trace.distance(), DELTA);
    }

    /** A slab is half a block: a shot over it goes on. */
    @Test
    void partOfABlockOnlyStopsWhatPassesThroughIt() {
        Vec3 eye = new Vec3(0.5, 65.62, 0.5);
        Ray ray = new Ray(eye, new Vec3(1, 0, 0));
        List<Aabb> slab = List.of(new Aabb(5, 65, 0, 6, 65.5, 1));

        RayCaster.Trace<String> trace = RayCaster.trace(ray, 64, slab, List.of(player("behind", 10.5, 0.5)), 0);

        assertEquals(1, trace.hits().size());
        assertFalse(trace.blocked());
    }

    @Test
    void piercingGoesThroughThatManyTargetsAndEndsInTheLast() {
        Vec3 eye = new Vec3(0, 65.0, 0);
        Ray ray = new Ray(eye, new Vec3(1, 0, 0));
        List<RayCaster.Target<String>> line = List.of(player("a", 3, 0), player("b", 6, 0), player("c", 9, 0));

        RayCaster.Trace<String> once = RayCaster.trace(ray, 64, List.of(), line, 0);
        RayCaster.Trace<String> twice = RayCaster.trace(ray, 64, List.of(), line, 1);
        RayCaster.Trace<String> all = RayCaster.trace(ray, 64, List.of(), line, 5);

        assertEquals(List.of("a"), once.hits().stream().map(RayCaster.Hit::handle).toList());
        assertEquals(2.7, once.distance(), DELTA);
        assertEquals(List.of("a", "b"), twice.hits().stream().map(RayCaster.Hit::handle).toList());
        assertEquals(5.7, twice.distance(), DELTA);
        assertEquals(3, all.hits().size());
        assertEquals(64, all.distance(), DELTA, "piercing left over: it flies on");
    }

    @Test
    void spreadStaysInsideItsConeAndCoversIt() {
        Vec3 forward = Vec3.fromRotation(30, -10);
        List<Vec3> pellets = Spread.directions(forward, 6, 2000, new SplittableRandom(42));

        double widest = 0;
        int outer = 0;
        for (Vec3 pellet : pellets) {
            assertEquals(1, pellet.length(), 1e-9);
            double angle = pellet.angleTo(forward);
            assertTrue(angle <= 6 + 1e-6, "angle " + angle);
            widest = Math.max(widest, angle);
            if (angle > 6 / Math.sqrt(2)) {
                outer++;
            }
        }
        assertTrue(widest > 5.8, "reaches the edge of the cone");
        // Even over the opening: the outer ring (half the area) holds about half the pellets.
        assertTrue(outer > 850 && outer < 1150, "outer ring holds " + outer);
    }

    @Test
    void noSpreadSendsEveryPelletStraightAndASeedReplaysAShot() {
        Vec3 forward = new Vec3(0, 0, 1);
        assertEquals(List.of(forward, forward, forward), Spread.directions(forward, 0, 3, new SplittableRandom(1)));
        assertEquals(Spread.directions(forward, 4, 5, new SplittableRandom(9)),
                Spread.directions(forward, 4, 5, new SplittableRandom(9)));
        // Straight up has no "up" of its own to open the cone across.
        Spread.directions(new Vec3(0, 1, 0), 3, 10, new SplittableRandom(3))
                .forEach(pellet -> assertTrue(pellet.angleTo(new Vec3(0, 1, 0)) <= 3 + 1e-6));
    }

    @Test
    void minecraftsRotationsPointWhereThePlayerLooks() {
        assertVector(new Vec3(0, 0, 1), Vec3.fromRotation(0, 0));
        assertVector(new Vec3(-1, 0, 0), Vec3.fromRotation(90, 0));
        assertVector(new Vec3(0, 0, -1), Vec3.fromRotation(180, 0));
        assertVector(new Vec3(0, 1, 0), Vec3.fromRotation(0, -90));
    }

    @Test
    void damageFadesInAStraightLineFromStartToRange() {
        DamageFalloff falloff = new DamageFalloff(20, 60, 0.25);
        assertEquals(1, falloff.factor(5), DELTA);
        assertEquals(1, falloff.factor(20), DELTA);
        assertEquals(0.625, falloff.factor(40), DELTA);
        assertEquals(0.25, falloff.factor(60), DELTA);
        assertEquals(0.25, falloff.factor(90), DELTA);
        assertEquals(6 * 0.625 * 2, falloff.damage(6, 40, true, 2), DELTA);
    }

    /** Eight pellets into one target are one hit of their total, not eight hits lost to invulnerability. */
    @Test
    void aShotAddsUpEveryPelletPerTarget() {
        Vec3 eye = new Vec3(0, 65.0, 0);
        List<Vec3> pellets = List.of(new Vec3(1, 0, 0), new Vec3(1, 0.01, 0), new Vec3(1, 0.06, 0), new Vec3(0, 0, 1));
        Shot<String> shot = new Shot<>(eye, pellets, 30, List.of(), List.of(player("zombie", 8, 0)), 0, 2.0, 1.5,
                new DamageFalloff(30, 30, 1));

        Shot.Outcome<String> outcome = shot.resolve();

        Map<String, Shot.Damage> damage = outcome.damage();
        assertEquals(1, damage.size());
        Shot.Damage zombie = damage.get("zombie");
        // Two body pellets at 2, one into the head at 2 * 1.5; the fourth flew off to the south.
        assertEquals(3, zombie.pellets());
        assertTrue(zombie.headshot());
        assertEquals(2 + 2 + 3, zombie.amount(), DELTA);
        assertEquals(4, outcome.paths().size());
        assertEquals(30, outcome.paths().get(3).to().distance(eye), DELTA);
        assertNull(RayCaster.first(new Ray(eye, new Vec3(0, 0, 1)), 30, List.of(player("zombie", 8, 0))));
    }

    private static void assertVector(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x(), actual.x(), 1e-9);
        assertEquals(expected.y(), actual.y(), 1e-9);
        assertEquals(expected.z(), actual.z(), 1e-9);
    }
}
