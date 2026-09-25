package com.arkcronist.enchants.model;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/** When an enchantment fires. Names match AdvancedEnchantments' "type" values. */
public enum Trigger {
    ATTACK, ATTACK_MOB,
    DEFENSE, DEFENSE_MOB, DEFENSE_PROJECTILE,
    SHOOT, SHOOT_MOB,
    MINING,
    KILL_MOB, KILL_PLAYER,
    DEATH,
    FALL_DAMAGE, FIRE, EXPLOSION,
    EFFECT_STATIC, HELD, REPEATING, ELYTRA_FLY,
    RIGHT_CLICK,
    CATCH_FISH, HOOK_ENTITY, BITE_HOOK,
    ITEM_BREAK;

    /** Parses "ATTACK;ATTACK_MOB"; unknown names go to {@code unknown}. */
    public static Set<Trigger> parse(String value, Set<String> unknown) {
        Set<Trigger> out = EnumSet.noneOf(Trigger.class);
        if (value == null) {
            return out;
        }
        for (String part : value.split("[;,]")) {
            String p = part.trim().toUpperCase(Locale.ROOT);
            if (p.isEmpty()) {
                continue;
            }
            if (p.equals("PASSIVE_DEATH")) {
                out.add(DEATH);
                continue;
            }
            try {
                out.add(valueOf(p));
            } catch (IllegalArgumentException e) {
                unknown.add(p);
            }
        }
        return out;
    }

    /** Triggers whose items are the worn armor (plus whatever else matches "applies"). */
    public boolean passive() {
        return this == EFFECT_STATIC || this == REPEATING || this == ELYTRA_FLY || this == HELD;
    }
}
