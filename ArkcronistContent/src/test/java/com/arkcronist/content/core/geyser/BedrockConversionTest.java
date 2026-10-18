package com.arkcronist.content.core.geyser;

import com.arkcronist.content.core.block.NoteBlockState;
import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.definition.ResourceLocation;
import com.arkcronist.content.core.furniture.Footprint;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 3.0: the content converted for Bedrock players - models, furniture cut into blocks, icons, the pack. */
class BedrockConversionTest {

    private static final ResourceLocation STONE = new ResourceLocation("demo", "block/stone");

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    void aCubeAllBlockResolvesToOneCubeWithItsTextureOnEveryFace() {
        JavaModel model = JavaModel.resolve(new ResourceLocation("demo", "block/ruby"),
                location -> location.path().equals("block/ruby")
                        ? json("{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"demo:block/ruby\"}}") : null);
        assertNotNull(model);
        assertEquals(1, model.elements().size());
        assertEquals(6, model.elements().get(0).faces().size());
        model.elements().get(0).faces().values()
                .forEach(face -> assertEquals(new ResourceLocation("demo", "block/ruby"), face.texture()));
        assertArrayEquals(new float[]{0, 0, 16, 16}, model.elements().get(0).faces().get(JavaModel.Face.UP).uv());
    }

    @Test
    void aFlatItemHasLayersAndAMissingParentResolvesToNothing() {
        JavaModel flat = JavaModel.resolve(new ResourceLocation("demo", "item/ruby"),
                location -> json("{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"demo:item/ruby\"}}"));
        assertEquals(List.of(new ResourceLocation("demo", "item/ruby")), flat.layers());
        assertNull(JavaModel.resolve(new ResourceLocation("demo", "x"),
                location -> location.path().equals("x") ? json("{\"parent\":\"other:nowhere\"}") : null));
    }

    /** A face with no UV of its own gets its rectangle on the 16 x 16 square, as the client lays it. */
    @Test
    void facesWithoutUvTakeTheirOwnRectangle() {
        float[] from = {2, 3, 4};
        float[] to = {10, 12, 14};
        assertArrayEquals(new float[]{6, 4, 14, 13}, JavaModel.autoUv(JavaModel.Face.NORTH, from, to));
        assertArrayEquals(new float[]{2, 4, 10, 13}, JavaModel.autoUv(JavaModel.Face.SOUTH, from, to));
        assertArrayEquals(new float[]{2, 4, 10, 14}, JavaModel.autoUv(JavaModel.Face.UP, from, to));
        assertArrayEquals(new float[]{2, 2, 10, 12}, JavaModel.autoUv(JavaModel.Face.DOWN, from, to));
    }

    private static JavaModel cubeModel(float[] from, float[] to) {
        JsonObject model = json("{\"textures\":{\"0\":\"demo:block/stone\"},\"elements\":[{\"from\":[" + from[0] + ","
                + from[1] + "," + from[2] + "],\"to\":[" + to[0] + "," + to[1] + "," + to[2] + "],\"faces\":{"
                + "\"north\":{\"uv\":[0,0,16,16],\"texture\":\"#0\"},\"south\":{\"uv\":[0,0,16,16],\"texture\":\"#0\"},"
                + "\"up\":{\"uv\":[0,0,16,8],\"texture\":\"#0\"},\"east\":{\"texture\":\"#0\"}}}]}");
        return JavaModel.resolve(new ResourceLocation("demo", "m"), location -> model);
    }

    /**
     * A single block piece: the item display's half turn puts the model's south face to the north,
     * turns its top's picture half round, and leaves the cube in its own block.
     */
    @Test
    void aOneBlockPieceIsHalfTurnedIntoItsOwnBlock() {
        FurnitureLayout.Layout layout = FurnitureLayout.lay(cubeModel(new float[]{0, 0, 0}, new float[]{16, 16, 16}),
                Footprint.SINGLE, Placement.Display.DEFAULT, false);
        assertEquals(Set.of(new FurnitureLayout.Cell(0, 0, 0)), layout.cells().keySet());
        BedrockCube cube = layout.cells().get(new FurnitureLayout.Cell(0, 0, 0)).get(0);
        assertArrayEquals(new float[]{-8, 0, -8}, cube.min());
        assertArrayEquals(new float[]{8, 16, 8}, cube.max());
        assertTrue(cube.faces().containsKey(JavaModel.Face.NORTH) && cube.faces().containsKey(JavaModel.Face.WEST),
                "the model's east is the world's west");
        assertArrayEquals(new float[]{16, 8, 0, 0}, cube.faces().get(JavaModel.Face.UP).uv(), "the top turned half round");
        assertTrue(layout.notes().isEmpty());
    }

