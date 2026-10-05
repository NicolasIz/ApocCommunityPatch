package com.arkcronist.content.core.importer;

import com.arkcronist.content.core.definition.ResourceLocation;
import com.arkcronist.content.core.pack.PackSource;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * ItemsAdder's generated resource pack - what {@code /iazip} writes, often shared as a zip split in
 * parts - taken whole.
 *
 * <p>Such a pack holds no ItemsAdder configs: no names, no lore, no behaviours, only what the
 * client draws. So it is not converted, it is <em>absorbed</em>: every file copied byte for byte
 * into {@code packs/<name>/}, which is merged into this plugin's pack on every rebuild - models with
 * their texture variables and UVs as Blockbench wrote them, textures at their own resolution with
 * their {@code .png.mcmeta} animations, {@code sounds.json} with its {@code .ogg} files, the
 * overlays and the {@code pack.mcmeta} entries that declare them. The path of every file stays the
 * same, so every reference inside the pack still lands where it did.</p>
 *
 * <p>What can be read from it is which item each {@code custom_model_data} number drew. Each one
 * becomes an item of this plugin, drawn through {@code item_model} - an item definition that is the
 * pack's own entry for that number, copied as it is - instead of the number. Bones of ModelEngine
 * models, vanilla looks and ItemsAdder's internal icons are not items and are left to the pack.</p>
 */
final class GeneratedPacks {

    /** First line of the file left in a pack folder the importer wrote: the only ones it replaces. */
    static final String MARKER_FILE = "imported-from-itemsadder.txt";

    /** {@code generated_4 - parte 2}, {@code pack_part1}, {@code pack.pt-3}: one part of a split zip. */
    private static final Pattern PART = Pattern.compile("(?i)^(.*?)[\\s._-]*(?:parte|part|pt)[\\s._-]*(\\d+)$");
    private static final Pattern OVERLAY = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern VANILLA_DEFINITION = Pattern.compile("assets/minecraft/items/([a-z0-9_.-]+)\\.json");
    private static final Pattern ITEM_ID = Pattern.compile("[a-z0-9_.-]+");

    /** The format a 1.21.8 client reads overlays for. */
    static final int PACK_FORMAT = 64;

    /** Namespaces whose models are not items: ModelEngine's bones, vanilla, ItemsAdder's own icons. */
    private static final Map<String, String> NOT_ITEMS = Map.of(
            "modelengine", "bones of ModelEngine models - ModelEngine draws them",
            ResourceLocation.MINECRAFT, "vanilla models",
            "_iainternal", "ItemsAdder's own internal icons");

    private GeneratedPacks() {
    }

    /**
     * A generated pack found in import/.
     *
     * @param name  its folder under packs/
     * @param parts the zips it was split into, in order, or the one folder it is
     */
    record Found(String name, List<Path> parts) {
    }

    /** One {@code custom_model_data} entry that draws an item. */
    record Entry(String material, int threshold, ResourceLocation primary, JsonObject model, boolean oversized) {
    }

    // ------------------------------------------------------------ finding

