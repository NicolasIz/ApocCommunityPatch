package com.arkcronist.gen.core.biome;

import com.arkcronist.gen.core.terrain.ColumnData;
import com.arkcronist.gen.core.terrain.TerrainSettings;

import java.util.List;

/**
 * Chooses a biome for a column.
 *
 * <p>Selection is a soft nearest match rather than a chain of if/else rules. Every biome states
 * where it likes to live (land factor, ocean gradient, mountain factor, altitude) and what climate
 * it wants; the column scores every candidate and takes the closest. Because the score is continuous
 * the borders move smoothly with the underlying fields, which is what produces natural transitions
 * instead of straight biome edges.</p>
 */
public final class BiomeSelector {

    private final ArkBiome[] candidates;
    private final ArkBiome floating;
    private final TerrainSettings settings;

    public BiomeSelector(BiomeRegistry registry, TerrainSettings settings) {
        this.settings = settings;
        List<ArkBiome> all = registry.all();
        ArkBiome sky = null;
        int count = 0;
        for (ArkBiome biome : all) {
            if (biome.category == BiomeCategory.FLOATING) {
                sky = biome;
            } else {
                count++;
            }
        }
        this.candidates = new ArkBiome[count];
        int index = 0;
        for (ArkBiome biome : all) {
            if (biome.category != BiomeCategory.FLOATING) {
                candidates[index++] = biome;
            }
        }
        this.floating = sky != null ? sky : all.get(0);
    }

    /** Biome used for the surface of floating islands. */
    public ArkBiome floatingBiome() {
        return floating;
    }

    public int select(ColumnData column) {
        double relativeHeight = column.height - settings.seaLevel;
        boolean isRiver = column.riverStrength > 0.5 && column.land > 0.45;
        boolean isLake = column.lakeStrength > 0.5 && column.land > 0.45;

        int best = 0;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < candidates.length; i++) {
            double score = score(candidates[i], column, relativeHeight, isRiver, isLake);
            if (score < bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return candidates[best].id;
    }

    private double score(ArkBiome biome, ColumnData column, double relativeHeight, boolean isRiver, boolean isLake) {
        double score = outside(column.land, biome.minLand, biome.maxLand) * 60.0;
        if (score > 40.0) {
            // Cheap early exit: a land biome in the deep sea can never win.
            return score;
        }
        if (biome.oceanic()) {
            score += outside(column.oceanT, biome.minOceanT, biome.maxOceanT) * 45.0;
        }
        score += outside(column.mountainFactor, biome.minMountain, biome.maxMountain) * 30.0;

        double heightPenalty = outsideRange(relativeHeight, biome.minHeight, biome.maxHeight) / 32.0;
        score += heightPenalty * heightPenalty * 6.0;

        double dt = column.temperature - biome.temperature;
        double dh = column.humidity - biome.humidity;
        double dw = column.weirdness - biome.weirdness;
        score += dt * dt * 1.35 + dh * dh + dw * dw * biome.weirdnessWeight;

        if (biome.river) {
            score += (isRiver || isLake) ? -2.0 : 9.0;
        } else if (isRiver) {
            score += 1.5;
        }
        return score;
    }

    /** 0 inside the range, growing with distance outside it. */
    private static double outside(double value, double min, double max) {
        if (value < min) {
            return min - value;
        }
        if (value > max) {
            return value - max;
        }
        return 0.0;
    }

    private static double outsideRange(double value, double min, double max) {
        if (value < min) {
            return min - value;
        }
        if (value > max) {
            return value - max;
        }
        return 0.0;
    }
}
