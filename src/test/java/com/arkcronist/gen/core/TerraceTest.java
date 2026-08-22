package com.arkcronist.gen.core;

import com.arkcronist.gen.core.terrain.ChunkTerrain;
import com.arkcronist.gen.core.terrain.Preset;
import com.arkcronist.gen.core.terrain.TerrainEngine;
import com.arkcronist.gen.core.terrain.TerrainSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slopes must not generate as staircases.
 *
 * <p>Every height term is evaluated on a grid four blocks apart and interpolated, which leaves a
 * hillside locally straight - measured at 0.066 blocks of curvature against a gradient of 0.35. A
 * straight ramp of blocks is a flight of stairs, and that is what put the parallel terraces on every
 * slope in the world. {@code surfaceDetailAmplitude} adds roughness back at true block resolution.</p>
 */
class TerraceTest {

    private static double heightAt(TerrainEngine engine, int x, int z) {
        ChunkTerrain terrain = engine.terrain(x >> 4, z >> 4);
        return terrain.heightAt(x & 15, z & 15);
    }

    /**
     * Mean deviation of a column from the straight line through its two neighbours, over the
     * gradients where terracing actually shows: gentle enough to make wide treads, steep enough not
     * to be flat ground. Near zero means a dead straight ramp.
     */
    private static double straightness(TerrainEngine engine) {
        double sum = 0.0;
        long n = 0;
        for (int z = -200; z < 200; z += 2) {
            for (int x = -200; x < 200; x++) {
                double h = heightAt(engine, x, z);
                double before = heightAt(engine, x - 1, z);
                double after = heightAt(engine, x + 1, z);
                if (h <= 64 || before <= 64 || after <= 64) {
                    continue;
                }
                double gradient = Math.abs(after - before) / 2.0;
                if (gradient < 0.2 || gradient > 0.7) {
                    continue;
                }
                sum += Math.abs(h - (before + after) / 2.0);
                n++;
            }
        }
        assertTrue(n > 3000, "not enough sloped ground sampled: " + n);
        return sum / n;
    }

    @ParameterizedTest
    @EnumSource(Preset.class)
    @DisplayName("slopes carry block-scale detail instead of generating as straight ramps")
    void slopesAreNotStaircases(Preset preset) {
        TerrainSettings bare = TerrainSettings.forPreset(preset);
        bare.surfaceDetailAmplitude = 0.0;
        double withoutDetail = straightness(new TerrainEngine(20260822L, preset, bare, 4096));
        double withDetail = straightness(new TerrainEngine(20260822L, preset, TerrainSettings.forPreset(preset), 4096));

        // Interpolation alone lands around 0.066-0.078 on every preset; the detail term roughly
        // doubles it. Asserting the ratio rather than an absolute keeps this meaningful if the
        // underlying landform is ever retuned.
        assertTrue(withoutDetail < 0.10,
                preset + ": baseline should be a near straight ramp, measured " + withoutDetail);
        assertTrue(withDetail > withoutDetail * 1.6,
                preset + ": slopes are still straight ramps - " + withDetail
                        + " against a bare " + withoutDetail);
    }

    @ParameterizedTest
    @EnumSource(Preset.class)
    @DisplayName("open country stays flat enough to build on")
    void flatGroundSurvivesTheDetail(Preset preset) {
        // The other half of the bargain. Roughness is gated on slope precisely so the flatland the
        // presets carve out keeps its purpose: villages, castles and the open country the server
        // asked for need ground you can actually put a foundation on.
        TerrainSettings bare = TerrainSettings.forPreset(preset);
        bare.surfaceDetailAmplitude = 0.0;

        double before = flatShare(new TerrainEngine(20260822L, preset, bare, 4096));
        double after = flatShare(new TerrainEngine(20260822L, preset, TerrainSettings.forPreset(preset), 4096));

        assertTrue(after > before * 0.85,
                preset + ": the detail term ate the buildable ground - " + after + " against " + before);
    }

    private static double flatShare(TerrainEngine engine) {
        long land = 0;
        long flat = 0;
        for (int z = -160; z < 160; z += 2) {
            for (int x = -160; x < 160; x += 2) {
                int h = engine.heightmapHeight(x, z);
                if (h <= 64) {
                    continue;
                }
                land++;
                int low = h;
                int high = h;
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        int v = engine.heightmapHeight(x + dx, z + dz);
                        low = Math.min(low, v);
                        high = Math.max(high, v);
                    }
                }
                if (high - low <= 2) {
                    flat++;
                }
            }
        }
        return flat / (double) land;
    }
}
