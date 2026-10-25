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
 *       both draw ours wins - except this plugin's space characters, which give way to the other
 *       pack's glyph: its menus and titles were laid out with it;</li>
 *   <li>{@code sounds.json} and {@code lang/*.json} - their keys are joined;</li>
 *   <li>a vanilla item's {@code models/item/*.json} (the pre-1.21.4 way) - their
 *       {@code custom_model_data} overrides are joined and sorted, as the client needs them;</li>
 *   <li>a vanilla item's {@code items/*.json} (1.21.4+) - when both dispatch on
 *       {@code custom_model_data}, their entries are joined; when one only gives the item another
 *       look, that look is what the other's dispatch falls back to;</li>
 *   <li>{@code blockstates/*.json} - state by state, a block of either pack wins over the plain
 *       vanilla look: how ItemsAdder's custom blocks and this plugin's share the note block.</li>
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
    private static final Pattern BLOCKSTATE = Pattern.compile("assets/[^/]+/blockstates/([^/]+)\\.json");

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
                || ITEM_DEFINITION.matcher(path).matches() || BLOCKSTATE.matcher(path).matches();
    }

    /**
     * @param path   pack-relative, e.g. {@code assets/minecraft/font/default.json}
     * @param ours   what the pack has so far
     * @param theirs what the pack being merged in has
     */
    public static Result merge(String path, byte[] ours, byte[] theirs) {
        return merge(path, ours, theirs, "ours", true);
    }

    /**
     * The other way round: {@code theirs} is the file - a merged pack's overlay copy, for clients of
     * other versions - and ours is joined into it. Where both give the same key, theirs is kept: in
     * its own overlay, the pack is drawn exactly as it was made.
     */
    public static Result mergeIntoTheirs(String path, byte[] ours, byte[] theirs) {
        return merge(path, theirs, ours, "the pack's own", false);
    }

    /**
     * @param kept        who wins a clash, for messages
     * @param yieldSpaces whether {@code ours}' space characters give way to {@code theirs}' glyphs
     */
    private static Result merge(String path, byte[] ours, byte[] theirs, String kept, boolean yieldSpaces) {
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
            merged = joinFonts(path, a, b, collisions, kept, yieldSpaces);
        } else if (SOUNDS.matcher(path).matches() || LANG.matcher(path).matches()) {
            merged = joinKeys(path, a, b, collisions, kept);
        } else if (LEGACY_ITEM_MODEL.matcher(path).matches()) {
            merged = joinOverrides(path, a, b, collisions, kept);
        } else if (BLOCKSTATE.matcher(path).matches()) {
            java.util.regex.Matcher block = BLOCKSTATE.matcher(path);
            block.matches();
            merged = joinVariants(path, block.group(1), a, b, collisions, kept);
        } else {
            merged = joinDispatch(path, a, b, collisions, kept);
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

    /**
     * Fonts: ours, with each of their providers after. The client draws a character with the first
     * provider that has it, so where both packs draw one, ours would win - right for an emoji or a
     * HUD icon, which have characters of their own (and are named here as a collision), but not for
     * the space characters: the other pack's titles and menus were laid out with its own glyph for
     * those, so ours give way on every character it draws.
     */
    private static @Nullable JsonObject joinFonts(String path, JsonObject ours, JsonObject theirs,
                                                  List<String> collisions, String kept, boolean yieldSpaces) {
        if (!(ours.get("providers") instanceof JsonArray a) || !(theirs.get("providers") instanceof JsonArray b)) {
            return null;
        }
        java.util.Set<Integer> theirChars = FontCharacters.of(theirs);
        java.util.TreeSet<Integer> yielded = new java.util.TreeSet<>();
        java.util.TreeSet<Integer> clashing = new java.util.TreeSet<>();
        JsonArray joined = new JsonArray();
        for (JsonElement element : a) {
            if (yieldSpaces && element instanceof JsonObject provider && FontCharacters.isSpace(provider)
                    && provider.get("advances") instanceof JsonObject advances) {
                JsonObject remaining = new JsonObject();
                for (Map.Entry<String, JsonElement> advance : advances.entrySet()) {
                    if (theirChars.contains(advance.getKey().codePointAt(0))) {
                        yielded.add(advance.getKey().codePointAt(0));
                    } else {
                        remaining.add(advance.getKey(), advance.getValue());
                    }
                }
                if (remaining.size() > 0) {
                    JsonObject copy = provider.deepCopy();
                    copy.add("advances", remaining);
                    joined.add(copy);
                }
                continue;
            }
            if (element instanceof JsonObject provider) {
                for (int character : FontCharacters.of(provider)) {
                    if (theirChars.contains(character)) {
                        clashing.add(character);
                    }
                }
            }
            joined.add(element.deepCopy());
        }
        for (JsonElement entry : b) {
            if (!joined.contains(entry)) {
                joined.add(entry.deepCopy());
            }
        }
        if (!yielded.isEmpty()) {
            collisions.add(path + ": " + yielded.size() + " space character(s) " + FontCharacters.describe(yielded)
                    + " are drawn by the other pack too - its glyphs are kept, as its menus were laid out with them;"
                    + " HUDs here move by that pack's widths for these");
        }
        if (!clashing.isEmpty()) {
            collisions.add(path + ": " + clashing.size() + " character(s) " + FontCharacters.describe(clashing)
                    + " are drawn by both packs - keeping " + kept + "; free them in one of the two");
        }
        JsonObject merged = ours.deepCopy();
        merged.add("providers", joined);
        return merged;
    }

    private static JsonObject joinKeys(String path, JsonObject ours, JsonObject theirs, List<String> collisions,
                                       String kept) {
        JsonObject merged = ours.deepCopy();
        for (Map.Entry<String, JsonElement> entry : theirs.entrySet()) {
            JsonElement mine = merged.get(entry.getKey());
            if (mine == null) {
                merged.add(entry.getKey(), entry.getValue().deepCopy());
            } else if (!mine.equals(entry.getValue())) {
                collisions.add(path + ": '" + entry.getKey() + "' is defined by both packs - keeping " + kept);
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
                                                      List<String> collisions, String kept) {
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
                            + " in theirs - keeping " + kept + "; give one of them another number");
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
     * one, and their other top-level settings ({@code oversized_in_gui},
     * {@code hand_animation_on_swap}) are kept where ours say nothing.
     *
     * <p>An entry that only draws its file's own fallback - the step back to the vanilla look this
     * plugin puts after each of its numbers - is no item: on the same number, the other pack's real
     * entry takes its place without a collision.</p>
     */
    private static @Nullable JsonObject joinDispatch(String path, JsonObject ours, JsonObject theirs,
                                                     List<String> collisions, String kept) {
        JsonObject a = dispatch(ours);
        JsonObject b = dispatch(theirs);
        if (a != null && b == null && theirs.get("model") instanceof JsonObject look) {
            return underDispatch(ours, theirs, look);
        }
        if (a == null && b != null && ours.get("model") instanceof JsonObject look) {
            return underDispatch(theirs, ours, look);
        }
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
            if (earlier == null || earlier.equals(entry) || filler(entry, b)) {
                continue;
            }
            if (filler(earlier, a)) {
                byThreshold.put(threshold, entry);
            } else {
                collisions.add(path + ": custom_model_data " + format(threshold) + " is drawn by both packs - keeping "
                        + kept + "; give one of them another number");
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
        for (Map.Entry<String, JsonElement> setting : theirs.entrySet()) {
            if (!merged.has(setting.getKey())) {
                merged.add(setting.getKey(), setting.getValue().deepCopy());
            }
        }
        return merged;
    }

    /**
     * One pack numbers the item, the other only gives it another look - ItemsAdder draws the barrier
     * item as one of its buttons. The numbers stay, and every other stack draws that look: it
     * becomes what the dispatch falls back to, at its steps back included.
     */
    private static JsonObject underDispatch(JsonObject numbered, JsonObject other, JsonObject look) {
        JsonObject merged = numbered.deepCopy();
        JsonObject dispatch = merged.getAsJsonObject("model");
        JsonElement fallback = dispatch.get("fallback");
        JsonArray entries = new JsonArray();
        for (JsonElement element : dispatch.getAsJsonArray("entries")) {
            JsonObject entry = element.getAsJsonObject().deepCopy();
            if (fallback != null && fallback.equals(entry.get("model"))) {
                entry.add("model", look.deepCopy());
            }
            entries.add(entry);
        }
        dispatch.add("entries", entries);
        dispatch.add("fallback", look.deepCopy());
        for (Map.Entry<String, JsonElement> setting : other.entrySet()) {
            if (!merged.has(setting.getKey())) {
                merged.add(setting.getKey(), setting.getValue().deepCopy());
            }
        }
        return merged;
    }

    /**
     * Blockstates, state by state. The states are every key, of either file, that names all the
     * block's properties - this plugin writes its note block and tripwire files that way, one key per
     * state - and each is drawn as whichever pack makes it something: a custom block wins over the
     * vanilla look, and where both make it a block of their own, {@code kept} keeps it.
     */
    private static @Nullable JsonObject joinVariants(String path, String block, JsonObject ours, JsonObject theirs,
                                                     List<String> collisions, String kept) {
        if (!(ours.get("variants") instanceof JsonObject a) || !(theirs.get("variants") instanceof JsonObject b)
                || ours.has("multipart") || theirs.has("multipart")) {
            return null;
        }
        java.util.Set<String> names = new java.util.HashSet<>();
        for (JsonObject variants : List.of(a, b)) {
            variants.keySet().forEach(key -> names.addAll(BlockStates.properties(key).keySet()));
        }
        java.util.Set<String> states = new java.util.LinkedHashSet<>();
        for (JsonObject variants : List.of(a, b)) {
            for (String key : variants.keySet()) {
                if (!names.isEmpty() && BlockStates.properties(key).keySet().equals(names)) {
                    states.add(key);
                }
            }
        }
        if (states.isEmpty()) {
            return null;
        }
        JsonObject variants = new JsonObject();
        List<String> clashes = new ArrayList<>();
        for (String key : states) {
            Map<String, String> state = BlockStates.properties(key);
            JsonElement mine = BlockStates.resolve(a, state);
            JsonElement other = BlockStates.resolve(b, state);
            JsonElement chosen;
            if (mine == null || other == null || mine.equals(other)) {
                chosen = mine != null ? mine : other;
            } else if (BlockStates.vanillaLook(mine, block)) {
                chosen = other;
            } else {
                if (!BlockStates.vanillaLook(other, block)) {
                    clashes.add(key);
                }
                chosen = mine;
            }
            variants.add(key, chosen.deepCopy());
        }
        if (!clashes.isEmpty()) {
            collisions.add(path + ": " + clashes.size() + " state(s), e.g. " + clashes.get(0) + ", are a block in"
                    + " both packs - keeping " + kept + "; give one of the two blocks another state");
        }
        JsonObject merged = ours.deepCopy();
        merged.add("variants", variants);
        return merged;
    }

    /** An entry that draws exactly what its dispatch falls back to. */
    private static boolean filler(JsonObject entry, JsonObject dispatch) {
        return dispatch.get("fallback") instanceof JsonObject fallback && fallback.equals(entry.get("model"));
    }

    /** ItemsAdder writes {@code range_dispatch}, vanilla {@code minecraft:range_dispatch}: the same to the client. */
    private static @Nullable JsonObject dispatch(JsonObject definition) {
        if (definition.get("model") instanceof JsonObject model
                && "range_dispatch".equals(bare(string(model, "type")))
                && "custom_model_data".equals(bare(string(model, "property")))
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

    static @Nullable String bare(@Nullable String id) {
        return id != null && id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    private static String format(double number) {
        return number == Math.rint(number) ? String.valueOf((long) number) : String.valueOf(number);
    }
}
