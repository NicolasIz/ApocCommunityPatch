package com.arkcronist.content.core.loader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which demo files an upgrade brings into a {@code contents/} folder that already exists.
 *
 * <p>The demo pack is written whole only on the first start, when {@code contents/} does not exist
 * yet, so the examples a later version adds - the 1.3 chest and bed, the 1.4 armour - would never
 * reach a server that already had one. Each version's new examples are a set, named by the
 * version, and the plugin remembers the sets it has offered. A set not offered yet is copied in,
 * with three exceptions:</p>
 * <ul>
 *   <li>a file that is already there is never overwritten;</li>
 *   <li>nothing goes into a pack folder that is gone: deleting {@code contents/demo/} means no
 *       demo;</li>
 *   <li>a set some of whose files are there came with a version from before sets were
 *       remembered, and its missing files were deleted by the owner: it is left as it is.</li>
 * </ul>
 * <p>A set is offered once, so a file the owner deletes afterwards is not brought back.</p>
 */
public final class ExampleUpdates {

    private ExampleUpdates() {
    }

    /**
     * The examples to copy now, relative to the plugin's folder ({@code contents/<pack>/...}).
     *
     * @param sets       each version's new examples, by version
     * @param offered    the sets already offered to this folder
     * @param dataFolder the plugin's folder, which holds {@code contents/}
     */
    public static List<String> toCopy(Map<String, List<String>> sets, Set<String> offered, Path dataFolder) {
        List<String> copy = new ArrayList<>();
        sets.entrySet().stream()
                .filter(set -> !offered.contains(set.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .forEach(set -> {
                    if (set.getValue().stream().anyMatch(example -> Files.exists(dataFolder.resolve(example)))) {
                        return;
                    }
                    for (String example : set.getValue()) {
                        if (Files.isDirectory(packFolder(dataFolder, example))) {
                            copy.add(example);
                        }
                    }
                });
        return copy;
    }

    /** The remembered sets, one per line; blank lines ignored. */
    public static Set<String> parse(Collection<String> lines) {
        return Set.copyOf(lines.stream().map(String::trim).filter(line -> !line.isEmpty()).toList());
    }

    /** {@code contents/<pack>} for an example at {@code contents/<pack>/...}. */
    private static Path packFolder(Path dataFolder, String example) {
        String[] parts = example.split("/");
        return parts.length < 3 ? dataFolder.resolve(example) : dataFolder.resolve(parts[0]).resolve(parts[1]);
    }
}
