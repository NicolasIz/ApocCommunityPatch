package com.arkcronist.content.core.loader;

import com.arkcronist.content.core.definition.ItemAssets;
import com.arkcronist.content.core.definition.ItemBehaviour;
import com.arkcronist.content.core.definition.ItemDefinition;
import com.arkcronist.content.core.definition.ResourceLocation;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.error.MarkedYAMLException;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Scans the contents folder and reads every item it describes.
 *
 * <p>Layout: each folder directly under {@code contents/} is one content pack. Any {@code .yml}
 * inside it, at any depth, may declare items; the pack's {@code models/} and {@code textures/}
 * folders hold the files those items point at.</p>
 *
 * <pre>
 * contents/
 *   demo/
 *     items.yml                      namespace: demo, items: { ruby: ..., ruby_sword: ... }
 *     models/item/ruby.json          resource.model: item/ruby
 *     textures/item/ruby_sword.png   resource.texture: item/ruby_sword
 * </pre>
 *
 * <p>A file's namespace is its {@code namespace:} key, or failing that the name of the content
 * pack folder it sits in. Files are read in path order, so when two define the same id the one
 * that wins is the same on every restart - and the other is reported, never silently dropped.</p>
 *
 * <p>Runs off the main thread. It is plain file and YAML work with no server state, and SnakeYAML
 * is used through {@link SafeConstructor}: a content file can describe data, never ask for a Java
 * class to be instantiated.</p>
 */
public final class ContentLoader {

    private static final Pattern ITEM_ID = Pattern.compile("[a-z0-9_.-]+");

    /**
     * Reads every {@code .yml} and {@code .yaml} file under {@code contentsDir}.
     *
     * @throws IOException only when the folder itself cannot be walked; a single unreadable file
     *                     is reported in the result instead
     */
    public LoadReport load(Path contentsDir) throws IOException {
        if (!Files.isDirectory(contentsDir)) {
            return new LoadReport(List.of(), List.of());
        }

        List<Path> files;
        try (Stream<Path> walk = Files.walk(contentsDir)) {
            files = walk.filter(Files::isRegularFile)
                    .filter(ContentLoader::isYaml)
                    .sorted(Comparator.comparing(file -> unix(contentsDir.relativize(file))))
                    .toList();
        }

        Map<String, ItemDefinition> items = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();
        for (Path file : files) {
            readFile(contentsDir, file, items, problems);
        }
        return new LoadReport(new ArrayList<>(items.values()), problems);
    }

    private void readFile(Path contentsDir, Path file, Map<String, ItemDefinition> items,
                          List<String> problems) {
        String where = unix(contentsDir.relativize(file));

        Object document;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            document = yaml().load(reader);
        } catch (MarkedYAMLException exception) {
            // The mark is the useful half: SnakeYAML's first line says what, only the mark says where.
            Mark mark = exception.getProblemMark();
            problems.add(where + (mark != null ? " line " + (mark.getLine() + 1) : "")
                    + ": not valid YAML - " + firstLine(exception.getProblem()));
            return;
        } catch (IOException | YAMLException exception) {
            problems.add(where + ": not readable as YAML - " + firstLine(exception.getMessage()));
            return;
        }
        if (document == null) {
            return;
        }
        if (!(document instanceof Map<?, ?> root)) {
            problems.add(where + ": expected 'namespace' and 'items' at the top level");
            return;
        }

        Path sourceRoot = sourceRoot(contentsDir, file);
        String namespace = namespace(root, sourceRoot, contentsDir, where, problems);
        if (namespace == null) {
            return;
        }

        // A file without items is not an error: it may hold another kind of content this version
        // does not read yet.
        Object itemsNode = root.get("items");
        if (itemsNode == null) {
            return;
        }
        if (!(itemsNode instanceof Map<?, ?> itemSections)) {
            problems.add(where + ": 'items' should be a section of item ids");
            return;
        }

