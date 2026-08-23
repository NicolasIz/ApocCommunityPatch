package com.arkcronist.gen.core;

import com.arkcronist.gen.core.terrain.Preset;
import com.arkcronist.gen.core.terrain.TerrainEngine;
import com.arkcronist.gen.core.terrain.TerrainSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Water has one surface, and it is where the sea is.
 *
 * <p>A lake used to carry its own level, taken from the broad relief at the middle of its basin, and
 * nothing capped how high that could be - real water was generating on mountainsides at Y=110
 * against a sea level of 63. Worse, a lake's level and the sea's are two different numbers, and the
 * coarse grid blended between them: the columns in between were handed 64, 65, 66, 67, 68, so a
 * hillside carried sheets of water at every height in between and the height changed from chunk to
 * chunk.</p>
 */
class WaterLevelTest {

    @ParameterizedTest
    @EnumSource(Preset.class)
    @DisplayName("every water surface in the world sits at exactly sea level")
    void allWaterIsAtSeaLevel(Preset preset) {
        TerrainSettings settings = TerrainSettings.forPreset(preset);
        TerrainEngine engine = new TerrainEngine(20260822L, preset, settings, 2048);

        TreeSet<Integer> levels = new TreeSet<>();
        long wet = 0;
        // Coarse but very wide: elevated lakes were nowhere near the origin, and a sample that only
        // looked at spawn is exactly how this survived as long as it did.
        for (int z = -3000; z < 3000; z += 13) {
            for (int x = -3000; x < 3000; x += 13) {
                int water = engine.waterLevel(x, z);
                if (engine.heightmapHeight(x, z) >= water) {
                    continue;
                }
                wet++;
                levels.add(water);
            }
        }

        assertTrue(wet > 5000, preset + ": not enough water sampled, got " + wet);
        assertEquals(1, levels.size(),
                preset + ": water generates at more than one level - " + levels);
        assertEquals(settings.seaLevel, levels.first(),
                preset + ": water is not at sea level");
    }

    @ParameterizedTest
    @EnumSource(Preset.class)
    @DisplayName("neighbouring wet columns always share their surface")
    void waterSurfacesAreLevel(Preset preset) {
        TerrainEngine engine = new TerrainEngine(20260822L, preset, TerrainSettings.forPreset(preset), 8192);
        long pairs = 0;
        long stepped = 0;
        for (int z = -300; z < 300; z += 2) {
            for (int x = -300; x < 300; x++) {
                int water = engine.waterLevel(x, z);
                int next = engine.waterLevel(x + 1, z);
                if (engine.heightmapHeight(x, z) >= water || engine.heightmapHeight(x + 1, z) >= next) {
                    continue;
                }
                pairs++;
                if (water != next) {
                    stepped++;
                }
            }
        }
        assertTrue(pairs > 10000, preset + ": not enough water sampled, got " + pairs);
        assertEquals(0, stepped, preset + ": " + stepped + " of " + pairs
                + " neighbouring water columns sit at different heights");
    }

    @ParameterizedTest
    @EnumSource(Preset.class)
    @DisplayName("with per-lake levels turned back on, no lake generates above its cap")
    void lakesRespectTheirAltitudeCap(Preset preset) {
        // The opt-out path still has to keep its promise. The cap is a hard cut, not a fade: a fade
        // never reaches zero, and a lake at six percent strength is still full of water - which is
        // how one turned up at Y=97 under a cap meant to stop it.
        TerrainSettings settings = TerrainSettings.forPreset(preset);
        settings.waterAtSeaLevel = false;
        TerrainEngine engine = new TerrainEngine(20260822L, preset, settings, 2048);

        int ceiling = (int) (settings.seaLevel + settings.lakeAltitudeFadeEnd);
        int highest = settings.seaLevel;
        for (int z = -3000; z < 3000; z += 13) {
            for (int x = -3000; x < 3000; x += 13) {
                int water = engine.waterLevel(x, z);
                if (engine.heightmapHeight(x, z) < water) {
                    highest = Math.max(highest, water);
                }
            }
        }
        assertTrue(highest <= ceiling,
                preset + ": a lake generated at Y=" + highest + ", above its cap of " + ceiling);
    }
}
