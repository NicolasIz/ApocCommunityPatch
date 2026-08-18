package com.arkcronist.gen.core;

import com.arkcronist.gen.core.bench.TerrainBenchmark;
import com.arkcronist.gen.core.block.BlockRegistry;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.block.Palette;
import com.arkcronist.gen.core.terrain.Preset;
import com.arkcronist.gen.core.terrain.TerrainCache;
import com.arkcronist.gen.core.terrain.TerrainEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CacheAndBenchmarkTest {

    @Test
    @DisplayName("the terrain cache stays bounded and still serves hits")
    void cacheIsBounded() {
        TerrainEngine engine = new TerrainEngine(11L, Preset.BASE, 
                com.arkcronist.gen.core.terrain.TerrainSettings.forPreset(Preset.BASE), 64);
        TerrainCache cache = engine.cache();
        for (int i = 0; i < 400; i++) {
            engine.terrain(i, 0);
        }
        assertTrue(cache.size() <= 128, "cache grew past its limit: " + cache.size());
        engine.terrain(399, 0);
        assertTrue(cache.hits() > 0, "cache never served a hit");
        cache.clear();
        assertEquals(0, cache.size());
    }

    @Test
    @DisplayName("repeated lookups of the same chunk are served from cache")
    void cacheHitsDominate() {
        TerrainEngine engine = new TerrainEngine(12L, Preset.BASE);
        for (int i = 0; i < 200; i++) {
            engine.terrain(3, 4);
        }
        assertTrue(engine.cache().hitRate() > 0.9,
                "hit rate should be near 1, was " + engine.cache().hitRate());
    }

    @Test
    @DisplayName("the benchmark runs and reports sane numbers")
    void benchmarkRuns() {
        TerrainBenchmark.Result result = TerrainBenchmark.run(99L, Preset.BASE, 16, 0, 0);
        assertEquals(16, result.chunks());
        assertTrue(result.totalMs() > 0.0);
        assertTrue(result.chunksPerSecond() > 0.0);
        assertTrue(result.blocksWritten() > 10000, "benchmark wrote almost nothing");
        assertNotNull(result.describe());
    }

    @Test
    @DisplayName("block ids are dense, unique and stable")
    void blockRegistry() {
        BlockRegistry registry = new BlockRegistry();
        int stone = registry.id("minecraft:stone");
        int dirt = registry.id("minecraft:dirt");
        assertEquals(stone, registry.id("minecraft:stone"));
        assertNotEquals(stone, dirt);
        assertEquals("minecraft:stone", registry.key(stone));
        assertEquals(2, registry.size());

        Set<Integer> ids = new HashSet<>();
        for (String key : Blocks.REGISTRY.keys()) {
            int id = Blocks.REGISTRY.id(key);
            assertTrue(ids.add(id), "duplicate id for " + key);
            assertTrue(key.startsWith("minecraft:"), "block key without a namespace: " + key);
        }
        assertTrue(Blocks.REGISTRY.size() > 150, "expected a broad block palette");
        assertTrue(Blocks.isAir(Blocks.AIR));
        assertTrue(Blocks.isLiquid(Blocks.WATER));
        assertFalse(Blocks.isLiquid(Blocks.STONE));
    }

    @Test
    @DisplayName("palettes respect their weights")
    void palettes() {
        Palette palette = Palette.of(Blocks.STONE, 9.0, Blocks.GRAVEL, 1.0);
        int stone = 0;
        for (int i = 0; i < 1000; i++) {
            if (palette.pick(i / 1000.0) == Blocks.STONE) {
                stone++;
            }
        }
        assertTrue(stone > 850 && stone < 950, "weighting is off: " + stone + "/1000");
        assertEquals(Blocks.STONE, palette.first());
        assertEquals(2, palette.size());
        assertEquals(Blocks.DIRT, Palette.single(Blocks.DIRT).pick(0.99));
    }
}
