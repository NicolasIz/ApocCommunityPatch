package com.arkcronist.gen.core.structure.types;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.BlockShapes;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.structure.*;

/**
 * A woodland mansion: three floors of dark oak halls and rooms, a cobblestone plinth, a pitched
 * roof, lit corridors, and an attic full of illagers.
 */
public final class WoodlandMansionStructure implements Structure {

    @Override
    public String id() {
        return "mansion";
    }

    @Override
    public StructureTag tag() {
        return StructureTag.MANSION;
    }

    @Override
    public int radius() {
        return 40;
    }

    @Override
    public double weight() {
        return 0.9;
    }

    @Override
    public boolean canPlace(StructureContext context) {
        return !context.submerged(context.originX, context.originZ)
                && context.groundY > context.seaLevel() + 2
                && context.relief(context.originX, context.originZ, 16) < 14;
    }

    @Override
    public void build(StructureContext context, StructureBuffer buffer) {
        FastRandom random = context.random;
        int x = context.originX;
        int z = context.originZ;
        int base = context.averageHeight(16);
        int halfX = random.nextInt(13, 18);
        int halfZ = random.nextInt(13, 18);
        int floors = 3;
        int floorHeight = 5;

        // Plinth.
        for (int ox = -halfX - 1; ox <= halfX + 1; ox++) {
            for (int oz = -halfZ - 1; oz <= halfZ + 1; oz++) {
                buffer.set(x + ox, base, z + oz, Blocks.COBBLESTONE);
                BuildKit.foundation(context, buffer, x + ox, z + oz, base, Blocks.COBBLESTONE, 14);
            }
        }
        BuildKit.clear(buffer, x - halfX - 1, base + 1, z - halfZ - 1, x + halfX + 1,
                base + floors * floorHeight + 14, z + halfZ + 1);

        for (int floor = 0; floor < floors; floor++) {
            int y = base + 1 + floor * floorHeight;
            BuildKit.walls(buffer, x - halfX, y, z - halfZ, x + halfX, y + floorHeight - 1, z + halfZ,
                    Blocks.DARK_OAK_PLANKS);
            BuildKit.box(buffer, x - halfX + 1, y - 1, z - halfZ + 1, x + halfX - 1, y - 1, z + halfZ - 1,
                    floor == 0 ? Blocks.DARK_OAK_PLANKS : Blocks.SPRUCE_PLANKS);
            // Timber frame.
            for (int ox = -halfX; ox <= halfX; ox += 5) {
                for (int i = 0; i < floorHeight; i++) {
                    buffer.set(x + ox, y + i, z - halfZ, Blocks.DARK_OAK_LOG);
                    buffer.set(x + ox, y + i, z + halfZ, Blocks.DARK_OAK_LOG);
                }
            }
            for (int oz = -halfZ; oz <= halfZ; oz += 5) {
                for (int i = 0; i < floorHeight; i++) {
                    buffer.set(x - halfX, y + i, z + oz, Blocks.DARK_OAK_LOG);
                    buffer.set(x + halfX, y + i, z + oz, Blocks.DARK_OAK_LOG);
                }
            }
            // Windows.
            for (int ox = -halfX + 2; ox <= halfX - 2; ox += 5) {
                buffer.set(x + ox, y + 2, z - halfZ, Blocks.GLASS_PANE);
                buffer.set(x + ox, y + 3, z - halfZ, Blocks.GLASS_PANE);
                buffer.set(x + ox, y + 2, z + halfZ, Blocks.GLASS_PANE);
                buffer.set(x + ox, y + 3, z + halfZ, Blocks.GLASS_PANE);
            }

            // Rooms: a grid of partitions with doors, each one furnished and lit.
            for (int ox = -halfX + 6; ox <= halfX - 6; ox += 9) {
                for (int i = -halfZ + 1; i <= halfZ - 1; i++) {
                    buffer.set(x + ox, y, z + i, Blocks.DARK_OAK_PLANKS);
                    buffer.set(x + ox, y + 1, z + i, Blocks.DARK_OAK_PLANKS);
                    buffer.set(x + ox, y + 2, z + i, Blocks.DARK_OAK_PLANKS);
                }
                buffer.set(x + ox, y, z, BlockShapes.door("dark_oak", 2, false, false));
                buffer.set(x + ox, y + 1, z, BlockShapes.door("dark_oak", 2, true, false));
            }
            for (int i = 0; i < 5; i++) {
                int rx = x + random.nextInt(-halfX + 3, halfX - 3);
                int rz = z + random.nextInt(-halfZ + 3, halfZ - 3);
                room(buffer, random, rx, y, rz, floor);
            }
            // Corridor lighting.
            for (int ox = -halfX + 3; ox <= halfX - 3; ox += 6) {
                BuildKit.hangingLantern(buffer, x + ox, y + floorHeight - 1, z, Blocks.SPRUCE_PLANKS);
            }
            // Stairs to the next floor.
            BuildKit.staircase(buffer, x + halfX - 2, y + floorHeight - 1, z + halfZ - 2, floorHeight - 1,
                    -1, 0, 0, "dark_oak");
        }

        // Grand entrance.
        buffer.set(x, base + 1, z - halfZ, BlockShapes.door("dark_oak", 2, false, false));
        buffer.set(x, base + 2, z - halfZ, BlockShapes.door("dark_oak", 2, true, false));
        BuildKit.staircase(buffer, x, base, z - halfZ - 1, 2, 0, -1, 2, "cobblestone");
        BuildKit.wallTorch(buffer, x - 1, base + 3, z - halfZ, 0, -1, false);
        BuildKit.wallTorch(buffer, x + 1, base + 3, z - halfZ, 0, -1, false);

        // Roof.
        int roofY = base + 1 + floors * floorHeight;
        BuildKit.stairRoof(buffer, x - halfX, roofY, z - halfZ, x + halfX, z + halfZ, "dark_oak",
                Blocks.DARK_OAK_PLANKS);

        // Inhabitants.
        for (int i = 0; i < 10; i++) {
            buffer.addSpawn(MobSpawn.mob(x + random.nextInt(-halfX + 3, halfX - 3),
                    base + 1 + random.nextInt(0, floors) * floorHeight,
                    z + random.nextInt(-halfZ + 3, halfZ - 3),
                    random.chance(0.6) ? "VINDICATOR" : "PILLAGER", 3));
        }
        buffer.addSpawn(MobSpawn.boss(x, base + 1 + (floors - 1) * floorHeight, z, "EVOKER", 4, "mansion_master"));
        BuildKit.chest(buffer, x + 2, base + 1 + (floors - 1) * floorHeight, z + 2, 3, "mansion");
        BuildKit.chest(buffer, x - 2, base + 1 + (floors - 1) * floorHeight, z - 2, 3, "mansion");
    }

