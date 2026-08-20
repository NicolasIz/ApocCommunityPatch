package com.arkcronist.gen.core.prefab;

import com.arkcronist.gen.core.math.FastRandom;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Every schematic the generator knows about, indexed by the directory it came from.
 *
 * <p>Nothing here is hard coded. A prefab exists because a {@code .schem} file exists in
 * {@code prefabs/<category>/}, and its role is read from the directory it sits in and from the words
 * in its file name. Dropping {@code prefabs/trees/ancient_tree_01.schem} into the plugin folder and
 * restarting is the whole installation procedure for a new tree - there is no registration list to
 * edit and nothing to recompile.</p>
 *
 * <p>The registry is filled once during plugin start-up and never mutated afterwards, so generation
 * threads read it without any synchronisation at all.</p>
 */
public final class PrefabRegistry {

    /** Substitutes used when a biome asks for a species nobody supplied a prefab for. */
    private static final Map<String, List<String>> SPECIES_KIN = Map.ofEntries(
            Map.entry("mangrove", List.of("jungle", "oak", "azalea")),
            Map.entry("cherry", List.of("azalea", "birch", "oak")),
            Map.entry("pale_oak", List.of("birch", "oak", "dark_oak")),
            Map.entry("big_oak", List.of("oak", "acacia", "dark_oak")),
            Map.entry("giant_spruce", List.of("spruce", "birch")),
            Map.entry("giant_jungle", List.of("jungle", "mangrove")),
            Map.entry("azalea", List.of("birch", "oak")),
            Map.entry("acacia", List.of("oak", "jungle")),
            Map.entry("dark_oak", List.of("oak", "jungle")),
            Map.entry("jungle", List.of("mangrove", "acacia")),
            Map.entry("spruce", List.of("birch")),
            Map.entry("birch", List.of("azalea", "spruce")),
            Map.entry("oak", List.of("acacia", "azalea")),
            Map.entry("dead", List.of("spruce", "dark_oak")));

    /**
     * Families that must be asked for by name.
     *
     * <p>A leafless snag and a crystal tree both carry the wood species in their name, so without
     * this an ordinary oak forest would fill up with dead trunks and amethyst. Asking for
     * {@code dead} still gets dead trees; asking for {@code oak} no longer does.</p>
     */
    private static final java.util.Set<String> OPT_IN = java.util.Set.of("dead", "crystal", "autumn");

    private final Map<String, List<Prefab>> byCategory = new TreeMap<>();
    private int total;

    private PrefabRegistry() {
    }

    public static PrefabRegistry empty() {
        return new PrefabRegistry();
    }

