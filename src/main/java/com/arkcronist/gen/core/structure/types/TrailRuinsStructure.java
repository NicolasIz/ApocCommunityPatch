package com.arkcronist.gen.core.structure.types;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.structure.*;

/** Trail ruins: a buried brick street plan with suspicious gravel and decorated pots. */
public final class TrailRuinsStructure implements Structure {

    @Override
    public String id() {
        return "trail_ruins";
    }

    @Override
    public StructureTag tag() {
        return StructureTag.TRAIL_RUINS;
    }

    @Override
    public int radius() {
        return 20;
    }

    @Override
    public double weight() {
        return 1.2;
    }

    @Override
    public boolean canPlace(StructureContext context) {
        return !context.submerged(context.originX, context.originZ)
                && context.relief(context.originX, context.originZ, 8) < 10;
    }

    @Override
    public void build(StructureContext context, StructureBuffer buffer) {
        FastRandom random = context.random;
        int x = context.originX;
        int z = context.originZ;

        // Buried street grid: only the tops of the walls break the surface.
        for (int ox = -14; ox <= 14; ox++) {
            for (int oz = -14; oz <= 14; oz++) {
                boolean street = (ox % 7 == 0) || (oz % 7 == 0);
                if (!street) {
                    continue;
                }
                int px = x + ox;
                int pz = z + oz;
                int ground = context.height(px, pz);
                int depth = random.nextInt(1, 4);
                for (int d = 0; d < depth; d++) {
                    double roll = random.nextDouble();
                    int block = roll < 0.45 ? Blocks.BRICKS
                            : roll < 0.7 ? Blocks.MUD_BRICKS
                            : roll < 0.85 ? Blocks.PACKED_MUD : Blocks.GRAVEL;
                    buffer.set(px, ground - d, pz, block);
                }
                if (random.chance(0.05)) {
                    buffer.set(px, ground - depth, pz, Blocks.SUSPICIOUS_GRAVEL);
                }
                if (random.chance(0.02)) {
                    buffer.set(px, ground + 1, pz, Blocks.DECORATED_POT);
                }
            }
        }

        // A buried cellar with the real find.
        int cellarY = context.groundY - random.nextInt(4, 8);
        BuildKit.hollow(buffer, x - 3, cellarY, z - 3, x + 3, cellarY + 3, z + 3, Blocks.MUD_BRICKS,
                Blocks.AIR);
        BuildKit.chest(buffer, x, cellarY + 1, z, 3, "trail");
        buffer.set(x + 2, cellarY + 1, z + 2, Blocks.DECORATED_POT);
        buffer.set(x - 2, cellarY + 1, z - 2, Blocks.SUSPICIOUS_SAND);
        buffer.set(x, cellarY + 3, z, Blocks.LANTERN);
    }
}
