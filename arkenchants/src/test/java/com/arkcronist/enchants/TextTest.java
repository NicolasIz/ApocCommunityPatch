package com.arkcronist.enchants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.arkcronist.enchants.text.MathExpr;
import com.arkcronist.enchants.text.Roman;
import com.arkcronist.enchants.text.Tags;
import java.util.Random;
import org.junit.jupiter.api.Test;

class TextTest {

    @Test
    void bookDescriptionsWrapIntoLinesWithoutLineBreaks() {
        var lines = com.arkcronist.enchants.item.Items.wrap(
                "Plant seeds in a 3x3 area\nby shift+right-clicking. Una frase bastante larga para partirla en dos.", 20);
        assertEquals("Plant seeds in a 3x3", lines.get(0));
        assertEquals("area", lines.get(1));
        assertEquals("by", lines.get(2).substring(0, 2));
        for (String l : lines) {
            assertTrue(!l.contains("\n") && l.length() <= 22, l);
        }
        // colours carry over to the next line
        var coloured = com.arkcronist.enchants.item.Items.wrap("&cuno dos tres cuatro cinco seis", 10);
        assertTrue(coloured.get(1).startsWith("&c"), coloured.toString());
    }

    @Test
    void triggersReadAsSpanish() {
        var t = java.util.EnumSet.of(com.arkcronist.enchants.model.Trigger.ATTACK, com.arkcronist.enchants.model.Trigger.ATTACK_MOB);
        assertEquals("al golpear", com.arkcronist.enchants.item.Texts.triggers(t));
        assertEquals("permanente", com.arkcronist.enchants.item.Texts.chance(100,
                java.util.EnumSet.of(com.arkcronist.enchants.model.Trigger.EFFECT_STATIC)));
        assertEquals("5 min", com.arkcronist.enchants.item.Texts.cooldown(300));
        assertEquals("ninguna", com.arkcronist.enchants.item.Texts.cooldown(0));
    }

    @Test
    void romanNumeralsRoundTrip() {
        assertEquals("I", Roman.of(1));
        assertEquals("IV", Roman.of(4));
        assertEquals("IX", Roman.of(9));
        assertEquals("XIV", Roman.of(14));
        for (int i = 1; i < 200; i++) {
            assertEquals(i, Roman.parse(Roman.of(i)));
        }
        assertEquals(-1, Roman.parse("ABC"));
    }

    @Test
    void mathFollowsPrecedence() {
        assertEquals(7, MathExpr.eval("1 + 2 * 3"));
        assertEquals(9, MathExpr.eval("(1 + 2) * 3"));
        assertEquals(12.5, MathExpr.eval("10 * (1.0 + 0.25 * 1)"));
        assertEquals(-2, MathExpr.eval("-2"));
        assertEquals(8, MathExpr.eval("2 ^ 3"));
        assertEquals(24, MathExpr.eval("min(10 * 3, 24)"));
        assertEquals(6, MathExpr.eval("min(2 * 3, 24)"));
        assertEquals(5, MathExpr.eval("max(1, 5, 3)"));
        assertEquals(2, MathExpr.eval("floor(2.7)"));
    }

    @Test
    void randomNumbersStayInRange() {
        Random r = new Random(1);
        for (int i = 0; i < 200; i++) {
            int v = Integer.parseInt(Tags.resolve("<random number>2-5</random number>", r));
            assertTrue(v >= 2 && v <= 5);
        }
    }

    @Test
    void mathRunsAfterRandomAndDropsTrailingZero() {
        Random r = new Random(1);
        assertEquals("EXP:15", Tags.resolve("EXP:<math>10 * (1.0 + 0.25 * 2)</math>", r));
        String word = Tags.resolve("<random word>a,b</random word>", r);
        assertTrue(word.equals("a") || word.equals("b"));
    }
}
