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
        return get(info, preset, false);
    }

    public ArkWorld get(WorldInfo info, Preset preset, boolean datapackTerrain) {
        return worlds.computeIfAbsent(info.getName(), name -> create(name, info.getSeed(), preset,
                info.getMinHeight(), info.getMaxHeight(), datapackTerrain));
    }

    public ArkWorld get(World world, Preset preset) {
        return get(world, preset, false);
    }

    public ArkWorld get(World world, Preset preset, boolean datapackTerrain) {
        return worlds.computeIfAbsent(world.getName(), name -> create(name, world.getSeed(), preset,
                world.getMinHeight(), world.getMaxHeight(), datapackTerrain));
    }

    public ArkWorld find(String name) {
        return worlds.get(name);
    }

    public Collection<ArkWorld> all() {
        return worlds.values();
    }

    private ArkWorld create(String name, long seed, Preset preset, int minY, int maxY,
                            boolean datapackTerrain) {
        TerrainSettings settings = plugin.arkConfig().settingsFor(preset, minY, maxY);
        ArkWorld world = new ArkWorld(name, seed, preset, settings, plugin.arkConfig().cacheSize(),
                plugin.arkConfig().disabledStructures(), plugin.prefabs());

        if (datapackTerrain) {
            // Nothing below applies to a world this plugin does not build. Saying "preset INSANE,
            // vanilla structures on (monument, stronghold, ancient city...)" over a world where we
            // place none of that reads as a promise, and the pregeneration warning under it is
            // about a pause that belongs to our generator and not to the game's.
            plugin.getLogger().info("Registered world '" + name + "' (seed " + seed + "). Its"
                    + " terrain, biomes and structures are the game's and its datapacks'; the "
                    + preset + " tables are used for its hostile mobs only.");
            return world;
        }

        plugin.getLogger().info("Prepared world '" + name + "' with preset " + preset
                + " (seed " + seed + ", y " + minY + ".." + maxY + ")"
                + (plugin.arkConfig().vanillaStructures()
                ? ", vanilla structures on (monument, stronghold, ancient city, mansion, mineshaft, temples)"
                : ", vanilla structures off"));
        warnAboutSpawnPregeneration(name);
        return world;
    }

    /**
     * Says, before it happens, why the server is about to stop responding.
     *
     * <p>Creating a world does not hand it over until the game has generated the region around its
     * spawn, and it does that with the main thread parked waiting. Paper notices the main thread has
     * not answered for ten seconds and prints a thread dump in red, headed "DO NOT REPORT THIS TO
     * PAPER - THIS IS NOT A BUG OR A CRASH". It is telling the truth: the real crash timeout is
     * minutes away and the server carries on normally afterwards. But it looks exactly like a crash,
     * the dump lands on whatever this plugin happened to be doing at that instant, and it has now
     * cost two separate investigations.</p>
     *
     * <p>Measured on the INSANE preset this generator produces a chunk in about fifteen
     * milliseconds, and a spawn region is several hundred chunks, so the pause is seconds long by
     * arithmetic rather than by anything going wrong. Only said for a world with no region files
     * yet: an existing world reads its spawn off the disk and none of this applies.</p>
     */
    private void warnAboutSpawnPregeneration(String name) {
        java.io.File regions = new java.io.File(
                new java.io.File(plugin.getServer().getWorldContainer(), name), "region");
        String[] existing = regions.list();
        if (existing != null && existing.length > 0) {
            return;
        }
        plugin.getLogger().info("'" + name + "' is new, so the server now generates the region"
                + " around its spawn before handing the world over. That takes several seconds with"
                + " the main thread waiting, and Paper may print a red thread dump saying the server"
                + " has not responded for 10 seconds. That message says it is not a crash and it is"
                + " right - the server carries on. To stop seeing it, raise watchdog"
                + " early-warning-delay in paper-global.yml.");
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
