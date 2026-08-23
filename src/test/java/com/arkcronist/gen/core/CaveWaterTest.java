package com.arkcronist.gen.core;

import com.arkcronist.gen.core.cave.AquiferSampler;
import com.arkcronist.gen.core.terrain.Preset;
import com.arkcronist.gen.core.terrain.TerrainEngine;
import com.arkcronist.gen.core.terrain.TerrainSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The underground has to be mostly dry.
 *
 * <p>Aquifers used to be unconditional: 39% of columns carried a water table, the mean sat at Y=-5
 * and they reached Y=55. That drowns the deep caves and everything the server places in them - an
 * ancient city occupies roughly Y=-51 to -20, and 38% of columns had water above its floor.</p>
 */
class CaveWaterTest {

    private record Survey(double wetShare, double aboveCityFloor, int highest) {
    }

    private static Survey survey(Preset preset, TerrainSettings settings) {
        AquiferSampler aquifer = new AquiferSampler(20260822L, settings);
        TerrainEngine engine = new TerrainEngine(20260822L, preset, settings, 2048);
        long columns = 0;
        long wet = 0;
        long aboveCity = 0;
        int highest = Integer.MIN_VALUE;
        for (int z = -1500; z < 1500; z += 17) {
            for (int x = -1500; x < 1500; x += 17) {
                columns++;
                int table = aquifer.waterTable(x, z, engine.heightmapHeight(x, z));
                if (table == AquiferSampler.NO_WATER) {
                    continue;
                }
                wet++;
                highest = Math.max(highest, table);
                if (table > -51) {
                    aboveCity++;
                }
            }
        }
        return new Survey(wet / (double) columns, aboveCity / (double) columns, highest);
    }

    @ParameterizedTest
    @EnumSource(Preset.class)
    @DisplayName("only a small share of the underground is flooded, and only near the bedrock")
    void cavesAreMostlyDry(Preset preset) {
        Survey survey = survey(preset, TerrainSettings.forPreset(preset));

        assertTrue(survey.wetShare() < 0.05,
                preset + ": " + Math.round(survey.wetShare() * 100) + "% of the underground is flooded");
        assertTrue(survey.aboveCityFloor() < 0.05,
                preset + ": water stands over the ancient city band in "
                        + Math.round(survey.aboveCityFloor() * 100) + "% of columns");
        assertTrue(survey.highest() < 0,
                preset + ": a water table reached Y=" + survey.highest() + ", well above the deep caves");
    }

    @ParameterizedTest
    @EnumSource(Preset.class)
    @DisplayName("cave-water 0 means no underground water at all")
    void zeroMeansDry(Preset preset) {
        TerrainSettings settings = TerrainSettings.forPreset(preset);
        settings.caveWater = 0.0;
        AquiferSampler aquifer = new AquiferSampler(20260822L, settings);
        TerrainEngine engine = new TerrainEngine(20260822L, preset, settings, 2048);
        long wet = 0;
        for (int z = -800; z < 800; z += 17) {
            for (int x = -800; x < 800; x += 17) {
                if (aquifer.waterTable(x, z, engine.heightmapHeight(x, z)) != AquiferSampler.NO_WATER) {
                    wet++;
                }
            }
        }
        assertEquals(0, wet, preset + ": cave-water is 0 but " + wet + " columns still carry a table");
    }
}