        for (Map.Entry<?, ?> entry : itemSections.entrySet()) {
            String id = String.valueOf(entry.getKey());
            String prefix = where + " > " + namespace + ":" + id + ": ";
            if (!(entry.getValue() instanceof Map<?, ?> section)) {
                problems.add(prefix + "should be a section with at least 'material'");
                continue;
            }
            ItemDefinition item = readItem(namespace, id, section, sourceRoot, file, prefix, problems);
            if (item == null) {
                continue;
            }
            ItemDefinition earlier = items.putIfAbsent(item.fullId(), item);
            if (earlier != null) {
                problems.add(prefix + "already defined in "
                        + unix(contentsDir.relativize(earlier.source())) + " - this one is ignored");
            }
        }
    }

    private static String namespace(Map<?, ?> root, Path sourceRoot, Path contentsDir, String where,
                                    List<String> problems) {
        Object declared = root.get("namespace");
        String namespace;
        if (declared != null) {
            namespace = String.valueOf(declared).trim();
        } else if (!sourceRoot.equals(contentsDir)) {
            namespace = sourceRoot.getFileName().toString().toLowerCase(Locale.ROOT);
        } else {
            problems.add(where + ": files placed directly in contents/ must declare a 'namespace'");
            return null;
        }

        if (!ResourceLocation.isValidNamespace(namespace)) {
            problems.add(where + ": invalid namespace '" + namespace + "' (allowed: a-z 0-9 _ . -)");
            return null;
        }
        // Writing into minecraft: would replace the vanilla item of the same name for every player,
        // and one typo away from "ruby" is "diamond".
        if (namespace.equals(ResourceLocation.MINECRAFT)) {
            problems.add(where + ": the minecraft namespace is reserved for vanilla content");
            return null;
        }
        return namespace;
    }

    private static ItemDefinition readItem(String namespace, String id, Map<?, ?> section,
                                           Path sourceRoot, Path file, String prefix,
                                           List<String> problems) {
        if (!ITEM_ID.matcher(id).matches()) {
            problems.add(prefix + "invalid id (allowed: a-z 0-9 _ . -)");
            return null;
        }

        String material = text(section, "material", prefix, problems);
        if (material == null || material.isBlank()) {
            problems.add(prefix + "'material' is required, e.g. material: PAPER");
            return null;
        }

        String displayName = text(section, "display-name", prefix, problems);
        List<String> lore = lines(section, "lore", prefix, problems);
        ItemAssets assets = assets(namespace, section(section, "resource", prefix, problems),
                sourceRoot, prefix, problems);
        ItemBehaviour behaviour = behaviour(section(section, "behaviour", prefix, problems),
                prefix, problems);

        return new ItemDefinition(namespace, id, material.trim(), displayName, lore, assets,
                behaviour, file);
    }

    private static ItemAssets assets(String namespace, Map<?, ?> section, Path sourceRoot,
                                     String prefix, List<String> problems) {
        if (section == null) {
            return ItemAssets.none(sourceRoot);
        }
        ResourceLocation model = location(section, "model", namespace, prefix, problems);
        ResourceLocation texture = location(section, "texture", namespace, prefix, problems);
        // Parents are nearly always vanilla (item/generated, item/handheld), so a bare parent reads
        // the way it would inside a model file.
        ResourceLocation parent = location(section, "parent", ResourceLocation.MINECRAFT, prefix, problems);

        if (model != null && texture != null) {
            problems.add(prefix + "'texture' is ignored because 'model' is set"
                    + " - the textures the model uses are copied with it");
            texture = null;
        }
        return new ItemAssets(sourceRoot, model, texture, parent != null ? parent : ItemAssets.GENERATED);
    }

    private static ItemBehaviour behaviour(Map<?, ?> section, String prefix, List<String> problems) {
        if (section == null) {
            return ItemBehaviour.DEFAULT;
        }
        return new ItemBehaviour(
                flag(section, "cancel-vanilla-use", ItemBehaviour.DEFAULT.cancelVanillaUse(), prefix, problems),
                flag(section, "placeable", ItemBehaviour.DEFAULT.placeable(), prefix, problems));
    }

    // ---------------------------------------------------------------- typed reads

    private static ResourceLocation location(Map<?, ?> section, String key, String defaultNamespace,
                                             String prefix, List<String> problems) {
        String raw = text(section, key, prefix, problems);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        // Tolerate the extension people naturally type; the client never wants it.
        if (value.endsWith(".json") || value.endsWith(".png")) {
            value = value.substring(0, value.lastIndexOf('.'));
        }
        try {
            return ResourceLocation.parse(value, defaultNamespace);
        } catch (IllegalArgumentException exception) {
            problems.add(prefix + "'" + key + "': " + exception.getMessage());
            return null;
        }
    }

    private static String text(Map<?, ?> section, String key, String prefix, List<String> problems) {
        Object value = section.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            problems.add(prefix + "'" + key + "' should be a single value");
            return null;
        }
        return String.valueOf(value);
    }

    private static List<String> lines(Map<?, ?> section, String key, String prefix, List<String> problems) {
        Object value = section.get(key);
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            List<String> lines = new ArrayList<>(list.size());
            for (Object line : list) {
                lines.add(line == null ? "" : String.valueOf(line));
            }
            return lines;
        }
        if (value instanceof Map<?, ?>) {
            problems.add(prefix + "'" + key + "' should be a list of lines");
            return List.of();
        }
        return List.of(String.valueOf(value));
    }

    private static boolean flag(Map<?, ?> section, String key, boolean fallback, String prefix,
                                List<String> problems) {
        Object value = section.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        problems.add(prefix + "'" + key + "' should be true or false; using " + fallback);
        return fallback;
    }

    private static Map<?, ?> section(Map<?, ?> parent, String key, String prefix, List<String> problems) {
        Object value = parent.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        problems.add(prefix + "'" + key + "' should be a section");
        return null;
    }

    // ---------------------------------------------------------------- helpers

    /** The content pack a file belongs to: its top folder under contents/, or contents/ itself. */
    static Path sourceRoot(Path contentsDir, Path file) {
        Path relative = contentsDir.relativize(file);
        return relative.getNameCount() > 1 ? contentsDir.resolve(relative.getName(0)) : contentsDir;
    }

    private static Yaml yaml() {
        LoaderOptions options = new LoaderOptions();
        // A repeated key is almost always a copy-paste slip that would otherwise silently keep the
        // second value.
        options.setAllowDuplicateKeys(false);
        return new Yaml(new SafeConstructor(options));
    }

    private static boolean isYaml(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }

    private static String unix(Path relative) {
        return relative.toString().replace(relative.getFileSystem().getSeparator(), "/");
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "no detail given";
        }
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }
}
