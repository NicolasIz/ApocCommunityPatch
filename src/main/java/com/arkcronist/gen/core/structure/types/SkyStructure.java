package com.arkcronist.gen.core.structure.types;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.structure.*;

/**
 * A sanctuary hanging in the sky: a stone platform, an open pillared hall, chains dangling from the
 * underside and a sentinel guarding the reliquary.
 *
 * <p>Only ever placed by presets that generate floating terrain, and always positioned inside the
 * island band so it either crowns an island or hangs next to one.</p>
 */
public final class SkyStructure implements Structure {

    @Override
    public String id() {
        return "sky_sanctuary";
    }

    @Override
    public StructureTag tag() {
        return StructureTag.SKY;
    }

    @Override
    public int radius() {
        return 24;
    }

    @Override
    public double weight() {
        return 1.0;
    }

    @Override
    public boolean canPlace(StructureContext context) {
        return context.engine.density().floatingIslandsEnabled();
    }

    @Override
    public void build(StructureContext context, StructureBuffer buffer) {
        FastRandom random = context.random;
        StructureMaterials materials = StructureMaterials.forBiome(context.biome, random);
        int x = context.originX;
        int z = context.originZ;
        int minY = context.engine.density().islandMinY();
        int maxY = context.engine.density().islandMaxY();
        int base = random.nextInt(minY + 10, Math.max(minY + 11, maxY - 30));
        int radius = random.nextInt(7, 12);

        // Floating platform with a tapered underside.
        BuildKit.cylinder(buffer, x, base, z, radius, 1, materials.floor, false);
        for (int i = 1; i <= radius; i++) {
            int r = radius - i;
            if (r <= 0) {
                break;
            }
            BuildKit.cylinder(buffer, x, base - i, z, r, 1, i > radius / 2 ? Blocks.STONE : materials.wall, false);
        }

        // Open hall: pillars and a roof ring.
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4.0;
            int px = x + (int) Math.round(Math.cos(angle) * (radius - 2));
            int pz = z + (int) Math.round(Math.sin(angle) * (radius - 2));
            for (int y = 1; y <= 6; y++) {
                buffer.set(px, base + y, pz, materials.pillar);
            }
            buffer.set(px, base + 7, pz, materials.slab);
            BuildKit.hangingLantern(buffer, px, base + 6, pz, materials.floor);
        }
        BuildKit.cylinder(buffer, x, base + 8, z, radius - 2, 1, materials.roof, false);

        // Reliquary at the centre.
        BuildKit.box(buffer, x - 1, base + 1, z - 1, x + 1, base + 1, z + 1, materials.wallAccent);
        buffer.set(x, base + 2, z, Blocks.LODESTONE);
        BuildKit.chest(buffer, x + 1, base + 2, z, 3, "sky");
        BuildKit.chest(buffer, x - 1, base + 2, z, 3, "sky");

        // Chains hanging beneath, purely so it reads as suspended.
        for (int i = 0; i < 4; i++) {
            double angle = i * Math.PI / 2.0 + Math.PI / 4.0;
            int cx = x + (int) Math.round(Math.cos(angle) * (radius - 3));
            int cz = z + (int) Math.round(Math.sin(angle) * (radius - 3));
            int length = random.nextInt(6, 16);
            for (int y = 1; y <= length; y++) {
                buffer.set(cx, base - radius - y, cz, Blocks.CHAIN);
            }
        }

        int guards = random.nextInt(2, 5);
        for (int i = 0; i < guards; i++) {
            buffer.addSpawn(MobSpawn.mob(x + random.nextInt(-radius + 2, radius - 2), base + 1,
                    z + random.nextInt(-radius + 2, radius - 2), random.chance(0.5) ? "VEX" : "PHANTOM", 3));
        }
        buffer.addSpawn(MobSpawn.boss(x, base + 2, z + 2, "EVOKER", 4, "sky_sentinel"));
    }
}
