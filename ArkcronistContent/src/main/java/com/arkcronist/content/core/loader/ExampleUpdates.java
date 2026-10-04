package com.arkcronist.content.core.loader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Which demo files an upgrade brings into a {@code contents/} folder that already exists.
 *
 * <p>The demo pack is written whole only on the first start, when {@code contents/} does not exist
 * yet, so the examples a later version adds - the 1.4.0 armour set - would never reach a server
 * that already had one. Each version's new examples are listed under it, and the plugin remembers
 * the version that last offered examples: an upgrade copies in those of every version since, and
 * only those. A file that is already there is never overwritten. One the owner deleted after it
 * was offered is not brought back, since it is not offered twice. And nothing goes into a pack
 * folder that is gone: deleting {@code contents/demo/} means no demo.</p>
 */
public final class ExampleUpdates {

    /**
     * Assumed for a {@code contents/} folder with no version remembered: the plugin did not remember
     * one before 1.4.0, so such a folder was filled by 1.3.x at the latest.
     */
    public static final String BEFORE_REMEMBERED = "1.3.0";

    private ExampleUpdates() {
    }

    /**
     * The examples to copy now, relative to the plugin's folder ({@code contents/<pack>/...}).
     *
     * @param added       the examples each version added, by version
     * @param lastOffered the version that last offered examples to this folder
     * @param dataFolder  the plugin's folder, which holds {@code contents/}
     */
    public static List<String> toCopy(Map<String, List<String>> added, String lastOffered, Path dataFolder) {
        List<String> copy = new ArrayList<>();
        added.entrySet().stream()
                .filter(entry -> compare(entry.getKey(), lastOffered) > 0)
                .sorted(Map.Entry.comparingByKey(ExampleUpdates::compare))
                .forEach(entry -> {
                    for (String example : entry.getValue()) {
                        Path file = dataFolder.resolve(example);
                        if (Files.isDirectory(packFolder(dataFolder, example)) && !Files.exists(file)) {
                            copy.add(example);
                        }
                    }
                });
        return copy;
    }

    /** {@code contents/<pack>} for an example at {@code contents/<pack>/...}. */
    private static Path packFolder(Path dataFolder, String example) {
        String[] parts = example.split("/");
        return parts.length < 3 ? dataFolder.resolve(example) : dataFolder.resolve(parts[0]).resolve(parts[1]);
    }

    /**
     * Dotted versions, number by number: 1.10.0 comes after 1.9.2, and 1.4 is 1.4.0. A part that is
     * not a number - the tail of 1.4.0-SNAPSHOT - counts as 0.
     */
    public static int compare(String a, String b) {
        String[] left = a.trim().split("\\.");
        String[] right = b.trim().split("\\.");
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            int difference = Integer.compare(part(left, i), part(right, i));
            if (difference != 0) {
                return difference;
            }
        }
        return 0;
    }

    private static int part(String[] parts, int index) {
        if (index >= parts.length) {
            return 0;
        }
        String digits = parts[index].replaceAll("[^0-9].*$", "");
        return digits.isEmpty() ? 0 : Integer.parseInt(digits);
    }
}
