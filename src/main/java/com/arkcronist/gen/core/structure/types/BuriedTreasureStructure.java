package com.arkcronist.gen.core.structure.types;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.structure.*;

/** A treasure chest buried under a beach, marked only by suspicious sand at the surface. */
public final class BuriedTreasureStructure implements Structure {

    @Override
    public String id() {
        return "buried_treasure";
    }

    @Override
    public StructureTag tag() {
        return StructureTag.TREASURE;
    }

    @Override
    public int radius() {
        return 6;
    }

    @Override
    public double weight() {
        return 2.4;
    }

    @Override
    public boolean canPlace(StructureContext context) {
        return context.groundY > context.seaLevel() - 12;
    }

    @Override
    public void build(StructureContext context, StructureBuffer buffer) {
        FastRandom random = context.random;
        int x = context.originX;
        int z = context.originZ;
        int ground = context.groundY;
        int depth = random.nextInt(3, 7);
        int y = ground - depth;

        BuildKit.box(buffer, x - 1, y - 1, z - 1, x + 1, y + 1, z + 1, Blocks.SAND);
        BuildKit.chest(buffer, x, y, z, 3, "treasure");
        // A tell at the surface for anyone with a brush.
        buffer.set(x, ground, z, Blocks.SUSPICIOUS_SAND);
        if (random.chance(0.5)) {
            buffer.set(x + 1, ground, z, Blocks.SUSPICIOUS_GRAVEL);
        }
        for (int i = 0; i < 3; i++) {
            buffer.set(x + random.nextInt(-2, 2), y + random.nextInt(-1, 1),
                    z + random.nextInt(-2, 2), Blocks.BONE_BLOCK);
        }
    }
}
