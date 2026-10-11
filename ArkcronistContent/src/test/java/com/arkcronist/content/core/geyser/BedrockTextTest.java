package com.arkcronist.content.core.geyser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BedrockTextTest {

    @Test
    void picturesAndNegativeSpacesAreDroppedAndTheWordsKept() {
        String title = "  Quest Board";
        assertEquals("Quest Board", BedrockText.plain(title));
    }

    @Test
    void plainTextIsLeftAlone() {
        assertEquals("Mochila de viaje", BedrockText.plain("Mochila de viaje"));
        assertEquals("", BedrockText.plain(""));
    }
}
