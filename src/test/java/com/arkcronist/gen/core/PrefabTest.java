package com.arkcronist.gen.core;

import com.arkcronist.gen.core.bench.CountingWriter;
import com.arkcronist.gen.core.biome.ArkBiome;
import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.decorate.FeaturePlacer;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.prefab.BlockStateRotator;
import com.arkcronist.gen.core.prefab.Prefab;
import com.arkcronist.gen.core.prefab.PrefabRegistry;
import com.arkcronist.gen.core.prefab.SchematicReader;
import com.arkcronist.gen.core.prefab.TreeKind;
import com.arkcronist.gen.core.structure.LootMarker;
import com.arkcronist.gen.core.structure.MobSpawn;
import com.arkcronist.gen.core.structure.RegionWriter;
import com.arkcronist.gen.core.structure.SpawnerMarker;
import com.arkcronist.gen.core.structure.StructurePlacer;
import com.arkcronist.gen.core.terrain.ChunkTerrain;
import com.arkcronist.gen.core.terrain.Preset;
import com.arkcronist.gen.core.terrain.TerrainEngine;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/** Everything that comes out of {@code prefabs/}: loading, rotating, stamping and placing. */
class PrefabTest {

    private static final int MIN_Y = -64;
    private static final int MAX_Y = 320;

    private static PrefabRegistry registry;

    @BeforeAll
    static void load() {
        List<String> problems = new ArrayList<>();
        registry = PrefabRegistry.fromDirectory(Path.of("src/main/resources/prefabs"), problems::add);
        assertTrue(problems.isEmpty(), "prefabs failed to load: " + problems);
    }

    /** Collects every write, unclipped. */
    static final class OpenWriter implements RegionWriter {
        final Map<Long, Integer> blocks = new HashMap<>();

