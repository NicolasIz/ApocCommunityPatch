package com.arkcronist.content.core.hud;

/**
 * A HUD as the built pack draws it: its definition, and its icons' characters and widths.
 */
public record HudLayout(HudDefinition definition, HudRenderer.Glyphs glyphs) {

    /**
     * The bar's text for {@code value} out of {@code max}: from its offset as written, or - with an
     * anchor - against the vanilla HUD, the bar's right end on the food bar's when it sits on that
     * side, however many icons it draws.
     */
    public String render(double value, double max) {
        HudAnchor anchor = definition.anchor();
        if (anchor == null) {
            return HudRenderer.render(glyphs, definition.segments(), definition.spacing(), definition.offset(), value, max);
        }
        java.util.List<HudRenderer.Icon> icons = HudRenderer.icons(glyphs, definition.segments(), value, max,
                anchor.right());
        return HudRenderer.place(icons, definition.spacing(), anchor.offset(HudRenderer.width(icons, definition.spacing())));
    }
}
