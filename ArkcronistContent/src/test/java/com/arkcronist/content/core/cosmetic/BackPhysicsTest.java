package com.arkcronist.content.core.cosmetic;

import com.arkcronist.content.core.cosmetic.BackPhysics.Pose;
import com.arkcronist.content.core.cosmetic.BackPhysics.Sample;
import com.arkcronist.content.core.definition.Placement;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 3.0: a backpack's swing - driven by its wearer's acceleration, damped, bounded. */
class BackPhysicsTest {

    private static final long TICK = 50_000_000L;
    private static final double MAX = Math.toRadians(CosmeticDefinition.Physics.DEFAULT.maxAngle());

    /** A wearer facing south (+z) moving along z at {@code speed} m/s from tick {@code from}. */
    private static double[] run(BackPhysics physics, double z, int ticks, double speed, long[] clock, float yaw,
                                double[] extremes) {
        for (int i = 0; i < ticks; i++) {
            z += speed * 0.05;
            clock[0] += TICK;
            Pose pose = physics.step(new Sample(0, 64, z, yaw, false, clock[0]), clock[0]);
            extremes[0] = Math.max(extremes[0], pose.swing());
            extremes[1] = Math.min(extremes[1], pose.swing());
        }
        return new double[]{z};
    }

    @Test
    void aWearerStandingStillLeavesItAtRest() {
        BackPhysics physics = new BackPhysics(CosmeticDefinition.Physics.DEFAULT);
        long[] clock = {0};
        double[] extremes = {0, 0};
        run(physics, 0, 100, 0, clock, 0, extremes);
        assertEquals(Pose.REST, physics.pose());
    }

    /** Off at a sprint, it swings out behind; at a steady pace it settles; never past its limit. */
    @Test
    void startingToRunSwingsItOutBehindThenItSettles() {
        BackPhysics physics = new BackPhysics(CosmeticDefinition.Physics.DEFAULT);
        long[] clock = {0};
        double[] extremes = {0, 0};
        physics.step(new Sample(0, 64, 0, 0, false, 0), 0);
        run(physics, 0, 6, 5.6, clock, 0, extremes);
        assertTrue(extremes[0] > Math.toRadians(8), "swung out " + Math.toDegrees(extremes[0]) + " degrees");
        assertTrue(extremes[0] <= MAX + 1e-9);
        run(physics, 6 * 0.28, 80, 5.6, clock, 0, extremes);
        assertTrue(Math.abs(physics.pose().swing()) < Math.toRadians(0.5), "settled at a steady pace");
    }

    /** Stopping throws it against the back: it can swing forward only a little, and bounces off. */
    @Test
    void stoppingSwingsItIntoTheBackNoFurtherThanTheBackAllows() {
        BackPhysics physics = new BackPhysics(CosmeticDefinition.Physics.DEFAULT);
        long[] clock = {0};
        double[] extremes = {0, 0};
        physics.step(new Sample(0, 64, 0, 0, false, 0), 0);
        double z = run(physics, 0, 80, 5.6, clock, 0, extremes)[0];
        extremes[0] = 0;
        extremes[1] = 0;
        run(physics, z, 30, 0, clock, 0, extremes);
        assertTrue(extremes[1] < -Math.toRadians(2), "swung forward " + Math.toDegrees(extremes[1]));
        assertTrue(extremes[1] >= -BackPhysics.MAX_FORWARD - 1e-9, "but not into the back");
    }

    @Test
    void movingOffToTheRightSwingsItToTheLeft() {
        BackPhysics physics = new BackPhysics(CosmeticDefinition.Physics.DEFAULT);
        long t = 0;
        physics.step(new Sample(0, 64, 0, 0, false, 0), 0);
        double x = 0;
        double lowest = 0;
        for (int i = 0; i < 6; i++) {
            // Facing south, the wearer's right is -x.
            x -= 4 * 0.05;
            t += TICK;
            lowest = Math.min(lowest, physics.step(new Sample(x, 64, 0, 0, false, t), t).roll());
        }
        assertTrue(lowest < -Math.toRadians(5), "rolled " + Math.toDegrees(lowest));
    }

    /** Landing from a fall sags it, and it springs back up within a second. */
    @Test
    void landingMakesItSagAndSpringBack() {
        BackPhysics physics = new BackPhysics(CosmeticDefinition.Physics.DEFAULT);
        long t = 0;
        double y = 80;
        physics.step(new Sample(0, y, 0, 0, false, 0), 0);
        for (int i = 0; i < 10; i++) {
            y -= 1.0 * (i + 1) * 0.05 * 5;
            t += TICK;
            physics.step(new Sample(0, y, 0, 0, false, t), t);
        }
        double deepest = 0;
        for (int i = 0; i < 20; i++) {
            t += TICK;
            deepest = Math.max(deepest, physics.step(new Sample(0, y, 0, 0, false, t), t).drop());
        }
        assertTrue(deepest > 0.02, "sagged " + deepest);
        assertTrue(deepest <= BackPhysics.MAX_DROP + 1e-9);
        for (int i = 0; i < 40; i++) {
            t += TICK;
            physics.step(new Sample(0, y, 0, 0, false, t), t);
        }
        assertTrue(Math.abs(physics.pose().drop()) < 0.005, "back up: " + physics.pose().drop());
    }

