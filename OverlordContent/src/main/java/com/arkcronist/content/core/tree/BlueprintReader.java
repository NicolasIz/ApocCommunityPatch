package com.arkcronist.content.core.tree;

import com.arkcronist.content.core.block.BlockAxis;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 5.5: reads {@code tree: blueprints:} - a list of drawings, each its layers of characters from the
 * bottom up and a legend saying what each character is.
 *
 * <pre>
 * blueprints:
 *   - weight: 2              # picked twice as often as a blueprint of weight 1
 *     rotate: true           # turned a random quarter turn (default)
 *     mirror: true           # and mirrored at random (default)
 *     origin: [2, 2]         # column and row of the sapling in the bottom layer; else its centre-most log
 *     legend:
 *       "#": log             # the tree's log, upright
 *       "-": log-x           # lying east to west
 *       "|": log-z           # lying north to south
 *       "@": leaves          # the tree's leaves
 *       "%": { block: leaves, chance: 0.6 }
 *       "v": woodland:wisteria_blossom
 *       "~": minecraft:vine[north=true]
 *     layers:
 *       - |
 *         .....
 *         ..#..
 *         .....
 *       - [ ".@@@.", "@@#@@", ".@@@." ]   # rows as a list works too
 * </pre>
 *
 * <p>In a layer, a row runs west to east and the rows north to south; {@code .} and spaces are no
 * block. A blueprint that cannot be read is left out, and why is said; the others are kept.</p>
 */
public final class BlueprintReader {

    private BlueprintReader() {
    }

    /** What one legend character stands for. */
    private record Key(String block, BlockAxis axis, double chance) {
    }

