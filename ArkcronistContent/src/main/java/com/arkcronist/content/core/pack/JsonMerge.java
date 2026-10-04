package com.arkcronist.content.core.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Two packs' versions of a file that is meant to be shared, combined into one.
 *
 * <p>Most files belong to one pack: a model or a texture at the same path in two packs is a real
 * clash, and the first is kept. But some files are lists every pack adds to - and keeping only the
 * first would silently drop the other pack's entries, which is how one plugin's hats or emojis
 * vanish when another's pack is merged in:</p>
 * <ul>
 *   <li>{@code atlases/*.json} - their {@code sources} are joined;</li>
 *   <li>{@code font/*.json} - their {@code providers} are joined, ours first, so on a character
 *       both draw ours wins;</li>
 *   <li>{@code sounds.json} and {@code lang/*.json} - their keys are joined;</li>
 *   <li>a vanilla item's {@code models/item/*.json} (the pre-1.21.4 way) - their
 *       {@code custom_model_data} overrides are joined and sorted, as the client needs them;</li>
 *   <li>a vanilla item's {@code items/*.json} (1.21.4+) - when both dispatch on
 *       {@code custom_model_data}, their entries are joined.</li>
 * </ul>
 * <p>Where both packs give the same key - one {@code custom_model_data} number to two models, one
 * sound event twice - that is a real ID collision: ours is kept and the collision is named, so it
 * can be renumbered.</p>
 */
public final class JsonMerge {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Pattern ATLAS = Pattern.compile("assets/[^/]+/atlases/[^/]+\\.json");
    private static final Pattern FONT = Pattern.compile("assets/[^/]+/font/[^/]+\\.json");
    private static final Pattern SOUNDS = Pattern.compile("assets/[^/]+/sounds\\.json");
    private static final Pattern LANG = Pattern.compile("assets/[^/]+/lang/[^/]+\\.json");
    private static final Pattern LEGACY_ITEM_MODEL = Pattern.compile("assets/[^/]+/models/item/.+\\.json");
    private static final Pattern ITEM_DEFINITION = Pattern.compile("assets/[^/]+/items/.+\\.json");

    /**
     * @param merged     the file to write; null when the two cannot be merged, and the first is kept
     * @param collisions what both gave differently, of which ours was kept
     */
    public record Result(@Nullable byte[] merged, List<String> collisions) {
        public Result {
            collisions = List.copyOf(collisions);
        }

        static Result unmerged() {
            return new Result(null, List.of());
        }
    }

    private JsonMerge() {
    }

    /** Whether files at this path are combined rather than kept from one pack. */
    public static boolean mergeable(String path) {
        return ATLAS.matcher(path).matches() || FONT.matcher(path).matches() || SOUNDS.matcher(path).matches()
                || LANG.matcher(path).matches() || LEGACY_ITEM_MODEL.matcher(path).matches()
                || ITEM_DEFINITION.matcher(path).matches();
    }

    /**
     * @param path   pack-relative, e.g. {@code assets/minecraft/font/default.json}
     * @param ours   what the pack has so far
     * @param theirs what the pack being merged in has
     */
    public static Result merge(String path, byte[] ours, byte[] theirs) {
        if (!mergeable(path)) {
            return Result.unmerged();
        }
        JsonObject a = object(ours);
        JsonObject b = object(theirs);
        if (a == null || b == null) {
            return Result.unmerged();
        }
        List<String> collisions = new ArrayList<>();
        JsonObject merged;
        if (ATLAS.matcher(path).matches()) {
            merged = joinArrays(a, b, "sources");
        } else if (FONT.matcher(path).matches()) {
            merged = joinArrays(a, b, "providers");
        } else if (SOUNDS.matcher(path).matches() || LANG.matcher(path).matches()) {
            merged = joinKeys(path, a, b, collisions);
        } else if (LEGACY_ITEM_MODEL.matcher(path).matches()) {
            merged = joinOverrides(path, a, b, collisions);
        } else {
            merged = joinDispatch(path, a, b, collisions);
        }
        return merged == null ? Result.unmerged()
                : new Result((GSON.toJson(merged) + "\n").getBytes(StandardCharsets.UTF_8), collisions);
    }

    private static @Nullable JsonObject object(byte[] bytes) {
        try {
            return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)) instanceof JsonObject object
                    ? object : null;
        } catch (JsonParseException exception) {
            return null;
        }
    }

    /** Ours, with each of their entries that is not there already added after. */
    private static @Nullable JsonObject joinArrays(JsonObject ours, JsonObject theirs, String key) {
        if (!(ours.get(key) instanceof JsonArray a) || !(theirs.get(key) instanceof JsonArray b)) {
            return null;
        }
        JsonObject merged = ours.deepCopy();
        JsonArray joined = a.deepCopy();
        for (JsonElement entry : b) {
            if (!joined.contains(entry)) {
                joined.add(entry.deepCopy());
            }
        }
        merged.add(key, joined);
        return merged;
    }

    private static JsonObject joinKeys(String path, JsonObject ours, JsonObject theirs, List<String> collisions) {
        JsonObject merged = ours.deepCopy();
        for (Map.Entry<String, JsonElement> entry : theirs.entrySet()) {
            JsonElement mine = merged.get(entry.getKey());
            if (mine == null) {
                merged.add(entry.getKey(), entry.getValue().deepCopy());
            } else if (!mine.equals(entry.getValue())) {
                collisions.add(path + ": '" + entry.getKey() + "' is defined by both packs - keeping ours");
            }
        }
        return merged;
    }

    /**
     * A vanilla item's model before 1.21.4: the base model and its overrides, each picking another
     * model when the stack's {@code custom_model_data} is at least its number. The client takes the
     * last override that matches, so they are sorted by that number.
     */
    private static @Nullable JsonObject joinOverrides(String path, JsonObject ours, JsonObject theirs,
                                                      List<String> collisions) {
        if (!(ours.get("overrides") instanceof JsonArray a) || !(theirs.get("overrides") instanceof JsonArray b)) {
            return null;
        }
        Map<Double, JsonObject> byNumber = new LinkedHashMap<>();
        List<JsonObject> others = new ArrayList<>();
        for (JsonArray overrides : List.of(a, b)) {
            boolean mine = overrides == a;
            for (JsonElement element : overrides) {
                if (!(element instanceof JsonObject override)) {
                    continue;
                }
                Double number = customModelData(override);
                if (number == null) {
                    if (!others.contains(override)) {
                        others.add(override);
                    }
                    continue;
                }
                JsonObject earlier = byNumber.get(number);
                if (earlier == null) {
                    byNumber.put(number, override);
                } else if (!earlier.equals(override) && !mine) {
                    collisions.add(path + ": custom_model_data " + format(number) + " is "
                            + earlier.get("model") + " in ours and " + override.get("model")
                            + " in theirs - keeping ours; give one of them another number");
                }
            }
        }
        JsonArray joined = new JsonArray();
        others.forEach(joined::add);
        byNumber.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> joined.add(entry.getValue()));
        JsonObject merged = ours.deepCopy();
        merged.add("overrides", joined);
        return merged;
    }

    private static @Nullable Double customModelData(JsonObject override) {
        if (override.get("predicate") instanceof JsonObject predicate && predicate.size() == 1
                && predicate.get("custom_model_data") instanceof JsonElement value && value.isJsonPrimitive()
                && value.getAsJsonPrimitive().isNumber()) {
            return value.getAsDouble();
        }
        return null;
    }

    /**
     * A vanilla item's definition from 1.21.4: when both dispatch on {@code custom_model_data}
     * (the same index), their entries are joined by threshold. The fallback is ours when we have
     * one.
     */
    private static @Nullable JsonObject joinDispatch(String path, JsonObject ours, JsonObject theirs,
                                                     List<String> collisions) {
        JsonObject a = dispatch(ours);
        JsonObject b = dispatch(theirs);
        if (a == null || b == null || index(a) != index(b)) {
            return null;
        }
        Map<Double, JsonObject> byThreshold = new LinkedHashMap<>();
        for (JsonObject entry : entries(a)) {
            byThreshold.putIfAbsent(entry.get("threshold").getAsDouble(), entry);
        }
        for (JsonObject entry : entries(b)) {
            double threshold = entry.get("threshold").getAsDouble();
            JsonObject earlier = byThreshold.putIfAbsent(threshold, entry);
            if (earlier != null && !earlier.equals(entry)) {
                collisions.add(path + ": custom_model_data " + format(threshold) + " is drawn by both packs - keeping"
                        + " ours; give one of them another number");
            }
        }
        JsonArray joined = new JsonArray();
        byThreshold.entrySet().stream()
                .sorted(Comparator.comparingDouble(Map.Entry::getKey))
                .forEach(entry -> joined.add(entry.getValue()));
        JsonObject model = a.deepCopy();
        model.add("entries", joined);
        if (!model.has("fallback") && b.has("fallback")) {
            model.add("fallback", b.get("fallback").deepCopy());
        }
        JsonObject merged = ours.deepCopy();
        merged.add("model", model);
        return merged;
    }

    private static @Nullable JsonObject dispatch(JsonObject definition) {
        if (definition.get("model") instanceof JsonObject model
                && "minecraft:range_dispatch".equals(string(model, "type"))
                && "minecraft:custom_model_data".equals(string(model, "property"))
                && model.get("entries") instanceof JsonArray entries
                && entries.asList().stream().allMatch(entry -> entry instanceof JsonObject object
                && object.get("threshold") instanceof JsonElement threshold && threshold.isJsonPrimitive()
                && threshold.getAsJsonPrimitive().isNumber())) {
            return model;
        }
        return null;
    }

    private static List<JsonObject> entries(JsonObject dispatch) {
        List<JsonObject> entries = new ArrayList<>();
        dispatch.getAsJsonArray("entries").forEach(entry -> entries.add(entry.getAsJsonObject()));
        return entries;
    }

    private static int index(JsonObject dispatch) {
        return dispatch.get("index") instanceof JsonElement index && index.isJsonPrimitive() ? index.getAsInt() : 0;
    }

    private static @Nullable String string(JsonObject object, String key) {
        return object.get(key) instanceof JsonElement value && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    private static String format(double number) {
        return number == Math.rint(number) ? String.valueOf((long) number) : String.valueOf(number);
    }
}
