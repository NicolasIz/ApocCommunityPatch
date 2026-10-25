package com.arkcronist.content.core.definition;

import java.nio.file.Path;

/**
 * A chat emoji: {@code :name:} typed in chat becomes a glyph drawn from a texture in the pack.
 *
 * @param namespace  the content pack it came from, whose textures/ holds the image
 * @param name       what is typed between the colons; unique across every namespace
 * @param texture    the image, usually 16x16
 * @param height     glyph height in font pixels (a line of chat is 9); the image is scaled to it
 * @param ascent     how far the glyph reaches above the baseline, at most {@code height}
 * @param permission needed to use it, or null for everyone
 * @param sourceRoot the content folder {@code textures/} is read from
 * @param source     the content file it came from, for messages
 */
public record EmojiDefinition(String namespace, String name, ResourceLocation texture, int height, int ascent,
                              String permission, Path sourceRoot, Path source) {

    public String fullId() {
        return namespace + ":" + name;
    }
}
