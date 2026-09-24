package com.arkcronist.gen.bukkit.mythic;

import com.arkcronist.gen.bukkit.mythic.MobClassifier.Habitat;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What can be known about one biome without standing in it: its name, the tags a datapack filed it
 * under, and its climate when a file said so.
 *
 * <p>The name carries most of the weight, and that is not a shortcut. Biome authors name biomes for
 * players - {@code snowy_taiga}, {@code terralith:sakura_grove}, {@code terralith:cave/frostfire_caves}
 * - so the words in a key are the same words a mob pack uses for the same place. Tags and climate are
 * there for the biomes whose names do not say enough: {@code terralith:alpine_grove} is snowy, and
 * only its temperature says so.</p>
 *
 * @param key         the namespaced key, lower case, e.g. {@code terralith:alpine_grove}
 * @param words       the words in the key's path, plus each adjacent pair joined ({@code darkforest})
 * @param tags        tag names without their namespace ({@code is_forest}, {@code is_snowy})
 * @param temperature the biome's temperature, or NaN when nothing said
 * @param downfall    the biome's downfall, or NaN when nothing said
 * @param habitat     which dimension the biome belongs to
 */
public record BiomeProfile(String key, Set<String> words, Set<String> tags, double temperature,
                           double downfall, Habitat habitat) {

    public BiomeProfile {
        key = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        words = words == null ? Set.of() : Set.copyOf(words);
        tags = tags == null ? Set.of() : Set.copyOf(tags);
        habitat = habitat == null ? Habitat.OVERWORLD : habitat;
    }

    /** A profile built from a key alone, with the words read out of it. */
    public static BiomeProfile of(String key, Set<String> tags, double temperature, double downfall,
                                  Habitat habitat) {
        return new BiomeProfile(key, wordsOf(key), tags, temperature, downfall, habitat);
    }

    /** Whether a file gave this biome a temperature. */
    public boolean climateKnown() {
        return !Double.isNaN(temperature);
    }

    /** The key without its namespace: {@code alpine_grove} for {@code terralith:alpine_grove}. */
    public String path() {
        int colon = key.indexOf(':');
        return colon < 0 ? key : key.substring(colon + 1);
    }

    /**
     * The words in a biome key's path, with every adjacent pair also joined.
     *
     * <p>The pairs are for the places whose meaning is in two words together: {@code dark_forest}
     * is a dark-oak wood, which neither "dark" nor "forest" says alone, and a pack writes the same
     * place as {@code dark_oak}. Joining both sides the same way lets them meet.</p>
     */
    public static Set<String> wordsOf(String key) {
        if (key == null || key.isBlank()) {
            return Set.of();
        }
        String path = key.toLowerCase(Locale.ROOT);
        int colon = path.indexOf(':');
        if (colon >= 0) {
            path = path.substring(colon + 1);
        }
        return withPairs(List.of(path.split("[^a-z0-9]+")));
    }

    /** Words and each adjacent pair joined, in order, empties dropped. */
    static Set<String> withPairs(List<String> parts) {
        Set<String> words = new LinkedHashSet<>();
        String previous = null;
        for (String part : parts) {
            if (part == null || part.isEmpty()) {
                continue;
            }
            words.add(part);
            if (previous != null) {
                words.add(previous + part);
            }
            previous = part;
        }
        return words;
    }
}
