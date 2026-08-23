package com.arkcronist.gen.core.structure.types;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.prefab.Prefab;
import com.arkcronist.gen.core.prefab.PrefabRegistry;
import com.arkcronist.gen.core.structure.BufferWriter;
import com.arkcronist.gen.core.structure.Structure;
import com.arkcronist.gen.core.structure.StructureBuffer;
import com.arkcronist.gen.core.structure.StructureContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A stand of trees placed whole, from one file.
 *
 * <p>Everything else that plants a tree does it one tree at a time, from {@code prefabs/trees/},
 * and that is the right way to fill a wood. This is for the case where the file <em>is</em> the
 * wood: four giant trees leaning into each other over the ground they grew from, cut as one
 * selection. Taking it apart into four schematics would throw away the thing that makes it worth
 * having - the canopies interlock and the trunks lean - so it is never taken apart. It goes into
 * the world entire or it does not go in.</p>
 *
 * <p>That size is also why this is a structure and not a decoration. The stand is 121 blocks
 * across, eight chunks, and the decoration pass can only write to the chunk it is called for and
 * its immediate neighbours. Only the structure pass carries a buffer across a whole
 * neighbourhood.</p>
 *
 * <p>What it does that a building does not: it lays no plinth. A building levels every column it
 * occupies, which is right for a keep and wrong for a wood - a column forty blocks under a branch
 * has no business being flattened. Ground is only made up under the columns the stand actually
 * rests on, which for this file is the patch of soil the trees stand in.</p>
 */
public final class GroveStructure implements Structure {

    /** The folder groves are loaded from. */
    public static final String CATEGORY = "groves";

    private final PrefabRegistry prefabs;
    private final int radius;
    private final int tallest;

    public GroveStructure(PrefabRegistry prefabs) {
        this.prefabs = prefabs;
        int widest = 8;
        int highest = 8;
        for (Prefab prefab : prefabs.category(CATEGORY)) {
            widest = Math.max(widest, prefab.radius());
            highest = Math.max(highest, prefab.height);
        }
        this.radius = widest;
        this.tallest = highest;
    }

    /** Whether any grove was supplied. Without one this structure does not register. */
    public boolean available() {
        return !prefabs.category(CATEGORY).isEmpty();
    }

    @Override
    public String id() {
        return "grove";
    }

    @Override
    public StructureTag tag() {
        return StructureTag.GROVE;
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
        // Heavy on purpose. This is not a landmark competing for a cell, it is the wood of the biome
        // that asks for it: where a scarlet forest offers a cell, the grove should almost always be
        // what stands in it, because there is nothing else planting those trees.
        return 9.0;
    }

