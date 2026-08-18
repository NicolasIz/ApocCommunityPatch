package com.arkcronist.gen.core.terrain;

/**
 * Everything the pipeline knows about one column of the world.
 *
 * <p>Instances are recycled (one per thread, or one per grid cell inside a chunk) so sampling a
 * million columns does not allocate a million objects.</p>
 */
public final class ColumnData {

    /** Final surface height in blocks (may be fractional before rounding). */
    public double height;
    /** Water surface for this column: sea level normally, higher inside a mountain lake. */
    public double waterLevel;

    /** Very low frequency landmass field; negative is ocean, positive is inland. */
    public double continentalness;
    /** 0 = open ocean, 1 = fully inland. The coast lives in between. */
    public double land;
    /** 0 at the shoreline, 1 in the deepest abyssal water. */
    public double oceanT;

    /** 0 = no river here, 1 = river channel centre. */
    public double riverStrength;
    /** 0 = no lake here, 1 = lake centre. */
    public double lakeStrength;

    /** How mountainous the column is, before erosion. Drives biome choice and decoration. */
    public double mountainFactor;
    public double plateauFactor;
    public double canyonFactor;
    /** Simulated weathering strength, high in old flat lands, low on young sharp ranges. */
    public double erosion;

    public double temperature;
    public double humidity;
    public double weirdness;

    /** Assigned by the biome layer. */
    public int biomeId = -1;

    public void reset(int seaLevel) {
        height = seaLevel;
        waterLevel = seaLevel;
        continentalness = 0.0;
        land = 0.0;
        oceanT = 0.0;
        riverStrength = 0.0;
        lakeStrength = 0.0;
        mountainFactor = 0.0;
        plateauFactor = 0.0;
        canyonFactor = 0.0;
        erosion = 0.5;
        temperature = 0.0;
        humidity = 0.0;
        weirdness = 0.0;
        biomeId = -1;
    }

    public void copyFrom(ColumnData other) {
        height = other.height;
        waterLevel = other.waterLevel;
        continentalness = other.continentalness;
        land = other.land;
        oceanT = other.oceanT;
        riverStrength = other.riverStrength;
        lakeStrength = other.lakeStrength;
        mountainFactor = other.mountainFactor;
        plateauFactor = other.plateauFactor;
        canyonFactor = other.canyonFactor;
        erosion = other.erosion;
        temperature = other.temperature;
        humidity = other.humidity;
        weirdness = other.weirdness;
        biomeId = other.biomeId;
    }

    public boolean underwater() {
        return height < waterLevel;
    }

    public double depthBelowWater() {
        return waterLevel - height;
    }
}
