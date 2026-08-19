package com.arkcronist.gen.core.structure.types;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.BlockShapes;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.structure.*;

/** A desert pyramid with the classic buried treasure room, its pressure plate and its TNT. */
public final class DesertPyramidStructure implements Structure {

    @Override
    public String id() {
        return "desert_pyramid";
    }

    @Override
    public StructureTag tag() {
        return StructureTag.PYRAMID;
    }

    @Override
    public int radius() {
        return 22;
    }

    @Override
    public double weight() {
        return 2.2;
    }

    @Override
    public boolean canPlace(StructureContext context) {
        return !context.submerged(context.originX, context.originZ)
                && context.groundY > context.seaLevel()
                && context.relief(context.originX, context.originZ, 10) < 12;
    }

    @Override
    public void build(StructureContext context, StructureBuffer buffer) {
        FastRandom random = context.random;
        int x = context.originX;
        int z = context.originZ;
        int base = context.averageHeight(10);
        int half = random.nextInt(9, 12);
        boolean red = context.biome.name.contains("red") || context.biome.name.contains("badlands");
        int body = red ? Blocks.RED_SANDSTONE : Blocks.SANDSTONE;
        int accent = red ? Blocks.CUT_SANDSTONE : Blocks.CHISELED_SANDSTONE;
        String family = "sandstone";

        BuildKit.foundationArea(context, buffer, x - half, z - half, x + half, z + half, base, body, 20);
        BuildKit.clear(buffer, x - half - 1, base + 1, z - half - 1, x + half + 1, base + half + 6, z + half + 1);

        // Stepped mass.
        for (int layer = 0; layer <= half; layer++) {
            int layerHalf = half - layer;
            if (layerHalf < 0) {
                break;
            }
            for (int ox = -layerHalf; ox <= layerHalf; ox++) {
                for (int oz = -layerHalf; oz <= layerHalf; oz++) {
                    boolean edge = Math.abs(ox) == layerHalf || Math.abs(oz) == layerHalf;
                    buffer.set(x + ox, base + layer, z + oz,
                            edge && layer % 3 == 0 ? accent : body);
                }
            }
        }

        // Twin entrance towers and a doorway.
        for (int side = -1; side <= 1; side += 2) {
            int tx = x + side * (half - 2);
            for (int oy = 0; oy <= half + 3; oy++) {
                BuildKit.box(buffer, tx - 1, base + oy, z - half - 1, tx + 1, base + oy, z - half + 1, body);
            }
            BuildKit.box(buffer, tx - 1, base + half + 4, z - half - 1, tx + 1, base + half + 4,
                    z - half + 1, accent);
        }
        BuildKit.archway(buffer, x, base + 1, z - half, 1, 3, true, family);
        BuildKit.box(buffer, x - 1, base + 1, z - half + 1, x + 1, base + 3, z, Blocks.AIR);

        // Inner chamber, then the trap room below it.
        BuildKit.box(buffer, x - 4, base + 1, z - 4, x + 4, base + 4, z + 4, Blocks.AIR);
        BuildKit.torchRing(buffer, x - 3, base + 3, z - 3, x + 3, z + 3, 3, Blocks.TORCH);
        buffer.set(x, base + 4, z, accent);

        int trapY = base - 6;
        BuildKit.hollow(buffer, x - 4, trapY, z - 4, x + 4, trapY + 4, z + 4, body, Blocks.AIR);
        BuildKit.box(buffer, x - 1, trapY, z - 1, x + 1, trapY, z + 1, Blocks.BLUE_CARPET);
        buffer.set(x, trapY, z, Blocks.STONE_PRESSURE_PLATE);
        BuildKit.box(buffer, x - 1, trapY - 3, z - 1, x + 1, trapY - 1, z + 1, Blocks.TNT);
        for (int corner = 0; corner < 4; corner++) {
            int cx = (corner & 1) == 0 ? x - 3 : x + 3;
            int cz = (corner & 2) == 0 ? z - 3 : z + 3;
            BuildKit.chest(buffer, cx, trapY + 1, cz, 3, "temple");
        }
        // Shaft down from the chamber, hidden under the floor.
        for (int y = trapY + 5; y <= base; y++) {
            BuildKit.box(buffer, x - 1, y, z - 1, x + 1, y, z + 1, Blocks.AIR);
            buffer.set(x + 1, y, z + 1, BlockShapes.ladder(2));
        }
        buffer.addSpawn(MobSpawn.boss(x, trapY + 1, z, "HUSK", 4, "temple_guardian"));
        for (int i = 0; i < 3; i++) {
            buffer.addSpawn(MobSpawn.mob(x + random.nextInt(-3, 3), trapY + 1, z + random.nextInt(-3, 3),
                    "HUSK", 2));
        }
    }
}
