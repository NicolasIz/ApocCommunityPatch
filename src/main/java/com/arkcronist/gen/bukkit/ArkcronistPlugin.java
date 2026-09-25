package com.arkcronist.gen.bukkit;

import com.arkcronist.gen.bukkit.command.AgCommand;
import com.arkcronist.gen.bukkit.colour.BiomeColourPack;
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
import java.util.Map;
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
    private org.bukkit.scheduler.BukkitTask ambientSpawner;
    private com.arkcronist.gen.bukkit.loot.LootRules lootRules =
            com.arkcronist.gen.bukkit.loot.LootRules.none();
    private com.arkcronist.gen.bukkit.world.DimensionManager dimensions;
    private final com.arkcronist.gen.bukkit.mobs.SpawnedMobs spawnedMobs =
            new com.arkcronist.gen.bukkit.mobs.SpawnedMobs(this);
    private final com.arkcronist.gen.bukkit.mythic.MobRoster roster =
            new com.arkcronist.gen.bukkit.mythic.MobRoster();

    @Override
    public void onEnable() {
        ensureUsableConfig();
        this.arkConfig = new ArkConfig(getConfig());
        this.lootRules = com.arkcronist.gen.bukkit.loot.LootRules.read(getConfig(), getLogger()::warning);
        this.worlds = new WorldRegistry(this);
        this.dimensions = new com.arkcronist.gen.bukkit.world.DimensionManager(this);

        // Prefabs first: loading them registers every block state they use, and the bridge below
        // resolves the whole table in one pass.
        this.prefabs = PrefabInstaller.install(getDataFolder().toPath(), getLogger(),
                arkConfig.extractBundledPrefabs());
        installDatapacks();
        BlockBridge.initialize(getLogger());
        com.arkcronist.gen.bukkit.mobs.MythicBridge.initialize(getLogger());
        installBiomeColours();
        getServer().getPluginManager().registerEvents(new ChunkSpawnListener(this), this);
        getServer().getPluginManager().registerEvents(spawnedMobs, this);
        getServer().getPluginManager().registerEvents(
                new com.arkcronist.gen.bukkit.mobs.HostileSwapListener(this), this);
        getServer().getPluginManager().registerEvents(new WorldAdoptionListener(this), this);
        getServer().getPluginManager().registerEvents(
                new com.arkcronist.gen.bukkit.world.PortalListener(this), this);
        // Worlds that are already open when the plugin starts. Without this a restarted server does
        // not recognise its own generated world until something makes it generate a fresh chunk, and
        // everything keyed on "is this one of ours" - the hostile mob swap above, most visibly - does
        // nothing at all until then.
        for (org.bukkit.World world : getServer().getWorlds()) {
            WorldAdoptionListener.adopt(this, world);
        }
        startAmbientSpawner();
        // The per-world count the spawn limit reads, refreshed every five seconds. See SpawnedMobs.
        getServer().getScheduler().runTaskTimer(this,
                () -> spawnedMobs.recount(getServer().getWorlds()), 40L, 100L);
        // Deferred to the first tick on purpose: MythicMobs loads its packs in its own onEnable and
        // enable order is not ours to rely on, so asking it now finds an empty catalogue about as
        // often as not. See MobRoster.
        roster.biomeSource(this::readBiomes);
        roster.scheduleFirstRead(this, arkConfig);

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
     * Copies the admin's datapacks into the folder the server generates worlds from.
     *
     * <p>This is how Incendium and Nullscape reach the Nether and End this plugin creates. Both
     * replace the vanilla dimension outright, so once the server has read them every Nether and
     * every End it makes uses them - there is nothing to attach per world, and no code here that
     * knows anything about either pack.</p>
     *
     * <p>The catch is timing, and it cannot be engineered away: world generation registries are
     * built from the datapack folder before plugins load and frozen when the first world opens.
     * So the first start after a pack is added copies it and the next start is when it does
     * something. Saying so, once, in the log, is the whole of the fix.</p>
     */
    private void installDatapacks() {
        if (!arkConfig.installDatapacks()) {
            return;
        }
        Path source = getDataFolder().toPath()
                .resolve(com.arkcronist.gen.bukkit.datapack.DatapackInstaller.SOURCE_FOLDER);
        boolean firstTime = !Files.isDirectory(source);
        try {
            Files.createDirectories(source);
            Path readme = source.resolve("README.txt");
            if (!Files.exists(readme)) {
                Files.writeString(readme,
                        com.arkcronist.gen.bukkit.datapack.DatapackInstaller.README);
            }
            if (firstTime) {
                // Said once, on the start that creates it. A folder nobody knows about is a folder
                // nobody puts anything in.
                getLogger().info("Datapacks go in " + source + " - drop the zips there and restart."
                        + " There is a README.txt in it explaining the rest.");
            }
        } catch (IOException exception) {
            getLogger().log(Level.WARNING, "Could not prepare " + source, exception);
            return;
        }

        java.util.List<org.bukkit.World> open = getServer().getWorlds();
        if (open.isEmpty()) {
            // Nothing to copy into yet. Only possible on a server with no level at all.
            return;
        }
        Path target = open.get(0).getWorldFolder().toPath().resolve("datapacks");

        int format = com.arkcronist.gen.bukkit.datapack.DatapackInstaller.serverFormat(getServer());
        if (format == 0) {
            getLogger().warning("This server will not say which data pack format it loads, so packs"
                    + " are installed without checking. One built for another version will be"
                    + " listed by the server and quietly not loaded.");
        }

        java.util.List<com.arkcronist.gen.bukkit.datapack.DatapackInstaller.Result> results =
                com.arkcronist.gen.bukkit.datapack.DatapackInstaller.install(
                        source, target, format, arkConfig.retargetDatapacks(), getLogger()::info);
        if (results.isEmpty()) {
            return;
        }

        for (String collision : com.arkcronist.gen.bukkit.datapack.DatapackInstaller
                .collisions(target, results)) {
            getLogger().warning(collision);
        }
        reportBrokenFiles(target, results);
        reportDimensions(target, results);

        for (com.arkcronist.gen.bukkit.datapack.DatapackInstaller.Result result : results) {
            if (result.outcome() == com.arkcronist.gen.bukkit.datapack.DatapackInstaller
                    .Outcome.RETARGETED) {
                reportMissingBlocks(target.resolve(result.file()));
            }
        }

        long fresh = results.stream()
                .filter(r -> r.outcome() == com.arkcronist.gen.bukkit.datapack.DatapackInstaller
                        .Outcome.INSTALLED)
                .count();
        if (fresh > 0) {
            getLogger().warning(fresh + " datapack(s) were copied into " + target + ". They do"
                    + " NOT apply to this run: the server builds world generation before plugins"
                    + " start. Restart the server once and they will be in effect, including for"
                    + " the Nether and End of every world this generator makes.");
        }
    }

    /**
     * Says which installed datapacks add dimensions, and that they cannot simply be removed again.
     *
     * <p>The first server start after such a pack loads writes its dimensions into the world's own
     * level.dat. Delete the zip afterwards and the world still names dimension types that no longer
     * exist: the server stops with "Failed to load datapacks, can't proceed with server load" and
     * will not open the world until the pack is put back or level.dat is edited by hand. Nothing is
     * lost and the world is fine - but it is a bad thing to find out at the moment a server will not
     * start, and worse to find out after acting on advice to remove the pack.</p>
     */
    private void reportDimensions(Path target,
            java.util.List<com.arkcronist.gen.bukkit.datapack.DatapackInstaller.Result> results) {
        for (com.arkcronist.gen.bukkit.datapack.DatapackInstaller.Result result : results) {
            if (result.outcome() != com.arkcronist.gen.bukkit.datapack.DatapackInstaller
                    .Outcome.INSTALLED) {
                // Only on the start that installs it: after that the admin has been told, and
                // repeating it every boot is how a warning stops being read.
                continue;
            }
            java.util.List<String> dimensions = com.arkcronist.gen.bukkit.datapack.DatapackInstaller
                    .dimensions(target.resolve(result.file()));
            if (dimensions.isEmpty()) {
                continue;
            }
            getLogger().warning("'" + result.file() + "' adds " + dimensions.size()
                    + " dimension(s) of its own (" + String.join(", ", dimensions) + "). Once the"
                    + " server has loaded it, they are written into the world's level.dat, and"
                    + " deleting the zip after that leaves the world naming dimensions that no"
                    + " longer exist - the server then refuses to start until the pack is put back."
                    + " Decide now whether you want it, because removing it later is not just"
                    + " deleting a file.");
        }
    }

    /**
     * Warns about files in an installed datapack that the game will refuse to read.
     *
     * <p>A pack's own broken file costs that pack a feature. A broken file under
     * {@code data/minecraft/} costs the game one: the pack has said it replaces something vanilla,
     * the replacement will not parse, and what was there before is gone - in every world on the
     * server, including ones the pack has nothing to do with.</p>
     */
    private void reportBrokenFiles(Path target,
            java.util.List<com.arkcronist.gen.bukkit.datapack.DatapackInstaller.Result> results) {
        for (com.arkcronist.gen.bukkit.datapack.DatapackInstaller.Result result : results) {
            if (result.outcome() != com.arkcronist.gen.bukkit.datapack.DatapackInstaller
                        .Outcome.INSTALLED
                    && result.outcome() != com.arkcronist.gen.bukkit.datapack.DatapackInstaller
                        .Outcome.PRESENT
                    && result.outcome() != com.arkcronist.gen.bukkit.datapack.DatapackInstaller
                        .Outcome.RETARGETED) {
                continue;
            }
            com.arkcronist.gen.bukkit.datapack.DatapackInstaller.Broken broken =
                    com.arkcronist.gen.bukkit.datapack.DatapackInstaller
                            .brokenFiles(target.resolve(result.file()));
            if (broken.total() == 0) {
                continue;
            }
            if (!broken.vanilla().isEmpty()) {
                getLogger().warning("'" + result.file() + "' has " + broken.vanilla().size()
                        + " unreadable file(s) that REPLACE the game's own: "
                        + String.join(", ", broken.vanilla()) + ". Whatever each of those replaced"
                        + " is now missing from every world on this server, not only from worlds"
                        + " this pack is about. Consider removing the pack.");
            }
            if (!broken.own().isEmpty()) {
                getLogger().warning("'" + result.file() + "' has " + broken.own().size()
                        + " unreadable file(s) of its own, so that much of it will not work: "
                        + String.join(", ", broken.own()));
            }
        }
    }

    /**
     * Warns about blocks a forced datapack uses that this game does not have.
     *
     * <p>Changing the version a pack declares is enough to make the server load it and says nothing
     * at all about whether it will work. When a structure is placed, any block in its palette the
     * game does not recognise is simply dropped: the building generates with holes in it, no error
     * is logged, and the cause is a game version away from the symptom. Reading the palettes turns
     * that into a list of names before anybody walks out to find the hole.</p>
     */
    private void reportMissingBlocks(Path pack) {
        java.util.List<String> missing = new java.util.ArrayList<>();
        for (String id : com.arkcronist.gen.bukkit.datapack.PackBlocks.paletteBlocks(pack)) {
            if (org.bukkit.Material.matchMaterial(id) == null) {
                missing.add(id);
            }
        }
        if (missing.isEmpty()) {
            getLogger().info("'" + pack.getFileName() + "' uses no blocks this game does not have,"
                    + " so the version rewrite is very likely all it needed.");
            return;
        }
        java.util.Collections.sort(missing);
        getLogger().warning("'" + pack.getFileName() + "' uses " + missing.size()
                + " block(s) that do not exist in this version: " + String.join(", ", missing)
                + ". Wherever a structure of its uses one, that block is left out and the building"
                + " generates with a hole in it. Nothing else will tell you this.");
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

    /**
     * Writes the biome colour datapack into every world folder on disk.
     *
     * <p>A datapack is read when its world loads, and this runs after that, so the colours arrive on
     * the <em>next</em> start. That is stated in the log rather than hidden: there is no way for a
     * plugin to add a biome to a running server - Paper's registry API covers banner patterns,
     * enchantments and mob variants, but not biomes.</p>
     *
     * <p>Nothing depends on it. The biome provider asks the registry for the custom key and falls
     * back to the vanilla one when it is missing, so a pack that never loads costs the colour and
     * nothing else.</p>
     */
    /**
     * Starts the spawner that puts the custom mobs out in daylight.
     *
     * <p>Kept as a field so a reload can stop it and start it again at the new period rather than
     * quietly leaving the old one running beside the new one, which is how a plugin ends up spawning
     * at twice the rate it says it does.</p>
     */
    private void startAmbientSpawner() {
        stopAmbientSpawner();
        if (!arkConfig.hostileMobsEnabled() || !arkConfig.ambientEnabled()) {
            return;
        }
        int period = arkConfig.ambientPeriodTicks();
        ambientSpawner = getServer().getScheduler()
                .runTaskTimer(this, new com.arkcronist.gen.bukkit.mobs.AmbientSpawnTask(this),
                        period, period);
    }

    private void stopAmbientSpawner() {
        if (ambientSpawner != null) {
            ambientSpawner.cancel();
            ambientSpawner = null;
        }
    }

    private void installBiomeColours() {
        Map<String, BiomeColourPack.Colours> table =
                arkConfig.biomeColourTable(new com.arkcronist.gen.core.biome.BiomeRegistry());
        if (table.isEmpty()) {
            return;
        }
        Path container = getServer().getWorldContainer().toPath();
        int written = 0;
        try (var worlds = Files.list(container)) {
            for (Path folder : worlds.toList()) {
                if (Files.isDirectory(folder) && Files.exists(folder.resolve("level.dat"))) {
                    if (BiomeColourPack.install(folder, table, getLogger())) {
                        written++;
                    }
                }
            }
        } catch (IOException exception) {
            getLogger().warning("Could not scan the world folders for the colour datapack: "
                    + exception.getMessage());
            return;
        }
        if (written > 0) {
            getLogger().info("Biome colours prepared for " + written + " world(s): "
                    + String.join(", ", table.keySet())
                    + ". Restart once for them to take effect.");
        }
    }

    @Override
    public void onDisable() {
        stopAmbientSpawner();
        if (worlds != null) {
            worlds.clearCaches();
        }
    }

    public ArkConfig arkConfig() {
        return arkConfig;
    }

    /** What MythicMobs has loaded, sorted into what may spawn where. Empty when discovery is off. */
    /**
     * Every biome the server has, for giving discovered mobs their biomes: the game's own, every
     * datapack's from the world folders (Terralith and the like, read from their files for their
     * climate and tags), and anything else the registry lists by name alone.
     */
    private java.util.List<com.arkcronist.gen.bukkit.mythic.BiomeProfile> readBiomes() {
        java.util.List<Path> folders = new java.util.ArrayList<>();
        for (org.bukkit.World world : getServer().getWorlds()) {
            Path folder = world.getWorldFolder().toPath().resolve("datapacks");
            if (!folders.contains(folder)) {
                folders.add(folder);
            }
        }
        folders.add(getDataFolder().toPath().resolve(
                com.arkcronist.gen.bukkit.datapack.DatapackInstaller.SOURCE_FOLDER));
        java.util.List<String> registry = new java.util.ArrayList<>();
        try {
            for (org.bukkit.block.Biome biome : org.bukkit.Registry.BIOME) {
                registry.add(biome.getKey().toString());
            }
        } catch (RuntimeException | LinkageError unsupported) {
            // A server that will not list its biomes still has the files and the game's own list.
        }
        return com.arkcronist.gen.bukkit.mythic.DatapackBiomes.merge(
                com.arkcronist.gen.bukkit.mythic.VanillaBiomes.all(),
                com.arkcronist.gen.bukkit.mythic.DatapackBiomes.read(folders), registry);
    }

    public com.arkcronist.gen.bukkit.mobs.SpawnedMobs spawnedMobs() {
        return spawnedMobs;
    }

    public com.arkcronist.gen.bukkit.mythic.MobRoster roster() {
        return roster;
    }

    /** Gives each generated world its own Nether and End. */
    public com.arkcronist.gen.bukkit.world.DimensionManager dimensions() {
        return dimensions;
    }

    /** What the config says goes in structure chests. Re-read on reload, like everything else. */
    public com.arkcronist.gen.bukkit.loot.LootRules lootRules() {
        return lootRules;
    }

    public WorldRegistry worlds() {
        return worlds;
    }

    /** The schematics available to this server; shared by every world. */
    public PrefabRegistry prefabs() {
        return prefabs;
    }

    /** Re-reads config.yml. Existing worlds keep their engines; caches are dropped. */
    /**
     * Re-reads the configuration and makes it actually apply.
     *
     * <p>The worlds are rebuilt, not just emptied of cached chunks, and that is the whole point of
     * this method. An {@link ArkWorld} builds its engine once from the {@link
     * com.arkcronist.gen.core.terrain.TerrainSettings} it is handed, so clearing the cache alone left
     * every world still generating from the old config while the command cheerfully reported the new
     * one as applied - a change to {@code terrain:} or {@code presets:} did nothing at all until the
     * server was restarted, and nothing said so.</p>
     *
     * <p>Rebuilding empties each world's queue of structure mobs that have not been spawned yet, so
     * a garrison whose chunk had been generated but not yet visited is lost. That is a small price on
     * an explicit command, and the alternative is a reload that quietly does not reload.</p>
     */
    public void reload() {
        reloadConfig();
        this.arkConfig = new ArkConfig(getConfig());
        this.lootRules = com.arkcronist.gen.bukkit.loot.LootRules.read(getConfig(), getLogger()::warning);
        startAmbientSpawner();
        // Re-read here rather than only at startup: a reload is how an operator applies a new
        // exclude list or a corrected habitat, and a roster still holding the previous verdicts
        // would make the command report a change it had not actually made.
        roster.refresh(arkConfig, getLogger());
        worlds.reset();
        // And straight back in. Leaving the registry empty would mean nothing recognises these as
        // our worlds until a fresh chunk is generated - which is exactly the bug that made the
        // hostile mob swap do nothing after a restart.
        for (org.bukkit.World world : getServer().getWorlds()) {
            WorldAdoptionListener.adopt(this, world);
        }
    }

    @Override
    public @Nullable ChunkGenerator getDefaultWorldGenerator(@NotNull String worldName, @Nullable String id) {
        GeneratorId parsed = GeneratorId.parse(id, arkConfig.defaultPreset());
        if (!parsed.recognised()) {
            getLogger().warning("Did not understand all of '" + id + "' for world '" + worldName
                    + "'. Using " + parsed.describe() + ". Valid: BASE, CHAOTIC, INSANE, and"
                    + " 'datapack' to leave the terrain to the game and its datapacks; combine them"
                    + " as INSANE+datapack.");
        }
        if (parsed.datapackTerrain()) {
            getLogger().info("World '" + worldName + "' will be generated by the game and whatever"
                    + " datapacks are installed, not by this plugin. No Arkcronist terrain, prefabs"
                    + " or structures will be placed in it; its hostile mobs follow the "
                    + parsed.preset() + " tables.");
            if (!com.arkcronist.gen.bukkit.datapack.DatapackInstaller.anyInstalled(getServer())) {
                // Said here rather than left to be discovered on foot. Asking for datapack terrain
                // on a server with no datapacks produces a plain vanilla world and no error at all.
                getLogger().warning("...but this server has no datapack loaded, so '" + worldName
                        + "' will come out as an ordinary vanilla world. A terrain datapack must sit"
                        + " in the MAIN world's datapacks folder - drop the zip in "
                        + getDataFolder().toPath().resolve(
                                com.arkcronist.gen.bukkit.datapack.DatapackInstaller.SOURCE_FOLDER)
                        + " and restart TWICE: once to copy it into place, once for the game to read"
                        + " it. A new world's own datapacks folder is never read. Check with"
                        + " /datapack list before creating the world.");
            }
        }
        return new ArkChunkGenerator(this, parsed.preset(), parsed.datapackTerrain());
    }

    @Override
    public @Nullable BiomeProvider getDefaultBiomeProvider(@NotNull String worldName, @Nullable String id) {
        // The generator supplies the provider once the world's seed and height limits are known.
        return null;
    }
}
