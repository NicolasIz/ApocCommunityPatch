package com.arkcronist.content.core.ballistics;

/**
 * How a shot's damage fades with distance: whole up to {@code start}, then down in a straight line
 * to {@code minFactor} of it at {@code range}.
 *
 * @param start     blocks a shot keeps its full damage for
 * @param range     how far a shot reaches at all
 * @param minFactor the share of the damage left at {@code range}, 0 to 1
 */
public record DamageFalloff(double start, double range, double minFactor) {

    public DamageFalloff {
        range = Math.max(0.1, range);
        start = Math.max(0, Math.min(start, range));
        minFactor = Math.max(0, Math.min(1, minFactor));
    }

    /** The share of the damage a hit at {@code distance} keeps. */
    public double factor(double distance) {
        if (distance <= start) {
            return 1;
        }
        if (distance >= range) {
            return minFactor;
        }
        double along = (distance - start) / (range - start);
        return 1 - along * (1 - minFactor);
    }

    /**
     * What one pellet does.
     *
     * @param base               damage of one pellet at close range
     * @param headshotMultiplier what a headshot multiplies it by
     */
    public double damage(double base, double distance, boolean headshot, double headshotMultiplier) {
        return base * factor(distance) * (headshot ? headshotMultiplier : 1);
    }
}
