package com.arkcronist.gen.bukkit.mythic;

import com.arkcronist.gen.bukkit.mythic.MobClassifier.Habitat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * Decides which biomes a mob belongs in, from its name and what the biomes are.
 *
 * <h2>Why a name</h2>
 *
 * <p>A pack author sorts mobs by place long before any plugin reads them: {@code frost_yeti},
 * {@code sakura_tree_ent}, {@code swamp_hag}, {@code desert_mummy}. The place is already written down;
 * it only has to be matched against the world's biomes, whose names are written in the same words -
 * {@code snowy_taiga}, {@code terralith:sakura_grove}, {@code mangrove_swamp}. Where a biome's name
 * does not say it, its tags or its temperature do: {@code terralith:alpine_grove} is snowy, and only
 * the {@code c:is_snowy} tag and a temperature below freezing say so.</p>
 *
 * <h2>How it chooses</h2>
 *
 * <p>Each theme is a set of words a mob may carry and a test a biome may pass. A mob earns a theme's
 * weight in every biome that passes that theme's test, plus a little for any word it shares outright
 * with a biome's name. The biomes that score highest are the mob's; ties are all kept, so an ice mob
 * gets every cold biome and a mob that is both ice and mountain gets the cold mountains when there
 * are any. Specific woods weigh more than "forest", so a sakura ent lands in the cherry groves rather
 * than in every wood.</p>
 *
 * <h2>What it will not do</h2>
 *
 * <p>A mob whose name says nothing about a place is left alone: a goblin still turns up everywhere,
 * exactly as before. So is a mob whose words match most of the world, because a list that covers
 * nearly everything is not a place. And a mob is only ever matched against biomes of its own
 * dimension - a Nether creature is never offered in a snowy forest because its name contained
 * "soul".</p>
 */
public final class BiomeThemes {

    /**
     * One kind of place.
     *
     * @param name      what {@code /ag mythic} prints as the reason
     * @param weight    how much it counts; specific places count more than broad ones
     * @param mobWords  words in a mob's name that point here
     * @param biomeWords words in a biome's name that mean this place
     * @param tags      tag words that mean this place (any segment of a tag's path)
     * @param climate   a climate test for biomes whose name and tags say nothing, or null
     */
    record Theme(String name, int weight, Set<String> mobWords, Set<String> biomeWords,
                 Set<String> tags, Predicate<BiomeProfile> climate) {

        boolean fits(BiomeProfile biome) {
            for (String word : biome.words()) {
                if (biomeWords.contains(word)) {
                    return true;
                }
            }
            for (String tag : biome.tags()) {
                if (tags.contains(tag)) {
                    return true;
                }
            }
            return climate != null && biome.climateKnown() && climate.test(biome);
        }
    }

    private static Theme theme(String name, int weight, String mobWords, String biomeWords,
                               String tags, Predicate<BiomeProfile> climate) {
        return new Theme(name, weight, split(mobWords), split(biomeWords), split(tags), climate);
    }

