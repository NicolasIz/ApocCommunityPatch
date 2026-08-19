package com.arkcronist.gen.core.structure.types;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.structure.*;

/**
 * A walled city: perimeter wall with gates and towers, a paved grid of streets, multi storey stone
 * buildings and a market square.
 */
public final class CityStructure implements Structure {

    @Override
    public String id() {
        return "city";
    }

    @Override
    public StructureTag tag() {
        return StructureTag.CITY;
    }

    @Override
    public int radius() {
        return 64;
    }

    @Override
    public double weight() {
        return 0.55;
    }

    @Override
    public boolean canPlace(StructureContext context) {
        return !context.submerged(context.originX, context.originZ)
                && context.groundY > context.seaLevel() + 1
                && context.relief(context.originX, context.originZ, 24) < 20;
    }

    @Override
    public void build(StructureContext context, StructureBuffer buffer) {
        FastRandom random = context.random;
        StructureMaterials materials = StructureMaterials.forBiome(context.biome, random);
        int x = context.originX;
        int z = context.originZ;
        int base = context.averageHeight(20);
        int half = random.nextInt(28, 40);

        // Terrace the whole plot to one level: cities do not follow the hill, they cut into it.
        for (int ox = -half - 2; ox <= half + 2; ox++) {
            for (int oz = -half - 2; oz <= half + 2; oz++) {
                buffer.set(x + ox, base, z + oz, materials.floor);
                BuildKit.foundation(context, buffer, x + ox, z + oz, base, materials.wall, 10);
                BuildKit.clear(buffer, x + ox, base + 1, z + oz, x + ox, base + 4, z + oz);
            }
        }

        // Perimeter wall with corner towers and four gates.
        BuildKit.walls(buffer, x - half, base + 1, z - half, x + half, base + 6, z + half, materials.wall);
        BuildKit.battlements(buffer, x - half, base + 7, z - half, x + half, z + half, materials.wallAccent);
        for (int corner = 0; corner < 4; corner++) {
            int cx = (corner & 1) == 0 ? x - half : x + half;
            int cz = (corner & 2) == 0 ? z - half : z + half;
            BuildKit.cylinder(buffer, cx, base + 1, cz, 3, 12, materials.wallAccent, true);
            BuildKit.cylinder(buffer, cx, base + 12, cz, 3, 1, materials.floor, false);
            buffer.set(cx, base + 13, cz, materials.light);
            buffer.addSpawn(MobSpawn.mob(cx, base + 13, cz, "PILLAGER", 2));
        }
        gate(buffer, materials, x, base, z - half, true);
        gate(buffer, materials, x, base, z + half, true);
        gate(buffer, materials, x - half, base, z, false);
        gate(buffer, materials, x + half, base, z, false);

        // Street grid.
        for (int offset = -half + 8; offset <= half - 8; offset += 12) {
            for (int step = -half + 1; step <= half - 1; step++) {
                buffer.set(x + offset, base, z + step, materials.wallAccent);
                buffer.set(x + step, base, z + offset, materials.wallAccent);
            }
        }

        // Blocks of buildings between the streets.
        for (int bx = -half + 10; bx <= half - 10; bx += 12) {
            for (int bz = -half + 10; bz <= half - 10; bz += 12) {
                if (Math.abs(bx) < 8 && Math.abs(bz) < 8) {
                    continue;
                }
                if (random.chance(0.25)) {
                    continue;
                }
                building(buffer, random, materials, x + bx, base, z + bz);
            }
        }

        marketSquare(buffer, random, materials, x, base, z);
    }

    private void gate(StructureBuffer buffer, StructureMaterials materials, int x, int base, int z, boolean alongX) {
        for (int i = -2; i <= 2; i++) {
            int gx = alongX ? x + i : x;
            int gz = alongX ? z : z + i;
            BuildKit.clear(buffer, gx, base + 1, gz, gx, base + 4, gz);
        }
        int lx = alongX ? x - 3 : x;
        int lz = alongX ? z : z - 3;
        buffer.set(lx, base + 5, lz, materials.light);
    }

    private void building(StructureBuffer buffer, FastRandom random, StructureMaterials materials,
                          int x, int base, int z) {
        int halfX = random.nextInt(3, 5);
        int halfZ = random.nextInt(3, 5);
        int floors = random.nextInt(2, 4);
        int floorHeight = 4;
        int total = floors * floorHeight;

        BuildKit.clear(buffer, x - halfX, base + 1, z - halfZ, x + halfX, base + total + 4, z + halfZ);
        BuildKit.walls(buffer, x - halfX, base + 1, z - halfZ, x + halfX, base + total, z + halfZ, materials.wall);
        for (int floor = 0; floor <= floors; floor++) {
            int y = base + floor * floorHeight;
            BuildKit.box(buffer, x - halfX + 1, y, z - halfZ + 1, x + halfX - 1, y, z + halfZ - 1,
                    floor == 0 ? materials.floor : materials.plank);
            if (floor < floors) {
                BuildKit.hangingLantern(buffer, x, y + floorHeight - 1, z, materials.floor);
                buffer.set(x + halfX, y + 2, z, materials.glass);
                buffer.set(x - halfX, y + 2, z, materials.glass);
                BuildKit.ladder(buffer, x + halfX - 1, y + 1, y + floorHeight, z + halfZ - 1, Blocks.LADDER_SOUTH);
                buffer.set(x + halfX - 1, y + floorHeight, z + halfZ - 1, Blocks.AIR);
            }
        }
        BuildKit.box(buffer, x - halfX, base + total + 1, z - halfZ, x + halfX, base + total + 1, z + halfZ,
                materials.slab);
        buffer.set(x, base + 1, z - halfZ, Blocks.OAK_DOOR_LOWER);
        buffer.set(x, base + 2, z - halfZ, Blocks.OAK_DOOR_UPPER);
        BuildKit.chest(buffer, x - halfX + 1, base + 1, z - halfZ + 1, 2, "city");
        buffer.addSpawn(MobSpawn.mob(x, base + 1, z, random.chance(0.6) ? "VILLAGER" : "PILLAGER", 1));
    }

    private void marketSquare(StructureBuffer buffer, FastRandom random, StructureMaterials materials,
                              int x, int base, int z) {
        BuildKit.box(buffer, x - 7, base, z - 7, x + 7, base, z + 7, materials.wallAccent);
        for (int i = 0; i < 6; i++) {
            int sx = x + random.nextInt(-6, 6);
            int sz = z + random.nextInt(-6, 6);
            // Market stall: four posts and an awning.
            for (int corner = 0; corner < 4; corner++) {
                int cx = sx + ((corner & 1) == 0 ? -1 : 1);
                int cz = sz + ((corner & 2) == 0 ? -1 : 1);
                buffer.set(cx, base + 1, cz, materials.fence);
                buffer.set(cx, base + 2, cz, materials.fence);
            }
            BuildKit.box(buffer, sx - 1, base + 3, sz - 1, sx + 1, base + 3, sz + 1, materials.roof);
            buffer.set(sx, base + 1, sz, Blocks.BARREL);
            buffer.addSpawn(MobSpawn.mob(sx, base + 1, sz, "VILLAGER", 0));
        }
        buffer.set(x, base + 1, z, Blocks.LODESTONE);
        BuildKit.chest(buffer, x + 1, base + 1, z, 2, "city");
        buffer.addSpawn(MobSpawn.boss(x, base + 1, z + 2, "ILLUSIONER", 3, "city_warden"));
    }
}
