package com.arkcronist.content.core.vehicle;

import com.arkcronist.content.core.ballistics.Aabb;
import com.arkcronist.content.core.vehicle.VehicleDefinition.Offset;
import com.arkcronist.content.core.vehicle.VehiclePhysics.Input;
import com.arkcronist.content.core.vehicle.VehiclePhysics.State;
import com.arkcronist.content.core.vehicle.VehiclePhysics.Terrain;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 4.0: driving, turning, walls, steps, falls and water; 5.0: leaning with the ground, and other vehicles. */
class VehiclePhysicsTest {

    private static final double STEP = 0.05;
    private static final VehicleDefinition CART = new VehicleDefinition(8, 3, 6, 16, 3, 90, 0.6, 1.2, 2.0, 1.0, 0.35,
            List.of(new Offset(0, 0.6, 0)), List.of());
    private static final Input FORWARD = new Input(true, false, false, false, false);
    private static final Input BACKWARD = new Input(false, true, false, false, false);
    private static final Input BRAKE = new Input(false, false, false, false, true);
    private static final Input FORWARD_RIGHT = new Input(true, false, false, true, false);

    /** Flat ground: the top of y = -1 under everything from -50 to 50. */
    private static List<Aabb> ground() {
        List<Aabb> boxes = new ArrayList<>();
        boxes.add(new Aabb(-50, -1, -50, 50, 0, 50));
        return boxes;
    }

    private static State run(State state, Input input, Terrain terrain, int steps) {
        for (int i = 0; i < steps; i++) {
            state = VehiclePhysics.step(CART, state, input, terrain, STEP);
        }
        return state;
    }

    @Test
    void itSettlesOnTheGroundAndThenRests() {
        State state = run(State.parked(0, 0, 0, 0), Input.NONE, new Terrain(ground(), List.of()), 2);
        assertTrue(state.onGround());
        assertTrue(state.atRest());
        assertEquals(0, state.y(), 1e-9);
    }

    @Test
    void throttleReachesTopSpeedAndNoMoreThenCoastsToAStop() {
        Terrain flat = new Terrain(ground(), List.of());
        State state = run(State.parked(0, 0, 0, 0), FORWARD, flat, 60);
        assertEquals(8, state.speed(), 1e-9, "6 b/s² for 3 s is 18; the top is 8");
        assertTrue(state.z() > 15, "yaw 0 drives south, +z: " + state.z());
        assertEquals(0, state.x(), 1e-9);
        State coasting = run(state, Input.NONE, flat, 40);
        assertTrue(coasting.speed() > 0 && coasting.speed() < 8, "coasting at 3 b/s² for 2 s: " + coasting.speed());
        State stopped = run(state, Input.NONE, flat, 80);
        assertEquals(0, stopped.speed(), 1e-9);
        State braked = run(state, BRAKE, flat, 12);
        assertEquals(0, braked.speed(), 1e-9, "16 b/s² stops 8 b/s in half a second");
    }

    @Test
    void backwardBrakesFirstThenReversesUpToItsOwnTopSpeed() {
        Terrain flat = new Terrain(ground(), List.of());
        State moving = run(State.parked(0, 0, 0, 0), FORWARD, flat, 40);
        State braking = VehiclePhysics.step(CART, moving, BACKWARD, flat, STEP);
        assertEquals(moving.speed() - 16 * STEP, braking.speed(), 1e-9, "backward while moving forward brakes");
        State reversing = run(moving, BACKWARD, flat, 80);
        assertEquals(-3, reversing.speed(), 1e-9);
    }

    @Test
    void rightTurnsRaiseTheYawForwardAndLowerItInReverseAndNothingTurnsAtAStandstill() {
        Terrain flat = new Terrain(ground(), List.of());
        State still = run(State.parked(0, 0, 0, 0), new Input(false, false, false, true, false), flat, 10);
        assertEquals(0, still.yaw(), 1e-9);
        State turning = run(State.parked(0, 0, 0, 0), FORWARD_RIGHT, flat, 30);
        assertTrue(turning.yaw() > 30, "turning right raises the yaw: " + turning.yaw());
        assertTrue(turning.x() < 0, "and the cart heads west, -x: " + turning.x());
        State back = run(State.parked(0, 0, 0, 0), new Input(false, true, false, true, false), flat, 30);
        assertTrue(back.yaw() < -10, "in reverse, right lowers it: " + back.yaw());
    }

