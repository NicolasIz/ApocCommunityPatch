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
    private final FractalNoise cavernCentre;
    private final FractalNoise megaCentre;

    public CaveCarver(long seed, TerrainSettings settings) {
        this.settings = settings;
        this.cheese = FractalNoise.fbm(seed, "caveCheese", 3, settings.caveCheeseFrequency);
        this.tunnelA = FractalNoise.fbm(seed, "tunnelA", 2, settings.tunnelFrequency);
        this.tunnelB = FractalNoise.fbm(seed, "tunnelB", 2, settings.tunnelFrequency * 1.07);
        this.cavern = FractalNoise.fbm(seed, "cavern", 3, settings.cavernFrequency);
        this.mega = FractalNoise.fbm(seed, "megaCave", 2, settings.megaCaveFrequency);
        this.megaCells = new CellularNoise(seed, "megaCaveCells", settings.megaCaveFrequency * 0.6, 0.9);
        this.cavernCentre = FractalNoise.fbm(seed, "cavernCentre", 2, settings.cavernFrequency * 0.45);
        this.megaCentre = FractalNoise.fbm(seed, "megaCentre", 2, settings.megaCaveFrequency * 0.5);
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

        // Caverns: open halls big enough to build inside. Each one sits on its own level rather than
        // spanning the whole underground, which is what keeps them halls instead of voids.
        if (settings.cavernDensity > 0.0 && y >= settings.cavernMinY && y <= settings.cavernMaxY) {
            double centre = MathUtil.lerp(cavernCentre.unsigned2(x, z), settings.cavernMinY + 10,
                    settings.cavernMaxY - 10);
            double band = layer(y, centre, 10.0);
            if (band > 0.0) {
                double threshold = 1.0 - settings.cavernDensity * 0.55;
                best = Math.max(best, (cavern.unsigned3(x, y * 2.1, z) - threshold) * band * 0.6);
            }
        }

        // Mega caves: preset gated and cell clustered, tall enough to hold an underground structure -
        // but still a room with a floor and a roof, never a hollowed out world.
        if (settings.megaCaveDensity > 0.0 && megaCells.cellValue(x, z) < settings.megaCaveDensity) {
            int top = Math.min(settings.seaLevel - 14, 40);
            double centre = MathUtil.lerp(megaCentre.unsigned2(x, z), settings.minY + 22, top);
            // Thirteen, not twenty. A forty block tall room swallows whatever the server puts down
            // there: an ancient city is written into the terrain the carver already left, so where
            // a hall that size overlaps one, the city ends up hanging in the void in pieces.
            double band = layer(y, centre, 13.0);
            if (band > 0.0) {
                best = Math.max(best, (mega.unsigned3(x, y * 1.25, z) - 0.55) * band * 0.9);
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
        return gate(y, surface, settings.surfaceCaveClearance);
    }

    /** Gate with an explicit roof thickness, so sea floors can demand a thicker one. */
    public double gate(int y, double surface, int clearance) {
        double roofClearance = surface - clearance - y;
        if (roofClearance < 0.0) {
            return 0.0;
        }
        double roofFade = MathUtil.smoothStep(MathUtil.normalize(roofClearance, 0.0, 8.0));
        double floorFade = MathUtil.smoothStep(MathUtil.normalize(y - settings.minY, 2.0, 12.0));
        return roofFade * floorFade;
    }

    /** 1 at the centre of a layer, fading to 0 at {@code halfHeight} blocks away. */
    private static double layer(int y, double centre, double halfHeight) {
        double distance = Math.abs(y - centre);
        if (distance >= halfHeight) {
            return 0.0;
        }
        return MathUtil.smoothStep(1.0 - distance / halfHeight);
    }

    /**
     * Builds the per chunk interpolation grid for the cave field.
     *
     * <p>Four blocks horizontally, six vertically: caves are wider than they are tall at this scale,
     * and the coarser vertical step cuts a third of the noise work out of the hottest loop in the
     * generator without visibly rounding tunnels off.</p>
     */
    public ScalarField3D field(int blockX, int blockZ, int minY, int maxY) {
        return ScalarField3D.build(blockX, minY, blockZ, maxY, 4, 6, this::rawOpenness);
    }
}
