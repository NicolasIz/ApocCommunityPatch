package com.arkcronist.gen.core.structure.types;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.math.Hashing;
import com.arkcronist.gen.core.noise.FractalNoise;
import com.arkcronist.gen.core.prefab.Prefab;
import com.arkcronist.gen.core.prefab.PrefabRegistry;
import com.arkcronist.gen.core.structure.BufferWriter;
import com.arkcronist.gen.core.structure.LootMarker;
import com.arkcronist.gen.core.structure.SpawnerMarker;
import com.arkcronist.gen.core.structure.Structure;
import com.arkcronist.gen.core.structure.StructureBuffer;
import com.arkcronist.gen.core.structure.StructureContext;

/**
 * The ancient city, built from a schematic rather than from the server's own generator.
 *
 * <p>This is the one vanilla structure taken over here. The server can no longer place its own,
 * because it only ever does so in the {@code deep_dark} biome and the biome provider stops
 * reporting that key; every other vanilla structure is left exactly as it was.</p>
 *
 * <p>The order is seed, place, turn, chamber, city, blend, loot:</p>
 * <ol>
 *   <li><b>Seed and place.</b> One candidate per grid cell, offset inside the cell by the world
 *       seed, so where a city stands is fixed for a seed and unrelated to the order chunks are
 *       generated in.</li>
 *   <li><b>Turn.</b> One of four rotations, and independently a mirror, so two cities on the same
 *       world are not the same building twice.</li>
 *   <li><b>Chamber.</b> The schematic carries its own cavern - it was cut with the rock around it,
 *       58% of the file is air - so the room does not have to be invented. What does have to be
 *       dealt with is the edge of the file, which is a flat plane where that cavern is cut off. An
 *       apron of noise driven cavities is opened just outside it, so the hall frays into the rock
 *       instead of ending against a wall.</li>
 *   <li><b>City.</b> Written whole, air included, over its entire footprint.</li>
 *   <li><b>Blend and protect.</b> Because the air is written too, the footprint is authoritative:
 *       whatever the cave carver did there is overwritten. A cave cannot cut the city, cannot leave
 *       it hanging, and cannot run through it - not because the carver was told to avoid it, but
 *       because there is nothing left of the carver's work inside the box.</li>
 *   <li><b>Loot.</b> Its chests are registered as containers and filled from the vanilla ancient
 *       city table.</li>
 * </ol>
 */
public final class AncientCityStructure implements Structure {

    /** The folder the schematic lives in. Not a placement rule - this structure places it. */
    public static final String CATEGORY = "ancient_city";

    private final PrefabRegistry prefabs;
    private final Prefab city;
    private final FractalNoise apron;

    public AncientCityStructure(PrefabRegistry prefabs, long seed) {
        this.prefabs = prefabs;
        this.city = prefabs.has(CATEGORY) && !prefabs.category(CATEGORY).isEmpty()
                ? prefabs.category(CATEGORY).get(0)
                : null;
        this.apron = FractalNoise.fbm(seed, "ancientCityApron", 3, 0.075);
    }

    /** Whether a schematic was supplied at all. Without one this structure does not register. */
    public boolean available() {
        return city != null;
    }

    @Override
    public Placement placement() {
        return Placement.DEEP_LANDMARK;
    }

    @Override
    public String id() {
        return "ancient_city";
    }

    @Override
    public StructureTag tag() {
        return StructureTag.ANCIENT_CITY;
    }

    @Override
    public int radius() {
        // Half the footprint plus the apron, so the placer searches a wide enough neighbourhood of
        // chunks to find this city from any chunk it reaches into.
        return city == null ? 56 : city.radius() + APRON;
    }

    @Override
    public double weight() {
        return 1.0;
    }

    /** Rock that must stand over every part of the roof before a site is accepted. */
    private static final int ROOF_ROCK = 12;

    /**
     * How far the chamber may fray out past the schematic's own edge, at its widest.
     *
     * <p>This is a maximum, not a margin: how far the rock actually opens at any point around the
     * perimeter is decided by noise, and most of it opens nowhere near this far. A uniform skirt was
     * the first attempt and it did not work - a rectangle with a 12 block border is still a
     * rectangle. What stops it reading as a box is the reach varying wildly from one side to the
     * next, so the hall runs out into the rock in lobes.</p>
     */
    private static final int APRON = 22;

    @Override
    public boolean canPlace(StructureContext context) {
        if (city == null || !context.engine().settings().ancientCity) {
            return false;
        }
        int minY = context.engine().settings().minY;
        int base = baseY(context);
        if (base < minY + 4) {
            return false;
        }
        // Rock over the WHOLE footprint, not just over the middle. The city is 172 blocks across,
        // and the ground it needs above it is the ground above its thinnest corner: checking only
        // the centre column let a site sit half under a hill and half under an ocean trench, where
        // the sea floor cut straight through the roof and the water came in.
        int roof = base + city.height;
        return context.lowestHeight(city.radius()) > roof + ROOF_ROCK;
    }

