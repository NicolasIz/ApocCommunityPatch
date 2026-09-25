package com.arkcronist.enchants.engine;

/**
 * Damage changes collected from every enchant that fired on one hit (the attacker's weapon and the defender's
 * armor), applied once at the end so they add up instead of overwriting each other.
 */
public final class DamageMods {

    public double percent;
    public double multiplier = 1;
    public double flat;

    public double apply(double damage) {
        double d = (damage * Math.max(0, 1 + percent / 100.0)) * multiplier + flat;
        return Math.max(0, d);
    }

    public boolean changed() {
        return percent != 0 || multiplier != 1 || flat != 0;
    }
}
