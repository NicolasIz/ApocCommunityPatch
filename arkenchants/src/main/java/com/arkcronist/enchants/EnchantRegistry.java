package com.arkcronist.enchants;

import com.arkcronist.enchants.model.Enchant;
import com.arkcronist.enchants.model.Group;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/** The loaded enchantments and groups. Swapped as a whole on reload. */
public final class EnchantRegistry {

    private final Map<String, Enchant> enchants;
    private final Map<String, Group> groups;
    private final List<String> groupOrder;

    public EnchantRegistry(Map<String, Enchant> enchants, Map<String, Group> groups) {
        this.enchants = enchants;
        this.groups = groups;
        this.groupOrder = new ArrayList<>(groups.keySet());
    }

    public Enchant get(String id) {
        return id == null ? null : enchants.get(id.toLowerCase(java.util.Locale.ROOT));
    }

    public Collection<Enchant> all() {
        return enchants.values();
    }

    public Group group(String id) {
        Group g = groups.get(id);
        if (g == null && !groups.isEmpty()) {
            g = groups.values().iterator().next();
        }
        return g;
    }

    public Collection<Group> groups() {
        return groups.values();
    }

    /** Position of a group in the file order; later groups are rarer and are listed first on items. */
    public int rank(String groupId) {
        int i = groupOrder.indexOf(groupId);
        return i < 0 ? -1 : i;
    }

    public List<Enchant> inGroup(String groupId) {
        List<Enchant> out = new ArrayList<>();
        for (Enchant e : enchants.values()) {
            if (e.group().equalsIgnoreCase(groupId) && e.inEnchanter()) {
                out.add(e);
            }
        }
        return out;
    }

    public Enchant random(String groupId, RandomGenerator rnd) {
        List<Enchant> list = inGroup(groupId);
        return list.isEmpty() ? null : list.get(rnd.nextInt(list.size()));
    }
}
