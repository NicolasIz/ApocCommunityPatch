package com.arkcronist.gen.core;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.prefab.Prefab;
import com.arkcronist.gen.core.prefab.PrefabRegistry;
import com.arkcronist.gen.core.structure.LootMarker;
import com.arkcronist.gen.core.structure.MobSpawn;
import com.arkcronist.gen.core.structure.RegionWriter;
import com.arkcronist.gen.core.structure.SpawnerMarker;
import com.arkcronist.gen.core.structure.StructurePlacer;
import com.arkcronist.gen.core.terrain.BlockWriter;
import com.arkcronist.gen.core.terrain.Preset;
import com.arkcronist.gen.core.terrain.TerrainEngine;
import com.arkcronist.gen.core.terrain.TerrainSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ancient city is built here, from a schematic, rather than by the server.
 *
 * <p>The server can no longer place one, because it only ever does so in the {@code deep_dark}
 * biome and the biome provider stops reporting that key. What these tests hold to is that the
 * replacement arrives <em>whole</em>: the city is written over its entire footprint, air included,
 * so whatever the cave carver left inside that box is overwritten and cannot cut it.</p>
 */
class AncientCityTest {

    private static final Path PREFABS = Path.of("src", "main", "resources", "prefabs");

    private static PrefabRegistry prefabs() {
        return PrefabRegistry.fromDirectory(PREFABS, message -> {
        });
    }

    /** Collects a whole region: terrain written by the engine, structures written over it. */
    private static final class Region implements BlockWriter, RegionWriter {
        final Map<Long, Integer> blocks = new HashMap<>();
        int originX;
        int originZ;

        static long key(int x, int y, int z) {
            return (((long) x) << 40) ^ (((long) (y + 64)) << 22) ^ (z & 0x3FFFFFL);
        }

        @Override
        public void set(int localX, int y, int localZ, int blockId) {
            blocks.put(key(originX + localX, y, originZ + localZ), blockId);
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

        int at(int x, int y, int z) {
            return blocks.getOrDefault(key(x, y, z), 0);
        }
    }

