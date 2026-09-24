package com.arkcronist.gen.bukkit.config;

import com.arkcronist.gen.core.terrain.Preset;
import com.arkcronist.gen.core.terrain.TerrainSettings;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Reads config.yml into runtime settings.
 *
 * <p>Presets provide the baseline; config.yml only overrides what it actually mentions. That keeps
 * upgrades painless (new tunables appear with sensible values) and lets an operator nudge one
 * number without copying an entire preset definition.</p>
 */
public final class ArkConfig {

    private final FileConfiguration config;
    private final Map<String, String> minibossNames = new HashMap<>();

    /**
     * The hostile-mob swap, parsed once.
     *
     * <p>Read on every natural spawn, which on a populated server is hundreds of events a second,
     * so these are not rebuilt per call. Rebuilding them was the first shape of this and it meant
     * three fresh collections - two sets and a map of copied lists - for every mob that tried to
     * appear anywhere in the world. This object is thrown away and remade on reload, so parsing in
     * the constructor is also what keeps a reload honest.</p>
     */
    private final java.util.Set<String> hostileMobPresets;
    private final java.util.Set<String> hostileMobReasons;
    private final java.util.Map<String, java.util.List<String>> hostileMobTable;
    private final java.util.Map<String, java.util.List<String>> bossMobTable;
    private final java.util.Map<String, java.util.List<String>> biomeMobTable;
    private final java.util.Map<String, java.util.List<String>> netherMobTable;
    private final java.util.Set<String> netherWorlds;
    private final java.util.Map<String, java.util.List<String>> endMobTable;
    private final java.util.Set<String> endWorlds;
    private final java.util.List<String> ambientOverworld;
    private final java.util.List<String> ambientNether;
    private final java.util.List<String> ambientEnd;
    private final java.util.Map<String, String> adoptedWorlds;
    private final java.util.Set<String> autoDiscoverExclude;
    private final java.util.Map<String, String> autoDiscoverRoles;
    private final java.util.Map<String, String> autoDiscoverHabitats;
    private final java.util.Map<String, java.util.List<String>> autoDiscoverBiomeOverrides;

    public ArkConfig(FileConfiguration config) {
        this.config = config;
        ConfigurationSection names = config.getConfigurationSection("minibosses.names");
        if (names != null) {
            for (String key : names.getKeys(false)) {
                minibossNames.put(key.toLowerCase(Locale.ROOT), names.getString(key, key));
            }
        }
        this.hostileMobPresets = readUpperCaseSet("hostile-mobs.presets", java.util.Set.of());
        this.hostileMobReasons = readUpperCaseSet("hostile-mobs.reasons", java.util.Set.of("NATURAL"));
        this.hostileMobTable = readMobTable("hostile-mobs.table");
        this.bossMobTable = readMobTable("hostile-mobs.boss-table");
        this.biomeMobTable = readMobTable("hostile-mobs.biome-table");
        this.netherMobTable = readMobTable("hostile-mobs.nether.table");
        this.netherWorlds = readLowerCaseSet("hostile-mobs.nether.worlds");
        this.endMobTable = readMobTable("hostile-mobs.end.table");
        this.endWorlds = readLowerCaseSet("hostile-mobs.end.worlds");
        this.ambientOverworld = readNames("hostile-mobs.ambient.overworld");
        this.ambientNether = readNames("hostile-mobs.ambient.nether");
        this.ambientEnd = readNames("hostile-mobs.ambient.end");
        this.adoptedWorlds = readAdoptedWorlds();
        this.autoDiscoverExclude = readUpperCaseSet("hostile-mobs.auto-discover.exclude",
                java.util.Set.of());
        this.autoDiscoverRoles = readWords("hostile-mobs.auto-discover.roles");
        this.autoDiscoverHabitats = readWords("hostile-mobs.auto-discover.habitats");
        this.autoDiscoverBiomeOverrides = readMobTable("hostile-mobs.auto-discover.biomes.overrides");
    }

