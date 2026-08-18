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
