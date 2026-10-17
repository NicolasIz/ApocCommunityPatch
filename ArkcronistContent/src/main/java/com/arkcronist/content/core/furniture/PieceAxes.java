package com.arkcronist.content.core.furniture;

/**
 * A piece of furniture's own directions, in the world: <i>across</i> its front, from the left of
 * someone looking at it to their right; <i>up</i>; and <i>forward</i>, out of its front towards
 * that someone. Particles and the guillotine's stocks are placed in these, so they turn with the
 * piece.
 *
 * <p>A piece at yaw 0 faces south: forward is +z, and the right hand of someone looking at it - from
 * the south, looking north - is east, +x. The same right hand {@link Footprint#right(float)} gives
 * for its blocks.</p>
 */
public final class PieceAxes {

    private PieceAxes() {
    }

    /**
     * 3.2: the yaw a piece placed by someone standing {@code dx}, {@code dz} from its block's centre
     * takes to face them, to the nearest quarter turn: towards where they stand, not along where they
     * look - placed from one side while looking ahead, it still turns to them. Straight above it
     * (closer than a third of a block across), the way they look decides, as before 3.2.
     *
     * @param lookYaw the placer's own yaw, for when where they stand says nothing
     */
    public static float towards(double dx, double dz, float lookYaw) {
        double yaw = Math.abs(dx) < 0.34 && Math.abs(dz) < 0.34 ? lookYaw + 180.0 : Math.toDegrees(Math.atan2(-dx, dz));
        return (float) (((Math.round(yaw / 90.0) * 90.0) % 360.0 + 360.0) % 360.0);
    }

    /** {@code across, up, forward} for a piece turned to {@code yaw}, as {dx, dy, dz} in the world. */
    public static double[] offset(float yaw, double across, double up, double forward) {
        double radians = Math.toRadians(yaw);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        // right = (cos, sin), forward = (-sin, cos)
        return new double[] {across * cos - forward * sin, up, across * sin + forward * cos};
    }
}
