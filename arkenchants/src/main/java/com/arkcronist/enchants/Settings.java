package com.arkcronist.enchants;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

/** Everything read from ArkEnchants' config.yml. */
public final class Settings {

    public final String loreFormat;
    public final String soulsLore;
    public final int maxEnchants;
    public final boolean upgradeOnSameLevel;
    public final Material bookMaterial;
    public final String bookName;
    public final List<String> bookLore;
    public final Material mysteryMaterial;
    public final String mysteryName;
    public final List<String> mysteryLore;
    public final Material trackerMaterial;
    public final String trackerName;
    public final List<String> trackerLore;
    public final String enchanterTitle;
    public final Set<String> disabledWorlds;
    public final int passiveInterval;
    public final boolean importAdvancedEnchantments;
    public final List<String> extraMaterials;
    public final boolean convertLegacyLore;
    public final double chargeSeconds;
    public final boolean setExtra;
    public final boolean setAdvanced;
    public final boolean lootEnabled;
    public final double lootChance;
    public final int lootMax;
    public final double lootCurseChance;
    public final java.util.Map<String, Integer> lootWeights = new java.util.LinkedHashMap<>();
    public final Set<String> lootWorlds;
    public final double chargeReadySeconds;
    private final FileConfiguration cfg;

    public Settings(FileConfiguration c) {
        this.cfg = c;
        loreFormat = c.getString("lore.format", "%group-color%%display% %level%");
        soulsLore = c.getString("lore.souls", "&cAlmas: &f%souls%");
        maxEnchants = c.getInt("apply.max-enchants", 9);
        upgradeOnSameLevel = c.getBoolean("apply.upgrade-on-same-level", true);
        bookMaterial = material(c.getString("books.material"), Material.ENCHANTED_BOOK);
        bookName = c.getString("books.name", "%group-color%&l%display% %level%");
        bookLore = c.getStringList("books.lore");
        mysteryMaterial = material(c.getString("mystery-books.material"), Material.BOOK);
        mysteryName = c.getString("mystery-books.name", "%group-color%&lLibro %group-name% &7(Clic derecho)");
        mysteryLore = c.getStringList("mystery-books.lore");
        trackerMaterial = material(c.getString("soul-tracker.material"), Material.PAPER);
        trackerName = c.getString("soul-tracker.name", "&f&lRastreador de almas");
        trackerLore = c.getStringList("soul-tracker.lore");
        enchanterTitle = c.getString("enchanter.title", "&8Encantador");
        disabledWorlds = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        disabledWorlds.addAll(c.getStringList("disabled-worlds"));
        passiveInterval = Math.max(10, c.getInt("passive-interval-ticks", 40));
        importAdvancedEnchantments = c.getBoolean("import-advancedenchantments", false);
        extraMaterials = c.getStringList("apply.extra-materials");
        convertLegacyLore = c.getBoolean("convert-advancedenchantments-lore", true);
        chargeSeconds = c.getDouble("charge.seconds", 1.2);
        setExtra = c.getBoolean("sets.extra", true);
        setAdvanced = c.getBoolean("sets.advancedenchantments", true);
        lootEnabled = c.getBoolean("loot.enabled", true);
        lootChance = c.getDouble("loot.chance", 40);
        lootMax = Math.max(1, c.getInt("loot.max-enchants", 3));
        lootCurseChance = c.getDouble("loot.curse-chance", 20);
        var w = c.getConfigurationSection("loot.group-weights");
        if (w != null) {
            for (String k : w.getKeys(false)) {
                lootWeights.put(k.toUpperCase(java.util.Locale.ROOT), w.getInt(k));
            }
        }
        if (lootWeights.isEmpty()) {
            // an older config.yml without the section still gets sensible odds
            lootWeights.putAll(java.util.Map.of("SIMPLE", 40, "UNIQUE", 25, "ELITE", 15, "ULTIMATE", 10, "LEGENDARY", 6, "FABLED", 2));
        }
        lootWorlds = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        lootWorlds.addAll(c.getStringList("loot.worlds"));
        chargeReadySeconds = c.getDouble("charge.ready-seconds", 4);
    }

    public String msg(String key) {
        return cfg.getString("messages." + key, "&c[" + key + "]");
    }

    public String prefix() {
        return cfg.getString("messages.prefix", "&5&lArkEnchants &8» &7");
    }

    private static Material material(String name, Material def) {
        if (name == null) {
            return def;
        }
        Material m = Material.matchMaterial(name);
        return m == null ? def : m;
    }
}
