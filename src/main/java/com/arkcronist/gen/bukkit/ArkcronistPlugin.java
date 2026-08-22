package com.arkcronist.gen.bukkit;

import com.arkcronist.gen.bukkit.command.AgCommand;
import com.arkcronist.gen.bukkit.config.ArkConfig;
import com.arkcronist.gen.bukkit.mobs.ChunkSpawnListener;
import com.arkcronist.gen.core.prefab.PrefabRegistry;
import com.arkcronist.gen.core.terrain.Preset;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;

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
    private PrefabRegistry prefabs;

    @Override
    public void onEnable() {
        ensureUsableConfig();
        this.arkConfig = new ArkConfig(getConfig());
        this.worlds = new WorldRegistry(this);

        // Prefabs first: loading them registers every block state they use, and the bridge below
        // resolves the whole table in one pass.
        this.prefabs = PrefabInstaller.install(getDataFolder().toPath(), getLogger(),
                arkConfig.extractBundledPrefabs());
        BlockBridge.initialize(getLogger());
        getServer().getPluginManager().registerEvents(new ChunkSpawnListener(this), this);

        PluginCommand command = getCommand("ag");
        if (command != null) {
            AgCommand executor = new AgCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        getLogger().info("ArkcronistGenerator " + getPluginMeta().getVersion() + " ready"
                + " - " + prefabs.size() + " prefabs"
                + " - default preset " + arkConfig.defaultPreset()
                + ", use -g ArkcronistGenerator:<BASE|CHAOTIC|INSANE>");
    }

    /**
     * Writes the default config if there is none, and makes sure whatever is on disk actually
     * parses before anything tries to read it.
     *
     * <p>A config.yml that YAML rejects is not a recoverable state for a generator: every setting
     * reads as absent, worlds come out with defaults the admin never chose, and the only clue is a
     * stack trace during startup. It is also sticky - {@code saveDefaultConfig()} will not replace
     * a file that already exists, so once a broken config lands in the data folder, updating the
     * jar does not clear it. Dropping in a new build has to be enough to fix it.</p>
     *
     * <p>So a file that will not parse is moved aside, with its own timestamped name, and replaced
     * by the packaged default. Nothing is deleted - hand edits are still there to copy back from -
     * but the server starts.</p>
     */
    private void ensureUsableConfig() {
        saveDefaultConfig();

        Path file = getDataFolder().toPath().resolve("config.yml");
        if (!Files.isRegularFile(file)) {
            // saveDefaultConfig() already reported whatever went wrong; getConfig() falls back to
            // the copy inside the jar.
            return;
        }

        try {
            // loadConfiguration() swallows the parse error and hands back an empty config, which is
            // exactly the silent-defaults case this exists to prevent. load() throws instead.
            new YamlConfiguration().load(file.toFile());
            return;
        } catch (InvalidConfigurationException ex) {
            getLogger().warning("config.yml is not valid YAML: " + firstLine(ex.getMessage()));
        } catch (IOException ex) {
            getLogger().log(Level.WARNING, "config.yml could not be read", ex);
            return;
        }

        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path quarantine = file.resolveSibling("config.broken-" + stamp + ".yml");
        try {
            Files.move(file, quarantine, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            getLogger().log(Level.SEVERE, "Could not set the broken config.yml aside; "
                    + "the plugin is starting on built-in defaults. Fix or delete the file by hand.", ex);
            return;
        }

        saveResource("config.yml", true);
        reloadConfig();
        getLogger().warning("Your edits were kept in " + quarantine.getFileName()
                + " and config.yml has been rewritten from the defaults."
                + " Copy your settings back across once the YAML is fixed.");
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "no detail given";
        }
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
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

    /** The schematics available to this server; shared by every world. */
    public PrefabRegistry prefabs() {
        return prefabs;
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
