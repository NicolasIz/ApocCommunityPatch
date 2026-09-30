package com.arkcronist.content.core.importer;

import com.arkcronist.content.core.definition.Placement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RotationsTest {

    @Test
    void aTurnAboutOneAxisStaysOnThatAxis() {
        assertEquals(new Placement.Vec3(0, 180, 0), degrees(Rotations.Quaternion.axisAngle(0, 1, 0, 180)));
        assertEquals(new Placement.Vec3(90, 0, 0), degrees(Rotations.Quaternion.axisAngle(1, 0, 0, 90)));
        assertEquals(new Placement.Vec3(0, 0, -45), degrees(Rotations.Quaternion.axisAngle(0, 0, 1, -45)));
        assertEquals(Placement.Vec3.ZERO, degrees(Rotations.Quaternion.IDENTITY));
    }

    /** Whatever the angles, applying them x, then y, then z must give back the same rotation. */
    @Test
    void anyRotationSurvivesTheRoundTrip() {
        Rotations.Quaternion[] samples = {
                Rotations.Quaternion.axisAngle(1, 1, 0, 30),
                Rotations.Quaternion.axisAngle(0.2, -1, 0.7, 250),
                Rotations.Quaternion.axisAngle(0, 1, 0, 90).times(Rotations.Quaternion.axisAngle(1, 0, 0, 90)),
                Rotations.Quaternion.axisAngle(1, 0, 0, 20).times(Rotations.Quaternion.axisAngle(0, 1, 0, 90)),
        };
        for (Rotations.Quaternion sample : samples) {
            Placement.Vec3 angles = Rotations.toDegreesXYZ(sample);
            Rotations.Quaternion back = Rotations.Quaternion.axisAngle(1, 0, 0, angles.x())
                    .times(Rotations.Quaternion.axisAngle(0, 1, 0, angles.y()))
                    .times(Rotations.Quaternion.axisAngle(0, 0, 1, angles.z()));
            double[][] expected = Rotations.matrix(sample.normalized());
            double[][] actual = Rotations.matrix(back);
            for (int row = 0; row < 3; row++) {
                for (int column = 0; column < 3; column++) {
                    assertEquals(expected[row][column], actual[row][column], 1e-4, sample + " -> " + angles);
                }
            }
        }
    }

    private static Placement.Vec3 degrees(Rotations.Quaternion rotation) {
        return Rotations.toDegreesXYZ(rotation);
    }
}
