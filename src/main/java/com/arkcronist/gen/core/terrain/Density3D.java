package com.arkcronist.gen.core.terrain;

import com.arkcronist.gen.core.math.MathUtil;
import com.arkcronist.gen.core.noise.CellularNoise;
import com.arkcronist.gen.core.noise.FractalNoise;

/**
 * The stages that make the world stop being a heightmap.
 *
 * <p>Two effects live here:</p>
 * <ul>
 *     <li><b>Overhangs and arches</b> - a 3D field added to the "is this block below the surface"
 *     test inside a band around the surface. Positive lobes push rock out over empty air (ledges,
 *     shelves, undercut cliffs), negative lobes punch holes through it, and where a hole opens under
 *     a lobe you get a natural arch.</li>
 *     <li><b>Floating islands</b> - lens shaped masses with a flat-ish top and a tapering underside,
 *     clustered by cellular noise so they form archipelagos in the sky rather than uniform confetti.</li>
 * </ul>
 */
public final class Density3D {

    private final TerrainSettings settings;
    private final FractalNoise overhang;
    private final FractalNoise arch;
    private final FractalNoise islandMask;
    private final FractalNoise islandCentre;
    private final FractalNoise islandDetail;
    private final CellularNoise islandClusters;

    public Density3D(long seed, TerrainSettings settings) {
        this.settings = settings;
        this.overhang = FractalNoise.fbm(seed, "overhang", 3, settings.overhangFrequency);
        this.arch = FractalNoise.ridged(seed, "arch", 2, settings.overhangFrequency * 0.65);
        this.islandMask = FractalNoise.fbm(seed, "islandMask", 4, settings.floatingIslandFrequency);
        this.islandCentre = FractalNoise.fbm(seed, "islandCentre", 2, settings.floatingIslandFrequency * 0.5);
        this.islandDetail = FractalNoise.fbm(seed, "islandDetail", 3, settings.floatingIslandFrequency * 6.0);
        this.islandClusters = new CellularNoise(seed, "islandClusters", settings.floatingIslandFrequency * 0.22, 0.9);
    }

    public boolean overhangsEnabled() {
        return settings.overhangStrength > 0.0 || settings.archStrength > 0.0;
    }

    public boolean floatingIslandsEnabled() {
        return settings.floatingIslandDensity > 0.0;
    }

    /**
     * Position only part of the overhang/arch field.
     *
     * <p>Kept free of any dependency on the column height so it can be evaluated on a coarse grid and
     * interpolated; the height dependent shaping is applied per block by
     * {@link #shapeDelta(double, int, double, double)}.</p>
     */
    public double rawSurfaceNoise(int x, int y, int z) {
        double delta = 0.0;
        if (settings.overhangStrength > 0.0) {
            delta += overhang.noise3(x, y * 0.8, z) * settings.overhangStrength * 14.0;
        }
        if (settings.archStrength > 0.0) {
            double window = MathUtil.normalize(arch.unsigned3(x, y * 0.55, z), 0.62, 1.0);
            if (window > 0.0) {
                delta -= Math.pow(window, 1.4) * settings.archStrength * 20.0;
            }
        }
        return delta;
    }

    /**
     * Signed offset added to {@code surface - y}: positive means "more rock here than the heightmap
     * says" (ledges, undercut cliffs), negative means "carve this away" (windows and arches).
     */
    public double shapeDelta(double rawNoise, int y, double surface, double mountainFactor) {
        double distance = Math.abs(y - surface);
        if (distance > settings.overhangBand) {
            return 0.0;
        }
        double band = MathUtil.smoothStep(1.0 - distance / settings.overhangBand);
        // Overhangs belong on steep ground; flat plains stay flat.
        double relief = MathUtil.smoothStep(MathUtil.normalize(mountainFactor, 0.10, 0.65));
        return rawNoise * band * (0.35 + relief);
    }

    /** Per chunk grid for the overhang/arch noise. */
    public ScalarField3D surfaceField(int blockX, int blockZ, int minY, int maxY) {
        return ScalarField3D.build(blockX, minY, blockZ, maxY, 4, 4, this::rawSurfaceNoise);
    }

    /** Cheap per column rejection: whole columns outside an island cluster are skipped entirely. */
    public boolean islandPossible(int x, int z) {
        return settings.floatingIslandDensity > 0.0
                && islandClusters.cellValue(x, z) <= settings.floatingIslandDensity;
    }

    public int islandMinY() {
        return settings.floatingIslandMinY;
    }

    public int islandMaxY() {
        return settings.floatingIslandMaxY;
    }

    /**
     * Density of a floating island at this position; positive means solid.
     *
     * <p>Islands are lens shaped: a broad, slightly domed top surface for building on, and a long
     * tapering underside so they read as torn-off chunks of land rather than flying pancakes.</p>
     */
    public double islandDensity(int x, int y, int z) {
        if (settings.floatingIslandDensity <= 0.0
                || y < settings.floatingIslandMinY || y > settings.floatingIslandMaxY) {
            return -1.0;
        }
        double cluster = islandClusters.cellValue(x, z);
        if (cluster > settings.floatingIslandDensity) {
            return -1.0;
        }

        double mask = islandMask.unsigned2(x, z);
        if (mask < 0.56) {
            return -1.0;
        }
        double core = MathUtil.normalize(mask, 0.56, 0.95);

        double bandMin = settings.floatingIslandMinY;
        double bandMax = settings.floatingIslandMaxY;
        double centre = MathUtil.lerp(islandCentre.unsigned2(x, z), bandMin + 12.0, bandMax - 12.0);
        double thickness = (7.0 + core * 26.0) * settings.floatingIslandSize;

        double dy = y - centre;
        double profile = dy >= 0.0
                ? 1.0 - dy / Math.max(2.0, thickness * 0.42)
                : 1.0 + dy / Math.max(3.0, thickness * 1.35);
        if (profile <= 0.0) {
            return -1.0;
        }

        double detail = islandDetail.noise3(x, y * 0.7, z) * 0.35;
        return profile * core + detail - 0.32;
    }

    /** Builds a per chunk grid for the floating island field. */
    public ScalarField3D islandField(int blockX, int blockZ) {
        return ScalarField3D.build(blockX, settings.floatingIslandMinY, blockZ, settings.floatingIslandMaxY,
                4, 4, this::islandDensity);
    }
}
