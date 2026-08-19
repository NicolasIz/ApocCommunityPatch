package com.arkcronist.gen.core.structure.types;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.structure.*;

/**
 * A stepped temple: a ziggurat of terraces, a pillared shrine on top, and a sealed treasure vault
 * underneath guarded by its keeper.
 */
public final class TempleStructure implements Structure {

    @Override
    public String id() {
        return "temple";
    }

    @Override
    public StructureTag tag() {
        return StructureTag.TEMPLE;
    }

    @Override
    public int radius() {
        return 26;
    }

    @Override
    public double weight() {
        return 1.0;
    }

    @Override
    public boolean canPlace(StructureContext context) {
        return !context.submerged(context.originX, context.originZ)
                && context.relief(context.originX, context.originZ, 12) < 11;
    }

    @Override
    public void build(StructureContext context, StructureBuffer buffer) {
        FastRandom random = context.random;
        StructureMaterials materials = StructureMaterials.forBiome(context.biome, random);
        int x = context.originX;
        int z = context.originZ;
        int base = context.averageHeight(12);
        int half = random.nextInt(9, 14);
        int steps = random.nextInt(4, 7);

        BuildKit.foundationArea(context, buffer, x - half, z - half, x + half, z + half, base, materials.wall, 18);
        BuildKit.clear(buffer, x - half - 1, base + 1, z - half - 1, x + half + 1, base + steps * 3 + 10, z + half + 1);

        // Terraces.
        for (int step = 0; step < steps; step++) {
            int stepHalf = half - step * 2;
            if (stepHalf < 2) {
                break;
            }
            int y = base + step * 3;
            BuildKit.box(buffer, x - stepHalf, y, z - stepHalf, x + stepHalf, y + 2, z + stepHalf, materials.wall);
            BuildKit.box(buffer, x - stepHalf, y + 2, z - stepHalf, x + stepHalf, y + 2, z + stepHalf,
                    materials.wallAccent);
        }

        // Grand stair on the south face.
        for (int step = 0; step < steps * 3; step++) {
            buffer.set(x, base + step, z - half + step / 3 * 2, materials.stairs);
            buffer.set(x - 1, base + step, z - half + step / 3 * 2, materials.stairs);
            buffer.set(x + 1, base + step, z - half + step / 3 * 2, materials.stairs);
        }

        // Shrine on the summit: open pillared roof over an altar.
        int shrineY = base + steps * 3;
        int shrineHalf = Math.max(2, half - steps * 2);
        for (int corner = 0; corner < 4; corner++) {
            int cx = (corner & 1) == 0 ? x - shrineHalf : x + shrineHalf;
            int cz = (corner & 2) == 0 ? z - shrineHalf : z + shrineHalf;
            for (int y = 1; y <= 5; y++) {
                buffer.set(cx, shrineY + y, cz, materials.pillar);
            }
        }
        BuildKit.box(buffer, x - shrineHalf, shrineY + 6, z - shrineHalf, x + shrineHalf, shrineY + 6,
                z + shrineHalf, materials.roof);
        buffer.set(x, shrineY + 1, z, Blocks.LODESTONE);
        BuildKit.hangingLantern(buffer, x, shrineY + 5, z, materials.floor);
        BuildKit.chest(buffer, x + 1, shrineY + 1, z, 2, "temple");

        // Vault below, reachable only by breaking in from the shrine floor.
        int vaultY = base - random.nextInt(6, 11);
        BuildKit.hollow(buffer, x - 6, vaultY, z - 6, x + 6, vaultY + 6, z + 6, materials.wallAccent, Blocks.AIR);
        BuildKit.box(buffer, x - 5, vaultY, z - 5, x + 5, vaultY, z + 5, materials.floor);
        BuildKit.torchRing(buffer, x - 5, vaultY + 1, z - 5, x + 5, z + 5, 5, materials.light);
        for (int i = 0; i < 4; i++) {
            BuildKit.chest(buffer, x + (i < 2 ? -3 : 3), vaultY + 1, z + (i % 2 == 0 ? -3 : 3), 3, "temple");
        }
        BuildKit.spawner(buffer, x, vaultY + 1, z, "HUSK");
        buffer.addSpawn(MobSpawn.boss(x, vaultY + 1, z + 2, "HUSK", 4, "temple_guardian"));
        for (int i = 0; i < 4; i++) {
            buffer.addSpawn(MobSpawn.mob(x + random.nextInt(-4, 4), vaultY + 1, z + random.nextInt(-4, 4),
                    random.chance(0.5) ? "HUSK" : "SKELETON", 2));
        }

        // Trapped shaft connecting shrine and vault.
        for (int y = vaultY + 7; y < shrineY; y++) {
            BuildKit.walls(buffer, x - 1, y, z - 1, x + 1, y, z + 1, materials.wall);
            buffer.set(x, y, z, Blocks.AIR);
        }
        buffer.set(x, shrineY, z, Blocks.AIR);
        buffer.set(x, vaultY + 7, z, Blocks.COBWEB);
    }
}
