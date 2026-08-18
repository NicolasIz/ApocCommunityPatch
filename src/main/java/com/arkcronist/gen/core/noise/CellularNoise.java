package com.arkcronist.gen.core.noise;

import com.arkcronist.gen.core.math.Hashing;
import com.arkcronist.gen.core.math.MathUtil;

/**
 * Jittered grid (Worley/Voronoi) noise.
 *
 * <p>Used for things that want cells rather than waves: lake basins, plateau blocks, floating island
 * clusters, mineral provinces and the region grid that keeps biome identity coherent over large
 * areas.</p>
 */
public final class CellularNoise {

    private final long seed;
    private final double frequency;
    private final double jitter;

    public CellularNoise(long seed, String name, double frequency, double jitter) {
        this.seed = Hashing.salt(seed, name + "#cellular");
        this.frequency = frequency;
        this.jitter = MathUtil.clamp(jitter, 0.0, 1.0);
    }

    /** Distance to the nearest feature point, normalised to roughly [0,1]. */
    public double f1(double x, double z) {
        return sample(x, z, Result.F1);
    }

    /** Difference between the two nearest feature points; near zero exactly on a cell border. */
    public double edge(double x, double z) {
        return sample(x, z, Result.F2_MINUS_F1);
    }

    /** Stable pseudo random value in [0,1) shared by every position inside the same cell. */
    public double cellValue(double x, double z) {
        return sample(x, z, Result.VALUE);
    }

    /** Identifier of the cell containing the position, stable for the world seed. */
    public long cellId(double x, double z) {
        double fx = x * frequency;
        double fz = z * frequency;
        int baseX = MathUtil.floor(fx);
        int baseZ = MathUtil.floor(fz);
        double best = Double.MAX_VALUE;
        int bestX = baseX;
        int bestZ = baseZ;
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                int cx = baseX + ox;
                int cz = baseZ + oz;
                double px = cx + 0.5 + (Hashing.value(seed, cx, cz) - 0.5) * jitter * 2.0;
                double pz = cz + 0.5 + (Hashing.value(seed ^ 0x5DEECE66DL, cx, cz) - 0.5) * jitter * 2.0;
                double d = MathUtil.squareDistance(px - fx, pz - fz);
                if (d < best) {
                    best = d;
                    bestX = cx;
                    bestZ = cz;
                }
            }
        }
        return Hashing.hash(seed, bestX, bestZ);
    }

    /** Cell centre in world coordinates, used to anchor features to their cell. */
    public void cellCentre(double x, double z, double[] out) {
        double fx = x * frequency;
        double fz = z * frequency;
        int baseX = MathUtil.floor(fx);
        int baseZ = MathUtil.floor(fz);
        double best = Double.MAX_VALUE;
        double bestPx = fx;
        double bestPz = fz;
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                int cx = baseX + ox;
                int cz = baseZ + oz;
                double px = cx + 0.5 + (Hashing.value(seed, cx, cz) - 0.5) * jitter * 2.0;
                double pz = cz + 0.5 + (Hashing.value(seed ^ 0x5DEECE66DL, cx, cz) - 0.5) * jitter * 2.0;
                double d = MathUtil.squareDistance(px - fx, pz - fz);
                if (d < best) {
                    best = d;
                    bestPx = px;
                    bestPz = pz;
                }
            }
        }
        out[0] = bestPx / frequency;
        out[1] = bestPz / frequency;
    }

    private enum Result {
        F1,
        F2_MINUS_F1,
        VALUE
    }

    private double sample(double x, double z, Result result) {
        double fx = x * frequency;
        double fz = z * frequency;
        int baseX = MathUtil.floor(fx);
        int baseZ = MathUtil.floor(fz);

        double first = Double.MAX_VALUE;
        double second = Double.MAX_VALUE;
        int bestX = baseX;
        int bestZ = baseZ;

        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                int cx = baseX + ox;
                int cz = baseZ + oz;
                double px = cx + 0.5 + (Hashing.value(seed, cx, cz) - 0.5) * jitter * 2.0;
                double pz = cz + 0.5 + (Hashing.value(seed ^ 0x5DEECE66DL, cx, cz) - 0.5) * jitter * 2.0;
                double d = MathUtil.squareDistance(px - fx, pz - fz);
                if (d < first) {
                    second = first;
                    first = d;
                    bestX = cx;
                    bestZ = cz;
                } else if (d < second) {
                    second = d;
                }
            }
        }

        return switch (result) {
            case F1 -> MathUtil.clamp(Math.sqrt(first), 0.0, 1.0);
            case F2_MINUS_F1 -> MathUtil.clamp(Math.sqrt(second) - Math.sqrt(first), 0.0, 1.0);
            case VALUE -> (Hashing.hash(seed ^ 0x1234567L, bestX, bestZ) >>> 11) * 0x1.0p-53;
        };
    }
}
