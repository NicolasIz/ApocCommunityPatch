package com.arkcronist.gen.core.terrain;

/**
 * Every tunable the terrain pipeline reads.
 *
 * <p>Plain mutable fields on purpose: settings are built once (from a preset, then overlaid with
 * config.yml), handed to the samplers and never written again, so generation threads only ever see
 * a fully published, effectively immutable object.</p>
 */
public final class TerrainSettings {

    // ---------------------------------------------------------------- world shape
    public int seaLevel = 63;
    public int minY = -64;
    public int maxY = 320;
    public int lavaLevel = -54;
    public int bedrockRoughness = 4;

    // ---------------------------------------------------------------- continents
    /** Lower frequency means bigger landmasses. 0.00030 gives continents thousands of blocks wide. */
    public double continentFrequency = 0.00030;
    public int continentOctaves = 5;
    public double continentWarp = 320.0;
    public double continentWarpFrequency = 0.00055;
    /** Continentalness below this value is ocean. */
    public double oceanCutoff = -0.04;
    /** Width of the coast transition in continentalness units. */
    public double coastWidth = 0.075;
    /** Extra bays and inlets carved into the coastline. */
    public double coastRoughness = 0.55;

    // ---------------------------------------------------------------- oceans
    public double shelfDepth = 16.0;
    /** Fraction of the ocean gradient occupied by the continental shelf. */
    public double shelfExtent = 0.20;
    public double slopeDepth = 52.0;
    /** Fraction of the ocean gradient occupied by the continental slope. */
    public double slopeExtent = 0.48;
    public double abyssDepth = 82.0;
    public double abyssRoughness = 9.0;
    public double trenchDepth = 38.0;
    public double trenchFrequency = 0.00040;
    public double trenchThreshold = 0.80;
    public double seamountAmplitude = 46.0;
    public double seamountFrequency = 0.0013;
    public double seamountThreshold = 0.62;
    /** Chance-ish mask controlling how often seamounts breach the surface as islands. */
    public double oceanIslandStrength = 0.35;
    public double oceanIslandFrequency = 0.0021;

    // ---------------------------------------------------------------- land
    public double baseHeight = 74.0;
    public double reliefAmplitude = 24.0;
    public double reliefFrequency = 0.0032;
    public double mountainAmplitude = 115.0;
    public double mountainFrequency = 0.0016;
    public double mountainMaskFrequency = 0.00062;
    public double mountainMaskThreshold = 0.10;
    public double ridgeSharpness = 0.45;
    public double hillFrequency = 0.010;
    public double hillAmplitude = 9.5;
    public double detailFrequency = 0.055;
    public double detailAmplitude = 3.6;

    /**
     * Roughness added to the heightmap at true block resolution, in blocks.
     *
     * <p>Every other height term above is evaluated on the coarse grid, four blocks apart, and then
     * interpolated. That band-limits the whole landscape: nothing finer than about eight blocks
     * survives, so a hillside is locally a straight ramp - measured at 0.07 blocks of curvature
     * against a gradient of 0.35. A straight ramp in a voxel world is a perfect staircase, which is
     * what put those parallel terraces on every slope.</p>
     *
     * <p>This term is added after the interpolation, per block, purely to break that up. It is
     * gated by slope, so open country stays open: villages, castles and the flatland the presets
     * carve out need ground you can actually build on, and roughening it would undo that.</p>
     */
    public double surfaceDetailAmplitude = 1.00;
    public double surfaceDetailFrequency = 0.15;

    /**
     * Gradient band over which the roughness above fades in, in blocks per block.
     *
     * <p>Below the minimum the ground is left exactly as the interpolation produced it. That is what
     * keeps open country buildable - the flatland the presets carve out is there so villages and
     * castles have somewhere to stand, and roughening it takes that away again.</p>
     */
    public double surfaceDetailSlopeMin = 0.20;
    public double surfaceDetailSlopeMax = 0.42;

    // ---------------------------------------------------------------- plateaus, canyons, cliffs
    /**
     * How strongly broad regions are flattened into open country.
     *
     * <p>A world of nothing but ranges and canyons has nowhere to put a village. This carves out
     * wide, gently rolling areas between the ranges - flat enough to build on, and large enough
     * that the structure placer's flat-site search actually finds somewhere.
     */
    public double flatlandStrength = 0.35;
    public double flatlandFrequency = 0.00042;