    /**
     * A bench two blocks wide is cut at the block boundary; each half keeps its half of the front's
     * texture - the model's north face, shown to the south once turned.
     */
    @Test
    void aTwoBlockBenchIsCutInTwoAndEachHalfKeepsItsHalfOfTheTexture() {
        FurnitureLayout.Layout layout = FurnitureLayout.lay(cubeModel(new float[]{-8, 0, 4}, new float[]{24, 8, 12}),
                new Footprint(2, 1), Placement.Display.DEFAULT, false);
        assertEquals(Set.of(new FurnitureLayout.Cell(0, 0, 0), new FurnitureLayout.Cell(1, 0, 0)),
                layout.cells().keySet());
        BedrockCube west = layout.cells().get(new FurnitureLayout.Cell(0, 0, 0)).get(0);
        BedrockCube east = layout.cells().get(new FurnitureLayout.Cell(1, 0, 0)).get(0);
        assertArrayEquals(new float[]{-8, 0, -4}, west.min());
        assertArrayEquals(new float[]{8, 8, 4}, west.max());
        assertArrayEquals(new float[]{-8, 0, -4}, east.min(), "in its own block's frame");
        // The model's north face (u 0-16 running east to west) is the world's south: x in the model
        // runs the other way once turned, so the world's west half carries u 8-16... of the model's east half.
        float[] westFront = west.faces().get(JavaModel.Face.SOUTH).uv();
        float[] eastFront = east.faces().get(JavaModel.Face.SOUTH).uv();
        assertEquals(8, Math.abs(westFront[2] - westFront[0]), 1e-4);
        assertEquals(8, Math.abs(eastFront[2] - eastFront[0]), 1e-4);
        assertEquals(16, Math.min(westFront[0], westFront[2]) + Math.min(eastFront[0], eastFront[2]) + 8, 1e-4);
        assertFalse(west.faces().containsKey(JavaModel.Face.EAST) && east.faces().containsKey(JavaModel.Face.WEST),
                "no face where the bench was cut");
    }

    /** A chair back 24.5 pixels tall: the 8.5 above its block go to the block above. */
    @Test
    void whatRisesAboveItsBlockGoesToTheBlockAbove() {
        FurnitureLayout.Layout layout = FurnitureLayout.lay(cubeModel(new float[]{2, 0, 12}, new float[]{14, 24.5f, 14}),
                Footprint.SINGLE, Placement.Display.DEFAULT, false);
        BedrockCube top = layout.cells().get(new FurnitureLayout.Cell(0, 1, 0)).get(0);
        assertEquals(0, top.min()[1], 1e-4);
        assertEquals(8.5f, top.max()[1], 1e-4);
        assertTrue(BedrockGeometry.fits(layout.cells().get(new FurnitureLayout.Cell(0, 0, 0))));
    }

    /**
     * The blocks a turned piece covers, worked out by turning its south-facing layout, are exactly
     * the blocks the plugin puts its supports on - for every footprint and every quarter turn.
     */
    @Test
    void turnedCellsLandOnTheSupportsThePluginPlaces() {
        for (Footprint footprint : List.of(Footprint.SINGLE, new Footprint(2, 1), new Footprint(3, 2),
                new Footprint(4, 1), new Footprint(1, 3))) {
            for (int quarter = 0; quarter < 4; quarter++) {
                Set<String> turned = new TreeSet<>();
                for (int[] offset : footprint.offsets(0)) {
                    FurnitureLayout.Cell cell = FurnitureLayout.turned(new FurnitureLayout.Cell(offset[0], offset[1],
                            offset[2]), footprint, quarter);
                    turned.add(cell.key());
                }
                Set<String> supports = new TreeSet<>();
                for (int[] offset : footprint.offsets(quarter * 90f)) {
                    supports.add(offset[0] + "," + offset[1] + "," + offset[2]);
                }
                assertEquals(supports, turned, footprint + " turned " + quarter);
            }
        }
    }