    /** World-coordinate face of the same region, for the structure pass. */
    private record WorldFace(Region region) implements RegionWriter {
        @Override
        public void set(int x, int y, int z, int blockId) {
            region.blocks.put(Region.key(x, y, z), blockId);
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

    @Test
    @DisplayName("the schematic ships with the plugin and is the only file in its folder")
    void schematicIsInstalled() {
        PrefabRegistry registry = prefabs();
        assertTrue(registry.has("ancient_city"), "prefabs/ancient_city/ has no schematic in it");
        List<Prefab> found = registry.category("ancient_city");
        assertEquals(1, found.size(), "the ancient city folder should hold exactly one file");
        Prefab city = found.get(0);
        assertTrue(city.width > 100 && city.length > 100,
                "that does not look like an ancient city: " + city.width + "x" + city.height + "x" + city.length);
    }

    @Test
    @DisplayName("no command block survives loading, whatever the file contains")
    void commandBlocksAreNeverStamped() {
        // The supplied schematic carried an always-active command block running "/kill @e". Block
        // entity data never travels through this generator, so the command itself could not have
        // reached a world - but the block should not either.
        int commandBlock = Blocks.REGISTRY.id("minecraft:command_block");
        Prefab city = prefabs().category("ancient_city").get(0);
        for (int rotation = 0; rotation < 4; rotation++) {
            assertTrue(city.blockCount(commandBlock, rotation) == 0,
                    "a command block survived into rotation " + rotation);
        }
    }

    @Test
    @DisplayName("the city survives the list of structures that stand down for vanilla")
    void theCityIsNotDisabledByTheVanillaHandover() {
        // The bug this exists for. With vanilla-structures on - which is the default - every built-in
        // structure that duplicates a vanilla one is disabled. The ancient city was on that list,
        // from back when the server still placed its own. Once the server stopped being able to,
        // that entry became the thing preventing ANY city from existing: the schematic loaded, the
        // structure was constructed, and register() threw it away. On a live server it showed up as
        // "Nothing found within range" with the prefab sitting right there in /ag prefabs.
        assertTrue(!com.arkcronist.gen.bukkit.config.ArkConfig.vanillaEquivalents().contains("ancient_city"),
                "ancient_city is back on the stand-down list; nothing will ever build one");

        TerrainEngine engine = new TerrainEngine(20260823L, Preset.INSANE,
                TerrainSettings.forPreset(Preset.INSANE), 2048);
        StructurePlacer placer = new StructurePlacer(engine,
                com.arkcronist.gen.bukkit.config.ArkConfig.vanillaEquivalents(), prefabs());
        boolean registered = placer.structures().stream()
                .anyMatch(structure -> structure.tag() == StructureTag.ANCIENT_CITY);
        assertTrue(registered, "the ancient city is not registered under the real disabled set");
    }

    @ParameterizedTest
    @EnumSource(Preset.class)
    @DisplayName("a city can be found, and the same seed always puts it in the same place")
    void placementIsDeterministic(Preset preset) {
        int[] first = locate(preset);
        assertNotNull(first, preset + ": no ancient city within reach of the origin");
        int[] second = locate(preset);
        assertEquals(first[0], second[0], preset + ": x moved between two identical lookups");
        assertEquals(first[2], second[2], preset + ": z moved between two identical lookups");
    }

    private static int[] locate(Preset preset) {
        TerrainEngine engine = new TerrainEngine(20260823L, preset, TerrainSettings.forPreset(preset), 4096);
        StructurePlacer placer = new StructurePlacer(engine,
                com.arkcronist.gen.bukkit.config.ArkConfig.vanillaEquivalents(), prefabs());
        for (int ring = 0; ring <= 6; ring++) {
            int[] site = placer.locate(0, 0, StructureTag.ANCIENT_CITY, ring);
            if (site != null) {
                return site;
            }
        }
        return null;
    }

    @ParameterizedTest
    @EnumSource(Preset.class)
    @DisplayName("every site has solid rock over the whole roof, so the sea cannot cut into it")
    void noSiteHasItsRoofCutOpen(Preset preset) {
        // Reported from the server: the ocean was deep enough to break through the roof and let the
        // water in. Two things caused it - the sea floor reached Y=-58, and canPlace only looked at
        // the centre column, so a footprint 172 blocks across could sit half under a hill and half
        // under a trench. The site test now measures the thinnest point of the roof.
        PrefabRegistry registry = prefabs();
        Prefab city = registry.category("ancient_city").get(0);
        TerrainEngine engine = new TerrainEngine(20260823L, preset, TerrainSettings.forPreset(preset), 4096);
        StructurePlacer placer = new StructurePlacer(engine,
                com.arkcronist.gen.bukkit.config.ArkConfig.vanillaEquivalents(), registry);

        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        int checked = 0;
        int cut = 0;
        java.util.Random random = new java.util.Random(11);
        for (int attempt = 0; attempt < 24; attempt++) {
            int qx = random.nextInt(30000) - 15000;
            int qz = random.nextInt(30000) - 15000;
            int[] site = null;
            for (int ring = 0; ring <= 4 && site == null; ring++) {
                site = placer.locate(qx, qz, StructureTag.ANCIENT_CITY, ring);
            }
            if (site == null || !seen.add(site[0] + "," + site[2])) {
                continue;
            }
            checked++;
            int roof = (site[1] - 3) + city.height;
            int lowest = Integer.MAX_VALUE;
            for (int dx = -city.radius(); dx <= city.radius(); dx += 6) {
                for (int dz = -city.radius(); dz <= city.radius(); dz += 6) {
                    lowest = Math.min(lowest, engine.heightmapHeight(site[0] + dx, site[2] + dz));
                }
            }
            if (lowest <= roof) {
                cut++;
            }
        }
        assertTrue(checked >= 10, preset + ": only " + checked + " cities found, sample too small");
        assertEquals(0, cut, preset + ": " + cut + " of " + checked + " cities have ground cutting their roof");
    }

    @Test
    @DisplayName("the city generates whole: every block of the file reaches the world")
    void theCityArrivesIntact() {
        // The test that matters. Terrain and caves are generated first, then the structure pass runs
        // over them, exactly as the server does it. If a cave could cut the city, the counts would
        // come up short.
        Preset preset = Preset.INSANE;
        PrefabRegistry registry = prefabs();
        Prefab city = registry.category("ancient_city").get(0);
        TerrainEngine engine = new TerrainEngine(20260823L, preset, TerrainSettings.forPreset(preset), 8192);
        StructurePlacer placer = new StructurePlacer(engine,
                com.arkcronist.gen.bukkit.config.ArkConfig.vanillaEquivalents(), registry);

        int[] site = null;
        for (int ring = 0; ring <= 6 && site == null; ring++) {
            site = placer.locate(0, 0, StructureTag.ANCIENT_CITY, ring);
        }
        assertNotNull(site, "no ancient city near the origin to check");

        Region region = new Region();
        WorldFace face = new WorldFace(region);
        List<LootMarker> loot = new ArrayList<>();
        int centreX = site[0] >> 4;
        int centreZ = site[2] >> 4;
        for (int cx = centreX - 8; cx <= centreX + 8; cx++) {
            for (int cz = centreZ - 8; cz <= centreZ + 8; cz++) {
                region.originX = cx << 4;
                region.originZ = cz << 4;
                engine.generateChunk(cx, cz, region);
                placer.placeInto(cx, cz, face, new ArrayList<MobSpawn>(), loot, new ArrayList<SpawnerMarker>());
            }
        }

        // Sculk is the city and nothing else down there makes it, so its count is a direct check
        // that the whole file landed.
        int sculk = Blocks.REGISTRY.id("minecraft:sculk");
        long placed = region.blocks.values().stream().filter(id -> id == sculk).count();
        long expected = city.blockCount(sculk, 0);
        assertTrue(expected > 1000, "the schematic has almost no sculk in it, this check is meaningless");
        assertEquals(expected, placed,
                "the city lost blocks on the way into the world - " + placed + " of " + expected + " sculk");

        long chests = loot.stream().filter(m -> "ancient_city".equals(m.theme())).count();
        assertTrue(chests > 0, "the city registered no containers for loot");
    }

    /**
     * The chamber must not read as the cuboid the file was cut as.
     *
     * <p>What a player sees standing in the hall is a horizontal slice of it, so that is what is
     * measured: along each of the four planes the file was cut on, at each height, the longest
     * unbroken run of wall that sits exactly on that plane. A schematic dropped in as-is scores the
     * full width of the file on every slice - a wall running dead straight for 172 blocks, which is
     * the box. Measured on the code this test was written against, the worst slice of the worst
     * wall is 35 blocks and the average is single digits; before the openings were extruded it was
     * 118.</p>
     *
     * <p>The limit here is 60, a third of the file. It is deliberately loose: the point is to catch
     * the chamber going back to being a box, not to pin the noise down to the block.</p>
     */
    @Test
    @DisplayName("the chamber's walls do not run straight along the edges of the file")
    void theChamberIsNotACuboid() {
        Preset preset = Preset.INSANE;
        PrefabRegistry registry = prefabs();
        TerrainEngine engine = new TerrainEngine(20260823L, preset, TerrainSettings.forPreset(preset), 8192);
        StructurePlacer placer = new StructurePlacer(engine,
                com.arkcronist.gen.bukkit.config.ArkConfig.vanillaEquivalents(), registry);

        int[] site = null;
        for (int ring = 0; ring <= 6 && site == null; ring++) {
            site = placer.locate(0, 0, StructureTag.ANCIENT_CITY, ring);
        }
        assertNotNull(site, "no ancient city near the origin to check");

        Region region = new Region();
        WorldFace face = new WorldFace(region);
        int centreX = site[0] >> 4;
        int centreZ = site[2] >> 4;
        for (int cx = centreX - 9; cx <= centreX + 9; cx++) {
            for (int cz = centreZ - 9; cz <= centreZ + 9; cz++) {
                region.originX = cx << 4;
                region.originZ = cz << 4;
                engine.generateChunk(cx, cz, region);
                placer.placeInto(cx, cz, face, new ArrayList<MobSpawn>(), loot(), new ArrayList<SpawnerMarker>());
            }
        }

        // Where the file is. Taken from the run of columns holding city material that contains the
        // site, rather than from the outer extent of city material in the window: the window is wide
        // enough to clip a second city, and measuring the gap between two of them as though it were
        // one wall gives a number that means nothing.
        int sculk = Blocks.REGISTRY.id("minecraft:sculk");
        int tiles = Blocks.REGISTRY.id("minecraft:deepslate_tiles");
        int reach = 140;
        boolean[] alongX = new boolean[2 * reach + 1];
        boolean[] alongZ = new boolean[2 * reach + 1];
        for (int x = site[0] - reach; x <= site[0] + reach; x++) {
            for (int z = site[2] - reach; z <= site[2] + reach; z++) {
                for (int y = -56; y <= -10; y++) {
                    int id = region.at(x, y, z);
                    if (id == sculk || id == tiles) {
                        alongX[x - site[0] + reach] = true;
                        alongZ[z - site[2] + reach] = true;
                        break;
                    }
                }
            }
        }
        int minX = site[0];
        int maxX = site[0];
        int minZ = site[2];
        int maxZ = site[2];
        while (minX - 1 >= site[0] - reach && alongX[minX - 1 - site[0] + reach]) {
            minX--;
        }
        while (maxX + 1 <= site[0] + reach && alongX[maxX + 1 - site[0] + reach]) {
            maxX++;
        }
        while (minZ - 1 >= site[2] - reach && alongZ[minZ - 1 - site[2] + reach]) {
            minZ--;
        }
        while (maxZ + 1 <= site[2] + reach && alongZ[maxZ + 1 - site[2] + reach]) {
            maxZ++;
        }
        assertTrue(maxX - minX > 120 && maxZ - minZ > 120,
                "the city was not found where it was expected, nothing useful can be measured");

        int limit = 60;
        for (int wall = 0; wall < 4; wall++) {
            boolean alongTheZAxis = wall < 2;
            int from = alongTheZAxis ? minZ : minX;
            int to = alongTheZAxis ? maxZ : maxX;
            int worst = 0;
            for (int y = -54; y <= -14; y++) {
                int run = 0;
                for (int p = from; p <= to; p++) {
                    int insideX;
                    int insideZ;
                    int outsideX;
                    int outsideZ;
                    switch (wall) {
                        case 0 -> {
                            insideX = minX;
                            insideZ = p;
                            outsideX = minX - 1;
                            outsideZ = p;
                        }
                        case 1 -> {
                            insideX = maxX;
                            insideZ = p;
                            outsideX = maxX + 1;
                            outsideZ = p;
                        }
                        case 2 -> {
                            insideX = p;
                            insideZ = minZ;
                            outsideX = p;
                            outsideZ = minZ - 1;
                        }
                        default -> {
                            insideX = p;
                            insideZ = maxZ;
                            outsideX = p;
                            outsideZ = maxZ + 1;
                        }
                    }
                    // A visible face: open where the file ends, solid immediately beyond it.
                    boolean flat = isOpen(region.at(insideX, y, insideZ))
                            && !isOpen(region.at(outsideX, y, outsideZ));
                    run = flat ? run + 1 : 0;
                    worst = Math.max(worst, run);
                }
            }
            assertTrue(worst < limit, "wall " + wall + " runs straight along the edge of the file for "
                    + worst + " blocks - the chamber is reading as the box it was cut as");
        }
    }

    private static List<LootMarker> loot() {
        return new ArrayList<>();
    }

    private static boolean isOpen(int blockId) {
        return blockId == 0 || blockId == Blocks.AIR || blockId == Blocks.CAVE_AIR;
    }
}
