package com.arkcronist.gen.bukkit;

import com.arkcronist.gen.bukkit.mobs.SpawnedMobs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which mobs a purge takes: the ones this plugin spawned, and never a boss somebody placed.
 */
class SpawnedMobsTest {

    private static final Set<String> SPAWNABLE = Set.of("oak_tree_ent", "am_goblin_melee");

    @Test
    @DisplayName("a marked mob always goes")
    void marked() {
        assertTrue(SpawnedMobs.purgeable(true, null, SPAWNABLE));
    }

    @Test
    @DisplayName("an unmarked mob from the spawn pools goes, whatever case its name is in")
    void fromThePools() {
        assertTrue(SpawnedMobs.purgeable(false, "Oak_Tree_Ent", SPAWNABLE));
    }

    @Test
    @DisplayName("a boss and a vanilla mob stay")
    void bossAndVanillaStay() {
        assertFalse(SpawnedMobs.purgeable(false, "acnologia", SPAWNABLE));
        assertFalse(SpawnedMobs.purgeable(false, null, SPAWNABLE));
    }
}
