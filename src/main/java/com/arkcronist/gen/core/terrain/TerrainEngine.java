package com.arkcronist.gen.core.terrain;

import com.arkcronist.gen.core.biome.ArkBiome;
import com.arkcronist.gen.core.math.MathUtil;
import com.arkcronist.gen.core.biome.BiomeRegistry;
import com.arkcronist.gen.core.biome.BiomeSelector;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.cave.AquiferSampler;
import com.arkcronist.gen.core.cave.CaveBiome;
import com.arkcronist.gen.core.cave.CaveBiomeSampler;
import com.arkcronist.gen.core.cave.CaveDecorator;
import com.arkcronist.gen.core.math.Hashing;
import com.arkcronist.gen.core.noise.FractalNoise;

/**
 * The generator core: owns every sampler and turns a chunk coordinate into blocks.
 *
 * <p>One instance per world. It holds no per-chunk mutable state, so any number of Paper generation
 * threads may call it at once; the only shared structure is the bounded terrain cache, which is
 * concurrent by construction.</p>
 *
 * <p>A chunk is written in a single top-down pass per column. That pass simultaneously resolves
 * overhangs, cave carving, water, surface layering, strata and ores, which avoids the repeated
 * "scan the column again" work that multi-stage generators pay for.</p>
 */
public final class TerrainEngine {

    private final long seed;
    private final Preset preset;
    private final TerrainSettings settings;
    private final BiomeRegistry biomeRegistry;
    private final BiomeSelector selector;
    private final TerrainSampler sampler;
    private final CaveCarver caves;
    private final Density3D density;
    private final StrataSampler strata;
    private final OreSampler ores;
    private final CaveBiomeSampler caveBiomes;
    private final AquiferSampler aquifers;
    private final CaveDecorator caveDecorator;
    private final FractalNoise surfaceMottle;
    private final TerrainCache cache;

    public TerrainEngine(long seed, Preset preset, TerrainSettings settings, int cacheSize) {
        this.seed = seed;
        this.preset = preset;
        this.settings = settings;
        this.biomeRegistry = new BiomeRegistry();
        this.selector = new BiomeSelector(biomeRegistry, settings);
        this.sampler = new TerrainSampler(seed, settings);
        this.caves = new CaveCarver(seed, settings);
        this.density = new Density3D(seed, settings);
        this.strata = new StrataSampler(seed, settings);
        this.ores = new OreSampler(seed, settings);
        this.caveBiomes = new CaveBiomeSampler(seed, settings);
        this.aquifers = new AquiferSampler(seed, settings);
        this.caveDecorator = new CaveDecorator(seed);
        this.surfaceMottle = FractalNoise.fbm(seed, "surface-mottle", 3, 0.021);
        this.cache = new TerrainCache(cacheSize);
    }

    public TerrainEngine(long seed, Preset preset) {
        this(seed, preset, TerrainSettings.forPreset(preset), 512);
    }

    public long seed() {
        return seed;
    }

    public Preset preset() {
        return preset;
    }

    public TerrainSettings settings() {
        return settings;
    }

    public BiomeRegistry biomes() {
        return biomeRegistry;
    }

    public BiomeSelector selector() {
        return selector;
    }

    public TerrainSampler sampler() {
        return sampler;
    }

    public Density3D density() {
        return density;
    }

    public TerrainCache cache() {
        return cache;
    }

    public CaveBiomeSampler caveBiomes() {
        return caveBiomes;
    }

    public AquiferSampler aquifers() {
        return aquifers;
    }

    /** Lowest y the per chunk 3D fields must cover. Shared by every caller so they agree exactly. */
    private int fieldBottom(ChunkTerrain terrain) {
        return Math.max(settings.minY, terrain.minSurface - fieldBand() - 40);
    }

    /** Highest y the per chunk 3D fields must cover. */
    private int fieldTop(ChunkTerrain terrain) {
        return Math.min(settings.maxY - 1, terrain.maxSurface + fieldBand() + 2);
    }

    private int fieldBand() {
        return density.overhangsEnabled() ? (int) Math.ceil(settings.overhangBand) + 1 : 0;
    }

    public ChunkTerrain terrain(int chunkX, int chunkZ) {
        return cache.get(chunkX, chunkZ, key -> ChunkTerrain.build(sampler, selector, chunkX, chunkZ));
    }

