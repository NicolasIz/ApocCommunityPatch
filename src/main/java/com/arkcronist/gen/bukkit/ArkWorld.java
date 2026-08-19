package com.arkcronist.gen.bukkit;

import com.arkcronist.gen.bukkit.mobs.MobQueue;
import com.arkcronist.gen.core.decorate.DecorationPlacer;
import com.arkcronist.gen.core.decorate.FeaturePlacer;
import com.arkcronist.gen.core.prefab.PrefabRegistry;
import com.arkcronist.gen.core.structure.StructurePlacer;
import com.arkcronist.gen.core.terrain.Preset;
import com.arkcronist.gen.core.terrain.TerrainEngine;
import com.arkcronist.gen.core.terrain.TerrainSettings;

/** Everything ArkcronistGenerator keeps per world: one engine, one placer of each kind, one queue. */
public final class ArkWorld {

    private final String name;
    private final Preset preset;
    private final long seed;
    private final TerrainEngine engine;
    private final FeaturePlacer features;
    private final DecorationPlacer decoration;
    private final StructurePlacer structures;
    private final MobQueue mobQueue = new MobQueue();

    public ArkWorld(String name, long seed, Preset preset, TerrainSettings settings, int cacheSize) {
        this(name, seed, preset, settings, cacheSize, java.util.Set.of(), PrefabRegistry.empty());
    }

    public ArkWorld(String name, long seed, Preset preset, TerrainSettings settings, int cacheSize,
                    java.util.Set<String> disabledStructures, PrefabRegistry prefabs) {
        this.name = name;
        this.seed = seed;
        this.preset = preset;
        this.engine = new TerrainEngine(seed, preset, settings, cacheSize);
        this.features = new FeaturePlacer(engine, prefabs);
        this.decoration = new DecorationPlacer(engine);
        this.structures = new StructurePlacer(engine, disabledStructures, prefabs);
    }

    public String name() {
        return name;
    }

    public long seed() {
        return seed;
    }

    public Preset preset() {
        return preset;
    }

    public TerrainEngine engine() {
        return engine;
    }

    public FeaturePlacer features() {
        return features;
    }

    public DecorationPlacer decoration() {
        return decoration;
    }

    public StructurePlacer structures() {
        return structures;
    }

    public MobQueue mobQueue() {
        return mobQueue;
    }
}
