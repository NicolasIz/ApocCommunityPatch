package com.arkcronist.gen.core.structure.types;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.math.MathUtil;
import com.arkcronist.gen.core.prefab.Prefab;
import com.arkcronist.gen.core.prefab.PrefabRegistry;
import com.arkcronist.gen.core.structure.BufferWriter;
import com.arkcronist.gen.core.structure.LootMarker;
import com.arkcronist.gen.core.structure.MobSpawn;
import com.arkcronist.gen.core.structure.PrefabFurnisher;
import com.arkcronist.gen.core.structure.SpawnerMarker;
import com.arkcronist.gen.core.structure.Structure;
import com.arkcronist.gen.core.structure.StructureBuffer;
import com.arkcronist.gen.core.structure.StructureContext;

import java.util.List;

/**
 * Any single-file building loaded from a prefab folder: a castle, a tower, a temple, a landmark.
 *
 * <p>One instance per category, so {@code prefabs/castles/} produces castles and
 * {@code prefabs/towers/} produces towers without either of them needing its own class. What a
 * category is called, which family it claims and how rare it is all come from
 * {@link com.arkcronist.gen.core.prefab.PrefabCategories}.</p>
 *
 * <p>The work that is not in the schematic is the siting. A file exported from a flat creative plot
 * has a flat base and no idea what a hillside is, so the placer levels the ground under the
 * footprint from both directions: it lays a foundation down to wherever the ground actually falls
 * away, and it cuts back whatever rises above the chosen floor. Both only inside the columns the
 * building really occupies, so a keep terraces itself into a slope instead of shaving a rectangle
 * out of the landscape.</p>
 */
public final class PrefabBuildingStructure implements Structure {

    private final PrefabRegistry prefabs;
    private final String category;
    private final String id;
    private final StructureTag tag;
    private final double weight;
    private final int radius;
    private final int tallest;
    /** Wide enough to need the landmark grid's spacing. */
    private final boolean sprawling;
    /** Imposing enough to deserve a boss and better loot - tall counts as much as wide here. */
    private final boolean landmark;

