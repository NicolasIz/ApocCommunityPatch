package com.arkcronist.content.core.geyser;

import com.arkcronist.content.core.block.NoteBlockState;
import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.definition.ResourceLocation;
import com.arkcronist.content.core.furniture.Footprint;
import com.arkcronist.content.core.pack.ItemModels;
import com.arkcronist.content.core.pack.PngReader;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * The content as Bedrock players see it through GeyserMC (3.0): every item mapped to a Bedrock
 * item with an icon, every custom block's note block state and every liquid's tripwire state
 * mapped to a Bedrock block, every piece of furniture cut into Bedrock blocks ({@link
 * FurnitureLayout}) - and the resource pack that draws all of it, built in memory from the Java
 * pack this plugin has just compiled.
 *
 * <p>Nothing is written for anyone to configure: the Geyser hook hands these to Geyser as it
 * starts, and the pack to the clients. Pure: it reads the Java pack through {@link PackFiles} and
 * returns data; the plugin decides what to do with it.</p>
 *
 * <p>Furniture has no block of its own in the world - it stands on barriers - so for Bedrock players
 * each block a piece covers is shown as a note block state no world ever holds, a <em>carrier</em>,
 * which Geyser draws as that part of the piece, turned to the piece's quarter. Carriers come from
 * the custom blocks' 799 states, from the top down, kept in {@code bedrock_carriers.json} like the
 * blocks' own, and never given to a custom block.</p>
 */
public final class BedrockConverter {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    /** Bedrock's seconds to break a block of hardness 0.8 by hand: a note block's. */
    private static final float NOTE_BLOCK_SECONDS = 1.2f;
    private static final String FULL_BLOCK = "minecraft:geometry.full_block";
    /** Paths in a Bedrock pack stay under this many characters: some platforms open no longer one. */
    private static final int MAX_PATH = 80;
    /** The picture of an item with none of its own. */
    static final String MISSING_ICON = "ark_no_picture";

    /** A file of the built Java pack by its path in the zip; null when there is none. */
    @FunctionalInterface
    public interface PackFiles {
        @Nullable
        byte[] read(String path);
    }

    /** Any custom item, for its Bedrock twin and icon. */
    public record ItemSpec(String id, String material, ResourceLocation itemModel, @Nullable ResourceLocation model,
                           @Nullable Integer customModelData, String displayName, boolean handheld) {
    }

    /** A custom block: its note block state and its model. */
    public record BlockSpec(String id, NoteBlockState state, ResourceLocation model, String displayName) {
    }

    /** A liquid: its two tripwire states, as block data strings, and their models. */
    public record LiquidSpec(String id, String sourceState, ResourceLocation sourceModel, String flowingState,
                             ResourceLocation flowingModel, String displayName, int glow) {
    }

    /**
     * A piece of furniture standing on barriers or light blocks.
     *
     * @param solid true on a barrier, which is walked into; false on a light block, walked through
     */
    public record FurnitureSpec(String id, ResourceLocation model, Footprint footprint, Placement.Display display,
                                boolean animated, boolean solid, int light, String displayName) {
    }

    /** An emoji's character and its picture, for Bedrock's glyph sheets. */
    public record EmojiSpec(int character, ResourceLocation texture) {
    }

    /**
     * @param previousCarriers what bedrock_carriers.json held
     * @param blockStates      note block states custom blocks hold or held: never carriers
     * @param skipFurniture    3.3: furniture Bedrock players are not shown, by id or {@code namespace:*}
     *                         ({@code bedrock.skip-furniture}), leaving its carriers to the rest
     */
    public record Input(PackFiles pack, List<ItemSpec> items, List<BlockSpec> blocks, List<LiquidSpec> liquids,
                        List<FurnitureSpec> furniture, List<EmojiSpec> emojis, Map<String, Integer> previousCarriers,
                        Set<Integer> blockStates, Set<String> skipFurniture) {

        public Input(PackFiles pack, List<ItemSpec> items, List<BlockSpec> blocks, List<LiquidSpec> liquids,
                     List<FurnitureSpec> furniture, List<EmojiSpec> emojis, Map<String, Integer> previousCarriers,
                     Set<Integer> blockStates) {
            this(pack, items, blocks, liquids, furniture, emojis, previousCarriers, blockStates, Set.of());
        }

        /** Whether {@code id} is left out for Bedrock players. */
        public boolean skipped(String id) {
            return skipFurniture.contains(id) || skipFurniture.contains(id.substring(0, id.indexOf(':') + 1) + "*");
        }
    }

    /**
     * An item as Geyser is told about it.
     *
     * @param byNumber 3.3.2: the Java pack also draws it by its {@code custom_model_data} number, on the
     *                 plain vanilla item - as ItemsAdder's packs do, for the stacks it made and for menus
     *                 set up by number - so Geyser 2.9 and later are told to know it by that number too
     */
    public record BedrockItem(String id, String name, String javaItem, @Nullable Integer customModelData,
                              String itemModel, String icon, String displayName, boolean handheld, boolean byNumber) {

        public BedrockItem(String id, String name, String javaItem, @Nullable Integer customModelData,
                           String itemModel, String icon, String displayName, boolean handheld) {
            this(id, name, javaItem, customModelData, itemModel, icon, displayName, handheld, false);
        }
    }

    /** A texture of a block's, and how Bedrock blends it. */
    public record Material(String texture, String renderMethod) {
    }

