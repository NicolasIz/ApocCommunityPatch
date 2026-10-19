package com.arkcronist.content.core.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * The sound files a {@code sounds.json} names, read as the client reads them.
 *
 * <p>A sound's {@code name} is a location, and one written with no namespace means
 * {@code minecraft:} - whichever namespace's {@code sounds.json} it is in (1.21.8's
 * {@code SoundEventRegistrationSerializer} parses it with {@code ResourceLocation.parse}). So
 * {@code "golem/roar"} in {@code assets/bosses/sounds.json} is played from
 * {@code assets/minecraft/sounds/golem/roar.ogg}, and when the file is really at
 * {@code assets/bosses/sounds/golem/roar.ogg} the event plays nothing. The same happens to a name
 * that keeps the namespace of the pack it was first made for ({@code enderman_overhaul:cave_hurt_1}
 * in a pack that renamed it {@code enderman_expansion}). Such names are found here, and pointed at
 * the namespace their file is in ({@link #fix}) - in the pack that is built, not in the source -
 * unless {@code pack.fix-sound-names} is off, when they are only reported.</p>
 */
public final class SoundNames {

    private SoundNames() {
    }

    /**
     * The names in {@code namespace}'s sounds.json whose file is not where they point but is in
     * {@code namespace}'s own sounds/, each with the name that plays it: {@code golem/roar} to
     * {@code bosses:golem/roar}.
     *
     * @param exists whether the pack has a pack-relative file, {@code assets/x/sounds/y.ogg}
     */
    public static SortedMap<String, String> misplaced(JsonObject sounds, String namespace, Predicate<String> exists) {
        SortedMap<String, String> names = new TreeMap<>();
        if (namespace.equals("minecraft")) {
            return names;
        }
        for (Map.Entry<String, JsonElement> event : sounds.entrySet()) {
            if (!(event.getValue() instanceof JsonObject registration)
                    || !(registration.get("sounds") instanceof JsonArray list)) {
                continue;
            }
            for (JsonElement sound : list) {
                String name = name(sound);
                if (name == null) {
                    continue;
                }
                int colon = name.indexOf(':');
                String target = colon < 0 ? "minecraft" : name.substring(0, colon);
                String path = name.substring(colon + 1);
                if (!target.equals(namespace) && exists.test("assets/" + namespace + "/sounds/" + path + ".ogg")
                        && !exists.test("assets/" + target + "/sounds/" + path + ".ogg")) {
                    names.put(name, namespace + ":" + path);
                }
            }
        }
        return names;
    }

    /**
     * A copy of {@code sounds} with each name of {@code names} replaced by the one it maps to; every
     * other part - events, volumes, pitches, weights, streaming, subtitles - as it was.
     */
    public static JsonObject fix(JsonObject sounds, Map<String, String> names) {
        JsonObject fixed = sounds.deepCopy();
        for (Map.Entry<String, JsonElement> event : fixed.entrySet()) {
            if (!(event.getValue() instanceof JsonObject registration)
                    || !(registration.get("sounds") instanceof JsonArray list)) {
                continue;
            }
            for (int i = 0; i < list.size(); i++) {
                JsonElement sound = list.get(i);
                String name = name(sound);
                String replacement = name == null ? null : names.get(name);
                if (replacement == null) {
                    continue;
                }
                if (sound.isJsonPrimitive()) {
                    list.set(i, new JsonPrimitive(replacement));
                } else {
                    sound.getAsJsonObject().addProperty("name", replacement);
                }
            }
        }
        return fixed;
    }

    /** The report for {@link #misplaced}'s names when they are left as they are, or null for none. */
    public static String describe(String file, String namespace, SortedMap<String, String> names) {
        if (names.isEmpty()) {
            return null;
        }
        String first = names.firstKey();
        return file + ": " + names.size() + " sound name(s) point where their file is not - e.g. '" + first
                + "', which the client plays from " + played(first) + " - while the files are in " + namespace
                + "/sounds/; those events play nothing. Left as they are: write '" + names.get(first)
                + "' (and the same for the rest) in sounds.json to make them play";
    }

    /** The note for {@link #misplaced}'s names once fixed. */
    public static String describeFixed(String file, String namespace, SortedMap<String, String> names) {
        String first = names.firstKey();
        return file + ": " + names.size() + " sound name(s) pointed where their file is not and now name '"
                + namespace + ":', where it is - e.g. '" + first + "' is now '" + names.get(first)
                + "' (pack.fix-sound-names)";
    }

    /**
     * 3.5.3: the vanilla sounds a {@code minecraft} sounds.json silences - an event replaced by no sound
     * at all. ItemsAdder's pack does this to {@code block.wood.*} and {@code block.stone.*} (its blocks are
     * note blocks and mushroom blocks, which sound like wood) and plays wood and stone itself, as
     * {@code internal.block.*}; without ItemsAdder running, nothing plays them, and every wooden or stone
     * block, step and blow - and every animation or skill that plays those sounds - is silent.
     */
    public static SortedSet<String> silenced(JsonObject sounds, String namespace) {
        SortedSet<String> silenced = new TreeSet<>();
        if (!namespace.equals("minecraft")) {
            return silenced;
        }
        for (Map.Entry<String, JsonElement> event : sounds.entrySet()) {
            if (event.getValue() instanceof JsonObject registration
                    && registration.get("replace") instanceof JsonElement replace && replace.isJsonPrimitive()
                    && replace.getAsBoolean()
                    && (!(registration.get("sounds") instanceof JsonArray list) || list.isEmpty())) {
                silenced.add(event.getKey());
            }
        }
        return silenced;
    }

    /** A copy of {@code sounds} without the events of {@code silenced}: vanilla's play again. */
    public static JsonObject unsilence(JsonObject sounds, Set<String> silenced) {
        JsonObject kept = new JsonObject();
        for (Map.Entry<String, JsonElement> event : sounds.entrySet()) {
            if (!silenced.contains(event.getKey())) {
                kept.add(event.getKey(), event.getValue().deepCopy());
            }
        }
        return kept;
    }

    /** The note for {@link #silenced}'s events once given back to vanilla. */
    public static String describeUnsilenced(String file, SortedSet<String> silenced) {
        return file + ": " + silenced.size() + " vanilla sound(s) a merged pack silenced play again: "
                + String.join(", ", silenced) + ". ItemsAdder silences them and plays them itself; it is not"
                + " installed here, so nothing else would (pack.unsilence-vanilla-sounds)";
    }

    /** Where the client looks for a sound name's file: {@code minecraft:sounds/golem/roar.ogg}. */
    private static String played(String name) {
        int colon = name.indexOf(':');
        return (colon < 0 ? "minecraft" : name.substring(0, colon)) + ":sounds/" + name.substring(colon + 1) + ".ogg";
    }

    /** A sound entry's file name; null for one that names another event. */
    private static String name(JsonElement sound) {
        if (sound.isJsonPrimitive()) {
            return sound.getAsString();
        }
        if (sound instanceof JsonObject object && object.get("name") instanceof JsonElement value && value.isJsonPrimitive()
                && !(object.get("type") instanceof JsonElement type && type.isJsonPrimitive()
                && type.getAsString().equals("event"))) {
            return value.getAsString();
        }
        return null;
    }
}
