package com.arkcronist.content.core.furniture;

import com.arkcronist.content.core.definition.Placement;

import java.util.Locale;

/**
 * Where a piece of furniture's model sits on its block: the three parts of
 * {@code furniture.display} the in-game editor changes, and the matrix the client draws them with.
 *
 * <p>An item display draws its item through {@code translation · leftRotation · scale ·
 * rightRotation}; this plugin puts the whole rotation on the left and none on the right, so a point
 * {@code p} of the model lands at {@code T + R(S ⊙ p)}: scaled along the model's own axes, then
 * turned, then moved. The rotation is {@code x}, then {@code y}, then {@code z} degrees, each about
 * the axes as the previous turns left them - JOML's {@code rotationXYZ}, the one the furniture
 * service builds the display's quaternion with.</p>
 *
 * <p>Every change is rounded to four decimals, so a hundred clicks of 0.01 come to exactly 1 and
 * what is written back to the YAML file reads as typed. Values are kept within what a piece of
 * furniture can use: a translation of at most {@value #MAX_TRANSLATION} blocks either way, a scale
 * between {@value #MIN_SCALE} and {@value #MAX_SCALE}, and an angle in {@code [-180, 180)}.</p>
 *
 * @param translation offset from the support block's centre, in blocks
 * @param scale       per axis, along the model's own axes
 * @param rotation    degrees about x, y and z
 */
public record DisplayTransform(Placement.Vec3 translation, Placement.Vec3 scale, Placement.Vec3 rotation) {

    public static final double MAX_TRANSLATION = 16;
    public static final double MIN_SCALE = 0.01;
    public static final double MAX_SCALE = 64;

    /** The three parts a click changes. */
    public enum Part {
        TRANSLATION("Translation", "blocks"),
        SCALE("Scale", "x"),
        ROTATION("Rotation", "°");

        public final String label;
        public final String unit;

        Part(String label, String unit) {
            this.label = label;
            this.unit = unit;
        }
    }

    public enum Axis { X, Y, Z }

    public static final DisplayTransform IDENTITY = new DisplayTransform(Placement.Vec3.ZERO, Placement.Vec3.ONE,
            Placement.Vec3.ZERO);

    public static DisplayTransform of(Placement.Display display) {
        return new DisplayTransform(display.translation(), display.scale(), display.rotation());
    }

    /** {@code display} with this transform and its own transform name. */
    public Placement.Display applyTo(Placement.Display display) {
        return new Placement.Display(display.transform(), translation, scale, rotation);
    }

    public Placement.Vec3 get(Part part) {
        return switch (part) {
            case TRANSLATION -> translation;
            case SCALE -> scale;
            case ROTATION -> rotation;
        };
    }

    public static float component(Placement.Vec3 vector, Axis axis) {
        return switch (axis) {
            case X -> vector.x();
            case Y -> vector.y();
            case Z -> vector.z();
        };
    }

    /** One part's axis moved by {@code delta} - blocks, a factor, or degrees - rounded and kept in range. */
    public DisplayTransform adjust(Part part, Axis axis, double delta) {
        Placement.Vec3 vector = get(part);
        double value = component(vector, axis) + delta;
        value = switch (part) {
            case TRANSLATION -> Math.max(-MAX_TRANSLATION, Math.min(MAX_TRANSLATION, value));
            case SCALE -> Math.max(MIN_SCALE, Math.min(MAX_SCALE, value));
            case ROTATION -> wrapDegrees(value);
        };
        Placement.Vec3 changed = with(vector, axis, (float) round(value));
        return with(part, changed);
    }

    /** This transform with one part replaced. */
    public DisplayTransform with(Part part, Placement.Vec3 vector) {
        return switch (part) {
            case TRANSLATION -> new DisplayTransform(vector, scale, rotation);
            case SCALE -> new DisplayTransform(translation, vector, rotation);
            case ROTATION -> new DisplayTransform(translation, scale, vector);
        };
    }

    /** The rotation as a unit quaternion {@code {x, y, z, w}}: {@code qx · qy · qz}, JOML's {@code rotationXYZ}. */
    public double[] quaternion() {
        double[] q = {0, 0, 0, 1};
        q = multiply(q, axisQuaternion(0, rotation.x()));
        q = multiply(q, axisQuaternion(1, rotation.y()));
        q = multiply(q, axisQuaternion(2, rotation.z()));
        return q;
    }

    /**
     * The affine matrix the client draws with, column-major like JOML's {@code Matrix4f.get(float[])}:
     * element {@code [column * 4 + row]}. Its upper 3×3 is {@code R · diag(S)}, its last column
     * {@code T}.
     */
    public double[] matrix() {
        double[][] r = rotationMatrix(quaternion());
        double[] s = {scale.x(), scale.y(), scale.z()};
        double[] t = {translation.x(), translation.y(), translation.z()};
        double[] m = new double[16];
        for (int column = 0; column < 3; column++) {
            for (int row = 0; row < 3; row++) {
                m[column * 4 + row] = r[row][column] * s[column];
            }
        }
        m[12] = t[0];
        m[13] = t[1];
        m[14] = t[2];
        m[15] = 1;
        return m;
    }

    /** Where a point of the model, in its own blocks around the display, is drawn. */
    public double[] apply(double x, double y, double z) {
        double[] m = matrix();
        return new double[] {
            m[0] * x + m[4] * y + m[8] * z + m[12],
            m[1] * x + m[5] * y + m[9] * z + m[13],
            m[2] * x + m[6] * y + m[10] * z + m[14]
        };
    }

    /** {@code [0, 0.5, 0]}: a vector as the YAML files write it, with no trailing zeros. */
    public static String yaml(Placement.Vec3 vector) {
        return "[" + number(vector.x()) + ", " + number(vector.y()) + ", " + number(vector.z()) + "]";
    }

    /** {@code 0.5}, {@code -1}, {@code 0.0125}: a float written short, the way it was most likely typed. */
    public static String number(float value) {
        double rounded = round(value);
        if (rounded == Math.rint(rounded)) {
            return Long.toString((long) rounded);
        }
        String text = String.format(Locale.ROOT, "%.4f", rounded);
        return text.replaceAll("0+$", "");
    }

    private static double round(double value) {
        double rounded = Math.round(value * 10_000d) / 10_000d;
        return rounded == 0 ? 0 : rounded;
    }

    private static double wrapDegrees(double degrees) {
        double wrapped = ((degrees + 180) % 360 + 360) % 360 - 180;
        return round(wrapped) == 180 ? -180 : wrapped;
    }

    private static Placement.Vec3 with(Placement.Vec3 vector, Axis axis, float value) {
        return switch (axis) {
            case X -> new Placement.Vec3(value, vector.y(), vector.z());
            case Y -> new Placement.Vec3(vector.x(), value, vector.z());
            case Z -> new Placement.Vec3(vector.x(), vector.y(), value);
        };
    }

    private static double[] axisQuaternion(int axis, double degrees) {
        double half = Math.toRadians(degrees) / 2;
        double[] q = {0, 0, 0, Math.cos(half)};
        q[axis] = Math.sin(half);
        return q;
    }

    /** {@code a · b}: b's turn applied first, then a's - for body axes, a then b. */
    private static double[] multiply(double[] a, double[] b) {
        return new double[] {
            a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
            a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
            a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
            a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2]
        };
    }

    private static double[][] rotationMatrix(double[] q) {
        double x = q[0];
        double y = q[1];
        double z = q[2];
        double w = q[3];
        return new double[][] {
            {1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)},
            {2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)},
            {2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)}
        };
    }
}
