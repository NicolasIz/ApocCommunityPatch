package com.arkcronist.gen.bukkit;

import com.arkcronist.gen.bukkit.mobs.StructureGarrison;
import com.arkcronist.gen.bukkit.mythic.StructureThemes;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which mobs a datapack's structure gets, with the structure keys of the packs this was written
 * against: Incendium, Nullscape, Just Another Structure Pack, Grim Kingdoms, Ominous Towers, Reds
 * More Structures and Villages Revamped.
 */
class StructureThemesTest {

    private static final List<String> MOBS = List.of("cursed_knight", "cursed_archer", "cursed_mage",
            "skeleton_melee", "skeleton_mage", "am_goblin_brute", "spider_trapper",
            "dSkeleton_Warrior_Cinder_em");

    @Test
    @DisplayName("a castle gets the knights and the warriors")
    void castle() {
        StructureThemes.Pick pick = StructureThemes.pick("incendium:forbidden_castle", MOBS, MOBS);
        assertTrue(pick.mobs().contains("cursed_knight"), pick.toString());
        assertTrue(pick.mobs().contains("dSkeleton_Warrior_Cinder_em"), pick.toString());
        assertFalse(pick.mobs().contains("spider_trapper"), pick.toString());
    }

    @Test
    @DisplayName("catacombs get the dead, an arcane spire the casters")
    void cryptAndSpire() {
        StructureThemes.Pick crypt = StructureThemes.pick("ethrium:catacombs", MOBS, MOBS);
        assertTrue(crypt.mobs().contains("skeleton_melee"));
        assertFalse(crypt.mobs().contains("cursed_knight"));
        StructureThemes.Pick spire = StructureThemes.pick("ominous:arcane_spire", MOBS, MOBS);
        assertTrue(spire.mobs().contains("cursed_mage"));
        assertTrue(spire.mobs().contains("skeleton_mage"));
    }

    @Test
    @DisplayName("a haunted house gets the dead, a witch hut the casters")
    void hostileHouses() {
        assertTrue(StructureThemes.pick("trek:overworld/medium/haunted_house", MOBS, MOBS).mobs()
                .contains("skeleton_melee"));
        assertTrue(StructureThemes.pick("terralith:witch_hut", MOBS, MOBS).mobs()
                .contains("cursed_mage"));
    }

    @Test
    @DisplayName("a structure whose name says nothing gets the general mix")
    void noTheme() {
        List<String> general = List.of("am_goblin_brute");
        StructureThemes.Pick pick = StructureThemes.pick("nullscape:rift", MOBS, general);
        assertEquals(general, pick.mobs());
    }

    @Test
    @DisplayName("villages, cabins, statues and friendly outposts are left alone")
    void peaceful() {
        for (String key : List.of("vsrevamped:village_cherry", "incendium:piglin_village",
                "grim_kingdoms:nordic_cabin1", "red:herobrine_statue", "ethrium:friendly_outpost",
                "ethrium:forest_ambient", "red:birch_deforestation", "grim_kingdoms:gnomish_hut")) {
            assertTrue(StructureThemes.peaceful(key, StructureThemes.PEACEFUL), key);
        }
        for (String key : List.of("incendium:forbidden_castle", "ethrium:catacombs",
                "grim_kingdoms:glacierfall_keep", "ominous:ominous_tower", "red:dark_tower",
                "nullscape:dragon_skeleton", "trek:overworld/medium/haunted_house",
                "terralith:witch_hut", "trek:overworld/medium/farm_pillager",
                "trek:overworld/medium/sorcerers_house")) {
            assertFalse(StructureThemes.peaceful(key, StructureThemes.PEACEFUL), key);
        }
    }

    @Test
    @DisplayName("only the part of a piece inside the chunk is used, and far edges are exclusive")
    void pieceInsideChunk() {
        // A room from x=10 to x=24 (exclusive), in the chunk that starts at x=16.
        int[] part = StructureGarrison.within(new BoundingBox(10, 40, 3, 25, 48, 9), 16, 0);
        assertArrayEquals(new int[] {16, 24, 3, 8, 40, 47}, part);
        assertNull(StructureGarrison.within(new BoundingBox(0, 40, 0, 16, 48, 16), 16, 0));
    }
}
