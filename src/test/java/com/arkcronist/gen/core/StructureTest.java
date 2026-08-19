package com.arkcronist.gen.core;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.structure.*;
import com.arkcronist.gen.core.terrain.Preset;
import com.arkcronist.gen.core.terrain.TerrainEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StructureTest {

    private static final int MIN_Y = -64;
    private static final int MAX_Y = 320;

    static class ChunkWriter implements RegionWriter {
        final Map<Long, Integer> blocks = new HashMap<>();
        private final int chunkX;
        private final int chunkZ;

        public ChunkWriter(int chunkX, int chunkZ) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        @Override
        public void set(int x, int y, int z, int blockId) {
            if (contains(x, y, z)) {
                blocks.put(BlockKey.key(x, y, z), blockId);
            }
        }

        @Override
        public boolean contains(int x, int y, int z) {
            int x0 = chunkX << 4;
            int z0 = chunkZ << 4;
            return x >= x0 && x <= x0 + 15 && z >= z0 && z <= z0 + 15 && y >= MIN_Y && y < MAX_Y;
        }

        @Override
        public boolean intersectsColumn(int minX, int minZ, int maxX, int maxZ) {
            int x0 = chunkX << 4;
            int z0 = chunkZ << 4;
            return maxX >= x0 && minX <= x0 + 15 && maxZ >= z0 && minZ <= z0 + 15;
        }

        @Override
        public int minY() {
            return MIN_Y;
        }

        @Override
        public int maxY() {
            return MAX_Y;
        }
    }

    @Test
    @DisplayName("a structure buffer blits into chunks without losing or duplicating blocks")
    void blitCoversEveryBlock() {
        StructureBuffer buffer = new StructureBuffer();
        BuildKit.box(buffer, -20, 60, -20, 35, 78, 35, 1);
        int expected = buffer.blockCount();

        int written = 0;
        Map<Long, Integer> assembled = new HashMap<>();
        for (int cx = -3; cx <= 3; cx++) {
            for (int cz = -3; cz <= 3; cz++) {
                ChunkWriter writer = new ChunkWriter(cx, cz);
                buffer.blitChunk(cx, cz, writer);
                written += writer.blocks.size();
                assembled.putAll(writer.blocks);
            }
        }
        assertEquals(expected, written, "blit wrote a different number of blocks than the buffer holds");
        assertEquals(expected, assembled.size(), "blit produced overlapping writes");
    }

    @ParameterizedTest
    @EnumSource(Preset.class)
    @DisplayName("structure placement is deterministic for a seed")
    void placementIsDeterministic(Preset preset) {
        Map<Long, Integer> first = place(new TerrainEngine(3131L, preset));
        Map<Long, Integer> second = place(new TerrainEngine(3131L, preset));
        assertEquals(first, second, preset + ": structures moved between runs");
    }

    private Map<Long, Integer> place(TerrainEngine engine) {
        StructurePlacer placer = new StructurePlacer(engine);
        Map<Long, Integer> all = new HashMap<>();
        for (int cx = -6; cx <= 6; cx++) {
            for (int cz = -6; cz <= 6; cz++) {
                ChunkWriter writer = new ChunkWriter(cx, cz);
                placer.placeInto(cx, cz, writer, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
                all.putAll(writer.blocks);
            }
        }
        return all;
    }

    @ParameterizedTest
    @EnumSource(Preset.class)
    @DisplayName("the world contains structures, with loot and inhabitants")
    void structuresExist(Preset preset) {
        TerrainEngine engine = new TerrainEngine(646464L, preset);
        StructurePlacer placer = new StructurePlacer(engine);
        List<MobSpawn> spawns = new ArrayList<>();
        List<LootMarker> loot = new ArrayList<>();
        List<SpawnerMarker> spawners = new ArrayList<>();
        int blocks = 0;

        for (int cx = -14; cx <= 14; cx++) {
            for (int cz = -14; cz <= 14; cz++) {
                ChunkWriter writer = new ChunkWriter(cx, cz);
                placer.placeInto(cx, cz, writer, spawns, loot, spawners);
                blocks += writer.blocks.size();
            }
        }
        assertTrue(blocks > 500, preset + ": barely any structure blocks in 841 chunks (" + blocks + ")");
        assertFalse(loot.isEmpty(), preset + ": no loot containers were placed");
        assertFalse(spawns.isEmpty(), preset + ": no structure mobs were requested");
        for (LootMarker marker : loot) {
            assertTrue(marker.tier() >= 0 && marker.tier() <= 3, "loot tier out of range: " + marker.tier());
        }
        for (MobSpawn spawn : spawns) {
            assertNotNull(spawn.entityType());
            assertTrue(spawn.y() > MIN_Y && spawn.y() < MAX_Y, "mob requested outside the world");
        }
    }

    @Test
    @DisplayName("minibosses are named and tiered")
    void minibossesAreConfigured() {
        TerrainEngine engine = new TerrainEngine(24L, Preset.CHAOTIC);
        StructurePlacer placer = new StructurePlacer(engine);
        List<MobSpawn> spawns = new ArrayList<>();
        for (int cx = -14; cx <= 14; cx++) {
            for (int cz = -14; cz <= 14; cz++) {
                placer.placeInto(cx, cz, new ChunkWriter(cx, cz), spawns, new ArrayList<>(), new ArrayList<>());
            }
        }
        List<MobSpawn> bosses = spawns.stream().filter(MobSpawn::miniboss).toList();
        assertFalse(bosses.isEmpty(), "no minibosses were requested anywhere");
        for (MobSpawn boss : bosses) {
            assertNotNull(boss.name(), "miniboss without a role name");
            assertTrue(boss.tier() >= 2, "miniboss tier too low: " + boss.tier());
        }
    }

    @Test
    @DisplayName("locate finds a structure of the requested type")
    void locateWorks() {
        TerrainEngine engine = new TerrainEngine(31415L, Preset.BASE);
        StructurePlacer placer = new StructurePlacer(engine);
        int[] found = placer.locate(0, 0, StructureTag.UNDERGROUND, 8);
        assertNotNull(found, "no vault found within 8 grid rings");
        int[] again = placer.locate(0, 0, StructureTag.UNDERGROUND, 8);
        assertArrayEquals(found, again, "locate is not deterministic");
    }
}
