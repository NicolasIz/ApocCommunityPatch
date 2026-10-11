package com.arkcronist.content.core.minigame;

import org.jetbrains.annotations.Nullable;

import java.util.random.RandomGenerator;

/**
 * One game being played: where the needle is at any moment, and the verdict on a press.
 *
 * <p>Everything is worked out on the server, from the server's clock: the needle's place is a
 * function of the time since the game started, the frames the player sees are drawn from it, and a
 * press is judged against it - nothing the client says decides the result. Pure: times are given,
 * not read, so the same game judges the same press the same way on any thread.</p>
 *
 * <p>A player presses on what they see, which reached them some time ago, and the press takes as
 * long to come back: a press is judged where the needle was {@code latency} before it arrived -
 * the player's round trip, as the server measures it - but never more than {@code maxLatency}
 * before, so a slow connection cannot be used to look ahead.</p>
 */
public final class PrecisionGame {

    public enum Outcome {
        CRITICAL, HIT, MISS, TIMEOUT;

        public boolean hit() {
            return this == CRITICAL || this == HIT;
        }
    }

    /**
     * @param error how far from the middle of the zone the needle stopped, in pixels; null when the
     *              game ran out without a press
     */
    public record Result(Outcome outcome, @Nullable Double error) {
    }

    private final MinigameDefinition definition;
    private final long startNanos;
    private final double zoneCentre;

    /** A game with its zone placed by {@code random} - in the middle if the definition keeps it there. */
    public PrecisionGame(MinigameDefinition definition, long startNanos, RandomGenerator random) {
        this(definition, startNanos, definition.randomZone()
                ? definition.zone() / 2.0 + random.nextDouble() * (MinigameDefinition.BAR - definition.zone())
                : MinigameDefinition.BAR / 2.0);
    }

    public PrecisionGame(MinigameDefinition definition, long startNanos, double zoneCentre) {
        this.definition = definition;
        this.startNanos = startNanos;
        this.zoneCentre = zoneCentre;
    }

    public MinigameDefinition definition() {
        return definition;
    }

    public double zoneCentre() {
        return zoneCentre;
    }

    /** The zone's first pixel and width, as drawn. */
    public int zoneLeft() {
        return (int) Math.round(zoneCentre - definition.zone() / 2.0);
    }

    public int criticalLeft() {
        return (int) Math.round(zoneCentre - definition.critical() / 2.0);
    }

    /**
     * Where the needle is at {@code nanos}, 0 to {@link MinigameDefinition#BAR}: there and back,
     * at an even speed, starting from the left.
     */
    public double needleAt(long nanos) {
        double sweeps = Math.max(0, nanos - startNanos) / 1e9 * definition.sweepsPerSecond();
        long whole = (long) Math.floor(sweeps);
        double along = sweeps - whole;
        return MinigameDefinition.BAR * (whole % 2 == 0 ? along : 1 - along);
    }

    public boolean over(long nanos) {
        return nanos - startNanos >= definition.lengthNanos();
    }

    /**
     * The verdict on a press that arrived at {@code pressNanos} from a player whose round trip is
     * {@code latencyNanos}.
     */
    public Result judge(long pressNanos, long latencyNanos, long maxLatencyNanos) {
        long seen = pressNanos - Math.max(0, Math.min(latencyNanos, maxLatencyNanos));
        seen = Math.max(seen, startNanos);
        if (over(seen)) {
            return new Result(Outcome.TIMEOUT, null);
        }
        double error = Math.abs(needleAt(seen) - zoneCentre);
        Outcome outcome = error <= definition.critical() / 2.0 ? Outcome.CRITICAL
                : error <= definition.zone() / 2.0 ? Outcome.HIT
                : Outcome.MISS;
        return new Result(outcome, error);
    }

    /** The verdict on a game that ran out. */
    public static Result timeout() {
        return new Result(Outcome.TIMEOUT, null);
    }
}
