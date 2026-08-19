package com.arkcronist.gen.core.decorate;

import com.arkcronist.gen.core.biome.ArkBiome;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.math.Hashing;
import com.arkcronist.gen.core.structure.RegionWriter;
import com.arkcronist.gen.core.terrain.ChunkTerrain;
import com.arkcronist.gen.core.terrain.TerrainEngine;
import com.arkcronist.gen.core.tree.TreeBuilder;
import com.arkcronist.gen.core.tree.TreeSpecies;
import com.arkcronist.gen.core.tree.TreeVariant;

/**
 * Places features that are larger than one block column: trees, boulders and log piles.
 *
 * <p>Features are generated per <em>origin</em> chunk. A chunk being populated asks its neighbours
 * for their features too and draws whatever reaches into it, so a 15 block wide crown that starts
 * two chunks away still lands complete. Nothing is ever queued, stored or replayed - the neighbour's
 * features are simply recomputed, which is cheap and completely order independent.</p>
 */
public final class FeaturePlacer {

    private static final long TREE_SALT = 0x71EE_5EEDL;
    private static final long BOULDER_SALT = 0xB0DL;
    private static final int TREE_ATTEMPTS = 32;

    private final TerrainEngine engine;

    public FeaturePlacer(TerrainEngine engine) {
        this.engine = engine;
    }

    /** Horizontal reach in chunks that a feature can extend from its origin chunk. */
    public static int chunkRadius() {
        return 2;
    }

    /** Places every feature whose origin lies inside the given chunk. */
    public void place(int chunkX, int chunkZ, RegionWriter writer) {
        ChunkTerrain terrain = engine.terrain(chunkX, chunkZ);
        FastRandom random = FastRandom.forChunk(engine.seed(), chunkX, chunkZ, TREE_SALT);
        double globalTreeDensity = engine.settings().treeDensity;
        double globalDecoration = engine.settings().decorationDensity;

        for (int attempt = 0; attempt < TREE_ATTEMPTS; attempt++) {
            int localX = random.nextInt(16);
            int localZ = random.nextInt(16);
            int index = ChunkTerrain.index(localX, localZ);
            ArkBiome biome = engine.biomes().byId(terrain.biome[index]);
            if (biome.trees.length == 0 || biome.treeDensity <= 0.0) {
                random.nextDouble();
                continue;
            }
            double chance = biome.treeDensity * globalTreeDensity * 256.0 / TREE_ATTEMPTS;
            if (!random.chance(chance)) {
                continue;
            }

            int x = (chunkX << 4) + localX;
            int z = (chunkZ << 4) + localZ;
            double water = terrain.water[index];
            // The real surface, not the heightmap: overhangs, arches and cave mouths move it.
            int groundY = engine.surfaceHeight(x, z);
            if (groundY < water - 0.2) {
                continue;
            }
            if (groundY + 8 >= writer.maxY()) {
                continue;
            }
            if (slope(x, z, groundY) > 4) {
                continue;
            }

            TreeSpecies species = biome.pickTree(random.nextDouble());
            if (species == null) {
                continue;
            }
            // Skip trees that cannot reach the area being written; neighbouring chunks are scanned
            // for completeness, not because most of their trees matter here.
            int reach = species.maxRadius() + 6;
            if (!writer.intersectsColumn(x - reach, z - reach, x + reach, z + reach)) {
                continue;
            }
            TreeVariant variant = pickVariant(biome, random);
            long treeSeed = Hashing.hash3(engine.seed() ^ TREE_SALT, x, groundY, z);
            TreeBuilder.build(species, variant, treeSeed, x, groundY, z, writer);
        }

        placeBoulders(chunkX, chunkZ, terrain, writer, globalDecoration);
    }

    private TreeVariant pickVariant(ArkBiome biome, FastRandom random) {
        double roll = random.nextDouble();
        double giant = biome.giantTreeChance;
        double fallen = giant + biome.fallenTreeChance;
        double stump = fallen + biome.stumpChance;
        double dead = stump + biome.deadTreeChance;
        double leaning = dead + biome.leaningTreeChance;
        if (roll < giant) {
            return TreeVariant.GIANT;
        }
        if (roll < fallen) {
            return TreeVariant.FALLEN;
        }
        if (roll < stump) {
            return TreeVariant.STUMP;
        }
        if (roll < dead) {
            return TreeVariant.DEAD;
        }
        if (roll < leaning) {
            return TreeVariant.LEANING;
        }
        return TreeVariant.NORMAL;
    }

    /** Maximum height difference around a position; used to keep trees off cliff faces. */
    private int slope(int x, int z, int groundY) {
        int max = 0;
        for (int i = 0; i < 4; i++) {
            int ox = i == 0 ? -2 : i == 1 ? 2 : 0;
            int oz = i == 2 ? -2 : i == 3 ? 2 : 0;
            int neighbour = engine.surfaceHeight(x + ox, z + oz);
            max = Math.max(max, Math.abs(neighbour - groundY));
        }
        return max;
    }

    private void placeBoulders(int chunkX, int chunkZ, ChunkTerrain terrain, RegionWriter writer, double density) {
        FastRandom random = FastRandom.forChunk(engine.seed(), chunkX, chunkZ, BOULDER_SALT);
        for (int attempt = 0; attempt < 4; attempt++) {
            int localX = random.nextInt(16);
            int localZ = random.nextInt(16);
            int index = ChunkTerrain.index(localX, localZ);
            ArkBiome biome = engine.biomes().byId(terrain.biome[index]);
            double chance = biome.decoration.boulders * density * 256.0 / 4.0;
            if (!random.chance(chance)) {
                continue;
            }
            int x = (chunkX << 4) + localX;
            int z = (chunkZ << 4) + localZ;
            int y = engine.surfaceHeight(x, z);
            if (y < terrain.water[index]) {
                continue;
            }
            int radius = random.nextInt(1, 3);
            if (!writer.intersectsColumn(x - radius - 1, z - radius - 1, x + radius + 1, z + radius + 1)) {
                continue;
            }
            int stone = biome.stone.pickAt(engine.seed(), x, y, z);
            for (int ox = -radius; ox <= radius; ox++) {
                for (int oy = -radius; oy <= radius; oy++) {
                    for (int oz = -radius; oz <= radius; oz++) {
                        if (ox * ox + oy * oy + oz * oz > radius * radius + radius) {
                            continue;
                        }
                        int block = random.chance(0.18) ? Blocks.MOSS_BLOCK : stone;
                        writer.set(x + ox, y + oy, z + oz, block);
                    }
                }
            }
        }
    }
}
