package com.arkcronist.gen.core;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.prefab.PrefabRegistry;
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
            blocks.put(BlockKey.key(x, y, z), blockId);
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
        PrefabRegistry prefabs = PrefabRegistry.fromDirectory(java.nio.file.Path.of("src/main/resources/prefabs"),
                message -> {
                });
        StructurePlacer placer = new StructurePlacer(engine, java.util.Set.of(), prefabs);
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
                    && !structure.id().equals("trail_ruins") && !structure.id().equals("ancient_city")
                    // Schematic-backed families are stamped exactly as their author built them, so
                    // their lighting is the author's business and not this rule's.
                    && !structure.id().startsWith("prefab_")) {
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
        List<StructurePlacer> placers = new ArrayList<>();
        // With the prefab folder loaded, so that the schematic families exist to be found at all.
        PrefabRegistry prefabs = PrefabRegistry.fromDirectory(java.nio.file.Path.of("src/main/resources/prefabs"),
                message -> {
                });
        for (Preset preset : Preset.values()) {
            placers.add(new StructurePlacer(new TerrainEngine(2468013L, preset), java.util.Set.of(), prefabs));
        }
        // Stop as soon as a tag turns up: most are found in the first preset, and searching all
        // three for all of them made this the slowest test in the suite by an order of magnitude.
        for (StructureTag tag : StructureTag.values()) {
            for (StructurePlacer placer : placers) {
                if (placer.locate(0, 0, tag, 14) != null) {
                    found.add(tag);
                    break;
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
    @DisplayName("surface structures end up on flat ground, not halfway up a hill")
    void surfaceStructuresLandOnFlatGround() {
        // The complaint from the server: castles, villages and towers were being swallowed by
        // hillsides. Placement now searches its cell for the flattest site it can reach.
        for (Preset preset : Preset.values()) {
            TerrainEngine engine = new TerrainEngine(515151L, preset);
            StructurePlacer placer = new StructurePlacer(engine);
            int checked = 0;
            int steep = 0;
            for (StructureTag tag : new StructureTag[]{StructureTag.CASTLE, StructureTag.VILLAGE,
                    StructureTag.TOWER, StructureTag.BATTLE_TOWER, StructureTag.CITY}) {
                int[] site = placer.locate(0, 0, tag, 12);
                if (site == null) {
                    continue;
                }
                checked++;
                int min = Integer.MAX_VALUE;
                int max = Integer.MIN_VALUE;
                for (int i = 0; i < 9; i++) {
                    int height = engine.surfaceHeight(site[0] + (i % 3 - 1) * 12,
                            site[2] + (i / 3 - 1) * 12);
                    min = Math.min(min, height);
                    max = Math.max(max, height);
                }
                if (max - min > 20) {
                    steep++;
                }
            }
            assertTrue(checked > 0, preset + ": no landmark structures found to check");
            assertEquals(0, steep, preset + ": " + steep + " of " + checked
                    + " landmark structures sit on steep ground");
        }
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