    @Test
    void aWallStopsItHeadOn() {
        List<Aabb> boxes = ground();
        boxes.add(new Aabb(-5, 0, 5, 5, 3, 6));
        State state = run(State.parked(0, 0, 0, 0), FORWARD, new Terrain(boxes, List.of()), 60);
        assertEquals(4, state.z(), 1e-6, "its front (1 block ahead of its middle) stops at the wall at z = 5");
        assertEquals(0, state.speed(), 1e-9);
    }

    @Test
    void itDrivesUpASlabButNotAFullBlock() {
        List<Aabb> slab = ground();
        slab.add(new Aabb(-5, 0, 4, 5, 0.5, 30));
        State up = run(State.parked(0, 0, 0, 0), FORWARD, new Terrain(slab, List.of()), 40);
        assertEquals(0.5, up.y(), 1e-6);
        assertTrue(up.z() > 6, "it carried on over the slab: " + up.z());

        List<Aabb> block = ground();
        block.add(new Aabb(-5, 0, 4, 5, 1, 30));
        State stopped = run(State.parked(0, 0, 0, 0), FORWARD, new Terrain(block, List.of()), 40);
        assertEquals(0, stopped.y(), 1e-6);
        assertEquals(3, stopped.z(), 1e-6);
    }

    @Test
    void offAnEdgeItFallsAndLandsBelow() {
        List<Aabb> boxes = new ArrayList<>();
        boxes.add(new Aabb(-5, 2, -5, 5, 3, 3));     // a ledge up to z = 3, its top at y = 3
        boxes.add(new Aabb(-50, -1, -50, 50, 0, 50)); // the ground below
        State state = run(State.parked(0, 3, 0, 0), FORWARD, new Terrain(boxes, List.of()), 60);
        assertEquals(0, state.y(), 1e-6);
        assertTrue(state.onGround());
        assertTrue(state.z() > 4);
    }

    @Test
    void waterHoldsItToItsWaterSpeed() {
        List<Aabb> water = List.of(new Aabb(-50, 0, -50, 50, 1, 50));
        State state = run(State.parked(0, 0, 0, 0), FORWARD, new Terrain(ground(), water), 80);
        assertEquals(8 * 0.35, state.speed(), 1e-9);
    }

    @Test
    void aTurnIntoAWallIsNotMade() {
        // A corridor just wider than the cart: turning would swing its corners into the walls.
        List<Aabb> boxes = ground();
        boxes.add(new Aabb(-3, 0, -10, -0.65, 2, 10));
        boxes.add(new Aabb(0.65, 0, -10, 3, 2, 10));
        State state = run(State.parked(0, 0, 0, 0), FORWARD_RIGHT, new Terrain(boxes, List.of()), 20);
        // The 5 cm either side let it turn a couple of degrees, and no further.
        assertTrue(state.yaw() >= 0 && state.yaw() < 4, "yaw " + state.yaw());
        assertTrue(state.z() > 1);
        assertFalse(state.speed() == 0);
    }

    @Test
    void seatsTurnWithTheVehicle() {
        double[] right = VehiclePhysics.point(State.parked(10, 64, 10, 0), new Offset(1, 0.6, 0));
        assertEquals(9, right[0], 1e-9, "facing south, the driver's right is west (-x)");
        assertEquals(64.6, right[1], 1e-9);
        assertEquals(10, right[2], 1e-9);
        double[] ahead = VehiclePhysics.point(State.parked(10, 64, 10, 90), new Offset(0, 0, 2));
        assertEquals(8, ahead[0], 1e-9, "yaw 90 faces west");
        assertEquals(10, ahead[2], 1e-9);
    }

