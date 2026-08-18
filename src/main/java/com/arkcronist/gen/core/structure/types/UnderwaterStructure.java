package com.arkcronist.gen.core.structure.types;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.structure.*;

/**
 * A sunken complex on the sea floor: a prismarine dome with an air-free interior, outlying ruins and
 * a leviathan in the deep variants.
 *
 * <p>Deep water versions are deliberately bigger and better lit, since the abyss and trench biomes
 * are pitch dark - a sea lantern dome is how you find one from above.</p>
 */
public final class UnderwaterStructure implements Structure {

    @Override
    public String id() {
        return "underwater";
    }

    @Override
    public StructureTag tag() {
        return StructureTag.UNDERWATER;
    }

    @Override
    public int radius() {
        return 28;
    }

    @Override
    public double weight() {
        return 1.1;
    }

    @Override
    public boolean canPlace(StructureContext context) {
        int depth = context.waterY - context.groundY;
        return depth > 8 && context.relief(context.originX, context.originZ, 10) < 24;
    }

    @Override
    public void build(StructureContext context, StructureBuffer buffer) {
        FastRandom random = context.random;
        StructureMaterials materials = StructureMaterials.forBiome(context.biome, random);
        int x = context.originX;
        int z = context.originZ;
        int floor = context.groundY;
        int depth = context.waterY - floor;
        boolean deep = depth > 45;

        int radius = deep ? random.nextInt(9, 14) : random.nextInt(6, 10);
        int height = deep ? random.nextInt(10, 16) : random.nextInt(6, 11);

        // Platform, dome and hollow interior.
        BuildKit.cylinder(buffer, x, floor - 1, z, radius + 2, 2, materials.floor, false);
        BuildKit.cylinder(buffer, x, floor + 1, z, radius, height, materials.wall, true);
        BuildKit.dome(buffer, x, floor + height, z, radius, materials.wallAccent, true);
        BuildKit.cylinder(buffer, x, floor + 1, z, radius - 1, height - 1, Blocks.WATER, false);
        BuildKit.cylinder(buffer, x, floor + 1, z, radius - 1, 1, materials.floor, false);

        // Lighting: sea lanterns embedded in the walls and a beacon at the apex.
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4.0;
            int lx = x + (int) Math.round(Math.cos(angle) * (radius - 1));
            int lz = z + (int) Math.round(Math.sin(angle) * (radius - 1));
            buffer.set(lx, floor + 3, lz, Blocks.SEA_LANTERN);
            buffer.set(lx, floor + height - 2, lz, Blocks.SEA_LANTERN);
        }
        buffer.set(x, floor + height + radius - 1, z, Blocks.SEA_LANTERN);

        // Inner chamber with the loot.
        BuildKit.hollow(buffer, x - 3, floor + 1, z - 3, x + 3, floor + 5, z + 3, materials.wallAccent, Blocks.AIR);
        buffer.set(x, floor + 1, z - 3, Blocks.AIR);
        BuildKit.chest(buffer, x - 2, floor + 2, z - 2, deep ? 3 : 2, "underwater");
        BuildKit.chest(buffer, x + 2, floor + 2, z + 2, deep ? 3 : 2, "underwater");
        buffer.set(x, floor + 2, z, Blocks.SPONGE);

        int guards = deep ? random.nextInt(4, 8) : random.nextInt(2, 5);
        for (int i = 0; i < guards; i++) {
            buffer.addSpawn(MobSpawn.mob(x + random.nextInt(-radius + 2, radius - 2), floor + 3,
                    z + random.nextInt(-radius + 2, radius - 2), random.chance(0.6) ? "DROWNED" : "GUARDIAN", 2));
        }
        buffer.addSpawn(MobSpawn.boss(x, floor + 4, z, deep ? "ELDER_GUARDIAN" : "DROWNED",
                deep ? 4 : 3, "deep_leviathan"));

        // Outlying broken walls so it reads as a settlement, not a single pod.
        for (int i = 0; i < 5; i++) {
            int rx = x + random.nextInt(-radius - 8, radius + 8);
            int rz = z + random.nextInt(-radius - 8, radius + 8);
            int ry = context.height(rx, rz);
            BuildKit.decayedBox(buffer, random, rx - 2, ry + 1, rz, rx + 2, ry + random.nextInt(2, 5), rz,
                    materials.wall, 0.45);
        }
    }
}
