package com.arkcronist.gen.core.block;

import com.arkcronist.gen.core.math.Hashing;

/**
 * A weighted set of block ids.
 *
 * <p>Palettes are sampled either from a random value (decoration) or from a position hash (strata,
 * surface speckling) so the same stone patch always comes out the same way.</p>
 */
public final class Palette {

    private final int[] blocks;
    private final double[] cumulative;

    private Palette(int[] blocks, double[] cumulative) {
        this.blocks = blocks;
        this.cumulative = cumulative;
    }

    public static Palette single(int block) {
        return new Palette(new int[]{block}, new double[]{1.0});
    }

    /** Builds a palette from alternating block/weight pairs. */
    public static Palette of(int block, double weight, Object... rest) {
        int count = 1 + rest.length / 2;
        int[] blocks = new int[count];
        double[] cumulative = new double[count];
        blocks[0] = block;
        double total = weight;
        cumulative[0] = total;
        for (int i = 0; i < rest.length; i += 2) {
            int index = 1 + i / 2;
            blocks[index] = (Integer) rest[i];
            total += ((Number) rest[i + 1]).doubleValue();
            cumulative[index] = total;
        }
        for (int i = 0; i < count; i++) {
            cumulative[i] /= total;
        }
        return new Palette(blocks, cumulative);
    }

    public int pick(double random01) {
        for (int i = 0; i < cumulative.length; i++) {
            if (random01 < cumulative[i]) {
                return blocks[i];
            }
        }
        return blocks[blocks.length - 1];
    }

    public int pickAt(long seed, int x, int y, int z) {
        return pick(Hashing.value3(seed, x, y, z));
    }

    public int first() {
        return blocks[0];
    }

    public int size() {
        return blocks.length;
    }
}
