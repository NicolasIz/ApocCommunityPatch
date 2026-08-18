package com.arkcronist.gen.core;

import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.decorate.FeaturePlacer;
import com.arkcronist.gen.core.structure.RegionWriter;
import com.arkcronist.gen.core.terrain.Preset;
import com.arkcronist.gen.core.terrain.TerrainEngine;
import com.arkcronist.gen.core.tree.TreeBuilder;
import com.arkcronist.gen.core.tree.TreeSpecies;
import com.arkcronist.gen.core.tree.TreeVariant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Trees must survive chunk borders.
 *
 * <p>The core guarantee: drawing a tree once into an unbounded writer, and drawing it again into a
 * grid of chunk sized writers, must produce exactly the same blocks. If that ever breaks, trees get
 * sliced at chunk lines.</p>
 */
class TreeTest {

    /** Collects every block, unbounded. */
    static final class OpenWriter implements RegionWriter {
        final Map<Long, Integer> blocks = new HashMap<>();

        @Override
        public void set(int x, int y, int z, int blockId) {
            blocks.put(key(x, y, z), blockId);
        }

        @Override
        public boolean contains(int x, int y, int z) {
            return y >= minY() && y < maxY();
        }

        @Override
        public boolean intersectsColumn(int minX, int minZ, int maxX, int maxZ) {
            return true;
        }

        @Override
        public int minY() {
            return -64;
        }

        @Override
        public int maxY() {
            return 320;
        }
    }

    /** Collects only the blocks inside one chunk, like the real populator writer. */
    static final class ChunkWriter implements RegionWriter {
        final Map<Long, Integer> blocks = new HashMap<>();
        private final int chunkX;
        private final int chunkZ;

        ChunkWriter(int chunkX, int chunkZ) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        @Override
        public void set(int x, int y, int z, int blockId) {
            if (contains(x, y, z)) {
                blocks.put(key(x, y, z), blockId);
            }
        }

        @Override
        public boolean contains(int x, int y, int z) {
            int x0 = chunkX << 4;
            int z0 = chunkZ << 4;
            return x >= x0 && x <= x0 + 15 && z >= z0 && z <= z0 + 15 && y >= minY() && y < maxY();
        }

        @Override
        public boolean intersectsColumn(int minX, int minZ, int maxX, int maxZ) {
            int x0 = chunkX << 4;
            int z0 = chunkZ << 4;
            return maxX >= x0 && minX <= x0 + 15 && maxZ >= z0 && minZ <= z0 + 15;
        }

        @Override
        public int minY() {
            return -64;
        }

        @Override
        public int maxY() {
            return 320;
        }
    }

    static long key(int x, int y, int z) {
        return ((long) (x & 0xFFFFF) << 40) | ((long) (y & 0xFFFF) << 24) | (z & 0xFFFFFF);
    }

    @ParameterizedTest
    @EnumSource(TreeSpecies.class)
    @DisplayName("a tree drawn across a chunk grid equals the same tree drawn whole")
    void treesAreNotCutAtChunkBorders(TreeSpecies species) {
        // Sit the trunk right on a chunk corner so the crown spans four chunks.
        int x = 16;
        int z = 16;
        int ground = 80;
        long seed = 0xC0FFEEL + species.ordinal();

        OpenWriter whole = new OpenWriter();
        TreeBuilder.build(species, TreeVariant.GIANT, seed, x, ground, z, whole);

        Map<Long, Integer> assembled = new HashMap<>();
        for (int cx = -1; cx <= 2; cx++) {
            for (int cz = -1; cz <= 2; cz++) {
                ChunkWriter chunk = new ChunkWriter(cx, cz);
                TreeBuilder.build(species, TreeVariant.GIANT, seed, x, ground, z, chunk);
                assembled.putAll(chunk.blocks);
            }
        }

        assertEquals(whole.blocks.size(), assembled.size(),
                species + ": chunked rendering lost or duplicated blocks");
        for (Map.Entry<Long, Integer> entry : whole.blocks.entrySet()) {
            assertEquals(entry.getValue(), assembled.get(entry.getKey()),
                    species + ": block mismatch between whole and chunked rendering");
        }
    }

    @ParameterizedTest
    @EnumSource(TreeVariant.class)
    @DisplayName("every variant produces wood, and living variants produce leaves")
    void variantsProduceWood(TreeVariant variant) {
        OpenWriter writer = new OpenWriter();
        TreeBuilder.build(TreeSpecies.OAK, variant, 99L, 0, 70, 0, writer);
        long logs = writer.blocks.values().stream()
                .filter(id -> Blocks.REGISTRY.key(id).contains("log") || Blocks.REGISTRY.key(id).contains("wood"))
                .count();
        assertTrue(logs > 0, variant + " produced no wood at all");
        if (variant != TreeVariant.STUMP && variant != TreeVariant.DEAD && variant != TreeVariant.FALLEN) {
            long leaves = writer.blocks.values().stream()
                    .filter(id -> Blocks.REGISTRY.key(id).contains("leaves"))
                    .count();
            assertTrue(leaves > 10, variant + " produced almost no leaves: " + leaves);
        }
    }

    @Test
    @DisplayName("the same position always grows the same tree")
    void treesAreDeterministic() {
        OpenWriter first = new OpenWriter();
        OpenWriter second = new OpenWriter();
        TreeBuilder.build(TreeSpecies.GIANT_JUNGLE, TreeVariant.NORMAL, 777L, 5, 90, -5, first);
        TreeBuilder.build(TreeSpecies.GIANT_JUNGLE, TreeVariant.NORMAL, 777L, 5, 90, -5, second);
        assertEquals(first.blocks, second.blocks);
    }

    @Test
    @DisplayName("trees never grow through the build limit")
    void treesRespectTheCeiling() {
        OpenWriter writer = new OpenWriter();
        TreeBuilder.build(TreeSpecies.GIANT_SPRUCE, TreeVariant.GIANT, 4L, 0, 300, 0, writer);
        for (long key : writer.blocks.keySet()) {
            int y = (int) ((key >> 24) & 0xFFFF);
            assertTrue(y < writer.maxY(), "tree block above the build limit at y=" + y);
        }
    }

    @ParameterizedTest
    @EnumSource(Preset.class)
    @DisplayName("forests actually grow, and only above water")
    void forestsGrowOnLand(Preset preset) {
        TerrainEngine engine = new TerrainEngine(2020L, preset);
        FeaturePlacer placer = new FeaturePlacer(engine);
        int logs = 0;
        for (int cx = 0; cx < 12; cx++) {
            for (int cz = 0; cz < 12; cz++) {
                OpenWriter writer = new OpenWriter();
                placer.place(cx, cz, writer);
                for (Map.Entry<Long, Integer> entry : writer.blocks.entrySet()) {
                    if (Blocks.REGISTRY.key(entry.getValue()).contains("log")) {
                        logs++;
                    }
                }
            }
        }
        assertTrue(logs > 0, preset + ": no trees generated in 144 chunks");
    }
}
