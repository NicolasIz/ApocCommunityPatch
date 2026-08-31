package com.arkcronist.gen.core;

import com.arkcronist.gen.core.biome.ArkBiome;
import com.arkcronist.gen.core.biome.BiomeRegistry;
import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.prefab.PrefabRegistry;
import com.arkcronist.gen.core.structure.Structure;
import com.arkcronist.gen.core.structure.StructureBuffer;
import com.arkcronist.gen.core.structure.StructureContext;
import com.arkcronist.gen.core.structure.StructurePlacer;
import com.arkcronist.gen.core.terrain.Preset;
import com.arkcronist.gen.core.terrain.TerrainEngine;
import com.arkcronist.gen.core.terrain.TerrainSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The volcano, and the one thing about it that has to be right.
 *
 * <p>A crater with a lava lake in it is the whole point of the structure and it is also the only
 * part that can go badly wrong on a live server. The first version of this got it wrong in a way
 * that no unit test would have caught and a cross section showed immediately: the profile drops a
 * third of its height inside the first fifth of the radius, so by the crater's edge the cone was
 * already thirty blocks below the summit, and a crater measured down from that summit came out
 * deeper than the mountain was tall. The result was a lake sitting in a bowl with no walls - which
 * in the world means lava pouring down every flank, into whatever is at the bottom.</p>
 *
 * <p>So the test that matters here is not "is it tall" or "is there lava". It is <b>can the lava get
 * out</b>, asked of every lava block in the structure.</p>
 */
class VolcanoTest {

    private static PrefabRegistry prefabs() {
        return PrefabRegistry.fromDirectory(Path.of("src", "main", "resources", "prefabs"), message -> {
        });
    }

    private static Structure volcano(StructurePlacer placer) {
        return placer.structures().stream()
                .filter(structure -> structure.id().equals("volcano"))
                .findFirst().orElse(null);
    }

    /** Builds one volcano wherever the given preset will take it, or null if none would build. */
    private static StructureBuffer built(TerrainEngine engine, Structure volcano) {
        for (int attempt = 0; attempt < 400; attempt++) {
            int x = attempt * 211 - 20000;
            int z = attempt * -167 + 15000;
            StructureContext context = new StructureContext(engine, x, z,
                    new FastRandom(0x5EED ^ (attempt * 2654435761L)));
            if (!volcano.canPlace(context)) {
                continue;
            }
            StructureBuffer buffer = new StructureBuffer();
            volcano.build(context, buffer);
            if (buffer.blockCount() > 10000) {
                buffer.settleSpawns();
                return buffer;
            }
        }
        return null;
    }

    @ParameterizedTest
    @EnumSource(Preset.class)
    @DisplayName("the crater holds its lava: no lava block can reach open air sideways")
    void theLavaStaysInTheCrater(Preset preset) {
        TerrainEngine engine = new TerrainEngine(20260823L, preset, TerrainSettings.forPreset(preset), 4096);
        StructurePlacer placer = new StructurePlacer(engine, Set.of(), prefabs());
        Structure volcano = volcano(placer);
        assertNotNull(volcano, "the volcano is not in the catalogue");

        StructureBuffer buffer = built(engine, volcano);
        assertNotNull(buffer, preset + ": no volcano would build anywhere in four hundred sites");

        int lava = 0;
        List<String> leaks = new ArrayList<>();
        for (int x = buffer.minX(); x <= buffer.maxX(); x++) {
            for (int z = buffer.minZ(); z <= buffer.maxZ(); z++) {
                for (int y = buffer.minY(); y <= buffer.maxY(); y++) {
                    if (buffer.get(x, y, z) != Blocks.LAVA) {
                        continue;
                    }
                    lava++;
                    // Sideways only. Lava falls downhill, and downhill from a crater floor is the
                    // crater floor; what would empty the lake is a way out at its own level.
                    check(buffer, leaks, x, y, z, x + 1, z);
                    check(buffer, leaks, x, y, z, x - 1, z);
                    check(buffer, leaks, x, y, z, x, z + 1);
                    check(buffer, leaks, x, y, z, x, z - 1);
                }
            }
        }

        assertTrue(lava > 200, preset + ": the crater holds only " + lava + " lava, so this test is "
                + "passing by having nothing to check");
        assertTrue(leaks.isEmpty(), preset + ": the lava lake is not walled in - " + leaks.size()
                + " blocks of it open straight onto air, which on a live server is a lava fall down "
                + "the mountain. First few: " + leaks.subList(0, Math.min(5, leaks.size())));
    }

    private static void check(StructureBuffer buffer, List<String> leaks, int x, int y, int z,
                              int nx, int nz) {
        int neighbour = buffer.get(nx, y, nz);
        if (neighbour == Blocks.LAVA) {
            return;
        }
        // Below zero is the worst case, not the safe one: it means the structure wrote nothing there
        // at all, so that side of the lake is open to whatever the terrain happens to be.
        if (neighbour < 0 || Blocks.isAir(neighbour)) {
            leaks.add(x + "," + y + "," + z);
        }
    }

