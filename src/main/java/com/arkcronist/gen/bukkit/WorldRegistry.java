package com.arkcronist.gen.bukkit;

import com.arkcronist.gen.core.terrain.Preset;
import com.arkcronist.gen.core.terrain.TerrainSettings;
import org.bukkit.World;
import org.bukkit.generator.WorldInfo;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One {@link ArkWorld} per world name, created on first use.
 *
 * <p>Paper can ask the generator for data before the {@code World} object exists, so worlds are keyed
 * by name and built from whatever {@link WorldInfo} is available at the time.</p>
 */
public final class WorldRegistry {

    private final ArkcronistPlugin plugin;
    private final Map<String, ArkWorld> worlds = new ConcurrentHashMap<>();

    public WorldRegistry(ArkcronistPlugin plugin) {
        this.plugin = plugin;
    }

    public ArkWorld get(WorldInfo info, Preset preset) {
        return worlds.computeIfAbsent(info.getName(),
                name -> create(name, info.getSeed(), preset, info.getMinHeight(), info.getMaxHeight()));
    }

    public ArkWorld get(World world, Preset preset) {
        return worlds.computeIfAbsent(world.getName(),
                name -> create(name, world.getSeed(), preset, world.getMinHeight(), world.getMaxHeight()));
    }

    public ArkWorld find(String name) {
        return worlds.get(name);
    }

    public Collection<ArkWorld> all() {
        return worlds.values();
    }

    private ArkWorld create(String name, long seed, Preset preset, int minY, int maxY) {
        TerrainSettings settings = plugin.arkConfig().settingsFor(preset, minY, maxY);
        ArkWorld world = new ArkWorld(name, seed, preset, settings, plugin.arkConfig().cacheSize(),
                plugin.arkConfig().disabledStructures(), plugin.prefabs());
        plugin.getLogger().info("Prepared world '" + name + "' with preset " + preset
                + " (seed " + seed + ", y " + minY + ".." + maxY + ")"
                + (plugin.arkConfig().vanillaStructures()
                ? ", vanilla structures on (monument, stronghold, ancient city, mansion, mineshaft, temples)"
                : ", vanilla structures off"));
        return world;
    }

    public void clearCaches() {
        for (ArkWorld world : worlds.values()) {
            world.engine().cache().clear();
        }
    }

    public void reset() {
        worlds.clear();
    }
}
