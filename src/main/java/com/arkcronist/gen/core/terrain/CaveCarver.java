package com.arkcronist.gen.core.terrain;

import com.arkcronist.gen.core.math.MathUtil;
import com.arkcronist.gen.core.noise.CellularNoise;
import com.arkcronist.gen.core.noise.FractalNoise;

/**
 * The underground: winding tunnels, open cheese caves, big caverns and preset gated mega caves.
 *
 * <p>All four systems fold into a single "openness" scalar (positive means carved). The scalar
 * depends only on world position, never on the surface height, which is what allows it to be
 * evaluated on one coarse grid per chunk and interpolated per block - and why tunnels line up
 * perfectly across chunk borders with no cross-chunk bookkeeping at all.</p>
 *
 * <p>The surface dependent part (never carving through the last few metres of ground, so oceans and
 * lakes cannot drain into the cave system) is a separate multiplier applied per block.</p>
 */
public final class CaveCarver {

    private final TerrainSettings settings;
    private final FractalNoise cheese;
    private final FractalNoise tunnelA;
    private final FractalNoise tunnelB;
    private final FractalNoise cavern;
    private final FractalNoise mega;
    private final CellularNoise megaCells;

    public CaveCarver(long seed, TerrainSettings settings) {
        this.settings = settings;
        this.cheese = FractalNoise.fbm(seed, "caveCheese", 3, settings.caveCheeseFrequency);
        this.tunnelA = FractalNoise.fbm(seed, "tunnelA", 2, settings.tunnelFrequency);
        this.tunnelB = FractalNoise.fbm(seed, "tunnelB", 2, settings.tunnelFrequency * 1.07);
        this.cavern = FractalNoise.fbm(seed, "cavern", 3, settings.cavernFrequency);
        this.mega = FractalNoise.fbm(seed, "megaCave", 2, settings.megaCaveFrequency);
        this.megaCells = new CellularNoise(seed, "megaCaveCells", settings.megaCaveFrequency * 0.6, 0.9);
    }

    /** Position only openness. Positive means the world would be carved away here. */
    public double rawOpenness(int x, int y, int z) {
        if (!settings.caves) {
            return -1.0;
        }
        double best;

        // Cheese caves: irregular rooms, the classic void pockets you fall into.
        double cheeseSize = (1.0 - settings.caveCheeseThreshold) * 0.12;
        best = cheeseSize - Math.abs(cheese.noise3(x, y * 1.45, z));

        // Spaghetti tunnels: two thin noise sheets intersect into long winding corridors.
        double a = Math.abs(tunnelA.noise3(x, y * 0.85, z));
        double b = Math.abs(tunnelB.noise3(x + 517.0, y * 0.85, z - 311.0));
        best = Math.max(best, Math.min(settings.tunnelThreshold - a, settings.tunnelThreshold - b));

        // Caverns: open halls big enough to build inside.
        if (settings.cavernDensity > 0.0 && y >= settings.cavernMinY && y <= settings.cavernMaxY) {
            double band = verticalBand(y, settings.cavernMinY, settings.cavernMaxY);
            double threshold = 1.0 - settings.cavernDensity * 0.75;
            best = Math.max(best, (cavern.unsigned3(x, y * 2.1, z) - threshold) * band * 0.6);
        }

        // Mega caves: preset gated and cell clustered, tall enough to hold an underground structure.
        if (settings.megaCaveDensity > 0.0 && megaCells.cellValue(x, z) < settings.megaCaveDensity) {
            int top = Math.min(settings.seaLevel - 10, 48);
            double band = verticalBand(y, settings.minY + 8, top);
            if (band > 0.0) {
                best = Math.max(best, (mega.unsigned3(x, y * 1.25, z) - 0.60) * band * 1.1);
            }
        }
        return best;
    }

    /**
     * Surface dependent gate in [0,1].
     *
     * <p>Zero near the surface (so caves never open the ocean floor or a lake bed) and zero at the
     * very bottom of the world.</p>
     */
    public double gate(int y, double surface) {
        double roofClearance = surface - settings.surfaceCaveClearance - y;
        if (roofClearance < 0.0) {
            return 0.0;
        }
        double roofFade = MathUtil.smoothStep(MathUtil.normalize(roofClearance, 0.0, 8.0));
        double floorFade = MathUtil.smoothStep(MathUtil.normalize(y - settings.minY, 2.0, 12.0));
        return roofFade * floorFade;
    }

    /** 1 in the middle of the band, tapering to 0 at both ends. */
    private static double verticalBand(int y, int min, int max) {
        if (y <= min || y >= max) {
            return 0.0;
        }
        double t = (y - min) / (double) (max - min);
        return MathUtil.smoothStep(Math.min(t, 1.0 - t) * 2.0);
    }

    /** Builds the per chunk interpolation grid for the cave field. */
    public ScalarField3D field(int blockX, int blockZ, int minY, int maxY) {
        return ScalarField3D.build(blockX, minY, blockZ, maxY, 4, 4, this::rawOpenness);
    }
}
