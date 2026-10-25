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
 * height runs up from it, and the depth (3.1) runs back from it, away from whoever placed it. The
 * model is authored centred on the footprint, front to the north, like other furniture.</p>
 *
 * @param width  blocks across the front, 1-6
 * @param height blocks up, 1-6
 * @param depth  blocks back from the front, 1-6
 */
public record Footprint(int width, int height, int depth) {

    public static final Footprint SINGLE = new Footprint(1, 1, 1);
    /** 3.1: six, for a square's well; five before. */
    public static final int MAX = 6;

    public Footprint {
        if (width < 1 || width > MAX || height < 1 || height > MAX || depth < 1 || depth > MAX) {
            throw new IllegalArgumentException("a footprint is 1 to " + MAX + " blocks each way, not " + width + "x"
                    + height + "x" + depth);
        }
    }

    /** One block deep, as every footprint was before 3.1. */
    public Footprint(int width, int height) {
        this(width, height, 1);
    }

    public boolean single() {
        return width == 1 && height == 1 && depth == 1;
    }

    /** How many blocks it covers. */
    public int blocks() {
        return width * height * depth;
    }

    /**
     * Every block it covers, as {x, y, z} offsets from the block it was placed on, that block first.
     *
     * @param yaw the piece's yaw: south 0, west 90, north 180, east 270; snapped to the nearest
     */
    public List<int[]> offsets(float yaw) {
        int[] right = right(yaw);
        int[] back = back(yaw);
        int first = -(width - 1) / 2;
        List<int[]> offsets = new ArrayList<>();
        offsets.add(new int[] {0, 0, 0});
        for (int behind = 0; behind < depth; behind++) {
            for (int up = 0; up < height; up++) {
                for (int across = first; across < first + width; across++) {
                    if (behind == 0 && up == 0 && across == 0) {
                        continue;
                    }
                    offsets.add(new int[] {right[0] * across + back[0] * behind, up, right[1] * across + back[1] * behind});
                }
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
        int[] back = back(yaw);
        double across = -(width - 1) / 2 + (width - 1) / 2.0;
        double behind = (depth - 1) / 2.0;
        return new double[] {right[0] * across + back[0] * behind, right[1] * across + back[1] * behind};
    }

    /** 3.1: away from someone looking at the front of a piece turned to {@code yaw}, {dx, dz}. */
    public static int[] back(float yaw) {
        int[] right = right(yaw);
        return new int[] {right[1], -right[0]};
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
