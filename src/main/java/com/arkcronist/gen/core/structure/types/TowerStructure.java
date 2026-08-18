package com.arkcronist.gen.core.structure.types;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.structure.*;

/**
 * A watchtower: round or square, several floors, a lit top platform and a captain waiting on it.
 */
public final class TowerStructure implements Structure {

    @Override
    public String id() {
        return "tower";
    }

    @Override
    public StructureTag tag() {
        return StructureTag.TOWER;
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
        return !context.submerged(context.originX, context.originZ)
                && context.groundY > context.seaLevel() - 2
                && context.relief(context.originX, context.originZ, 6) < 14;
    }

    @Override
    public void build(StructureContext context, StructureBuffer buffer) {
        FastRandom random = context.random;
        StructureMaterials materials = StructureMaterials.forBiome(context.biome, random);
        int x = context.originX;
        int z = context.originZ;
        int base = context.averageHeight(6);
        boolean round = random.nextBoolean();
        int radius = random.nextInt(3, 5);
        int floors = random.nextInt(3, 6);
        int floorHeight = 5;
        int top = base + floors * floorHeight;
        if (top + 6 >= context.maxY()) {
            return;
        }

        // Platform and foundation so the tower does not float on a slope.
        BuildKit.foundationArea(context, buffer, x - radius - 1, z - radius - 1, x + radius + 1, z + radius + 1,
                base, materials.wall, 18);
        BuildKit.clear(buffer, x - radius, base + 1, z - radius, x + radius, top + 6, z + radius);

        for (int floor = 0; floor < floors; floor++) {
            int y = base + floor * floorHeight;
            if (round) {
                BuildKit.cylinder(buffer, x, y, z, radius, floorHeight, materials.wall, true);
                BuildKit.cylinder(buffer, x, y, z, radius - 1, 1, materials.floor, false);
            } else {
                BuildKit.walls(buffer, x - radius, y, z - radius, x + radius, y + floorHeight - 1, z + radius,
                        materials.wall);
                BuildKit.box(buffer, x - radius + 1, y, z - radius + 1, x + radius - 1, y, z + radius - 1,
                        materials.floor);
            }

            // Arrow slits and a lantern on every floor.
            buffer.set(x + radius, y + 2, z, Blocks.GLASS_PANE);
            buffer.set(x - radius, y + 2, z, Blocks.GLASS_PANE);
            buffer.set(x, y + 2, z + radius, Blocks.GLASS_PANE);
            buffer.set(x, y + 2, z - radius, Blocks.GLASS_PANE);
            buffer.set(x, y + floorHeight - 1, z, materials.hangingLight);

            BuildKit.ladder(buffer, x + radius - 1, y + 1, y + floorHeight, z, Blocks.LADDER_WEST);

            if (floor > 0 && random.chance(0.7)) {
                BuildKit.chest(buffer, x - radius + 1, y + 1, z + radius - 1, floor >= floors - 2 ? 2 : 1, "tower");
            }
            if (floor > 0) {
                buffer.addSpawn(MobSpawn.mob(x, y + 1, z, garrison(random), 1 + floor / 2));
            }
        }

        // Roof: open crenellated platform with a brazier.
        int roofY = base + floors * floorHeight;
        if (round) {
            BuildKit.cylinder(buffer, x, roofY, z, radius, 1, materials.floor, false);
            BuildKit.cylinder(buffer, x, roofY + 1, z, radius, 2, materials.wallAccent, true);
        } else {
            BuildKit.box(buffer, x - radius, roofY, z - radius, x + radius, roofY, z + radius, materials.floor);
            BuildKit.battlements(buffer, x - radius, roofY + 1, z - radius, x + radius, z + radius, materials.wallAccent);
        }
        buffer.set(x, roofY + 1, z, Blocks.CAMPFIRE);
        BuildKit.chest(buffer, x + 1, roofY + 1, z + 1, 3, "tower");
        buffer.addSpawn(MobSpawn.boss(x, roofY + 2, z, boss(random), Math.min(4, 1 + floors / 2), "tower_captain"));
    }

    private static String garrison(FastRandom random) {
        return switch (random.nextInt(4)) {
            case 0 -> "SKELETON";
            case 1 -> "ZOMBIE";
            case 2 -> "PILLAGER";
            default -> "STRAY";
        };
    }

    private static String boss(FastRandom random) {
        return random.chance(0.5) ? "VINDICATOR" : "PILLAGER";
    }
}
