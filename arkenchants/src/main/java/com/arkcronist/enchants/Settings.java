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
        importAdvancedEnchantments = c.getBoolean("import-advancedenchantments", true);
        extraMaterials = c.getStringList("apply.extra-materials");
        convertLegacyLore = c.getBoolean("convert-advancedenchantments-lore", true);
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
