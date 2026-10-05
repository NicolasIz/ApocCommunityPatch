package com.arkcronist.content.core.importer;

import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.definition.ResourceLocation;
import com.arkcronist.content.core.pack.ExternalPack;
import com.arkcronist.content.core.pack.PackSource;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.error.MarkedYAMLException;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Converts ItemsAdder content packs into this plugin's content files.
 *
 * <p>Everything under {@code import/} is read: ItemsAdder's whole {@code contents} folder, one pack
 * out of it, or loose files, in any of the five folder layouts ItemsAdder accepts (see
 * {@link AssetIndex}). For each ItemsAdder config file with items, a content file is written to
 * {@code contents/<namespace>/imported/<its path under import/>}, and every model, texture and
 * animation those items use is copied to {@code contents/<namespace>/models|textures/} - exactly
 * where the loader and the pack compiler look for them. From there on they are ordinary content:
 * the next rebuild loads them, compiles their assets and gives each item its {@code item_model}.</p>
 *
 * <p>What is translated:</p>
 * <ul>
 *   <li>{@code name} / {@code display_name} and {@code lore}: dictionary keys resolved, legacy
 *       {@code &} colours turned into MiniMessage;</li>
 *   <li>{@code resource} (generated from textures, or {@code model_path}) and the 1.21.4+
 *       {@code graphics} section: into {@code resource.model}, {@code resource.texture} or
 *       {@code resource.textures} + {@code parent} - the {@code CustomModelData} number is not
 *       needed any more;</li>
 *   <li>{@code behaviours.block}: into {@code type: custom_block}, with six-face textures in
 *       ItemsAdder's order (down, east, north, south, up, west);</li>
 *   <li>{@code behaviours.furniture}: into {@code type: custom_furniture}, solid or not, light,
 *       rotation and the item display transformation.</li>
 * </ul>
 * <p>Everything else - recipes, loot, events, durability, enchantments - is listed in the report's
 * notes, per item, rather than dropped silently. The {@code custom_model_data} number an item had is
 * not carried over: the item is drawn by its {@code item_model}.</p>
 *
 * <p>Assets are copied <b>byte for byte</b>: a model keeps its texture variables, elements and UVs
 * exactly as written, a texture its size and pixels, a {@code .png.mcmeta} its frames, an
 * {@code .ogg} its sample rate and channels; the folder structure under {@code assets/<ns>/} is
 * kept. Nothing is renamed, re-encoded or "fixed" - a reference that would not resolve is reported,
 * not rewritten. A namespace's {@code sounds.json} and {@code sounds/} go to
 * {@code contents/<namespace>/}, where the pack compiler takes them as they are.</p>
 *
 * <p>ItemsAdder's <b>generated</b> resource pack - the zip {@code /iazip} writes, whole or split in
 * parts - is recognised too, and taken whole into {@code packs/} ({@link GeneratedPacks}): every
 * file as it is, overlays included. Each item it draws by number becomes an item drawn by
 * {@code item_model}; where the same item is also in an ItemsAdder config under import/, the
 * config's item is the one kept, with its name and lore.</p>
 *
 * <p>A file that cannot be read, or an item that cannot be converted, is reported and skipped; the
 * rest of the import carries on. Nothing in import/ is changed, and a file already in contents/ is
 * never overwritten with different bytes unless the importer wrote it itself, so running the
 * import twice gives the same result.</p>
 *
 * <p>Plain file and YAML work with no server state, run on the plugin's worker thread. YAML is read
 * through {@link SafeConstructor}, like every other content file.</p>
 */
public final class ItemsAdderImporter {

    /** First line of every file the importer writes: the only files it will overwrite. */
    static final String MARKER = "# Imported from ItemsAdder by /arkcontent import";

    private static final Pattern ITEM_ID = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern SOUNDS_JSON = Pattern.compile("assets/([^/]+)/sounds\\.json");
    private static final Pattern DICTIONARY_KEY = Pattern.compile("[a-z0-9_.]+-[a-z0-9_.-]+");

    /** ItemsAdder's order for a block's six textures. */
    private static final List<String> FACES = List.of("down", "east", "north", "south", "up", "west");

    /** Item keys this converter reads, or that mean nothing outside ItemsAdder. */
    private static final Set<String> HANDLED = Set.of("enabled", "name", "display_name", "lore", "resource",
            "graphics", "behaviours", "material", "permission", "permission_suffix", "specific_properties",
            "template", "variant_of");

    /** Top-level sections of a config file that are read. */
    private static final Set<String> FILE_SECTIONS = Set.of("info", "items", "dictionary");

    /** Parents whose one texture variable is known, so a lone {@code texture} can be put in it. */
    private static final Map<String, String> SINGLE_VARIABLE = Map.of(
            "minecraft:item/generated", "layer0",
            "minecraft:item/handheld", "layer0",
            "minecraft:block/cube_all", "all",
            "minecraft:block/leaves", "all",
            "minecraft:block/cross", "cross",
            "minecraft:block/tinted_cross", "cross");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /**
     * {@link #run(Path, Path, Path)} with generated packs going to {@code packs/} beside
     * {@code contents/}.
     */
    public ImportReport run(Path importDir, Path contentsDir) throws IOException {
        return run(importDir, contentsDir, contentsDir.resolveSibling("packs"));
    }

    /**
     * Imports everything under {@code importDir} into {@code contentsDir}, and every generated
     * resource pack into {@code packsDir}. Creates import/ when it does not exist yet, so there is a
     * place to put things.
     *
     * @throws IOException only when import/ itself cannot be listed; a single broken file is
     *                     reported in the result instead
     */
    public ImportReport run(Path importDir, Path contentsDir, Path packsDir) throws IOException {
        if (!Files.isDirectory(importDir)) {
            Files.createDirectories(importDir);
            return new ImportReport(0, 0, 0, 0, 0, 0, 0, List.of(), List.of(), List.of());
        }
        return new Run(importDir, contentsDir, packsDir).execute();
    }

    /** One config file with items. */
    private record Source(Path file, String where, Path pack, String namespace, Map<?, ?> items) {
    }

    private static final class Run {

        private final Path importDir;
        private final Path contentsDir;
        private final Path packsDir;
        private final List<String> problems = new ArrayList<>();
        private final List<String> notes = new ArrayList<>();
        private final List<String> written = new ArrayList<>();
        /** Dictionary key to text; English wins over any other language. */
        private final Map<String, String> dictionary = new HashMap<>();
        private final Set<String> englishKeys = new HashSet<>();
        /** Destination files already handled in this run, so shared textures are copied once. */
        private final Set<Path> handled = new HashSet<>();
        private AssetIndex assets;
        /** The generated packs taken into packs/ by this run: what they hold is not copied again. */
        private final List<PackSource> absorbed = new ArrayList<>();
        /** Items the configs gave, {@code ns:id}, and the models they are drawn with. */
        private final Set<String> configItems = new HashSet<>();
        private final Set<String> configModels = new HashSet<>();

        private int files;
        private int converted;
        private int items;
        private int skipped;
        private int resources;
        private int packs;
        private int packFiles;

