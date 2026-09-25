package com.arkcronist.enchants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.arkcronist.enchants.engine.Condition;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

class ConditionTest {

    private static UnaryOperator<String> vars(Map<String, String> m) {
        return s -> {
            String out = s;
            for (var e : m.entrySet()) {
                out = out.replace("%" + e.getKey() + "%", e.getValue());
            }
            return out;
        };
    }

    @Test
    void comparesNumbersAndText() {
        assertTrue(Condition.evaluate("5 > 3"));
        assertFalse(Condition.evaluate("5 < 3"));
        assertTrue(Condition.evaluate("-30 <= -30"));
        assertTrue(Condition.evaluate("DIAMOND_ORE contains ORE"));
        assertFalse(Condition.evaluate("STONE contains ORE"));
        assertTrue(Condition.evaluate("true = true && DEEPSLATE_GOLD_ORE contains ORE"));
        assertTrue(Condition.evaluate("false = true || 2 >= 2"));
        assertTrue(Condition.evaluate("ZOMBIE = zombie"));
        assertTrue(Condition.evaluate("STONE !contains ORE"));
    }

    @Test
    void stopCancelsWhenTrue() {
        var c = List.of(Condition.parse("%victim health% > 6 : %stop%"));
        assertTrue(Condition.check(c, vars(Map.of("victim health", "10"))).stop());
        assertFalse(Condition.check(c, vars(Map.of("victim health", "4"))).stop());
    }

    @Test
    void allowOnlyLetsThroughWhenTrue() {
        var c = List.of(Condition.parse("%player is sneaking% = true : %allow%"));
        assertFalse(Condition.check(c, vars(Map.of("player is sneaking", "true"))).stop());
        assertTrue(Condition.check(c, vars(Map.of("player is sneaking", "false"))).stop());
    }

    @Test
    void chanceBonusAddsUp() {
        var c = List.of(Condition.parse("%victim is sneaking% = true : %chance%+7"));
        assertEquals(7, Condition.check(c, vars(Map.of("victim is sneaking", "true"))).chanceBonus());
        assertEquals(0, Condition.check(c, vars(Map.of("victim is sneaking", "false"))).chanceBonus());
    }
}
