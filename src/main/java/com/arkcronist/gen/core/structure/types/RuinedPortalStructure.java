package com.arkcronist.gen.core.structure.types;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.structure.*;

/** A collapsed portal: broken obsidian frame, netherrack scorch, gold blocks and a loot chest. */
public final class RuinedPortalStructure implements Structure {

    @Override
    public String id() {
        return "ruined_portal";
    }

    @Override
    public StructureTag tag() {
        return StructureTag.RUINED_PORTAL;
    }

    @Override
    public int radius() {
        return 12;
    }

    @Override
    public double weight() {
        return 1.4;
    }

    @Override
    public boolean canPlace(StructureContext context) {
        return context.relief(context.originX, context.originZ, 6) < 14;
    }

    @Override
    public void build(StructureContext context, StructureBuffer buffer) {
        FastRandom random = context.random;
        int x = context.originX;
        int z = context.originZ;
        int base = context.averageHeight(5) + 1;
        boolean underground = random.chance(0.25);
        if (underground) {
            base = Math.max(context.engine().settings().minY + 12, base - random.nextInt(14, 34));
            BuildKit.box(buffer, x - 6, base - 1, z - 6, x + 6, base + 8, z + 6, Blocks.AIR);
        }

        int width = random.nextInt(3, 5);
        int height = random.nextInt(4, 6);
        double decay = random.nextDouble(0.25, 0.55);

        // Frame, eaten away.
        for (int i = -1; i <= width; i++) {
            if (!random.chance(decay)) {
                buffer.set(x + i, base, z, Blocks.OBSIDIAN);
            }
            if (!random.chance(decay)) {
                buffer.set(x + i, base + height, z, Blocks.OBSIDIAN);
            }
        }
        for (int j = 0; j <= height; j++) {
            if (!random.chance(decay)) {
                buffer.set(x - 1, base + j, z, random.chance(0.2) ? Blocks.CRYING_OBSIDIAN : Blocks.OBSIDIAN);
            }
            if (!random.chance(decay)) {
                buffer.set(x + width, base + j, z, random.chance(0.2) ? Blocks.CRYING_OBSIDIAN : Blocks.OBSIDIAN);
            }
        }

        // Scorched ground and rubble.
        for (int i = 0; i < 40; i++) {
            int px = x + random.nextInt(-5, 5);
            int pz = z + random.nextInt(-4, 4);
            int py = underground ? base - 1 : context.height(px, pz);
            double roll = random.nextDouble();
            int block = roll < 0.5 ? Blocks.NETHERRACK
                    : roll < 0.7 ? Blocks.MAGMA_BLOCK
                    : roll < 0.85 ? Blocks.BLACKSTONE : Blocks.GILDED_BLACKSTONE;
            buffer.set(px, py, pz, block);
            if (random.chance(0.15)) {
                buffer.set(px, py + 1, pz, Blocks.GOLD_BLOCK);
            }
        }
        BuildKit.chest(buffer, x + width + 2, base, z + 2, 3, "portal");
        if (random.chance(0.4)) {
            buffer.set(x + 1, base + 1, z, Blocks.SOUL_FIRE);
        }
    }
}
