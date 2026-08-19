package com.arkcronist.gen.core.structure.types;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.structure.*;

/**
 * A compact border fortress: thick square walls, a barracks block, an armoury and a marshal.
 * Smaller and denser than a castle, and it happily sits on rougher ground.
 */
public final class FortressStructure implements Structure {

    @Override
    public String id() {
        return "fortress";
    }

    @Override
    public StructureTag tag() {
        return StructureTag.FORTRESS;
    }

    @Override
    public int radius() {
        return 26;
    }

    @Override
    public double weight() {
        return 1.0;
    }

    @Override
    public boolean canPlace(StructureContext context) {
        return !context.submerged(context.originX, context.originZ)
                && context.relief(context.originX, context.originZ, 12) < 16;
    }

    @Override
    public void build(StructureContext context, StructureBuffer buffer) {
        FastRandom random = context.random;
        StructureMaterials materials = StructureMaterials.forBiome(context.biome, random);
        int x = context.originX;
        int z = context.originZ;
        int base = context.averageHeight(10);
        int half = random.nextInt(11, 16);
        int wallHeight = random.nextInt(7, 10);

        for (int ox = -half; ox <= half; ox++) {
            for (int oz = -half; oz <= half; oz++) {
                buffer.set(x + ox, base, z + oz, materials.floor);
                BuildKit.foundation(context, buffer, x + ox, z + oz, base, materials.wall, 20);
                BuildKit.clear(buffer, x + ox, base + 1, z + oz, x + ox, base + wallHeight + 6, z + oz);
            }
        }

        // Double thickness wall: outer face plus an inner walkway wall.
        BuildKit.walls(buffer, x - half, base + 1, z - half, x + half, base + wallHeight, z + half, materials.wall);
        BuildKit.walls(buffer, x - half + 1, base + 1, z - half + 1, x + half - 1, base + wallHeight - 1,
                z + half - 1, materials.wallAccent);
        BuildKit.walls(buffer, x - half + 1, base + wallHeight, z - half + 1, x + half - 1, base + wallHeight,
                z + half - 1, materials.slab);
        BuildKit.battlements(buffer, x - half, base + wallHeight + 1, z - half, x + half, z + half, materials.wall);

        BuildKit.clear(buffer, x - 1, base + 1, z - half, x + 1, base + 4, z - half);

        // Barracks: one long hall along the north wall.
        int hallHalf = Math.max(4, half - 5);
        BuildKit.hollow(buffer, x - hallHalf, base + 1, z + 2, x + hallHalf, base + 6, z + half - 2,
                materials.wall, Blocks.AIR);
        BuildKit.box(buffer, x - hallHalf + 1, base + 6, z + 3, x + hallHalf - 1, base + 6, z + half - 3,
                materials.slab);
        for (int i = -hallHalf + 2; i <= hallHalf - 2; i += 3) {
            buffer.set(x + i, base + 1, z + 3, Blocks.HAY_BLOCK);
            BuildKit.hangingLantern(buffer, x + i, base + 5, z + 4, materials.floor);
            buffer.addSpawn(MobSpawn.mob(x + i, base + 1, z + 4, garrison(random), 2));
        }
        BuildKit.chest(buffer, x - hallHalf + 1, base + 1, z + half - 3, 2, "fortress");
        BuildKit.chest(buffer, x + hallHalf - 1, base + 1, z + half - 3, 3, "fortress");

        // Watch turrets on the corners.
        for (int corner = 0; corner < 4; corner++) {
            int cx = (corner & 1) == 0 ? x - half : x + half;
            int cz = (corner & 2) == 0 ? z - half : z + half;
            BuildKit.box(buffer, cx - 1, base + 1, cz - 1, cx + 1, base + wallHeight + 4, cz + 1, materials.wall);
            BuildKit.clear(buffer, cx, base + wallHeight + 1, cz, cx, base + wallHeight + 4, cz);
            buffer.set(cx, base + wallHeight + 4, cz, materials.light);
            buffer.addSpawn(MobSpawn.mob(cx, base + wallHeight + 2, cz, "SKELETON", 2));
        }

        buffer.addSpawn(MobSpawn.boss(x, base + 1, z - 3, "PIGLIN_BRUTE", 4, "fortress_marshal"));
        BuildKit.chest(buffer, x, base + 1, z - 4, 3, "fortress");
    }

    private static String garrison(FastRandom random) {
        return switch (random.nextInt(4)) {
            case 0 -> "PILLAGER";
            case 1 -> "VINDICATOR";
            case 2 -> "SKELETON";
            default -> "ZOMBIE";
        };
    }
}
