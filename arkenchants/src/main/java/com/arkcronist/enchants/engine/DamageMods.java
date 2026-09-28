package com.arkcronist.enchants.engine;

/**
 * Damage changes collected from every enchant that fired on one hit (the attacker's weapon and the defender's
 * armor), applied once at the end so they add up instead of overwriting each other.
 */
public final class DamageMods {

    public double percent;
    public double multiplier = 1;
    public double flat;

    /** Caps from config.yml (balance.*): how far all enchants together may raise or lower one hit, in percent. */
    public static double maxBonus = 200;
    public static double maxReduction = 80;

    public double apply(double damage) {
        double factor = Math.max(0, 1 + percent / 100.0) * multiplier;
        if (multiplier != 0) {
            // NEGATE_DAMAGE (multiplier 0) still cancels the whole hit; everything else stays within the caps
            factor = Math.max(1 - maxReduction / 100.0, Math.min(1 + maxBonus / 100.0, factor));
        }
        return Math.max(0, damage * factor + flat);
    }

    public boolean changed() {
        return percent != 0 || multiplier != 1 || flat != 0;
    }
}
