package com.arkcronist.gen.core;

import com.arkcronist.gen.core.biome.ArkBiome;
import com.arkcronist.gen.core.biome.BiomeRegistry;
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
        for (int ring = 0; ring <= StructurePlacer.searchRings(StructureTag.ANCIENT_CITY); ring++) {
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

        // Distinct query points, because locate() searches everything out to the ring it is given
        // and returns the nearest hit. Walking the rings outward from one point therefore hands
        // back the same city every time - the earlier attempt at this collected exactly one city
        // and then failed for having too small a sample. Six points a long way apart, and the
        // placer is shared so its site cache still pays for itself.
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        int checked = 0;
        int cut = 0;
        int[][] origins = {{0, 0}, {9000, 0}, {0, 9000}, {-9000, 0}, {0, -9000}, {9000, 9000}};
        for (int[] origin : origins) {
            int[] site = null;
            for (int ring = 0; ring <= 8 && site == null; ring++) {
                site = placer.locate(origin[0], origin[1], StructureTag.ANCIENT_CITY, ring);
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
        assertTrue(checked >= 3, preset + ": only " + checked + " cities found, sample too small");
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
     * Only the ice spikes may hold a city.
     *
     * <p>This is the guarantee itself, and it costs nothing to check: the rule is declarative. A
     * deep landmark is placed by depth, far under whatever is overhead, but the placer asks the
     * surface biome whether the family may be there at all - so the whole rule is which biome lists
     * ANCIENT_CITY, and exactly one does.</p>
     */
    @Test
    @DisplayName("the ice spikes biome is the only one that lists the ancient city")
    void onlyTheIceSpikesListTheCity() {
        BiomeRegistry registry = new BiomeRegistry();
        List<String> hosts = new ArrayList<>();
        for (ArkBiome biome : registry.all()) {
            if (biome.structures.contains(StructureTag.ANCIENT_CITY)) {
                hosts.add(biome.name);
            }
        }
        assertEquals(List.of("glacier"), hosts,
                "the ancient city should be listed by the ice spikes and nothing else, but got " + hosts);
        ArkBiome ice = registry.byName("glacier");
        assertNotNull(ice, "the ice spikes biome is missing");
        assertEquals("minecraft:ice_spikes", ice.vanillaKey,
                "the biome hosting the city is not the ice spikes any more");
    }

    /**
     * And the nearest city really does come out under them, on dry land.
     *
     * <p>One city per preset, not ten. Sweeping rings for every city in reach is what the rule cost
     * before it was tightened, and it made this class take longer than the rest of the suite put
     * together; the first city each preset offers is enough to catch the rule being wrong, because
     * if it were wrong the first one would already be somewhere else.</p>
     */
    @ParameterizedTest
    @EnumSource(Preset.class)
    @DisplayName("the nearest city stands under the ice spikes, clear of the sea")
    void theNearestCityStandsUnderTheIceSpikes(Preset preset) {
        PrefabRegistry registry = prefabs();
        TerrainEngine engine = new TerrainEngine(20260823L, preset, TerrainSettings.forPreset(preset), 8192);
        StructurePlacer placer = new StructurePlacer(engine,
                com.arkcronist.gen.bukkit.config.ArkConfig.vanillaEquivalents(), registry);

        int[] site = null;
        for (int ring = 0; ring <= StructurePlacer.searchRings(StructureTag.ANCIENT_CITY) && site == null; ring++) {
            site = placer.locate(0, 0, StructureTag.ANCIENT_CITY, ring);
        }
        assertNotNull(site, preset + ": no ancient city anywhere within the search range");

        assertEquals("glacier", engine.biomeAt(site[0], site[2]).name,
                preset + ": the city at " + site[0] + "," + site[2] + " is not under the ice spikes");

        // The two halves of "never in the ocean and never near it", checked the way the structure
        // states them rather than as one fuzzy sweep for wet blocks. Asking for no water at all
        // anywhere in the margin would be stricter than the rule and stricter than the words: a
        // frozen pond on an ice plateau is water and is not the sea.
        Prefab city = registry.category("ancient_city").get(0);
        for (int dx = -(city.radius() + 48); dx <= city.radius() + 48; dx += 16) {
            for (int dz = -(city.radius() + 48); dz <= city.radius() + 48; dz += 16) {
                assertTrue(!engine.biomeAt(site[0] + dx, site[2] + dz).oceanic(),
                        preset + ": the city at " + site[0] + "," + site[2] + " is next to the sea");
            }
        }
        for (int dx = -city.radius(); dx <= city.radius(); dx += 8) {
            for (int dz = -city.radius(); dz <= city.radius(); dz += 8) {
                assertTrue(engine.heightmapHeight(site[0] + dx, site[2] + dz) >= engine.settings().seaLevel,
                        preset + ": there is open water over the roof of the city at "
                                + site[0] + "," + site[2]);
            }
        }
    }
}