    public double plateauStrength = 0.45;
    public double plateauFrequency = 0.0011;
    public double plateauSteps = 0.14;
    public double canyonStrength = 0.0;
    public double canyonFrequency = 0.00095;
    public double canyonWidth = 0.045;
    public double canyonDepth = 46.0;
    public double cliffSharpness = 0.25;
    public double cliffSteps = 0.20;

    // ---------------------------------------------------------------- erosion
    public int erosionPasses = 2;
    public double erosionTalus = 1.35;
    public double erosionStrength = 0.42;
    public double depositionStrength = 0.22;

    // ---------------------------------------------------------------- rivers and lakes
    public boolean rivers = true;
    public double riverFrequency = 0.00085;
    public double riverWidth = 0.030;
    public double riverDepth = 9.0;
    public double riverBankSlope = 2.4;
    public boolean lakes = true;
    public double lakeFrequency = 0.0024;
    public double lakeChance = 0.10;
    public double lakeDepth = 8.0;

    /**
     * Hold every water surface in the world at sea level.
     *
     * <p>A lake used to carry its own level, taken from the broad relief at the middle of its basin.
     * On high ground that put real water high on a mountainside - measured at Y=110 against a sea
     * level of 63 - and since a lake's level and the sea's are two different numbers, the grid
     * between them interpolated through every value in between. That is where sheets of water at a
     * dozen different heights came from, and why the height changed from chunk to chunk.</p>
     *
     * <p>With this on there is exactly one water surface in the world and lakes fill only where
     * their floor lies below it. Turn it off to get per-lake levels back; they are levelled and
     * altitude-capped now either way, but one sea level is the setting that cannot go wrong.</p>
     */
    public boolean waterAtSeaLevel = true;

    /**
     * Height band, above sea level, over which lake basins fade out.
     *
     * <p>Rivers have always had this - it is why they leave dry gorges up high instead of floating
     * water. Lakes had nothing equivalent, so a basin could form at any altitude the relief allowed.
     * Past the end of this band no lake is carved at all.</p>
     */
    public double lakeAltitudeFadeStart = 8.0;
    public double lakeAltitudeFadeEnd = 26.0;

    // ---------------------------------------------------------------- 3D terrain
    public double overhangStrength = 0.0;
    public double overhangFrequency = 0.021;
    public double overhangBand = 26.0;
    public double archStrength = 0.0;
    public double floatingIslandDensity = 0.0;
    public int floatingIslandMinY = 150;
    public int floatingIslandMaxY = 290;
    public double floatingIslandFrequency = 0.0035;
    public double floatingIslandSize = 1.0;

    // ---------------------------------------------------------------- caves
    public boolean caves = true;

    /**
     * How much of the underground carries a water table, from 0 (bone dry) to 1.
     *
     * <p>Aquifers used to be unconditional: 39% of the world had one, the mean table sat at Y=-5 and
     * they reached as high as Y=55. That drowns the deep caves, and it drowns anything the server
     * wants to put in them - an ancient city occupies roughly Y=-51 to -20 and was underwater in
     * 38% of columns.</p>
     *
     * <p>This scales both how often a region has water at all and how high the table may rise. At
     * the default only a small share of the deep world is flooded, and the pools stay near the
     * bedrock where they read as features rather than as a drowned world.</p>
     */
    public double caveWater = 0.10;
    public double caveCheeseThreshold = 0.48;
    public double caveCheeseFrequency = 0.0128;
    public double tunnelThreshold = 0.064;
    public double tunnelFrequency = 0.0092;
    public double cavernDensity = 0.45;
    public double cavernFrequency = 0.0042;
    public int cavernMinY = -58;
    public int cavernMaxY = 40;
    public double megaCaveDensity = 0.0;
    public double megaCaveFrequency = 0.0016;
    public int surfaceCaveClearance = 5;

    // ---------------------------------------------------------------- strata and ores
    public double strataThickness = 9.0;
    public double strataWarp = 12.0;
    public double strataFrequency = 0.0035;
    public double oreMultiplier = 1.0;

    // ---------------------------------------------------------------- biomes
    public double climateFrequency = 0.00062;
    public double climateWarp = 150.0;
    public double biomeFragmentation = 0.0;

