package com.arkcronist.content.core.ballistics;

/**
 * A half line: where a shot starts and which way it goes.
 *
 * @param origin    where it starts - a shooter's eyes
 * @param direction which way, always one block long, so a distance along the ray is in blocks
 */
public record Ray(Vec3 origin, Vec3 direction) {

    public Ray {
        direction = direction.normalize();
    }

    /** The point {@code distance} blocks along the ray. */
    public Vec3 at(double distance) {
        return origin.add(direction.multiply(distance));
    }
}