    /** Every place this knows how to recognise. */
    static final List<Theme> THEMES = List.of(
            theme("frio", 1,
                    "snow snowy ice icy frost frosty frozen freeze cold winter yeti glacier glacial"
                            + " polar tundra blizzard arctic penguin iceologer frostmite wendigo"
                            + " snowman snowball chill hail sleet boreas",
                    "snow snowy ice icy frozen frost frosty glacier glacial tundra arctic polar"
                            + " cold winter wintry permafrost siberian",
                    "is_snowy is_icy is_cold frozen_all frozen_with_structures",
                    biome -> biome.temperature() <= 0.15),
            theme("desierto", 2,
                    "desert sand sandy dune dunes scorpion mummy pharaoh sphinx cactus oasis nomad"
                            + " sandstorm anubis sandworm",
                    "desert dune dunes sand sandy sands oasis oases arid",
                    "is_desert desert_all desert_with_structures oases", null),
            theme("meseta", 2,
                    "mesa badlands canyon cowboy outlaw",
                    "badlands mesa canyon",
                    "is_badlands badlands_all badlands_with_structures", null),
            theme("selva", 2,
                    "jungle tropical tropic monkey ape gorilla jaguar panther parrot rainforest"
                            + " amazon tiki",
                    "jungle tropical rainforest",
                    "is_jungle jungle", null),
            theme("pantano", 2,
                    "swamp bog marsh mire fen bayou toad frog leech blight blightwood sludge"
                            + " mangrove bogling croc crocodile alligator gator",
                    "swamp bog marsh mire fen bayou mangrove wetland wetlands",
                    "is_swamp swamp", null),
            theme("bosque", 1,
                    "forest tree trees ent entling treant wood woods woodland grove oak dryad elf"
                            + " elves elven sylvan druid fairy pixie bark timber lumber lumberjack"
                            + " leaf leaves thorn",
                    "forest forests woods woodland woodlands grove thicket orchard",
                    "is_forest forest", null),
            theme("cerezo", 3,
                    "sakura cherry blossom",
                    "sakura cherry blossom blooming", "", null),
            theme("abedul", 3,
                    "birch aspen",
                    "birch aspen", "is_birch_forest", null),
            theme("roble_oscuro", 3,
                    "darkoak darkwood",
                    "darkforest darkoak pale", "", null),
            theme("taiga", 2,
                    "taiga spruce pine conifer boreal",
                    "taiga spruce pine conifer boreal",
                    "is_taiga taiga coniferous", null),
            theme("montana", 1,
                    "mountain mountains peak peaks cliff cliffs crag rock rocks stone dwarf dwarven"
                            + " troll alpine highland highlands ridge summit goat",
                    "mountain mountains peak peaks cliff cliffs crag crags highland highlands alpine"
                            + " ridge summit slopes spires stony rocky plateau windswept",
                    "is_mountain is_hill mountain mountain_peak mountain_slope", null),
            theme("oceano", 1,
                    "ocean sea marine shark kraken pirate siren mermaid merfolk drowned coral tide"
                            + " tidal reef crab naga fish squid octopus sailor",
                    "ocean sea reef coral beach shore coast coastal island islands isles lagoon",
                    "is_ocean is_deep_ocean is_beach", null),
            theme("rio", 1,
                    "river lake",
                    "river lake stream",
                    "is_river", null),
            theme("sabana", 2,
                    "savanna lion hyena safari rhino elephant giraffe zebra",
                    "savanna savannah shrubland steppe brushland",
                    "is_savanna savanna", null),
            theme("cueva", 1,
                    "cave caves cavern underground miner crystal dripstone burrow tunnel geode"
                            + " subterranean",
                    "cave caves cavern caverns underground dripstone geode deepdark",
                    "is_cave cave deep_cave is_underground", null),
            theme("setas", 2,
                    "mushroom fungus fungal spore spores myconid shroom toadstool",
                    "mushroom fungal shroom fungus",
                    "is_mushroom", null),
            theme("llanura", 1,
                    "plains meadow prairie grassland scarecrow",
                    "plains meadow prairie grassland fields steppe",
                    "is_plains plains", null),
            theme("volcan", 2,
                    "volcano volcanic",
                    "volcano volcanic caldera crater yellowstone", "", null),
            theme("maldito", 1,
                    "haunted ghost ghosts ghoul wraith specter spectre phantom banshee revenant",
                    "haunted cursed dead",
                    "is_spooky is_dead is_wasteland", null),
            theme("magico", 1,
                    "arcane mystic mystical magic magical enchanted fae crystal amethyst",
                    "amethyst mystic moonlight enchanted",
                    "is_magical mystical", null),
            // The Nether. Only ever offered to mobs judged to live there.
            theme("carmesi", 3, "crimson", "crimson", "", null),
            theme("distorsionado", 3, "warped", "warped", "", null),
            theme("almas", 2, "soul soulsand souls", "soul", "", null),
            theme("basalto", 3, "basalt delta deltas", "basalt", "", null));

    /**
     * Words too common in mob names to mean a place when they happen to match a biome's name.
     * Only the direct word match uses this; themes list their words on purpose.
     */
    private static final Set<String> COMMON = split(
            "warrior knight archer mage melee ranger elite brute small large giant great spirit"
                    + " beast monster creature minion skeleton zombie spider goblin guard guardian"
                    + " hunter shaman priest witch lord king queen boss mini minor major young elder"
                    + " ancient");

    /** Every word any theme reads in a mob's name. */
    private static final Set<String> THEME_WORDS = themeWords();

    private static Set<String> themeWords() {
        Set<String> all = new LinkedHashSet<>();
        for (Theme theme : THEMES) {
            all.addAll(theme.mobWords());
        }
        return Set.copyOf(all);
    }

    /** Share of a dimension's biomes above which a match is too broad to be a place. */
    static final double TOO_BROAD = 0.6;

    /**
     * Where one mob goes and why.
     *
     * @param biomes the biome keys it was given; empty means "anywhere, as before"
     * @param why    the themes or words that decided it, for {@code /ag mythic}
     */
    public record Placement(List<String> biomes, String why) {
    }

    /** A mob to place: its name, what it is called on screen, and its dimension. */
    public record Candidate(String name, String displayName, Habitat habitat) {
    }

    /** The answer for a whole catalogue. */
    public record Result(Map<String, Placement> byMob, Map<String, List<String>> byBiome,
                         int biomesRead) {

        public static final Result NONE = new Result(Map.of(), Map.of(), 0);

        /** The mobs that were given a place, and so are not spawned anywhere else. */
        public Set<String> themed() {
            Set<String> names = new LinkedHashSet<>();
            byMob.forEach((name, placement) -> {
                if (!placement.biomes().isEmpty()) {
                    names.add(name);
                }
            });
            return names;
        }
    }

