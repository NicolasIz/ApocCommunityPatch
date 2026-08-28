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
import com.arkcronist.gen.core.structure.MobSpawn;
import com.arkcronist.gen.core.structure.PrefabFurnisher;
import com.arkcronist.gen.core.structure.SpawnerMarker;
import com.arkcronist.gen.core.structure.Structure;
import com.arkcronist.gen.core.structure.StructureBuffer;
import com.arkcronist.gen.core.structure.StructureContext;

import java.util.List;

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

    /** How far the natural chamber frays out past the schematic's own edge. */
    private static final int APRON = 12;

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
        // Never under the sea, and never near it. Which biome is overhead is already settled by the
        // placer - only the ice spikes list this family - but a coast can run through the middle of
        // any biome, and the city is 172 blocks across. So the whole footprint and a wide margin
        // round it have to be dry land: not one column of open water anywhere over the roof.
        if (nearTheSea(context, city.radius() + SHORE_CLEARANCE)
                || waterOverTheRoof(context, city.radius())) {
            return false;
        }
        // Rock over the WHOLE footprint, not just over the middle. The city is 172 blocks across,
        // and the ground it needs above it is the ground above its thinnest corner: checking only
        // the centre column let a site sit half under a hill and half under an ocean trench, where
        // the sea floor cut straight through the roof and the water came in.
        int roof = base + city.height;
        return context.lowestHeight(city.radius()) > roof + ROOF_ROCK;
    }

    /** How far past its own footprint a city insists on dry land. */
    private static final int SHORE_CLEARANCE = 48;

    /**
     * Whether the sea itself is anywhere near, judged by biome rather than by height.
     *
     * <p>This is the half of the rule that says "never in the ocean and never near it", and asking
     * the biome is the exact way to ask it: a frozen pond on top of an ice plateau is water, and is
     * not the sea. Biomes are large enough that a stride of sixteen cannot step over one.</p>
     */
    private boolean nearTheSea(StructureContext context, int reach) {
        for (int dx = -reach; dx <= reach; dx += 16) {
            for (int dz = -reach; dz <= reach; dz += 16) {
                if (context.engine().biomeAt(context.originX + dx, context.originZ + dz).oceanic()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether any open water stands over the footprint itself.
     *
     * <p>The other half, and the one the complaint was actually about: water above the city. Held
     * to the footprint rather than the whole margin, because what matters is what is over the roof,
     * and stepped by eight because an inlet or a river is narrower than the twenty-two that
     * {@code lowestHeight} moves in - it went straight through those gaps and put a city under the
     * sea at -11157,-1366 while reporting the ground never dropped below sea level.</p>
     *
     * <p>Water here is not the same as water getting in: {@code ROOF_ROCK} already keeps twelve
     * blocks of stone over the highest part of the city. This is the belt to that pair of braces.</p>
     */
    private boolean waterOverTheRoof(StructureContext context, int reach) {
        for (int dx = -reach; dx <= reach; dx += 8) {
            for (int dz = -reach; dz <= reach; dz += 8) {
                if (context.height(context.originX + dx, context.originZ + dz) < context.seaLevel()) {
                    return true;
                }
            }
        }
        return false;
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
        garrison(context, buffer, random, x, base, z, rotation);
    }

    /**
     * Who is down there when you arrive.
     *
     * <p>Until now the city had spawners and nothing else, which meant walking into the largest
     * structure in the world and finding it empty until a spawner happened to tick. These are placed
     * with the city, spread through it, and on this generator they are the same skeletons the rest of
     * the world uses - the table in the config decides what those actually are, so a server running
     * the custom packs meets those here too.</p>
     *
     * <p>Every one of them stands on a floor the schematic itself laid, found by reading the city
     * back out of the buffer it was just written into. Nothing is guessed from the height of the
     * chamber, which is what put garrisons inside the rock elsewhere.</p>
     */
    private void garrison(StructureContext context, StructureBuffer buffer, FastRandom random,
                          int x, int base, int z, int rotation) {
        int guards = switch (context.preset) {
            case BASE -> 8;
            case CHAOTIC -> 14;
            case INSANE -> 22;
        };
        // The city is hundreds of blocks across, so the stride is wide and the sample is still large.
        int span = Math.min(city.rotatedWidth(rotation), city.rotatedLength(rotation));
        int stride = Math.max(2, span / 24);
        List<int[]> spots = PrefabFurnisher.standingSpots(buffer, city, x, base, z, rotation,
                guards * 4, stride);
        if (spots.isEmpty()) {
            return;
        }
        for (int i = 0; i < guards && !spots.isEmpty(); i++) {
            int[] spot = spots.remove(random.nextInt(spots.size()));
            // A wither skeleton is 2.4 blocks tall and suffocates under a two block ceiling, so it
            // only goes where the hall is actually open above it. The city has plenty of that; a
            // corridor does not, and gets an ordinary skeleton.
            boolean tall = PrefabFurnisher.headroom(buffer, spot[0], spot[1], spot[2], 3);
            buffer.addSpawn(MobSpawn.mob(spot[0], spot[1], spot[2],
                    tall && random.chance(0.25) ? "WITHER_SKELETON" : "SKELETON", 3));
        }
        for (int[] seat : spots) {
            if (PrefabFurnisher.headroom(buffer, seat[0], seat[1], seat[2], 3)) {
                buffer.addSpawn(MobSpawn.boss(seat[0], seat[1], seat[2], "WITHER_SKELETON", 4,
                        "cave_horror"));
                return;
            }
        }
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
                // Fades out with distance, so the skirt thins rather than ending on its own edge.
                double reach = 1.0 - beyond / (double) APRON;
                if (reach <= 0.0) {
                    continue;
                }
                double n = apron.unsigned2(wx, wz);
                if (n > reach * 0.85) {
                    continue;
                }
                // The slot wanders up and down as it goes round, so the skirt reads as passages
                // leaving the hall at different levels rather than as one band cut at mid height.
                double drift = apron.unsigned2(wx * 0.35 + 811.0, wz * 0.35 - 407.0);
                int span = toY - fromY;
                int mid = fromY + (int) (span * (0.25 + drift * 0.5));
                int height = 3 + (int) ((1.0 - n) * 7.0);
                int lo = Math.max(fromY, mid - height);
                int hi = Math.min(toY, mid + height);
                for (int wy = lo; wy <= hi; wy++) {
                    writer.set(wx, wy, wz, Blocks.CAVE_AIR);
                }
            }
        }
    }
}
