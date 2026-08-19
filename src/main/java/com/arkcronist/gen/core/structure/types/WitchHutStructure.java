package com.arkcronist.gen.core.structure.types;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.BlockShapes;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.structure.*;

/** A swamp hut on stilts, with its cauldron, its cat and its witch. */
public final class WitchHutStructure implements Structure {

    @Override
    public String id() {
        return "witch_hut";
    }

    @Override
    public StructureTag tag() {
        return StructureTag.WITCH_HUT;
    }

    @Override
    public int radius() {
        return 12;
    }

    @Override
    public double weight() {
        return 2.4;
    }

    @Override
    public boolean canPlace(StructureContext context) {
        return context.relief(context.originX, context.originZ, 6) < 8;
    }

    @Override
    public void build(StructureContext context, StructureBuffer buffer) {
        FastRandom random = context.random;
        int x = context.originX;
        int z = context.originZ;
        int water = context.waterY;
        int ground = context.groundY;
        int floor = Math.max(water + 2, ground + 2);
        int half = 3;

        // Stilts down to whatever is below, water or mud.
        for (int corner = 0; corner < 4; corner++) {
            int cx = (corner & 1) == 0 ? x - half : x + half;
            int cz = (corner & 2) == 0 ? z - half : z + half;
            for (int y = floor - 1; y >= ground; y--) {
                buffer.set(cx, y, cz, Blocks.SPRUCE_LOG);
            }
        }

        BuildKit.clear(buffer, x - half - 1, floor, z - half - 1, x + half + 1, floor + 8, z + half + 1);
        BuildKit.box(buffer, x - half, floor, z - half, x + half, floor, z + half, Blocks.SPRUCE_PLANKS);
        BuildKit.walls(buffer, x - half, floor + 1, z - half, x + half, floor + 3, z + half,
                Blocks.SPRUCE_PLANKS);
        for (int corner = 0; corner < 4; corner++) {
            int cx = (corner & 1) == 0 ? x - half : x + half;
            int cz = (corner & 2) == 0 ? z - half : z + half;
            for (int i = 1; i <= 3; i++) {
                buffer.set(cx, floor + i, cz, Blocks.SPRUCE_LOG);
            }
        }
        BuildKit.stairRoof(buffer, x - half, floor + 4, z - half, x + half, z + half, "spruce",
                Blocks.SPRUCE_PLANKS);

        // Interior: cauldron, brewing stand, table, and a window with no glass.
        buffer.set(x, floor + 1, z - half, Blocks.AIR);
        buffer.set(x - 1, floor + 1, z + half, Blocks.AIR);
        buffer.set(x - 2, floor + 1, z + 2, Blocks.CAULDRON);
        buffer.set(x + 2, floor + 1, z + 2, Blocks.BREWING_STAND);
        buffer.set(x + 2, floor + 1, z - 2, Blocks.CRAFTING_TABLE);
        buffer.set(x - 2, floor + 1, z - 2, Blocks.RED_MUSHROOM);
        BuildKit.hangingLantern(buffer, x, floor + 4, z, Blocks.SPRUCE_PLANKS);
        BuildKit.chest(buffer, x - 2, floor + 1, z, 2, "witch");

        // Ladder down to the water.
        buffer.set(x + half, floor + 1, z, BlockShapes.ladder(1));
        for (int y = ground; y <= floor; y++) {
            buffer.set(x + half + 1, y, z, BlockShapes.ladder(1));
        }

        buffer.addSpawn(MobSpawn.boss(x, floor + 1, z, "WITCH", 3, "witch_matron"));
        buffer.addSpawn(MobSpawn.mob(x + 1, floor + 1, z + 1, "CAT", 0));
    }
}
