package com.arkcronist.gen.core;

import com.arkcronist.gen.core.bench.CountingWriter;
import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.prefab.Prefab;
import com.arkcronist.gen.core.prefab.PrefabCategories;
import com.arkcronist.gen.core.prefab.PrefabRegistry;
import com.arkcronist.gen.core.structure.LootMarker;
import com.arkcronist.gen.core.structure.MobSpawn;
import com.arkcronist.gen.core.structure.SpawnerMarker;
import com.arkcronist.gen.core.structure.Structure;
import com.arkcronist.gen.core.structure.StructurePlacer;
import com.arkcronist.gen.core.terrain.Preset;
import com.arkcronist.gen.core.terrain.TerrainEngine;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Server owners drop their own buildings into {@code prefabs/<family>/}; these tests are what says
 * those buildings actually reach the world, take over from the procedural versions, and sit on the
 * ground rather than hovering over it.
 */
class PrefabCategoryTest {

    private static final int MIN_Y = -64;
    private static final int MAX_Y = 320;

    @TempDir
    static Path folder;

    private static PrefabRegistry registry;

    @BeforeAll
    static void buildFolder() throws IOException {
        // Stands in for what a server owner would copy in: Viking houses, towers and a keep.
        SchematicFixtures.house(9, 4, "minecraft:spruce_planks", "minecraft:dark_oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]")
                .write(folder.resolve("houses"), "medium_viking_longhouse_01");
        SchematicFixtures.house(7, 3, "minecraft:oak_planks", "minecraft:spruce_planks")
                .write(folder.resolve("houses"), "small_viking_hut_01");
        SchematicFixtures.house(11, 5, "minecraft:stripped_spruce_log[axis=y]", "minecraft:spruce_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]")
                .write(folder.resolve("houses"), "large_viking_hall_01");
        SchematicFixtures.tower(9, 22).write(folder.resolve("towers"), "large_watchtower_01");
        SchematicFixtures.tower(13, 34).write(folder.resolve("battle_towers"), "giant_battle_tower_01");
        SchematicFixtures.castle(31, 8, 20).write(folder.resolve("castles"), "giant_keep_01");

        List<String> problems = new ArrayList<>();
        registry = PrefabRegistry.fromDirectory(folder, problems::add);
        assertTrue(problems.isEmpty(), "fixtures failed to load: " + problems);
    }

    @Test
    @DisplayName("a folder of buildings loads as its own category, no code and no config")
    void foldersBecomeCategories() {
        assertEquals(Set.of("battle_towers", "castles", "houses", "towers"),
                new HashSet<>(registry.categories()));
        assertEquals(3, registry.category("houses").size());
        assertEquals(1, registry.category("castles").size());
        Prefab keep = registry.byId("giant_keep_01");
        assertNotNull(keep);
        assertEquals("giant", keep.sizeClass, "the size word in the file name should be honoured");
        assertTrue(keep.hasTag("keep"), "words in the file name should become tags: " + keep.tags);
    }

    @Test
    @DisplayName("a category with files takes the place of the procedural structure it replaces")
    void prefabFamiliesSupplantTheBuiltIns() {
        TerrainEngine engine = new TerrainEngine(4242L, Preset.BASE);
        List<String> withPrefabs = ids(new StructurePlacer(engine, Set.of(), registry));
        List<String> withoutPrefabs = ids(new StructurePlacer(engine, Set.of(), PrefabRegistry.empty()));

        for (String replaced : List.of("castle", "tower", "battle_tower", "village")) {
            assertTrue(withoutPrefabs.contains(replaced), "expected a built-in " + replaced);
            assertFalse(withPrefabs.contains(replaced),
                    replaced + " should have stood down for the prefab folder");
        }
        assertTrue(withPrefabs.contains(PrefabCategories.structureId("castles")));
        assertTrue(withPrefabs.contains(PrefabCategories.structureId("towers")));
        assertTrue(withPrefabs.contains("prefab_village"));
        // Families nobody supplied files for are untouched.
        assertTrue(withPrefabs.contains("fortress"));
        assertTrue(withPrefabs.contains("temple"));
    }

    private List<String> ids(StructurePlacer placer) {
        List<String> ids = new ArrayList<>();
        for (Structure structure : placer.structures()) {
            ids.add(structure.id());
        }
        return ids;
    }

    @Test
    @DisplayName("the replaced families keep their tag, so the same biomes still get them")
    void replacementsKeepTheirFamily() {
        TerrainEngine engine = new TerrainEngine(4242L, Preset.BASE);
        StructurePlacer placer = new StructurePlacer(engine, Set.of(), registry);
        for (Structure structure : placer.structures()) {
            switch (structure.id()) {
                case "prefab_castles" -> assertEquals(StructureTag.CASTLE, structure.tag());
                case "prefab_towers" -> assertEquals(StructureTag.TOWER, structure.tag());
                case "prefab_battle_towers" -> assertEquals(StructureTag.BATTLE_TOWER, structure.tag());
                case "prefab_village" -> assertEquals(StructureTag.VILLAGE, structure.tag());
                default -> {
                }
            }
        }
    }