    /**
     * How much of the climate warp is applied to temperature, as a fraction.
     *
     * <p>Warping temperature as hard as the rest of the climate is what shreds a world's thermal
     * map: a cold biome ends up bordering a warm one, and any snow or ice on the warm side of that
     * seam melts on the first random tick. Keeping temperature smooth means a snowfield's
     * neighbours are also freezing, so the ice stays.
     */
    public double thermalWarpFactor = 0.30;

    /** Wavelength multiplier for temperature; above one it makes broader thermal belts. */
    public double thermalScale = 0.55;
    public double temperatureBias = 0.0;
    public double humidityBias = 0.0;
    public double biomeBlend = 24.0;

    // ---------------------------------------------------------------- decoration and structures
    public double treeDensity = 1.0;
    public double decorationDensity = 1.0;
    public double structureDensity = 1.0;
    public int structureGridSize = 448;
    public boolean structures = true;

    /**
     * Blocks to raise a schematic building above the ground the placer picked.
     *
     * <p>A schematic's own Y=0 is wherever its author started the selection, which is usually a
     * course or two below the visible floor - foundations, a plinth, the layer the walls sit on. Set
     * the building down on the exact surface height and it reads as sunk into the hill. This lifts
     * every prefab building by the same amount; the foundation pass fills whatever gap that opens.
     */
    public int prefabBuildingLift = 3;

    /**
     * Blocks to raise a floating vessel above the waterline worked out from its hull.
     *
     * <p>The waterline is guessed from where a hull stops being dense, which lands low: a ship set
     * there floats with its deck awash and only the masts showing.
     */
    public int prefabShipLift = 6;

    /** Whether the generator adds a bed, a light and a job block to prefabs that lack them. */
    public boolean prefabFurnish = false;
    public boolean minibosses = true;

