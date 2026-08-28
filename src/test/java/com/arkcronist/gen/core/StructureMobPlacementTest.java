package com.arkcronist.gen.core;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.prefab.PrefabRegistry;
import com.arkcronist.gen.core.structure.MobSpawn;
import com.arkcronist.gen.core.structure.Structure;
import com.arkcronist.gen.core.structure.StructureBuffer;
import com.arkcronist.gen.core.structure.StructureContext;
import com.arkcronist.gen.core.structure.StructurePlacer;
import com.arkcronist.gen.core.terrain.Preset;
import com.arkcronist.gen.core.terrain.TerrainEngine;
import com.arkcronist.gen.core.terrain.TerrainSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where structures put the things that live in them.
 *
 * <p>This is the guard for a bug reported from a live server in the plainest possible terms: the
 * mobs come out underground and die. They did, and it was not one structure's mistake. A structure
 * chooses its garrison while it is being built, and then carries on building - it lays furniture,
 * cuts a stair, hangs a lantern, writes a wall. Several of them also scatter guards sideways around
 * an anchor while keeping the anchor's height, which on any slope asks for a mob inside the hill.
 * Measured across the whole catalogue at 120 sites each: with the settling pass below turned off,
 * <b>2928 of 8052</b> queued mobs are standing inside a block; with it, <b>44</b>.</p>
 *
 * <p>The fix is one pass over the finished buffer, in {@link StructureBuffer#settleSpawns()}, which
 * moves each mob to a floor the structure really laid. What it deliberately will not do is guess
 * about terrain: where the buffer holds nothing, the structure has no opinion about that column, and
 * the position is left for the server side - {@code SpawnSpot} - to check against the real world,
 * which is the only place that knows what is actually there.</p>
 */
class StructureMobPlacementTest {

    private static PrefabRegistry prefabs() {
        return PrefabRegistry.fromDirectory(Path.of("src", "main", "resources", "prefabs"), message -> {
        });
    }

    /** Schematic-backed: everything built by stamping a file rather than by laying blocks. */
    private static boolean fromASchematic(Structure structure) {
        return structure.id().startsWith("prefab_") || structure.id().equals("grove")
                || structure.id().equals("ancient_city");
    }

    /**
     * A mob's position, checked against the buffer the structure filled.
     *
     * <p>How much room it needs and whether it needs a floor come from the request itself, so this
     * holds a wither skeleton to the three blocks it is tall and lets a guardian float in water.</p>
     *
     * @return null when the spot is good, or what is wrong with it
     */
    private static String faultAt(StructureBuffer buffer, MobSpawn spawn) {
        boolean floats = !spawn.needsFloor();
        for (int y = spawn.y(); y < spawn.y() + spawn.height(); y++) {
            int block = buffer.get(spawn.x(), y, spawn.z());
            // Below zero is not a fault: the structure wrote nothing there, so there is nothing of
            // its own for the mob to be stuck inside.
            if (block < 0 || Blocks.isAir(block) || (floats && Blocks.isLiquid(block))) {
                continue;
            }
            return (y > spawn.y() + 1 ? "no room for its head: " : "walled into ")
                    + Blocks.REGISTRY.key(block) + " at y=" + y;
        }
        if (floats) {
            return null;
        }
        int floor = buffer.get(spawn.x(), spawn.y() - 1, spawn.z());
        if (floor < 0) {
            // Terrain, which only the live world can judge. Left to SpawnSpot on purpose.
            return null;
        }
        if (Blocks.isAir(floor)) {
            return "air under its feet: it is hanging in the middle of the room";
        }
        return Blocks.isLiquid(floor) ? "standing on " + Blocks.REGISTRY.key(floor) : null;
    }

    /** Builds one structure at a spread of sites and returns the buffers that came out. */
    private static List<StructureBuffer> samples(TerrainEngine engine, Structure structure, int sites) {
        List<StructureBuffer> built = new ArrayList<>();
        for (int attempt = 0; attempt < sites; attempt++) {
            int x = attempt * 149 - 6000;
            int z = attempt * -223 + 4000;
            StructureContext context = new StructureContext(engine, x, z,
                    new FastRandom(0xA11CE ^ (attempt * 2654435761L)));
            if (!structure.canPlace(context)) {
                continue;
            }
            StructureBuffer buffer = new StructureBuffer();
            structure.build(context, buffer);
            if (buffer.blockCount() < 20) {
                continue;
            }
            // Exactly what the placer does with a finished structure, and the thing under test.
            buffer.settleSpawns();
            built.add(buffer);
        }
        return built;
    }

    @Test
    @DisplayName("across the whole catalogue, almost no mob is left standing inside a block")
    void theCatalogueDoesNotBuryItsMobs() {
        TerrainEngine engine = new TerrainEngine(1234567L, Preset.INSANE);
        StructurePlacer placer = new StructurePlacer(engine, Set.of(), prefabs());

        int total = 0;
        List<String> faults = new ArrayList<>();
        for (Structure structure : placer.structures()) {
            for (StructureBuffer buffer : samples(engine, structure, 120)) {
                for (MobSpawn spawn : buffer.spawns()) {
                    total++;
                    String fault = faultAt(buffer, spawn);
                    if (fault != null) {
                        faults.add(structure.id() + ": its " + spawn.entityType() + " has " + fault);
                    }
                }
            }
        }

        assertTrue(total > 4000, "only " + total + " mobs were queued in the whole sweep, so this "
                + "test is not measuring the catalogue");
        // Not zero, and the number is the point. What is left is a handful of mobs standing where
        // their structure later hung a lantern or put a barrel, in rooms with nowhere else to go -
        // the buffer pass has no better answer for those and SpawnSpot decides them against the real
        // world. One in two hundred is that residue; one in twenty would mean this pass had stopped
        // working.
        double share = (double) faults.size() / total;
        assertTrue(share < 0.01, String.format(
                "%d of %d queued mobs (%.1f%%) are inside a block after settling; the first few: %s",
                faults.size(), total, share * 100.0, faults.subList(0, Math.min(8, faults.size()))));
    }

    @Test
    @DisplayName("the schematic structures put every one of their mobs on a floor of their own")
    void schematicGarrisonsStandOnTheirOwnFloors() {
        // Held to zero rather than to a share, because these are the ones the request was about and
        // because they can be: a stamped file knows exactly where its own floors are.
        TerrainEngine engine = new TerrainEngine(1234567L, Preset.INSANE);
        StructurePlacer placer = new StructurePlacer(engine, Set.of(), prefabs());

        List<String> faults = new ArrayList<>();
        int checked = 0;
        int withMobs = 0;
        for (Structure structure : placer.structures()) {
            if (!fromASchematic(structure) || structure.id().equals("ancient_city")) {
                continue;
            }
            for (StructureBuffer buffer : samples(engine, structure, 220)) {
                checked++;
                if (!buffer.spawns().isEmpty()) {
                    withMobs++;
                }
                for (MobSpawn spawn : buffer.spawns()) {
                    String fault = faultAt(buffer, spawn);
                    if (fault != null && faults.size() < 12) {
                        faults.add(structure.id() + ": its " + spawn.entityType() + " at "
                                + spawn.x() + "," + spawn.y() + "," + spawn.z() + " has " + fault);
                    }
                }
            }
        }

        assertTrue(checked > 20, "only " + checked + " schematic structures built at all, so this "
                + "test proved almost nothing");
        assertTrue(withMobs > 0, "no schematic structure queued a single mob, so nothing was checked");
        assertTrue(faults.isEmpty(), faults.size() + " garrison mobs are placed somewhere they cannot "
                + "stand:\n  " + String.join("\n  ", faults));
    }

    @Test
    @DisplayName("the ancient city is garrisoned, and on its own floors")
    void theAncientCityHasSomeoneInIt() {
        // Its own test because it cannot be built at an arbitrary spot: it only places under the ice
        // and only well below the surface, so the site has to be found rather than guessed.
        Preset preset = Preset.INSANE;
        TerrainEngine engine = new TerrainEngine(20260823L, preset, TerrainSettings.forPreset(preset), 8192);
        StructurePlacer placer = new StructurePlacer(engine, Set.of(), prefabs());

        int[] site = null;
        for (int ring = 0; ring <= StructurePlacer.searchRings(StructureTag.ANCIENT_CITY) && site == null; ring++) {
            site = placer.locate(0, 0, StructureTag.ANCIENT_CITY, ring);
        }
        assertNotNull(site, "no ancient city anywhere near the origin to check");

        Structure city = placer.structures().stream()
                .filter(structure -> structure.id().equals("ancient_city"))
                .findFirst().orElse(null);
        assertNotNull(city, "the ancient city is not in the catalogue");

        StructureContext context = new StructureContext(engine, site[0], site[2],
                new FastRandom(engine.seed() ^ 0x0AC17_9EEDL));
        assertTrue(city.canPlace(context), "the located city will not build at its own site");
        StructureBuffer buffer = new StructureBuffer();
        city.build(context, buffer);
        buffer.settleSpawns();

        // It used to have spawners and nothing else, which meant the largest structure in the world
        // was empty when you walked in and stayed that way until a spawner happened to tick.
        assertTrue(buffer.spawns().size() >= 8,
                "the ancient city queues only " + buffer.spawns().size() + " mobs");

        List<String> faults = new ArrayList<>();
        for (MobSpawn spawn : buffer.spawns()) {
            String fault = faultAt(buffer, spawn);
            if (fault != null) {
                faults.add(spawn.entityType() + " at " + spawn.x() + "," + spawn.y() + "," + spawn.z()
                        + " has " + fault);
            }
        }
        assertTrue(faults.isEmpty(), "the city's garrison cannot stand where it was put:\n  "
                + String.join("\n  ", faults));

        // And they are types the hostile-mob table knows, so a server running the custom packs meets
        // those down there rather than plain vanilla skeletons.
        for (MobSpawn spawn : buffer.spawns()) {
            assertTrue(spawn.entityType().equals("SKELETON") || spawn.entityType().equals("WITHER_SKELETON"),
                    "the city spawns a " + spawn.entityType() + ", which the hostile-mob table does "
                            + "not cover, so it would stay vanilla");
        }
    }
}
