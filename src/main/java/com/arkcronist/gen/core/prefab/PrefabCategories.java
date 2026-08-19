package com.arkcronist.gen.core.prefab;

import com.arkcronist.gen.core.biome.StructureTag;

import java.util.List;
import java.util.Locale;

/**
 * What each prefab folder means.
 *
 * <p>The folder name is the whole interface. Putting a schematic in {@code prefabs/castles/} makes
 * it a castle: it inherits the biomes castles are allowed in, the grid castles are placed on and,
 * unless told otherwise, it takes the place of the generator's own procedural castle so a world does
 * not end up with two competing kinds. Nothing has to be registered and nothing recompiled.</p>
 *
 * <p>A folder that is not on this list still loads - it just has no placement rule of its own, which
 * is the right behaviour for a folder of parts that some other system draws from.</p>
 */
public final class PrefabCategories {

    /**
     * @param folder    directory name under {@code prefabs/}
     * @param tag       structure family the prefabs claim, which decides the biomes they appear in
     * @param replaces  id of the built-in structure that stands down when this folder has files,
     *                  or null when the category only ever adds to the world
     * @param weight    selection weight against the other structures a biome allows
     */
    public record Rule(String folder, StructureTag tag, String replaces, double weight) {
    }

    /**
     * Folders whose contents are assembled by another structure rather than placed on their own.
     *
     * <p>{@code houses} is the one that matters: a file there is one building, and the village
     * builder decides how many of them stand where.</p>
     */
    public static final String HOUSES = "houses";

    private static final List<Rule> RULES = List.of(
            new Rule("castles", StructureTag.CASTLE, "castle", 1.4),
            new Rule("cities", StructureTag.CITY, "city", 1.2),
            new Rule("villages", StructureTag.VILLAGE, "village", 2.2),
            new Rule("fortresses", StructureTag.FORTRESS, "fortress", 1.3),
            new Rule("outposts", StructureTag.OUTPOST, "outpost", 1.4),
            new Rule("towers", StructureTag.TOWER, "tower", 1.8),
            new Rule("battle_towers", StructureTag.BATTLE_TOWER, "battle_tower", 1.6),
            new Rule("temples", StructureTag.TEMPLE, "temple", 1.5),
            new Rule("camps", StructureTag.CAMP, "camp", 1.6),
            new Rule("mansions", StructureTag.MANSION, "mansion", 1.0),
            // Landmarks are additive: they have no procedural counterpart to replace.
            new Rule("ruins", StructureTag.PREFAB_RUIN, null, 0.9));

    private PrefabCategories() {
    }

    public static List<Rule> rules() {
        return RULES;
    }

    /** The rule for a folder, or null when the folder has no placement of its own. */
    public static Rule forFolder(String folder) {
        String name = folder.toLowerCase(Locale.ROOT);
        for (Rule rule : RULES) {
            if (rule.folder().equals(name)) {
                return rule;
            }
        }
        return null;
    }

    /** Structure id used for a category's prefab-backed placement. */
    public static String structureId(String folder) {
        return "prefab_" + folder.toLowerCase(Locale.ROOT);
    }
}
