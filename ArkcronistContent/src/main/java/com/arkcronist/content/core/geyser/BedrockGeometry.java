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
                float[] java = face.getValue().uv();
                JsonObject one = new JsonObject();
                boolean flat = face.getKey() == JavaModel.Face.UP || face.getKey() == JavaModel.Face.DOWN;
                if (flat) {
                    one.add("uv", array(java[2], java[3]));
                    one.add("uv_size", array(java[0] - java[2], java[1] - java[3]));
                } else {
                    one.add("uv", array(java[0], java[1]));
                    one.add("uv_size", array(java[2] - java[0], java[3] - java[1]));
                }
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
