package com.arkcronist.gen.core.terrain;

import com.arkcronist.gen.core.biome.ArkBiome;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.Hashing;
import com.arkcronist.gen.core.math.MathUtil;
import com.arkcronist.gen.core.noise.FractalNoise;

/**
 * Geological strata.
 *
 * <p>Instead of a uniform block of stone with a deepslate line through it, the underground is built
 * from horizontal bands whose boundaries are warped by low frequency noise, so a cliff face or a
 * canyon wall exposes readable layers that continue coherently for hundreds of blocks.</p>
 *
 * <p>Band materials are drawn per <em>province</em>: a large region shares the same sequence of rock
 * types, which is what makes strata look geological rather than random. Deepslate takes over below a
 * noisy transition depth, and the biome's own stone palette bleeds into the top bands so a volcanic
 * range shows basalt where a badlands shows terracotta.</p>
 */
public final class StrataSampler {

    private static final int[] SHALLOW_ROCKS = {
            Blocks.STONE, Blocks.STONE, Blocks.ANDESITE, Blocks.GRANITE, Blocks.DIORITE,
            Blocks.TUFF, Blocks.CALCITE, Blocks.GRAVEL, Blocks.SANDSTONE
    };

    private static final int[] DEEP_ROCKS = {
            Blocks.DEEPSLATE, Blocks.DEEPSLATE, Blocks.DEEPSLATE, Blocks.TUFF,
            Blocks.SMOOTH_BASALT, Blocks.BLACKSTONE, Blocks.CALCITE, Blocks.DRIPSTONE
    };

    private final TerrainSettings settings;
    private final long seed;
    private final FractalNoise warp;
    private final FractalNoise mottle;
    private final FractalNoise deepslateEdge;

    public StrataSampler(long seed, TerrainSettings settings) {
        this.seed = seed;
        this.settings = settings;
        this.warp = FractalNoise.fbm(seed, "strataWarp", 3, settings.strataFrequency);
        this.mottle = FractalNoise.fbm(seed, "strataMottle", 2, 0.055);
        this.deepslateEdge = FractalNoise.fbm(seed, "deepslateEdge", 2, 0.012);
    }

    /**
     * Per column strata offset. Sampling this once per column instead of once per block removes
     * roughly a hundred thousand noise evaluations per chunk - it was the single most expensive line
     * in the whole generator before it was hoisted out of the inner loop.
     */
    public double columnWarp(int x, int z) {
        return warp.noise2(x, z) * settings.strataWarp;
    }

    /** Per chunk grid for the band mottling noise, interpolated per block. */
    public ScalarField3D mottleField(int blockX, int blockZ, int minY, int maxY) {
        return ScalarField3D.build(blockX, minY, blockZ, maxY, 4, 4,
                (x, y, z) -> mottle.noise3(x, y * 1.6, z));
    }

    /**
     * Stone for a position deep under the surface.
     *
     * @param depth      how far below the surface this block is, in blocks
     * @param warpOffset value of {@link #columnWarp(int, int)} for this column
     * @param mottleValue interpolated mottling noise at this position
     * @param deep       whether this block is below the deepslate transition
     */
    public int stoneAt(int x, int y, int z, ArkBiome biome, double depth,
                       double warpOffset, double mottleValue, boolean deep) {
        double warped = y + warpOffset;
        int band = MathUtil.floor(warped / settings.strataThickness);

        // Geological provinces: a few hundred blocks wide, each with its own band sequence.
        int provinceX = x >> 8;
        int provinceZ = z >> 8;
        long bandSeed = Hashing.hash3(seed, provinceX, band, provinceZ);

        int[] table = deep ? DEEP_ROCKS : SHALLOW_ROCKS;
        int rock = table[(int) ((bandSeed >>> 24) % table.length)];

        // The top few metres of rock belong to the biome, so cliffs show local stone.
        if (!deep && depth < 12.0) {
            double blend = MathUtil.normalize(depth, 12.0, 3.0);
            if (Hashing.value3(seed ^ 0x9F1D, x, y, z) < blend) {
                rock = biome.stone.pickAt(seed, x, y, z);
            }
        }

        // Mottling: neighbouring bands interleave slightly instead of meeting on a razor line.
        if (mottleValue > 0.62) {
            long neighbourSeed = Hashing.hash3(seed, provinceX, band + 1, provinceZ);
            rock = table[(int) ((neighbourSeed >>> 24) % table.length)];
        }
        return rock;
    }

    /** Noisy deepslate transition depth. */
    public double deepslateLevel(int x, int z) {
        return 4.0 + deepslateEdge.noise2(x, z) * 9.0;
    }
}
