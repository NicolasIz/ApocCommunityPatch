package com.arkcronist.gen.core.prefab;

import com.arkcronist.gen.core.structure.RegionWriter;

import java.util.Set;

/**
 * A loaded schematic, ready to be stamped into the world at any of four rotations.
 *
 * <p>The block array is stored once in its original orientation; a rotation is applied by mapping
 * coordinates on the way out and by using a pre-rotated palette, so all four orientations cost one
 * copy of the geometry and four copies of a few hundred strings.</p>
 *
 * <p>Two things are worked out once at load time and matter a great deal on the ground:</p>
 * <ul>
 *   <li><b>Anchor</b> - the trunk, mast or centre column at the prefab's base. Placement positions
 *       the anchor, not the corner of the bounding box, so a tree stands where the placer picked
 *       ground rather than a random distance away from it.</li>
 *   <li><b>Interior air</b> - the air cells the schematic encloses, found by flooding in from the
 *       outside. Only those are written as air, which is what lets a ship keep a dry cabin while
 *       floating without punching a rectangular hole through the ocean around it.</li>
 * </ul>
 */
public final class Prefab {

    /** Width of one tile used to skip parts of a prefab that the writer cannot accept. */
    private static final int TILE = 16;

    public final String id;
    public final String category;
    public final Set<String> tags;
    public final String sizeClass;
    public final double weight;
    public final int width;
    public final int height;
    public final int length;
    /** Water surface height inside the prefab, in local Y. Meaningful for vessels. */
    public final int waterline;
    public final int solidCount;
    /** True when the schematic already contains a bed, so a village need not add one. */
    public final boolean hasBed;
    /** True when the schematic already lights itself. */
    public final boolean hasLight;

    /** palette index -> block registry id, for each of the four rotations. */
    private final int[][] palettes;
    /** palette index -> true when that entry is some flavour of air. */
    private final boolean[] paletteAir;
    /** width*height*length palette indices, in Y-Z-X order, unrotated. */
    private final char[] blocks;
    /** One bit per cell: air the schematic encloses, as opposed to air around it. */
    private final long[] interiorAir;
    private final int anchorX;
    private final int anchorZ;
    /** Cell indices of chests, barrels and spawners, so a prefab's containers can be filled. */
    private final int[] containers;
    private final int[] spawners;
    /** One bit per XZ column: does this prefab put anything at all in it? */
    private final long[] footprint;
    /** One bit per XZ column: the columns the prefab rests on, ignoring overhangs. */
    private final long[] footing;

    Prefab(String id, String category, Set<String> tags, String sizeClass, double weight,
           int width, int height, int length, int waterline,
           int[][] palettes, boolean[] paletteAir, char[] blocks, long[] interiorAir,
           int anchorX, int anchorZ, int solidCount, int[] containers, int[] spawners,
           long[] footprint, long[] footing, boolean hasBed, boolean hasLight) {
        this.id = id;
        this.category = category;
        this.tags = tags;
        this.sizeClass = sizeClass;
        this.weight = weight;
        this.width = width;
        this.height = height;
        this.length = length;
        this.waterline = waterline;
        this.palettes = palettes;
        this.paletteAir = paletteAir;
        this.blocks = blocks;
        this.interiorAir = interiorAir;
        this.anchorX = anchorX;
        this.anchorZ = anchorZ;
        this.solidCount = solidCount;
        this.containers = containers;
        this.spawners = spawners;
        this.footprint = footprint;
        this.footing = footing;
        this.hasBed = hasBed;
        this.hasLight = hasLight;
    }

    /**
     * Whether the prefab occupies a column, given in the rotated frame the caller sees.
     *
     * <p>Used when a building has to make room for itself: only the columns it actually stands in
     * are levelled, so a keep cuts its own terrace out of a slope instead of shaving a rectangle out
     * of the landscape around it.</p>
     */
    /** Whether the prefab rests on a column, which is where a foundation belongs. */
    public boolean standsOn(int rotation, int outX, int outZ) {
        return maskAt(footing, rotation, outX, outZ);
    }

    public boolean occupies(int rotation, int outX, int outZ) {
        return maskAt(footprint, rotation, outX, outZ);
    }

