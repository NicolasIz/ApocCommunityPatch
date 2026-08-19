package com.arkcronist.gen.core.prefab;

import com.arkcronist.gen.core.block.Blocks;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Turns a Sponge schematic file into a {@link Prefab}.
 *
 * <p>Both schematic revisions in circulation are accepted: version 2, where the palette and block
 * data sit directly in the root, and version 3, where they live under a {@code Blocks} compound. In
 * either case the palette is resolved through the generator's own block registry, so a prefab's
 * blocks travel through generation as the same plain integers the terrain uses and cost nothing
 * extra to write.</p>
 */
public final class SchematicReader {

    private static final Set<String> CONTAINERS = Set.of(
            "minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel");
    private static final Set<String> SPAWNERS = Set.of("minecraft:spawner", "minecraft:trial_spawner");

    private static final Set<String> AIR_NAMES = Set.of(
            "minecraft:air", "minecraft:cave_air", "minecraft:void_air", "minecraft:structure_void");

    private SchematicReader() {
    }

    /**
     * @param id       prefab id, normally the file name without its extension
     * @param category the directory the prefab was found in
     * @param settings optional sidecar settings; may be empty
     */
    @SuppressWarnings("unchecked")
    public static Prefab read(InputStream stream, String id, String category, Properties settings)
            throws IOException {
        Map<String, Object> root = NbtReader.read(stream);
        Map<String, Object> schematic = root.get("Schematic") instanceof Map<?, ?> nested
                ? (Map<String, Object>) nested
                : root;

        int width = NbtReader.intAt(schematic, "Width", 0);
        int height = NbtReader.intAt(schematic, "Height", 0);
        int length = NbtReader.intAt(schematic, "Length", 0);
        if (width <= 0 || height <= 0 || length <= 0) {
            throw new IOException("schematic " + id + " has no dimensions");
        }
        long volume = (long) width * height * length;
        if (volume > 1 << 24) {
            throw new IOException("schematic " + id + " is " + volume + " blocks, too large to load");
        }

        Map<String, Object> paletteSource = schematic;
        Object blocksSection = schematic.get("Blocks");
        if (blocksSection instanceof Map<?, ?> nested) {
            paletteSource = (Map<String, Object>) nested;
        }
        Object rawPalette = paletteSource.get("Palette");
        if (!(rawPalette instanceof Map<?, ?> paletteMap)) {
            throw new IOException("schematic " + id + " has no palette");
        }
        Object rawData = paletteSource.get("Data");
        if (rawData == null) {
            rawData = paletteSource.get("BlockData");
        }
        if (!(rawData instanceof byte[] data)) {
            throw new IOException("schematic " + id + " has no block data");
        }

        String[] names = paletteNames((Map<String, Object>) paletteMap, category);
        char[] blocks = decode(data, (int) volume, names.length, id);

        boolean[] paletteAir = new boolean[names.length];
        for (int i = 0; i < names.length; i++) {
            paletteAir[i] = isAir(names[i]);
        }
        int[][] palettes = new int[4][names.length];
        for (int rotation = 0; rotation < 4; rotation++) {
            for (int i = 0; i < names.length; i++) {
                palettes[rotation][i] = Blocks.REGISTRY.id(BlockStateRotator.rotate(names[i], rotation));
            }
        }

        int solidCount = 0;
        for (char index : blocks) {
            if (!paletteAir[index]) {
                solidCount++;
            }
        }
        if (solidCount == 0) {
            throw new IOException("schematic " + id + " contains nothing but air");
        }

        boolean base = !"center".equalsIgnoreCase(settings.getProperty("anchor", defaultAnchor(category)));
        int[] anchor = base
                ? baseAnchor(blocks, paletteAir, width, height, length)
                : new int[]{width / 2, length / 2};

        int waterline = settings.containsKey("waterline")
                ? parseInt(settings.getProperty("waterline"), 0)
                : deriveWaterline(blocks, paletteAir, width, height, length);

        long[] interior = interiorAir(blocks, paletteAir, width, height, length);

        int[] containers = cellsMatching(blocks, names, CONTAINERS);
        int[] spawners = cellsMatching(blocks, names, SPAWNERS);

        Set<String> tags = tagsFor(id, settings);
        String sizeClass = settings.getProperty("size", sizeClassFor(tags, width, height, length));
        double weight = parseDouble(settings.getProperty("weight"), 1.0);

        return new Prefab(id, category, tags, sizeClass, weight, width, height, length, waterline,
                palettes, paletteAir, blocks, interior, anchor[0], anchor[1], solidCount,
                containers, spawners);
    }

    private static String defaultAnchor(String category) {
        return "trees".equals(category) ? "base" : "center";
    }

