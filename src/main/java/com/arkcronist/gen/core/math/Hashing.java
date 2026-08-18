package com.arkcronist.gen.core.math;

/**
 * Coordinate hashing used everywhere a value must be reproducible from position alone.
 *
 * <p>Position hashing (rather than sequential random state) is what makes the generator order
 * independent: a chunk generated first or last, on any thread, sees the same numbers.</p>
 */
public final class Hashing {

    private static final long PRIME_X = 0x9E3779B97F4A7C15L;
    private static final long PRIME_Y = 0xC2B2AE3D27D4EB4FL;
    private static final long PRIME_Z = 0x165667B19E3779F9L;

    private Hashing() {
    }

    public static long hash(long seed, int x, int z) {
        long h = seed ^ (x * PRIME_X) ^ (z * PRIME_Z);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        return h ^ (h >>> 33);
    }

    public static long hash3(long seed, int x, int y, int z) {
        long h = seed ^ (x * PRIME_X) ^ (y * PRIME_Y) ^ (z * PRIME_Z);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 29;
        h *= 0xC4CEB9FE1A85EC53L;
        return h ^ (h >>> 32);
    }

    /** Deterministic value in {@code [0,1)} for a column. */
    public static double value(long seed, int x, int z) {
        return (hash(seed, x, z) >>> 11) * 0x1.0p-53;
    }

    /** Deterministic value in {@code [0,1)} for a block position. */
    public static double value3(long seed, int x, int y, int z) {
        return (hash3(seed, x, y, z) >>> 11) * 0x1.0p-53;
    }

    /** Deterministic integer in {@code [0,bound)} for a column. */
    public static int valueInt(long seed, int x, int z, int bound) {
        return (int) ((hash(seed, x, z) >>> 33) % bound);
    }

    /** Mixes a name into a seed so every sampler in the pipeline gets its own noise field. */
    public static long salt(long seed, String name) {
        long h = 0xCBF29CE484222325L;
        for (int i = 0; i < name.length(); i++) {
            h ^= name.charAt(i);
            h *= 0x100000001B3L;
        }
        return hash3(seed, (int) h, (int) (h >>> 21), (int) (h >>> 42));
    }
}
