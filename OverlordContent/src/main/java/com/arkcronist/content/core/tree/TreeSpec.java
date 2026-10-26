package com.arkcronist.content.core.tree;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * 5.4: a tree a custom sapling grows into, and that new chunks can be given: a trunk of one block and
 * a crown of another, each a custom block or a vanilla one, in one of a few shapes.
 *
 * @param log        the trunk's block: a custom block's {@code namespace:id}, or a vanilla block's
 *                   ({@code minecraft:dark_oak_log}; a bare name in the YAML is the pack's block of
 *                   that name, else the vanilla one)
 * @param leaves     the crown's block, the same way
 * @param shape      how the crown sits on the trunk
 * @param minHeight  the shortest trunk, in blocks
 * @param maxHeight  the tallest
 * @param radius     how far the crown reaches out from the trunk at its widest
 * @param generation where new chunks get trees of their own; empty for nowhere
 */
public record TreeSpec(String log, String leaves, Shape shape, int minHeight, int maxHeight, int radius,
                       List<Generation> generation) {

    /** The tallest trunk taken: a tree stays inside what a world's height and a chunk's neighbours allow. */
    public static final int MAX_HEIGHT = 32;
    /** The widest crown taken: a tree laid into a new chunk must stay within the chunks next to it. */
    public static final int MAX_RADIUS = 6;

    public TreeSpec {
        generation = List.copyOf(generation);
        if (minHeight < 1 || maxHeight < minHeight || maxHeight > MAX_HEIGHT || radius < 0 || radius > MAX_RADIUS) {
            throw new IllegalArgumentException("height " + minHeight + ".." + maxHeight + ", radius " + radius);
        }
    }

    /** The shapes a crown takes. */
    public enum Shape {
        /** An oak's: a blob of leaves round the top of the trunk, its corners ragged. */
        ROUND("round"),
        /** A spruce's: rings narrowing to a point, from low on the trunk. */
        CONE("cone"),
        /** A birch's or a jungle tree's: a long bare trunk and a small crown at the very top. */
        TALL("tall"),
        /** A ball of leaves round a short stump: a shrub. */
        BUSH("bush"),
        /** An acacia's: the trunk leans, and a flat wide crown sits on it. */
        FLAT("flat");

        private final String yamlName;

        Shape(String yamlName) {
            this.yamlName = yamlName;
        }

        public String yamlName() {
            return yamlName;
        }

        public static Optional<Shape> parse(@Nullable String raw) {
            if (raw == null) {
                return Optional.empty();
            }
            String name = raw.trim().toLowerCase(Locale.ROOT);
            for (Shape shape : values()) {
                if (shape.yamlName.equals(name)) {
                    return Optional.of(shape);
                }
            }
            return switch (name) {
                case "oak", "blob" -> Optional.of(ROUND);
                case "spruce", "pine", "fir" -> Optional.of(CONE);
                case "birch", "jungle" -> Optional.of(TALL);
                case "shrub" -> Optional.of(BUSH);
                case "acacia", "savanna" -> Optional.of(FLAT);
                default -> Optional.empty();
            };
        }
    }

    /**
     * Where new chunks get this tree.
     *
     * @param worlds       world names; empty for every world of {@code environments}
     * @param environments normal, nether, the_end, custom; empty for normal
     * @param biomes       biome keys, {@code minecraft:forest}; empty for every biome
     * @param perChunk     how many trees each chunk tries for
     * @param chance       the chance each try makes a tree, 0-1
     * @param soils        what a tree stands on, vanilla block names; it needs one under its trunk
     */
    public record Generation(Set<String> worlds, Set<String> environments, Set<String> biomes, int perChunk,
                             double chance, Set<String> soils) {

        public Generation {
            worlds = Set.copyOf(worlds);
            environments = Set.copyOf(environments);
            biomes = Set.copyOf(biomes);
            soils = Set.copyOf(soils);
            if (perChunk < 0 || !(chance >= 0 && chance <= 1)) {
                throw new IllegalArgumentException(perChunk + " per chunk, chance " + chance);
            }
        }

        /** As {@code OreGeneration.appliesTo}: named worlds, else worlds of the environments - normal by default. */
        public boolean appliesTo(String world, String environment) {
            String env = environment.toLowerCase(Locale.ROOT);
            if (!worlds.isEmpty() && !worlds.contains(world.toLowerCase(Locale.ROOT))) {
                return false;
            }
            if (environments.isEmpty()) {
                return !worlds.isEmpty() || env.equals("normal");
            }
            return environments.contains(env);
        }

        public boolean inBiome(String biomeKey) {
            return biomes.isEmpty() || biomes.contains(biomeKey.toLowerCase(Locale.ROOT));
        }
    }

    /** One block of a grown tree, from the block its trunk starts on. */
    public record Part(int dx, int dy, int dz, boolean log) {
    }

    /**
     * The blocks of one tree of this kind, trunk first: the same {@code seed} gives the same tree, so
     * a world's trees come from its seed and a test can say what it gets. Leaves never take a
     * trunk's place.
     */
    public List<Part> plan(long seed) {
        Random random = new Random(seed);
        int height = minHeight + (maxHeight > minHeight ? random.nextInt(maxHeight - minHeight + 1) : 0);
        List<Part> logs = new ArrayList<>();
        List<Part> leaves = new ArrayList<>();
        Set<Long> taken = new java.util.HashSet<>();
        int topX = 0;
        int topZ = 0;
        if (shape == Shape.FLAT) {
            // The trunk leans one way for its upper half, a block at a time.
            int[][] ways = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            int[] way = ways[random.nextInt(4)];
            int lean = Math.max(1, height / 2);
            for (int y = 0; y < height; y++) {
                if (y >= height - lean && y > 0) {
                    topX += way[0];
                    topZ += way[1];
                }
                logs.add(new Part(topX, y, topZ, true));
            }
        } else {
            for (int y = 0; y < height; y++) {
                logs.add(new Part(0, y, 0, true));
            }
        }
        for (Part log : logs) {
            taken.add(key(log.dx(), log.dy(), log.dz()));
        }
        int top = height - 1;
        switch (shape) {
            case ROUND -> {
                // Two wide layers round the top of the trunk, then two narrow ones over it: an oak's.
                for (int dy = -2; dy <= 1; dy++) {
                    int r = dy <= -1 ? radius : Math.max(1, radius - 1);
                    disc(leaves, taken, random, 0, top + dy, 0, r, true);
                }
            }
            case TALL -> {
                for (int dy = -1; dy <= 1; dy++) {
                    disc(leaves, taken, random, 0, top + dy, 0, dy == 1 ? Math.max(1, radius - 1) : radius, true);
                }
                leaf(leaves, taken, 0, top + 2, 0);
            }
            case CONE -> {
                // From a third of the way up: rings of a radius that grows down and starts again, to a point.
                int bottom = Math.max(1, height / 3);
                int r = 0;
                for (int y = top + 1; y >= bottom; y--) {
                    disc(leaves, taken, random, 0, y, 0, r, false);
                    r = r >= radius ? Math.max(1, radius - 1) : r + 1;
                }
                leaf(leaves, taken, 0, top + 2, 0);
            }
            case BUSH -> {
                int r = Math.max(1, radius);
                for (int dx = -r; dx <= r; dx++) {
                    for (int dy = -r; dy <= r; dy++) {
                        for (int dz = -r; dz <= r; dz++) {
                            if (dx * dx + dy * dy + dz * dz <= r * r + 1 && top + dy >= 0) {
                                leaf(leaves, taken, dx, top + dy, dz);
                            }
                        }
                    }
                }
            }
            case FLAT -> {
                disc(leaves, taken, random, topX, top, topZ, radius, true);
                disc(leaves, taken, random, topX, top + 1, topZ, Math.max(1, radius - 1), true);
            }
        }
        List<Part> all = new ArrayList<>(logs);
        all.addAll(leaves);
        return all;
    }

    /** A flat ring of leaves of radius {@code r} - corners cut, and with {@code ragged} some of them gone. */
    private static void disc(List<Part> leaves, Set<Long> taken, Random random, int cx, int y, int cz, int r,
                             boolean ragged) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                boolean corner = Math.abs(dx) == r && Math.abs(dz) == r && r > 0;
                if (corner && (!ragged || random.nextBoolean())) {
                    continue;
                }
                leaf(leaves, taken, cx + dx, y, cz + dz);
            }
        }
    }

    private static void leaf(List<Part> leaves, Set<Long> taken, int dx, int dy, int dz) {
        if (taken.add(key(dx, dy, dz))) {
            leaves.add(new Part(dx, dy, dz, false));
        }
    }

    private static long key(int dx, int dy, int dz) {
        return ((long) (dx & 0xFFFF) << 32) | ((long) (dy & 0xFFFF) << 16) | (dz & 0xFFFF);
    }

    /** How far from its trunk's foot a tree of this kind reaches, at most, across: its trunk's lean and crown. */
    public int reach() {
        return radius + (shape == Shape.FLAT ? Math.max(1, maxHeight / 2) : 0) + (shape == Shape.BUSH ? 1 : 0);
    }
}
