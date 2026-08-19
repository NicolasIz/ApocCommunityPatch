package com.arkcronist.gen.core.structure.types;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.structure.*;

/**
 * An ocean monument: a prismarine block of halls and towers on the sea floor, lit by sea lanterns,
 * with a sponge room, a treasure core and its elder guardian.
 */
public final class OceanMonumentStructure implements Structure {

    @Override
    public String id() {
        return "monument";
    }

    @Override
    public StructureTag tag() {
        return StructureTag.MONUMENT;
    }

    @Override
    public int radius() {
        return 34;
    }

    @Override
    public double weight() {
        return 1.2;
    }

    @Override
    public boolean canPlace(StructureContext context) {
        return context.waterY - context.groundY > 18
                && context.relief(context.originX, context.originZ, 14) < 22;
    }

    @Override
    public void build(StructureContext context, StructureBuffer buffer) {
        FastRandom random = context.random;
        int x = context.originX;
        int z = context.originZ;
        int floor = context.groundY;
        int half = random.nextInt(14, 19);
        int height = random.nextInt(14, 19);

        // Plinth and outer shell.
        BuildKit.box(buffer, x - half - 2, floor - 1, z - half - 2, x + half + 2, floor, z + half + 2,
                Blocks.PRISMARINE);
        for (int ox = -half; ox <= half; ox++) {
            for (int oz = -half; oz <= half; oz++) {
                for (int oy = 0; oy <= height; oy++) {
                    boolean shell = Math.abs(ox) == half || Math.abs(oz) == half || oy == 0 || oy == height;
                    if (shell) {
                        double roll = ((ox * 31 + oz * 17 + oy * 7) % 100 + 100) % 100 / 100.0;
                        buffer.set(x + ox, floor + oy, z + oz, roll < 0.55 ? Blocks.PRISMARINE
                                : roll < 0.85 ? Blocks.PRISMARINE_BRICKS : Blocks.DARK_PRISMARINE);
                    } else {
                        buffer.set(x + ox, floor + oy, z + oz, Blocks.WATER);
                    }
                }
            }
        }

        // Corner towers.
        for (int corner = 0; corner < 4; corner++) {
            int cx = (corner & 1) == 0 ? x - half : x + half;
            int cz = (corner & 2) == 0 ? z - half : z + half;
            BuildKit.box(buffer, cx - 3, floor, cz - 3, cx + 3, floor + height + 6, cz + 3,
                    Blocks.PRISMARINE_BRICKS);
            BuildKit.box(buffer, cx - 2, floor + 1, cz - 2, cx + 2, floor + height + 5, cz + 2, Blocks.WATER);
            buffer.set(cx, floor + height + 6, cz, Blocks.SEA_LANTERN);
            buffer.addSpawn(MobSpawn.mob(cx, floor + 3, cz, "GUARDIAN", 2));
        }

        // Interior halls: a grid of pillars with lantern capitals.
        for (int ox = -half + 4; ox <= half - 4; ox += 6) {
            for (int oz = -half + 4; oz <= half - 4; oz += 6) {
                for (int oy = 1; oy < height; oy++) {
                    buffer.set(x + ox, floor + oy, z + oz, Blocks.DARK_PRISMARINE);
                }
                buffer.set(x + ox, floor + height - 1, z + oz, Blocks.SEA_LANTERN);
            }
        }

        // Sponge room.
        int sx = x + half / 2;
        int sz = z - half / 2;
        BuildKit.box(buffer, sx - 3, floor + 1, sz - 3, sx + 3, floor + 4, sz + 3, Blocks.PRISMARINE_BRICKS);
        BuildKit.box(buffer, sx - 2, floor + 1, sz - 2, sx + 2, floor + 3, sz + 2, Blocks.WATER);
        for (int i = 0; i < 8; i++) {
            buffer.set(sx + random.nextInt(-2, 2), floor + 1 + random.nextInt(0, 2),
                    sz + random.nextInt(-2, 2), Blocks.WET_SPONGE);
        }

        // Treasure core: gold under dark prismarine, guarded.
        BuildKit.box(buffer, x - 3, floor + 1, z - 3, x + 3, floor + 5, z + 3, Blocks.DARK_PRISMARINE);
        BuildKit.box(buffer, x - 2, floor + 2, z - 2, x + 2, floor + 4, z + 2, Blocks.WATER);
        BuildKit.box(buffer, x - 1, floor + 2, z - 1, x + 1, floor + 2, z + 1, Blocks.GOLD_BLOCK);
        buffer.set(x, floor + 4, z, Blocks.SEA_LANTERN);
        BuildKit.chest(buffer, x + 2, floor + 2, z, 3, "monument");
        BuildKit.chest(buffer, x - 2, floor + 2, z, 3, "monument");

        buffer.addSpawn(MobSpawn.boss(x, floor + 3, z + 3, "ELDER_GUARDIAN", 4, "deep_leviathan"));
        for (int i = 0; i < 8; i++) {
            buffer.addSpawn(MobSpawn.mob(x + random.nextInt(-half + 3, half - 3), floor + 3,
                    z + random.nextInt(-half + 3, half - 3), "GUARDIAN", 2));
        }
    }
}