    @Test
    void aTeleportIsNotMovement() {
        BackPhysics physics = new BackPhysics(CosmeticDefinition.Physics.DEFAULT);
        physics.step(new Sample(0, 64, 0, 0, false, 0), 0);
        physics.step(new Sample(5000, 120, -3000, 0, false, TICK), TICK);
        Pose pose = physics.step(new Sample(5000, 120, -3000, 0, false, TICK), 2 * TICK);
        assertEquals(0, pose.swing(), 1e-12);
        assertEquals(0, pose.drop(), 1e-12);
    }

    /** The same sample seen again is not the wearer stopping - until it is old enough to mean that. */
    @Test
    void aRepeatedSampleIsNotAStopUntilItIsStale() {
        BackPhysics steady = new BackPhysics(CosmeticDefinition.Physics.DEFAULT);
        BackPhysics repeated = new BackPhysics(CosmeticDefinition.Physics.DEFAULT);
        long t = 0;
        double z = 0;
        steady.step(new Sample(0, 64, 0, 0, false, 0), 0);
        repeated.step(new Sample(0, 64, 0, 0, false, 0), 0);
        for (int i = 0; i < 60; i++) {
            z += 0.2;
            t += TICK;
            Sample sample = new Sample(0, 64, z, 0, false, t);
            steady.step(sample, t);
            repeated.step(sample, t);
            repeated.step(sample, t + TICK / 2);
        }
        assertEquals(steady.pose().swing(), repeated.pose().swing(), Math.toRadians(1));
        Pose stopped = null;
        for (int i = 1; i <= 6; i++) {
            stopped = repeated.step(new Sample(0, 64, z, 0, false, t), t + i * TICK);
        }
        assertTrue(stopped.swing() < 0, "a sample 300 ms old is a stop");
    }

    @Test
    void sneakingTiltsItWithTheLean() {
        BackPhysics physics = new BackPhysics(CosmeticDefinition.Physics.DEFAULT);
        long t = 0;
        for (int i = 0; i <= 60; i++) {
            physics.step(new Sample(0, 64, 0, 0, true, t), t);
            t += TICK;
        }
        assertEquals(BackPhysics.SNEAK_TILT, physics.pose().swing(), 0.01);
    }

    @Test
    void aStiffSpringOverALongGapStaysFinite() {
        BackPhysics physics = new BackPhysics(new CosmeticDefinition.Physics(400, 0, 4, 80, 1));
        physics.step(new Sample(0, 64, 0, 0, false, 0), 0);
        long t = 0;
        double z = 0;
        for (int i = 0; i < 200; i++) {
            z += (i % 2 == 0 ? 1 : -1) * 0.4;
            t += 250_000_000L;
            Pose pose = physics.step(new Sample(0, 64, z, i * 37f, false, t), t);
            assertTrue(Double.isFinite(pose.swing()) && Math.abs(pose.swing()) <= Math.toRadians(80) + 1e-9);
            assertTrue(Math.abs(pose.drop()) <= BackPhysics.MAX_DROP + 1e-9);
        }
    }

    /** At rest the bag sits behind the wearer - behind is east for one facing west - and a swing lifts it out. */
    @Test
    void thePoseFollowsTheBodysYawAndTheSwing() {
        CosmeticDefinition satchel = new CosmeticDefinition(CosmeticDefinition.Slot.BACK,
                new Placement.Vec3(0, -0.35f, 0.25f), 1, CosmeticDefinition.Physics.DEFAULT);
        BackPose.Transform south = BackPose.of(satchel, Pose.REST, 0, false);
        assertEquals(new Vector3f(0, BackPose.SHOULDERS - 0.35f, -0.25f).distance(south.translation()), 0, 1e-5);
        BackPose.Transform west = BackPose.of(satchel, Pose.REST, 90, false);
        assertEquals(0.25f, west.translation().x(), 1e-5, "facing west, behind is +x");
        assertEquals(0, west.translation().z(), 1e-5);
        BackPose.Transform swung = BackPose.of(satchel, new Pose(Math.toRadians(25), 0, 0, 0), 0, false);
        assertTrue(swung.translation().z() < south.translation().z() - 0.05, "swung out behind");
        assertTrue(swung.translation().y() > south.translation().y(), "and up, about the shoulders");
        BackPose.Transform sagging = BackPose.of(satchel, new Pose(0, 0, 0, 0.1), 0, false);
        assertEquals(south.translation().y() - 0.1f, sagging.translation().y(), 1e-5);
    }
}
