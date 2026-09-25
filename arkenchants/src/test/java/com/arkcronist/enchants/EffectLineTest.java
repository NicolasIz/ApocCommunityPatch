package com.arkcronist.enchants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.arkcronist.enchants.engine.EffectLine;
import java.util.List;
import org.junit.jupiter.api.Test;

class EffectLineTest {

    @Test
    void nameArgsAndTarget() {
        EffectLine l = EffectLine.parse("POTION:SLOW:0:100 @Victim");
        assertEquals("POTION", l.name());
        assertEquals(List.of("SLOW", "0", "100"), l.args());
        assertEquals("VICTIM", l.target());
    }

    @Test
    void targetWrittenAsLastArgument() {
        EffectLine l = EffectLine.parse("GUARD:CREEPER:8:1:@Victim");
        assertEquals(List.of("CREEPER", "8", "1"), l.args());
        assertEquals("VICTIM", l.target());
    }

    @Test
    void targetWithOptions() {
        EffectLine l = EffectLine.parse("KILL:1 @Aoe{radius=3,target=mobs}");
        assertEquals("AOE", l.target());
        assertEquals("3", l.targetArgs().get("radius"));
        assertEquals("mobs", l.targetArgs().get("target"));
        EffectLine t = EffectLine.parse("BREAK_BLOCK @Tunnel{radiuscustom=1x3x5,mode=UP} <condition>%pitch% <= -30 : %allow%</condition>");
        assertEquals("TUNNEL", t.target());
        assertEquals("1x3x5", t.targetArgs().get("radiuscustom"));
        assertEquals(1, t.conditions().size());
        assertEquals(List.of(), t.args());
    }

    @Test
    void chanceTagAndRandomArgs() {
        EffectLine l = EffectLine.parse("POTION:SPEED:1:100 %attacker% <chance>25</chance>");
        assertEquals(25, l.chance());
        EffectLine r = EffectLine.parse("ADD_FOOD:<random number>1-4</random number> @Attacker");
        assertEquals(List.of("<random number>1-4</random number>"), r.args());
        assertEquals("ATTACKER", r.target());
    }

    @Test
    void noTargetMeansHolder() {
        EffectLine l = EffectLine.parse("MORE_DROPS:1");
        assertNull(l.target());
        assertEquals("MORE_DROPS", l.name());
        EffectLine m = EffectLine.parse("MESSAGE:§4You're bleeding! @Victim");
        assertEquals("§4You're bleeding!", m.args().get(0));
    }
}