        Run(Path importDir, Path contentsDir, Path packsDir) {
            this.importDir = importDir;
            this.contentsDir = contentsDir;
            this.packsDir = packsDir;
        }

        ImportReport execute() throws IOException {
            try {
                List<GeneratedPacks.Found> generated = absorbGeneratedPacks();
                convertConfigs();
                for (GeneratedPacks.Found pack : generated) {
                    PackSource source = absorbed.get(generated.indexOf(pack));
                    if (source != null) {
                        try {
                            packItems(pack, source);
                        } catch (IOException | RuntimeException exception) {
                            problems.add("packs/" + pack.name() + ": its items could not be imported - " + describe(exception));
                        }
                    }
                }
            } finally {
                for (PackSource source : absorbed) {
                    if (source != null) {
                        source.close();
                    }
                }
            }
            return new ImportReport(files, converted, items, skipped, resources, packs, packFiles, written, problems,
                    notes);
        }

        /** Every generated pack in import/, copied whole into packs/; the list matches {@link #absorbed}. */
        private List<GeneratedPacks.Found> absorbGeneratedPacks() throws IOException {
            List<GeneratedPacks.Found> generated = GeneratedPacks.find(importDir, notes);
            for (GeneratedPacks.Found pack : generated) {
                PackSource source = null;
                try {
                    Files.createDirectories(packsDir);
                    int copied = GeneratedPacks.absorb(pack, importDir, packsDir, problems, notes);
                    if (copied >= 0) {
                        packs++;
                        packFiles += copied;
                        source = PackSource.open(new ExternalPack("packs/" + pack.name(), packsDir.resolve(pack.name())));
                        checkSoundNames(pack, source);
                    }
                } catch (IOException | RuntimeException exception) {
                    problems.add(String.join(" + ", pack.parts().stream()
                            .map(part -> unix(importDir.relativize(part))).toList())
                            + ": could not be taken into packs/ - " + describe(exception));
                }
                absorbed.add(source);
            }
            return generated;
        }

        private void convertConfigs() throws IOException {
            List<Path> yamlFiles;
            try (Stream<Path> walk = Files.walk(importDir)) {
                yamlFiles = walk.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                        .filter(Run::isYaml)
                        .filter(path -> !hidden(importDir.relativize(path)))
                        .sorted()
                        .toList();
            }

            List<Source> sources = new ArrayList<>();
            Map<Path, Set<String>> packs = new HashMap<>();
            for (Path file : yamlFiles) {
                files++;
                Source source = read(file);
                if (source != null) {
                    sources.add(source);
                    packs.computeIfAbsent(source.pack(), key -> new TreeSet<>()).add(source.namespace());
                }
            }
            assets = AssetIndex.scan(importDir, packs);

            Map<String, Source> namespaces = new TreeMap<>();
            for (Source source : sources) {
                namespaces.putIfAbsent(source.namespace(), source);
                try {
                    convert(source);
                } catch (IOException | RuntimeException exception) {
                    problems.add(source.where() + ": could not be imported - " + describe(exception));
                }
            }
            for (Source source : namespaces.values()) {
                try {
                    copySounds(source);
                } catch (IOException | RuntimeException exception) {
                    problems.add(source.where() + ": the sounds of " + source.namespace() + " could not be copied - "
                            + describe(exception));
                }
            }
        }

        // ------------------------------------------------------------ reading

        /** Reads one YAML file: its dictionary is kept, and its items returned if it has any. */
        private Source read(Path file) {
            String where = unix(importDir.relativize(file));
            Object document;
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                document = yaml().load(reader);
            } catch (MarkedYAMLException exception) {
                Mark mark = exception.getProblemMark();
                problems.add(where + (mark != null ? " line " + (mark.getLine() + 1) : "")
                        + ": not valid YAML, skipped - " + firstLine(exception.getProblem()));
                return null;
            } catch (IOException | YAMLException exception) {
                problems.add(where + ": not readable, skipped - " + firstLine(exception.getMessage()));
                return null;
            }
            if (!(document instanceof Map<?, ?> root) || !(root.get("info") instanceof Map<?, ?> info)
                    || info.get("namespace") == null) {
                // ItemsAdder's own config.yml, a language file, anything else dropped in with a pack.
                notes.add(where + ": not an ItemsAdder content file (no info.namespace) - skipped");
                return null;
            }

            String namespace = String.valueOf(info.get("namespace")).trim().toLowerCase(Locale.ROOT);
            List<String> ignored = root.keySet().stream().map(String::valueOf)
                    .filter(key -> !FILE_SECTIONS.contains(key)).sorted().toList();
            if (!ignored.isEmpty()) {
                notes.add(where + ": not imported - " + String.join(", ", ignored));
            }

            if (root.get("dictionary") instanceof Map<?, ?> entries) {
                boolean english = String.valueOf(info.get("dictionary-lang")).toLowerCase(Locale.ROOT).startsWith("en");
                for (Map.Entry<?, ?> entry : entries.entrySet()) {
                    String key = String.valueOf(entry.getKey());
                    String value = String.valueOf(entry.getValue());
                    if (english && englishKeys.add(key)) {
                        dictionary.put(key, value);
                    } else if (!englishKeys.contains(key)) {
                        dictionary.putIfAbsent(key, value);
                    }
                }
            }

            Object itemsNode = root.get("items");
            if (itemsNode == null) {
                return null;
            }
            if (!(itemsNode instanceof Map<?, ?> itemSections)) {
                problems.add(where + ": 'items' is not a section - skipped");
                return null;
            }
            if (!ResourceLocation.isValidNamespace(namespace)) {
                problems.add(where + ": namespace '" + namespace + "' is not valid (a-z 0-9 _ . -) - skipped");
                return null;
            }
            if (namespace.equals(ResourceLocation.MINECRAFT)) {
                problems.add(where + ": items in the minecraft namespace would replace vanilla ones - skipped");
                return null;
            }
            return new Source(file, where, packFolder(file), namespace, itemSections);
        }

        /** The pack a config belongs to: the folder holding its configs/ folder, or its own folder. */
        private Path packFolder(Path file) {
            for (Path folder = file.getParent(); folder != null && folder.startsWith(importDir)
                    && !folder.equals(importDir); folder = folder.getParent()) {
                if (folder.getFileName().toString().equals("configs")) {
                    return folder.getParent();
                }
            }
            return file.getParent();
        }

        // ------------------------------------------------------------ converting

