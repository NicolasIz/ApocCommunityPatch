package com.arkcronist.enchants.model;

import java.util.List;
import java.util.NavigableMap;
import java.util.Set;

/** A custom enchantment loaded from enchantments.yml. */
public record Enchant(String id, String display, String description, String appliesTo, String group,
                      Set<Trigger> triggers, List<String> applies, NavigableMap<Integer, EnchantLevel> levels,
                      List<String> required, List<String> conflicts, boolean removable, boolean inEnchanter) {

    public int maxLevel() {
        return levels.isEmpty() ? 0 : levels.lastKey();
    }

    /** Closest level at or below the one on the item (items can carry levels the config no longer has). */
    public EnchantLevel level(int lvl) {
        var e = levels.floorEntry(lvl);
        return e == null ? null : e.getValue();
    }
}
