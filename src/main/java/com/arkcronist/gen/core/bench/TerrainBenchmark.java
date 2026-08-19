package com.arkcronist.gen.core.bench;

import com.arkcronist.gen.core.decorate.DecorationPlacer;
import com.arkcronist.gen.core.decorate.FeaturePlacer;
import com.arkcronist.gen.core.prefab.PrefabRegistry;
import com.arkcronist.gen.core.structure.LootMarker;
import com.arkcronist.gen.core.structure.MobSpawn;
import com.arkcronist.gen.core.structure.SpawnerMarker;
import com.arkcronist.gen.core.structure.StructurePlacer;
import com.arkcronist.gen.core.terrain.ChunkTerrain;
import com.arkcronist.gen.core.terrain.Preset;
import com.arkcronist.gen.core.terrain.TerrainEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Measures the generator the way the server actually uses it.
 *
 * <p>Runs each stage separately - heightfield, blocks, decoration and features, structures - so a
 * regression can be attributed to a stage instead of to "generation got slower". Runnable both from
 * the command line and in-game through {@code /ag bench}.</p>
 */
public final class TerrainBenchmark {

    /** Timings in milliseconds per chunk, plus throughput. */
    public record Result(Preset preset, int chunks, double heightMs, double blocksMs, double decorationMs,
                         double structureMs, double totalMs, long blocksWritten, double cacheHitRate) {

        public double chunksPerSecond() {
            return totalMs <= 0.0 ? 0.0 : 1000.0 / totalMs;
        }

        public String describe() {
            return String.format(Locale.ROOT,
                    "%s: %.3f ms/chunk total (height %.3f, blocks %.3f, features %.3f, structures %.3f), "
                            + "%.0f chunks/s/thread, %d blocks written, cache hit rate %.0f%%",
                    preset, totalMs, heightMs, blocksMs, decorationMs, structureMs,
                    chunksPerSecond(), blocksWritten, cacheHitRate * 100.0);
        }
    }

    private TerrainBenchmark() {
    }

    public static Result run(long seed, Preset preset, int chunks, int originX, int originZ) {
        return run(seed, preset, chunks, originX, originZ, PrefabRegistry.discover());
    }

    /** Same benchmark against a specific prefab set, so the server can measure what it actually has. */
    public static Result run(long seed, Preset preset, int chunks, int originX, int originZ,
                             PrefabRegistry prefabs) {
        TerrainEngine engine = new TerrainEngine(seed, preset);
        FeaturePlacer features = new FeaturePlacer(engine, prefabs);
        DecorationPlacer decoration = new DecorationPlacer(engine);
        StructurePlacer structures = new StructurePlacer(engine, java.util.Set.of(), prefabs);
        int minY = engine.settings().minY;
        int maxY = engine.settings().maxY;

        int side = (int) Math.ceil(Math.sqrt(chunks));
        // Warm up so the measurement reflects steady state rather than JIT compilation.
        for (int i = 0; i < Math.min(16, chunks); i++) {
            engine.generateChunk(originX + i, originZ, new CountingWriter(minY, maxY));
        }
        engine.cache().clear();

        long heightNanos = 0;
        long blockNanos = 0;
        long decorationNanos = 0;
        long structureNanos = 0;
        long blocksWritten = 0;
        int generated = 0;

        for (int cx = 0; cx < side && generated < chunks; cx++) {
            for (int cz = 0; cz < side && generated < chunks; cz++) {
                int chunkX = originX + cx;
                int chunkZ = originZ + cz;

                long t0 = System.nanoTime();
                ChunkTerrain terrain = engine.terrain(chunkX, chunkZ);
                long t1 = System.nanoTime();

                CountingWriter blockWriter = new CountingWriter(minY, maxY, chunkX, chunkZ);
                engine.generateChunk(chunkX, chunkZ, blockWriter);
                long t2 = System.nanoTime();

                CountingWriter featureWriter = new CountingWriter(minY, maxY, chunkX, chunkZ);
                decoration.decorate(chunkX, chunkZ, featureWriter);
                int radius = features.chunkRadius();
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        features.place(chunkX + dx, chunkZ + dz, featureWriter);
                    }
                }
                long t3 = System.nanoTime();

                List<MobSpawn> spawns = new ArrayList<>();
                List<LootMarker> loot = new ArrayList<>();
                List<SpawnerMarker> spawners = new ArrayList<>();
                CountingWriter structureWriter = new CountingWriter(minY, maxY, chunkX, chunkZ);
                structures.placeInto(chunkX, chunkZ, structureWriter, spawns, loot, spawners);
                long t4 = System.nanoTime();

                heightNanos += t1 - t0;
                blockNanos += t2 - t1;
                decorationNanos += t3 - t2;
                structureNanos += t4 - t3;
                blocksWritten += blockWriter.blocks() + featureWriter.blocks() + structureWriter.blocks();
                generated++;
                if (terrain == null) {
                    throw new IllegalStateException("terrain was not built");
                }
            }
        }

        double divisor = Math.max(1, generated) * 1_000_000.0;
        double height = heightNanos / divisor;
        double blocks = blockNanos / divisor;
        double decorate = decorationNanos / divisor;
        double structure = structureNanos / divisor;
        return new Result(preset, generated, height, blocks, decorate, structure,
                height + blocks + decorate + structure, blocksWritten, engine.cache().hitRate());
    }

    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 1234567890L;
        int chunks = args.length > 1 ? Integer.parseInt(args[1]) : 256;
        System.out.println("ArkcronistGenerator benchmark - seed " + seed + ", " + chunks + " chunks per preset");
        for (Preset preset : Preset.values()) {
            Result result = run(seed, preset, chunks, 0, 0);
            System.out.println("  " + result.describe());
        }
    }
}
