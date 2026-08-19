package com.arkcronist.gen.core.structure.types;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.BlockShapes;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.structure.*;

/** A pillager outpost: dark oak watchtower, palisade, cages, tents and a captain. */
public final class PillagerOutpostStructure implements Structure {

    @Override
    public String id() {
        return "outpost";
    }

    @Override
    public StructureTag tag() {
        return StructureTag.OUTPOST;
    }

    @Override
    public int radius() {
        return 22;
    }

    @Override
    public double weight() {
        return 1.1;
    }

    @Override
    public boolean canPlace(StructureContext context) {
        return !context.submerged(context.originX, context.originZ)
                && context.groundY > context.seaLevel()
                && context.relief(context.originX, context.originZ, 9) < 9;
    }

    @Override
    public void build(StructureContext context, StructureBuffer buffer) {
        FastRandom random = context.random;
        int x = context.originX;
        int z = context.originZ;
        int base = context.averageHeight(9);
        int half = 3;
        int height = 14;

        BuildKit.foundationArea(context, buffer, x - half, z - half, x + half, z + half, base,
                Blocks.COBBLESTONE, 16);
        BuildKit.clear(buffer, x - half - 1, base + 1, z - half - 1, x + half + 1, base + height + 8,
                z + half + 1);

        // Tower: log corners, plank walls, overhanging top floor.
        for (int corner = 0; corner < 4; corner++) {
            int cx = (corner & 1) == 0 ? x - half : x + half;
            int cz = (corner & 2) == 0 ? z - half : z + half;
            for (int i = 1; i <= height; i++) {
                buffer.set(cx, base + i, cz, Blocks.DARK_OAK_LOG);
            }
        }
        for (int i = 1; i <= height; i++) {
            if (i % 6 == 0 || i == height) {
                BuildKit.walls(buffer, x - half, base + i, z - half, x + half, base + i, z + half,
                        Blocks.DARK_OAK_PLANKS);
            }
        }
        int deckY = base + height;
        BuildKit.box(buffer, x - half - 1, deckY, z - half - 1, x + half + 1, deckY, z + half + 1,
                Blocks.DARK_OAK_PLANKS);
        BuildKit.walls(buffer, x - half - 1, deckY + 1, z - half - 1, x + half + 1, deckY + 2, z + half + 1,
                Blocks.DARK_OAK_PLANKS);
        BuildKit.stairRoof(buffer, x - half - 1, deckY + 3, z - half - 1, x + half + 1, z + half + 1,
                "dark_oak", Blocks.DARK_OAK_PLANKS);
        buffer.set(x, deckY + 1, z - half - 1, Blocks.AIR);
        buffer.set(x, deckY + 1, z + half + 1, Blocks.AIR);
        BuildKit.hangingLantern(buffer, x, deckY + 3, z, Blocks.DARK_OAK_PLANKS);
        buffer.set(x + 1, deckY + 1, z, BlockShapes.wallBanner(2));

        // Ladder shaft all the way up.
        for (int y = base + 1; y <= deckY; y++) {
            buffer.set(x, y, z, Blocks.AIR);
            buffer.set(x, y, z + 1, BlockShapes.ladder(0));
        }

        // Palisade and outbuildings.
        int fenceRadius = 10;
        for (int i = -fenceRadius; i <= fenceRadius; i++) {
            for (int side = 0; side < 4; side++) {
                int fx = side < 2 ? x + i : (side == 2 ? x - fenceRadius : x + fenceRadius);
                int fz = side < 2 ? (side == 0 ? z - fenceRadius : z + fenceRadius) : z + i;
                int ground = context.height(fx, fz);
                if (Math.abs(i) % 7 == 0) {
                    for (int h = 1; h <= 3; h++) {
                        buffer.set(fx, ground + h, fz, Blocks.DARK_OAK_LOG);
                    }
                    buffer.set(fx, ground + 4, fz, Blocks.LANTERN);
                } else {
                    buffer.set(fx, ground + 1, fz, Blocks.DARK_OAK_FENCE);
                    buffer.set(fx, ground + 2, fz, Blocks.DARK_OAK_FENCE);
                }
            }
        }
        // Cage and tents.
        int cageX = x + 6;
        int cageZ = z + 5;
        int cageY = context.height(cageX, cageZ) + 1;
        BuildKit.box(buffer, cageX - 1, cageY, cageZ - 1, cageX + 1, cageY + 2, cageZ + 1, Blocks.IRON_BARS);
        BuildKit.box(buffer, cageX, cageY, cageZ, cageX, cageY + 1, cageZ, Blocks.AIR);
        buffer.addSpawn(MobSpawn.mob(cageX, cageY, cageZ, "IRON_GOLEM", 0));
        for (int i = 0; i < 3; i++) {
            int tx = x + random.nextInt(-8, 8);
            int tz = z + random.nextInt(-8, 8);
            int ty = context.height(tx, tz) + 1;
            BuildKit.box(buffer, tx - 1, ty, tz - 1, tx + 1, ty, tz + 1, Blocks.WHITE_TERRACOTTA);
            buffer.set(tx, ty + 1, tz, Blocks.CAMPFIRE);
            buffer.addSpawn(MobSpawn.mob(tx, ty + 1, tz, "PILLAGER", 2));
        }

        BuildKit.chest(buffer, x - 1, deckY + 1, z - 1, 2, "outpost");
        BuildKit.chest(buffer, x + 1, base + 1, z + 1, 2, "outpost");
        buffer.addSpawn(MobSpawn.boss(x, deckY + 1, z, "PILLAGER", 4, "outpost_captain"));
        for (int i = 0; i < 4; i++) {
            buffer.addSpawn(MobSpawn.mob(x + random.nextInt(-6, 6), base + 1, z + random.nextInt(-6, 6),
                    random.chance(0.3) ? "VINDICATOR" : "PILLAGER", 2));
        }
    }
}
