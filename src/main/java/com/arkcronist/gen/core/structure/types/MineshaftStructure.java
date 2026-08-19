package com.arkcronist.gen.core.structure.types;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.BlockShapes;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.structure.*;

/**
 * An abandoned mine.
 *
 * <p>Branching corridors on two levels, timber supports every few blocks, rails down the middle,
 * cobwebs and broken supports where the roof gave way, storage chests, a flooded section, a cave
 * spider nest, and vertical shafts with ladders connecting the levels.</p>
 */
public final class MineshaftStructure implements Structure {

    @Override
    public Placement placement() {
        return Placement.UNDERGROUND;
    }

    @Override
    public String id() {
        return "mineshaft";
    }

    @Override
    public StructureTag tag() {
        return StructureTag.MINESHAFT;
    }

    @Override
    public int radius() {
        return 60;
    }

    @Override
    public double weight() {
        return 1.6;
    }

    @Override
    public boolean canPlace(StructureContext context) {
        return context.groundY > context.engine().settings().minY + 45;
    }

    @Override
    public void build(StructureContext context, StructureBuffer buffer) {
        FastRandom random = context.random;
        int x = context.originX;
        int z = context.originZ;
        int minY = context.engine().settings().minY;
        boolean badlands = context.biome.name.contains("badlands") || context.biome.name.contains("canyon");
        int top = Math.min(context.groundY - 14, badlands ? 60 : 30);
        int mainLevel = Math.max(minY + 12, top - random.nextInt(0, 20));

        int levels = random.nextInt(1, 3);
        for (int level = 0; level < levels; level++) {
            int y = mainLevel - level * random.nextInt(8, 14);
            if (y < minY + 8) {
                break;
            }
            int trunks = random.nextInt(3, 6);
            for (int i = 0; i < trunks; i++) {
                double angle = (i / (double) trunks) * Math.PI * 2.0 + random.nextDouble(-0.3, 0.3);
                corridor(buffer, random, x, y, z, angle, random.nextInt(24, 55), true);
            }
            if (level > 0) {
                shaft(buffer, x + 2, y, mainLevel, z + 2);
            }
        }

        // Cave spider nest: the reason mineshafts are remembered.
        int nestX = x + random.nextInt(-18, 18);
        int nestZ = z + random.nextInt(-18, 18);
        int nestY = mainLevel - random.nextInt(0, 6);
        BuildKit.hollow(buffer, nestX - 4, nestY, nestZ - 4, nestX + 4, nestY + 4, nestZ + 4,
                Blocks.MOSSY_COBBLESTONE, Blocks.AIR);
        for (int i = 0; i < 14; i++) {
            buffer.set(nestX + random.nextInt(-3, 3), nestY + random.nextInt(1, 3),
                    nestZ + random.nextInt(-3, 3), Blocks.COBWEB);
        }
        BuildKit.spawner(buffer, nestX, nestY + 1, nestZ, "CAVE_SPIDER");
        buffer.set(nestX - 3, nestY + 1, nestZ - 3, Blocks.LANTERN);
        BuildKit.chest(buffer, nestX + 3, nestY + 1, nestZ + 3, 2, "mineshaft");
        buffer.addSpawn(MobSpawn.mob(nestX + 1, nestY + 1, nestZ, "CAVE_SPIDER", 2));
    }

    private void corridor(StructureBuffer buffer, FastRandom random, int x, int y, int z,
                          double angle, int length, boolean allowBranch) {
        double dx = Math.cos(angle);
        double dz = Math.sin(angle);
        boolean alongX = Math.abs(dx) > Math.abs(dz);
        int rail = alongX ? Blocks.RAIL_EW : Blocks.RAIL_NS;

        for (int step = 0; step < length; step++) {
            int cx = x + (int) Math.round(dx * step);
            int cz = z + (int) Math.round(dz * step);
            int cy = y + (step % 17 == 16 ? 1 : 0);

            BuildKit.box(buffer, cx - 1, cy, cz - 1, cx + 1, cy + 2, cz + 1, Blocks.AIR);
            BuildKit.box(buffer, cx - 1, cy - 1, cz - 1, cx + 1, cy - 1, cz + 1, Blocks.COBBLESTONE);

            // Timber support frame.
            if (step % 5 == 0) {
                int post = alongX ? Blocks.OAK_FENCE : Blocks.OAK_FENCE;
                buffer.set(cx - 1, cy, cz - 1, post);
                buffer.set(cx - 1, cy + 1, cz - 1, post);
                buffer.set(cx + 1, cy, cz + 1, post);
                buffer.set(cx + 1, cy + 1, cz + 1, post);
                buffer.set(cx - 1, cy + 2, cz - 1, Blocks.OAK_PLANKS);
                buffer.set(cx, cy + 2, cz, Blocks.OAK_PLANKS);
                buffer.set(cx + 1, cy + 2, cz + 1, Blocks.OAK_PLANKS);
                // Every second support frame carries a torch: an unlit mine is just a hole.
                if (step % 10 == 0) {
                    buffer.set(cx - 1, cy + 2, cz, Blocks.LANTERN);
                } else if (random.chance(0.35)) {
                    BuildKit.wallTorch(buffer, cx - 1, cy + 1, cz, 1, 0, false);
                }
            }
            // Rails, sometimes broken.
            if (random.chance(0.85)) {
                buffer.set(cx, cy, cz, rail);
            }
            if (random.chance(0.07)) {
                buffer.set(cx + random.nextInt(-1, 1), cy + random.nextInt(0, 2),
                        cz + random.nextInt(-1, 1), Blocks.COBWEB);
            }
            if (random.chance(0.03)) {
                BuildKit.chest(buffer, cx + 1, cy, cz, 1, "mineshaft");
            }
            if (random.chance(0.02)) {
                buffer.set(cx - 1, cy, cz, Blocks.BARREL_UP);
            }
            // Collapsed section: gravel spill and no rail.
            if (random.chance(0.04)) {
                BuildKit.box(buffer, cx - 1, cy, cz - 1, cx + 1, cy + 1, cz + 1, Blocks.GRAVEL);
            }
            if (allowBranch && step > 8 && random.chance(0.09)) {
                corridor(buffer, random, cx, cy, cz, angle + (random.nextBoolean() ? 1.4 : -1.4),
                        random.nextInt(8, 22), false);
            }
        }
    }

    private void shaft(StructureBuffer buffer, int x, int fromY, int toY, int z) {
        for (int y = fromY; y <= toY; y++) {
            BuildKit.box(buffer, x - 1, y, z - 1, x + 1, y, z + 1, Blocks.AIR);
            buffer.set(x - 1, y, z - 1, Blocks.OAK_PLANKS);
            buffer.set(x + 1, y, z + 1, Blocks.OAK_PLANKS);
            buffer.set(x, y, z + 1, BlockShapes.ladder(2));
        }
        BuildKit.box(buffer, x - 1, toY + 1, z - 1, x + 1, toY + 1, z + 1, Blocks.OAK_PLANKS);
    }
}
