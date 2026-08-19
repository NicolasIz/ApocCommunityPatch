package com.arkcronist.gen.core.structure.types;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.BlockShapes;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.structure.*;

/**
 * A stronghold buried in the rock: corridors of aged stone brick, a pillared library, cells, a
 * fountain hall and the portal room with its silverfish nest.
 */
public final class StrongholdStructure implements Structure {

    @Override
    public Placement placement() {
        return Placement.UNDERGROUND;
    }

    @Override
    public String id() {
        return "stronghold";
    }

    @Override
    public StructureTag tag() {
        return StructureTag.STRONGHOLD;
    }

    @Override
    public int radius() {
        return 52;
    }

    @Override
    public double weight() {
        return 0.5;
    }

    @Override
    public boolean canPlace(StructureContext context) {
        return context.groundY > context.seaLevel() - 6
                && context.groundY > context.engine().settings().minY + 60;
    }

    @Override
    public void build(StructureContext context, StructureBuffer buffer) {
        FastRandom random = context.random;
        int x = context.originX;
        int z = context.originZ;
        int minY = context.engine().settings().minY;
        int y = Math.max(minY + 12, Math.min(context.groundY - 30, 14) - random.nextInt(0, 16));

        // Rooms placed around a ring, joined by corridors.
        int rooms = random.nextInt(5, 9);
        int previousX = x;
        int previousZ = z;
        for (int i = 0; i < rooms; i++) {
            double angle = (i / (double) rooms) * Math.PI * 2.0 + random.nextDouble(-0.3, 0.3);
            int distance = random.nextInt(12, 30);
            int rx = x + (int) Math.round(Math.cos(angle) * distance);
            int rz = z + (int) Math.round(Math.sin(angle) * distance);
            room(buffer, random, rx, y, rz, i);
            corridor(buffer, random, previousX, y, previousZ, rx, rz);
            previousX = rx;
            previousZ = rz;
        }
        library(buffer, random, x + random.nextInt(-8, 8), y, z + random.nextInt(-8, 8));
        portalRoom(buffer, random, x, y - 6, z);
        // Stair well up towards the surface, so the place is findable.
        BuildKit.staircase(buffer, x, y + 12, z + 4, 12, 0, 1, 1, "stone_brick");
    }

    private int aged(FastRandom random) {
        double roll = random.nextDouble();
        if (roll < 0.55) {
            return Blocks.STONE_BRICKS;
        }
        if (roll < 0.75) {
            return Blocks.MOSSY_STONE_BRICKS;
        }
        if (roll < 0.92) {
            return Blocks.CRACKED_STONE_BRICKS;
        }
        return Blocks.INFESTED_STONE_BRICKS;
    }

    private void room(StructureBuffer buffer, FastRandom random, int x, int y, int z, int index) {
        int halfX = random.nextInt(4, 7);
        int halfZ = random.nextInt(4, 7);
        int height = 5;
        for (int ox = -halfX; ox <= halfX; ox++) {
            for (int oz = -halfZ; oz <= halfZ; oz++) {
                for (int oy = -1; oy <= height; oy++) {
                    boolean shell = ox == -halfX || ox == halfX || oz == -halfZ || oz == halfZ
                            || oy == -1 || oy == height;
                    buffer.set(x + ox, y + oy, z + oz, shell ? aged(random) : Blocks.AIR);
                }
            }
        }
        BuildKit.torchRing(buffer, x - halfX + 1, y + 3, z - halfZ + 1, x + halfX - 1, z + halfZ - 1, 3,
                Blocks.TORCH);
        for (int corner = 0; corner < 4; corner++) {
            int cx = (corner & 1) == 0 ? x - halfX + 1 : x + halfX - 1;
            int cz = (corner & 2) == 0 ? z - halfZ + 1 : z + halfZ - 1;
            BuildKit.column(buffer, cx, y, cz, height, Blocks.STONE_BRICKS, "stone_brick");
        }

        switch (index % 4) {
            case 0 -> {
                // Prison cells.
                for (int i = -halfX + 1; i <= halfX - 1; i += 3) {
                    buffer.set(x + i, y, z + halfZ - 1, Blocks.IRON_BARS);
                    buffer.set(x + i, y + 1, z + halfZ - 1, Blocks.IRON_BARS);
                    buffer.set(x + i, y + 2, z + halfZ - 1, Blocks.IRON_BARS);
                }
                buffer.addSpawn(MobSpawn.mob(x, y, z, "ZOMBIE", 2));
            }
            case 1 -> {
                // Fountain.
                BuildKit.box(buffer, x - 2, y, z - 2, x + 2, y, z + 2, Blocks.STONE_BRICKS);
                BuildKit.box(buffer, x - 1, y, z - 1, x + 1, y, z + 1, Blocks.WATER);
                buffer.set(x, y + 1, z, Blocks.STONE_BRICKS);
                buffer.set(x, y + 2, z, Blocks.WATER);
            }
            case 2 -> {
                // Storage.
                BuildKit.chest(buffer, x - halfX + 1, y, z - halfZ + 1, 2, "stronghold");
                buffer.set(x - halfX + 2, y, z - halfZ + 1, Blocks.CRAFTING_TABLE);
                BuildKit.spawner(buffer, x, y, z, "SILVERFISH");
            }
            default -> {
                buffer.addSpawn(MobSpawn.mob(x, y, z, "SKELETON", 2));
                BuildKit.chest(buffer, x + halfX - 1, y, z + halfZ - 1, 2, "stronghold");
            }
        }
    }

