package com.arkcronist.content.core.storage;

import java.util.UUID;

/**
 * 5.0: a custom block or a liquid source the integrity check put back after something else took it
 * away - how often, in the current window.
 *
 * @param kind      {@code block} or {@code liquid}
 * @param contentId the block's or liquid's id, as it was put back
 * @param repairs   how many times in the window that began at {@code firstAt}
 * @param firstAt   when the window began, epoch millis
 * @param lastAt    the latest time, epoch millis
 */
public record BlockRepair(UUID world, int x, int y, int z, String kind, String contentId, int repairs, long firstAt,
                          long lastAt) {

    public static final String BLOCK = "block";
    public static final String LIQUID = "liquid";

    /** The key it is kept under in memory: world, position and kind. */
    public static String key(UUID world, int x, int y, int z, String kind) {
        return world + "/" + x + "/" + y + "/" + z + "/" + kind;
    }

    public String key() {
        return key(world, x, y, z, kind);
    }
}
