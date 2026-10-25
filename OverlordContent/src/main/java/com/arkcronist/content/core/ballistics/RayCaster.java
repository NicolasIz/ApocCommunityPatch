package com.arkcronist.content.core.ballistics;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;

/**
 * Traces a shot through a copy of the world: the boxes of the blocks along it and the hitboxes of
 * the entities near it. Pure arithmetic over values, so it runs on any thread - the world was read
 * before, on the server's own.
 */
public final class RayCaster {

    /** Two distances this close are the same point: a head entered where the body is. */
    private static final double EPSILON = 1e-7;

    /**
     * Something a shot can hit.
     *
     * @param handle what it stands for - an entity's id; never looked at here
     * @param body   the whole hitbox
     * @param head   the part that counts as the head, or null for something without one
     */
    public record Target<T>(T handle, Aabb body, @Nullable Aabb head) {
    }

    /**
     * Where a shot met a target.
     *
     * @param distance blocks from the ray's origin to where it entered
     * @param headshot it entered through the head
     */
    public record Hit<T>(T handle, double distance, Vec3 point, boolean headshot) {
    }

    /**
     * What one ray did.
     *
     * @param hits     the targets it went through, nearest first - at most {@code 1 + pierce}
     * @param distance how far it went: to the block that stopped it, the last target it could pierce,
     *                 or its full range
     * @param blocked  a block stopped it
     */
    public record Trace<T>(List<Hit<T>> hits, double distance, boolean blocked) {
    }

    private RayCaster() {
    }

    /**
     * Traces {@code ray} for {@code range} blocks.
     *
     * @param obstacles boxes nothing passes through - the collision shapes of the blocks on the way
     * @param targets   what can be hit; a target behind an obstacle is not
     * @param pierce    how many targets it goes on through after the first
     */
    public static <T> Trace<T> trace(Ray ray, double range, Collection<Aabb> obstacles,
                                     Collection<Target<T>> targets, int pierce) {
        double blockedAt = range;
        boolean blocked = false;
        for (Aabb obstacle : obstacles) {
            OptionalDouble distance = obstacle.intersect(ray, blockedAt);
            if (distance.isPresent() && distance.getAsDouble() < blockedAt) {
                blockedAt = distance.getAsDouble();
                blocked = true;
            }
        }

        List<Hit<T>> hits = new ArrayList<>();
        for (Target<T> target : targets) {
            Hit<T> hit = hit(ray, blockedAt, target);
            if (hit != null) {
                hits.add(hit);
            }
        }
        hits.sort(Comparator.comparingDouble(Hit::distance));
        int limit = 1 + Math.max(0, pierce);
        int kept = Math.min(hits.size(), limit);
        List<Hit<T>> result = List.copyOf(hits.subList(0, kept));
        // A ray that used up its piercing ends in the last thing it hit.
        boolean spent = kept == limit;
        double distance = spent ? result.get(kept - 1).distance() : blockedAt;
        return new Trace<>(result, distance, blocked && !spent);
    }

    /** The first target {@code ray} meets within {@code range}, ignoring obstacles; null when none. */
    public static <T> @Nullable Hit<T> first(Ray ray, double range, Collection<Target<T>> targets) {
        List<Hit<T>> hits = trace(ray, range, List.of(), targets, 0).hits();
        return hits.isEmpty() ? null : hits.get(0);
    }

    /** Where the ray meets this one target before {@code range}, or null. */
    static <T> @Nullable Hit<T> hit(Ray ray, double range, Target<T> target) {
        OptionalDouble body = target.body().intersect(ray, range);
        OptionalDouble head = target.head() == null ? OptionalDouble.empty() : target.head().intersect(ray, range);
        if (body.isEmpty() && head.isEmpty()) {
            return null;
        }
        double bodyDistance = body.orElse(Double.POSITIVE_INFINITY);
        double headDistance = head.orElse(Double.POSITIVE_INFINITY);
        // Through the head only when that is where it went in: a shot rising through the chest
        // and out of the top of the head hit the chest.
        boolean headshot = head.isPresent() && headDistance <= bodyDistance + EPSILON;
        double distance = Math.min(bodyDistance, headDistance);
        return new Hit<>(target.handle(), distance, ray.at(distance), headshot);
    }
}