        @Override
        public void set(int x, int y, int z, int blockId) {
            blocks.put(BlockKey.key(x, y, z), blockId);
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

    @Test
    @DisplayName("the bundled prefab folder loads trees, ships and landmarks")
    void bundledPrefabsLoad() {
        assertTrue(registry.size() >= 30, "only " + registry.size() + " prefabs loaded");
        assertTrue(registry.category("trees").size() >= 20, "too few trees");
        assertEquals(5, registry.category("ships").size(), "the five vessels should all load");
        assertFalse(registry.category("ruins").isEmpty(), "no landmark prefab loaded");
        for (Prefab prefab : registry.category("trees")) {
            assertTrue(prefab.solidCount > 50, prefab.id + " is nearly empty");
            assertTrue(prefab.height > 5, prefab.id + " is too short to be a tree");
        }
    }

    @Test
    @DisplayName("every rotation writes the same blocks and swaps the footprint")
    void rotationPreservesGeometry() {
        for (String category : registry.categories()) {
            for (Prefab prefab : registry.category(category)) {
                int reference = -1;
                for (int rotation = 0; rotation < 4; rotation++) {
                    OpenWriter writer = new OpenWriter();
                    int written = prefab.blit(writer, 500, 80, -300, rotation, Prefab.BlitOptions.flooded());
                    if (reference < 0) {
                        reference = written;
                        assertTrue(written > 0, prefab.id + " wrote nothing");
                    }
                    assertEquals(reference, written, prefab.id + " changed size when rotated " + rotation);
                    assertEquals(written, writer.blocks.size(), prefab.id + " wrote a position twice");
                    assertEquals(rotation % 2 == 0 ? prefab.width : prefab.length,
                            prefab.rotatedWidth(rotation), prefab.id + " footprint at rotation " + rotation);
                }
            }
        }
    }

    @Test
    @DisplayName("stamping a prefab twice produces exactly the same blocks")
    void stampingIsDeterministic() {
        Prefab tree = registry.category("trees").get(0);
        OpenWriter first = new OpenWriter();
        OpenWriter second = new OpenWriter();
        tree.blit(first, 12, 70, -44, 2, Prefab.BlitOptions.flooded());
        tree.blit(second, 12, 70, -44, 2, Prefab.BlitOptions.flooded());
        assertEquals(first.blocks, second.blocks);
    }

    @Test
    @DisplayName("wreck damage is the same every time it is applied with the same seed")
    void wreckDamageIsDeterministic() {
        Prefab ship = registry.byId("ship_brig");
        assertNotNull(ship);
        OpenWriter first = new OpenWriter();
        OpenWriter second = new OpenWriter();
        ship.blit(first, 0, 50, 0, 1, Prefab.BlitOptions.wreck(99L, 0.25));
        ship.blit(second, 0, 50, 0, 1, Prefab.BlitOptions.wreck(99L, 0.25));
        assertEquals(first.blocks, second.blocks);

        OpenWriter intact = new OpenWriter();
        ship.blit(intact, 0, 50, 0, 1, Prefab.BlitOptions.flooded());
        assertTrue(first.blocks.size() < intact.blocks.size(), "damage removed nothing");
        assertTrue(first.blocks.size() > intact.blocks.size() / 2, "damage removed almost everything");
    }

    @Test
    @DisplayName("a prefab crossing chunk borders is written complete, each chunk writing its own part")
    void prefabsCrossChunkBordersIntact() {
        for (Prefab prefab : List.of(registry.byId("giant_jungle_mangrove_01"),
                registry.byId("ship_first_rate"),
                registry.byId("citadel_bashna"))) {
            assertNotNull(prefab);
            int originX = 37;
            int originZ = -19;
            OpenWriter whole = new OpenWriter();
            prefab.blit(whole, originX, 90, originZ, 3, Prefab.BlitOptions.flooded());

            Map<Long, Integer> assembled = new HashMap<>();
            int perChunkTotal = 0;
            int reach = prefab.radius() / 16 + 2;
            for (int cx = (originX >> 4) - reach; cx <= (originX >> 4) + reach; cx++) {
                for (int cz = (originZ >> 4) - reach; cz <= (originZ >> 4) + reach; cz++) {
                    StructureTest.ChunkWriter chunk = new StructureTest.ChunkWriter(cx, cz);
                    prefab.blit(chunk, originX, 90, originZ, 3, Prefab.BlitOptions.flooded());
                    perChunkTotal += chunk.blocks.size();
                    assembled.putAll(chunk.blocks);
                }
            }
            assertEquals(whole.blocks.size(), perChunkTotal,
                    prefab.id + " lost or duplicated blocks across chunk borders");
            assertEquals(whole.blocks, assembled, prefab.id + " differs when assembled chunk by chunk");
        }
    }

    @Test
    @DisplayName("a floating ship keeps its holds dry without punching a hole in the sea")
    void interiorAirStaysInside() {
        Prefab ship = registry.byId("ship_brig");
        assertNotNull(ship);
        OpenWriter writer = new OpenWriter();
        ship.blit(writer, 0, 60, 0, 0, Prefab.BlitOptions.solid(Blocks.AIR));

        long air = writer.blocks.values().stream().filter(id -> id == Blocks.AIR).count();
        assertTrue(air > 100, "the hull has no interior at all");
        assertTrue(air < ship.solidCount, "more air than hull: the flood fill leaked outside");

        OpenWriter flooded = new OpenWriter();
        ship.blit(flooded, 0, 60, 0, 0, Prefab.BlitOptions.flooded());
        assertEquals(ship.solidCount, flooded.blocks.size(), "a flooded wreck should place only its own blocks");
    }

    @Test
    @DisplayName("chests inside a prefab are reported where they were actually written")
    void containersLandOnTheirBlocks() {
        Prefab ship = registry.byId("ship_brig");
        assertNotNull(ship);
        for (int rotation = 0; rotation < 4; rotation++) {
            OpenWriter writer = new OpenWriter();
            ship.blit(writer, 200, 64, -80, rotation, Prefab.BlitOptions.flooded());
            List<long[]> found = new ArrayList<>();
            ship.forEachContainer(200, 64, -80, rotation, (x, y, z) -> found.add(new long[]{BlockKey.key(x, y, z)}));
            assertFalse(found.isEmpty(), "the brig should carry cargo");
            for (long[] position : found) {
                assertTrue(writer.blocks.containsKey(position[0]),
                        "a container was reported at a position nothing was written to (rotation " + rotation + ")");
            }
        }
    }

    @Test
    @DisplayName("block states rotate the way Minecraft expects")
    void blockStatesRotate() {
        assertEquals("minecraft:oak_stairs[facing=east,half=bottom]",
                BlockStateRotator.rotate("minecraft:oak_stairs[facing=north,half=bottom]", 1));
        assertEquals("minecraft:oak_stairs[facing=south,half=bottom]",
                BlockStateRotator.rotate("minecraft:oak_stairs[facing=north,half=bottom]", 2));
        assertEquals("minecraft:oak_log[axis=z]", BlockStateRotator.rotate("minecraft:oak_log[axis=x]", 1));
        assertEquals("minecraft:oak_log[axis=x]", BlockStateRotator.rotate("minecraft:oak_log[axis=x]", 2));
        assertEquals("minecraft:oak_log[axis=y]", BlockStateRotator.rotate("minecraft:oak_log[axis=y]", 3));
        assertEquals("minecraft:rail[shape=ascending_south]",
                BlockStateRotator.rotate("minecraft:rail[shape=ascending_north]", 2));
        assertEquals("minecraft:oak_sign[rotation=7]",
                BlockStateRotator.rotate("minecraft:oak_sign[rotation=3]", 1));
        assertEquals("minecraft:oak_fence[east=false,north=false,south=true,west=true]",
                BlockStateRotator.rotate("minecraft:oak_fence[east=true,north=false,south=true,west=false]", 1));
        // A ladder facing up has nothing to turn, and a plain block has no state at all.
        assertEquals("minecraft:stone", BlockStateRotator.rotate("minecraft:stone", 1));
        assertEquals("minecraft:piston[facing=up]", BlockStateRotator.rotate("minecraft:piston[facing=up]", 3));
    }

    @Test
    @DisplayName("a corrupt schematic is rejected rather than crashing the load")
    void badSchematicsAreRejected() {
        byte[] garbage = {0x0A, 0x00, 0x00, 0x00};
        assertThrows(IOException.class, () ->
                SchematicReader.read(new ByteArrayInputStream(garbage), "broken", "trees", new Properties()));
        assertThrows(IOException.class, () ->
                SchematicReader.read(new ByteArrayInputStream(new byte[0]), "empty", "trees", new Properties()));
    }

    @Test
    @DisplayName("a biome's species reaches a prefab of that species")
    void speciesSelectionHitsTheRightFamily() {
        assertFamily(TreeKind.JUNGLE, Set.of("jungle", "mangrove"));
        assertFamily(TreeKind.SPRUCE, Set.of("spruce", "birch"));
        assertFamily(TreeKind.AZALEA, Set.of("azalea", "birch"));
        assertFamily(TreeKind.DEAD, Set.of("dead", "spruce", "dark_oak"));
        assertFamily(TreeKind.CRYSTAL, Set.of("crystal"));
        // Nobody supplied a cherry tree, so the registry must still answer with something sane.
        assertFamily(TreeKind.CHERRY, Set.of("azalea", "birch", "oak"));
    }

    private void assertFamily(String species, Set<String> acceptable) {
        FastRandom random = new FastRandom(species.hashCode() * 31L + 7L);
        int matches = 0;
        for (int i = 0; i < 400; i++) {
            Prefab picked = registry.pickTree(species, "giant", random);
            assertNotNull(picked, "no tree at all for " + species);
            if (!java.util.Collections.disjoint(picked.tags, acceptable)) {
                matches++;
            }
        }
        assertTrue(matches > 300, species + " landed on " + acceptable + " only " + matches + "/400 times");
    }

    @Test
    @DisplayName("ordinary forests never fill up with dead or crystal trees")
    void exoticTreesAreOptIn() {
        FastRandom random = new FastRandom(6060L);
        int exotic = 0;
        for (int i = 0; i < 2000; i++) {
            Prefab picked = registry.pickTree(TreeKind.OAK, "giant", random);
            if (picked.hasTag("dead") || picked.hasTag("crystal") || picked.hasTag("autumn")) {
                exotic++;
            }
        }
        assertTrue(exotic < 60, "an oak forest picked " + exotic + "/2000 strange trees");
    }

    @ParameterizedTest
    @EnumSource(Preset.class)
    @DisplayName("forests are planted with prefab trees, standing on the ground")
    void forestsGrowPrefabTrees(Preset preset) {
        TerrainEngine engine = new TerrainEngine(20260819L, preset);
        FeaturePlacer placer = new FeaturePlacer(engine, registry);
        int[] forest = findForest(engine);
        assertNotNull(forest, "no forest found for " + preset);

        long blocks = 0;
        for (int cx = forest[0]; cx < forest[0] + 6; cx++) {
            for (int cz = forest[1]; cz < forest[1] + 6; cz++) {
                CountingWriter writer = new CountingWriter(MIN_Y, MAX_Y, cx, cz);
                int reach = placer.chunkRadius();
                for (int dx = -reach; dx <= reach; dx++) {
                    for (int dz = -reach; dz <= reach; dz++) {
                        placer.place(cx + dx, cz + dz, writer);
                    }
                }
                blocks += writer.blocks();
            }
        }
        assertTrue(blocks > 3000, preset + " grew only " + blocks + " blocks of vegetation over 36 chunks");
    }

    /**
     * Finds the most thickly wooded chunk in a wide sweep.
     *
     * <p>Taking the densest rather than the first one over a threshold matters: a mixed coastline
     * clears a low bar and then plants almost nothing, which would make a tree test pass or fail on
     * where the sweep happened to start.</p>
     */
    private int[] findForest(TerrainEngine engine) {
        int bestX = 0;
        int bestZ = 0;
        double best = -1.0;
        for (int cx = -40; cx <= 40; cx += 3) {
            for (int cz = -40; cz <= 40; cz += 3) {
                ChunkTerrain terrain = engine.terrain(cx, cz);
                double density = 0.0;
                for (int i = 0; i < 256; i += 17) {
                    ArkBiome biome = engine.biomes().byId(terrain.biome[i]);
                    density += biome.trees.length == 0 ? 0.0 : biome.treeDensity;
                }
                if (density > best) {
                    best = density;
                    bestX = cx;
                    bestZ = cz;
                }
            }
        }
        return best <= 0.0 ? null : new int[]{bestX, bestZ};
    }

    @ParameterizedTest
    @EnumSource(Preset.class)
    @DisplayName("ships are placed on the water in every preset")
    void shipsSailInEveryPreset(Preset preset) {
        TerrainEngine engine = new TerrainEngine(77123L, preset);
        StructurePlacer placer = new StructurePlacer(engine, Set.of(), registry);
        int[] site = placer.locate(0, 0, StructureTag.SHIP, 10);
        assertNotNull(site, preset + " placed no ship within ten grid rings");

        int ground = engine.surfaceHeight(site[0], site[2]);
        int water = engine.waterLevel(site[0], site[2]);
        assertTrue(water > ground, preset + " put a ship on dry land at " + site[0] + "," + site[2]);

        // And it must actually write blocks into the chunks around that site.
        long blocks = 0;
        int chunkX = site[0] >> 4;
        int chunkZ = site[2] >> 4;
        for (int cx = chunkX - 3; cx <= chunkX + 3; cx++) {
            for (int cz = chunkZ - 3; cz <= chunkZ + 3; cz++) {
                CountingWriter writer = new CountingWriter(MIN_Y, MAX_Y, cx, cz);
                placer.placeInto(cx, cz, writer, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
                blocks += writer.blocks();
            }
        }
        assertTrue(blocks > 200, preset + " found a ship site but built only " + blocks + " blocks there");
    }

    @Test
    @DisplayName("a ship site carries loot for whoever boards it")
    void shipsCarryCargo() {
        TerrainEngine engine = new TerrainEngine(77123L, Preset.CHAOTIC);
        StructurePlacer placer = new StructurePlacer(engine, Set.of(), registry);
        int[] site = placer.locate(0, 0, StructureTag.SHIP, 10);
        assertNotNull(site);
        List<LootMarker> loot = new ArrayList<>();
        int chunkX = site[0] >> 4;
        int chunkZ = site[2] >> 4;
        for (int cx = chunkX - 3; cx <= chunkX + 3; cx++) {
            for (int cz = chunkZ - 3; cz <= chunkZ + 3; cz++) {
                placer.placeInto(cx, cz, new CountingWriter(MIN_Y, MAX_Y, cx, cz),
                        new ArrayList<>(), loot, new ArrayList<>());
            }
        }
        assertFalse(loot.isEmpty(), "no chest was registered anywhere on the ship");
    }

    @Test
    @DisplayName("the schematic landmark stands on level ground with a garrison")
    void landmarkIsPlacedAndPopulated() {
        TerrainEngine engine = new TerrainEngine(31337L, Preset.CHAOTIC);
        StructurePlacer placer = new StructurePlacer(engine, Set.of(), registry);
        int[] site = placer.locate(0, 0, StructureTag.PREFAB_RUIN, 12);
        assertNotNull(site, "no schematic landmark within twelve grid rings");

        List<MobSpawn> spawns = new ArrayList<>();
        long blocks = 0;
        int chunkX = site[0] >> 4;
        int chunkZ = site[2] >> 4;
        for (int cx = chunkX - 2; cx <= chunkX + 2; cx++) {
            for (int cz = chunkZ - 2; cz <= chunkZ + 2; cz++) {
                CountingWriter writer = new CountingWriter(MIN_Y, MAX_Y, cx, cz);
                placer.placeInto(cx, cz, writer, spawns, new ArrayList<>(), new ArrayList<SpawnerMarker>());
                blocks += writer.blocks();
            }
        }
        assertTrue(blocks > 1000, "the landmark built only " + blocks + " blocks");
        assertTrue(spawns.stream().anyMatch(MobSpawn::miniboss), "the landmark has no boss");
    }

    @Test
    @DisplayName("presets really differ: INSANE reaches for strange trees, BASE never does")
    void presetsDifferInWhatTheyPick() {
        // Sampled over several seeds: which forest sits near the origin varies enough from seed to
        // seed that a single one would be measuring the map, not the preset.
        long[] seeds = {555L, 909L, 20260819L};
        Map<Preset, Integer> exotic = new TreeMap<>();
        for (Preset preset : Preset.values()) {
            Set<String> seen = new HashSet<>();
            for (long seed : seeds) {
                TerrainEngine engine = new TerrainEngine(seed, preset);
                FeaturePlacer placer = new FeaturePlacer(engine, registry);
                int[] forest = findForest(engine);
                if (forest == null) {
                    continue;
                }
                for (int cx = forest[0]; cx < forest[0] + 12; cx++) {
                    for (int cz = forest[1]; cz < forest[1] + 12; cz++) {
                        placer.place(cx, cz, new RecordingWriter(seen));
                    }
                }
            }
            exotic.put(preset, countExotic(seen));
        }
        assertEquals(0, exotic.get(Preset.BASE),
                "BASE should never grow a crystal or autumn tree, found " + exotic);
        assertTrue(exotic.get(Preset.CHAOTIC) > 0, "CHAOTIC grew no strange trees: " + exotic);
        assertTrue(exotic.get(Preset.INSANE) > 0, "INSANE grew no strange trees: " + exotic);
    }

    private int countExotic(Set<String> blockKeys) {
        int count = 0;
        for (String key : blockKeys) {
            if (key.contains("amethyst") || key.contains("stained_glass") || key.contains("shroomlight")) {
                count++;
            }
        }
        return count;
    }

    /** Records which block states were written, so a test can tell one tree family from another. */
    static final class RecordingWriter implements RegionWriter {
        private final Set<String> seen;

        RecordingWriter(Set<String> seen) {
            this.seen = seen;
        }

        @Override
        public void set(int x, int y, int z, int blockId) {
            seen.add(Blocks.REGISTRY.key(blockId));
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
            return MIN_Y;
        }

        @Override
        public int maxY() {
            return MAX_Y;
        }
    }

    @Test
    @DisplayName("the same seed grows the same forest twice")
    void forestsAreDeterministic() {
        long first = plant(new TerrainEngine(909L, Preset.CHAOTIC));
        long second = plant(new TerrainEngine(909L, Preset.CHAOTIC));
        assertEquals(first, second);
        assertTrue(first > 0, "nothing was planted, so the comparison proves nothing");
    }

    private long plant(TerrainEngine engine) {
        FeaturePlacer placer = new FeaturePlacer(engine, registry);
        int[] forest = findForest(engine);
        assertNotNull(forest);
        long blocks = 0;
        for (int cx = forest[0]; cx < forest[0] + 5; cx++) {
            for (int cz = forest[1]; cz < forest[1] + 5; cz++) {
                CountingWriter writer = new CountingWriter(MIN_Y, MAX_Y, cx, cz);
                placer.place(cx, cz, writer);
                blocks += writer.blocks();
            }
        }
        return blocks;
    }

    @Test
    @DisplayName("a folder with no prefabs degrades quietly instead of failing")
    void emptyRegistryIsHarmless() {
        PrefabRegistry empty = PrefabRegistry.empty();
        assertEquals(0, empty.size());
        assertNull(empty.pickTree(TreeKind.OAK, "giant", new FastRandom(1L)));
        TerrainEngine engine = new TerrainEngine(4L, Preset.BASE);
        FeaturePlacer placer = new FeaturePlacer(engine, empty);
        CountingWriter writer = new CountingWriter(MIN_Y, MAX_Y, 0, 0);
        assertDoesNotThrow(() -> placer.place(0, 0, writer));
        assertDoesNotThrow(() -> new StructurePlacer(engine, Set.of(), empty));
    }
}
