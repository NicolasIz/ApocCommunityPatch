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

    public ArkConfig(FileConfiguration config) {
        this.config = config;
        ConfigurationSection names = config.getConfigurationSection("minibosses.names");
        if (names != null) {
            for (String key : names.getKeys(false)) {
                minibossNames.put(key.toLowerCase(Locale.ROOT), names.getString(key, key));
            }
        }
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
