package com.arkcronist.gen.core.tree;

import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.math.MathUtil;
import com.arkcronist.gen.core.structure.RegionWriter;

/**
 * Grows a complete tree, block by block, from a seed.
 *
 * <p>Trees are built as one whole object - roots, trunk, branches and crown - and handed to a writer
 * that keeps only the part inside the area currently being generated. Since the shape is a pure
 * function of (world seed, position, species, variant), every chunk that overlaps a tree draws the
 * same tree and the parts line up exactly. That is why nothing here is ever sliced at a chunk
 * border, no matter how large the specimen is.</p>
 *
 * <p>Draw order matters and is deliberate: crown first, then branches, then trunk, then roots. Later
 * passes overwrite earlier ones, so a branch never gets swallowed by its own leaves and the writer
 * never has to read blocks back (which it could not do reliably outside the current chunk).</p>
 */
public final class TreeBuilder {

    private TreeBuilder() {
    }

    /**
     * @param groundY the y of the block the tree stands on (the trunk starts at {@code groundY + 1})
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
            height = (int) (height * random.nextDouble(1.35, 1.7));
        }
        height = Math.min(height, writer.maxY() - groundY - 4);
        if (height < 3) {
            return;
        }

        int trunkRadius = giant ? species.trunkRadius + 1 : species.trunkRadius;
        double leanX = 0.0;
        double leanZ = 0.0;
        if (leaning) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double strength = random.nextDouble(0.18, 0.40);
            leanX = Math.cos(angle) * strength;
            leanZ = Math.sin(angle) * strength;
        }

        int topX = x + (int) Math.round(leanX * height);
        int topZ = z + (int) Math.round(leanZ * height);
        int topY = groundY + height;

        if (!dead) {
            buildCrown(species, random, topX, topY, topZ, height, giant, writer);
        }
        buildBranches(species, random, x, groundY, z, height, leanX, leanZ, giant, dead, writer);
        buildTrunk(species, x, groundY, z, height, trunkRadius, leanX, leanZ, writer);
        buildRoots(species, random, x, groundY, z, trunkRadius, giant, writer);
    }

    private static void buildTrunk(TreeSpecies species, int x, int groundY, int z, int height,
                                   int radius, double leanX, double leanZ, RegionWriter writer) {
        for (int i = 0; i <= height; i++) {
            int y = groundY + i;
            int cx = x + (int) Math.round(leanX * i);
            int cz = z + (int) Math.round(leanZ * i);
            // Trunks taper: the widest part is the base, the crown sits on a slimmer stem.
            double taper = 1.0 - 0.55 * (i / (double) height);
            int r = (int) Math.round((radius - 1) * taper);
            int block = radius > 1 ? species.wood : species.log;
            for (int ox = -r; ox <= r; ox++) {
                for (int oz = -r; oz <= r; oz++) {
                    if (ox * ox + oz * oz <= r * r + r) {
                        writer.set(cx + ox, y, cz + oz, block);
                    }
                }
            }
            if (r == 0) {
                writer.set(cx, y, cz, species.log);
            }
        }
    }

    private static void buildRoots(TreeSpecies species, FastRandom random, int x, int groundY, int z,
                                   int radius, boolean giant, RegionWriter writer) {
        int rootCount = giant ? random.nextInt(5, 8) : random.nextInt(2, 5);
        int reach = giant ? random.nextInt(3, 6) : random.nextInt(1, 3);
        boolean stilts = species.canopy == TreeSpecies.Canopy.MANGROVE;

        for (int i = 0; i < rootCount; i++) {
            double angle = (i / (double) rootCount) * Math.PI * 2.0 + random.nextDouble(-0.3, 0.3);
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            int startY = stilts ? groundY + random.nextInt(2, 4) : groundY;
            for (int step = 1; step <= reach + (stilts ? 2 : 0); step++) {
                int rx = x + (int) Math.round(dx * step);
                int rz = z + (int) Math.round(dz * step);
                int ry = startY - (stilts ? 0 : Math.max(0, step - 1) / 2);
                if (stilts) {
                    // Mangroves walk down to the ground on stilt roots.
                    for (int y = startY; y >= groundY - 1; y--) {
                        writer.set(rx, y, rz, species.log);
                    }
                } else {
                    writer.set(rx, ry, rz, radius > 1 ? species.wood : species.log);
                    if (giant && step <= 2) {
                        writer.set(rx, ry + 1, rz, species.wood);
                    }
                }
            }
        }
    }

    private static void buildBranches(TreeSpecies species, FastRandom random, int x, int groundY, int z,
                                      int height, double leanX, double leanZ, boolean giant, boolean dead,
                                      RegionWriter writer) {
        double branchiness = species.branchiness * (giant ? 1.4 : 1.0);
        if (branchiness <= 0.05) {
            return;
        }
        int branchCount = (int) Math.round(branchiness * (giant ? 9 : 5)) + random.nextInt(0, 2);
        int lowest = Math.max(2, (int) (height * (species.canopy == TreeSpecies.Canopy.UMBRELLA ? 0.55 : 0.45)));

        for (int i = 0; i < branchCount; i++) {
            int startOffset = random.nextInt(lowest, Math.max(lowest, height - 1));
            int startY = groundY + startOffset;
            int startX = x + (int) Math.round(leanX * startOffset);
            int startZ = z + (int) Math.round(leanZ * startOffset);

            double angle = random.nextDouble() * Math.PI * 2.0;
            double rise = switch (species.canopy) {
                case UMBRELLA -> random.nextDouble(0.25, 0.55);
                case CONIFER -> random.nextDouble(-0.35, 0.1);
                default -> random.nextDouble(0.35, 0.85);
            };
            int length = random.nextInt(3, Math.max(4, (int) (species.maxRadius() * (giant ? 1.1 : 0.8))));

            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            int endX = startX;
            int endY = startY;
            int endZ = startZ;
            for (int step = 1; step <= length; step++) {
                endX = startX + (int) Math.round(dx * step);
                endZ = startZ + (int) Math.round(dz * step);
                endY = startY + (int) Math.round(rise * step);
                if (endY >= writer.maxY() - 1) {
                    break;
                }
                writer.set(endX, endY, endZ, orientedLog(species, dx, rise, dz));
            }
            if (!dead) {
                leafBlob(species, random, endX, endY, endZ, giant ? 3 : 2, writer);
            }
        }
    }

    private static void buildCrown(TreeSpecies species, FastRandom random, int x, int y, int z,
                                   int height, boolean giant, RegionWriter writer) {
        switch (species.canopy) {
            case CONIFER -> {
                int layers = Math.max(4, height / 2);
                int maxRadius = (giant ? 4 : 3) + (species == TreeSpecies.GIANT_SPRUCE ? 1 : 0);
                for (int i = 0; i < layers; i++) {
                    int ly = y - i;
                    // Conifer skirts: radius grows downwards, with a small wobble per layer.
                    double t = i / (double) layers;
                    int radius = (int) Math.round(MathUtil.lerp(t, 0.6, maxRadius));
                    if (i % 3 == 2) {
                        radius = Math.max(0, radius - 1);
                    }
                    disc(species, random, x, ly, z, radius, 0.82, writer);
                }
                writer.set(x, y + 1, z, species.leaves);
            }
            case COLUMN -> {
                int layers = Math.max(3, height / 3);
                for (int i = 0; i < layers; i++) {
                    int radius = i == 0 || i == layers - 1 ? 1 : 2;
                    disc(species, random, x, y - i, z, radius, 0.88, writer);
                }
            }
            case UMBRELLA -> {
                int radius = giant ? 7 : 5;
                disc(species, random, x, y, z, radius - 1, 0.9, writer);
                disc(species, random, x, y - 1, z, radius, 0.85, writer);
                disc(species, random, x, y - 2, z, radius - 2, 0.6, writer);
            }
            case MANGROVE -> {
                disc(species, random, x, y, z, 3, 0.85, writer);
                disc(species, random, x, y - 1, z, 4, 0.75, writer);
                disc(species, random, x, y + 1, z, 2, 0.8, writer);
            }
            case BRANCHING -> {
                int radius = giant ? 5 : 4;
                disc(species, random, x, y + 1, z, radius - 2, 0.75, writer);
                disc(species, random, x, y, z, radius, 0.85, writer);
                disc(species, random, x, y - 1, z, radius, 0.8, writer);
                disc(species, random, x, y - 2, z, radius - 1, 0.65, writer);
            }
            case ROUND -> {
                int radius = giant ? 4 : 3;
                for (int i = -2; i <= 1; i++) {
                    int r = radius - Math.abs(i);
                    disc(species, random, x, y + i, z, r, 0.85, writer);
                }
            }
            case NONE -> {
                // Dead wood keeps its bare silhouette.
            }
        }
    }

    private static void leafBlob(TreeSpecies species, FastRandom random, int x, int y, int z,
                                 int radius, RegionWriter writer) {
        for (int oy = -radius; oy <= radius; oy++) {
            int r = radius - Math.abs(oy) / 2;
            disc(species, random, x, y + oy, z, r, 0.72, writer);
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
