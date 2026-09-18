package com.arkcronist.gen.bukkit;

import com.arkcronist.gen.bukkit.mobs.BiomeMobs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Choosing mobs by where they are rather than by what they replace.
 *
 * <p>The failure that matters is the quiet one: a biome that should answer does not, the entity
 * table answers instead, and a yeti turns up in a desert. The table is keyed in upper case by the
 * config reader, and a biome key arrives namespaced and lower case, so the two only meet if this
 * gets the conversion right.</p>
 */
class BiomeMobsTest {

    private static final Map<String, List<String>> TABLE = Map.of(
            "SNOWY_PLAINS", List.of("frostmite", "iceologer"),
            "MINECRAFT:ICE_SPIKES", List.of("yeti"),
            "TERRALITH:ALPINE_GROVE", List.of("gnut"));

    @Test
    @DisplayName("a biome written without its namespace still matches")
    void matchesBarePath() {
        assertEquals(List.of("frostmite", "iceologer"),
                BiomeMobs.candidates(TABLE, "minecraft:snowy_plains"));
    }

    @Test
    @DisplayName("a biome written with its namespace matches too")
    void matchesFullKey() {
        assertEquals(List.of("yeti"), BiomeMobs.candidates(TABLE, "minecraft:ice_spikes"));
    }

    @Test
    @DisplayName("a datapack's own biome matches by its full key")
    void matchesDatapackBiome() {
        // Terralith adds 136 biomes of its own. Only the full key can name one, because the bare
        // path may well collide with something vanilla.
        assertEquals(List.of("gnut"), BiomeMobs.candidates(TABLE, "terralith:alpine_grove"));
    }

    @Test
    @DisplayName("a biome nobody named answers nothing, so the entity table gets its turn")
    void unnamedBiomeFallsThrough() {
        assertNull(BiomeMobs.candidates(TABLE, "minecraft:desert"));
        assertNull(BiomeMobs.candidates(TABLE, "terralith:volcanic_crater"));
    }

    @Test
    @DisplayName("no table, no key and an empty list all mean 'not my business'")
    void nothingConfigured() {
        assertNull(BiomeMobs.candidates(Map.of(), "minecraft:snowy_plains"));
        assertNull(BiomeMobs.candidates(TABLE, null));
        assertNull(BiomeMobs.candidates(TABLE, ""));
        // An entry left empty by half an edit must not win and then spawn nothing.
        assertNull(BiomeMobs.candidates(Map.of("SNOWY_PLAINS", List.of()), "minecraft:snowy_plains"));
    }
}
