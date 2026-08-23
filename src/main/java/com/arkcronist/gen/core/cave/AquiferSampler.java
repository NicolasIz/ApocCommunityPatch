package com.arkcronist.gen.core.cave;

import com.arkcronist.gen.core.math.MathUtil;
import com.arkcronist.gen.core.noise.FractalNoise;
import com.arkcronist.gen.core.terrain.TerrainSettings;

/**
 * The lava table: how high molten rock stands in a region.
 *
 * <p>There is deliberately no water table here any more. A table is a height, and filling every
 * cavity that happens to lie below one is what put water in sealed caves, under caves, and at
 * several different levels on the same hillside. Water is decided by connection instead - see the
 * column pass in the terrain engine - and the only liquid a closed cave can hold is this one,
 * sitting just off the bedrock the way it does in the vanilla world.</p>
 */
public final class AquiferSampler {

    private final TerrainSettings settings;
    private final FractalNoise lavaPresence;
    private final FractalNoise lavaLevel;

    public AquiferSampler(long seed, TerrainSettings settings) {
        this.settings = settings;
        this.lavaPresence = FractalNoise.fbm(seed, "lavaPresence", 2, 0.0017);
        this.lavaLevel = FractalNoise.fbm(seed, "lavaLevel", 2, 0.0011);
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
