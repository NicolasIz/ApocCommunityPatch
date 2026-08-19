package com.arkcronist.gen.core.structure.types;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.BlockShapes;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.structure.*;

/**
 * A castle worth walking up to.
 *
 * <p>Levelled bailey, curtain wall with a walkway and crenellations, square corner towers with lit
 * turret rooms, a gatehouse with an arched passage, a paved approach with lamp posts and steps, a
 * courtyard with garden beds, a well and training gear, and a keep whose interior is actually
 * furnished and lit floor by floor - ending in a throne room with its lord.</p>
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
        return 44;
    }

    @Override
    public double weight() {
        return 0.7;
    }

    @Override
    public boolean canPlace(StructureContext context) {
        return !context.submerged(context.originX, context.originZ)
                && context.groundY > context.seaLevel() + 2
                && context.relief(context.originX, context.originZ, 18) < 24;
    }

    @Override
    public void build(StructureContext context, StructureBuffer buffer) {
        FastRandom random = context.random;
        StructureMaterials materials = StructureMaterials.forBiome(context.biome, random);
        String stone = materials.stoneFamily;
        String wood = materials.woodFamily;
        int x = context.originX;
        int z = context.originZ;
        int base = context.averageHeight(18);
        int half = random.nextInt(17, 24);
        int wallHeight = 8;

        bailey(context, buffer, materials, x, base, z, half);
        curtainWall(buffer, random, materials, stone, x, base, z, half, wallHeight);
        for (int corner = 0; corner < 4; corner++) {
            int cx = (corner & 1) == 0 ? x - half : x + half;
            int cz = (corner & 2) == 0 ? z - half : z + half;
            cornerTower(buffer, random, materials, stone, cx, base, cz, wallHeight);
        }
        gatehouse(context, buffer, random, materials, stone, wood, x, base, z - half, half);
        courtyard(buffer, random, materials, stone, x, base, z, half);
        keep(context, buffer, random, materials, stone, wood, x, base, z, half);
    }

    /** Cuts the plot flat, underpins it and lays the courtyard floor. */
    private void bailey(StructureContext context, StructureBuffer buffer, StructureMaterials materials,
                        int x, int base, int z, int half) {
        for (int ox = -half - 1; ox <= half + 1; ox++) {
            for (int oz = -half - 1; oz <= half + 1; oz++) {
                buffer.set(x + ox, base, z + oz, materials.floor);
                BuildKit.foundation(context, buffer, x + ox, z + oz, base, materials.wall, 18);
                BuildKit.clear(buffer, x + ox, base + 1, z + oz, x + ox, base + 5, z + oz);
            }
        }
    }

    private void curtainWall(StructureBuffer buffer, FastRandom random, StructureMaterials materials,
                             String stone, int x, int base, int z, int half, int height) {
        BuildKit.walls(buffer, x - half, base + 1, z - half, x + half, base + height, z + half, materials.wall);
        BuildKit.walls(buffer, x - half + 1, base + 1, z - half + 1, x + half - 1, base + height - 1,
                z + half - 1, materials.wallAccent);
        // Walkway on top of the wall, plus a cornice under it.
        BuildKit.walls(buffer, x - half + 1, base + height, z - half + 1, x + half - 1, base + height,
                z + half - 1, BlockShapes.slab(stone, false));
        BuildKit.stairTrim(buffer, x - half + 1, base + height - 1, z - half + 1, x + half - 1, z + half - 1,
                stone, true);

        for (int i = -half; i <= half; i++) {
            boolean merlon = ((i + half) & 1) == 0;
            int block = merlon ? materials.wallAccent : BlockShapes.wall(stone);
            buffer.set(x + i, base + height + 1, z - half, block);
            buffer.set(x + i, base + height + 1, z + half, block);
            buffer.set(x - half, base + height + 1, z + i, block);
            buffer.set(x + half, base + height + 1, z + i, block);
            // Torches along the inner face of the wall walk.
            if (((i + half) % 7) == 0) {
                BuildKit.wallTorch(buffer, x + i, base + height + 1, z - half, 0, 1, false);
                BuildKit.wallTorch(buffer, x + i, base + height + 1, z + half, 0, -1, false);
                BuildKit.wallTorch(buffer, x - half, base + height + 1, z + i, 1, 0, false);
                BuildKit.wallTorch(buffer, x + half, base + height + 1, z + i, -1, 0, false);
            }
        }
        // Stairs from the courtyard up to the wall walk.
        BuildKit.staircase(buffer, x - half + 2, base + height, z - half + 2, height, 1, 0, 1, stone);
        if (random.chance(0.6)) {
            buffer.set(x - half + 1, base + height + 2, z, BlockShapes.wallBanner(1));
            buffer.set(x + half - 1, base + height + 2, z, BlockShapes.wallBanner(3));
        }
    }

    private void cornerTower(StructureBuffer buffer, FastRandom random, StructureMaterials materials,
                             String stone, int cx, int base, int cz, int wallHeight) {
        int towerHalf = 3;
        int height = wallHeight + random.nextInt(5, 9);

        BuildKit.walls(buffer, cx - towerHalf, base + 1, cz - towerHalf, cx + towerHalf, base + height,
                cz + towerHalf, materials.wall);
        BuildKit.clear(buffer, cx - towerHalf + 1, base + 1, cz - towerHalf + 1, cx + towerHalf - 1,
                base + height - 1, cz + towerHalf - 1);
        BuildKit.box(buffer, cx - towerHalf + 1, base, cz - towerHalf + 1, cx + towerHalf - 1, base,
                cz + towerHalf - 1, materials.floor);

        // Turret room: floor, light, arrow slits.
        int roomFloor = base + wallHeight;
        BuildKit.box(buffer, cx - towerHalf + 1, roomFloor, cz - towerHalf + 1, cx + towerHalf - 1,
                roomFloor, cz + towerHalf - 1, materials.floor);
        BuildKit.hangingLantern(buffer, cx, base + height, cz, materials.floor);
        for (int face = 0; face < 4; face++) {
            int wx = face == 1 ? cx + towerHalf : face == 3 ? cx - towerHalf : cx;
            int wz = face == 2 ? cz + towerHalf : face == 0 ? cz - towerHalf : cz;
            buffer.set(wx, roomFloor + 2, wz, Blocks.AIR);
            buffer.set(wx, roomFloor + 3, wz, materials.glass);
        }
        BuildKit.ladder(buffer, cx, base + 1, roomFloor, cz + towerHalf - 1, BlockShapes.ladder(2));
        buffer.set(cx, roomFloor, cz + towerHalf - 1, Blocks.AIR);

        // Battlemented cap with a pyramid of stairs.
        int capY = base + height + 1;
        BuildKit.box(buffer, cx - towerHalf, capY, cz - towerHalf, cx + towerHalf, capY, cz + towerHalf,
                BlockShapes.slab(stone, false));
        for (int i = -towerHalf; i <= towerHalf; i++) {
            if (((i + towerHalf) & 1) == 0) {
                buffer.set(cx + i, capY + 1, cz - towerHalf, materials.wallAccent);
                buffer.set(cx + i, capY + 1, cz + towerHalf, materials.wallAccent);
                buffer.set(cx - towerHalf, capY + 1, cz + i, materials.wallAccent);
                buffer.set(cx + towerHalf, capY + 1, cz + i, materials.wallAccent);
            }
        }
        buffer.set(cx, capY + 1, cz, Blocks.LANTERN);
        buffer.addSpawn(MobSpawn.mob(cx, roomFloor + 1, cz, "PILLAGER", 2));
        BuildKit.chest(buffer, cx + 1, roomFloor + 1, cz + 1, 2, "castle");
    }

    private void gatehouse(StructureContext context, StructureBuffer buffer, FastRandom random,
                           StructureMaterials materials, String stone, String wood,
                           int x, int base, int gateZ, int half) {
        // Passage through the wall, arched, with a portcullis and flanking towers.
        BuildKit.clear(buffer, x - 2, base + 1, gateZ - 1, x + 2, base + 4, gateZ + 1);
        BuildKit.archway(buffer, x - 1, base + 1, gateZ, 3, 4, true, stone);
        for (int i = -1; i <= 1; i++) {
            buffer.set(x + i, base + 5, gateZ, BlockShapes.stairs(stone, 0, true));
            buffer.set(x + i, base + 4, gateZ, Blocks.IRON_BARS);
        }
        BuildKit.box(buffer, x - 4, base + 1, gateZ - 1, x - 3, base + 12, gateZ + 1, materials.wall);
        BuildKit.box(buffer, x + 3, base + 1, gateZ - 1, x + 4, base + 12, gateZ + 1, materials.wall);
        for (int i = -4; i <= 4; i++) {
            if (((i + 4) & 1) == 0) {
                buffer.set(x + i, base + 13, gateZ, materials.wallAccent);
            }
        }
        BuildKit.wallTorch(buffer, x - 3, base + 4, gateZ - 1, 0, -1, false);
        BuildKit.wallTorch(buffer, x + 3, base + 4, gateZ - 1, 0, -1, false);

        // Approach: paved path with lamp posts, and steps up onto the plateau.
        for (int step = 1; step <= 14; step++) {
            int pz = gateZ - step;
            int ground = context.height(x, pz);
            for (int ox = -2; ox <= 2; ox++) {
                buffer.set(x + ox, ground, pz, Math.abs(ox) == 2 ? materials.wallAccent : materials.floor);
                BuildKit.clear(buffer, x + ox, ground + 1, pz, x + ox, ground + 4, pz);
            }
            if (step % 5 == 0) {
                BuildKit.lampPost(buffer, x - 3, ground, pz, BlockShapes.fence(wood), 3);
                BuildKit.lampPost(buffer, x + 3, ground, pz, BlockShapes.fence(wood), 3);
            }
        }
        buffer.addSpawn(MobSpawn.mob(x, base + 1, gateZ + 3, "VINDICATOR", 2));
    }

    private void courtyard(StructureBuffer buffer, FastRandom random, StructureMaterials materials,
                           String stone, int x, int base, int z, int half) {
        // Paths crossing the bailey.
        for (int i = -half + 1; i <= half - 1; i++) {
            buffer.set(x + i, base, z, materials.wallAccent);
            buffer.set(x, base, z + i, materials.wallAccent);
        }
        // Garden beds in two quadrants, training ground in another.
        BuildKit.gardenBed(buffer, random, x - half / 2, base, z + half / 2, 3, 2,
                Blocks.DIRT, BlockShapes.slab(stone, false));
        BuildKit.gardenBed(buffer, random, x + half / 2, base, z + half / 2, 3, 2,
                Blocks.DIRT, BlockShapes.slab(stone, false));
        for (int i = 0; i < 4; i++) {
            int px = x - half / 2 + random.nextInt(-3, 3);
            int pz = z - half / 2 + random.nextInt(-3, 3);
            buffer.set(px, base + 1, pz, Blocks.HAY_BLOCK);
            if (random.chance(0.5)) {
                buffer.set(px, base + 2, pz, Blocks.ANVIL);
            }
        }
        // Well.
        int wx = x + half / 2;
        int wz = z - half / 2;
        BuildKit.walls(buffer, wx - 1, base, wz - 1, wx + 1, base + 1, wz + 1, materials.wallAccent);
        buffer.set(wx, base, wz, Blocks.WATER);
        buffer.set(wx, base - 1, wz, Blocks.WATER);
        for (int corner = 0; corner < 4; corner++) {
            int px = (corner & 1) == 0 ? wx - 1 : wx + 1;
            int pz = (corner & 2) == 0 ? wz - 1 : wz + 1;
            buffer.set(px, base + 2, pz, BlockShapes.fence(materials.woodFamily));
            buffer.set(px, base + 3, pz, BlockShapes.fence(materials.woodFamily));
        }
        BuildKit.box(buffer, wx - 1, base + 4, wz - 1, wx + 1, base + 4, wz + 1, BlockShapes.slab(stone, false));
        buffer.set(wx, base + 3, wz, Blocks.LANTERN);
    }

    private void keep(StructureContext context, StructureBuffer buffer, FastRandom random,
                      StructureMaterials materials, String stone, String wood,
                      int x, int base, int z, int outerHalf) {
        int half = Math.max(6, outerHalf / 2 - 1);
        int floors = random.nextInt(3, 5);
        int floorHeight = 6;
        int keepZ = z + outerHalf / 3;
        int top = base + floors * floorHeight;

        BuildKit.clear(buffer, x - half, base + 1, keepZ - half, x + half, top + 8, keepZ + half);

        for (int floor = 0; floor < floors; floor++) {
            int y = base + 1 + floor * floorHeight;
            BuildKit.walls(buffer, x - half, y, keepZ - half, x + half, y + floorHeight - 1, keepZ + half,
                    materials.wall);
            BuildKit.box(buffer, x - half + 1, y - 1, keepZ - half + 1, x + half - 1, y - 1, keepZ + half - 1,
                    floor == 0 ? materials.floor : materials.plank);
            // Corner pillars and a cornice.
            for (int corner = 0; corner < 4; corner++) {
                int cx = (corner & 1) == 0 ? x - half : x + half;
                int cz = (corner & 2) == 0 ? keepZ - half : keepZ + half;
                for (int i = 0; i < floorHeight; i++) {
                    buffer.set(cx, y + i, cz, materials.pillar);
                }
            }
            BuildKit.stairTrim(buffer, x - half, y + floorHeight - 1, keepZ - half, x + half, keepZ + half,
                    stone, true);

            // Windows on all four faces.
            for (int offset = -half + 2; offset <= half - 2; offset += 4) {
                buffer.set(x + offset, y + 2, keepZ - half, materials.glass);
                buffer.set(x + offset, y + 3, keepZ - half, materials.glass);
                buffer.set(x + offset, y + 2, keepZ + half, materials.glass);
                buffer.set(x + offset, y + 3, keepZ + half, materials.glass);
                buffer.set(x - half, y + 2, keepZ + offset, materials.glass);
                buffer.set(x + half, y + 2, keepZ + offset, materials.glass);
            }

            // Furnishing: light, carpet, tables, and a way up.
            BuildKit.chandelier(buffer, x, y + floorHeight - 1, keepZ, materials.floor, 1);
            for (int ox = -2; ox <= 2; ox++) {
                for (int oz = -2; oz <= 2; oz++) {
                    buffer.set(x + ox, y, keepZ + oz, Blocks.RED_CARPET);
                }
            }
            BuildKit.wallTorch(buffer, x - half, y + 3, keepZ + 2, 1, 0, false);
            BuildKit.wallTorch(buffer, x + half, y + 3, keepZ + 2, -1, 0, false);
            BuildKit.staircase(buffer, x + half - 2, y + floorHeight - 1, keepZ + half - 2,
                    floorHeight - 1, 0, -1, 0, stone);

            if (floor == 0) {
                buffer.set(x, y, keepZ - half, BlockShapes.door(wood, 2, false, false));
                buffer.set(x, y + 1, keepZ - half, BlockShapes.door(wood, 2, true, false));
                buffer.set(x - 2, y, keepZ + half - 1, Blocks.CRAFTING_TABLE);
                buffer.set(x + 2, y, keepZ + half - 1, BlockShapes.furnace(0));
            } else if (floor == 1) {
                buffer.set(x - half + 1, y, keepZ - half + 1, Blocks.BOOKSHELF);
                buffer.set(x - half + 1, y + 1, keepZ - half + 1, Blocks.BOOKSHELF);
                buffer.set(x - half + 2, y, keepZ - half + 1, Blocks.LECTERN);
                buffer.set(x + half - 1, y, keepZ - half + 1, BlockShapes.bed(2, false));
                buffer.set(x + half - 1, y, keepZ - half + 2, BlockShapes.bed(2, true));
            }

            for (int i = 0; i < 1 + floor; i++) {
                buffer.addSpawn(MobSpawn.mob(x + random.nextInt(-half + 2, half - 2), y,
                        keepZ + random.nextInt(-half + 2, half - 2),
                        floor == 0 ? "ZOMBIE" : "VINDICATOR", 2 + floor / 2));
            }
            if (random.chance(0.8)) {
                BuildKit.chest(buffer, x - half + 1, y, keepZ + half - 1, Math.min(3, 1 + floor), "castle");
            }
        }

        // Throne room on the top floor.
        int throneY = base + 1 + (floors - 1) * floorHeight;
        BuildKit.box(buffer, x - 2, throneY, keepZ + half - 3, x + 2, throneY, keepZ + half - 2,
                materials.wallAccent);
        buffer.set(x, throneY + 1, keepZ + half - 2, BlockShapes.stairs(stone, 0, false));
        buffer.set(x - 1, throneY + 1, keepZ + half - 2, BlockShapes.wall(stone));
        buffer.set(x + 1, throneY + 1, keepZ + half - 2, BlockShapes.wall(stone));
        buffer.set(x - 2, throneY + 2, keepZ + half - 1, BlockShapes.wallBanner(0));
        buffer.set(x + 2, throneY + 2, keepZ + half - 1, BlockShapes.wallBanner(0));
        BuildKit.chest(buffer, x - 1, throneY + 1, keepZ + half - 3, 3, "castle");
        BuildKit.chest(buffer, x + 1, throneY + 1, keepZ + half - 3, 3, "castle");
        buffer.addSpawn(MobSpawn.boss(x, throneY + 1, keepZ, "EVOKER", 4, "castle_lord"));

        // Roof: walkway with crenellations and corner lanterns.
        BuildKit.box(buffer, x - half, top + 1, keepZ - half, x + half, top + 1, keepZ + half,
                BlockShapes.slab(stone, false));
        for (int i = -half; i <= half; i++) {
            if (((i + half) & 1) == 0) {
                buffer.set(x + i, top + 2, keepZ - half, materials.wallAccent);
                buffer.set(x + i, top + 2, keepZ + half, materials.wallAccent);
                buffer.set(x - half, top + 2, keepZ + i, materials.wallAccent);
                buffer.set(x + half, top + 2, keepZ + i, materials.wallAccent);
            }
        }
        for (int corner = 0; corner < 4; corner++) {
            int cx = (corner & 1) == 0 ? x - half + 1 : x + half - 1;
            int cz = (corner & 2) == 0 ? keepZ - half + 1 : keepZ + half - 1;
            buffer.set(cx, top + 2, cz, Blocks.LANTERN);
        }
    }
}
