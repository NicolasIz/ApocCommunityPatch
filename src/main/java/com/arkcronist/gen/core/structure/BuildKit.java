package com.arkcronist.gen.core.structure;

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
            int y = context.height(x, z) + 1;
            buffer.set(x, y, z, block);
            if (random.chance(0.3)) {
                buffer.set(x, y + 1, z, block);
            }
        }
    }
}