    public TerrainSettings copy() {
        TerrainSettings copy = new TerrainSettings();
        copy.seaLevel = seaLevel;
        copy.minY = minY;
        copy.maxY = maxY;
        copy.lavaLevel = lavaLevel;
        copy.bedrockRoughness = bedrockRoughness;
        copy.continentFrequency = continentFrequency;
        copy.continentOctaves = continentOctaves;
        copy.continentWarp = continentWarp;
        copy.continentWarpFrequency = continentWarpFrequency;
        copy.oceanCutoff = oceanCutoff;
        copy.coastWidth = coastWidth;
        copy.coastRoughness = coastRoughness;
        copy.shelfDepth = shelfDepth;
        copy.shelfExtent = shelfExtent;
        copy.slopeDepth = slopeDepth;
        copy.slopeExtent = slopeExtent;
        copy.abyssDepth = abyssDepth;
        copy.abyssRoughness = abyssRoughness;
        copy.trenchDepth = trenchDepth;
        copy.trenchFrequency = trenchFrequency;
        copy.trenchThreshold = trenchThreshold;
        copy.seamountAmplitude = seamountAmplitude;
        copy.seamountFrequency = seamountFrequency;
        copy.seamountThreshold = seamountThreshold;
        copy.oceanIslandStrength = oceanIslandStrength;
        copy.oceanIslandFrequency = oceanIslandFrequency;
        copy.baseHeight = baseHeight;
        copy.reliefAmplitude = reliefAmplitude;
        copy.reliefFrequency = reliefFrequency;
        copy.mountainAmplitude = mountainAmplitude;
        copy.mountainFrequency = mountainFrequency;
        copy.mountainMaskFrequency = mountainMaskFrequency;
        copy.mountainMaskThreshold = mountainMaskThreshold;
        copy.ridgeSharpness = ridgeSharpness;
        copy.hillFrequency = hillFrequency;
        copy.hillAmplitude = hillAmplitude;
        copy.detailFrequency = detailFrequency;
        copy.detailAmplitude = detailAmplitude;
        copy.surfaceDetailAmplitude = surfaceDetailAmplitude;
        copy.surfaceDetailFrequency = surfaceDetailFrequency;
        copy.surfaceDetailSlopeMin = surfaceDetailSlopeMin;
        copy.surfaceDetailSlopeMax = surfaceDetailSlopeMax;
        copy.flatlandStrength = flatlandStrength;
        copy.flatlandFrequency = flatlandFrequency;
        copy.plateauStrength = plateauStrength;
        copy.plateauFrequency = plateauFrequency;
        copy.plateauSteps = plateauSteps;
        copy.canyonStrength = canyonStrength;
        copy.canyonFrequency = canyonFrequency;
        copy.canyonWidth = canyonWidth;
        copy.canyonDepth = canyonDepth;
        copy.cliffSharpness = cliffSharpness;
        copy.cliffSteps = cliffSteps;
        copy.erosionPasses = erosionPasses;
        copy.erosionTalus = erosionTalus;
        copy.erosionStrength = erosionStrength;
        copy.depositionStrength = depositionStrength;
        copy.rivers = rivers;
        copy.riverFrequency = riverFrequency;
        copy.riverWidth = riverWidth;
        copy.riverDepth = riverDepth;
        copy.riverBankSlope = riverBankSlope;
        copy.lakes = lakes;
        copy.lakeFrequency = lakeFrequency;
        copy.lakeChance = lakeChance;
        copy.lakeDepth = lakeDepth;
        copy.waterAtSeaLevel = waterAtSeaLevel;
        copy.lakeAltitudeFadeStart = lakeAltitudeFadeStart;
        copy.lakeAltitudeFadeEnd = lakeAltitudeFadeEnd;
        copy.overhangStrength = overhangStrength;
        copy.overhangFrequency = overhangFrequency;
        copy.overhangBand = overhangBand;
        copy.archStrength = archStrength;
        copy.floatingIslandDensity = floatingIslandDensity;
        copy.floatingIslandMinY = floatingIslandMinY;
        copy.floatingIslandMaxY = floatingIslandMaxY;
        copy.floatingIslandFrequency = floatingIslandFrequency;
        copy.floatingIslandSize = floatingIslandSize;
        copy.caves = caves;
        copy.caveWater = caveWater;
        copy.caveCheeseThreshold = caveCheeseThreshold;
        copy.caveCheeseFrequency = caveCheeseFrequency;
        copy.tunnelThreshold = tunnelThreshold;
        copy.tunnelFrequency = tunnelFrequency;
        copy.cavernDensity = cavernDensity;
        copy.cavernFrequency = cavernFrequency;
        copy.cavernMinY = cavernMinY;
        copy.cavernMaxY = cavernMaxY;
        copy.megaCaveDensity = megaCaveDensity;
        copy.megaCaveFrequency = megaCaveFrequency;
        copy.surfaceCaveClearance = surfaceCaveClearance;
        copy.strataThickness = strataThickness;
        copy.strataWarp = strataWarp;
        copy.strataFrequency = strataFrequency;
        copy.oreMultiplier = oreMultiplier;
        copy.climateFrequency = climateFrequency;
        copy.climateWarp = climateWarp;
        copy.biomeFragmentation = biomeFragmentation;
        copy.thermalWarpFactor = thermalWarpFactor;
        copy.thermalScale = thermalScale;
        copy.temperatureBias = temperatureBias;
        copy.humidityBias = humidityBias;
        copy.biomeBlend = biomeBlend;
        copy.treeDensity = treeDensity;
        copy.decorationDensity = decorationDensity;
        copy.structureDensity = structureDensity;
        copy.structureGridSize = structureGridSize;
        copy.structures = structures;
        copy.prefabBuildingLift = prefabBuildingLift;
        copy.prefabShipLift = prefabShipLift;
        copy.prefabFurnish = prefabFurnish;
        copy.minibosses = minibosses;
        return copy;
    }

