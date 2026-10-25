package com.arkcronist.content.bukkit.furniture;

import com.arkcronist.content.core.animation.AnimatedModel;
import com.arkcronist.content.core.animation.Animator;
import com.arkcronist.content.core.animation.BbModelReaderTest;
import com.arkcronist.content.core.definition.Placement;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A bone's pose as the transformation its display is given. */
class AnimationPlayerTest {

    private final AnimatedModel chest = BbModelReaderTest.read(BbModelReaderTest.chest(), new ArrayList<>());

    @Test
    void withDefaultSettingsABoneIsDrawnExactlyAsPosed() {
        List<Animator.Transform> open = Animator.pose(chest, chest.clips().get("open"), 10);
        Animator.Transform lid = open.get(1);

        Transformation drawn = AnimationPlayer.transformation(lid, Placement.Display.DEFAULT);

        assertVector(vector(lid.translation()), drawn.getTranslation());
        assertQuaternion(quaternion(lid.rotation()), drawn.getLeftRotation());
        assertVector(vector(lid.scale()), drawn.getScale());
        assertQuaternion(new Quaternionf(), drawn.getRightRotation());
    }

    @Test
    void theFurnituresDisplaySettingsCarryEveryBoneWithThem() {
        Placement.Display settings = new Placement.Display("NONE", new Placement.Vec3(0, 0.1f, 0),
                new Placement.Vec3(2, 2, 2), new Placement.Vec3(0, 90, 0));
        Animator.Transform bone = new Animator.Transform(new Placement.Vec3(0, 0, 1), Animator.Quat.IDENTITY,
                new Placement.Vec3(1, 1, 1));

        Transformation drawn = AnimationPlayer.transformation(bone, settings);

        // Scaled to (0, 0, 2), turned 90 degrees around Y to (2, 0, 0), then offset.
        assertVector(new Vector3f(2, 0.1f, 0), drawn.getTranslation());
        assertQuaternion(new Quaternionf().rotationY((float) Math.toRadians(90)), drawn.getLeftRotation());
        assertVector(new Vector3f(2, 2, 2), drawn.getScale());
    }

    @Test
    void aHingeStaysPutWhileItsLidTurns() {
        Transformation shut = AnimationPlayer.transformation(Animator.rest(chest).get(1), Placement.Display.DEFAULT);
        Transformation open = AnimationPlayer.transformation(
                Animator.pose(chest, chest.clips().get("open"), 10).get(1), Placement.Display.DEFAULT);

        assertVector(shut.getTranslation(), open.getTranslation());
        assertEquals(Math.toRadians(90), new Quaternionf(open.getLeftRotation()).angle(), 1e-4);
        assertTrue(new Quaternionf(shut.getLeftRotation()).angle() < 1e-4);
    }

    private static Vector3f vector(Placement.Vec3 vec) {
        return new Vector3f(vec.x(), vec.y(), vec.z());
    }

    private static Quaternionf quaternion(Animator.Quat quat) {
        return new Quaternionf(quat.x(), quat.y(), quat.z(), quat.w());
    }

    private static void assertVector(Vector3f expected, Vector3f actual) {
        assertTrue(expected.distance(actual) < 1e-4, "expected " + expected + " but was " + actual);
    }

    private static void assertQuaternion(Quaternionf expected, Quaternionf actual) {
        // q and -q are the same rotation.
        float dot = Math.abs(expected.dot(actual));
        assertEquals(1f, dot, 1e-4, "expected " + expected + " but was " + actual);
    }
}
