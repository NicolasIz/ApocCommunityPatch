package com.arkcronist.enchants.model;

import com.arkcronist.enchants.engine.Condition;
import com.arkcronist.enchants.engine.EffectLine;
import java.util.List;

/** One level of an enchantment: its chance, cooldown (seconds), conditions and effects. */
public record EnchantLevel(int level, double chance, double cooldown, List<Condition> conditions,
                           List<EffectLine> effects, String description) {
}
