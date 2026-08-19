package com.arkcronist.gen.core.structure.types;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.BlockShapes;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.structure.*;

/**
 * A tiered watchtower.
 *
 * <p>Three or four stepped storeys, each one narrower than the last and separated by a course of
 * upside-down stairs, an arched doorway on a raised terrace, arrow slits and windows on every face,
 * ivy creeping up the stone, and a lit beacon room at the top with its captain.</p>
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
        return 16;
    }

    @Override
    public double weight() {
        return 1.4;
    }

    @Override
    public boolean canPlace(StructureContext context) {
        return !context.submerged(context.originX, context.originZ)
                && context.groundY > context.seaLevel() - 2
                && context.relief(context.originX, context.originZ, 7) < 9;
    }

    @Override
    public void build(StructureContext context, StructureBuffer buffer) {
        FastRandom random = context.random;
        StructureMaterials materials = StructureMaterials.forBiome(context.biome, random);
        String stone = materials.stoneFamily;
        int x = context.originX;
        int z = context.originZ;
        int base = context.averageHeight(7);
        int tiers = random.nextInt(3, 5);
        int tierHeight = 6;
        int half = random.nextInt(3, 5);
        boolean overgrown = random.chance(0.65);

        if (base + tiers * tierHeight + 8 >= context.maxY()) {
            return;
        }

        // Terrace: a raised platform with a stair skirt, so the tower reads as built, not dropped.
        int terrace = half + 3;
        BuildKit.foundationArea(context, buffer, x - terrace, z - terrace, x + terrace, z + terrace,
                base, materials.wall, 20);
        BuildKit.box(buffer, x - terrace, base, z - terrace, x + terrace, base, z + terrace, materials.floor);
        BuildKit.clear(buffer, x - terrace, base + 1, z - terrace, x + terrace, base + tiers * tierHeight + 8,
                z + terrace);
        BuildKit.stairTrim(buffer, x - terrace, base, z - terrace, x + terrace, z + terrace, stone, false);
        for (int i = -terrace; i <= terrace; i++) {
            buffer.set(x + i, base + 1, z - terrace, BlockShapes.wall(stone));
            buffer.set(x + i, base + 1, z + terrace, BlockShapes.wall(stone));
            buffer.set(x - terrace, base + 1, z + i, BlockShapes.wall(stone));
            buffer.set(x + terrace, base + 1, z + i, BlockShapes.wall(stone));
        }
        BuildKit.staircase(buffer, x, base, z - terrace - 1, 3, 0, -1, 1, stone);

        for (int tier = 0; tier < tiers; tier++) {
            int y = base + 1 + tier * tierHeight;
            int tierHalf = Math.max(2, half - tier);

            BuildKit.walls(buffer, x - tierHalf, y, z - tierHalf, x + tierHalf, y + tierHeight - 1,
                    z + tierHalf, materials.wall);
            BuildKit.box(buffer, x - tierHalf + 1, y - 1, z - tierHalf + 1, x + tierHalf - 1, y - 1,
                    z + tierHalf - 1, materials.floor);
            // Corner quoins in the accent stone.
            for (int corner = 0; corner < 4; corner++) {
                int cx = (corner & 1) == 0 ? x - tierHalf : x + tierHalf;
                int cz = (corner & 2) == 0 ? z - tierHalf : z + tierHalf;
                for (int i = 0; i < tierHeight; i++) {
                    buffer.set(cx, y + i, cz, materials.wallAccent);
                }
            }
            // Cornice between storeys.
            BuildKit.stairTrim(buffer, x - tierHalf, y + tierHeight - 1, z - tierHalf,
                    x + tierHalf, z + tierHalf, stone, true);

            // Openings: a proper arched door at the bottom, windows above.
            if (tier == 0) {
                BuildKit.archway(buffer, x, y, z - tierHalf, 1, 3, true, stone);
                buffer.set(x, y, z - tierHalf, BlockShapes.door(materials.woodFamily, 2, false, false));
                buffer.set(x, y + 1, z - tierHalf, BlockShapes.door(materials.woodFamily, 2, true, false));
                BuildKit.wallTorch(buffer, x - 1, y + 2, z - tierHalf, 0, -1, false);
                BuildKit.wallTorch(buffer, x + 1, y + 2, z - tierHalf, 0, -1, false);
            } else {
                for (int face = 0; face < 4; face++) {
                    int wx = face == 1 ? x + tierHalf : face == 3 ? x - tierHalf : x;
                    int wz = face == 2 ? z + tierHalf : face == 0 ? z - tierHalf : z;
                    buffer.set(wx, y + 2, wz, materials.glass);
                    buffer.set(wx, y + 3, wz, materials.glass);
                    buffer.set(wx, y + 4, wz, BlockShapes.stairs(stone, face, true));
                }
            }

            // Interior: ladder up, a light hanging from the storey above, and something to find.
            BuildKit.ladder(buffer, x + tierHalf - 1, y, y + tierHeight - 1, z + tierHalf - 1,
                    BlockShapes.ladder(2));
            buffer.set(x + tierHalf - 1, y + tierHeight - 1, z + tierHalf - 1, Blocks.AIR);
            BuildKit.hangingLantern(buffer, x, y + tierHeight - 1, z, materials.floor);

            if (tier > 0 && random.chance(0.7)) {
                BuildKit.chest(buffer, x - tierHalf + 1, y, z - tierHalf + 1,
                        tier >= tiers - 2 ? 2 : 1, "tower");
            }
            if (tier > 0) {
                buffer.addSpawn(MobSpawn.mob(x, y, z, garrison(random), 1 + tier / 2));
            }
        }

        // Crown: battlements, braziers and the captain.
        int roofY = base + 1 + tiers * tierHeight;
        int roofHalf = Math.max(2, half - tiers + 1);
        BuildKit.box(buffer, x - roofHalf, roofY - 1, z - roofHalf, x + roofHalf, roofY - 1,
                z + roofHalf, materials.floor);
        for (int i = -roofHalf; i <= roofHalf; i++) {
            if (((i + roofHalf) & 1) == 0) {
                buffer.set(x + i, roofY, z - roofHalf, materials.wallAccent);
                buffer.set(x + i, roofY, z + roofHalf, materials.wallAccent);
                buffer.set(x - roofHalf, roofY, z + i, materials.wallAccent);
                buffer.set(x + roofHalf, roofY, z + i, materials.wallAccent);
            } else {
                buffer.set(x + i, roofY, z - roofHalf, BlockShapes.wall(stone));
                buffer.set(x + i, roofY, z + roofHalf, BlockShapes.wall(stone));
                buffer.set(x - roofHalf, roofY, z + i, BlockShapes.wall(stone));
                buffer.set(x + roofHalf, roofY, z + i, BlockShapes.wall(stone));
            }
        }
        buffer.set(x, roofY, z, Blocks.CAMPFIRE);
        buffer.set(x + 1, roofY, z, Blocks.LANTERN);
        BuildKit.chest(buffer, x - 1, roofY, z + 1, 3, "tower");
        buffer.addSpawn(MobSpawn.boss(x, roofY + 1, z, boss(random), Math.min(4, 1 + tiers), "tower_captain"));

        if (overgrown) {
            ivy(buffer, random, x, base, z, half, tiers * tierHeight);
        }
    }

    /** Vines down the faces: the difference between "a build" and "a build that has been there". */
    private void ivy(StructureBuffer buffer, FastRandom random, int x, int base, int z, int half, int height) {
        for (int i = 0; i < 26; i++) {
            int face = random.nextInt(4);
            int wx = face == 1 ? x + half : face == 3 ? x - half : x + random.nextInt(-half, half);
            int wz = face == 2 ? z + half : face == 0 ? z - half : z + random.nextInt(-half, half);
            int start = base + 2 + random.nextInt(0, Math.max(1, height - 4));
            int length = random.nextInt(2, 7);
            int vine = switch (face) {
                case 0 -> Blocks.VINE_SOUTH;
                case 1 -> Blocks.VINE_WEST;
                case 2 -> Blocks.VINE_NORTH;
                default -> Blocks.VINE_EAST;
            };
            int dx = face == 1 ? 1 : face == 3 ? -1 : 0;
            int dz = face == 2 ? 1 : face == 0 ? -1 : 0;
            for (int j = 0; j < length; j++) {
                buffer.set(wx + dx, start - j, wz + dz, vine);
            }
        }
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