    /**
     * @param namespace the pack's: a bare block name in a legend is its block of that name, or - with
     *                  none - vanilla's, as for a tree's log and leaves
     */
    public static List<TreeBlueprint> read(@Nullable Object value, String namespace, String where,
                                           List<String> problems) {
        if (value == null) {
            return List.of();
        }
        List<?> entries = value instanceof List<?> list ? list : List.of(value);
        List<TreeBlueprint> blueprints = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            String at = entries.size() > 1 ? where + "#" + (i + 1) + ": " : where;
            if (!(entries.get(i) instanceof Map<?, ?> section)) {
                problems.add(at + "should be a section with legend and layers; left out");
                continue;
            }
            TreeBlueprint blueprint = blueprint(section, namespace, at, problems);
            if (blueprint != null) {
                blueprints.add(blueprint);
            }
        }
        return blueprints;
    }

    private static @Nullable TreeBlueprint blueprint(Map<?, ?> section, String namespace, String at,
                                                     List<String> problems) {
        Map<Character, Key> legend = legend(section.get("legend"), namespace, at + "legend: ", problems);
        if (legend.isEmpty()) {
            problems.add(at + "needs a legend - which block each character is; left out");
            return null;
        }
        List<List<String>> layers = layers(section.get("layers"), at, problems);
        if (layers.isEmpty()) {
            problems.add(at + "needs layers, from the bottom up; left out");
            return null;
        }
        // Every character's block, by position in the drawing: x the column, z the row.
        record Drawn(int x, int y, int z, Key key) {
        }
        List<Drawn> drawn = new ArrayList<>();
        Set<Character> unknown = new LinkedHashSet<>();
        for (int y = 0; y < layers.size(); y++) {
            List<String> rows = layers.get(y);
            for (int z = 0; z < rows.size(); z++) {
                String row = rows.get(z);
                for (int x = 0; x < row.length(); x++) {
                    char c = row.charAt(x);
                    if (c == '.' || c == ' ') {
                        continue;
                    }
                    Key key = legend.get(c);
                    if (key == null) {
                        unknown.add(c);
                        continue;
                    }
                    drawn.add(new Drawn(x, y, z, key));
                }
            }
        }
        if (!unknown.isEmpty()) {
            problems.add(at + "characters not in the legend, left out: " + unknown);
        }
        int[] origin = origin(section.get("origin"), at, problems);
        if (origin == null) {
            // The centre-most log of the bottom layer.
            double cx = 0;
            double cz = 0;
            int counted = 0;
            for (Drawn cell : drawn) {
                if (cell.y() == 0) {
                    cx += cell.x();
                    cz += cell.z();
                    counted++;
                }
            }
            double best = Double.MAX_VALUE;
            for (Drawn cell : drawn) {
                if (cell.y() == 0 && cell.key().block().equals(TreeSpec.LOG) && counted > 0) {
                    double d = Math.pow(cell.x() - cx / counted, 2) + Math.pow(cell.z() - cz / counted, 2);
                    if (d < best) {
                        best = d;
                        origin = new int[]{cell.x(), cell.z()};
                    }
                }
            }
            if (origin == null) {
                problems.add(at + "needs a log in its bottom layer - the sapling's block - or an origin:; left out");
                return null;
            }
        }
        List<TreeBlueprint.Cell> cells = new ArrayList<>();
        boolean rooted = false;
        for (Drawn cell : drawn) {
            int dx = cell.x() - origin[0];
            int dz = cell.z() - origin[1];
            boolean root = dx == 0 && cell.y() == 0 && dz == 0;
            rooted |= root;
            // The sapling's own block is always the tree's: never left to chance.
            cells.add(new TreeBlueprint.Cell(dx, cell.y(), dz, cell.key().block(), cell.key().axis(),
                    root ? 1 : cell.key().chance()));
        }
        if (!rooted) {
            problems.add(at + "origin " + origin[0] + ", " + origin[1] + " has no block in the bottom layer - the"
                    + " sapling's block must be one of the tree's; left out");
            return null;
        }
        if (cells.stream().noneMatch(cell -> cell.block().equals(TreeSpec.LOG))) {
            problems.add(at + "has no log - a tree needs wood; left out");
            return null;
        }
        TreeBlueprint blueprint = new TreeBlueprint((int) number(section, "weight", 1, 1, 1000, at, problems),
                flag(section, "rotate", true, at, problems), flag(section, "mirror", true, at, problems), cells);
        if (blueprint.reach() > TreeSpec.MAX_BLUEPRINT_REACH) {
            problems.add(at + "reaches " + blueprint.reach() + " blocks from its sapling - at most "
                    + TreeSpec.MAX_BLUEPRINT_REACH + ", so a tree in a new chunk stays within the chunks round it;"
                    + " left out");
            return null;
        }
        if (blueprint.height() > TreeSpec.MAX_HEIGHT) {
            problems.add(at + "is " + blueprint.height() + " layers tall - at most " + TreeSpec.MAX_HEIGHT + "; left out");
            return null;
        }
        return blueprint;
    }

    private static Map<Character, Key> legend(@Nullable Object value, String namespace, String at,
                                              List<String> problems) {
        Map<Character, Key> legend = new HashMap<>();
        if (!(value instanceof Map<?, ?> section)) {
            if (value != null) {
                problems.add(at + "should be a section: a character, and the block it is");
            }
            return legend;
        }
        for (Map.Entry<?, ?> entry : section.entrySet()) {
            String character = String.valueOf(entry.getKey());
            if (character.length() != 1 || character.equals(".") || character.equals(" ")) {
                problems.add(at + "'" + character + "' should be one character, not . or a space (no block); left out");
                continue;
            }
            String block;
            double chance = 1;
            String axis = null;
            if (entry.getValue() instanceof Map<?, ?> detail) {
                block = text(detail.get("block"));
                chance = number(detail, "chance", 1, 0, 1, at + character + ": ", problems);
                axis = text(detail.get("axis"));
            } else {
                block = text(entry.getValue());
            }
            if (block == null) {
                problems.add(at + character + ": needs a block; left out");
                continue;
            }
            Key key = key(block.trim(), axis, chance, namespace, at + character + ": ", problems);
            if (key != null) {
                legend.put(character.charAt(0), key);
            }
        }
        return legend;
    }

    private static @Nullable Key key(String raw, @Nullable String rawAxis, double chance, String namespace, String at,
                                     List<String> problems) {
        String name = raw.toLowerCase(Locale.ROOT);
        BlockAxis axis = BlockAxis.Y;
        if (name.equals(TreeSpec.LOG) || name.startsWith(TreeSpec.LOG + "-")) {
            String way = rawAxis != null ? rawAxis.trim().toLowerCase(Locale.ROOT)
                    : name.length() > TreeSpec.LOG.length() ? name.substring(TreeSpec.LOG.length() + 1) : "y";
            switch (way) {
                case "y", "up", "upright" -> axis = BlockAxis.Y;
                case "x", "east", "west" -> axis = BlockAxis.X;
                case "z", "north", "south" -> axis = BlockAxis.Z;
                default -> {
                    problems.add(at + "'" + way + "' is no way for a log to lie (x, y or z); upright");
                    axis = BlockAxis.Y;
                }
            }
            return new Key(TreeSpec.LOG, axis, chance);
        }
        if (rawAxis != null) {
            problems.add(at + "axis: only the tree's log lies along an axis - ignored");
        }
        if (name.equals(TreeSpec.LEAVES)) {
            return new Key(TreeSpec.LEAVES, axis, chance);
        }
        // A block of its own: a bare name is the pack's, as the tree's log and leaves are; its state kept as written.
        int state = name.indexOf('[');
        String id = state < 0 ? name : name.substring(0, state);
        if (id.isBlank() || (state >= 0 && !name.endsWith("]"))) {
            problems.add(at + "'" + raw + "' is not a block; left out");
            return null;
        }
        String full = (id.contains(":") ? id : namespace + ":" + id) + (state < 0 ? "" : name.substring(state));
        return new Key(full, axis, chance);
    }

    private static List<List<String>> layers(@Nullable Object value, String at, List<String> problems) {
        List<List<String>> layers = new ArrayList<>();
        if (!(value instanceof List<?> list)) {
            if (value != null) {
                problems.add(at + "layers: should be a list, from the bottom up");
            }
            return layers;
        }
        for (Object layer : list) {
            List<String> rows = new ArrayList<>();
            if (layer instanceof List<?> lines) {
                lines.forEach(line -> rows.add(line == null ? "" : String.valueOf(line)));
            } else if (layer != null) {
                for (String line : String.valueOf(layer).split("\n", -1)) {
                    rows.add(line.stripTrailing());
                }
                // A block scalar ends in a line break: the empty row after it is no row.
                while (!rows.isEmpty() && rows.get(rows.size() - 1).isEmpty()) {
                    rows.remove(rows.size() - 1);
                }
            }
            layers.add(rows);
        }
        return layers;
    }

    private static @Nullable int[] origin(@Nullable Object value, String at, List<String> problems) {
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> pair && pair.size() == 2 && pair.get(0) instanceof Number x
                && pair.get(1) instanceof Number z) {
            return new int[]{x.intValue(), z.intValue()};
        }
        problems.add(at + "origin: should be [column, row] of the bottom layer; the centre-most log is used");
        return null;
    }

    // ---------------------------------------------------------------- helpers

    private static @Nullable String text(@Nullable Object value) {
        if (value == null || value instanceof Map<?, ?> || value instanceof List<?>) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }

    private static boolean flag(Map<?, ?> section, String key, boolean fallback, String where, List<String> problems) {
        Object value = section.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean flag) {
            return flag;
        }
        problems.add(where + "'" + key + "' should be true or false; using " + fallback);
        return fallback;
    }

    private static double number(Map<?, ?> section, String key, double fallback, double min, double max, String where,
                                 List<String> problems) {
        Object value = section.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number && Double.isFinite(number.doubleValue()) && number.doubleValue() >= min
                && number.doubleValue() <= max) {
            return number.doubleValue();
        }
        problems.add(where + "'" + key + "' should be a number from " + plain(min) + " to " + plain(max) + "; using "
                + plain(fallback));
        return fallback;
    }

    private static String plain(double value) {
        return value == Math.rint(value) && Math.abs(value) < 1e15 ? String.valueOf((long) value) : String.valueOf(value);
    }
}
