package com.arkcronist.gen.core.structure.types;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.prefab.Prefab;
import com.arkcronist.gen.core.prefab.PrefabRegistry;
import com.arkcronist.gen.core.structure.BufferWriter;
import com.arkcronist.gen.core.structure.LootMarker;
import com.arkcronist.gen.core.structure.Structure;
import com.arkcronist.gen.core.structure.StructureBuffer;
import com.arkcronist.gen.core.structure.StructureContext;
import com.arkcronist.gen.core.terrain.Preset;

import java.util.List;

/**
 * Sailing ships built from the schematics in {@code prefabs/ships/}.
 *
 * <p>The sea decides what a site becomes. Over deep water a hull is floated on the surface with its
 * cabins pumped dry; over a shelf or a beach the same schematic is dropped onto the bottom, sunk
 * into it, left open to the water and worn away from the masts down - which is the difference
 * between a ship and a wreck without needing two sets of files.</p>
 *
 * <p>Preset decides the scale: BASE keeps a lone cutter or schooner on the horizon, CHAOTIC sails
 * larger hulls more often, and INSANE puts whole first rates out there and occasionally a squadron
 * of them anchored together.</p>
 */
public final class ShipPrefabStructure implements Structure {

    private static final int DEEP_WATER = 14;

    private final PrefabRegistry prefabs;
    private final int radius;

    public ShipPrefabStructure(PrefabRegistry prefabs) {
        this.prefabs = prefabs;
        int widest = 16;
        for (Prefab prefab : prefabs.category("ships")) {
            widest = Math.max(widest, prefab.radius());
        }
        this.radius = widest;
    }

    @Override
    public String id() {
        return "prefab_ship";
    }

    @Override
    public StructureTag tag() {
        return StructureTag.SHIP;
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
        return 1.6;
    }

    @Override
    public boolean canPlace(StructureContext context) {
        if (prefabs.category("ships").isEmpty()) {
            return false;
        }
        // A hull needs open water on every side; half a ship sticking out of a headland is worse
        // than no ship at all.
        int probe = Math.max(12, radius / 2);
        int wet = 0;
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4.0;
            int px = context.originX + (int) Math.round(Math.cos(angle) * probe);
            int pz = context.originZ + (int) Math.round(Math.sin(angle) * probe);
            if (context.height(px, pz) < context.water(px, pz) - 1) {
                wet++;
            }
        }
        return wet >= 6 && context.waterY > context.groundY;
    }

    @Override
    public void build(StructureContext context, StructureBuffer buffer) {
        FastRandom random = context.random;
        int depth = context.waterY - context.groundY;
        boolean afloat = depth >= DEEP_WATER;
        int count = afloat ? squadronSize(context.preset, random) : 1;

        for (int index = 0; index < count; index++) {
            int spread = index == 0 ? 0 : radius + random.nextInt(6, 24);
            double angle = random.nextDouble() * Math.PI * 2.0;
            int x = context.originX + (int) Math.round(Math.cos(angle) * spread);
            int z = context.originZ + (int) Math.round(Math.sin(angle) * spread);
            int floor = context.height(x, z);
            int water = context.water(x, z);
            if (water - floor < 2 && index > 0) {
                continue;
            }
            place(context, buffer, random, x, z, floor, water, afloat && water - floor >= DEEP_WATER);
        }
    }

    private void place(StructureContext context, StructureBuffer buffer, FastRandom random,
                       int x, int z, int floor, int water, boolean afloat) {
        Prefab ship = prefabs.pick("ships", null, sizeFor(context.preset, afloat, random), random);
        if (ship == null) {
            return;
        }
        int rotation = random.nextInt(4);
        int baseY;
        Prefab.BlitOptions options;
        if (afloat) {
            // Sit the hull on the water rather than on the bottom, and keep the holds dry.
            baseY = water - ship.waterline;
            options = Prefab.BlitOptions.solid(Blocks.AIR);
        } else {
            // Aground: bed the keel into the bottom and let the sea take the rigging.
            baseY = floor - random.nextInt(1, 4);
            double decay = 0.10 + random.nextDouble() * 0.22;
            options = Prefab.BlitOptions.wreck(random.nextLong(), decay);
        }
        if (baseY + ship.height >= context.maxY()) {
            baseY = context.maxY() - ship.height - 1;
        }
        ship.blit(new BufferWriter(buffer, context.engine.settings().minY, context.maxY()),
                x, baseY, z, rotation, options);

        if (afloat) {
            hangDeckLanterns(buffer, ship, x, baseY, z, rotation, random);
        }

        int tier = afloat ? 2 : 1;
        String theme = afloat ? "ship" : "shipwreck";
        ship.forEachContainer(x, baseY, z, rotation, (cx, cy, cz) ->
                buffer.addLoot(new LootMarker(cx, cy, cz, tier, theme)));
    }

    /**
     * Hangs a few lanterns over a ship's deck.
     *
     * <p>Some of the schematics carry no light source at all, which on the water means a black hull
     * that breeds mobs the moment a player climbs aboard. Rather than editing the author's file, the
     * placer reads the hull back out of the buffer, finds the top of each of a handful of columns
     * above the waterline and sets a lantern on it - so the light lands on real deck planking and
     * never floats.</p>
     */
    private void hangDeckLanterns(StructureBuffer buffer, Prefab ship, int x, int baseY, int z,
                                  int rotation, FastRandom random) {
        int halfX = ship.rotatedWidth(rotation) / 2;
        int halfZ = ship.rotatedLength(rotation) / 2;
        int deck = baseY + ship.waterline;
        int hung = 0;
        for (int attempt = 0; attempt < 40 && hung < 4; attempt++) {
            int px = x + random.nextInt(-halfX / 2, halfX / 2 + 1);
            int pz = z + random.nextInt(-halfZ / 2, halfZ / 2 + 1);
            for (int y = deck + 8; y > deck; y--) {
                int below = buffer.get(px, y - 1, pz);
                if (below < 0 || below == Blocks.AIR) {
                    continue;
                }
                if (buffer.get(px, y, pz) == Blocks.AIR) {
                    buffer.set(px, y, pz, Blocks.LANTERN);
                    hung++;
                }
                break;
            }
        }
    }

    /** How many hulls one deep water site holds. Only the wilder presets ever field a squadron. */
    private int squadronSize(Preset preset, FastRandom random) {
        return switch (preset) {
            case BASE -> 1;
            case CHAOTIC -> random.chance(0.12) ? 2 : 1;
            case INSANE -> random.chance(0.30) ? random.nextInt(2, 4) : 1;
        };
    }

    /**
     * Which class of vessel a site gets.
     *
     * <p>Wrecks skew small - a cutter that ran aground is ordinary, a first rate on a beach is a
     * landmark - and each preset up the scale lets bigger hulls out to sea.</p>
     */
    private String sizeFor(Preset preset, boolean afloat, FastRandom random) {
        double roll = random.nextDouble();
        if (!afloat) {
            return roll < 0.45 ? "small" : roll < 0.85 ? "medium" : "large";
        }
        return switch (preset) {
            case BASE -> roll < 0.45 ? "small" : roll < 0.85 ? "medium" : "large";
            case CHAOTIC -> roll < 0.20 ? "small" : roll < 0.55 ? "medium" : roll < 0.90 ? "large" : "giant";
            case INSANE -> roll < 0.08 ? "small" : roll < 0.30 ? "medium" : roll < 0.65 ? "large" : "giant";
        };
    }

    /** The vessels this structure can draw from; used by the prefab tests. */
    public List<Prefab> pool() {
        return prefabs.category("ships");
    }
}