    private BiomeThemes() {
    }

    /**
     * Places every candidate.
     *
     * @param mobs      the hostile mobs to place
     * @param biomes    every biome known, from any source
     * @param overrides mob name to the biomes an operator chose; {@code ANY} keeps a mob everywhere
     */
    public static Result assign(List<Candidate> mobs, List<BiomeProfile> biomes,
                                Map<String, List<String>> overrides) {
        Map<String, Placement> byMob = new LinkedHashMap<>();
        Map<String, List<String>> byBiome = new TreeMap<>();
        for (Candidate mob : mobs) {
            Placement placement = place(mob, biomes, overrides);
            byMob.put(mob.name(), placement);
            for (String key : placement.biomes()) {
                byBiome.computeIfAbsent(key, k -> new ArrayList<>()).add(mob.name());
            }
        }
        Map<String, List<String>> frozen = new LinkedHashMap<>();
        byBiome.forEach((key, names) -> frozen.put(key, List.copyOf(names)));
        return new Result(Map.copyOf(byMob), Map.copyOf(frozen), biomes.size());
    }

    /** Where one mob goes. */
    static Placement place(Candidate mob, List<BiomeProfile> biomes,
                           Map<String, List<String>> overrides) {
        List<String> chosen = override(overrides, mob.name());
        if (chosen != null) {
            if (chosen.size() == 1 && chosen.get(0).equalsIgnoreCase("ANY")) {
                return new Placement(List.of(), "set to ANY in config");
            }
            List<String> keys = new ArrayList<>();
            for (String name : chosen) {
                String key = name.trim().toLowerCase(Locale.ROOT);
                keys.add(key.contains(":") ? key : "minecraft:" + key);
            }
            return new Placement(List.copyOf(keys), "set in config");
        }

        Set<String> words = mobWords(mob.name() + " " + mob.displayName());
        List<Theme> themes = new ArrayList<>();
        for (Theme theme : THEMES) {
            for (String word : words) {
                if (theme.mobWords().contains(word)) {
                    themes.add(theme);
                    break;
                }
            }
        }

        List<BiomeProfile> here = new ArrayList<>();
        for (BiomeProfile biome : biomes) {
            if (biome.habitat() == mob.habitat()) {
                here.add(biome);
            }
        }
        if (here.isEmpty()) {
            return new Placement(List.of(), "no biomes known in " + mob.habitat());
        }

        int best = 0;
        Map<String, Integer> scores = new LinkedHashMap<>();
        Map<String, Set<String>> reasons = new LinkedHashMap<>();
        for (BiomeProfile biome : here) {
            int score = 0;
            Set<String> because = new LinkedHashSet<>();
            for (Theme theme : themes) {
                if (theme.fits(biome)) {
                    score += theme.weight();
                    because.add(theme.name());
                }
            }
            for (String word : words) {
                // A word a theme already reads is counted by the theme; counting it again here
                // would pull an ice mob to the three biomes called "frozen" and away from the
                // forty that are frozen without saying so.
                if (word.length() >= 5 && !COMMON.contains(word) && !THEME_WORDS.contains(word)
                        && biome.words().contains(word)) {
                    score += 2;
                    because.add("'" + word + "'");
                }
            }
            if (score > 0) {
                scores.put(biome.key(), score);
                reasons.put(biome.key(), because);
                best = Math.max(best, score);
            }
        }
        if (best == 0) {
            return new Placement(List.of(), "its name names no place");
        }
        List<String> keys = new ArrayList<>();
        Set<String> why = new LinkedHashSet<>();
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            if (entry.getValue() == best) {
                keys.add(entry.getKey());
                why.addAll(reasons.get(entry.getKey()));
            }
        }
        if (keys.size() > here.size() * TOO_BROAD) {
            return new Placement(List.of(), "its name (" + String.join(", ", why)
                    + ") fits " + keys.size() + " of " + here.size()
                    + " biomes, which is everywhere");
        }
        return new Placement(List.copyOf(keys), String.join(", ", why));
    }

    /** A mob name's words, lower case, with each adjacent pair also joined. */
    static Set<String> mobWords(String text) {
        return BiomeProfile.withPairs(MobClassifier.words(text));
    }

    private static List<String> override(Map<String, List<String>> overrides, String name) {
        if (overrides == null || overrides.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, List<String>> entry : overrides.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name) && entry.getValue() != null
                    && !entry.getValue().isEmpty()) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static Set<String> split(String words) {
        Set<String> set = new LinkedHashSet<>();
        for (String word : words.trim().split("\\s+")) {
            if (!word.isEmpty()) {
                set.add(word);
            }
        }
        return Set.copyOf(set);
    }
}
