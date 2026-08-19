package com.arkcronist.gen.core.cave;

import com.arkcronist.gen.core.math.MathUtil;
import com.arkcronist.gen.core.noise.FractalNoise;
import com.arkcronist.gen.core.terrain.TerrainSettings;

/**
 * Underground water and lava tables.
 *
 * <p>Without this, every carved cavity is dry air and the bottom of the world turns into one
 * continuous lava ocean. With it, each region has its own local water table and its own, much lower,
 * lava table: some caves are flooded lakes, some are dry, and lava stays in pools near the bedrock
 * instead of flooding everything below a fixed line.</p>
 */
public final class AquiferSampler {

    /** Returned when a region has no water table at all. */
    public static final int NO_WATER = Integer.MIN_VALUE;

    private final TerrainSettings settings;
    private final FractalNoise presence;
    private final FractalNoise level;
    private final FractalNoise lavaPresence;
    private final FractalNoise lavaLevel;

    public AquiferSampler(long seed, TerrainSettings settings) {
        this.settings = settings;
        this.presence = FractalNoise.fbm(seed, "aquiferPresence", 2, 0.0021);
        this.level = FractalNoise.fbm(seed, "aquiferLevel", 2, 0.0013);
        this.lavaPresence = FractalNoise.fbm(seed, "lavaPresence", 2, 0.0017);
        this.lavaLevel = FractalNoise.fbm(seed, "lavaLevel", 2, 0.0011);
    }

    /**
     * Water table height for a region, or {@link #NO_WATER} where the rock is dry.
     *
     * <p>Kept below the surface everywhere so a flooded cave never spills out of a hillside.</p>
     */
    public int waterTable(int x, int z, double surfaceHeight) {
        if (presence.noise2(x, z) < 0.10) {
            return NO_WATER;
        }
        double t = presence.unsigned2(x, z);
        double raw = MathUtil.lerp(MathUtil.normalize(level.noise2(x, z), -0.6, 0.6),
                settings.minY + 12.0, settings.seaLevel - 8.0);
        double capped = Math.min(raw, surfaceHeight - 12.0);
        int result = (int) Math.floor(capped);
        // Faint tables produce shallow puddles rather than full lakes.
        if (t < 0.55) {
            result = (int) Math.floor(Math.min(capped, settings.minY + 34.0));
        }
        return result <= settings.minY + 4 ? NO_WATER : result;
    }

    /**
     * Lava table height: normally right above bedrock, rising into a lava lake only in the regions
     * that roll for one.
     */
    public int lavaTable(int x, int z) {
        double presenceValue = lavaPresence.noise2(x, z);
        int floor = settings.minY + 6;
        if (presenceValue < 0.28) {
            return floor;
        }
        double t = MathUtil.normalize(presenceValue, 0.28, 0.75);
        double raw = MathUtil.lerp(t, floor, Math.min(settings.lavaLevel, settings.minY + 22.0));
        raw += lavaLevel.noise2(x, z) * 3.0;
        return (int) Math.floor(raw);
    }
}
