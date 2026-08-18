package com.arkcronist.gen.core.math;

/**
 * A small xoroshiro128++ generator.
 *
 * <p>{@link java.util.Random} is synchronised and comparatively slow; world generation asks for
 * millions of numbers per second across several threads, so the generator used by decoration and
 * structures is a plain, non shared, value based PRNG. Instances are cheap: create one per
 * chunk/feature from a deterministic seed and throw it away.</p>
 */
public final class FastRandom {

    private long s0;
    private long s1;

    public FastRandom(long seed) {
        setSeed(seed);
    }

    public static FastRandom forChunk(long worldSeed, int chunkX, int chunkZ, long salt) {
        return new FastRandom(Hashing.hash(worldSeed ^ salt, chunkX, chunkZ));
    }

    public static FastRandom forBlock(long worldSeed, int x, int y, int z, long salt) {
        return new FastRandom(Hashing.hash3(worldSeed ^ salt, x, y, z));
    }

    public void setSeed(long seed) {
        long z = seed + 0x9E3779B97F4A7C15L;
        this.s0 = mix(z);
        this.s1 = mix(z + 0x9E3779B97F4A7C15L);
        if ((s0 | s1) == 0L) {
            this.s0 = 0x2545F4914F6CDD1DL;
            this.s1 = 0x9E3779B97F4A7C15L;
        }
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public long nextLong() {
        long x = s0;
        long y = s1;
        long result = Long.rotateLeft(x + y, 17) + x;
        y ^= x;
        s0 = Long.rotateLeft(x, 49) ^ y ^ (y << 21);
        s1 = Long.rotateLeft(y, 28);
        return result;
    }

    public int nextInt() {
        return (int) (nextLong() >>> 32);
    }

    /** Uniform value in {@code [0, bound)}. */
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive: " + bound);
        }
        int r = nextInt() >>> 1;
        int m = bound - 1;
        if ((bound & m) == 0) {
            return r & m;
        }
        for (int u = r; u - (r = u % bound) + m < 0; u = nextInt() >>> 1) {
            // retry to stay uniform
        }
        return r;
    }

    /** Uniform value in {@code [min, max]} inclusive. */
    public int nextInt(int min, int max) {
        return max <= min ? min : min + nextInt(max - min + 1);
    }

    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    public double nextDouble(double min, double max) {
        return min + nextDouble() * (max - min);
    }

    public float nextFloat() {
        return (nextLong() >>> 40) * 0x1.0p-24f;
    }

    public boolean nextBoolean() {
        return (nextLong() & 1L) != 0L;
    }

    /** Returns true with the given probability. */
    public boolean chance(double probability) {
        return probability > 0.0 && (probability >= 1.0 || nextDouble() < probability);
    }

    /** Gaussian-ish value via the sum of three uniforms; fast and good enough for decoration. */
    public double nextBell() {
        return (nextDouble() + nextDouble() + nextDouble()) / 1.5 - 1.0;
    }

    /** Derives an independent generator, useful to keep sub-features from consuming shared state. */
    public FastRandom fork(long salt) {
        return new FastRandom(nextLong() ^ (salt * 0x9E3779B97F4A7C15L));
    }
}
