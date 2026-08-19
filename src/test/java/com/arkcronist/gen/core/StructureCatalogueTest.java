package com.arkcronist.gen.core;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.structure.*;
import com.arkcronist.gen.core.terrain.Preset;
import com.arkcronist.gen.core.terrain.TerrainEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every structure in the catalogue must actually build, light itself, and be reachable in a world.
 *
 * <p>These are the guards for the two failures found by playing the first build: buildings that came
 * out as unlit boxes, and structure families that could never spawn because no biome accepted them.</p>
 */
class StructureCatalogueTest {

    /** Unbounded collector so a structure can be inspected as a whole. */
    static final class OpenBuffer implements RegionWriter {
        final Map<Long, Integer> blocks = new HashMap<>();

        @Override
        public void set(int x, int y, int z, int blockId) {
            blocks.put(TreeTest.key(x, y, z), blockId);
        }

        @Override
        public boolean contains(int x, int y, int z) {
            return true;
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

    private static final Set<String> LIGHT_KEYWORDS = Set.of(
            "torch", "lantern", "campfire", "sea_lantern", "shroomlight", "glowstone", "copper_bulb",
            "candle", "fire", "magma", "lava", "amethyst");

    @Test
    @DisplayName("every structure builds, places blocks and is not a dark empty box")
    void everyStructureBuilds() {
        TerrainEngine engine = new TerrainEngine(1234567L, Preset.CHAOTIC);
        StructurePlacer placer = new StructurePlacer(engine);
        List<Structure> catalogue = placer.structures();
        assertTrue(catalogue.size() >= 28, "expected the full vanilla-equivalent catalogue, found " + catalogue.size());

        List<String> silent = new ArrayList<>();
        List<String> dark = new ArrayList<>();
        for (Structure structure : catalogue) {
            boolean built = false;
            boolean lit = false;
            for (int attempt = 0; attempt < 40 && !built; attempt++) {
                int x = attempt * 137 - 2000;
                int z = attempt * -211 + 1500;
                StructureContext context = new StructureContext(engine, x, z,
                        new FastRandom(0xABCDEF ^ (attempt * 31L)));
                if (!structure.canPlace(context)) {
                    continue;
                }
                StructureBuffer buffer = new StructureBuffer();
                structure.build(context, buffer);
                if (buffer.blockCount() < 20) {
                    continue;
                }
                built = true;
                OpenBuffer open = new OpenBuffer();
                for (int cx = (buffer.minX() >> 4) - 1; cx <= (buffer.maxX() >> 4) + 1; cx++) {
                    for (int cz = (buffer.minZ() >> 4) - 1; cz <= (buffer.maxZ() >> 4) + 1; cz++) {
                        buffer.blitChunk(cx, cz, open);
                    }
                }
                assertEquals(buffer.blockCount(), open.blocks.size(),
                        structure.id() + ": blit lost or duplicated blocks");
                for (int block : open.blocks.values()) {
                    String key = Blocks.REGISTRY.key(block);
                    if (LIGHT_KEYWORDS.stream().anyMatch(key::contains)) {
                        lit = true;
                        break;
                    }
                }
            }
            if (!built) {
                silent.add(structure.id());
            } else if (!lit && !structure.id().equals("buried_treasure") && !structure.id().equals("fossil")
                    && !structure.id().equals("trail_ruins") && !structure.id().equals("ancient_city")) {
                dark.add(structure.id());
            }
        }
        assertTrue(silent.isEmpty(), "these structures never built anything: " + silent);
        assertTrue(dark.isEmpty(), "these structures have no light source at all: " + dark);
    }

    @Test
    @DisplayName("every structure family can be found in a world")
    void everyFamilyIsReachable() {
        Set<StructureTag> found = new HashSet<>();
        for (Preset preset : Preset.values()) {
            TerrainEngine engine = new TerrainEngine(2468013L, preset);
            StructurePlacer placer = new StructurePlacer(engine);
            for (StructureTag tag : StructureTag.values()) {
                if (placer.locate(0, 0, tag, 14) != null) {
                    found.add(tag);
                }
            }
        }
        List<StructureTag> missing = new ArrayList<>();
        for (StructureTag tag : StructureTag.values()) {
            if (!found.contains(tag)) {
                missing.add(tag);
            }
        }
        // TREASURE hides on beaches, which are narrow: allow it to be missed near the origin.
        missing.remove(StructureTag.TREASURE);
        assertTrue(missing.isEmpty(), "no biome or grid ever produces these: " + missing);
    }

    @Test
    @DisplayName("underground structures are placed by depth, not by biome")
    void undergroundPlacement() {
        TerrainEngine engine = new TerrainEngine(99887L, Preset.BASE);
        StructurePlacer placer = new StructurePlacer(engine);
        int underground = 0;
        for (Structure structure : placer.structures()) {
            if (structure.placement() == Structure.Placement.UNDERGROUND) {
                underground++;
            }
        }
        assertTrue(underground >= 8, "expected the deep catalogue (mines, stronghold, city, vaults, geodes)");
    }
}
