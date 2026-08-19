package com.arkcronist.gen.core.structure.types;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.BlockShapes;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.structure.*;

/**
 * A proper village: a settlement, not a handful of huts.
 *
 * <p>A ring of fence with gate posts encloses the whole place. Inside it: a plaza with a roofed well
 * and a bell, lanes paved and lit with lamp posts, twelve to twenty buildings drawn from eight
 * archetypes (cottage, two storey house, longhouse, barn, church with a bell tower, smithy with a
 * forge, inn, watchtower), fenced farm plots with crop rows and irrigation channels, animal pens and
 * haystacks. Every building levels its own plot, drops a foundation, and is furnished and lit
 * inside.</p>
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
        return 58;
    }

    @Override
    public double weight() {
        return 1.6;
    }

    @Override
    public boolean canPlace(StructureContext context) {
        return !context.submerged(context.originX, context.originZ)
                && context.groundY > context.seaLevel()
                && context.relief(context.originX, context.originZ, 16) < 11;
    }

    @Override
    public void build(StructureContext context, StructureBuffer buffer) {
        FastRandom random = context.random;
        StructureMaterials materials = StructureMaterials.forBiome(context.biome, random);
        int x = context.originX;
        int z = context.originZ;
        int centre = context.averageHeight(14);
        int extent = random.nextInt(38, 50);

        plaza(context, buffer, random, materials, x, centre, z);

        // Lanes radiate from the plaza; buildings and farms line them.
        int lanes = random.nextInt(4, 6);
        double[] angles = new double[lanes];
        for (int i = 0; i < lanes; i++) {
            angles[i] = (i / (double) lanes) * Math.PI * 2.0 + random.nextDouble(-0.18, 0.18);
            lane(context, buffer, materials, x, z, angles[i], extent - 6);
        }

        int buildings = random.nextInt(12, 21);
        int placed = 0;
        for (int i = 0; i < buildings * 2 && placed < buildings; i++) {
            double angle = angles[i % lanes] + random.nextDouble(-0.55, 0.55);
            int distance = random.nextInt(10, extent - 10);
            int bx = x + (int) Math.round(Math.cos(angle) * distance);
            int bz = z + (int) Math.round(Math.sin(angle) * distance);
            if (context.submerged(bx, bz) || context.relief(bx, bz, 5) > 6) {
                continue;
            }
            building(context, buffer, random, materials, bx, bz, placed, distance < 18);
            placed++;
        }

        int farms = random.nextInt(3, 7);
        for (int i = 0; i < farms; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            int distance = random.nextInt(14, extent - 8);
            int fx = x + (int) Math.round(Math.cos(angle) * distance);
            int fz = z + (int) Math.round(Math.sin(angle) * distance);
            if (context.submerged(fx, fz) || context.relief(fx, fz, 6) > 5) {
                continue;
            }
            farm(context, buffer, random, materials, fx, fz);
        }

        for (int i = 0; i < 2; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            int distance = random.nextInt(16, extent - 10);
            pen(context, buffer, random, materials,
                    x + (int) Math.round(Math.cos(angle) * distance),
                    z + (int) Math.round(Math.sin(angle) * distance));
        }

        perimeter(context, buffer, random, materials, x, z, extent, angles);
    }

    // ------------------------------------------------------------------ public spaces

    private void plaza(StructureContext context, StructureBuffer buffer, FastRandom random,
                       StructureMaterials materials, int x, int y, int z) {
        for (int ox = -7; ox <= 7; ox++) {
            for (int oz = -7; oz <= 7; oz++) {
                if (ox * ox + oz * oz > 54) {
                    continue;
                }
                buffer.set(x + ox, y, z + oz, (ox + oz) % 3 == 0 ? materials.wallAccent : materials.floor);
                BuildKit.foundation(context, buffer, x + ox, z + oz, y, materials.wall, 10);
                BuildKit.clear(buffer, x + ox, y + 1, z + oz, x + ox, y + 7, z + oz);
            }
        }

        BuildKit.walls(buffer, x - 2, y, z - 2, x + 2, y + 1, z + 2, materials.wallAccent);
        BuildKit.box(buffer, x - 1, y - 5, z - 1, x + 1, y, z + 1, Blocks.WATER);
        BuildKit.walls(buffer, x - 2, y - 6, z - 2, x + 2, y - 1, z + 2, materials.wall);
        for (int corner = 0; corner < 4; corner++) {
            int cx = (corner & 1) == 0 ? x - 2 : x + 2;
            int cz = (corner & 2) == 0 ? z - 2 : z + 2;
            for (int i = 2; i <= 4; i++) {
                buffer.set(cx, y + i, cz, BlockShapes.fence(materials.woodFamily));
            }
        }
        BuildKit.stairRoof(buffer, x - 2, y + 5, z - 2, x + 2, z + 2, materials.woodFamily, materials.plank);
        buffer.set(x, y + 4, z, Blocks.LANTERN);

        buffer.set(x + 5, y + 1, z, BlockShapes.fence(materials.woodFamily));
        buffer.set(x + 5, y + 2, z, BlockShapes.fence(materials.woodFamily));
        buffer.set(x + 5, y + 3, z, materials.plank);
        buffer.set(x + 5, y + 2, z + 1, Blocks.BELL_CEILING);

        for (int i = 0; i < 3; i++) {
            int sx = x + random.nextInt(-6, 6);
            int sz = z + random.nextInt(-6, 6);
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
        buffer.addSpawn(MobSpawn.mob(x - 3, y + 1, z - 1, "IRON_GOLEM", 0));
    }

    private void lane(StructureContext context, StructureBuffer buffer, StructureMaterials materials,
                      int x, int z, double angle, int length) {
        double dx = Math.cos(angle);
        double dz = Math.sin(angle);
        for (int step = 6; step <= length; step++) {
            int px = x + (int) Math.round(dx * step);
            int pz = z + (int) Math.round(dz * step);
            int py = context.height(px, pz);
            for (int ox = -1; ox <= 1; ox++) {
                for (int oz = -1; oz <= 1; oz++) {
                    buffer.set(px + ox, py, pz + oz,
                            (ox + oz) % 2 == 0 ? materials.floor : materials.wallAccent);
                    BuildKit.clear(buffer, px + ox, py + 1, pz + oz, px + ox, py + 3, pz + oz);
                }
            }
            if (step % 10 == 0) {
                BuildKit.lampPost(buffer, px + 2, py, pz, BlockShapes.fence(materials.woodFamily), 3);
            }
        }
    }

    /** Fence ring around the whole settlement, with gate posts where the lanes leave. */
    private void perimeter(StructureContext context, StructureBuffer buffer, FastRandom random,
                           StructureMaterials materials, int x, int z, int extent, double[] lanes) {
        int steps = extent * 6;
        for (int i = 0; i < steps; i++) {
            double angle = i * Math.PI * 2.0 / steps;
            boolean gate = false;
            for (double lane : lanes) {
                double delta = Math.abs(Math.atan2(Math.sin(angle - lane), Math.cos(angle - lane)));
                if (delta < 0.07) {
                    gate = true;
                    break;
                }
            }
            int px = x + (int) Math.round(Math.cos(angle) * extent);
            int pz = z + (int) Math.round(Math.sin(angle) * extent);
            int py = context.height(px, pz);
            if (context.submerged(px, pz)) {
                continue;
            }
            if (gate) {
                buffer.set(px, py + 1, pz, Blocks.AIR);
                continue;
            }
            if (i % 12 == 0) {
                for (int h = 1; h <= 3; h++) {
                    buffer.set(px, py + h, pz, materials.pillar);
                }
                buffer.set(px, py + 4, pz, Blocks.LANTERN);
            } else {
                buffer.set(px, py + 1, pz, BlockShapes.fence(materials.woodFamily));
                buffer.set(px, py + 2, pz, BlockShapes.fence(materials.woodFamily));
            }
        }
    }

    // ------------------------------------------------------------------ buildings

    private void building(StructureContext context, StructureBuffer buffer, FastRandom random,
                          StructureMaterials materials, int x, int z, int index, boolean central) {
        int archetype = central && index == 0 ? 4 : random.nextInt(0, 7);
        switch (archetype) {
            case 0 -> house(context, buffer, random, materials, x, z, 1);
            case 1 -> house(context, buffer, random, materials, x, z, 2);
            case 2 -> longhouse(context, buffer, random, materials, x, z);
            case 3 -> barn(context, buffer, random, materials, x, z);
            case 4 -> church(context, buffer, random, materials, x, z);
            case 5 -> smithy(context, buffer, random, materials, x, z);
            default -> watchtower(context, buffer, random, materials, x, z);
        }
    }

    private void house(StructureContext context, StructureBuffer buffer, FastRandom random,
                       StructureMaterials materials, int x, int z, int storeys) {
        String wood = materials.woodFamily;
        String stone = materials.stoneFamily;
        int halfX = random.nextInt(3, 6);
        int halfZ = random.nextInt(3, 6);
        int storeyHeight = 4;
        int base = plotLevel(context, x, z, Math.max(halfX, halfZ));
        int height = storeys * storeyHeight;

        BuildKit.clear(buffer, x - halfX - 1, base + 1, z - halfZ - 1, x + halfX + 1,
                base + height + halfZ + 4, z + halfZ + 1);
        BuildKit.foundationArea(context, buffer, x - halfX, z - halfZ, x + halfX, z + halfZ, base,
                materials.wall, 14);
        BuildKit.box(buffer, x - halfX, base, z - halfZ, x + halfX, base, z + halfZ, materials.floor);

        for (int storey = 0; storey < storeys; storey++) {
            int y = base + storey * storeyHeight;
            BuildKit.walls(buffer, x - halfX, y + 1, z - halfZ, x + halfX, y + storeyHeight, z + halfZ,
                    materials.plank);
            // Stone plinth course and timber frame.
            for (int ox = -halfX; ox <= halfX; ox++) {
                buffer.set(x + ox, y + 1, z - halfZ, storey == 0 ? materials.wall : materials.plank);
                buffer.set(x + ox, y + 1, z + halfZ, storey == 0 ? materials.wall : materials.plank);
            }
            for (int corner = 0; corner < 4; corner++) {
                int cx = (corner & 1) == 0 ? x - halfX : x + halfX;
                int cz = (corner & 2) == 0 ? z - halfZ : z + halfZ;
                for (int i = 1; i <= storeyHeight; i++) {
                    buffer.set(cx, y + i, cz, materials.pillar);
                }
            }
            for (int offset = -halfX + 2; offset <= halfX - 2; offset += 3) {
                buffer.set(x + offset, y + 3, z - halfZ, materials.glass);
                buffer.set(x + offset, y + 3, z + halfZ, materials.glass);
            }
            for (int offset = -halfZ + 2; offset <= halfZ - 2; offset += 3) {
                buffer.set(x - halfX, y + 3, z + offset, materials.glass);
                buffer.set(x + halfX, y + 3, z + offset, materials.glass);
            }
            BuildKit.hangingLantern(buffer, x, y + storeyHeight, z, materials.plank);
            if (storey > 0) {
                BuildKit.box(buffer, x - halfX + 1, y, z - halfZ + 1, x + halfX - 1, y, z + halfZ - 1,
                        materials.plank);
                buffer.set(x + halfX - 1, y, z + halfZ - 1, Blocks.AIR);
                BuildKit.ladder(buffer, x + halfX - 1, y - storeyHeight + 1, y,
                        z + halfZ - 1, BlockShapes.ladder(2));
            }
        }

        BuildKit.stairRoof(buffer, x - halfX, base + height + 1, z - halfZ, x + halfX, z + halfZ,
                wood, materials.plank);

        buffer.set(x, base + 1, z - halfZ, BlockShapes.door(wood, 2, false, false));
        buffer.set(x, base + 2, z - halfZ, BlockShapes.door(wood, 2, true, false));
        buffer.set(x, base, z - halfZ - 1, BlockShapes.stairs(stone, 0, false));
        BuildKit.wallTorch(buffer, x - 1, base + 3, z - halfZ, 0, -1, false);

        buffer.set(x - halfX + 1, base + 1, z + halfZ - 1, BlockShapes.bed(0, false));
        buffer.set(x - halfX + 1, base + 1, z + halfZ - 2, BlockShapes.bed(0, true));
        buffer.set(x + halfX - 1, base + 1, z + halfZ - 1, workstation(random));
        buffer.set(x + halfX - 1, base + 1, z - halfZ + 1, Blocks.BARREL_UP);
        BuildKit.chest(buffer, x - halfX + 1, base + 1, z - halfZ + 1, 1, "village");

        if (random.chance(0.55)) {
            int cx = x + halfX - 1;
            int cz = z - halfZ + 1;
            for (int i = 1; i <= height + halfZ + 2; i++) {
                buffer.set(cx, base + i, cz, materials.wall);
            }
            buffer.set(cx, base + height + halfZ + 3, cz, BlockShapes.slab(stone, false));
            buffer.set(cx, base + 1, cz, Blocks.CAMPFIRE);
        }

        buffer.addSpawn(MobSpawn.mob(x, base + 1, z, "VILLAGER", 0));
        if (storeys > 1) {
            buffer.addSpawn(MobSpawn.mob(x + 1, base + 1, z, "VILLAGER", 0));
        }
    }

    private void longhouse(StructureContext context, StructureBuffer buffer, FastRandom random,
                           StructureMaterials materials, int x, int z) {
        String wood = materials.woodFamily;
        int halfX = random.nextInt(7, 11);
        int halfZ = 4;
        int height = 5;
        int base = plotLevel(context, x, z, halfX);

        BuildKit.clear(buffer, x - halfX - 1, base + 1, z - halfZ - 1, x + halfX + 1, base + height + 8,
                z + halfZ + 1);
        BuildKit.foundationArea(context, buffer, x - halfX, z - halfZ, x + halfX, z + halfZ, base,
                materials.wall, 14);
        BuildKit.box(buffer, x - halfX, base, z - halfZ, x + halfX, base, z + halfZ, materials.floor);
        BuildKit.walls(buffer, x - halfX, base + 1, z - halfZ, x + halfX, base + height, z + halfZ,
                materials.plank);
        for (int ox = -halfX; ox <= halfX; ox += 3) {
            for (int i = 1; i <= height; i++) {
                buffer.set(x + ox, base + i, z - halfZ, materials.pillar);
                buffer.set(x + ox, base + i, z + halfZ, materials.pillar);
            }
        }
        for (int ox = -halfX + 2; ox <= halfX - 2; ox += 3) {
            buffer.set(x + ox, base + 3, z - halfZ, materials.glass);
            buffer.set(x + ox, base + 3, z + halfZ, materials.glass);
        }
        BuildKit.stairRoof(buffer, x - halfX, base + height + 1, z - halfZ, x + halfX, z + halfZ,
                wood, materials.plank);

        buffer.set(x, base + 1, z - halfZ, BlockShapes.door(wood, 2, false, false));
        buffer.set(x, base + 2, z - halfZ, BlockShapes.door(wood, 2, true, false));
        for (int ox = -halfX + 3; ox <= halfX - 3; ox += 5) {
            BuildKit.hangingLantern(buffer, x + ox, base + height, z, materials.plank);
            buffer.set(x + ox, base + 1, z + halfZ - 1, BlockShapes.bed(0, false));
            buffer.set(x + ox, base + 1, z + halfZ - 2, BlockShapes.bed(0, true));
            buffer.addSpawn(MobSpawn.mob(x + ox, base + 1, z, "VILLAGER", 0));
        }
        buffer.set(x - halfX + 1, base + 1, z - halfZ + 1, Blocks.CRAFTING_TABLE);
        BuildKit.chest(buffer, x + halfX - 1, base + 1, z - halfZ + 1, 2, "village");
    }

    private void barn(StructureContext context, StructureBuffer buffer, FastRandom random,
                      StructureMaterials materials, int x, int z) {
        String wood = materials.woodFamily;
        int halfX = random.nextInt(5, 8);
        int halfZ = random.nextInt(4, 6);
        int height = 6;
        int base = plotLevel(context, x, z, Math.max(halfX, halfZ));

        BuildKit.clear(buffer, x - halfX - 1, base + 1, z - halfZ - 1, x + halfX + 1, base + height + 8,
                z + halfZ + 1);
        BuildKit.foundationArea(context, buffer, x - halfX, z - halfZ, x + halfX, z + halfZ, base,
                materials.wall, 12);
        BuildKit.box(buffer, x - halfX, base, z - halfZ, x + halfX, base, z + halfZ, materials.floor);
        BuildKit.walls(buffer, x - halfX, base + 1, z - halfZ, x + halfX, base + height, z + halfZ,
                materials.plank);
        BuildKit.stairRoof(buffer, x - halfX, base + height + 1, z - halfZ, x + halfX, z + halfZ,
                wood, materials.plank);
        // Big doors, hay bales and a loft.
        BuildKit.clear(buffer, x - 1, base + 1, z - halfZ, x + 1, base + 3, z - halfZ);
        for (int i = 0; i < 6; i++) {
            buffer.set(x + random.nextInt(-halfX + 1, halfX - 1), base + 1,
                    z + random.nextInt(-halfZ + 1, halfZ - 1), Blocks.HAY_BLOCK);
        }
        BuildKit.box(buffer, x - halfX + 1, base + 4, z - halfZ + 1, x - halfX + 3, base + 4,
                z + halfZ - 1, materials.plank);
        BuildKit.hangingLantern(buffer, x, base + height, z, materials.plank);
        buffer.addSpawn(MobSpawn.mob(x, base + 1, z, "COW", 0));
        buffer.addSpawn(MobSpawn.mob(x + 1, base + 1, z, "SHEEP", 0));
        BuildKit.chest(buffer, x + halfX - 1, base + 1, z + halfZ - 1, 1, "village");
    }

    /** The village's landmark: a hall with a bell tower. */
    private void church(StructureContext context, StructureBuffer buffer, FastRandom random,
                        StructureMaterials materials, int x, int z) {
        String wood = materials.woodFamily;
        String stone = materials.stoneFamily;
        int halfX = 4;
        int halfZ = 7;
        int height = 7;
        int base = plotLevel(context, x, z, halfZ);

        BuildKit.clear(buffer, x - halfX - 2, base + 1, z - halfZ - 1, x + halfX + 2, base + 26, z + halfZ + 1);
        BuildKit.foundationArea(context, buffer, x - halfX, z - halfZ, x + halfX, z + halfZ, base,
                materials.wall, 16);
        BuildKit.box(buffer, x - halfX, base, z - halfZ, x + halfX, base, z + halfZ, materials.floor);
        BuildKit.walls(buffer, x - halfX, base + 1, z - halfZ, x + halfX, base + height, z + halfZ,
                materials.wall);
        for (int oz = -halfZ + 2; oz <= halfZ - 2; oz += 3) {
            buffer.set(x - halfX, base + 3, z + oz, materials.glass);
            buffer.set(x - halfX, base + 4, z + oz, materials.glass);
            buffer.set(x + halfX, base + 3, z + oz, materials.glass);
            buffer.set(x + halfX, base + 4, z + oz, materials.glass);
        }
        BuildKit.stairRoof(buffer, x - halfX, base + height + 1, z - halfZ, x + halfX, z + halfZ,
                wood, materials.plank);

        BuildKit.archway(buffer, x, base + 1, z - halfZ, 1, 3, true, stone);
        buffer.set(x, base + 1, z - halfZ, BlockShapes.door(wood, 2, false, false));
        buffer.set(x, base + 2, z - halfZ, BlockShapes.door(wood, 2, true, false));

        // Nave: pews, a lectern and chandeliers.
        for (int oz = -halfZ + 2; oz <= halfZ - 3; oz += 2) {
            buffer.set(x - 2, base + 1, z + oz, BlockShapes.stairs(wood, 2, false));
            buffer.set(x + 2, base + 1, z + oz, BlockShapes.stairs(wood, 2, false));
        }
        buffer.set(x, base + 1, z + halfZ - 2, Blocks.LECTERN);
        BuildKit.chandelier(buffer, x, base + height, z, materials.plank, 1);
        BuildKit.chandelier(buffer, x, base + height, z - 4, materials.plank, 1);

        // Bell tower.
        int towerHalf = 2;
        int towerZ = z - halfZ + 2;
        int towerTop = base + 18;
        for (int y = base + 1; y <= towerTop; y++) {
            BuildKit.walls(buffer, x - towerHalf, y, towerZ - towerHalf, x + towerHalf, y,
                    towerZ + towerHalf, y % 5 == 0 ? materials.wallAccent : materials.wall);
        }
        BuildKit.clear(buffer, x - towerHalf + 1, base + 1, towerZ - towerHalf + 1, x + towerHalf - 1,
                towerTop - 1, towerZ + towerHalf - 1);
        BuildKit.ladder(buffer, x, base + 1, towerTop - 4, towerZ + towerHalf - 1, BlockShapes.ladder(2));
        for (int face = 0; face < 4; face++) {
            int wx = face == 1 ? x + towerHalf : face == 3 ? x - towerHalf : x;
            int wz = face == 2 ? towerZ + towerHalf : face == 0 ? towerZ - towerHalf : towerZ;
            buffer.set(wx, towerTop - 3, wz, Blocks.AIR);
            buffer.set(wx, towerTop - 2, wz, Blocks.AIR);
        }
        buffer.set(x, towerTop - 2, towerZ, Blocks.BELL_CEILING);
        buffer.set(x, towerTop - 1, towerZ, materials.plank);
        for (int layer = 0; layer <= towerHalf + 1; layer++) {
            int r = towerHalf - layer;
            if (r < 0) {
                break;
            }
            for (int ox = -r; ox <= r; ox++) {
                for (int oz = -r; oz <= r; oz++) {
                    buffer.set(x + ox, towerTop + layer, towerZ + oz,
                            BlockShapes.stairs(wood, BlockShapes.facingFrom(ox, oz), false));
                }
            }
        }
        buffer.set(x, towerTop + towerHalf + 2, towerZ, Blocks.LANTERN);
        buffer.addSpawn(MobSpawn.mob(x, base + 1, z, "VILLAGER", 0));
    }

    private void smithy(StructureContext context, StructureBuffer buffer, FastRandom random,
                        StructureMaterials materials, int x, int z) {
        String wood = materials.woodFamily;
        int halfX = 4;
        int halfZ = 4;
        int height = 5;
        int base = plotLevel(context, x, z, halfX);

        BuildKit.clear(buffer, x - halfX - 1, base + 1, z - halfZ - 1, x + halfX + 1, base + height + 8,
                z + halfZ + 1);
        BuildKit.foundationArea(context, buffer, x - halfX, z - halfZ, x + halfX, z + halfZ, base,
                materials.wall, 12);
        BuildKit.box(buffer, x - halfX, base, z - halfZ, x + halfX, base, z + halfZ, materials.floor);
        BuildKit.walls(buffer, x - halfX, base + 1, z - halfZ, x + halfX, base + height, z + halfZ,
                materials.wall);
        BuildKit.stairRoof(buffer, x - halfX, base + height + 1, z - halfZ, x + halfX, z + halfZ,
                wood, materials.plank);
        // Open forge front.
        BuildKit.clear(buffer, x - 2, base + 1, z - halfZ, x + 2, base + 3, z - halfZ);
        buffer.set(x - 1, base + 1, z + halfZ - 1, BlockShapes.furnace(0));
        buffer.set(x, base + 1, z + halfZ - 1, BlockShapes.furnace(0));
        buffer.set(x + 1, base + 1, z + halfZ - 1, Blocks.BLAST_FURNACE);
        buffer.set(x + 2, base + 1, z, Blocks.ANVIL);
        buffer.set(x - 2, base + 1, z, Blocks.SMITHING_TABLE);
        buffer.set(x, base + 1, z, Blocks.GRINDSTONE);
        buffer.set(x + halfX - 1, base + 1, z - halfZ + 1, Blocks.CAMPFIRE);
        BuildKit.hangingLantern(buffer, x, base + height, z, materials.plank);
        BuildKit.chest(buffer, x - halfX + 1, base + 1, z - halfZ + 1, 2, "village");
        buffer.addSpawn(MobSpawn.mob(x, base + 1, z, "VILLAGER", 0));
    }

    private void watchtower(StructureContext context, StructureBuffer buffer, FastRandom random,
                            StructureMaterials materials, int x, int z) {
        String wood = materials.woodFamily;
        int half = 2;
        int height = random.nextInt(9, 14);
        int base = plotLevel(context, x, z, half);

        BuildKit.clear(buffer, x - half - 1, base + 1, z - half - 1, x + half + 1, base + height + 6,
                z + half + 1);
        BuildKit.foundationArea(context, buffer, x - half, z - half, x + half, z + half, base,
                materials.wall, 14);
        for (int corner = 0; corner < 4; corner++) {
            int cx = (corner & 1) == 0 ? x - half : x + half;
            int cz = (corner & 2) == 0 ? z - half : z + half;
            for (int i = 1; i <= height; i++) {
                buffer.set(cx, base + i, cz, materials.pillar);
            }
        }
        BuildKit.box(buffer, x - half, base + height, z - half, x + half, base + height, z + half,
                materials.plank);
        BuildKit.walls(buffer, x - half, base + height + 1, z - half, x + half, base + height + 2,
                z + half, BlockShapes.fence(wood));
        BuildKit.stairRoof(buffer, x - half, base + height + 3, z - half, x + half, z + half, wood,
                materials.plank);
        BuildKit.ladder(buffer, x, base + 1, base + height, z + half - 1, BlockShapes.ladder(2));
        buffer.set(x, base + height + 1, z, Blocks.LANTERN);
        buffer.addSpawn(MobSpawn.mob(x, base + height + 1, z, "IRON_GOLEM", 0));
    }

    // ------------------------------------------------------------------ land use

    private void farm(StructureContext context, StructureBuffer buffer, FastRandom random,
                      StructureMaterials materials, int x, int z) {
        int halfX = random.nextInt(4, 8);
        int halfZ = random.nextInt(4, 8);
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
                    if (random.chance(0.8)) {
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

    private void pen(StructureContext context, StructureBuffer buffer, FastRandom random,
                     StructureMaterials materials, int x, int z) {
        if (context.submerged(x, z)) {
            return;
        }
        int half = random.nextInt(3, 6);
        int base = plotLevel(context, x, z, half);
        for (int ox = -half; ox <= half; ox++) {
            for (int oz = -half; oz <= half; oz++) {
                boolean border = Math.abs(ox) == half || Math.abs(oz) == half;
                if (border) {
                    buffer.set(x + ox, base + 1, z + oz, BlockShapes.fence(materials.woodFamily));
                }
                BuildKit.clear(buffer, x + ox, base + 2, z + oz, x + ox, base + 3, z + oz);
            }
        }
        buffer.set(x, base + 1, z - half, Blocks.AIR);
        buffer.set(x, base + 1, z, Blocks.HAY_BLOCK);
        String animal = random.chance(0.4) ? "COW" : random.chance(0.5) ? "SHEEP" : "PIG";
        for (int i = 0; i < 3; i++) {
            buffer.addSpawn(MobSpawn.mob(x + random.nextInt(-half + 1, half - 1), base + 1,
                    z + random.nextInt(-half + 1, half - 1), animal, 0));
        }
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

    /** Median-ish ground level of a plot, so a building is neither buried nor on stilts. */
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