    /** Bedrock stores x mirrored and lays the top and bottom pictures half turned against Java. */
    @Test
    void geometryIsWrittenInBedrocksTerms() {
        BedrockCube cube = new BedrockCube(new float[]{2, 0, -3}, new float[]{6, 4, 5},
                Map.of(JavaModel.Face.UP, new BedrockCube.Face(new float[]{1, 2, 5, 10}, STONE),
                        JavaModel.Face.EAST, new BedrockCube.Face(new float[]{0, 0, 8, 4}, STONE)),
                null, 0, new float[]{0, 0, 0});
        BedrockGeometry.Built built = BedrockGeometry.build("geometry.arkcontent.test", List.of(cube));
        JsonObject written = built.json().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
                .getAsJsonArray("bones").get(0).getAsJsonObject().getAsJsonArray("cubes").get(0).getAsJsonObject();
        assertEquals("[-6.0,0.0,-3.0]", written.get("origin").toString());
        assertEquals("[4.0,4.0,8.0]", written.get("size").toString());
        JsonObject up = written.getAsJsonObject("uv").getAsJsonObject("up");
        assertEquals("[5.0,10.0]", up.get("uv").toString());
        assertEquals("[-4.0,-8.0]", up.get("uv_size").toString());
        assertEquals("[0.0,0.0]", written.getAsJsonObject("uv").getAsJsonObject("east").get("uv").toString());
        assertEquals(Map.of("t0", STONE), built.materials());
        assertFalse(BedrockGeometry.fits(List.of(cube.moved(0, 20, 0))));
    }