    @Override
    public boolean canPlace(StructureContext context) {
        if (prefabs.category(CATEGORY).isEmpty()) {
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
        // Gentler than a building asks for, and deliberately much gentler than the first attempt.
        // Trees do not mind a slope the way a floor does, and the stand is only pinned to the ground
        // where its trunks are, so a few blocks of fall between one side and the other reads as a
        // wood on a hillside rather than as a mistake. Measuring the relief over two thirds of a
        // sixty-block radius and then demanding under eighteen blocks of it was asking for a
        // parade ground. What the stand actually cannot survive is a cliff through the middle of
        // it, which is what a tight probe catches.
        return context.relief(context.originX, context.originZ, 24) < 26;
    }

    @Override
    public void build(StructureContext context, StructureBuffer buffer) {
        List<Prefab> pool = prefabs.category(CATEGORY);
        if (pool.isEmpty()) {
            return;
        }
        FastRandom random = context.random;
        Prefab grove = pool.size() == 1 ? pool.get(0) : pool.get(random.nextInt(pool.size()));
        int rotation = random.nextInt(4);
        int x = context.originX;
        int z = context.originZ;

        // The ground under the columns the stand actually rests on, taken high rather than average.
        //
        // The average of the whole footprint was wrong in a way that showed up immediately in a
        // cross section: the footprint is 121 blocks across and mostly canopy, so its average has
        // little to do with where the trunks are, and wherever the real ground came out above that
        // average the levelling below dug a pit. The file only carries four or five courses of soil,
        // so a twenty block cut got five blocks of fill and left a hole.
        //
        // Sitting the stand near the TOP of its own footing turns almost every column into a fill
        // instead of a cut, and a fill is soil heaped round the base of a trunk - which is what the
        // foot of a big tree looks like anyway.
        int baseY = footingLevel(context, grove, x, z, rotation) - 1;
        if (baseY + grove.height >= context.maxY()) {
            baseY = context.maxY() - grove.height - 1;
        }

        BufferWriter writer = new BufferWriter(buffer, context.engine.settings().minY, context.maxY());
        bed(context, writer, grove, x, z, baseY, rotation);
        grove.blit(writer, x, baseY, z, rotation, Prefab.BlitOptions.solid(Blocks.AIR));
    }

    /** How far a rise may be cut back, in blocks. About what the file can fill in again. */
    private static final int CUTBACK = 5;

    /**
     * The level the stand's own footing wants to sit at.
     *
     * <p>The eightieth percentile of the ground under the columns it rests on, so a few high corners
     * do not lift the whole wood off the ground and the great majority of it is filled up to rather
     * than cut down to.</p>
     */
    private int footingLevel(StructureContext context, Prefab grove, int x, int z, int rotation) {
        int outWidth = grove.rotatedWidth(rotation);
        int outLength = grove.rotatedLength(rotation);
        int minX = x - grove.rotatedAnchorX(rotation);
        int minZ = z - grove.rotatedAnchorZ(rotation);
        List<Integer> heights = new ArrayList<>();
        // Every fourth column: the pad is thousands of columns and the percentile does not need
        // all of them.
        for (int outX = 0; outX < outWidth; outX += 4) {
            for (int outZ = 0; outZ < outLength; outZ += 4) {
                if (grove.standsOn(rotation, outX, outZ)) {
                    heights.add(context.height(minX + outX, minZ + outZ));
                }
            }
        }
        if (heights.isEmpty()) {
            return context.averageHeight(Math.min(radius, 32));
        }
        Collections.sort(heights);
        return heights.get(Math.min(heights.size() - 1, (int) (heights.size() * 0.8)));
    }

    /**
     * Makes the ground meet the trees, and only where the trees touch it.
     *
     * <p>Restricted to the columns the stand rests on. A building levels everything it occupies;
     * doing that here would shave the hillside out from under every column a branch happens to
     * reach over, and leave the wood standing on a rectangular table.</p>
     */
    private void bed(StructureContext context, BufferWriter writer, Prefab grove,
                     int x, int z, int baseY, int rotation) {
        int outWidth = grove.rotatedWidth(rotation);
        int outLength = grove.rotatedLength(rotation);
        int minX = x - grove.rotatedAnchorX(rotation);
        int minZ = z - grove.rotatedAnchorZ(rotation);
        int floor = context.engine.settings().minY + 1;
        int soil = context.biome.subsurface.pickAt(context.engine.seed(), x, baseY, z);
        int turf = context.biome.surface.pickAt(context.engine.seed(), x, baseY, z);

        for (int outX = 0; outX < outWidth; outX++) {
            for (int outZ = 0; outZ < outLength; outZ++) {
                if (!grove.standsOn(rotation, outX, outZ)) {
                    continue;
                }
                int worldX = minX + outX;
                int worldZ = minZ + outZ;
                int ground = context.height(worldX, worldZ);
                // Fill a dip up to the stand's own floor, in the biome's soil, so no trunk is left
                // hanging over a hollow.
                for (int y = Math.max(ground, floor); y < baseY; y++) {
                    writer.set(worldX, y, worldZ, y == baseY - 1 ? turf : soil);
                }
                // And clear a rise back down to it, so no trunk is buried to the knee - but only a
                // little way. The file has five courses of soil to give back; cutting deeper than it
                // can fill is what dug the pits, so anything beyond that is left as the hillside it
                // is and the trunk simply stands in it.
                int cut = Math.min(ground, baseY + CUTBACK);
                for (int y = baseY; y <= cut; y++) {
                    writer.set(worldX, y, worldZ, Blocks.AIR);
                }
            }
        }
    }

    /** The files this structure can draw from; used by the prefab tests. */
    public List<Prefab> pool() {
        return prefabs.category(CATEGORY);
    }
}
