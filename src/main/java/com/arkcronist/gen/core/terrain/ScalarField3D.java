package com.arkcronist.gen.core.terrain;

import com.arkcronist.gen.core.math.MathUtil;

/**
 * A coarse 3D scalar grid over one chunk, sampled with trilinear interpolation.
 *
 * <p>Evaluating 3D noise per block is what makes naive generators slow: a single chunk holds tens of
 * thousands of blocks. Sampling every four blocks horizontally and every four to eight vertically and
 * interpolating cuts the noise work by one to two orders of magnitude while staying visually
 * identical for cave-scale features. The grid always covers the full chunk plus one closing node, so
 * neighbouring chunks interpolate from the same values on their shared faces.</p>
 */
public final class ScalarField3D {

    /** Source of the underlying scalar, evaluated only at grid nodes. */
    public interface Source {
        double sample(int x, int y, int z);
    }

    private final float[] data;
    private final int nx;
    private final int ny;
    private final int nz;
    private final int x0;
    private final int y0;
    private final int z0;
    private final int stepXZ;
    private final int stepY;

    private ScalarField3D(float[] data, int nx, int ny, int nz, int x0, int y0, int z0, int stepXZ, int stepY) {
        this.data = data;
        this.nx = nx;
        this.ny = ny;
        this.nz = nz;
        this.x0 = x0;
        this.y0 = y0;
        this.z0 = z0;
        this.stepXZ = stepXZ;
        this.stepY = stepY;
    }

    /**
     * Builds a field covering {@code [blockX, blockX+16)} x {@code [minY, maxY]} x {@code [blockZ, blockZ+16)}.
     */
    public static ScalarField3D build(int blockX, int minY, int blockZ, int maxY, int stepXZ, int stepY, Source source) {
        int nx = 16 / stepXZ + 1;
        int nz = 16 / stepXZ + 1;
        int span = Math.max(stepY, maxY - minY);
        int ny = span / stepY + 2;
        float[] data = new float[nx * ny * nz];
        int i = 0;
        for (int yi = 0; yi < ny; yi++) {
            int y = minY + yi * stepY;
            for (int zi = 0; zi < nz; zi++) {
                int z = blockZ + zi * stepXZ;
                for (int xi = 0; xi < nx; xi++) {
                    data[i++] = (float) source.sample(blockX + xi * stepXZ, y, z);
                }
            }
        }
        return new ScalarField3D(data, nx, ny, nz, blockX, minY, blockZ, stepXZ, stepY);
    }

    /** Trilinear lookup in world coordinates. Positions outside the field clamp to its edge. */
    public double get(int x, int y, int z) {
        double fx = (x - x0) / (double) stepXZ;
        double fy = (y - y0) / (double) stepY;
        double fz = (z - z0) / (double) stepXZ;

        int ix = MathUtil.clamp((int) Math.floor(fx), 0, nx - 2);
        int iy = MathUtil.clamp((int) Math.floor(fy), 0, ny - 2);
        int iz = MathUtil.clamp((int) Math.floor(fz), 0, nz - 2);

        double tx = MathUtil.clamp(fx - ix, 0.0, 1.0);
        double ty = MathUtil.clamp(fy - iy, 0.0, 1.0);
        double tz = MathUtil.clamp(fz - iz, 0.0, 1.0);

        double c000 = at(ix, iy, iz);
        double c100 = at(ix + 1, iy, iz);
        double c010 = at(ix, iy + 1, iz);
        double c110 = at(ix + 1, iy + 1, iz);
        double c001 = at(ix, iy, iz + 1);
        double c101 = at(ix + 1, iy, iz + 1);
        double c011 = at(ix, iy + 1, iz + 1);
        double c111 = at(ix + 1, iy + 1, iz + 1);

        double bottom = MathUtil.bilinear(tx, tz, c000, c100, c001, c101);
        double top = MathUtil.bilinear(tx, tz, c010, c110, c011, c111);
        return MathUtil.lerp(ty, bottom, top);
    }

    private double at(int xi, int yi, int zi) {
        return data[(yi * nz + zi) * nx + xi];
    }

    public int nodeCount() {
        return data.length;
    }
}
