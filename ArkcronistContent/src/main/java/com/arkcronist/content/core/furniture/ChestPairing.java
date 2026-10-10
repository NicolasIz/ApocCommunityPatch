package com.arkcronist.content.core.furniture;

import com.arkcronist.content.core.furniture.BedLayout.Facing;

import java.util.List;

/**
 * Two chests side by side, facing the same way, as vanilla joins them into a double chest - the
 * geometry, kept apart from the server so it can be tested.
 *
 * <p>Vanilla names the halves from the chest's own point of view, looking out of its front: a
 * {@link Half#LEFT LEFT} half has its partner to its right. Its double inventory starts with the
 * {@link Half#RIGHT RIGHT} half - the one on the left of a player standing in front - and so does a
 * double chest of furniture: that half's slots are rows 1-3, its partner's rows 4-6.</p>
 */
public final class ChestPairing {

    /** Vanilla's {@code ChestType} of a joined half. */
    public enum Half {
        LEFT,
        RIGHT;

        public Half other() {
            return this == LEFT ? RIGHT : LEFT;
        }
    }

    private ChestPairing() {
    }

    /** Where a joined half's partner is: vanilla's {@code ChestBlock.getConnectedDirection}. */
    public static Facing partnerDirection(Facing facing, Half half) {
        boolean left = half == Half.LEFT;
        return switch (facing) {
            case NORTH -> left ? Facing.EAST : Facing.WEST;
            case EAST -> left ? Facing.SOUTH : Facing.NORTH;
            case SOUTH -> left ? Facing.WEST : Facing.EAST;
            case WEST -> left ? Facing.NORTH : Facing.SOUTH;
        };
    }

    /** Which half a chest facing {@code facing} is, with its partner {@code towards} it. */
    public static Half halfFor(Facing facing, Facing towards) {
        if (partnerDirection(facing, Half.LEFT) == towards) {
            return Half.LEFT;
        }
        if (partnerDirection(facing, Half.RIGHT) == towards) {
            return Half.RIGHT;
        }
        throw new IllegalArgumentException("a chest facing " + facing + " has no partner to the " + towards);
    }

    /** The two directions beside a chest where a partner can stand: across its front, never behind it. */
    public static List<Facing> sides(Facing facing) {
        return List.of(partnerDirection(facing, Half.LEFT), partnerDirection(facing, Half.RIGHT));
    }

    /**
     * Of two halves - {@code a}, and its partner {@code towards} it - whether {@code a} is the first:
     * vanilla's RIGHT half, rows 1-3 of the double inventory.
     */
    public static boolean firstIs(Facing facing, Facing towards) {
        return halfFor(facing, towards) == Half.RIGHT;
    }
}
