package com.arkcronist.content.core.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;
import java.util.SortedSet;
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
 * {@code assets/bosses/sounds/golem/roar.ogg} the event plays nothing. Such names are found here so
 * they can be reported; the file is never rewritten.</p>
 */
public final class SoundNames {

    private SoundNames() {
    }

    /**
     * The names in {@code namespace}'s sounds.json that have no namespace, whose file is in
     * {@code namespace}'s sounds/ and not in minecraft's.
     *
     * @param exists whether the pack has a pack-relative file, {@code assets/x/sounds/y.ogg}
     */
    public static SortedSet<String> bareButOwn(JsonObject sounds, String namespace, Predicate<String> exists) {
        SortedSet<String> names = new TreeSet<>();
        if (namespace.equals("minecraft")) {
            return names;
        }
        for (Map.Entry<String, JsonElement> event : sounds.entrySet()) {
            if (!(event.getValue() instanceof JsonObject registration)
                    || !(registration.get("sounds") instanceof JsonArray list)) {
                continue;
            }
            for (JsonElement sound : list) {
                String name = null;
                if (sound.isJsonPrimitive()) {
                    name = sound.getAsString();
                } else if (sound instanceof JsonObject object && object.get("name") instanceof JsonElement value
                        && value.isJsonPrimitive()
                        && !(object.get("type") instanceof JsonElement type && type.isJsonPrimitive()
                        && type.getAsString().equals("event"))) {
                    name = value.getAsString();
                }
                if (name != null && name.indexOf(':') < 0
                        && exists.test("assets/" + namespace + "/sounds/" + name + ".ogg")
                        && !exists.test("assets/minecraft/sounds/" + name + ".ogg")) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    /** The report for {@link #bareButOwn}'s names, or null when there are none. */
    public static String describe(String file, String namespace, SortedSet<String> names) {
        if (names.isEmpty()) {
            return null;
        }
        String first = names.first();
        return file + ": " + names.size() + " sound name(s) have no namespace, so the client plays them from"
                + " minecraft:sounds/ - e.g. '" + first + "' is minecraft:sounds/" + first + ".ogg - while the files are"
                + " in " + namespace + "/sounds/; those events play nothing. Left as they are: write '" + namespace
                + ":" + first + "' (and the same for the rest) in sounds.json to make them play";
    }
}
