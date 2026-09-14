package com.arkcronist.gen.core;

import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.prefab.PrefabRegistry;
import com.arkcronist.gen.core.structure.Structure;
import com.arkcronist.gen.core.structure.StructureBuffer;
import com.arkcronist.gen.core.structure.StructureContext;
import com.arkcronist.gen.core.structure.StructurePlacer;
import com.arkcronist.gen.core.terrain.Preset;
import com.arkcronist.gen.core.terrain.TerrainEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * No structure may write further from its origin than {@link Structure#radius()} says it does.
 *
 * <p>This is not tidiness. A chunk only consults the grid cells within the catalogue's widest radius
 * of itself, so anything a structure writes beyond its declared reach lands in cells that distant
 * chunks never ask about - and the structure comes out cut off at exactly that distance. It does not
 * read as a number being too small; it reads as a ship with its bow missing.</p>
 *
 * <p>Found by measuring rather than by reading, and the numbers are worth keeping. A fleet declared
 * 43 and reached <b>104</b>, against a catalogue whose widest structure was 99 - so the outer ships
 * were being cut in the world as it shipped. A village declared 72 and reached 95, a fossil 10 and
 * reached 13, a mineshaft 60 and reached 61; those three were hidden only because the ancient city
 * happened to be installed and dragged the catalogue's maximum up to 99. Remove that one schematic
 * and three more structures start losing their edges.</p>
 *
 * <p>Each of them had the same shape of cause: a number in {@code build()} with no relationship to
 * the number in {@code radius()}.</p>
 */
class StructureReachTest {

    /** How far a structure actually wrote, and how far it said it would. */
    private record Reach(int declared, int actual, int samples) {
    }

    @Test
    @DisplayName("every structure stays inside the radius it declares")
    void nothingWritesBeyondItsDeclaredRadius() {
        PrefabRegistry prefabs = PrefabRegistry.fromDirectory(
                Path.of("src", "main", "resources", "prefabs"), message -> {
                });

        Map<String, Reach> reach = new LinkedHashMap<>();
        for (Preset preset : Preset.values()) {
            TerrainEngine engine = new TerrainEngine(1234567L, preset);
            StructurePlacer placer = new StructurePlacer(engine, Set.of(), prefabs);
            for (Structure structure : placer.structures()) {
                for (int attempt = 0; attempt < 50; attempt++) {
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
                    // Settled first, because that pass can move a mob and the markers count towards
                    // the buffer's bounds.
                    buffer.settleSpawns();
                    int furthest = Math.max(
                            Math.max(x - buffer.minX(), buffer.maxX() - x),
                            Math.max(z - buffer.minZ(), buffer.maxZ() - z));
                    Reach seen = reach.get(structure.id());
                    reach.put(structure.id(), new Reach(structure.radius(),
                            seen == null ? furthest : Math.max(seen.actual(), furthest),
                            seen == null ? 1 : seen.samples() + 1));
                }
            }
        }

        List<String> over = new ArrayList<>();
        int built = 0;
        for (Map.Entry<String, Reach> entry : reach.entrySet()) {
            Reach seen = entry.getValue();
            built++;
            if (seen.actual() > seen.declared()) {
                over.add(entry.getKey() + " declares " + seen.declared() + " and reaches "
                        + seen.actual() + " (" + (seen.actual() - seen.declared()) + " over, "
                        + seen.samples() + " samples)");
            }
        }

        org.junit.jupiter.api.Assertions.assertTrue(built > 25,
                "only " + built + " structures built at all, so this test measured almost nothing");
        org.junit.jupiter.api.Assertions.assertTrue(over.isEmpty(),
                "these structures write outside the radius they declare, so distant chunks never "
                        + "consult their cell and they come out cut off:\n  " + String.join("\n  ", over));
    }

    @Test
    @DisplayName("no structure declares a radius so large that it widens every chunk's search")
    void theDeclaredRadiiStayReasonable() {
        // The other half of the contract. The catalogue's widest radius decides how many grid cells
        // every chunk consults, on every grid - so a structure that answers an overrun by declaring
        // some enormous number makes all generation slower for everyone, and would let this file's
        // first test pass while making things worse.
        PrefabRegistry prefabs = PrefabRegistry.fromDirectory(
                Path.of("src", "main", "resources", "prefabs"), message -> {
                });
        StructurePlacer placer = new StructurePlacer(new TerrainEngine(1L, Preset.INSANE),
                Set.of(), prefabs);
        List<String> huge = new ArrayList<>();
        for (Structure structure : placer.structures()) {
            if (structure.radius() > 128) {
                huge.add(structure.id() + " = " + structure.radius());
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(huge.isEmpty(),
                "these declare a radius past 128 blocks, which widens the cell search for every "
                        + "chunk in the world: " + huge);
    }
}
