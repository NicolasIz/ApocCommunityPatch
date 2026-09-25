package com.arkcronist.enchants.model;

import java.util.Locale;
import java.util.Set;

/**
 * Matches AdvancedEnchantments' "applies" entries (ALL_SWORD, ALL_ARMOR, DIAMOND_ARMOR, BOW...) against a
 * material name, so it can be tested without a server.
 */
public final class ItemKinds {

    private static final Set<String> ARMOR_SUFFIX = Set.of("_HELMET", "_CHESTPLATE", "_LEGGINGS", "_BOOTS");

    private ItemKinds() {
    }

    public static boolean matches(String entry, String material, boolean edible) {
        String e = entry.trim().toUpperCase(Locale.ROOT);
        String m = material.toUpperCase(Locale.ROOT);
        if (e.equals(m) || e.equals("ALL")) {
            return true;
        }
        switch (e) {
            case "ALL_ARMOR":
                return armor(m);
            case "ALL_HELMET":
                return m.endsWith("_HELMET") || m.equals("TURTLE_HELMET");
            case "ALL_CHESTPLATE":
                return m.endsWith("_CHESTPLATE");
            case "ALL_LEGGINGS":
                return m.endsWith("_LEGGINGS");
            case "ALL_BOOTS":
                return m.endsWith("_BOOTS");
            case "ALL_SWORD":
                return m.endsWith("_SWORD");
            case "ALL_AXE":
                return m.endsWith("_AXE") && !m.endsWith("_PICKAXE");
            case "ALL_PICKAXE":
                return m.endsWith("_PICKAXE");
            case "ALL_SPADE":
            case "ALL_SHOVEL":
                return m.endsWith("_SHOVEL") || m.endsWith("_SPADE");
            case "ALL_HOE":
                return m.endsWith("_HOE");
            case "ALL_TOOLS":
                return m.endsWith("_PICKAXE") || m.endsWith("_AXE") || m.endsWith("_SHOVEL") || m.endsWith("_HOE");
            case "ALL_WEAPONS":
                return m.endsWith("_SWORD") || (m.endsWith("_AXE") && !m.endsWith("_PICKAXE")) || m.equals("TRIDENT")
                        || m.equals("MACE");
            case "ALL_EDIBLE":
                return edible;
            case "SKULL_ITEM":
                return m.endsWith("_HEAD") || m.endsWith("_SKULL");
            default:
                break;
        }
        if (e.endsWith("_ARMOR")) {
            // LEATHER_ARMOR, IRON_ARMOR, CHAIN_ARMOR, GOLD_ARMOR, DIAMOND_ARMOR, NETHERITE_ARMOR
            String prefix = e.substring(0, e.length() - "_ARMOR".length())
                    .replace("CHAIN", "CHAINMAIL").replace("GOLD", "GOLDEN");
            return armor(m) && m.startsWith(prefix + "_");
        }
        return false;
    }

    public static boolean armor(String m) {
        for (String s : ARMOR_SUFFIX) {
            if (m.endsWith(s)) {
                return true;
            }
        }
        return m.equals("TURTLE_HELMET");
    }
}
