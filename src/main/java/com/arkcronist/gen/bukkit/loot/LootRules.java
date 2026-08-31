package com.arkcronist.gen.bukkit.loot;

import com.arkcronist.gen.core.math.FastRandom;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootTables;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * What goes in a structure's chests, read from {@code config.yml}.
 *
 * <p>Until now this was a switch in Java: the folder a schematic came out of picked a vanilla loot
 * table and that was the end of it. That is fine for the families this plugin builds itself and
 * useless for the ones it does not - a server that drops its own castles into {@code prefabs/castles/}
 * had no way to say what is in them, and a server that wanted its own economy's items in there had no
 * way at all.</p>
 *
 * <p>So a theme can now name vanilla tables, its own items, or both, and anything it does not name
 * falls through to the built-in behaviour. A config that says nothing behaves exactly as before,
 * which is the point: this adds a lever, it does not move one.</p>
 *
 * <p>Names are checked when the config is read rather than when a chest is opened. A misspelt loot
 * table or material would otherwise be a chest that is quietly emptier than intended, on one
 * structure, somewhere - the least findable kind of wrong.</p>
 */
public final class LootRules {

    /** One line of a theme's own item list. */
    public record Entry(Material material, int min, int max, double weight, boolean enchanted) {
        public ItemStack roll(FastRandom random) {
            int amount = min >= max ? min : random.nextInt(min, max + 1);
            return new ItemStack(material, Math.max(1, Math.min(amount, material.getMaxStackSize())));
        }
    }

    /** Everything one theme says about its chests. */
    public record Theme(List<LootTables> tables, List<Entry> items, double totalWeight,
                        int rolls, double fillChance) {

        /** Whether this theme says anything at all, or should fall through to the built-in rules. */
        public boolean speaks() {
            return !tables.isEmpty() || !items.isEmpty();
        }

        /** One item from the theme's own list, chosen by weight. */
        public ItemStack pick(FastRandom random) {
            if (items.isEmpty()) {
                return null;
            }
            double target = random.nextDouble() * totalWeight;
            for (Entry entry : items) {
                target -= entry.weight();
                if (target <= 0.0) {
                    return entry.roll(random);
                }
            }
            return items.get(items.size() - 1).roll(random);
        }
    }

    private static final Theme SILENT = new Theme(List.of(), List.of(), 0.0, 0, 1.0);

    private final Map<String, Theme> themes;
    private final boolean enabled;
    private final int maxStacks;

    private LootRules(Map<String, Theme> themes, boolean enabled, int maxStacks) {
        this.themes = themes;
        this.enabled = enabled;
        this.maxStacks = maxStacks;
    }

    /** Nothing configured: every theme falls through to the built-in rules. */
    public static LootRules none() {
        return new LootRules(Map.of(), true, 8);
    }

    /**
     * Reads the {@code loot} section.
     *
     * @param warn told about every name that could not be understood, once, at load
     */
    public static LootRules read(FileConfiguration config, Consumer<String> warn) {
        boolean enabled = config.getBoolean("loot.enabled", true);
        int maxStacks = Math.max(1, Math.min(27, config.getInt("loot.max-stacks", 8)));
        double defaultChance = clampChance(config.getDouble("loot.fill-chance", 1.0));
        int defaultRolls = Math.max(0, config.getInt("loot.rolls", 4));

        ConfigurationSection section = config.getConfigurationSection("loot.themes");
        if (section == null) {
            return new LootRules(Map.of(), enabled, maxStacks);
        }
        Map<String, Theme> themes = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection theme = section.getConfigurationSection(key);
            if (theme == null) {
                continue;
            }
            String name = key.trim().toLowerCase(Locale.ROOT);
            themes.put(name, readTheme(name, theme, defaultChance, defaultRolls, warn));
        }
        return new LootRules(Map.copyOf(themes), enabled, maxStacks);
    }

    private static Theme readTheme(String name, ConfigurationSection theme, double defaultChance,
                                   int defaultRolls, Consumer<String> warn) {
        List<LootTables> tables = new ArrayList<>();
        for (String raw : theme.getStringList("tables")) {
            String value = raw.trim().toUpperCase(Locale.ROOT);
            if (value.isEmpty()) {
                continue;
            }
            try {
                tables.add(LootTables.valueOf(value));
            } catch (IllegalArgumentException ignored) {
                warn.accept("loot.themes." + name + ": '" + raw + "' is not a loot table this "
                        + "version of the game has; that entry is ignored");
            }
        }

        List<Entry> items = new ArrayList<>();
        double total = 0.0;
        for (String raw : theme.getStringList("items")) {
            Entry entry = readEntry(name, raw, warn);
            if (entry != null) {
                items.add(entry);
                total += entry.weight();
            }
        }

        return new Theme(List.copyOf(tables), List.copyOf(items), total,
                Math.max(0, theme.getInt("rolls", defaultRolls)),
                clampChance(theme.getDouble("fill-chance", defaultChance)));
    }

    /**
     * One line of an item list: {@code MATERIAL amount [weight=n] [enchanted]}.
     *
     * <p>The amount is either a number or a {@code min-max} range. Everything after it is optional
     * and order does not matter, because a config people edit by hand should not punish them for
     * writing the words the other way round.</p>
     */
    private static Entry readEntry(String theme, String raw, Consumer<String> warn) {
        String[] parts = raw.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) {
            return null;
        }
        Material material = Material.matchMaterial(parts[0]);
        if (material == null || material.isAir()) {
            warn.accept("loot.themes." + theme + ": '" + parts[0] + "' is not a material; that line "
                    + "is ignored");
            return null;
        }

        int min = 1;
        int max = 1;
        double weight = 10.0;
        boolean enchanted = false;
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i].toLowerCase(Locale.ROOT);
            if (part.equals("enchanted")) {
                enchanted = true;
            } else if (part.startsWith("weight=")) {
                weight = Math.max(0.01, parseDouble(part.substring(7), 10.0));
            } else if (part.contains("-")) {
                int dash = part.indexOf('-');
                min = (int) parseDouble(part.substring(0, dash), 1);
                max = (int) parseDouble(part.substring(dash + 1), min);
            } else {
                min = (int) parseDouble(part, 1);
                max = min;
            }
        }
        if (min < 1) {
            min = 1;
        }
        if (max < min) {
            max = min;
        }
        return new Entry(material, min, max, weight, enchanted);
    }

    private static double parseDouble(String text, double fallback) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double clampChance(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    /** Whether chests are filled at all. */
    public boolean enabled() {
        return enabled;
    }

    /** Most different items one chest may hold. */
    public int maxStacks() {
        return maxStacks;
    }

    /** What the config says about this theme; a silent theme means "use the built-in rules". */
    public Theme theme(String name) {
        Theme found = themes.get(name == null ? "" : name.toLowerCase(Locale.ROOT));
        return found == null ? SILENT : found;
    }

    /** The theme names the config actually defines; used by the tests and by {@code /ag}. */
    public java.util.Set<String> names() {
        return themes.keySet();
    }
}
