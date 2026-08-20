package com.arkcronist.gen.core.terrain;

import com.arkcronist.gen.core.math.MathUtil;
import com.arkcronist.gen.core.noise.CellularNoise;
import com.arkcronist.gen.core.noise.DomainWarp;
import com.arkcronist.gen.core.noise.FractalNoise;

/**
 * Turns a world position into terrain.
 *
 * <p>The column pipeline runs in this order, and every stage is a pure function of position:</p>
 * <ol>
 *     <li><b>Continents</b> - one very low frequency, heavily domain warped field decides land or sea.</li>
 *     <li><b>Ocean profile</b> - shoreline, continental shelf, continental slope, abyssal plain, then
 *         trenches subtracted and seamounts added on top. This is a real bathymetric profile rather
 *         than "terrain minus a constant".</li>
 *     <li><b>Land relief</b> - rolling base, hills, fine detail, then ridged mountain ranges gated by
 *         their own mask so ranges form belts instead of covering everything.</li>
 *     <li><b>Plateaus, canyons and cliffs</b> - terracing and subtractive carving.</li>
 *     <li><b>Rivers and lakes</b> - warped ridge networks carve valleys; cellular basins create lakes
 *         that carry their own water level.</li>
 * </ol>
 *
 * <p>Erosion is deliberately <i>not</i> here: it needs neighbours and is applied by
 * {@link HeightField} over the shared coarse grid so that adjacent chunks always agree.</p>
 */
public final class TerrainSampler {

    private final TerrainSettings settings;
    private final long seed;

    private final DomainWarp continentWarp;
    private final DomainWarp mountainWarp;
    private final DomainWarp riverWarp;

    private final FractalNoise continent;
    private final FractalNoise coastDetail;
    private final FractalNoise abyss;
    private final FractalNoise trench;
    private final FractalNoise seamount;
    private final FractalNoise oceanIsland;

    private final FractalNoise relief;
    private final FractalNoise hills;
    private final FractalNoise detail;
    private final FractalNoise mountain;
    private final FractalNoise mountainMask;
    private final FractalNoise flatlandMask;
    private final FractalNoise plateauMask;
    private final FractalNoise canyon;
    private final FractalNoise erosionField;

    private final FractalNoise river;
    private final CellularNoise lakeCells;

    private final DomainWarp biomeWarp;
    private final FractalNoise temperature;
    private final FractalNoise humidity;
    private final FractalNoise weirdness;
    private final DomainWarp climateWarp;
    private final DomainWarp thermalWarp;

