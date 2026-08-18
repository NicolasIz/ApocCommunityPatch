package com.arkcronist.gen.core.terrain;

import com.arkcronist.gen.core.biome.ArkBiome;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.Hashing;
import com.arkcronist.gen.core.math.MathUtil;
import com.arkcronist.gen.core.noise.FractalNoise;

/**
 * Ore placement.
 *
 * <p>Ores are not scattered per block: a low frequency 3D "vein" field decides where a deposit
 * exists, and a per block hash fills that deposit. The vein field is sampled on the same coarse grid
 * machinery as caves, so deposits form blobs and ribbons that continue across chunk borders, and the
 * per block cost stays a single hash for the overwhelming majority of stone.</p>
 *
 * <p>Depth ranges follow the familiar vanilla intuition (coal high, diamond deep) while the biome's
 * ore bias tilts the mix: a volcanic range yields more gold and copper, peaks yield emerald.</p>
 */
public final class OreSampler {

    private final TerrainSettings settings;
    private final long seed;
    private final FractalNoise vein;

    public OreSampler(long seed, TerrainSettings settings) {
        this.seed = seed;
        this.settings = settings;
        this.vein = FractalNoise.fbm(seed, "oreVein", 2, 0.028);
    }

    public ScalarField3D veinField(int blockX, int blockZ, int minY, int maxY) {
        return ScalarField3D.build(blockX, minY, blockZ, maxY, 4, 4,
                (x, y, z) -> vein.noise3(x, y, z));
    }

    /**
     * Returns an ore block id, or {@code -1} to keep the stone.
     *
     * @param veinValue value of the vein field at this position
     * @param deepslate whether the surrounding stone is deepslate
     */
    public int oreAt(int x, int y, int z, ArkBiome biome, double veinValue, boolean deepslate) {
        // Cheap rejection first: the hash costs a few operations, the vein lookup and the ore weights
        // cost far more, and over 80% of stone blocks never become ore.
        double roll = Hashing.value3(seed ^ 0x0FEED5L, x, y, z);
        if (roll > 0.16 * settings.oreMultiplier) {
            return -1;
        }
        double strength = MathUtil.normalize(veinValue, 0.34, 0.85);
        if (strength <= 0.0 || roll > 0.16 * strength * settings.oreMultiplier) {
            return -1;
        }

        double pick = Hashing.value3(seed ^ 0x51DE7L, x, y, z);
        return chooseOre(y, biome, pick, deepslate);
    }

    private int chooseOre(int y, ArkBiome biome, double pick, boolean deepslate) {
        double coal = weight(y, 0, 190, 60) * biome.coalBonus * 1.35;
        double copper = weight(y, -16, 100, 48) * biome.copperBonus;
        double iron = (weight(y, -24, 190, 16) + weight(y, 60, 220, 140) * 0.6) * biome.ironBonus;
        double gold = weight(y, -64, 40, -18) * biome.goldBonus * 0.55;
        double redstone = weight(y, -64, 16, -58) * biome.redstoneBonus * 0.9;
        double lapis = weight(y, -48, 64, 0) * biome.lapisBonus * 0.35;
        double diamond = weight(y, -64, 12, -58) * biome.diamondBonus * 0.28;
        double emerald = weight(y, -16, 256, 200) * biome.emeraldBonus * 0.22;

        double total = coal + copper + iron + gold + redstone + lapis + diamond + emerald;
        if (total <= 0.0) {
            return -1;
        }
        double target = pick * total;
        double running = coal;
        if (target < running) {
            return deepslate ? Blocks.DEEPSLATE_COAL_ORE : Blocks.COAL_ORE;
        }
        running += copper;
        if (target < running) {
            return deepslate ? Blocks.DEEPSLATE_COPPER_ORE : Blocks.COPPER_ORE;
        }
        running += iron;
        if (target < running) {
            return deepslate ? Blocks.DEEPSLATE_IRON_ORE : Blocks.IRON_ORE;
        }
        running += gold;
        if (target < running) {
            return deepslate ? Blocks.DEEPSLATE_GOLD_ORE : Blocks.GOLD_ORE;
        }
        running += redstone;
        if (target < running) {
            return deepslate ? Blocks.DEEPSLATE_REDSTONE_ORE : Blocks.REDSTONE_ORE;
        }
        running += lapis;
        if (target < running) {
            return deepslate ? Blocks.DEEPSLATE_LAPIS_ORE : Blocks.LAPIS_ORE;
        }
        running += diamond;
        if (target < running) {
            return deepslate ? Blocks.DEEPSLATE_DIAMOND_ORE : Blocks.DIAMOND_ORE;
        }
        return deepslate ? Blocks.DEEPSLATE_EMERALD_ORE : Blocks.EMERALD_ORE;
    }

    /** Triangular distribution: peaks at {@code peak}, zero outside [min,max]. */
    private static double weight(int y, int min, int max, int peak) {
        if (y < min || y > max) {
            return 0.0;
        }
        return y <= peak
                ? MathUtil.normalize(y, min, peak)
                : MathUtil.normalize(y, max, peak);
    }
}
