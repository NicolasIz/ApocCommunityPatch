package com.arkcronist.gen.bukkit.mythic;

import com.arkcronist.gen.bukkit.mythic.MobClassifier.Habitat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The game's own biomes, with the temperature each one is defined with.
 *
 * <p>A datapack's biomes are read from its files; the game's are not in any file a plugin can reach
 * without opening the server jar, which moves between versions. They change rarely enough to keep
 * here, and a biome missing from this list is not lost - it still arrives from the server's registry,
 * named but without a climate, and its name is usually enough.</p>
 */
public final class VanillaBiomes {

    private VanillaBiomes() {
    }

    private static final Object[][] OVERWORLD = {
            {"plains", 0.8}, {"sunflower_plains", 0.8}, {"snowy_plains", 0.0}, {"ice_spikes", 0.0},
            {"desert", 2.0}, {"swamp", 0.8}, {"mangrove_swamp", 0.8}, {"forest", 0.7},
            {"flower_forest", 0.7}, {"birch_forest", 0.6}, {"dark_forest", 0.7},
            {"pale_garden", 0.7}, {"old_growth_birch_forest", 0.6}, {"old_growth_pine_taiga", 0.3},
            {"old_growth_spruce_taiga", 0.25}, {"taiga", 0.25}, {"snowy_taiga", -0.5},
            {"savanna", 2.0}, {"savanna_plateau", 2.0}, {"windswept_hills", 0.2},
            {"windswept_gravelly_hills", 0.2}, {"windswept_forest", 0.2}, {"windswept_savanna", 2.0},
            {"jungle", 0.95}, {"sparse_jungle", 0.95}, {"bamboo_jungle", 0.95}, {"badlands", 2.0},
            {"eroded_badlands", 2.0}, {"wooded_badlands", 2.0}, {"meadow", 0.5},
            {"cherry_grove", 0.5}, {"grove", -0.2}, {"snowy_slopes", -0.3}, {"frozen_peaks", -0.7},
            {"jagged_peaks", -0.7}, {"stony_peaks", 1.0}, {"river", 0.5}, {"frozen_river", 0.0},
            {"beach", 0.8}, {"snowy_beach", 0.05}, {"stony_shore", 0.2}, {"warm_ocean", 0.5},
            {"lukewarm_ocean", 0.5}, {"deep_lukewarm_ocean", 0.5}, {"ocean", 0.5},
            {"deep_ocean", 0.5}, {"cold_ocean", 0.5}, {"deep_cold_ocean", 0.5},
            {"frozen_ocean", 0.0}, {"deep_frozen_ocean", 0.5}, {"mushroom_fields", 0.9},
            {"dripstone_caves", 0.8}, {"lush_caves", 0.5}, {"deep_dark", 0.8}};

    private static final String[] NETHER = {
            "nether_wastes", "crimson_forest", "warped_forest", "soul_sand_valley", "basalt_deltas"};

    private static final String[] END = {
            "the_end", "end_highlands", "end_midlands", "small_end_islands", "end_barrens"};

    /** Every vanilla biome this knows, as profiles. */
    public static List<BiomeProfile> all() {
        List<BiomeProfile> all = new ArrayList<>();
        for (Object[] row : OVERWORLD) {
            all.add(BiomeProfile.of("minecraft:" + row[0], Set.of(), (Double) row[1], Double.NaN,
                    Habitat.OVERWORLD));
        }
        for (String name : NETHER) {
            all.add(BiomeProfile.of("minecraft:" + name, Set.of(), 2.0, 0.0, Habitat.NETHER));
        }
        for (String name : END) {
            all.add(BiomeProfile.of("minecraft:" + name, Set.of(), 0.5, 0.5, Habitat.END));
        }
        return all;
    }

    /** Which dimension a vanilla biome is in, or null when it is not one of the game's. */
    public static Habitat habitatOf(String key) {
        for (BiomeProfile profile : all()) {
            if (profile.key().equals(key)) {
                return profile.habitat();
            }
        }
        return null;
    }
}
