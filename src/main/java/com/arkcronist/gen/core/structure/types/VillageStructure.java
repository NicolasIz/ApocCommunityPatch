package com.arkcronist.gen.core.structure.types;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.structure.*;

/**
 * A large village: a well at the centre, radiating paths, houses that each level their own plot,
 * farm plots, fences and villagers.
 *
 * <p>Houses are placed along the paths and every one of them cuts its own platform into the terrain
 * and drops a foundation, so a village can sit on rolling ground without a single floating door.</p>
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
        return 46;
    }

    @Override
    public double weight() {
        return 1.6;
    }

    @Override
    public boolean canPlace(StructureContext context) {
        return !context.submerged(context.originX, context.originZ)
                && context.groundY > context.seaLevel()
                && context.relief(context.originX, context.originZ, 16) < 22;
    }

    @Override
    public void build(StructureContext context, StructureBuffer buffer) {
        FastRandom random = context.random;
        StructureMaterials materials = StructureMaterials.forBiome(context.biome, random);
        int x = context.originX;
        int z = context.originZ;
        int centre = context.averageHeight(12);

        buildWell(buffer, materials, x, centre, z);

        int roads = random.nextInt(3, 5);
        int houses = random.nextInt(6, 13);
        double[] angles = new double[roads];
        for (int i = 0; i < roads; i++) {
            angles[i] = (i / (double) roads) * Math.PI * 2.0 + random.nextDouble(-0.25, 0.25);
            path(context, buffer, materials, x, z, angles[i], random.nextInt(18, 34));
        }

        for (int i = 0; i < houses; i++) {
            double angle = angles[i % roads] + random.nextDouble(-0.35, 0.35);
            int distance = random.nextInt(8, 30);
            int hx = x + (int) Math.round(Math.cos(angle) * distance);
            int hz = z + (int) Math.round(Math.sin(angle) * distance);
            if (context.submerged(hx, hz)) {
                continue;
            }
            buildHouse(context, buffer, random, materials, hx, hz);
        }

        int farms = random.nextInt(2, 5);
        for (int i = 0; i < farms; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            int distance = random.nextInt(10, 26);
            int fx = x + (int) Math.round(Math.cos(angle) * distance);
            int fz = z + (int) Math.round(Math.sin(angle) * distance);
            buildFarm(context, buffer, random, materials, fx, fz);
        }
    }

    private void buildWell(StructureBuffer buffer, StructureMaterials materials, int x, int y, int z) {
        BuildKit.clear(buffer, x - 2, y + 1, z - 2, x + 2, y + 5, z + 2);
        BuildKit.walls(buffer, x - 2, y, z - 2, x + 2, y + 1, z + 2, materials.wall);
        BuildKit.box(buffer, x - 1, y - 3, z - 1, x + 1, y - 1, z + 1, Blocks.WATER);
        BuildKit.walls(buffer, x - 2, y - 4, z - 2, x + 2, y - 1, z + 2, materials.wall);
        for (int corner = 0; corner < 4; corner++) {
            int cx = (corner & 1) == 0 ? x - 2 : x + 2;
            int cz = (corner & 2) == 0 ? z - 2 : z + 2;
            for (int i = 2; i <= 4; i++) {
                buffer.set(cx, y + i, cz, materials.fence);
            }
        }
        BuildKit.box(buffer, x - 2, y + 5, z - 2, x + 2, y + 5, z + 2, materials.slab);
        buffer.set(x, y + 4, z, materials.hangingLight);
    }

    private void path(StructureContext context, StructureBuffer buffer, StructureMaterials materials,
                      int x, int z, double angle, int length) {
        double dx = Math.cos(angle);
        double dz = Math.sin(angle);
        for (int step = 3; step <= length; step++) {
            int px = x + (int) Math.round(dx * step);
            int pz = z + (int) Math.round(dz * step);
            int py = context.height(px, pz);
            for (int ox = -1; ox <= 1; ox++) {
                for (int oz = -1; oz <= 1; oz++) {
                    buffer.set(px + ox, py, pz + oz, materials.floor);
                    buffer.set(px + ox, py + 1, pz + oz, Blocks.AIR);
                }
            }
            if (step % 8 == 0) {
                buffer.set(px + 2, py + 1, pz, materials.fence);
                buffer.set(px + 2, py + 2, pz, materials.fence);
                buffer.set(px + 2, py + 3, pz, materials.light);
            }
        }
    }

    private void buildHouse(StructureContext context, StructureBuffer buffer, FastRandom random,
                            StructureMaterials materials, int x, int z) {
        int halfX = random.nextInt(3, 6);
        int halfZ = random.nextInt(3, 6);
        int height = random.nextInt(4, 6);
        int base = averagePlot(context, x, z, Math.max(halfX, halfZ));

        BuildKit.clear(buffer, x - halfX - 1, base + 1, z - halfZ - 1, x + halfX + 1, base + height + 6, z + halfZ + 1);
        BuildKit.foundationArea(context, buffer, x - halfX, z - halfZ, x + halfX, z + halfZ, base,
                materials.wall, 14);
        BuildKit.box(buffer, x - halfX, base, z - halfZ, x + halfX, base, z + halfZ, materials.floor);
        BuildKit.walls(buffer, x - halfX, base + 1, z - halfZ, x + halfX, base + height, z + halfZ, materials.plank);

        // Corner posts give the timber-frame look.
        for (int corner = 0; corner < 4; corner++) {
            int cx = (corner & 1) == 0 ? x - halfX : x + halfX;
            int cz = (corner & 2) == 0 ? z - halfZ : z + halfZ;
            for (int y = base + 1; y <= base + height; y++) {
                buffer.set(cx, y, cz, materials.pillar);
            }
        }

        BuildKit.gableRoof(buffer, x - halfX, base + height + 1, z - halfZ, x + halfX, z + halfZ,
                materials.roof, materials.slab);

        // Door and windows.
        buffer.set(x, base + 1, z - halfZ, Blocks.OAK_DOOR_LOWER);
        buffer.set(x, base + 2, z - halfZ, Blocks.OAK_DOOR_UPPER);
        buffer.set(x - halfX, base + 2, z, materials.glass);
        buffer.set(x + halfX, base + 2, z, materials.glass);
        buffer.set(x, base + 2, z + halfZ, materials.glass);

        // Interior.
        buffer.set(x + halfX - 1, base + 1, z + halfZ - 1, Blocks.CRAFTING_TABLE);
        buffer.set(x - halfX + 1, base + 1, z + halfZ - 1, Blocks.FURNACE);
        buffer.set(x, base + height, z, materials.hangingLight);
        BuildKit.chest(buffer, x - halfX + 1, base + 1, z - halfZ + 1, 1, "village");
        buffer.addSpawn(MobSpawn.mob(x, base + 1, z, "VILLAGER", 0));
        if (random.chance(0.3)) {
            buffer.addSpawn(MobSpawn.mob(x + 1, base + 1, z, "IRON_GOLEM", 0));
        }
    }

    private void buildFarm(StructureContext context, StructureBuffer buffer, FastRandom random,
                           StructureMaterials materials, int x, int z) {
        int halfX = random.nextInt(3, 6);
        int halfZ = random.nextInt(3, 6);
        int base = averagePlot(context, x, z, Math.max(halfX, halfZ));
        BuildKit.clear(buffer, x - halfX, base + 1, z - halfZ, x + halfX, base + 3, z + halfZ);
        for (int ox = -halfX; ox <= halfX; ox++) {
            for (int oz = -halfZ; oz <= halfZ; oz++) {
                boolean border = Math.abs(ox) == halfX || Math.abs(oz) == halfZ;
                if (border) {
                    buffer.set(x + ox, base, z + oz, materials.floor);
                    buffer.set(x + ox, base + 1, z + oz, materials.fence);
                } else if (oz % 3 == 0) {
                    buffer.set(x + ox, base, z + oz, Blocks.WATER);
                } else {
                    buffer.set(x + ox, base, z + oz, Blocks.DIRT);
                    buffer.set(x + ox, base + 1, z + oz, random.chance(0.7) ? Blocks.HAY_BLOCK : Blocks.AIR);
                }
            }
        }
    }

    private int averagePlot(StructureContext context, int x, int z, int radius) {
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