    private void room(StructureBuffer buffer, FastRandom random, int x, int y, int z, int floor) {
        switch (random.nextInt(6)) {
            case 0 -> {
                buffer.set(x, y, z, BlockShapes.bed(0, false));
                buffer.set(x, y, z - 1, BlockShapes.bed(0, true));
                buffer.set(x + 1, y, z, Blocks.RED_CARPET);
            }
            case 1 -> {
                buffer.set(x, y, z, Blocks.BOOKSHELF);
                buffer.set(x, y + 1, z, Blocks.BOOKSHELF);
                buffer.set(x + 1, y, z, Blocks.LECTERN);
            }
            case 2 -> {
                buffer.set(x, y, z, Blocks.CRAFTING_TABLE);
                buffer.set(x + 1, y, z, BlockShapes.furnace(0));
                buffer.set(x - 1, y, z, Blocks.BARREL_UP);
            }
            case 3 -> {
                BuildKit.box(buffer, x - 1, y, z - 1, x + 1, y, z + 1, Blocks.RED_CARPET);
                BuildKit.chest(buffer, x, y, z, 2, "mansion");
            }
            case 4 -> {
                buffer.set(x, y, z, Blocks.CAULDRON);
                buffer.set(x + 1, y, z, Blocks.BREWING_STAND);
            }
            default -> {
                buffer.set(x, y, z, Blocks.COBWEB);
                BuildKit.spawner(buffer, x + 1, y, z, "VEX");
            }
        }
    }
}
