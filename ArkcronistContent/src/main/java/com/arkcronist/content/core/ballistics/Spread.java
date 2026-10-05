package com.arkcronist.content.core.ballistics;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Where each pellet of a shot goes: inside a cone around where the shooter looks, spread evenly
 * over the cone's opening rather than bunched at its centre.
 */
public final class Spread {

    private Spread() {
    }

    /**
     * @param forward  where the shooter looks
     * @param degrees  the cone's half-angle: no pellet strays further than this from {@code forward};
     *                 0 sends every pellet straight
     * @param pellets  how many directions, at least one
     * @param random   the source of chance - seeded in tests, so a shot can be replayed
     * @return one unit direction per pellet
     */
    public static List<Vec3> directions(Vec3 forward, double degrees, int pellets, RandomGenerator random) {
        Vec3 axis = forward.normalize();
        int count = Math.max(1, pellets);
        List<Vec3> directions = new ArrayList<>(count);
        if (degrees <= 0) {
            for (int i = 0; i < count; i++) {
                directions.add(axis);
            }
            return directions;
        }
        // Two directions square to the axis and to each other: the plane the cone opens across.
        Vec3 helper = Math.abs(axis.y()) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 side = axis.cross(helper).normalize();
        Vec3 up = side.cross(axis).normalize();
        double cosMax = Math.cos(Math.toRadians(Math.min(degrees, 90)));
        for (int i = 0; i < count; i++) {
            // Uniform over the cap of the sphere the cone cuts out: cos(theta) uniform, not theta.
            double cos = 1 - random.nextDouble() * (1 - cosMax);
            double sin = Math.sqrt(Math.max(0, 1 - cos * cos));
            double around = random.nextDouble() * Math.PI * 2;
            Vec3 offset = side.multiply(Math.cos(around) * sin).add(up.multiply(Math.sin(around) * sin));
            directions.add(axis.multiply(cos).add(offset).normalize());
        }
        return directions;
    }
}
