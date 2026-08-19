package com.arkcronist.gen.core.structure.types;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.BlockShapes;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.structure.*;

/**
 * A castle in pale stone with timber roofs.
 *
 * <p>Diorite and stone brick banding on the walls, square corner towers capped with tall spruce
 * spires, a gatehouse with an arched passage and flanking turrets, a great hall with a long pitched
 * roof, a keep with a spire of its own, crenellated curtain walls with a walkway, banners, lanterns
 * on every tower, and a courtyard with lawn, paths and garden beds.</p>
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
                && context.relief(context.originX, context.originZ, 18) < 13;
    }

    @Override
    public void build(StructureContext context, StructureBuffer buffer) {
        FastRandom random = context.random;
        Palette palette = Palette.forBiome(context, random);
        int x = context.originX;
        int z = context.originZ;
        int base = context.averageHeight(18);
        int half = random.nextInt(18, 24);
        int wallHeight = 9;

        bailey(context, buffer, palette, x, base, z, half);
        curtainWall(buffer, random, palette, x, base, z, half, wallHeight);
        for (int corner = 0; corner < 4; corner++) {
            int cx = (corner & 1) == 0 ? x - half : x + half;
            int cz = (corner & 2) == 0 ? z - half : z + half;
            tower(buffer, random, palette, cx, base, cz, wallHeight + random.nextInt(6, 10), 3, true);
        }
        gatehouse(context, buffer, random, palette, x, base, z - half, wallHeight);
        courtyard(buffer, random, palette, x, base, z, half);
        greatHall(buffer, random, palette, x - half / 2, base, z + half / 2, random.nextInt(6, 8),
                random.nextInt(4, 6));
        keep(context, buffer, random, palette, x + half / 3, base, z + half / 3, half);
    }

    /** Materials for a castle, chosen from the region but always reading as pale stone and timber. */
    private record Palette(int wall, int band, int floor, int trim, int roof, int plank,
                           String stoneFamily, String woodFamily) {

        static Palette forBiome(StructureContext context, FastRandom random) {
            StructureMaterials materials = StructureMaterials.forBiome(context.biome, random);
            boolean pale = random.chance(0.65);
            int wall = pale ? Blocks.DIORITE : materials.wall;
            int band = pale ? Blocks.STONE_BRICKS : materials.wallAccent;
            String stone = pale ? "diorite" : materials.stoneFamily;
            return new Palette(wall, band, Blocks.POLISHED_ANDESITE, Blocks.CHISELED_STONE_BRICKS,
                    Blocks.SPRUCE_PLANKS, materials.plank, stone, "spruce");
        }
    }

    private void bailey(StructureContext context, StructureBuffer buffer, Palette palette,
                        int x, int base, int z, int half) {
        for (int ox = -half - 1; ox <= half + 1; ox++) {
            for (int oz = -half - 1; oz <= half + 1; oz++) {
                boolean lawn = Math.abs(ox) < half - 1 && Math.abs(oz) < half - 1;
                buffer.set(x + ox, base, z + oz, lawn ? Blocks.GRASS_BLOCK : palette.floor());
                BuildKit.foundation(context, buffer, x + ox, z + oz, base, palette.wall(), 18);
                BuildKit.clear(buffer, x + ox, base + 1, z + oz, x + ox, base + 5, z + oz);
            }
        }
    }

    private void curtainWall(StructureBuffer buffer, FastRandom random, Palette palette,
                             int x, int base, int z, int half, int height) {
        String stone = palette.stoneFamily();
        // Banded wall: two courses of the accent stone break the mass up.
        for (int i = 1; i <= height; i++) {
            int block = (i == 3 || i == height - 1) ? palette.band() : palette.wall();
            BuildKit.walls(buffer, x - half, base + i, z - half, x + half, base + i, z + half, block);
            BuildKit.walls(buffer, x - half + 1, base + i, z - half + 1, x + half - 1, base + i,
                    z + half - 1, block);
        }
        // Walkway and cornice.
        BuildKit.walls(buffer, x - half + 1, base + height + 1, z - half + 1, x + half - 1,
                base + height + 1, z + half - 1, BlockShapes.slab(stone, false));
        BuildKit.stairTrim(buffer, x - half + 1, base + height, z - half + 1, x + half - 1, z + half - 1,
                stone, true);

        for (int i = -half; i <= half; i++) {
            boolean merlon = ((i + half) & 1) == 0;
            int block = merlon ? palette.band() : BlockShapes.wall(stone);
            buffer.set(x + i, base + height + 2, z - half, block);
            buffer.set(x + i, base + height + 2, z + half, block);
            buffer.set(x - half, base + height + 2, z + i, block);
            buffer.set(x + half, base + height + 2, z + i, block);
            if (((i + half) % 8) == 0) {
                BuildKit.wallTorch(buffer, x + i, base + height + 2, z - half, 0, 1, false);
                BuildKit.wallTorch(buffer, x + i, base + height + 2, z + half, 0, -1, false);
            }
            // Arrow slits.
            if (((i + half) % 5) == 0) {
                buffer.set(x + i, base + 5, z - half, Blocks.AIR);
                buffer.set(x + i, base + 5, z + half, Blocks.AIR);
                buffer.set(x - half, base + 5, z + i, Blocks.AIR);
                buffer.set(x + half, base + 5, z + i, Blocks.AIR);
            }
        }
        BuildKit.staircase(buffer, x - half + 2, base + height + 1, z - half + 2, height, 1, 0, 1, stone);
    }

    /** Square tower with a banded shaft and a tall timber spire. */
    private void tower(StructureBuffer buffer, FastRandom random, Palette palette,
                       int cx, int base, int cz, int height, int half, boolean spire) {
        String stone = palette.stoneFamily();
        String wood = palette.woodFamily();

        for (int i = 1; i <= height; i++) {
            int block = (i % 4 == 0) ? palette.band() : palette.wall();
            BuildKit.walls(buffer, cx - half, base + i, cz - half, cx + half, base + i, cz + half, block);
        }
        BuildKit.clear(buffer, cx - half + 1, base + 1, cz - half + 1, cx + half - 1, base + height,
                cz + half - 1);
        BuildKit.box(buffer, cx - half + 1, base, cz - half + 1, cx + half - 1, base, cz + half - 1,
                palette.floor());

        // Room at the top with a floor, a light and windows.
        int roomFloor = base + height - 4;
        BuildKit.box(buffer, cx - half + 1, roomFloor, cz - half + 1, cx + half - 1, roomFloor,
                cz + half - 1, palette.floor());
        BuildKit.hangingLantern(buffer, cx, base + height, cz, palette.floor());
        for (int face = 0; face < 4; face++) {
            int wx = face == 1 ? cx + half : face == 3 ? cx - half : cx;
            int wz = face == 2 ? cz + half : face == 0 ? cz - half : cz;
            buffer.set(wx, roomFloor + 2, wz, Blocks.GLASS_PANE);
            buffer.set(wx, roomFloor + 3, wz, BlockShapes.stairs(stone, face, true));
        }
        BuildKit.ladder(buffer, cx, base + 1, roomFloor, cz + half - 1, BlockShapes.ladder(2));
        buffer.set(cx, roomFloor, cz + half - 1, Blocks.AIR);

        // Machicolation: the overhanging course under the battlement.
        int capY = base + height + 1;
        BuildKit.stairTrim(buffer, cx - half, capY - 1, cz - half, cx + half, cz + half, stone, true);
        BuildKit.box(buffer, cx - half, capY, cz - half, cx + half, capY, cz + half,
                BlockShapes.slab(stone, false));
        for (int i = -half; i <= half; i++) {
            if (((i + half) & 1) == 0) {
                buffer.set(cx + i, capY + 1, cz - half, palette.band());
                buffer.set(cx + i, capY + 1, cz + half, palette.band());
                buffer.set(cx - half, capY + 1, cz + i, palette.band());
                buffer.set(cx + half, capY + 1, cz + i, palette.band());
            }
        }

        if (spire) {
            // Timber spire: stair courses stepping in to a point, with a lightning rod on top.
            int spireBase = capY + 2;
            for (int layer = 0; layer <= half + 1; layer++) {
                int r = half - layer;
                if (r < 0) {
                    break;
                }
                for (int ox = -r; ox <= r; ox++) {
                    for (int oz = -r; oz <= r; oz++) {
                        boolean edge = Math.abs(ox) == r || Math.abs(oz) == r;
                        if (!edge && layer < half) {
                            continue;
                        }
                        int block = edge && r > 0
                                ? BlockShapes.stairs(wood, BlockShapes.facingFrom(ox, oz), false)
                                : palette.roof();
                        buffer.set(cx + ox, spireBase + layer, cz + oz, block);
                    }
                }
            }
            buffer.set(cx, spireBase + half + 2, cz, Blocks.LIGHTNING_ROD);
            buffer.set(cx + half, capY + 2, cz, BlockShapes.wallBanner(1));
        }
        buffer.addSpawn(MobSpawn.mob(cx, roomFloor + 1, cz, "PILLAGER", 2));
        BuildKit.chest(buffer, cx + 1, roomFloor + 1, cz + 1, 2, "castle");
    }

    private void gatehouse(StructureContext context, StructureBuffer buffer, FastRandom random,
                           Palette palette, int x, int base, int gateZ, int wallHeight) {
        String stone = palette.stoneFamily();
        String wood = palette.woodFamily();

        BuildKit.clear(buffer, x - 2, base + 1, gateZ - 2, x + 2, base + 5, gateZ + 2);
        BuildKit.archway(buffer, x - 1, base + 1, gateZ, 3, 4, true, stone);
        for (int i = -1; i <= 1; i++) {
            buffer.set(x + i, base + 5, gateZ, BlockShapes.stairs(stone, 0, true));
            buffer.set(x + i, base + 4, gateZ, Blocks.IRON_BARS);
        }
        // Flanking turrets with spires.
        tower(buffer, random, palette, x - 4, base, gateZ, wallHeight + 5, 2, true);
        tower(buffer, random, palette, x + 4, base, gateZ, wallHeight + 5, 2, true);
        BuildKit.wallTorch(buffer, x - 2, base + 4, gateZ - 1, 0, -1, false);
        BuildKit.wallTorch(buffer, x + 2, base + 4, gateZ - 1, 0, -1, false);

        // Paved approach with lamp posts, cut into the slope with steps.
        for (int step = 1; step <= 16; step++) {
            int pz = gateZ - step;
            int ground = context.height(x, pz);
            for (int ox = -2; ox <= 2; ox++) {
                buffer.set(x + ox, ground, pz, Math.abs(ox) == 2 ? palette.band() : palette.floor());
                BuildKit.clear(buffer, x + ox, ground + 1, pz, x + ox, ground + 4, pz);
            }
            if (step % 6 == 0) {
                BuildKit.lampPost(buffer, x - 3, ground, pz, BlockShapes.fence(wood), 3);
                BuildKit.lampPost(buffer, x + 3, ground, pz, BlockShapes.fence(wood), 3);
            }
        }
        buffer.addSpawn(MobSpawn.mob(x, base + 1, gateZ + 3, "VINDICATOR", 2));
    }

    private void courtyard(StructureBuffer buffer, FastRandom random, Palette palette,
                           int x, int base, int z, int half) {
        for (int i = -half + 2; i <= half - 2; i++) {
            buffer.set(x + i, base, z, palette.floor());
            buffer.set(x, base, z + i, palette.floor());
        }
        BuildKit.gardenBed(buffer, random, x - half / 2, base, z - half / 2, 3, 2,
                Blocks.DIRT, BlockShapes.slab(palette.stoneFamily(), false));
        BuildKit.gardenBed(buffer, random, x + half / 2, base, z - half / 2, 3, 2,
                Blocks.DIRT, BlockShapes.slab(palette.stoneFamily(), false));
        // Well.
        int wx = x - half / 2;
        int wz = z + half / 3;
        BuildKit.walls(buffer, wx - 1, base, wz - 1, wx + 1, base + 1, wz + 1, palette.band());
        buffer.set(wx, base, wz, Blocks.WATER);
        buffer.set(wx, base - 1, wz, Blocks.WATER);
        for (int corner = 0; corner < 4; corner++) {
            int px = (corner & 1) == 0 ? wx - 1 : wx + 1;
            int pz = (corner & 2) == 0 ? wz - 1 : wz + 1;
            buffer.set(px, base + 2, pz, BlockShapes.fence(palette.woodFamily()));
            buffer.set(px, base + 3, pz, BlockShapes.fence(palette.woodFamily()));
        }
        BuildKit.stairRoof(buffer, wx - 1, base + 4, wz - 1, wx + 1, wz + 1, palette.woodFamily(),
                palette.roof());
        buffer.set(wx, base + 3, wz, Blocks.LANTERN);
        // Training ground.
        for (int i = 0; i < 4; i++) {
            int px = x + random.nextInt(-half + 4, half - 4);
            int pz = z + random.nextInt(-half + 4, half - 4);
            buffer.set(px, base + 1, pz, random.chance(0.5) ? Blocks.HAY_BLOCK : Blocks.ANVIL);
        }
    }

    /** Long hall with a pitched timber roof against the inside of the wall. */
    private void greatHall(StructureBuffer buffer, FastRandom random, Palette palette,
                           int x, int base, int z, int halfX, int halfZ) {
        String stone = palette.stoneFamily();
        String wood = palette.woodFamily();
        int height = 6;

        BuildKit.clear(buffer, x - halfX, base + 1, z - halfZ, x + halfX, base + height + halfZ + 4,
                z + halfZ);
        BuildKit.box(buffer, x - halfX, base, z - halfZ, x + halfX, base, z + halfZ, palette.floor());
        BuildKit.walls(buffer, x - halfX, base + 1, z - halfZ, x + halfX, base + height, z + halfZ,
                palette.wall());
        for (int ox = -halfX; ox <= halfX; ox += 3) {
            for (int i = 1; i <= height; i++) {
                buffer.set(x + ox, base + i, z - halfZ, Blocks.SPRUCE_LOG);
                buffer.set(x + ox, base + i, z + halfZ, Blocks.SPRUCE_LOG);
            }
        }
        for (int ox = -halfX + 1; ox <= halfX - 1; ox += 3) {
            buffer.set(x + ox, base + 3, z - halfZ, Blocks.GLASS_PANE);
            buffer.set(x + ox, base + 4, z - halfZ, Blocks.GLASS_PANE);
            buffer.set(x + ox, base + 3, z + halfZ, Blocks.GLASS_PANE);
            buffer.set(x + ox, base + 4, z + halfZ, Blocks.GLASS_PANE);
        }
        BuildKit.stairRoof(buffer, x - halfX, base + height + 1, z - halfZ, x + halfX, z + halfZ,
                wood, palette.roof());

        // Interior: long table, carpet runner, hearth, chandeliers.
        for (int ox = -halfX + 2; ox <= halfX - 2; ox++) {
            buffer.set(x + ox, base + 1, z, Blocks.SPRUCE_PLANKS);
            buffer.set(x + ox, base + 1, z - 1, Blocks.RED_CARPET);
            buffer.set(x + ox, base + 1, z + 1, Blocks.RED_CARPET);
        }
        for (int ox = -halfX + 3; ox <= halfX - 3; ox += 4) {
            BuildKit.chandelier(buffer, x + ox, base + height, z, palette.roof(), 1);
        }
        buffer.set(x - halfX + 1, base + 1, z, Blocks.CAMPFIRE);
        buffer.set(x, base + 1, z - halfZ, BlockShapes.door(wood, 0, false, false));
        buffer.set(x, base + 2, z - halfZ, BlockShapes.door(wood, 0, true, false));
        BuildKit.chest(buffer, x + halfX - 1, base + 1, z + halfZ - 1, 2, "castle");
        buffer.addSpawn(MobSpawn.mob(x, base + 1, z + 2, "VINDICATOR", 2));
    }

    private void keep(StructureContext context, StructureBuffer buffer, FastRandom random,
                      Palette palette, int x, int base, int z, int outerHalf) {
        String stone = palette.stoneFamily();
        String wood = palette.woodFamily();
        int half = Math.max(5, outerHalf / 3);
        int floors = random.nextInt(3, 5);
        int floorHeight = 5;
        int top = base + floors * floorHeight;

        BuildKit.clear(buffer, x - half, base + 1, z - half, x + half, top + half + 8, z + half);

        for (int floor = 0; floor < floors; floor++) {
            int y = base + 1 + floor * floorHeight;
            for (int i = 0; i < floorHeight; i++) {
                int block = (i == floorHeight - 1) ? palette.band() : palette.wall();
                BuildKit.walls(buffer, x - half, y + i, z - half, x + half, y + i, z + half, block);
            }
            BuildKit.box(buffer, x - half + 1, y - 1, z - half + 1, x + half - 1, y - 1, z + half - 1,
                    floor == 0 ? palette.floor() : Blocks.SPRUCE_PLANKS);
            for (int corner = 0; corner < 4; corner++) {
                int cx = (corner & 1) == 0 ? x - half : x + half;
                int cz = (corner & 2) == 0 ? z - half : z + half;
                for (int i = 0; i < floorHeight; i++) {
                    buffer.set(cx, y + i, cz, palette.trim());
                }
            }
            for (int offset = -half + 2; offset <= half - 2; offset += 3) {
                buffer.set(x + offset, y + 2, z - half, Blocks.GLASS_PANE);
                buffer.set(x + offset, y + 2, z + half, Blocks.GLASS_PANE);
                buffer.set(x - half, y + 2, z + offset, Blocks.GLASS_PANE);
                buffer.set(x + half, y + 2, z + offset, Blocks.GLASS_PANE);
            }
            BuildKit.chandelier(buffer, x, y + floorHeight - 1, z, Blocks.SPRUCE_PLANKS, 1);
            BuildKit.staircase(buffer, x + half - 2, y + floorHeight - 1, z + half - 2, floorHeight - 1,
                    0, -1, 0, stone);
            for (int i = 0; i < 1 + floor; i++) {
                buffer.addSpawn(MobSpawn.mob(x + random.nextInt(-half + 2, half - 2), y,
                        z + random.nextInt(-half + 2, half - 2),
                        floor == 0 ? "ZOMBIE" : "VINDICATOR", 2 + floor / 2));
            }
            if (random.chance(0.8)) {
                BuildKit.chest(buffer, x - half + 1, y, z + half - 1, Math.min(3, 1 + floor), "castle");
            }
        }

        // Throne room.
        int throneY = base + 1 + (floors - 1) * floorHeight;
        BuildKit.box(buffer, x - 2, throneY, z + half - 3, x + 2, throneY, z + half - 2, palette.band());
        buffer.set(x, throneY + 1, z + half - 2, BlockShapes.stairs(stone, 0, false));
        buffer.set(x - 1, throneY + 1, z + half - 2, BlockShapes.wall(stone));
        buffer.set(x + 1, throneY + 1, z + half - 2, BlockShapes.wall(stone));
        buffer.set(x - 2, throneY + 2, z + half - 1, BlockShapes.wallBanner(0));
        buffer.set(x + 2, throneY + 2, z + half - 1, BlockShapes.wallBanner(0));
        for (int ox = -2; ox <= 2; ox++) {
            for (int oz = -3; oz <= 2; oz++) {
                buffer.set(x + ox, throneY, z + oz, Blocks.RED_CARPET);
            }
        }
        BuildKit.chest(buffer, x - 1, throneY + 1, z + half - 3, 3, "castle");
        BuildKit.chest(buffer, x + 1, throneY + 1, z + half - 3, 3, "castle");
        buffer.addSpawn(MobSpawn.boss(x, throneY + 1, z, "EVOKER", 4, "castle_lord"));

        // Spire over the keep: the tallest thing in the build.
        BuildKit.stairTrim(buffer, x - half, top + 1, z - half, x + half, z + half, stone, true);
        BuildKit.box(buffer, x - half, top + 2, z - half, x + half, top + 2, z + half,
                BlockShapes.slab(stone, false));
        for (int layer = 0; layer <= half + 2; layer++) {
            int r = half - layer;
            if (r < 0) {
                break;
            }
            for (int ox = -r; ox <= r; ox++) {
                for (int oz = -r; oz <= r; oz++) {
                    boolean edge = Math.abs(ox) == r || Math.abs(oz) == r;
                    if (!edge && layer < half) {
                        continue;
                    }
                    buffer.set(x + ox, top + 3 + layer, z + oz, edge && r > 0
                            ? BlockShapes.stairs(wood, BlockShapes.facingFrom(ox, oz), false)
                            : palette.roof());
                }
            }
        }
        buffer.set(x, top + half + 5, z, Blocks.LIGHTNING_ROD);
    }
}