    public TerrainSampler(long seed, TerrainSettings settings) {
        this.seed = seed;
        this.settings = settings;

        this.continentWarp = new DomainWarp(seed, "continent", settings.continentWarpFrequency, settings.continentWarp);
        this.mountainWarp = new DomainWarp(seed, "mountain", 0.0022, 42.0);
        this.riverWarp = new DomainWarp(seed, "river", 0.0011, 190.0);
        this.climateWarp = new DomainWarp(seed, "climate", settings.climateFrequency * 1.7, settings.climateWarp);
        // Temperature is warped far less than the rest of the climate. Latitude is the one thing
        // that has to stay coherent over long distances: humidity and weirdness can break a region
        // into forest, swamp and meadow, but they must not drop a desert next to a glacier.
        this.thermalWarp = new DomainWarp(seed, "thermal", settings.climateFrequency * 0.9,
                settings.climateWarp * settings.thermalWarpFactor);

        this.continent = FractalNoise.fbm(seed, "continent", settings.continentOctaves, settings.continentFrequency);
        this.coastDetail = FractalNoise.fbm(seed, "coast", 4, settings.continentFrequency * 26.0);
        this.abyss = FractalNoise.fbm(seed, "abyss", 4, 0.0026);
        this.trench = FractalNoise.ridged(seed, "trench", 3, settings.trenchFrequency);
        this.seamount = FractalNoise.ridged(seed, "seamount", 4, settings.seamountFrequency);
        this.oceanIsland = FractalNoise.fbm(seed, "oceanIsland", 3, settings.oceanIslandFrequency);

        this.relief = FractalNoise.fbm(seed, "relief", 5, settings.reliefFrequency);
        this.hills = FractalNoise.fbm(seed, "hills", 3, settings.hillFrequency);
        this.detail = FractalNoise.fbm(seed, "detail", 2, settings.detailFrequency);
        this.mountain = FractalNoise.ridged(seed, "mountain", 5, settings.mountainFrequency);
        this.mountainMask = FractalNoise.fbm(seed, "mountainMask", 3, settings.mountainMaskFrequency);
        // Two octaves only: open country should be one broad shape, not a lumpy one.
        this.flatlandMask = FractalNoise.fbm(seed, "flatland", 2, settings.flatlandFrequency);
        this.plateauMask = FractalNoise.fbm(seed, "plateau", 3, settings.plateauFrequency);
        this.canyon = FractalNoise.fbm(seed, "canyon", 3, settings.canyonFrequency);
        this.erosionField = FractalNoise.fbm(seed, "erosion", 3, 0.00085);

        this.river = FractalNoise.fbm(seed, "river", 3, settings.riverFrequency);
        this.lakeCells = new CellularNoise(seed, "lakes", settings.lakeFrequency, 0.85);

        this.biomeWarp = new DomainWarp(seed, "biomeEdge", 0.011, 7.0);
        // Fewer octaves and a longer wavelength than the other two: broad thermal belts rather
        // than a speckle of hot and cold.
        this.temperature = FractalNoise.fbm(seed, "temperature", 3,
                settings.climateFrequency * settings.thermalScale);
        this.humidity = FractalNoise.fbm(seed, "humidity", 4, settings.climateFrequency * 1.23);
        this.weirdness = FractalNoise.fbm(seed, "weirdness", 4, settings.climateFrequency * 2.11);
    }

    public TerrainSettings settings() {
        return settings;
    }

    public long seed() {
        return seed;
    }

    /** Full column sample, pre erosion. */
    public void sample(int x, int z, ColumnData out) {
        out.reset(settings.seaLevel);

        double wx = continentWarp.warpedX(x, z);
        double wz = continentWarp.warpedZ(x, z);
        double c = continent.noise2(wx, wz) + coastDetail.noise2(x, z) * 0.045 * settings.coastRoughness;
        out.continentalness = c;

        double land = MathUtil.smootherStep(MathUtil.normalize(c - settings.oceanCutoff, 0.0, settings.coastWidth));
        out.land = land;
        double oceanT = MathUtil.smoothStep(MathUtil.normalize(settings.oceanCutoff - c, 0.0, 0.62));
        out.oceanT = oceanT;

        double erosion = MathUtil.normalize(erosionField.noise2(x, z), -0.7, 0.7);
        out.erosion = erosion;

        double oceanHeight = oceanFloor(x, z, oceanT, out);
        double landHeight = landSurface(x, z, erosion, out);

        // Blending in height space keeps beaches continuous: the shoreline is where the two meet.
        double height = MathUtil.lerp(land, oceanHeight, landHeight);

        if (settings.rivers && land > 0.02) {
            height = carveRiver(x, z, height, land, out);
        }
        if (settings.lakes && land > 0.35) {
            height = carveLake(x, z, height, erosion, out);
        }

        // Soft world limits: instead of letting a trench clip through bedrock or a peak flatten on
        // the build ceiling, both ends are compressed smoothly so the shape survives, just squashed.
        height = MathUtil.smoothMax(height, settings.minY + 6.0, 10.0);
        height = MathUtil.smoothMin(height, settings.maxY - 14.0, 16.0);

        out.height = height;
        sampleClimate(x, z, height, out);
    }

    // ------------------------------------------------------------------ oceans

