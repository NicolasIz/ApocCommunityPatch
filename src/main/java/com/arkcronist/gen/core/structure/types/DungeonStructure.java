package com.arkcronist.gen.core.structure.types;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.structure.*;

/**
 * An underground dungeon: a ring of rooms joined by corridors, spawners, traps and a boss chamber.
 *
 * <p>Built well below the surface with its own staircase entrance, so it is discoverable from above
 * but survives whatever the cave carver did to the rock around it.</p>
 */
public final class DungeonStructure implements Structure {

    @Override
    public String id() {
        return "dungeon";
    }

    @Override
    public StructureTag tag() {
        return StructureTag.DUNGEON;
    }

    @Override
    public int radius() {
        return 30;
    }

    @Override
    public double weight() {
        return 1.2;
    }

    @Override
    public boolean canPlace(StructureContext context) {
        return context.groundY > context.seaLevel() + 2;
    }

    @Override
    public void build(StructureContext context, StructureBuffer buffer) {
        FastRandom random = context.random;
        StructureMaterials materials = StructureMaterials.forBiome(context.biome, random);
        int x = context.originX;
        int z = context.originZ;
        int surface = context.groundY;
        int depth = random.nextInt(18, 34);
        int floorY = Math.max(context.engine.settings().minY + 8, surface - depth);

        // Entrance shaft with a stair well.
        BuildKit.clear(buffer, x - 1, floorY, z - 1, x + 1, surface + 3, z + 1);
        BuildKit.walls(buffer, x - 2, floorY, z - 2, x + 2, surface + 2, z + 2, materials.wall);
        BuildKit.ladder(buffer, x, floorY + 1, surface + 1, z + 1, Blocks.LADDER_NORTH);
        buffer.set(x, surface + 3, z, Blocks.AIR);
        buffer.set(x + 1, surface + 2, z, materials.light);

        int rooms = random.nextInt(4, 8);
        int previousX = x;
        int previousZ = z;
        for (int i = 0; i < rooms; i++) {
            double angle = (i / (double) rooms) * Math.PI * 2.0 + random.nextDouble(-0.4, 0.4);
            int distance = random.nextInt(8, 22);
            int rx = x + (int) Math.round(Math.cos(angle) * distance);
            int rz = z + (int) Math.round(Math.sin(angle) * distance);
            int halfX = random.nextInt(3, 6);
            int halfZ = random.nextInt(3, 6);
            int height = random.nextInt(4, 6);

            BuildKit.hollow(buffer, rx - halfX, floorY, rz - halfZ, rx + halfX, floorY + height, rz + halfZ,
                    materials.wall, Blocks.AIR);
            BuildKit.box(buffer, rx - halfX + 1, floorY, rz - halfZ + 1, rx + halfX - 1, floorY, rz + halfZ - 1,
                    materials.floor);
            buffer.set(rx, floorY + height - 1, rz, materials.hangingLight);

            corridor(buffer, materials, previousX, previousZ, rx, rz, floorY);
            previousX = rx;
            previousZ = rz;

            if (random.chance(0.75)) {
                BuildKit.spawner(buffer, rx + halfX - 1, floorY + 1, rz + halfZ - 1, dweller(random));
            }
            int mobs = random.nextInt(2, 5);
            for (int m = 0; m < mobs; m++) {
                buffer.addSpawn(MobSpawn.mob(rx + random.nextInt(-halfX + 1, halfX - 1), floorY + 1,
                        rz + random.nextInt(-halfZ + 1, halfZ - 1), dweller(random), 2));
            }
            BuildKit.chest(buffer, rx - halfX + 1, floorY + 1, rz - halfZ + 1, random.nextInt(1, 3), "dungeon");
            if (random.chance(0.4)) {
                buffer.set(rx, floorY + 1, rz, Blocks.COBWEB);
            }
        }

        // Boss chamber, deliberately larger and better lit than the rest.
        int bx = x + random.nextInt(-6, 6);
        int bz = z + random.nextInt(-6, 6);
        int bossY = floorY - random.nextInt(4, 9);
        BuildKit.hollow(buffer, bx - 8, bossY, bz - 8, bx + 8, bossY + 8, bz + 8, materials.wallAccent, Blocks.AIR);
        BuildKit.box(buffer, bx - 7, bossY, bz - 7, bx + 7, bossY, bz + 7, materials.floor);
        BuildKit.torchRing(buffer, bx - 6, bossY + 1, bz - 6, bx + 6, bz + 6, 4, materials.light);
        corridorVertical(buffer, materials, bx, bz, bossY, floorY);
        BuildKit.chest(buffer, bx, bossY + 1, bz - 6, 3, "dungeon");
        BuildKit.chest(buffer, bx, bossY + 1, bz + 6, 3, "dungeon");
        buffer.addSpawn(MobSpawn.boss(bx, bossY + 1, bz, bossType(random), 4, "dungeon_master"));
    }

    private void corridor(StructureBuffer buffer, StructureMaterials materials, int x0, int z0, int x1, int z1, int y) {
        int x = x0;
        int z = z0;
        while (x != x1 || z != z1) {
            if (x != x1) {
                x += Integer.signum(x1 - x);
            } else {
                z += Integer.signum(z1 - z);
            }
            BuildKit.hollow(buffer, x - 1, y, z - 1, x + 1, y + 3, z + 1, materials.wall, Blocks.AIR);
            buffer.set(x, y, z, materials.floor);
        }
    }

    private void corridorVertical(StructureBuffer buffer, StructureMaterials materials, int x, int z,
                                  int fromY, int toY) {
        for (int y = fromY; y <= toY + 1; y++) {
            BuildKit.walls(buffer, x - 2, y, z - 2, x + 2, y, z + 2, materials.wall);
            BuildKit.clear(buffer, x - 1, y, z - 1, x + 1, y, z + 1);
        }
        BuildKit.ladder(buffer, x, fromY + 1, toY, z + 1, Blocks.LADDER_NORTH);
    }

    private static String dweller(FastRandom random) {
        return switch (random.nextInt(6)) {
            case 0 -> "ZOMBIE";
            case 1 -> "SKELETON";
            case 2 -> "CAVE_SPIDER";
            case 3 -> "HUSK";
            case 4 -> "WITCH";
            default -> "SPIDER";
        };
    }

    private static String bossType(FastRandom random) {
        return switch (random.nextInt(4)) {
            case 0 -> "WITHER_SKELETON";
            case 1 -> "EVOKER";
            case 2 -> "RAVAGER";
            default -> "VINDICATOR";
        };
    }
}
