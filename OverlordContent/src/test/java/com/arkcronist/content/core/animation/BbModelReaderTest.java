package com.arkcronist.content.core.animation;

import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.definition.ResourceLocation;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BbModelReaderTest {

    /** A 1x1 PNG. */
    public static final String PNG = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==";
    static final ResourceLocation ITEM = new ResourceLocation("demo", "ruby_chest");

    @TempDir
    Path dir;

    /**
     * A chest in Blockbench 5's Generic Model format: a base, a lid hinged at its back edge, and a
     * latch on the lid's front; "open" swings the lid up and holds, "close" brings it down once.
     */
    public static String chest() {
        return """
                {
                  "meta": {"format_version": "5.0", "model_format": "free", "box_uv": false},
                  "name": "ruby_chest",
                  "resolution": {"width": 64, "height": 32},
                  "textures": [{"name": "chest.png", "id": "0", "uv_width": 64, "uv_height": 32,
                                "source": "data:image/png;base64,%s"}],
                  "elements": [
                    {"name": "base", "type": "cube", "uuid": "e1", "from": [-7, 0, -7], "to": [7, 10, 7], "origin": [0, 0, 0],
                     "faces": {"north": {"uv": [0, 0, 14, 10], "texture": 0}, "south": {"uv": [14, 0, 28, 10], "texture": 0, "rotation": 180},
                               "east": {"uv": [0, 10, 14, 20], "texture": 0}, "west": {"uv": [0, 10, 14, 20], "texture": 0},
                               "up": {"uv": [28, 0, 42, 14], "texture": null}, "down": {"uv": [28, 0, 42, 14], "texture": 0}}},
                    {"name": "lid", "type": "cube", "uuid": "e2", "from": [-7, 10, -7], "to": [7, 14, 7], "origin": [0, 10, 7],
                     "faces": {"north": {"uv": [0, 20, 14, 24], "texture": 0}, "up": {"uv": [28, 14, 42, 28], "texture": 0}}},
                    {"name": "latch", "type": "cube", "uuid": "e3", "from": [-1, 11, -8], "to": [1, 13, -7], "origin": [0, 12, -7],
                     "rotation": [0, 0, 22.5], "faces": {"north": {"uv": [60, 0, 62, 2], "texture": 0}}},
                    {"name": "hidden", "type": "cube", "uuid": "e4", "from": [0, 0, 0], "to": [1, 1, 1], "visibility": false},
                    {"name": "spot", "type": "locator", "uuid": "e5"}
                  ],
                  "outliner": [
                    {"name": "Base", "uuid": "g1", "origin": [0, 0, 0], "children": ["e1", "e4"]},
                    {"name": "lid", "uuid": "g2", "origin": [0, 10, 7], "children": ["e2",
                      {"name": "Latch!", "uuid": "g3", "origin": [0, 12, -7], "children": ["e3"]}]}
                  ],
                  "animations": [
                    {"name": "open", "loop": "hold", "length": 0.5, "animators": {
                      "g2": {"name": "lid", "type": "bone", "keyframes": [
                        {"channel": "rotation", "data_points": [{"x": "0", "y": "0", "z": "0"}], "time": 0, "interpolation": "linear"},
                        {"channel": "rotation", "data_points": [{"x": "90", "y": "0", "z": "0"}], "time": 0.5, "interpolation": "linear"}]}}},
                    {"name": "close", "loop": "once", "length": 0.5, "animators": {
                      "g2": {"name": "lid", "type": "bone", "keyframes": [
                        {"channel": "rotation", "data_points": [{"x": 90, "y": 0, "z": 0}], "time": 0},
                        {"channel": "rotation", "data_points": [{"x": 0, "y": 0, "z": 0}], "time": 0.5}]}}},
                    {"name": "wobble", "loop": "loop", "length": 1, "animators": {
                      "g1": {"name": "Base", "type": "bone", "keyframes": [
                        {"channel": "position", "data_points": [{"x": 0, "y": 0, "z": 0}], "time": 0, "interpolation": "catmullrom"},
                        {"channel": "position", "data_points": [{"x": 16, "y": 8, "z": 4}], "time": 0.5, "interpolation": "catmullrom"},
                        {"channel": "position", "data_points": [{"x": 0, "y": 0, "z": 0}], "time": 1, "interpolation": "catmullrom"},
                        {"channel": "scale", "data_points": [{"x": 1, "y": 1, "z": 1}], "time": 0, "interpolation": "step"},
                        {"channel": "scale", "data_points": [{"x": 2, "y": "", "z": "math.sin(q.anim_time)"}], "time": 0.75, "interpolation": "bezier"}]},
                      "effects": {"name": "Effects", "type": "effect", "keyframes": []}}}
                  ]
                }
                """.formatted(PNG);
    }

    @Test
    void groupsBecomeBonesParentsFirst() {
        List<String> problems = new ArrayList<>();
        AnimatedModel model = read(chest(), problems);

        assertEquals(List.of("base", "lid", "latch"), model.bones().stream().map(AnimatedModel.Bone::name).toList());
        assertEquals(List.of(-1, -1, 1), model.bones().stream().map(AnimatedModel.Bone::parent).toList());
        // Pivots in blocks, mirrored in X and Z: the lid's hinge is at the back (+z in Blockbench).
        assertEquals(new Placement.Vec3(0, 0, 0), model.bones().get(0).offset());
        assertEquals(new Placement.Vec3(0, 0.625f, -0.4375f), model.bones().get(1).offset());
        assertEquals(new Placement.Vec3(0, 0.125f, 0.875f), model.bones().get(2).offset(), "relative to the lid's hinge");
        assertEquals(1f, model.bones().get(1).modelScale());
        assertEquals(List.of("open", "close", "wobble"), List.copyOf(model.clips().keySet()));
    }

    @Test
    void aBoneModelHasItsPivotAtTheCentre() {
        AnimatedModel model = read(chest(), new ArrayList<>());
        JsonObject lid = JsonParser.parseString(model.bones().get(1).model()).getAsJsonObject();

        JsonObject element = lid.getAsJsonArray("elements").get(0).getAsJsonObject();
        assertEquals("[1,8,-6]", element.get("from").toString(), "(from - hinge) + 8");
        assertEquals("[15,12,8]", element.get("to").toString());
        assertEquals("demo:ruby_chest/tex_0", lid.getAsJsonObject("textures").get("0").getAsString());
        assertEquals("#0", lid.getAsJsonObject("textures").get("particle").getAsString());
        // UVs from a 64x32 texture to Minecraft's 0-16.
        assertEquals("[0,10,3.5,12]", element.getAsJsonObject("faces").getAsJsonObject("north").get("uv").toString());
    }

    @Test
    void facesWithoutATextureAreLeftOutAndHiddenCubesSkipped() {
        AnimatedModel model = read(chest(), new ArrayList<>());
        JsonObject base = JsonParser.parseString(model.bones().get(0).model()).getAsJsonObject();
        JsonArray elements = base.getAsJsonArray("elements");

        assertEquals(1, elements.size(), "the invisible cube is not drawn");
        JsonObject faces = elements.get(0).getAsJsonObject().getAsJsonObject("faces");
        assertTrue(!faces.has("up"), "texture: null means no face");
        assertEquals(180, faces.getAsJsonObject("south").get("rotation").getAsInt());
    }

    @Test
    void legalElementRotationsAreKeptAroundTheirOrigin() {
        AnimatedModel model = read(chest(), new ArrayList<>());
        JsonObject latch = JsonParser.parseString(model.bones().get(2).model()).getAsJsonObject();
        JsonObject rotation = latch.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonObject("rotation");

        assertEquals(22.5f, rotation.get("angle").getAsFloat());
        assertEquals("z", rotation.get("axis").getAsString());
        assertEquals("[8,8,8]", rotation.get("origin").toString(), "the latch's origin is its pivot");
    }

    @Test
    void whatCannotBeDrawnIsReportedOnce() {
        List<String> problems = new ArrayList<>();
        read(chest(), problems);

        assertTrue(problems.stream().anyMatch(p -> p.contains("'locator' is not supported")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("bezier keyframes are played as linear")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("Molang expressions")), problems.toString());
        assertEquals(problems.size(), problems.stream().distinct().count(), problems.toString());
    }

    @Test
    void animationValuesAreConvertedToTheDisplayFrame() {
        AnimatedModel model = read(chest(), new ArrayList<>());

        AnimatedModel.Clip open = model.clips().get("open");
        assertEquals(10, open.length(), "half a second");
        assertEquals(AnimatedModel.Loop.HOLD, open.loop());
        AnimatedModel.Keyframe swung = open.channels().get(1).rotation().get(1);
        assertEquals(10f, swung.tick());
        assertEquals(new Placement.Vec3(-90, 0, 0), swung.value(), "Blockbench 5: x and z flip");

        AnimatedModel.Keyframe moved = model.clips().get("wobble").channels().get(0).position().get(1);
        assertEquals(new Placement.Vec3(-1, 0.5f, -0.25f), moved.value(), "pixels to blocks, x and z flipped");
        AnimatedModel.Keyframe scaled = model.clips().get("wobble").channels().get(0).scale().get(1);
        assertEquals(new Placement.Vec3(2, 1, 1), scaled.value(), "an empty or Molang scale reads as 1");
        assertEquals(AnimatedModel.Loop.ONCE, model.clips().get("close").loop());
        assertEquals(AnimatedModel.Loop.LOOP, model.clips().get("wobble").loop());
    }

    @Test
    void olderBlockbenchFlipsOtherComponents() {
        String legacy = chest().replace("\"format_version\": \"5.0\"", "\"format_version\": \"4.10\"");
        AnimatedModel model = read(legacy, new ArrayList<>());

        assertEquals(new Placement.Vec3(90, 0, 0), model.clips().get("open").channels().get(1).rotation().get(1).value());
        assertEquals(new Placement.Vec3(1, 0.5f, -0.25f), model.clips().get("wobble").channels().get(0).position().get(1).value());
    }

    @Test
    void anEmbeddedTextureIsKeptByteForByte() {
        AnimatedModel model = read(chest(), new ArrayList<>());
        assertEquals(1, model.textures().size());
        assertEquals(new ResourceLocation("demo", "ruby_chest/tex_0"), model.textures().getFirst().location());
        assertArrayEquals(Base64.getDecoder().decode(PNG), model.textures().getFirst().png());
    }

    @Test
    void aTextureBesideTheProjectIsReadButNeverOutsideThePack() throws Exception {
        Path pack = dir.resolve("pack");
        Files.createDirectories(pack.resolve("models"));
        Files.write(pack.resolve("models/chest.png"), Base64.getDecoder().decode(PNG));
        Files.write(dir.resolve("secret.png"), Base64.getDecoder().decode(PNG));
        String beside = chest().replace("\"source\": \"data:image/png;base64," + PNG + "\"", "\"relative_path\": \"chest.png\"");
        String outside = chest().replace("\"source\": \"data:image/png;base64," + PNG + "\"", "\"relative_path\": \"../../secret.png\"");

        List<String> problems = new ArrayList<>();
        AnimatedModel ok = BbModelReader.read(beside, pack.resolve("models/chest.bbmodel"), pack, ITEM, problems);
        assertNotNull(ok);
        assertTrue(ok.textures().getFirst().png().length > 0);
        assertTrue(problems.stream().noneMatch(p -> p.contains("texture")), problems.toString());

        BbModelReader.read(outside, pack.resolve("models/chest.bbmodel"), pack, ITEM, problems);
        assertTrue(problems.stream().anyMatch(p -> p.contains("outside the content pack")), problems.toString());
    }

    @Test
    void theIconIsTheWholeModelAtRest() {
        AnimatedModel model = read(chest(), new ArrayList<>());
        assertNotNull(model.icon());
        JsonObject icon = JsonParser.parseString(model.icon()).getAsJsonObject();
        assertEquals(3, icon.getAsJsonArray("elements").size());
        assertEquals("minecraft:block/block", icon.get("parent").getAsString(), "drawn like a block in an inventory");
        // Bottom centre of the block is Blockbench's origin.
        assertEquals("[1,0,1]", icon.getAsJsonArray("elements").get(0).getAsJsonObject().get("from").toString());
    }

    @Test
    void aRotatedGroupHasNoMergedIcon() {
        String turned = chest().replace("\"name\": \"Base\", \"uuid\": \"g1\", \"origin\": [0, 0, 0]",
                "\"name\": \"Base\", \"uuid\": \"g1\", \"origin\": [0, 0, 0], \"rotation\": [0, 30, 0]");
        AnimatedModel model = read(turned, new ArrayList<>());
        assertNull(model.icon());
        assertEquals(new Placement.Vec3(0, 30, 0), model.bones().getFirst().restRotation(), "y is not mirrored");
    }

    @Test
    void aBoneTooLargeForMinecraftIsShrunkAndScaledBack() {
        String big = chest().replace("\"from\": [-7, 0, -7], \"to\": [7, 10, 7]", "\"from\": [-40, 0, -7], \"to\": [40, 10, 7]");
        AnimatedModel model = read(big, new ArrayList<>());
        AnimatedModel.Bone base = model.bones().getFirst();

        assertEquals(40f / 24f, base.modelScale(), 1e-5);
        JsonObject element = JsonParser.parseString(base.model()).getAsJsonObject().getAsJsonArray("elements").get(0).getAsJsonObject();
        for (JsonArray corner : List.of(element.getAsJsonArray("from"), element.getAsJsonArray("to"))) {
            for (int i = 0; i < 3; i++) {
                float value = corner.get(i).getAsFloat();
                assertTrue(value >= -16 && value <= 32, corner.toString());
            }
        }
        assertNull(model.icon(), "too wide to merge");
    }

    @Test
    void illegalCubeRotationsAreRoundedAndReported() {
        String odd = chest().replace("\"rotation\": [0, 0, 22.5]", "\"rotation\": [10, 0, 30]");
        List<String> problems = new ArrayList<>();
        AnimatedModel model = read(odd, problems);

        JsonObject rotation = JsonParser.parseString(model.bones().get(2).model()).getAsJsonObject()
                .getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonObject("rotation");
        assertEquals(22.5f, rotation.get("angle").getAsFloat());
        assertEquals("z", rotation.get("axis").getAsString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("Minecraft models allow one axis")), problems.toString());
    }

    @Test
    void javaBlockModelsAreMeasuredFromTheBlockCorner() {
        String block = chest().replace("\"model_format\": \"free\"", "\"model_format\": \"java_block\"");
        AnimatedModel model = read(block, new ArrayList<>());
        // The lid's hinge at (0, 10, 7) is now 8 pixels west and north of the centre.
        assertEquals(new Placement.Vec3(0.5f, 0.625f, 0.0625f), model.bones().get(1).offset());
    }

    @Test
    void unusableFilesAreRefused() {
        List<String> problems = new ArrayList<>();
        assertNull(read("{not json", problems));
        assertNull(read("[1, 2]", problems));
        assertNull(read("{\"elements\": [], \"outliner\": []}", problems));
        assertEquals(3, problems.size(), problems.toString());
        assertTrue(problems.get(2).contains("no cubes"));
    }

    @Test
    void namesAreMadeSafeAndUnique() {
        assertEquals("latch", BbModelReader.name("Latch!"));
        assertEquals("left_door", BbModelReader.name("Left Door"));
        assertEquals("bone", BbModelReader.name("¡¿?!"));
        String twins = chest().replace("\"name\": \"lid\", \"uuid\": \"g2\"", "\"name\": \"base\", \"uuid\": \"g2\"");
        assertEquals(List.of("base", "base_2", "latch"), read(twins, new ArrayList<>()).bones().stream()
                .map(AnimatedModel.Bone::name).toList());
    }

    @Test
    void anglesRoundToWhatModelsAllow() {
        assertEquals(22.5f, BbModelReader.legalAngle(30));
        assertEquals(45f, BbModelReader.legalAngle(90));
        assertEquals(-45f, BbModelReader.legalAngle(-60));
        assertEquals(0f, BbModelReader.legalAngle(5));
    }

    public static AnimatedModel read(String json, List<String> problems) {
        return BbModelReader.read(json, Path.of("contents/demo/models/ruby_chest.bbmodel"), Path.of("contents/demo"),
                ITEM, problems);
    }
}
