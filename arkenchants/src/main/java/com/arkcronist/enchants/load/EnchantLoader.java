package com.arkcronist.enchants.load;

import com.arkcronist.enchants.engine.Condition;
import com.arkcronist.enchants.engine.EffectLine;
import com.arkcronist.enchants.model.Enchant;
import com.arkcronist.enchants.model.EnchantLevel;
import com.arkcronist.enchants.model.Group;
import com.arkcronist.enchants.model.Trigger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.bukkit.configuration.ConfigurationSection;

/** Reads enchantments.yml and groups.yml in AdvancedEnchantments' format. */
public final class EnchantLoader {

    /** What went wrong or was skipped while loading, for the console summary. */
    public static final class Report {
        public final Set<String> unknownTriggers = new LinkedHashSet<>();
        public final List<String> broken = new ArrayList<>();
    }

    private EnchantLoader() {
    }

    public static Map<String, Enchant> enchants(ConfigurationSection root, Report report) {
        Map<String, Enchant> out = new LinkedHashMap<>();
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            try {
                Enchant e = enchant(id.toLowerCase(Locale.ROOT), s, report);
                if (e.levels().isEmpty()) {
                    report.broken.add(id + ": no levels");
                    continue;
                }
                out.put(e.id(), e);
            } catch (RuntimeException ex) {
                report.broken.add(id + ": " + ex.getMessage());
            }
        }
        return out;
    }

    static Enchant enchant(String id, ConfigurationSection s, Report report) {
        Set<Trigger> triggers = Trigger.parse(s.getString("type"), report.unknownTriggers);
        TreeMap<Integer, EnchantLevel> levels = new TreeMap<>();
        ConfigurationSection lv = s.getConfigurationSection("levels");
        if (lv != null) {
            for (String key : lv.getKeys(false)) {
                ConfigurationSection l = lv.getConfigurationSection(key);
                int n;
                try {
                    n = Integer.parseInt(key.trim());
                } catch (NumberFormatException ex) {
                    continue;
                }
                if (l == null) {
                    continue;
                }
                List<Condition> conds = new ArrayList<>();
                for (String c : strings(l, "conditions", "condition")) {
                    conds.add(Condition.parse(c));
                }
                List<EffectLine> effects = new ArrayList<>();
                for (String ef : l.getStringList("effects")) {
                    effects.add(EffectLine.parse(ef));
                }
                levels.put(n, new EnchantLevel(n, l.getDouble("chance", 100), l.getDouble("cooldown", 0),
                        List.copyOf(conds), List.copyOf(effects), l.getString("description")));
            }
        }
        ConfigurationSection set = s.getConfigurationSection("settings");
        List<String> required = set == null ? List.of() : lower(set.getStringList("required-enchants"));
        List<String> conflicts = set == null ? List.of() : lower(set.getStringList("not-applyable-with"));
        boolean removable = set == null || set.getBoolean("removeable", true);
        boolean inEnchanter = set == null || !set.getBoolean("disable-in-enchanter", false);
        List<String> applies = new ArrayList<>();
        for (String a : s.getStringList("applies")) {
            applies.add(a.trim().toUpperCase(Locale.ROOT));
        }
        return new Enchant(id, s.getString("display", "%group-color%" + id), s.getString("description", ""),
                s.getString("applies-to", ""), s.getString("group", "SIMPLE").toUpperCase(Locale.ROOT),
                Collections.unmodifiableSet(triggers), List.copyOf(applies),
                Collections.unmodifiableNavigableMap(levels), required, conflicts, removable, inEnchanter);
    }

    /**
     * Groups from AdvancedEnchantments' groups.yml (colour and name) merged with ArkEnchants' own book
     * settings from config.yml ({@code groups.<ID>.success/destroy/cost/weight}).
     */
    public static Map<String, Group> groups(ConfigurationSection aeGroups, ConfigurationSection ours) {
        Map<String, Group> out = new LinkedHashMap<>();
        Set<String> ids = new LinkedHashSet<>();
        if (aeGroups != null) {
            ids.addAll(aeGroups.getKeys(false));
        }
        if (ours != null) {
            ids.addAll(ours.getKeys(false));
        }
        for (String raw : ids) {
            String id = raw.toUpperCase(Locale.ROOT);
            ConfigurationSection a = aeGroups == null ? null : aeGroups.getConfigurationSection(raw);
            ConfigurationSection o = ours == null ? null : ours.getConfigurationSection(raw);
            String color = pick(o, a, "color", "global-color", "&7");
            String name = pick(o, a, "name", "group-name", id.charAt(0) + id.substring(1).toLowerCase(Locale.ROOT));
            int[] success = range(o == null ? null : o.getString("success"), 25, 100);
            int[] destroy = range(o == null ? null : o.getString("destroy"), 0, 50);
            out.put(id, new Group(id, color, name, o == null ? 10 : o.getInt("weight", 10),
                    success[0], success[1], destroy[0], destroy[1], o == null ? 10 : o.getInt("cost", 10),
                    o == null ? !id.equals("CURSE") : o.getBoolean("enchanter", !id.equals("CURSE"))));
        }
        // curses always have a group, even with an old groups.yml
        out.putIfAbsent("CURSE", new Group("CURSE", "&4", "Maldito", 20, 100, 100, 0, 0, 0, false));
        return out;
    }

    private static String pick(ConfigurationSection first, ConfigurationSection second, String k1, String k2, String def) {
        if (first != null && first.isString(k1)) {
            return first.getString(k1);
        }
        if (second != null && second.isString(k2)) {
            return second.getString(k2);
        }
        return def;
    }

    static int[] range(String s, int lo, int hi) {
        if (s == null) {
            return new int[]{lo, hi};
        }
        String[] p = s.split("-");
        try {
            int a = Integer.parseInt(p[0].trim());
            int b = p.length > 1 ? Integer.parseInt(p[1].trim()) : a;
            return new int[]{Math.min(a, b), Math.max(a, b)};
        } catch (NumberFormatException e) {
            return new int[]{lo, hi};
        }
    }

    private static List<String> strings(ConfigurationSection s, String... keys) {
        for (String k : keys) {
            if (s.isList(k)) {
                return s.getStringList(k);
            }
            if (s.isString(k)) {
                return List.of(s.getString(k));
            }
        }
        return List.of();
    }

    private static List<String> lower(List<String> in) {
        List<String> out = new ArrayList<>();
        for (String s : in) {
            out.add(s.toLowerCase(Locale.ROOT).trim());
        }
        return List.copyOf(out);
    }
}
