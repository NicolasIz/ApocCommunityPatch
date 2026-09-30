package com.arkcronist.content.core.importer;

import com.arkcronist.content.core.definition.Placement;

/**
 * Rotations as other plugins write them - quaternions, axis and angle - turned into the degrees
 * around x, y and z that {@code furniture.display.rotation} takes.
 *
 * <p>The furniture display applies those degrees as {@code rotationXYZ}: x first, then y, then z,
 * the matrix {@code Rx * Ry * Rz}. The conversion here is that decomposition, so a rotation read
 * from an ItemsAdder file comes out identical once the display turns it back into a quaternion.</p>
 */
final class Rotations {

    /** A unit quaternion, {@code x y z w}. */
    record Quaternion(double x, double y, double z, double w) {

        static final Quaternion IDENTITY = new Quaternion(0, 0, 0, 1);

        static Quaternion axisAngle(double axisX, double axisY, double axisZ, double degrees) {
            double length = Math.sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ);
            if (length == 0) {
                return IDENTITY;
            }
            double half = Math.toRadians(degrees) / 2;
            double s = Math.sin(half) / length;
            return new Quaternion(axisX * s, axisY * s, axisZ * s, Math.cos(half));
        }

        /** {@code this * other}: {@code other} is applied first. */
        Quaternion times(Quaternion o) {
            return new Quaternion(
                    w * o.x + x * o.w + y * o.z - z * o.y,
                    w * o.y - x * o.z + y * o.w + z * o.x,
                    w * o.z + x * o.y - y * o.x + z * o.w,
                    w * o.w - x * o.x - y * o.y - z * o.z);
        }

        Quaternion normalized() {
            double length = Math.sqrt(x * x + y * y + z * z + w * w);
            return length == 0 ? IDENTITY : new Quaternion(x / length, y / length, z / length, w / length);
        }
    }

    private Rotations() {
    }

    /**
     * Degrees around x, y and z. A turn about a single axis - by far the usual case - comes out as
     * that angle on that axis, the way someone would have written it by hand.
     */
    static Placement.Vec3 toDegreesXYZ(Quaternion rotation) {
        Quaternion q = rotation.normalized();
        double[][] m = matrix(q);

        double sinY = clamp(m[0][2]);
        double y = Math.asin(sinY);
        double x;
        double z;
        if (Math.abs(sinY) < 0.9999999) {
            x = Math.atan2(-m[1][2], m[2][2]);
            z = Math.atan2(-m[0][1], m[0][0]);
        } else {
            // Gimbal lock: x and z turn about the same axis; put it all on x.
            x = Math.atan2(m[2][1], m[1][1]);
            z = 0;
        }
        Placement.Vec3 degrees = new Placement.Vec3(tidy(x), tidy(y), tidy(z));

        // Rx(180) Rz(180) is Ry(180): prefer the single-axis spelling when there is one.
        for (int axis = 0; axis < 3; axis++) {
            double[] unit = {axis == 0 ? 1 : 0, axis == 1 ? 1 : 0, axis == 2 ? 1 : 0};
            double angle = 2 * Math.atan2(unit[0] * q.x() + unit[1] * q.y() + unit[2] * q.z(), q.w());
            Quaternion single = Quaternion.axisAngle(unit[0], unit[1], unit[2], Math.toDegrees(angle));
            if (same(single, q)) {
                float a = tidy(angle);
                return new Placement.Vec3(axis == 0 ? a : 0, axis == 1 ? a : 0, axis == 2 ? a : 0);
            }
        }
        return degrees;
    }

    /** The rotation matrix of a unit quaternion, row by row. */
    static double[][] matrix(Quaternion q) {
        double x = q.x();
        double y = q.y();
        double z = q.z();
        double w = q.w();
        return new double[][] {
                {1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)},
                {2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)},
                {2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)}};
    }

    /** The same rotation: {@code q} and {@code -q} are, so the sign of the dot product does not matter. */
    private static boolean same(Quaternion a, Quaternion b) {
        double dot = a.x() * b.x() + a.y() * b.y() + a.z() * b.z() + a.w() * b.w();
        return Math.abs(Math.abs(dot) - 1) < 1e-9;
    }

    /** Degrees in (-180, 180], rounded to 0.001 so 89.99999 is written as 90. */
    private static float tidy(double radians) {
        double degrees = Math.toDegrees(radians);
        degrees = Math.round(degrees * 1000) / 1000.0;
        if (degrees <= -180) {
            degrees += 360;
        }
        return (float) (degrees == -0.0 ? 0.0 : degrees);
    }

    private static double clamp(double value) {
        return Math.max(-1, Math.min(1, value));
    }
}
