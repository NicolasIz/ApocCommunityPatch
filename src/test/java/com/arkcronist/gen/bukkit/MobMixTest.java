package com.arkcronist.gen.bukkit;

import com.arkcronist.gen.bukkit.mobs.MobMix;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How much of the world's hostile mobs are custom and how much stays vanilla.
 *
 * <p>The edges matter more than the middle here. At 1.0 the behaviour has to be exactly what it was
 * before this dial existed, and at 0.0 the table has to do nothing at all - anything approximate at
 * either end is a silent change to every server that never touched the setting.</p>
 */
class MobMixTest {

    @Test
    @DisplayName("1.0 replaces everything and 0.0 replaces nothing, exactly")
    void theEdgesAreExact() {
        // Asked with the rolls that a > or >= slip would get wrong.
        for (double roll : new double[]{0.0, 0.5, 0.999999, 0.9999999999}) {
            assertTrue(MobMix.replaces(1.0, roll), "1.0 left a spawn vanilla at roll " + roll);
            assertFalse(MobMix.replaces(0.0, roll), "0.0 replaced a spawn at roll " + roll);
        }
        // And out-of-range values behave as the nearest edge rather than as something undefined.
        assertTrue(MobMix.replaces(1.5, 0.99));
        assertFalse(MobMix.replaces(-0.5, 0.0));
    }

    @Test
    @DisplayName("a roll below the chance replaces and one at or above it does not")
    void theMiddleIsTheChance() {
        assertTrue(MobMix.replaces(0.6, 0.0));
        assertTrue(MobMix.replaces(0.6, 0.59));
        assertFalse(MobMix.replaces(0.6, 0.6));
        assertFalse(MobMix.replaces(0.6, 0.99));
    }

    @Test
    @DisplayName("a structure's garrison is decided by where it is, so it never changes")
    void garrisonsAreFixedPerPlace() {
        // The castle a player walks into has to hold the same guards every time its chunk is
        // generated. A coin flip here would mean goblins or zombies depending on when it loaded.
        for (int i = 0; i < 50; i++) {
            assertEquals(MobMix.replacesAt(0.5, 120, 70, -340),
                    MobMix.replacesAt(0.5, 120, 70, -340),
                    "the same site gave two different answers");
        }
        assertTrue(MobMix.replacesAt(1.0, 120, 70, -340));
        assertFalse(MobMix.replacesAt(0.0, 120, 70, -340));
    }

    @Test
    @DisplayName("over a world's worth of places the positional mix lands near the figure asked for")
    void garrisonsMixInTheRightProportion() {
        // Fixed per place is only half of it: if the hash favoured one answer the whole world would
        // come out vanilla while every individual structure looked correctly stable.
        int replaced = 0;
        int total = 0;
        for (int x = -400; x < 400; x += 17) {
            for (int z = -400; z < 400; z += 13) {
                total++;
                if (MobMix.replacesAt(0.5, x, 64, z)) {
                    replaced++;
                }
            }
        }
        double share = (double) replaced / total;
        assertTrue(share > 0.44 && share < 0.56,
                "asked for half and got " + Math.round(share * 100) + "% over " + total + " places");
    }

    @Test
    @DisplayName("different positions do not all answer the same way")
    void positionsDiffer() {
        boolean first = MobMix.replacesAt(0.5, 0, 64, 0);
        boolean anyDifferent = false;
        for (int x = 1; x < 200 && !anyDifferent; x++) {
            anyDifferent = MobMix.replacesAt(0.5, x, 64, 0) != first;
        }
        assertTrue(anyDifferent, "every position within 200 blocks gave the same answer");
    }
}
