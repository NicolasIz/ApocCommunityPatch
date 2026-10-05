package com.arkcronist.content.core.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * The other way to draw this plugin's items: a vanilla material with a {@code custom_model_data}
 * number, for plugins that can only be told "this material, this number".
 *
 * <p>Older plugins, and many still written that way - ClueScrolls' scrolls, Holographic Displays'
 * floating items, BossShop's and DeluxeMenus' {@code model_data}, any menu or reward that takes a
 * material and a number - cannot be given an {@code item_model}. ItemsAdder covers them by giving
 * every item a number; so does this plugin: each item drawn from a material whose vanilla
 * definition is one plain model gets a stable number, and the pack's definition of that material
 * picks the item's look for that number and the vanilla look for any other.</p>
 *
 * <p>The client's {@code range_dispatch} picks the entry with the highest threshold not above the
 * stack's number, so a number between two of ours would borrow the lower one's look. Each number is
 * therefore followed by an entry back to the vanilla model at the next number up, unless that is one
 * of ours too: exactly our numbers draw our items.</p>
 *
 * <p>Materials whose vanilla definition is more than one model - bows that draw back, banners,
 * armour with trims, clocks - are left out: replacing their definition would lose what it does.</p>
 */
public final class ModelDataDispatch {

    private static final Pattern VANILLA_DEFINITION = Pattern.compile("assets/minecraft/items/[^/]+\\.json");

    private static volatile @Nullable Map<String, String> vanilla;

    private ModelDataDispatch() {
    }

    /**
     * The vanilla model an item is drawn with, when its whole definition is that one model; null for
     * the rest. From the 1.21.8 client's {@code assets/minecraft/items/}.
     *
     * @param material a Bukkit material name, {@code PAPER}
     */
    public static @Nullable String vanillaModel(String material) {
        return vanillaModels().get(material.toLowerCase(Locale.ROOT));
    }

    /** Whether items made from this material can be drawn by number. */
    public static boolean dispatchable(String material) {
        return vanillaModel(material) != null;
    }

    /**
     * The definition of a vanilla material that draws {@code models} for their numbers and the
     * vanilla model otherwise: what goes in {@code assets/minecraft/items/<material>.json}.
     *
     * @param models each number's item model, the {@code model} object of an item definition
     * @throws IllegalArgumentException for a material {@link #dispatchable} refuses
     */
    public static JsonObject definition(String material, SortedMap<Integer, JsonObject> models) {
        String model = vanillaModel(material);
        if (model == null) {
            throw new IllegalArgumentException(material + " has no plain vanilla model to fall back to");
        }
        JsonObject fallback = new JsonObject();
        fallback.addProperty("type", "minecraft:model");
        fallback.addProperty("model", "minecraft:" + model);

        JsonArray entries = new JsonArray();
        for (Map.Entry<Integer, JsonObject> entry : models.entrySet()) {
            entries.add(entry(entry.getKey(), entry.getValue()));
            int next = entry.getKey() + 1;
            if (!models.containsKey(next)) {
                entries.add(entry(next, fallback));
            }
        }
        JsonObject dispatch = new JsonObject();
        dispatch.addProperty("type", "minecraft:range_dispatch");
        dispatch.addProperty("property", "minecraft:custom_model_data");
        dispatch.addProperty("index", 0);
        dispatch.add("entries", entries);
        dispatch.add("fallback", fallback);
        JsonObject definition = new JsonObject();
        definition.add("model", dispatch);
        return definition;
    }

    /**
     * The {@code custom_model_data} numbers another pack's vanilla item definitions draw, in its
     * base assets and in its overlays: an ItemsAdder pack's, a cosmetics plugin's. None of them is
     * handed to an item of this plugin, so that both packs' numbers keep drawing what they did.
     */
    public static Set<Integer> numbersUsedBy(PackSource source) throws IOException {
        Set<Integer> numbers = new TreeSet<>();
        List<String> folders = new java.util.ArrayList<>(List.of("assets/minecraft/items"));
        source.overlays().keySet().forEach(overlay -> folders.add(overlay + "/assets/minecraft/items"));
        List<String> files = new java.util.ArrayList<>();
        for (String folder : folders) {
            files.addAll(source.children(folder));
        }
        for (String file : files) {
            if (!VANILLA_DEFINITION.matcher(PackSource.inner(file)).matches()) {
                continue;
            }
            byte[] bytes = source.read(file);
            JsonElement root;
            try {
                root = bytes == null ? null : JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            } catch (JsonParseException exception) {
                continue;
            }
            if (root instanceof JsonObject definition && definition.get("model") instanceof JsonObject model
                    && "custom_model_data".equals(JsonMerge.bare(string(model, "property")))
                    && "range_dispatch".equals(JsonMerge.bare(string(model, "type")))
                    && model.get("entries") instanceof JsonArray entries) {
                for (JsonElement entry : entries) {
                    if (entry instanceof JsonObject object && object.get("threshold") instanceof JsonElement threshold
                            && threshold.isJsonPrimitive() && threshold.getAsJsonPrimitive().isNumber()) {
                        double value = threshold.getAsDouble();
                        if (value == Math.rint(value) && Math.abs(value) <= Integer.MAX_VALUE) {
                            numbers.add((int) value);
                        }
                    }
                }
            }
        }
        return numbers;
    }

    private static @Nullable String string(JsonObject object, String key) {
        return object.get(key) instanceof JsonElement value && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    private static JsonObject entry(int threshold, JsonObject model) {
        JsonObject entry = new JsonObject();
        entry.addProperty("threshold", threshold);
        entry.add("model", model.deepCopy());
        return entry;
    }

    private static Map<String, String> vanillaModels() {
        Map<String, String> models = vanilla;
        if (models == null) {
            models = load();
            vanilla = models;
        }
        return models;
    }

    private static Map<String, String> load() {
        Map<String, String> models = new HashMap<>();
        try (InputStream in = ModelDataDispatch.class.getResourceAsStream("/vanilla-item-models.txt")) {
            if (in == null) {
                return Map.of();
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                String[] parts = trimmed.split("\\s+");
                if (parts.length == 2) {
                    models.put(parts[0], parts[1]);
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return Map.copyOf(models);
    }
}
