package com.arkcronist.content.core.furniture;

import java.util.ArrayList;
import java.util.List;

/**
 * The blocks a piece of furniture stands on when it is more than one: a quest board three blocks
 * wide and two high, a wardrobe two high. Every one of them gets an invisible support of its own,
 * so the whole piece is solid and a click or a punch anywhere on it reaches it.
 *
 * <p>The width runs across the piece's front - from the left of someone looking at it to their
 * right - centred on the block it was placed on (one block further right for an even width), the
 * height runs up from it, and the depth is one block. The model is authored centred on the
 * footprint, front to the north, like other furniture.</p>
 *
 * @param width  blocks across the front, 1-5
 * @param height blocks up, 1-5
 */
public record Footprint(int width, int height) {

    public static final Footprint SINGLE = new Footprint(1, 1);
    public static final int MAX = 5;

    public Footprint {
        if (width < 1 || width > MAX || height < 1 || height > MAX) {
            throw new IllegalArgumentException("a footprint is 1 to " + MAX + " blocks each way, not " + width + "x" + height);
        }
    }

    public boolean single() {
        return width == 1 && height == 1;
    }

    /** How many blocks it covers. */
    public int blocks() {
        return width * height;
    }

    /**
     * Every block it covers, as {x, y, z} offsets from the block it was placed on, that block first.
     *
     * @param yaw the piece's yaw: south 0, west 90, north 180, east 270; snapped to the nearest
     */
    public List<int[]> offsets(float yaw) {
        int[] right = right(yaw);
        int first = -(width - 1) / 2;
        List<int[]> offsets = new ArrayList<>();
        offsets.add(new int[] {0, 0, 0});
        for (int up = 0; up < height; up++) {
            for (int across = first; across < first + width; across++) {
                if (up == 0 && across == 0) {
                    continue;
                }
                offsets.add(new int[] {right[0] * across, up, right[1] * across});
            }
        }
        return offsets;
    }

    /**
     * How far the model's centre is from the centre of the block it was placed on, {x, z} in blocks:
     * nothing for an odd width, half a block to the right for an even one.
     */
    public double[] centreShift(float yaw) {
        int[] right = right(yaw);
        double across = -(width - 1) / 2 + (width - 1) / 2.0;
        return new double[] {right[0] * across, right[1] * across};
    }

    /**
     * The right hand of someone looking at the front of a piece turned to {@code yaw}, {dx, dz}:
     * facing south, they look north, and their right is east.
     */
    public static int[] right(float yaw) {
        int quarter = Math.floorMod(Math.round(yaw / 90f), 4);
        return switch (quarter) {
            case 0 -> new int[] {1, 0};    // facing south
            case 1 -> new int[] {0, 1};    // facing west
            case 2 -> new int[] {-1, 0};   // facing north
            default -> new int[] {0, -1};  // facing east
        };
    }
}
