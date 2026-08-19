package com.arkcronist.gen.core.structure.types;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.prefab.Prefab;
import com.arkcronist.gen.core.prefab.PrefabRegistry;
import com.arkcronist.gen.core.structure.BufferWriter;
import com.arkcronist.gen.core.structure.LootMarker;
import com.arkcronist.gen.core.structure.MobSpawn;
import com.arkcronist.gen.core.structure.SpawnerMarker;
import com.arkcronist.gen.core.structure.Structure;
import com.arkcronist.gen.core.structure.StructureBuffer;
import com.arkcronist.gen.core.structure.StructureContext;

import java.util.List;

/**
 * Landmarks built from the schematics in {@code prefabs/ruins/}.
 *
 * <p>These are the big set pieces - a ruined citadel a hundred blocks tall is one schematic, not a
 * hundred lines of tower-building code - so the placer is careful about where one lands: it demands
 * genuinely level ground, then sinks the base a little and lays a stone apron under the footprint so
 * the walls meet the hillside instead of hovering over it.</p>
 */
public final class PrefabRuinStructure implements Structure {

    private final PrefabRegistry prefabs;
    private final int radius;

    public PrefabRuinStructure(PrefabRegistry prefabs) {
        this.prefabs = prefabs;
        int widest = 16;
        for (Prefab prefab : prefabs.category("ruins")) {
            widest = Math.max(widest, prefab.radius());
        }
        this.radius = widest;
    }

    @Override
    public String id() {
        return "prefab_ruin";
    }

    @Override
    public StructureTag tag() {
        return StructureTag.PREFAB_RUIN;
    }

    @Override
    public int radius() {
        return radius;
    }

    @Override
    public Placement placement() {
        return Placement.SURFACE_LARGE;
    }

    @Override
    public double weight() {
        return 0.9;
    }

    @Override
    public boolean canPlace(StructureContext context) {
        if (prefabs.category("ruins").isEmpty()) {
            return false;
        }
        if (context.submerged(context.originX, context.originZ)) {
            return false;
        }
        if (context.groundY <= context.seaLevel()) {
            return false;
        }
        return context.relief(context.originX, context.originZ, radius * 2 / 3) < 14;
    }

    @Override
    public void build(StructureContext context, StructureBuffer buffer) {
        FastRandom random = context.random;
        Prefab ruin = prefabs.pick("ruins", null, null, random);
        if (ruin == null) {
            return;
        }
        int rotation = random.nextInt(4);
        int x = context.originX;
        int z = context.originZ;
        // Average rather than the exact centre column: a landmark should meet the ground it stands
        // on as a whole, not balance on whatever block happened to be under its middle.
        int baseY = context.averageHeight(Math.min(radius, 24)) - 1;
        if (baseY + ruin.height >= context.maxY()) {
            baseY = context.maxY() - ruin.height - 1;
        }

        BufferWriter writer = new BufferWriter(buffer, context.engine.settings().minY, context.maxY());
        foundation(context, writer, ruin, x, z, baseY, rotation);
        ruin.blit(writer, x, baseY, z, rotation, Prefab.BlitOptions.solid(Blocks.AIR));

        ruin.forEachContainer(x, baseY, z, rotation, (cx, cy, cz) ->
                buffer.addLoot(new LootMarker(cx, cy, cz, 2, "ruins")));
        ruin.forEachSpawner(x, baseY, z, rotation, (cx, cy, cz) ->
                buffer.addSpawner(new SpawnerMarker(cx, cy, cz, "ZOMBIE")));
        garrison(context, buffer, random, x, z, baseY);
    }

    /**
     * Fills the gap between a flat schematic base and real ground.
     *
     * <p>Only downwards, and only where the ground is actually lower - the prefab's own blocks still
     * win everywhere they exist, so this never shows through a floor.</p>
     */
    private void foundation(StructureContext context, BufferWriter writer, Prefab ruin,
                            int x, int z, int baseY, int rotation) {
        int halfX = ruin.rotatedWidth(rotation) / 2;
        int halfZ = ruin.rotatedLength(rotation) / 2;
        for (int dx = -halfX; dx <= halfX; dx++) {
            for (int dz = -halfZ; dz <= halfZ; dz++) {
                int ground = context.height(x + dx, z + dz);
                for (int y = Math.max(ground, context.engine.settings().minY + 1); y < baseY; y++) {
                    writer.set(x + dx, y, z + dz, Blocks.STONE_BRICKS);
                }
            }
        }
    }

    /** Something has moved into the ruin; a landmark this size should not be empty. */
    private void garrison(StructureContext context, StructureBuffer buffer, FastRandom random,
                          int x, int z, int baseY) {
        int guards = switch (context.preset) {
            case BASE -> 3;
            case CHAOTIC -> 5;
            case INSANE -> 8;
        };
        for (int i = 0; i < guards; i++) {
            int gx = x + random.nextInt(-radius / 2, radius / 2 + 1);
            int gz = z + random.nextInt(-radius / 2, radius / 2 + 1);
            buffer.addSpawn(MobSpawn.mob(gx, baseY + 2, gz, random.chance(0.5) ? "ZOMBIE" : "SKELETON", 1));
        }
        buffer.addSpawn(MobSpawn.boss(x, baseY + 3, z, "WITHER_SKELETON", 3, "Guardian of the Citadel"));
    }

    /** The landmarks this structure can draw from; used by the prefab tests. */
    public List<Prefab> pool() {
        return prefabs.category("ruins");
    }
}
