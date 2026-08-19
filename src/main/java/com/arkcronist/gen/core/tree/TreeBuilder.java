package com.arkcronist.gen.core.tree;

import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.math.MathUtil;
import com.arkcronist.gen.core.structure.RegionWriter;

/**
 * Grows a complete tree, block by block, from a seed.
 *
 * <p>Trees are built as one whole object - flared base, trunk, limbs and crown - and handed to a
 * writer that keeps only the part inside the area currently being generated. Since the shape is a
 * pure function of (world seed, position, species, variant), every chunk that overlaps a tree draws
 * the same tree and the parts line up exactly. That is why nothing here is ever sliced at a chunk
 * border, no matter how large the specimen is.</p>
 *
 * <p>Draw order matters and is deliberate: crown first, then limbs, then trunk, then roots. Later
 * passes overwrite earlier ones, so a limb never gets swallowed by its own leaves and the writer
 * never has to read blocks back (which it could not do reliably outside the current chunk).</p>
 *
 * <p>Each canopy is a distinct silhouette rather than a radius setting: a spruce is stacked discs
 * with air between them, an acacia is inclined limbs capped by flat plates, a birch is a narrow
 * spire on a bare stem, a dark oak is a heavy flat crown on a three-wide trunk.</p>
 */
public final class TreeBuilder {

    private TreeBuilder() {
    }

    /**
     * @param groundY the y of the block the tree stands on; the trunk starts at {@code groundY + 1}
     */
    public static void build(TreeSpecies species, TreeVariant variant, long seed,
                             int x, int groundY, int z, RegionWriter writer) {
        FastRandom random = new FastRandom(seed);
        switch (variant) {
            case FALLEN -> buildFallen(species, random, x, groundY, z, writer);
            case STUMP -> buildStump(species, random, x, groundY, z, writer);
            default -> buildStanding(species, variant, random, x, groundY, z, writer);
        }
    }

    private static void buildStanding(TreeSpecies species, TreeVariant variant, FastRandom random,
                                      int x, int groundY, int z, RegionWriter writer) {
        boolean giant = variant == TreeVariant.GIANT;
        boolean dead = variant == TreeVariant.DEAD || species == TreeSpecies.DEAD;
        boolean leaning = variant == TreeVariant.LEANING;

        int height = random.nextInt(species.minHeight, species.maxHeight);
        if (giant) {
            height = (int) (height * random.nextDouble(1.3, 1.6));
        }
        height = Math.min(height, writer.maxY() - groundY - 5);
        if (height < 4) {
            return;
        }

        int trunkRadius = giant ? species.trunkRadius + 1 : species.trunkRadius;
        double leanX = 0.0;
        double leanZ = 0.0;
        if (leaning) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double strength = random.nextDouble(0.16, 0.34);
            leanX = Math.cos(angle) * strength;
            leanZ = Math.sin(angle) * strength;
        }

        int topX = x + (int) Math.round(leanX * height);
        int topZ = z + (int) Math.round(leanZ * height);
        int topY = groundY + height;

