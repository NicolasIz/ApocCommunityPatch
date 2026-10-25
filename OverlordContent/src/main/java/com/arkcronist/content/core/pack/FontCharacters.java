package com.arkcronist.content.core.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * The characters a font draws: what a bitmap provider's {@code chars} rows and a space provider's
 * {@code advances} name.
 */
public final class FontCharacters {

    private FontCharacters() {
    }

    /**
     * Every character another pack's default font draws - in its base assets and its overlays - so
     * this plugin's emojis and HUD icons are never put on one of them.
     */
    public static Set<Integer> usedBy(PackSource source) throws IOException {
        List<String> files = new ArrayList<>(List.of("assets/minecraft/font/default.json"));
        source.overlays().keySet().forEach(overlay -> files.add(overlay + "/assets/minecraft/font/default.json"));
        Set<Integer> characters = new TreeSet<>();
        for (String file : files) {
            byte[] bytes = source.read(file);
            if (bytes == null) {
                continue;
            }
            try {
                if (JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)) instanceof JsonObject font) {
                    characters.addAll(of(font));
                }
            } catch (JsonParseException exception) {
                // A broken font draws nothing on the client either.
            }
        }
        return characters;
    }

    /** The characters of a whole font file, or of one provider. */
    public static Set<Integer> of(JsonObject fontOrProvider) {
        Set<Integer> characters = new TreeSet<>();
        if (fontOrProvider.get("providers") instanceof JsonArray providers) {
            for (JsonElement provider : providers) {
                if (provider instanceof JsonObject object) {
                    characters.addAll(of(object));
                }
            }
            return characters;
        }
        if (fontOrProvider.get("chars") instanceof JsonArray rows) {
            for (JsonElement row : rows) {
                if (row.isJsonPrimitive()) {
                    row.getAsString().codePoints().filter(character -> character != 0).forEach(characters::add);
                }
            }
        }
        if (isSpace(fontOrProvider) && fontOrProvider.get("advances") instanceof JsonObject advances) {
            for (Map.Entry<String, JsonElement> advance : advances.entrySet()) {
                if (!advance.getKey().isEmpty()) {
                    characters.add(advance.getKey().codePointAt(0));
                }
            }
        }
        return characters;
    }

    static boolean isSpace(JsonObject provider) {
        return provider.get("type") instanceof JsonElement type && type.isJsonPrimitive()
                && "space".equals(JsonMerge.bare(type.getAsString()));
    }

    /** {@code U+F801-U+F80F, U+F822-U+F82F}: runs of characters, for messages. */
    static String describe(SortedSet<Integer> characters) {
        StringBuilder text = new StringBuilder();
        Integer start = null;
        Integer previous = null;
        for (int character : characters) {
            if (previous != null && character == previous + 1) {
                previous = character;
                continue;
            }
            if (start != null) {
                run(text, start, previous);
            }
            start = character;
            previous = character;
        }
        if (start != null) {
            run(text, start, previous);
        }
        return text.toString();
    }

    private static void run(StringBuilder text, int start, int end) {
        if (!text.isEmpty()) {
            text.append(", ");
        }
        text.append(String.format("U+%04X", start));
        if (end != start) {
            text.append(String.format("-U+%04X", end));
        }
    }
}