    /**
     * Bathymetric profile: shelf, slope, abyssal plain, trenches and seamounts.
     *
     * <p>The three-segment profile is what makes ArkcronistGenerator's oceans read as real oceans
     * from a boat: you swim off a beach onto a shallow shelf, the floor drops away over the slope,
     * and then there is a long, dark, genuinely deep plain with mountains and gashes in it.</p>
     */
    private double oceanFloor(int x, int z, double oceanT, ColumnData out) {
        double depth;
        if (oceanT < settings.shelfExtent) {
            double t = MathUtil.smoothStep(oceanT / settings.shelfExtent);
            depth = MathUtil.lerp(t, 1.5, settings.shelfDepth);
        } else if (oceanT < settings.slopeExtent) {
            double t = MathUtil.smootherStep((oceanT - settings.shelfExtent)
                    / (settings.slopeExtent - settings.shelfExtent));
            depth = MathUtil.lerp(t, settings.shelfDepth, settings.slopeDepth);
        } else {
            double t = MathUtil.smoothStep((oceanT - settings.slopeExtent) / (1.0 - settings.slopeExtent));
            depth = MathUtil.lerp(t, settings.slopeDepth, settings.abyssDepth);
        }

        double deepMask = MathUtil.smoothStep(MathUtil.normalize(oceanT, settings.shelfExtent, 0.85));
        double floor = settings.seaLevel - depth;

        // Rough abyssal plain: gentle undulation that grows with depth.
        floor += abyss.noise2(x, z) * settings.abyssRoughness * deepMask;

        // Trenches: long, narrow, very deep gashes that only exist far offshore.
        double trenchValue = trench.unsigned2(x, z);
        if (trenchValue > settings.trenchThreshold) {
            double t = MathUtil.normalize(trenchValue, settings.trenchThreshold, 1.0);
            floor -= Math.pow(t, 1.6) * settings.trenchDepth * deepMask;
        }

        // Seamounts: underwater mountains, occasionally tall enough to become islands.
        double seamountValue = seamount.unsigned2(x, z);
        if (seamountValue > settings.seamountThreshold) {
            double t = MathUtil.normalize(seamountValue, settings.seamountThreshold, 1.0);
            double islandBoost = MathUtil.normalize(oceanIsland.noise2(x, z) * 1.7, 0.10, 0.60)
                    * settings.oceanIslandStrength;
            floor += Math.pow(t, 1.8) * settings.seamountAmplitude * (0.65 + islandBoost)
                    * MathUtil.lerp(deepMask, 0.35, 1.0);
        }

        out.mountainFactor = Math.max(out.mountainFactor, MathUtil.normalize(seamountValue, settings.seamountThreshold, 1.0) * 0.5);
        return Math.min(floor, settings.seaLevel - 0.5);
    }

    // ------------------------------------------------------------------ land

