package com.arkcronist.gen.core.structure.types;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.structure.*;

/**
 * A castle: curtain wall with corner towers, a gatehouse, a courtyard and a multi floor keep with a
 * throne room and its lord.
 */
public final class CastleStructure implements Structure {

    @Override
    public String id() {
        return "castle";
    }

    @Override
    public StructureTag tag() {
        return StructureTag.CASTLE;
    }

    @Override
    public int radius() {
        return 40;
    }

    @Override
    public double weight() {
        return 0.7;
    }

    @Override
    public boolean canPlace(StructureContext context) {
        return !context.submerged(context.originX, context.originZ)
                && context.groundY > context.seaLevel() + 3
                && context.relief(context.originX, context.originZ, 16) < 26;
    }

    @Override
    public void build(StructureContext context, StructureBuffer buffer) {
        FastRandom random = context.random;
        StructureMaterials materials = StructureMaterials.forBiome(context.biome, random);
        int x = context.originX;
        int z = context.originZ;
        int base = context.averageHeight(16);
        int half = random.nextInt(18, 26);
        int wallHeight = random.nextInt(8, 12);

        // Courtyard platform, cut into the hill and underpinned.
        for (int ox = -half; ox <= half; ox++) {
            for (int oz = -half; oz <= half; oz++) {
                buffer.set(x + ox, base, z + oz, materials.floor);
                BuildKit.foundation(context, buffer, x + ox, z + oz, base, materials.wall, 16);
                BuildKit.clear(buffer, x + ox, base + 1, z + oz, x + ox, base + 3, z + oz);
            }
        }

        // Curtain wall with a walkway and battlements.
        BuildKit.walls(buffer, x - half, base + 1, z - half, x + half, base + wallHeight, z + half, materials.wall);
        BuildKit.walls(buffer, x - half + 1, base + wallHeight, z - half + 1, x + half - 1, base + wallHeight,
                z + half - 1, materials.slab);
        BuildKit.battlements(buffer, x - half, base + wallHeight + 1, z - half, x + half, z + half,
                materials.wallAccent);

        // Corner towers.
        for (int corner = 0; corner < 4; corner++) {
            int cx = (corner & 1) == 0 ? x - half : x + half;
            int cz = (corner & 2) == 0 ? z - half : z + half;
            int towerHeight = wallHeight + random.nextInt(4, 9);
            BuildKit.cylinder(buffer, cx, base + 1, cz, 4, towerHeight, materials.wall, true);
            BuildKit.cylinder(buffer, cx, base + towerHeight, cz, 4, 1, materials.floor, false);
            BuildKit.cylinder(buffer, cx, base + towerHeight + 1, cz, 4, 2, materials.wallAccent, true);
            buffer.set(cx, base + towerHeight + 1, cz, Blocks.CAMPFIRE);
            BuildKit.ladder(buffer, cx + 3, base + 2, base + towerHeight, cz, Blocks.LADDER_WEST);
            buffer.addSpawn(MobSpawn.mob(cx, base + towerHeight + 1, cz, "PILLAGER", 2));
            BuildKit.chest(buffer, cx + 1, base + towerHeight + 1, cz + 1, 2, "castle");
        }

        // Gatehouse.
        int gateZ = z - half;
        BuildKit.clear(buffer, x - 2, base + 1, gateZ, x + 2, base + 5, gateZ);
        BuildKit.box(buffer, x - 3, base + 6, gateZ - 1, x + 3, base + wallHeight + 3, gateZ + 1, materials.wallAccent);
        buffer.set(x - 2, base + 5, gateZ, Blocks.IRON_BARS);
        buffer.set(x + 2, base + 5, gateZ, Blocks.IRON_BARS);
        buffer.addSpawn(MobSpawn.mob(x, base + 1, gateZ + 3, "VINDICATOR", 2));

        buildKeep(context, buffer, random, materials, x, base, z, half);
        courtyard(buffer, random, materials, x, base, z, half);
    }

    private void buildKeep(StructureContext context, StructureBuffer buffer, FastRandom random,
                           StructureMaterials materials, int x, int base, int z, int outerHalf) {
        int half = Math.max(6, outerHalf / 2 - 2);
        int floors = random.nextInt(3, 5);
        int floorHeight = 6;
        int top = base + floors * floorHeight;

        BuildKit.clear(buffer, x - half, base + 1, z - half, x + half, top + 6, z + half);
        for (int floor = 0; floor < floors; floor++) {
            int y = base + floor * floorHeight;
            BuildKit.walls(buffer, x - half, y + 1, z - half, x + half, y + floorHeight, z + half, materials.wall);
            BuildKit.box(buffer, x - half + 1, y, z - half + 1, x + half - 1, y, z + half - 1,
                    floor == 0 ? materials.floor : materials.plank);
            buffer.set(x, y + floorHeight - 1, z, materials.hangingLight);
            BuildKit.ladder(buffer, x + half - 1, y + 1, y + floorHeight, z + half - 1, Blocks.LADDER_SOUTH);
            buffer.set(x + half - 1, y + floorHeight, z + half - 1, Blocks.AIR);

            for (int i = 0; i < 2 + floor; i++) {
                buffer.addSpawn(MobSpawn.mob(x + random.nextInt(-half + 2, half - 2), y + 1,
                        z + random.nextInt(-half + 2, half - 2), floor == 0 ? "ZOMBIE" : "VINDICATOR", 2 + floor / 2));
            }
            if (random.chance(0.8)) {
                BuildKit.chest(buffer, x - half + 1, y + 1, z - half + 1, Math.min(3, 1 + floor), "castle");
            }
        }

        // Throne room on the top floor.
        int throneY = base + (floors - 1) * floorHeight + 1;
        buffer.set(x, throneY, z - half + 2, materials.stairs);
        BuildKit.box(buffer, x - 2, throneY, z - half + 1, x + 2, throneY, z - half + 3, materials.wallAccent);
        buffer.set(x, throneY + 1, z - half + 2, Blocks.AIR);
        BuildKit.chest(buffer, x - 1, throneY, z - half + 2, 3, "castle");
        BuildKit.chest(buffer, x + 1, throneY, z - half + 2, 3, "castle");
        buffer.addSpawn(MobSpawn.boss(x, throneY + 1, z, "EVOKER", 4, "castle_lord"));

        BuildKit.box(buffer, x - half, top + 1, z - half, x + half, top + 1, z + half, materials.floor);
        BuildKit.battlements(buffer, x - half, top + 2, z - half, x + half, z + half, materials.wallAccent);
    }

    private void courtyard(StructureBuffer buffer, FastRandom random, StructureMaterials materials,
                           int x, int base, int z, int half) {
        for (int i = 0; i < 8; i++) {
            int px = x + random.nextInt(-half + 3, half - 3);
            int pz = z + random.nextInt(-half + 3, half - 3);
            if (random.chance(0.4)) {
                buffer.set(px, base + 1, pz, Blocks.HAY_BLOCK);
            } else if (random.chance(0.5)) {
                buffer.set(px, base + 1, pz, materials.fence);
                buffer.set(px, base + 2, pz, materials.light);
            } else {
                buffer.set(px, base + 1, pz, Blocks.ANVIL);
            }
        }
    }
}
