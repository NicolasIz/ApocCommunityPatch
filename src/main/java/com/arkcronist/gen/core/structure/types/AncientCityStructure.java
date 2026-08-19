package com.arkcronist.gen.core.structure.types;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.BlockShapes;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.structure.*;

/**
 * An ancient city in the deep dark.
 *
 * <p>A sculk covered platform of deepslate tile, a colonnade of tall pillars, the central frame of
 * reinforced deepslate with its wardens' altar, ruined side buildings, soul lanterns as the only
 * light, and sensors and shriekers everywhere. Loot is heavy; so is the risk.</p>
 */
public final class AncientCityStructure implements Structure {

    @Override
    public Placement placement() {
        return Placement.UNDERGROUND;
    }

    @Override
    public String id() {
        return "ancient_city";
    }

    @Override
    public StructureTag tag() {
        return StructureTag.ANCIENT_CITY;
    }

    @Override
    public int radius() {
        return 56;
    }

    @Override
    public double weight() {
        return 0.45;
    }

    @Override
    public boolean canPlace(StructureContext context) {
        return context.groundY > context.engine().settings().minY + 70;
    }

    @Override
    public void build(StructureContext context, StructureBuffer buffer) {
        FastRandom random = context.random;
        int x = context.originX;
        int z = context.originZ;
        int minY = context.engine().settings().minY;
        int y = minY + random.nextInt(10, 22);
        int half = random.nextInt(26, 36);

        // Excavate the cavern that holds the city, then floor it.
        for (int ox = -half; ox <= half; ox++) {
            for (int oz = -half; oz <= half; oz++) {
                double distance = Math.sqrt(ox * ox + oz * oz) / half;
                if (distance > 1.0) {
                    continue;
                }
                int roof = y + 18 - (int) (distance * 6);
                for (int oy = 0; oy <= roof - y; oy++) {
                    buffer.set(x + ox, y + oy, z + oz, Blocks.AIR);
                }
                double roll = (ox * 31 + oz * 17 + 1000) % 100 / 100.0;
                buffer.set(x + ox, y - 1, z + oz, roll < 0.55 ? Blocks.SCULK
                        : roll < 0.75 ? Blocks.DEEPSLATE_TILES : Blocks.DEEPSLATE_BRICKS);
                buffer.set(x + ox, roof, z + oz, roll < 0.4 ? Blocks.SCULK : Blocks.DEEPSLATE);
            }
        }

        // Colonnade.
        for (int i = 0; i < 12; i++) {
            double angle = i * Math.PI / 6.0;
            int px = x + (int) Math.round(Math.cos(angle) * (half - 8));
            int pz = z + (int) Math.round(Math.sin(angle) * (half - 8));
            int height = random.nextInt(10, 17);
            for (int oy = 0; oy < height; oy++) {
                BuildKit.box(buffer, px - 1, y + oy, pz - 1, px + 1, y + oy, pz + 1,
                        oy % 5 == 4 ? Blocks.CHISELED_DEEPSLATE : Blocks.DEEPSLATE_BRICKS);
            }
            buffer.set(px, y + height, pz, Blocks.SCULK_CATALYST);
            if (random.chance(0.5)) {
                buffer.set(px + 1, y + 2, pz, Blocks.SOUL_LANTERN);
            }
        }

        // Central platform and the frame.
        BuildKit.box(buffer, x - 8, y - 1, z - 8, x + 8, y - 1, z + 8, Blocks.DEEPSLATE_TILES);
        BuildKit.box(buffer, x - 6, y, z - 6, x + 6, y, z + 6, Blocks.SCULK);
        for (int i = -4; i <= 4; i++) {
            buffer.set(x + i, y + 1, z - 5, Blocks.REINFORCED_DEEPSLATE);
            buffer.set(x + i, y + 1, z + 5, Blocks.REINFORCED_DEEPSLATE);
        }
        for (int oy = 1; oy <= 7; oy++) {
            buffer.set(x - 5, y + oy, z, Blocks.REINFORCED_DEEPSLATE);
            buffer.set(x + 5, y + oy, z, Blocks.REINFORCED_DEEPSLATE);
        }
        for (int i = -5; i <= 5; i++) {
            buffer.set(x + i, y + 8, z, Blocks.REINFORCED_DEEPSLATE);
        }
        buffer.set(x, y + 1, z, Blocks.SCULK_CATALYST);
        buffer.set(x - 1, y + 1, z, Blocks.SCULK_SHRIEKER);
        buffer.set(x + 1, y + 1, z, Blocks.SCULK_SHRIEKER);
        BuildKit.chest(buffer, x, y + 1, z - 2, 3, "ancient_city");
        BuildKit.chest(buffer, x, y + 1, z + 2, 3, "ancient_city");

        // Ruined side buildings with their own loot and traps.
        int buildings = random.nextInt(4, 8);
        for (int i = 0; i < buildings; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            int distance = random.nextInt(12, half - 6);
            int bx = x + (int) Math.round(Math.cos(angle) * distance);
            int bz = z + (int) Math.round(Math.sin(angle) * distance);
            int halfX = random.nextInt(3, 7);
            int halfZ = random.nextInt(3, 7);
            int height = random.nextInt(4, 8);
            BuildKit.decayedBox(buffer, random, bx - halfX, y, bz - halfZ, bx + halfX, y + height,
                    bz + halfZ, Blocks.DEEPSLATE_BRICKS, 0.55);
            BuildKit.box(buffer, bx - halfX + 1, y, bz - halfZ + 1, bx + halfX - 1, y + height - 1,
                    bz + halfZ - 1, Blocks.AIR);
            BuildKit.box(buffer, bx - halfX + 1, y - 1, bz - halfZ + 1, bx + halfX - 1, y - 1,
                    bz + halfZ - 1, Blocks.DEEPSLATE_TILES);
            buffer.set(bx, y, bz, Blocks.SCULK_SENSOR);
            if (random.chance(0.7)) {
                BuildKit.chest(buffer, bx + halfX - 1, y, bz + halfZ - 1, 3, "ancient_city");
            }
            if (random.chance(0.5)) {
                buffer.set(bx - halfX + 1, y + 1, bz - halfZ + 1, Blocks.SOUL_LANTERN);
            }
            buffer.set(bx + 1, y, bz + 1, Blocks.SCULK_SHRIEKER);
        }

        // Sculk growth spreading out from the centre.
        for (int i = 0; i < 220; i++) {
            int sx = x + random.nextInt(-half + 2, half - 2);
            int sz = z + random.nextInt(-half + 2, half - 2);
            buffer.set(sx, y - 1, sz, random.chance(0.15) ? Blocks.SCULK_SENSOR : Blocks.SCULK);
        }
        buffer.addSpawn(MobSpawn.boss(x + 3, y + 1, z + 3, "WITHER_SKELETON", 4, "cave_horror"));
    }
}
