package com.arkcronist.gen.bukkit;

import com.arkcronist.gen.bukkit.mythic.BiomeProfile;
import com.arkcronist.gen.bukkit.mythic.BiomeThemes;
import com.arkcronist.gen.bukkit.mythic.BiomeThemes.Candidate;
import com.arkcronist.gen.bukkit.mythic.MobClassifier.Habitat;
import com.arkcronist.gen.bukkit.mythic.VanillaBiomes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Giving a discovered mob the biomes its name points to.
 *
 * <p>The two failures worth guarding are opposite ones. A mob sent somewhere its name never said -
 * a goblin kept out of half the world because "goblin" happened to share letters with a biome - is
 * the first. The second is a place-named mob left everywhere, which is the yeti in the desert this
 * exists to end. Both are tested against the game's biomes plus a handful shaped like Terralith's,
 * whose names do not always say what they are.</p>
 */
class BiomeThemesTest {

    private static List<BiomeProfile> world() {
        List<BiomeProfile> all = new ArrayList<>(VanillaBiomes.all());
        // Snowy, and only its tags and temperature say so.
        all.add(BiomeProfile.of("terralith:alpine_grove", Set.of("is_snowy", "is_taiga"), -0.2,
                0.8, Habitat.OVERWORLD));
        all.add(BiomeProfile.of("terralith:sakura_grove", Set.of("is_forest"), 0.5, 0.8,
                Habitat.OVERWORLD));
        all.add(BiomeProfile.of("terralith:orchid_swamp", Set.of("is_swamp"), 0.8, 0.9,
                Habitat.OVERWORLD));
        all.add(BiomeProfile.of("terralith:ancient_sands", Set.of("is_desert"), 2.0, 0.0,
                Habitat.OVERWORLD));
        all.add(BiomeProfile.of("terralith:cave/frostfire_caves", Set.of("is_cave"), 0.8, 0.4,
                Habitat.OVERWORLD));
        return all;
    }

    private static List<String> placed(String mob) {
        return placed(mob, Habitat.OVERWORLD);
    }

    private static List<String> placed(String mob, Habitat habitat) {
        return BiomeThemes.assign(List.of(new Candidate(mob, "", habitat)), world(), Map.of())
                .byMob().get(mob).biomes();
    }

    @Test
    @DisplayName("an ice mob goes to every cold biome, including one only its tags call snowy")
    void iceMobGoesToTheCold() {
        List<String> biomes = placed("frostmite");
        assertTrue(biomes.contains("minecraft:snowy_plains"));
        assertTrue(biomes.contains("minecraft:ice_spikes"));
        assertTrue(biomes.contains("terralith:alpine_grove"), biomes.toString());
        assertFalse(biomes.contains("minecraft:desert"));
        assertFalse(biomes.contains("minecraft:plains"));
    }

    @Test
    @DisplayName("a sakura ent goes to the cherry groves, not to every forest")
    void specificWoodBeatsForest() {
        List<String> biomes = placed("sakura_tree_ent");
        assertTrue(biomes.contains("minecraft:cherry_grove"));
        assertTrue(biomes.contains("terralith:sakura_grove"));
        assertFalse(biomes.contains("minecraft:forest"));
        assertFalse(biomes.contains("minecraft:birch_forest"));
    }

    @Test
    @DisplayName("a dark oak ent goes to the dark forest, from the two words together")
    void wordPairs() {
        assertEquals(List.of("minecraft:dark_forest"), placed("dark_oak_tree_ent").stream()
                .filter(key -> key.startsWith("minecraft:")).toList());
    }

    @Test
    @DisplayName("a swamp mob goes to swamps, including a datapack's tagged as one")
    void swamp() {
        List<String> biomes = placed("swamp_hag");
        assertTrue(biomes.containsAll(List.of("minecraft:swamp", "minecraft:mangrove_swamp",
                "terralith:orchid_swamp")), biomes.toString());
        assertFalse(biomes.contains("minecraft:forest"));
    }

    @Test
    @DisplayName("a desert mob finds a desert whose name does not say desert")
    void desertByTag() {
        assertTrue(placed("desert_mummy").contains("terralith:ancient_sands"));
    }

    @Test
    @DisplayName("a mob whose name names no place stays everywhere")
    void genericStaysEverywhere() {
        assertTrue(placed("am_goblin_melee").isEmpty());
        assertTrue(placed("skeleton_archer").isEmpty());
        assertTrue(placed("spider_poison").isEmpty());
    }

    @Test
    @DisplayName("a Nether mob is only matched against the Nether")
    void staysInItsDimension() {
        assertEquals(List.of("minecraft:crimson_forest"), placed("crimson_brute", Habitat.NETHER));
        // An overworld mob called crimson has no crimson biome of its own to go to.
        assertFalse(placed("crimson_brute", Habitat.OVERWORLD).contains("minecraft:crimson_forest"));
    }

    @Test
    @DisplayName("cave mobs go to the cave biomes, a datapack's included")
    void caves() {
        List<String> biomes = placed("cave_crawler");
        assertTrue(biomes.contains("minecraft:dripstone_caves"));
        assertTrue(biomes.contains("terralith:cave/frostfire_caves"));
        assertFalse(biomes.contains("minecraft:plains"));
    }

    @Test
    @DisplayName("an override wins, and ANY puts a mob back everywhere")
    void overrides() {
        Map<String, List<String>> overrides = Map.of(
                "YETI", List.of("ice_spikes", "terralith:alpine_grove"),
                "frostmite", List.of("ANY"));
        BiomeThemes.Result result = BiomeThemes.assign(List.of(
                new Candidate("yeti", "", Habitat.OVERWORLD),
                new Candidate("frostmite", "", Habitat.OVERWORLD)), world(), overrides);
        assertEquals(List.of("minecraft:ice_spikes", "terralith:alpine_grove"),
                result.byMob().get("yeti").biomes());
        assertTrue(result.byMob().get("frostmite").biomes().isEmpty());
        assertEquals(Set.of("yeti"), result.themed());
    }

    @Test
    @DisplayName("the biome index lists each placed mob under each of its biomes")
    void byBiome() {
        BiomeThemes.Result result = BiomeThemes.assign(List.of(
                new Candidate("yeti", "", Habitat.OVERWORLD),
                new Candidate("frostmite", "", Habitat.OVERWORLD),
                new Candidate("am_goblin_melee", "", Habitat.OVERWORLD)), world(), Map.of());
        assertEquals(List.of("yeti", "frostmite"), result.byBiome().get("minecraft:ice_spikes"));
        assertFalse(result.byBiome().values().stream().anyMatch(l -> l.contains("am_goblin_melee")));
    }
}