    /**
     * A block as Geyser is told about it: the Java state it stands in for, and how it is drawn.
     *
     * @param geometry  {@code minecraft:geometry.full_block} or one of the pack's geometries
     * @param rotationY quarter turns about y, right-handed, in degrees
     */
    public record BedrockBlock(String name, String javaState, String geometry, Map<String, Material> materials,
                               boolean collision, boolean selection, int light, float destroySeconds, int rotationY,
                               String displayName) {
    }

    /**
     * Where a carrier goes: the piece, its quarter turn, and the block of it relative to the one it
     * was placed on.
     *
     * @param support whether a barrier or light block of the piece stands there; if not, it is
     *                shown only where the world has air
     */
    public record Carrier(String furnitureId, int quarter, int dx, int dy, int dz, NoteBlockState state,
                          boolean support) {
    }

    /**
     * @param carrierAssignments what bedrock_carriers.json should hold
     * @param pack               the .mcpack
     * @param packId             a short hash of it, for its file name
     * @param unpictured         items no icon could be drawn for, which show as a question mark
     */
    public record Build(List<BedrockItem> items, List<BedrockBlock> blocks, List<Carrier> carriers,
                        Map<String, Integer> carrierAssignments, byte[] pack, String packId, List<String> problems,
                        List<String> unpictured) {
    }

    private final Input input;
    private final List<String> problems = new ArrayList<>();
    /** Items with no icon, by why: one line each in the log would be thousands for a large import. */
    private final Map<String, List<String>> unpictured = new TreeMap<>();
    private final Map<String, byte[]> files = new TreeMap<>();
    private final Map<String, String> terrain = new TreeMap<>();
    private final Map<String, String> itemTextures = new TreeMap<>();
    private final Map<ResourceLocation, String> terrainKeys = new HashMap<>();
    private final Map<ResourceLocation, BufferedImage> images = new HashMap<>();
    /** 3.3.1: sprites the atlases give another name, by that name; null until first needed. */
    private @Nullable Map<ResourceLocation, ResourceLocation> spriteAliases;
    private final Map<ResourceLocation, JsonObject> models = new HashMap<>();
    /** 3.3.2: by vanilla item, the custom_model_data numbers the Java pack draws on it. */
    private final Map<String, Set<Integer>> numbersDrawn = new HashMap<>();
    private final List<JsonObject> flipbooks = new ArrayList<>();

    private BedrockConverter(Input input) {
        this.input = input;
    }

    public static Build convert(Input input) {
        return new BedrockConverter(input).run();
    }

    private Build run() {
        List<BedrockItem> items = new ArrayList<>();
        for (ItemSpec item : input.items()) {
            BedrockItem converted = item(item);
            if (converted != null) {
                items.add(converted);
            }
        }
        List<BedrockBlock> blocks = new ArrayList<>();
        for (BlockSpec block : input.blocks()) {
            BedrockBlock converted = block(block);
            if (converted != null) {
                blocks.add(converted);
            }
        }
        for (LiquidSpec liquid : input.liquids()) {
            liquid(liquid, blocks);
        }
        unpictured.forEach((reason, ids) -> problems.add(unpicturedLine(reason, ids)));
        List<Carrier> carriers = new ArrayList<>();
        Map<String, Integer> assignments = furniture(blocks, carriers);
        emojis();
        byte[] pack = pack();
        String packId = HexFormat.of().formatHex(sha256(pack)).substring(0, 12);
        List<String> missing = new ArrayList<>();
        unpictured.values().forEach(missing::addAll);
        return new Build(List.copyOf(items), List.copyOf(blocks), List.copyOf(carriers), assignments, pack, packId,
                List.copyOf(problems), List.copyOf(missing));
    }

    // ---------------------------------------------------------------- items

    private @Nullable BedrockItem item(ItemSpec item) {
        String name = name(item.id());
        BufferedImage icon = null;
        ResourceLocation drawn = item.model() != null ? item.model() : item.itemModel();
        if (drawn != null) {
            JavaModel model = model(drawn);
            if (model != null) {
                icon = model.elements().isEmpty()
                        ? IconRenderer.flat(model.layers().stream().map(this::image).filter(java.util.Objects::nonNull).toList())
                        : IconRenderer.render(model, this::image);
            }
        }
        if (icon == null) {
            String reason = drawn == null || modelJsonOrDefinition(drawn) == null ? "its model is not in the pack"
                    : "its textures are not in the pack";
            unpictured.computeIfAbsent(reason, key -> new ArrayList<>()).add(item.id());
            // Registered all the same, under its own name, with a question mark for a picture: left
            // out, a stack of it would be drawn by Geyser 2.8 as the next item down by number.
            if (!itemTextures.containsKey(MISSING_ICON)) {
                files.put("textures/items/ark/" + MISSING_ICON + ".png", png(IconRenderer.missing()));
                itemTextures.put(MISSING_ICON, "textures/items/ark/" + MISSING_ICON);
            }
            return new BedrockItem(item.id(), name, "minecraft:" + item.material().toLowerCase(Locale.ROOT),
                    item.customModelData(), item.itemModel().toString(), MISSING_ICON, item.displayName(), item.handheld(),
                    drawnByNumber(item));
        }
        String path = shortPath("textures/items/ark/", name, ".png");
        files.put(path, png(icon));
        itemTextures.put(name, path.substring(0, path.length() - 4));
        return new BedrockItem(item.id(), name, "minecraft:" + item.material().toLowerCase(Locale.ROOT),
                item.customModelData(), item.itemModel().toString(), name, item.displayName(), item.handheld(),
                drawnByNumber(item));
    }

