package com.arkcronist.content.core.importer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyTextTest {

    @Test
    void coloursAndDecorationsBecomeTags() {
        assertEquals("<red>Ruby", LegacyText.toMiniMessage("&cRuby"));
        assertEquals("<gold><bold>Gold", LegacyText.toMiniMessage("§6§lGold"));
        assertEquals("<underlined><italic>x", LegacyText.toMiniMessage("&n&ox"));
    }

    @Test
    void aColourAfterADecorationEndsItAsLegacyTextDoes() {
        assertEquals("<red><bold>Hot <reset><gray>cold", LegacyText.toMiniMessage("&c&lHot &7cold"));
        assertEquals("<bold>a<reset>b", LegacyText.toMiniMessage("&la&rb"));
    }

    @Test
    void bothHexForms() {
        assertEquals("<#ff8800>Amber", LegacyText.toMiniMessage("&#FF8800Amber"));
        assertEquals("<#ff8800>Amber", LegacyText.toMiniMessage("&x&f&f&8&8&0&0Amber"));
    }

    @Test
    void anythingElseIsLeftAsWritten() {
        assertEquals("Salt & pepper", LegacyText.toMiniMessage("Salt & pepper"));
        assertEquals("<gradient:red:blue>Fancy</gradient>", LegacyText.toMiniMessage("<gradient:red:blue>Fancy</gradient>"));
        assertEquals("&", LegacyText.toMiniMessage("&"));
        assertEquals("&#12", LegacyText.toMiniMessage("&#12"));
        assertEquals("&zx", LegacyText.toMiniMessage("&zx"));
    }
}
