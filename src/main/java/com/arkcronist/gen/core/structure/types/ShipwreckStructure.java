package com.arkcronist.gen.core.structure.types;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.BlockShapes;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.structure.*;

/** A wrecked ship, listing on the sea floor or half buried in a beach, with three cargo chests. */
public final class ShipwreckStructure implements Structure {

    @Override
    public String id() {
        return "shipwreck";
    }

    @Override
    public StructureTag tag() {
        return StructureTag.SHIPWRECK;
    }

    @Override
    public int radius() {
        return 20;
    }

    @Override
    public double weight() {
        return 1.8;
    }

    @Override
    public boolean canPlace(StructureContext context) {
        return context.relief(context.originX, context.originZ, 8) < 16;
    }

    @Override
    public void build(StructureContext context, StructureBuffer buffer) {
        FastRandom random = context.random;
        int x = context.originX;
        int z = context.originZ;
        int floor = context.groundY;
        boolean sunken = context.submerged(x, z);
        int length = random.nextInt(12, 20);
        int width = 3;
        int tilt = random.nextInt(-1, 1);
        int plank = random.chance(0.5) ? Blocks.OAK_PLANKS : Blocks.SPRUCE_PLANKS;
        String woodFamily = plank == Blocks.OAK_PLANKS ? "oak" : "spruce";
        boolean broken = random.chance(0.6);

        for (int i = 0; i < length; i++) {
            int y = floor + 1 + (i * tilt) / Math.max(1, length / 3);
            int taper = i < 3 ? i : (i > length - 4 ? length - 1 - i : width);
            int halfWidth = Math.min(width, Math.max(1, taper));
            if (broken && i > length * 2 / 3 && random.chance(0.55)) {
                continue;
            }

            // Hull ribs and planking.
            for (int ox = -halfWidth; ox <= halfWidth; ox++) {
                buffer.set(x + ox, y, z - length / 2 + i, plank);
                buffer.set(x + ox, y + 3, z - length / 2 + i, i % 4 == 0 ? plank : Blocks.AIR);
            }
            buffer.set(x - halfWidth, y + 1, z - length / 2 + i, plank);
            buffer.set(x + halfWidth, y + 1, z - length / 2 + i, plank);
            buffer.set(x - halfWidth, y + 2, z - length / 2 + i, i % 3 == 0 ? Blocks.OAK_FENCE : plank);
            buffer.set(x + halfWidth, y + 2, z - length / 2 + i, i % 3 == 0 ? Blocks.OAK_FENCE : plank);
            for (int ox = -halfWidth + 1; ox <= halfWidth - 1; ox++) {
                buffer.set(x + ox, y + 1, z - length / 2 + i, sunken ? Blocks.WATER : Blocks.AIR);
                buffer.set(x + ox, y + 2, z - length / 2 + i, sunken ? Blocks.WATER : Blocks.AIR);
            }
        }

        // Mast and rigging.
        int mastZ = z - length / 2 + length / 3;
        for (int i = 1; i <= 8; i++) {
            buffer.set(x, floor + 3 + i, mastZ, Blocks.OAK_LOG);
        }
        for (int ox = -2; ox <= 2; ox++) {
            buffer.set(x + ox, floor + 9, mastZ, Blocks.OAK_FENCE);
        }

        // Cargo: the three chests a wreck is worth diving for.
        BuildKit.chest(buffer, x, floor + 2, z - length / 2 + 2, 2, "shipwreck");
        BuildKit.chest(buffer, x - 1, floor + 2, z + length / 2 - 3, 3, "shipwreck");
        BuildKit.chest(buffer, x + 1, floor + 2, z, 2, "shipwreck");
        buffer.set(x, floor + 2, z + 1, Blocks.BARREL_UP);
        // Always a light aboard: a deck lantern above water, a sea lantern in the hold below it.
        buffer.set(x + 1, floor + 4, mastZ, sunken ? Blocks.SEA_LANTERN : Blocks.LANTERN);
        buffer.set(x, floor + 1, z - length / 2 + 3, sunken ? Blocks.SEA_LANTERN : Blocks.LANTERN);
        if (sunken) {
            buffer.addSpawn(MobSpawn.mob(x, floor + 2, z, "DROWNED", 2));
            buffer.addSpawn(MobSpawn.mob(x + 1, floor + 2, z + 2, "DROWNED", 2));
        }
    }
}