    /** Heightmap value at a world position: the shape of the land, ignoring 3D features. */
    public int surfaceHeight(int x, int z) {
        ChunkTerrain terrain = terrain(x >> 4, z >> 4);
        return solidSurface(terrain)[ChunkTerrain.index(x & 15, z & 15)];
    }

    /** Heightmap height without the 3D correction; used where only the landform matters. */
    public int heightmapHeight(int x, int z) {
        ChunkTerrain terrain = terrain(x >> 4, z >> 4);
        return (int) Math.floor(terrain.heightAt(x & 15, z & 15));
    }

    /**
     * The block a feature should actually stand on.
     *
     * <p>Computed with the same interpolated fields the block pass uses, so a tree planted here lands
     * exactly on the rock the generator wrote - not on the heightmap, which an overhang, an arch or a
     * cave mouth may have moved by tens of blocks.</p>
     */
    private short[] solidSurface(ChunkTerrain terrain) {
        short[] cached = terrain.solidSurface;
        if (cached != null) {
            return cached;
        }
        short[] surface = new short[256];
        int blockX = terrain.chunkX << 4;
        int blockZ = terrain.chunkZ << 4;
        boolean overhangs = density.overhangsEnabled();
        int band = fieldBand();
        int top = fieldTop(terrain);
        int bottom = fieldBottom(terrain);

        // Exactly the same fields, over exactly the same range, as the block pass uses.
        ScalarField3D overhangField = overhangs
                ? density.surfaceField(blockX, blockZ, bottom, top)
                : null;
        ScalarField3D caveField = settings.caves
                ? caves.field(blockX, blockZ, settings.minY, top)
                : null;

        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                int index = ChunkTerrain.index(localX, localZ);
                int x = blockX + localX;
                int z = blockZ + localZ;
                double height = terrain.height[index];
                double mountain = terrain.mountain[index];
                int result = (int) Math.floor(height);
                double cutoff = height - settings.surfaceCaveClearance;

                for (int y = Math.min(top, (int) Math.floor(height) + band); y >= bottom; y--) {
                    double solidity = height - y;
                    if (overhangs && Math.abs(y - height) <= settings.overhangBand) {
                        solidity += density.shapeDelta(overhangField.get(x, y, z), y, height, mountain);
                    }
                    if (solidity <= 0.0) {
                        continue;
                    }
                    if (caveField != null && y < cutoff && caves.gate(y, height) > 0.0
                            && caveField.get(x, y, z) > 0.0) {
                        continue;
                    }
                    result = y;
                    break;
                }
                surface[index] = (short) result;
            }
        }
        terrain.solidSurface = surface;
        return surface;
    }

    /** Water surface at a world position (sea level, or a lake's own level). */
    public int waterLevel(int x, int z) {
        ChunkTerrain terrain = terrain(x >> 4, z >> 4);
        return (int) Math.floor(terrain.waterAt(x & 15, z & 15));
    }

    public ArkBiome biomeAt(int x, int z) {
        ChunkTerrain terrain = terrain(x >> 4, z >> 4);
        return biomeRegistry.byId(terrain.biomeAt(x & 15, z & 15));
    }

    /**
     * Writes one chunk: terrain, water, strata, ores, caves, surface layers, floating islands and
     * bedrock.
     */
    public void generateChunk(int chunkX, int chunkZ, BlockWriter writer) {
        ChunkTerrain terrain = terrain(chunkX, chunkZ);
        int blockX = chunkX << 4;
        int blockZ = chunkZ << 4;

        int minY = Math.max(settings.minY, writer.minY());
        int maxY = Math.min(settings.maxY, writer.maxY()) - 1;

        boolean overhangs = density.overhangsEnabled();
        int band = fieldBand();
        int fieldTop = fieldTop(terrain);
        int fieldBottom = fieldBottom(terrain);

        ScalarField3D overhangField = overhangs
                ? density.surfaceField(blockX, blockZ, fieldBottom, fieldTop)
                : null;
        ScalarField3D caveField = settings.caves
                ? caves.field(blockX, blockZ, minY, fieldTop)
                : null;
        ScalarField3D veinField = ores.veinField(blockX, blockZ, minY, fieldTop);
        ScalarField3D mottleField = strata.mottleField(blockX, blockZ, minY, fieldTop);
        ScalarField3D islandField = density.floatingIslandsEnabled()
                ? ScalarField3D.build(blockX, Math.max(minY, density.islandMinY()), blockZ,
                Math.min(maxY, density.islandMaxY()), 4, 4, density::islandDensity)
                : null;

        short[] surface = new short[256];

        for (int localZ = 0; localZ < 16; localZ++) {
            int z = blockZ + localZ;
            for (int localX = 0; localX < 16; localX++) {
                int x = blockX + localX;
                int index = ChunkTerrain.index(localX, localZ);

                double height = terrain.height[index];
                double water = terrain.water[index];
                double mountain = terrain.mountain[index];
                ArkBiome biome = biomeRegistry.byId(terrain.biome[index]);

                surface[index] = (short) writeColumn(writer, x, z, localX, localZ, height, water, mountain,
                        biome, terrain.temperature[index], minY, maxY, band, overhangs, overhangField,
                        caveField, veinField, mottleField);

                if (islandField != null && density.islandPossible(x, z)) {
                    writeIslandColumn(writer, x, z, localX, localZ, minY, maxY, islandField);
                }

                writeBedrock(writer, x, z, localX, localZ, minY);
            }
        }
        // The block pass already knows where the ground is; publishing it here means features never
        // have to rebuild the 3D fields just to ask.
        terrain.solidSurface = surface;
    }

    /** Writes one column and returns the y of its topmost solid block. */
    private int writeColumn(BlockWriter writer, int x, int z, int localX, int localZ,
                             double height, double water, double mountain, ArkBiome biome,
                             double temperature, int minY, int maxY, int band, boolean overhangs,
                             ScalarField3D overhangField, ScalarField3D caveField, ScalarField3D veinField,
                             ScalarField3D mottleField) {
        int waterTop = (int) Math.floor(water);
        int solidTop = (int) Math.floor(height) + band;
        int columnTop = Math.min(maxY, Math.max(solidTop, waterTop));

        int depth = -1;
        boolean caveAirAbove = false;
        boolean runUnderground = false;
        // Under water the roof of rock is kept much thicker, and any cavity below it is flooded from
        // the start. An air pocket a few blocks under the sea floor is the thing that made a cave
        // "flood the moment you swim into it": it was dry air sitting directly beneath an ocean.
        boolean seabed = height < water - 0.5;
        int clearance = seabed ? settings.surfaceCaveClearance + 9 : settings.surfaceCaveClearance;
        // Steep ground needs a deeper skin. On a slope the same clearance is measured straight down,
        // so a tunnel that sits comfortably below a flat field breaks straight out of a cliff face -
        // which is where those raw square holes in the mountainsides came from. Mouths still open,
        // but only where the field is strong enough to cut a whole passage out to daylight.
        if (!seabed && mountain > 0.15) {
            clearance += (int) Math.round(MathUtil.smoothStep(MathUtil.normalize(mountain, 0.15, 0.85)) * 10.0);
        }
        double surfaceCutoff = height - clearance;
        // Hoisted out of the inner loop: all three are functions of the column, not of y.
        double strataWarp = strata.columnWarp(x, z);
        double deepslateLevel = strata.deepslateLevel(x, z);
        double surfaceRoll = surfaceRoll(x, z, biome);

        // Underground water and lava tables for this column. These are what stop the deep world from
        // being either bone dry or one continuous lava ocean.
        int lavaTable = aquifers.lavaTable(x, z);
        int waterTable = aquifers.waterTable(x, z, height);
        if (seabed) {
            // Everything hollow under the sea floor belongs to the sea.
            int seaFill = (int) Math.floor(water);
            waterTable = waterTable == AquiferSampler.NO_WATER ? seaFill : Math.max(waterTable, seaFill);
        }
        // Cave region sampled once per column instead of once per cavity.
        double caveRoll = caveBiomes.regionRoll(x, z);
        double caveBlend = caveBiomes.regionBlend(x, z);

        // Cavity tracking: the column pass discovers floors and ceilings for free, so cave
        // decoration rides along instead of needing a second scan of the chunk.
        int cavityTop = Integer.MIN_VALUE;
        int airRun = 0;
        boolean cavityFlooded = false;
        int topSolid = Integer.MIN_VALUE;

        for (int y = columnTop; y >= minY; y--) {
            double solidity = height - y;
            if (overhangs && Math.abs(y - height) <= settings.overhangBand) {
                solidity += density.shapeDelta(overhangField.get(x, y, z), y, height, mountain);
            }
            boolean solid = solidity > 0.0;
            boolean carved = false;

            if (solid && caveField != null && y < surfaceCutoff) {
                double gate = caves.gate(y, height, clearance);
                // Submarine caves exist, but they are pockets, not the same cave network the surface
                // has: only the strongest part of the field carves under the sea.
                // The openness field peaks around 0.15, so this cut keeps only the strongest third
                // of it under the sea: pockets and short tunnels instead of the full network.
                double threshold = seabed ? 0.022 : 0.0;
                if (gate > 0.0 && caveField.get(x, y, z) > threshold) {
                    solid = false;
                    carved = true;
                }
            }

            if (solid) {
                if (depth < 0) {
                    runUnderground = caveAirAbove;
                }
                depth++;
                int block = blockFor(x, y, z, biome, depth, height, water, surfaceRoll, runUnderground, veinField,
                        mottleField, strataWarp, deepslateLevel);
                writer.set(localX, y, localZ, block);
                if (topSolid == Integer.MIN_VALUE) {
                    topSolid = y;
                }

                // We just closed a cavity: decorate its floor and, now that the height is known, its
                // ceiling too.
                if (cavityTop != Integer.MIN_VALUE && airRun >= 2) {
                    CaveBiome caveBiome = caveBiomes.resolve(caveRoll, caveBlend, y, temperature);
                    if (!cavityFlooded) {
                        int floorBlock = caveDecorator.decorateFloor(x, y, z, caveBiome, airRun,
                                writer, localX, localZ);
                        if (floorBlock >= 0) {
                            writer.set(localX, y, localZ, floorBlock);
                        }
                        int ceilingBlock = caveDecorator.decorateCeiling(x, cavityTop, z, caveBiome,
                                airRun, writer, localX, localZ);
                        if (ceilingBlock >= 0 && cavityTop + 1 <= maxY) {
                            writer.set(localX, cavityTop + 1, localZ, ceilingBlock);
                        }
                    } else if (caveBiome == CaveBiome.LUSH) {
                        writer.set(localX, y, localZ, Blocks.CLAY);
                    }
                }
                cavityTop = Integer.MIN_VALUE;
                airRun = 0;
                cavityFlooded = false;
                caveAirAbove = false;
            } else {
                depth = -1;
                if (carved) {
                    caveAirAbove = true;
                    if (cavityTop == Integer.MIN_VALUE) {
                        cavityTop = y;
                        cavityFlooded = false;
                    }
                    airRun++;
                    if (y <= lavaTable) {
                        writer.set(localX, y, localZ, Blocks.LAVA);
                        cavityFlooded = true;
                    } else if (waterTable != AquiferSampler.NO_WATER && y <= waterTable) {
                        writer.set(localX, y, localZ, Blocks.WATER);
                        cavityFlooded = true;
                    }
                } else {
                    caveAirAbove = false;
                    cavityTop = Integer.MIN_VALUE;
                    airRun = 0;
                    cavityFlooded = false;
                    if (y <= waterTop) {
                        writer.set(localX, y, localZ, Blocks.WATER);
                    }
                }
            }
        }
        return topSolid == Integer.MIN_VALUE ? (int) Math.floor(height) : topSolid;
    }

    /**
     * Standard deviation of {@link FractalNoise#unsigned2} at three octaves, measured over half a
     * million samples. Fractal noise is bell-shaped, not flat: it sits around 0.5 and never once
     * reached the top or bottom tenth of its range.
     */
    private static final double MOTTLE_SIGMA = 0.145;

    /**
     * Where a column's surface lands in its biome palette, as a uniform value in [0,1).
     *
     * <p>Drawing this from a per-block hash is what covered the snowy slopes in salt-and-pepper:
     * with a palette of snow, grass and packed ice, every block rolled its own material and the
     * hillside came out as confetti rather than as ground. The roll is coherent noise instead, so a
     * palette paints <em>patches</em> - a run of snow, then a bank of packed ice - the way a real
     * surface reads. One roll per column, shared by the whole surface stack, so the soil under a
     * snow patch belongs to that patch.</p>
     *
     * <p>Two corrections make the coherent roll behave like the hash it replaces:</p>
     * <ul>
     *   <li>A little white noise is folded into the sample so patch edges fray. Without it the
     *       boundaries are smooth curves and look drawn on. It goes in before the flattening, as a
     *       nudge to the position on the curve, so it costs nothing in distribution.</li>
     *   <li>The result is pushed back through the normal CDF. Palette weights are shares - 6/3/1
     *       means six parts snow to one part blue ice - and a bell-shaped roll would hand almost
     *       everything to the middle entry and never once reach the tail. Flattening restores the
     *       weights as written.</li>
     * </ul>
     */
    private double surfaceRoll(int x, int z, ArkBiome biome) {
        double scale = biome.surfaceRoughness <= 0.0 ? 1.0 : biome.surfaceRoughness;
        double coherent = surfaceMottle.unsigned2(x * scale, z * scale);
        double frayed = coherent + (Hashing.value3(seed ^ 0x9E37B1L, x, 0, z) - 0.5) * 0.055;
        double roll = normalCdf((frayed - 0.5) / MOTTLE_SIGMA);
        return roll <= 0.0 ? 0.0 : roll >= 1.0 ? 0.9999999 : roll;
    }

    /**
     * The surface roll for a column, for tests that need to check the distribution the palettes
     * actually see rather than infer it from generated blocks.
     */
    public double surfaceRollAt(int x, int z) {
        return surfaceRoll(x, z, biomeAt(x, z));
    }

    /** Normal CDF, tanh approximation - within 1e-4 across the range and cheap enough per column. */
    private static double normalCdf(double z) {
        return 0.5 * (1.0 + Math.tanh(0.7988 * z * (1.0 + 0.04417 * z * z)));
    }

    private int blockFor(int x, int y, int z, ArkBiome biome, int depth, double height, double water,
                         double surfaceRoll, boolean underground, ScalarField3D veinField,
                         ScalarField3D mottleField, double strataWarp, double deepslateLevel) {
        boolean submerged = height < water - 0.4;
        if (!underground) {
            if (depth == 0) {
                return submerged ? biome.underwater.pick(surfaceRoll) : biome.surface.pick(surfaceRoll);
            }
            if (depth <= biome.surfaceDepth) {
                return submerged
                        ? biome.underwater.pick(surfaceRoll)
                        : biome.subsurface.pick(surfaceRoll);
            }
        }

        boolean deep = y < deepslateLevel;
        int ore = ores.oreAt(x, y, z, biome, veinField.get(x, y, z), deep);
        if (ore >= 0) {
            return ore;
        }
        return strata.stoneAt(x, y, z, biome, height - y, strataWarp, mottleField.get(x, y, z), deep);
    }

    /** Floating islands are their own little worlds: stone core, soil shell, sky biome surface. */
    private void writeIslandColumn(BlockWriter writer, int x, int z, int localX, int localZ,
                                   int minY, int maxY, ScalarField3D islandField) {
        ArkBiome biome = selector.floatingBiome();
        int top = Math.min(maxY, density.islandMaxY());
        int bottom = Math.max(minY, density.islandMinY());
        int depth = -1;
        double surfaceRoll = surfaceRoll(x, z, biome);
        for (int y = top; y >= bottom; y--) {
            if (islandField.get(x, y, z) > 0.0) {
                depth++;
                int block;
                if (depth == 0) {
                    block = biome.surface.pick(surfaceRoll);
                } else if (depth <= 3) {
                    block = biome.subsurface.pick(surfaceRoll);
                } else {
                    // Stone is meant to look mottled block by block; only the skin needs patches.
                    block = biome.stone.pickAt(seed, x, y, z);
                }
                writer.set(localX, y, localZ, block);
            } else {
                depth = -1;
            }
        }
    }

    private void writeBedrock(BlockWriter writer, int x, int z, int localX, int localZ, int minY) {
        writer.set(localX, minY, localZ, Blocks.BEDROCK);
        for (int i = 1; i <= settings.bedrockRoughness; i++) {
            double chance = 1.0 - i / (double) (settings.bedrockRoughness + 1);
            if (Hashing.value3(seed ^ 0xBED0CL, x, i, z) < chance) {
                writer.set(localX, minY + i, localZ, Blocks.BEDROCK);
            }
        }
    }
}
