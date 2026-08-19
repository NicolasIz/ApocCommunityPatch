package com.arkcronist.gen.core.structure.types;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.BlockShapes;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.structure.*;

/** A snow dome with a furnished interior and, often, a ladder down to a hidden basement. */
public final class IglooStructure implements Structure {

    @Override
    public String id() {
        return "igloo";
    }

    @Override
    public StructureTag tag() {
        return StructureTag.IGLOO;
    }

    @Override
    public int radius() {
        return 10;
    }

    @Override
    public double weight() {
        return 2.2;
    }

    @Override
    public boolean canPlace(StructureContext context) {
        return !context.submerged(context.originX, context.originZ)
                && context.relief(context.originX, context.originZ, 5) < 6;
    }

    @Override
    public void build(StructureContext context, StructureBuffer buffer) {
        FastRandom random = context.random;
        int x = context.originX;
        int z = context.originZ;
        int base = context.averageHeight(4) + 1;
        int radius = 4;

        BuildKit.clear(buffer, x - radius - 1, base, z - radius - 1, x + radius + 1, base + radius + 2,
                z + radius + 1);
        BuildKit.dome(buffer, x, base, z, radius, Blocks.SNOW_BLOCK, true);
        BuildKit.cylinder(buffer, x, base - 1, z, radius, 1, Blocks.SNOW_BLOCK, false);
        BuildKit.cylinder(buffer, x, base, z, radius - 1, radius - 1, Blocks.AIR, false);

        // Entrance tunnel.
        for (int i = 0; i <= 2; i++) {
            BuildKit.box(buffer, x - 1, base, z - radius - i, x + 1, base + 2, z - radius - i,
                    Blocks.SNOW_BLOCK);
            BuildKit.box(buffer, x, base, z - radius - i, x, base + 1, z - radius - i, Blocks.AIR);
        }
        buffer.set(x, base, z - radius, BlockShapes.door("spruce", 2, false, false));
        buffer.set(x, base + 1, z - radius, BlockShapes.door("spruce", 2, true, false));

        // Interior.
        buffer.set(x - 2, base, z + 1, BlockShapes.bed(0, false));
        buffer.set(x - 2, base, z, BlockShapes.bed(0, true));
        buffer.set(x + 2, base, z + 1, BlockShapes.furnace(3));
        buffer.set(x + 2, base, z, Blocks.CRAFTING_TABLE);
        buffer.set(x, base + radius - 2, z, Blocks.LANTERN);
        buffer.set(x, base, z + 2, Blocks.RED_CARPET);
        BuildKit.chest(buffer, x - 1, base, z + 2, 1, "igloo");

        if (random.chance(0.55)) {
            // Basement laboratory: the reason to break the floor.
            int labY = base - random.nextInt(6, 10);
            BuildKit.hollow(buffer, x - 4, labY, z - 4, x + 4, labY + 4, z + 4, Blocks.STONE_BRICKS,
                    Blocks.AIR);
            BuildKit.box(buffer, x - 3, labY, z - 3, x + 3, labY, z + 3, Blocks.STONE_BRICKS);
            for (int y = labY + 5; y < base; y++) {
                buffer.set(x + 1, y, z + 1, Blocks.AIR);
                buffer.set(x + 1, y, z + 2, BlockShapes.ladder(0));
            }
            buffer.set(x + 1, base - 1, z + 1, Blocks.AIR);
            buffer.set(x - 2, labY + 1, z - 2, Blocks.BREWING_STAND);
            buffer.set(x - 2, labY + 1, z - 1, Blocks.CAULDRON);
            buffer.set(x + 2, labY + 1, z + 2, Blocks.IRON_BARS);
            BuildKit.chest(buffer, x + 2, labY + 1, z - 2, 2, "igloo");
            buffer.set(x, labY + 3, z, Blocks.LANTERN);
            buffer.addSpawn(MobSpawn.mob(x, labY + 1, z, "VILLAGER", 0));
            buffer.addSpawn(MobSpawn.boss(x + 1, labY + 1, z, "WITCH", 3, "camp_chief"));
        } else {
            buffer.addSpawn(MobSpawn.mob(x, base, z, "VILLAGER", 0));
        }
    }
}
