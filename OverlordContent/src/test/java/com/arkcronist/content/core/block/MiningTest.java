package com.arkcronist.content.core.block;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 5.4: a custom block's tool and hardness, reckoned as vanilla reckons mining. */
class MiningTest {

    private static final double EPSILON = 1e-9;
    /** A note block: what a custom cube is underneath. Hardness 0.8, mined by anything, an axe best. */
    private static final double NOTE_BLOCK = 0.8;

    private final Mining iron = new Mining(Mining.Tool.PICKAXE, Mining.Tier.IRON, 5, true);

    @Test
    void progressIsVanillas() {
        // Stone (1.5) with a wooden pickaxe (2): 2 / 1.5 / 30 a tick - 22.5 ticks, 1.15 s in vanilla.
        assertEquals(2 / 1.5 / 30, Mining.progress(2, 1.5, true), EPSILON);
        // Stone by hand: not harvested, so over 100.
        assertEquals(1 / 1.5 / 100, Mining.progress(1, 1.5, false), EPSILON);
        assertEquals(0, Mining.progress(1, Mining.UNBREAKABLE, true));
        assertTrue(Double.isInfinite(Mining.progress(1, 0, true)));
    }

    @Test
    void theFactorMakesTheNoteBlockMineAsTheCustomBlock() {
        // An iron pickaxe hits a note block at a hand's speed, 1, and harvests it; it should mine the
        // ruby block at its speed against stone, 6, as the right tool.
        double factor = iron.speedFactor(1, NOTE_BLOCK, true, 6, true);
        assertEquals((6 / 5.0 / 30) / (1 / NOTE_BLOCK / 30), factor, EPSILON);
        assertEquals(Mining.progress(6, 5, true), Mining.progress(1, NOTE_BLOCK, true) * factor, EPSILON);

        // By hand: the wrong tool, a hundredth of a hardness of 5 a tick.
        double hand = iron.speedFactor(1, NOTE_BLOCK, true, 1, false);
        assertEquals(Mining.progress(1, 5, false), Mining.progress(1, NOTE_BLOCK, true) * hand, EPSILON);
        assertTrue(hand < factor);

        // A copper slab (hardness 3) underneath, which an axe hits at 1 and does not harvest.
        Mining axe = new Mining(Mining.Tool.AXE, Mining.Tier.ANY, 2, false);
        double planks = axe.speedFactor(1, 3, false, 6, true);
        assertEquals(Mining.progress(6, 2, true), Mining.progress(1, 3, false) * planks, EPSILON);
    }

    @Test
    void unbreakableAndInstantBlocks() {
        Mining bedrock = new Mining(Mining.Tool.NONE, Mining.Tier.ANY, Mining.UNBREAKABLE, false);
        assertTrue(bedrock.unbreakable());
        assertEquals(0, bedrock.speedFactor(8, NOTE_BLOCK, true, 1, true));
        Mining flower = new Mining(Mining.Tool.NONE, Mining.Tier.ANY, 0, false);
        assertEquals(Mining.MAX_FACTOR, flower.speedFactor(1, NOTE_BLOCK, true, 1, true));
        assertTrue(flower.instant(1, true));
        assertFalse(iron.instant(6, true));
        // Never past what the attribute takes.
        Mining soft = new Mining(Mining.Tool.NONE, Mining.Tier.ANY, 0.001, false);
        assertEquals(Mining.MAX_FACTOR, soft.speedFactor(1, 50, false, 1, true));
    }

    @Test
    void dropsNeedTheToolOnlyWhenAsked() {
        assertTrue(iron.drops(true));
        assertFalse(iron.drops(false));
        Mining wood = new Mining(Mining.Tool.AXE, Mining.Tier.ANY, 2, false);
        assertTrue(wood.drops(false));
    }

    @Test
    void toolsAndTiersAreReadByName() {
        assertEquals(Optional.of(Mining.Tool.PICKAXE), Mining.Tool.parse("Pickaxe"));
        assertEquals(Optional.of(Mining.Tool.SHOVEL), Mining.Tool.parse("spade"));
        assertEquals(Optional.of(Mining.Tool.NONE), Mining.Tool.parse("any"));
        assertEquals(Optional.empty(), Mining.Tool.parse("sword"));
        assertEquals(Optional.of(Mining.Tier.WOOD), Mining.Tier.parse("golden"));
        assertEquals(Optional.of(Mining.Tier.IRON), Mining.Tier.parse("iron"));
        assertEquals(Mining.Tier.DIAMOND, Mining.Tier.ofMaterial("DIAMOND_AXE"));
        assertEquals(Mining.Tier.WOOD, Mining.Tier.ofMaterial("GOLDEN_SHOVEL"));
        assertNull(Mining.Tier.ofMaterial("PAPER"));
        assertTrue(Mining.Tier.IRON.admits(Mining.Tier.NETHERITE));
        assertFalse(Mining.Tier.IRON.admits(Mining.Tier.STONE));
        assertTrue(Mining.Tier.IRON.admits(null), "a custom tool of no known tier");
        assertEquals(7.5f, iron.bedrockSeconds(), 1e-6);
    }
}
