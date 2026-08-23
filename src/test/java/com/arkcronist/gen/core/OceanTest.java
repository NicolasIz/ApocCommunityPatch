package com.arkcronist.gen.core;

import com.arkcronist.gen.core.biome.ArkBiome;
import com.arkcronist.gen.core.terrain.ColumnData;
import com.arkcronist.gen.core.terrain.Preset;
import com.arkcronist.gen.core.terrain.TerrainEngine;
import com.arkcronist.gen.core.terrain.TerrainSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Oceans must be real oceans: a shelf, a slope, a deep plain, and trenches deeper still. */
class OceanTest {

    private static final int SAMPLES = 30000;

    @ParameterizedTest
    @EnumSource(Preset.class)
    @DisplayName("the sea floor deepens as the ocean gradient grows")
    void depthFollowsTheProfile(Preset preset) {
        TerrainEngine engine = new TerrainEngine(112233L, preset);
        TerrainSettings settings = engine.settings();
        ColumnData column = new ColumnData();

        double shelfDepthSum = 0.0;
        int shelfCount = 0;
        double abyssDepthSum = 0.0;
        int abyssCount = 0;
        double deepest = 0.0;

        for (int i = 0; i < SAMPLES; i++) {
            int x = (i * 149) % 40000 - 20000;
            int z = (i * 331) % 40000 - 20000;
            engine.sampler().sample(x, z, column);
            if (column.land > 0.2) {
                continue;
            }
            double depth = settings.seaLevel - column.height;
            deepest = Math.max(deepest, depth);
            if (column.oceanT < settings.shelfExtent * 0.8) {
                shelfDepthSum += depth;
                shelfCount++;
            } else if (column.oceanT > 0.85) {
                abyssDepthSum += depth;
                abyssCount++;
            }
        }

        assertTrue(shelfCount > 50, "no shelf columns sampled");
        assertTrue(abyssCount > 50, "no abyssal columns sampled");
        double shelfAverage = shelfDepthSum / shelfCount;
        double abyssAverage = abyssDepthSum / abyssCount;

        assertTrue(shelfAverage < abyssAverage * 0.5,
                preset + ": the shelf (" + shelfAverage + ") should be far shallower than the abyss ("
                        + abyssAverage + ")");
        assertTrue(abyssAverage > 40.0, preset + ": deep ocean averages only " + abyssAverage + " blocks");
        // A range, not a floor, and the upper end is the point of it.
        //
        // This used to demand more than seventy blocks of depth, from when the sea was meant to be
        // as deep as it could get. It is not any more: the deep was reaching Y=-25, which is a long
        // swim back up and leaves almost no rock between the sea floor and the caves under it. What
        // is wanted now is a sea with a real deep in it that still stops well short of the deep
        // itself, so both ends are held: below forty-five there is no deep ocean worth the name,
        // and above seventy-eight the floor is back down where it was.
        assertTrue(deepest > 45.0, preset + ": the sea has no deep in it - deepest point is only "
                + deepest + " blocks down");
        assertTrue(deepest < 78.0, preset + ": the sea is back to being a pit - deepest point is "
                + deepest + " blocks down");
    }

    @Test
    @DisplayName("each preset goes deeper than the one before it")
    void presetsEscalate() {
        double baseDeepest = deepest(Preset.BASE);
        double chaoticDeepest = deepest(Preset.CHAOTIC);
        double insaneDeepest = deepest(Preset.INSANE);
        assertTrue(chaoticDeepest > baseDeepest,
                "CHAOTIC (" + chaoticDeepest + ") should beat BASE (" + baseDeepest + ")");
        assertTrue(insaneDeepest > chaoticDeepest,
                "INSANE (" + insaneDeepest + ") should beat CHAOTIC (" + chaoticDeepest + ")");
    }

    private double deepest(Preset preset) {
        TerrainEngine engine = new TerrainEngine(778899L, preset);
        ColumnData column = new ColumnData();
        double deepest = 0.0;
        for (int i = 0; i < SAMPLES; i++) {
            int x = (i * 173) % 60000 - 30000;
            int z = (i * 421) % 60000 - 30000;
            engine.sampler().sample(x, z, column);
            deepest = Math.max(deepest, engine.settings().seaLevel - column.height);
        }
        return deepest;
    }

    @ParameterizedTest
    @EnumSource(Preset.class)
    @DisplayName("ocean columns select oceanic biomes, land columns do not")
    void biomesMatchTheWater(Preset preset) {
        TerrainEngine engine = new TerrainEngine(5150L, preset);
        ColumnData column = new ColumnData();
        int oceanChecked = 0;
        int oceanCorrect = 0;
        int landChecked = 0;
        int landCorrect = 0;

        for (int i = 0; i < 6000; i++) {
            int x = (i * 271) % 30000 - 15000;
            int z = (i * 617) % 30000 - 15000;
            engine.sampler().sample(x, z, column);
            ArkBiome biome = engine.biomes().byId(engine.selector().select(column));
            if (column.oceanT > 0.6 && column.land < 0.05) {
                oceanChecked++;
                if (biome.oceanic()) {
                    oceanCorrect++;
                }
            } else if (column.land > 0.95 && column.height > engine.settings().seaLevel + 10) {
                landChecked++;
                if (!biome.oceanic()) {
                    landCorrect++;
                }
            }
        }
        assertTrue(oceanChecked > 20 && oceanCorrect >= oceanChecked * 0.95,
                preset + ": deep water picked land biomes (" + oceanCorrect + "/" + oceanChecked + ")");
        assertTrue(landChecked > 20 && landCorrect >= landChecked * 0.95,
                preset + ": dry land picked ocean biomes (" + landCorrect + "/" + landChecked + ")");
    }
}
