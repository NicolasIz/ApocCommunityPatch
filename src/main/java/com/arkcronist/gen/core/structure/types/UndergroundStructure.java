package com.arkcronist.gen.core.structure.types;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.structure.*;

/**
 * A buried vault: a sealed stone box deep underground with no entrance of its own.
 *
 * <p>You find these by mining or by falling into the cave system next to them, which is exactly the
 * point - they are the reward for exploring the deep layers rather than the surface.</p>
 */
public final class UndergroundStructure implements Structure {

    @Override
    public String id() {
        return "vault";
    }

    @Override
    public StructureTag tag() {
        return StructureTag.UNDERGROUND;
    }

    @Override
    public int radius() {
        return 20;
    }

    @Override
    public double weight() {
        return 1.3;
    }

    @Override
    public boolean canPlace(StructureContext context) {
        return context.groundY > context.engine.settings().minY + 40;
    }

    @Override
    public void build(StructureContext context, StructureBuffer buffer) {
        FastRandom random = context.random;
        StructureMaterials materials = StructureMaterials.forBiome(context.biome, random);
        int x = context.originX;
        int z = context.originZ;
        int minY = context.engine.settings().minY;
        int y = random.nextInt(minY + 10, Math.max(minY + 11, Math.min(context.groundY - 25, 20)));

        int halfX = random.nextInt(6, 11);
        int halfZ = random.nextInt(6, 11);
        int height = random.nextInt(5, 9);

        // Solid shell, then hollowed out: this survives whatever the cave carver did nearby.
        BuildKit.box(buffer, x - halfX - 1, y - 1, z - halfZ - 1, x + halfX + 1, y + height + 1, z + halfZ + 1,
                materials.wall);
        BuildKit.box(buffer, x - halfX, y, z - halfZ, x + halfX, y + height, z + halfZ, Blocks.AIR);
        BuildKit.box(buffer, x - halfX, y - 1, z - halfZ, x + halfX, y - 1, z + halfZ, materials.floor);
        BuildKit.torchRing(buffer, x - halfX + 1, y + 1, z - halfZ + 1, x + halfX - 1, z + halfZ - 1, 4,
                materials.light);

        // Interior: pillars, cells, and a treasury behind bars.
        for (int px = -halfX + 2; px <= halfX - 2; px += 4) {
            for (int pz = -halfZ + 2; pz <= halfZ - 2; pz += 4) {
                for (int i = 0; i <= height; i++) {
                    buffer.set(x + px, y + i, z + pz, materials.pillar);
                }
            }
        }
        BuildKit.walls(buffer, x + halfX - 4, y, z - 3, x + halfX - 1, y + 3, z + 3, Blocks.IRON_BARS);
        BuildKit.chest(buffer, x + halfX - 2, y, z, 3, "vault");
        BuildKit.chest(buffer, x + halfX - 3, y, z + 1, 3, "vault");
        BuildKit.spawner(buffer, x + halfX - 2, y, z + 2, dweller(random));

        int mobs = random.nextInt(3, 7);
        for (int i = 0; i < mobs; i++) {
            buffer.addSpawn(MobSpawn.mob(x + random.nextInt(-halfX + 1, halfX - 1), y,
                    z + random.nextInt(-halfZ + 1, halfZ - 1), dweller(random), 3));
        }
        buffer.addSpawn(MobSpawn.boss(x, y, z, "WITHER_SKELETON", 4, "cave_horror"));

        if (random.chance(0.5)) {
            // Amethyst pocket embedded in one wall, as a visual tell when you break in.
            BuildKit.box(buffer, x - halfX - 2, y + 1, z - 2, x - halfX - 1, y + 3, z + 2, Blocks.AMETHYST_BLOCK);
            buffer.set(x - halfX - 1, y + 2, z, Blocks.BUDDING_AMETHYST);
        }
    }

    private static String dweller(FastRandom random) {
        return switch (random.nextInt(5)) {
            case 0 -> "WITHER_SKELETON";
            case 1 -> "SKELETON";
            case 2 -> "CAVE_SPIDER";
            case 3 -> "ZOMBIE";
            default -> "MAGMA_CUBE";
        };
    }
}
