package com.arkcronist.gen.core.terrain;

import java.util.Locale;

/**
 * The three world flavours ArkcronistGenerator ships with.
 *
 * <p>Presets are not "the same world with a bigger amplitude multiplier". Each one enables and
 * disables whole terrain stages: CHAOTIC turns on canyon carving, aggressive terracing and biome
 * fragmentation, INSANE additionally turns on 3D overhangs, arches, floating islands, mega caverns
 * and ocean trenches at a scale the other two never reach.</p>
 */
public enum Preset {

    BASE("Natural, varied and immediately playable."),
    CHAOTIC("Broken, dramatic terrain: ranges, canyons, cliffs, fragmented biomes and archipelagos."),
    INSANE("Extreme world: colossal peaks, mega canyons, arches, overhangs, floating islands, mega caves and abyssal trenches.");

    private final String description;

    Preset(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    /** Parses a preset name; unknown or blank values fall back to {@link #BASE}. */
    public static Preset parse(String value) {
        if (value == null || value.isBlank()) {
            return BASE;
        }
        String normalised = value.trim().toUpperCase(Locale.ROOT);
        for (Preset preset : values()) {
            if (preset.name().equals(normalised)) {
                return preset;
            }
        }
        return BASE;
    }

    public static boolean isKnown(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalised = value.trim().toUpperCase(Locale.ROOT);
        for (Preset preset : values()) {
            if (preset.name().equals(normalised)) {
                return true;
            }
        }
        return false;
    }
}
