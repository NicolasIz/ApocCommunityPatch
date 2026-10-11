package com.arkcronist.content.core.geyser;

/**
 * Text as a Bedrock client can draw it. Pictures in the private use area - font images, menu
 * backgrounds, HUD glyphs - and the negative spaces that place them come from a Java pack's fonts,
 * which Bedrock has none of: on its screen they are empty boxes. Only emojis get glyph sheets in
 * the Bedrock pack, and in a title they are dropped all the same: what is left is the words.
 */
public final class BedrockText {

    private static final int PRIVATE_USE_FIRST = 0xE000;
    private static final int PRIVATE_USE_LAST = 0xF8FF;

    private BedrockText() {
    }

    /** {@code text} without private-use characters, trimmed, with runs of spaces made one. */
    public static String plain(String text) {
        StringBuilder out = new StringBuilder(text.length());
        text.codePoints()
                .filter(point -> point < PRIVATE_USE_FIRST || point > PRIVATE_USE_LAST)
                .forEach(out::appendCodePoint);
        return out.toString().replaceAll(" {2,}", " ").trim();
    }
}
