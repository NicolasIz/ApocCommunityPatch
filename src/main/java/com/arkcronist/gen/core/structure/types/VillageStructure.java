package com.arkcronist.gen.core.structure.types;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.BlockShapes;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.structure.*;

/**
 * A village that looks lived in.
 *
 * <p>A central plaza with a roofed well and a bell, paved lanes with lamp posts that follow the
 * ground, and houses that each level their own plot, drop a foundation, and come furnished: beds,
 * a work station, a lit interior, glazed windows, a stair roof and a chimney. Around them, fenced
 * farm plots with irrigation channels, a market stall or two, and the villagers themselves.</p>
 */
public final class VillageStructure implements Structure {

    @Override
    public String id() {
        return "village";
    }

    @Override
    public StructureTag tag() {
        return StructureTag.VILLAGE;
    }

    @Override
    public int radius() {
        return 48;
    }

    @Override
    public double weight() {
        return 1.6;
    }

    @Override
    public boolean canPlace(StructureContext context) {
        return !context.submerged(context.originX, context.originZ)
                && context.groundY > context.seaLevel()
                && context.relief(context.originX, context.originZ, 16) < 20;
    }

    @Override
    public void build(StructureContext context, StructureBuffer buffer) {
        FastRandom random = context.random;
        StructureMaterials materials = StructureMaterials.forBiome(context.biome, random);
        int x = context.originX;
        int z = context.originZ;
        int centre = context.averageHeight(12);

        plaza(context, buffer, random, materials, x, centre, z);

        int lanes = random.nextInt(3, 5);
        double[] angles = new double[lanes];
        for (int i = 0; i < lanes; i++) {
            angles[i] = (i / (double) lanes) * Math.PI * 2.0 + random.nextDouble(-0.2, 0.2);
            lane(context, buffer, materials, x, z, angles[i], random.nextInt(20, 36));
        }

        int houses = random.nextInt(7, 13);
        for (int i = 0; i < houses; i++) {
            double angle = angles[i % lanes] + random.nextDouble(-0.45, 0.45);
            int distance = random.nextInt(9, 30);
            int hx = x + (int) Math.round(Math.cos(angle) * distance);
            int hz = z + (int) Math.round(Math.sin(angle) * distance);
            if (context.submerged(hx, hz) || context.relief(hx, hz, 4) > 7) {
                continue;
            }
            house(context, buffer, random, materials, hx, hz);
        }

        int farms = random.nextInt(2, 5);
        for (int i = 0; i < farms; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            int distance = random.nextInt(12, 28);
            int fx = x + (int) Math.round(Math.cos(angle) * distance);
            int fz = z + (int) Math.round(Math.sin(angle) * distance);
            if (context.submerged(fx, fz)) {
                continue;
            }
            farm(context, buffer, random, materials, fx, fz);
        }
    }

    private void plaza(StructureContext context, StructureBuffer buffer, FastRandom random,
                       StructureMaterials materials, int x, int y, int z) {
        String stone = materials.stoneFamily;
        for (int ox = -6; ox <= 6; ox++) {
            for (int oz = -6; oz <= 6; oz++) {
                if (ox * ox + oz * oz > 40) {
                    continue;
                }
                buffer.set(x + ox, y, z + oz, (ox + oz) % 3 == 0 ? materials.wallAccent : materials.floor);
                BuildKit.foundation(context, buffer, x + ox, z + oz, y, materials.wall, 10);
                BuildKit.clear(buffer, x + ox, y + 1, z + oz, x + ox, y + 6, z + oz);
            }
        }

        // Well with a roof, the heart of the village.
        BuildKit.walls(buffer, x - 2, y, z - 2, x + 2, y + 1, z + 2, materials.wallAccent);
        BuildKit.box(buffer, x - 1, y - 4, z - 1, x + 1, y, z + 1, Blocks.WATER);
        BuildKit.walls(buffer, x - 2, y - 5, z - 2, x + 2, y - 1, z + 2, materials.wall);
        for (int corner = 0; corner < 4; corner++) {
            int cx = (corner & 1) == 0 ? x - 2 : x + 2;
            int cz = (corner & 2) == 0 ? z - 2 : z + 2;
            for (int i = 2; i <= 4; i++) {
                buffer.set(cx, y + i, cz, BlockShapes.fence(materials.woodFamily));
            }
        }
        BuildKit.stairRoof(buffer, x - 2, y + 5, z - 2, x + 2, z + 2, materials.woodFamily, materials.plank);
        buffer.set(x, y + 4, z, Blocks.LANTERN);

        // Bell on a post, where the villagers gather.
        buffer.set(x + 4, y + 1, z, BlockShapes.fence(materials.woodFamily));
        buffer.set(x + 4, y + 2, z, BlockShapes.fence(materials.woodFamily));
        buffer.set(x + 4, y + 3, z, materials.plank);
        buffer.set(x + 4, y + 2, z + 1, Blocks.BELL_CEILING);
        buffer.addSpawn(MobSpawn.mob(x + 3, y + 1, z + 1, "VILLAGER", 0));
        buffer.addSpawn(MobSpawn.mob(x - 3, y + 1, z - 1, "IRON_GOLEM", 0));

        // A couple of market stalls.
        for (int i = 0; i < 2; i++) {
            int sx = x + random.nextInt(-5, 5);
            int sz = z + random.nextInt(-5, 5);
            for (int corner = 0; corner < 4; corner++) {
                int cx = sx + ((corner & 1) == 0 ? -1 : 1);
                int cz = sz + ((corner & 2) == 0 ? -1 : 1);
                buffer.set(cx, y + 1, cz, BlockShapes.fence(materials.woodFamily));
                buffer.set(cx, y + 2, cz, BlockShapes.fence(materials.woodFamily));
            }
            BuildKit.box(buffer, sx - 1, y + 3, sz - 1, sx + 1, y + 3, sz + 1, Blocks.WHITE_CARPET);
            buffer.set(sx, y + 1, sz, Blocks.BARREL_UP);
            buffer.addSpawn(MobSpawn.mob(sx, y + 1, sz + 1, "VILLAGER", 0));
        }
    }

