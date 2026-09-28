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
    private static final String DEFAULT_TITLE = "&5⚡ &dEncantamientos &5⚡";
    public final Set<String> disabledWorlds;
    public final int passiveInterval;
    public final boolean importAdvancedEnchantments;
    public final List<String> extraMaterials;
    public final boolean convertLegacyLore;
    public final double chargeSeconds;
    public final String protectedLore;
    public final java.util.Map<String, Scroll> scrolls = new java.util.LinkedHashMap<>();

    /** One kind of scroll: looks, the success range it is sold/given with, and its /enchanter price in levels. */
    public record Scroll(String kind, Material material, String name, List<String> lore, double min, double max, int cost) {
    }
    public final boolean setExtra;
    public final boolean setAdvanced;
    public final boolean lootEnabled;
    public final double lootChance;
    public final int lootMax;
    public final double lootCurseChance;
    public final java.util.Map<String, Integer> lootWeights = new java.util.LinkedHashMap<>();
    public final Set<String> lootWorlds;
    public final double chargeReadySeconds;
    public final boolean setArcanos;
    public final boolean setAstrales;
    public final boolean cursesPermanent;
    public final boolean astralSoulbound;
    public final boolean astralKeepOnDeath;
    public final boolean spanishTexts;
    public final int bookWrap;
    public final String boundLore;
    public final int summonMax;
    public final double summonLeash;
    public final double summonAggro;
    /** Creatures SUMMON can call by name: MythicMobs ids tried in order, then a vanilla fallback. */
    public final java.util.Map<String, Creature> creatures = new java.util.LinkedHashMap<>();

    public record Creature(String key, List<String> mythic, org.bukkit.entity.EntityType vanilla, String name) {
    }

    /** books.lore up to 1.5.0: a config that still has it moves to the new, more detailed book. */
    private static final List<String> OLD_BOOK_LORE = List.of("&7%description%", "", "&a%success%% &7probabilidad de exito",
            "&c%destroy%% &7probabilidad de romper el item", "", "&7Aplica a: &f%applies-to%", "&8Arrastralo encima del item para aplicarlo.");
    private final FileConfiguration cfg;

    public Settings(FileConfiguration c) {
        this.cfg = c;
        loreFormat = c.getString("lore.format", "%group-color%%display% %level%");
        soulsLore = c.getString("lore.souls", "&cAlmas: &f%souls%");
        maxEnchants = c.getInt("apply.max-enchants", 9);
        upgradeOnSameLevel = c.getBoolean("apply.upgrade-on-same-level", true);
        bookMaterial = material(c.getString("books.material"), Material.ENCHANTED_BOOK);
        bookName = c.getString("books.name", "%group-color%&l%display% %level%");
        List<String> bookLines = c.getStringList("books.lore");
        var cfgDefaults = c.getDefaults();
        if (bookLines.equals(OLD_BOOK_LORE) && cfgDefaults != null && !cfgDefaults.getStringList("books.lore").isEmpty()) {
            bookLines = cfgDefaults.getStringList("books.lore");
        }
        bookLore = bookLines;
        mysteryMaterial = material(c.getString("mystery-books.material"), Material.BOOK);
        mysteryName = c.getString("mystery-books.name", "%group-color%&lLibro %group-name% &7(Clic derecho)");
        mysteryLore = c.getStringList("mystery-books.lore");
        trackerMaterial = material(c.getString("soul-tracker.material"), Material.PAPER);
        trackerName = c.getString("soul-tracker.name", "&f&lRastreador de almas");
        trackerLore = c.getStringList("soul-tracker.lore");
        String title = c.getString("enchanter.title", DEFAULT_TITLE);
        // the old default from 1.4.0 and earlier moves to the new one; a title someone wrote stays
        enchanterTitle = title.equals("&8Encantador") ? DEFAULT_TITLE : title;
        disabledWorlds = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        disabledWorlds.addAll(c.getStringList("disabled-worlds"));
        passiveInterval = Math.max(10, c.getInt("passive-interval-ticks", 40));
        importAdvancedEnchantments = c.getBoolean("import-advancedenchantments", false);
        extraMaterials = c.getStringList("apply.extra-materials");
        convertLegacyLore = c.getBoolean("convert-advancedenchantments-lore", true);
        chargeSeconds = c.getDouble("charge.seconds", 1.2);
        protectedLore = c.getString("lore.protected", "&f&lPROTEGIDO");
        String[][] defaults = {
            {"extract", "INK_SAC", "&8&lPergamino de Extraccion &7(%success%%)", "25-100", "30"},
            {"protect", "PAPER", "&f&lPergamino de Proteccion", "100", "25"},
            {"purify", "GLOW_INK_SAC", "&b&lPergamino de Purificacion &7(%success%%)", "25-100", "35"}};
        for (String[] d : defaults) {
            String base = "scrolls." + d[0] + ".";
            double[] range = com.arkcronist.enchants.load.EnchantLoader.range(c.getString(base + "success", d[3]), 100, 100);
            List<String> lore = c.getStringList(base + "lore");
            scrolls.put(d[0], new Scroll(d[0], material(c.getString(base + "material", d[1]), Material.PAPER),
                    c.getString(base + "name", d[2]), lore, range[0], range[1], c.getInt(base + "cost", Integer.parseInt(d[4]))));
        }
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
        setArcanos = c.getBoolean("sets.arcanos", true);
        setAstrales = c.getBoolean("sets.astrales", true);
        cursesPermanent = c.getBoolean("curses.permanent", true);
        astralSoulbound = c.getBoolean("astral.soulbound", true);
        astralKeepOnDeath = c.getBoolean("astral.keep-on-death", true);
        spanishTexts = c.getBoolean("spanish-texts", true);
        bookWrap = Math.max(20, c.getInt("books.wrap", 34));
        boundLore = c.getString("astral.bound-lore", "&d✦ Vinculado a &f%player%");
        summonMax = Math.max(1, c.getInt("summons.max-per-player", 6));
        summonLeash = Math.max(6, c.getDouble("summons.leash", 24));
        summonAggro = Math.max(2, c.getDouble("summons.aggro-radius", 12));
        var cs = c.getConfigurationSection("summons.creatures");
        if (cs != null) {
            for (String k : cs.getKeys(false)) {
                var e = cs.getConfigurationSection(k);
                if (e == null) {
                    continue;
                }
                org.bukkit.entity.EntityType type = com.arkcronist.enchants.engine.Lookups.entity(e.getString("vanilla", "ZOMBIE"));
                creatures.put(k.toLowerCase(java.util.Locale.ROOT), new Creature(k.toLowerCase(java.util.Locale.ROOT),
                        e.getStringList("mythicmobs"), type == null ? org.bukkit.entity.EntityType.ZOMBIE : type,
                        e.getString("name", k)));
            }
        }
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
