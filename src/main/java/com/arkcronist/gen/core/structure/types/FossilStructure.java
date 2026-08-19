package com.arkcronist.gen.core.structure.types;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.structure.*;

/** A fossil: a bone block ribcage or spine buried in the rock, with a little coal in the marrow. */
public final class FossilStructure implements Structure {

    @Override
    public Placement placement() {
        return Placement.UNDERGROUND;
    }

    @Override
    public String id() {
        return "fossil";
    }

    @Override
    public StructureTag tag() {
        return StructureTag.FOSSIL;
    }

    @Override
    public int radius() {
        return 10;
    }

    @Override
    public double weight() {
        return 1.4;
    }

    @Override
    public boolean canPlace(StructureContext context) {
        return context.groundY > context.engine().settings().minY + 25;
    }

    @Override
    public void build(StructureContext context, StructureBuffer buffer) {
        FastRandom random = context.random;
        int x = context.originX;
        int z = context.originZ;
        int minY = context.engine().settings().minY;
        int y = random.nextInt(minY + 10, Math.max(minY + 11, Math.min(context.groundY - 8, 40)));
        boolean spine = random.chance(0.5);
        int length = random.nextInt(8, 14);

        if (spine) {
            for (int i = 0; i < length; i++) {
                buffer.set(x + i, y, z, decay(random));
                if (i % 3 == 0) {
                    for (int rib = 1; rib <= random.nextInt(2, 5); rib++) {
                        buffer.set(x + i, y + rib, z - rib, decay(random));
                        buffer.set(x + i, y + rib, z + rib, decay(random));
                    }
                }
            }
        } else {
            // Ribcage: two arcs meeting over a spine.
            for (int i = 0; i < length; i++) {
                for (int rib = 0; rib <= 4; rib++) {
                    int height = (int) Math.round(Math.sqrt(Math.max(0, 16 - rib * rib)));
                    buffer.set(x + i, y + height, z - rib, decay(random));
                    buffer.set(x + i, y + height, z + rib, decay(random));
                }
                buffer.set(x + i, y, z, decay(random));
            }
        }
    }

    private int decay(FastRandom random) {
        if (random.chance(0.12)) {
            return Blocks.COAL_ORE;
        }
        return random.chance(0.15) ? Blocks.AIR : Blocks.BONE_BLOCK;
    }
}