    /**
     * 3.3.2: whether the Java pack draws this item on the plain vanilla one by its number - the vanilla
     * item's definition dispatches on {@code custom_model_data} and has an entry at exactly that number
     * that is not the vanilla model. A stack ItemsAdder made, or a menu icon set up by number, carries
     * only the number; Geyser 2.9 and later only see it as this item if told so.
     */
    private boolean drawnByNumber(ItemSpec item) {
        Integer number = item.customModelData();
        if (number == null) {
            return false;
        }
        String material = item.material().toLowerCase(Locale.ROOT);
        return numbersDrawn.computeIfAbsent(material, this::numbersDrawn).contains(number);
    }

    /** The numbers {@code assets/minecraft/items/<material>.json} draws a model of its own for. */
    private Set<Integer> numbersDrawn(String material) {
        byte[] bytes = input.pack().read("assets/minecraft/items/" + material + ".json");
        Set<Integer> numbers = new HashSet<>();
        if (bytes == null) {
            return numbers;
        }
        try {
            JsonElement root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            if (root.isJsonObject() && root.getAsJsonObject().has("model")) {
                collectNumbers(root.getAsJsonObject().get("model"), material, numbers);
            }
        } catch (RuntimeException unreadable) {
            // A definition the client would not read either draws nothing by number.
        }
        return numbers;
    }