        if (!dead) {
            crown(species, random, topX, topY, topZ, height, trunkRadius, giant, writer);
        }
        limbs(species, random, x, groundY, z, height, leanX, leanZ, trunkRadius, giant, dead, writer);
        trunk(species, x, groundY, z, height, trunkRadius, leanX, leanZ, writer);
        roots(species, random, x, groundY, z, trunkRadius, giant, writer);
        if (!dead && (species == TreeSpecies.JUNGLE || species == TreeSpecies.GIANT_JUNGLE
                || species.canopy == TreeSpecies.Canopy.WILLOW)) {
            vines(species, random, topX, topY, topZ, species.maxRadius(), writer);
        }
    }

    // ------------------------------------------------------------------ skeleton

    private static void trunk(TreeSpecies species, int x, int groundY, int z, int height,
                              int radius, double leanX, double leanZ, RegionWriter writer) {
        int block = radius > 1 ? species.wood : species.log;
        for (int i = 1; i <= height; i++) {
            int y = groundY + i;
            int cx = x + (int) Math.round(leanX * i);
            int cz = z + (int) Math.round(leanZ * i);

            // Flared base: the first two blocks are one wider, which is what makes a thick trunk
            // look planted instead of extruded.
            double taper = 1.0 - 0.5 * (i / (double) height);
            int r = (int) Math.round((radius - 1) * taper);
            if (i <= 2 && radius >= 2) {
                r = radius - 1;
            }

            if (r <= 0) {
                writer.set(cx, y, cz, species.log);
                continue;
            }
            int limit = r * r + r;
            for (int ox = -r; ox <= r; ox++) {
                for (int oz = -r; oz <= r; oz++) {
                    if (ox * ox + oz * oz <= limit) {
                        writer.set(cx + ox, y, cz + oz, block);
                    }
                }
            }
        }
    }

    private static void roots(TreeSpecies species, FastRandom random, int x, int groundY, int z,
                              int radius, boolean giant, RegionWriter writer) {
        boolean stilts = species.canopy == TreeSpecies.Canopy.WILLOW;
        int rootCount = giant ? random.nextInt(5, 9) : random.nextInt(3, 6);
        int reach = giant ? random.nextInt(3, 6) : random.nextInt(1, 4);

        for (int i = 0; i < rootCount; i++) {
            double angle = (i / (double) rootCount) * Math.PI * 2.0 + random.nextDouble(-0.25, 0.25);
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            if (stilts) {
                // Mangrove stilts: the trunk stands on arched legs.
                int start = groundY + random.nextInt(2, 5);
                for (int step = 1; step <= reach + 2; step++) {
                    int rx = x + (int) Math.round(dx * step);
                    int rz = z + (int) Math.round(dz * step);
                    for (int y = start - step; y >= groundY; y--) {
                        writer.set(rx, y, rz, species.log);
                    }
                }
                continue;
            }
            for (int step = 1; step <= reach; step++) {
                int rx = x + (int) Math.round(dx * step);
                int rz = z + (int) Math.round(dz * step);
                int ry = groundY + Math.max(0, 2 - step);
                writer.set(rx, ry, rz, radius > 1 ? species.wood : species.log);
                if (giant && step <= 2) {
                    writer.set(rx, ry + 1, rz, species.wood);
                }
            }
        }
    }

    /** Main limbs. Their number, angle and length are what define the silhouette below the crown. */
    private static void limbs(TreeSpecies species, FastRandom random, int x, int groundY, int z,
                              int height, double leanX, double leanZ, int trunkRadius,
                              boolean giant, boolean dead, RegionWriter writer) {
        TreeSpecies.Canopy canopy = species.canopy;
        if (canopy == TreeSpecies.Canopy.SPIRE || canopy == TreeSpecies.Canopy.PAGODA) {
            // Conifers and birches keep a clean stem; their crowns are built straight on the trunk.
            if (dead) {
                deadBranches(species, random, x, groundY, z, height, writer);
            }
            return;
        }

        int count = switch (canopy) {
            case PLATE -> random.nextInt(2, 4);
            case MEGA -> random.nextInt(4, 7);
            case BROAD -> random.nextInt(3, 6);
            case WILLOW -> random.nextInt(3, 5);
            default -> random.nextInt(1, 3);
        };
        if (giant) {
            count += 2;
        }
        if (dead) {
            deadBranches(species, random, x, groundY, z, height, writer);
            return;
        }

        double lowest = switch (canopy) {
            case PLATE -> 0.45;
            case MEGA -> 0.62;
            case WILLOW -> 0.60;
            default -> 0.55;
        };

        for (int i = 0; i < count; i++) {
            int startOffset = (int) (height * lowest) + random.nextInt(0, Math.max(1, (int) (height * 0.3)));
            int startY = groundY + startOffset;
            int startX = x + (int) Math.round(leanX * startOffset);
            int startZ = z + (int) Math.round(leanZ * startOffset);

            double angle = (i / (double) count) * Math.PI * 2.0 + random.nextDouble(-0.5, 0.5);
            double rise = switch (canopy) {
                case PLATE -> random.nextDouble(0.55, 0.95);
                case WILLOW -> random.nextDouble(0.10, 0.35);
                case MEGA -> random.nextDouble(0.25, 0.55);
                default -> random.nextDouble(0.35, 0.70);
            };
            int length = switch (canopy) {
                case PLATE -> random.nextInt(4, 8);
                case MEGA -> random.nextInt(4, 8) + trunkRadius;
                case WILLOW -> random.nextInt(3, 6);
                default -> random.nextInt(3, 6);
            };

            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            int endX = startX;
            int endY = startY;
            int endZ = startZ;
            // Limbs may not climb past the crown: a limb that overshot used to hang its foliage in
            // the air above the tree with a gap under it.
            int ceiling = groundY + height;
            for (int step = 1; step <= length; step++) {
                int nextY = Math.min(ceiling, startY + (int) Math.round(rise * step));
                if (nextY >= writer.maxY() - 2) {
                    break;
                }
                endX = startX + (int) Math.round(dx * step);
                endZ = startZ + (int) Math.round(dz * step);
                endY = nextY;
                writer.set(endX, endY, endZ, orientedLog(species, dx, rise, dz));
            }

            switch (canopy) {
                case PLATE -> plate(species, random, endX, endY + 1, endZ, giant ? 4 : 3, writer);
                case WILLOW -> blob(species, random, endX, endY, endZ, 3, writer);
                case MEGA -> blob(species, random, endX, endY, endZ, giant ? 4 : 3, writer);
                default -> blob(species, random, endX, endY, endZ, giant ? 3 : 2, writer);
            }
        }
    }

    private static void deadBranches(TreeSpecies species, FastRandom random, int x, int groundY, int z,
                                     int height, RegionWriter writer) {
        int count = random.nextInt(2, 5);
        for (int i = 0; i < count; i++) {
            int startY = groundY + random.nextInt(height / 2, Math.max(height / 2 + 1, height - 1));
            double angle = random.nextDouble() * Math.PI * 2.0;
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            int length = random.nextInt(2, 5);
            for (int step = 1; step <= length; step++) {
                writer.set(x + (int) Math.round(dx * step), startY + step / 2,
                        z + (int) Math.round(dz * step), orientedLog(species, dx, 0.4, dz));
            }
        }
    }

    // ------------------------------------------------------------------ crowns

    private static void crown(TreeSpecies species, FastRandom random, int x, int y, int z,
                              int height, int trunkRadius, boolean giant, RegionWriter writer) {
        switch (species.canopy) {
            case PAGODA -> pagoda(species, random, x, y, z, height, giant, writer);
            case SPIRE -> spire(species, random, x, y, z, giant, writer);
            case ROUND -> ball(species, random, x, y - 1, z, giant ? 4 : 3, writer);
            case BROAD -> broad(species, random, x, y, z, giant, writer);
            case MEGA -> mega(species, random, x, y, z, trunkRadius, giant, writer);
            case PLATE -> plate(species, random, x, y + 1, z, giant ? 5 : 4, writer);
            case WILLOW -> willow(species, random, x, y, z, giant, writer);
            case NONE -> {
                // Dead wood keeps its bare silhouette.
            }
        }
    }

    /**
     * Stacked discs with air between them: the spruce look.
     *
     * <p>The crown is sized from the tree, not from a fixed layer count. A tall spruce gets more
     * layers <em>and</em> a wider skirt, instead of the same small cone stuck on a longer pole.</p>
     */
    private static void pagoda(TreeSpecies species, FastRandom random, int x, int y, int z,
                               int height, boolean giant, RegionWriter writer) {
        int crownHeight = Math.max(6, (int) (height * (giant ? 0.75 : 0.62)));
        int step = 2;
        int layers = Math.max(3, crownHeight / step);
        int maxRadius = giant ? 6 : 4;

        writer.set(x, y + 2, z, species.leaves);
        writer.set(x, y + 1, z, species.leaves);
        for (int i = 0; i < layers; i++) {
            int currentY = y - i * step;
            double t = i / (double) Math.max(1, layers - 1);
            int radius = (int) Math.round(MathUtil.lerp(Math.pow(t, 0.75), 1.0, maxRadius));
            // A skirt one block under each disc gives the layer its depth.
            disc(species, random, x, currentY, z, radius, 0.94, writer);
            if (radius > 1) {
                disc(species, random, x, currentY - 1, z, radius - 1, 0.8, writer);
            }
        }
    }

    /** A narrow crown on a long clean stem: the birch look. */
    private static void spire(TreeSpecies species, FastRandom random, int x, int y, int z,
                              boolean giant, RegionWriter writer) {
        int layers = giant ? 7 : 5;
        for (int i = 0; i < layers; i++) {
            double t = i / (double) layers;
            int radius = t < 0.25 ? 1 : t < 0.75 ? 2 : 1;
            disc(species, random, x, y - i, z, radius, 0.9, writer);
        }
        writer.set(x, y + 1, z, species.leaves);
    }

    private static void ball(TreeSpecies species, FastRandom random, int x, int y, int z,
                             int radius, RegionWriter writer) {
        for (int oy = -radius; oy <= radius; oy++) {
            double t = Math.abs(oy) / (double) radius;
            int r = (int) Math.round(radius * Math.sqrt(Math.max(0.0, 1.0 - t * t)));
            disc(species, random, x, y + oy, z, r, 0.88, writer);
        }
    }

    /** Large fused crown with lobes: a grown oak or a jungle tree. */
    private static void broad(TreeSpecies species, FastRandom random, int x, int y, int z,
                              boolean giant, RegionWriter writer) {
        int radius = giant ? 5 : 4;
        disc(species, random, x, y + 1, z, radius - 2, 0.8, writer);
        disc(species, random, x, y, z, radius, 0.92, writer);
        disc(species, random, x, y - 1, z, radius, 0.88, writer);
        disc(species, random, x, y - 2, z, radius - 1, 0.7, writer);
        // Lobes hanging off the main mass break the outline up.
        int lobes = giant ? 4 : 3;
        for (int i = 0; i < lobes; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            int lx = x + (int) Math.round(Math.cos(angle) * (radius - 1));
            int lz = z + (int) Math.round(Math.sin(angle) * (radius - 1));
            blob(species, random, lx, y - random.nextInt(0, 2), lz, 2, writer);
        }
    }

    /** Wide, flat, heavy crown on a thick trunk: dark oak and giant jungle. */
    private static void mega(TreeSpecies species, FastRandom random, int x, int y, int z,
                             int trunkRadius, boolean giant, RegionWriter writer) {
        int radius = (giant ? 7 : 6) + trunkRadius;
        plate(species, random, x, y + 1, z, radius - 2, writer);
        disc(species, random, x, y, z, radius, 0.95, writer);
        disc(species, random, x, y - 1, z, radius, 0.9, writer);
        disc(species, random, x, y - 2, z, radius - 2, 0.6, writer);
        for (int i = 0; i < 4; i++) {
            double angle = i * Math.PI / 2.0 + random.nextDouble(-0.4, 0.4);
            int lx = x + (int) Math.round(Math.cos(angle) * (radius - 2));
            int lz = z + (int) Math.round(Math.sin(angle) * (radius - 2));
            blob(species, random, lx, y - 1, lz, 2, writer);
        }
    }

    /** Two thin layers of leaves with a hard edge: the acacia plate. */
    private static void plate(TreeSpecies species, FastRandom random, int x, int y, int z,
                              int radius, RegionWriter writer) {
        disc(species, random, x, y, z, radius, 0.97, writer);
        disc(species, random, x, y - 1, z, radius - 1, 0.9, writer);
    }

    /** Wide crown with long strands hanging off the rim. */
    private static void willow(TreeSpecies species, FastRandom random, int x, int y, int z,
                               boolean giant, RegionWriter writer) {
        int radius = giant ? 6 : 5;
        disc(species, random, x, y + 1, z, radius - 2, 0.85, writer);
        disc(species, random, x, y, z, radius, 0.95, writer);
        disc(species, random, x, y - 1, z, radius, 0.85, writer);
        for (int i = 0; i < 20; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            int lx = x + (int) Math.round(Math.cos(angle) * radius);
            int lz = z + (int) Math.round(Math.sin(angle) * radius);
            int length = random.nextInt(2, 6);
            for (int j = 0; j < length; j++) {
                writer.set(lx, y - 1 - j, lz, species.leaves);
            }
        }
    }

    private static void blob(TreeSpecies species, FastRandom random, int x, int y, int z,
                             int radius, RegionWriter writer) {
        for (int oy = -radius; oy <= radius; oy++) {
            int r = radius - Math.abs(oy) / 2;
            disc(species, random, x, y + oy, z, r, 0.75, writer);
        }
    }

    private static void disc(TreeSpecies species, FastRandom random, int x, int y, int z,
                             int radius, double fill, RegionWriter writer) {
        if (radius <= 0) {
            writer.set(x, y, z, species.leaves);
            return;
        }
        int limit = radius * radius + radius;
        for (int ox = -radius; ox <= radius; ox++) {
            for (int oz = -radius; oz <= radius; oz++) {
                int distance = ox * ox + oz * oz;
                if (distance > limit) {
                    continue;
                }
                // Edges thin out so crowns look organic instead of like stamped circles.
                double edge = distance / (double) Math.max(1, limit);
                if (edge > 0.55 && random.nextDouble() > fill) {
                    continue;
                }
                writer.set(x + ox, y, z + oz, species.leaves);
            }
        }
    }

    /** Vines hanging from the crown, for jungle and willow species. */
    private static void vines(TreeSpecies species, FastRandom random, int x, int y, int z,
                              int radius, RegionWriter writer) {
        for (int i = 0; i < 24; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = random.nextDouble(radius * 0.4, radius * 0.9);
            int vx = x + (int) Math.round(Math.cos(angle) * distance);
            int vz = z + (int) Math.round(Math.sin(angle) * distance);
            int face = random.nextInt(4);
            int vine = switch (face) {
                case 0 -> Blocks.VINE_SOUTH;
                case 1 -> Blocks.VINE_WEST;
                case 2 -> Blocks.VINE_NORTH;
                default -> Blocks.VINE_EAST;
            };
            int length = random.nextInt(2, 8);
            for (int j = 1; j <= length; j++) {
                writer.set(vx, y - j, vz, vine);
            }
        }
    }

    // ------------------------------------------------------------------ ground variants

    private static void buildFallen(TreeSpecies species, FastRandom random, int x, int groundY, int z,
                                    RegionWriter writer) {
        int length = random.nextInt(4, Math.max(5, species.maxHeight - 2));
        double angle = random.nextDouble() * Math.PI * 2.0;
        double dx = Math.cos(angle);
        double dz = Math.sin(angle);
        int log = Math.abs(dx) > Math.abs(dz) ? species.logX : species.logZ;

        for (int step = 0; step <= length; step++) {
            int lx = x + (int) Math.round(dx * step);
            int lz = z + (int) Math.round(dz * step);
            writer.set(lx, groundY + 1, lz, log);
            if (random.chance(0.25)) {
                writer.set(lx, groundY + 1, lz + 1, Blocks.BROWN_MUSHROOM);
            }
            if (random.chance(0.18)) {
                writer.set(lx, groundY + 2, lz, Blocks.MOSS_CARPET);
            }
        }
        // Root plate torn out of the ground at the base.
        for (int oy = 0; oy <= 2; oy++) {
            for (int ox = -1; ox <= 1; ox++) {
                for (int oz = -1; oz <= 1; oz++) {
                    if (random.chance(0.45)) {
                        writer.set(x + ox, groundY + 1 + oy, z + oz, species.wood);
                    }
                }
            }
        }
    }

    private static void buildStump(TreeSpecies species, FastRandom random, int x, int groundY, int z,
                                   RegionWriter writer) {
        int height = random.nextInt(1, 3);
        int radius = species.trunkRadius;
        for (int i = 1; i <= height; i++) {
            for (int ox = -radius; ox <= radius; ox++) {
                for (int oz = -radius; oz <= radius; oz++) {
                    if (ox * ox + oz * oz <= radius * radius + radius) {
                        writer.set(x + ox, groundY + i, z + oz, species.log);
                    }
                }
            }
        }
        // Splintered top: a couple of blocks stick up higher than the rest.
        for (int i = 0; i < 3; i++) {
            if (random.chance(0.5)) {
                writer.set(x + random.nextInt(-radius, radius), groundY + height + 1,
                        z + random.nextInt(-radius, radius), species.log);
            }
        }
    }

    private static int orientedLog(TreeSpecies species, double dx, double dy, double dz) {
        double ax = Math.abs(dx);
        double ay = Math.abs(dy);
        double az = Math.abs(dz);
        if (ay >= ax && ay >= az) {
            return species.log;
        }
        return ax >= az ? species.logX : species.logZ;
    }
}
