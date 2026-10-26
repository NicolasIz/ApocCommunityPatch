package com.arkcronist.content.core.furniture;

import com.arkcronist.content.core.definition.Placement;

import java.util.ArrayList;
import java.util.List;

/**
 * 5.3: the boxes a piece is clicked and punched by - interaction entities, each square across - and
 * where they stand however the piece is turned.
 *
 * <p>All in blocks, from the middle of the floor of the block the piece was placed on, x east, y
 * up, z south, for the piece facing south, as {@link Placement.Hitbox.Box} has them.</p>
 */
public final class Hitboxes {

    /** The smallest a box is made across or up: a thinner one is hard to hit. */
    static final double THINNEST = 0.2;
    /** The most boxes one stretch of a model is cut into. */
    static final int MOST = 8;

    private Hitboxes() {
    }

    /**
     * Square boxes covering the box from {@code min} to {@code max}: one when it is about as wide as
     * it is deep, else a row along its longer side - each as wide as the shorter side, or half a
     * block when that is thinner, so a long thin piece is not cut into dozens.
     */
    public static List<Placement.Hitbox.Box> cover(double[] min, double[] max) {
        double[] low = min.clone();
        double[] high = max.clone();
        for (int axis = 0; axis < 3; axis++) {
            if (high[axis] - low[axis] < THINNEST) {
                double middle = (low[axis] + high[axis]) / 2;
                low[axis] = middle - THINNEST / 2;
                high[axis] = middle + THINNEST / 2;
            }
        }
        double dx = high[0] - low[0];
        double dz = high[2] - low[2];
        boolean alongX = dx >= dz;
        double longer = Math.max(dx, dz);
        double shorter = Math.min(dx, dz);
        int count = (int) Math.max(1, Math.min(MOST, Math.ceil(longer / Math.max(shorter, 0.5) - 1e-6)));
        double step = longer / count;
        double width = Math.max(shorter, step);
        List<Placement.Hitbox.Box> boxes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double along = (alongX ? low[0] : low[2]) + (i + 0.5) * step;
            double x = alongX ? along : (low[0] + high[0]) / 2;
            double z = alongX ? (low[2] + high[2]) / 2 : along;
            boxes.add(new Placement.Hitbox.Box(width, high[1] - low[1],
                    new Placement.Vec3((float) x, (float) low[1], (float) z)));
        }
        return boxes;
    }

    /**
     * Where the bottom middle of a box stands for a piece turned to {@code yaw}: turned with the
     * display about the piece's middle, which an even width or depth shifts off the block it was
     * placed on. {x, y, z} from the middle of that block's floor.
     */
    public static double[] turned(Placement.Vec3 offset, Footprint footprint, float yaw) {
        double[] from = footprint.centreShift(0);
        double[] to = footprint.centreShift(yaw);
        double x = offset.x() - from[0];
        double z = offset.z() - from[1];
        // The display turns by -yaw about y, right-handed, as its cells do on Bedrock.
        double angle = Math.toRadians(-yaw);
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new double[]{x * cos + z * sin + to[0], offset.y(), -x * sin + z * cos + to[1]};
    }
}