        private void convert(Source source) throws IOException {
            Map<String, Object> converted = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : source.items().entrySet()) {
                String rawId = String.valueOf(entry.getKey());
                String id = rawId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
                String prefix = source.where() + " > " + source.namespace() + ":" + rawId + ": ";
                if (!(entry.getValue() instanceof Map<?, ?> section)) {
                    problems.add(prefix + "not a section - skipped");
                    skipped++;
                    continue;
                }
                if (!ITEM_ID.matcher(id).matches()) {
                    problems.add(prefix + "id cannot be used here - skipped");
                    skipped++;
                    continue;
                }
                if (converted.containsKey(id)) {
                    problems.add(prefix + "becomes " + source.namespace() + ":" + id
                            + " once lower-cased, like another item in this file - skipped");
                    skipped++;
                    continue;
                }
                if (!id.equals(rawId)) {
                    notes.add(prefix + "renamed to " + source.namespace() + ":" + id);
                }
                try {
                    Map<String, Object> item = convertItem(source, id, section, prefix);
                    if (item == null) {
                        skipped++;
                    } else {
                        converted.put(id, item);
                        items++;
                        configItems.add(source.namespace() + ":" + id);
                    }
                } catch (IOException | RuntimeException exception) {
                    problems.add(prefix + "could not be converted - " + describe(exception));
                    skipped++;
                }
            }
            if (converted.isEmpty()) {
                return;
            }

