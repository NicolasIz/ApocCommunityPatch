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
            "(?:^|.*/)data/([a-z0-9_.-]+)/(worldgen/biome|tags/worldgen/biome)/(.+)\\.json$");

    /** A biome's file, before its tags are known. */
    private record Raw(double temperature, double downfall) {
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
        for (Path folder : folders) {
            if (folder == null || !Files.isDirectory(folder)) {
                continue;
            }
            try (Stream<Path> packs = Files.list(folder)) {
                for (Path pack : (Iterable<Path>) packs::iterator) {
                    try {
                        if (Files.isDirectory(pack)) {
                            readFolder(pack, biomes, tags);
                        } else if (pack.toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
                            readZip(pack, biomes, tags);
                        }
                    } catch (IOException | RuntimeException skipped) {
                        // One unreadable pack does not cost the others.
                    }
                }
            } catch (IOException skipped) {
                // A folder that cannot be listed has nothing to give.
            }
        }
        return profiles(biomes, tags);
    }

    private static void readZip(Path pack, Map<String, Raw> biomes, Map<String, List<String>> tags)
            throws IOException {
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
                        take(match, in, biomes, tags);
                    }
                }
            }
        }
    }

    private static void readFolder(Path pack, Map<String, Raw> biomes,
                                   Map<String, List<String>> tags) throws IOException {
        try (Stream<Path> files = Files.walk(pack)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                Matcher match = ENTRY.matcher(pack.relativize(file).toString().replace('\\', '/'));
                if (match.matches()) {
                    try (InputStream in = Files.newInputStream(file)) {
                        take(match, in, biomes, tags);
                    }
                }
            }
        }
    }

    private static void take(Matcher match, InputStream in, Map<String, Raw> biomes,
                             Map<String, List<String>> tags) {
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

    /** Turns biome files and tag files into profiles, following nested tags. */
    static List<BiomeProfile> profiles(Map<String, Raw> biomes, Map<String, List<String>> tags) {
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
            profiles.add(BiomeProfile.of(key, words, entry.getValue().temperature(),
                    entry.getValue().downfall(), habitat(key, words)));
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
