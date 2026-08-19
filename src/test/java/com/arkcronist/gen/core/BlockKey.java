package com.arkcronist.gen.core;

/** Packs a block position into one long so tests can collect writes in a plain map. */
final class BlockKey {

    private BlockKey() {
    }

    static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (y & 0xFFF) << 26) | (z & 0x3FFFFFFL);
    }
}
