package com.arkcronist.content.core.animation;

import com.arkcronist.content.core.definition.Placement;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The display animations: poses, the bone hierarchy, interpolation, and the frames sent to clients. */
class AnimatorTest {

    private final AnimatedModel chest = BbModelReaderTest.read(BbModelReaderTest.chest(), new ArrayList<>());

    @Test
    void atRestEveryBoneSitsOnItsPivot() {
        List<Animator.Transform> rest = Animator.rest(chest);

        assertEquals(new Placement.Vec3(0, 0, 0), rest.get(0).translation());
        assertEquals(new Placement.Vec3(0, 0.625f, -0.4375f), rest.get(1).translation());
        assertEquals(new Placement.Vec3(0, 0.75f, 0.4375f), rest.get(2).translation(), "the latch: hinge + its offset");
        for (Animator.Transform transform : rest) {
            assertEquals(Animator.Quat.IDENTITY, transform.rotation());
            assertEquals(new Placement.Vec3(1, 1, 1), transform.scale());
        }
    }

    @Test
    void anOpenLidCarriesItsLatchAroundTheHinge() {
        List<Animator.Transform> open = Animator.pose(chest, chest.clips().get("open"), 10);

        // The lid swings up around its hinge: its front edge, towards +Z in the displays' mirrored
        // frame, now points up...
        Quaternionf lid = quaternion(open.get(1).rotation());
        Vector3f front = lid.transform(new Vector3f(0, 0, 1));
        assertVector(new Vector3f(0, 1, 0), front);
        assertEquals(open.get(1).translation(), Animator.rest(chest).get(1).translation(), "a hinge does not move");
        // ... and the latch, a child of the lid, swings with it: its offset (0, 0.125, 0.875) turned
        // -90 degrees around X is (0, 0.875, -0.125), added to the hinge (0, 0.625, -0.4375).
        assertVector(new Vector3f(0, 1.5f, -0.5625f), vector(open.get(2).translation()));
        assertEquals(open.get(1).rotation(), open.get(2).rotation(), "it turns with the lid");
        // The base is not animated.
        assertEquals(Animator.rest(chest).get(0), open.get(0));
    }

    @Test
    void halfwayIsHalfway() {
        Quaternionf half = quaternion(Animator.pose(chest, chest.clips().get("open"), 5).get(1).rotation());
        assertEquals(Math.toRadians(45), half.angle(), 1e-4);
    }

    @Test
    void beforeTheFirstAndAfterTheLastKeyframeTheValueHolds() {
        AnimatedModel.Clip open = chest.clips().get("open");
        assertEquals(Animator.pose(chest, open, 10), Animator.pose(chest, open, 50));
        assertEquals(Animator.rest(chest), Animator.pose(chest, open, -3));
    }

    @Test
    void openIsOneGlideThatHolds() {
        List<Animator.Frame> frames = Animator.frames(chest, chest.clips().get("open"));

        assertEquals(1, frames.size(), frames.toString());
        assertEquals(0, frames.getFirst().tick());
        assertEquals(10, frames.getFirst().duration(), "the client glides the whole way on its own");
        assertEquals(Animator.pose(chest, chest.clips().get("open"), 10), frames.getFirst().pose());
    }

    @Test
    void aPlayedOnceAnimationSnapsBackToRest() {
        List<Animator.Frame> frames = Animator.frames(chest, chest.clips().get("close"));

        Animator.Frame last = frames.getLast();
        assertEquals(10, last.tick());
        assertEquals(0, last.duration());
        assertEquals(Animator.rest(chest), last.pose());
    }

    @Test
    void smoothCurvesAreSampledEveryTwoTicksAndStepsJump() {
        List<Animator.Frame> frames = Animator.frames(chest, chest.clips().get("wobble"));
        List<Integer> ticks = frames.stream().map(Animator.Frame::tick).toList();

        for (int t = 0; t < 20; t += Animator.SMOOTH_STEP) {
            assertTrue(ticks.contains(t), "tick " + t + " in " + ticks);
        }
        // The step scale keyframe at 0 holds until tick 14, then the scale jumps at 15.
        assertTrue(ticks.contains(14), ticks.toString());
        Animator.Frame jump = frames.stream().filter(f -> f.tick() == 14).findFirst().orElseThrow();
        assertEquals(1, jump.duration());
        assertEquals(2f, jump.pose().get(0).scale().x());
        Animator.Frame before = frames.stream().filter(f -> f.tick() == 12).findFirst().orElseThrow();
        assertEquals(1f, before.pose().get(0).scale().x(), "held until the jump");
        for (Animator.Frame frame : frames) {
            assertTrue(frame.duration() >= 0 && frame.tick() + frame.duration() <= 20, frame.toString());
        }
    }

