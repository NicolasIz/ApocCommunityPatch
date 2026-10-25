package com.arkcronist.content.core.storage;

/**
 * A block position packed into one {@code long}, the same layout Minecraft uses for its own block
 * positions: 26 bits of x, 26 bits of z, 12 bits of y. That covers x and z to +-33 million - past the
 * world border - and y from -2048 to 2047, beyond any height a datapack can set.
 *
 * <p>Used as the key of the in-memory index, so a lookup costs no object for the position.</p>
 */
public final class BlockKey {

    private static final int XZ_BITS = 26;
    private static final int Y_BITS = 12;

    private BlockKey() {
    }

    public static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << (XZ_BITS + Y_BITS)
                | ((long) z & 0x3FFFFFFL) << Y_BITS
                | ((long) y & 0xFFFL);
    }

    public static int x(long key) {
        return (int) (key >> (XZ_BITS + Y_BITS));
    }

    public static int y(long key) {
        return (int) (key << (64 - Y_BITS) >> (64 - Y_BITS));
    }

    public static int z(long key) {
        return (int) (key << (64 - XZ_BITS - Y_BITS) >> (64 - XZ_BITS));
    }
}