    private double landSurface(int x, int z, double erosion, ColumnData out) {
        // Open country: broad regions where relief is damped down to something buildable. Worked out
        // first because it also holds the mountain belts back, so a range never begins in the middle
        // of a plain.
        double flat = 0.0;
        if (settings.flatlandStrength > 0.0) {
            flat = MathUtil.smootherStep(MathUtil.normalize(flatlandMask.noise2(x, z) * 1.5, 0.10, 0.62))
                    * settings.flatlandStrength;
        }
        double reliefDamp = 1.0 - flat * 0.80;

        double height = settings.baseHeight
                + relief.noise2(x, z) * settings.reliefAmplitude * MathUtil.lerp(erosion, 1.25, 0.55) * reliefDamp
                + hills.noise2(x, z) * settings.hillAmplitude * reliefDamp
                + detail.noise2(x, z) * settings.detailAmplitude;

        // Mountain belts: a mask gates where ranges may exist at all, so ranges form chains with
        // lowlands between them instead of a uniform blanket of peaks.
        // Fractal noise concentrates near its mean, so the raw field is stretched before the mask
        // threshold is applied; otherwise a threshold of 0.3 would gate out essentially everything.
        double maskRaw = MathUtil.normalize(mountainMask.noise2(x, z) * 1.6,
                settings.mountainMaskThreshold, settings.mountainMaskThreshold + 0.35);
        double mask = MathUtil.smootherStep(maskRaw) * (1.0 - flat);
        if (mask > 0.0) {
            double mx = mountainWarp.warpedX(x, z);
            double mz = mountainWarp.warpedZ(x, z);
            // Remap the ridge field so valleys reach a true zero and crests reach one; without this
            // the fractal sits around its mean and ranges come out as broad domes instead of peaks.
            double ridge = MathUtil.normalize(mountain.unsigned2(mx, mz), 0.42, 0.97);
            ridge = Math.pow(ridge, 1.0 + settings.ridgeSharpness * 1.5);
            double amplitude = settings.mountainAmplitude * MathUtil.lerp(erosion, 1.15, 0.45);
            height += ridge * amplitude * Math.pow(mask, 1.2);
            out.mountainFactor = Math.max(out.mountainFactor, ridge * mask);
        }

        // Plateaus and mesas: terrace the result where the plateau mask is strong.
        double plateau = MathUtil.smootherStep(MathUtil.normalize(plateauMask.noise2(x, z) * 1.6, 0.18, 0.60));
        if (plateau > 0.0 && settings.plateauStrength > 0.0) {
            double terraced = MathUtil.terrace(height, settings.plateauSteps, settings.plateauStrength * plateau);
            height = MathUtil.lerp(plateau, height, terraced);
            out.plateauFactor = plateau;
        }

        // Cliff banding: benches and ledges, but only where the ground is steep enough to have them.
        // Applying this everywhere flattened gentle country into contour lines.
        if (settings.cliffSharpness > 0.0 && out.mountainFactor > 0.15) {
            double steepness = MathUtil.smoothStep(MathUtil.normalize(out.mountainFactor, 0.15, 0.75));
            height = MathUtil.terrace(height, settings.cliffSteps,
                    settings.cliffSharpness * steepness * (1.0 - erosion * 0.5));
        }

        // Canyons: narrow, deep, vertical walled subtraction following a warped ridge network.
        if (settings.canyonStrength > 0.0) {
            double canyonValue = Math.abs(canyon.noise2(x, z));
            double core = 1.0 - MathUtil.normalize(canyonValue, 0.0, settings.canyonWidth);
            if (core > 0.0) {
                double profile = MathUtil.smootherStep(core);
                double depth = settings.canyonDepth * settings.canyonStrength * profile;
                // Only carve where there is rock to carve; canyons stop at the sea.
                double floor = Math.max(settings.seaLevel - 6.0, height - depth);
                height = MathUtil.lerp(profile, height, floor);
                out.canyonFactor = profile;
            }
        }

        return height;
    }

    // ------------------------------------------------------------------ water features

    private double carveRiver(int x, int z, double height, double land, ColumnData out) {
        double rx = riverWarp.warpedX(x, z);
        double rz = riverWarp.warpedZ(x, z);
        double value = Math.abs(river.noise2(rx, rz));
        double core = 1.0 - MathUtil.normalize(value, 0.0, settings.riverWidth);
        if (core <= 0.0) {
            return height;
        }

        double profile = MathUtil.smootherStep(core);
        // Rivers fade out as the ground climbs; high ground gets dry gorges instead of floating water.
        double altitudeFade = 1.0 - MathUtil.smoothStep(
                MathUtil.normalize(height, settings.seaLevel + 26.0, settings.seaLevel + 78.0));
        double strength = profile * altitudeFade * MathUtil.smoothStep(MathUtil.normalize(land, 0.0, 0.35));
        if (strength <= 0.0) {
            return height;
        }

        double bed = height - settings.riverDepth * strength;
        double banked = MathUtil.lerp(Math.pow(strength, settings.riverBankSlope), height, bed);
        out.riverStrength = strength;
        // A river surface is flat, like the sea it runs into. Taking the column's own height meant
        // every column on a slope carried its own water level, and the result was a sheet of water
        // climbing the hillside. Above the sea the valley is carved but left dry.
        if (strength > 0.35 && banked < settings.seaLevel) {
            out.waterLevel = Math.max(out.waterLevel, settings.seaLevel);
        }
        return Math.min(height, banked);
    }

    /**
     * The smooth part of the land height at a point, used to level a lake.
     *
     * <p>Deliberately only the broad shape - no hills, no detail, no ranges. It has to give the same
     * answer for every column of a lake, and it is only ever asked about the middle of one.</p>
     */
    private double basinLevel(double x, double z) {
        return settings.baseHeight + relief.noise2(x, z) * settings.reliefAmplitude;
    }

