package com.arkcronist.gen.core.structure;

import com.arkcronist.gen.core.block.BlockShapes;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;

/**
 * Drawing primitives shared by every structure: boxes, walls, roofs, columns, domes, foundations,
 * stairs, rubble and the little touches (torches, chests, spawners) that make a build read as a
 * place rather than a shape.
 */
public final class BuildKit {

    private BuildKit() {
    }

    public static void box(StructureBuffer buffer, int x0, int y0, int z0, int x1, int y1, int z1, int block) {
        for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
                    buffer.set(x, y, z, block);
                }
            }
        }
    }

    /** Box with a decay chance per block: the basic ruin maker. */
    public static void decayedBox(StructureBuffer buffer, FastRandom random, int x0, int y0, int z0,
                                  int x1, int y1, int z1, int block, double decay) {
        for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
                    if (!random.chance(decay)) {
                        buffer.set(x, y, z, block);
                    }
                }
            }
        }
    }

    /** Four walls, no floor or ceiling. */
    public static void walls(StructureBuffer buffer, int x0, int y0, int z0, int x1, int y1, int z1, int block) {
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                buffer.set(x, y, z0, block);
                buffer.set(x, y, z1, block);
            }
            for (int z = z0; z <= z1; z++) {
                buffer.set(x0, y, z, block);
                buffer.set(x1, y, z, block);
            }
        }
    }

    public static void hollow(StructureBuffer buffer, int x0, int y0, int z0, int x1, int y1, int z1,
                              int shell, int inside) {
        box(buffer, x0, y0, z0, x1, y1, z1, shell);
        if (x1 - x0 > 1 && y1 - y0 > 1 && z1 - z0 > 1) {
            box(buffer, x0 + 1, y0 + 1, z0 + 1, x1 - 1, y1 - 1, z1 - 1, inside);
        }
    }

    /** Clears space so the building is not buried by the hill it stands on. */
    public static void clear(StructureBuffer buffer, int x0, int y0, int z0, int x1, int y1, int z1) {
        box(buffer, x0, y0, z0, x1, y1, z1, Blocks.AIR);
    }

    /**
     * Drops a foundation column down to solid ground so a building on a slope is not left on stilts.
     */
    public static void foundation(StructureContext context, StructureBuffer buffer, int x, int z,
                                  int fromY, int block, int maxDepth) {
        int ground = context.height(x, z);
        int target = Math.min(fromY - 1, ground);
        for (int y = fromY - 1; y >= target - 1 && y >= fromY - maxDepth; y--) {
            buffer.set(x, y, z, block);
        }
    }

    /** Foundation under an entire rectangle. */
    public static void foundationArea(StructureContext context, StructureBuffer buffer,
                                      int x0, int z0, int x1, int z1, int fromY, int block, int maxDepth) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                foundation(context, buffer, x, z, fromY, block, maxDepth);
            }
        }
    }

    public static void cylinder(StructureBuffer buffer, int cx, int y0, int cz, int radius, int height,
                                int block, boolean hollow) {
        int limit = radius * radius + radius;
        for (int y = y0; y < y0 + height; y++) {
            for (int ox = -radius; ox <= radius; ox++) {
                for (int oz = -radius; oz <= radius; oz++) {
                    int distance = ox * ox + oz * oz;
                    if (distance > limit) {
                        continue;
                    }
                    boolean edge = distance > limit - (radius * 2 + 1);
                    if (!hollow || edge) {
                        buffer.set(cx + ox, y, cz + oz, block);
                    }
                }
            }
        }
    }

    public static void dome(StructureBuffer buffer, int cx, int cy, int cz, int radius, int block, boolean hollow) {
        for (int oy = 0; oy <= radius; oy++) {
            double slice = Math.sqrt(Math.max(0.0, radius * radius - oy * oy));
            int r = (int) Math.round(slice);
            int limit = r * r + r;
            for (int ox = -r; ox <= r; ox++) {
                for (int oz = -r; oz <= r; oz++) {
                    int distance = ox * ox + oz * oz;
                    if (distance > limit) {
                        continue;
                    }
                    boolean shell = distance > limit - (r * 2 + 1) || oy == radius;
                    if (!hollow || shell) {
                        buffer.set(cx + ox, cy + oy, cz + oz, block);
                    }
                }
            }
        }
    }

    /** Simple gabled roof over a rectangle. */
    public static void gableRoof(StructureBuffer buffer, int x0, int y, int z0, int x1, int z1,
                                 int block, int slab) {
        int width = z1 - z0;
        int layers = width / 2 + 1;
        for (int i = 0; i <= layers; i++) {
            int zStart = z0 + i - 1;
            int zEnd = z1 - i + 1;
            if (zStart > zEnd) {
                break;
            }
            for (int x = x0 - 1; x <= x1 + 1; x++) {
                buffer.set(x, y + i, zStart, block);
                buffer.set(x, y + i, zEnd, block);
            }
            if (i == layers) {
                for (int x = x0 - 1; x <= x1 + 1; x++) {
                    for (int z = zStart; z <= zEnd; z++) {
                        buffer.set(x, y + i, z, slab);
                    }
                }
            }
        }
    }

    /** Flat roof with a crenellated parapet, the castle/fortress look. */
    public static void battlements(StructureBuffer buffer, int x0, int y, int z0, int x1, int z1, int block) {
        for (int x = x0; x <= x1; x++) {
            if ((x - x0) % 2 == 0) {
                buffer.set(x, y, z0, block);
                buffer.set(x, y, z1, block);
            }
        }
        for (int z = z0; z <= z1; z++) {
            if ((z - z0) % 2 == 0) {
                buffer.set(x0, y, z, block);
                buffer.set(x1, y, z, block);
            }
        }
    }

    /** Vertical ladder shaft used to connect floors. */
    public static void ladder(StructureBuffer buffer, int x, int y0, int y1, int z, int facing) {
        for (int y = y0; y <= y1; y++) {
            buffer.set(x, y, z, facing);
        }
    }

    /**
     * Hangs a lantern under a ceiling.
     *
     * <p>The ceiling block is written first and the lantern goes in the air below it. Writing the
     * lantern into the ceiling position - which is what the first version of every builder here did -
     * left the light with nothing to hang from and punched a hole in the roof.</p>
     */
    public static void hangingLantern(StructureBuffer buffer, int x, int ceilingY, int z, int ceilingBlock) {
        buffer.set(x, ceilingY, z, ceilingBlock);
        buffer.set(x, ceilingY - 1, z, Blocks.HANGING_LANTERN);
    }

    /** Chain plus lantern, for high ceilings. */
    public static void chandelier(StructureBuffer buffer, int x, int ceilingY, int z, int ceilingBlock, int drop) {
        buffer.set(x, ceilingY, z, ceilingBlock);
        for (int i = 1; i <= drop; i++) {
            buffer.set(x, ceilingY - i, z, Blocks.CHAIN);
        }
        buffer.set(x, ceilingY - drop - 1, z, Blocks.HANGING_LANTERN);
    }

    /**
     * Puts a torch on a wall.
     *
     * @param wallX,wallZ the solid block the torch is mounted on
     * @param dx,dz       direction from the wall into the open air
     */
    public static void wallTorch(StructureBuffer buffer, int wallX, int y, int wallZ, int dx, int dz,
                                 boolean soul) {
        int facing = BlockShapes.facingFrom(dx, dz);
        int torch = soul ? BlockShapes.soulWallTorch(facing) : BlockShapes.wallTorch(facing);
        buffer.set(wallX + dx, y, wallZ + dz, torch);
    }

    /** Lantern standing on a post, the classic street light. */
    public static void lampPost(StructureBuffer buffer, int x, int groundY, int z, int postBlock, int height) {
        for (int i = 1; i <= height; i++) {
            buffer.set(x, groundY + i, z, postBlock);
        }
        buffer.set(x, groundY + height + 1, z, Blocks.LANTERN);
    }

    /** A window: frame of the wall material with panes in the middle. */
    public static void window(StructureBuffer buffer, int x, int y, int z, int width, int height,
                              boolean alongX, int pane) {
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                buffer.set(x + (alongX ? i : 0), y + j, z + (alongX ? 0 : i), pane);
            }
        }
    }

    /**
     * Trim course: a ring of upside-down stairs just under a roof or over a plinth. This one detail
     * does more for a building's silhouette than any amount of extra height.
     */
    public static void stairTrim(StructureBuffer buffer, int x0, int y, int z0, int x1, int z1,
                                 String family, boolean upsideDown) {
        for (int x = x0; x <= x1; x++) {
            buffer.set(x, y, z0 - 1, BlockShapes.stairs(family, 0, upsideDown));
            buffer.set(x, y, z1 + 1, BlockShapes.stairs(family, 2, upsideDown));
        }
        for (int z = z0; z <= z1; z++) {
            buffer.set(x0 - 1, y, z, BlockShapes.stairs(family, 3, upsideDown));
            buffer.set(x1 + 1, y, z, BlockShapes.stairs(family, 1, upsideDown));
        }
        buffer.set(x0 - 1, y, z0 - 1, BlockShapes.slab(family, upsideDown));
        buffer.set(x1 + 1, y, z0 - 1, BlockShapes.slab(family, upsideDown));
        buffer.set(x0 - 1, y, z1 + 1, BlockShapes.slab(family, upsideDown));
        buffer.set(x1 + 1, y, z1 + 1, BlockShapes.slab(family, upsideDown));
    }

    /** Pitched roof built from stairs, ridged with slabs. */
    public static void stairRoof(StructureBuffer buffer, int x0, int y, int z0, int x1, int z1,
                                 String family, int fill) {
        int layers = (z1 - z0) / 2 + 1;
        for (int i = 0; i <= layers; i++) {
            int zNorth = z0 + i;
            int zSouth = z1 - i;
            if (zNorth > zSouth) {
                break;
            }
            for (int x = x0 - 1; x <= x1 + 1; x++) {
                buffer.set(x, y + i, zNorth, BlockShapes.stairs(family, 0, false));
                buffer.set(x, y + i, zSouth, BlockShapes.stairs(family, 2, false));
                for (int z = zNorth + 1; z < zSouth; z++) {
                    if (i > 0) {
                        buffer.set(x, y + i - 1, z, fill);
                    }
                }
            }
            if (zNorth == zSouth || zNorth + 1 == zSouth) {
                for (int x = x0 - 1; x <= x1 + 1; x++) {
                    buffer.set(x, y + i + 1, zNorth, BlockShapes.slab(family, false));
                    buffer.set(x, y + i + 1, zSouth, BlockShapes.slab(family, false));
                }
                break;
            }
        }
    }

    /** Doorway with an arch of stairs above it. */
    public static void archway(StructureBuffer buffer, int x, int y, int z, int width, int height,
                               boolean alongX, String family) {
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                buffer.set(x + (alongX ? i : 0), y + j, z + (alongX ? 0 : i), Blocks.AIR);
            }
        }
        int left = alongX ? 3 : 0;
        int right = alongX ? 1 : 2;
        buffer.set(x - (alongX ? 1 : 0), y + height, z - (alongX ? 0 : 1),
                BlockShapes.stairs(family, right, true));
        buffer.set(x + (alongX ? width : 0), y + height, z + (alongX ? 0 : width),
                BlockShapes.stairs(family, left, true));
    }

    /** Column with a base and a capital, both made of stairs. */
    public static void column(StructureBuffer buffer, int x, int y0, int z, int height,
                              int shaft, String family) {
        for (int i = 0; i < height; i++) {
            buffer.set(x, y0 + i, z, shaft);
        }
        for (int f = 0; f < 4; f++) {
            int dx = f == 1 ? 1 : f == 3 ? -1 : 0;
            int dz = f == 2 ? 1 : f == 0 ? -1 : 0;
            buffer.set(x + dx, y0, z + dz, BlockShapes.stairs(family, BlockShapes.opposite(f), false));
            buffer.set(x + dx, y0 + height - 1, z + dz, BlockShapes.stairs(family, BlockShapes.opposite(f), true));
        }
    }

    /** Staircase from one level to another, cut into the ground. */
    public static void staircase(StructureBuffer buffer, int x, int y, int z, int steps,
                                 int dx, int dz, int width, String family) {
        int facing = BlockShapes.facingFrom(dx, dz);
        for (int i = 0; i < steps; i++) {
            int sx = x + dx * i;
            int sz = z + dz * i;
            for (int w = -width; w <= width; w++) {
                int wx = sx + (dz != 0 ? w : 0);
                int wz = sz + (dx != 0 ? w : 0);
                buffer.set(wx, y - i, wz, BlockShapes.stairs(family, facing, false));
                buffer.set(wx, y - i - 1, wz, BlockShapes.doubleSlab(family));
                for (int j = 1; j <= 3; j++) {
                    buffer.set(wx, y - i + j, wz, Blocks.AIR);
                }
            }
        }
    }

    /** Garden bed: tilled look with crops and a fence border. */
    public static void gardenBed(StructureBuffer buffer, FastRandom random, int x, int y, int z,
                                 int halfX, int halfZ, int soil, int border) {
        for (int ox = -halfX; ox <= halfX; ox++) {
            for (int oz = -halfZ; oz <= halfZ; oz++) {
                boolean edge = Math.abs(ox) == halfX || Math.abs(oz) == halfZ;
                if (edge) {
                    buffer.set(x + ox, y, z + oz, border);
                } else {
                    buffer.set(x + ox, y, z + oz, soil);
                    if (random.chance(0.7)) {
                        buffer.set(x + ox, y + 1, z + oz, random.chance(0.5)
                                ? Blocks.SHORT_GRASS : Blocks.POPPY);
                    }
                }
            }
        }
    }

    public static void chest(StructureBuffer buffer, int x, int y, int z, int tier, String theme) {
        buffer.set(x, y, z, Blocks.CHEST);
        buffer.addLoot(new LootMarker(x, y, z, tier, theme));
    }

    /** Places a mob spawner and records which entity it should hold. */
    public static void spawner(StructureBuffer buffer, int x, int y, int z, String entityType) {
        buffer.set(x, y, z, Blocks.SPAWNER);
        buffer.addSpawner(new SpawnerMarker(x, y, z, entityType));
    }

    public static void torchRing(StructureBuffer buffer, int x0, int y, int z0, int x1, int z1, int step, int block) {
        for (int x = x0; x <= x1; x += step) {
            buffer.set(x, y, z0, block);
            buffer.set(x, y, z1, block);
        }
        for (int z = z0; z <= z1; z += step) {
            buffer.set(x0, y, z, block);
            buffer.set(x1, y, z, block);
        }
    }

    /** Scatters rubble and vegetation around a ruin so it sits in the landscape. */
    public static void rubble(StructureContext context, StructureBuffer buffer, FastRandom random,
                              int centreX, int centreZ, int radius, int block, double density) {
        for (int i = 0; i < radius * radius / 2; i++) {
            if (!random.chance(density)) {
                continue;
            }
            int x = centreX + random.nextInt(-radius, radius);
            int z = centreZ + random.nextInt(-radius, radius);
            int ground = context.height(x, z);
            // Rubble sits on the ground, and only where the ground is dry. Dropping it at the
            // heightmap regardless left single blocks hanging in the water off every sunken ruin.
            if (context.submerged(x, z)) {
                buffer.set(x, ground, z, block);
                continue;
            }
            int y = ground + 1;
            buffer.set(x, y, z, block);
            if (random.chance(0.3)) {
                buffer.set(x, y + 1, z, block);
            }
        }
    }
}