    private static String[] paletteNames(Map<String, Object> palette, String category) throws IOException {
        int max = 0;
        for (Object value : palette.values()) {
            if (value instanceof Number number) {
                max = Math.max(max, number.intValue());
            }
        }
        String[] names = new String[max + 1];
        for (Map.Entry<String, Object> entry : palette.entrySet()) {
            if (entry.getValue() instanceof Number number) {
                names[number.intValue()] = normalize(entry.getKey(), category);
            }
        }
        for (int i = 0; i < names.length; i++) {
            if (names[i] == null) {
                names[i] = "minecraft:air";
            }
        }
        if (names.length > Character.MAX_VALUE) {
            throw new IOException("palette of " + names.length + " entries is too large");
        }
        return names;
    }

    /**
     * Fixes up block states that would misbehave once placed by a generator rather than by a player.
     *
     * <p>Leaves exported from a built world usually carry a decay distance; dropped into a freshly
     * generated chunk they would rot away within a minute of a player arriving, taking the crown of
     * every prefab tree with them.</p>
     */
    private static String normalize(String state, String category) {
        if (!state.contains("_leaves[")) {
            return state;
        }
        String fixed = state.replace("persistent=false", "persistent=true");
        int distance = fixed.indexOf("distance=");
        if (distance >= 0) {
            int end = distance + "distance=".length();
            int stop = end;
            while (stop < fixed.length() && Character.isDigit(fixed.charAt(stop))) {
                stop++;
            }
            fixed = fixed.substring(0, end) + "1" + fixed.substring(stop);
        }
        return fixed;
    }

    /** Finds every cell whose palette entry is one of the given block names. */
    private static int[] cellsMatching(char[] blocks, String[] names, Set<String> wanted) {
        boolean[] match = new boolean[names.length];
        boolean any = false;
        for (int i = 0; i < names.length; i++) {
            int bracket = names[i].indexOf('[');
            match[i] = wanted.contains(bracket > 0 ? names[i].substring(0, bracket) : names[i]);
            any |= match[i];
        }
        if (!any) {
            return new int[0];
        }
        int count = 0;
        for (char index : blocks) {
            if (match[index]) {
                count++;
            }
        }
        int[] cells = new int[count];
        int cursor = 0;
        for (int cell = 0; cell < blocks.length; cell++) {
            if (match[blocks[cell]]) {
                cells[cursor++] = cell;
            }
        }
        return cells;
    }

    private static boolean isAir(String state) {
        int bracket = state.indexOf('[');
        String plain = bracket > 0 ? state.substring(0, bracket) : state;
        return AIR_NAMES.contains(plain);
    }