    /**
     * Loads every schematic under {@code root/<category>/}.
     *
     * @param onProblem called with a human readable message for each file that could not be read;
     *                  a bad prefab is skipped, never fatal - one corrupt download must not stop a
     *                  server from starting
     */
    public static PrefabRegistry fromDirectory(Path root, Consumer<String> onProblem) {
        PrefabRegistry registry = new PrefabRegistry();
        if (!Files.isDirectory(root)) {
            return registry;
        }
        List<Path> categories = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    categories.add(entry);
                }
            }
        } catch (IOException exception) {
            onProblem.accept("could not list " + root + ": " + exception.getMessage());
            return registry;
        }
        Collections.sort(categories);
        for (Path directory : categories) {
            registry.loadCategory(directory, directory.getFileName().toString().toLowerCase(Locale.ROOT), onProblem);
        }
        return registry;
    }

    private void loadCategory(Path directory, String category, Consumer<String> onProblem) {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.schem")) {
            for (Path file : stream) {
                files.add(file);
            }
        } catch (IOException exception) {
            onProblem.accept("could not list " + directory + ": " + exception.getMessage());
            return;
        }
        // Sorted so that the same folder always produces the same order, and therefore the same world.
        Collections.sort(files);
        for (Path file : files) {
            String name = file.getFileName().toString();
            String id = name.substring(0, name.length() - ".schem".length());
            Properties settings = sidecar(directory.resolve(id + ".properties"), onProblem);
            try (InputStream stream = Files.newInputStream(file)) {
                add(SchematicReader.read(stream, id, category, settings));
            } catch (IOException | RuntimeException exception) {
                onProblem.accept("skipping " + category + "/" + name + ": " + exception.getMessage());
            }
        }
    }

    private static Properties sidecar(Path path, Consumer<String> onProblem) {
        Properties settings = new Properties();
        if (!Files.isRegularFile(path)) {
            return settings;
        }
        try (InputStream stream = Files.newInputStream(path)) {
            settings.load(stream);
        } catch (IOException exception) {
            onProblem.accept("could not read " + path + ": " + exception.getMessage());
        }
        return settings;
    }

    /**
     * Finds a prefab folder without being told where one is.
     *
     * <p>Used by the command line benchmark and by the tests, which have no plugin folder to ask.
     * The running server never goes through here - it loads the folder it unpacked itself.</p>
     */
    public static PrefabRegistry discover() {
        for (String candidate : new String[]{
                "prefabs",
                "src/main/resources/prefabs",
                "plugins/ArkcronistGenerator/prefabs"}) {
            Path path = Path.of(candidate);
            if (Files.isDirectory(path)) {
                PrefabRegistry registry = fromDirectory(path, message -> {
                });
                if (registry.size() > 0) {
                    return registry;
                }
            }
        }
        return empty();
    }

    /** Adds a prefab directly; used by tests and by any future non-file source. */
    public void add(Prefab prefab) {
        byCategory.computeIfAbsent(prefab.category, key -> new ArrayList<>()).add(prefab);
        total++;
    }

    public int size() {
        return total;
    }

    public List<String> categories() {
        return List.copyOf(byCategory.keySet());
    }

    public List<Prefab> category(String category) {
        List<Prefab> found = byCategory.get(category.toLowerCase(Locale.ROOT));
        return found == null ? List.of() : Collections.unmodifiableList(found);
    }

    public boolean has(String category) {
        return !category(category).isEmpty();
    }

    /** Looks a prefab up by id, whatever category it is in. */
    public Prefab byId(String id) {
        for (List<Prefab> prefabs : byCategory.values()) {
            for (Prefab prefab : prefabs) {
                if (prefab.id.equals(id)) {
                    return prefab;
                }
            }
        }
        return null;
    }

    /**
     * Picks a tree.
     *
     * <p>Rather than demanding an exact match and giving up when there is none, every candidate is
     * scored: the right species at the right size wins comfortably, the right species at the wrong
     * size still competes, and everything else is a distant fallback. That means a biome asking for
     * cherry trees still gets sensible trees on a server whose prefab folder has none, instead of a
     * bald landscape.</p>
     *
     * @param species  preferred species tag, e.g. {@code birch}
     * @param sizeClass preferred size, e.g. {@code giant}
     */
    public Prefab pickTree(String species, String sizeClass, FastRandom random) {
        return pick("trees", species, sizeClass, random);
    }

    /** Scored weighted pick inside one category; returns null only when the category is empty. */
    public Prefab pick(String category, String preferredTag, String sizeClass, FastRandom random) {
        List<Prefab> pool = category(category);
        if (pool.isEmpty()) {
            return null;
        }
        List<String> kin = preferredTag == null
                ? List.of()
                : SPECIES_KIN.getOrDefault(preferredTag, List.of());
        String wanted = preferredTag == null ? "" : preferredTag;
        double total = 0.0;
        double[] scores = new double[pool.size()];
        for (int i = 0; i < pool.size(); i++) {
            Prefab prefab = pool.get(i);
            double score = 0.08;
            if (preferredTag != null && prefab.hasTag(preferredTag)) {
                score = 1.0;
            } else {
                for (int k = 0; k < kin.size(); k++) {
                    if (prefab.hasTag(kin.get(k))) {
                        score = 0.5 / (k + 1);
                        break;
                    }
                }
            }
            // Opt-in families are kept apart from the ordinary ones and from each other: an oak
            // forest never fills with dead trunks, asking for dead trunks never returns oaks, and
            // asking for dead trunks never returns crystal trees either. The species word in a file
            // name is not enough on its own - "large_dead_dark_oak" is a dead tree, not an oak.
            boolean exotic = false;
            for (String family : OPT_IN) {
                if (prefab.hasTag(family)) {
                    exotic = true;
                    break;
                }
            }
            boolean wantedExotic = OPT_IN.contains(wanted);
            if (exotic != wantedExotic || (exotic && !prefab.hasTag(wanted))) {
                // Zero, not merely small: "BASE never grows a crystal tree" has to be a promise,
                // and a long tail of unlikely draws is not a promise.
                score = 0.0;
            }
            if (sizeClass != null) {
                score *= prefab.sizeClass.equals(sizeClass) ? 3.0 : 0.6;
            }
            score *= prefab.weight;
            scores[i] = score;
            total += score;
        }
        if (total <= 0.0) {
            // Nothing scored. Still avoid the opt-in families, which are the one thing a caller
            // that did not ask for them must never be handed.
            List<Prefab> ordinary = new ArrayList<>();
            for (Prefab prefab : pool) {
                boolean exotic = false;
                for (String family : OPT_IN) {
                    if (prefab.hasTag(family)) {
                        exotic = true;
                        break;
                    }
                }
                if (exotic == OPT_IN.contains(wanted)) {
                    ordinary.add(prefab);
                }
            }
            List<Prefab> fallback = ordinary.isEmpty() ? pool : ordinary;
            return fallback.get(random.nextInt(fallback.size()));
        }
        double target = random.nextDouble() * total;
        double running = 0.0;
        for (int i = 0; i < pool.size(); i++) {
            running += scores[i];
            if (target < running) {
                return pool.get(i);
            }
        }
        return pool.get(pool.size() - 1);
    }

    /** One line per category, for the start-up log and for {@code /ag prefabs}. */
    public Map<String, Integer> counts() {
        Map<String, Integer> counts = new HashMap<>();
        byCategory.forEach((category, prefabs) -> counts.put(category, prefabs.size()));
        return counts;
    }
}
