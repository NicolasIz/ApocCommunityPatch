package com.arkcronist.gen.bukkit;

import com.arkcronist.gen.bukkit.command.AgCommand;
import com.arkcronist.gen.bukkit.config.ArkConfig;
import com.arkcronist.gen.bukkit.mobs.ChunkSpawnListener;
import com.arkcronist.gen.core.terrain.Preset;
import org.bukkit.command.PluginCommand;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * ArkcronistGenerator.
 *
 * <p>Used as a world generator from any world manager (including ArkcronistWorlds) with
 * {@code -g ArkcronistGenerator}, optionally naming a preset:
 * {@code -g ArkcronistGenerator:BASE}, {@code :CHAOTIC} or {@code :INSANE}.</p>
 */
public final class ArkcronistPlugin extends JavaPlugin {

    private ArkConfig arkConfig;
    private WorldRegistry worlds;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.arkConfig = new ArkConfig(getConfig());
        this.worlds = new WorldRegistry(this);

        BlockBridge.initialize(getLogger());
        getServer().getPluginManager().registerEvents(new ChunkSpawnListener(this), this);

        PluginCommand command = getCommand("ag");
        if (command != null) {
            AgCommand executor = new AgCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        getLogger().info("ArkcronistGenerator " + getPluginMeta().getVersion() + " ready"
                + " - default preset " + arkConfig.defaultPreset()
                + ", use -g ArkcronistGenerator:<BASE|CHAOTIC|INSANE>");
    }

    @Override
    public void onDisable() {
        if (worlds != null) {
            worlds.clearCaches();
        }
    }

    public ArkConfig arkConfig() {
        return arkConfig;
    }

    public WorldRegistry worlds() {
        return worlds;
    }

    /** Re-reads config.yml. Existing worlds keep their engines; caches are dropped. */
    public void reload() {
        reloadConfig();
        this.arkConfig = new ArkConfig(getConfig());
        worlds.clearCaches();
    }

    @Override
    public @Nullable ChunkGenerator getDefaultWorldGenerator(@NotNull String worldName, @Nullable String id) {
        Preset preset = id == null || id.isBlank() ? arkConfig.defaultPreset() : Preset.parse(id);
        if (id != null && !id.isBlank() && !Preset.isKnown(id)) {
            getLogger().warning("Unknown preset '" + id + "' for world '" + worldName
                    + "', falling back to " + preset);
        }
        return new ArkChunkGenerator(this, preset);
    }

    @Override
    public @Nullable BiomeProvider getDefaultBiomeProvider(@NotNull String worldName, @Nullable String id) {
        // The generator supplies the provider once the world's seed and height limits are known.
        return null;
    }
}