    /**
     * Every resource pack in import/: a zip with {@code pack.mcmeta} or {@code assets/} at its top
     * (or one folder down), or a folder with both. Zips named as parts of one pack are grouped.
     */
    static List<Found> find(Path importDir, List<String> notes) throws IOException {
        List<Path> zips = new ArrayList<>();
        List<Path> folders = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(importDir)) {
            for (Path path : walk.sorted().toList()) {
                Path relative = importDir.relativize(path);
                if (path.equals(importDir) || hidden(relative) || folders.stream().anyMatch(path::startsWith)) {
                    continue;
                }
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                        && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
                    zips.add(path);
                } else if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                        && Files.isRegularFile(path.resolve("pack.mcmeta"), LinkOption.NOFOLLOW_LINKS)
                        && Files.isDirectory(path.resolve("assets"), LinkOption.NOFOLLOW_LINKS)) {
                    folders.add(path);
                }
            }
        }

        Map<String, List<Path>> groups = new TreeMap<>();
        Map<Path, Integer> partNumbers = new HashMap<>();
        for (Path zip : zips) {
            if (!resourcePack(zip)) {
                notes.add(unix(importDir.relativize(zip)) + ": not a resource pack (no pack.mcmeta or assets/) - skipped");
                continue;
            }
            String stem = zip.getFileName().toString();
            stem = stem.substring(0, stem.length() - 4);
            Matcher part = PART.matcher(stem);
            String base = stem;
            int number = 0;
            if (part.matches() && !part.group(1).isBlank()) {
                base = part.group(1);
                number = Integer.parseInt(part.group(2));
            }
            partNumbers.put(zip, number);
            groups.computeIfAbsent(folderName(base), key -> new ArrayList<>()).add(zip);
        }

        List<Found> found = new ArrayList<>();
        for (Map.Entry<String, List<Path>> group : groups.entrySet()) {
            List<Path> parts = new ArrayList<>(group.getValue());
            parts.sort(Comparator.comparing((Path zip) -> partNumbers.get(zip)).thenComparing(Path::toString));
            found.add(new Found(group.getKey(), List.copyOf(parts)));
        }
        for (Path folder : folders) {
            String name = folderName(folder.getFileName().toString());
            while (true) {
                String taken = name;
                if (found.stream().noneMatch(other -> other.name().equals(taken))) {
                    break;
                }
                name = name + "_folder";
            }
            found.add(new Found(name, List.of(folder)));
        }
        return found;
    }

    /** Whether a zip holds a resource pack, or a part of one. */
    private static boolean resourcePack(Path zip) throws IOException {
        try (ZipFile file = new ZipFile(zip.toFile())) {
            String prefix = PackSource.zipPrefix(file);
            for (ZipEntry entry : Collections.list(file.entries())) {
                String name = entry.getName().replace('\\', '/');
                if (name.equals(prefix + "pack.mcmeta") || name.startsWith(prefix + "assets/")) {
                    return true;
                }
            }
            return false;
        } catch (ZipException exception) {
            return false;
        }
    }

    // ------------------------------------------------------------ absorbing

    /**
     * Copies a found pack into {@code packs/<name>/}, every file exactly as it is, replacing what an
     * earlier import put there. A folder there that the importer did not write is left alone.
     *
     * @return the number of files copied, or -1 when nothing was
     */
    static int absorb(Found found, Path importDir, Path packsDir, List<String> problems, List<String> notes)
            throws IOException {
        String where = String.join(" + ", found.parts().stream().map(part -> unix(importDir.relativize(part))).toList());
        Path target = packsDir.resolve(found.name());
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(target.resolve(MARKER_FILE), LinkOption.NOFOLLOW_LINKS)) {
            problems.add(where + ": packs/" + found.name() + " already exists and was not written by the importer"
                    + " - left alone; move it away and import again to replace it");
            return -1;
        }

        Path staging = packsDir.resolve("." + found.name() + ".importing");
        deleteTree(staging);
        Files.createDirectories(staging);
        Map<String, Path> origins = new HashMap<>();
        List<String> outside = new ArrayList<>();
        int files = 0;
        boolean mcmeta = false;
        try {
            for (Path part : found.parts()) {
                String partName = unix(importDir.relativize(part));
                if (Files.isDirectory(part)) {
                    files += copyFolder(part, staging, partName, origins, outside, problems);
                } else {
                    files += copyZip(part, staging, partName, origins, outside, problems);
                }
            }
            mcmeta = Files.isRegularFile(staging.resolve("pack.mcmeta"));
            Files.writeString(staging.resolve(MARKER_FILE), MARKER_FILE + "\n"
                    + "Imported from ItemsAdder by /arkcontent import, from import/" + where + ".\n"
                    + "Every file is a byte-for-byte copy; this folder is merged into the plugin's pack on every\n"
                    + "rebuild. Importing again replaces the whole folder - delete this file to keep it as it is.\n",
                    StandardCharsets.UTF_8);
            deleteTree(target);
            Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            deleteTree(staging);
        }

        if (!mcmeta) {
            problems.add(where + ": no pack.mcmeta in " + (found.parts().size() > 1 ? "any part" : "it")
                    + " - its overlays are unknown, so only assets/ is merged. If the pack was split, a part is missing");
        }
        missingParts(found, where, problems);
        if (!outside.isEmpty()) {
            notes.add(where + ": " + outside.size() + " file(s) outside assets/ and the overlays left out - "
                    + String.join(", ", outside.subList(0, Math.min(5, outside.size())))
                    + (outside.size() > 5 ? ", ..." : ""));
        }
        return files;
    }

    private static int copyZip(Path zip, Path staging, String partName, Map<String, Path> origins,
                               List<String> outside, List<String> problems) throws IOException {
        int files = 0;
        try (ZipFile file = new ZipFile(zip.toFile())) {
            String prefix = PackSource.zipPrefix(file);
            List<ZipEntry> entries = new ArrayList<>(Collections.list(file.entries()));
            entries.sort(Comparator.comparing(ZipEntry::getName));
            for (ZipEntry entry : entries) {
                String name = entry.getName().replace('\\', '/');
                if (entry.isDirectory() || !name.startsWith(prefix)) {
                    continue;
                }
                String relative = name.substring(prefix.length());
                if (!kept(relative)) {
                    if (safe(relative)) {
                        outside.add(relative);
                    }
                    continue;
                }
                try (InputStream in = file.getInputStream(entry)) {
                    if (write(staging, relative, in, partName, origins, problems)) {
                        files++;
                    }
                }
            }
        } catch (ZipException exception) {
            problems.add(partName + ": not a readable zip - " + exception.getMessage());
        }
        return files;
    }

    private static int copyFolder(Path folder, Path staging, String partName, Map<String, Path> origins,
                                  List<String> outside, List<String> problems) throws IOException {
        int files = 0;
        List<Path> paths;
        try (Stream<Path> walk = Files.walk(folder)) {
            paths = walk.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).sorted().toList();
        }
        for (Path path : paths) {
            String relative = unix(folder.relativize(path));
            if (!kept(relative)) {
                outside.add(relative);
                continue;
            }
            try (InputStream in = Files.newInputStream(path)) {
                if (write(staging, relative, in, partName, origins, problems)) {
                    files++;
                }
            }
        }
        return files;
    }

    /** One file into the staging folder; a second part's different copy of it is reported and dropped. */
    private static boolean write(Path staging, String relative, InputStream in, String partName,
                                 Map<String, Path> origins, List<String> problems) throws IOException {
        Path target = staging.resolve(relative);
        if (origins.containsKey(relative)) {
            byte[] mine = Files.readAllBytes(target);
            byte[] theirs = in.readAllBytes();
            if (!Arrays.equals(mine, theirs)) {
                problems.add(partName + ": " + relative + " is also in " + origins.get(relative).getFileName()
                        + " with other content - keeping the first part's");
            }
            return false;
        }
        Files.createDirectories(target.getParent());
        try (OutputStream out = Files.newOutputStream(target)) {
            in.transferTo(out);
        }
        origins.put(relative, Path.of(partName));
        return true;
    }

    /** pack.mcmeta, pack.png, assets/ and any folder's assets/ (an overlay) - nothing that climbs out. */
    private static boolean kept(String relative) {
        if (!safe(relative)) {
            return false;
        }
        if (relative.equals("pack.mcmeta") || relative.equals("pack.png") || relative.startsWith("assets/")) {
            return true;
        }
        int slash = relative.indexOf('/');
        return slash > 0 && OVERLAY.matcher(relative.substring(0, slash)).matches()
                && relative.startsWith("assets/", slash + 1);
    }

    private static boolean safe(String relative) {
        if (relative.isEmpty() || relative.startsWith("/") || relative.contains("\\") || relative.contains(":")) {
            return false;
        }
        for (String part : relative.split("/")) {
            if (part.isEmpty() || part.equals("..") || part.equals(".")) {
                return false;
            }
        }
        return true;
    }

    private static void missingParts(Found found, String where, List<String> problems) {
        List<Integer> numbers = new ArrayList<>();
        for (Path part : found.parts()) {
            String stem = part.getFileName().toString();
            Matcher matcher = PART.matcher(stem.endsWith(".zip") ? stem.substring(0, stem.length() - 4) : stem);
            if (matcher.matches()) {
                numbers.add(Integer.parseInt(matcher.group(2)));
            }
        }
        if (numbers.isEmpty()) {
            return;
        }
        int first = Collections.min(numbers) <= 1 ? Collections.min(numbers) : 1;
        for (int number = first; number < Collections.max(numbers); number++) {
            if (!numbers.contains(number)) {
                problems.add(where + ": part " + number + " is not in import/ - the files in it are missing");
            }
        }
    }

    // ------------------------------------------------------------ items

    /**
     * The entries that draw items: from the {@code custom_model_data} definition of each vanilla
     * item a {@code PACK_FORMAT} client reads - an overlay's copy when one is active for it, the
     * last such overlay winning as on the client - in material and number order.
     *
     * @param skipped how many entries were left out, by reason
     */
    static List<Entry> entries(PackSource source, Map<String, Integer> skipped) throws IOException {
        List<String> order = new ArrayList<>(source.overlays().keySet());
        Map<String, String> chosen = new TreeMap<>();
        for (String file : source.files()) {
            Matcher matcher = VANILLA_DEFINITION.matcher(PackSource.inner(file));
            if (!matcher.matches()) {
                continue;
            }
            String overlay = PackSource.overlayOf(file);
            if (overlay != null && !covers(source.overlays().get(overlay), PACK_FORMAT)) {
                continue;
            }
            String material = matcher.group(1);
            String current = chosen.get(material);
            if (current == null || rank(order, overlay) > rank(order, PackSource.overlayOf(current))) {
                chosen.put(material, file);
            }
        }

        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<String, String> material : chosen.entrySet()) {
            byte[] bytes = source.read(material.getValue());
            JsonObject definition;
            try {
                definition = bytes != null && JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8))
                        instanceof JsonObject object ? object : null;
            } catch (JsonParseException exception) {
                definition = null;
            }
            if (definition == null || !(definition.get("model") instanceof JsonObject dispatch)
                    || !"custom_model_data".equals(bare(string(dispatch, "property")))
                    || !"range_dispatch".equals(bare(string(dispatch, "type")))
                    || !(dispatch.get("entries") instanceof JsonArray list)) {
                continue;
            }
            boolean oversized = definition.get("oversized_in_gui") instanceof JsonElement flag && flag.isJsonPrimitive()
                    && flag.getAsJsonPrimitive().isBoolean() && flag.getAsBoolean();
            for (JsonElement element : list) {
                if (!(element instanceof JsonObject entry) || !(entry.get("model") instanceof JsonObject model)
                        || !(entry.get("threshold") instanceof JsonElement threshold) || !threshold.isJsonPrimitive()
                        || !threshold.getAsJsonPrimitive().isNumber()) {
                    continue;
                }
                String primary = primary(model);
                ResourceLocation location;
                try {
                    location = primary == null ? null : ResourceLocation.parse(primary, ResourceLocation.MINECRAFT);
                } catch (IllegalArgumentException exception) {
                    location = null;
                }
                if (location == null) {
                    skipped.merge("entries that name no model", 1, Integer::sum);
                    continue;
                }
                String reason = NOT_ITEMS.get(location.namespace());
                if (reason != null) {
                    skipped.merge(reason, 1, Integer::sum);
                    continue;
                }
                entries.add(new Entry(material.getKey(), threshold.getAsInt(), location, model, oversized));
            }
        }
        return entries;
    }

    /**
     * The model an item model draws in the plain case - a model's own, a condition's when false, a
     * select's or a dispatch's fallback - which names the item.
     */
    static @Nullable String primary(JsonElement element) {
        if (!(element instanceof JsonObject model)) {
            return null;
        }
        return switch (String.valueOf(bare(string(model, "type")))) {
            case "model" -> string(model, "model");
            case "special" -> string(model, "base");
            case "condition" -> firstOf(primary(model.get("on_false")), primary(model.get("on_true")));
            case "select" -> firstOf(primary(model.get("fallback")), model.get("cases") instanceof JsonArray cases
                    && !cases.isEmpty() && cases.get(0) instanceof JsonObject first ? primary(first.get("model")) : null);
            case "range_dispatch" -> firstOf(primary(model.get("fallback")), model.get("entries") instanceof JsonArray list
                    && !list.isEmpty() && list.get(0) instanceof JsonObject first ? primary(first.get("model")) : null);
            case "composite" -> model.get("models") instanceof JsonArray models && !models.isEmpty()
                    ? primary(models.get(0)) : null;
            default -> null;
        };
    }

    /**
     * Ids for the entries, by namespace: the model's file name - ItemsAdder names a generated model
     * after its item - or, where two models share one, the model's whole path.
     */
    static Map<Entry, String> ids(List<Entry> entries) {
        Map<String, Map<String, Integer>> counts = new HashMap<>();
        for (Entry entry : entries) {
            counts.computeIfAbsent(entry.primary().namespace(), key -> new HashMap<>())
                    .merge(fileName(entry.primary()), 1, Integer::sum);
        }
        Map<Entry, String> ids = new LinkedHashMap<>();
        Map<String, java.util.Set<String>> used = new HashMap<>();
        for (Entry entry : entries) {
            String namespace = entry.primary().namespace();
            String id = fileName(entry.primary());
            if (counts.get(namespace).get(id) > 1) {
                id = sanitise(entry.primary().path().replace('/', '.'));
            }
            String unique = id;
            for (int n = 2; !used.computeIfAbsent(namespace, key -> new java.util.HashSet<>()).add(unique); n++) {
                unique = id + "_" + n;
            }
            ids.put(entry, unique);
        }
        return ids;
    }

    /** {@code nm_plushie_shulker} as {@code Nm Plushie Shulker}: the only name a generated pack gives. */
    static String displayName(String id) {
        StringBuilder name = new StringBuilder();
        for (String word : id.split("[_.\\-]+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!name.isEmpty()) {
                name.append(' ');
            }
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return name.toString();
    }

    private static String fileName(ResourceLocation location) {
        String path = location.path();
        return sanitise(path.substring(path.lastIndexOf('/') + 1));
    }

    private static String sanitise(String raw) {
        String id = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        return ITEM_ID.matcher(id).matches() ? id : "item";
    }

    /** Whether an overlay entry is active for a client of {@code format}. */
    static boolean covers(@Nullable JsonObject entry, int format) {
        if (entry == null) {
            return false;
        }
        // Before 1.21.9 (format 65) the client reads 'formats'; from then on min_format and max_format.
        JsonElement formats = entry.get("formats");
        if (formats != null && (format < 65 || (!entry.has("min_format") && !entry.has("max_format")))) {
            if (formats.isJsonPrimitive() && formats.getAsJsonPrimitive().isNumber()) {
                return formats.getAsInt() == format;
            }
            if (formats instanceof JsonArray range && range.size() == 2) {
                return range.get(0).getAsInt() <= format && format <= range.get(1).getAsInt();
            }
            if (formats instanceof JsonObject range) {
                return major(range.get("min_inclusive"), Integer.MIN_VALUE) <= format
                        && format <= major(range.get("max_inclusive"), Integer.MAX_VALUE);
            }
            return false;
        }
        return major(entry.get("min_format"), Integer.MIN_VALUE) <= format
                && format <= major(entry.get("max_format"), Integer.MAX_VALUE);
    }

    private static int major(@Nullable JsonElement value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            return value.getAsInt();
        }
        if (value instanceof JsonArray array && !array.isEmpty()) {
            return array.get(0).getAsInt();
        }
        return fallback;
    }

    private static int rank(List<String> order, @Nullable String overlay) {
        return overlay == null ? -1 : order.indexOf(overlay);
    }

    private static @Nullable String firstOf(@Nullable String a, @Nullable String b) {
        return a != null ? a : b;
    }

    private static @Nullable String string(JsonObject object, String key) {
        return object.get(key) instanceof JsonElement value && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    private static @Nullable String bare(@Nullable String type) {
        return type != null && type.startsWith("minecraft:") ? type.substring("minecraft:".length()) : type;
    }

    /** A folder name under packs/: lower case, a-z 0-9 _ . - only. */
    static String folderName(String raw) {
        String name = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]+", "_").replaceAll("^[_.-]+|[_.-]+$", "");
        return name.isEmpty() ? "itemsadder" : name;
    }

    static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private static boolean hidden(Path relative) {
        for (Path part : relative) {
            if (part.toString().startsWith(".")) {
                return true;
            }
        }
        return false;
    }

    static String unix(Path relative) {
        return relative.toString().replace(relative.getFileSystem().getSeparator(), "/");
    }
}
