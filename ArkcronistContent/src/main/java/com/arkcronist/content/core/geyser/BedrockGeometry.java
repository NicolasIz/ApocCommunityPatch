package com.arkcronist.content.core.geyser;

import com.arkcronist.content.core.definition.ResourceLocation;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bedrock block geometry from {@link BedrockCube cubes}: the {@code .geo.json} a resource pack
 * carries, and the material instances the block definition names its textures by.
 *
 * <p>Bedrock files store x mirrored - a cube at the world's east is written at negative x - and
 * turn about x and y the other way round; the faces keep the names of the ways they face, and the
 * up and down faces lie turned half round against Java's. These are the conventions Blockbench
 * converts by, between its Java and Bedrock block formats. A Bedrock block's geometry must stay
 * within 30 pixels, the block and 7 pixels around it ({@link #fits}).</p>
 */
public final class BedrockGeometry {

    /** How far geometry may reach outside its block, in pixels, on any side. */
    public static final float OVERHANG = 7;

    /**
     * The geometry, and the texture of each material instance it names.
     *
     * @param json      the whole {@code .geo.json} file
     * @param materials material instance name to texture, in the order they were first used
     */
    public record Built(JsonObject json, Map<String, ResourceLocation> materials) {
    }

    private BedrockGeometry() {
    }

    public static Built build(String identifier, List<BedrockCube> cubes) {
        Map<ResourceLocation, String> instance = new LinkedHashMap<>();
        JsonArray written = new JsonArray();
        for (BedrockCube cube : cubes) {
            JsonObject json = new JsonObject();
            json.add("origin", array(-cube.max()[0], cube.min()[1], cube.min()[2]));
            json.add("size", array(cube.max()[0] - cube.min()[0], cube.max()[1] - cube.min()[1],
                    cube.max()[2] - cube.min()[2]));
            if (cube.axis() != null && cube.angle() != 0) {
                float x = cube.axis() == 'x' ? cube.angle() : 0;
                float y = cube.axis() == 'y' ? cube.angle() : 0;
                float z = cube.axis() == 'z' ? cube.angle() : 0;
                json.add("pivot", array(-cube.pivot()[0], cube.pivot()[1], cube.pivot()[2]));
                json.add("rotation", array(-x, -y, z));
            }
            JsonObject uv = new JsonObject();
            for (Map.Entry<JavaModel.Face, BedrockCube.Face> face : cube.faces().entrySet()) {
                String material = instance.computeIfAbsent(face.getValue().texture(), ignored -> "t" + instance.size());
                JsonObject one = faceUv(face.getKey(), face.getValue().uv(), 0);
                one.addProperty("material_instance", material);
                uv.add(face.getKey().key(), one);
            }
            json.add("uv", uv);
            written.add(json);
        }
        JsonObject bone = new JsonObject();
        bone.addProperty("name", "ark");
        bone.add("pivot", array(0, 0, 0));
        bone.add("cubes", written);
        JsonArray bones = new JsonArray();
        bones.add(bone);

        JsonObject description = new JsonObject();
        description.addProperty("identifier", identifier);
        description.addProperty("texture_width", 16);
        description.addProperty("texture_height", 16);
        description.addProperty("visible_bounds_width", 3);
        description.addProperty("visible_bounds_height", 3);
        description.add("visible_bounds_offset", array(0, 0.75f, 0));
        JsonObject geometry = new JsonObject();
        geometry.add("description", description);
        geometry.add("bones", bones);
        JsonArray geometries = new JsonArray();
        geometries.add(geometry);
        JsonObject file = new JsonObject();
        file.addProperty("format_version", "1.12.0");
        file.add("minecraft:geometry", geometries);

        Map<String, ResourceLocation> materials = new LinkedHashMap<>();
        instance.forEach((texture, name) -> materials.put(name, texture));
        return new Built(file, materials);
    }

    /**
     * 3.4: a model worn on a Bedrock player's head, for an attachable - placed as Java draws an item
     * worn there: its middle four pixels above the neck, at {@code itemScale} of its size, then moved,
     * turned and sized by the model's {@code display.head}. A bone named {@code head}, as in vanilla's
     * helmet geometry, ties it to the wearer's head.
     *
     * @param rows     the row of the texture sheet each texture is in; the sheet is one 16-unit
     *                 square per row
     * @param rowCount how many rows the sheet has
     */
    public static JsonObject onHead(String identifier, List<JavaModel.Element> elements, Map<ResourceLocation, Integer> rows,
                                    int rowCount, float[] translation, float[] rotation, float[] scale, float itemScale) {
        float[] centre = {translation[0] * itemScale, 28 + translation[1] * itemScale, translation[2] * itemScale};
        JsonArray written = new JsonArray();
        for (JavaModel.Element element : elements) {
            float[] min = new float[3];
            float[] max = new float[3];
            for (int axis = 0; axis < 3; axis++) {
                float from = centre[axis] + (element.from()[axis] - 8) * scale[axis] * itemScale;
                float to = centre[axis] + (element.to()[axis] - 8) * scale[axis] * itemScale;
                min[axis] = Math.min(from, to);
                max[axis] = Math.max(from, to);
            }
            JsonObject json = new JsonObject();
            json.add("origin", array(-max[0], min[1], min[2]));
            json.add("size", array(max[0] - min[0], max[1] - min[1], max[2] - min[2]));
            if (element.rotated()) {
                float[] pivot = new float[3];
                for (int axis = 0; axis < 3; axis++) {
                    pivot[axis] = centre[axis] + (element.origin()[axis] - 8) * scale[axis] * itemScale;
                }
                float x = element.axis() == 'x' ? element.angle() : 0;
                float y = element.axis() == 'y' ? element.angle() : 0;
                float z = element.axis() == 'z' ? element.angle() : 0;
                json.add("pivot", array(-pivot[0], pivot[1], pivot[2]));
                json.add("rotation", array(-x, -y, z));
            }
            JsonObject uv = new JsonObject();
            for (Map.Entry<JavaModel.Face, JavaModel.FaceTexture> face : element.faces().entrySet()) {
                float offset = 16f * rows.getOrDefault(face.getValue().texture(), 0);
                uv.add(face.getKey().key(), faceUv(face.getKey(), face.getValue().uv(), offset));
            }
            json.add("uv", uv);
            written.add(json);
        }
        JsonObject head = new JsonObject();
        head.addProperty("name", "head");
        head.add("pivot", array(0, 24, 0));
        JsonObject worn = new JsonObject();
        worn.addProperty("name", "ark_worn");
        worn.addProperty("parent", "head");
        worn.add("pivot", array(-centre[0], centre[1], centre[2]));
        if (rotation[0] != 0 || rotation[1] != 0 || rotation[2] != 0) {
            worn.add("rotation", array(-rotation[0], -rotation[1], rotation[2]));
        }
        worn.add("cubes", written);
        JsonArray bones = new JsonArray();
        bones.add(head);
        bones.add(worn);

        JsonObject description = new JsonObject();
        description.addProperty("identifier", identifier);
        description.addProperty("texture_width", 16);
        description.addProperty("texture_height", 16 * Math.max(1, rowCount));
        description.addProperty("visible_bounds_width", 3);
        description.addProperty("visible_bounds_height", 3.5f);
        description.add("visible_bounds_offset", array(0, 1.5f, 0));
        JsonObject geometry = new JsonObject();
        geometry.add("description", description);
        geometry.add("bones", bones);
        JsonArray geometries = new JsonArray();
        geometries.add(geometry);
        JsonObject file = new JsonObject();
        file.addProperty("format_version", "1.12.0");
        file.add("minecraft:geometry", geometries);
        return file;
    }

    /** A face's place on its texture, Bedrock's way: the up and down faces turned half round. */
    private static JsonObject faceUv(JavaModel.Face face, float[] java, float offset) {
        JsonObject one = new JsonObject();
        if (face == JavaModel.Face.UP || face == JavaModel.Face.DOWN) {
            one.add("uv", array(java[2], java[3] + offset));
            one.add("uv_size", array(java[0] - java[2], java[1] - java[3]));
        } else {
            one.add("uv", array(java[0], java[1] + offset));
            one.add("uv_size", array(java[2] - java[0], java[3] - java[1]));
        }
        return one;
    }

    /** Whether every cube stays within the block and {@value #OVERHANG} pixels around it. */
    public static boolean fits(List<BedrockCube> cubes) {
        for (BedrockCube cube : cubes) {
            float reach = cube.axis() != null && cube.angle() != 0 ? turnedReach(cube) : 0;
            if (cube.min()[0] - reach < -8 - OVERHANG || cube.max()[0] + reach > 8 + OVERHANG
                    || cube.min()[1] - reach < -OVERHANG || cube.max()[1] + reach > 16 + OVERHANG
                    || cube.min()[2] - reach < -8 - OVERHANG || cube.max()[2] + reach > 8 + OVERHANG) {
                return false;
            }
        }
        return true;
    }

    /** A turned cube's corners can reach this much further than its unturned box, at most. */
    private static float turnedReach(BedrockCube cube) {
        float largest = 0;
        for (int i = 0; i < 3; i++) {
            largest = Math.max(largest, cube.max()[i] - cube.min()[i]);
        }
        return largest * 0.21f;
    }

    private static JsonArray array(float... values) {
        JsonArray array = new JsonArray();
        for (float value : values) {
            array.add(round(value));
        }
        return array;
    }

    private static float round(float value) {
        float rounded = Math.round(value * 10000f) / 10000f;
        return rounded == 0 ? 0 : rounded;
    }

    /** Every cube's faces, for tests and checks. */
    static List<String> faceKeys(List<BedrockCube> cubes) {
        List<String> keys = new ArrayList<>();
        for (BedrockCube cube : cubes) {
            cube.faces().keySet().forEach(face -> keys.add(face.key()));
        }
        return keys;
    }
}
