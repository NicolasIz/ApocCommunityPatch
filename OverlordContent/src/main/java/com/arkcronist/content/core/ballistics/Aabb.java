package com.arkcronist.content.core.ballistics;

import java.util.OptionalDouble;

/**
 * An axis-aligned box - an entity's hitbox, a head, one box of a block's collision shape. The
 * corners are put in order on construction, so either pair can be passed first.
 */
public record Aabb(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

    public Aabb {
        if (minX > maxX) {
            double swap = minX;
            minX = maxX;
            maxX = swap;
        }
        if (minY > maxY) {
            double swap = minY;
            minY = maxY;
            maxY = swap;
        }
        if (minZ > maxZ) {
            double swap = minZ;
            minZ = maxZ;
            maxZ = swap;
        }
    }

    /** The full cube of the block at {@code x, y, z}. */
    public static Aabb block(int x, int y, int z) {
        return new Aabb(x, y, z, x + 1, y + 1, z + 1);
    }

    /** The box between two points. */
    public static Aabb between(Vec3 a, Vec3 b) {
        return new Aabb(a.x(), a.y(), a.z(), b.x(), b.y(), b.z());
    }

    public Aabb offset(double dx, double dy, double dz) {
        return new Aabb(minX + dx, minY + dy, minZ + dz, maxX + dx, maxY + dy, maxZ + dz);
    }

    public Aabb expand(double amount) {
        return new Aabb(minX - amount, minY - amount, minZ - amount, maxX + amount, maxY + amount, maxZ + amount);
    }

    /** The smallest box holding both. */
    public Aabb union(Aabb other) {
        return new Aabb(Math.min(minX, other.minX), Math.min(minY, other.minY), Math.min(minZ, other.minZ),
                Math.max(maxX, other.maxX), Math.max(maxY, other.maxY), Math.max(maxZ, other.maxZ));
    }

    /** The upper part, from {@code fromY} to the top - a head over a body. */
    public Aabb above(double fromY) {
        return new Aabb(minX, Math.max(minY, Math.min(maxY, fromY)), minZ, maxX, maxY, maxZ);
    }

    public boolean contains(Vec3 point) {
        return point.x() >= minX && point.x() <= maxX && point.y() >= minY && point.y() <= maxY
                && point.z() >= minZ && point.z() <= maxZ;
    }

    public boolean intersects(Aabb other) {
        return minX <= other.maxX && maxX >= other.minX && minY <= other.maxY && maxY >= other.minY
                && minZ <= other.maxZ && maxZ >= other.minZ;
    }

    /** Whether it is the whole of the block cube it sits in: nothing gets past it. */
    public boolean isFullBlock() {
        double x = Math.floor(minX);
        double y = Math.floor(minY);
        double z = Math.floor(minZ);
        return minX == x && minY == y && minZ == z && maxX == x + 1 && maxY == y + 1 && maxZ == z + 1;
    }

    /**
     * How far along {@code ray} it is first touched, within {@code maxDistance} - the slab method:
     * the ray is inside the box exactly while it is between both planes of every axis, so it enters
     * at the latest of the three entries and leaves at the earliest of the three exits.
     *
     * @return the distance at which the ray enters, 0 when it starts inside; empty when it misses,
     *         or only reaches the box beyond {@code maxDistance}
     */
    public OptionalDouble intersect(Ray ray, double maxDistance) {
        double enter = 0;
        double exit = maxDistance;
        double[] origin = {ray.origin().x(), ray.origin().y(), ray.origin().z()};
        double[] direction = {ray.direction().x(), ray.direction().y(), ray.direction().z()};
        double[] min = {minX, minY, minZ};
        double[] max = {maxX, maxY, maxZ};
        for (int axis = 0; axis < 3; axis++) {
            if (direction[axis] == 0) {
                // Parallel to these planes: between them all the way, or never.
                if (origin[axis] < min[axis] || origin[axis] > max[axis]) {
                    return OptionalDouble.empty();
                }
                continue;
            }
            double inverse = 1 / direction[axis];
            double near = (min[axis] - origin[axis]) * inverse;
            double far = (max[axis] - origin[axis]) * inverse;
            if (near > far) {
                double swap = near;
                near = far;
                far = swap;
            }
            enter = Math.max(enter, near);
            exit = Math.min(exit, far);
            if (enter > exit) {
                return OptionalDouble.empty();
            }
        }
        return OptionalDouble.of(enter);
    }
}
