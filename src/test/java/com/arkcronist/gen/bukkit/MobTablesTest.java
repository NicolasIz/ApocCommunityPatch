package com.arkcronist.gen.bukkit;

import com.arkcronist.gen.bukkit.mythic.MobTables;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Putting discovery together with what the operator wrote.
 *
 * <p>Every one of these is really the same assertion from a different angle: switching auto-discovery
 * on must not change an answer config.yml already gave. That is the promise the feature is shipped
 * on, and it is the only part of it a test can hold.</p>
 */
class MobTablesTest {

    @Test
    @DisplayName("a configured entity type keeps exactly what was written for it")
    void configuredKeysWinOutright() {
        Map<String, List<String>> configured = Map.of("ZOMBIE", List.of("am_goblin_melee"));
        Map<String, List<String>> discovered = new LinkedHashMap<>();
        discovered.put("ZOMBIE", List.of("Nether_Mushroom", "frostmite", "oak_tree_ent"));
        discovered.put("PILLAGER", List.of("goblin_archer"));

        Map<String, List<String>> merged = MobTables.swap(configured, discovered);

        // The whole point: writing one name under ZOMBIE means that name, not a one-in-four chance
        // of it.
        assertEquals(List.of("am_goblin_melee"), merged.get("ZOMBIE"));
        assertEquals(List.of("goblin_archer"), merged.get("PILLAGER"),
                "discovery did not fill an entity type the config says nothing about");
    }

    @Test
    @DisplayName("an ambient pool keeps the configured names first and adds the rest")
    void poolsAreAUnion() {
        List<String> merged = MobTables.pool(List.of("am_goblin_melee", "skeleton_archer"),
                List.of("frostmite", "oak_tree_ent"));
        assertEquals(List.of("am_goblin_melee", "skeleton_archer", "frostmite", "oak_tree_ent"),
                merged);
    }

    @Test
    @DisplayName("a name already written by hand is not added a second time")
    void poolsDoNotDouble() {
        // A repeat in these lists is the weighting mechanism, so a silent doubling would quietly
        // give a mob twice its share of the world.
        List<String> merged = MobTables.pool(List.of("am_goblin_melee"),
                List.of("AM_Goblin_Melee", "frostmite"));
        assertEquals(List.of("am_goblin_melee", "frostmite"), merged,
                "the same mob typed differently was counted twice");
    }

    @Test
    @DisplayName("nothing discovered leaves the configured tables untouched")
    void discoveryOffChangesNothing() {
        Map<String, List<String>> configured = Map.of("ZOMBIE", List.of("am_goblin_melee"));
        List<String> pool = List.of("am_goblin_melee");

        assertEquals(configured, MobTables.swap(configured, Map.of()));
        assertEquals(configured, MobTables.swap(configured, null));
        assertEquals(pool, MobTables.pool(pool, List.of()));
        assertEquals(pool, MobTables.pool(pool, null));
    }

    @Test
    @DisplayName("an empty config leaves discovery as the only answer")
    void nothingConfigured() {
        Map<String, List<String>> discovered = Map.of("ZOMBIE", List.of("am_goblin_melee"));
        assertEquals(discovered, MobTables.swap(Map.of(), discovered));
        assertEquals(discovered, MobTables.swap(null, discovered));
        assertEquals(List.of("frostmite"), MobTables.pool(List.of(), List.of("frostmite")));
        assertEquals(List.of("frostmite"), MobTables.pool(null, List.of("frostmite")));
    }

    @Test
    @DisplayName("both empty is an empty answer, not a null")
    void bothEmpty() {
        assertTrue(MobTables.swap(null, null).isEmpty());
        assertTrue(MobTables.pool(null, null).isEmpty());
    }
}
