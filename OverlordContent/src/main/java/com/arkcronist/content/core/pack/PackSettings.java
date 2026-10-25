package com.arkcronist.content.core.pack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * What goes into {@code pack.mcmeta}.
 *
 * <p>The pack declares a range of formats rather than one number. The format moves with most
 * releases, and a client outside the declared range shows the pack as incompatible and asks the
 * player to load it anyway. The floor is 46 (1.21.4): that is the release that introduced the
 * {@code items/} folder and the {@code item_model} component this plugin is built on, so nothing
 * older could draw these items however the pack were labelled.</p>
 *
 * <p>Both spellings of the range are written. Clients before 1.21.9 read {@code pack_format} and
 * {@code supported_formats}; from 1.21.9 on they read {@code min_format} and {@code max_format}, and
 * still expect the older pair while the range reaches back below format 65.</p>
 *
 * @param description a chat component, already serialised to JSON
 * @param packFormat  the format the pack is written for; must lie inside the range
 */
public record PackSettings(JsonElement description, int packFormat, int minFormat, int maxFormat) {

    /** Resource pack format of 1.21.4, the first release with item model definitions. */
    public static final int FIRST_ITEM_MODEL_FORMAT = 46;

    public PackSettings {
        if (minFormat > maxFormat || packFormat < minFormat || packFormat > maxFormat) {
            throw new IllegalArgumentException("pack format " + packFormat + " must lie inside "
                    + minFormat + ".." + maxFormat);
        }
    }

    public JsonObject mcmeta() {
        JsonObject range = new JsonObject();
        range.addProperty("min_inclusive", minFormat);
        range.addProperty("max_inclusive", maxFormat);

        JsonObject pack = new JsonObject();
        pack.add("description", description.deepCopy());
        pack.addProperty("pack_format", packFormat);
        pack.add("supported_formats", range);
        pack.addProperty("min_format", minFormat);
        pack.addProperty("max_format", maxFormat);

        JsonObject root = new JsonObject();
        root.add("pack", pack);
        return root;
    }
}
