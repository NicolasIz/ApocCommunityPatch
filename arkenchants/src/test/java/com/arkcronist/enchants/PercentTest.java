package com.arkcronist.enchants;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.arkcronist.enchants.load.EnchantLoader;
import com.arkcronist.enchants.text.Percent;
import java.util.Random;
import org.junit.jupiter.api.Test;

class PercentTest {

    @Test
    void formatsWithoutUselessZeros() {
        assertEquals("45", Percent.fmt(45));
        assertEquals("0.1", Percent.fmt(0.1));
        assertEquals("12.35", Percent.fmt(12.35));
        assertEquals("99.5", Percent.fmt(99.5));
    }

    @Test
    void rangesAcceptDecimals() {
        assertArrayEquals(new double[]{0.1, 100}, EnchantLoader.range("0.1-100", 0, 0));
        assertArrayEquals(new double[]{0.5, 2}, EnchantLoader.range("0,5-2", 0, 0));
        assertArrayEquals(new double[]{100, 100}, EnchantLoader.range("100", 0, 0));
    }

    @Test
    void rollsStayInRangeAndNeverHitZero() {
        Random r = new Random(3);
        for (int i = 0; i < 5000; i++) {
            double v = Percent.roll(0.1, 5, r);
            assertTrue(v >= 0.1 && v <= 5, String.valueOf(v));
        }
    }

    @Test
    void tinyChanceIsRare() {
        Random r = new Random(5);
        int hits = 0;
        for (int i = 0; i < 100_000; i++) {
            if (Percent.chance(0.1, r)) {
                hits++;
            }
        }
        // about 100 in 100k
        assertTrue(hits > 50 && hits < 160, String.valueOf(hits));
    }
}
