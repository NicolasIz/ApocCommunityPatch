package com.arkcronist.gen.bukkit.mythic;

import com.arkcronist.gen.bukkit.mythic.MobClassifier.Habitat;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reads the biomes a datapack defines, and the tags it files them under, straight from its files.
 *
 * <p>This is how {@code terralith:alpine_grove} becomes something a mob can be matched against. The
 * server knows the biome by name, but not that it is snowy: that lives in the pack, in the biome's
 * own file ({@code "temperature": -0.45}) and in the tags that list it ({@code c:is_snowy}). Terralith
 * nests its tags - {@code c:is_snowy} lists {@code #terralith:reference/temperature/frozen_all}, which
 * lists the biomes - so tags are followed to the bottom before anything is assigned.</p>
 *
 * <p>A pack may be a zip or a folder, and a zip may keep its {@code data} folder one level down, which
 * is how a pack downloaded and re-zipped by hand usually arrives. Anything that cannot be read is
 * skipped: a biome list missing one pack is still a biome list.</p>
 */
public final class DatapackBiomes {

    private static final Pattern ENTRY = Pattern.compile(
            "(?:^|.*/)data/([a-z0-9_.-]+)/(worldgen/biome|tags/worldgen/biome|dimension)/(.+)\\.json$");

    /** A biome's file, before its tags are known. */
    private record Raw(double temperature, double downfall) {
    }

    /**
     * Which dimension each biome is generated in, from the packs' own {@code dimension} files.
     *
     * <p>This is the only place a pack says it outright. Incendium replaces
     * {@code minecraft:the_nether} with a file that lists all thirteen of its biomes, and Nullscape
     * does the same for {@code minecraft:the_end}; the biome files themselves say nothing about
     * which side of a portal they are on. Tags usually say it too ({@code #minecraft:is_nether}), but
     * a tag is a courtesy a pack author may skip, while a biome missing from the dimension file is a
     * biome that is never generated. So this wins over the tags.</p>
     */
    private record Found(Map<String, Raw> biomes, Map<String, List<String>> tags,
                         Map<String, Habitat> dimensions) {
    }

    private DatapackBiomes() {
    }

    /**
     * Reads every datapack in these folders.
     *
     * @param folders folders holding datapacks, as zips or as folders; missing ones are skipped
     */
    public static List<BiomeProfile> read(List<Path> folders) {
        Map<String, Raw> biomes = new LinkedHashMap<>();
        Map<String, List<String>> tags = new HashMap<>();
        Map<String, Habitat> dimensions = new HashMap<>();
        Found found = new Found(biomes, tags, dimensions);
        for (Path folder : folders) {
            if (folder == null || !Files.isDirectory(folder)) {
                continue;
            }
            try (Stream<Path> packs = Files.list(folder)) {
                for (Path pack : (Iterable<Path>) packs::iterator) {
                    try {
                        if (Files.isDirectory(pack)) {
                            readFolder(pack, found);
                        } else if (pack.toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
                            readZip(pack, found);
                        }
                    } catch (IOException | RuntimeException skipped) {
                        // One unreadable pack does not cost the others.
                    }
                }
            } catch (IOException skipped) {
                // A folder that cannot be listed has nothing to give.
            }
        }
        return profiles(biomes, tags, dimensions);
    }

    private static void readZip(Path pack, Found found) throws IOException {
        try (ZipFile zip = new ZipFile(pack.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                Matcher match = ENTRY.matcher(entry.getName().replace('\\', '/'));
                if (match.matches()) {
                    try (InputStream in = zip.getInputStream(entry)) {
                        take(match, in, found);
                    }
                }
            }
        }
    }

    private static void readFolder(Path pack, Found found) throws IOException {
        try (Stream<Path> files = Files.walk(pack)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                Matcher match = ENTRY.matcher(pack.relativize(file).toString().replace('\\', '/'));
                if (match.matches()) {
                    try (InputStream in = Files.newInputStream(file)) {
                        take(match, in, found);
                    }
                }
            }
        }
    }

    private static void take(Matcher match, InputStream in, Found found) {
        Map<String, Raw> biomes = found.biomes();
        Map<String, List<String>> tags = found.tags();
        String key = match.group(1) + ":" + match.group(3);
        JsonElement json;
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            json = JsonParser.parseReader(reader);
        } catch (IOException | RuntimeException malformed) {
            return;
        }
        if (!json.isJsonObject()) {
            return;
        }
        JsonObject object = json.getAsJsonObject();
        if (match.group(2).equals("worldgen/biome")) {
            biomes.put(key, new Raw(number(object, "temperature"), number(object, "downfall")));
            return;
        }
        if (match.group(2).equals("dimension")) {
            Habitat habitat = dimensionHabitat(key, object);
            if (habitat != null) {
                for (String biome : dimensionBiomes(object)) {
                    found.dimensions().put(biome, habitat);
                }
            }
            return;
        }
        if (!object.has("values") || !object.get("values").isJsonArray()) {
            return;
        }
        List<String> values = tags.computeIfAbsent(key, k -> new ArrayList<>());
        for (JsonElement value : object.getAsJsonArray("values")) {
            if (value.isJsonPrimitive()) {
                values.add(value.getAsString());
            } else if (value.isJsonObject() && value.getAsJsonObject().has("id")) {
                values.add(value.getAsJsonObject().get("id").getAsString());
            }
        }
    }

    private static double number(JsonObject object, String field) {
        try {
            return object.has(field) ? object.get(field).getAsDouble() : Double.NaN;
        } catch (RuntimeException notANumber) {
            return Double.NaN;
        }
    }

    /**
     * Which dimension a dimension file describes: by its {@code type} first, which is what the game
     * uses, and by its own name when the type is a pack's own.
     */
    static Habitat dimensionHabitat(String key, JsonObject dimension) {
        String type = dimension.has("type") && dimension.get("type").isJsonPrimitive()
                ? dimension.get("type").getAsString().toLowerCase(Locale.ROOT) : "";
        for (String name : new String[] {type, key.toLowerCase(Locale.ROOT)}) {
            if (name.endsWith("the_nether") || name.endsWith(":nether")) {
                return Habitat.NETHER;
            }
            if (name.endsWith("the_end") || name.endsWith(":end")) {
                return Habitat.END;
            }
            if (name.endsWith("overworld")) {
                return Habitat.OVERWORLD;
            }
        }
        return null;
    }

    /**
     * Every biome a dimension file's biome source names: the {@code biomes} list of a
     * {@code multi_noise} or {@code checkerboard} source, or the single one of a {@code fixed}.
     * A tag in the list ({@code #incendium:all}) is kept with its {@code #} and resolved later.
     */
    static List<String> dimensionBiomes(JsonObject dimension) {
        List<String> found = new ArrayList<>();
        if (!dimension.has("generator") || !dimension.get("generator").isJsonObject()) {
            return found;
        }
        JsonObject generator = dimension.getAsJsonObject("generator");
        if (!generator.has("biome_source") || !generator.get("biome_source").isJsonObject()) {
            return found;
        }
        JsonObject source = generator.getAsJsonObject("biome_source");
        if (source.has("biome") && source.get("biome").isJsonPrimitive()) {
            found.add(source.get("biome").getAsString());
        }
        if (source.has("biomes")) {
            JsonElement list = source.get("biomes");
            if (list.isJsonPrimitive()) {
                found.add(list.getAsString());
            } else if (list.isJsonArray()) {
                for (JsonElement entry : list.getAsJsonArray()) {
                    if (entry.isJsonPrimitive()) {
                        found.add(entry.getAsString());
                    } else if (entry.isJsonObject() && entry.getAsJsonObject().has("biome")
                            && entry.getAsJsonObject().get("biome").isJsonPrimitive()) {
                        found.add(entry.getAsJsonObject().get("biome").getAsString());
                    }
                }
            }
        }
        List<String> keys = new ArrayList<>();
        for (String name : found) {
            String lower = name.trim().toLowerCase(Locale.ROOT);
            if (!lower.isEmpty()) {
                keys.add(lower.startsWith("#") || lower.contains(":") ? lower : "minecraft:" + lower);
            }
        }
        return keys;
    }

    /** Turns biome files and tag files into profiles, following nested tags. */
    static List<BiomeProfile> profiles(Map<String, Raw> biomes, Map<String, List<String>> tags) {
        return profiles(biomes, tags, Map.of());
    }

    /**
     * The same, with the dimension each biome was found generated in. A biome a dimension file
     * lists takes that dimension whatever its tags say; a tag in a dimension file gives its whole
     * membership.
     */
    static List<BiomeProfile> profiles(Map<String, Raw> biomes, Map<String, List<String>> tags,
                                       Map<String, Habitat> dimensions) {
        Map<String, Habitat> placed = new HashMap<>();
        Map<String, Set<String>> tagCache = new HashMap<>();
        dimensions.forEach((name, habitat) -> {
            if (name.startsWith("#")) {
                String tag = name.substring(1);
                for (String member : members(tag.contains(":") ? tag : "minecraft:" + tag, tags,
                        tagCache, new HashSet<>())) {
                    placed.put(member, habitat);
                }
            } else {
                placed.put(name, habitat);
            }
        });
        Map<String, Set<String>> tagWords = new HashMap<>();
        Map<String, Set<String>> resolved = new HashMap<>();
        for (String tag : tags.keySet()) {
            for (String biome : members(tag, tags, resolved, new HashSet<>())) {
                tagWords.computeIfAbsent(biome, k -> new LinkedHashSet<>()).addAll(tagWordsOf(tag));
            }
        }
        List<BiomeProfile> profiles = new ArrayList<>();
        for (Map.Entry<String, Raw> entry : biomes.entrySet()) {
            String key = entry.getKey();
            Set<String> words = tagWords.getOrDefault(key, Set.of());
            Habitat habitat = placed.get(key);
            profiles.add(BiomeProfile.of(key, words, entry.getValue().temperature(),
                    entry.getValue().downfall(), habitat != null ? habitat : habitat(key, words)));
        }
        return profiles;
    }

    private static Set<String> members(String tag, Map<String, List<String>> tags,
                                       Map<String, Set<String>> resolved, Set<String> visiting) {
        Set<String> done = resolved.get(tag);
        if (done != null) {
            return done;
        }
        Set<String> members = new LinkedHashSet<>();
        if (!visiting.add(tag)) {
            return members;                       // a tag that includes itself
        }
        for (String value : tags.getOrDefault(tag, List.of())) {
            String name = value.trim().toLowerCase(Locale.ROOT);
            if (name.startsWith("#")) {
                String inner = name.substring(1);
                members.addAll(members(inner.contains(":") ? inner : "minecraft:" + inner, tags,
                        resolved, visiting));
            } else if (!name.isEmpty()) {
                members.add(name.contains(":") ? name : "minecraft:" + name);
            }
        }
        visiting.remove(tag);
        resolved.put(tag, members);
        return members;
    }

    /** Every segment of a tag's path: {@code c:is_cold/overworld} gives is_cold and overworld. */
    static Set<String> tagWordsOf(String tag) {
        String path = tag.substring(tag.indexOf(':') + 1);
        Set<String> words = new LinkedHashSet<>();
        for (String part : path.split("/")) {
            if (!part.isEmpty()) {
                words.add(part);
            }
        }
        return words;
    }

    private static Habitat habitat(String key, Set<String> tagWords) {
        Habitat vanilla = VanillaBiomes.habitatOf(key);
        if (vanilla != null) {
            return vanilla;
        }
        if (tagWords.contains("is_nether")) {
            return Habitat.NETHER;
        }
        if (tagWords.contains("is_end")) {
            return Habitat.END;
        }
        return Habitat.OVERWORLD;
    }

    /**
     * Puts profiles from every source together. A datapack's own file beats the built-in vanilla
     * table - Terralith redefines the vanilla biomes too - and a name the registry knows that no
     * file described is kept with its name alone.
     */
    public static List<BiomeProfile> merge(List<BiomeProfile> vanilla, List<BiomeProfile> datapacks,
                                           List<String> registryKeys) {
        Map<String, BiomeProfile> all = new LinkedHashMap<>();
        for (BiomeProfile profile : vanilla) {
            all.put(profile.key(), profile);
        }
        for (BiomeProfile profile : datapacks) {
            BiomeProfile known = all.get(profile.key());
            if (known != null && !profile.climateKnown()) {
                profile = new BiomeProfile(profile.key(), profile.words(), profile.tags(),
                        known.temperature(), known.downfall(), profile.habitat());
            }
            all.put(profile.key(), profile);
        }
        if (registryKeys != null) {
            for (String key : registryKeys) {
                String lower = key.toLowerCase(Locale.ROOT);
                if (!all.containsKey(lower)) {
                    all.put(lower, BiomeProfile.of(lower, Set.of(), Double.NaN, Double.NaN,
                            Habitat.OVERWORLD));
                }
            }
        }
        return List.copyOf(all.values());
    }
}
