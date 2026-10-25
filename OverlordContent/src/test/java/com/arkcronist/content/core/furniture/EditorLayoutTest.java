package com.arkcronist.content.core.furniture;

import com.arkcronist.content.core.definition.Placement;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** From a click on a slot to the matrix the client draws the furniture with. */
class EditorLayoutTest {

    private static final double EPSILON = 1e-6;

    @Test
    void everyButtonIsWhereTheLayoutSaysAndDoesOneThing() {
        Set<String> seen = new HashSet<>();
        int adjusters = 0;
        for (int slot = 0; slot < EditorLayout.SIZE; slot++) {
            EditorLayout.Action action = EditorLayout.at(slot);
            if (action == null) {
                continue;
            }
            assertTrue(seen.add(action.toString()), "two slots do " + action);
            if (action instanceof EditorLayout.Adjust adjust) {
                adjusters++;
                int step = indexOf(Math.abs(adjust.step()));
                assertEquals(slot, EditorLayout.button(adjust.axis(), step, adjust.step() > 0));
                assertEquals(EditorLayout.row(adjust.axis()), slot / 9, "an axis' buttons share its row");
            }
        }
        assertEquals(3 * 3 * 2, adjusters, "three axes, three steps, both ways");
        assertNull(EditorLayout.at(EditorLayout.value(DisplayTransform.Axis.Y)), "a value is shown, not clicked");
        assertNull(EditorLayout.at(-1));
        assertNull(EditorLayout.at(EditorLayout.SIZE), "the player's own inventory is not the menu");
        for (DisplayTransform.Part part : DisplayTransform.Part.values()) {
            assertEquals(new EditorLayout.Select(part), EditorLayout.at(EditorLayout.selector(part)));
        }
        assertEquals(EditorLayout.Button.SAVE, EditorLayout.at(EditorLayout.SAVE));
        assertEquals(EditorLayout.Button.CANCEL, EditorLayout.at(EditorLayout.CANCEL));

        // The smallest step sits next to the value, the largest furthest out; minus on the left.
        int value = EditorLayout.value(DisplayTransform.Axis.X);
        assertEquals(new EditorLayout.Adjust(DisplayTransform.Axis.X, -0.01), EditorLayout.at(value - 1));
        assertEquals(new EditorLayout.Adjust(DisplayTransform.Axis.X, -1.0), EditorLayout.at(value - 3));
        assertEquals(new EditorLayout.Adjust(DisplayTransform.Axis.X, 0.01), EditorLayout.at(value + 1));
        assertEquals(new EditorLayout.Adjust(DisplayTransform.Axis.X, 1.0), EditorLayout.at(value + 3));
        assertEquals("+0.01", EditorLayout.label(0.01));
        assertEquals("-1", EditorLayout.label(-1));
    }

    @Test
    void translationClicksMoveTheModelAlongTheDisplaysAxes() {
        DisplayTransform start = new DisplayTransform(new Placement.Vec3(0, 0.5f, 0), Placement.Vec3.ONE,
                Placement.Vec3.ZERO);
        DisplayTransform edited = start;
        // Three clicks of +0.1 on x, one shift-click of -0.01 on z (ten of them), one +1 on y.
        for (int i = 0; i < 3; i++) {
            edited = click(edited, DisplayTransform.Part.TRANSLATION, EditorLayout.button(DisplayTransform.Axis.X, 1, true), false);
        }
        edited = click(edited, DisplayTransform.Part.TRANSLATION, EditorLayout.button(DisplayTransform.Axis.Z, 0, false), true);
        edited = click(edited, DisplayTransform.Part.TRANSLATION, EditorLayout.button(DisplayTransform.Axis.Y, 2, true), false);

        assertEquals(new Placement.Vec3(0.3f, 1.5f, -0.1f), edited.translation());
        double[] matrix = edited.matrix();
        assertArrayEquals(new double[] {0.3f, 1.5f, -0.1f}, new double[] {matrix[12], matrix[13], matrix[14]}, EPSILON);
        // The model's centre is drawn where the translation says; its corner the same distance on.
        assertArrayEquals(new double[] {0.3f + 0.5, 1.5f + 0.5, -0.1f + 0.5}, edited.apply(0.5, 0.5, 0.5), EPSILON);
    }

    @Test
    void rotationAndScaleClicksGiveTheMatrixJomlBuilds() {
        DisplayTransform edited = DisplayTransform.IDENTITY;
        int plusOneY = EditorLayout.button(DisplayTransform.Axis.Y, 2, true);
        // Nine shift-clicks of +1 degree: a quarter turn.
        for (int i = 0; i < 9; i++) {
            edited = click(edited, DisplayTransform.Part.ROTATION, plusOneY, true);
        }
        assertEquals(90f, edited.rotation().y());
        // Twice +0.1 and five times -0.01 on the scale's x: 1.15.
        for (int i = 0; i < 2; i++) {
            edited = click(edited, DisplayTransform.Part.SCALE, EditorLayout.button(DisplayTransform.Axis.X, 1, true), false);
        }
        for (int i = 0; i < 5; i++) {
            edited = click(edited, DisplayTransform.Part.SCALE, EditorLayout.button(DisplayTransform.Axis.X, 0, false), false);
        }
        assertEquals(1.15f, edited.scale().x());

        // Stretched along its own x, then turned: the stretch now runs along -z.
        assertArrayEquals(new double[] {0, 0, -1.15f}, edited.apply(1, 0, 0), EPSILON);
        Matrix4d joml = new Matrix4d().rotate(new Quaterniond().rotationXYZ(0, Math.toRadians(90), 0))
                .scale(edited.scale().x(), 1, 1);
        double[] expected = new double[16];
        joml.get(expected);
        assertArrayEquals(expected, edited.matrix(), 1e-9);
        Vector3d corner = joml.transformPosition(new Vector3d(-0.5, 0.25, 0.5));
        assertArrayEquals(new double[] {corner.x, corner.y, corner.z}, edited.apply(-0.5, 0.25, 0.5), 1e-9);

        // Thirty-six shift-clicks of the "1" button on z come back round to where they started.
        DisplayTransform turned = edited;
        int plusOneZ = EditorLayout.button(DisplayTransform.Axis.Z, 2, true);
        for (int i = 0; i < 36; i++) {
            turned = click(turned, DisplayTransform.Part.ROTATION, plusOneZ, true);
        }
        assertEquals(0f, turned.rotation().z());
        assertArrayEquals(edited.matrix(), turned.matrix(), EPSILON);
    }

    private static DisplayTransform click(DisplayTransform transform, DisplayTransform.Part part, int slot, boolean shift) {
        EditorLayout.Adjust adjust = assertInstanceOf(EditorLayout.Adjust.class, EditorLayout.at(slot));
        return EditorLayout.click(transform, part, adjust, shift);
    }

    private static int indexOf(double step) {
        for (int i = 0; i < EditorLayout.STEPS.length; i++) {
            if (EditorLayout.STEPS[i] == step) {
                return i;
            }
        }
        throw new AssertionError("no step " + step);
    }
}
