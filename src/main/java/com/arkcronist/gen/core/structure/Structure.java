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

    String id();

    StructureTag tag();

    /** Maximum horizontal reach from the origin, in blocks. Used for chunk neighbourhood search. */
    int radius();

    /** Relative selection weight among the structures a biome allows. */
    double weight();

    /** Terrain sanity check: slope, water, altitude, room to build. */
    boolean canPlace(StructureContext context);

    void build(StructureContext context, StructureBuffer buffer);
}