    private void lane(StructureContext context, StructureBuffer buffer, StructureMaterials materials,
                      int x, int z, double angle, int length) {
        double dx = Math.cos(angle);
        double dz = Math.sin(angle);
        for (int step = 5; step <= length; step++) {
            int px = x + (int) Math.round(dx * step);
            int pz = z + (int) Math.round(dz * step);
            int py = context.height(px, pz);
            for (int ox = -1; ox <= 1; ox++) {
                for (int oz = -1; oz <= 1; oz++) {
                    buffer.set(px + ox, py, pz + oz, (ox + oz) % 2 == 0 ? materials.floor : materials.wallAccent);
                    BuildKit.clear(buffer, px + ox, py + 1, pz + oz, px + ox, py + 3, pz + oz);
                }
            }
            if (step % 9 == 0) {
                BuildKit.lampPost(buffer, px + 2, py, pz, BlockShapes.fence(materials.woodFamily), 3);
            }
        }
    }

    private void house(StructureContext context, StructureBuffer buffer, FastRandom random,
                       StructureMaterials materials, int x, int z) {
        String wood = materials.woodFamily;
        String stone = materials.stoneFamily;
        int halfX = random.nextInt(3, 6);
        int halfZ = random.nextInt(3, 6);
        int height = 4;
        int base = plotLevel(context, x, z, Math.max(halfX, halfZ));

        BuildKit.clear(buffer, x - halfX - 1, base + 1, z - halfZ - 1, x + halfX + 1, base + height + 8,
                z + halfZ + 1);
        BuildKit.foundationArea(context, buffer, x - halfX, z - halfZ, x + halfX, z + halfZ, base,
                materials.wall, 14);

        // Stone plinth, timber frame, plank infill.
        BuildKit.box(buffer, x - halfX, base, z - halfZ, x + halfX, base, z + halfZ, materials.floor);
        BuildKit.walls(buffer, x - halfX, base + 1, z - halfZ, x + halfX, base + height, z + halfZ,
                materials.plank);
        for (int corner = 0; corner < 4; corner++) {
            int cx = (corner & 1) == 0 ? x - halfX : x + halfX;
            int cz = (corner & 2) == 0 ? z - halfZ : z + halfZ;
            for (int i = 1; i <= height; i++) {
                buffer.set(cx, base + i, cz, materials.pillar);
            }
        }
        for (int ox = -halfX; ox <= halfX; ox++) {
            buffer.set(x + ox, base + 1, z - halfZ, materials.wall);
            buffer.set(x + ox, base + 1, z + halfZ, materials.wall);
        }
        for (int oz = -halfZ; oz <= halfZ; oz++) {
            buffer.set(x - halfX, base + 1, z + oz, materials.wall);
            buffer.set(x + halfX, base + 1, z + oz, materials.wall);
        }

        BuildKit.stairRoof(buffer, x - halfX, base + height + 1, z - halfZ, x + halfX, z + halfZ,
                wood, materials.plank);

        // Door with a small porch, windows on every wall.
        buffer.set(x, base + 1, z - halfZ, BlockShapes.door(wood, 2, false, false));
        buffer.set(x, base + 2, z - halfZ, BlockShapes.door(wood, 2, true, false));
        buffer.set(x, base, z - halfZ - 1, BlockShapes.stairs(stone, 0, false));
        BuildKit.wallTorch(buffer, x - 1, base + 3, z - halfZ, 0, -1, false);
        for (int offset = -halfX + 2; offset <= halfX - 2; offset += 3) {
            buffer.set(x + offset, base + 3, z - halfZ, materials.glass);
            buffer.set(x + offset, base + 3, z + halfZ, materials.glass);
        }
        for (int offset = -halfZ + 2; offset <= halfZ - 2; offset += 3) {
            buffer.set(x - halfX, base + 3, z + offset, materials.glass);
            buffer.set(x + halfX, base + 3, z + offset, materials.glass);
        }

        // Interior: light, bed, work station, storage.
        BuildKit.hangingLantern(buffer, x, base + height, z, materials.plank);
        buffer.set(x - halfX + 1, base + 1, z + halfZ - 1, BlockShapes.bed(0, false));
        buffer.set(x - halfX + 1, base + 1, z + halfZ - 2, BlockShapes.bed(0, true));
        buffer.set(x + halfX - 1, base + 1, z + halfZ - 1, workstation(random));
        buffer.set(x + halfX - 1, base + 1, z - halfZ + 1, Blocks.BARREL_UP);
        BuildKit.chest(buffer, x - halfX + 1, base + 1, z - halfZ + 1, 1, "village");

        // Chimney: a small thing that makes a roof read as a house.
        if (random.chance(0.5)) {
            int cx = x + halfX - 1;
            int cz = z - halfZ + 1;
            for (int i = 1; i <= height + halfZ + 2; i++) {
                buffer.set(cx, base + i, cz, materials.wall);
            }
            buffer.set(cx, base + height + halfZ + 3, cz, BlockShapes.slab(stone, false));
            buffer.set(cx, base + 1, cz, Blocks.CAMPFIRE);
        }

        buffer.addSpawn(MobSpawn.mob(x, base + 1, z, "VILLAGER", 0));
        if (random.chance(0.25)) {
            buffer.addSpawn(MobSpawn.mob(x + 1, base + 1, z, "CAT", 0));
        }
    }

