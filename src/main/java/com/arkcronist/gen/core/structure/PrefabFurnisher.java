package com.arkcronist.gen.core.structure;

import com.arkcronist.gen.core.block.BlockShapes;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.prefab.Prefab;

import java.util.ArrayList;
import java.util.List;

/**
 * Puts into a stamped prefab whatever it needs to work and its author left out.
 *
 * <p>Schematics that people share are usually exteriors. They have walls, a roof and shutters, and
 * very often no light at all and nothing to sleep on - which on a live server means a pitch black
 * building that breeds mobs, and villagers with no home to claim. Rather than editing anybody's
 * file, the placer reads the building back out of the buffer it was just written into and adds only
 * what is missing.</p>
 *
 * <p>Finding a room is done from the building's own geometry rather than from sealed air, because
 * most shared schematics are not airtight: their windows are open holes behind a shutter. A spot
 * counts when the prefab laid a floor there and left headroom above it, which no position outside
 * the walls can satisfy.</p>
 */
public final class PrefabFurnisher {

    private PrefabFurnisher() {
    }

    /**
     * Floor positions inside a prefab: somewhere it laid a floor and left two blocks of headroom.
     *
     * @param limit stop after this many, so a cathedral does not scan its whole volume
     */
    public static List<int[]> interiorSpots(StructureBuffer buffer, Prefab prefab,
                                            int originX, int baseY, int originZ, int rotation, int limit) {
        return standingSpots(buffer, prefab, originX, baseY, originZ, rotation, limit, 1);
    }

    /**
     * The same scan, taken across the whole building instead of out of one corner.
     *
     * <p>{@link #interiorSpots} walks every column and stops at its limit, which for anything larger
     * than a cottage means every spot it returns is in the same corner - fine for the first few
     * lanterns, useless for a garrison, which has to be spread through the place it is guarding.
     * Striding over the footprint samples all of it for the same amount of work.</p>
     *
     * @param stride how many columns to skip between samples; 1 is every column
     */
    public static List<int[]> standingSpots(StructureBuffer buffer, Prefab prefab, int originX, int baseY,
                                            int originZ, int rotation, int limit, int stride) {
        int step = Math.max(1, stride);
        int outWidth = prefab.rotatedWidth(rotation);
        int outLength = prefab.rotatedLength(rotation);
        int minX = originX - prefab.rotatedAnchorX(rotation);
        int minZ = originZ - prefab.rotatedAnchorZ(rotation);
        List<int[]> spots = new ArrayList<>();
        for (int outX = 1; outX < outWidth - 1 && spots.size() < limit; outX += step) {
            for (int outZ = 1; outZ < outLength - 1 && spots.size() < limit; outZ += step) {
                if (!prefab.occupies(rotation, outX, outZ)) {
                    continue;
                }
                int worldX = minX + outX;
                int worldZ = minZ + outZ;
                for (int y = baseY + 1; y < baseY + prefab.height - 1; y++) {
                    int floor = buffer.get(worldX, y - 1, worldZ);
                    if (floor < 0 || Blocks.isAir(floor)) {
                        continue;
                    }
                    if (clear(buffer, worldX, y, worldZ) && clear(buffer, worldX, y + 1, worldZ)) {
                        spots.add(new int[]{worldX, y, worldZ});
                        break;
                    }
                }
            }
        }
        return spots;
    }

    /**
     * Nothing solid here: either the prefab wrote air, or it wrote nothing at all.
     *
     * <p>Cave air counts. It is air in every way that matters and it is what the deep structures are
     * hollowed out with, so a check for {@code minecraft:air} alone reads the whole inside of an
     * ancient city as solid rock and finds nowhere in it to stand.</p>
     */
    public static boolean clear(StructureBuffer buffer, int x, int y, int z) {
        int block = buffer.get(x, y, z);
        return block < 0 || Blocks.isAir(block);
    }

    /**
     * Whether a spot has this much clear space above it.
     *
     * <p>Two blocks is a person and most mobs. Some are taller and it matters more than it sounds:
     * a wither skeleton is 2.4 blocks and an iron golem 2.7, so both of them put their eyes inside
     * the ceiling of an ordinary room - and a mob with its eyes in a solid block suffocates. That is
     * why a standard house cannot hold one, and why anything tall has to be asked for by height
     * rather than just dropped on the nearest floor.</p>
     */
    public static boolean headroom(StructureBuffer buffer, int x, int y, int z, int blocks) {
        for (int offset = 0; offset < blocks; offset++) {
            if (!clear(buffer, x, y + offset, z)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Lights a building that does not light itself.
     *
     * <p>Spread across the spots rather than clustered, so a long hall is not lit only at one end.</p>
     *
     * @return the index to carry on placing furniture from
     */
    public static int light(StructureBuffer buffer, List<int[]> spots, int from, int lanterns) {
        if (from >= spots.size() || lanterns <= 0) {
            return from;
        }
        int remaining = spots.size() - from;
        int step = Math.max(1, remaining / lanterns);
        int placed = 0;
        int index = from;
        while (index < spots.size() && placed < lanterns) {
            int[] spot = spots.get(index);
            buffer.set(spot[0], spot[1], spot[2], Blocks.LANTERN);
            placed++;
            index += step;
        }
        return from + placed;
    }

    /**
     * Lays a bed, which needs two clear blocks side by side over a floor.
     *
     * @return the index to carry on placing furniture from, unchanged when no pair fitted
     */
    public static int bed(StructureBuffer buffer, List<int[]> spots, int from) {
        for (int i = from; i < spots.size(); i++) {
            int[] foot = spots.get(i);
            for (int facing = 0; facing < 4; facing++) {
                int headX = foot[0] + (facing == 1 ? 1 : facing == 3 ? -1 : 0);
                int headZ = foot[2] + (facing == 2 ? 1 : facing == 0 ? -1 : 0);
                int under = buffer.get(headX, foot[1] - 1, headZ);
                if (!clear(buffer, headX, foot[1], headZ) || under < 0 || under == Blocks.AIR) {
                    continue;
                }
                buffer.set(foot[0], foot[1], foot[2], BlockShapes.bed(facing, false));
                buffer.set(headX, foot[1], headZ, BlockShapes.bed(facing, true));
                return i + 1;
            }
        }
        return from;
    }

    /** One of these gives a villager a trade instead of leaving them to wander. */
    private static final int[] WORKSTATIONS = {
            Blocks.COMPOSTER, Blocks.LECTERN, Blocks.SMITHING_TABLE, Blocks.FLETCHING_TABLE,
            Blocks.CARTOGRAPHY_TABLE, Blocks.LOOM, Blocks.STONECUTTER, Blocks.SMOKER,
            Blocks.BLAST_FURNACE, Blocks.BARREL, Blocks.GRINDSTONE, Blocks.CAULDRON};

    public static int workstation(StructureBuffer buffer, List<int[]> spots, int from, FastRandom random) {
        if (from >= spots.size()) {
            return from;
        }
        int[] spot = spots.get(from);
        buffer.set(spot[0], spot[1], spot[2], WORKSTATIONS[random.nextInt(WORKSTATIONS.length)]);
        return from + 1;
    }
}
