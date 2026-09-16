package com.arkcronist.gen.bukkit;

import com.arkcronist.gen.core.terrain.Preset;

import java.util.Locale;
import java.util.Set;

/**
 * What {@code -g ArkcronistGenerator:<id>} asked for.
 *
 * <p>The id used to be a preset name and nothing else. It now also carries whether the world's
 * terrain should come from this generator or be left to the game - which, on a server with terrain
 * datapacks installed, means leaving it to Terralith or Tectonic or whatever else is in the folder.
 * Both spellings are accepted together, so {@code INSANE+datapack} is an INSANE world whose ground
 * somebody else builds.</p>
 *
 * <p>Why that combination is useful: a datapack cannot be pointed at one world and not another.
 * World generation registries are global and frozen when the first world opens, so a pack that
 * replaces the overworld replaces it everywhere. What decides whether a world listens is the world's
 * own generator, and this is the switch. A normal Arkcronist world ignores datapack terrain by
 * construction - it turns the game's noise, surface and cave passes off - and a world created this
 * way turns them all back on and adds nothing of its own.</p>
 *
 * <p>The preset still means something in datapack mode: it is not generating anything, but it is
 * what the hostile mob tables are chosen by, so an INSANE datapack world gets the INSANE mobs.</p>
 */
public record GeneratorId(Preset preset, boolean datapackTerrain, boolean recognised) {

    /**
     * Spellings that mean "let the datapacks build it".
     *
     * <p>More than one because this is typed into a server console at the end of a long day, once,
     * and a world created with the wrong one is a world generated wrong.</p>
     */
    private static final Set<String> DATAPACK_WORDS = Set.of(
            "DATAPACK", "DATAPACKS", "VANILLA", "NOPREFAB", "NOPREFABS", "NOTPREFAB", "NOTPREFABS");

    /** Reads an id, falling back to {@code fallback} for the preset when none is named. */
    public static GeneratorId parse(String id, Preset fallback) {
        if (id == null || id.isBlank()) {
            return new GeneratorId(fallback, false, true);
        }

        Preset preset = null;
        boolean datapack = false;
        boolean recognised = true;

        for (String part : id.split("[:+,\\-_/ ]+")) {
            if (part.isBlank()) {
                continue;
            }
            String word = part.trim().toUpperCase(Locale.ROOT);
            if (DATAPACK_WORDS.contains(word)) {
                datapack = true;
            } else if (Preset.isKnown(word)) {
                preset = Preset.parse(word);
            } else {
                recognised = false;
            }
        }

        return new GeneratorId(preset == null ? fallback : preset, datapack, recognised);
    }

    /** How this reads in a log line. */
    public String describe() {
        return datapackTerrain
                ? preset + " with terrain left to the game and its datapacks (no Arkcronist"
                        + " terrain, prefabs or structures)"
                : preset.toString();
    }
}
