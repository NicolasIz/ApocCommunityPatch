package com.arkcronist.content.core.geyser;

import com.arkcronist.content.core.definition.ResourceLocation;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * A Java model with its parents followed: its cubes and the textures their faces use, ready to be
 * drawn some other way - as Bedrock geometry, or as an icon.
 *
 * <p>The vanilla parents a pack does not carry are the few every custom block is made from -
 * {@code block/cube}, {@code cube_all}, {@code cube_column}, {@code cube_bottom_top},
 * {@code cube_top}, {@code orientable} - and are built in here, cube for cube as vanilla has them.
 * A flat item ({@code item/generated} and its kin) has no cubes: its first layer is its picture.</p>
 *
 * @param elements  the cubes, in model pixels
 * @param layers    a flat item's layers, in order; empty for a model with cubes
 * @param textures  every texture variable, followed to a texture
 * @param guiRotation how the model is turned in an inventory - its {@code display.gui}, or vanilla's
 *                    default for a block
 */
public record JavaModel(List<Element> elements, List<ResourceLocation> layers, Map<String, ResourceLocation> textures,
                        float[] guiRotation, float guiScale) {

    public enum Face {
        DOWN, UP, NORTH, SOUTH, WEST, EAST;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        static @Nullable Face parse(String key) {
            try {
                return valueOf(key.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException unknown) {
                return null;
            }
        }
    }

    /**
     * One face of a cube.
     *
     * @param uv       {u1, v1, u2, v2} in the 0-16 square every texture is mapped onto
     * @param rotation the texture's quarter turns on the face, 0-270
     */
    public record FaceTexture(float[] uv, ResourceLocation texture, int rotation) {
    }

    /**
     * A cube of a model.
     *
     * @param axis   the one axis it is turned about, x, y or z; null for none
     * @param angle  degrees about it
     * @param origin the point it turns about
     */
    public record Element(float[] from, float[] to, @Nullable Character axis, float angle, float[] origin,
                          Map<Face, FaceTexture> faces) {

        public boolean rotated() {
            return axis != null && angle != 0;
        }
    }

    /** The default GUI pose of a block in vanilla: tipped 30 degrees, turned 225. */
    public static final float[] BLOCK_GUI = {30, 225, 0};

    private static final String[] FLAT = {"minecraft:item/generated", "minecraft:item/handheld",
            "minecraft:item/handheld_rod", "minecraft:item/handheld_mace", "minecraft:builtin/generated"};

    /**
     * @param model  the model to read
     * @param reader a model file's JSON, or null when the pack has none by that name
     * @return null when the model, or a parent it needs, cannot be read
     */
    public static @Nullable JavaModel resolve(ResourceLocation model, Function<ResourceLocation, JsonObject> reader) {
        Map<String, String> variables = new LinkedHashMap<>();
        List<Element> raw = null;
        List<Map<Face, String[]>> rawFaces = null;
        JsonObject gui = null;
        boolean flat = false;
        ResourceLocation current = model;
        for (int depth = 0; current != null && depth < 16; depth++) {
            String name = current.toString();
            for (String generated : FLAT) {
                if (generated.equals(name)) {
                    flat = true;
                }
            }
            if (flat) {
                break;
            }
            JsonObject json = reader.apply(current);
            if (json == null) {
                json = builtin(name);
                if (json == null) {
                    return null;
                }
            }
            if (json.has("textures") && json.get("textures").isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("textures").entrySet()) {
                    if (entry.getValue().isJsonPrimitive()) {
                        variables.putIfAbsent(entry.getKey(), entry.getValue().getAsString());
                    }
                }
            }
            if (gui == null && json.has("display") && json.get("display").isJsonObject()
                    && json.getAsJsonObject("display").has("gui")) {
                gui = json.getAsJsonObject("display").getAsJsonObject("gui");
            }
            if (raw == null && json.has("elements") && json.get("elements").isJsonArray()) {
                raw = new ArrayList<>();
                rawFaces = new ArrayList<>();
                readElements(json.getAsJsonArray("elements"), raw, rawFaces);
            }
            current = json.has("parent") ? ResourceLocation.parse(json.get("parent").getAsString(), "minecraft") : null;
        }
        Map<String, ResourceLocation> textures = new LinkedHashMap<>();
        for (String key : variables.keySet()) {
            ResourceLocation texture = follow(key, variables);
            if (texture != null) {
                textures.put(key, texture);
            }
        }
        float[] rotation = gui != null && gui.has("rotation") ? floats(gui.getAsJsonArray("rotation"), 3) : BLOCK_GUI;
        float scale = gui != null && gui.has("scale") ? floats(gui.getAsJsonArray("scale"), 3)[0] : 0.625f;
        if (flat || raw == null) {
            List<ResourceLocation> layers = new ArrayList<>();
            for (int i = 0; textures.containsKey("layer" + i); i++) {
                layers.add(textures.get("layer" + i));
            }
            return layers.isEmpty() ? null : new JavaModel(List.of(), layers, textures, rotation, scale);
        }
        List<Element> elements = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            Element element = raw.get(i);
            Map<Face, FaceTexture> faces = new EnumMap<>(Face.class);
            for (Map.Entry<Face, String[]> face : rawFaces.get(i).entrySet()) {
                String reference = face.getValue()[0];
                ResourceLocation texture = reference.startsWith("#")
                        ? textures.get(reference.substring(1)) : ResourceLocation.parse(reference, "minecraft");
                if (texture == null) {
                    continue;
                }
                float[] uv = face.getValue()[1] != null ? parseUv(face.getValue()[1])
                        : autoUv(face.getKey(), element.from(), element.to());
                faces.put(face.getKey(), new FaceTexture(uv, texture, Integer.parseInt(face.getValue()[2])));
            }
            if (!faces.isEmpty()) {
                elements.add(new Element(element.from(), element.to(), element.axis(), element.angle(),
                        element.origin(), faces));
            }
        }
        return new JavaModel(elements, List.of(), textures, rotation, scale);
    }

    /**
     * The UV a face gets when the model gives none: the face's own rectangle on the 16 x 16 square,
     * as the client works it out - which is also how a texture lies on a face.
     */
    public static float[] autoUv(Face face, float[] from, float[] to) {
        return switch (face) {
            case DOWN -> new float[]{from[0], 16 - to[2], to[0], 16 - from[2]};
            case UP -> new float[]{from[0], from[2], to[0], to[2]};
            case NORTH -> new float[]{16 - to[0], 16 - to[1], 16 - from[0], 16 - from[1]};
            case SOUTH -> new float[]{from[0], 16 - to[1], to[0], 16 - from[1]};
            case WEST -> new float[]{from[2], 16 - to[1], to[2], 16 - from[1]};
            case EAST -> new float[]{16 - to[2], 16 - to[1], 16 - from[2], 16 - from[1]};
        };
    }

    private static void readElements(JsonArray array, List<Element> elements, List<Map<Face, String[]>> faces) {
        for (JsonElement entry : array) {
            if (!entry.isJsonObject()) {
                continue;
            }
            JsonObject element = entry.getAsJsonObject();
            if (!element.has("from") || !element.has("to") || !element.has("faces")) {
                continue;
            }
            float[] from = floats(element.getAsJsonArray("from"), 3);
            float[] to = floats(element.getAsJsonArray("to"), 3);
            Character axis = null;
            float angle = 0;
            float[] origin = {8, 8, 8};
            if (element.has("rotation") && element.get("rotation").isJsonObject()) {
                JsonObject rotation = element.getAsJsonObject("rotation");
                axis = rotation.has("axis") ? rotation.get("axis").getAsString().charAt(0) : null;
                angle = rotation.has("angle") ? rotation.get("angle").getAsFloat() : 0;
                if (rotation.has("origin")) {
                    origin = floats(rotation.getAsJsonArray("origin"), 3);
                }
            }
            Map<Face, String[]> own = new EnumMap<>(Face.class);
            for (Map.Entry<String, JsonElement> face : element.getAsJsonObject("faces").entrySet()) {
                Face which = Face.parse(face.getKey());
                if (which == null || !face.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject spec = face.getValue().getAsJsonObject();
                if (!spec.has("texture")) {
                    continue;
                }
                String uv = spec.has("uv") ? spec.get("uv").toString() : null;
                String turn = spec.has("rotation") ? String.valueOf(Math.floorMod(spec.get("rotation").getAsInt(), 360)) : "0";
                own.put(which, new String[]{spec.get("texture").getAsString(), uv, turn});
            }
            elements.add(new Element(from, to, axis, angle, origin, Map.of()));
            faces.add(own);
        }
    }

    private static float[] parseUv(String json) {
        JsonArray array = com.google.gson.JsonParser.parseString(json).getAsJsonArray();
        return floats(array, 4);
    }

    private static float[] floats(JsonArray array, int count) {
        float[] values = new float[count];
        for (int i = 0; i < count && i < array.size(); i++) {
            values[i] = array.get(i).getAsFloat();
        }
        return values;
    }

    /** A variable followed through #references to a texture; null for one that leads nowhere. */
    private static @Nullable ResourceLocation follow(String key, Map<String, String> variables) {
        String value = variables.get(key);
        for (int hops = 0; value != null && value.startsWith("#") && hops < 16; hops++) {
            value = variables.get(value.substring(1));
        }
        return value == null || value.startsWith("#") ? null : ResourceLocation.parse(value, "minecraft");
    }

    /** The vanilla block parents custom blocks are made from, as vanilla writes them. */
    static @Nullable JsonObject builtin(String name) {
        String cube = "{\"from\":[0,0,0],\"to\":[16,16,16],\"faces\":{"
                + "\"down\":{\"texture\":\"#down\"},\"up\":{\"texture\":\"#up\"},\"north\":{\"texture\":\"#north\"},"
                + "\"south\":{\"texture\":\"#south\"},\"west\":{\"texture\":\"#west\"},\"east\":{\"texture\":\"#east\"}}}";
        String json = switch (name) {
            case "minecraft:block/cube" -> "{\"elements\":[" + cube + "]}";
            case "minecraft:block/cube_all" -> "{\"parent\":\"minecraft:block/cube\",\"textures\":{\"particle\":\"#all\","
                    + "\"down\":\"#all\",\"up\":\"#all\",\"north\":\"#all\",\"east\":\"#all\",\"south\":\"#all\",\"west\":\"#all\"}}";
            case "minecraft:block/cube_column", "minecraft:block/cube_column_horizontal" ->
                    "{\"parent\":\"minecraft:block/cube\",\"textures\":{\"particle\":\"#side\",\"down\":\"#end\","
                            + "\"up\":\"#end\",\"north\":\"#side\",\"east\":\"#side\",\"south\":\"#side\",\"west\":\"#side\"}}";
            case "minecraft:block/cube_bottom_top" -> "{\"parent\":\"minecraft:block/cube\",\"textures\":{\"particle\":"
                    + "\"#side\",\"down\":\"#bottom\",\"up\":\"#top\",\"north\":\"#side\",\"east\":\"#side\","
                    + "\"south\":\"#side\",\"west\":\"#side\"}}";
            case "minecraft:block/cube_top" -> "{\"parent\":\"minecraft:block/cube\",\"textures\":{\"particle\":\"#side\","
                    + "\"down\":\"#side\",\"up\":\"#top\",\"north\":\"#side\",\"east\":\"#side\",\"south\":\"#side\",\"west\":\"#side\"}}";
            case "minecraft:block/orientable" -> "{\"parent\":\"minecraft:block/cube\",\"textures\":{\"particle\":\"#front\","
                    + "\"bottom\":\"#top\",\"down\":\"#bottom\",\"up\":\"#top\",\"north\":\"#front\",\"east\":\"#side\",\"south\":\"#side\",\"west\":\"#side\"}}";
            case "minecraft:block/block", "minecraft:item/handheld_bow" -> "{}";
            default -> null;
        };
        return json == null ? null : com.google.gson.JsonParser.parseString(json).getAsJsonObject();
    }

    /** A copy of the faces' textures, as used: texture to every face using it. */
    public Map<ResourceLocation, Integer> textureUse() {
        Map<ResourceLocation, Integer> use = new HashMap<>();
        for (Element element : elements) {
            for (FaceTexture face : element.faces().values()) {
                use.merge(face.texture(), 1, Integer::sum);
            }
        }
        return use;
    }
}