    private static byte[] png(int width, int height, int argb) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, argb);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test
    void aBlockIconIsDrawnLitFromAbove() throws IOException {
        JavaModel model = cubeModel(new float[]{0, 0, 0}, new float[]{16, 16, 16});
        BufferedImage texture = ImageIO.read(new ByteArrayInputStream(png(16, 16, 0xffc04040)));
        BufferedImage icon = IconRenderer.render(model, location -> texture);
        assertNotNull(icon);
        int top = icon.getRGB(16, 6);
        int side = icon.getRGB(10, 22);
        assertTrue((top >>> 24) > 200 && (side >>> 24) > 200, "both drawn");
        assertTrue(((top >> 16) & 0xff) > ((side >> 16) & 0xff), "the top brighter than a side");
        assertEquals(0, icon.getRGB(0, 0) >>> 24, "the corner left clear");
    }

    /** 3.3.1: a model hung far below its block - a cosmetic worn on the back - is drawn centred, not lost. */
    @Test
    void aModelOutsideItsFrameIsDrawnCentred() throws IOException {
        JavaModel model = cubeModel(new float[]{4, -28, 4}, new float[]{12, -20, 12});
        BufferedImage texture = ImageIO.read(new ByteArrayInputStream(png(16, 16, 0xff40c040)));
        BufferedImage icon = IconRenderer.render(model, location -> texture);
        assertNotNull(icon, "drawn after all");
        assertTrue((icon.getRGB(IconRenderer.SIZE / 2, IconRenderer.SIZE / 2) >>> 24) > 200, "in the middle of the icon");
        assertEquals(0, icon.getRGB(0, 0) >>> 24, "the corner left clear");
    }

    private static BedrockConverter.Input input(Map<String, byte[]> pack, Map<String, Integer> previous) {
        Placement.Display display = Placement.Display.DEFAULT;
        return new BedrockConverter.Input(pack::get,
                List.of(new BedrockConverter.ItemSpec("demo:ruby", "PAPER", new ResourceLocation("demo", "ruby"),
                                new ResourceLocation("demo", "item/ruby"), 1001, "Ruby", false),
                        new BedrockConverter.ItemSpec("demo:bench", "BARRIER", new ResourceLocation("demo", "bench"),
                                new ResourceLocation("demo", "furniture/bench"), 1002, "Bench", false)),
                List.of(new BedrockConverter.BlockSpec("demo:stone_block", NoteBlockState.fromIndex(1),
                        new ResourceLocation("demo", "block/stone_block"), "Stone block")),
                List.of(),
                List.of(new BedrockConverter.FurnitureSpec("demo:bench", new ResourceLocation("demo", "furniture/bench"),
                        new Footprint(2, 1), display, false, true, 0, "Bench")),
                List.of(new BedrockConverter.EmojiSpec(0xE001, new ResourceLocation("demo", "emoji/ruby"))),
                previous, Set.of(1, 799));
    }

    private static Map<String, byte[]> pack() throws IOException {
        Map<String, byte[]> pack = new HashMap<>();
        pack.put("assets/demo/models/item/ruby.json",
                "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"demo:item/ruby\"}}".getBytes(StandardCharsets.UTF_8));
        pack.put("assets/demo/models/block/stone_block.json",
                "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"demo:block/stone\"}}".getBytes(StandardCharsets.UTF_8));
        pack.put("assets/demo/models/furniture/bench.json", ("{\"textures\":{\"0\":\"demo:block/stone\"},\"elements\":"
                + "[{\"from\":[-8,0,4],\"to\":[24,8,12],\"faces\":{\"north\":{\"texture\":\"#0\"},\"up\":{\"texture\":\"#0\"}}}]}")
                .getBytes(StandardCharsets.UTF_8));
        pack.put("assets/demo/textures/item/ruby.png", png(16, 16, 0xffd02020));
        pack.put("assets/demo/textures/block/stone.png", png(16, 16, 0xff808080));
        pack.put("assets/demo/textures/emoji/ruby.png", png(16, 16, 0xffff0000));
        return pack;
    }

    private static Map<String, byte[]> unzip(byte[] zip) throws IOException {
        Map<String, byte[]> files = new HashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                files.put(entry.getName(), in.readAllBytes());
            }
        }
        return files;
    }

    /**
     * The whole conversion: items with icons, a custom block as a full block, the bench as two
     * blocks four ways round on carrier states from the top of the range, the emoji on its glyph
     * sheet - and the same content gives the same pack.
     */
    @Test
    void contentBecomesBedrockItemsBlocksCarriersAndAPack() throws IOException {
        BedrockConverter.Build build = BedrockConverter.convert(input(pack(), Map.of()));
        assertEquals(List.of(), build.problems());
        assertEquals(List.of("ark_demo_ruby", "ark_demo_bench"), build.items().stream().map(BedrockConverter.BedrockItem::name).toList());
        assertEquals("minecraft:barrier", build.items().get(1).javaItem());

        BedrockConverter.BedrockBlock stone = build.blocks().get(0);
        assertEquals("minecraft:note_block[instrument=harp,note=0,powered=true]", stone.javaState());
        assertEquals("minecraft:geometry.full_block", stone.geometry());
        assertEquals("ark_demo_block_stone", stone.materials().get("up").texture());

        assertEquals(8, build.carriers().size(), "two blocks, four quarter turns");
        Set<Integer> states = new HashSet<>();
        build.carriers().forEach(carrier -> states.add(carrier.state().index()));
        assertEquals(8, states.size());
        assertFalse(states.contains(799), "a custom block's state is never a carrier");
        assertTrue(states.stream().allMatch(state -> state >= 790), "carriers come from the top: " + states);
        assertEquals(new TreeSet<>(states), new TreeSet<>(build.carrierAssignments().values()));
        BedrockConverter.Carrier turned = build.carriers().stream()
                .filter(carrier -> carrier.quarter() == 1 && !(carrier.dx() == 0 && carrier.dz() == 0)).findFirst().orElseThrow();
        assertEquals(1, turned.dz(), "facing west, the bench's second block is to the south");
        assertTrue(build.blocks().stream().anyMatch(block -> block.rotationY() == -90));

        Map<String, byte[]> files = unzip(build.pack());
        assertTrue(files.containsKey("manifest.json"));
        assertTrue(files.containsKey("textures/terrain_texture.json") && files.containsKey("textures/item_texture.json"));
        assertTrue(files.containsKey("textures/items/ark/ark_demo_bench.png"), "the bench drawn as an icon");
        assertTrue(files.containsKey("models/blocks/ark/ark_demo_bench_c0_0_0.geo.json"));
        assertTrue(files.containsKey("models/blocks/ark/ark_demo_bench_c1_0_0.geo.json"));
        assertTrue(files.containsKey("font/glyph_E0.png"));
        JsonObject manifest = JsonParser.parseString(new String(files.get("manifest.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray modules = manifest.getAsJsonArray("modules");
        assertEquals("resources", modules.get(0).getAsJsonObject().get("type").getAsString());

        BedrockConverter.Build again = BedrockConverter.convert(input(pack(), build.carrierAssignments()));
        assertArrayEquals(build.pack(), again.pack(), "the same content, the same bytes");
        assertEquals(build.carrierAssignments(), again.carrierAssignments(), "carriers kept");
    }

    /**
     * 3.3.1: ItemsAdder's generated packs give every texture a short sprite name in the atlas
     * ({@code ia:7} for {@code demo:item/ruby}) and the models use that name: the icon is still found.
     */
    @Test
    void anAtlasSpriteNameLeadsToItsTexture() throws IOException {
        Map<String, byte[]> pack = pack();
        pack.put("assets/demo/models/item/ruby.json",
                "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"ia:7\"}}".getBytes(StandardCharsets.UTF_8));
        BedrockConverter.Build without = BedrockConverter.convert(input(pack, Map.of()));
        assertEquals(List.of("demo:ruby"), without.unpictured(), "no atlas: ia:7 is nowhere");
        pack.put("assets/minecraft/atlases/blocks.json", ("{\"sources\":[{\"type\":\"minecraft:single\","
                + "\"resource\":\"demo:item/ruby\",\"sprite\":\"ia:7\"},{\"type\":\"single\",\"resource\":\"Bad Name\","
                + "\"sprite\":\"ia:8\"}]}").getBytes(StandardCharsets.UTF_8));
        BedrockConverter.Build with = BedrockConverter.convert(input(pack, Map.of()));
        assertEquals(List.of(), with.unpictured(), "found through the atlas, a bad entry beside it or not");
        assertEquals(List.of(), with.problems());
    }

    /**
     * 3.3.2: ItemsAdder's packs draw each item on the plain vanilla one by its custom_model_data number
     * (a range dispatch, each range closed by the vanilla model). Items the Java pack draws so are
     * marked, for Geyser 2.9 and later to know them by that number too; nothing else is.
     */
    @Test
    void itemsTheJavaPackDrawsByNumberAreMarked() throws IOException {
        Map<String, byte[]> pack = pack();
        BedrockConverter.Build without = BedrockConverter.convert(input(pack, Map.of()));
        assertFalse(without.items().get(0).byNumber(), "no vanilla definition: not drawn by number");

        pack.put("assets/minecraft/items/paper.json", ("{\"model\":{\"type\":\"minecraft:select\",\"property\":"
                + "\"minecraft:display_context\",\"cases\":[],\"fallback\":{\"type\":\"minecraft:range_dispatch\","
                + "\"property\":\"minecraft:custom_model_data\",\"entries\":["
                + "{\"threshold\":1001,\"model\":{\"type\":\"model\",\"model\":\"demo:item/ruby\"}},"
                + "{\"threshold\":1002,\"model\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:item/paper\"}}],"
                + "\"fallback\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:item/paper\"}}}}")
                .getBytes(StandardCharsets.UTF_8));
        BedrockConverter.Build with = BedrockConverter.convert(input(pack, Map.of()));
        assertTrue(with.items().get(0).byNumber(), "the ruby, 1001 on paper: drawn by number, inside a select too");
        assertFalse(with.items().get(1).byNumber(), "the bench is a barrier: nothing draws barriers by number");

        pack.put("assets/minecraft/items/paper.json", ("{\"model\":{\"type\":\"range_dispatch\",\"property\":"
                + "\"custom_model_data\",\"entries\":[{\"threshold\":1001,\"model\":{\"type\":\"model\","
                + "\"model\":\"minecraft:item/paper\"}}],\"fallback\":{\"type\":\"model\",\"model\":"
                + "\"minecraft:item/paper\"}}}").getBytes(StandardCharsets.UTF_8));
        assertFalse(BedrockConverter.convert(input(pack, Map.of())).items().get(0).byNumber(),
                "1001 drawn as plain paper is not the ruby's");
    }

    private static boolean drawn(BufferedImage sheet, int character) {
        int cell = sheet.getWidth() / 16;
        int low = character & 0xff;
        for (int y = (low >> 4) * cell; y < ((low >> 4) + 1) * cell; y++) {
            for (int x = (low & 15) * cell; x < ((low & 15) + 1) * cell; x++) {
                if ((sheet.getRGB(x, y) >>> 24) != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The rows of a glyph's cell that hold any pixel. */
    private static int drawnRows(BufferedImage sheet, int character) {
        int cell = sheet.getWidth() / 16;
        int low = character & 0xff;
        int rows = 0;
        for (int y = (low >> 4) * cell; y < ((low >> 4) + 1) * cell; y++) {
            for (int x = (low & 15) * cell; x < ((low & 15) + 1) * cell; x++) {
                if ((sheet.getRGB(x, y) >>> 24) != 0) {
                    rows++;
                    break;
                }
            }
        }
        return rows;
    }

    /**
     * 3.3.3: the Java pack's own font images - ranks in a scoreboard - become Bedrock glyphs, drawn
     * at the size Java draws them (Bedrock draws a sheet's pixels about one to a font pixel): a rank
     * 22 pixels high shown ten high is drawn 11 high, whole, in a larger cell. Only a glyph wider
     * than the largest cell is cut into pieces, on free characters the build names. Spaces and menu
     * backgrounds are drawn as nothing rather than as boxes; a referenced font counts too.
     */
    @Test
    void theJavaFontsImagesBecomeBedrockGlyphs() throws IOException {
        Map<String, byte[]> pack = pack();
        pack.put("assets/minecraft/font/default.json", ("{\"providers\":["
                + "{\"type\":\"bitmap\",\"file\":\"demo:font/badge.png\",\"height\":10,\"ascent\":8,\"chars\":[\"\\ue6a0\"]},"
                + "{\"type\":\"bitmap\",\"file\":\"demo:font/icon.png\",\"ascent\":7,\"chars\":[\"\\ue6a1\"]},"
                + "{\"type\":\"bitmap\",\"file\":\"demo:font/menu.png\",\"height\":256,\"ascent\":13,\"chars\":[\"\\ue6a2\"]},"
                + "{\"type\":\"bitmap\",\"file\":\"demo:font/banner.png\",\"height\":10,\"ascent\":8,\"chars\":[\"\\ue6a4\"]},"
                + "{\"type\":\"space\",\"advances\":{\"\\uf801\":-1,\" \":4}},"
                + "{\"type\":\"reference\",\"id\":\"minecraft:include/more\"}]}").getBytes(StandardCharsets.UTF_8));
        pack.put("assets/minecraft/font/include/more.json", ("{\"providers\":[{\"type\":\"bitmap\",\"file\":"
                + "\"demo:font/icon.png\",\"height\":8,\"ascent\":7,\"chars\":[\"\\ue6a3\"]}]}").getBytes(StandardCharsets.UTF_8));
        pack.put("assets/demo/textures/font/badge.png", png(88, 22, 0xffc08020));
        pack.put("assets/demo/textures/font/icon.png", png(16, 16, 0xff20c040));
        pack.put("assets/demo/textures/font/menu.png", png(256, 256, 0xff808080));
        pack.put("assets/demo/textures/font/banner.png", png(300, 10, 0xff4060c0));

        BedrockConverter.Build build = BedrockConverter.convert(input(pack, Map.of()));
        assertEquals(List.of(), build.problems());
        assertEquals(Set.of(0xE6A4), build.glyphText().keySet(), "only the banner, 300 wide, is wider than a cell");
        String pieces = build.glyphText().get(0xE6A4);
        assertEquals(3, pieces.codePointCount(0, pieces.length()), "300 pixels, 128 a piece: three");
        assertTrue(pieces.codePoints().allMatch(c -> c >= 0xE200 && c <= 0xF7FF), "free characters, not E0 or E1");

        Map<String, byte[]> files = unzip(build.pack());
        BufferedImage e6 = ImageIO.read(new ByteArrayInputStream(files.get("font/glyph_E6.png")));
        assertEquals(128 * 16, e6.getWidth(), "the banner's own glyph needs the largest cell");
        assertEquals(11, drawnRows(e6, 0xE6A0), "22 high shown ten high: half, 11 rows, whole");
        assertEquals(8, drawnRows(e6, 0xE6A1), "16 high shown eight high: eight rows");
        assertFalse(drawn(e6, 0xE6A2), "a menu background is no inline glyph: drawn as nothing");
        assertTrue(drawn(e6, 0xE6A3), "from the referenced font");
        BufferedImage spaces = ImageIO.read(new ByteArrayInputStream(files.get("font/glyph_F8.png")));
        assertFalse(drawn(spaces, 0xF801), "a space is there, and empty: no box on Bedrock");
        int first = pieces.codePointAt(0);
        BufferedImage cut = ImageIO.read(new ByteArrayInputStream(files.get(String.format("font/glyph_%02X.png", first >> 8))));
        pieces.codePoints().forEach(piece -> assertEquals(10, drawnRows(cut, piece), "piece " + Integer.toHexString(piece)));
        assertTrue(files.containsKey("font/glyph_E0.png"), "the emojis are still there");
    }

    /** 3.3: furniture listed in bedrock.skip-furniture gets no carriers; its item still has its icon. */
    @Test
    void skippedFurnitureGetsNoCarriers() throws IOException {
        BedrockConverter.Input base = input(pack(), Map.of());
        for (Set<String> skip : List.of(Set.of("demo:bench"), Set.of("demo:*"))) {
            BedrockConverter.Build build = BedrockConverter.convert(new BedrockConverter.Input(base.pack(), base.items(),
                    base.blocks(), base.liquids(), base.furniture(), base.emojis(), base.previousCarriers(),
                    base.blockStates(), skip));
            assertEquals(List.of(), build.problems());
            assertEquals(0, build.carriers().size(), skip.toString());
            assertEquals(Map.of(), build.carrierAssignments());
            assertEquals(2, build.items().size(), "the bench is still an item");
        }
    }

    /**
     * An item made with resource.item-model - most imported ones - names an item definition, not
     * a model: its icon comes from the model the definition draws by default. And however long an
     * id, no path in the pack reaches 80 characters, which some Bedrock platforms cannot open.
     */
    @Test
    void anItemDefinitionIsFollowedToItsModelAndLongNamesGetShortPaths() throws IOException {
        Map<String, byte[]> pack = pack();
        String longId = "nazgul_weaponry_vol_4:espada_del_senor_oscuro_de_las_tierras_del_este_edicion_coleccionista";
        String path = longId.substring(longId.indexOf(':') + 1);
        pack.put("assets/nazgul_weaponry_vol_4/items/" + path + ".json", ("{\"model\":{\"type\":\"minecraft:select\","
                + "\"property\":\"minecraft:display_context\",\"cases\":[{\"when\":\"gui\",\"model\":{\"type\":"
                + "\"minecraft:model\",\"model\":\"demo:item/ruby\"}}],\"fallback\":{\"type\":\"minecraft:model\","
                + "\"model\":\"demo:item/ruby\"}}}").getBytes(StandardCharsets.UTF_8));
        BedrockConverter.Input base = input(pack, Map.of());
        List<BedrockConverter.ItemSpec> items = new java.util.ArrayList<>(base.items());
        ResourceLocation definition = new ResourceLocation("nazgul_weaponry_vol_4", path);
        items.add(new BedrockConverter.ItemSpec(longId, "NETHERITE_SWORD", definition, definition, 1003, "Espada", true));
        BedrockConverter.Build build = BedrockConverter.convert(new BedrockConverter.Input(base.pack(), items,
                base.blocks(), base.liquids(), base.furniture(), base.emojis(), base.previousCarriers(), base.blockStates()));
        assertEquals(List.of(), build.problems());
        assertEquals(3, build.items().size(), "the sword has its icon");
        assertTrue(unzip(build.pack()).keySet().stream().allMatch(file -> file.length() < 80),
                "every path under 80 characters");
    }

    /**
     * Geyser 2.8 reverses the order items were registered in, then picks for a stack the first whose
     * number is at most the stack's (its bytecode: Collections.reverse, then a scan). Registered in
     * the order the content had, a ruby (12007) was drawn as a bucket of frost (12006) on a live
     * Bedrock client; highest first, everything was drawn as the lowest. Lowest first, each is itself.
     */
    @Test
    void itemsGoToGeyserLowestNumberFirst() {
        BedrockConverter.BedrockItem acid = item("demo:acid", 12005);
        BedrockConverter.BedrockItem frost = item("demo:frost", 12006);
        BedrockConverter.BedrockItem ruby = item("demo:ruby", 12007);
        BedrockConverter.BedrockItem backpack = item("medieval_props:mochila_viaje", 12404);
        BedrockConverter.BedrockItem none = item("demo:x", null);
        List<BedrockConverter.BedrockItem> order = BedrockConverter.lowestNumberFirst(List.of(ruby, none, backpack, frost, acid));
        assertEquals(List.of(acid, frost, ruby, backpack, none), order);
        List<BedrockConverter.BedrockItem> asGeyserReadsThem = new java.util.ArrayList<>(order);
        java.util.Collections.reverse(asGeyserReadsThem);
        for (BedrockConverter.BedrockItem stack : List.of(acid, frost, ruby, backpack)) {
            BedrockConverter.BedrockItem drawn = asGeyserReadsThem.stream().filter(item -> item.customModelData() != null
                    && stack.customModelData() >= item.customModelData()).findFirst().orElseThrow();
            assertEquals(stack, drawn);
        }
    }

    /**
     * An item whose textures are not in the pack still goes to Geyser, under its own name, with a
     * question mark: left out, Geyser 2.8 would draw a stack of it as the next item down by number.
     */
    @Test
    void anItemWithNoPictureIsAQuestionMarkNotAnotherItem() throws IOException {
        Map<String, byte[]> pack = pack();
        pack.put("assets/demo/models/item/lost.json",
                "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"ia:702\"}}".getBytes(StandardCharsets.UTF_8));
        BedrockConverter.Input base = input(pack, Map.of());
        List<BedrockConverter.ItemSpec> items = new java.util.ArrayList<>(base.items());
        items.add(new BedrockConverter.ItemSpec("demo:lost", "PAPER", new ResourceLocation("demo", "lost"),
                new ResourceLocation("demo", "item/lost"), 1004, "Lost", false));
        BedrockConverter.Build build = BedrockConverter.convert(new BedrockConverter.Input(base.pack(), items,
                base.blocks(), base.liquids(), base.furniture(), base.emojis(), base.previousCarriers(), base.blockStates()));
        BedrockConverter.BedrockItem lost = build.items().stream().filter(item -> item.id().equals("demo:lost"))
                .findFirst().orElseThrow();
        assertEquals(BedrockConverter.MISSING_ICON, lost.icon());
        assertEquals(1004, lost.customModelData());
        assertEquals(List.of("demo:lost"), build.unpictured());
        assertTrue(build.problems().get(0).contains("its textures are not in the pack"), build.problems().toString());
        assertTrue(unzip(build.pack()).containsKey("textures/items/ark/" + BedrockConverter.MISSING_ICON + ".png"));
    }

    private static BedrockConverter.BedrockItem item(String id, Integer number) {
        String name = BedrockConverter.name(id);
        return new BedrockConverter.BedrockItem(id, name, "minecraft:paper", number, id, name, id, false);
    }

    @Test
    void textureBlendingIsReadFromItsPixels() throws IOException {
        assertEquals("opaque", BedrockConverter.blending(ImageIO.read(new ByteArrayInputStream(png(4, 4, 0xff112233)))));
        assertEquals("alpha_test", BedrockConverter.blending(ImageIO.read(new ByteArrayInputStream(png(4, 4, 0x00000000)))));
        assertEquals("blend", BedrockConverter.blending(ImageIO.read(new ByteArrayInputStream(png(4, 4, 0x80ff0000)))));
        assertEquals("ark_demo_block_stone", BedrockConverter.name("demo:block/stone"));
    }
}
