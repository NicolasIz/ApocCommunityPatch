package com.arkcronist.gen.resources;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped hostile-mob table, checked against the packs it was written for.
 *
 * <p>The names in {@code config.yml} are the only link between this plugin and MythicMobs: the packs
 * themselves are installed separately and nothing here loads them, so a typo cannot be caught at
 * startup or by the compiler. It would show up on a live server as mobs that quietly never get
 * replaced, which is the least visible way for this to be broken.</p>
 *
 * <p>The two lists below are transcribed from the mob definitions in the Goblin Mobs and Skeleton
 * Mobs packs by Amonde. Only the names are here - the packs are paid assets and none of their files
 * are in this repository.</p>
 */
class HostileMobsConfigTest {

    /** Every mob the two packs define that is an actual enemy. */
    private static final Set<String> SPAWNABLE = Set.of(
            "am_goblin_brute", "am_goblin_mage", "am_goblin_melee", "am_goblin_ranger", "am_goblin_whip",
            "skeleton_melee", "skeleton_archer", "skeleton_mage", "skeleton_elite");

    /**
     * The one entry in those packs that must never appear in a spawn list.
     *
     * <p>{@code skeleton_mage_proj} is the mage's projectile, and it is an {@code ARMOR_STAND} with
     * no health, no AI and no model of its own beyond the bolt. Put it in the table and the world
     * fills up with invisible armour stands where its mobs should be.</p>
     */
    private static final String PROJECTILE = "skeleton_mage_proj";

    @SuppressWarnings("unchecked")
    private static Map<String, Object> hostileMobs() {
        Path onDisk = Path.of("src", "main", "resources", "config.yml");
        String text = assertDoesNotThrow(() -> Files.readString(onDisk, StandardCharsets.UTF_8));
        Map<String, Object> root = (Map<String, Object>) new Yaml().load(text);
        Object section = root.get("hostile-mobs");
        assertNotNull(section, "config.yml has no hostile-mobs section");
        assertTrue(section instanceof Map, "hostile-mobs must be a mapping");
        return (Map<String, Object>) section;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> table() {
        Object table = hostileMobs().get("table");
        assertNotNull(table, "hostile-mobs has no table");
        assertTrue(table instanceof Map, "hostile-mobs.table must be a mapping");
        return (Map<String, Object>) table;
    }

    @SuppressWarnings("unchecked")
    private static List<String> names() {
        List<String> names = new ArrayList<>();
        for (Object value : table().values()) {
            assertTrue(value instanceof List, "every entry in hostile-mobs.table must be a list");
            for (Object entry : (List<Object>) value) {
                names.add(String.valueOf(entry));
            }
        }
        return names;
    }

    @Test
    @DisplayName("every name in the table is a mob one of the installed packs actually defines")
    void everyNameExistsInThePacks() {
        List<String> unknown = new ArrayList<>();
        for (String name : names()) {
            if (!SPAWNABLE.contains(name)) {
                unknown.add(name);
            }
        }
        assertTrue(unknown.isEmpty(),
                "these names are in config.yml but not in the packs, so they would never spawn: " + unknown);
    }

    @Test
    @DisplayName("the mage's projectile is not in any spawn list")
    void theProjectileIsNotSpawnable() {
        assertTrue(!names().contains(PROJECTILE),
                PROJECTILE + " is the mage's projectile, an ARMOR_STAND - spawning it as an enemy "
                        + "would litter the world with invisible armour stands");
    }

    @Test
    @DisplayName("the swap is aimed at the preset that was asked for, and at natural spawning")
    void itIsPointedAtInsaneAndAtNaturalSpawns() {
        Map<String, Object> section = hostileMobs();
        assertEquals(Boolean.TRUE, section.get("enabled"), "the swap ships turned off");
        assertEquals(List.of("INSANE"), section.get("presets"),
                "the swap was asked for in the INSANE world");
        Object reasons = section.get("reasons");
        assertTrue(reasons instanceof List && ((List<?>) reasons).contains("NATURAL"),
                "natural spawning is the one that has to be replaced; got " + reasons);
    }

    @Test
    @DisplayName("both packs are drawn on, and the mobs are spread over several vanilla types")
    void bothPacksAreUsed() {
        List<String> names = names();
        assertTrue(names.stream().anyMatch(n -> n.startsWith("am_goblin")), "no goblin is ever used");
        assertTrue(names.stream().anyMatch(n -> n.startsWith("skeleton_")), "no skeleton is ever used");
        assertTrue(table().size() >= 4,
                "only " + table().size() + " vanilla types are replaced, which leaves most of the "
                        + "world's mobs vanilla");
    }
}