    @Test
    void aSmoothCurvePassesThroughItsKeyframes() {
        AnimatedModel.Clip wobble = chest.clips().get("wobble");
        assertEquals(new Placement.Vec3(-1, 0.5f, -0.25f), Animator.pose(chest, wobble, 10).get(0).translation());
        // A straight line would be at (-0.5, 0.25, -0.125) at tick 5; the curve overshoots it.
        Placement.Vec3 at5 = Animator.pose(chest, wobble, 5).get(0).translation();
        assertTrue(at5.x() < -0.5f, at5.toString());
    }

    @Test
    void catmullRomMeetsItsEnds() {
        Vector3f a = new Vector3f(0, 0, 0);
        Vector3f b = new Vector3f(1, 2, 3);
        Vector3f c = new Vector3f(4, 0, -1);
        Vector3f d = new Vector3f(5, 5, 5);
        assertVector(b, Animator.catmullRom(a, b, c, d, 0));
        assertVector(c, Animator.catmullRom(a, b, c, d, 1));
    }

    @Test
    void stepHoldsLinearBlends() {
        List<AnimatedModel.Keyframe> keys = List.of(
                new AnimatedModel.Keyframe(0, new Placement.Vec3(0, 0, 0), AnimatedModel.Interpolation.STEP),
                new AnimatedModel.Keyframe(10, new Placement.Vec3(10, 0, 0), AnimatedModel.Interpolation.LINEAR),
                new AnimatedModel.Keyframe(20, new Placement.Vec3(20, 0, 0), AnimatedModel.Interpolation.LINEAR));
        assertEquals(0, Animator.value(keys, 9.9f, 0).x, 1e-5, "step: held");
        assertEquals(15, Animator.value(keys, 15, 0).x, 1e-5, "linear: blended");
        assertEquals(1, Animator.value(List.of(), 3, 1).y, "no keyframes: the default");
    }

    @Test
    void rotationsComposeZThenYThenX() {
        Quaternionf q = Animator.quaternion(new Vector3f(90, 90, 0));
        // Applied to +Z: X turns it to -Y... after Y first. rotateZYX = Rz * Ry * Rx.
        Quaternionf expected = new Quaternionf().rotateZ(0).rotateY((float) Math.toRadians(90)).rotateX((float) Math.toRadians(90));
        assertTrue(q.equals(expected, 1e-6f), q + " vs " + expected);
    }

    @Test
    void parentScaleStretchesChildren() {
        AnimatedModel model = new AnimatedModel(List.of(
                new AnimatedModel.Bone("parent", -1, new Placement.Vec3(0, 0, 0), new Placement.Vec3(0, 0, 0), 1, null),
                new AnimatedModel.Bone("child", 0, new Placement.Vec3(1, 0, 0), new Placement.Vec3(0, 0, 0), 2, "{}")),
                java.util.Map.of("grow", new AnimatedModel.Clip("grow", AnimatedModel.Loop.HOLD, 10, java.util.Map.of(0,
                        new AnimatedModel.Channels(List.of(), List.of(), List.of(
                                new AnimatedModel.Keyframe(0, new Placement.Vec3(3, 3, 3), AnimatedModel.Interpolation.LINEAR)))))),
                List.of(), null);
        List<Animator.Transform> pose = Animator.pose(model, model.clips().get("grow"), 0);

        assertEquals(new Placement.Vec3(3, 0, 0), pose.get(1).translation(), "its offset stretched");
        assertEquals(new Placement.Vec3(6, 6, 6), pose.get(1).scale(), "inherited 3, times its model's own 2");
    }

    @Test
    void differentMomentsGiveDifferentPoses() {
        AnimatedModel.Clip open = chest.clips().get("open");
        assertNotEquals(Animator.pose(chest, open, 2), Animator.pose(chest, open, 8));
    }

    private static Quaternionf quaternion(Animator.Quat q) {
        return new Quaternionf(q.x(), q.y(), q.z(), q.w());
    }

    private static Vector3f vector(Placement.Vec3 v) {
        return new Vector3f(v.x(), v.y(), v.z());
    }

    private static void assertVector(Vector3f expected, Vector3f actual) {
        assertTrue(expected.equals(actual, 1e-4f), "expected " + expected + " but was " + actual);
    }
}
