package com.arkcronist.gen.resources;

import org.bukkit.Material;
import org.bukkit.loot.LootTables;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped loot section, checked against the game it names.
 *
 * <p>Every string in that section is a name the compiler never sees: a loot table, a material, a
 * prefab folder. A typo in any of them fails in the quietest way this plugin has - one theme's
 * chests come out thinner than intended, on one kind of structure, somewhere in the world, and
 * nobody finds out. The plugin does warn at load, but only the person reading the console at the
 * right moment sees it.</p>
 *
 * <p>So the names are checked here instead, against the real {@link LootTables} and {@link Material}
 * of the version this builds against.</p>
 */
class LootConfigTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loot() {
        Path onDisk = Path.of("src", "main", "resources", "config.yml");
        String text = assertDoesNotThrow(() -> Files.readString(onDisk, StandardCharsets.UTF_8));
        Map<String, Object> root = (Map<String, Object>) new Yaml().load(text);
        Object section = root.get("loot");
        assertNotNull(section, "config.yml has no loot section");
        return (Map<String, Object>) section;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> themes() {
        Object themes = loot().get("themes");
        assertNotNull(themes, "loot has no themes");
        return (Map<String, Map<String, Object>>) themes;
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(Map<String, Object> theme, String key) {
        Object value = theme.get(key);
        if (value == null) {
            return List.of();
        }
        assertTrue(value instanceof List, key + " must be a list");
        List<String> out = new ArrayList<>();
        for (Object entry : (List<Object>) value) {
            out.add(String.valueOf(entry));
        }
        return out;
    }

    @Test
    @DisplayName("every loot table named is one this version of the game actually has")
    void everyTableExists() {
        List<String> unknown = new ArrayList<>();
        int total = 0;
        for (Map.Entry<String, Map<String, Object>> theme : themes().entrySet()) {
            for (String name : strings(theme.getValue(), "tables")) {
                total++;
                try {
                    LootTables.valueOf(name.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    unknown.add(theme.getKey() + " -> " + name);
                }
            }
        }
        assertTrue(total >= 10, "only " + total + " loot tables are named, so this proves little");
        assertTrue(unknown.isEmpty(), "these are not loot tables the game has, so those chests would "
                + "silently fall back to hand rolled items: " + unknown);
    }

    @Test
    @DisplayName("every item line parses, and names a real material")
    void everyItemLineIsUsable() {
        List<String> broken = new ArrayList<>();
        int total = 0;
        for (Map.Entry<String, Map<String, Object>> theme : themes().entrySet()) {
            for (String line : strings(theme.getValue(), "items")) {
                total++;
                String[] parts = line.trim().split("\\s+");
                // valueOf and not matchMaterial: matchMaterial goes through Paper's registry, which
                // does not exist without a running server. The plugin itself uses matchMaterial, so
                // it also accepts "minecraft:iron_ingot"; what is checked here is the shipped config,
                // which is written in the enum's own spelling.
                Material material = null;
                try {
                    material = parts.length == 0 ? null
                            : Material.valueOf(parts[0].toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    // Left null and reported below.
                }
                // Compared against the constants rather than asked isAir(): that call goes through
                // asBlockType() into Paper's registry, which is another thing that only exists with
                // a server running.
                if (material == null || material == Material.AIR || material == Material.CAVE_AIR
                        || material == Material.VOID_AIR) {
                    broken.add(theme.getKey() + " -> " + line + "  (not a material)");
                    continue;
                }
                boolean asksEnchanted = false;
                for (int i = 1; i < parts.length; i++) {
                    String part = parts[i].toLowerCase(Locale.ROOT);
                    if (part.equals("enchanted")) {
                        asksEnchanted = true;
                        continue;
                    }
                    if (part.startsWith("weight=")) {
                        continue;
                    }
                    // Everything else has to be an amount or a min-max range.
                    if (!part.matches("\\d+(-\\d+)?")) {
                        broken.add(theme.getKey() + " -> " + line + "  (cannot read '" + parts[i] + "')");
                    }
                }
                // The keyword, from the tokens after the material - not the word anywhere in the
                // line, which is what this asked first and which made ENCHANTED_BOOK and
                // ENCHANTED_GOLDEN_APPLE look like mistakes when they are exactly what was meant.
                if (asksEnchanted && !damageable(material)) {
                    broken.add(theme.getKey() + " -> " + line + "  (" + material
                            + " takes no enchantment from this, so 'enchanted' does nothing)");
                }
            }
        }
        assertTrue(total >= 25, "only " + total + " item lines, so this proves little");
        assertTrue(broken.isEmpty(), "these lines would be dropped at load:\n  "
                + String.join("\n  ", broken));
    }

    /** Whether this is the sort of thing an enchantment goes on: a tool, a weapon, or armour. */
    private static boolean damageable(Material material) {
        String name = material.name();
        for (String suffix : new String[]{"_SWORD", "_AXE", "_PICKAXE", "_SHOVEL", "_HOE",
                "_HELMET", "_CHESTPLATE", "_LEGGINGS", "_BOOTS", "BOW", "TRIDENT", "SHIELD",
                "ELYTRA", "SHEARS", "FISHING_ROD", "FLINT_AND_STEEL"}) {
            if (name.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("every prefab folder that ships with chests has a theme of its own")
    void thePrefabFoldersAreCovered() {
        // These are the folders whose schematics actually carry containers - measured, not guessed.
        // A folder with chests and no theme falls back to whatever the built-in switch says, which
        // for a folder somebody invented is nothing at all.
        List<String> withChests = List.of("castles", "houses", "ships", "temples", "ruins",
                "battle_towers", "ancient_city");
        List<String> missing = new ArrayList<>();
        for (String folder : withChests) {
            if (!themes().containsKey(folder)) {
                missing.add(folder);
            }
        }
        assertTrue(missing.isEmpty(), "these prefab folders ship schematics with chests in them but "
                + "have nothing in the loot config: " + missing);
    }

    @Test
    @DisplayName("the section is on, bounded, and its chances are fractions")
    void theSectionIsSane() {
        Map<String, Object> loot = loot();
        assertEquals(Boolean.TRUE, loot.get("enabled"), "the loot section ships turned off");
        int maxStacks = (Integer) loot.get("max-stacks");
        assertTrue(maxStacks >= 1 && maxStacks <= 27,
                "max-stacks of " + maxStacks + " is not a number of slots in a chest");

        for (Map.Entry<String, Map<String, Object>> theme : themes().entrySet()) {
            Object chance = theme.getValue().get("fill-chance");
            if (chance != null) {
                double value = ((Number) chance).doubleValue();
                assertTrue(value > 0.0 && value <= 1.0, theme.getKey() + ": fill-chance of " + value
                        + " is not a fraction; at zero the theme would never hold anything");
            }
            Object rolls = theme.getValue().get("rolls");
            if (rolls != null) {
                assertTrue((Integer) rolls >= 0 && (Integer) rolls <= 27,
                        theme.getKey() + ": rolls of " + rolls + " does not fit in a chest");
            }
        }
    }
}