    /**
     * Builds the settings for a preset.
     *
     * <p>CHAOTIC and INSANE do not simply scale BASE: they switch stages on. Read the differences as
     * "which terrain systems participate", not "how loud is the noise".</p>
     */
    public static TerrainSettings forPreset(Preset preset) {
        TerrainSettings s = new TerrainSettings();
        switch (preset) {
            case BASE -> {
                // Defaults above already describe BASE: broad continents, honest oceans with a
                // shelf/slope/abyss profile, moderate ranges, gentle erosion, no floating terrain.
                s.canyonStrength = 0.18;
                s.canyonDepth = 34.0;
                s.overhangStrength = 0.08;
                s.megaCaveDensity = 0.10;
                s.trenchThreshold = 0.86;
            }
            case CHAOTIC -> {
                s.continentFrequency = 0.00042;
                s.continentWarp = 520.0;
                s.coastRoughness = 0.95;
                s.coastWidth = 0.055;

                s.shelfDepth = 20.0;
                s.shelfExtent = 0.18;
                s.slopeDepth = 64.0;
                s.abyssDepth = 86.0;
                s.abyssRoughness = 16.0;
                s.trenchDepth = 44.0;
                s.trenchThreshold = 0.74;
                s.seamountAmplitude = 68.0;
                s.oceanIslandStrength = 0.65;

                s.reliefAmplitude = 40.0;
                s.mountainAmplitude = 178.0;
                s.mountainMaskThreshold = 0.14;
                s.ridgeSharpness = 0.65;
                s.flatlandStrength = 0.74;
                s.flatlandFrequency = 0.00046;
                s.hillAmplitude = 12.0;

                s.plateauStrength = 0.70;
                s.canyonStrength = 0.65;
                s.canyonDepth = 70.0;
                s.canyonWidth = 0.06;
                s.cliffSharpness = 0.55;

                s.erosionPasses = 2;
                s.erosionStrength = 0.30;
                s.erosionTalus = 1.9;

                s.riverDepth = 12.0;
                s.riverWidth = 0.034;
                s.lakeChance = 0.16;

                s.overhangStrength = 0.42;
                s.archStrength = 0.25;
                s.floatingIslandDensity = 0.10;

                s.cavernDensity = 0.60;
                s.megaCaveDensity = 0.30;
                s.caveCheeseThreshold = 0.42;

                s.biomeFragmentation = 0.22;
                s.climateFrequency = 0.00068;
                s.climateWarp = 200.0;
                s.biomeBlend = 24.0;

                s.structureDensity = 1.25;
                s.structureGridSize = 384;
                s.decorationDensity = 1.1;
            }
            case INSANE -> {
                s.continentFrequency = 0.00036;
                s.continentWarp = 760.0;
                s.coastRoughness = 1.25;
                s.coastWidth = 0.045;

                s.shelfDepth = 26.0;
                s.shelfExtent = 0.16;
                s.slopeExtent = 0.42;
                s.slopeDepth = 78.0;
                s.abyssDepth = 96.0;
                s.abyssRoughness = 22.0;
                s.trenchDepth = 52.0;
                s.trenchThreshold = 0.62;
                s.trenchFrequency = 0.00052;
                s.seamountAmplitude = 120.0;
                s.seamountThreshold = 0.50;
                s.oceanIslandStrength = 0.95;

                s.baseHeight = 78.0;
                s.reliefAmplitude = 56.0;
                s.mountainAmplitude = 268.0;
                s.mountainFrequency = 0.0013;
                s.mountainMaskThreshold = 0.02;
                s.flatlandStrength = 0.68;
                s.flatlandFrequency = 0.00040;
                s.ridgeSharpness = 0.80;
                s.hillAmplitude = 16.0;
                s.detailAmplitude = 3.5;

                s.plateauStrength = 0.90;
                s.plateauSteps = 0.12;
                s.canyonStrength = 1.0;
                s.canyonDepth = 118.0;
                s.canyonWidth = 0.075;
                s.cliffSharpness = 0.80;
                s.cliffSteps = 0.16;

                s.erosionPasses = 3;
                s.erosionStrength = 0.22;
                s.erosionTalus = 2.6;
                s.depositionStrength = 0.30;

                s.riverDepth = 16.0;
                s.riverWidth = 0.040;
                s.lakeChance = 0.20;
                s.lakeDepth = 12.0;

                s.overhangStrength = 1.0;
                s.overhangBand = 46.0;
                s.archStrength = 0.85;
                s.floatingIslandDensity = 0.55;
                s.floatingIslandSize = 1.8;
                s.floatingIslandMinY = 140;
                s.floatingIslandMaxY = 310;

                s.caveCheeseThreshold = 0.36;
                s.cavernDensity = 0.78;
                s.megaCaveDensity = 0.55;
                s.megaCaveFrequency = 0.0013;

                s.strataThickness = 6.5;
                s.strataWarp = 22.0;
                s.oreMultiplier = 1.35;

                s.climateFrequency = 0.00074;
                s.climateWarp = 260.0;
                s.biomeFragmentation = 0.30;
                s.biomeBlend = 22.0;

                s.treeDensity = 1.15;
                s.decorationDensity = 1.25;
                s.structureDensity = 1.6;
                s.structureGridSize = 352;
            }
        }
        return s;
    }
}
