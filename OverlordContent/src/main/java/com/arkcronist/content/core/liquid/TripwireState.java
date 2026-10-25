package com.arkcronist.content.core.liquid;

import java.util.ArrayList;
import java.util.List;

/**
 * One of the tripwire states liquids are drawn through.
 *
 * <p>Tripwire has seven properties - attached, disarmed, powered and its four connections - and
 * vanilla only ever holds a placed string disarmed for the instant shears cut it. So every state
 * that is {@code disarmed=true, powered=false} is free: the attached flag and the four connections
 * make {@value #CAPACITY} of them. Each liquid takes two - its source and its flowing liquid - so
 * there is room for {@value #LIQUIDS} liquids:</p>
 *
 * <pre>
 *   index  = attached * 16 + north * 8 + east * 4 + south * 2 + west
 *   liquid k: source = index (16 + 2k) mod 32, flowing = the index after it
 * </pre>
 *
 * <p>The attached states are handed out first. Walking into an attached tripwire is announced
 * before it is pressed, as a physical interaction the plugin cancels, so those states never change
 * at all. Walking into an unattached one presses it with no event first, and the plugin puts it
 * back within the same tick - so liquids 9 to 16 work too, at the cost of a block update each tick
 * someone stands in them.</p>
 *
 * <p>Vanilla also reconnects string to its neighbours whenever one of them changes. The plugin stops
 * that spreading from one liquid block to the next, and puts any block it reached back as it was.</p>
 */
public record TripwireState(boolean attached, boolean north, boolean east, boolean south, boolean west) {

    public static final int CAPACITY = 32;
    public static final int LIQUIDS = CAPACITY / 2;

    public static TripwireState fromIndex(int index) {
        if (index < 0 || index >= CAPACITY) {
            throw new IllegalArgumentException("tripwire state index " + index + " is outside 0-" + (CAPACITY - 1));
        }
        return new TripwireState((index & 16) != 0, (index & 8) != 0, (index & 4) != 0, (index & 2) != 0,
                (index & 1) != 0);
    }

    public int index() {
        return (attached ? 16 : 0) | (north ? 8 : 0) | (east ? 4 : 0) | (south ? 2 : 0) | (west ? 1 : 0);
    }

    /** The source state of the liquid in slot {@code slot}, 0 to {@value #LIQUIDS} - 1. */
    public static TripwireState source(int slot) {
        return fromIndex((16 + slot * 2) % CAPACITY);
    }

    /** The flowing state of the liquid in slot {@code slot}. */
    public static TripwireState flowing(int slot) {
        return fromIndex((16 + slot * 2) % CAPACITY + 1);
    }

    /**
     * The state as the server writes it, for {@code Bukkit.createBlockData}:
     * {@code minecraft:tripwire[attached=false,disarmed=true,...]}.
     */
    public String blockData() {
        return "minecraft:tripwire[" + variantKey(true, false) + "]";
    }

    /** The key of this state in the blockstate file, every property named, in the client's order. */
    public String variantKey(boolean disarmed, boolean powered) {
        return "attached=" + attached + ",disarmed=" + disarmed + ",east=" + east + ",north=" + north
                + ",powered=" + powered + ",south=" + south + ",west=" + west;
    }

    /** The key vanilla's own blockstate file uses: everything but disarmed and powered. */
    public String vanillaKey() {
        return "attached=" + attached + ",east=" + east + ",north=" + north + ",south=" + south + ",west=" + west;
    }

    /** All {@value #CAPACITY} states, in index order. */
    public static List<TripwireState> all() {
        List<TripwireState> states = new ArrayList<>(CAPACITY);
        for (int index = 0; index < CAPACITY; index++) {
            states.add(fromIndex(index));
        }
        return states;
    }
}
