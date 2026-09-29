package com.arkcronist.content.core.loader;

import com.arkcronist.content.core.definition.ContentType;
import com.arkcronist.content.core.definition.ItemBehaviour;
import com.arkcronist.content.core.definition.ItemDefinition;
import com.arkcronist.content.core.definition.ModelSource;
import com.arkcronist.content.core.definition.Placement;
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
import java.util.Optional;
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
 * <p>Every entry under {@code items:} is an item. {@code type: custom_block} and
 * {@code type: custom_furniture} make it something that is placed into the world as well, with
 * its own {@code block:} or {@code furniture:} section; see {@link ContentType}.</p>
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
    private static final Pattern TEXTURE_VARIABLE = Pattern.compile("[a-z0-9_]+");

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
        ContentType type = type(section, prefix, problems);
        if (type == null) {
            return null;
        }
        unusedSection(section, "block", ContentType.CUSTOM_BLOCK, type, prefix, problems);
        unusedSection(section, "furniture", ContentType.CUSTOM_FURNITURE, type, prefix, problems);

        String displayName = text(section, "display-name", prefix, problems);
        List<String> lore = lines(section, "lore", prefix, problems);
        ModelSource model = model(namespace, id, type, section(section, "resource", prefix, problems),
                sourceRoot, prefix, problems);
        ItemBehaviour behaviour = behaviour(section(section, "behaviour", prefix, problems),
                prefix, problems);

        if (type == ContentType.ITEM) {
            String material = text(section, "material", prefix, problems);
            if (material == null || material.isBlank()) {
                problems.add(prefix + "'material' is required, e.g. material: PAPER");
                return null;
            }
            return new ItemDefinition(namespace, id, material.trim(), displayName, lore, model,
                    behaviour, null, file);
        }

        // Blocks and furniture: the material is what the placement needs, the look is mandatory, and
        // the item has to stay placeable.
        if (model == null) {
            problems.add(prefix + "a " + type.yamlName() + " needs resource.model, resource.texture"
                    + " or resource.textures - without one it would look like its support block");
            return null;
        }
        Placement placement = type == ContentType.CUSTOM_BLOCK
                ? block(section(section, "block", prefix, problems), prefix, problems)
                : furniture(section(section, "furniture", prefix, problems), prefix, problems);
        String material = placement instanceof Placement.Furniture furniture
                ? furniture.support().name()
                : "NOTE_BLOCK";
        if (section.containsKey("material")) {
            problems.add(prefix + "'material' is ignored - a " + type.yamlName() + " is always "
                    + material + " underneath");
        }
        if (behaviour.cancelVanillaUse()) {
            problems.add(prefix + "'cancel-vanilla-use' is ignored - it would stop the "
                    + type.yamlName() + " being placed");
        }
        return new ItemDefinition(namespace, id, material, displayName, lore, model,
                new ItemBehaviour(false, true), placement, file);
    }

    private static ContentType type(Map<?, ?> section, String prefix, List<String> problems) {
        String raw = text(section, "type", prefix, problems);
        if (raw == null || raw.isBlank()) {
            return ContentType.ITEM;
        }
        Optional<ContentType> type = ContentType.parse(raw);
        if (type.isEmpty()) {
            problems.add(prefix + "unknown type '" + raw.trim() + "' (item, custom_block, custom_furniture)");
            return null;
        }
        return type.get();
    }

    /**
     * How the entry is drawn.
     *
     * <ul>
     *   <li>{@code model} - a model file from the content pack, used as it is;</li>
     *   <li>{@code texture} - one texture: a flat item ({@code item/generated}, {@code layer0}) or,
     *       for a block, a cube with that texture on every face ({@code block/cube_all},
     *       {@code all});</li>
     *   <li>{@code textures} + {@code parent} - any vanilla-style parent with its own texture
     *       variables, e.g. {@code block/cube_column} with {@code end} and {@code side}.</li>
     * </ul>
     */
    private static ModelSource model(String namespace, String id, ContentType type, Map<?, ?> resource,
                                     Path sourceRoot, String prefix, List<String> problems) {
        if (resource == null) {
            return null;
        }
        ResourceLocation model = location(resource, "model", namespace, prefix, problems);
        ResourceLocation texture = location(resource, "texture", namespace, prefix, problems);
        Map<String, ResourceLocation> textures = textureVariables(resource, namespace, prefix, problems);
        // Parents are nearly always vanilla (item/generated, block/cube_all), so a bare parent reads
        // the way it would inside a model file.
        ResourceLocation parent = location(resource, "parent", ResourceLocation.MINECRAFT, prefix, problems);

        if (model != null) {
            if (texture != null || !textures.isEmpty() || parent != null) {
                problems.add(prefix + "'texture', 'textures' and 'parent' are ignored because 'model' is set"
                        + " - the textures the model uses are copied with it");
            }
            return new ModelSource.Provided(sourceRoot, model);
        }

        boolean block = type == ContentType.CUSTOM_BLOCK;
        Map<String, ResourceLocation> variables = new LinkedHashMap<>(textures);
        if (texture != null) {
            variables.putIfAbsent(block ? "all" : "layer0", texture);
        }
        if (variables.isEmpty()) {
            if (parent != null) {
                problems.add(prefix + "'parent' is ignored without 'texture' or 'textures'");
            }
            return null;
        }
        ResourceLocation location = new ResourceLocation(namespace, (block ? "block/" : "item/") + id);
        ResourceLocation defaultParent = block ? ModelSource.CUBE_ALL : ModelSource.ITEM_GENERATED;
        return new ModelSource.Generated(sourceRoot, location, parent != null ? parent : defaultParent, variables);
    }

    /** {@code textures:} - variable name to texture. Blockbench numbers its variables, so 0 is a valid name. */
    private static Map<String, ResourceLocation> textureVariables(Map<?, ?> resource, String namespace,
                                                                  String prefix, List<String> problems) {
        Map<?, ?> section = section(resource, "textures", prefix, problems);
        if (section == null) {
            return Map.of();
        }
        Map<String, ResourceLocation> textures = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : section.entrySet()) {
            String variable = String.valueOf(entry.getKey());
            if (!TEXTURE_VARIABLE.matcher(variable).matches()) {
                problems.add(prefix + "texture variable '" + variable + "' (allowed: a-z 0-9 _)");
                continue;
            }
            ResourceLocation texture = location(entry.getValue(), "textures." + variable, namespace, prefix, problems);
            if (texture != null) {
                textures.put(variable, texture);
            }
        }
        return textures;
    }

    private static Placement.Block block(Map<?, ?> section, String prefix, List<String> problems) {
        return new Placement.Block(section == null || flag(section, "drop-self", true, prefix, problems));
    }

    private static Placement.Furniture furniture(Map<?, ?> section, String prefix, List<String> problems) {
        if (section == null) {
            return new Placement.Furniture(Placement.Support.BARRIER, 0, true, Placement.Display.DEFAULT);
        }

        Placement.Support support = Placement.Support.BARRIER;
        String rawSupport = text(section, "support", prefix, problems);
        if (rawSupport != null) {
            try {
                support = Placement.Support.valueOf(rawSupport.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                problems.add(prefix + "'support' must be BARRIER or LIGHT, not '" + rawSupport.trim()
                        + "'; using BARRIER");
            }
        }

        int light = integer(section, "light", 0, prefix, problems);
        if (light < 0 || light > 15) {
            int clamped = Math.max(0, Math.min(15, light));
            problems.add(prefix + "'light' must be 0-15; using " + clamped);
            light = clamped;
        }
        if (support != Placement.Support.LIGHT && section.containsKey("light")) {
            problems.add(prefix + "'light' only applies to a LIGHT support");
            light = 0;
        }

        return new Placement.Furniture(support, light,
                flag(section, "face-player", true, prefix, problems),
                display(section(section, "display", prefix, problems), prefix, problems));
    }

    private static Placement.Display display(Map<?, ?> section, String prefix, List<String> problems) {
        if (section == null) {
            return Placement.Display.DEFAULT;
        }
        String transform = Placement.Display.DEFAULT.transform();
        String rawTransform = text(section, "transform", prefix, problems);
        if (rawTransform != null) {
            String upper = rawTransform.trim().toUpperCase(Locale.ROOT);
            if (Placement.Display.TRANSFORMS.contains(upper)) {
                transform = upper;
            } else {
                problems.add(prefix + "'display.transform' must be one of " + Placement.Display.TRANSFORMS
                        + "; using " + transform);
            }
        }
        return new Placement.Display(transform,
                vector(section, "translation", Placement.Vec3.ZERO, prefix, problems),
                vector(section, "scale", Placement.Vec3.ONE, prefix, problems),
                vector(section, "rotation", Placement.Vec3.ZERO, prefix, problems));
    }

    private static ItemBehaviour behaviour(Map<?, ?> section, String prefix, List<String> problems) {
        if (section == null) {
            return ItemBehaviour.DEFAULT;
        }
        return new ItemBehaviour(
                flag(section, "cancel-vanilla-use", ItemBehaviour.DEFAULT.cancelVanillaUse(), prefix, problems),
                flag(section, "placeable", ItemBehaviour.DEFAULT.placeable(), prefix, problems));
    }

    private static void unusedSection(Map<?, ?> section, String key, ContentType owner, ContentType type,
                                      String prefix, List<String> problems) {
        if (type != owner && section.containsKey(key)) {
            problems.add(prefix + "'" + key + "' is only read when type is " + owner.yamlName());
        }
    }

    // ---------------------------------------------------------------- typed reads

    private static ResourceLocation location(Map<?, ?> section, String key, String defaultNamespace,
                                             String prefix, List<String> problems) {
        return location(section.get(key), key, defaultNamespace, prefix, problems);
    }

    private static ResourceLocation location(Object node, String key, String defaultNamespace,
                                             String prefix, List<String> problems) {
        if (node instanceof Map<?, ?> || node instanceof List<?>) {
            problems.add(prefix + "'" + key + "' should be a single value");
            return null;
        }
        String raw = node == null ? null : String.valueOf(node);
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

    private static int integer(Map<?, ?> section, String key, int fallback, String prefix, List<String> problems) {
        Object value = section.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Integer || value instanceof Long) {
            return ((Number) value).intValue();
        }
        problems.add(prefix + "'" + key + "' should be a whole number; using " + fallback);
        return fallback;
    }

    /** A number for all three axes, or a list of exactly three: {@code 0.5} or {@code [0, 0.5, 0]}. */
    private static Placement.Vec3 vector(Map<?, ?> section, String key, Placement.Vec3 fallback,
                                         String prefix, List<String> problems) {
        Object value = section.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            float n = number.floatValue();
            return new Placement.Vec3(n, n, n);
        }
        if (value instanceof List<?> list && list.size() == 3
                && list.stream().allMatch(element -> element instanceof Number)) {
            return new Placement.Vec3(((Number) list.get(0)).floatValue(), ((Number) list.get(1)).floatValue(),
                    ((Number) list.get(2)).floatValue());
        }
        problems.add(prefix + "'" + key + "' should be a number or a list of three, e.g. [0, 0.5, 0]; using "
                + "[" + fallback.x() + ", " + fallback.y() + ", " + fallback.z() + "]");
        return fallback;
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