    /** Sponge stores block data as a flat stream of varints in Y-Z-X order. */
    private static char[] decode(byte[] data, int volume, int paletteSize, String id) throws IOException {
        char[] out = new char[volume];
        int cursor = 0;
        int index = 0;
        while (cursor < data.length && index < volume) {
            int value = 0;
            int shift = 0;
            while (true) {
                if (cursor >= data.length) {
                    throw new IOException("schematic " + id + " ends inside a varint");
                }
                int b = data[cursor++] & 0xFF;
                value |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    break;
                }
                shift += 7;
                if (shift > 28) {
                    throw new IOException("schematic " + id + " has a malformed varint");
                }
            }
            if (value < 0 || value >= paletteSize) {
                throw new IOException("schematic " + id + " references palette entry " + value);
            }
            out[index++] = (char) value;
        }
        if (index != volume) {
            throw new IOException("schematic " + id + " holds " + index + " blocks, expected " + volume);
        }
        return out;
    }

    /**
     * The horizontal centre of whatever the prefab stands on.
     *
     * <p>For a tree that is the trunk; positioning by trunk rather than by bounding box is what keeps
     * a leaning crown from dragging the trunk off the ground the placer chose.</p>
     */
    private static int[] baseAnchor(char[] blocks, boolean[] air, int width, int height, int length) {
        for (int y = 0; y < height; y++) {
            long sumX = 0;
            long sumZ = 0;
            int count = 0;
            int layer = y * width * length;
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    if (!air[blocks[layer + z * width + x]]) {
                        sumX += x;
                        sumZ += z;
                        count++;
                    }
                }
            }
            if (count > 0) {
                return new int[]{(int) (sumX / count), (int) (sumZ / count)};
            }
        }
        return new int[]{width / 2, length / 2};
    }

    /**
     * Guesses where the water surface would sit on a vessel.
     *
     * <p>A hull is by far the densest part of a ship, so the highest layer that is still close to the
     * densest one is the top of the hull - which is where the waterline belongs.</p>
     */
    private static int deriveWaterline(char[] blocks, boolean[] air, int width, int height, int length) {
        int[] perLayer = new int[height];
        int densest = 0;
        for (int y = 0; y < height; y++) {
            int layer = y * width * length;
            int count = 0;
            for (int i = 0; i < width * length; i++) {
                if (!air[blocks[layer + i]]) {
                    count++;
                }
            }
            perLayer[y] = count;
            densest = Math.max(densest, count);
        }
        int threshold = (int) (densest * 0.5);
        int line = 0;
        for (int y = 0; y < height; y++) {
            if (perLayer[y] >= threshold) {
                line = y;
            }
        }
        return Math.min(height - 1, line + 1);
    }

    /**
     * Marks the air a prefab encloses, by flooding in from every face of its bounding box.
     *
     * <p>Anything the flood cannot reach is a room, a hold or a cellar, and only those are written as
     * air. Without this a floating ship would carve a rectangular hole in the sea and a hall built
     * into a slope would still be full of rock.</p>
     */
    private static long[] interiorAir(char[] blocks, boolean[] air, int width, int height, int length) {
        int volume = width * height * length;
        long[] interior = new long[(volume + 63) >>> 6];
        boolean[] outside = new boolean[volume];
        // A plain int stack rather than a queue of boxed Integers: a large schematic can have
        // millions of air cells, and this runs once per file at start-up.
        int[] stack = new int[Math.max(64, volume / 8)];
        int top = 0;

        for (int y = 0; y < height; y++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    boolean edge = x == 0 || z == 0 || y == 0 || x == width - 1 || z == length - 1 || y == height - 1;
                    if (!edge) {
                        continue;
                    }
                    int cell = y * width * length + z * width + x;
                    if (air[blocks[cell]] && !outside[cell]) {
                        outside[cell] = true;
                        if (top == stack.length) {
                            stack = java.util.Arrays.copyOf(stack, stack.length * 2);
                        }
                        stack[top++] = cell;
                    }
                }
            }
        }
        while (top > 0) {
            int cell = stack[--top];
            int x = cell % width;
            int z = (cell / width) % length;
            int y = cell / (width * length);
            for (int face = 0; face < 6; face++) {
                int nx = x + (face == 0 ? -1 : face == 1 ? 1 : 0);
                int ny = y + (face == 2 ? -1 : face == 3 ? 1 : 0);
                int nz = z + (face == 4 ? -1 : face == 5 ? 1 : 0);
                if (nx < 0 || ny < 0 || nz < 0 || nx >= width || ny >= height || nz >= length) {
                    continue;
                }
                int next = ny * width * length + nz * width + nx;
                if (!outside[next] && air[blocks[next]]) {
                    outside[next] = true;
                    if (top == stack.length) {
                        stack = java.util.Arrays.copyOf(stack, stack.length * 2);
                    }
                    stack[top++] = next;
                }
            }
        }
        for (int cell = 0; cell < volume; cell++) {
            if (air[blocks[cell]] && !outside[cell]) {
                interior[cell >>> 6] |= 1L << (cell & 63);
            }
        }
        return interior;
    }

    /** Tags come from the file name so that dropping in a new prefab needs no code and no config. */
    private static Set<String> tagsFor(String id, Properties settings) {
        Set<String> tags = new HashSet<>();
        for (String token : id.toLowerCase(java.util.Locale.ROOT).split("[_\\-.]")) {
            if (!token.isEmpty() && !token.chars().allMatch(Character::isDigit)) {
                tags.add(token);
            }
        }
        String extra = settings.getProperty("tags", "");
        for (String token : extra.toLowerCase(java.util.Locale.ROOT).split("[,\\s]+")) {
            if (!token.isEmpty()) {
                tags.add(token);
            }
        }
        // Two-word species survive the split, so put them back together for selection.
        if (tags.contains("dark") && tags.contains("oak")) {
            tags.add("dark_oak");
        }
        if (tags.contains("pale") && tags.contains("oak")) {
            tags.add("pale_oak");
        }
        return Set.copyOf(tags);
    }

    private static String sizeClassFor(Set<String> tags, int width, int height, int length) {
        for (String candidate : List.of("shrub", "small", "medium", "large", "giant", "huge")) {
            if (tags.contains(candidate)) {
                return "huge".equals(candidate) ? "giant" : candidate;
            }
        }
        int spread = Math.max(width, length);
        if (spread <= 9 && height <= 12) {
            return "small";
        }
        if (height >= 30 || spread >= 26) {
            return "giant";
        }
        if (height >= 20 || spread >= 18) {
            return "large";
        }
        return "medium";
    }

    private static int parseInt(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return value == null ? fallback : Double.parseDouble(value.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
