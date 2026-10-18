package com.arkcronist.content.bukkit.bedrock;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 3.3.3: text for a Bedrock player, with each glyph too wide for one Bedrock glyph written as the
 * pieces the .mcpack draws it in ({@code BedrockConverter.Build#glyphText}). No PacketEvents here:
 * the listener that uses it on the way out is in the PacketEvents hook.
 */
public final class WideGlyphText {

    private WideGlyphText() {
    }

    /** What finds those characters and writes their pieces in; null when there are none. */
    public static @Nullable TextReplacementConfig replacementFor(Map<Integer, String> pieces) {
        if (pieces.isEmpty()) {
            return null;
        }
        // Private-use characters only: none of them means anything to a pattern.
        StringBuilder characters = new StringBuilder("[");
        pieces.keySet().forEach(characters::appendCodePoint);
        Pattern pattern = Pattern.compile(characters.append(']').toString());
        return TextReplacementConfig.builder()
                .match(pattern)
                .replacement((match, builder) -> builder.content(pieces.getOrDefault(match.group().codePointAt(0),
                        match.group())))
                .build();
    }

    /** The text with every such character written as its pieces; null when it had none. */
    public static @Nullable Component written(@Nullable Component text, TextReplacementConfig config) {
        if (text == null) {
            return null;
        }
        Component written = text.replaceText(config);
        return written.equals(text) ? null : written;
    }
}