    private double carveLake(int x, int z, double height, double erosion, ColumnData out) {
        double cell = lakeCells.cellValue(x, z);
        if (cell > settings.lakeChance) {
            return height;
        }
        double edge = lakeCells.edge(x, z);
        double basin = MathUtil.smootherStep(MathUtil.normalize(edge, 0.02, 0.35));
        if (basin <= 0.0) {
            return height;
        }
        // Lakes prefer settled, eroded ground: no ponds hanging on a cliff face.
        double flatness = MathUtil.smoothStep(MathUtil.normalize(erosion, 0.35, 0.8));
        double strength = basin * flatness;
        if (strength <= 0.02) {
            return height;
        }

        double bed = height - settings.lakeDepth * strength;
        out.lakeStrength = strength;
        // One level for the whole lake, taken at its centre, so its surface is flat however the
        // ground around it rolls. Columns whose ground stands above that level simply stay dry -
        // which is where the shoreline comes from.
        // Local, not a field: one sampler serves every generation thread at once.
        double[] centre = new double[2];
        lakeCells.cellCentre(x, z, centre);
        double surface = basinLevel(centre[0], centre[1]) - 1.0;
        double carved = MathUtil.lerp(strength, height, bed);
        if (carved < surface) {
            out.waterLevel = Math.max(out.waterLevel, surface);
        }
        return carved;
    }

    // ------------------------------------------------------------------ climate

    private void sampleClimate(int x, int z, double height, ColumnData out) {
        double cx = climateWarp.warpedX(x, z);
        double cz = climateWarp.warpedZ(x, z);
        double tx = thermalWarp.warpedX(x, z);
        double tz = thermalWarp.warpedZ(x, z);

        double fragment = settings.biomeFragmentation;
        // Fractal noise clusters around its mean; spreading it out is what lets the extremes of the
        // climate table (true desert, true polar) actually occur instead of everything reading as
        // "mild temperate".
        double t = MathUtil.clamp(temperature.noise2(tx, tz) * 1.9, -1.0, 1.0);
        double h = MathUtil.clamp(humidity.noise2(cx, cz) * 1.9, -1.0, 1.0);
        double w = MathUtil.clamp(weirdness.noise2(cx, cz) * 1.7, -1.0, 1.0);
        if (fragment > 0.0) {
            // Fragmentation breaks a region into distinct patches - but only through humidity and
            // weirdness. Sharpening temperature too is what used to put a snowfield one block from
            // a meadow, and snow placed on the warm side of that seam simply melts.
            h = MathUtil.sharpen(h, fragment * 0.5);
            w = MathUtil.sharpen(w, fragment * 0.35);
        }

        // Altitude cools the air: the same latitude can hold jungle at the coast and snow on the peak.
        double altitude = MathUtil.normalize(height, settings.seaLevel + 40.0, settings.seaLevel + 170.0);
        t = MathUtil.clamp(t + settings.temperatureBias - altitude * 1.15, -1.0, 1.0);
        h = MathUtil.clamp(h + settings.humidityBias + out.riverStrength * 0.25 - altitude * 0.35, -1.0, 1.0);

        out.temperature = t;
        out.humidity = h;
        out.weirdness = w;
    }

    /** Small warp applied to biome lookups so region borders wander instead of following the grid. */
    public double biomeWarpX(int x, int z) {
        return biomeWarp.warpedX(x, z);
    }

    public double biomeWarpZ(int x, int z) {
        return biomeWarp.warpedZ(x, z);
    }

    /** Land height ignoring rivers and lakes; used to anchor lake surfaces and structure foundations. */
    public double baseLandHeight(int x, int z) {
        ColumnData scratch = new ColumnData();
        scratch.reset(settings.seaLevel);
        double erosion = MathUtil.normalize(erosionField.noise2(x, z), -0.7, 0.7);
        return landSurface(x, z, erosion, scratch);
    }
}