    /** A section of name to single word, kept as written so the reader can report a typo verbatim. */
    private java.util.Map<String, String> readWords(String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            return java.util.Map.of();
        }
        java.util.Map<String, String> words = new java.util.LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            String value = section.getString(key);
            if (value != null && !value.isBlank()) {
                words.put(key.trim(), value.trim());
            }
        }
        return java.util.Map.copyOf(words);
    }

    /**
     * Worlds this generator did not make, that should still get the custom mobs.
     *
     * <p>Read as world name to preset name. The preset is not used to generate anything - these
     * worlds are generated by somebody else - only to pick which of the configured mob tables
     * applies, the same way an Arkcronist world's own preset does.</p>
     */
    private java.util.Map<String, String> readAdoptedWorlds() {
        java.util.Map<String, String> adopted = new java.util.LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("hostile-mobs.adopt-worlds");
        if (section == null) {
            return adopted;
        }
        for (String world : section.getKeys(false)) {
            String preset = section.getString(world);
            if (preset != null && !preset.isBlank()) {
                adopted.put(world.toLowerCase(java.util.Locale.ROOT),
                        preset.trim().toUpperCase(java.util.Locale.ROOT));
            }
        }
        return adopted;
    }

    /**
     * The preset name to treat a foreign world as, or null when it is not adopted.
     *
     * @see #readAdoptedWorlds()
     */
    public String adoptedPreset(String worldName) {
        return adoptedWorlds.get(worldName.toLowerCase(java.util.Locale.ROOT));
    }

    private java.util.Set<String> readLowerCaseSet(String path) {
        java.util.Set<String> values = new java.util.LinkedHashSet<>();
        for (String raw : config.getStringList(path)) {
            String value = raw.trim().toLowerCase(Locale.ROOT);
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return java.util.Set.copyOf(values);
    }

    private java.util.List<String> readNames(String path) {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (String raw : config.getStringList(path)) {
            String name = raw.trim();
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return java.util.List.copyOf(names);
    }

    private java.util.Set<String> readUpperCaseSet(String path, java.util.Set<String> fallback) {
        java.util.Set<String> values = new java.util.LinkedHashSet<>();
        for (String raw : config.getStringList(path)) {
            String value = raw.trim().toUpperCase(Locale.ROOT);
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values.isEmpty() ? fallback : java.util.Set.copyOf(values);
    }

    private java.util.Map<String, java.util.List<String>> readMobTable(String path) {
        java.util.Map<String, java.util.List<String>> table = new java.util.LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            return java.util.Map.of();
        }
        for (String key : section.getKeys(false)) {
            java.util.List<String> mobs = new java.util.ArrayList<>();
            for (String raw : section.getStringList(key)) {
                String name = raw.trim();
                if (!name.isEmpty()) {
                    mobs.add(name);
                }
            }
            if (!mobs.isEmpty()) {
                table.put(key.trim().toUpperCase(Locale.ROOT), java.util.List.copyOf(mobs));
            }
        }
        return java.util.Map.copyOf(table);
    }

    /**
     * Which custom mob stands in for each of the catalogue's named bosses.
     *
     * <p>Keyed by the boss's own name - {@code castle_lord}, {@code deep_leviathan},
     * {@code fortress_marshal} - rather than by the vanilla entity it is built on. That is the
     * whole point of it existing: a castle lord is an EVOKER and so is an ordinary evoker standing
     * in a courtyard, so the entity-type table cannot tell them apart, and mapping EVOKER to a
     * thousand-health boss turns every evoker in the world into one. A boss is the one mob in a
     * structure that should be chosen per structure, and its name is already in the request.</p>
     *
     * <p>Empty by default. Falls back to the entity-type table for any boss not named here, which
     * is what the prefab building guardians use - their names are display text built from the folder
     * they came from, not fixed keys.</p>
     */
    public java.util.Map<String, java.util.List<String>> bossMobTable() {
        return bossMobTable;
    }

    /**
     * Which custom mobs belong to a biome rather than to a vanilla entity type.
     *
     * <p>Consulted before the entity table and winning where it answers. Empty by default, which
     * leaves the entity table as the only rule, exactly as before.</p>
     */
    public java.util.Map<String, java.util.List<String>> biomeMobTable() {
        return biomeMobTable;
    }

    /**
     * Whether the plugin asks MythicMobs what it has rather than being told in config.yml.
     *
     * <p>Off by default, and that is not timidity: it is the only setting here that can change what
     * spawns in a world without anybody naming a single mob. An operator who has hand-built their
     * tables gets exactly what they wrote until they ask for this.</p>
     */
    public boolean autoDiscoverEnabled() {
        return config.getBoolean("hostile-mobs.auto-discover.enabled", false);
    }

    /**
     * Whether config.yml mentions auto-discovery at all.
     *
     * <p>Not the same question as {@link #autoDiscoverEnabled()}, and the difference matters because
     * {@code saveDefaultConfig()} never replaces a file that is already there: a config.yml written
     * before this feature existed has no {@code auto-discover} section, so it reads as off without
     * anybody having decided that. Told apart, the plugin can say once what to add instead of quietly
     * doing nothing - which is exactly how the datapack {@code retarget} setting came to look
     * broken.</p>
     */
    public boolean autoDiscoverConfigured() {
        return config.isSet("hostile-mobs.auto-discover.enabled");
    }

    /** Health at or above which a discovered mob is treated as a boss and kept out of spawns. */
    public double autoDiscoverBossHealth() {
        double health = config.getDouble("hostile-mobs.auto-discover.boss-health",
                com.arkcronist.gen.bukkit.mythic.MobClassifier.DEFAULT_BOSS_HEALTH);
        return Math.max(0.0, health);
    }

    /** Whether discovered hostiles join the ambient pools. */
    public boolean autoDiscoverAmbient() {
        return config.getBoolean("hostile-mobs.auto-discover.ambient", true);
    }

    /** Whether discovered hostiles fill the entity types the swap table does not name. */
    public boolean autoDiscoverSwap() {
        return config.getBoolean("hostile-mobs.auto-discover.swap", true);
    }

    /** Discovered names never to use, whatever they were judged to be. */
    public java.util.Set<String> autoDiscoverExclude() {
        return autoDiscoverExclude;
    }

    /** Name to PROP, PET, HOSTILE or BOSS, overruling the judgement. */
    public java.util.Map<String, String> autoDiscoverRoles() {
        return autoDiscoverRoles;
    }

    /** Name to OVERWORLD, NETHER, END or ANY, overruling the judgement. */
    public java.util.Map<String, String> autoDiscoverHabitats() {
        return autoDiscoverHabitats;
    }

    /**
     * Whether discovered hostiles are given biomes from their names, by reading every biome the
     * server has - the game's own and every datapack's, Terralith included.
     *
     * <p>Off unless config.yml says so. The config.yml this plugin ships turns it on, so a new
     * install has it; a server updating the jar keeps the config it already has, which does not
     * mention it, and so keeps spawning exactly as it did - a mob it already had in every biome is
     * not moved to three of them by a jar update nobody asked to change that.</p>
     */
    public boolean autoDiscoverBiomes() {
        return config.getBoolean("hostile-mobs.auto-discover.biomes.enabled", false);
    }

    /**
     * In a biome that has mobs of its own, the share of spawns that use them; the rest use the
     * general mix, so a snowy forest has its yetis and still the odd goblin.
     */
    public double autoDiscoverBiomeShare() {
        double share = config.getDouble("hostile-mobs.auto-discover.biomes.share", 0.7);
        return Math.max(0.0, Math.min(1.0, share));
    }

    /** Mob name to the biomes an operator chose for it, or [ANY] to keep it everywhere. */
    public java.util.Map<String, java.util.List<String>> autoDiscoverBiomeOverrides() {
        return autoDiscoverBiomeOverrides;
    }

    public Preset defaultPreset() {
        return Preset.parse(config.getString("default-preset", "BASE"));
    }

    public int cacheSize() {
        return config.getInt("performance.terrain-cache-chunks", 512);
    }

    public boolean parallel() {
        return config.getBoolean("performance.parallel-generation", true);
    }

    public boolean spawnMinibosses() {
        return config.getBoolean("minibosses.enabled", true);
    }

    public boolean fillLoot() {
        return config.getBoolean("structures.fill-loot", true);
    }

    /**
     * Whether the server places its own vanilla structures (monument, stronghold, ancient city,
     * mansion, mineshaft, desert pyramid and the rest) into this world.
     *
     * <p>They are placed by the server itself from the game's own definitions, so they are the real
     * thing rather than an imitation, and they follow the vanilla biome keys this generator reports.</p>
     */
    /**
     * Whether the jar's bundled schematics are unpacked into {@code plugins/.../prefabs} on start-up.
     *
     * <p>Turn it off once the folder has been curated by hand; existing files are never overwritten
     * either way, so leaving it on only ever adds back something that was deleted.</p>
     */
    public boolean extractBundledPrefabs() {
        return config.getBoolean("prefabs.extract-bundled", true);
    }

    /** Blocks to raise schematic buildings, and vessels, above where the terrain check put them. */
    public int prefabBuildingLift() {
        return config.getInt("prefabs.lift.buildings", 3);
    }

    public int prefabShipLift() {
        return config.getInt("prefabs.lift.ships", 6);
    }

    /** Off by default: prefabs are placed exactly as their author built them. */
    public boolean prefabFurnish() {
        return config.getBoolean("prefabs.furnish", false);
    }

    /**
     * Whether to copy datapacks from the plugin folder into the server's world folder at startup.
     *
     * <p>On by default because the folder starts empty: with nothing in it this does nothing at all
     * beyond creating the folder and a note explaining what it is for.</p>
     */
    public boolean installDatapacks() {
        return config.getBoolean("datapacks.install", true);
    }

    /**
     * Datapacks to install even though they declare a different game version.
     *
     * <p>Names of files, exactly as they are in the folder. Empty by default, because forcing a pack
     * onto a version it was not built for is a decision with consequences and nobody should arrive
     * at it by accident.</p>
     */
    public java.util.Set<String> retargetDatapacks() {
        java.util.Set<String> named =
                new java.util.LinkedHashSet<>(config.getStringList("datapacks.retarget"));
        if (config.getBoolean("datapacks.retarget-all", false)) {
            // One switch instead of a list of file names, because names are the wrong thing to ask
            // for: a browser turns a second download into "pack (1).zip", and a pack that does not
            // match its entry is silently not forced - which looks exactly like the feature being
            // broken.
            named.add(com.arkcronist.gen.bukkit.datapack.DatapackInstaller.EVERYTHING);
        }
        return named;
    }

    public boolean vanillaStructures() {
        return config.getBoolean("structures.vanilla-structures", true);
    }

    /**
     * Structure ids this generator must not place.
     *
     * <p>When vanilla structures are on, the built-in equivalents step aside automatically so a
     * world does not end up with two ocean monuments on top of each other.</p>
     */
    public java.util.Set<String> disabledStructures() {
        java.util.Set<String> disabled = new java.util.HashSet<>(
                config.getStringList("structures.disabled"));
        if (vanillaStructures()) {
            disabled.addAll(VANILLA_EQUIVALENTS);
        }
        disabled.removeAll(config.getStringList("structures.force-enabled"));
        return disabled;
    }

    /**
     * Built-in structures that duplicate something the vanilla generator already provides.
     *
     * <p>The ancient city is deliberately <b>not</b> in this list, and that is the whole point of
     * the takeover. Every other entry here steps aside because the server places its own; the
     * server cannot place an ancient city any more, because it only ever does so in the
     * {@code deep_dark} biome and the biome provider stops reporting that key. Leaving it in the
     * list disabled the only thing left that could build one - the prefab loaded, the structure was
     * constructed, and then it was thrown away before it was ever registered.</p>
     */
    private static final java.util.Set<String> VANILLA_EQUIVALENTS = java.util.Set.of(
            "monument", "stronghold", "mineshaft", "mansion", "desert_pyramid",
            "jungle_temple", "igloo", "witch_hut", "shipwreck", "buried_treasure", "ruined_portal",
            "trail_ruins", "outpost", "trial_chamber");

    /** The list above, for tests that need to check what stands down. */
    public static java.util.Set<String> vanillaEquivalents() {
        return VANILLA_EQUIVALENTS;
    }

    /** Whether the biome colour datapack should be written into the world folders. */
    public boolean biomeColours() {
        return config.getBoolean("biome-colours.enabled", true);
    }

    /**
     * Colour overrides read from the config, keyed by this generator's own biome name.
     *
     * <p>Only biomes with at least one colour set are returned; everything else keeps its vanilla
     * key untouched.</p>
     */
    public java.util.Map<String, com.arkcronist.gen.bukkit.colour.BiomeColourPack.Colours> biomeColourTable(
            com.arkcronist.gen.core.biome.BiomeRegistry registry) {
        java.util.Map<String, com.arkcronist.gen.bukkit.colour.BiomeColourPack.Colours> out =
                new java.util.LinkedHashMap<>();
        if (!biomeColours()) {
            return out;
        }
        org.bukkit.configuration.ConfigurationSection section = config.getConfigurationSection("biome-colours");
        if (section == null) {
            return out;
        }
        for (com.arkcronist.gen.core.biome.ArkBiome biome : registry.all()) {
            org.bukkit.configuration.ConfigurationSection entry = section.getConfigurationSection(biome.name);
            if (entry == null) {
                continue;
            }
            Integer grass = colour(entry, "grass");
            Integer foliage = colour(entry, "foliage");
            Integer water = colour(entry, "water");
            Integer waterFog = colour(entry, "water-fog");
            Integer sky = colour(entry, "sky");
            Integer fog = colour(entry, "fog");
            if (grass == null && foliage == null && water == null && waterFog == null
                    && sky == null && fog == null) {
                continue;
            }
            // Temperature and downfall are what the game uses to decide rain and snow. Taken from
            // the generator's own biome so a custom colour never quietly changes the weather.
            double temperature = Math.max(-0.5, Math.min(2.0, 0.5 + biome.temperature * 0.75));
            double downfall = Math.max(0.0, Math.min(1.0, 0.5 + biome.humidity * 0.5));
            out.put(biome.name, new com.arkcronist.gen.bukkit.colour.BiomeColourPack.Colours(
                    biome.vanillaKey, grass, foliage, water, waterFog, sky, fog,
                    temperature, downfall, entry.getBoolean("rain", true)));
        }
        return out;
    }

    /** Reads "#RRGGBB" or a plain integer. Returns null when the key is absent or unreadable. */
    private static Integer colour(org.bukkit.configuration.ConfigurationSection section, String key) {
        String raw = section.getString(key);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim();
        try {
            if (text.startsWith("#")) {
                return Integer.parseInt(text.substring(1), 16);
            }
            if (text.startsWith("0x") || text.startsWith("0X")) {
                return Integer.parseInt(text.substring(2), 16);
            }
            return Integer.parseInt(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /** Whether hostile mobs should be swapped for MythicMobs ones at all. */
    public boolean hostileMobsEnabled() {
        return config.getBoolean("hostile-mobs.enabled", false);
    }

    /** Which presets get the swap. A world on any other preset keeps its vanilla mobs. */
    public java.util.Set<String> hostileMobPresets() {
        return hostileMobPresets;
    }

    /** Which spawn reasons get the swap. Natural spawning only, unless told otherwise. */
    public java.util.Set<String> hostileMobReasons() {
        return hostileMobReasons;
    }

    /**
     * How much of the hostile mobs the table names is actually replaced.
     *
     * <p>1.0, the default, is what this always did: a type in the table never appears as itself again.
     * Lower it and the rest of the spawns are left exactly as the game made them, so a goblin pack
     * gives goblins <em>as well as</em> zombies. Worth having as a dial rather than a switch because
     * auto-discovery fills in every body a pack happens to use, and "no zombies" then quietly becomes
     * "no vanilla hostile mobs anywhere".</p>
     *
     * @see com.arkcronist.gen.bukkit.mobs.MobMix
     */
    public double replaceChance() {
        double chance = config.getDouble("hostile-mobs.replace-chance", 1.0);
        return Math.max(0.0, Math.min(1.0, chance));
    }

    /**
     * Whether config.yml mentions the mix at all.
     *
     * @see #autoDiscoverConfigured()
     */
    public boolean replaceChanceConfigured() {
        return config.isSet("hostile-mobs.replace-chance");
    }

    /** Whether the garrisons and bosses this generator places are swapped too. */
    public boolean replaceStructureMobs() {
        return config.getBoolean("hostile-mobs.replace-structure-mobs", true);
    }

    /**
     * Vanilla entity type to the MythicMobs names that may stand in for it.
     *
     * <p>Keyed by the upper-case entity name, because that is what both the spawn event and the
     * generator's own mob requests carry. A name repeated in a list simply comes up more often -
     * that is the whole weighting mechanism, and it is enough.</p>
     */
    public java.util.Map<String, java.util.List<String>> hostileMobTable() {
        return hostileMobTable;
    }

    /**
     * Vanilla entity type to MythicMobs name, for the Nether.
     *
     * <p>A table of its own because the Nether is not a world this plugin makes. It cannot be
     * selected by preset the way an Arkcronist world is, and what belongs there is not what belongs
     * on a green hillside - which is the whole reason the volcanic skeletons are kept to it.</p>
     */
    public java.util.Map<String, java.util.List<String>> netherMobTable() {
        return netherMobTable;
    }

    /** Whether the Nether rules apply to the world with this name. */
    public boolean netherApplies(String worldName) {
        if (!config.getBoolean("hostile-mobs.nether.enabled", true)) {
            return false;
        }
        // An empty list means every Nether on the server, which is what a single-world server wants
        // and what a server with several of them can narrow by naming them.
        return netherWorlds.isEmpty() || netherWorlds.contains(worldName.toLowerCase(Locale.ROOT));
    }

    /**
     * Vanilla entity type to MythicMobs name, for the End.
     *
     * <p>The same shape as the Nether's and for the same reason. Empty by default - there is no
     * catalogue of End mobs to ship a default from - which is exactly the gap auto-discovery fills:
     * a mob built on a shulker, or named for the void, lands here without anybody writing it down.</p>
     */
    public java.util.Map<String, java.util.List<String>> endMobTable() {
        return endMobTable;
    }

    /** Whether the End rules apply to the world with this name. */
    public boolean endApplies(String worldName) {
        if (!config.getBoolean("hostile-mobs.end.enabled", true)) {
            return false;
        }
        return endWorlds.isEmpty() || endWorlds.contains(worldName.toLowerCase(Locale.ROOT));
    }

    /** Whether the plugin spawns mobs itself, which is the only way to have them out in daylight. */
    public boolean ambientEnabled() {
        return config.getBoolean("hostile-mobs.ambient.enabled", false);
    }

    /** How often the ambient spawner runs, in ticks. */
    public int ambientPeriodTicks() {
        return Math.max(20, config.getInt("hostile-mobs.ambient.period-ticks", 100));
    }

    /** How many positions it tries per player per run before giving up until the next one. */
    public int ambientAttempts() {
        return Math.max(1, Math.min(16, config.getInt("hostile-mobs.ambient.attempts", 4)));
    }

    /** Never closer than this to the player, so nobody watches one appear. */
    public int ambientMinDistance() {
        return Math.max(8, config.getInt("hostile-mobs.ambient.min-distance", 26));
    }

    /** Never further than this, so it stays inside what the server keeps loaded. */
    public int ambientMaxDistance() {
        return Math.max(12, config.getInt("hostile-mobs.ambient.max-distance", 46));
    }

    /** How many of its own mobs may already be near a player before it stops adding more. */
    public int ambientCap() {
        return Math.max(0, config.getInt("hostile-mobs.ambient.per-player-cap", 8));
    }

    /** What the ambient spawner puts in an Arkcronist overworld. */
    public java.util.List<String> ambientOverworld() {
        return ambientOverworld;
    }

    /** What it puts in the Nether. */
    public java.util.List<String> ambientNether() {
        return ambientNether;
    }

    /** What it puts in the End. */
    public java.util.List<String> ambientEnd() {
        return ambientEnd;
    }

    /** Whether each generated world gets a Nether and an End of its own. */
    public boolean ownDimensions() {
        return config.getBoolean("dimensions.enabled", true);
    }

    public boolean ownNether() {
        return config.getBoolean("dimensions.nether", true);
    }

    public boolean ownEnd() {
        return config.getBoolean("dimensions.end", true);
    }

    /** Appended to the world's name for its Nether; the server's own convention by default. */
    public String netherSuffix() {
        String value = config.getString("dimensions.nether-suffix", "_nether");
        return value == null || value.isBlank() ? "_nether" : value.trim();
    }

    public String endSuffix() {
        String value = config.getString("dimensions.end-suffix", "_the_end");
        return value == null || value.isBlank() ? "_the_end" : value.trim();
    }

    /** Whether portals are routed to those worlds. Off when another plugin already does it. */
    public boolean linkPortals() {
        return config.getBoolean("dimensions.link-portals", true);
    }

    public boolean vanillaMobs() {
        return config.getBoolean("world.vanilla-mob-generation", true);
    }

    public double minibossHealthMultiplier() {
        return config.getDouble("minibosses.health-multiplier", 4.0);
    }

    public double minibossDamageMultiplier() {
        return config.getDouble("minibosses.damage-multiplier", 2.0);
    }

    public boolean minibossGlowing() {
        return config.getBoolean("minibosses.glowing", true);
    }

    public String minibossName(String role) {
        return minibossNames.getOrDefault(role.toLowerCase(Locale.ROOT), defaultName(role));
    }

    private static String defaultName(String role) {
        String[] parts = role.split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
        }
        return builder.toString().trim();
    }

    /** Builds the settings for a preset, applying global and per-preset overrides from config.yml. */
    public TerrainSettings settingsFor(Preset preset, int worldMinY, int worldMaxY) {
        TerrainSettings settings = TerrainSettings.forPreset(preset);
        settings.minY = worldMinY;
        settings.maxY = worldMaxY;

        // Prefab placement is a property of the schematics, not of the terrain, so it comes from
        // its own section rather than from the per-preset terrain overrides.
        settings.prefabBuildingLift = prefabBuildingLift();
        settings.prefabShipLift = prefabShipLift();
        settings.prefabFurnish = prefabFurnish();

        apply(settings, config.getConfigurationSection("terrain"));
        apply(settings, config.getConfigurationSection("presets." + preset.name().toLowerCase(Locale.ROOT)));
        return settings;
    }

    private void apply(TerrainSettings settings, ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(true)) {
            Object value = section.get(key);
            if (value == null || value instanceof ConfigurationSection) {
                continue;
            }
            assign(settings, key.replace('.', '-'), value);
        }
    }

    /** Maps a config key such as {@code ocean-abyss-depth} onto the matching settings field. */
    private void assign(TerrainSettings settings, String key, Object value) {
        String field = toCamelCase(key);
        try {
            java.lang.reflect.Field target = TerrainSettings.class.getField(field);
            Class<?> type = target.getType();
            if (type == double.class && value instanceof Number number) {
                target.setDouble(settings, number.doubleValue());
            } else if (type == int.class && value instanceof Number number) {
                target.setInt(settings, number.intValue());
            } else if (type == boolean.class && value instanceof Boolean bool) {
                target.setBoolean(settings, bool);
            }
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
            // Unknown keys are ignored on purpose so old configs keep working after an update.
        }
    }

    private static String toCamelCase(String key) {
        StringBuilder builder = new StringBuilder();
        boolean upper = false;
        for (char c : key.toCharArray()) {
            if (c == '-' || c == '_') {
                upper = true;
                continue;
            }
            builder.append(upper ? Character.toUpperCase(c) : c);
            upper = false;
        }
        return builder.toString();
    }
}
