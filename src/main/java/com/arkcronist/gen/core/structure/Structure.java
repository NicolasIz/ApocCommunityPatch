package com.arkcronist.gen.core.structure;

import com.arkcronist.gen.core.biome.StructureTag;

/**
 * A procedurally built structure.
 *
 * <p>Structures draw into a {@link StructureBuffer} in world coordinates and are expected to adapt to
 * the terrain themselves: level their own platform, sink foundations into the slope and clear the
 * space above. The placer only decides <em>where</em>.</p>
 */
public interface Structure {

    /** Which grid a structure is placed on, and what its site check means. */
    enum Placement {
        /** Landmarks: cities, castles, villages, monuments. Rare, far apart. */
        SURFACE_LARGE,
        /** Finds: towers, camps, ruins, wrecks. Common, close together. */
        SURFACE_SMALL,
        /** Placed by depth rather than by biome: mines, strongholds, vaults, geodes. */
        UNDERGROUND,
        /**
         * One rare landmark deep underground, on a grid of its own.
         *
         * <p>The ancient city is the only member. It is far too large to share the underground grid
         * with mines and geodes - it would collide with them and turn up far too often - so it gets
         * its own spacing and its own salt.</p>
         */
        DEEP_LANDMARK
    }

    /** Defaults by size; underground structures override it. */
    default Placement placement() {
        return radius() > 24 ? Placement.SURFACE_LARGE : Placement.SURFACE_SMALL;
    }

    String id();

    StructureTag tag();

    /** Maximum horizontal reach from the origin, in blocks. Used for chunk neighbourhood search. */
    int radius();

    /** Relative selection weight among the structures a biome allows. */
    double weight();

    /** Terrain sanity check: slope, water, altitude, room to build. */
    boolean canPlace(StructureContext context);

    void build(StructureContext context, StructureBuffer buffer);

    /**
     * The height worth telling a player about when they ask where this structure is.
     *
     * <p>The surface, for anything built on it. A structure that lives deep underground has to
     * override this, otherwise a search reports the ground overhead and sends the player digging in
     * the right column at the wrong depth - a hundred blocks off, for an ancient city.</p>
     */
    default int locateY(StructureContext context) {
        return context.groundY;
    }
}
