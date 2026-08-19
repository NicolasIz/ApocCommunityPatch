package com.arkcronist.gen.core.structure.types;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.structure.*;

/**
 * Collapsed architecture: broken walls, toppled columns and a half buried cellar.
 *
 * <p>Ruins are built by drawing an intact building and then removing material with a height biased
 * decay - the higher the block, the more likely it is gone - which reads as a structure that fell
 * down rather than one that was drawn with holes.</p>
 */
public final class RuinsStructure implements Structure {

    @Override
    public String id() {
        return "ruins";
    }

    @Override
    public StructureTag tag() {
        return StructureTag.RUINS;
    }

    @Override
    public int radius() {
        return 16;
    }

    @Override
    public double weight() {
        return 2.0;
    }

    @Override
    public boolean canPlace(StructureContext context) {
        return context.relief(context.originX, context.originZ, 8) < 16;
    }

    @Override
    public void build(StructureContext context, StructureBuffer buffer) {
        FastRandom random = context.random;
        StructureMaterials materials = StructureMaterials.forBiome(context.biome, random);
        int x = context.originX;
        int z = context.originZ;
        int base = context.averageHeight(8);
        boolean sunken = context.submerged(x, z);

        int halfX = random.nextInt(5, 9);
        int halfZ = random.nextInt(5, 9);
        int height = random.nextInt(4, 8);

        BuildKit.foundationArea(context, buffer, x - halfX, z - halfZ, x + halfX, z + halfZ, base,
                materials.wallAccent, 8);
        BuildKit.box(buffer, x - halfX, base, z - halfZ, x + halfX, base, z + halfZ, materials.floor);

        // Walls, eaten away more the higher they go.
        for (int y = base + 1; y <= base + height; y++) {
            double decay = 0.12 + 0.75 * (y - base) / (double) height;
            for (int ox = -halfX; ox <= halfX; ox++) {
                if (!random.chance(decay)) {
                    buffer.set(x + ox, y, z - halfZ, materials.wall);
                }
                if (!random.chance(decay)) {
                    buffer.set(x + ox, y, z + halfZ, materials.wall);
                }
            }
            for (int oz = -halfZ; oz <= halfZ; oz++) {
                if (!random.chance(decay)) {
                    buffer.set(x - halfX, y, z + oz, materials.wall);
                }
                if (!random.chance(decay)) {
                    buffer.set(x + halfX, y, z + oz, materials.wall);
                }
            }
        }

        // Columns: a few still standing, the rest lying where they fell.
        int columns = random.nextInt(3, 7);
        for (int i = 0; i < columns; i++) {
            int cx = x + random.nextInt(-halfX + 1, halfX - 1);
            int cz = z + random.nextInt(-halfZ + 1, halfZ - 1);
            if (random.chance(0.55)) {
                int columnHeight = random.nextInt(2, height + 1);
                for (int y = 0; y < columnHeight; y++) {
                    buffer.set(cx, base + 1 + y, cz, materials.pillar);
                }
            } else {
                int length = random.nextInt(2, 5);
                boolean alongX = random.nextBoolean();
                for (int step = 0; step < length; step++) {
                    buffer.set(cx + (alongX ? step : 0), base + 1, cz + (alongX ? 0 : step), materials.pillar);
                }
            }
        }

        // Cellar: the reason to dig here at all.
        if (random.chance(0.6)) {
            int cellarY = base - random.nextInt(3, 6);
            BuildKit.hollow(buffer, x - 3, cellarY, z - 3, x + 3, cellarY + 3, z + 3, materials.wall, Blocks.AIR);
            BuildKit.box(buffer, x - 2, cellarY, z - 2, x + 2, cellarY, z + 2, materials.floor);
            BuildKit.ladder(buffer, x, cellarY + 1, base, z + 2, Blocks.LADDER_NORTH);
            buffer.set(x, base, z + 2, Blocks.AIR);
            BuildKit.chest(buffer, x - 2, cellarY + 1, z - 2, 2, "ruins");
            buffer.addSpawn(MobSpawn.mob(x, cellarY + 1, z, "ZOMBIE", 2));
            if (random.chance(0.35)) {
                buffer.addSpawn(MobSpawn.boss(x + 1, cellarY + 1, z, "WITHER_SKELETON", 3, "ruin_wraith"));
            }
        }

        // A ruin still needs something to find it by at night: a fallen brazier by the columns.
        if (!sunken) {
            int fireX = x + random.nextInt(-halfX + 1, halfX - 1);
            int fireZ = z + random.nextInt(-halfZ + 1, halfZ - 1);
            buffer.set(fireX, base + 1, fireZ, Blocks.CAMPFIRE);
            buffer.set(x - halfX + 1, base + 2, z, materials.light);
        } else {
            buffer.set(x, base + 1, z, Blocks.SEA_LANTERN);
        }

        int rubbleBlock = sunken ? Blocks.GRAVEL : materials.wallAccent;
        BuildKit.rubble(context, buffer, random, x, z, Math.max(halfX, halfZ) + 4, rubbleBlock, 0.22);
        if (!sunken && random.chance(0.5)) {
            BuildKit.chest(buffer, x + halfX - 1, base + 1, z + halfZ - 1, 1, "ruins");
        }
    }
}