    /** Every range dispatch on the first custom_model_data float, wherever it sits in the definition. */
    private static void collectNumbers(JsonElement element, String material, Set<Integer> numbers) {
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(child -> collectNumbers(child, material, numbers));
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }
        JsonObject node = element.getAsJsonObject();
        String type = node.has("type") && node.get("type").isJsonPrimitive()
                ? node.get("type").getAsString().replace("minecraft:", "") : "";
        String property = node.has("property") && node.get("property").isJsonPrimitive()
                ? node.get("property").getAsString().replace("minecraft:", "") : "";
        int index = node.has("index") && node.get("index").isJsonPrimitive() ? node.get("index").getAsInt() : 0;
        if (type.equals("range_dispatch") && property.equals("custom_model_data") && index == 0
                && node.has("entries") && node.get("entries").isJsonArray()) {
            for (JsonElement entry : node.getAsJsonArray("entries")) {
                if (!entry.isJsonObject() || !entry.getAsJsonObject().has("threshold")) {
                    continue;
                }
                double threshold = entry.getAsJsonObject().get("threshold").getAsDouble();
                JsonElement model = entry.getAsJsonObject().get("model");
                if (threshold == Math.rint(threshold) && model != null && !vanillaModel(model, material)) {
                    numbers.add((int) threshold);
                }
            }
        }
        for (Map.Entry<String, JsonElement> child : node.entrySet()) {
            if (child.getValue().isJsonObject() || child.getValue().isJsonArray()) {
                collectNumbers(child.getValue(), material, numbers);
            }
        }
    }

    /** An entry that only draws the item as vanilla does - how ItemsAdder ends each of its ranges. */
    private static boolean vanillaModel(JsonElement model, String material) {
        if (!model.isJsonObject()) {
            return false;
        }
        JsonObject node = model.getAsJsonObject();
        String type = node.has("type") && node.get("type").isJsonPrimitive()
                ? node.get("type").getAsString().replace("minecraft:", "") : "";
        String name = node.has("model") && node.get("model").isJsonPrimitive() ? node.get("model").getAsString() : "";
        return type.equals("model") && (name.equals("minecraft:item/" + material) || name.equals("item/" + material)
                || name.equals("minecraft:block/" + material) || name.equals("block/" + material));
    }

    // ---------------------------------------------------------------- blocks and liquids

    private @Nullable BedrockBlock block(BlockSpec block) {
        JavaModel model = model(block.model());
        String name = name(block.id());
        if (model == null || model.elements().isEmpty()) {
            problems.add(block.id() + ": its model could not be read for Bedrock - it shows as a note block there");
            return null;
        }
        Map<String, Material> materials = new LinkedHashMap<>();
        String geometry;
        if (fullCube(model)) {
            geometry = FULL_BLOCK;
            JavaModel.Element cube = model.elements().get(0);
            for (Map.Entry<JavaModel.Face, JavaModel.FaceTexture> face : cube.faces().entrySet()) {
                materials.put(face.getKey().key(), material(face.getValue().texture(), false));
            }
            materials.put("*", materials.getOrDefault("north", materials.values().iterator().next()));
        } else {
            List<BedrockCube> cubes = blockCubes(model);
            if (!BedrockGeometry.fits(cubes)) {
                problems.add(block.id() + ": its model reaches more than 7 pixels outside its block, which Bedrock"
                        + " does not draw - it shows as a note block there");
                return null;
            }
            geometry = geometry(name, cubes, materials, false);
        }
        return new BedrockBlock(name, block.state().asBlockData(), geometry, materials, true, true, 0,
                NOTE_BLOCK_SECONDS, 0, block.displayName());
    }

    private void liquid(LiquidSpec liquid, List<BedrockBlock> blocks) {
        for (boolean source : new boolean[]{true, false}) {
            JavaModel model = model(source ? liquid.sourceModel() : liquid.flowingModel());
            String name = name(liquid.id()) + (source ? "_source" : "_flowing");
            if (model == null || model.elements().isEmpty()) {
                problems.add(liquid.id() + ": its " + (source ? "source" : "flowing") + " model could not be read for Bedrock");
                continue;
            }
            Map<String, Material> materials = new LinkedHashMap<>();
            String geometry = geometry(name, blockCubes(model), materials, true);
            blocks.add(new BedrockBlock(name, source ? liquid.sourceState() : liquid.flowingState(), geometry, materials,
                    false, true, liquid.glow(), 0, 0, liquid.displayName()));
        }
    }

    /** A block model's cubes in a Bedrock block's frame: no turn, only the origin moved to the floor's middle. */
    private static List<BedrockCube> blockCubes(JavaModel model) {
        List<BedrockCube> cubes = new ArrayList<>();
        for (JavaModel.Element element : model.elements()) {
            Map<JavaModel.Face, BedrockCube.Face> faces = new java.util.EnumMap<>(JavaModel.Face.class);
            element.faces().forEach((face, texture) -> faces.put(face, new BedrockCube.Face(texture.uv(), texture.texture())));
            float[] min = {element.from()[0] - 8, element.from()[1], element.from()[2] - 8};
            float[] max = {element.to()[0] - 8, element.to()[1], element.to()[2] - 8};
            float[] pivot = {element.origin()[0] - 8, element.origin()[1], element.origin()[2] - 8};
            cubes.add(new BedrockCube(min, max, faces, element.rotated() ? element.axis() : null, element.angle(), pivot));
        }
        return cubes;
    }

    private static boolean fullCube(JavaModel model) {
        if (model.elements().size() != 1) {
            return false;
        }
        JavaModel.Element cube = model.elements().get(0);
        for (int i = 0; i < 3; i++) {
            if (cube.from()[i] != 0 || cube.to()[i] != 16) {
                return false;
            }
        }
        if (cube.faces().size() != 6 || cube.rotated()) {
            return false;
        }
        for (Map.Entry<JavaModel.Face, JavaModel.FaceTexture> face : cube.faces().entrySet()) {
            float[] uv = face.getValue().uv();
            if (uv[0] != 0 || uv[1] != 0 || uv[2] != 16 || uv[3] != 16 || face.getValue().rotation() != 0) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- furniture

    private Map<String, Integer> furniture(List<BedrockBlock> blocks, List<Carrier> carriers) {
        Map<String, List<Object[]>> wanted = new LinkedHashMap<>();
        Map<String, List<String>> keysByPiece = new TreeMap<>();
        // Said only of pieces that reach Bedrock players: of one left out, nothing is drawn anyway.
        Map<String, List<String>> cellWarnings = new LinkedHashMap<>();
        for (FurnitureSpec piece : input.furniture()) {
            if (input.skipped(piece.id())) {
                continue;
            }
            JavaModel model = model(piece.model());
            if (model == null || model.elements().isEmpty()) {
                problems.add(piece.id() + ": its model could not be read for Bedrock - Bedrock players see nothing there");
                continue;
            }
            FurnitureLayout.Layout layout = FurnitureLayout.lay(model, piece.footprint(), piece.display(), piece.animated());
            layout.notes().forEach(note -> problems.add(piece.id() + " on Bedrock: " + note));
            Set<String> support = new HashSet<>();
            for (int[] offset : piece.footprint().offsets(0)) {
                support.add(offset[0] + "," + offset[1] + "," + offset[2]);
            }
            for (Map.Entry<FurnitureLayout.Cell, List<BedrockCube>> cell : new TreeMap<>(layout.cells()).entrySet()) {
                if (!BedrockGeometry.fits(cell.getValue())) {
                    cellWarnings.computeIfAbsent(piece.id(), ignored -> new ArrayList<>()).add(cell.getKey().key());
                }
                Map<String, Material> materials = new LinkedHashMap<>();
                String base = name(piece.id()) + "_" + cellName(cell.getKey());
                String geometry = geometry(base, cell.getValue(), materials, false);
                boolean onSupport = support.contains(cell.getKey().key());
                for (int quarter = 0; quarter < 4; quarter++) {
                    String key = piece.id() + "|" + cell.getKey().key() + "|" + quarter;
                    wanted.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new Object[]{piece, cell.getKey(),
                            quarter, geometry, materials, onSupport, base});
                    keysByPiece.computeIfAbsent(piece.id(), ignored -> new ArrayList<>()).add(key);
                }
            }
        }
        // Carriers from the top of the custom blocks' range down, never a state a block holds.
        Map<String, Integer> mirroredPrevious = new HashMap<>();
        input.previousCarriers().forEach((key, state) -> mirroredPrevious.put(key, NoteBlockState.CAPACITY - state));
        Set<Integer> reserved = new HashSet<>();
        input.blockStates().forEach(state -> reserved.add(NoteBlockState.CAPACITY - state));
        CarrierPlan plan = planCarriers(keysByPiece, mirroredPrevious, reserved, 1, NoteBlockState.CAPACITY - 2);
        cellWarnings.forEach((piece, cells) -> {
            if (!plan.leftOut().containsKey(piece)) {
                problems.add(piece + " on Bedrock: a turned cube reaches more than 7 pixels out of its "
                        + (cells.size() == 1 ? "block " + cells.get(0) : cells.size() + " blocks " + String.join(" ", cells))
                        + " - " + (cells.size() == 1 ? "that block" : "those blocks") + " of it may not be drawn");
            }
        });
        if (!plan.leftOut().isEmpty()) {
            int total = plan.leftOut().values().stream().mapToInt(Integer::intValue).sum();
            List<String> named = new ArrayList<>();
            plan.leftOut().entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue())
                    .forEach(entry -> named.add(entry.getKey() + " (" + entry.getValue() + ")"));
            problems.add("Bedrock: " + plan.leftOut().size() + " piece(s) of furniture left out for Bedrock players - "
                    + "not enough note block states for their carriers (one per block of a piece and per quarter turn; "
                    + plan.assignments().size() + " in use, " + total + " more needed). The smallest pieces go first. "
                    + "Left out, with what each needs: " + String.join(", ", named) + ". List pieces under "
                    + "bedrock.skip-furniture in config.yml to leave those out instead.");
        }
        Map<String, Integer> assignments = new TreeMap<>();
        plan.assignments().forEach((key, mirrored) -> assignments.put(key, NoteBlockState.CAPACITY - mirrored));
        for (Map.Entry<String, List<Object[]>> entry : wanted.entrySet()) {
            Integer mirrored = plan.assignments().get(entry.getKey());
            if (mirrored == null) {
                continue;
            }
            Object[] spec = entry.getValue().get(0);
            FurnitureSpec piece = (FurnitureSpec) spec[0];
            FurnitureLayout.Cell cell = (FurnitureLayout.Cell) spec[1];
            int quarter = (int) spec[2];
            @SuppressWarnings("unchecked")
            Map<String, Material> materials = (Map<String, Material>) spec[4];
            boolean onSupport = (boolean) spec[5];
            NoteBlockState state = NoteBlockState.fromIndex(NoteBlockState.CAPACITY - mirrored);
            FurnitureLayout.Cell placed = FurnitureLayout.turned(cell, piece.footprint(), quarter);
            carriers.add(new Carrier(piece.id(), quarter, placed.dx(), placed.dy(), placed.dz(), state, onSupport));
            blocks.add(new BedrockBlock(spec[6] + "_r" + quarter, state.asBlockData(), (String) spec[3], materials,
                    onSupport && piece.solid(), onSupport, onSupport ? piece.light() : 0, 0,
                    Math.floorMod(-quarter * 90 + 180, 360) - 180, piece.displayName()));
        }
        return assignments;
    }

    /**
     * Which carriers go to which piece.
     *
     * @param assignments every carrier key's number
     * @param leftOut     pieces given none, with how many they needed
     */
    record CarrierPlan(Map<String, Integer> assignments, Map<String, Integer> leftOut) {
    }

    /**
     * 3.3: hands out the numbers {@code first} to {@code last}, less {@code reserved}, to whole pieces:
     * a piece gets a carrier for every block and quarter turn or none at all, since half a piece is
     * worse than none. The smallest pieces go first, so the most pieces are shown when there are not
     * enough for all; a key keeps the number it had before when it can. A carrier is only ever a
     * block change sent to a Bedrock client, and Geyser learns them all anew as it starts: nothing in
     * the world holds one, so a piece no longer defined frees its numbers at once.
     *
     * @param keysByPiece every piece's carrier keys
     * @param previous    the number each key had before
     */
    static CarrierPlan planCarriers(Map<String, List<String>> keysByPiece, Map<String, Integer> previous,
                                    Set<Integer> reserved, int first, int last) {
        java.util.TreeSet<Integer> free = new java.util.TreeSet<>();
        for (int number = first; number <= last; number++) {
            if (!reserved.contains(number)) {
                free.add(number);
            }
        }
        // What each key had, where it still can: a free number, and not another key's too.
        Set<String> wanted = new HashSet<>();
        keysByPiece.values().forEach(wanted::addAll);
        Map<String, Integer> claim = new HashMap<>();
        Map<Integer, String> claimedBy = new HashMap<>();
        for (Map.Entry<String, Integer> entry : new TreeMap<>(previous).entrySet()) {
            Integer number = entry.getValue();
            if (wanted.contains(entry.getKey()) && number != null && free.contains(number) && !claimedBy.containsKey(number)) {
                claim.put(entry.getKey(), number);
                claimedBy.put(number, entry.getKey());
            }
        }
        List<String> order = new ArrayList<>(keysByPiece.keySet());
        order.sort(java.util.Comparator.comparingInt((String piece) -> keysByPiece.get(piece).size())
                .thenComparing(java.util.Comparator.naturalOrder()));
        Map<String, Integer> assignments = new TreeMap<>();
        Map<String, Integer> leftOut = new TreeMap<>();
        for (String piece : order) {
            List<String> keys = new ArrayList<>(new java.util.TreeSet<>(keysByPiece.get(piece)));
            List<String> own = keys.stream().filter(key -> claim.containsKey(key) && free.contains(claim.get(key))).toList();
            int needed = keys.size() - own.size();
            if (needed > free.size() - own.size()) {
                leftOut.put(piece, keys.size());
                own.forEach(key -> claimedBy.remove(claim.get(key)));
                continue;
            }
            for (String key : own) {
                int number = claim.get(key);
                assignments.put(key, number);
                free.remove(number);
                claimedBy.remove(number);
            }
            for (String key : keys) {
                if (assignments.containsKey(key)) {
                    continue;
                }
                // A number nobody had first; else one a later, larger piece had.
                Integer number = free.stream().filter(n -> !claimedBy.containsKey(n)).findFirst().orElse(free.first());
                String owner = claimedBy.remove(number);
                if (owner != null) {
                    claim.remove(owner);
                }
                assignments.put(key, number);
                free.remove(number);
            }
        }
        return new CarrierPlan(assignments, leftOut);
    }

    private static String cellName(FurnitureLayout.Cell cell) {
        return ("c" + cell.dx() + "_" + cell.dy() + "_" + cell.dz()).replace('-', 'm');
    }

    // ---------------------------------------------------------------- geometry and textures

    /** Writes the geometry file and fills {@code materials}; returns its identifier. */
    private String geometry(String name, List<BedrockCube> cubes, Map<String, Material> materials, boolean liquid) {
        String identifier = "geometry.arkcontent." + name;
        BedrockGeometry.Built built = BedrockGeometry.build(identifier, cubes);
        files.put(shortPath("models/blocks/ark/", name, ".geo.json"), GSON.toJson(built.json()).getBytes(StandardCharsets.UTF_8));
        String first = null;
        for (Map.Entry<String, ResourceLocation> material : built.materials().entrySet()) {
            Material made = material(material.getValue(), liquid);
            materials.put(material.getKey(), made);
            if (first == null) {
                first = material.getKey();
            }
        }
        if (first != null) {
            materials.put("*", materials.get(first));
        }
        return identifier;
    }

    /** A texture in the terrain atlas, copied in once, and how it should be blended. */
    private Material material(ResourceLocation texture, boolean liquid) {
        String key = terrainKeys.computeIfAbsent(texture, location -> {
            String name = "ark_" + sanitize(location.namespace() + "_" + location.path());
            BufferedImage image = image(location);
            String path = shortPath("textures/blocks/ark/", sanitize(location.namespace()) + "/" + location.path(), ".png");
            path = path.substring(0, path.length() - ".png".length());
            if (image == null) {
                problems.add("Bedrock: texture " + location + " is not in the pack");
            } else {
                ResourceLocation file = textureFile(location);
                byte[] mcmeta = input.pack().read("assets/" + file.namespace() + "/textures/" + file.path()
                        + ".png.mcmeta");
                if (mcmeta != null && image.getHeight() > image.getWidth()) {
                    files.put(path + ".png", png(image));
                    flipbook(path, name, mcmeta);
                } else {
                    files.put(path + ".png", png(image));
                }
            }
            terrain.put(name, path);
            return name;
        });
        BufferedImage image = image(texture);
        String method = liquid ? "blend" : image == null ? "opaque" : blending(IconRenderer.frame(image));
        return new Material(key, method);
    }

    private void flipbook(String path, String key, byte[] mcmeta) {
        int frametime = 1;
        try {
            JsonObject animation = JsonParser.parseString(new String(mcmeta, StandardCharsets.UTF_8)).getAsJsonObject()
                    .getAsJsonObject("animation");
            if (animation != null && animation.has("frametime")) {
                frametime = Math.max(1, animation.get("frametime").getAsInt());
            }
        } catch (RuntimeException ignored) {
            // A broken .mcmeta: one tick a frame.
        }
        JsonObject flipbook = new JsonObject();
        flipbook.addProperty("flipbook_texture", path);
        flipbook.addProperty("atlas_tile", key);
        flipbook.addProperty("ticks_per_frame", frametime);
        flipbooks.add(flipbook);
    }

    /** {@code opaque} with no transparency, {@code alpha_test} with only see-through pixels, {@code blend} with half see-through ones. */
    static String blending(BufferedImage image) {
        boolean clear = false;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int alpha = image.getRGB(x, y) >>> 24;
                if (alpha > 0 && alpha < 255) {
                    return "blend";
                }
                clear |= alpha == 0;
            }
        }
        return clear ? "alpha_test" : "opaque";
    }

    /**
     * The model at {@code location} - or, when there is no model there, the one the item definition
     * at {@code location} draws ({@code assets/<namespace>/items/<path>.json}), as an item made with
     * {@code resource.item-model} names it.
     */
    private @Nullable JavaModel model(ResourceLocation location) {
        JavaModel model = JavaModel.resolve(location, this::modelJson);
        if (model != null || modelJson(location) != null) {
            return model;
        }
        ResourceLocation defined = definedModel(location);
        return defined == null ? null : JavaModel.resolve(defined, this::modelJson);
    }

    private @Nullable JsonObject modelJsonOrDefinition(ResourceLocation location) {
        JsonObject json = modelJson(location);
        if (json != null) {
            return json;
        }
        ResourceLocation defined = definedModel(location);
        return defined == null ? null : modelJson(defined);
    }

    /**
     * The order Geyser 2.8 is to be given items in. It knows a custom item by its custom_model_data
     * number: it reverses the order items were registered in, then gives a stack the first whose
     * number is at most the stack's - Java's old override rule. Registered lowest number first,
     * the first it finds for a stack is the stack's own. Items with no number go last; they are
     * not registered that way.
     */
    public static List<BedrockItem> lowestNumberFirst(List<BedrockItem> items) {
        return GeyserExport.lowestNumberFirst(items);
    }

    /** One line for many items: how many, from which packs, a few of them. */
    static String unpicturedLine(String reason, List<String> ids) {
        Map<String, Integer> byNamespace = new TreeMap<>();
        ids.forEach(id -> byNamespace.merge(id.substring(0, Math.max(0, id.indexOf(':'))), 1, Integer::sum));
        List<String> largest = byNamespace.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(6)
                .map(entry -> entry.getKey() + " " + entry.getValue())
                .toList();
        return "Bedrock: " + ids.size() + " item(s) with no picture - " + reason + "; Bedrock players see them as a"
                + " question mark, under their own names (" + String.join(", ", largest) + (byNamespace.size() > 6 ? ", ..." : "")
                + "; e.g. " + String.join(", ", ids.subList(0, Math.min(3, ids.size())))
                + " - every one in items-without-picture.txt, beside the .mcpack)";
    }

    /** The model an item definition draws: its plain model, or the fallback or first case of a switch. */
    private @Nullable ResourceLocation definedModel(ResourceLocation definition) {
        byte[] bytes = input.pack().read("assets/" + definition.namespace() + "/items/" + definition.path() + ".json");
        if (bytes == null) {
            return null;
        }
        try {
            JsonElement root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            String found = root.isJsonObject() ? ItemModels.firstModel(root.getAsJsonObject().get("model")) : null;
            return found == null ? null : ResourceLocation.parse(found, "minecraft");
        } catch (RuntimeException broken) {
            problems.add("Bedrock: the item definition " + definition + " could not be read (" + broken.getMessage() + ")");
            return null;
        }
    }

    private @Nullable JsonObject modelJson(ResourceLocation location) {
        return models.computeIfAbsent(location, key -> {
            byte[] bytes = input.pack().read("assets/" + key.namespace() + "/models/" + key.path() + ".json");
            if (bytes == null) {
                return null;
            }
            try {
                JsonElement json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
                return json.isJsonObject() ? json.getAsJsonObject() : null;
            } catch (RuntimeException broken) {
                problems.add("Bedrock: " + key + " is not valid JSON");
                return null;
            }
        });
    }

    /**
     * 3.3.1: the texture file a model's texture name stands for. Usually the same name; but an atlas
     * can give a sprite a name of its own - ItemsAdder's generated packs name every texture so
     * ({@code "ia:143"} for {@code arkespadas:item/mitica_eclipse}) - and the models use that name.
     */
    private ResourceLocation textureFile(ResourceLocation sprite) {
        if (input.pack().read("assets/" + sprite.namespace() + "/textures/" + sprite.path() + ".png") != null) {
            return sprite;
        }
        if (spriteAliases == null) {
            spriteAliases = spriteAliases(input.pack());
        }
        return spriteAliases.getOrDefault(sprite, sprite);
    }

    /** Every renamed sprite of the blocks and items atlases: a {@code single} source with a {@code sprite}. */
    static Map<ResourceLocation, ResourceLocation> spriteAliases(PackFiles pack) {
        Map<ResourceLocation, ResourceLocation> aliases = new HashMap<>();
        for (String atlas : List.of("blocks", "items")) {
            byte[] bytes = pack.read("assets/minecraft/atlases/" + atlas + ".json");
            if (bytes == null) {
                continue;
            }
            try {
                JsonElement sources = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8))
                        .getAsJsonObject().get("sources");
                if (sources == null || !sources.isJsonArray()) {
                    continue;
                }
                for (JsonElement entry : sources.getAsJsonArray()) {
                    if (!entry.isJsonObject()) {
                        continue;
                    }
                    JsonObject source = entry.getAsJsonObject();
                    String type = source.has("type") ? source.get("type").getAsString().replace("minecraft:", "") : "";
                    if (type.equals("single") && source.has("resource") && source.has("sprite")) {
                        try {
                            aliases.putIfAbsent(ResourceLocation.parse(source.get("sprite").getAsString(), "minecraft"),
                                    ResourceLocation.parse(source.get("resource").getAsString(), "minecraft"));
                        } catch (RuntimeException badName) {
                            // One name the client would not take either: the rest still count.
                        }
                    }
                }
            } catch (RuntimeException unreadable) {
                // An atlas that is not JSON names nothing: the textures keep their own names.
            }
        }
        return aliases;
    }

    private @Nullable BufferedImage image(ResourceLocation location) {
        if (images.containsKey(location)) {
            return images.get(location);
        }
        BufferedImage image = null;
        ResourceLocation file = textureFile(location);
        byte[] bytes = input.pack().read("assets/" + file.namespace() + "/textures/" + file.path() + ".png");
        if (bytes != null) {
            try {
                image = PngReader.read(bytes);
            } catch (IOException broken) {
                problems.add("Bedrock: " + location + " could not be read as a PNG");
            }
        }
        images.put(location, image);
        return image;
    }

    // ---------------------------------------------------------------- emojis

    /**
     * Bedrock draws a private-use character from {@code font/glyph_XX.png}: 256 characters a sheet,
     * 16 across, each cell the sheet's width over 16. Each emoji's picture goes in its cell, so chat
     * shows the emoji on Bedrock too.
     */
    private void emojis() {
        Map<Integer, List<EmojiSpec>> sheets = new TreeMap<>();
        for (EmojiSpec emoji : input.emojis()) {
            if (emoji.character() >= 0xE000 && emoji.character() <= 0xF8FF) {
                sheets.computeIfAbsent(emoji.character() >> 8, ignored -> new ArrayList<>()).add(emoji);
            }
        }
        for (Map.Entry<Integer, List<EmojiSpec>> sheet : sheets.entrySet()) {
            int cell = 16;
            for (EmojiSpec emoji : sheet.getValue()) {
                BufferedImage image = image(emoji.texture());
                if (image != null) {
                    cell = Math.max(cell, Math.min(64, IconRenderer.frame(image).getWidth()));
                }
            }
            BufferedImage glyphs = new BufferedImage(cell * 16, cell * 16, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D graphics = glyphs.createGraphics();
            for (EmojiSpec emoji : sheet.getValue()) {
                BufferedImage image = image(emoji.texture());
                if (image == null) {
                    continue;
                }
                int low = emoji.character() & 0xff;
                graphics.drawImage(IconRenderer.frame(image), (low & 15) * cell, (low >> 4) * cell, cell, cell, null);
            }
            graphics.dispose();
            files.put(String.format(Locale.ROOT, "font/glyph_%02X.png", sheet.getKey()), png(glyphs));
        }
    }

    // ---------------------------------------------------------------- the pack

    private byte[] pack() {
        JsonObject terrainAtlas = atlas("atlas.terrain", terrain);
        terrainAtlas.addProperty("padding", 8);
        terrainAtlas.addProperty("num_mip_levels", 4);
        files.put("textures/terrain_texture.json", GSON.toJson(terrainAtlas).getBytes(StandardCharsets.UTF_8));
        files.put("textures/item_texture.json", GSON.toJson(atlas("atlas.items", itemTextures)).getBytes(StandardCharsets.UTF_8));
        if (!flipbooks.isEmpty()) {
            JsonArray array = new JsonArray();
            flipbooks.forEach(array::add);
            files.put("textures/flipbook_textures.json", GSON.toJson(array).getBytes(StandardCharsets.UTF_8));
        }
        // The pack's identity follows its content: a client that has an older one downloads this.
        MessageDigest digest = digest();
        files.forEach((path, bytes) -> {
            digest.update(path.getBytes(StandardCharsets.UTF_8));
            digest.update(bytes);
        });
        byte[] hash = digest.digest();
        UUID header = UUID.nameUUIDFromBytes(("arkcontent-bedrock-pack:" + HexFormat.of().formatHex(hash))
                .getBytes(StandardCharsets.UTF_8));
        UUID module = UUID.nameUUIDFromBytes(("arkcontent-bedrock-resources:" + HexFormat.of().formatHex(hash))
                .getBytes(StandardCharsets.UTF_8));
        JsonObject manifest = new JsonObject();
        manifest.addProperty("format_version", 2);
        JsonObject head = new JsonObject();
        head.addProperty("name", "ArkcronistContent");
        head.addProperty("description", "Custom items, blocks and furniture, made for Bedrock by ArkcronistContent");
        head.addProperty("uuid", header.toString());
        head.add("version", version());
        head.add("min_engine_version", version(1, 21, 0));
        manifest.add("header", head);
        JsonObject resources = new JsonObject();
        resources.addProperty("type", "resources");
        resources.addProperty("uuid", module.toString());
        resources.add("version", version());
        JsonArray modules = new JsonArray();
        modules.add(resources);
        manifest.add("modules", modules);
        files.put("manifest.json", GSON.toJson(manifest).getBytes(StandardCharsets.UTF_8));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                ZipEntry entry = new ZipEntry(file.getKey());
                entry.setTime(0);
                zip.putNextEntry(entry);
                zip.write(file.getValue());
                zip.closeEntry();
            }
        } catch (IOException impossible) {
            throw new UncheckedIOException(impossible);
        }
        return bytes.toByteArray();
    }

    private static JsonObject atlas(String name, Map<String, String> entries) {
        JsonObject atlas = new JsonObject();
        atlas.addProperty("resource_pack_name", "arkcontent");
        atlas.addProperty("texture_name", name);
        JsonObject data = new JsonObject();
        entries.forEach((key, path) -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("textures", path);
            data.add(key, entry);
        });
        atlas.add("texture_data", data);
        return atlas;
    }

    private static JsonArray version(int... parts) {
        JsonArray array = new JsonArray();
        for (int part : parts.length == 0 ? new int[]{3, 0, 0} : parts) {
            array.add(part);
        }
        return array;
    }

    // ---------------------------------------------------------------- names

    /** A Bedrock name for a custom item or block: lower case letters, digits and underscores. */
    public static String name(String id) {
        return "ark_" + sanitize(id);
    }

    static String sanitize(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (char c : text.toLowerCase(Locale.ROOT).toCharArray()) {
            out.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '/' ? c : '_');
        }
        return out.toString().replace('/', '_');
    }

    private static byte[] png(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException impossible) {
            throw new UncheckedIOException(impossible);
        }
    }

    /**
     * {@code folder + name + extension}, or - when that would be 80 characters or more, which some
     * Bedrock platforms cannot open - {@code folder} and a hash of the name in its place.
     */
    static String shortPath(String folder, String name, String extension) {
        String path = folder + name + extension;
        if (path.length() < MAX_PATH) {
            return path;
        }
        String hash = HexFormat.of().formatHex(sha256(name.getBytes(StandardCharsets.UTF_8))).substring(0, 20);
        return folder + hash + extension;
    }

    private static byte[] sha256(byte[] bytes) {
        return digest().digest(bytes);
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