    @Test
    @DisplayName("prefab buildings are found in the world, built, lit and garrisoned")
    void prefabBuildingsReachTheWorld() {
        for (StructureTag tag : List.of(StructureTag.CASTLE, StructureTag.TOWER, StructureTag.BATTLE_TOWER)) {
            boolean found = false;
            for (Preset preset : Preset.values()) {
                TerrainEngine engine = new TerrainEngine(864213L, preset);
                StructurePlacer placer = new StructurePlacer(engine, Set.of(), registry);
                int[] site = placer.locate(0, 0, tag, 12);
                if (site == null) {
                    continue;
                }
                found = true;
                Result result = collect(placer, site);
                assertTrue(result.blocks > 300, tag + " built only " + result.blocks + " blocks");
                assertFalse(result.spawns.isEmpty(), tag + " has nobody in it");
                break;
            }
            assertTrue(found, "no " + tag + " within twelve grid rings in any preset");
        }
    }

    @Test
    @DisplayName("a village is assembled from several houses, with villagers living in them")
    void villagesAreAssembledFromHouses() {
        boolean found = false;
        for (Preset preset : Preset.values()) {
            TerrainEngine engine = new TerrainEngine(112233L, preset);
            StructurePlacer placer = new StructurePlacer(engine, Set.of(), registry);
            int[] site = placer.locate(0, 0, StructureTag.VILLAGE, 12);
            if (site == null) {
                continue;
            }
            found = true;
            Result result = collect(placer, site);
            long villagers = result.spawns.stream().filter(s -> s.entityType().equals("VILLAGER")).count();
            assertTrue(villagers >= 3, preset + " village has only " + villagers + " villagers");
            assertFalse(result.loot.isEmpty(), "no chest anywhere in the village");
            // Several houses, not one building: a single 11-wide cottage cannot cover this much ground.
            assertTrue(result.blocks > 1200, preset + " village is only " + result.blocks + " blocks");
            break;
        }
        assertTrue(found, "no village within twelve grid rings in any preset");
    }

    @Test
    @DisplayName("a building levels its plot: no gap underneath, no hillside through the middle")
    void buildingsMeetTheGround() {
        TerrainEngine engine = new TerrainEngine(864213L, Preset.CHAOTIC);
        StructurePlacer placer = new StructurePlacer(engine, Set.of(), registry);
        int[] site = placer.locate(0, 0, StructureTag.CASTLE, 12);
        assertNotNull(site, "no castle to inspect");

        ColumnWriter writer = new ColumnWriter();
        int chunkX = site[0] >> 4;
        int chunkZ = site[2] >> 4;
        for (int cx = chunkX - 4; cx <= chunkX + 4; cx++) {
            for (int cz = chunkZ - 4; cz <= chunkZ + 4; cz++) {
                placer.placeInto(cx, cz, writer, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
            }
        }
        assertFalse(writer.columns.isEmpty(), "the castle wrote nothing");

        // Every column the structure touched must be continuous from its lowest written block down
        // to the terrain: that is what the foundation is for.
        int floating = 0;
        for (var entry : writer.columns.entrySet()) {
            long packed = entry.getKey();
            int x = (int) (packed >> 32);
            int z = (int) packed;
            int lowest = entry.getValue();
            int ground = engine.surfaceHeight(x, z);
            if (lowest > ground + 1) {
                floating++;
            }
        }
        assertEquals(0, floating, floating + " columns start above the ground with nothing under them");
    }

    /** Remembers the lowest non-air block written in each column. */
    static final class ColumnWriter implements com.arkcronist.gen.core.structure.RegionWriter {
        final java.util.Map<Long, Integer> columns = new java.util.HashMap<>();

        @Override
        public void set(int x, int y, int z, int blockId) {
            if (blockId == Blocks.AIR) {
                return;
            }
            columns.merge(((long) x << 32) | (z & 0xFFFFFFFFL), y, Math::min);
        }

        @Override
        public boolean contains(int x, int y, int z) {
            return y >= MIN_Y && y < MAX_Y;
        }

        @Override
        public boolean intersectsColumn(int minX, int minZ, int maxX, int maxZ) {
            return true;
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

    private record Result(long blocks, List<MobSpawn> spawns, List<LootMarker> loot) {
    }

    private Result collect(StructurePlacer placer, int[] site) {
        List<MobSpawn> spawns = new ArrayList<>();
        List<LootMarker> loot = new ArrayList<>();
        List<SpawnerMarker> spawners = new ArrayList<>();
        long blocks = 0;
        int chunkX = site[0] >> 4;
        int chunkZ = site[2] >> 4;
        for (int cx = chunkX - 5; cx <= chunkX + 5; cx++) {
            for (int cz = chunkZ - 5; cz <= chunkZ + 5; cz++) {
                CountingWriter writer = new CountingWriter(MIN_Y, MAX_Y, cx, cz);
                placer.placeInto(cx, cz, writer, spawns, loot, spawners);
                blocks += writer.blocks();
            }
        }
        return new Result(blocks, spawns, loot);
    }

    @Test
    @DisplayName("an unknown folder still loads but never places itself")
    void unknownFoldersHaveNoPlacement() throws IOException {
        SchematicFixtures.house(5, 3, "minecraft:oak_planks", "minecraft:oak_planks")
                .write(folder.resolve("statues"), "small_statue_01");
        PrefabRegistry reloaded = PrefabRegistry.fromDirectory(folder, message -> {
        });
        assertEquals(1, reloaded.category("statues").size(), "the folder should still load");
        assertNull(PrefabCategories.forFolder("statues"), "and have no placement rule of its own");

        TerrainEngine engine = new TerrainEngine(4242L, Preset.BASE);
        for (Structure structure : new StructurePlacer(engine, Set.of(), reloaded).structures()) {
            assertNotEquals("prefab_statues", structure.id());
        }
    }
}
