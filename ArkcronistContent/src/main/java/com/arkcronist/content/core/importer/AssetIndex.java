package com.arkcronist.content.core.importer;

import com.arkcronist.content.core.definition.ResourceLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Where each namespace's {@code models/} and {@code textures/} are inside import/.
 *
 * <p>ItemsAdder accepts five layouts for a content pack, and a server that has been through a few
 * versions usually has more than one of them:</p>
 * <pre>
 *   1  pack/configs/*.yml   pack/models/...   pack/textures/...
 *   2  pack/resourcepack/assets/&lt;ns&gt;/models/...
 *   3  pack/resourcepack/&lt;ns&gt;/models/...
 *   4  pack/assets/&lt;ns&gt;/models/...
 *   5  pack/&lt;ns&gt;/models/...
 * </pre>
 * <p>All of them are indexed as "a folder that holds namespace X's models and textures". A lookup
 * prefers the folders of the pack the asking config file belongs to, then any other, in path order,
 * so the result never depends on the order the disk lists directories in.</p>
 *
 * <p>Nothing outside import/ is ever returned: links are not followed while scanning, and a file is
 * only handed out once its real path is confirmed to be inside import/.</p>
 */
final class AssetIndex {

    /** Folders inside a pack that are never a namespace of their own. */
    private static final Set<String> NOT_A_NAMESPACE = Set.of("configs", "assets", "resourcepack", "models",
            "textures", "sounds", "font", "lang", "shaders", "texts", "particles", "atlases", "blockstates");

    private final Path importReal;
    private final Map<String, Set<Path>> roots = new TreeMap<>();

    private AssetIndex(Path importReal) {
        this.importReal = importReal;
    }

    /**
     * @param packs every pack folder that holds config files, with the namespaces those files
     *              declare - layout 1 names no namespace on disk, so the configs have to say it
     */
    static AssetIndex scan(Path importDir, Map<Path, Set<String>> packs) throws IOException {
        AssetIndex index = new AssetIndex(importDir.toRealPath());

        List<Path> directories;
        try (Stream<Path> walk = Files.walk(importDir)) {
            directories = walk.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .sorted()
                    .toList();
        }
        for (Path directory : directories) {
            String name = directory.getFileName().toString();
            if (name.equals("assets")) {
                for (Path namespace : children(directory)) {
                    index.add(namespace.getFileName().toString(), namespace);
                }
            } else if (name.equals("resourcepack") && !Files.isDirectory(directory.resolve("assets"))) {
                for (Path namespace : children(directory)) {
                    if (holdsAssets(namespace)) {
                        index.add(namespace.getFileName().toString(), namespace);
                    }
                }
            }
        }

        for (Map.Entry<Path, Set<String>> pack : new TreeMap<>(packs).entrySet()) {
            Path folder = pack.getKey();
            if (holdsAssets(folder)) {
                for (String namespace : pack.getValue()) {
                    index.add(namespace, folder);
                }
                index.add(folder.getFileName().toString(), folder);
            }
            for (Path child : children(folder)) {
                String name = child.getFileName().toString();
                if (!NOT_A_NAMESPACE.contains(name) && holdsAssets(child)) {
                    index.add(name, child);
                }
            }
        }
        return index;
    }

    /**
     * The file {@code folder/relative} of {@code namespace}, e.g. {@code textures} and
     * {@code item/ruby.png}, looking first in the folders under {@code near}.
     */
    Optional<Path> find(String namespace, String folder, String relative, Path near) {
        Set<Path> candidates = roots.get(namespace);
        if (candidates == null) {
            return Optional.empty();
        }
        List<Path> ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator.comparing((Path root) -> !root.startsWith(near)).thenComparing(Path::toString));
        for (Path root : ordered) {
            Path file = root.resolve(folder).resolve(relative);
            if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && inside(file)) {
                return Optional.of(file);
            }
        }
        return Optional.empty();
    }

    boolean knows(String namespace) {
        return roots.containsKey(namespace);
    }

    private void add(String namespace, Path folder) {
        if (ResourceLocation.isValidNamespace(namespace)) {
            roots.computeIfAbsent(namespace, key -> new LinkedHashSet<>()).add(folder);
        }
    }

    /** A folder somewhere on the way may itself be a link; the real path settles it. */
    private boolean inside(Path file) {
        try {
            return file.toRealPath().startsWith(importReal);
        } catch (IOException exception) {
            return false;
        }
    }

    private static boolean holdsAssets(Path folder) {
        return Files.isDirectory(folder.resolve("models"), LinkOption.NOFOLLOW_LINKS)
                || Files.isDirectory(folder.resolve("textures"), LinkOption.NOFOLLOW_LINKS);
    }

    private static List<Path> children(Path folder) throws IOException {
        if (!Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        try (Stream<Path> list = Files.list(folder)) {
            return list.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)).sorted().toList();
        }
    }
}
