package com.arkcronist.gen.core.structure.types;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.structure.*;

/** An amethyst geode: smooth basalt rind, calcite shell, budding interior, hollow crystal core. */
public final class GeodeStructure implements Structure {

    @Override
    public Placement placement() {
        return Placement.UNDERGROUND;
    }

    @Override
    public String id() {
        return "geode";
    }

    @Override
    public StructureTag tag() {
        return StructureTag.GEODE;
    }

    @Override
    public int radius() {
        return 12;
    }

    @Override
    public double weight() {
        return 1.6;
    }

    @Override
    public boolean canPlace(StructureContext context) {
        return context.groundY > context.engine().settings().minY + 30;
    }

    @Override
    public void build(StructureContext context, StructureBuffer buffer) {
        FastRandom random = context.random;
        int x = context.originX;
        int z = context.originZ;
        int minY = context.engine().settings().minY;
        int y = random.nextInt(minY + 8, Math.max(minY + 9, Math.min(context.groundY - 14, 30)));
        int radius = random.nextInt(5, 9);

        for (int ox = -radius - 2; ox <= radius + 2; ox++) {
            for (int oy = -radius - 2; oy <= radius + 2; oy++) {
                for (int oz = -radius - 2; oz <= radius + 2; oz++) {
                    double distance = Math.sqrt(ox * ox + oy * oy + oz * oz)
                            + random.nextDouble(-0.6, 0.6);
                    int block;
                    if (distance > radius + 2) {
                        continue;
                    } else if (distance > radius + 1) {
                        block = Blocks.SMOOTH_BASALT;
                    } else if (distance > radius) {
                        block = Blocks.CALCITE;
                    } else if (distance > radius - 1) {
                        block = random.chance(0.20) ? Blocks.BUDDING_AMETHYST : Blocks.AMETHYST_BLOCK;
                    } else {
                        block = Blocks.AIR;
                    }
                    buffer.set(x + ox, y + oy, z + oz, block);
                }
            }
        }

        // Crystals growing inwards from the shell.
        for (int i = 0; i < 40; i++) {
            double theta = random.nextDouble() * Math.PI * 2.0;
            double phi = random.nextDouble() * Math.PI;
            int cx = x + (int) Math.round(Math.sin(phi) * Math.cos(theta) * (radius - 1));
            int cy = y + (int) Math.round(Math.cos(phi) * (radius - 1));
            int cz = z + (int) Math.round(Math.sin(phi) * Math.sin(theta) * (radius - 1));
            buffer.set(cx, cy, cz, random.chance(0.5) ? Blocks.AMETHYST_CLUSTER_UP
                    : Blocks.LARGE_AMETHYST_BUD_UP);
        }
    }
}
