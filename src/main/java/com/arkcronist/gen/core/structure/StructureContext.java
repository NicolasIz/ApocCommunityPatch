package com.arkcronist.gen.core.structure;

import com.arkcronist.gen.core.biome.ArkBiome;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.terrain.Preset;
import com.arkcronist.gen.core.terrain.TerrainEngine;

/** Everything a structure needs to know about where it is being built. */
public final class StructureContext {

    public final TerrainEngine engine;
    public final int originX;
    public final int originZ;
    public final int groundY;
    public final int waterY;
    public final ArkBiome biome;
    public final FastRandom random;
    public final Preset preset;

    public StructureContext(TerrainEngine engine, int originX, int originZ, FastRandom random) {
        this.engine = engine;
        this.originX = originX;
        this.originZ = originZ;
        this.groundY = engine.surfaceHeight(originX, originZ);
        this.waterY = engine.waterLevel(originX, originZ);
        this.biome = engine.biomeAt(originX, originZ);
        this.random = random;
        this.preset = engine.preset();
    }

    public TerrainEngine engine() {
        return engine;
    }

    public int height(int x, int z) {
        return engine.surfaceHeight(x, z);
    }

    public int water(int x, int z) {
        return engine.waterLevel(x, z);
    }

    public boolean submerged(int x, int z) {
        return engine.surfaceHeight(x, z) < engine.waterLevel(x, z) - 1;
    }

    /** Largest height difference found on a ring around a position: the terrain's roughness. */
    public int relief(int x, int z, int radius) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4.0;
            int px = x + (int) Math.round(Math.cos(angle) * radius);
            int pz = z + (int) Math.round(Math.sin(angle) * radius);
            int h = height(px, pz);
            min = Math.min(min, h);
            max = Math.max(max, h);
        }
        return max - min;
    }

    /** Average ground height over the footprint, used to pick a sensible platform level. */
    public int averageHeight(int radius) {
        long sum = 0;
        int samples = 0;
        for (int dx = -radius; dx <= radius; dx += Math.max(1, radius / 3)) {
            for (int dz = -radius; dz <= radius; dz += Math.max(1, radius / 3)) {
                sum += height(originX + dx, originZ + dz);
                samples++;
            }
        }
        return (int) (sum / Math.max(1, samples));
    }

    public int seaLevel() {
        return engine.settings().seaLevel;
    }

    public int maxY() {
        return engine.settings().maxY;
    }
}