    /**
     * The floor the city stands on.
     *
     * <p>Deliberately a function of the world seed and the cell alone, not of the terrain: a fixed
     * deep band means the biome provider can answer "is this inside a city" without touching the
     * heightmap, and it keeps the city clear of the surface no matter what is overhead.</p>
     */
    private int baseY(StructureContext context) {
        int minY = context.engine().settings().minY;
        long roll = Hashing.hash3(context.engine().seed() ^ 0xC17F1_00A5L,
                context.originX >> 6, 0, context.originZ >> 6);
        // Twelve clear of the world floor, not six. The lava table sits at minY+6, and the file's
        // own bottom course is rock but not solid rock everywhere - at six the two met and the city
        // was standing on lava in places. This leaves a floor of stone underneath it.
        return minY + 12 + (int) ((roll >>> 17) % 7);
    }

    @Override
    public int locateY(StructureContext context) {
        // Stand a player a few blocks above the floor of the hall, not on the ground overhead.
        return baseY(context) + 3;
    }

    @Override
    public void build(StructureContext context, StructureBuffer buffer) {
        if (city == null) {
            return;
        }
        FastRandom random = context.random;
        int x = context.originX;
        int z = context.originZ;
        int base = baseY(context);

        // Four turns, chosen from the site seed. Not a mirror: mirroring a schematic means
        // mirroring every block state in it - which way a stair faces, which way a door hangs - and
        // the rotator here does turns only. Four deterministic orientations, honestly four.
        int rotation = random.nextInt(4);

        BufferWriter writer = new BufferWriter(buffer, context.engine().settings().minY, context.maxY());

        // The apron first: it only ever opens rock that the city is about to overwrite anyway where
        // the two overlap, so order costs nothing and the city always wins.
        carveApron(writer, city, x, base, z, rotation);

        // Air included. This is what makes the footprint authoritative and the city safe from
        // anything the carver left behind.
        city.blit(writer, x, base, z, rotation, Prefab.BlitOptions.authoritative(Blocks.CAVE_AIR));

        int tier = 3;
        city.forEachContainer(x, base, z, rotation, (cx, cy, cz) ->
                buffer.addLoot(new LootMarker(cx, cy, cz, tier, "ancient_city")));
        city.forEachSpawner(x, base, z, rotation, (cx, cy, cz) ->
                buffer.addSpawner(new SpawnerMarker(cx, cy, cz, "SKELETON")));
    }

    /**
     * Opens an irregular skirt of cavities around the schematic's outer wall.
     *
     * <p>The file is a cuboid, so wherever its own cavern reaches the edge it stops against a flat
     * plane of untouched stone - a rectangular room in the middle of the deep. This eats into that
     * plane with noise, so the hall runs out into the rock in fingers and side pockets and the
     * boundary stops reading as a box.</p>
     */
    private void carveApron(BufferWriter writer, Prefab prefab, int originX, int baseY, int originZ,
                            int rotation) {
        int width = prefab.rotatedWidth(rotation);
        int length = prefab.rotatedLength(rotation);
        int minX = originX - prefab.rotatedAnchorX(rotation);
        int minZ = originZ - prefab.rotatedAnchorZ(rotation);
        int maxX = minX + width - 1;
        int maxZ = minZ + length - 1;

        int fromX = minX - APRON;
        int toX = maxX + APRON;
        int fromZ = minZ - APRON;
        int toZ = maxZ + APRON;
        // Only the middle of the file's height: the schematic's own floor and ceiling stay sealed,
        // so the apron cannot open the hall to the sky or drop it onto bedrock.
        int fromY = baseY + 2;
        int toY = baseY + prefab.height - 6;

        for (int wx = fromX; wx <= toX; wx++) {
            for (int wz = fromZ; wz <= toZ; wz++) {
                boolean inside = wx >= minX && wx <= maxX && wz >= minZ && wz <= maxZ;
                if (inside) {
                    continue;
                }
                int beyond = Math.max(Math.max(minX - wx, wx - maxX), Math.max(minZ - wz, wz - maxZ));

                // How far the rock opens HERE. Long wavelength on purpose - a whole stretch of the
                // perimeter shares a value - so the boundary becomes a few broad bays and a few
                // places where the wall comes right up to the file's edge, instead of an even
                // fringe that traces the rectangle it was cut from.
                double lobe = apron.unsigned2(wx * 0.016 + 133.0, wz * 0.016 - 77.0);
                double localReach = APRON * (lobe * lobe * 1.45);
                if (beyond > localReach) {
                    continue;
                }
                double into = 1.0 - beyond / Math.max(1.0, localReach);

                // Fine noise on top, so the edge of each bay is ragged rather than a smooth curve.
                double grain = apron.unsigned2(wx * 0.14, wz * 0.14);
                if (grain > 0.35 + into * 0.75) {
                    continue;
                }

                // Height: nearly the whole hall where a bay is deep, closing to a low passage as it
                // runs out. This is the other half of what a uniform slot got wrong - a band cut at
                // one level reads as a corridor round a building, not as the room continuing.
                double drift = apron.unsigned2(wx * 0.05 + 811.0, wz * 0.05 - 407.0);
                int span = toY - fromY;
                int half = (int) (span * (0.12 + into * 0.42));
                int mid = fromY + (int) (span * (0.25 + drift * 0.45));
                int lo = Math.max(fromY, mid - half);
                int hi = Math.min(toY, mid + half);
                for (int wy = lo; wy <= hi; wy++) {
                    writer.set(wx, wy, wz, Blocks.CAVE_AIR);
                }
            }
        }
    }
}
