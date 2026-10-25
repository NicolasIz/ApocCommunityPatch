package com.arkcronist.content.core.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

/** Reading the model an item definition ({@code assets/<ns>/items/<path>.json}) draws. */
public final class ItemModels {

    private ItemModels() {
    }

    /**
     * The model an item definition's {@code model} node draws: its plain model, or the fallback or
     * first case of a select, condition or range dispatch. Null when none can be found.
     */
    public static @Nullable String firstModel(@Nullable JsonElement node) {
        return firstModel(node, 0);
    }

    private static @Nullable String firstModel(@Nullable JsonElement node, int depth) {
        if (node == null || !node.isJsonObject() || depth > 8) {
            return null;
        }
        JsonObject object = node.getAsJsonObject();
        String type = object.has("type") ? object.get("type").getAsString() : "";
        if ((type.equals("minecraft:model") || type.equals("model")) && object.has("model")
                && object.get("model").isJsonPrimitive()) {
            return object.get("model").getAsString();
        }
        // A select, condition or range dispatch: what it draws by default, else its first branch.
        for (String key : new String[]{"fallback", "on_false", "on_true"}) {
            String found = firstModel(object.get(key), depth + 1);
            if (found != null) {
                return found;
            }
        }
        for (String list : new String[]{"cases", "entries", "models"}) {
            if (object.get(list) instanceof JsonArray array) {
                for (JsonElement entry : array) {
                    JsonElement inner = entry.isJsonObject() && entry.getAsJsonObject().has("model")
                            ? entry.getAsJsonObject().get("model") : entry;
                    String found = firstModel(inner, depth + 1);
                    if (found != null) {
                        return found;
                    }
                }
            }
        }
        return null;
    }
}
