package com.arkcronist.gen.bukkit;

import com.arkcronist.gen.bukkit.mobs.SpawnLimit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** When the plugin stops adding custom mobs. */
class SpawnLimitTest {

    @Test
    @DisplayName("a spot is full at the near limit, not before")
    void nearLimit() {
        assertFalse(SpawnLimit.full(15, 0, 16, 150));
        assertTrue(SpawnLimit.full(16, 0, 16, 150));
    }

    @Test
    @DisplayName("a world is full at its limit wherever the spot is")
    void worldLimit() {
        assertFalse(SpawnLimit.full(0, 149, 16, 150));
        assertTrue(SpawnLimit.full(0, 150, 16, 150));
    }

    @Test
    @DisplayName("a limit of 0 is no limit")
    void zeroIsOff() {
        assertFalse(SpawnLimit.full(10_000, 10_000, 0, 0));
    }
}
