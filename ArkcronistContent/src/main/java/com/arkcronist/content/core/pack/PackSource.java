package com.arkcronist.content.core.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * The files of another plugin's resource pack - a folder or a zip - read one at a time, exactly as
 * they are.
 *
 * <p>What is taken: {@code assets/}, and the {@code assets/} of every overlay the pack's
 * {@code pack.mcmeta} declares ({@code overlays.entries[].directory}). Overlays are folders a client
 * of the right version lays over the base, and generated packs keep in them whatever changed
 * between releases - ItemsAdder its {@code custom_model_data} definitions for 1.21.6+ and its
 * armour for 1.21.4+, MythicArmor its equipment. Nothing else is read: the {@code pack.mcmeta}
 * itself is this plugin's, and a folder no overlay names is read by no client.</p>
 *
 * <p>A zip may hold the pack at its top or one folder down. Links in a folder are not followed and a
 * zip entry naming {@code ..} is skipped: the merged pack is served publicly.</p>
 */
public final class PackSource implements Closeable {

    /** What the client accepts as an overlay directory. */
    private static final Pattern OVERLAY = Pattern.compile("[a-z0-9_.-]+");

    private final String name;
    private final @Nullable Path folder;
    private final @Nullable ZipFile zip;
    private final String prefix;
    private final List<String> problems = new ArrayList<>();
    private final Map<String, JsonObject> overlays = new LinkedHashMap<>();
    private @Nullable SortedSet<String> files;

    private PackSource(String name, @Nullable Path folder, @Nullable ZipFile zip, String prefix) {
        this.name = name;
        this.folder = folder;
        this.zip = zip;
        this.prefix = prefix;
    }

    /**
     * Opens a pack; one that is missing or unreadable opens empty, with the reason in
     * {@link #problems()}.
     */
    public static PackSource open(ExternalPack pack) throws IOException {
        Path root = pack.root();
        PackSource source;
        if (Files.isDirectory(root)) {
            source = new PackSource(pack.name(), root, null, "");
        } else if (Files.isRegularFile(root)) {
            ZipFile zip;
            try {
                zip = new ZipFile(root.toFile());
            } catch (ZipException exception) {
                PackSource empty = new PackSource(pack.name(), null, null, "");
                empty.problems.add(root + " is not a readable zip - nothing merged");
                empty.files = Collections.emptySortedSet();
                return empty;
            }
            source = new PackSource(pack.name(), null, zip, zipPrefix(zip));
        } else {
            PackSource empty = new PackSource(pack.name(), null, null, "");
            empty.problems.add("no assets folder in " + root + " - nothing merged");
            empty.files = Collections.emptySortedSet();
            return empty;
        }
        source.readOverlays();
        return source;
    }

    public String name() {
        return name;
    }

    /** What could not be read, without the pack's name. */
    public List<String> problems() {
        return List.copyOf(problems);
    }

    /**
     * The overlay entries of the pack's {@code pack.mcmeta}, in its order, by directory - each
     * object exactly as written, format ranges and all.
     */
    public Map<String, JsonObject> overlays() {
        return Collections.unmodifiableMap(overlays);
    }

    /** Every file taken, pack-relative ({@code assets/...}, {@code <overlay>/assets/...}), sorted. */
    public SortedSet<String> files() throws IOException {
        if (files == null) {
            files = Collections.unmodifiableSortedSet(list());
            if (files.isEmpty()) {
                problems.add(folder != null ? "no assets folder in " + folder + " - nothing merged"
                        : "no assets/ in " + zip.getName() + " - nothing merged");
            }
        }
        return files;
    }

    /**
     * The files directly in one folder of the pack ({@code assets/minecraft/items}), pack-relative
     * and sorted; without listing the whole pack when it is a folder.
     */
    public List<String> children(String folderPath) throws IOException {
        String prefix = folderPath.endsWith("/") ? folderPath : folderPath + "/";
        if (folder != null && files == null) {
            Path directory = folder.resolve(folderPath);
            if (!taken(prefix + "x") || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || !inside(directory)) {
                return List.of();
            }
            try (Stream<Path> list = Files.list(directory)) {
                return list.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                        .map(path -> prefix + path.getFileName())
                        .sorted()
                        .toList();
            }
        }
        List<String> children = new ArrayList<>();
        for (String file : files()) {
            if (file.startsWith(prefix) && file.indexOf('/', prefix.length()) < 0) {
                children.add(file);
            }
        }
        return children;
    }

    /** Whether the pack has this pack-relative file. */
    public boolean has(String relative) throws IOException {
        if (folder != null && files == null) {
            Path file = folder.resolve(relative);
            return taken(relative) && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && inside(file);
        }
        return files().contains(relative);
    }

    /** A folder on the way may be a link; the real path settles whether the file is the pack's. */
    private boolean inside(Path path) {
        try {
            return path.toRealPath().startsWith(folder.toRealPath());
        } catch (IOException exception) {
            return false;
        }
    }

