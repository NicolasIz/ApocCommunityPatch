package com.arkcronist.enchants;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.arkcronist.enchants.engine.DamageMods;
import org.junit.jupiter.api.Test;

class DamageModsTest {

    @Test
    void enchantsTogetherStayWithinTheCaps() {
        DamageMods.maxBonus = 200;
        DamageMods.maxReduction = 80;
        DamageMods up = new DamageMods();
        up.percent = 500;
        up.multiplier = 2;
        assertEquals(30, up.apply(10), 1e-9);
        DamageMods down = new DamageMods();
        down.percent = -95;
        assertEquals(2, down.apply(10), 1e-9);
        DamageMods some = new DamageMods();
        some.percent = 50;
        assertEquals(15, some.apply(10), 1e-9);
    }

    @Test
    void negateStillCancelsTheWholeHit() {
        DamageMods.maxReduction = 80;
        DamageMods none = new DamageMods();
        none.multiplier = 0;
        assertEquals(0, none.apply(10), 1e-9);
    }
}