    private boolean maskAt(long[] mask, int rotation, int outX, int outZ) {
        int turns = rotation & 3;
        int sourceX;
        int sourceZ;
        switch (turns) {
            case 1 -> {
                sourceX = outZ;
                sourceZ = length - 1 - outX;
            }
            case 2 -> {
                sourceX = width - 1 - outX;
                sourceZ = length - 1 - outZ;
            }
            case 3 -> {
                sourceX = width - 1 - outZ;
                sourceZ = outX;
            }
            default -> {
                sourceX = outX;
                sourceZ = outZ;
            }
        }
        if (sourceX < 0 || sourceZ < 0 || sourceX >= width || sourceZ >= length) {
            return false;
        }
        int column = sourceZ * width + sourceX;
        return (mask[column >>> 6] & (1L << (column & 63))) != 0L;
    }

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    /** Footprint width after rotating by the given number of quarter turns. */
    public int rotatedWidth(int rotation) {
        return (rotation & 1) == 0 ? width : length;
    }

    public int rotatedLength(int rotation) {
        return (rotation & 1) == 0 ? length : width;
    }

    public int rotatedAnchorX(int rotation) {
        return switch (rotation & 3) {
            case 1 -> length - 1 - anchorZ;
            case 2 -> width - 1 - anchorX;
            case 3 -> anchorZ;
            default -> anchorX;
        };
    }

    public int rotatedAnchorZ(int rotation) {
        return switch (rotation & 3) {
            case 1 -> anchorX;
            case 2 -> length - 1 - anchorZ;
            case 3 -> width - 1 - anchorX;
            default -> anchorZ;
        };
    }

    /** The largest horizontal reach from the anchor, used for structure radii and spacing. */
    public int radius() {
        int x = Math.max(anchorX, width - 1 - anchorX);
        int z = Math.max(anchorZ, length - 1 - anchorZ);
        return Math.max(x, z) + 1;
    }

    /**
     * Stamps the prefab into a writer.
     *
     * @param writer      destination, which clips whatever falls outside the area being generated
     * @param originX     world X the anchor lands on
     * @param baseY       world Y of the prefab's own Y=0 layer
     * @param originZ     world Z the anchor lands on
     * @param rotation    quarter turns clockwise, 0-3
     * @param options     how to treat enclosed air and how much of the prefab to damage
     * @return the number of blocks actually handed to the writer
     */
    public int blit(RegionWriter writer, int originX, int baseY, int originZ, int rotation, BlitOptions options) {
        int turns = rotation & 3;
        int outWidth = rotatedWidth(turns);
        int outLength = rotatedLength(turns);
        int minX = originX - rotatedAnchorX(turns);
        int minZ = originZ - rotatedAnchorZ(turns);
        if (!writer.intersectsColumn(minX, minZ, minX + outWidth - 1, minZ + outLength - 1)) {
            return 0;
        }
        int[] palette = palettes[turns];
        int minY = Math.max(0, writer.minY() - baseY);
        int maxY = Math.min(height - 1, writer.maxY() - 1 - baseY);
        if (minY > maxY) {
            return 0;
        }
        int written = 0;

        // Walk the footprint in tiles so that a prefab spanning nine chunks only pays for the part
        // the current writer can actually accept.
        for (int tileZ = 0; tileZ < outLength; tileZ += TILE) {
            int tileMaxZ = Math.min(outLength - 1, tileZ + TILE - 1);
            for (int tileX = 0; tileX < outWidth; tileX += TILE) {
                int tileMaxX = Math.min(outWidth - 1, tileX + TILE - 1);
                if (!writer.intersectsColumn(minX + tileX, minZ + tileZ, minX + tileMaxX, minZ + tileMaxZ)) {
                    continue;
                }
                written += blitTile(writer, minX, baseY, minZ, turns, palette, options,
                        tileX, tileMaxX, tileZ, tileMaxZ, minY, maxY);
            }
        }
        return written;
    }

