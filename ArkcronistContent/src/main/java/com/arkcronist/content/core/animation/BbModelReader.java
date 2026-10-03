package com.arkcronist.content.core.animation;

import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.definition.ResourceLocation;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Reads a Blockbench project ({@code .bbmodel}) into an {@link AnimatedModel}: its groups become
 * bones, each with a Minecraft model of its own cubes, and its animations become keyframes in the
 * frame item displays move in.
 *
 * <h2>What is read</h2>
 * <ul>
 *   <li>Cubes, with per-face UVs, face rotation, inflate, and element rotation - the last limited to
 *       what Minecraft models allow: one axis, a multiple of 22.5 degrees up to 45. Anything else is
 *       rounded to the nearest such angle and reported.</li>
 *   <li>Groups, nested to any depth, with their pivot and rest rotation. Cubes outside every group
 *       go into a bone named {@code root}.</li>
 *   <li>Textures embedded in the project (Blockbench's default), or saved beside it inside the
 *       content pack.</li>
 *   <li>Animations: position, rotation and scale keyframes with linear, smooth (catmullrom) or step
 *       interpolation, and their loop mode. Bezier keyframes are read as linear; Molang expressions,
 *       sounds and particles are not supported.</li>
 * </ul>
 *
 * <h2>Conventions</h2>
 * <p>The axis conventions are Blockbench's own and the ones open-source Blockbench importers such
 * as BetterModel use: an item display draws its model turned half a turn around Y, so positions and
 * rotations are mirrored in X and Z on the way in; Blockbench 5 changed the sign of animation
 * rotations and positions, so the format version decides which components flip. The model's front -
 * Blockbench's north - faces the way the furniture faces.</p>
 *
 * <p>Models in the Generic Model format (Blockbench's "free") have their origin at the bottom centre
 * of the block; Java Block models, at its corner. Both are read relative to the bottom centre.</p>
 */
public final class BbModelReader {

    /** Minecraft accepts model coordinates from -16 to 32: 24 pixels either side of a model's centre. */
    static final float MODEL_REACH = 24f;
    /** One display per bone: past this, a model is refused rather than spawning that many entities. */
    static final int MAX_BONES = 48;
    private static final float[] LEGAL_ANGLES = {-45f, -22.5f, 0f, 22.5f, 45f};
    private static final List<String> FACES = List.of("north", "east", "south", "west", "up", "down");
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final Path file;
    private final Path sourceRoot;
    private final ResourceLocation itemModel;
    private final String prefix;
    private final List<String> problems;
    /** Each kind of problem once per model, not once per cube. */
    private final Set<String> reported = new HashSet<>();

    private BbModelReader(Path file, Path sourceRoot, ResourceLocation itemModel, String prefix, List<String> problems) {
        this.file = file;
        this.sourceRoot = sourceRoot;
        this.itemModel = itemModel;
        this.prefix = prefix;
        this.problems = problems;
    }

    /**
     * Reads {@code file}.
     *
     * @param sourceRoot the content pack folder; textures saved beside the project must be inside it
     * @param itemModel  the item the model belongs to: bone models and textures are named after it
     * @param prefix     put before every problem, e.g. {@code "demo:ruby_chest: "}
     * @return null when the file cannot be used at all, the reason added to {@code problems}
     */
    public static @Nullable AnimatedModel read(Path file, Path sourceRoot, ResourceLocation itemModel, String prefix,
                                               List<String> problems) {
        return new BbModelReader(file, sourceRoot, itemModel, prefix, problems).read();
    }

    /**
     * As {@link #read(Path, Path, ResourceLocation, String, List)}, from text already in memory;
     * {@code file} only locates textures saved beside the project.
     */
    public static @Nullable AnimatedModel read(String json, Path file, Path sourceRoot, ResourceLocation itemModel,
                                               List<String> problems) {
        return new BbModelReader(file, sourceRoot, itemModel, "", problems).parse(json);
    }

    private @Nullable AnimatedModel read() {
        String json;
        try {
            json = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            problems.add(prefix + "cannot read " + file.getFileName() + ": " + exception.getMessage());
            return null;
        }
        return parse(json);
    }

    private @Nullable AnimatedModel parse(String json) {
        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                problems.add(prefix + file.getFileName() + " is not a Blockbench project");
                return null;
            }
            root = parsed.getAsJsonObject();
        } catch (JsonParseException exception) {
            problems.add(prefix + file.getFileName() + " is not valid JSON: " + exception.getMessage());
            return null;
        }

        JsonObject meta = object(root, "meta");
        String format = meta == null ? "free" : string(meta, "model_format", "free");
        boolean blockbench5 = majorVersion(meta == null ? "" : string(meta, "format_version", "")) >= 5;
        // Java Block models are drawn from the block's corner; everything else from its bottom centre.
        float[] shift = format.equals("java_block") ? new float[] {8, 0, 8} : new float[] {0, 0, 0};

        JsonObject resolution = object(root, "resolution");
        float projectWidth = resolution == null ? 16 : number(resolution, "width", 16);
        float projectHeight = resolution == null ? 16 : number(resolution, "height", 16);

        List<float[]> uvSizes = new ArrayList<>();
        List<AnimatedModel.Texture> textures = readTextures(root, projectWidth, projectHeight, uvSizes);

        Map<String, JsonObject> cubes = new LinkedHashMap<>();
        for (JsonElement element : array(root, "elements")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            String type = string(object, "type", "cube");
            if (!type.equals("cube")) {
                once("element type '" + type + "' is not supported and was skipped (only cubes are)");
                continue;
            }
            if (object.has("visibility") && !object.get("visibility").getAsBoolean()) {
                continue;
            }
            cubes.put(string(object, "uuid", "#" + cubes.size()), object);
        }

        // Bones, depth first: each one's own cubes, pivot and rest rotation, in Blockbench's frame.
        List<RawBone> raw = new ArrayList<>();
        List<String> loose = new ArrayList<>();
        for (JsonElement entry : array(root, "outliner")) {
            if (entry.isJsonPrimitive()) {
                loose.add(entry.getAsString());
            }
        }
        if (!loose.isEmpty()) {
            raw.add(new RawBone("root", "", -1, new float[3], new float[3], cubesOf(loose, cubes)));
        }
        for (JsonElement entry : array(root, "outliner")) {
            if (entry.isJsonObject()) {
                group(entry.getAsJsonObject(), -1, cubes, shift, raw);
            }
        }
        if (raw.isEmpty()) {
            problems.add(prefix + file.getFileName() + " has no cubes to draw");
            return null;
        }
        if (raw.size() > MAX_BONES) {
            problems.add(prefix + file.getFileName() + " has " + raw.size() + " bones; at most " + MAX_BONES
                    + " are drawn (one entity each). Merge groups that never move on their own.");
            return null;
        }
        uniqueNames(raw);

        List<AnimatedModel.Bone> bones = new ArrayList<>();
        for (RawBone bone : raw) {
            float[] parentPivot = bone.parent < 0 ? new float[3] : raw.get(bone.parent).pivot;
            Placement.Vec3 offset = mirror(new float[] {
                    (bone.pivot[0] - parentPivot[0]) / 16f,
                    (bone.pivot[1] - parentPivot[1]) / 16f,
                    (bone.pivot[2] - parentPivot[2]) / 16f});
            Placement.Vec3 rest = mirror(bone.rotation);
            float scale = fitScale(bone.cubes, bone.pivot, shift);
            String model = bone.cubes.isEmpty() ? null
                    : modelJson(bone.cubes, bone.pivot, shift, scale, uvSizes, textures, bone.name);
            bones.add(new AnimatedModel.Bone(bone.name, bone.parent, offset, rest, scale, model));
        }

        Map<String, Integer> boneByUuid = new HashMap<>();
        for (int i = 0; i < raw.size(); i++) {
            boneByUuid.put(raw.get(i).uuid, i);
        }
        Map<String, AnimatedModel.Clip> clips = new LinkedHashMap<>();
        for (JsonElement entry : array(root, "animations")) {
            if (entry.isJsonObject()) {
                AnimatedModel.Clip clip = clip(entry.getAsJsonObject(), boneByUuid, blockbench5);
                if (clip != null) {
                    clips.put(clip.name(), clip);
                }
            }
        }

        return new AnimatedModel(bones, clips, textures, icon(raw, shift, uvSizes, textures));
    }

    // ---------------------------------------------------------------- bones

    /** A bone in Blockbench's frame: pivot in pixels, rotation in degrees, unmirrored. */
    private record RawBone(String name, String uuid, int parent, float[] pivot, float[] rotation, List<JsonObject> cubes) {

        RawBone withName(String name) {
            return new RawBone(name, uuid, parent, pivot, rotation, cubes);
        }
    }

    private void group(JsonObject group, int parent, Map<String, JsonObject> cubes, float[] shift, List<RawBone> out) {
        if (group.has("visibility") && !group.get("visibility").getAsBoolean()) {
            return;
        }
        float[] origin = vector(group, "origin", 0);
        float[] pivot = {origin[0] - shift[0], origin[1] - shift[1], origin[2] - shift[2]};
        List<String> own = new ArrayList<>();
        List<JsonObject> children = new ArrayList<>();
        for (JsonElement child : array(group, "children")) {
            if (child.isJsonPrimitive()) {
                own.add(child.getAsString());
            } else if (child.isJsonObject()) {
                children.add(child.getAsJsonObject());
            }
        }
        int index = out.size();
        out.add(new RawBone(name(string(group, "name", "bone")), string(group, "uuid", "#bone" + index), parent, pivot,
                vector(group, "rotation", 0), cubesOf(own, cubes)));
        for (JsonObject child : children) {
            group(child, index, cubes, shift, out);
        }
    }

    private static List<JsonObject> cubesOf(List<String> uuids, Map<String, JsonObject> cubes) {
        List<JsonObject> found = new ArrayList<>();
        for (String uuid : uuids) {
            JsonObject cube = cubes.get(uuid);
            if (cube != null) {
                found.add(cube);
            }
        }
        return found;
    }

    /** Bone names become file names: lower case, {@code [a-z0-9_]}. */
    static String name(String raw) {
        String name = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_").replaceAll("^_+|_+$", "");
        return name.isEmpty() ? "bone" : name;
    }

    private static void uniqueNames(List<RawBone> bones) {
        Set<String> taken = new HashSet<>();
        for (int i = 0; i < bones.size(); i++) {
            String name = bones.get(i).name;
            String unique = name;
            for (int n = 2; !taken.add(unique); n++) {
                unique = name + "_" + n;
            }
            if (!unique.equals(name)) {
                bones.set(i, bones.get(i).withName(unique));
            }
        }
    }

    /**
     * Minecraft refuses model coordinates beyond 24 pixels either side of the centre. A bone that
     * reaches further has its model shrunk by this factor, and its display scales it back up.
     */
    static float fitScale(List<JsonObject> cubes, float[] pivot, float[] shift) {
        float reach = 0;
        for (JsonObject cube : cubes) {
            float inflate = number(cube, "inflate", 0);
            for (String key : List.of("from", "to")) {
                float[] corner = vector(cube, key, 0);
                for (int axis = 0; axis < 3; axis++) {
                    reach = Math.max(reach, Math.abs(corner[axis] - shift[axis] - pivot[axis]) + inflate);
                }
            }
        }
        return Math.max(1f, reach / MODEL_REACH);
    }

    // ---------------------------------------------------------------- models

    /** A bone's own cubes as a Minecraft model, its pivot moved to the model's centre (8, 8, 8). */
    private String modelJson(List<JsonObject> cubes, float[] pivot, float[] shift, float scale, List<float[]> uvSizes,
                             List<AnimatedModel.Texture> textures, String bone) {
        float[] centre = new float[3];
        for (int axis = 0; axis < 3; axis++) {
            centre[axis] = -pivot[axis] / scale + 8;
        }
        return model(cubes, shift, scale, centre, uvSizes, textures, "bone '" + bone + "'");
    }

    /**
     * Writes cubes as model elements: Blockbench's coordinates, less {@code shift}, divided by
     * {@code scale}, plus {@code offset}.
     */
    private String model(List<JsonObject> cubes, float[] shift, float scale, float[] offset, List<float[]> uvSizes,
                         List<AnimatedModel.Texture> textures, String where) {
        Set<Integer> used = new TreeSet<>();
        JsonArray elements = new JsonArray();
        for (JsonObject cube : cubes) {
            float inflate = number(cube, "inflate", 0);
            float[] from = vector(cube, "from", 0);
            float[] to = vector(cube, "to", 0);
            JsonObject element = new JsonObject();
            element.add("from", array(at(from, shift, scale, offset, -inflate)));
            element.add("to", array(at(to, shift, scale, offset, inflate)));
            JsonObject rotation = rotation(cube, shift, scale, offset, where);
            if (rotation != null) {
                element.add("rotation", rotation);
            }
            JsonObject faces = new JsonObject();
            JsonObject source = object(cube, "faces");
            for (String face : FACES) {
                JsonObject written = face(source == null ? null : object(source, face), uvSizes, textures.size(), used, where);
                if (written != null) {
                    faces.add(face, written);
                }
            }
            element.add("faces", faces);
            elements.add(element);
        }
        JsonObject textureMap = new JsonObject();
        for (int index : used) {
            textureMap.addProperty(Integer.toString(index), textures.get(index).location().toString());
        }
        if (!used.isEmpty()) {
            textureMap.addProperty("particle", "#" + used.iterator().next());
        }
        JsonObject model = new JsonObject();
        model.add("textures", textureMap);
        model.add("elements", elements);
        return GSON.toJson(model);
    }

    private static float[] at(float[] point, float[] shift, float scale, float[] offset, float inflate) {
        float[] out = new float[3];
        for (int axis = 0; axis < 3; axis++) {
            out[axis] = (point[axis] - shift[axis] + inflate) / scale + offset[axis];
        }
        return out;
    }

    /** An element rotation, as Minecraft allows it: one axis, -45 to 45 in steps of 22.5. */
    private @Nullable JsonObject rotation(JsonObject cube, float[] shift, float scale, float[] offset, String where) {
        float[] angles = vector(cube, "rotation", 0);
        int axis = -1;
        int turned = 0;
        for (int i = 0; i < 3; i++) {
            if (angles[i] != 0) {
                turned++;
                if (axis < 0 || Math.abs(angles[i]) > Math.abs(angles[axis])) {
                    axis = i;
                }
            }
        }
        if (axis < 0) {
            return null;
        }
        float angle = legalAngle(angles[axis]);
        if (turned > 1 || angle != angles[axis]) {
            once(where + ": a cube is rotated " + Arrays.toString(angles) + "; Minecraft models allow one axis and"
                    + " -45, -22.5, 22.5 or 45 degrees, so it is drawn at " + angle + " around " + "xyz".charAt(axis)
                    + ". Rotate the group instead: groups can turn any way");
        }
        if (angle == 0) {
            return null;
        }
        JsonObject rotation = new JsonObject();
        rotation.addProperty("angle", angle);
        rotation.addProperty("axis", String.valueOf("xyz".charAt(axis)));
        rotation.add("origin", array(at(vector(cube, "origin", 0), shift, scale, offset, 0)));
        return rotation;
    }

    static float legalAngle(float angle) {
        float best = 0;
        for (float legal : LEGAL_ANGLES) {
            if (Math.abs(angle - legal) < Math.abs(angle - best)) {
                best = legal;
            }
        }
        return best;
    }

    private @Nullable JsonObject face(@Nullable JsonObject face, List<float[]> uvSizes, int textureCount, Set<Integer> used,
                                      String where) {
        if (face == null || !face.has("texture") || !face.get("texture").isJsonPrimitive()
                || !face.get("texture").getAsJsonPrimitive().isNumber()) {
            return null;
        }
        int texture = face.get("texture").getAsInt();
        if (texture < 0 || texture >= textureCount) {
            once(where + ": a face uses texture " + texture + ", which the project does not have");
            return null;
        }
        float[] uv = new float[4];
        JsonArray rawUv = face.has("uv") && face.get("uv").isJsonArray() ? face.getAsJsonArray("uv") : null;
        if (rawUv == null || rawUv.size() != 4) {
            once(where + ": a face has no UV; set the cube to per-face UV in Blockbench");
            return null;
        }
        float[] size = uvSizes.get(texture);
        for (int i = 0; i < 4; i++) {
            uv[i] = rawUv.get(i).getAsFloat() * 16f / size[i % 2];
        }
        used.add(texture);
        JsonObject written = new JsonObject();
        written.add("uv", array(uv));
        written.addProperty("texture", "#" + texture);
        int rotation = Math.round(number(face, "rotation", 0));
        if (rotation % 360 != 0) {
            written.addProperty("rotation", ((rotation % 360) + 360) % 360);
        }
        return written;
    }

    /**
     * The whole model at rest as one item model, for the item in an inventory: possible while no
     * group is rotated and every cube fits Minecraft's bounds as it stands.
     */
    private @Nullable String icon(List<RawBone> bones, float[] shift, List<float[]> uvSizes,
                                  List<AnimatedModel.Texture> textures) {
        List<JsonObject> all = new ArrayList<>();
        for (RawBone bone : bones) {
            if (bone.rotation[0] != 0 || bone.rotation[1] != 0 || bone.rotation[2] != 0) {
                return null;
            }
            all.addAll(bone.cubes);
        }
        float[] offset = {8, 0, 8};
        for (JsonObject cube : all) {
            float inflate = number(cube, "inflate", 0);
            for (float[] corner : List.of(at(vector(cube, "from", 0), shift, 1, offset, -inflate),
                    at(vector(cube, "to", 0), shift, 1, offset, inflate))) {
                for (float value : corner) {
                    if (value < -16 || value > 32) {
                        return null;
                    }
                }
            }
        }
        int before = problems.size();
        JsonObject cubes = JsonParser.parseString(model(all, shift, 1, offset, uvSizes, textures, "icon")).getAsJsonObject();
        // Problems with the cubes were already said for their bones.
        while (problems.size() > before) {
            problems.removeLast();
        }
        // Held and in an inventory it is drawn like any block: turned, lit from the side, scaled down.
        JsonObject icon = new JsonObject();
        icon.addProperty("parent", "minecraft:block/block");
        cubes.entrySet().forEach(entry -> icon.add(entry.getKey(), entry.getValue()));
        return GSON.toJson(icon);
    }

    // ---------------------------------------------------------------- textures

    private List<AnimatedModel.Texture> readTextures(JsonObject root, float projectWidth, float projectHeight,
                                                     List<float[]> uvSizes) {
        List<AnimatedModel.Texture> textures = new ArrayList<>();
        JsonArray array = array(root, "textures");
        for (int i = 0; i < array.size(); i++) {
            JsonObject texture = array.get(i).isJsonObject() ? array.get(i).getAsJsonObject() : new JsonObject();
            uvSizes.add(new float[] {number(texture, "uv_width", projectWidth), number(texture, "uv_height", projectHeight)});
            byte[] png = png(texture, string(texture, "name", "texture " + i));
            // A placeholder keeps indices aligned: faces name textures by position.
            textures.add(new AnimatedModel.Texture(
                    new ResourceLocation(itemModel.namespace(), itemModel.path() + "/tex_" + i),
                    png == null ? new byte[0] : png));
        }
        return textures;
    }

    private @Nullable byte[] png(JsonObject texture, String name) {
        String source = string(texture, "source", "");
        if (source.startsWith("data:")) {
            int comma = source.indexOf(',');
            if (comma > 0 && source.substring(0, comma).endsWith(";base64")) {
                try {
                    byte[] bytes = Base64.getDecoder().decode(source.substring(comma + 1));
                    if (startsWith(bytes, PNG)) {
                        return bytes;
                    }
                } catch (IllegalArgumentException ignored) {
                    // reported below
                }
            }
            problems.add(prefix + "texture '" + name + "' is embedded but is not a PNG");
            return null;
        }
        String relative = string(texture, "relative_path", "");
        if (!relative.isEmpty() && file.getParent() != null) {
            Path resolved = file.getParent().resolve(relative).normalize();
            if (!resolved.startsWith(sourceRoot.normalize())) {
                problems.add(prefix + "texture '" + name + "' is outside the content pack (" + relative + ")");
                return null;
            }
            try {
                byte[] bytes = Files.readAllBytes(resolved);
                if (startsWith(bytes, PNG)) {
                    return bytes;
                }
            } catch (IOException ignored) {
                // reported below
            }
        }
        problems.add(prefix + "texture '" + name + "' is neither embedded nor found beside the project;"
                + " in Blockbench, save it into the project (File > Save Model keeps textures embedded)");
        return null;
    }

    // ---------------------------------------------------------------- animations

    private @Nullable AnimatedModel.Clip clip(JsonObject animation, Map<String, Integer> boneByUuid, boolean blockbench5) {
        String name = string(animation, "name", "");
        if (name.isEmpty()) {
            return null;
        }
        AnimatedModel.Loop loop = switch (string(animation, "loop", "once")) {
            case "hold" -> AnimatedModel.Loop.HOLD;
            case "loop" -> AnimatedModel.Loop.LOOP;
            default -> AnimatedModel.Loop.ONCE;
        };
        int length = Math.max(1, Math.round(number(animation, "length", 0) * 20f));
        Map<Integer, AnimatedModel.Channels> channels = new TreeMap<>();
        JsonObject animators = object(animation, "animators");
        if (animators != null) {
            for (Map.Entry<String, JsonElement> entry : animators.entrySet()) {
                Integer bone = boneByUuid.get(entry.getKey());
                if (bone == null || !entry.getValue().isJsonObject()) {
                    continue;
                }
                AnimatedModel.Channels read = channels(entry.getValue().getAsJsonObject(), blockbench5, name);
                if (read != null) {
                    channels.put(bone, read);
                }
            }
        }
        return new AnimatedModel.Clip(name, loop, length, channels);
    }

    private @Nullable AnimatedModel.Channels channels(JsonObject animator, boolean blockbench5, String clip) {
        List<AnimatedModel.Keyframe> position = new ArrayList<>();
        List<AnimatedModel.Keyframe> rotation = new ArrayList<>();
        List<AnimatedModel.Keyframe> scale = new ArrayList<>();
        for (JsonElement entry : array(animator, "keyframes")) {
            if (!entry.isJsonObject()) {
                continue;
            }
            JsonObject keyframe = entry.getAsJsonObject();
            String channel = string(keyframe, "channel", "");
            if (!channel.equals("position") && !channel.equals("rotation") && !channel.equals("scale")) {
                continue;
            }
            JsonArray points = array(keyframe, "data_points");
            if (points.isEmpty() || !points.get(0).isJsonObject()) {
                continue;
            }
            float fallback = channel.equals("scale") ? 1 : 0;
            JsonObject point = points.get(0).getAsJsonObject();
            float x = component(point, "x", fallback, clip);
            float y = component(point, "y", fallback, clip);
            float z = component(point, "z", fallback, clip);
            String interpolationName = string(keyframe, "interpolation", "linear");
            AnimatedModel.Interpolation interpolation = switch (interpolationName) {
                case "catmullrom" -> AnimatedModel.Interpolation.SMOOTH;
                case "step" -> AnimatedModel.Interpolation.STEP;
                default -> AnimatedModel.Interpolation.LINEAR;
            };
            if (interpolationName.equals("bezier")) {
                once("animation '" + clip + "': bezier keyframes are played as linear");
            }
            float tick = number(keyframe, "time", 0) * 20f;
            switch (channel) {
                case "position" -> position.add(new AnimatedModel.Keyframe(tick, blockbench5
                        ? vec(-x / 16f, y / 16f, -z / 16f)
                        : vec(x / 16f, y / 16f, -z / 16f), interpolation));
                case "rotation" -> rotation.add(new AnimatedModel.Keyframe(tick, blockbench5
                        ? vec(-x, y, -z)
                        : vec(x, -y, -z), interpolation));
                default -> scale.add(new AnimatedModel.Keyframe(tick, vec(x, y, z), interpolation));
            }
        }
        if (position.isEmpty() && rotation.isEmpty() && scale.isEmpty()) {
            return null;
        }
        Comparator<AnimatedModel.Keyframe> byTime = Comparator.comparingDouble(AnimatedModel.Keyframe::tick);
        position.sort(byTime);
        rotation.sort(byTime);
        scale.sort(byTime);
        return new AnimatedModel.Channels(position, rotation, scale);
    }

    private float component(JsonObject point, String key, float fallback, String clip) {
        JsonElement value = point.get(key);
        if (value == null || !value.isJsonPrimitive()) {
            return fallback;
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (primitive.isNumber()) {
            return primitive.getAsFloat();
        }
        String text = primitive.getAsString().trim();
        if (text.isEmpty()) {
            return fallback;
        }
        try {
            return Float.parseFloat(text);
        } catch (NumberFormatException exception) {
            once("animation '" + clip + "': Molang expressions such as '" + text + "' are not supported; read as "
                    + fallback);
            return fallback;
        }
    }

    // ---------------------------------------------------------------- helpers

    /** X and Z mirrored: an item display draws its model turned half a turn around Y. */
    private static Placement.Vec3 mirror(float[] value) {
        return vec(-value[0], value[1], -value[2]);
    }

    /** Never -0, which would make equal values compare unequal. */
    private static Placement.Vec3 vec(float x, float y, float z) {
        return new Placement.Vec3(x + 0f, y + 0f, z + 0f);
    }

    private void once(String problem) {
        if (reported.add(problem)) {
            problems.add(prefix + problem);
        }
    }

    static int majorVersion(String version) {
        int dot = version.indexOf('.');
        try {
            return Integer.parseInt(dot < 0 ? version : version.substring(0, dot));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static @Nullable JsonObject object(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static JsonArray array(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    private static String string(JsonObject parent, String key, String fallback) {
        JsonElement value = parent.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }

    static float number(JsonObject parent, String key, float fallback) {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return value.getAsFloat();
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    static float[] vector(JsonObject parent, String key, float fallback) {
        JsonElement value = parent.get(key);
        float[] out = {fallback, fallback, fallback};
        if (value != null && value.isJsonArray()) {
            JsonArray array = value.getAsJsonArray();
            for (int i = 0; i < 3 && i < array.size(); i++) {
                try {
                    out[i] = array.get(i).getAsFloat();
                } catch (RuntimeException ignored) {
                    // a non-number keeps the fallback
                }
            }
        }
        return out;
    }

    /** Rounded to a ten-thousandth, so the model's bytes - and the pack's hash - are stable. */
    private static JsonArray array(float[] values) {
        JsonArray array = new JsonArray();
        for (float value : values) {
            float rounded = Math.round(value * 10000f) / 10000f;
            if (rounded == Math.rint(rounded)) {
                array.add((int) rounded);
            } else {
                array.add(rounded);
            }
        }
        return array;
    }
}
