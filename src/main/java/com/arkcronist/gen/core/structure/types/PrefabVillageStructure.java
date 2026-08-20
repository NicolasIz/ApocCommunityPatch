package com.arkcronist.gen.core.structure.types;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.prefab.Prefab;
import com.arkcronist.gen.core.prefab.PrefabRegistry;
import com.arkcronist.gen.core.structure.BufferWriter;
import com.arkcronist.gen.core.structure.LootMarker;
import com.arkcronist.gen.core.structure.PrefabFurnisher;
import com.arkcronist.gen.core.structure.MobSpawn;
import com.arkcronist.gen.core.structure.Structure;
import com.arkcronist.gen.core.structure.StructureBuffer;
import com.arkcronist.gen.core.structure.StructureContext;

import java.util.ArrayList;
import java.util.List;

/**
 * A settlement assembled out of individual house schematics from {@code prefabs/houses/}.
 *
 * <p>This is the difference between a village and a building: one file is one house, and the
 * generator decides how many stand where. Houses are laid out on a ring around a green, each one
 * rotated to face inwards, each one levelled into its own patch of ground, with paths worn between
 * them and villagers living in them.</p>
 *
 * <p>Because every house is sited separately, a village follows rolling ground instead of demanding
 * a parade square - the plot for each building is levelled, the ground between them is left alone.
 * A house is only kept if its plot is flat enough and it does not overlap one already placed, so a
 * hillside village simply ends up with fewer houses rather than with houses hanging off a cliff.</p>
 */
public final class PrefabVillageStructure implements Structure {

    private static final String CATEGORY = "houses";

    private final PrefabRegistry prefabs;
    private final int houseRadius;
    private final int radius;

    public PrefabVillageStructure(PrefabRegistry prefabs) {
        this.prefabs = prefabs;
        int widest = 8;
        for (Prefab prefab : prefabs.category(CATEGORY)) {
            widest = Math.max(widest, prefab.radius());
        }
        this.houseRadius = widest;
        // Room for a ring of houses plus their gardens.
        this.radius = Math.min(96, widest * 4 + 16);
    }

    @Override
    public String id() {
        return "prefab_village";
    }

    @Override
    public StructureTag tag() {
        return StructureTag.VILLAGE;
    }

    @Override
    public int radius() {
        return radius;
    }

    @Override
    public Placement placement() {
        return Placement.SURFACE_LARGE;
    }

    @Override
    public double weight() {
        return 2.4;
    }

    @Override
    public boolean canPlace(StructureContext context) {
        if (prefabs.category(CATEGORY).isEmpty()) {
            return false;
        }
        if (context.submerged(context.originX, context.originZ)) {
            return false;
        }
        if (context.groundY <= context.seaLevel()) {
            return false;
        }
        // Generous, because each house levels its own plot: the village only needs land that is
        // broadly settleable, not a flat field.
        return context.relief(context.originX, context.originZ, radius / 2) < 26;
    }

    @Override
    public void build(StructureContext context, StructureBuffer buffer) {
        FastRandom random = context.random;
        BufferWriter writer = new BufferWriter(buffer, context.engine.settings().minY, context.maxY());
        int wanted = houseCount(context, random);
        List<int[]> placed = new ArrayList<>();

        int greenX = context.originX;
        int greenZ = context.originZ;
        int spacing = houseRadius * 2 + 5;

        for (int attempt = 0; attempt < wanted * 4 && placed.size() < wanted; attempt++) {
            Prefab house = prefabs.pick(CATEGORY, null, null, random);
            if (house == null) {
                return;
            }
            int ring = 1 + attempt / Math.max(1, wanted);
            double angle = random.nextDouble() * Math.PI * 2.0;
            int distance = ring * spacing / 2 + random.nextInt(0, spacing / 2 + 1);
            int x = greenX + (int) Math.round(Math.cos(angle) * distance);
            int z = greenZ + (int) Math.round(Math.sin(angle) * distance);

            int footprint = house.radius() + 2;
            if (overlaps(placed, x, z, footprint) || !plotIsBuildable(context, x, z, house)) {
                continue;
            }
            // Face the green, so the village reads as a village rather than as scattered huts.
            int rotation = facing(x - greenX, z - greenZ);
            int baseY = plotHeight(context, x, z, house) - 1
                    + context.engine.settings().prefabBuildingLift;
            if (baseY + house.height >= context.maxY()) {
                continue;
            }

            level(context, writer, house, x, z, baseY, rotation);
            house.blit(writer, x, baseY, z, rotation, Prefab.BlitOptions.solid(Blocks.AIR));
            house.forEachContainer(x, baseY, z, rotation, (cx, cy, cz) ->
                    buffer.addLoot(new LootMarker(cx, cy, cz, 1, "village")));
            if (context.engine.settings().prefabFurnish) {
                furnish(buffer, house, x, baseY, z, rotation, random);
            }
            populate(buffer, random, x, z, baseY);
            placed.add(new int[]{x, z, footprint, baseY});
        }

        if (!placed.isEmpty()) {
            paths(context, writer, placed, greenX, greenZ);
        }
    }

    private int houseCount(StructureContext context, FastRandom random) {
        return switch (context.preset) {
            case BASE -> random.nextInt(5, 9);
            case CHAOTIC -> random.nextInt(6, 12);
            case INSANE -> random.nextInt(9, 16);
        };
    }

    private boolean overlaps(List<int[]> placed, int x, int z, int footprint) {
        for (int[] other : placed) {
            int gap = footprint + other[2];
            if (Math.abs(other[0] - x) < gap && Math.abs(other[1] - z) < gap) {
                return true;
            }
        }
        return false;
    }

