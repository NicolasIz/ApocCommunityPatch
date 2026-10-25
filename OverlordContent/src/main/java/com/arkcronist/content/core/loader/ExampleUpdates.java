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
 *       demo - unless the set is the one that brings that pack: the 1.9.5 medieval props are a pack
 *       of their own, {@code contents/medieval_props/}, which no server can have yet;</li>
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
        // Every pack counts as known: only folders that are there are added to.
        Set<String> all = new java.util.HashSet<>();
        sets.values().forEach(set -> set.forEach(example -> all.add(pack(example))));
        return copy(all, sets, offered, dataFolder);
    }

    /**
     * As {@link #toCopy(Map, Set, Path)}, knowing which examples a first start writes: a set whose
     * examples are in a pack folder neither those nor any earlier set uses brings that pack, and
     * its folder is created for it - once, as a set is offered once.
     *
     * @param first the examples a first start writes
     */
    public static List<String> toCopy(List<String> first, Map<String, List<String>> sets, Set<String> offered,
                                      Path dataFolder) {
        Set<String> known = new java.util.HashSet<>();
        first.forEach(example -> known.add(pack(example)));
        return copy(known, sets, offered, dataFolder);
    }

    private static List<String> copy(Set<String> knownPacks, Map<String, List<String>> sets, Set<String> offered,
                                     Path dataFolder) {
        Set<String> known = new java.util.HashSet<>(knownPacks);
        // Packs an earlier set of this same upgrade brings: their folders are not there yet, but will be.
        Set<String> coming = new java.util.HashSet<>();
        List<String> copy = new ArrayList<>();
        sets.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(set -> {
                    Set<String> brought = new java.util.HashSet<>();
                    for (String example : set.getValue()) {
                        if (!known.contains(pack(example))) {
                            brought.add(pack(example));
                        }
                    }
                    set.getValue().forEach(example -> known.add(pack(example)));
                    if (offered.contains(set.getKey())
                            || set.getValue().stream().anyMatch(example -> Files.exists(dataFolder.resolve(example)))) {
                        return;
                    }
                    for (String example : set.getValue()) {
                        // A later set may list a file again (1.9.6 repainted 1.9.5's textures): copied once.
                        if ((brought.contains(pack(example)) || coming.contains(pack(example))
                                || Files.isDirectory(packFolder(dataFolder, example))) && !copy.contains(example)) {
                            copy.add(example);
                        }
                    }
                    coming.addAll(brought);
                });
        return copy;
    }

    /** {@code contents/<pack>} for an example at {@code contents/<pack>/...}, as text. */
    private static String pack(String example) {
        String[] parts = example.split("/");
        return parts.length < 3 ? example : parts[0] + "/" + parts[1];
    }

    /** The remembered sets, one per line; blank lines ignored. */
    public static Set<String> parse(Collection<String> lines) {
        return Set.copyOf(lines.stream().map(String::trim).filter(line -> !line.isEmpty()).toList());
    }

    /**
     * Earlier versions of the shipped examples, from {@code examples-history.txt}: one
     * {@code <path> <sha1>} per line, {@code #} for comments.
     */
    public static Map<String, Set<String>> parseHistory(Collection<String> lines) {
        Map<String, Set<String>> history = new java.util.HashMap<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] parts = trimmed.split("\\s+");
            if (parts.length == 2) {
                history.computeIfAbsent(parts[0], path -> new java.util.HashSet<>()).add(parts[1].toLowerCase(java.util.Locale.ROOT));
            }
        }
        return history;
    }

    /**
     * The examples still exactly as an earlier version shipped them - never edited by the owner -
     * which the current version replaces. A file that differs from every shipped version is the
     * owner's work and is never listed.
     */
    public static List<String> outdated(Map<String, Set<String>> history, Path dataFolder) throws java.io.IOException {
        List<String> outdated = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : new java.util.TreeMap<>(history).entrySet()) {
            Path file = dataFolder.resolve(entry.getKey());
            if (Files.isRegularFile(file) && entry.getValue().contains(sha1(Files.readAllBytes(file)))) {
                outdated.add(entry.getKey());
            }
        }
        return outdated;
    }

    static String sha1(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-1").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** {@code contents/<pack>} for an example at {@code contents/<pack>/...}. */
    private static Path packFolder(Path dataFolder, String example) {
        String[] parts = example.split("/");
        return parts.length < 3 ? dataFolder.resolve(example) : dataFolder.resolve(parts[0]).resolve(parts[1]);
    }
}
