package com.arkcronist.content.core.sanity;

import com.arkcronist.content.core.storage.BlockRepair;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 5.0: a block is put back at most so many times in a window, then let go. */
class RepairBudgetTest {

    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final long HOUR = 60L * 60 * 1000;

    @Test
    void threeTimesADayThenLetGoUntilTheWindowIsOver() {
        RepairBudget budget = new RepairBudget(3, 24 * HOUR);
        BlockRepair past = null;
        long now = 1_000_000;
        for (int i = 1; i <= 3; i++) {
            assertTrue(budget.allows(past, now), "repair " + i);
            past = budget.after(past, WORLD, 1, 2, 3, BlockRepair.BLOCK, "demo:ruby_block", now);
            assertEquals(i, past.repairs());
            now += HOUR;
        }
        assertFalse(budget.allows(past, now), "a fourth within the day: something means it");
        assertEquals(1_000_000, past.firstAt(), "the window began with the first");
        long nextDay = 1_000_000 + 24 * HOUR + 1;
        assertTrue(budget.allows(past, nextDay));
        BlockRepair fresh = budget.after(past, WORLD, 1, 2, 3, BlockRepair.BLOCK, "demo:ruby_block", nextDay);
        assertEquals(1, fresh.repairs(), "a new window");
        assertEquals(nextDay, fresh.firstAt());
    }

    @Test
    void zeroMeansNeverAndTheKeyTellsKindsApart() {
        assertFalse(new RepairBudget(0, HOUR).allows(null, 0));
        assertTrue(RepairBudget.DEFAULT.allows(null, 0));
        assertFalse(BlockRepair.key(WORLD, 1, 2, 3, BlockRepair.BLOCK).equals(BlockRepair.key(WORLD, 1, 2, 3,
                BlockRepair.LIQUID)));
    }
}
