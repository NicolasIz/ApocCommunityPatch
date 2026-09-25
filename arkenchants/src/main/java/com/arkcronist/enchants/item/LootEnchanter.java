package com.arkcronist.enchants.item;

import com.arkcronist.enchants.EnchantRegistry;
import com.arkcronist.enchants.Settings;
import com.arkcronist.enchants.model.Enchant;
import com.arkcronist.enchants.model.Group;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;
import org.bukkit.inventory.ItemStack;

/**
 * Random enchants for found gear: a few enchants drawn by rarity weight (rare ones rarely), low levels more
 * often than high ones, and now and then a curse.
 */
public final class LootEnchanter {

    private LootEnchanter() {
    }

    /** Enchants the item in place; returns how many enchants it got (0 = untouched). */
    public static int roll(ItemStack item, EnchantRegistry reg, Settings s, RandomGenerator r, boolean forced) {
        if (Items.empty(item) || item.getMaxStackSize() > 1 || !Items.enchants(item).isEmpty() || Items.readBook(item) != null) {
            return 0;
        }
        if (!forced && r.nextDouble(100) >= s.lootChance) {
            return 0;
        }
        Map<String, Integer> out = new LinkedHashMap<>();
        int want = 1;
        while (want < s.lootMax && r.nextDouble() < 0.35) {
            want++;
        }
        for (int tries = 0; out.size() < want && tries < 20; tries++) {
            Group g = pickGroup(reg, s, r);
            if (g == null) {
                break;
            }
            Enchant e = pick(reg, g.id(), item, out, r);
            if (e != null) {
                out.put(e.id(), level(e.maxLevel(), r));
            }
        }
        if (r.nextDouble(100) < s.lootCurseChance) {
            Enchant curse = pick(reg, "CURSE", item, out, r);
            if (curse != null) {
                out.put(curse.id(), level(curse.maxLevel(), r));
            }
        }
        if (out.isEmpty()) {
            return 0;
        }
        Items.setEnchants(item, out);
        return out.size();
    }

    /** Level 1 is the most common; the top level shows up about one time in ten on a 3-level enchant. */
    static int level(int max, RandomGenerator r) {
        if (max <= 1) {
            return 1;
        }
        return 1 + (int) Math.floor(Math.pow(r.nextDouble(), 1.8) * max);
    }

    private static Group pickGroup(EnchantRegistry reg, Settings s, RandomGenerator r) {
        List<Group> groups = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        int total = 0;
        for (Group g : reg.groups()) {
            if (g.curse()) {
                continue;
            }
            int w = s.lootWeights.getOrDefault(g.id(), 0);
            if (w > 0) {
                groups.add(g);
                weights.add(w);
                total += w;
            }
        }
        if (total <= 0) {
            return null;
        }
        int x = r.nextInt(total);
        for (int i = 0; i < groups.size(); i++) {
            x -= weights.get(i);
            if (x < 0) {
                return groups.get(i);
            }
        }
        return groups.get(groups.size() - 1);
    }

    private static Enchant pick(EnchantRegistry reg, String group, ItemStack item, Map<String, Integer> have, RandomGenerator r) {
        List<Enchant> list = new ArrayList<>();
        for (Enchant e : reg.all()) {
            if (!e.group().equalsIgnoreCase(group) || have.containsKey(e.id()) || !Items.applicable(e, item)
                    || !e.required().isEmpty()) {
                continue;
            }
            boolean clash = false;
            for (String c : e.conflicts()) {
                clash |= have.containsKey(c);
            }
            for (String h : have.keySet()) {
                Enchant o = reg.get(h);
                clash |= o != null && o.conflicts().contains(e.id());
            }
            if (!clash) {
                list.add(e);
            }
        }
        return list.isEmpty() ? null : list.get(r.nextInt(list.size()));
    }
}
