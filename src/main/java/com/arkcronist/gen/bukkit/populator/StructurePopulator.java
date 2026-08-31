package com.arkcronist.gen.bukkit.populator;

import com.arkcronist.gen.bukkit.ArkWorld;
import com.arkcronist.gen.bukkit.ArkcronistPlugin;
import com.arkcronist.gen.bukkit.loot.LootFiller;
import com.arkcronist.gen.bukkit.writer.ChunkClippedWriter;
import com.arkcronist.gen.core.structure.LootMarker;
import com.arkcronist.gen.core.structure.MobSpawn;
import com.arkcronist.gen.core.structure.SpawnerMarker;
import com.arkcronist.gen.core.terrain.Preset;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Writes the structure blocks that belong to this chunk, fills its containers and hands any mob
 * requests to the queue for spawning once the chunk is live.
 */
public final class StructurePopulator extends BlockPopulator {

    private final ArkcronistPlugin plugin;
    private final Preset preset;

    public StructurePopulator(ArkcronistPlugin plugin, Preset preset) {
        this.plugin = plugin;
        this.preset = preset;
    }

    @Override
    public void populate(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ,
                         @NotNull LimitedRegion limitedRegion) {
        ArkWorld world = plugin.worlds().get(worldInfo, preset);
        ChunkClippedWriter writer = new ChunkClippedWriter(limitedRegion, chunkX, chunkZ,
                worldInfo.getMinHeight(), worldInfo.getMaxHeight());

        List<MobSpawn> spawns = new ArrayList<>();
        List<LootMarker> loot = new ArrayList<>();
        List<SpawnerMarker> spawners = new ArrayList<>();
        world.structures().placeInto(chunkX, chunkZ, writer, spawns, loot, spawners);

        if (plugin.arkConfig().fillLoot()) {
            LootFiller.fill(limitedRegion, loot, world.seed(), plugin.lootRules());
        }
        configureSpawners(limitedRegion, spawners);

        if (plugin.arkConfig().spawnMinibosses() && !spawns.isEmpty()) {
            world.mobQueue().enqueue(chunkX, chunkZ, spawns);
        }
    }

    private void configureSpawners(LimitedRegion region, List<SpawnerMarker> spawners) {
        for (SpawnerMarker marker : spawners) {
            if (!region.isInRegion(marker.x(), marker.y(), marker.z())) {
                continue;
            }
            BlockState state = region.getBlockState(marker.x(), marker.y(), marker.z());
            if (!(state instanceof CreatureSpawner spawner)) {
                continue;
            }
            try {
                spawner.setSpawnedType(EntityType.valueOf(marker.entityType().toUpperCase(Locale.ROOT)));
                region.setBlockState(marker.x(), marker.y(), marker.z(), spawner);
            } catch (IllegalArgumentException ignored) {
                // Unknown entity name in a custom config: leave the spawner at its default.
            }
        }
    }
}
