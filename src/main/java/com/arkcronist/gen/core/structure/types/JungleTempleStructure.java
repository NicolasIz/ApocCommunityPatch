package com.arkcronist.gen.core.structure.types;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.BlockShapes;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.structure.*;

/** A mossy jungle temple: stepped porch, vine covered walls, a puzzle room and a trapped corridor. */
public final class JungleTempleStructure implements Structure {

    @Override
    public String id() {
        return "jungle_temple";
    }

    @Override
    public StructureTag tag() {
        return StructureTag.JUNGLE_TEMPLE;
    }

    @Override
    public int radius() {
        return 18;
    }

    @Override
    public double weight() {
        return 2.2;
    }

    @Override
    public boolean canPlace(StructureContext context) {
        return !context.submerged(context.originX, context.originZ)
                && context.relief(context.originX, context.originZ, 8) < 14;
    }

    @Override
    public void build(StructureContext context, StructureBuffer buffer) {
        FastRandom random = context.random;
        int x = context.originX;
        int z = context.originZ;
        int base = context.averageHeight(8);
        int half = 6;
        String family = "mossy_stone_brick";

        BuildKit.foundationArea(context, buffer, x - half, z - half, x + half, z + half, base,
                Blocks.MOSSY_COBBLESTONE, 16);
        BuildKit.clear(buffer, x - half - 1, base + 1, z - half - 1, x + half + 1, base + 16, z + half + 1);

        // Three storeys of mossy stone, each stepped in.
        for (int storey = 0; storey < 3; storey++) {
            int storeyHalf = half - storey;
            int y = base + storey * 4;
            for (int ox = -storeyHalf; ox <= storeyHalf; ox++) {
                for (int oz = -storeyHalf; oz <= storeyHalf; oz++) {
                    for (int oy = 0; oy <= 4; oy++) {
                        boolean shell = Math.abs(ox) == storeyHalf || Math.abs(oz) == storeyHalf || oy == 0 || oy == 4;
                        int block = shell ? wall(random) : Blocks.AIR;
                        buffer.set(x + ox, y + oy, z + oz, block);
                    }
                }
            }
            BuildKit.stairTrim(buffer, x - storeyHalf, y + 4, z - storeyHalf, x + storeyHalf, z + storeyHalf,
                    family, true);
            // Window slits.
            buffer.set(x, y + 2, z - storeyHalf, Blocks.AIR);
            buffer.set(x, y + 2, z + storeyHalf, Blocks.AIR);
            buffer.set(x - storeyHalf, y + 2, z, Blocks.AIR);
            buffer.set(x + storeyHalf, y + 2, z, Blocks.AIR);
            BuildKit.hangingLantern(buffer, x, y + 4, z, wall(random));
        }

        // Porch with stairs.
        BuildKit.staircase(buffer, x, base, z - half - 1, 3, 0, -1, 1, family);
        BuildKit.archway(buffer, x, base + 1, z - half, 1, 3, true, family);

        // Puzzle room with levers and a trapped corridor.
        int roomY = base + 1;
        buffer.set(x - 2, roomY, z + 2, Blocks.LEVER_FLOOR);
        buffer.set(x + 2, roomY, z + 2, Blocks.LEVER_FLOOR);
        for (int i = -3; i <= 3; i++) {
            buffer.set(x + i, roomY, z - 3, Blocks.TRIPWIRE_HOOK_NORTH);
        }
        BuildKit.chest(buffer, x - 3, roomY, z + 3, 3, "temple");
        BuildKit.chest(buffer, x + 3, roomY, z + 3, 2, "temple");

        // Hidden cellar.
        int cellarY = base - 5;
        BuildKit.hollow(buffer, x - 3, cellarY, z - 3, x + 3, cellarY + 3, z + 3, Blocks.MOSSY_STONE_BRICKS,
                Blocks.AIR);
        for (int y = cellarY + 4; y <= base; y++) {
            buffer.set(x, y, z, Blocks.AIR);
            buffer.set(x, y, z + 1, BlockShapes.ladder(0));
        }
        BuildKit.chest(buffer, x - 2, cellarY + 1, z - 2, 3, "temple");
        BuildKit.spawner(buffer, x, cellarY + 1, z, "CAVE_SPIDER");
        buffer.addSpawn(MobSpawn.boss(x + 1, cellarY + 1, z, "WITCH", 3, "temple_guardian"));

        // Vines, so it belongs to the jungle.
        for (int i = 0; i < 40; i++) {
            int face = random.nextInt(4);
            int vx = face == 1 ? x + half : face == 3 ? x - half : x + random.nextInt(-half, half);
            int vz = face == 2 ? z + half : face == 0 ? z - half : z + random.nextInt(-half, half);
            int start = base + random.nextInt(2, 12);
            int vine = switch (face) {
                case 0 -> Blocks.VINE_SOUTH;
                case 1 -> Blocks.VINE_WEST;
                case 2 -> Blocks.VINE_NORTH;
                default -> Blocks.VINE_EAST;
            };
            int dx = face == 1 ? 1 : face == 3 ? -1 : 0;
            int dz = face == 2 ? 1 : face == 0 ? -1 : 0;
            for (int j = 0; j < random.nextInt(2, 7); j++) {
                buffer.set(vx + dx, start - j, vz + dz, vine);
            }
        }
    }

    private int wall(FastRandom random) {
        double roll = random.nextDouble();
        return roll < 0.5 ? Blocks.MOSSY_STONE_BRICKS
                : roll < 0.75 ? Blocks.STONE_BRICKS
                : roll < 0.9 ? Blocks.CRACKED_STONE_BRICKS : Blocks.MOSSY_COBBLESTONE;
    }
}
