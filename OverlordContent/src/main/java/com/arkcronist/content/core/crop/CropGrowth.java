package com.arkcronist.content.core.crop;

/**
 * How crops grow: the arithmetic, with no world in it.
 *
 * <p>A stage takes the crop's {@code stage-seconds}, give or take up to a fifth. The give-or-take is
 * worked out from the crop's position and stage, not drawn at random, so a field planted in one
 * go does not ripen in one tick, and the same crop always takes the same time - which is what lets
 * a test, or an admin, predict it.</p>
 *
 * <p>Growing is split in two, because deciding and showing happen on different threads. The
 * scheduler thread adds time to every crop in a loaded chunk ({@link #grow}), capped at the end of
 * the stage; the server thread then moves the crops that are {@link #due} on to their next stage
 * ({@link #advance}), once it has checked what only the world can tell - the light, the soil.</p>
 */
public final class CropGrowth {

    /** How far a stage's time may stray from the configured one, either way. */
    static final double SPREAD = 0.2;

    private CropGrowth() {
    }

    /** Seconds the given stage of a crop at {@code positionKey} takes. At least one. */
    public static long stageDuration(int stageSeconds, long positionKey, int stage) {
        long hash = mix(positionKey * 31 + stage);
        // The top 53 bits as a fraction in [0, 1), then into [-SPREAD, SPREAD).
        double unit = (hash >>> 11) * 0x1.0p-53;
        double factor = 1 + (unit * 2 - 1) * SPREAD;
        return Math.max(1, Math.round(stageSeconds * factor));
    }

    /**
     * {@code crop} after {@code elapsed} more seconds, stopping at the end of its current stage: the
     * move to the next is the server thread's to make. A crop already fully grown is returned as
     * it is.
     */
    public static PlantedCrop grow(PlantedCrop crop, long elapsed, int stageSeconds, int lastStage) {
        if (crop.stage() >= lastStage || elapsed <= 0) {
            return crop;
        }
        long duration = stageDuration(stageSeconds, crop.key(), crop.stage());
        long progress = Math.min(duration, crop.progress() + elapsed);
        return progress == crop.progress() ? crop : crop.withGrowth(crop.stage(), progress);
    }

    /** Whether {@code crop} has grown through its stage and is waiting to move to the next. */
    public static boolean due(PlantedCrop crop, int stageSeconds, int lastStage) {
        return crop.stage() < lastStage && crop.progress() >= stageDuration(stageSeconds, crop.key(), crop.stage());
    }

    /** The next stage, from its start. Stops at {@code lastStage}. */
    public static PlantedCrop advance(PlantedCrop crop, int lastStage) {
        return crop.withGrowth(Math.min(lastStage, crop.stage() + 1), 0);
    }

    /** SplitMix64's finaliser: every input bit reaches every output bit. */
    private static long mix(long value) {
        long z = value + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