    @Test
    void theFootprintTurnsWithTheHeading() {
        Aabb along = VehiclePhysics.footprint(CART, 0, 0, 0, 0);
        assertEquals(1.2, along.maxX() - along.minX(), 1e-9);
        assertEquals(2.0, along.maxZ() - along.minZ(), 1e-9);
        Aabb across = VehiclePhysics.footprint(CART, 0, 0, 0, 90);
        assertEquals(2.0, across.maxX() - across.minX(), 1e-9);
        assertEquals(-180, VehiclePhysics.wrap(180), 1e-9);
        assertEquals(170, VehiclePhysics.wrap(-190), 1e-9);
    }

    // ---------------------------------------------------------------- 5.0: leaning

    @Test
    void withItsFrontOnASlabTheNoseIsUpAndTheBodySinksToTheGroundBehind() {
        List<Aabb> boxes = ground();
        boxes.add(new Aabb(-5, 0, 0.5, 5, 0.5, 30));
        // Stepped up onto the slab with its front: its back axle (0.8 behind) is over the ground.
        State parked = new State(0, 0.5, 0.5, 0, 0, 0, true, 0);
        State leaning = VehiclePhysics.step(CART, parked, Input.NONE, new Terrain(boxes, List.of()), STEP);
        assertEquals(Math.toDegrees(Math.atan2(0.5, 1.6)), leaning.pitch(), 1e-6, "front 0, back 0.5 down, 1.6 apart");
        assertEquals(0, leaning.roll(), 1e-9);
        assertTrue(leaning.lift() < 0, "drawn lower, to sit on the ground: " + leaning.lift());
        double[] nose = VehiclePhysics.point(leaning, new Offset(0, 0, 1));
        double[] tail = VehiclePhysics.point(leaning, new Offset(0, 0, -1));
        assertTrue(nose[1] > tail[1], "the front is drawn higher than the back");
    }

    @Test
    void drivingOverASlabItTiltsAsItCrossesTheEdgeAndLevelsOnTop() {
        List<Aabb> slab = ground();
        slab.add(new Aabb(-5, 0, 4, 5, 0.5, 30));
        Terrain terrain = new Terrain(slab, List.of());
        State state = State.parked(0, 0, 0, 0);
        double most = 0;
        for (int i = 0; i < 60; i++) {
            state = VehiclePhysics.step(CART, state, FORWARD, terrain, STEP);
            most = Math.max(most, state.pitch());
        }
        assertTrue(most > 5, "nose up while crossing: " + most);
        assertEquals(0.5, state.y(), 1e-6);
        assertEquals(0, state.pitch(), 0.01, "level on top");
        assertEquals(0, state.lift(), 0.01);
    }

    @Test
    void aSlabUnderTheLeftSideRollsItRight() {
        List<Aabb> boxes = ground();
        boxes.add(new Aabb(0.3, 0, -10, 5, 0.5, 10));   // facing south, its left is +x
        State parked = new State(0, 0.5, 0, 0, 0, 0, true, 0);
        State leaning = VehiclePhysics.step(CART, parked, Input.NONE, new Terrain(boxes, List.of()), STEP);
        assertTrue(leaning.roll() > 20, "the left side up: " + leaning.roll());
        assertEquals(0, leaning.pitch(), 1e-9);
        double[] left = VehiclePhysics.point(leaning, new Offset(-0.5, 0, 0));
        double[] right = VehiclePhysics.point(leaning, new Offset(0.5, 0, 0));
        assertTrue(left[1] > right[1]);
    }

    @Test
    void inTheAirItLevelsOut() {
        State tilted = new State(0, 5, 0, 0, 0, 0, false, 0, 20, -10, -0.3);
        State falling = VehiclePhysics.step(CART, tilted, Input.NONE, Terrain.EMPTY, STEP);
        assertTrue(Math.abs(falling.pitch()) < 20 && Math.abs(falling.roll()) < 10, "easing back toward level");
        State later = falling;
        for (int i = 0; i < 40; i++) {
            later = VehiclePhysics.step(CART, later, Input.NONE, Terrain.EMPTY, STEP);
        }
        assertEquals(0, later.pitch(), 1e-3);
        assertEquals(0, later.roll(), 1e-3);
    }

    // ---------------------------------------------------------------- 5.0: other vehicles

