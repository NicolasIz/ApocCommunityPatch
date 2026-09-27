package com.arkcronist.gen.bukkit.mythic;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Decides which mobs garrison a structure a datapack generated, from the structure's name.
 *
 * <p>The same idea as {@link BiomeThemes}, one level down. A structure pack names its buildings for
 * players - {@code incendium:forbidden_castle}, {@code ethrium:catacombs},
 * {@code ominous:arcane_spire}, {@code grim_kingdoms:glacierfall_keep} - and a mob pack names its
 * creatures the same way: {@code cursed_knight}, {@code skeleton_mage}, {@code spider_trapper}. A
 * castle wants the knights and the guards, a crypt the dead, a spire the casters. Nothing in either
 * pack says so; the words already do.</p>
 *
 * <p>A structure whose name matches no theme, or whose theme finds no mob, gets the dimension's
 * general mix - the same mobs as its surroundings, which is never wrong, only less specific.</p>
 *
 * <p>And some structures are not places for hostile mobs at all: a village, a cabin, a farm, a
 * statue. Structure packs ship plenty of those - Villages Revamped, Grim Kingdoms' cabins, Reds'
 * statues - and a goblin garrison in a village is a village with no villagers by morning. Those are
 * recognised by their words too, and left alone.</p>
 */
public final class StructureThemes {

    /** Words that mean a structure is somewhere people live or something to look at. */
    public static final Set<String> PEACEFUL = split(
            "village villages town hamlet settlement house houses home cabin cottage hut farm"
                    + " farmhouse mill windmill well statue statues ambient deforestation friendly"
                    + " church chapel inn tavern market shop stall garden orchard bridge lamp"
                    + " signpost road path");

    /**
     * Words that make a structure hostile whatever else its name says.
     *
     * <p>Read before {@link #PEACEFUL}, because a name can carry both and it is the hostile word that
     * says what is inside: a {@code haunted_house} is a house full of ghosts, a {@code witch_hut}
     * has a witch in it, and a {@code farm_pillager} is a farm the pillagers took. Trek alone ships
     * all three.</p>
     */
    static final Set<String> HOSTILE = split(
            "haunted pillager pillagers illager illagers witch witches bandit bandits cursed"
                    + " sorcerer sorcerers cultist cultists raider raiders evil undead necromancer"
                    + " ruined abandoned destroyed infested");

    /**
     * One kind of building.
     *
     * @param name           what {@code /ag mythic structures} prints as the reason
     * @param structureWords words in a structure's name that mean this kind of building
     * @param mobWords       words in a mob's name that belong in it
     */
    record Theme(String name, Set<String> structureWords, Set<String> mobWords) {
    }

    static final List<Theme> THEMES = List.of(
            new Theme("castillo",
                    split("castle keep fort fortress citadel bastion stronghold hold watch spur"
                            + " tower bulwark garrison barracks palace throne kingdom kingdoms"),
                    split("knight knights guard guards guardian soldier paladin warrior footman"
                            + " swordman swordsman halberdier sentinel templar squire tank champion"
                            + " lancer spearman")),
            new Theme("cripta",
                    split("crypt crypts catacomb catacombs tomb tombs grave graves graveyard"
                            + " cemetery mausoleum necropolis ossuary sanctum barrow haunted"),
                    split("skeleton skeletons skeletal zombie zombies undead ghoul lich wraith"
                            + " mummy revenant bone bones skull ghost ghosts spirit specter spectre"
                            + " phantom banshee dead")),
            new Theme("arcano",
                    split("tower spire spires arcane library lab laboratory altar oracle reactor"
                            + " observatory academy mage wizard witch sorcerer sorcerers"),
                    split("mage mages wizard witch warlock sorcerer sorceress cultist cult shaman"
                            + " necromancer priest caster imp arcane enchanter")),
            new Theme("bandidos",
                    split("camp camps outpost ship pillager illager bandit bandits raid raider"
                            + " hideout encampment"),
                    split("bandit bandits pillager raider marauder goblin goblins orc orcs brute"
                            + " archer ranger cursed thief rogue whip")),
            new Theme("guarida",
                    split("nest lair den cave hive pit burrow web"),
                    split("spider spiders beast hound wolf insect crawler mite worm trapper"
                            + " poison")),
            new Theme("templo",
                    split("pyramid sphinx temple pantheon patheon shrine ziggurat ruins ruin"),
                    split("mummy pharaoh anubis scorpion cultist guardian priest")));

    /**
     * The mobs for one structure and why.
     *
     * @param mobs the names to draw from; never empty unless there was nothing to draw from at all
     * @param why  what decided it, for the report
     */
    public record Pick(List<String> mobs, String why) {
    }

    private StructureThemes() {
    }

    /**
     * Whether a structure is somewhere hostile mobs have no business being.
     *
     * @param key      the structure's key, e.g. {@code grim_kingdoms:nordic_cabin1}
     * @param peaceful the words that mean so, lower case
     */
    public static boolean peaceful(String key, Set<String> peaceful) {
        Set<String> words = words(key);
        for (String word : words) {
            if (HOSTILE.contains(word)) {
                return false;
            }
        }
        for (String word : words) {
            if (peaceful.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Which mobs garrison this structure.
     *
     * @param key     the structure's key
     * @param themed  every hostile of this dimension a theme may choose from
     * @param general the dimension's general mix, for when no theme speaks
     */
    public static Pick pick(String key, List<String> themed, List<String> general) {
        Set<String> structure = words(key);
        List<Theme> matched = new ArrayList<>();
        for (Theme theme : THEMES) {
            for (String word : structure) {
                if (theme.structureWords().contains(word)) {
                    matched.add(theme);
                    break;
                }
            }
        }
        if (matched.isEmpty()) {
            return new Pick(general, "su nombre no dice que es: mezcla general");
        }
        List<String> chosen = new ArrayList<>();
        Set<String> reasons = new LinkedHashSet<>();
        for (String mob : themed) {
            Set<String> mobWords = new LinkedHashSet<>(MobClassifier.words(mob));
            for (Theme theme : matched) {
                if (intersects(mobWords, theme.mobWords())) {
                    if (!chosen.contains(mob)) {
                        chosen.add(mob);
                    }
                    reasons.add(theme.name());
                }
            }
        }
        List<String> names = new ArrayList<>();
        for (Theme theme : matched) {
            names.add(theme.name());
        }
        if (chosen.isEmpty()) {
            return new Pick(general, String.join(", ", names)
                    + ": ningun mob encaja, mezcla general");
        }
        return new Pick(List.copyOf(chosen), String.join(", ", reasons));
    }

    /** A structure key's words, lower case, without its namespace and without digits. */
    static Set<String> words(String key) {
        if (key == null) {
            return Set.of();
        }
        String path = key.toLowerCase(Locale.ROOT);
        int colon = path.indexOf(':');
        if (colon >= 0) {
            path = path.substring(colon + 1);
        }
        Set<String> words = new LinkedHashSet<>();
        for (String part : path.split("[^a-z]+")) {
            if (!part.isEmpty()) {
                words.add(part);
            }
        }
        return words;
    }

    private static boolean intersects(Set<String> a, Set<String> b) {
        for (String word : a) {
            if (b.contains(word)) {
                return true;
            }
        }
        return false;
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
