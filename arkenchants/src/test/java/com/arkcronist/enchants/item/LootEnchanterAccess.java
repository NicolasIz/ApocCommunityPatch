package com.arkcronist.enchants.item;

import java.util.random.RandomGenerator;

/** Test access to LootEnchanter's package-private level roll. */
public final class LootEnchanterAccess {

    private LootEnchanterAccess() {
    }

    public static int level(int max, RandomGenerator r) {
        return LootEnchanter.level(max, r);
    }
}
