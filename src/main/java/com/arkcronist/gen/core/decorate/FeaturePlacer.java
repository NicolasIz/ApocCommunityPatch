package com.arkcronist.gen.core.decorate;

import com.arkcronist.gen.core.biome.ArkBiome;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.math.Hashing;
import com.arkcronist.gen.core.prefab.Prefab;
import com.arkcronist.gen.core.prefab.PrefabRegistry;
import com.arkcronist.gen.core.prefab.TreeKind;
import com.arkcronist.gen.core.structure.RegionWriter;
import com.arkcronist.gen.core.terrain.ChunkTerrain;
import com.arkcronist.gen.core.terrain.TerrainEngine;

/**
 * Places features that are larger than one block column: trees and boulders.
 *
 * <p>Trees are schematics. Every large tree in the world is a {@code .schem} prefab stamped from the
 * registry, chosen by the biome's species list, the preset, and the ground the placer found. There
 * is no procedural trunk-and-canopy builder any more: what a server owner puts in
 * {@code prefabs/trees/} is exactly what grows.</p>
 *
 * <p>Features are generated per <em>origin</em> chunk. A chunk being populated asks its neighbours
 * for their features too and draws whatever reaches into it, so a thirty block wide crown that
 * starts two chunks away still lands complete. Nothing is ever queued, stored or replayed - the
 * neighbour's features are simply recomputed, which is cheap and completely order independent.</p>
 */
public final class FeaturePlacer {

    private static final long TREE_SALT = 0x71EE_5EEDL;
    private static final long BOULDER_SALT = 0xB0DL;
    private static final int TREE_ATTEMPTS = 32;
    /**
     * Calibration for how far apart prefab trees stand.
     *
     * <p>A biome declares trees per block; a prefab declares how wide it is. This constant ties the
     * two together, and it is set so that the densest biome in the registry - jungle - ends up with
     * its canopies just touching. Everything else follows from that: a lighter biome thins out
     * proportionally, and a bigger schematic stands further from its neighbours instead of merging
     * into one continuous roof of leaves.</p>
     */
    private static final double REFERENCE_DENSITY = 0.0135;

    private final TerrainEngine engine;
    private final PrefabRegistry prefabs;
    private final int chunkRadius;

    public FeaturePlacer(TerrainEngine engine) {
        this(engine, PrefabRegistry.empty());
    }

    public FeaturePlacer(TerrainEngine engine, PrefabRegistry prefabs) {
        this.engine = engine;
        this.prefabs = prefabs;
        int reach = 8;
        for (Prefab prefab : prefabs.category("trees")) {
            reach = Math.max(reach, prefab.radius());
        }
        // Big enough that the widest prefab in the folder still reaches in from its origin chunk.
        this.chunkRadius = Math.max(2, (reach + 15) / 16);
    }

    /** Horizontal reach in chunks that a feature can extend from its origin chunk. */
    public int chunkRadius() {
        return chunkRadius;
    }

    /** Places every feature whose origin lies inside the given chunk. */
    public void place(int chunkX, int chunkZ, RegionWriter writer) {
        ChunkTerrain terrain = engine.terrain(chunkX, chunkZ);
        FastRandom random = FastRandom.forChunk(engine.seed(), chunkX, chunkZ, TREE_SALT);
        double globalTreeDensity = engine.settings().treeDensity;
        double globalDecoration = engine.settings().decorationDensity;
        boolean haveTrees = prefabs.has("trees");

        for (int attempt = 0; attempt < TREE_ATTEMPTS; attempt++) {
            int localX = random.nextInt(16);
            int localZ = random.nextInt(16);
            if (!haveTrees) {
                continue;
            }
            int index = ChunkTerrain.index(localX, localZ);
            ArkBiome biome = engine.biomes().byId(terrain.biome[index]);
            if (biome.trees.length == 0 || biome.treeDensity <= 0.0) {
                continue;
            }
            // Roll first against the densest a tree could ever be here, so the great majority of
            // attempts end before they touch the terrain or the registry at all.
            double ceiling = biome.treeDensity * globalTreeDensity * 256.0 / TREE_ATTEMPTS;
            if (!random.chance(ceiling)) {
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
            if (slope(x, z, groundY) > 4) {
                continue;
            }

            long treeSeed = Hashing.hash3(engine.seed() ^ TREE_SALT, x, groundY, z);
            Prefab tree = chooseTree(biome, treeSeed);
            if (tree == null || groundY + tree.height >= writer.maxY()) {
                continue;
            }
            // Bigger prefabs stand further apart: thinning is what keeps a forest of thirty block
            // crowns from turning into one continuous canopy, and it makes the density figure a
            // biome declares mean the same thing whatever size of schematic is dropped in.
            double spacing = 2.0 * tree.radius();
            double thinning = Math.min(1.0, 1.0 / (REFERENCE_DENSITY * spacing * spacing));
            if (!new FastRandom(treeSeed ^ 0x7141_1EDL).chance(thinning)) {
                continue;
            }
            int rotation = (int) (treeSeed >>> 58) & 3;
            tree.blit(writer, x, groundY + 1, z, rotation, Prefab.BlitOptions.flooded());
        }

        placeBoulders(chunkX, chunkZ, terrain, writer, globalDecoration);
    }

    /**
     * Picks the prefab for one tree.
     *
     * <p>Species comes from the biome, size from the biome's own taste plus the preset, and the
     * final choice from the registry's scoring - so a jungle asks for jungle and gets the best
     * jungle-ish schematic the server actually has.</p>
     */
    private Prefab chooseTree(ArkBiome biome, long treeSeed) {
        FastRandom random = new FastRandom(treeSeed ^ 0x7EEEL);
        String species = biome.pickTree(random.nextDouble());
        if (species == null) {
            return null;
        }
        if (random.chance(biome.deadTreeChance)) {
            species = TreeKind.DEAD;
        } else if (random.chance(exoticChance())) {
            // A preset's fingerprint on the forest: strange trees exist at all only above BASE.
            species = random.chance(0.5) ? TreeKind.CRYSTAL : TreeKind.AUTUMN;
        }
        String size = pickSize(biome, species, random);
        return prefabs.pickTree(species, size, random);
    }

    /** How often a preset lets a crystal or autumn tree stand in for an ordinary one. */
    private double exoticChance() {
        return switch (engine.preset()) {
            case BASE -> 0.0;
            case CHAOTIC -> 0.04;
            case INSANE -> 0.14;
        };
    }

    private String pickSize(ArkBiome biome, String species, FastRandom random) {
        double giant = biome.giantTreeChance + presetGiantBonus();
        if (TreeKind.prefersGiant(species) || random.chance(giant)) {
            return "giant";
        }
        // Weighted towards the small end: a wood is mostly undergrowth and young trees with the
        // occasional old giant, not a plantation of identical crowns.
        double roll = random.nextDouble();
        if (roll < 0.35) {
            return "small";
        }
        if (roll < 0.62) {
            return "medium";
        }
        return roll < 0.90 ? "large" : "giant";
    }

    private double presetGiantBonus() {
        return switch (engine.preset()) {
            case BASE -> 0.0;
            case CHAOTIC -> 0.10;
            case INSANE -> 0.35;
        };
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