    public PrefabBuildingStructure(PrefabRegistry prefabs, String category, String id,
                                   StructureTag tag, double weight) {
        this.prefabs = prefabs;
        this.category = category;
        this.id = id;
        this.tag = tag;
        this.weight = weight;
        int widest = 8;
        int highest = 8;
        for (Prefab prefab : prefabs.category(category)) {
            widest = Math.max(widest, prefab.radius());
            highest = Math.max(highest, prefab.height);
        }
        this.radius = widest;
        this.tallest = highest;
        this.sprawling = widest > 24;
        this.landmark = widest > 24 || highest >= 40;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public StructureTag tag() {
        return tag;
    }

    @Override
    public int radius() {
        return radius;
    }

    @Override
    public Placement placement() {
        return sprawling ? Placement.SURFACE_LARGE : Placement.SURFACE_SMALL;
    }

    @Override
    public double weight() {
        return weight;
    }

    @Override
    public boolean canPlace(StructureContext context) {
        if (prefabs.category(category).isEmpty()) {
            return false;
        }
        if (context.submerged(context.originX, context.originZ)) {
            return false;
        }
        if (context.groundY <= context.seaLevel()) {
            return false;
        }
        if (context.groundY + tallest >= context.maxY()) {
            return false;
        }
        // The bigger the building, the flatter the ground it insists on: levelling a tower is a few
        // blocks of foundation, levelling a citadel would be an earthwork.
        // The bigger the footprint, the flatter the ground it insists on. A relief of fourteen is
        // a gentle rise under a nine block tower and a cliff under a forty block manor, so the
        // allowance shrinks as the building grows.
        int probe = Math.max(6, radius * 2 / 3);
        int allowed = (int) Math.round(MathUtil.clamp(24.0 - radius * 0.22, 12.0, 22.0));
        return context.relief(context.originX, context.originZ, probe) < allowed;
    }

    @Override
    public void build(StructureContext context, StructureBuffer buffer) {
        FastRandom random = context.random;
        Prefab prefab = prefabs.pick(category, null, sizeFor(context, random), random);
        if (prefab == null) {
            return;
        }
        int rotation = random.nextInt(4);
        int x = context.originX;
        int z = context.originZ;
        // Average rather than the exact centre column: a building should meet the ground it stands
        // on as a whole, not balance on whatever block happened to be under its middle.
        int baseY = context.averageHeight(Math.min(radius, 24)) - 1
                + context.engine.settings().prefabBuildingLift;
        if (baseY + prefab.height >= context.maxY()) {
            baseY = context.maxY() - prefab.height - 1;
        }

        BufferWriter writer = new BufferWriter(buffer, context.engine.settings().minY, context.maxY());
        level(context, writer, prefab, x, z, baseY, rotation);
        prefab.blit(writer, x, baseY, z, rotation, Prefab.BlitOptions.solid(Blocks.AIR));

        // Off by default: a prefab is placed exactly as its author built it, dark or not.
        if (context.engine.settings().prefabFurnish && !prefab.hasLight) {
            List<int[]> spots = PrefabFurnisher.interiorSpots(buffer, prefab, x, baseY, z, rotation, 24);
            PrefabFurnisher.light(buffer, spots, 0, landmark ? 6 : 3);
        }

        int tier = landmark ? 2 : 1;
        prefab.forEachContainer(x, baseY, z, rotation, (cx, cy, cz) ->
                buffer.addLoot(new LootMarker(cx, cy, cz, tier, category)));
        prefab.forEachSpawner(x, baseY, z, rotation, (cx, cy, cz) ->
                buffer.addSpawner(new SpawnerMarker(cx, cy, cz, "ZOMBIE")));
        garrison(context, buffer, random, prefab, x, z, baseY, rotation);
    }

    /** Larger presets reach for the biggest file in the folder; BASE stays modest. */
    private String sizeFor(StructureContext context, FastRandom random) {
        double roll = random.nextDouble();
        return switch (context.preset) {
            case BASE -> roll < 0.45 ? "medium" : roll < 0.85 ? "large" : "giant";
            case CHAOTIC -> roll < 0.25 ? "medium" : roll < 0.70 ? "large" : "giant";
            case INSANE -> roll < 0.10 ? "medium" : roll < 0.45 ? "large" : "giant";
        };
    }

    /**
     * Makes the ground meet the building.
     *
     * <p>Below the floor: a foundation down to the real surface, so nothing hangs in the air over a
     * dip. Above it: the hillside is cut back, so nothing is swallowed by a rise. Both are limited
     * to the columns the prefab occupies and to what {@link #canPlace} already allowed, which keeps
     * the earthwork to a few blocks in each direction.</p>
     */
    private void level(StructureContext context, BufferWriter writer, Prefab prefab,
                       int x, int z, int baseY, int rotation) {
        int outWidth = prefab.rotatedWidth(rotation);
        int outLength = prefab.rotatedLength(rotation);
        int minX = x - prefab.rotatedAnchorX(rotation);
        int minZ = z - prefab.rotatedAnchorZ(rotation);
        int floor = context.engine.settings().minY + 1;
        int stone = foundationBlock(context);
        int soil = context.biome.subsurface.pickAt(context.engine.seed(), x, baseY, z);
        int turf = context.biome.surface.pickAt(context.engine.seed(), x, baseY, z);

        for (int outX = 0; outX < outWidth; outX++) {
            for (int outZ = 0; outZ < outLength; outZ++) {
                if (!prefab.occupies(rotation, outX, outZ)) {
                    continue;
                }
                int worldX = minX + outX;
                int worldZ = minZ + outZ;
                int ground = context.height(worldX, worldZ);
                boolean rests = prefab.standsOn(rotation, outX, outZ);
                // Ground, not masonry. A column of the biome's own stone under a building reads as
                // a plinth it was dropped onto; soil under turf reads as the hill it stands on.
                if (rests) {
                    for (int y = Math.max(ground, floor); y < baseY; y++) {
                        writer.set(worldX, y, worldZ,
                                y == baseY - 1 ? turf : baseY - y <= 3 ? soil : stone);
                    }
                }
                // Cut back anything standing above the floor we chose; the prefab's own blocks are
                // written afterwards and win wherever it has any.
                for (int y = baseY; y <= ground; y++) {
                    writer.set(worldX, y, worldZ, Blocks.AIR);
                }
            }
        }
    }

    private int foundationBlock(StructureContext context) {
        return context.biome.stone.pickAt(context.engine.seed(), context.originX, context.groundY, context.originZ);
    }

    /** Something lives here. What, depends on whether this is a home or a ruin. */
    private void garrison(StructureContext context, StructureBuffer buffer, FastRandom random,
                          Prefab prefab, int x, int z, int baseY, int rotation) {
        int guards = switch (context.preset) {
            case BASE -> landmark ? 3 : 1;
            case CHAOTIC -> landmark ? 5 : 2;
            case INSANE -> landmark ? 8 : 3;
        };
        int spread = Math.max(2, radius / 2);
        for (int i = 0; i < guards; i++) {
            int gx = x + random.nextInt(-spread, spread);
            int gz = z + random.nextInt(-spread, spread);
            buffer.addSpawn(MobSpawn.mob(gx, baseY + 2, gz, random.chance(0.5) ? "ZOMBIE" : "SKELETON", 1));
        }
        if (landmark) {
            buffer.addSpawn(MobSpawn.boss(x, baseY + 3, z, "WITHER_SKELETON", 3,
                    "Guardian of the " + displayName()));
        }
    }

    private String displayName() {
        String single = category.endsWith("s") ? category.substring(0, category.length() - 1) : category;
        return Character.toUpperCase(single.charAt(0)) + single.substring(1).replace('_', ' ');
    }

    /** The files this structure can draw from; used by the prefab tests. */
    public List<Prefab> pool() {
        return prefabs.category(category);
    }
}