    /** A plot has to be dry, above the sea and not a cliff face. */
    private boolean plotIsBuildable(StructureContext context, int x, int z, Prefab house) {
        if (context.submerged(x, z)) {
            return false;
        }
        int reach = Math.max(3, house.radius() * 2 / 3);
        return context.relief(x, z, reach) < 9;
    }

    /** Mean ground over the house's own plot, so each building sits on its own patch of hill. */
    private int plotHeight(StructureContext context, int x, int z, Prefab house) {
        int reach = Math.max(2, house.radius() / 2);
        long sum = 0;
        int samples = 0;
        for (int dx = -reach; dx <= reach; dx += Math.max(1, reach)) {
            for (int dz = -reach; dz <= reach; dz += Math.max(1, reach)) {
                sum += context.height(x + dx, z + dz);
                samples++;
            }
        }
        return (int) (sum / Math.max(1, samples));
    }

    /** Quarter turns that put the house's front towards the middle of the village. */
    private int facing(int dx, int dz) {
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? 3 : 1;
        }
        return dz > 0 ? 2 : 0;
    }

    private void level(StructureContext context, BufferWriter writer, Prefab house,
                       int x, int z, int baseY, int rotation) {
        int outWidth = house.rotatedWidth(rotation);
        int outLength = house.rotatedLength(rotation);
        int minX = x - house.rotatedAnchorX(rotation);
        int minZ = z - house.rotatedAnchorZ(rotation);
        int floor = context.engine.settings().minY + 1;
        int stone = context.biome.stone.pickAt(context.engine.seed(), x, baseY, z);

        for (int outX = 0; outX < outWidth; outX++) {
            for (int outZ = 0; outZ < outLength; outZ++) {
                if (!house.occupies(rotation, outX, outZ)) {
                    continue;
                }
                int worldX = minX + outX;
                int worldZ = minZ + outZ;
                int ground = context.height(worldX, worldZ);
                for (int y = Math.max(ground, floor); y < baseY; y++) {
                    writer.set(worldX, y, worldZ, stone);
                }
                for (int y = baseY; y <= ground; y++) {
                    writer.set(worldX, y, worldZ, Blocks.AIR);
                }
            }
        }
    }

    /**
     * Puts in whatever a house needs to be lived in and the builder left out.
     *
     * <p>Most schematics people share are exteriors: walls, a roof, shutters, maybe a chest. A
     * villager needs a bed to claim the house as a home, a job block to have a trade, and a light or
     * mobs spawn in the front room. Rather than editing anybody's file, the placer reads the house
     * back out of the buffer, finds a floor with headroom inside it and furnishes only what the
     * schematic did not already provide.</p>
     */
    /**
     * Gives a house a bed, a light and a trade block, unless its author already did.
     *
     * <p>Without a bed a villager never claims the house as a home, so the settlement never becomes
     * a village in the game's eyes: no trades, no breeding, no golems.</p>
     */
    private void furnish(StructureBuffer buffer, Prefab house, int x, int baseY, int z,
                         int rotation, FastRandom random) {
        List<int[]> spots = PrefabFurnisher.interiorSpots(buffer, house, x, baseY, z, rotation, 12);
        int cursor = 0;
        if (!house.hasBed) {
            cursor = PrefabFurnisher.bed(buffer, spots, cursor);
        }
        if (!house.hasLight) {
            cursor = PrefabFurnisher.light(buffer, spots, cursor, 1);
        }
        PrefabFurnisher.workstation(buffer, spots, cursor, random);
    }

    /** A worn path from each door back to the green. */
    private void paths(StructureContext context, BufferWriter writer, List<int[]> placed,
                       int greenX, int greenZ) {
        for (int[] house : placed) {
            int steps = Math.max(Math.abs(house[0] - greenX), Math.abs(house[1] - greenZ));
            for (int step = 0; step <= steps; step++) {
                double t = steps == 0 ? 0.0 : (double) step / steps;
                int px = (int) Math.round(house[0] + (greenX - house[0]) * t);
                int pz = (int) Math.round(house[1] + (greenZ - house[1]) * t);
                for (int ox = -1; ox <= 1; ox++) {
                    for (int oz = -1; oz <= 1; oz++) {
                        int y = context.height(px + ox, pz + oz);
                        if (y <= context.water(px + ox, pz + oz)) {
                            continue;
                        }
                        writer.set(px + ox, y, pz + oz,
                                (ox + oz) % 2 == 0 ? Blocks.DIRT_PATH : Blocks.COARSE_DIRT);
                    }
                }
            }
        }
    }

    /** Villagers, and the iron golem that comes with having enough of them. */
    private void populate(StructureBuffer buffer, FastRandom random, int x, int z, int baseY) {
        int residents = random.nextInt(1, 4);
        for (int i = 0; i < residents; i++) {
            buffer.addSpawn(MobSpawn.mob(x + random.nextInt(-2, 2), baseY + 1, z + random.nextInt(-2, 2),
                    "VILLAGER", 0));
        }
        if (random.chance(0.35)) {
            buffer.addSpawn(MobSpawn.mob(x + random.nextInt(-3, 3), baseY + 1, z + random.nextInt(-3, 3),
                    "IRON_GOLEM", 0));
        }
    }

    /** The houses this village can draw from; used by the prefab tests. */
    public List<Prefab> pool() {
        return prefabs.category(CATEGORY);
    }
}