    private void corridor(StructureBuffer buffer, FastRandom random, int x0, int y, int z0, int x1, int z1) {
        int x = x0;
        int z = z0;
        while (x != x1 || z != z1) {
            if (x != x1) {
                x += Integer.signum(x1 - x);
            } else {
                z += Integer.signum(z1 - z);
            }
            for (int ox = -2; ox <= 2; ox++) {
                for (int oz = -2; oz <= 2; oz++) {
                    for (int oy = -1; oy <= 4; oy++) {
                        boolean shell = Math.abs(ox) == 2 || Math.abs(oz) == 2 || oy == -1 || oy == 4;
                        if (shell) {
                            buffer.set(x + ox, y + oy, z + oz, aged(random));
                        } else {
                            buffer.set(x + ox, y + oy, z + oz, Blocks.AIR);
                        }
                    }
                }
            }
            if (random.chance(0.10)) {
                BuildKit.wallTorch(buffer, x - 2, y + 2, z, 1, 0, false);
            }
        }
    }

    private void library(StructureBuffer buffer, FastRandom random, int x, int y, int z) {
        int halfX = 7;
        int halfZ = 6;
        int height = 8;
        for (int ox = -halfX; ox <= halfX; ox++) {
            for (int oz = -halfZ; oz <= halfZ; oz++) {
                for (int oy = -1; oy <= height; oy++) {
                    boolean shell = ox == -halfX || ox == halfX || oz == -halfZ || oz == halfZ
                            || oy == -1 || oy == height;
                    buffer.set(x + ox, y + oy, z + oz, shell ? aged(random) : Blocks.AIR);
                }
            }
        }
        // Two storeys of shelves with a wooden gallery.
        for (int oz = -halfZ + 1; oz <= halfZ - 1; oz++) {
            for (int level = 0; level < 2; level++) {
                int shelfY = y + level * 4;
                buffer.set(x - halfX + 1, shelfY, z + oz, Blocks.BOOKSHELF);
                buffer.set(x - halfX + 1, shelfY + 1, z + oz, Blocks.BOOKSHELF);
                buffer.set(x + halfX - 1, shelfY, z + oz, Blocks.BOOKSHELF);
                buffer.set(x + halfX - 1, shelfY + 1, z + oz, Blocks.BOOKSHELF);
            }
        }
        for (int ox = -halfX + 2; ox <= halfX - 2; ox++) {
            buffer.set(x + ox, y + 3, z - halfZ + 2, Blocks.OAK_PLANKS);
            buffer.set(x + ox, y + 3, z + halfZ - 2, Blocks.OAK_PLANKS);
        }
        BuildKit.ladder(buffer, x, y + 1, y + 3, z + halfZ - 2, BlockShapes.ladder(0));
        BuildKit.chandelier(buffer, x, y + height - 1, z, Blocks.STONE_BRICKS, 2);
        buffer.set(x, y, z - 1, Blocks.LECTERN);
        BuildKit.chest(buffer, x, y, z + 1, 3, "stronghold");
        BuildKit.chest(buffer, x + 2, y + 4, z, 3, "stronghold");
        for (int i = 0; i < 8; i++) {
            buffer.set(x + random.nextInt(-halfX + 2, halfX - 2), y + random.nextInt(0, 5),
                    z + random.nextInt(-halfZ + 2, halfZ - 2), Blocks.COBWEB);
        }
    }

    private void portalRoom(StructureBuffer buffer, FastRandom random, int x, int y, int z) {
        int half = 6;
        for (int ox = -half; ox <= half; ox++) {
            for (int oz = -half; oz <= half; oz++) {
                for (int oy = -1; oy <= 7; oy++) {
                    boolean shell = Math.abs(ox) == half || Math.abs(oz) == half || oy == -1 || oy == 7;
                    buffer.set(x + ox, y + oy, z + oz, shell ? aged(random) : Blocks.AIR);
                }
            }
        }
        // Lava moat under the frame.
        BuildKit.box(buffer, x - 3, y - 1, z - 3, x + 3, y - 1, z + 3, Blocks.LAVA);
        BuildKit.box(buffer, x - 2, y, z - 2, x + 2, y, z + 2, Blocks.STONE_BRICKS);

        // End portal frame, missing most of its eyes.
        for (int i = -1; i <= 1; i++) {
            buffer.set(x + i, y + 1, z - 2, Blocks.END_PORTAL_FRAME_SOUTH);
            buffer.set(x + i, y + 1, z + 2, Blocks.END_PORTAL_FRAME_NORTH);
            buffer.set(x - 2, y + 1, z + i, Blocks.END_PORTAL_FRAME_EAST);
            buffer.set(x + 2, y + 1, z + i, Blocks.END_PORTAL_FRAME_WEST);
        }
        BuildKit.staircase(buffer, x, y + 6, z - half + 1, 5, 0, 1, 2, "stone_brick");
        BuildKit.spawner(buffer, x + 3, y + 1, z + 3, "SILVERFISH");
        BuildKit.torchRing(buffer, x - half + 1, y + 3, z - half + 1, x + half - 1, z + half - 1, 4,
                Blocks.TORCH);
        buffer.addSpawn(MobSpawn.boss(x, y + 1, z + 4, "WITHER_SKELETON", 4, "cave_horror"));
    }
}
