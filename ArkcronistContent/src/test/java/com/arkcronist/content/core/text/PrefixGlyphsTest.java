package com.arkcronist.content.core.text;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** 3.0: emojis in LuckPerms prefixes, the prefix's own colours kept around them. */
class PrefixGlyphsTest {

    private static final Function<String, String> EMOJIS = Map.of("crown", "", "ruby", "")::get;

    @Test
    void anEmojiIsDrawnWhiteAndTheColourAfterItComesBack() {
        assertEquals("&c&f&c Admin", PrefixGlyphs.replace("&c:crown: Admin", EMOJIS));
        assertEquals("§6§l[§f§6§lVIP]",
                PrefixGlyphs.replace("§6§l[:ruby:VIP]", EMOJIS), "bold comes back after the glyph");
    }

    @Test
    void hexColoursAreKeptWhole() {
        assertEquals("&#ff8800&f&#ff8800Gold", PrefixGlyphs.replace("&#ff8800:crown:Gold", EMOJIS));
        String spigotHex = "§x§f§f§8§8§0§0";
        assertEquals(spigotHex + "§f" + spigotHex + "x",
                PrefixGlyphs.replace(spigotHex + ":crown:x", EMOJIS));
    }

    @Test
    void withNoColourInForceTheTextAfterIsReset() {
        assertEquals("&f&r Admin &cX", PrefixGlyphs.replace(":crown: Admin &cX", EMOJIS));
    }

    @Test
    void miniMessagePrefixesGetTheGlyphWrappedInWhite() {
        assertEquals("<red><white></white> Admin</red>", PrefixGlyphs.replace("<red>:crown: Admin</red>", EMOJIS));
    }

    @Test
    void unknownNamesAndTextWithoutColonsAreLeftAlone() {
        String plain = "&7[Member]";
        assertSame(plain, PrefixGlyphs.replace(plain, EMOJIS));
        assertEquals("&7at 10:30 :nope: &f&7", PrefixGlyphs.replace("&7at 10:30 :nope: :ruby:", EMOJIS));
        assertEquals("R&D :crown:x", PrefixGlyphs.replace("R&D :crown:x", name -> null));
    }
}
