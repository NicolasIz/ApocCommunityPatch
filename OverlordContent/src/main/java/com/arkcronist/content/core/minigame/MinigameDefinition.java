package com.arkcronist.content.core.minigame;

import java.util.List;
import java.util.Locale;

/**
 * A precision minigame on the HUD (3.0): a needle sweeps across a bar, and the player stops it with
 * a click. Inside the zone is a hit, inside the narrow middle of the zone a critical hit.
 *
 * <p>Attached to a custom block - a rare ore, played with a right click on it - or to a fishing
 * rod, played when something bites and is reeled in.</p>
 *
 * @param sweepsPerSecond how many times a second the needle crosses the bar
 * @param zone            pixels of the bar that count as a hit, out of {@link #BAR}
 * @param critical        pixels in the middle of the zone that count as a critical hit
 * @param sweeps          crossings before the game ends by itself, as a miss
 * @param randomZone      the zone is put somewhere else each game; false keeps it in the middle
 * @param drops           what a hit gives: for a block, instead of its own drop
 * @param criticalDrops   what a critical hit gives, instead of {@code drops}
 * @param onMiss          what a miss does
 * @param cooldownTicks   ticks before the same player can play again
 */
public record MinigameDefinition(double sweepsPerSecond, int zone, int critical, int sweeps, boolean randomZone,
                                 List<Drop> drops, List<Drop> criticalDrops, OnMiss onMiss, int cooldownTicks) {

    /** The inside of the bar, in GUI pixels. */
    public static final int BAR = 100;

    public enum OnMiss {
        /** A block stays where it is: try again. */
        KEEP,
        /** A block breaks, giving nothing. */
        BREAK,
        /** A catch slips away. */
        LOSE;

        public static OnMiss parse(String text) {
            return valueOf(text.trim().toUpperCase(Locale.ROOT));
        }
    }

    /**
     * @param item   a custom item's id ({@code demo:ruby}) or a vanilla material ({@code DIAMOND})
     * @param chance 0 to 1
     */
    public record Drop(String item, int amount, double chance) {
    }

    public MinigameDefinition {
        if (sweepsPerSecond <= 0 || zone < 1 || zone > BAR || critical < 0 || critical > zone || sweeps < 1) {
            throw new IllegalArgumentException("a minigame needs speed > 0, 1 <= zone <= " + BAR
                    + ", 0 <= critical <= zone and at least one sweep");
        }
        drops = List.copyOf(drops);
        criticalDrops = List.copyOf(criticalDrops);
    }

    /** How long a game lasts at most, in nanoseconds. */
    public long lengthNanos() {
        return (long) (sweeps / sweepsPerSecond * 1_000_000_000L);
    }
}
