package com.arkcronist.content.core.hud;

/**
 * A HUD as the built pack draws it: its definition, and its icons' characters and widths.
 */
public record HudLayout(HudDefinition definition, HudRenderer.Glyphs glyphs) {

    /** The bar's text for {@code value} out of {@code max}. */
    public String render(double value, double max) {
        return HudRenderer.render(glyphs, definition.segments(), definition.spacing(), definition.offset(), value, max);
    }
}
