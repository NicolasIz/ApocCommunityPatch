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

    /** Every mob the installed packs define that is an actual enemy. */
    private static final Set<String> SPAWNABLE = Set.of(
            // Goblin Mobs and Skeleton Mobs, by Amonde.
            "am_goblin_brute", "am_goblin_mage", "am_goblin_melee", "am_goblin_ranger", "am_goblin_whip",
            "skeleton_melee", "skeleton_archer", "skeleton_mage", "skeleton_elite",
            // Spider Mobs, by Amonde.
            "spider_melee", "spider_trapper", "spider_poison", "spider_elite",
            // Cursed Mobs.
            "cursed_knight", "cursed_archer", "cursed_mage",
            // Dungeon Skeletons V1 - Volcanic Cinder expansion, by E-magination.
            "dSkeleton_footman_Cinder_em", "dSkeleton_swordman_Cinder_em", "dSkeleton_Archer_Cinder_em",
            "dSkeleton_Warrior_Cinder_em", "dSkeleton_Halberdier_Cinder_em", "dSkeleton_Wizard_Cinder_em",
            "dSkeleton_Tank_Cinder_em");

    /** The Volcanic Cinder names. The whole point of them is that they stay in the Nether. */
    private static final Set<String> CINDER = Set.of(
            "dSkeleton_footman_Cinder_em", "dSkeleton_swordman_Cinder_em", "dSkeleton_Archer_Cinder_em",
            "dSkeleton_Warrior_Cinder_em", "dSkeleton_Halberdier_Cinder_em", "dSkeleton_Wizard_Cinder_em",
            "dSkeleton_Tank_Cinder_em");

    /**
     * The entries in those packs that must never appear in a spawn list.
     *
     * <p>Every one of these is a prop rather than an enemy, and every one of them would be a
     * different flavour of mess. {@code skeleton_mage_proj} is the mage's bolt, an
     * {@code ARMOR_STAND} with no health and no AI: put it in a spawn list and the world fills with
     * invisible armour stands. The {@code cursed_*_vfx} entries are chickens with a thousand health
     * carrying a visual effect. {@code spider_trap} is the web the trapper lays, and {@code
     * spider_pois} and the {@code spdr_stomp_vfx} pair are armour stands again.</p>
     *
     * <p>None of these is caught by anything but a list. They are all real, spawnable names that
     * MythicMobs will happily produce.</p>
     */
    private static final Set<String> PROPS = Set.of(
            "skeleton_mage_proj", "spider_trap", "spider_pois", "spdr_stomp_vfx", "spdr_stomp_vfx_small",
            "cursed_slash_vfx", "cursed_cast_vfx", "cursed_spiral_vfx", "cursed_ray_vfx",
            "cursed_flames_vfx", "cursed_hollow_vfx", "cursed_arrow_vfx", "cursed_arrow_rain_vfx");

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
    private static Map<String, Object> nether() {
        Object section = hostileMobs().get("nether");
        assertNotNull(section, "hostile-mobs has no nether section");
        return (Map<String, Object>) section;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> ambient() {
        Object section = hostileMobs().get("ambient");
        assertNotNull(section, "hostile-mobs has no ambient section");
        return (Map<String, Object>) section;
    }

    @SuppressWarnings("unchecked")
    private static List<String> flatten(Object value) {
        List<String> names = new ArrayList<>();
        if (value instanceof List) {
            for (Object entry : (List<Object>) value) {
                names.add(String.valueOf(entry));
            }
        } else if (value instanceof Map) {
            for (Object nested : ((Map<String, Object>) value).values()) {
                names.addAll(flatten(nested));
            }
        }
        return names;
    }

    /** Every mob name anywhere in the section: both tables and both ambient pools. */
    private static List<String> names() {
        List<String> names = new ArrayList<>(flatten(table()));
        names.addAll(flatten(nether().get("table")));
        names.addAll(flatten(ambient().get("overworld")));
        names.addAll(flatten(ambient().get("nether")));
        return names;
    }

    /** Every name that can appear anywhere except the Nether. */
    private static List<String> overworldNames() {
        List<String> names = new ArrayList<>(flatten(table()));
        names.addAll(flatten(ambient().get("overworld")));
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
    @DisplayName("no projectile, prop or visual effect is in any spawn list")
    void thePropsAreNotSpawnable() {
        List<String> wrong = new ArrayList<>();
        for (String name : names()) {
            if (PROPS.contains(name)) {
                wrong.add(name);
            }
        }
        assertTrue(wrong.isEmpty(), "these are props, not enemies - armour stands, chickens carrying "
                + "a visual effect, the web a trapper lays - and spawning them as mobs would litter "
                + "the world with them: " + wrong);
    }

    @Test
    @DisplayName("the volcanic skeletons appear in the Nether and nowhere else")
    void theCinderSkeletonsStayInTheNether() {
        // This was the instruction in so many words, and it is the one thing here that a typo could
        // undo without anybody noticing until a player met a volcanic skeleton on a green hillside.
        List<String> escaped = new ArrayList<>();
        for (String name : overworldNames()) {
            if (CINDER.contains(name)) {
                escaped.add(name);
            }
        }
        assertTrue(escaped.isEmpty(),
                "these are the Volcanic Cinder skeletons and they are reachable outside the Nether: "
                        + escaped);

        List<String> down = new ArrayList<>(flatten(nether().get("table")));
        down.addAll(flatten(ambient().get("nether")));
        assertTrue(down.stream().anyMatch(CINDER::contains),
                "nothing in the Nether lists is a volcanic skeleton, so they would never appear at all");
        for (String name : down) {
            assertTrue(CINDER.contains(name),
                    name + " is in a Nether list but is not one of the volcanic skeletons; the Nether "
                            + "was asked for those and only those");
        }
    }

    @Test
    @DisplayName("the daylight spawner is on, bounded, and has something to spawn")
    void theAmbientSpawnerIsUsable() {
        Map<String, Object> ambient = ambient();
        assertEquals(Boolean.TRUE, ambient.get("enabled"),
                "the ambient spawner ships off, so the mobs would still only come out at night");
        assertTrue(!flatten(ambient.get("overworld")).isEmpty(), "nothing to spawn in the overworld");
        assertTrue(!flatten(ambient.get("nether")).isEmpty(), "nothing to spawn in the Nether");

        int min = (Integer) ambient.get("min-distance");
        int max = (Integer) ambient.get("max-distance");
        assertTrue(min >= 20, "min-distance of " + min + " is close enough for a player to watch a "
                + "mob appear out of nothing");
        assertTrue(max > min, "max-distance must be beyond min-distance");
        assertTrue(max <= 64, "max-distance of " + max + " reaches past what a server keeps loaded, "
                + "so most attempts would land in an unloaded chunk and be thrown away");
        assertTrue((Integer) ambient.get("period-ticks") >= 20, "the spawner runs more than once a second");
        assertTrue((Integer) ambient.get("per-player-cap") > 0, "the per-player cap is zero, so it never spawns");
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
        assertTrue(names.stream().anyMatch(n -> n.startsWith("spider_")), "no spider is ever used");
        assertTrue(names.stream().anyMatch(n -> n.startsWith("cursed_")), "no cursed mob is ever used");
        assertTrue(names.stream().anyMatch(CINDER::contains), "no volcanic skeleton is ever used");
        assertTrue(table().size() >= 4,
                "only " + table().size() + " vanilla types are replaced, which leaves most of the "
                        + "world's mobs vanilla");
    }
}
