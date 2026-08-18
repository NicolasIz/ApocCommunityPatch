package com.arkcronist.gen.core.math;

/**
 * Small, allocation free math helpers shared by every terrain stage.
 *
 * <p>Everything here is a pure function of its arguments which keeps the whole terrain pipeline
 * deterministic and safe to call from Paper's parallel chunk generation threads.</p>
 */
public final class MathUtil {

    private MathUtil() {
    }

    public static double clamp(double value, double min, double max) {
        return value < min ? min : (value > max ? max : value);
    }

    public static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    public static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    /** Classic 3t^2-2t^3 ease. */
    public static double smoothStep(double t) {
        t = clamp(t, 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    /** Ken Perlin's quintic ease, C2 continuous, used where visible creasing must be avoided. */
    public static double smootherStep(double t) {
        t = clamp(t, 0.0, 1.0);
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    /** Maps {@code value} from [inMin,inMax] onto [outMin,outMax] without clamping. */
    public static double remap(double value, double inMin, double inMax, double outMin, double outMax) {
        double t = (value - inMin) / (inMax - inMin);
        return outMin + t * (outMax - outMin);
    }

    /** Maps {@code value} from [inMin,inMax] onto [0,1], clamped. */
    public static double normalize(double value, double inMin, double inMax) {
        return clamp((value - inMin) / (inMax - inMin), 0.0, 1.0);
    }

    /** Smoothed minimum; blends the two values instead of producing a hard crease. */
    public static double smoothMin(double a, double b, double k) {
        if (k <= 0.0) {
            return Math.min(a, b);
        }
        double h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
        return lerp(h, b, a) - k * h * (1.0 - h);
    }

    /** Smoothed maximum, the mirror of {@link #smoothMin(double, double, double)}. */
    public static double smoothMax(double a, double b, double k) {
        return -smoothMin(-a, -b, k);
    }

    /**
     * Quantises a value into steps while keeping a soft riser between them.
     * This is what turns smooth slopes into mesa style benches and cliff bands.
     *
     * @param value     input value
     * @param steps     number of steps per unit
     * @param sharpness 0 keeps the input untouched, 1 produces hard steps
     */
    public static double terrace(double value, double steps, double sharpness) {
        if (steps <= 0.0) {
            return value;
        }
        double scaled = value * steps;
        double floor = Math.floor(scaled);
        double frac = scaled - floor;
        double stepped = floor + smootherStep(clamp(frac * 1.6 - 0.3, 0.0, 1.0));
        return lerp(clamp(sharpness, 0.0, 1.0), value, stepped / steps);
    }

    /** Pushes values towards 0 or 1; {@code bias} &lt; 0.5 darkens, &gt; 0.5 brightens. */
    public static double bias(double value, double bias) {
        return Math.pow(value, Math.log(clamp(bias, 0.001, 0.999)) / Math.log(0.5));
    }

    /** Contrast curve around 0.5. */
    public static double gain(double value, double gain) {
        return value < 0.5
                ? bias(value * 2.0, 1.0 - gain) * 0.5
                : 1.0 - bias(2.0 - value * 2.0, 1.0 - gain) * 0.5;
    }

    public static double bilinear(double tx, double tz, double v00, double v10, double v01, double v11) {
        return lerp(tz, lerp(tx, v00, v10), lerp(tx, v01, v11));
    }

    /** Catmull-Rom style cubic interpolation used when upsampling coarse height grids. */
    public static double cubic(double t, double p0, double p1, double p2, double p3) {
        double a = -0.5 * p0 + 1.5 * p1 - 1.5 * p2 + 0.5 * p3;
        double b = p0 - 2.5 * p1 + 2.0 * p2 - 0.5 * p3;
        double c = -0.5 * p0 + 0.5 * p2;
        return ((a * t + b) * t + c) * t + p1;
    }

    public static int floor(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    public static double squareDistance(double dx, double dz) {
        return dx * dx + dz * dz;
    }

    /** Signed sharpening of a [-1,1] noise value, keeps sign but pushes the magnitude out. */
    public static double sharpen(double value, double strength) {
        double sign = Math.signum(value);
        double magnitude = Math.abs(value);
        return sign * Math.pow(magnitude, Math.max(0.05, 1.0 - strength));
    }
}
