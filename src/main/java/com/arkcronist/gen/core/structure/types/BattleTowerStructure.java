package com.arkcronist.gen.core.structure.types;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.structure.*;

/**
 * A combat tower: every floor is a sealed arena with mobs and a chest, and the roof holds a miniboss
 * guarding the best loot in the build.
 */
public final class BattleTowerStructure implements Structure {

    @Override
    public String id() {
        return "battle_tower";
    }

    @Override
    public StructureTag tag() {
        return StructureTag.BATTLE_TOWER;
    }

    @Override
    public int radius() {
        return 14;
    }

    @Override
    public double weight() {
        return 0.9;
    }

    @Override
    public boolean canPlace(StructureContext context) {
        return !context.submerged(context.originX, context.originZ)
                && context.groundY > context.seaLevel()
                && context.relief(context.originX, context.originZ, 8) < 18;
    }

    @Override
    public void build(StructureContext context, StructureBuffer buffer) {
        FastRandom random = context.random;
        StructureMaterials materials = StructureMaterials.forBiome(context.biome, random);
        int x = context.originX;
        int z = context.originZ;
        int base = context.averageHeight(7);
        int half = random.nextInt(4, 6);
        int floors = switch (context.preset) {
            case BASE -> random.nextInt(4, 6);
            case CHAOTIC -> random.nextInt(5, 8);
            case INSANE -> random.nextInt(6, 10);
        };
        int floorHeight = 6;
        if (base + floors * floorHeight + 8 >= context.maxY()) {
            return;
        }

        BuildKit.foundationArea(context, buffer, x - half - 1, z - half - 1, x + half + 1, z + half + 1,
                base, materials.wall, 20);
        BuildKit.clear(buffer, x - half, base + 1, z - half, x + half, base + floors * floorHeight + 8, z + half);

        for (int floor = 0; floor < floors; floor++) {
            int y = base + floor * floorHeight;
            BuildKit.walls(buffer, x - half, y, z - half, x + half, y + floorHeight - 1, z + half, materials.wall);
            BuildKit.box(buffer, x - half + 1, y, z - half + 1, x + half - 1, y, z + half - 1, materials.floor);
            // Ceiling with a single hole: the only way up is through the fight.
            BuildKit.box(buffer, x - half + 1, y + floorHeight - 1, z - half + 1,
                    x + half - 1, y + floorHeight - 1, z + half - 1, materials.floor);
            buffer.set(x + half - 2, y + floorHeight - 1, z + half - 2, Blocks.AIR);
            BuildKit.ladder(buffer, x + half - 2, y + 1, y + floorHeight - 1, z + half - 1, Blocks.LADDER_SOUTH);

            for (int corner = 0; corner < 4; corner++) {
                int cx = (corner & 1) == 0 ? x - half + 1 : x + half - 1;
                int cz = (corner & 2) == 0 ? z - half + 1 : z + half - 1;
                buffer.set(cx, y + 1, cz, materials.light);
            }

            int mobs = 2 + floor / 2;
            for (int i = 0; i < mobs; i++) {
                buffer.addSpawn(MobSpawn.mob(x + random.nextInt(-half + 2, half - 2), y + 1,
                        z + random.nextInt(-half + 2, half - 2), garrison(random), 1 + floor / 2));
            }
            BuildKit.chest(buffer, x - half + 1, y + 1, z - half + 1, Math.min(3, 1 + floor / 2), "battle");
        }

        int roofY = base + floors * floorHeight;
        BuildKit.box(buffer, x - half, roofY, z - half, x + half, roofY, z + half, materials.floor);
        BuildKit.battlements(buffer, x - half, roofY + 1, z - half, x + half, z + half, materials.wallAccent);
        BuildKit.chest(buffer, x, roofY + 1, z + 1, 3, "battle");
        BuildKit.chest(buffer, x, roofY + 1, z - 1, 3, "battle");
        buffer.set(x + 1, roofY + 1, z, Blocks.LANTERN);
        buffer.addSpawn(MobSpawn.boss(x, roofY + 1, z, "VINDICATOR", 4, "battle_champion"));
    }

    private static String garrison(FastRandom random) {
        return switch (random.nextInt(6)) {
            case 0 -> "ZOMBIE";
            case 1 -> "SKELETON";
            case 2 -> "SPIDER";
            case 3 -> "PILLAGER";
            case 4 -> "WITCH";
            default -> "HUSK";
        };
    }
}
