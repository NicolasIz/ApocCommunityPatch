package com.arkcronist.enchants.model;

/** A rarity group (SIMPLE, UNIQUE...): colour, name and the book success/destroy ranges it rolls. */
public record Group(String id, String color, String name, int weight,
                    int successMin, int successMax, int destroyMin, int destroyMax, int enchanterCost,
                    boolean inEnchanter) {

    public boolean curse() {
        return id.equals("CURSE");
    }
}