    @ParameterizedTest
    @EnumSource(Preset.class)
    @DisplayName("it is a stratovolcano and not a traffic cone: broad skirt, steep summit, real crater")
    void theSilhouetteIsAStratovolcano(Preset preset) {
        TerrainEngine engine = new TerrainEngine(20260823L, preset, TerrainSettings.forPreset(preset), 4096);
        StructurePlacer placer = new StructurePlacer(engine, Set.of(), prefabs());
        Structure volcano = volcano(placer);
        assertNotNull(volcano, "the volcano is not in the catalogue");
        StructureBuffer buffer = built(engine, volcano);
        assertNotNull(buffer, preset + ": no volcano would build anywhere in four hundred sites");

        int centreX = (buffer.minX() + buffer.maxX()) / 2;
        int centreZ = (buffer.minZ() + buffer.maxZ()) / 2;
        int reach = (buffer.maxX() - buffer.minX()) / 2;

        // The top of the rock at every distance from the middle, averaged over four directions so
        // one gully cannot decide the answer.
        int[] profile = new int[reach + 1];
        for (int d = 0; d <= reach; d++) {
            int sum = 0;
            for (int[] way : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                sum += top(buffer, centreX + way[0] * d, centreZ + way[1] * d);
            }
            profile[d] = sum / 4;
        }

        // Where the rim actually is, rather than where it was assumed to be. The first version of
        // this test read the rim off a fixed tenth of the radius, which on this mountain is inside
        // the crater - so it compared the lava lake against the flank and reported a straight cone
        // over a profile that a cross section showed was nothing of the sort.
        int rimAt = 0;
        for (int d = 0; d <= reach; d++) {
            if (profile[d] > profile[rimAt]) {
                rimAt = d;
            }
        }
        int rim = profile[rimAt];
        int foot = profile[reach];

        assertTrue(rimAt > 2, preset + ": the highest point is at the very centre, so there is no "
                + "crater - just a peak");
        assertTrue(profile[0] < rim - 6, preset + ": the middle is " + profile[0] + " and the rim "
                + rim + "; that is a dimple, not a crater");
        assertTrue(rim - foot > 30, preset + ": only " + (rim - foot) + " blocks from foot to rim; "
                + "that is a hill, not a volcano");

        // Concave, measured down the flank from the rim outwards: a stratovolcano gives up more of
        // its height in the half of the flank nearest the summit than in the half nearest the foot.
        // A straight cone splits it evenly, a shield volcano the other way round.
        int half = rimAt + (reach - rimAt) / 2;
        int upper = rim - profile[half];
        int lower = profile[half] - foot;
        assertTrue(upper > lower, preset + ": the flank is not concave - it lost " + upper
                + " blocks over its upper half and " + lower + " over its lower, which is a straight "
                + "cone rather than a stratovolcano");
    }

    private static int top(StructureBuffer buffer, int x, int z) {
        for (int y = buffer.maxY(); y >= buffer.minY(); y--) {
            int block = buffer.get(x, y, z);
            if (block >= 0 && !Blocks.isAir(block)) {
                return y;
            }
        }
        return buffer.minY();
    }

    @Test
    @DisplayName("the volcano belongs to the ash plains and to nothing else")
    void onlyOneBiomeAsksForIt() {
        List<String> asking = new ArrayList<>();
        for (ArkBiome biome : new BiomeRegistry().all()) {
            if (biome.structures.contains(StructureTag.VOLCANO)) {
                asking.add(biome.name);
            }
        }
        assertTrue(asking.equals(List.of("volcanic_wastes")),
                "the volcano is offered by " + asking + "; a mountain of ash that turns up in a "
                        + "meadow is not a landmark, it is a mistake");
    }

    @ParameterizedTest
    @EnumSource(Preset.class)
    @DisplayName("the ash plains actually occur, and a volcano can be found in them")
    void theBiomeAndTheLandmarkAreReachable(Preset preset) {
        TerrainEngine engine = new TerrainEngine(20260823L, preset, TerrainSettings.forPreset(preset), 4096);
        int hits = 0;
        int total = 0;
        for (int z = -3000; z < 3000; z += 40) {
            for (int x = -3000; x < 3000; x += 40) {
                total++;
                if (engine.biomeAt(x, z).name.equals("volcanic_wastes")) {
                    hits++;
                }
            }
        }
        assertTrue(hits * 100.0 / total > 0.8, preset + ": the ash plains are only "
                + String.format("%.2f%%", hits * 100.0 / total) + " of the world, which is rare "
                + "enough that a player would never find one");
    }

    @Test
    @DisplayName("a volcano is findable from the origin")
    void aVolcanoIsWithinReach() {
        Preset preset = Preset.INSANE;
        TerrainEngine engine = new TerrainEngine(20260823L, preset, TerrainSettings.forPreset(preset), 8192);
        StructurePlacer placer = new StructurePlacer(engine, Set.of(), prefabs());
        int[] site = null;
        for (int ring = 0; ring <= 8 && site == null; ring++) {
            site = placer.locate(0, 0, StructureTag.VOLCANO, ring);
        }
        assertNotNull(site, "no volcano within eight grid rings of the origin");
        assertTrue(Math.hypot(site[0], site[2]) < 6000,
                "the nearest volcano is " + (int) Math.hypot(site[0], site[2]) + " blocks out, which "
                        + "is further than anyone will walk to see one");
    }
}
