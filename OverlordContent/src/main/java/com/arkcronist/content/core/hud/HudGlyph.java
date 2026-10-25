package com.arkcronist.content.core.hud;

import com.arkcronist.content.core.definition.ResourceLocation;

import java.nio.file.Path;

/**
 * One HUD icon as the font draws it: its texture, at its height and ascent, on its character.
 *
 * @param origin what it is for, for messages: {@code hud demo:thirst full}
 */
public record HudGlyph(String origin, String namespace, ResourceLocation texture, Path sourceRoot, int height,
                       int ascent, int character) {
}