    private int blitTile(RegionWriter writer, int minX, int baseY, int minZ, int turns,
                         int[] palette, BlitOptions options,
                         int tileX, int tileMaxX, int tileZ, int tileMaxZ, int minY, int maxY) {
        int written = 0;
        boolean hollow = options.fillInterior();
        double decay = options.decay();
        long decaySeed = options.decaySeed();
        for (int y = minY; y <= maxY; y++) {
            int worldY = baseY + y;
            int layer = y * width * length;
            for (int outZ = tileZ; outZ <= tileMaxZ; outZ++) {
                for (int outX = tileX; outX <= tileMaxX; outX++) {
                    int sourceX;
                    int sourceZ;
                    switch (turns) {
                        case 1 -> {
                            sourceX = outZ;
                            sourceZ = length - 1 - outX;
                        }
                        case 2 -> {
                            sourceX = width - 1 - outX;
                            sourceZ = length - 1 - outZ;
                        }
                        case 3 -> {
                            sourceX = width - 1 - outZ;
                            sourceZ = outX;
                        }
                        default -> {
                            sourceX = outX;
                            sourceZ = outZ;
                        }
                    }
                    int cell = layer + sourceZ * width + sourceX;
                    int index = blocks[cell];
                    boolean air = paletteAir[index];
                    if (air && !(hollow && isInterior(cell))) {
                        continue;
                    }
                    if (decay > 0.0 && !air && damaged(decaySeed, cell, y, decay)) {
                        continue;
                    }
                    writer.set(minX + outX, worldY, minZ + outZ, air ? options.airBlock() : palette[index]);
                    written++;
                }
            }
        }
        return written;
    }

    private boolean isInterior(int cell) {
        return (interiorAir[cell >>> 6] & (1L << (cell & 63))) != 0L;
    }

    /**
     * Deterministic damage for wrecks and ruins.
     *
     * <p>Damage grows with height so a hull stays recognisable while masts and rigging come apart,
     * which reads as a wreck rather than as a randomly holed boat.</p>
     */
    private boolean damaged(long seed, int cell, int y, double decay) {
        double topBias = height <= 1 ? 1.0 : (double) y / (height - 1);
        double chance = decay * (0.35 + 1.3 * topBias);
        long hash = seed ^ (cell * 0x9E3779B97F4A7C15L);
        hash ^= hash >>> 30;
        hash *= 0xBF58476D1CE4E5B9L;
        hash ^= hash >>> 27;
        double roll = ((hash >>> 11) & ((1L << 53) - 1)) / (double) (1L << 53);
        return roll < chance;
    }

    /** Reports where a stamped prefab put its containers and spawners, in world coordinates. */
    public interface MarkerSink {
        void accept(int x, int y, int z);
    }

    /** Walks the chests and barrels of a placement; coordinates match what {@link #blit} wrote. */
    public void forEachContainer(int originX, int baseY, int originZ, int rotation, MarkerSink sink) {
        walk(containers, originX, baseY, originZ, rotation, sink);
    }

    /** Walks the spawner blocks of a placement. */
    public void forEachSpawner(int originX, int baseY, int originZ, int rotation, MarkerSink sink) {
        walk(spawners, originX, baseY, originZ, rotation, sink);
    }

    private void walk(int[] cells, int originX, int baseY, int originZ, int rotation, MarkerSink sink) {
        int turns = rotation & 3;
        int minX = originX - rotatedAnchorX(turns);
        int minZ = originZ - rotatedAnchorZ(turns);
        for (int cell : cells) {
            int sourceX = cell % width;
            int sourceZ = (cell / width) % length;
            int y = cell / (width * length);
            int outX;
            int outZ;
            switch (turns) {
                case 1 -> {
                    outX = length - 1 - sourceZ;
                    outZ = sourceX;
                }
                case 2 -> {
                    outX = width - 1 - sourceX;
                    outZ = length - 1 - sourceZ;
                }
                case 3 -> {
                    outX = sourceZ;
                    outZ = width - 1 - sourceX;
                }
                default -> {
                    outX = sourceX;
                    outZ = sourceZ;
                }
            }
            sink.accept(minX + outX, baseY + y, minZ + outZ);
        }
    }

    /** How a prefab should be stamped: enclosed air, damage, and what "air" means. */
    public record BlitOptions(boolean fillInterior, int airBlock, double decay, long decaySeed) {

        public static BlitOptions solid(int airBlock) {
            return new BlitOptions(true, airBlock, 0.0, 0L);
        }

        public static BlitOptions flooded() {
            return new BlitOptions(false, 0, 0.0, 0L);
        }

        public static BlitOptions wreck(long seed, double decay) {
            return new BlitOptions(false, 0, decay, seed);
        }
    }
}
