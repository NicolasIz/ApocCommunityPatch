package com.arkcronist.content.bukkit.bedrock;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 3.3.3: a rank too wide for one Bedrock glyph, written as its pieces in what a Bedrock player is sent. */
class WideGlyphTextTest {

    private static final String PIECES = "\uF7FF\uF7FE\uF7FD";

    @Test
    void aWideGlyphIsWrittenAsItsPiecesKeepingItsStyle() {
        TextReplacementConfig config = WideGlyphText.replacementFor(Map.of(0xE6A0, PIECES));
        assertNotNull(config);
        Component line = Component.text("Rango: ").append(Component.text("\uE6A0", NamedTextColor.WHITE))
                .append(Component.text(" \uE6A0!"));
        Component written = WideGlyphText.written(line, config);
        assertNotNull(written);
        assertEquals("Rango: " + PIECES + " " + PIECES + "!", PlainTextComponentSerializer.plainText().serialize(written));
        TextComponent rank = (TextComponent) written.children().get(0);
        assertEquals(NamedTextColor.WHITE, rank.color(), "the rank keeps its colour");
    }

    @Test
    void textWithoutOneIsLeftAsItWas() {
        TextReplacementConfig config = WideGlyphText.replacementFor(Map.of(0xE6A0, PIECES));
        assertNotNull(config);
        assertNull(WideGlyphText.written(Component.text("Mundo: spawn \uE6A1"), config));
        assertNull(WideGlyphText.written(null, config));
        assertNull(WideGlyphText.replacementFor(Map.of()));
    }
}
