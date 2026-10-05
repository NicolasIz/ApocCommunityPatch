package com.arkcronist.content.core.ballistics;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Every block a ray passes through, in the order it passes through them - Amanatides and Woo's
 * walk: from the block the ray starts in, step into whichever neighbour's boundary the ray reaches
 * first, never skipping a block it grazes and never visiting one it misses.
 *
 * <p>It only names positions; what is at each is for the caller to say. A shot uses it to know
 * which blocks to copy before tracing; an empty bucket to find the liquid it is pointed at.</p>
 */
public final class VoxelTraversal {

    /** The side of a block a ray came in through. */
    public enum Face {
        WEST(-1, 0, 0), EAST(1, 0, 0), DOWN(0, -1, 0), UP(0, 1, 0), NORTH(0, 0, -1), SOUTH(0, 0, 1);

        public final int dx;
        public final int dy;
        public final int dz;

        Face(int dx, int dy, int dz) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
        }
    }

    /**
     * One block on the way.
     *
     * @param distance how far along the ray it is entered; 0 for the block the ray starts in
     * @param face     the side it is entered through, null for the block the ray starts in
     */
    public record Voxel(int x, int y, int z, double distance, Face face) {
    }

    @FunctionalInterface
    public interface Visitor {

        /** @return true to stop here */
        boolean visit(Voxel voxel);
    }

    private VoxelTraversal() {
    }

    /**
     * Walks the blocks the first {@code maxDistance} blocks of the ray pass through.
     *
     * @return the block the visitor stopped at, empty when it never did
     */
    public static Optional<Voxel> walk(Ray ray, double maxDistance, Visitor visitor) {
        Vec3 origin = ray.origin();
        Vec3 direction = ray.direction();
        int x = (int) Math.floor(origin.x());
        int y = (int) Math.floor(origin.y());
        int z = (int) Math.floor(origin.z());

        int stepX = (int) Math.signum(direction.x());
        int stepY = (int) Math.signum(direction.y());
        int stepZ = (int) Math.signum(direction.z());
        // How far along the ray one whole block is, on each axis; and the next boundary on each.
        double deltaX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / direction.x());
        double deltaY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / direction.y());
        double deltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / direction.z());
        double nextX = boundary(origin.x(), x, stepX, direction.x());
        double nextY = boundary(origin.y(), y, stepY, direction.y());
        double nextZ = boundary(origin.z(), z, stepZ, direction.z());

        Voxel voxel = new Voxel(x, y, z, 0, null);
        while (true) {
            if (visitor.visit(voxel)) {
                return Optional.of(voxel);
            }
            double distance;
            Face face;
            if (nextX <= nextY && nextX <= nextZ) {
                distance = nextX;
                x += stepX;
                nextX += deltaX;
                face = stepX > 0 ? Face.WEST : Face.EAST;
            } else if (nextY <= nextZ) {
                distance = nextY;
                y += stepY;
                nextY += deltaY;
                face = stepY > 0 ? Face.DOWN : Face.UP;
            } else {
                distance = nextZ;
                z += stepZ;
                nextZ += deltaZ;
                face = stepZ > 0 ? Face.NORTH : Face.SOUTH;
            }
            if (distance > maxDistance) {
                return Optional.empty();
            }
            voxel = new Voxel(x, y, z, distance, face);
        }
    }

    /** Every block the first {@code maxDistance} blocks of the ray pass through, in order. */
    public static List<Voxel> all(Ray ray, double maxDistance) {
        List<Voxel> voxels = new ArrayList<>();
        walk(ray, maxDistance, voxel -> {
            voxels.add(voxel);
            return false;
        });
        return voxels;
    }

    /** Distance along the ray to the first block boundary on one axis. */
    private static double boundary(double origin, int cell, int step, double direction) {
        if (step == 0) {
            return Double.POSITIVE_INFINITY;
        }
        double edge = step > 0 ? cell + 1 : cell;
        return (edge - origin) / direction;
    }
}
