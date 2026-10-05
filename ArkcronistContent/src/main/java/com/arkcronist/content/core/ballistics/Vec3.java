package com.arkcronist.content.core.ballistics;

/**
 * A point or a direction in world space, in blocks. Immutable, so a shot's snapshot can be handed
 * to another thread as it is.
 */
public record Vec3(double x, double y, double z) {

    public static final Vec3 ZERO = new Vec3(0, 0, 0);

    public Vec3 add(Vec3 other) {
        return new Vec3(x + other.x, y + other.y, z + other.z);
    }

    public Vec3 subtract(Vec3 other) {
        return new Vec3(x - other.x, y - other.y, z - other.z);
    }

    public Vec3 multiply(double factor) {
        return new Vec3(x * factor, y * factor, z * factor);
    }

    public double dot(Vec3 other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public Vec3 cross(Vec3 other) {
        return new Vec3(y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x);
    }

    public double length() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    public double distance(Vec3 other) {
        return subtract(other).length();
    }

    /** The same direction, one block long. */
    public Vec3 normalize() {
        double length = length();
        if (length == 0 || !Double.isFinite(length)) {
            throw new IllegalArgumentException("a zero or infinite vector has no direction: " + this);
        }
        return new Vec3(x / length, y / length, z / length);
    }

    /** The angle between two directions, in degrees. */
    public double angleTo(Vec3 other) {
        double cos = dot(other) / (length() * other.length());
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cos))));
    }

    /**
     * Where a player looks, the way Minecraft turns yaw and pitch into a direction: yaw 0 faces
     * south (+z) and grows clockwise seen from above, pitch -90 looks straight up.
     */
    public static Vec3 fromRotation(double yawDegrees, double pitchDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        double horizontal = Math.cos(pitch);
        return new Vec3(-Math.sin(yaw) * horizontal, -Math.sin(pitch), Math.cos(yaw) * horizontal);
    }
}
