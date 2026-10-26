package com.arkcronist.content.core.geyser;

import com.arkcronist.content.core.block.BlockShape;
import com.arkcronist.content.core.block.NoteBlockState;
import com.arkcronist.content.core.block.ShapedModels;
import com.arkcronist.content.core.block.ShapedStates;
import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.definition.ResourceLocation;
import com.arkcronist.content.core.furniture.Footprint;
import com.arkcronist.content.core.pack.BlockStates;
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
    /** 5.3: the same for copper's hardness, 3: what shaped blocks are drawn through. */
    private static final float COPPER_SECONDS = 4.5f;
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
                           @Nullable Integer customModelData, String displayName, boolean handheld, Uses uses,
                           @Nullable Worn worn) {

        public ItemSpec(String id, String material, ResourceLocation itemModel, @Nullable ResourceLocation model,
                        @Nullable Integer customModelData, String displayName, boolean handheld) {
            this(id, material, itemModel, model, customModelData, displayName, handheld, Uses.NONE);
        }

        public ItemSpec(String id, String material, ResourceLocation itemModel, @Nullable ResourceLocation model,
                        @Nullable Integer customModelData, String displayName, boolean handheld, Uses uses) {
            this(id, material, itemModel, model, customModelData, displayName, handheld, uses, null);
        }
    }

    /**
     * 3.4: how a piece of armour looks worn, for the Bedrock pack's attachable - without one a Bedrock
     * client draws nothing on a player wearing a custom item.
     *
     * @param slot  head, chest, legs or feet
     * @param asset the Java equipment asset whose layer is drawn over the body, as vanilla armour is;
     *              null for none
     * @param model a 3D model drawn on the head instead; null for none
     * @param vanilla for a piece worn as its material, the Bedrock pack's own texture of that armour
     *                ({@code textures/models/armor/iron_1}); null otherwise
     */
    public record Worn(String slot, @Nullable ResourceLocation asset, @Nullable ResourceLocation model,
                       @Nullable String vanilla) {

        /**
         * A vanilla armour piece by its material - {@code IRON_CHESTPLATE} - worn as that; null for
         * any other material, and for leather, whose colour is the stack's own.
         */
        public static @Nullable Worn ofMaterial(String material) {
            String name = material.toLowerCase(Locale.ROOT);
            int split = name.lastIndexOf('_');
            if (split < 0) {
                return null;
            }
            String slot = switch (name.substring(split + 1)) {
                case "helmet" -> "head";
                case "chestplate" -> "chest";
                case "leggings" -> "legs";
                case "boots" -> "feet";
                default -> null;
            };
            String texture = switch (name.substring(0, split)) {
                case "chainmail" -> "chain";
                case "golden" -> "gold";
                case "iron", "diamond", "netherite", "turtle" -> name.substring(0, split);
                default -> null;
            };
            if (slot == null || texture == null) {
                return null;
            }
            return new Worn(slot, null, null, "textures/models/armor/" + texture + (slot.equals("legs") ? "_2" : "_1"));
        }
    }

    /** 3.4: the namespaces Geyser gives the custom items: by item model (2.9+), by number (2.9+), and 2.8's. */
    public static final String ITEM_NAMESPACE = "arkcontent";
    public static final String BY_NUMBER_NAMESPACE = "arkcontent_cmd";
    public static final String LEGACY_NAMESPACE = "geyser_custom";

    /**
     * 3.4: how an item is used, for Geyser to tell Bedrock clients - which otherwise treat a custom
     * item as the plain one underneath: drunk (a canteen), one to a slot, with a damage bar, worn.
     *
     * @param drinkSeconds how long a drink of it takes; 0 for an item not drunk
     * @param maxStack     how many go in a slot; 0 for the plain item's
     * @param maxDamage    its damage bar's length; 0 for none
     * @param slot         where it is worn: head, chest, legs or feet; null for an item not worn
     */
    public record Uses(float drinkSeconds, int maxStack, int maxDamage, @Nullable String slot) {

        public static final Uses NONE = new Uses(0, 0, 0, null);

        /** As one field: {@code drink=1.2;stack=1;damage=6;slot=chest}, or empty. */
        public String encode() {
            StringBuilder out = new StringBuilder();
            if (drinkSeconds > 0) {
                out.append("drink=").append(drinkSeconds);
            }
            if (maxStack > 0) {
                out.append(out.isEmpty() ? "" : ";").append("stack=").append(maxStack);
            }
            if (maxDamage > 0) {
                out.append(out.isEmpty() ? "" : ";").append("damage=").append(maxDamage);
            }
            if (slot != null) {
                out.append(out.isEmpty() ? "" : ";").append("slot=").append(slot);
            }
            return out.toString();
        }

        /** Read back from {@link #encode}; what it does not know is left out. */
        public static Uses decode(String field) {
            float drink = 0;
            int stack = 0;
            int damage = 0;
            String slot = null;
            for (String part : field.split(";")) {
                int equals = part.indexOf('=');
                if (equals < 0) {
                    continue;
                }
                String value = part.substring(equals + 1);
                switch (part.substring(0, equals)) {
                    case "drink" -> drink = Float.parseFloat(value);
                    case "stack" -> stack = Integer.parseInt(value);
                    case "damage" -> damage = Integer.parseInt(value);
                    case "slot" -> slot = value;
                    default -> {
                    }
                }
            }
            return new Uses(drink, stack, damage, slot);
        }
    }

    /** A custom block: its note block state and its model. */
    public record BlockSpec(String id, NoteBlockState state, ResourceLocation model, String displayName,
                            float destroySeconds) {

        public BlockSpec(String id, NoteBlockState state, ResourceLocation model, String displayName) {
            this(id, state, model, displayName, NOTE_BLOCK_SECONDS);
        }
    }

    /**
     * 5.3: a shaped block - slab, stairs, transparent block, door, trapdoor - drawn through a vanilla
     * block of its shape.
     *
     * @param base   that block's id, without namespace: {@code waxed_exposed_cut_copper_slab}
     * @param models the models its block state names, by suffix ({@link ShapedModels#parts})
     */
    public record ShapedSpec(String id, BlockShape shape, String base, Map<String, ResourceLocation> models,
                             String displayName, float destroySeconds) {

        public ShapedSpec(String id, BlockShape shape, String base, Map<String, ResourceLocation> models,
                          String displayName) {
            this(id, shape, base, models, displayName, COPPER_SECONDS);
        }
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
                        Set<Integer> blockStates, Set<String> skipFurniture, List<ShapedSpec> shaped) {

        public Input(PackFiles pack, List<ItemSpec> items, List<BlockSpec> blocks, List<LiquidSpec> liquids,
                     List<FurnitureSpec> furniture, List<EmojiSpec> emojis, Map<String, Integer> previousCarriers,
                     Set<Integer> blockStates, Set<String> skipFurniture) {
            this(pack, items, blocks, liquids, furniture, emojis, previousCarriers, blockStates, skipFurniture, List.of());
        }

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
                              String itemModel, String icon, String displayName, boolean handheld, boolean byNumber,
                              Uses uses) {

        public BedrockItem(String id, String name, String javaItem, @Nullable Integer customModelData,
                           String itemModel, String icon, String displayName, boolean handheld) {
            this(id, name, javaItem, customModelData, itemModel, icon, displayName, handheld, false);
        }

        public BedrockItem(String id, String name, String javaItem, @Nullable Integer customModelData,
                           String itemModel, String icon, String displayName, boolean handheld, boolean byNumber) {
            this(id, name, javaItem, customModelData, itemModel, icon, displayName, handheld, byNumber, Uses.NONE);
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
                               String displayName, List<String> moreStates, List<float[]> boxes) {

        /**
         * @param moreStates 5.3: other Java states drawn the same way - a slab's waterlogged twin, the
         *                   stairs states vanilla draws with one model and turn
         * @param boxes      5.3: what is collided with and selected, each {x, y, z, width, height,
         *                   depth} in Bedrock's block frame before {@code rotationY}; empty for the
         *                   whole block, or none at all without {@code collision}
         */
        public BedrockBlock {
            moreStates = List.copyOf(moreStates);
            boxes = List.copyOf(boxes);
        }

        public BedrockBlock(String name, String javaState, String geometry, Map<String, Material> materials,
                            boolean collision, boolean selection, int light, float destroySeconds, int rotationY,
                            String displayName) {
            this(name, javaState, geometry, materials, collision, selection, light, destroySeconds, rotationY,
                    displayName, List.of(), List.of());
        }

        /** Every Java state it stands in for. */
        public List<String> javaStates() {
            List<String> all = new ArrayList<>();
            all.add(javaState);
            all.addAll(moreStates);
            return all;
        }
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
     * @param wornPieces         3.4: armour pieces the pack draws on a Bedrock player wearing them
     */
    public record Build(List<BedrockItem> items, List<BedrockBlock> blocks, List<Carrier> carriers,
                        Map<String, Integer> carrierAssignments, byte[] pack, String packId, List<String> problems,
                        List<String> unpictured, Map<Integer, String> glyphText, int wornPieces) {

        public Build(List<BedrockItem> items, List<BedrockBlock> blocks, List<Carrier> carriers,
                     Map<String, Integer> carrierAssignments, byte[] pack, String packId, List<String> problems,
                     List<String> unpictured, Map<Integer, String> glyphText) {
            this(items, blocks, carriers, carrierAssignments, pack, packId, problems, unpictured, glyphText, 0);
        }

        public Build(List<BedrockItem> items, List<BedrockBlock> blocks, List<Carrier> carriers,
                     Map<String, Integer> carrierAssignments, byte[] pack, String packId, List<String> problems,
                     List<String> unpictured) {
            this(items, blocks, carriers, carrierAssignments, pack, packId, problems, unpictured, Map.of());
        }
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
    /** 3.4: armour pieces given an attachable. */
    private int wornPieces;
    /** 3.4: armour pieces given none, by why. */
    private final Map<String, List<String>> unworn = new TreeMap<>();
    /** 3.4: the overlay folders a 1.21.8 client reads, topmost first; null until first needed. */
    private @Nullable List<String> clientOverlays;
    /** 3.3.3: a character too wide for one Bedrock glyph, and the glyphs its pieces are drawn as. */
    private final Map<Integer, String> glyphText = new TreeMap<>();
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
        for (ShapedSpec shaped : input.shaped()) {
            shaped(shaped, blocks);
        }
        unpictured.forEach((reason, ids) -> problems.add(unpicturedLine(reason, ids)));
        unworn.forEach((reason, ids) -> problems.add("Bedrock: " + ids.size() + " armour piece(s) not drawn on Bedrock"
                + " players wearing them - " + reason + ": " + String.join(", ", ids.subList(0, Math.min(6, ids.size())))
                + (ids.size() > 6 ? ", ..." : "")));
        List<Carrier> carriers = new ArrayList<>();
        Map<String, Integer> assignments = furniture(blocks, carriers);
        emojis();
        byte[] pack = pack();
        String packId = HexFormat.of().formatHex(sha256(pack)).substring(0, 12);
        List<String> missing = new ArrayList<>();
        unpictured.values().forEach(missing::addAll);
        return new Build(List.copyOf(items), List.copyOf(blocks), List.copyOf(carriers), assignments, pack, packId,
                List.copyOf(problems), List.copyOf(missing), Map.copyOf(glyphText), wornPieces);
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
                    drawnByNumber(item), item.uses());
        }
        String path = shortPath("textures/items/ark/", name, ".png");
        files.put(path, png(icon));
        itemTextures.put(name, path.substring(0, path.length() - 4));
        BedrockItem converted = new BedrockItem(item.id(), name, "minecraft:" + item.material().toLowerCase(Locale.ROOT),
                item.customModelData(), item.itemModel().toString(), name, item.displayName(), item.handheld(),
                drawnByNumber(item), item.uses());
        if (item.worn() != null) {
            worn(item, converted);
        }
        return converted;
    }

    // ---------------------------------------------------------------- armour, worn

    /** Each slot's vanilla armour geometry, and the variable that hides the wearer's own layer there. */
    private static final Map<String, String[]> ARMOUR_PIECES = Map.of(
            "head", new String[]{"helmet", "helmet_layer_visible"},
            "chest", new String[]{"chestplate", "chest_layer_visible"},
            "legs", new String[]{"leggings", "leg_layer_visible"},
            "feet", new String[]{"boots", "boot_layer_visible"});
    /** Java draws an item worn on the head at five eighths of its size, as a carved pumpkin. */
    private static final float HEAD_ITEM_SCALE = 0.625f;

    /**
     * 3.4: the attachable that draws a piece of armour on a Bedrock player - one for each name Geyser
     * may give the item - as vanilla's armour is drawn: the Java equipment layer over the body, or a
     * 3D model on the head. Without it, a custom item worn is not drawn at all.
     */
    private void worn(ItemSpec spec, BedrockItem item) {
        Worn worn = spec.worn();
        String[] piece = ARMOUR_PIECES.get(worn.slot());
        if (piece == null) {
            return;
        }
        String texture;
        String geometry;
        if (worn.asset() != null) {
            String layer = worn.slot().equals("legs") ? "humanoid_leggings" : "humanoid";
            BufferedImage image = armourLayer(worn.asset(), layer);
            if (image == null) {
                unworn.computeIfAbsent("its equipment asset has no " + layer + " picture in the pack",
                        key -> new ArrayList<>()).add(spec.id() + " (" + worn.asset() + ")");
                return;
            }
            String path = shortPath("textures/models/armor/ark/", item.name(), ".png");
            files.put(path, png(image));
            texture = path.substring(0, path.length() - ".png".length());
            geometry = "geometry.humanoid.armor." + piece[0];
        } else if (worn.vanilla() != null) {
            texture = worn.vanilla();
            geometry = "geometry.humanoid.armor." + piece[0];
        } else if (worn.model() != null && worn.slot().equals("head")) {
            String[] drawn = wornOnHead(spec.id(), item.name(), worn.model());
            if (drawn == null) {
                return;
            }
            geometry = drawn[0];
            texture = drawn[1];
        } else {
            return;
        }
        List<String> identifiers = new ArrayList<>(List.of(ITEM_NAMESPACE + ":" + item.name(),
                LEGACY_NAMESPACE + ":" + item.name()));
        if (item.byNumber()) {
            identifiers.add(BY_NUMBER_NAMESPACE + ":" + item.name());
        }
        for (String identifier : identifiers) {
            files.put(shortPath("attachables/ark/", identifier.replace(':', '.'), ".json"),
                    GSON.toJson(attachable(identifier, texture, geometry, piece[1])).getBytes(StandardCharsets.UTF_8));
        }
        wornPieces++;
    }

    /** Vanilla's armour attachable, with another texture and geometry. */
    static JsonObject attachable(String identifier, String texture, String geometry, String hiddenLayer) {
        JsonObject materials = new JsonObject();
        materials.addProperty("default", "armor");
        materials.addProperty("enchanted", "armor_enchanted");
        JsonObject textures = new JsonObject();
        textures.addProperty("default", texture);
        textures.addProperty("enchanted", "textures/misc/enchanted_actor_glint");
        JsonObject geometries = new JsonObject();
        geometries.addProperty("default", geometry);
        JsonObject scripts = new JsonObject();
        scripts.addProperty("parent_setup", "variable." + hiddenLayer + " = 0.0;");
        JsonArray controllers = new JsonArray();
        controllers.add("controller.render.armor");
        JsonObject description = new JsonObject();
        description.addProperty("identifier", identifier);
        description.add("materials", materials);
        description.add("textures", textures);
        description.add("geometry", geometries);
        description.add("scripts", scripts);
        description.add("render_controllers", controllers);
        JsonObject body = new JsonObject();
        body.add("description", description);
        JsonObject file = new JsonObject();
        file.addProperty("format_version", "1.8.0");
        file.add("minecraft:attachable", body);
        return file;
    }

    /**
     * An equipment asset's layer as one picture: {@code assets/<ns>/equipment/<asset>.json} names its
     * textures, drawn one over the other; an asset with no such file, the texture of its own name.
     *
     * <p>Only textures shaped as armour is - twice as wide as tall, 64 x 32 and its multiples - are
     * pictures of it. MythicArmor's pieces are drawn in 3D by its shaders from textures of other
     * shapes, which mean nothing without them; it gives each piece a plain look for far away as well,
     * {@code <asset>_far}, and that is what Bedrock players are shown.</p>
     */
    private @Nullable BufferedImage armourLayer(ResourceLocation asset, String layer) {
        BufferedImage picture = armourLayer(asset, layer, true);
        if (picture == null) {
            picture = armourLayer(new ResourceLocation(asset.namespace(), asset.path() + "_far"), layer, false);
        }
        return picture;
    }

    private @Nullable BufferedImage armourLayer(ResourceLocation asset, String layer, boolean ownName) {
        List<ResourceLocation> textures = new ArrayList<>();
        byte[] bytes = clientFile("assets/" + asset.namespace() + "/equipment/" + asset.path() + ".json");
        if (bytes != null) {
            try {
                JsonElement root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
                if (root.isJsonObject() && root.getAsJsonObject().get("layers") instanceof JsonObject layers
                        && layers.get(layer) instanceof JsonArray list) {
                    for (JsonElement entry : list) {
                        if (entry instanceof JsonObject one && one.get("texture") instanceof JsonElement name
                                && name.isJsonPrimitive()) {
                            ResourceLocation texture = ResourceLocation.parse(name.getAsString(), "minecraft");
                            textures.add(new ResourceLocation(texture.namespace(),
                                    "entity/equipment/" + layer + "/" + texture.path()));
                        }
                    }
                }
            } catch (RuntimeException broken) {
                problems.add("Bedrock: the equipment asset " + asset + " could not be read (" + broken.getMessage() + ")");
            }
        }
        if (textures.isEmpty() && (bytes != null || ownName)) {
            textures.add(new ResourceLocation(asset.namespace(), "entity/equipment/" + layer + "/" + asset.path()));
        }
        BufferedImage combined = null;
        for (ResourceLocation texture : textures) {
            BufferedImage image = clientImage(texture);
            if (image == null || image.getWidth() != image.getHeight() * 2) {
                continue;
            }
            if (combined == null) {
                combined = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
            }
            java.awt.Graphics2D graphics = combined.createGraphics();
            graphics.drawImage(image, 0, 0, combined.getWidth(), combined.getHeight(), null);
            graphics.dispose();
        }
        return combined;
    }

    /** The pack format of the Java clients this server is for: 1.21.8's. */
    static final int CLIENT_PACK_FORMAT = 64;

    /**
     * A file as a 1.21.8 client reads it: from the topmost overlay folder that is for its pack format
     * and has it, else from the pack itself. Armour often lives only in an overlay - ItemsAdder
     * writes its equipment assets for 1.21.4 and later into one.
     */
    private @Nullable byte[] clientFile(String path) {
        if (clientOverlays == null) {
            clientOverlays = overlays(input.pack(), CLIENT_PACK_FORMAT);
        }
        for (String folder : clientOverlays) {
            byte[] bytes = input.pack().read(folder + "/" + path);
            if (bytes != null) {
                return bytes;
            }
        }
        return input.pack().read(path);
    }

    /** A texture as a 1.21.8 client reads it - see {@link #clientFile}. */
    private @Nullable BufferedImage clientImage(ResourceLocation texture) {
        byte[] bytes = clientFile("assets/" + texture.namespace() + "/textures/" + texture.path() + ".png");
        if (bytes == null) {
            return image(texture);
        }
        try {
            return PngReader.read(bytes);
        } catch (IOException broken) {
            problems.add("Bedrock: " + texture + " could not be read as a PNG");
            return null;
        }
    }

    /**
     * The overlay folders {@code pack.mcmeta} lists for pack format {@code format}, the last listed -
     * the one the client puts on top - first.
     */
    static List<String> overlays(PackFiles pack, int format) {
        List<String> folders = new ArrayList<>();
        byte[] bytes = pack.read("pack.mcmeta");
        if (bytes == null) {
            return folders;
        }
        try {
            JsonElement root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            if (!(root instanceof JsonObject meta) || !(meta.get("overlays") instanceof JsonObject overlays)
                    || !(overlays.get("entries") instanceof JsonArray entries)) {
                return folders;
            }
            for (JsonElement element : entries) {
                if (element instanceof JsonObject entry && entry.get("directory") instanceof JsonElement directory
                        && directory.isJsonPrimitive() && covers(entry, format)) {
                    folders.addFirst(directory.getAsString());
                }
            }
        } catch (RuntimeException unreadable) {
            // A pack.mcmeta the client could not read either: no overlays.
        }
        return folders;
    }

    /** Whether an overlay entry is for {@code format}: its {@code formats}, or else its min and max format. */
    private static boolean covers(JsonObject entry, int format) {
        JsonElement formats = entry.get("formats");
        int min;
        int max;
        if (formats != null && formats.isJsonPrimitive()) {
            min = max = formats.getAsInt();
        } else if (formats instanceof JsonArray range && range.size() == 2) {
            min = range.get(0).getAsInt();
            max = range.get(1).getAsInt();
        } else if (formats instanceof JsonObject range && range.has("min_inclusive") && range.has("max_inclusive")) {
            min = range.get("min_inclusive").getAsInt();
            max = range.get("max_inclusive").getAsInt();
        } else if (entry.has("min_format") && entry.has("max_format")) {
            min = major(entry.get("min_format"));
            max = major(entry.get("max_format"));
        } else {
            return false;
        }
        return min <= format && format <= max;
    }

    /** A pack format written as a number, or 1.21.9's {@code [major, minor]}. */
    private static int major(JsonElement value) {
        return value instanceof JsonArray pair && !pair.isEmpty() ? pair.get(0).getAsInt() : value.getAsInt();
    }

    /**
     * A 3D helmet: its model as Bedrock geometry on the head, sized and placed as Java draws an item
     * worn there ({@code display.head}), with every texture it uses stacked into one picture.
     *
     * @return the geometry's identifier and the texture's path; null when it cannot be drawn
     */
    private @Nullable String[] wornOnHead(String id, String name, ResourceLocation location) {
        JavaModel model = model(location);
        if (model == null || model.elements().isEmpty()) {
            unworn.computeIfAbsent("worn on the head as a model with no cubes", key -> new ArrayList<>()).add(id);
            return null;
        }
        List<ResourceLocation> used = new ArrayList<>();
        for (JavaModel.Element element : model.elements()) {
            element.faces().values().forEach(face -> {
                if (!used.contains(face.texture())) {
                    used.add(face.texture());
                }
            });
        }
        List<BufferedImage> pictures = new ArrayList<>();
        int size = 16;
        for (ResourceLocation texture : used) {
            BufferedImage image = image(texture);
            BufferedImage frame = image == null ? null : IconRenderer.frame(image);
            pictures.add(frame);
            if (frame != null) {
                size = Math.max(size, frame.getWidth());
            }
        }
        BufferedImage sheet = new BufferedImage(size, size * Math.max(1, used.size()), BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = sheet.createGraphics();
        for (int i = 0; i < pictures.size(); i++) {
            if (pictures.get(i) != null) {
                graphics.drawImage(pictures.get(i), 0, i * size, size, size, null);
            }
        }
        graphics.dispose();
        Map<ResourceLocation, Integer> rows = new HashMap<>();
        for (int i = 0; i < used.size(); i++) {
            rows.put(used.get(i), i);
        }
        float[][] head = headDisplay(location);
        String identifier = "geometry.arkcontent.worn." + name;
        JsonObject geometry = BedrockGeometry.onHead(identifier, model.elements(), rows, Math.max(1, used.size()),
                head[0], head[1], head[2], HEAD_ITEM_SCALE);
        files.put(shortPath("models/entity/ark/", name, ".geo.json"), GSON.toJson(geometry).getBytes(StandardCharsets.UTF_8));
        String path = shortPath("textures/models/armor/ark/", name, ".png");
        files.put(path, png(sheet));
        return new String[]{identifier, path.substring(0, path.length() - ".png".length())};
    }

    /** A model's {@code display.head} - translation, rotation, scale - from it or the nearest parent with one. */
    private float[][] headDisplay(ResourceLocation location) {
        float[][] head = {{0, 0, 0}, {0, 0, 0}, {1, 1, 1}};
        ResourceLocation current = modelJson(location) != null ? location : definedModel(location);
        for (int depth = 0; current != null && depth < 16; depth++) {
            JsonObject json = modelJson(current);
            if (json == null) {
                break;
            }
            if (json.get("display") instanceof JsonObject display && display.get("head") instanceof JsonObject transform) {
                String[] keys = {"translation", "rotation", "scale"};
                for (int i = 0; i < keys.length; i++) {
                    if (transform.get(keys[i]) instanceof JsonArray values && values.size() == 3) {
                        for (int axis = 0; axis < 3; axis++) {
                            head[i][axis] = values.get(axis).getAsFloat();
                        }
                    }
                }
                break;
            }
            current = json.has("parent") ? ResourceLocation.parse(json.get("parent").getAsString(), "minecraft") : null;
        }
        return head;
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
                block.destroySeconds(), 0, block.displayName());
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

    /**
     * 5.3: a shaped block, one Bedrock block for each way vanilla draws its copper block - each model
     * and turn its block state names - standing in for every Java state drawn that way. The turn
     * about y is the block's transformation, as for furniture; upside-down stairs are turned in their
     * geometry. What is collided with is the model's own cubes.
     */
    private void shaped(ShapedSpec spec, List<BedrockBlock> blocks) {
        JsonObject variants = ShapedModels.blockState(spec.shape(),
                suffix -> spec.models().getOrDefault(suffix, spec.models().get("")).toString()).getAsJsonObject("variants");
        Map<String, List<String>> drawnAlike = new LinkedHashMap<>();
        for (Map<String, String> state : ShapedStates.all(spec.shape())) {
            JsonElement variant = BlockStates.resolve(variants, state);
            if (!(variant instanceof JsonObject drawn)) {
                continue;
            }
            String key = drawn.get("model").getAsString() + "|" + (drawn.has("x") ? drawn.get("x").getAsInt() : 0)
                    + "|" + (drawn.has("y") ? drawn.get("y").getAsInt() : 0);
            drawnAlike.computeIfAbsent(key, ignored -> new ArrayList<>()).add(ShapedStates.write(spec.base(), state));
        }
        int n = 0;
        for (Map.Entry<String, List<String>> way : drawnAlike.entrySet()) {
            String[] parts = way.getKey().split("\\|");
            JavaModel model = model(ResourceLocation.parse(parts[0], ResourceLocation.MINECRAFT));
            if (model == null || model.elements().isEmpty()) {
                problems.add(spec.id() + ": its model " + parts[0] + " could not be read for Bedrock - it shows as "
                        + spec.base() + " there");
                return;
            }
            List<BedrockCube> cubes = blockCubes(model);
            if (Integer.parseInt(parts[1]) == 180) {
                cubes = upsideDown(cubes);
            }
            String name = name(spec.id()) + "_" + n++;
            Map<String, Material> materials = new LinkedHashMap<>();
            String geometry;
            if (fullCube(model)) {
                geometry = FULL_BLOCK;
                for (Map.Entry<JavaModel.Face, JavaModel.FaceTexture> face : model.elements().get(0).faces().entrySet()) {
                    materials.put(face.getKey().key(), material(face.getValue().texture(), false));
                }
                materials.put("*", materials.getOrDefault("north", materials.values().iterator().next()));
            } else {
                geometry = geometry(name, cubes, materials, false);
            }
            List<float[]> boxes = new ArrayList<>();
            for (BedrockCube cube : cubes) {
                // Bedrock's x runs the other way, as in the geometry.
                boxes.add(new float[]{-cube.max()[0], cube.min()[1], cube.min()[2], cube.max()[0] - cube.min()[0],
                        cube.max()[1] - cube.min()[1], cube.max()[2] - cube.min()[2]});
            }
            List<String> states = way.getValue();
            int turn = Math.floorMod(-Integer.parseInt(parts[2]) + 180, 360) - 180;
            blocks.add(new BedrockBlock(name, states.get(0), geometry, materials, true, true, 0, spec.destroySeconds(), turn,
                    spec.displayName(), states.subList(1, states.size()), boxes));
        }
    }

    /**
     * Cubes turned half round about the east-west line through the block's middle - Java's
     * {@code x: 180}, which stands stairs on their heads. Up and down trade places, as do north and
     * south; the texture on every side face turns half round with it.
     */
    static List<BedrockCube> upsideDown(List<BedrockCube> cubes) {
        List<BedrockCube> turned = new ArrayList<>();
        for (BedrockCube cube : cubes) {
            Map<JavaModel.Face, BedrockCube.Face> faces = new java.util.EnumMap<>(JavaModel.Face.class);
            cube.faces().forEach((face, drawn) -> {
                float[] uv = drawn.uv();
                switch (face) {
                    case UP -> faces.put(JavaModel.Face.DOWN, drawn);
                    case DOWN -> faces.put(JavaModel.Face.UP, drawn);
                    default -> faces.put(face == JavaModel.Face.NORTH ? JavaModel.Face.SOUTH
                                    : face == JavaModel.Face.SOUTH ? JavaModel.Face.NORTH : face,
                            new BedrockCube.Face(new float[]{uv[2], uv[3], uv[0], uv[1]}, drawn.texture()));
                }
            });
            float angle = cube.axis() == null || cube.axis() == 'x' ? cube.angle() : -cube.angle();
            turned.add(new BedrockCube(new float[]{cube.min()[0], 16 - cube.max()[1], -cube.max()[2]},
                    new float[]{cube.max()[0], 16 - cube.min()[1], -cube.min()[2]}, faces, cube.axis(), angle,
                    new float[]{cube.pivot()[0], 16 - cube.pivot()[1], -cube.pivot()[2]}));
        }
        return turned;
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
        // 3.5: the three turned versions of a piece one block across, given carriers if any are left.
        Map<String, List<String>> turnsByPiece = new TreeMap<>();
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
                boolean turnless = turnless(layout, piece.footprint());
                for (int quarter = 0; quarter < 4; quarter++) {
                    String key = piece.id() + "|" + cell.getKey().key() + "|" + quarter;
                    wanted.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new Object[]{piece, cell.getKey(),
                            quarter, geometry, materials, onSupport, base});
                    (turnless && quarter > 0 ? turnsByPiece : keysByPiece)
                            .computeIfAbsent(piece.id(), ignored -> new ArrayList<>()).add(key);
                }
            }
        }
        // Carriers from the top of the custom blocks' range down, never a state a block holds; then
        // (3.5) the mob-head instruments' states.
        Map<String, Integer> mirroredPrevious = new HashMap<>();
        input.previousCarriers().forEach((key, state) -> mirroredPrevious.put(key, carrierNumber(state)));
        Set<Integer> reserved = new HashSet<>();
        input.blockStates().forEach(state -> reserved.add(carrierNumber(state)));
        reserved.add(NoteBlockState.CAPACITY - 1);
        Set<Integer> turnNumbers = new HashSet<>();
        turnsByPiece.values().forEach(keys -> keys.forEach(key -> {
            Integer number = mirroredPrevious.get(key);
            if (number != null) {
                turnNumbers.add(number);
            }
        }));
        CarrierPlan whole = planCarriers(keysByPiece, mirroredPrevious, reserved, 1, NoteBlockState.CARRIER_CAPACITY - 1,
                turnNumbers);
        // 3.5: a piece one block across stands on the same blocks however it is turned: shown unturned,
        // it needs one carrier a block, not four. Its turns get carriers only where some are left over.
        Set<Integer> taken = new HashSet<>(reserved);
        taken.addAll(whole.assignments().values());
        Map<String, List<String>> turnsShown = new TreeMap<>(turnsByPiece);
        turnsShown.keySet().removeAll(whole.leftOut().keySet());
        CarrierPlan turns = planCarriers(turnsShown, mirroredPrevious, taken, 1, NoteBlockState.CARRIER_CAPACITY - 1);
        Map<String, Integer> all = new HashMap<>(whole.assignments());
        all.putAll(turns.assignments());
        CarrierPlan plan = new CarrierPlan(all, whole.leftOut());
        if (!turns.leftOut().isEmpty()) {
            problems.add("Bedrock: " + turns.leftOut().size() + " piece(s) one block across are shown to Bedrock players"
                    + " unturned - not enough note block states to turn them too (they need four each, one a quarter"
                    + " turn); smallest first, the others are turned");
        }
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
        plan.assignments().forEach((key, number) -> assignments.put(key, carrierState(number)));
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
            NoteBlockState state = NoteBlockState.fromCarrierIndex(carrierState(mirrored));
            FurnitureLayout.Cell placed = FurnitureLayout.turned(cell, piece.footprint(), quarter);
            carriers.add(new Carrier(piece.id(), quarter, placed.dx(), placed.dy(), placed.dz(), state, onSupport));
            blocks.add(new BedrockBlock(spec[6] + "_r" + quarter, state.asBlockData(), (String) spec[3], materials,
                    onSupport && piece.solid(), onSupport, onSupport ? piece.light() : 0, 0,
                    Math.floorMod(-quarter * 90 + 180, 360) - 180, piece.displayName()));
        }
        return assignments;
    }

    /**
     * The order carriers are handed out in, by number: 1 to 798 the 16 block instruments' states from
     * the top down - as before 3.5, so a piece keeps its carriers - then from 800 the mob-head
     * instruments' states, as they are numbered.
     */
    static int carrierNumber(int carrierIndex) {
        return carrierIndex < NoteBlockState.CAPACITY ? NoteBlockState.CAPACITY - carrierIndex : carrierIndex;
    }

    /** The carrier index of a carrier number: see {@link #carrierNumber}. */
    static int carrierState(int number) {
        return number < NoteBlockState.CAPACITY ? NoteBlockState.CAPACITY - number : number;
    }

    /**
     * 3.5: whether a piece stands on the same blocks however it is turned - one block across, all its
     * model within its own column - so that, shown unturned, it is still where it is.
     */
    private static boolean turnless(FurnitureLayout.Layout layout, Footprint footprint) {
        for (FurnitureLayout.Cell cell : layout.cells().keySet()) {
            for (int quarter = 1; quarter < 4; quarter++) {
                if (!FurnitureLayout.turned(cell, footprint, quarter).equals(FurnitureLayout.turned(cell, footprint, 0))) {
                    return false;
                }
            }
        }
        return true;
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
     * worse than none. Pieces that had all theirs before keep them (3.5); then the smallest pieces go
     * first, so the most pieces are shown when there are not enough for all; a key keeps the number
     * it had before when it can. A carrier is only ever a
     * block change sent to a Bedrock client, and Geyser learns them all anew as it starts: nothing in
     * the world holds one, so a piece no longer defined frees its numbers at once.
     *
     * @param keysByPiece every piece's carrier keys
     * @param previous    the number each key had before
     */
    static CarrierPlan planCarriers(Map<String, List<String>> keysByPiece, Map<String, Integer> previous,
                                    Set<Integer> reserved, int first, int last) {
        return planCarriers(keysByPiece, previous, reserved, first, last, Set.of());
    }

    /** @param soft numbers handed out only when no others are left: what keys planned apart had (3.5) */
    static CarrierPlan planCarriers(Map<String, List<String>> keysByPiece, Map<String, Integer> previous,
                                    Set<Integer> reserved, int first, int last, Set<Integer> soft) {
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
        // 3.5: a piece Bedrock players were shown keeps its carriers, first; content added since only
        // gets what is left - a server adding three hundred small pieces does not lose its big ones.
        List<String> order = new ArrayList<>(keysByPiece.keySet());
        order.sort(java.util.Comparator.comparing((String piece) -> !keysByPiece.get(piece).stream().allMatch(claim::containsKey))
                .thenComparingInt(piece -> keysByPiece.get(piece).size())
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
                // A number nobody had first; then one another plan's key had (3.5: a small piece's turn);
                // else one a later, larger piece had.
                Integer number = free.stream().filter(n -> !claimedBy.containsKey(n) && !soft.contains(n)).findFirst()
                        .orElseGet(() -> free.stream().filter(n -> !claimedBy.containsKey(n)).findFirst().orElse(free.first()));
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

    // ---------------------------------------------------------------- emojis and font images

    /** Glyphs taller than this, in font pixels, are menu backgrounds and HUD panels, not inline icons. */
    private static final int TALLEST_INLINE_GLYPH = 32;
    /** The largest glyph cell drawn: sheets of 2048 pixels. A glyph wider than this is cut in pieces. */
    private static final int LARGEST_CELL = 128;

    /**
     * A character to draw on a glyph sheet.
     *
     * @param image  null for a character drawn as nothing (a space, a menu background)
     * @param height and {@code ascent}: the Java font's, in font pixels
     */
    private record Glyph(@Nullable BufferedImage image, int height, int ascent) {
    }

    /**
     * Bedrock draws a private-use character from {@code font/glyph_XX.png}: 256 characters a sheet,
     * 16 across, each cell the sheet's width over 16 - and, as seen on a phone (3.3.3), each pixel of
     * a sheet about one pixel of its font, whatever the sheet's size. So every glyph is drawn at the
     * size Java draws it: made smaller or larger by a whole factor, which keeps pixel art crisp (a
     * rank 22 pixels high, drawn ten high on Java, is drawn 11 high), in a cell as large as the
     * sheet's largest glyph needs, from 16 to 128 pixels; centred on the line as Java centres it.
     *
     * <p>That is every private-use character of the Java pack's default font - the ranks in a
     * scoreboard, the icons in a tab list, the plugin's emojis - and the emojis it does not draw.
     * Spaces, and the menu backgrounds a font carries, are drawn as nothing: on Bedrock they would
     * otherwise show as boxes.</p>
     */
    private void emojis() {
        Map<Integer, Glyph> glyphs = new TreeMap<>();
        fontGlyphs(new ResourceLocation("minecraft", "default"), glyphs, 0);
        for (EmojiSpec emoji : input.emojis()) {
            if (privateUse(emoji.character()) && !glyphs.containsKey(emoji.character())) {
                BufferedImage image = image(emoji.texture());
                glyphs.put(emoji.character(), new Glyph(image == null ? null : IconRenderer.frame(image), 9, 8));
            }
        }
        Map<Integer, Glyph> sized = new TreeMap<>();
        glyphs.forEach((character, glyph) -> sized.put(character, sized(glyph)));
        sized.putAll(pieces(sized));
        Map<Integer, Map<Integer, Glyph>> sheets = new TreeMap<>();
        sized.forEach((character, glyph) -> sheets.computeIfAbsent(character >> 8, ignored -> new TreeMap<>())
                .put(character & 0xff, glyph));
        for (Map.Entry<Integer, Map<Integer, Glyph>> sheet : sheets.entrySet()) {
            int cell = 16;
            for (Glyph glyph : sheet.getValue().values()) {
                if (glyph.image() != null) {
                    cell = Math.max(cell, Math.max(glyph.image().getWidth(), glyph.image().getHeight() + 4));
                }
            }
            cell = Math.min(LARGEST_CELL, Integer.highestOneBit(cell - 1) << 1); // 16 to 128, as Bedrock's own
            BufferedImage page = new BufferedImage(cell * 16, cell * 16, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D graphics = page.createGraphics();
            for (Map.Entry<Integer, Glyph> entry : sheet.getValue().entrySet()) {
                Glyph glyph = entry.getValue();
                if (glyph.image() == null) {
                    continue;
                }
                BufferedImage image = glyph.image();
                // Java puts the top ascent pixels above the baseline, a letter's middle 3.5 above it;
                // Bedrock puts a cell's middle about two pixels above a letter's middle.
                double below = 3.5 - glyph.ascent() + glyph.height() / 2.0;
                int top = (int) Math.round((cell - image.getHeight()) / 2.0 + 2 + below);
                top = Math.max(0, Math.min(cell - image.getHeight(), top));
                graphics.drawImage(image, (entry.getKey() & 15) * cell, (entry.getKey() >> 4) * cell + top, null);
            }
            graphics.dispose();
            files.put(String.format(Locale.ROOT, "font/glyph_%02X.png", sheet.getKey()), png(page));
        }
    }

    /**
     * The glyph's picture at about the size Java draws it, one pixel to a font pixel: smaller by a
     * whole factor, each pixel the mean of those it stands for, or larger by one, each pixel repeated.
     */
    private static Glyph sized(Glyph glyph) {
        BufferedImage image = glyph.image();
        if (image == null) {
            return glyph;
        }
        double scale = (double) glyph.height() / image.getHeight();
        if (scale < 1) {
            image = shrunk(image, Math.max(1, (int) Math.round(1 / scale)));
        } else if (scale >= 1.5) {
            int factor = (int) Math.round(scale);
            BufferedImage larger = new BufferedImage(image.getWidth() * factor, image.getHeight() * factor,
                    BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D graphics = larger.createGraphics();
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.drawImage(image, 0, 0, larger.getWidth(), larger.getHeight(), null);
            graphics.dispose();
            image = larger;
        }
        return new Glyph(image, glyph.height(), glyph.ascent());
    }

    /** Each square of {@code factor} by {@code factor} pixels made one, its colour weighed by how opaque each is. */
    static BufferedImage shrunk(BufferedImage image, int factor) {
        if (factor <= 1) {
            return image;
        }
        int width = (image.getWidth() + factor - 1) / factor;
        int height = (image.getHeight() + factor - 1) / factor;
        BufferedImage small = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                long alpha = 0;
                long red = 0;
                long green = 0;
                long blue = 0;
                int count = 0;
                for (int dy = 0; dy < factor && y * factor + dy < image.getHeight(); dy++) {
                    for (int dx = 0; dx < factor && x * factor + dx < image.getWidth(); dx++) {
                        int argb = image.getRGB(x * factor + dx, y * factor + dy);
                        int a = argb >>> 24;
                        alpha += a;
                        red += (long) ((argb >> 16) & 0xff) * a;
                        green += (long) ((argb >> 8) & 0xff) * a;
                        blue += (long) (argb & 0xff) * a;
                        count++;
                    }
                }
                if (alpha > 0) {
                    small.setRGB(x, y, (int) (alpha / count) << 24 | (int) (red / alpha) << 16
                            | (int) (green / alpha) << 8 | (int) (blue / alpha));
                }
            }
        }
        return small;
    }

    /**
     * 3.3.3: a glyph wider than the largest cell, even at its own size, is cut into pieces a cell wide,
     * each on a character the font leaves free, from U+F7FF down (never Bedrock's own sheets E0 and
     * E1); {@link Build#glyphText()} says which characters stand for which, and the plugin writes
     * those in its place in what it sends a Bedrock player. Its own cell keeps the whole glyph, made
     * smaller to fit, for text that is not rewritten.
     */
    private Map<Integer, Glyph> pieces(Map<Integer, Glyph> glyphs) {
        Map<Integer, Glyph> pieces = new TreeMap<>();
        Set<Integer> taken = new HashSet<>(glyphs.keySet());
        int next = 0xF7FF;
        for (Map.Entry<Integer, Glyph> entry : new ArrayList<>(glyphs.entrySet())) {
            Glyph glyph = entry.getValue();
            if (glyph.image() == null || glyph.image().getWidth() <= LARGEST_CELL) {
                continue;
            }
            BufferedImage image = glyph.image();
            int count = (image.getWidth() + LARGEST_CELL - 1) / LARGEST_CELL;
            StringBuilder text = new StringBuilder();
            Map<Integer, Glyph> these = new TreeMap<>();
            for (int i = 0; i < count; i++) {
                while (next >= 0xE200 && taken.contains(next)) {
                    next--;
                }
                if (next < 0xE200) {
                    break;
                }
                int from = i * LARGEST_CELL;
                int to = Math.min(image.getWidth(), from + LARGEST_CELL);
                these.put(next, new Glyph(image.getSubimage(from, 0, to - from, image.getHeight()), glyph.height(),
                        glyph.ascent()));
                taken.add(next);
                text.appendCodePoint(next);
                next--;
            }
            // Its own glyph, the whole of it, made smaller to fit one cell.
            glyphs.put(entry.getKey(), new Glyph(shrunk(image, count), glyph.height(), glyph.ascent()));
            if (these.size() < count) {
                problems.add("Bedrock: no free characters left to cut wide glyphs into - "
                        + String.format(Locale.ROOT, "U+%04X", entry.getKey()) + " and those after it stay one small glyph");
                break;
            }
            pieces.putAll(these);
            glyphText.put(entry.getKey(), text.toString());
        }
        return pieces;
    }

    /**
     * 3.3.3: the private-use characters of a Java font, by code point - the first provider that
     * names a character draws it, as on Java; a {@code reference} is followed a few levels deep.
     */
    private void fontGlyphs(ResourceLocation font, Map<Integer, Glyph> glyphs, int depth) {
        byte[] bytes = depth > 4 ? null : input.pack().read("assets/" + font.namespace() + "/font/" + font.path() + ".json");
        if (bytes == null) {
            return;
        }
        JsonArray providers;
        try {
            JsonElement root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            if (!root.isJsonObject() || !root.getAsJsonObject().has("providers")
                    || !root.getAsJsonObject().get("providers").isJsonArray()) {
                return;
            }
            providers = root.getAsJsonObject().getAsJsonArray("providers");
        } catch (RuntimeException unreadable) {
            problems.add("Bedrock: font " + font + " could not be read - its characters are not drawn there");
            return;
        }
        for (JsonElement element : providers) {
            try {
                if (element.isJsonObject()) {
                    fontProvider(element.getAsJsonObject(), glyphs, depth);
                }
            } catch (RuntimeException broken) {
                // A provider the client would not take either: the rest still count.
            }
        }
    }

    private void fontProvider(JsonObject provider, Map<Integer, Glyph> glyphs, int depth) {
        String type = provider.has("type") ? provider.get("type").getAsString().replace("minecraft:", "") : "";
        switch (type) {
            case "reference" -> fontGlyphs(ResourceLocation.parse(provider.get("id").getAsString(), "minecraft"), glyphs,
                    depth + 1);
            case "space" -> {
                if (provider.has("advances") && provider.get("advances").isJsonObject()) {
                    for (String character : provider.getAsJsonObject("advances").keySet()) {
                        int code = character.codePointAt(0);
                        if (privateUse(code)) {
                            glyphs.putIfAbsent(code, new Glyph(null, 0, 0));
                        }
                    }
                }
            }
            case "bitmap" -> {
                JsonArray rows = provider.getAsJsonArray("chars");
                int height = provider.has("height") ? provider.get("height").getAsInt() : 8;
                int ascent = provider.get("ascent").getAsInt();
                List<int[]> codes = new ArrayList<>();
                for (JsonElement row : rows) {
                    codes.add(row.getAsString().codePoints().toArray());
                }
                if (codes.stream().flatMapToInt(java.util.Arrays::stream).noneMatch(BedrockConverter::privateUse)) {
                    return;
                }
                BufferedImage sheet = height > 0 && height <= TALLEST_INLINE_GLYPH ? fontImage(provider) : null;
                int columns = codes.stream().mapToInt(row -> row.length).max().orElse(1);
                for (int row = 0; row < codes.size(); row++) {
                    for (int column = 0; column < codes.get(row).length; column++) {
                        int code = codes.get(row)[column];
                        if (!privateUse(code) || glyphs.containsKey(code)) {
                            continue;
                        }
                        BufferedImage image = null;
                        if (sheet != null) {
                            int cellWidth = sheet.getWidth() / columns;
                            int cellHeight = sheet.getHeight() / codes.size();
                            image = cellWidth > 2 && cellHeight > 0
                                    ? trimmed(sheet.getSubimage(column * cellWidth, row * cellHeight, cellWidth, cellHeight))
                                    : null;
                        }
                        glyphs.put(code, new Glyph(image, height, ascent));
                    }
                }
            }
            default -> {
            }
        }
    }

    private @Nullable BufferedImage fontImage(JsonObject provider) {
        ResourceLocation file = ResourceLocation.parse(provider.get("file").getAsString(), "minecraft");
        byte[] bytes = input.pack().read("assets/" + file.namespace() + "/textures/" + file.path());
        if (bytes == null) {
            return null;
        }
        try {
            return PngReader.read(bytes);
        } catch (IOException broken) {
            problems.add("Bedrock: font image " + file + " could not be read as a PNG");
            return null;
        }
    }

    /**
     * The glyph without the empty columns on its right: Java measures a glyph to its last pixel, and
     * so does Bedrock - kept, they would only make the glyph smaller in its cell. Null if empty.
     */
    private static @Nullable BufferedImage trimmed(BufferedImage glyph) {
        for (int x = glyph.getWidth() - 1; x >= 0; x--) {
            for (int y = 0; y < glyph.getHeight(); y++) {
                if ((glyph.getRGB(x, y) >>> 24) != 0) {
                    return x == glyph.getWidth() - 1 ? glyph : glyph.getSubimage(0, 0, x + 1, glyph.getHeight());
                }
            }
        }
        return null;
    }

    private static boolean privateUse(int code) {
        return code >= 0xE000 && code <= 0xF8FF;
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
        head.addProperty("name", "OverlordContent");
        head.addProperty("description", "Custom items, blocks and furniture, made for Bedrock by OverlordContent");
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
