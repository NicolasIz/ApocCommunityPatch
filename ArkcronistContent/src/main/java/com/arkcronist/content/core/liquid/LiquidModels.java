package com.arkcronist.content.core.liquid;

import com.arkcronist.content.core.definition.ResourceLocation;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * What the pack draws liquids with: two parent models every liquid's own model sits under, and the
 * tripwire blockstate file that points each liquid's states at them.
 */
public final class LiquidModels {

    /** Not a content namespace of anyone's: where the plugin keeps models of its own. */
    public static final String NAMESPACE = "arkcontent";
    public static final ResourceLocation SOURCE_PARENT = new ResourceLocation(NAMESPACE, "block/liquid_source");
    public static final ResourceLocation FLOWING_PARENT = new ResourceLocation(NAMESPACE, "block/liquid_flowing");
    /** The variable a liquid's texture is given as in its model. */
    public static final String TEXTURE_VARIABLE = "liquid";

    /** A source's surface sits two pixels under the top of the block, like water's; flowing liquid lower. */
    public static final int SOURCE_HEIGHT = 14;
    public static final int FLOWING_HEIGHT = 10;

    private LiquidModels() {
    }

    /**
     * One of the parents: a box {@code height} pixels high with the texture on every face, unshaded,
     * and each face but the top left out against a full block beside it.
     */
    public static JsonObject parent(int height) {
        JsonObject faces = new JsonObject();
        for (String face : new String[]{"down", "north", "south", "east", "west"}) {
            JsonObject entry = new JsonObject();
            entry.addProperty("texture", "#" + TEXTURE_VARIABLE);
            entry.addProperty("cullface", face);
            if (!face.equals("down")) {
                // The side shows as much of the texture as the box is tall.
                entry.add("uv", array(0, 16 - height, 16, 16));
            }
            faces.add(face, entry);
        }
        JsonObject up = new JsonObject();
        up.addProperty("texture", "#" + TEXTURE_VARIABLE);
        up.add("uv", array(0, 0, 16, 16));
        faces.add("up", up);

        JsonObject element = new JsonObject();
        element.add("from", array(0, 0, 0));
        element.add("to", array(16, height, 16));
        element.addProperty("shade", false);
        element.add("faces", faces);
        JsonArray elements = new JsonArray();
        elements.add(element);

        JsonObject textures = new JsonObject();
        textures.addProperty("particle", "#" + TEXTURE_VARIABLE);
        JsonObject model = new JsonObject();
        model.addProperty("ambientocclusion", false);
        model.add("textures", textures);
        model.add("elements", elements);
        return model;
    }

    /**
     * {@code assets/minecraft/blockstates/tripwire.json}: a model for every one of tripwire's 128
     * states. Each liquid's two states get its models; every other state keeps the model vanilla
     * gives it, so string looks and connects exactly as it does without the pack.
     *
     * @param models the model of each liquid state that is in use, by state
     */
    public static JsonObject blockstate(Map<TripwireState, ResourceLocation> models) {
        Map<String, JsonObject> vanilla = vanillaVariants();
        JsonObject variants = new JsonObject();
        for (boolean attached : new boolean[]{false, true}) {
            for (boolean disarmed : new boolean[]{false, true}) {
                for (boolean east : new boolean[]{false, true}) {
                    for (boolean north : new boolean[]{false, true}) {
                        for (boolean powered : new boolean[]{false, true}) {
                            for (boolean south : new boolean[]{false, true}) {
                                for (boolean west : new boolean[]{false, true}) {
                                    TripwireState state = new TripwireState(attached, north, east, south, west);
                                    ResourceLocation liquid = disarmed && !powered ? models.get(state) : null;
                                    JsonObject variant;
                                    if (liquid != null) {
                                        variant = new JsonObject();
                                        variant.addProperty("model", liquid.toString());
                                    } else {
                                        variant = Objects.requireNonNull(vanilla.get(state.vanillaKey()),
                                                state.vanillaKey()).deepCopy();
                                    }
                                    variants.add(state.variantKey(disarmed, powered), variant);
                                }
                            }
                        }
                    }
                }
            }
        }
        JsonObject root = new JsonObject();
        root.add("variants", variants);
        return root;
    }

    /** Vanilla's 32 tripwire variants, keyed without disarmed and powered (from the 1.21.8 client). */
    static Map<String, JsonObject> vanillaVariants() {
        try (InputStream in = LiquidModels.class.getResourceAsStream("/vanilla-tripwire.json")) {
            if (in == null) {
                throw new IllegalStateException("vanilla-tripwire.json is missing from the plugin jar");
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, JsonObject> variants = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("variants").entrySet()) {
                variants.put(entry.getKey(), entry.getValue().getAsJsonObject());
            }
            return variants;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static JsonArray array(int... values) {
        JsonArray array = new JsonArray();
        for (int value : values) {
            array.add(value);
        }
        return array;
    }
}
