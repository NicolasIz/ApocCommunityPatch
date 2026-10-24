package com.arkcronist.content.core.vehicle;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 4.0: an item that, placed on the ground, is a vehicle - how it drives, where it is sat in and what
 * it is drawn with. Distances in blocks, speeds in blocks a second, angles in degrees.
 *
 * <p>Offsets are in the vehicle's own frame, as it faces forward: {@code x} to the driver's right,
 * {@code y} up, {@code z} forward, from the middle of its footprint on the ground.</p>
 *
 * @param maxSpeed     top speed forward
 * @param reverseSpeed top speed backward
 * @param acceleration how fast it gains speed under throttle
 * @param braking      how fast it loses speed braking - backward while moving forward, or jump
 * @param coasting     how fast it loses speed with nothing pressed
 * @param turnRate     degrees a second it turns at full lock, from walking pace up
 * @param stepHeight   the highest step it drives up without stopping: 0.5 a slab, 1 a full block
 * @param width        its footprint across, for collisions
 * @param length       its footprint along
 * @param height       how tall it is, for collisions
 * @param waterSpeed   the share of its top speed it keeps in water, 0 to 1
 * @param seats        where riders sit; the first is the driver's
 * @param parts        what it is drawn with: the body and wheels
 */
public record VehicleDefinition(double maxSpeed, double reverseSpeed, double acceleration, double braking,
                                double coasting, double turnRate, double stepHeight, double width, double length,
                                double height, double waterSpeed, List<Offset> seats, List<Part> parts) {

    public VehicleDefinition {
        if (seats.isEmpty()) {
            throw new IllegalArgumentException("a vehicle needs a seat for its driver");
        }
        seats = List.copyOf(seats);
        parts = List.copyOf(parts);
    }

    /** A point in the vehicle's frame: right, up, forward. */
    public record Offset(double x, double y, double z) {

        public static final Offset ZERO = new Offset(0, 0, 0);
    }

    /**
     * One display the vehicle is drawn with: another item's model, where it sits on the vehicle and,
     * for a wheel, how it turns as the vehicle rolls.
     *
     * @param item   the item whose model is drawn, {@code namespace:id}; null for the vehicle's own
     * @param offset where its model's centre is
     * @param scale  how much bigger it is drawn than its model, the same on every axis
     * @param wheel  whether it rolls with the vehicle, about its own left-right axis
     * @param radius for a wheel, its radius: how far one turn takes the vehicle
     */
    public record Part(@Nullable String item, Offset offset, double scale, boolean wheel, double radius) {
    }
}
