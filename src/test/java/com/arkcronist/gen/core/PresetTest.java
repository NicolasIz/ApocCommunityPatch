package com.arkcronist.gen.core;

import com.arkcronist.gen.core.biome.ArkBiome;
import com.arkcronist.gen.core.biome.BiomeRegistry;
import com.arkcronist.gen.core.terrain.ColumnData;
import com.arkcronist.gen.core.terrain.Preset;
import com.arkcronist.gen.core.terrain.TerrainEngine;
import com.arkcronist.gen.core.terrain.TerrainSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The three presets must be genuinely different worlds, not the same world at three volumes. */
class PresetTest {

    private record Profile(double highest, double lowest, double variance, double landShare) {
    }

    private Profile profile(Preset preset) {
        TerrainEngine engine = new TerrainEngine(20250101L, preset);
        ColumnData column = new ColumnData();
        double highest = -Double.MAX_VALUE;
        double lowest = Double.MAX_VALUE;
        double sum = 0.0;
        double sumSquares = 0.0;
        int land = 0;
        int samples = 20000;
        for (int i = 0; i < samples; i++) {
            int x = (i * 211) % 50000 - 25000;
            int z = (i * 487) % 50000 - 25000;
            engine.sampler().sample(x, z, column);
            highest = Math.max(highest, column.height);
            lowest = Math.min(lowest, column.height);
            sum += column.height;
            sumSquares += column.height * column.height;
            if (column.height > engine.settings().seaLevel) {
                land++;
            }
        }
        double mean = sum / samples;
        return new Profile(highest, lowest, sumSquares / samples - mean * mean, land / (double) samples);
    }

    @Test
    @DisplayName("relief escalates from BASE to CHAOTIC to INSANE")
    void reliefEscalates() {
        Profile base = profile(Preset.BASE);
        Profile chaotic = profile(Preset.CHAOTIC);
        Profile insane = profile(Preset.INSANE);

        assertTrue(chaotic.highest() > base.highest() + 20,
                "CHAOTIC peaks (" + chaotic.highest() + ") should tower over BASE (" + base.highest() + ")");
        assertTrue(insane.highest() > chaotic.highest() + 20,
                "INSANE peaks (" + insane.highest() + ") should tower over CHAOTIC (" + chaotic.highest() + ")");
        assertTrue(chaotic.variance() > base.variance(), "CHAOTIC should be rougher than BASE");
        assertTrue(insane.variance() > chaotic.variance(), "INSANE should be rougher than CHAOTIC");
    }

    @Test
    @DisplayName("every preset produces a mix of land and sea")
    void everyPresetIsPlayable() {
        for (Preset preset : Preset.values()) {
            Profile profile = profile(preset);
            assertTrue(profile.landShare() > 0.25 && profile.landShare() < 0.75,
                    preset + ": land share is " + profile.landShare() + ", the world is lopsided");
        }
    }

    @Test
    @DisplayName("presets switch terrain systems on, not just amplitudes")
    void presetsEnableSystems() {
        TerrainSettings base = TerrainSettings.forPreset(Preset.BASE);
        TerrainSettings chaotic = TerrainSettings.forPreset(Preset.CHAOTIC);
        TerrainSettings insane = TerrainSettings.forPreset(Preset.INSANE);

        assertEquals(0.0, base.floatingIslandDensity, "BASE should have no floating islands");
        assertTrue(chaotic.floatingIslandDensity > 0.0, "CHAOTIC should have floating islands");
        assertTrue(insane.floatingIslandDensity > chaotic.floatingIslandDensity);

        assertEquals(0.0, base.archStrength, "BASE should have no arches");
        assertTrue(insane.archStrength > chaotic.archStrength, "INSANE should carve the most arches");
        assertTrue(insane.canyonStrength > chaotic.canyonStrength);
        assertTrue(chaotic.canyonStrength > base.canyonStrength);
        // Mega caves are gone entirely; INSANE now shows its hand through cave size instead.
        assertTrue(insane.caveCheeseThreshold < base.caveCheeseThreshold);
        assertTrue(insane.cavernDensity > base.cavernDensity);
        assertTrue(insane.biomeFragmentation > chaotic.biomeFragmentation);
        assertTrue(chaotic.biomeFragmentation > base.biomeFragmentation);
    }

    @Test
    @DisplayName("every preset uses a wide range of biomes")
    void biomeVariety() {
        for (Preset preset : Preset.values()) {
            TerrainEngine engine = new TerrainEngine(5309L, preset);
            ColumnData column = new ColumnData();
            Set<Integer> seen = new HashSet<>();
            for (int i = 0; i < 20000; i++) {
                int x = (i * 163) % 40000 - 20000;
                int z = (i * 379) % 40000 - 20000;
                engine.sampler().sample(x, z, column);
                seen.add(engine.selector().select(column));
            }
            assertTrue(seen.size() >= 15,
                    preset + ": only " + seen.size() + " biomes appear in the sampled area");
        }
    }

    @Test
    @DisplayName("preset names parse leniently")
    void presetParsing() {
        assertEquals(Preset.INSANE, Preset.parse("insane"));
        assertEquals(Preset.CHAOTIC, Preset.parse(" Chaotic "));
        assertEquals(Preset.BASE, Preset.parse(null));
        assertEquals(Preset.BASE, Preset.parse("nonsense"));
        assertTrue(Preset.isKnown("BASE"));
        assertFalse(Preset.isKnown("nonsense"));
    }

    @Test
    @DisplayName("the biome table is internally consistent")
    void biomeTableIsSane() {
        BiomeRegistry registry = new BiomeRegistry();
        assertTrue(registry.size() >= 30, "expected a rich biome table, found " + registry.size());
        Set<String> names = new HashSet<>();
        for (ArkBiome biome : registry.all()) {
            assertTrue(names.add(biome.name), "duplicate biome name: " + biome.name);
            assertEquals(biome, registry.byId(biome.id), "biome id lookup is inconsistent");
            assertTrue(biome.vanillaKey.startsWith("minecraft:"), "bad vanilla key: " + biome.vanillaKey);
            assertTrue(biome.surfaceDepth > 0);
            assertNotNull(biome.surface);
            assertNotNull(biome.stone);
            assertEquals(biome.trees.length, biome.treeWeights.length);
        }
    }
}