    private static VehiclePhysics.Body body(State state) {
        return VehiclePhysics.Body.of(CART, state);
    }

    @Test
    void headOnTwoVehiclesStopAtTheTouchAndBounceBackWithoutEverOverlapping() {
        Terrain flat = new Terrain(ground(), List.of());
        State a = State.parked(0, 0, 0, 0);       // facing south
        State b = State.parked(0, 0, 8, 180);     // facing north, toward a
        boolean bounced = false;
        for (int i = 0; i < 80; i++) {
            a = VehiclePhysics.step(CART, a, FORWARD, flat, List.of(body(b)), STEP);
            b = VehiclePhysics.step(CART, b, FORWARD, flat, List.of(body(a)), STEP);
            assertFalse(body(a).overlaps(body(b)), "step " + i + ": " + a + " / " + b);
            bounced |= a.speed() < 0 || b.speed() < 0;
        }
        assertTrue(bounced, "they were thrown back");
        assertTrue(b.z() - a.z() >= CART.length() - 1e-6, "never closer than touching: " + (b.z() - a.z()));
    }

    @Test
    void intoAParkedVehicleTheDriverBouncesAndTheParkedOneStays() {
        Terrain flat = new Terrain(ground(), List.of());
        State parked = new State(0, 0, 6, 90, 0, 0, true, 0);   // across the way
        State a = State.parked(0, 0, 0, 0);
        double slowest = 0;
        for (int i = 0; i < 50; i++) {
            a = VehiclePhysics.step(CART, a, FORWARD, flat, List.of(body(parked)), STEP);
            assertFalse(body(a).overlaps(body(parked)));
            slowest = Math.min(slowest, a.speed());
        }
        assertTrue(slowest < 0, "thrown back at the touch: " + slowest);
        assertTrue(a.z() < 6 - CART.width() / 2 - CART.length() / 2 + 1e-6, "its front no further than the other's side");
    }

    @Test
    void sideBySideTheyDriveOnUntouched() {
        Terrain flat = new Terrain(ground(), List.of());
        State a = State.parked(0, 0, 0, 0);
        State b = State.parked(1.7, 0, 0, 0);    // half a block apart
        for (int i = 0; i < 60; i++) {
            a = VehiclePhysics.step(CART, a, FORWARD, flat, List.of(body(b)), STEP);
            b = VehiclePhysics.step(CART, b, FORWARD, flat, List.of(body(a)), STEP);
        }
        assertEquals(8, a.speed(), 1e-9);
        assertEquals(8, b.speed(), 1e-9);
    }

    @Test
    void twoPutDownTooCloseCanDriveApart() {
        Terrain flat = new Terrain(ground(), List.of());
        State a = State.parked(0, 0, 0, 0);
        State b = new State(0.5, 0, 0.5, 0, 0, 0, true, 0);   // overlapping a
        for (int i = 0; i < 30; i++) {
            a = VehiclePhysics.step(CART, a, BACKWARD, flat, List.of(body(b)), STEP);
        }
        assertTrue(a.z() < -1, "it got out: " + a.z());
    }

    @Test
    void turnedRectanglesTouchOnlyWhereTheyReallyDo() {
        // Two vehicles turned 45 degrees, side by side with 0.3 between them: the boxes along the
        // axes that hold them overlap, the vehicles do not.
        double r = Math.sqrt(0.5);
        VehiclePhysics.Body a = new VehiclePhysics.Body(0, 0, 0, 45, 1.2, 2.0, 1);
        VehiclePhysics.Body b = new VehiclePhysics.Body(-1.5 * r, 0, -1.5 * r, 45, 1.2, 2.0, 1);
        assertFalse(a.overlaps(b));
        VehiclePhysics.Body closer = new VehiclePhysics.Body(-1.1 * r, 0, -1.1 * r, 45, 1.2, 2.0, 1);
        assertTrue(a.overlaps(closer));
        VehiclePhysics.Body above = new VehiclePhysics.Body(0, 1.5, 0, 0, 1.2, 2.0, 1);
        assertFalse(a.overlaps(above), "one on top of the other's height does not touch");
    }
}
