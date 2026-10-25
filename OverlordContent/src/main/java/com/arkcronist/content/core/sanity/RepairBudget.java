package com.arkcronist.content.core.sanity;

import com.arkcronist.content.core.storage.BlockRepair;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 5.0: how often the integrity check may put the same block back. Something that takes a block away
 * again and again - a plugin regenerating an area, a player with a tool the plugin does not see -
 * means it, and the check would only fight it: past {@code max} times in a window, the block is let
 * go and its row purged, as before 5.0. A window starts with the first repair and lasts
 * {@code windowMillis}; the next repair after it starts a new one.
 */
public record RepairBudget(int max, long windowMillis) {

    public static final RepairBudget DEFAULT = new RepairBudget(3, 24L * 60 * 60 * 1000);

    public RepairBudget {
        if (max < 0 || windowMillis <= 0) {
            throw new IllegalArgumentException("max " + max + ", window " + windowMillis);
        }
    }

    /** Whether one more repair is allowed, after {@code past} (null: never repaired). */
    public boolean allows(@Nullable BlockRepair past, long now) {
        return max > 0 && (past == null || now - past.firstAt() > windowMillis || past.repairs() < max);
    }

    /** {@code past} with one more repair at {@code now}: a new window when the last one is over. */
    public BlockRepair after(@Nullable BlockRepair past, UUID world, int x, int y, int z, String kind, String contentId,
                             long now) {
        if (past == null || now - past.firstAt() > windowMillis) {
            return new BlockRepair(world, x, y, z, kind, contentId, 1, now, now);
        }
        return new BlockRepair(world, x, y, z, kind, contentId, past.repairs() + 1, past.firstAt(), now);
    }
}
