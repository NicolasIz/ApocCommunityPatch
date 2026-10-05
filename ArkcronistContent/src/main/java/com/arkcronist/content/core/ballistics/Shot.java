package com.arkcronist.content.core.ballistics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One trigger pull, copied out of the world: where the shooter's eyes were, the pellets'
 * directions, the block boxes and hitboxes near them. {@link #resolve()} is the whole of the
 * arithmetic, and touches nothing but these values - so it is what runs on the guns' own thread.
 *
 * @param eye                where every pellet starts
 * @param directions         one per pellet
 * @param range              how far a pellet goes
 * @param obstacles          the collision boxes of the blocks along the pellets' paths
 * @param targets            what can be hit
 * @param pierce             how many targets a pellet goes on through after the first
 * @param damage             one pellet's damage at close range
 * @param headshotMultiplier what a headshot multiplies a pellet's damage by
 * @param falloff            how damage fades with distance
 */
public record Shot<T>(Vec3 eye, List<Vec3> directions, double range, Collection<Aabb> obstacles,
                      Collection<RayCaster.Target<T>> targets, int pierce, double damage, double headshotMultiplier,
                      DamageFalloff falloff) {

    public Shot {
        directions = List.copyOf(directions);
        obstacles = List.copyOf(obstacles);
        targets = List.copyOf(targets);
    }

    /**
     * What every pellet did to one target, added up: a target is damaged once per shot, by the
     * total, as a shotgun blast is one hit and not eight.
     *
     * @param pellets   how many pellets hit it
     * @param headshot  at least one went in through the head
     * @param nearest   the nearest point any pellet hit it at
     */
    public record Damage(double amount, int pellets, boolean headshot, Vec3 nearest, double distance) {
    }

    /**
     * @param damage per target, in the order they were first hit
     * @param paths  each pellet's path: from the eye to where it stopped
     */
    public record Outcome<T>(Map<T, Damage> damage, List<Path> paths) {
    }

    /** Where one pellet went, for drawing it; {@code blocked} when a block stopped it there. */
    public record Path(Vec3 from, Vec3 to, boolean blocked) {
    }

    public Outcome<T> resolve() {
        Map<T, Damage> damage = new LinkedHashMap<>();
        List<Path> paths = new ArrayList<>(directions.size());
        for (Vec3 direction : directions) {
            Ray ray = new Ray(eye, direction);
            RayCaster.Trace<T> trace = RayCaster.trace(ray, range, obstacles, targets, pierce);
            paths.add(new Path(eye, ray.at(trace.distance()), trace.blocked()));
            for (RayCaster.Hit<T> hit : trace.hits()) {
                double amount = falloff.damage(this.damage, hit.distance(), hit.headshot(), headshotMultiplier);
                damage.merge(hit.handle(), new Damage(amount, 1, hit.headshot(), hit.point(), hit.distance()),
                        (before, now) -> new Damage(before.amount() + now.amount(), before.pellets() + 1,
                                before.headshot() || now.headshot(),
                                before.distance() <= now.distance() ? before.nearest() : now.nearest(),
                                Math.min(before.distance(), now.distance())));
            }
        }
        return new Outcome<>(damage, paths);
    }
}