    /** The bytes of a file, or null when the pack does not have it. */
    public @Nullable byte[] read(String relative) throws IOException {
        if (!has(relative)) {
            return null;
        }
        if (folder != null) {
            return Files.readAllBytes(folder.resolve(relative));
        }
        ZipEntry entry = zip.getEntry(prefix + relative);
        try (InputStream in = zip.getInputStream(entry)) {
            return in.readAllBytes();
        }
    }

    /** The overlay a pack-relative path sits in, or null for the base {@code assets/}. */
    public static @Nullable String overlayOf(String relative) {
        int slash = relative.indexOf('/');
        return relative.startsWith("assets/") || slash < 0 ? null : relative.substring(0, slash);
    }

    /** The path inside its overlay: {@code assets/...} either way. */
    public static String inner(String relative) {
        return overlayOf(relative) == null ? relative : relative.substring(relative.indexOf('/') + 1);
    }

    @Override
    public void close() throws IOException {
        if (zip != null) {
            zip.close();
        }
    }

    // ------------------------------------------------------------ reading

    private void readOverlays() throws IOException {
        byte[] bytes = null;
        if (folder != null) {
            Path mcmeta = folder.resolve("pack.mcmeta");
            if (Files.isRegularFile(mcmeta, LinkOption.NOFOLLOW_LINKS)) {
                bytes = Files.readAllBytes(mcmeta);
            }
        } else if (zip.getEntry(prefix + "pack.mcmeta") instanceof ZipEntry entry && !entry.isDirectory()) {
            try (InputStream in = zip.getInputStream(entry)) {
                bytes = in.readAllBytes();
            }
        }
        if (bytes == null) {
            return;
        }
        JsonElement root;
        try {
            root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
        } catch (JsonParseException exception) {
            problems.add("pack.mcmeta is not valid JSON - its overlays are not merged");
            return;
        }
        if (!(root instanceof JsonObject object) || !(object.get("overlays") instanceof JsonObject section)
                || !(section.get("entries") instanceof JsonArray entries)) {
            return;
        }
        for (JsonElement element : entries) {
            if (!(element instanceof JsonObject entry) || !(entry.get("directory") instanceof JsonElement directory)
                    || !directory.isJsonPrimitive()) {
                problems.add("pack.mcmeta has an overlay entry without a directory - skipped");
                continue;
            }
            String dir = directory.getAsString();
            if (!OVERLAY.matcher(dir).matches() || dir.equals("assets")) {
                problems.add("overlay directory '" + dir + "' is not one a client accepts (a-z 0-9 _ . -) - skipped");
                continue;
            }
            overlays.putIfAbsent(dir, entry.deepCopy());
        }
    }

    private SortedSet<String> list() throws IOException {
        SortedSet<String> found = new TreeSet<>();
        if (folder != null) {
            List<Path> roots = new ArrayList<>();
            roots.add(folder.resolve("assets"));
            for (String overlay : overlays.keySet()) {
                roots.add(folder.resolve(overlay).resolve("assets"));
            }
            for (Path assets : roots) {
                if (!Files.isDirectory(assets, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                try (Stream<Path> walk = Files.walk(assets)) {
                    for (Path file : walk.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).toList()) {
                        Path relative = folder.relativize(file);
                        found.add(relative.toString().replace(relative.getFileSystem().getSeparator(), "/"));
                    }
                }
            }
            return found;
        }
        for (ZipEntry entry : Collections.list(zip.entries())) {
            String entryName = entry.getName().replace('\\', '/');
            if (entry.isDirectory() || !entryName.startsWith(prefix)) {
                continue;
            }
            String relative = entryName.substring(prefix.length());
            if (taken(relative)) {
                found.add(relative);
            }
        }
        return found;
    }

    /** Inside {@code assets/} or a declared overlay's, and nothing that climbs out of it. */
    private boolean taken(String relative) {
        if (relative.startsWith("/") || relative.contains("\\") || relative.endsWith("/")) {
            return false;
        }
        for (String part : relative.split("/")) {
            if (part.isEmpty() || part.equals("..") || part.equals(".")) {
                return false;
            }
        }
        if (relative.startsWith("assets/")) {
            return true;
        }
        int slash = relative.indexOf('/');
        return slash > 0 && overlays.containsKey(relative.substring(0, slash))
                && relative.startsWith("assets/", slash + 1);
    }

    /**
     * The folder the pack sits under inside a zip: {@code ""} for none, or one folder down
     * ({@code "MyPack/"}).
     */
    public static String zipPrefix(ZipFile zip) {
        Set<String> wrappers = new TreeSet<>();
        for (ZipEntry entry : Collections.list(zip.entries())) {
            String entryName = entry.getName().replace('\\', '/');
            if (entryName.equals("pack.mcmeta") || entryName.startsWith("assets/")) {
                return "";
            }
            int slash = entryName.indexOf('/');
            if (slash > 0 && (entryName.startsWith("pack.mcmeta", slash + 1) && entryName.length() == slash + 12
                    || entryName.startsWith("assets/", slash + 1))) {
                wrappers.add(entryName.substring(0, slash + 1));
            }
        }
        return wrappers.isEmpty() ? "" : wrappers.iterator().next();
    }
}
