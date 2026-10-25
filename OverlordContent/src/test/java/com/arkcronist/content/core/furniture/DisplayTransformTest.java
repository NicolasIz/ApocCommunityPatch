package com.arkcronist.content.core.furniture;

import com.arkcronist.content.core.definition.Placement;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The editor's matrix maths, checked against JOML - what the server hands the client. Against
 * JOML's doubles it must agree to the last digits; against the floats the client is sent, to what a
 * float can hold (near a half turn JOML's float {@code cosFromSin} drifts by up to 1e-4).
 */
class DisplayTransformTest {

    private static final double EPSILON = 1e-5;

    private static DisplayTransform transform(float tx, float ty, float tz, float sx, float sy, float sz,
                                              float rx, float ry, float rz) {
        return new DisplayTransform(new Placement.Vec3(tx, ty, tz), new Placement.Vec3(sx, sy, sz),
                new Placement.Vec3(rx, ry, rz));
    }

    @Test
    void theQuaternionIsJomlsRotationXyz() {
        Random random = new Random(7);
        for (int i = 0; i < 500; i++) {
            float rx = random.nextFloat() * 360 - 180;
            float ry = random.nextFloat() * 360 - 180;
            float rz = random.nextFloat() * 360 - 180;
            double[] ours = transform(0, 0, 0, 1, 1, 1, rx, ry, rz).quaternion();
            Quaterniond exact = new Quaterniond().rotationXYZ(Math.toRadians(rx), Math.toRadians(ry), Math.toRadians(rz));
            assertArrayEquals(new double[] {exact.x, exact.y, exact.z, exact.w}, ours, 1e-9,
                    "angles " + rx + "," + ry + "," + rz);
            Quaternionf sent = new Quaternionf().rotationXYZ((float) Math.toRadians(rx), (float) Math.toRadians(ry),
                    (float) Math.toRadians(rz));
            assertArrayEquals(new double[] {sent.x, sent.y, sent.z, sent.w}, ours, 1e-3);
            assertEquals(1, ours[0] * ours[0] + ours[1] * ours[1] + ours[2] * ours[2] + ours[3] * ours[3], EPSILON);
        }
    }

    @Test
    void theMatrixIsTranslationTimesRotationTimesScaleAsTheClientBuildsIt() {
        Random random = new Random(11);
        for (int i = 0; i < 500; i++) {
            DisplayTransform transform = transform(random.nextFloat() * 4 - 2, random.nextFloat() * 4 - 2,
                    random.nextFloat() * 4 - 2, 0.1f + random.nextFloat() * 3, 0.1f + random.nextFloat() * 3,
                    0.1f + random.nextFloat() * 3, random.nextFloat() * 360 - 180, random.nextFloat() * 360 - 180,
                    random.nextFloat() * 360 - 180);
            Placement.Vec3 t = transform.translation();
            Placement.Vec3 s = transform.scale();
            Placement.Vec3 r = transform.rotation();
            // org.bukkit.util.Transformation(translation, left, scale, right): T · L · S · R, right = identity.
            Matrix4d exact = new Matrix4d().translation(t.x(), t.y(), t.z())
                    .rotate(new Quaterniond().rotationXYZ(Math.toRadians(r.x()), Math.toRadians(r.y()), Math.toRadians(r.z())))
                    .scale(s.x(), s.y(), s.z());
            double[] expected = new double[16];
            exact.get(expected);
            assertArrayEquals(expected, transform.matrix(), 1e-9);
            Matrix4f sent = new Matrix4f().translation(t.x(), t.y(), t.z())
                    .rotate(new Quaternionf().rotationXYZ((float) Math.toRadians(r.x()), (float) Math.toRadians(r.y()),
                            (float) Math.toRadians(r.z())))
                    .scale(s.x(), s.y(), s.z());
            float[] floats = new float[16];
            sent.get(floats);
            for (int e = 0; e < 16; e++) {
                assertEquals(floats[e], transform.matrix()[e], 2e-3, "element " + e);
            }
            // A corner of the model goes where JOML sends it.
            Vector3d corner = exact.transformPosition(new Vector3d(0.5, -0.5, 0.25));
            assertArrayEquals(new double[] {corner.x, corner.y, corner.z}, transform.apply(0.5, -0.5, 0.25), 1e-9);
        }
    }

    @Test
    void quarterTurnsMoveTheAxesWhereTheyShould() {
        // 90° about y: the model's +x points to -z.
        assertArrayEquals(new double[] {0, 0, -1}, transform(0, 0, 0, 1, 1, 1, 0, 90, 0).apply(1, 0, 0), EPSILON);
        // Scale first, along the model's own axes, then turn, then move.
        assertArrayEquals(new double[] {0, 0.5, -2}, transform(0, 0.5f, 0, 2, 1, 1, 0, 90, 0).apply(1, 0, 0), EPSILON);
        // x first, then y about the axes x left: the model's +x ends up pointing up.
        assertArrayEquals(new double[] {0, 1, 0}, transform(0, 0, 0, 1, 1, 1, 90, 90, 0).apply(1, 0, 0), EPSILON);
        assertArrayEquals(new double[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1},
                DisplayTransform.IDENTITY.matrix(), EPSILON);
    }

    @Test
    void clicksAddUpExactlyAndStayInRange() {
        DisplayTransform transform = DisplayTransform.IDENTITY;
        for (int i = 0; i < 100; i++) {
            transform = transform.adjust(DisplayTransform.Part.TRANSLATION, DisplayTransform.Axis.Y, 0.01);
        }
        assertEquals(1f, transform.translation().y(), "a hundred clicks of 0.01 are exactly 1");
        for (int i = 0; i < 3; i++) {
            transform = transform.adjust(DisplayTransform.Part.TRANSLATION, DisplayTransform.Axis.X, 0.1);
        }
        assertEquals(0.3f, transform.translation().x());
        assertEquals("[0.3, 1, 0]", DisplayTransform.yaml(transform.translation()));

        transform = transform.adjust(DisplayTransform.Part.TRANSLATION, DisplayTransform.Axis.Z, 100);
        assertEquals(16f, transform.translation().z(), "at most 16 blocks away");
        transform = transform.adjust(DisplayTransform.Part.SCALE, DisplayTransform.Axis.X, -5);
        assertEquals(0.01f, transform.scale().x(), "never shrunk to nothing or turned inside out");
        transform = transform.adjust(DisplayTransform.Part.SCALE, DisplayTransform.Axis.Y, 0.25);
        assertEquals(1.25f, transform.scale().y());

        transform = transform.adjust(DisplayTransform.Part.ROTATION, DisplayTransform.Axis.Y, 170);
        transform = transform.adjust(DisplayTransform.Part.ROTATION, DisplayTransform.Axis.Y, 20);
        assertEquals(-170f, transform.rotation().y(), "190 degrees is -170");
        transform = transform.adjust(DisplayTransform.Part.ROTATION, DisplayTransform.Axis.Y, -10);
        assertEquals(-180f, transform.rotation().y());
        transform = transform.adjust(DisplayTransform.Part.ROTATION, DisplayTransform.Axis.X, 180);
        assertEquals(-180f, transform.rotation().x(), "180 and -180 are the same turn; one of them is kept");
        assertEquals("[0.01, 1.25, 1]", DisplayTransform.yaml(transform.scale()));
        assertEquals("0.0125", DisplayTransform.number(0.0125f));
        assertEquals("-2", DisplayTransform.number(-2f));
    }
}
