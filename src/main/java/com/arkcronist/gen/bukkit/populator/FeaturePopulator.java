package com.arkcronist.gen.bukkit.populator;

import com.arkcronist.gen.bukkit.ArkWorld;
import com.arkcronist.gen.bukkit.ArkcronistPlugin;
import com.arkcronist.gen.bukkit.writer.ChunkClippedWriter;
import com.arkcronist.gen.core.decorate.FeaturePlacer;
import com.arkcronist.gen.core.terrain.Preset;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

/**
 * Places ground decoration and then trees and boulders.
 *
 * <p>Order matters: plants go down first and trees overwrite them, which is why a trunk never grows
 * out of a flower.</p>
 *
 * <p>Trees are collected from the neighbouring chunks as well as this one, so any crown, trunk or
 * root system that reaches across a border arrives complete.</p>
 */
public final class FeaturePopulator extends BlockPopulator {

    private final ArkcronistPlugin plugin;
    private final Preset preset;

    public FeaturePopulator(ArkcronistPlugin plugin, Preset preset) {
        this.plugin = plugin;
        this.preset = preset;
    }

    @Override
    public void populate(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ,
                         @NotNull LimitedRegion limitedRegion) {
        ArkWorld world = plugin.worlds().get(worldInfo, preset);
        ChunkClippedWriter writer = new ChunkClippedWriter(limitedRegion, chunkX, chunkZ,
                worldInfo.getMinHeight(), worldInfo.getMaxHeight());

        world.decoration().decorate(chunkX, chunkZ, writer);

        int radius = FeaturePlacer.chunkRadius();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                world.features().place(chunkX + dx, chunkZ + dz, writer);
            }
        }
    }
}
