package com.arkcronist.content.core.furniture;

/**
 * Where the two halves of a bed are, and where the model drawing it goes.
 *
 * <p>A custom bed is a real vanilla bed - two blocks, foot and head - so sleeping, setting the
 * spawn point, skipping the night and refusing to sleep near monsters are all vanilla's. Over it
 * stands one display, centred between the two halves, turned so that the model's front points the
 * way the bed does: from the foot to the head. The client lays a sleeping player with their head on
 * the head block, pointing the same way, so the body lines up with the model along both X and Z.</p>
 *
 * <p>Models are authored the same way as other furniture, front to the north: a bed is 16 pixels
 * wide and 32 long, from z -8 to 24, its pillow at the north end (z -8) and its mattress top at 9
 * pixels, the height vanilla lays the sleeper at.</p>
 *
 * @param footX the foot block
 * @param facing from the foot towards the head - vanilla's {@code facing} of both halves
 */
public record BedLayout(int footX, int footY, int footZ, Facing facing) {

    /** The four directions a bed can point. */
    public enum Facing {
        NORTH(0, -1, 180),
        SOUTH(0, 1, 0),
        WEST(-1, 0, 90),
        EAST(1, 0, 270);

        /** One step this way. */
        public final int dx;
        public final int dz;
        /** The yaw of an entity looking this way: south is 0, west 90, north 180, east 270. */
        public final float yaw;

        Facing(int dx, int dz, float yaw) {
            this.dx = dx;
            this.dz = dz;
            this.yaw = yaw;
        }

        public Facing opposite() {
            return switch (this) {
                case NORTH -> SOUTH;
                case SOUTH -> NORTH;
                case WEST -> EAST;
                case EAST -> WEST;
            };
        }
    }

    /** One half of a bed. */
    public enum Part {
        FOOT,
        HEAD
    }

    /** The layout of the bed whose {@code part} is at the given block, facing {@code facing}. */
    public static BedLayout from(int x, int y, int z, Part part, Facing facing) {
        return part == Part.FOOT
                ? new BedLayout(x, y, z, facing)
                : new BedLayout(x - facing.dx, y, z - facing.dz, facing);
    }

    public int headX() {
        return footX + facing.dx;
    }

    public int headY() {
        return footY;
    }

    public int headZ() {
        return footZ + facing.dz;
    }

    /** The other half from the one at {@code part}: {x, y, z}. */
    public int[] other(Part part) {
        return part == Part.FOOT ? new int[] {headX(), headY(), headZ()} : new int[] {footX, footY, footZ};
    }

    /** Where the display stands: between the two halves, on the floor. */
    public double displayX() {
        return footX + 0.5 + facing.dx * 0.5;
    }

    public double displayY() {
        return footY;
    }

    public double displayZ() {
        return footZ + 0.5 + facing.dz * 0.5;
    }

    /** The display's yaw: the model's front - its pillow - towards the head. */
    public float yaw() {
        return facing.yaw;
    }

    /**
     * Where the sleeper's head rests, as the client places it: the centre of the head block. Its
     * distance from the display, along the bed's length, is half a block.
     */
    public double[] pillow() {
        return new double[] {headX() + 0.5, footY + 9 / 16.0, headZ() + 0.5};
    }
}
