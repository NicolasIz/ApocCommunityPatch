package com.arkcronist.gen.core.structure.types;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.structure.*;

/** A small bandit camp: tents, a campfire, a supply chest and a handful of occupants. */
public final class CampStructure implements Structure {

    @Override
    public String id() {
        return "camp";
    }

    @Override
    public StructureTag tag() {
        return StructureTag.CAMP;
    }

    @Override
    public int radius() {
        return 12;
    }

    @Override
    public double weight() {
        return 1.8;
    }

    @Override
    public boolean canPlace(StructureContext context) {
        return !context.submerged(context.originX, context.originZ)
                && context.relief(context.originX, context.originZ, 6) < 8;
    }

    @Override
    public void build(StructureContext context, StructureBuffer buffer) {
        FastRandom random = context.random;
        StructureMaterials materials = StructureMaterials.forBiome(context.biome, random);
        int x = context.originX;
        int z = context.originZ;
        int base = context.averageHeight(5) + 1;

        // Fire pit at the centre with log seats around it.
        buffer.set(x, base, z, Blocks.CAMPFIRE);
        for (int i = 0; i < 4; i++) {
            int ox = i == 0 ? -2 : i == 1 ? 2 : 0;
            int oz = i == 2 ? -2 : i == 3 ? 2 : 0;
            buffer.set(x + ox, base, z + oz, Blocks.OAK_LOG_X);
        }

        int tents = random.nextInt(2, 5);
        for (int tent = 0; tent < tents; tent++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            int tx = x + (int) Math.round(Math.cos(angle) * random.nextInt(4, 8));
            int tz = z + (int) Math.round(Math.sin(angle) * random.nextInt(4, 8));
            int ty = context.height(tx, tz) + 1;
            buildTent(buffer, random, materials, tx, ty, tz);
            buffer.addSpawn(MobSpawn.mob(tx, ty + 1, tz, occupant(random), 1));
        }

        BuildKit.chest(buffer, x + 1, base, z + 1, 1, "camp");
        if (random.chance(0.45)) {
            buffer.addSpawn(MobSpawn.boss(x, base + 1, z, "PILLAGER", 2, "camp_chief"));
        }
        BuildKit.rubble(context, buffer, random, x, z, 8, Blocks.COBBLESTONE, 0.10);
    }

    private void buildTent(StructureBuffer buffer, FastRandom random, StructureMaterials materials,
                           int x, int y, int z) {
        int length = random.nextInt(2, 4);
        BuildKit.clear(buffer, x - 2, y, z - length, x + 2, y + 3, z + length);
        for (int i = -length; i <= length; i++) {
            buffer.set(x - 2, y, z + i, materials.fence);
            buffer.set(x + 2, y, z + i, materials.fence);
            buffer.set(x - 1, y + 1, z + i, Blocks.WHITE_TERRACOTTA);
            buffer.set(x + 1, y + 1, z + i, Blocks.WHITE_TERRACOTTA);
            buffer.set(x, y + 2, z + i, Blocks.BROWN_TERRACOTTA);
        }
        buffer.set(x, y, z, Blocks.HAY_BLOCK);
    }

    private static String occupant(FastRandom random) {
        return switch (random.nextInt(4)) {
            case 0 -> "PILLAGER";
            case 1 -> "ZOMBIE";
            case 2 -> "SKELETON";
            default -> "VINDICATOR";
        };
    }
}
