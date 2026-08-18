package com.arkcronist.gen.core.noise;

import com.arkcronist.gen.core.math.Hashing;

/**
 * Domain warping: the single cheapest trick that makes procedural terrain stop looking procedural.
 *
 * <p>Instead of sampling a field at (x,z) it is sampled at (x+dx, z+dz) where the offsets come from
 * their own low frequency noise. Straight ridges become sinuous, coastlines gain fjords and inlets,
 * and biome borders stop looking like contour lines.</p>
 */
public final class DomainWarp {

    private final SimplexNoise warpX;
    private final SimplexNoise warpZ;
    private final SimplexNoise warpY;
    private final double frequency;
    private final double amplitude;

    public DomainWarp(long seed, String name, double frequency, double amplitude) {
        this.warpX = new SimplexNoise(Hashing.salt(seed, name + "#warpX"));
        this.warpZ = new SimplexNoise(Hashing.salt(seed, name + "#warpZ"));
        this.warpY = new SimplexNoise(Hashing.salt(seed, name + "#warpY"));
        this.frequency = frequency;
        this.amplitude = amplitude;
    }

    public double warpedX(double x, double z) {
        return x + warpX.noise2(x * frequency, z * frequency) * amplitude;
    }

    public double warpedZ(double x, double z) {
        return z + warpZ.noise2(x * frequency + 137.13, z * frequency - 91.7) * amplitude;
    }

    public double warpedY(double x, double y, double z) {
        return y + warpY.noise3(x * frequency, y * frequency * 0.5, z * frequency) * amplitude;
    }

    public double amplitude() {
        return amplitude;
    }
}