            Path target = contentsDir.resolve(source.namespace()).resolve("imported")
                    .resolve(importDir.relativize(source.file()).toString());
            if (Files.exists(target) && !writtenByImporter(target)) {
                problems.add(source.where() + ": " + unix(contentsDir.relativize(target))
                        + " already exists and was not written by the importer - left alone");
                return;
            }
            Map<String, Object> document = new LinkedHashMap<>();
            document.put("namespace", source.namespace());
            document.put("items", converted);
            String text = MARKER + "\n"
                    + "# Source: import/" + source.where() + "\n"
                    + "# Running the import again rewrites this file. To keep changes you make, move the\n"
                    + "# file out of imported/ (anywhere else in this folder) and delete the source.\n"
                    + YamlWriter.write(document);
            writeAtomically(target, text.getBytes(StandardCharsets.UTF_8));
            written.add(unix(contentsDir.relativize(target)));
            this.converted++;
        }

        /** One ItemsAdder item as a section of ours, or null when it is left out. */
        private Map<String, Object> convertItem(Source source, String id, Map<?, ?> ia, String prefix)
                throws IOException {
            if (Boolean.FALSE.equals(ia.get("enabled"))) {
                notes.add(prefix + "disabled in ItemsAdder (enabled: false) - skipped");
                return null;
            }
            if (Boolean.TRUE.equals(ia.get("template"))) {
                notes.add(prefix + "a template for variants - skipped");
                return null;
            }
            if (ia.get("variant_of") != null) {
                problems.add(prefix + "variant_of is not supported (copy the template's properties into it) - skipped");
                return null;
            }

            Map<?, ?> behaviours = map(ia.get("behaviours"));
            Map<?, ?> specific = map(ia.get("specific_properties"));
            Map<?, ?> blockSection = behaviours != null ? map(behaviours.get("block")) : null;
            if (blockSection == null && specific != null) {
                blockSection = map(specific.get("block"));
            }
            Map<?, ?> furnitureSection = behaviours != null ? map(behaviours.get("furniture")) : null;
            boolean block = blockSection != null;
            boolean furniture = !block && furnitureSection != null;

            List<String> dropped = new ArrayList<>();
            for (Object key : ia.keySet()) {
                if (!HANDLED.contains(String.valueOf(key))) {
                    dropped.add(String.valueOf(key));
                }
            }
            if (behaviours != null) {
                for (Object key : behaviours.keySet()) {
                    String name = String.valueOf(key);
                    if (!(name.equals("block") && block) && !(name.equals("furniture") && furniture)) {
                        dropped.add("behaviours." + name);
                    }
                }
            }
            if (specific != null) {
                for (Object key : specific.keySet()) {
                    if (!(String.valueOf(key).equals("block") && block)) {
                        dropped.add("specific_properties." + key);
                    }
                }
            }

            Map<String, Object> item = new LinkedHashMap<>();
            if (block) {
                item.put("type", "custom_block");
            } else if (furniture) {
                item.put("type", "custom_furniture");
            }

            Map<?, ?> resource = map(ia.get("resource"));
            Map<?, ?> graphics = map(ia.get("graphics"));
            if (resource != null && resource.get("model_id") != null) {
                notes.add(prefix + "custom_model_data " + resource.get("model_id") + " (resource.model_id) is not"
                        + " carried over - the item is drawn by item_model " + source.namespace() + ":" + id);
            }
            if (!block && !furniture) {
                Object material = ia.get("material");
                if (material == null && resource != null) {
                    material = resource.get("material");
                }
                item.put("material", material == null ? "PAPER" : String.valueOf(material).trim().toUpperCase(Locale.ROOT));
            }

            Object rawName = ia.get("name") != null ? ia.get("name") : ia.get("display_name");
            if (rawName != null) {
                item.put("display-name", text(String.valueOf(rawName), prefix + "name"));
            }
            Object lore = ia.get("lore");
            if (lore != null && !(lore instanceof Map<?, ?>)) {
                List<String> lines = new ArrayList<>();
                for (Object line : lore instanceof List<?> list ? list : List.of(lore)) {
                    lines.add(text(line == null ? "" : String.valueOf(line), prefix + "lore"));
                }
                if (!lines.isEmpty()) {
                    item.put("lore", lines);
                }
            }

            String material = String.valueOf(item.getOrDefault("material", "PAPER"));
            Map<String, Object> look = graphics != null
                    ? fromGraphics(source, graphics, block, material, prefix)
                    : fromResource(source, resource, block, material, prefix);
            if (look == null) {
                if (block || furniture) {
                    problems.add(prefix + "a " + (block ? "block" : "furniture")
                            + " needs a model or textures, and none was found - skipped");
                    return null;
                }
                notes.add(prefix + "has no model or texture - imported with the look of " + material);
            } else {
                item.put("resource", look);
            }

            if (block) {
                Map<String, Object> ours = block(blockSection, prefix, dropped);
                if (!ours.isEmpty()) {
                    item.put("block", ours);
                }
            } else if (furniture) {
                Map<String, Object> ours = furniture(furnitureSection, prefix, dropped);
                if (!ours.isEmpty()) {
                    item.put("furniture", ours);
                }
            }

            if (!dropped.isEmpty()) {
                notes.add(prefix + "not imported - " + String.join(", ", dropped));
            }
            return item;
        }

        private String text(String raw, String where) {
            String value = raw;
            String entry = dictionary.get(value.trim());
            if (entry != null) {
                value = entry;
            } else if (DICTIONARY_KEY.matcher(value.trim()).matches() && !value.contains(" ")) {
                notes.add(where + " '" + value.trim() + "' looks like a dictionary key, but no dictionary"
                        + " in import/ defines it - kept as written");
            }
            if (value.contains("<lang:")) {
                notes.add(where + " uses a client translation key; minecraft_lang_overwrite is not imported,"
                        + " so players see the key until a lang file provides it");
            }
            return LegacyText.toMiniMessage(value);
        }

        // ------------------------------------------------------------ looks

        /** The 1.21.4+ {@code graphics} section. */
        private Map<String, Object> fromGraphics(Source source, Map<?, ?> graphics, boolean block, String material,
                                         String prefix) throws IOException {
            String namespace = source.namespace();
            if (graphics.get("icon") != null) {
                notes.add(prefix + "graphics.icon is not used - the inventory shows the item's own model");
            }
            ResourceLocation parent = location(graphics.get("parent"), ResourceLocation.MINECRAFT, prefix);

            Object modelNode = graphics.get("model");
            if (modelNode == null && map(graphics.get("models")) instanceof Map<?, ?> models) {
                modelNode = models.get("normal");
                notes.add(prefix + "only the 'normal' model of graphics.models is imported (no pull or cast states)");
            }
            if (modelNode != null) {
                return providedModel(source, location(modelNode, namespace, prefix), prefix);
            }

            Map<String, ResourceLocation> variables = new LinkedHashMap<>();
            if (map(graphics.get("textures")) instanceof Map<?, ?> textures) {
                if (!block && textures.containsKey("normal")) {
                    notes.add(prefix + "only the 'normal' texture of graphics.textures is imported (no pull or cast states)");
                    ResourceLocation normal = location(textures.get("normal"), namespace, prefix);
                    if (normal != null) {
                        variables.put("layer0", normal);
                    }
                } else {
                    for (Map.Entry<?, ?> entry : textures.entrySet()) {
                        ResourceLocation texture = location(entry.getValue(), namespace, prefix);
                        if (texture != null) {
                            variables.put(String.valueOf(entry.getKey()), texture);
                        }
                    }
                }
            } else if (graphics.get("texture") != null) {
                ResourceLocation texture = location(graphics.get("texture"), namespace, prefix);
                if (texture != null) {
                    String variable = parent == null ? (block ? "all" : "layer0") : SINGLE_VARIABLE.get(parent.toString());
                    if (variable == null) {
                        variable = "all";
                        notes.add(prefix + "texture put in variable 'all' of " + parent
                                + " - check that the parent uses that name");
                    }
                    variables.put(variable, texture);
                }
            }
            if (variables.isEmpty()) {
                return null;
            }
            if (parent == null) {
                parent = block
                        ? new ResourceLocation(ResourceLocation.MINECRAFT, variables.containsKey("all") ? "block/cube_all" : "block/cube")
                        : new ResourceLocation(ResourceLocation.MINECRAFT, heldLikeATool(material) ? "item/handheld" : "item/generated");
            }
            return generatedModel(source, variables, parent, block, prefix);
        }

        /** The classic {@code resource} section. */
        private Map<String, Object> fromResource(Source source, Map<?, ?> resource, boolean block, String material, String prefix)
                throws IOException {
            if (resource == null) {
                return null;
            }
            String namespace = source.namespace();
            boolean generate = Boolean.TRUE.equals(resource.get("generate"));

            List<ResourceLocation> textures = new ArrayList<>();
            Object listed = resource.get("textures") != null ? resource.get("textures") : resource.get("texture");
            if (listed instanceof List<?> list) {
                for (Object entry : list) {
                    ResourceLocation texture = location(entry, namespace, prefix);
                    if (texture != null) {
                        textures.add(texture);
                    }
                }
            } else if (listed != null) {
                ResourceLocation texture = location(listed, namespace, prefix);
                if (texture != null) {
                    textures.add(texture);
                }
            }

            if ((!generate || textures.isEmpty()) && resource.get("model_path") != null) {
                return providedModel(source, location(resource.get("model_path"), namespace, prefix), prefix);
            }
            if (textures.isEmpty()) {
                return null;
            }

            Map<String, ResourceLocation> variables = new LinkedHashMap<>();
            ResourceLocation parent;
            if (block && textures.size() == FACES.size()) {
                for (int face = 0; face < FACES.size(); face++) {
                    variables.put(FACES.get(face), textures.get(face));
                }
                parent = new ResourceLocation(ResourceLocation.MINECRAFT, "block/cube");
            } else if (block) {
                if (textures.size() > 1) {
                    notes.add(prefix + textures.size() + " textures listed; a block takes 1 or 6 - the first is used on every face");
                }
                variables.put("all", textures.get(0));
                parent = new ResourceLocation(ResourceLocation.MINECRAFT, "block/cube_all");
            } else {
                if (textures.size() > 1) {
                    notes.add(prefix + "only the first of " + textures.size()
                            + " textures is imported (the others are pull, cast or charge states)");
                }
                variables.put("layer0", textures.get(0));
                parent = new ResourceLocation(ResourceLocation.MINECRAFT, heldLikeATool(material) ? "item/handheld" : "item/generated");
            }
            return generatedModel(source, variables, parent, block, prefix);
        }

        private Map<String, Object> providedModel(Source source, ResourceLocation model, String prefix) throws IOException {
            if (model == null) {
                return null;
            }
            if (model.namespace().equals(source.namespace())) {
                copyModel(source, model, prefix, new HashSet<>());
            }
            configModels.add(model.toString());
            Map<String, Object> resource = new LinkedHashMap<>();
            resource.put("model", shown(model, source.namespace()));
            return resource;
        }

        private Map<String, Object> generatedModel(Source source, Map<String, ResourceLocation> variables, ResourceLocation parent,
                                    boolean block, String prefix) throws IOException {
            String namespace = source.namespace();
            if (!parent.namespace().equals(ResourceLocation.MINECRAFT)) {
                notes.add(prefix + "parent " + parent + " is a pack model; generated models only take vanilla"
                        + " parents here - it has to be supplied by hand");
            }
            if (block && parent.path().equals("block/cube") && !variables.containsKey("particle")) {
                // block/cube has no particle texture of its own: without one, breaking it shows purple and black.
                variables.put("particle", variables.getOrDefault("north", variables.values().iterator().next()));
            }
            for (ResourceLocation texture : new LinkedHashSet<>(variables.values())) {
                if (texture.namespace().equals(namespace)) {
                    copyTexture(source, texture, prefix);
                } else if (!texture.namespace().equals(ResourceLocation.MINECRAFT)) {
                    problems.add(prefix + "texture " + texture + " is in another namespace; a generated model's"
                            + " textures are only packed from its own - move it into " + namespace);
                }
            }

            Map<String, Object> resource = new LinkedHashMap<>();
            boolean defaultParent = parent.equals(new ResourceLocation(ResourceLocation.MINECRAFT, block ? "block/cube_all" : "item/generated"));
            String single = block ? "all" : "layer0";
            if (defaultParent && variables.size() == 1 && variables.containsKey(single)) {
                resource.put("texture", shown(variables.get(single), namespace));
                return resource;
            }
            Map<String, Object> shownVariables = new LinkedHashMap<>();
            variables.forEach((variable, texture) -> shownVariables.put(variable, shown(texture, namespace)));
            resource.put("textures", shownVariables);
            resource.put("parent", parent.namespace().equals(ResourceLocation.MINECRAFT) ? parent.path() : parent.toString());
            return resource;
        }

        // ------------------------------------------------------------ placements

        private Map<String, Object> block(Map<?, ?> ia, String prefix, List<String> dropped) {
            Map<String, Object> block = new LinkedHashMap<>();
            if (map(ia.get("placed_model")) instanceof Map<?, ?> placed && placed.get("type") != null) {
                String type = String.valueOf(placed.get("type")).trim().toUpperCase(Locale.ROOT);
                if (!type.equals("REAL_NOTE")) {
                    notes.add(prefix + "was a " + type + " block in ItemsAdder; every custom block here is a"
                            + " note block - solid and opaque");
                }
            }
            boolean dropSelf = true;
            if (ia.get("drop_when_mined") instanceof Boolean drop) {
                dropSelf = drop;
            } else if (ia.get("cancel_drop") instanceof Boolean cancel) {
                dropSelf = !cancel;
            }
            if (!dropSelf) {
                block.put("drop-self", false);
            }
            for (Object key : ia.keySet()) {
                String name = String.valueOf(key);
                if (!Set.of("placed_model", "drop_when_mined", "cancel_drop").contains(name)) {
                    dropped.add("behaviours.block." + name);
                }
            }
            return block;
        }

        private Map<String, Object> furniture(Map<?, ?> ia, String prefix, List<String> dropped) {
            Map<String, Object> furniture = new LinkedHashMap<>();
            String entity = ia.get("entity") == null ? "armor_stand" : String.valueOf(ia.get("entity")).trim().toLowerCase(Locale.ROOT);
            boolean solid = Boolean.TRUE.equals(ia.get("solid"));
            int light = ia.get("light_level") instanceof Number number ? Math.max(0, Math.min(15, number.intValue())) : 0;

            furniture.put("support", solid ? "BARRIER" : "LIGHT");
            if (!solid && light > 0) {
                furniture.put("light", light);
            } else if (solid && light > 0) {
                notes.add(prefix + "light_level " + light + " dropped - a solid furniture stands on a barrier, which cannot glow");
            }
            if (Boolean.TRUE.equals(ia.get("fixed_rotation"))) {
                furniture.put("face-player", false);
            }

            Map<?, ?> transformation = map(ia.get("display_transformation"));
            String transform = switch (entity) {
                case "item_frame" -> "FIXED";
                case "item_display" -> "NONE";
                default -> "HEAD";
            };
            if (transformation != null && transformation.get("transform") != null) {
                String given = String.valueOf(transformation.get("transform")).trim().toUpperCase(Locale.ROOT);
                if (Placement.Display.TRANSFORMS.contains(given)) {
                    transform = given;
                } else {
                    notes.add(prefix + "display_transformation.transform '" + given + "' is unknown - using " + transform);
                }
            }
            Map<String, Object> display = new LinkedHashMap<>();
            if (!transform.equals("NONE")) {
                display.put("transform", transform);
            }
            if (transformation != null) {
                Placement.Vec3 translation = vector(transformation.get("translation"), Placement.Vec3.ZERO);
                Placement.Vec3 scale = vector(transformation.get("scale"), Placement.Vec3.ONE);
                Rotations.Quaternion left = quaternion(transformation.get("left_rotation"));
                Rotations.Quaternion right = quaternion(transformation.get("right_rotation"));
                if (!translation.equals(Placement.Vec3.ZERO)) {
                    display.put("translation", list(translation));
                }
                if (!scale.equals(Placement.Vec3.ONE)) {
                    display.put("scale", scale.x() == scale.y() && scale.y() == scale.z() ? (Object) scale.x() : list(scale));
                }
                if (right != Rotations.Quaternion.IDENTITY && !(scale.x() == scale.y() && scale.y() == scale.z())) {
                    notes.add(prefix + "right_rotation with an uneven scale is folded into one rotation - check its shape");
                }
                Placement.Vec3 rotation = Rotations.toDegreesXYZ(left.times(right));
                if (!rotation.equals(Placement.Vec3.ZERO)) {
                    display.put("rotation", list(rotation));
                }
            }
            if (!display.isEmpty()) {
                furniture.put("display", display);
            }

            notes.add(prefix + "drawn by an item display at the block's centre (ItemsAdder used "
                    + (entity.equals("item_display") ? "one" : "an " + entity) + ") - compare it in game and"
                    + " adjust furniture.display.translation if it sits too high or low");
            if (map(ia.get("hitbox")) instanceof Map<?, ?> hitbox && biggerThanOneBlock(hitbox)) {
                notes.add(prefix + "hitbox is larger than one block; only the block it stands on is solid here");
            }
            for (Object key : ia.keySet()) {
                String name = String.valueOf(key);
                if (!Set.of("entity", "solid", "light_level", "fixed_rotation", "display_transformation", "hitbox",
                        "gravity").contains(name)) {
                    dropped.add("behaviours.furniture." + name);
                }
            }
            return furniture;
        }

        // ------------------------------------------------------------ assets

        private void copyTexture(Source source, ResourceLocation texture, String prefix) throws IOException {
            if (absorbedHas(texture.assetPath("textures", ".png"))) {
                // A generated pack in packs/ has it, with its animation: that copy is the one drawn.
                return;
            }
            Optional<Path> found = assets.find(texture.namespace(), "textures", texture.path() + ".png", source.pack());
            if (found.isEmpty()) {
                problems.add(prefix + "texture " + texture + " not found under import/");
                return;
            }
            Path target = assetTarget(texture, "textures", ".png");
            copy(found.get(), target, prefix);
            Path animation = found.get().resolveSibling(found.get().getFileName() + ".mcmeta");
            if (Files.isRegularFile(animation, LinkOption.NOFOLLOW_LINKS)) {
                copy(animation, target.resolveSibling(target.getFileName() + ".mcmeta"), prefix);
            }
        }

        /**
         * Copies a model byte for byte, and with it every parent and texture it pulls in from its
         * namespace. Nothing in it is changed: its texture variables, elements and UVs are what the
         * client draws, exactly as Blockbench or the pack's author wrote them.
         *
         * <p>A bare texture path - {@code item/sword} instead of {@code my_items:item/sword} - means
         * minecraft to the client, and is the classic reason an exported model shows purple and black.
         * When the texture is really in this pack, that is reported, and the texture copied so that
         * adding the namespace in the model is all it takes; the model itself is not rewritten.</p>
         */
        private void copyModel(Source source, ResourceLocation model, String prefix, Set<ResourceLocation> seen)
                throws IOException {
            if (!seen.add(model)) {
                return;
            }
            if (absorbedHas(model.assetPath("models", ".json"))) {
                // A generated pack in packs/ has it, with its textures: that copy is the one drawn.
                return;
            }
            Optional<Path> found = assets.find(model.namespace(), "models", model.path() + ".json", source.pack());
            if (found.isEmpty()) {
                problems.add(prefix + "model " + model + " not found under import/");
                return;
            }
            byte[] bytes = Files.readAllBytes(found.get());
            copyBytes(bytes, assetTarget(model, "models", ".json"), found.get(), prefix);
            JsonObject json;
            try {
                JsonElement parsed = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
                if (!parsed.isJsonObject()) {
                    throw new JsonParseException("not a JSON object");
                }
                json = parsed.getAsJsonObject();
            } catch (JsonParseException exception) {
                problems.add(prefix + "model " + model + " is not valid JSON, copied as it is - " + firstLine(exception.getMessage()));
                return;
            }

            List<ResourceLocation> textures = new ArrayList<>();
            if (json.get("textures") instanceof JsonObject variables) {
                for (Map.Entry<String, JsonElement> entry : variables.entrySet()) {
                    ResourceLocation texture = reference(entry.getValue(), prefix);
                    if (texture == null) {
                        continue;
                    }
                    ResourceLocation own = bareButOwn(entry.getValue(), model, "textures", ".png", source);
                    if (own != null) {
                        problems.add(prefix + "model " + model + ": texture '" + entry.getKey() + "' is '"
                                + entry.getValue().getAsString() + "' with no namespace, which the client reads as "
                                + texture + " - the model is copied unchanged; write " + own
                                + " in it if it shows purple and black");
                        texture = own;
                    }
                    textures.add(texture);
                }
            }
            ResourceLocation parent = reference(json.get("parent"), prefix);
            ResourceLocation ownParent = bareButOwn(json.get("parent"), model, "models", ".json", source);
            if (ownParent != null) {
                problems.add(prefix + "model " + model + ": parent '" + json.get("parent").getAsString() + "' has no"
                        + " namespace, which the client reads as " + parent + " - the model is copied unchanged; write "
                        + ownParent + " in it if it does not draw");
                parent = ownParent;
            }

            for (ResourceLocation texture : textures) {
                if (texture.namespace().equals(model.namespace())) {
                    copyTexture(source, texture, prefix);
                } else if (!texture.namespace().equals(ResourceLocation.MINECRAFT)) {
                    problems.add(prefix + "model " + model + " uses " + texture + " from another namespace;"
                            + " a model's textures are only packed from its own namespace - move it into "
                            + model.namespace());
                }
            }
            if (parent != null && parent.namespace().equals(model.namespace())) {
                copyModel(source, parent, prefix, seen);
            }
        }

        /** A location named inside a model, read as the client reads it: bare means minecraft. */
        private ResourceLocation reference(JsonElement value, String prefix) {
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                return null;
            }
            String raw = value.getAsString();
            if (raw.startsWith("#") || raw.isBlank()) {
                return null;
            }
            try {
                return ResourceLocation.parse(raw, ResourceLocation.MINECRAFT);
            } catch (IllegalArgumentException exception) {
                problems.add(prefix + "a model refers to '" + raw + "' - " + exception.getMessage());
                return null;
            }
        }

        /**
         * The model's own namespace's file, when a bare location names one that exists in import/ -
         * what the author most likely meant - or null.
         */
        private ResourceLocation bareButOwn(JsonElement value, ResourceLocation model, String folder, String extension,
                                            Source source) throws IOException {
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                return null;
            }
            String raw = value.getAsString();
            if (raw.startsWith("#") || raw.isBlank() || raw.indexOf(':') >= 0
                    || model.namespace().equals(ResourceLocation.MINECRAFT)) {
                return null;
            }
            try {
                ResourceLocation own = new ResourceLocation(model.namespace(), raw);
                return assets.find(own.namespace(), folder, own.path() + extension, source.pack()).isPresent()
                        || absorbedHas(own.assetPath(folder, extension)) ? own : null;
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }

        /**
         * A namespace's {@code sounds.json} and {@code sounds/*.ogg}, byte for byte, into
         * {@code contents/<namespace>/} - unless a generated pack in packs/ brings them already.
         */
        private void copySounds(Source source) throws IOException {
            String namespace = source.namespace();
            String prefix = source.where() + " > " + namespace + " sounds: ";
            Optional<Path> index = assets.find(namespace, ".", "sounds.json", source.pack());
            if (index.isPresent() && !absorbedHas("assets/" + namespace + "/sounds.json")) {
                copy(index.get(), contentsDir.resolve(namespace).resolve("sounds.json"), prefix);
            }
            Map<String, Path> sounds = assets.all(namespace, "sounds", source.pack());
            for (Map.Entry<String, Path> sound : sounds.entrySet()) {
                if (!sound.getKey().endsWith(".ogg")
                        || absorbedHas("assets/" + namespace + "/sounds/" + sound.getKey())) {
                    continue;
                }
                copy(sound.getValue(), contentsDir.resolve(namespace).resolve("sounds").resolve(sound.getKey()), prefix);
            }
            if (index.isEmpty() && sounds.keySet().stream().anyMatch(name -> name.endsWith(".ogg"))
                    && !absorbedHas("assets/" + namespace + "/sounds.json")) {
                notes.add(prefix + "sound files copied, but there is no sounds.json naming them as sound events");
            }
        }

        private boolean absorbedHas(String relative) throws IOException {
            for (PackSource source : absorbed) {
                if (source != null && source.has(relative)) {
                    return true;
                }
            }
            return false;
        }

        // ------------------------------------------------------------ generated packs

        /**
         * The items a generated pack draws by number, each written as an item drawn by
         * {@code item_model}: {@code contents/<ns>/items/<id>.json} is the pack's own entry for it, as
         * it is, unless the pack has an item definition of that name already, which is then used.
         */
        private void packItems(GeneratedPacks.Found pack, PackSource source) throws IOException {
            Map<String, Integer> left = new TreeMap<>();
            List<GeneratedPacks.Entry> entries = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (GeneratedPacks.Entry entry : GeneratedPacks.entries(source, left)) {
                if (!seen.add(entry.primary().namespace() + "|" + entry.model())) {
                    left.merge("numbers drawing the same model as another number", 1, Integer::sum);
                } else if (configModels.contains(entry.primary().toString())) {
                    left.merge("items an ItemsAdder config under import/ gives, with its name and lore", 1, Integer::sum);
                } else {
                    entries.add(entry);
                }
            }

            String where = "packs/" + pack.name();
            Map<String, Map<String, Object>> byNamespace = new TreeMap<>();
            Map<GeneratedPacks.Entry, String> ids = GeneratedPacks.ids(entries);
            Map<String, java.util.SortedSet<String>> equipment = GeneratedPacks.equipmentAssets(source);
            int worn = 0;
            int onHead = 0;
            List<String> unworn = new ArrayList<>();
            for (GeneratedPacks.Entry entry : entries) {
                String namespace = entry.primary().namespace();
                String id = ids.get(entry);
                if (configItems.contains(namespace + ":" + id)) {
                    left.merge("items an ItemsAdder config under import/ gives, with its name and lore", 1, Integer::sum);
                    continue;
                }
                String prefix = where + " > " + entry.material() + " " + entry.threshold() + " (" + entry.primary() + "): ";
                ResourceLocation definition = new ResourceLocation(namespace, id);
                byte[] theirs = source.read(definition.assetPath("items", ".json"));
                boolean ownDefinition = theirs != null && drawsSame(theirs, entry);
                for (int n = 2; theirs != null && !ownDefinition; n++) {
                    // The pack's file of that name draws something else: never shadow it.
                    id = ids.get(entry) + "_" + n;
                    definition = new ResourceLocation(namespace, id);
                    theirs = source.read(definition.assetPath("items", ".json"));
                }
                if (!ownDefinition) {
                    JsonObject file = new JsonObject();
                    if (entry.oversized()) {
                        file.addProperty("oversized_in_gui", true);
                    }
                    file.add("model", entry.model().deepCopy());
                    copyBytes((GSON.toJson(file) + "\n").getBytes(StandardCharsets.UTF_8),
                            contentsDir.resolve(namespace).resolve("items").resolve(id + ".json"),
                            packsDir.resolve(pack.name()), prefix);
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("material", entry.material().toUpperCase(Locale.ROOT));
                item.put("display-name", GeneratedPacks.displayName(id));
                item.put("resource", Map.of("item-model", id));
                // Worn the way the pack shows: an armour piece with its layer set, a helm on the head.
                String slot = GeneratedPacks.armourSlot(entry.material());
                if (slot != null) {
                    String asset = GeneratedPacks.armourAsset(id, entry.material(), equipment.get(namespace));
                    if (asset != null) {
                        Map<String, Object> wear = new LinkedHashMap<>();
                        wear.put("slot", slot);
                        wear.put("asset", asset);
                        item.put("equipment", wear);
                        worn++;
                    } else {
                        unworn.add(namespace + ":" + id);
                    }
                } else if (!entry.material().equals("elytra")
                        && GeneratedPacks.headgear(id, entry.primary(), source)) {
                    item.put("equipment", Map.of("slot", "HEAD"));
                    onHead++;
                }
                byNamespace.computeIfAbsent(namespace, key -> new TreeMap<>()).put(id, item);
            }

            for (Map.Entry<String, Map<String, Object>> namespace : byNamespace.entrySet()) {
                Path target = contentsDir.resolve(namespace.getKey()).resolve("imported").resolve(pack.name() + "-pack.yml");
                if (Files.exists(target) && !writtenByImporter(target)) {
                    problems.add(where + ": " + unix(contentsDir.relativize(target))
                            + " already exists and was not written by the importer - left alone");
                    continue;
                }
                Map<String, Object> document = new LinkedHashMap<>();
                document.put("namespace", namespace.getKey());
                document.put("items", namespace.getValue());
                String text = MARKER + "\n"
                        + "# Source: import/" + String.join(" + ", pack.parts().stream()
                        .map(part -> unix(importDir.relativize(part))).toList())
                        + " - an ItemsAdder generated resource pack, now in " + where + "/\n"
                        + "# Running the import again rewrites this file. To keep changes you make, move the\n"
                        + "# file out of imported/ (anywhere else in this folder) and delete the source.\n"
                        + "#\n"
                        + "# A generated pack holds what the client draws, not ItemsAdder's configs: each item here is\n"
                        + "# one custom_model_data entry of it, now drawn by item_model - items/<id>.json, the pack's\n"
                        + "# own entry as it is - and named after its id. For names, lore and behaviours, put the\n"
                        + "# ItemsAdder configs (contents/<pack>/configs/*.yml) in import/ too: their items win.\n"
                        + YamlWriter.write(document);
                writeAtomically(target, text.getBytes(StandardCharsets.UTF_8));
                written.add(unix(contentsDir.relativize(target)));
                converted++;
                items += namespace.getValue().size();
            }
            int total = byNamespace.values().stream().mapToInt(Map::size).sum();
            notes.add(where + ": " + packFilesOf(source) + " file(s) merged as they are; " + total + " item(s) drawn by"
                    + " item_model instead of custom_model_data, in " + byNamespace.size() + " namespace(s)");
            if (worn > 0 || onHead > 0) {
                notes.add(where + ": " + worn + " armour piece(s) worn with the layer set of the pack's equipment/"
                        + " whose name matches theirs (astralion_peto -> astralion_armadura), and " + onHead
                        + " helm(s) and hat(s) worn on the head with their own model - as the pack shows them;"
                        + " change or remove 'equipment:' in the file where that guess is wrong");
            }
            if (!unworn.isEmpty()) {
                notes.add(where + ": " + unworn.size() + " armour piece(s) look as theirs in the inventory but are"
                        + " worn with their material's own layers - no equipment asset of the pack matches them by"
                        + " name (leather is dyed per stack, which only the configs say): "
                        + String.join(", ", unworn.subList(0, Math.min(12, unworn.size())))
                        + (unworn.size() > 12 ? ", ..." : ""));
            }
            left.forEach((reason, count) -> notes.add(where + ": " + count + " custom_model_data entr"
                    + (count == 1 ? "y" : "ies") + " not made items - " + reason));
        }

        /**
         * Sound names a pack's sounds.json points where their file is not, which would play nothing: noted.
         * The copy in packs/ stays as it is; the pack compiler gives them their namespace in the pack
         * players get, unless pack.fix-sound-names is off.
         */
        private void checkSoundNames(GeneratedPacks.Found pack, PackSource source) throws IOException {
            for (String file : source.files()) {
                java.util.regex.Matcher matcher = SOUNDS_JSON.matcher(file);
                if (!matcher.matches()) {
                    continue;
                }
                byte[] bytes = source.read(file);
                try {
                    if (bytes != null && JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8))
                            instanceof JsonObject sounds) {
                        String namespace = matcher.group(1);
                        java.util.SortedMap<String, String> names = com.arkcronist.content.core.pack.SoundNames.misplaced(
                                sounds, namespace, path -> {
                                    try {
                                        return source.has(path);
                                    } catch (IOException exception) {
                                        return false;
                                    }
                                });
                        if (!names.isEmpty()) {
                            String first = names.firstKey();
                            notes.add("packs/" + pack.name() + "/" + file + ": " + names.size() + " sound name(s) point"
                                    + " where their file is not - e.g. '" + first + "', while the file is in " + namespace
                                    + "/sounds/. This copy stays as ItemsAdder wrote it; the pack players get has '"
                                    + names.get(first) + "' and so on (pack.fix-sound-names)");
                        }
                    }
                } catch (JsonParseException exception) {
                    problems.add("packs/" + pack.name() + "/" + file + " is not valid JSON - the client drops every"
                            + " sound of that namespace; copied as it is");
                }
            }
        }

        private static int packFilesOf(PackSource source) throws IOException {
            return source.files().size();
        }

        /** Whether the pack's own item definition draws the entry's model. */
        private static boolean drawsSame(byte[] definition, GeneratedPacks.Entry entry) {
            try {
                if (JsonParser.parseString(new String(definition, StandardCharsets.UTF_8)) instanceof JsonObject object) {
                    String primary = GeneratedPacks.primary(object.get("model"));
                    return primary != null
                            && ResourceLocation.parse(primary, ResourceLocation.MINECRAFT).equals(entry.primary());
                }
            } catch (JsonParseException | IllegalArgumentException exception) {
                return false;
            }
            return false;
        }

        private Path assetTarget(ResourceLocation location, String folder, String extension) {
            return contentsDir.resolve(location.namespace()).resolve(folder).resolve(location.path() + extension);
        }

        private void copy(Path source, Path target, String prefix) throws IOException {
            copyBytes(Files.readAllBytes(source), target, source, prefix);
        }

        /**
         * Writes an asset unless the same bytes are there already. A file with different bytes is
         * kept and reported: it may have been edited since, or come from another pack.
         */
        private void copyBytes(byte[] bytes, Path target, Path source, String prefix) throws IOException {
            if (!handled.add(target)) {
                return;
            }
            if (Files.exists(target)) {
                if (Arrays.equals(Files.readAllBytes(target), bytes)) {
                    return;
                }
                problems.add(prefix + unix(contentsDir.relativize(target)) + " already exists with different content"
                        + " - kept it; delete it and import again to take import/" + unix(importDir.relativize(source)));
                return;
            }
            writeAtomically(target, bytes);
            resources++;
        }

        // ------------------------------------------------------------ helpers

        private ResourceLocation location(Object node, String namespace, String prefix) {
            if (node == null || node instanceof Map<?, ?> || node instanceof List<?>) {
                return null;
            }
            String value = String.valueOf(node).trim();
            if (value.endsWith(".png") || value.endsWith(".json")) {
                value = value.substring(0, value.lastIndexOf('.'));
            }
            try {
                return ResourceLocation.parse(value, namespace);
            } catch (IllegalArgumentException exception) {
                problems.add(prefix + "'" + value + "' - " + exception.getMessage());
                return null;
            }
        }

        private static String shown(ResourceLocation location, String namespace) {
            return location.namespace().equals(namespace) ? location.path() : location.toString();
        }

        private static boolean heldLikeATool(String material) {
            return material.endsWith("_SWORD") || material.endsWith("_AXE") || material.endsWith("_PICKAXE")
                    || material.endsWith("_SHOVEL") || material.endsWith("_HOE")
                    || Set.of("STICK", "BLAZE_ROD", "BREEZE_ROD", "MACE", "BONE").contains(material);
        }

        private static boolean biggerThanOneBlock(Map<?, ?> hitbox) {
            for (String axis : List.of("length", "width", "height")) {
                if (hitbox.get(axis) instanceof Number size && size.doubleValue() > 1) {
                    return true;
                }
            }
            return false;
        }

        /** {@code {x, y, z}}, {@code [x, y, z]} or one number for all three. */
        private static Placement.Vec3 vector(Object node, Placement.Vec3 fallback) {
            if (node instanceof Number number) {
                float n = number.floatValue();
                return new Placement.Vec3(n, n, n);
            }
            if (node instanceof Map<?, ?> map) {
                return new Placement.Vec3(number(map.get("x"), fallback.x()), number(map.get("y"), fallback.y()),
                        number(map.get("z"), fallback.z()));
            }
            if (node instanceof List<?> list && list.size() == 3) {
                return new Placement.Vec3(number(list.get(0), fallback.x()), number(list.get(1), fallback.y()),
                        number(list.get(2), fallback.z()));
            }
            return fallback;
        }

        /** {@code {axis_angle: {angle, axis: {x, y, z}}}} in degrees, or a quaternion {@code {x, y, z, w}}. */
        private static Rotations.Quaternion quaternion(Object node) {
            if (!(node instanceof Map<?, ?> map)) {
                return Rotations.Quaternion.IDENTITY;
            }
            if (map.get("axis_angle") instanceof Map<?, ?> axisAngle) {
                Placement.Vec3 axis = vector(axisAngle.get("axis"), Placement.Vec3.ZERO);
                return Rotations.Quaternion.axisAngle(axis.x(), axis.y(), axis.z(), number(axisAngle.get("angle"), 0));
            }
            if (map.get("w") != null) {
                return new Rotations.Quaternion(number(map.get("x"), 0), number(map.get("y"), 0),
                        number(map.get("z"), 0), number(map.get("w"), 1)).normalized();
            }
            return Rotations.Quaternion.IDENTITY;
        }

        private static float number(Object node, float fallback) {
            return node instanceof Number number ? number.floatValue() : fallback;
        }

        private static List<Float> list(Placement.Vec3 vector) {
            return List.of(vector.x(), vector.y(), vector.z());
        }

        private static Map<?, ?> map(Object node) {
            return node instanceof Map<?, ?> map ? map : null;
        }

        private static boolean writtenByImporter(Path file) throws IOException {
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                return MARKER.equals(reader.readLine());
            }
        }

        private static void writeAtomically(Path target, byte[] bytes) throws IOException {
            Files.createDirectories(target.getParent());
            Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
            Files.write(temporary, bytes);
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }

        private static Yaml yaml() {
            // Duplicate keys are allowed, as ItemsAdder allows them: the import should take what
            // ItemsAdder took, not refuse it. Aliases stay capped at SnakeYAML's default.
            return new Yaml(new SafeConstructor(new LoaderOptions()));
        }

        private static boolean isYaml(Path file) {
            String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
            return name.endsWith(".yml") || name.endsWith(".yaml");
        }

        /** Dot folders - .git, .DS_Store's siblings, editors' backups - are not content. */
        private static boolean hidden(Path relative) {
            for (Path part : relative) {
                if (part.toString().startsWith(".")) {
                    return true;
                }
            }
            return false;
        }

        private static String unix(Path relative) {
            return relative.toString().replace(relative.getFileSystem().getSeparator(), "/");
        }

        private static String describe(Exception exception) {
            return exception.getClass().getSimpleName() + ": " + firstLine(exception.getMessage());
        }

        private static String firstLine(String message) {
            if (message == null) {
                return "no detail given";
            }
            int newline = message.indexOf('\n');
            return newline < 0 ? message : message.substring(0, newline);
        }
    }

    /**
     * Writes the converted files: block style throughout, lists of numbers on one line, every
     * scalar quoted by SnakeYAML exactly when it needs to be.
     */
    static final class YamlWriter {

        private static final Yaml SCALARS;

        static {
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.FLOW);
            options.setWidth(Integer.MAX_VALUE);
            options.setSplitLines(false);
            SCALARS = new Yaml(options);
        }

        private YamlWriter() {
        }

        static String write(Map<String, Object> document) {
            StringBuilder out = new StringBuilder();
            map(document, 0, out);
            return out.toString();
        }

        private static void map(Map<?, ?> map, int indent, StringBuilder out) {
            String pad = " ".repeat(indent);
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = scalar(String.valueOf(entry.getKey()));
                Object value = entry.getValue();
                if (value instanceof Map<?, ?> child) {
                    out.append(pad).append(key).append(":\n");
                    map(child, indent + 2, out);
                } else if (value instanceof List<?> list && list.stream().allMatch(element -> element instanceof String)) {
                    out.append(pad).append(key).append(":\n");
                    for (Object line : list) {
                        out.append(pad).append("  - ").append(scalar(line)).append('\n');
                    }
                } else {
                    out.append(pad).append(key).append(": ").append(scalar(value)).append('\n');
                }
            }
        }

        private static String scalar(Object value) {
            return SCALARS.dump(value).strip();
        }
    }
}
