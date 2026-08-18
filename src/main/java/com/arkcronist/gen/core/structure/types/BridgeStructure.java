package com.arkcronist.gen.core.structure.types;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.structure.*;

/**
 * An arched bridge thrown across whatever gap is under it: a river, a canyon, a strait between two
 * cliffs.
 *
 * <p>The span is decided by probing the terrain outwards from the origin until solid ground comes
 * back up, so the bridge is only built where there is actually something to cross.</p>
 */
public final class BridgeStructure implements Structure {

    @Override
    public String id() {
        return "bridge";
    }

    @Override
    public StructureTag tag() {
        return StructureTag.BRIDGE;
    }

    @Override
    public int radius() {
        return 48;
    }

    @Override
    public double weight() {
        return 0.8;
    }

    @Override
    public boolean canPlace(StructureContext context) {
        return findSpan(context) > 0;
    }

    /** Returns the span length if the origin sits over a crossable gap, else 0. */
    private int findSpan(StructureContext context) {
        int x = context.originX;
        int z = context.originZ;
        int deckY = context.groundY;
        // A gap exists when the ground dips well below the origin and rises again within reach.
        int lowest = deckY;
        int span = 0;
        for (int distance = 3; distance <= 40; distance++) {
            int h = context.height(x, z + distance);
            lowest = Math.min(lowest, h);
            if (distance > 8 && h >= deckY - 2 && deckY - lowest > 6) {
                span = distance;
                break;
            }
        }
        return span;
    }

    @Override
    public void build(StructureContext context, StructureBuffer buffer) {
        FastRandom random = context.random;
        StructureMaterials materials = StructureMaterials.forBiome(context.biome, random);
        int span = findSpan(context);
        if (span <= 0) {
            return;
        }
        int x = context.originX;
        int z = context.originZ;
        int deckY = Math.max(context.groundY, context.height(x, z + span)) + 1;
        int width = random.nextInt(2, 4);

        for (int step = -2; step <= span + 2; step++) {
            int bz = z + step;
            for (int ox = -width; ox <= width; ox++) {
                buffer.set(x + ox, deckY, bz, materials.floor);
                BuildKit.clear(buffer, x + ox, deckY + 1, bz, x + ox, deckY + 4, bz);
            }
            // Railings, with lamp posts at intervals.
            buffer.set(x - width - 1, deckY, bz, materials.wall);
            buffer.set(x + width + 1, deckY, bz, materials.wall);
            buffer.set(x - width - 1, deckY + 1, bz, materials.fence);
            buffer.set(x + width + 1, deckY + 1, bz, materials.fence);
            if (step % 8 == 0) {
                buffer.set(x - width - 1, deckY + 2, bz, materials.fence);
                buffer.set(x - width - 1, deckY + 3, bz, materials.light);
                buffer.set(x + width + 1, deckY + 2, bz, materials.fence);
                buffer.set(x + width + 1, deckY + 3, bz, materials.light);
            }
        }

        // Piers and arches under the deck.
        int piers = Math.max(2, span / 12);
        for (int pier = 1; pier < piers; pier++) {
            int bz = z + span * pier / piers;
            int ground = context.height(x, bz);
            for (int y = deckY - 1; y >= ground; y--) {
                for (int ox = -1; ox <= 1; ox++) {
                    buffer.set(x + ox, y, bz, materials.wall);
                    buffer.set(x + ox, y, bz + 1, materials.wall);
                }
            }
            // Arch springing from the pier.
            int archRadius = Math.max(3, span / (piers * 2));
            for (int i = -archRadius; i <= archRadius; i++) {
                int height = (int) Math.round(Math.sqrt(Math.max(0, archRadius * archRadius - i * i)));
                for (int ox = -1; ox <= 1; ox++) {
                    buffer.set(x + ox, deckY - 1 - (archRadius - height), bz + i, materials.wallAccent);
                }
            }
        }

        if (random.chance(0.4)) {
            buffer.addSpawn(MobSpawn.mob(x, deckY + 1, z + span / 2, "PILLAGER", 2));
            BuildKit.chest(buffer, x + width, deckY + 1, z + span / 2, 1, "bridge");
        }
    }
}
