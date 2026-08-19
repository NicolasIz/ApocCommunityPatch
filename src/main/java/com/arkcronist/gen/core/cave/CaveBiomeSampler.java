package com.arkcronist.gen.core.cave;

import com.arkcronist.gen.core.math.Hashing;
import com.arkcronist.gen.core.noise.CellularNoise;
import com.arkcronist.gen.core.noise.FractalNoise;
import com.arkcronist.gen.core.terrain.TerrainSettings;

/**
 * Assigns a cave biome to a position.
 *
 * <p>Regions are cellular, so a cave biome covers a coherent patch of the underground instead of
 * flickering block by block. Depth and the surface climate both matter: the deep dark only exists
 * far down, ice caves only under cold country, magma caves only near the bottom.</p>
 */
public final class CaveBiomeSampler {

    private final TerrainSettings settings;
    private final CellularNoise regions;
    private final FractalNoise variation;
    private final long seed;

    public CaveBiomeSampler(long seed, TerrainSettings settings) {
        this.seed = seed;
        this.settings = settings;
        this.regions = new CellularNoise(seed, "caveRegions", 0.0016, 0.9);
        this.variation = FractalNoise.fbm(seed, "caveVariation", 2, 0.0034);
    }

    /**
     * Region roll for a column. The cellular lookup behind it is the expensive half of cave biome
     * selection, so callers hoist it out of the per block loop and hand it back to
     * {@link #resolve(double, double, int, double)}.
     */
    public double regionRoll(int x, int z) {
        return regions.cellValue(x, z);
    }

    public double regionBlend(int x, int z) {
        return variation.unsigned2(x, z);
    }

    /**
     * @param surfaceTemperature climate temperature of the column above, -1 cold to 1 hot
     */
    public CaveBiome biomeAt(int x, int y, int z, double surfaceTemperature) {
        return resolve(regionRoll(x, z), regionBlend(x, z), y, surfaceTemperature);
    }

    /** Cheap half of the decision: depth and climate rules over an already sampled region. */
    public CaveBiome resolve(double roll, double blend, int y, double surfaceTemperature) {
        int deepDarkCeiling = settings.minY + 40;

        // The deep dark owns the bottom of the world, in patches rather than a solid layer.
        if (y < deepDarkCeiling && roll < 0.34) {
            return CaveBiome.DEEP_DARK;
        }
        // Magma country: the last few dozen blocks above bedrock.
        if (y < settings.minY + 26 && roll > 0.78) {
            return CaveBiome.MAGMA;
        }
        if (surfaceTemperature < -0.45 && y > settings.seaLevel - 60 && roll > 0.62) {
            return CaveBiome.ICE;
        }
        if (roll < 0.16) {
            return CaveBiome.LUSH;
        }
        if (roll < 0.30) {
            return CaveBiome.DRIPSTONE;
        }
        if (roll < 0.37 && blend > 0.45) {
            return CaveBiome.MUSHROOM;
        }
        if (roll < 0.44 && y < 20) {
            return CaveBiome.CRYSTAL;
        }
        return CaveBiome.STONE;
    }

    /** True where an amethyst geode may be seeded. */
    public boolean geodeSite(int x, int z) {
        return Hashing.value(seed ^ 0x6E0DEL, x >> 5, z >> 5) < 0.020;
    }
}