    private void farm(StructureContext context, StructureBuffer buffer, FastRandom random,
                      StructureMaterials materials, int x, int z) {
        int halfX = random.nextInt(3, 6);
        int halfZ = random.nextInt(3, 6);
        int base = plotLevel(context, x, z, Math.max(halfX, halfZ));
        BuildKit.clear(buffer, x - halfX, base + 1, z - halfZ, x + halfX, base + 3, z + halfZ);
        for (int ox = -halfX; ox <= halfX; ox++) {
            for (int oz = -halfZ; oz <= halfZ; oz++) {
                boolean border = Math.abs(ox) == halfX || Math.abs(oz) == halfZ;
                if (border) {
                    buffer.set(x + ox, base, z + oz, materials.floor);
                    buffer.set(x + ox, base + 1, z + oz, BlockShapes.fence(materials.woodFamily));
                } else if (oz % 3 == 0) {
                    buffer.set(x + ox, base, z + oz, Blocks.WATER);
                } else {
                    buffer.set(x + ox, base, z + oz, Blocks.DIRT);
                    if (random.chance(0.75)) {
                        buffer.set(x + ox, base + 1, z + oz, random.chance(0.5)
                                ? Blocks.HAY_BLOCK : Blocks.SHORT_GRASS);
                    }
                }
            }
        }
        buffer.set(x, base + 1, z - halfZ, Blocks.AIR);
        buffer.set(x + halfX - 1, base + 1, z + halfZ - 1, Blocks.COMPOSTER);
        buffer.addSpawn(MobSpawn.mob(x, base + 1, z, "VILLAGER", 0));
    }

    private static int workstation(FastRandom random) {
        return switch (random.nextInt(8)) {
            case 0 -> Blocks.CRAFTING_TABLE;
            case 1 -> Blocks.SMITHING_TABLE;
            case 2 -> Blocks.FLETCHING_TABLE;
            case 3 -> Blocks.CARTOGRAPHY_TABLE;
            case 4 -> Blocks.LOOM;
            case 5 -> Blocks.STONECUTTER;
            case 6 -> Blocks.SMOKER;
            default -> Blocks.BREWING_STAND;
        };
    }

    /** Median-ish ground level of a plot, so a house is neither buried nor on stilts. */
    private int plotLevel(StructureContext context, int x, int z, int radius) {
        int sum = 0;
        int samples = 0;
        for (int ox = -radius; ox <= radius; ox += radius) {
            for (int oz = -radius; oz <= radius; oz += radius) {
                sum += context.height(x + ox, z + oz);
                samples++;
            }
        }
        return sum / Math.max(1, samples);
    }
}
