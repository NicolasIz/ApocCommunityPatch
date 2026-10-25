package com.arkcronist.content.core.pack;

import com.arkcronist.content.core.block.NoteBlockState;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * A blockstate file's {@code variants}, read the way the client matches them: a key names some of
 * the block's properties ({@code instrument=harp} names one, {@code ""} none) and stands for every
 * state that has those values; where several keys match a state, the one naming the most wins.
 */
public final class BlockStates {

    private BlockStates() {
    }

    /** {@code instrument=harp,note=3} as its properties. */
    public static Map<String, String> properties(String key) {
        Map<String, String> properties = new LinkedHashMap<>();
        if (key.isBlank()) {
            return properties;
        }
        for (String pair : key.split(",")) {
            int equals = pair.indexOf('=');
            if (equals > 0) {
                properties.put(pair.substring(0, equals).trim(), pair.substring(equals + 1).trim());
            }
        }
        return properties;
    }

    /** The variant a state is drawn with, or null when no key matches it. */
    public static @Nullable JsonElement resolve(JsonObject variants, Map<String, String> state) {
        JsonElement best = null;
        int bestSize = -1;
        for (Map.Entry<String, JsonElement> variant : variants.entrySet()) {
            Map<String, String> key = properties(variant.getKey());
            if (state.entrySet().containsAll(key.entrySet()) && key.size() >= bestSize) {
                best = variant.getValue();
                bestSize = key.size();
            }
        }
        return best;
    }

    /** The model a variant draws - the first, for a weighted list. */
    public static @Nullable String model(@Nullable JsonElement variant) {
        if (variant instanceof JsonArray list && !list.isEmpty()) {
            return model(list.get(0));
        }
        return variant instanceof JsonObject object && object.get("model") instanceof JsonElement model
                && model.isJsonPrimitive() ? model.getAsString() : null;
    }

    /**
     * Whether a variant only draws the block as vanilla does: a model of the block's own name under
     * {@code block/} - {@code minecraft:block/note_block}, ItemsAdder's {@code block/original/note_block},
     * {@code block/tripwire_attached_ns}. Such a variant is no custom block, and gives way to one.
     */
    public static boolean vanillaLook(@Nullable JsonElement variant, String block) {
        String model = model(variant);
        if (model == null) {
            return true;
        }
        String path = model.substring(model.indexOf(':') + 1);
        return path.startsWith("block/") && path.contains(block);
    }

    /**
     * The note block states another pack's {@code note_block.json} - in its base assets or an overlay
     * - draws as something of its own: its custom blocks, which no block of ours is put on.
     */
    public static Set<Integer> customNoteBlockStates(PackSource source) throws IOException {
        List<String> files = new ArrayList<>(List.of("assets/minecraft/blockstates/note_block.json"));
        source.overlays().keySet().forEach(overlay -> files.add(overlay + "/assets/minecraft/blockstates/note_block.json"));
        Set<Integer> states = new TreeSet<>();
        for (String file : files) {
            byte[] bytes = source.read(file);
            JsonObject variants;
            try {
                variants = bytes != null && JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8))
                        instanceof JsonObject root && root.get("variants") instanceof JsonObject found ? found : null;
            } catch (JsonParseException exception) {
                variants = null;
            }
            if (variants == null) {
                continue;
            }
            for (int index = 0; index < NoteBlockState.CAPACITY; index++) {
                NoteBlockState state = NoteBlockState.fromIndex(index);
                if (!vanillaLook(resolve(variants, properties(state.variantKey())), "note_block")) {
                    states.add(index);
                }
            }
        }
        return states;
    }
}
