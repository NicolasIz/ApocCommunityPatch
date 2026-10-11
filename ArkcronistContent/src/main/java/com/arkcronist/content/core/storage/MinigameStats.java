package com.arkcronist.content.core.storage;

import org.jetbrains.annotations.Nullable;

/**
 * A player's record at one HUD minigame.
 *
 * @param bestError the closest the needle was stopped to the middle of the bar, in pixels; null
 *                  until a game is played to a press
 */
public record MinigameStats(int plays, int hits, int criticals, @Nullable Double bestError, long lastPlayedMillis) {

    public static final MinigameStats NONE = new MinigameStats(0, 0, 0, null, 0);
}
