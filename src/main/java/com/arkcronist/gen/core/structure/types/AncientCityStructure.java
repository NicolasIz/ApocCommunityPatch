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
 *       dealt with is the edge of the file, where roughly half of every wall plane is open hall cut
 *       off flat against untouched stone. Those openings are read out of the file and extruded
 *       into the rock, so the hall carries on at the height it was already open at.</li>
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
     * How far the chamber may run out past the schematic's own edge, at its widest.
     *
     * <p>A maximum, not a margin. Two earlier attempts made the same mistake in different sizes: a
     * uniform 12 block skirt, then noise driven lobes that still picked their own height. Both left
     * a rectangle, because a border of any width drawn round a rectangle traces that rectangle. What
     * the reach does here is decide how far one already-open stretch of wall carries on, and it is
     * zero over the roughly half of the perimeter the file leaves solid.</p>
     */
    private static final int APRON = 30;

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
        if (context.lowestHeight(city.radius()) <= roof + ROOF_ROCK) {
            return false;
        }
        // And rock over the apron too. It reaches further out than the file does, though it stops
        // five short of the roof, so it needs the same cover measured over the wider ring.
        return context.lowestHeight(city.radius() + APRON) > roof + ROOF_ROCK - 5;
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
     * Carries the file's own cavern out past the edge of the file.
     *
     * <p>The schematic is a cuboid cut out of a bigger cave, so every one of its four walls is half
     * open air: measured on this file, 35% to 64% of each perimeter plane is a hall that simply
     * stops against untouched stone. That flat plane is the box. Nothing else is - the file's own
     * floor course is solid and its ceiling is rock, so top and bottom were never the problem.</p>
     *
     * <p>The first attempt at this opened cavities <em>near</em> the wall at a height noise picked
     * on its own. That is why it read as a fringe stapled to the outside of a box rather than as
     * the room going on: it was a separate corridor that happened to run alongside the building,
     * and it met the wall wherever it liked. What is done here instead is to read the file - for
     * each column just outside it, find the wall column it continues and the vertical opening the
     * file actually has there - and extrude that opening outwards, closing it gradually. Where the
     * file's edge is open the hall keeps going at exactly the height it was already open at, so
     * there is no plane where one thing becomes another. Where the file's edge is rock, nothing is
     * carved and it stays rock. The straight line survives only where it is buried in stone and
     * nobody can see it.</p>
     */
    private void carveApron(BufferWriter writer, Prefab prefab, int originX, int baseY, int originZ,
                            int rotation) {
        int width = prefab.rotatedWidth(rotation);
        int length = prefab.rotatedLength(rotation);
        int minX = originX - prefab.rotatedAnchorX(rotation);
        int minZ = originZ - prefab.rotatedAnchorZ(rotation);
        int maxX = minX + width - 1;
        int maxZ = minZ + length - 1;

        // The file's own floor course and ceiling stay sealed: the apron may only continue what is
        // open between them, so it can never open the hall to the sky or drop it onto bedrock.
        int fromLayer = 2;
        int toLayer = prefab.height - 5;
        if (toLayer <= fromLayer) {
            return;
        }

        for (int wx = minX - APRON; wx <= maxX + APRON; wx++) {
            for (int wz = minZ - APRON; wz <= maxZ + APRON; wz++) {
                if (wx >= minX && wx <= maxX && wz >= minZ && wz <= maxZ) {
                    continue;
                }

                // How far the rock opens along this stretch of wall. Long wavelength on purpose, so
                // a whole run of the perimeter shares one value and the hall leaves in a few broad
                // bays instead of an even border. Squaring it keeps most of the perimeter close to
                // the file and lets a few places run right out.
                double lobe = apron.unsigned2(wx * 0.013 + 133.0, wz * 0.013 - 77.0);
                double reach = APRON * lobe * lobe * 1.9;
                if (reach < 1.0) {
                    continue;
                }
                int nearX = Math.min(Math.max(wx, minX), maxX);
                int nearZ = Math.min(Math.max(wz, minZ), maxZ);
                double beyond = Math.hypot(wx - nearX, wz - nearZ);
                if (beyond > reach) {
                    continue;
                }

                // Which wall column this one continues. Dragged sideways as it goes out, so a bay
                // wanders off at an angle rather than extruding straight out from the wall like a
                // row of teeth.
                double sway = apron.unsigned2(wx * 0.045 - 219.0, wz * 0.045 + 88.0) * 2.0 - 1.0;
                int drag = (int) (sway * beyond * 0.9);
                int edgeX = nearX;
                int edgeZ = nearZ;
                if (wx < minX || wx > maxX) {
                    edgeZ = Math.min(Math.max(nearZ + drag, minZ), maxZ);
                } else {
                    edgeX = Math.min(Math.max(nearX + drag, minX), maxX);
                }

                // The opening the file has there: its longest unbroken run of air. Longest run and
                // not simply lowest-to-highest, because a column can be open along the floor and
                // open again under the ceiling with a building in between, and boring the whole
                // height of that would put a shaft outside where the file has a room.
                int bestLow = -1;
                int bestHigh = -2;
                int runLow = -1;
                for (int layer = fromLayer; layer <= toLayer; layer++) {
                    if (prefab.airAt(rotation, edgeX - minX, layer, edgeZ - minZ)) {
                        if (runLow < 0) {
                            runLow = layer;
                        }
                        if (layer - runLow > bestHigh - bestLow) {
                            bestLow = runLow;
                            bestHigh = layer;
                        }
                    } else {
                        runLow = -1;
                    }
                }
                if (bestLow < 0) {
                    // Solid wall here. It stays solid: there is no visible face to break up.
                    continue;
                }

                double out = beyond / reach;
                double keep = Math.pow(1.0 - out, 0.55);

                // Ragged tips, and ribs of rock left standing in the bays.
                double grain = apron.unsigned2(wx * 0.11 + 5.0, wz * 0.11 - 3.0);
                if (grain > 0.25 + keep * 0.9) {
                    continue;
                }

                double middle = (bestLow + bestHigh) * 0.5;
                double half = (bestHigh - bestLow) * 0.5 * keep;
                // Let the passage climb and dip on its way out, so the floor outside is not a
                // continuation of the file's floor plane either.
                middle += (apron.unsigned2(wx * 0.06 + 411.0, wz * 0.06 - 707.0) * 2.0 - 1.0)
                        * beyond * 0.35;
                int from = (int) Math.max(fromLayer, Math.ceil(middle - half));
                int to = (int) Math.min(toLayer, Math.floor(middle + half));
                for (int layer = from; layer <= to; layer++) {
                    writer.set(wx, baseY + layer, wz, Blocks.CAVE_AIR);
                }
            }
        }
    }
}
