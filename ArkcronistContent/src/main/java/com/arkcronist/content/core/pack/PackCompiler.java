package com.arkcronist.content.core.pack;

import com.arkcronist.content.core.animation.AnimatedModel;
import com.arkcronist.content.core.block.NoteBlockState;
import com.arkcronist.content.core.definition.AdvancementDefinition;
import com.arkcronist.content.core.definition.EmojiDefinition;
import com.arkcronist.content.core.definition.Equipment;
import com.arkcronist.content.core.definition.ItemDefinition;
import com.arkcronist.content.core.definition.ModelSource;
import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.definition.ResourceLocation;
import com.arkcronist.content.core.hud.HudGlyph;
import com.arkcronist.content.core.hud.Spaces;
import com.arkcronist.content.core.liquid.LiquidModels;
import com.arkcronist.content.core.liquid.TripwireState;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeSet;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Turns item definitions into a vanilla resource pack folder.
 *
 * <p>For every item with a look it writes three kinds of file into {@code assets/<namespace>/}:</p>
 * <ul>
 *   <li>{@code items/<id>.json} - the item model definition the stack's {@code item_model}
 *       component points at, which in turn names the model to draw;</li>
 *   <li>{@code models/...json} - the item's model, copied from the content pack, or one generated
 *       from a parent and textures (a flat item, a {@code cube_all} block);</li>
 *   <li>{@code textures/...png} - every texture those models use.</li>
 * </ul>
 *
 * <p>When there are custom blocks it also writes {@code assets/minecraft/blockstates/note_block.json},
 * which is how the client learns to draw a note block state as one of them.</p>
 *
 * <p>Files that are not drawn from a definition go in exactly as they are, never re-encoded: a
 * content pack's {@code sounds.json} and {@code sounds/*.ogg}, a texture's {@code .png.mcmeta}
 * animation, an item definition file ({@code resource.item-model}), and every file of a merged
 * pack ({@link PackSource}) - its overlays and their {@code pack.mcmeta} entries included.</p>
 *
 * <p>Only what is referenced is copied. A model is read, and its {@code parent} and
 * {@code textures} followed, for as long as they stay inside the item's namespace; anything in
 * {@code minecraft:} is already on the client. A stray draft texture in the content folder therefore
 * never reaches players, and a missing file is reported by the item that needed it.</p>
 *
 * <p>The folder is rebuilt from nothing on every run, so something deleted from the contents is
 * also gone from the pack. It is output, not a place to edit by hand.</p>
 *
 * <p>Disk-bound and meant for a worker thread. Nothing here touches the server.</p>
 */
public final class PackCompiler {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] OGG_SIGNATURE = {'O', 'g', 'g', 'S'};

    /** What the pack draws for every note block state that is not a custom block's. */
    private static final String VANILLA_NOTE_BLOCK = "minecraft:block/note_block";

    /**
     * What a compile wrote.
     *
     * @param customBlockStates note block states mapped to a custom block's model
     * @param glyphs            emojis written into the font
     * @param externalFiles     files taken from other plugins' packs
     * @param sounds            sound files and {@code sounds.json} files taken from the content packs
     * @param problems          missing or unreadable files and conflicting paths, each naming the
     *                          item that ran into it
     */
    public record Result(int itemDefinitions, int models, int textures, int customBlockStates,
                         int glyphs, int externalFiles, int sounds, List<String> problems) {

        public Result {
            problems = List.copyOf(problems);
        }
    }

    private final PackSettings settings;

    public PackCompiler(PackSettings settings) {
        this.settings = settings;
    }

    /** {@link #compile(Path, Collection, Map, List)} with no other plugin's pack to merge. */
    public Result compile(Path packDir, Collection<ItemDefinition> items,
                          Map<String, NoteBlockState> noteBlockStates) throws IOException {
        return compile(packDir, items, noteBlockStates, List.of());
    }

    /**
     * Deletes {@code packDir} and writes the pack for {@code items} into it.
     *
     * @param noteBlockStates the state each custom block is drawn through, by full id; empty leaves
     *                        the vanilla note block untouched
     * @param externalPacks   other plugins' packs to merge in, after this plugin's own files
     */
    public Result compile(Path packDir, Collection<ItemDefinition> items,
                          Map<String, NoteBlockState> noteBlockStates,
                          List<ExternalPack> externalPacks) throws IOException {
        return compile(packDir, items, noteBlockStates, Map.of(), externalPacks);
    }

    /**
     * Deletes {@code packDir} and writes the pack for {@code items} into it.
     *
     * @param noteBlockStates the state each custom block is drawn through, by full id; empty leaves
     *                        the vanilla note block untouched
     * @param glyphs          the character each emoji is drawn as; empty writes no font
     * @param externalPacks   other plugins' packs to merge in, after this plugin's own files
     */
    public Result compile(Path packDir, Collection<ItemDefinition> items,
                          Map<String, NoteBlockState> noteBlockStates,
                          Map<EmojiDefinition, Integer> glyphs,
                          List<ExternalPack> externalPacks) throws IOException {
        return compile(packDir, items, noteBlockStates, glyphs, List.of(), externalPacks);
    }

    /**
     * Deletes {@code packDir} and writes the pack for {@code items} into it.
     *
     * @param noteBlockStates the state each custom block is drawn through, by full id; empty leaves
     *                        the vanilla note block untouched
     * @param glyphs          the character each emoji is drawn as; empty writes no font
     * @param advancements    their tab backgrounds, when they are this plugin's textures; the
     *                        advancements themselves are server data, registered by the plugin
     * @param externalPacks   other plugins' packs to merge in, after this plugin's own files
     */
    public Result compile(Path packDir, Collection<ItemDefinition> items,
                          Map<String, NoteBlockState> noteBlockStates,
                          Map<EmojiDefinition, Integer> glyphs,
                          Collection<AdvancementDefinition> advancements,
                          List<ExternalPack> externalPacks) throws IOException {
        return compile(packDir, items, noteBlockStates, glyphs, advancements, Map.of(), externalPacks);
    }

    /**
     * Deletes {@code packDir} and writes the pack for {@code items} into it.
     *
     * @param noteBlockStates the state each custom block is drawn through, by full id; empty leaves
     *                        the vanilla note block untouched
     * @param glyphs          the character each emoji is drawn as; empty writes no font
     * @param advancements    their tab backgrounds, when they are this plugin's textures; the
     *                        advancements themselves are server data, registered by the plugin
     * @param modelData       the {@code custom_model_data} number each item is also drawn by on its
     *                        plain material, by full id ({@link ModelDataDispatch}); empty writes none
     * @param externalPacks   other plugins' packs to merge in, after this plugin's own files
     */
    public Result compile(Path packDir, Collection<ItemDefinition> items,
                          Map<String, NoteBlockState> noteBlockStates,
                          Map<EmojiDefinition, Integer> glyphs,
                          Collection<AdvancementDefinition> advancements,
                          Map<String, Integer> modelData,
                          List<ExternalPack> externalPacks) throws IOException {
        return compile(packDir, new Input(items, noteBlockStates, glyphs, advancements, modelData, Map.of(), List.of(),
                false, externalPacks));
    }

    /**
     * Everything one compile is given.
     *
     * @param noteBlockStates the state each custom block is drawn through, by full id; empty leaves
     *                        the vanilla note block untouched
     * @param glyphs          the character each emoji is drawn as
     * @param advancements    their tab backgrounds, when they are this plugin's textures
     * @param modelData       the {@code custom_model_data} number each item is also drawn by on its
     *                        plain material, by full id ({@link ModelDataDispatch}); empty writes none
     * @param liquidSlots     the pair of tripwire states each liquid is drawn through, by full id
     *                        ({@link TripwireState#source(int)}); empty leaves tripwire untouched
     * @param hudGlyphs       every HUD icon, on its character
     * @param spaces          write the space characters ({@link Spaces}) even with no emoji or HUD
     *                        needing the font - for menus and scoreboards that lay text out with them
     * @param externalPacks   other plugins' packs to merge in, after this plugin's own files
     * @param contentsDir     the content packs' folder: each pack's {@code sounds.json} and
     *                        {@code sounds/} go into {@code assets/<its folder name>/} as they are;
     *                        null takes no sounds
     */
    public record Input(Collection<ItemDefinition> items, Map<String, NoteBlockState> noteBlockStates,
                        Map<EmojiDefinition, Integer> glyphs, Collection<AdvancementDefinition> advancements,
                        Map<String, Integer> modelData, Map<String, Integer> liquidSlots, List<HudGlyph> hudGlyphs,
                        boolean spaces, List<ExternalPack> externalPacks, @Nullable Path contentsDir) {

        /** No content pack sounds. */
        public Input(Collection<ItemDefinition> items, Map<String, NoteBlockState> noteBlockStates,
                     Map<EmojiDefinition, Integer> glyphs, Collection<AdvancementDefinition> advancements,
                     Map<String, Integer> modelData, Map<String, Integer> liquidSlots, List<HudGlyph> hudGlyphs,
                     boolean spaces, List<ExternalPack> externalPacks) {
            this(items, noteBlockStates, glyphs, advancements, modelData, liquidSlots, hudGlyphs, spaces, externalPacks,
                    null);
        }
    }

    /** Deletes {@code packDir} and writes the pack for {@code input} into it. */
    public Result compile(Path packDir, Input input) throws IOException {
        // Opened first: an item may be drawn with a merged pack's model or item definition, and is
        // not reported missing when one of them has it.
        List<PackSource> sources = new ArrayList<>();
        try {
            for (ExternalPack pack : input.externalPacks().stream().sorted(Comparator.comparing(ExternalPack::name)).toList()) {
                sources.add(PackSource.open(pack));
            }
            return compile(packDir, input, sources);
        } finally {
            for (PackSource source : sources) {
                source.close();
            }
        }
    }

    private Result compile(Path packDir, Input input, List<PackSource> sources) throws IOException {
        Collection<ItemDefinition> items = input.items();
        Map<String, NoteBlockState> noteBlockStates = input.noteBlockStates();
        Map<EmojiDefinition, Integer> glyphs = input.glyphs();
        Collection<AdvancementDefinition> advancements = input.advancements();
        Map<String, Integer> modelData = input.modelData();
        Run run = new Run(packDir, sources);
        run.reset();
        run.writeText("pack.mcmeta", GSON.toJson(settings.mcmeta()) + "\n");

        // Sorted, so that when two items fight over one path the same one wins on every build.
        List<ItemDefinition> ordered = items.stream()
                .sorted(Comparator.comparing(ItemDefinition::fullId))
                .toList();
        for (ItemDefinition item : ordered) {
            run.item(item);
        }
        run.noteBlockStates(ordered, noteBlockStates);
        run.modelData(ordered, modelData);
        run.liquids(ordered, input.liquidSlots());
        run.font(glyphs, input.hudGlyphs(), input.spaces());
        run.advancementBackgrounds(advancements);
        if (input.contentsDir() != null) {
            run.sounds(input.contentsDir());
        }
        for (PackSource source : sources) {
            run.merge(source);
        }
        run.atlas();
        run.overlayShared();
        run.overlayEntries(settings.mcmeta());
        return new Result(run.itemDefinitions, run.models, run.textures, run.customBlockStates,
                run.glyphs, run.externalFiles, run.sounds, run.problems);
    }

    /** State for one compile. */
    private static final class Run {

        private final Path packDir;
        /** The merged packs, in merge order. */
        private final List<PackSource> sources;
        private final List<String> problems = new ArrayList<>();
        /** Pack-relative path of each file written, and the item it was written for. */
        private final Map<String, String> placed = new HashMap<>();
        /** Source files already followed, so shared parents and cycles are read once. */
        private final Set<String> followed = new HashSet<>();
        private final Set<String> namespaces = new HashSet<>();
        /** Textures models draw with that the blocks atlas does not take in by itself. */
        private final Set<String> atlasSprites = new TreeSet<>();

        private int itemDefinitions;
        private int models;
        private int textures;
        private int customBlockStates;
        private int glyphs;
        private int externalFiles;
        private int sounds;
        /** The merged packs' overlay entries, by directory: what pack.mcmeta has to declare. */
        private final Map<String, JsonObject> overlays = new LinkedHashMap<>();
        /** This plugin's own version of each shared file ({@link JsonMerge}), before any pack was merged in. */
        private final Map<String, byte[]> own = new HashMap<>();
        private boolean merging;

        Run(Path packDir, List<PackSource> sources) {
            this.packDir = packDir;
            this.sources = sources;
        }

        void reset() throws IOException {
            if (Files.exists(packDir)) {
                try (Stream<Path> walk = Files.walk(packDir)) {
                    for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                        Files.delete(path);
                    }
                }
            }
            Files.createDirectories(packDir.resolve("assets"));
        }

        void item(ItemDefinition item) throws IOException {
            scaffold(item.namespace());
            Equipment equipment = item.equipment();
            if (equipment != null && equipment.asset() != null) {
                equipmentAsset(item.namespace(), equipment, item.fullId());
            }

            ModelSource source = item.model();
            if (source == null) {
                return;
            }
            String origin = item.fullId();
            // Another namespace is someone else's to supply - vanilla, or another content pack.
            look(item.namespace(), source, origin);

            ModelSource worn = equipment == null ? null : equipment.worn();
            if (source instanceof ModelSource.Definition) {
                // The file is the item definition itself, and already placed as it is.
                if (worn != null) {
                    problems.add(origin + ": equipment.model is not drawn - the item's look is an item definition"
                            + " file (resource.item-model), and that file decides what the head shows too");
                }
            } else {
                if (worn != null) {
                    look(item.namespace(), worn, origin);
                }
                byte[] definition = worn == null
                        ? itemDefinition(source.location())
                        : wornItemDefinition(source.location(), worn.location());
                if (place(item.itemModel().assetPath("items", ".json"), definition, origin)) {
                    itemDefinitions++;
                }
            }
            if (item.placement() instanceof Placement.Crop crop) {
                stages(item, crop, origin);
            }
            if (item.placement() instanceof Placement.Furniture furniture && furniture.animated() != null) {
                bones(item, furniture.animated().model(), origin);
            }
        }

        /**
         * A Blockbench model's bones: each bone's model at {@code <namespace>:<id>/bone_<name>}, with an
         * item definition of the same name for its display, and the model's textures at
         * {@code <namespace>:<id>/tex_<n>}.
         */
        private void bones(ItemDefinition item, AnimatedModel model, String origin) throws IOException {
            for (AnimatedModel.Texture texture : model.textures()) {
                drawnByModel(texture.location());
                if (texture.png().length > 0
                        && place(texture.location().assetPath("textures", ".png"), texture.png(), origin)) {
                    textures++;
                }
            }
            for (AnimatedModel.Bone bone : model.bones()) {
                if (bone.model() == null) {
                    continue;
                }
                ResourceLocation key = AnimatedModel.boneItemModel(item.itemModel(), bone.name());
                String boneOrigin = origin + " bone " + bone.name();
                if (place(key.assetPath("models", ".json"), bone.model().getBytes(StandardCharsets.UTF_8), boneOrigin)) {
                    models++;
                }
                if (place(key.assetPath("items", ".json"), itemDefinition(key), boneOrigin)) {
                    itemDefinitions++;
                }
            }
        }

        /**
         * A crop's stages: each stage's model, and an item definition for it at
         * {@code <namespace>:<id>/stage_<n>} - the key the crop's display is switched to as it grows.
         */
        private void stages(ItemDefinition item, Placement.Crop crop, String origin) throws IOException {
            for (int stage = 0; stage < crop.stages().size(); stage++) {
                ModelSource model = crop.stages().get(stage);
                String stageOrigin = origin + " stage " + stage;
                look(item.namespace(), model, stageOrigin);
                ResourceLocation key = Placement.Crop.stageItemModel(item.itemModel(), stage);
                if (place(key.assetPath("items", ".json"), itemDefinition(model.location()), stageOrigin)) {
                    itemDefinitions++;
                }
            }
        }

        /** Writes or copies a model and the textures it needs from its namespace. */
        private void look(String namespace, ModelSource source, String origin) throws IOException {
            switch (source) {
                case ModelSource.Provided provided -> {
                    if (provided.location().namespace().equals(namespace)) {
                        copyModel(provided.sourceRoot(), provided.location(), origin);
                    }
                }
                case ModelSource.Generated generated -> {
                    generateModel(generated, origin);
                    for (ResourceLocation texture : generated.textures().values()) {
                        drawnByModel(texture);
                        if (texture.namespace().equals(namespace)) {
                            copyTexture(generated.sourceRoot(), texture, origin);
                        }
                    }
                }
                case ModelSource.Inline inline -> {
                    inlineTextures(inline, origin);
                    if (place(inline.location().assetPath("models", ".json"), inline.json().getBytes(StandardCharsets.UTF_8),
                            origin)) {
                        models++;
                    }
                }
                case ModelSource.Definition definition -> definitionFile(namespace, definition, origin);
            }
        }

        /**
         * An item definition file, byte for byte, and the models it names in its namespace. With no
         * such file in the content pack, a merged pack has to supply it.
         */
        private void definitionFile(String namespace, ModelSource.Definition definition, String origin)
                throws IOException {
            ResourceLocation location = definition.location();
            if (!location.namespace().equals(namespace)) {
                return;
            }
            String relative = location.assetPath("items", ".json");
            Path file = definition.sourceRoot().resolve("items").resolve(location.path() + ".json");
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                if (!supplied(relative)) {
                    problems.add(origin + ": item definition " + location + " not found, expected "
                            + shown(definition.sourceRoot(), file) + " or in a merged pack");
                }
                return;
            }
            byte[] bytes = Files.readAllBytes(file);
            JsonElement json;
            try {
                json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            } catch (JsonParseException exception) {
                problems.add(origin + ": " + shown(definition.sourceRoot(), file) + " is not valid JSON - "
                        + exception.getMessage());
                return;
            }
            if (!(json instanceof JsonObject object) || !(object.get("model") instanceof JsonObject)) {
                problems.add(origin + ": " + shown(definition.sourceRoot(), file) + " has no 'model' object - an item"
                        + " definition needs one");
                return;
            }
            if (place(relative, bytes, origin)) {
                itemDefinitions++;
            }
            Set<ResourceLocation> named = new java.util.TreeSet<>(Comparator.comparing(ResourceLocation::toString));
            namedModels(object.get("model"), named, location, origin);
            for (ResourceLocation model : named) {
                if (model.namespace().equals(namespace)) {
                    copyModel(definition.sourceRoot(), model, origin);
                }
            }
        }

        /**
         * Every model an item model names, at any depth: a plain model's {@code model}, a special
         * model's {@code base}, inside conditions, selects, dispatches and composites alike.
         */
        private void namedModels(JsonElement element, Set<ResourceLocation> named, ResourceLocation definition,
                                 String origin) {
            if (element instanceof JsonArray array) {
                array.forEach(child -> namedModels(child, named, definition, origin));
                return;
            }
            if (!(element instanceof JsonObject object)) {
                return;
            }
            String type = object.get("type") instanceof JsonElement value && value.isJsonPrimitive()
                    ? value.getAsString() : "";
            String key = switch (type) {
                case "model", "minecraft:model" -> "model";
                case "special", "minecraft:special" -> "base";
                default -> null;
            };
            if (key != null && object.get(key) instanceof JsonElement value && value.isJsonPrimitive()) {
                ResourceLocation model = reference(value, definition, origin);
                if (model != null) {
                    named.add(model);
                }
            }
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                if (entry.getValue().isJsonObject() || entry.getValue().isJsonArray()) {
                    namedModels(entry.getValue(), named, definition, origin);
                }
            }
        }

        /** Whether a merged pack has this file - in its base assets/ or in one of its overlays. */
        private boolean supplied(String relative) throws IOException {
            for (PackSource source : sources) {
                if (source.has(relative)) {
                    return true;
                }
                for (String overlay : source.overlays().keySet()) {
                    if (source.has(overlay + "/" + relative)) {
                        return true;
                    }
                }
            }
            return false;
        }

        /** The first merged pack's copy of a base file, or null. */
        private @Nullable byte[] supplier(String relative) throws IOException {
            for (PackSource source : sources) {
                byte[] bytes = source.read(relative);
                if (bytes != null) {
                    return bytes;
                }
            }
            return null;
        }

        /**
         * {@code assets/<namespace>/equipment/<asset>.json}: the armour layers the {@code equippable}
         * component's {@code asset_id} names, one entry per layer texture found, and those textures.
         * Every piece of a set names the same asset; the file is written once.
         */
        private void equipmentAsset(String namespace, Equipment equipment, String origin) throws IOException {
            ResourceLocation asset = equipment.asset();
            if (!asset.namespace().equals(namespace) || equipment.layers().isEmpty()) {
                // Another namespace's asset - minecraft:netherite - is only referenced; one with no
                // textures was reported by the loader.
                return;
            }
            JsonObject layers = new JsonObject();
            for (String layer : equipment.layers()) {
                JsonObject entry = new JsonObject();
                entry.addProperty("texture", asset.toString());
                JsonArray list = new JsonArray();
                list.add(entry);
                layers.add(layer, list);
                copyTexture(equipment.sourceRoot(), Equipment.layerTexture(asset, layer), origin);
            }
            JsonObject descriptor = new JsonObject();
            descriptor.add("layers", layers);
            place(asset.assetPath("equipment", ".json"), json(descriptor), origin);
        }

        /**
         * An item definition that draws {@code worn} on a player's head and {@code model} everywhere
         * else - the inventory, a hand, the ground.
         */
        private static byte[] wornItemDefinition(ResourceLocation model, ResourceLocation worn) {
            JsonObject onHead = new JsonObject();
            onHead.addProperty("type", "minecraft:model");
            onHead.addProperty("model", worn.toString());
            JsonObject headCase = new JsonObject();
            headCase.addProperty("when", "head");
            headCase.add("model", onHead);
            JsonArray cases = new JsonArray();
            cases.add(headCase);
            JsonObject elsewhere = new JsonObject();
            elsewhere.addProperty("type", "minecraft:model");
            elsewhere.addProperty("model", model.toString());
            JsonObject select = new JsonObject();
            select.addProperty("type", "minecraft:select");
            select.addProperty("property", "minecraft:display_context");
            select.add("cases", cases);
            select.add("fallback", elsewhere);
            JsonObject definition = new JsonObject();
            definition.add("model", select);
            return json(definition);
        }

        /**
         * {@code assets/minecraft/items/<material>.json} for each plain material an item is made of:
         * the item's own look for its number, vanilla's for any other ({@link ModelDataDispatch}).
         */
        void modelData(List<ItemDefinition> items, Map<String, Integer> numbers) throws IOException {
            Map<String, SortedMap<Integer, JsonObject>> byMaterial = new TreeMap<>();
            for (ItemDefinition item : items) {
                Integer number = numbers.get(item.fullId());
                if (number == null || !ModelDataDispatch.dispatchable(item.material())) {
                    continue;
                }
                String relative = item.itemModel().assetPath("items", ".json");
                Path definition = packDir.resolve(relative);
                byte[] bytes = Files.isRegularFile(definition) ? Files.readAllBytes(definition) : supplier(relative);
                JsonObject model;
                try {
                    model = bytes != null
                            && JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)) instanceof JsonObject root
                            && root.get("model") instanceof JsonObject drawn ? drawn : null;
                } catch (JsonParseException exception) {
                    model = null;
                }
                if (model == null) {
                    continue;
                }
                byMaterial.computeIfAbsent(item.material().toLowerCase(Locale.ROOT), material -> new TreeMap<>())
                        .put(number, model);
            }
            for (Map.Entry<String, SortedMap<Integer, JsonObject>> material : byMaterial.entrySet()) {
                place("assets/minecraft/items/" + material.getKey() + ".json",
                        json(ModelDataDispatch.definition(material.getKey(), material.getValue())), "custom_model_data");
            }
        }

        private static byte[] itemDefinition(ResourceLocation model) {
            JsonObject target = new JsonObject();
            target.addProperty("type", "minecraft:model");
            target.addProperty("model", model.toString());
            JsonObject definition = new JsonObject();
            definition.add("model", target);
            return json(definition);
        }

        /**
         * A tab background the client reads straight from {@code textures/<id>.png} - not from an
         * atlas - when it is one of this plugin's textures; vanilla's are the client's own.
         */
        void advancementBackgrounds(Collection<AdvancementDefinition> advancements) throws IOException {
            for (AdvancementDefinition advancement : advancements) {
                ResourceLocation background = advancement.background();
                if (background != null && background.namespace().equals(advancement.namespace())) {
                    scaffold(advancement.namespace());
                    copyTexture(advancement.sourceRoot(), background, "advancement " + advancement.fullId());
                }
            }
        }

        /**
         * {@code assets/minecraft/font/default.json}: one bitmap glyph per emoji and per HUD icon,
         * each on a private-use character, and the space characters HUDs and menus move text with
         * ({@link Spaces}). In the default font, so the characters draw anywhere text does - chat,
         * signs, books, other plugins' scoreboards, tab lists and menus. The client merges this file
         * with the vanilla one rather than replacing it, so every other character is untouched.
         * Written in character order: the same content gives the same bytes.
         */
        void font(Map<EmojiDefinition, Integer> characters, List<HudGlyph> hudGlyphs, boolean spacesAnyway)
                throws IOException {
            if (characters.isEmpty() && hudGlyphs.isEmpty() && !spacesAnyway) {
                return;
            }
            JsonArray providers = new JsonArray();
            List<Map.Entry<EmojiDefinition, Integer>> ordered = characters.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue())
                    .toList();
            for (Map.Entry<EmojiDefinition, Integer> glyph : ordered) {
                EmojiDefinition emoji = glyph.getKey();
                String origin = "emoji :" + emoji.name() + ":";
                if (emoji.texture().namespace().equals(emoji.namespace())) {
                    copyTexture(emoji.sourceRoot(), emoji.texture(), origin);
                }
                providers.add(bitmap(emoji.texture(), emoji.ascent(), emoji.height(), glyph.getValue()));
                glyphs++;
            }
            for (HudGlyph glyph : hudGlyphs.stream().sorted(Comparator.comparingInt(HudGlyph::character)).toList()) {
                if (glyph.texture().namespace().equals(glyph.namespace())) {
                    copyTexture(glyph.sourceRoot(), glyph.texture(), glyph.origin());
                }
                providers.add(bitmap(glyph.texture(), glyph.ascent(), glyph.height(), glyph.character()));
                glyphs++;
            }
            JsonObject spaces = new JsonObject();
            spaces.addProperty("type", "space");
            JsonObject advances = new JsonObject();
            Spaces.advances().forEach(advances::addProperty);
            spaces.add("advances", advances);
            providers.add(spaces);

            JsonObject font = new JsonObject();
            font.add("providers", providers);
            Files.createDirectories(packDir.resolve("assets/minecraft/font"));
            place("assets/minecraft/font/default.json", json(font), "font");
        }

        private static JsonObject bitmap(ResourceLocation texture, int ascent, int height, int character) {
            JsonObject provider = new JsonObject();
            provider.addProperty("type", "bitmap");
            provider.addProperty("file", texture + ".png");
            provider.addProperty("ascent", ascent);
            provider.addProperty("height", height);
            JsonArray chars = new JsonArray();
            chars.add(new String(Character.toChars(character)));
            provider.add("chars", chars);
            return provider;
        }

        /**
         * Liquids: the two parent models their own models sit under, and
         * {@code assets/minecraft/blockstates/tripwire.json} pointing each liquid's source and flowing
         * states at its models - and every other state at vanilla's, so string is untouched.
         */
        void liquids(List<ItemDefinition> ordered, Map<String, Integer> slots) throws IOException {
            if (slots.isEmpty()) {
                return;
            }
            scaffold(LiquidModels.NAMESPACE);
            place(LiquidModels.SOURCE_PARENT.assetPath("models", ".json"),
                    json(LiquidModels.parent(LiquidModels.SOURCE_HEIGHT)), "liquids");
            place(LiquidModels.FLOWING_PARENT.assetPath("models", ".json"),
                    json(LiquidModels.parent(LiquidModels.FLOWING_HEIGHT)), "liquids");
            models += 2;

            Map<TripwireState, ResourceLocation> drawn = new HashMap<>();
            for (ItemDefinition item : ordered) {
                Integer slot = slots.get(item.fullId());
                if (slot == null || !(item.placement() instanceof Placement.Liquid liquid)) {
                    continue;
                }
                String origin = item.fullId() + " liquid";
                look(item.namespace(), liquid.source(), origin);
                if (liquid.flowing() != liquid.source()) {
                    look(item.namespace(), liquid.flowing(), origin);
                }
                drawn.put(TripwireState.source(slot), liquid.source().location());
                drawn.put(TripwireState.flowing(slot), liquid.flowing().location());
                customBlockStates += 2;
            }
            Files.createDirectories(packDir.resolve("assets/minecraft/blockstates"));
            place("assets/minecraft/blockstates/tripwire.json", json(LiquidModels.blockstate(drawn)), "liquids");
        }

        /**
         * {@code assets/minecraft/blockstates/note_block.json}: which model the client draws for every
         * note block state.
         *
         * <p>It has to live in the minecraft namespace - the client finds a block's states by the
         * block's own id, and the block is {@code minecraft:note_block} - and it has to name a model
         * for every state, all {@code 23 x 25 x 2}, or the client draws the missing-model cube for the
         * ones it leaves out. States that belong to a custom block get its model; every other state,
         * the vanilla state included, keeps the plain note block. Written in a fixed order, so the same
         * assignments always give the same bytes.</p>
         */
        void noteBlockStates(List<ItemDefinition> ordered, Map<String, NoteBlockState> states) throws IOException {
            if (states.isEmpty()) {
                return;
            }
            Map<String, ResourceLocation> models = new HashMap<>();
            for (ItemDefinition item : ordered) {
                if (item.model() != null) {
                    models.put(item.fullId(), item.model().location());
                }
            }
            Map<String, String> byVariant = new HashMap<>();
            for (Map.Entry<String, NoteBlockState> entry : new TreeMap<>(states).entrySet()) {
                ResourceLocation model = models.get(entry.getKey());
                if (model == null) {
                    problems.add(entry.getKey() + ": has a note block state but no model - it will look like a note block");
                    continue;
                }
                byVariant.put(entry.getValue().variantKey(), model.toString());
                customBlockStates++;
            }

            JsonObject variants = new JsonObject();
            for (String instrument : NoteBlockState.ALL_INSTRUMENTS) {
                for (int note = 0; note < NoteBlockState.NOTES; note++) {
                    for (boolean powered : new boolean[] {false, true}) {
                        String key = new NoteBlockState(instrument, note, powered).variantKey();
                        JsonObject variant = new JsonObject();
                        variant.addProperty("model", byVariant.getOrDefault(key, VANILLA_NOTE_BLOCK));
                        variants.add(key, variant);
                    }
                }
            }
            JsonObject root = new JsonObject();
            root.add("variants", variants);
            Files.createDirectories(packDir.resolve("assets/minecraft/blockstates"));
            place("assets/minecraft/blockstates/note_block.json", json(root), "note blocks");
        }

        /**
         * Copies another plugin's pack in, from its folder or its zip: its {@code assets/} and its
         * overlays ({@link PackSource}), every file as it is. Runs after this plugin's own files, so a
         * path both supply keeps this plugin's version and the clash is reported - except for the
         * shared lists {@link JsonMerge} combines, where the other pack's entries are added to ours
         * and only true ID collisions are reported.
         */
        void merge(PackSource source) throws IOException {
            String origin = source.name() + " pack";
            merging = true;
            try {
                mergeFiles(source, origin);
            } finally {
                merging = false;
            }
            source.problems().forEach(problem -> problems.add(origin + ": " + problem));
            for (Map.Entry<String, JsonObject> overlay : source.overlays().entrySet()) {
                JsonObject earlier = overlays.putIfAbsent(overlay.getKey(), overlay.getValue());
                if (earlier != null && !earlier.equals(overlay.getValue())) {
                    problems.add(origin + ": overlay " + overlay.getKey() + " is declared by an earlier pack for other"
                            + " versions - keeping the first declaration, both packs' files share the folder");
                }
            }
        }

        private void mergeFiles(PackSource source, String origin) throws IOException {
            for (String file : source.files()) {
                byte[] theirs = source.read(file);
                String owner = placed.get(file);
                String inner = PackSource.inner(file);
                if (owner != null && JsonMerge.mergeable(inner)) {
                    Path target = packDir.resolve(file);
                    JsonMerge.Result merged = JsonMerge.merge(inner, Files.readAllBytes(target), theirs);
                    if (merged.merged() != null) {
                        Files.write(target, merged.merged());
                        merged.collisions().forEach(collision -> problems.add(origin + ": " + collision));
                        externalFiles++;
                        continue;
                    }
                }
                if (place(file, theirs, origin)) {
                    externalFiles++;
                }
            }
        }

        /**
         * A merged overlay replaces a base file for the clients it is for. When it carries a file this
         * plugin shares in the base - a vanilla item's {@code custom_model_data} definition, as
         * ItemsAdder's overlays do for 1.21.6 and later, the default font, an atlas, a
         * {@code sounds.json} - those clients would never see this plugin's part of it; so ours is
         * joined into the overlay's copy as well ({@link JsonMerge#mergeIntoTheirs}). On a clash the
         * overlay's own entry is kept: the pack draws exactly as it was made, and an item of ours is
         * drawn by its item_model whatever its number does.
         */
        void overlayShared() throws IOException {
            for (String file : new TreeSet<>(placed.keySet())) {
                String overlay = PackSource.overlayOf(file);
                if (overlay == null || !overlays.containsKey(overlay)) {
                    continue;
                }
                String inner = PackSource.inner(file);
                byte[] ours = own.get(inner);
                if (ours == null || !JsonMerge.mergeable(inner)) {
                    continue;
                }
                Path target = packDir.resolve(file);
                JsonMerge.Result merged = JsonMerge.mergeIntoTheirs(inner, ours, Files.readAllBytes(target));
                if (merged.merged() != null) {
                    Files.write(target, merged.merged());
                    merged.collisions().forEach(collision -> problems.add(placed.get(file) + ": " + overlay + "/"
                            + collision));
                }
            }
        }

        /** pack.mcmeta again, declaring the merged packs' overlays, each entry as its pack wrote it. */
        void overlayEntries(JsonObject mcmeta) throws IOException {
            if (overlays.isEmpty()) {
                return;
            }
            JsonArray entries = new JsonArray();
            overlays.values().forEach(entries::add);
            JsonObject section = new JsonObject();
            section.add("entries", entries);
            JsonObject root = mcmeta.deepCopy();
            root.add("overlays", section);
            Files.write(packDir.resolve("pack.mcmeta"), (GSON.toJson(root) + "\n").getBytes(StandardCharsets.UTF_8));
        }

        /**
         * Each content pack's sounds, as they are: {@code contents/<folder>/sounds.json} becomes
         * {@code assets/<folder>/sounds.json} - the folder name is the namespace its sound events are
         * played by - and every {@code .ogg} under {@code contents/<folder>/sounds/} goes beside it at
         * the same path, never re-encoded: its sample rate and channels are the file's.
         */
        void sounds(Path contentsDir) throws IOException {
            if (!Files.isDirectory(contentsDir)) {
                return;
            }
            List<Path> folders;
            try (Stream<Path> list = Files.list(contentsDir)) {
                folders = list.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)).sorted().toList();
            }
            for (Path folder : folders) {
                String namespace = folder.getFileName().toString();
                Path index = folder.resolve("sounds.json");
                Path files = folder.resolve("sounds");
                boolean hasIndex = Files.isRegularFile(index, LinkOption.NOFOLLOW_LINKS);
                if (!hasIndex && !Files.isDirectory(files, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                String origin = namespace + " sounds";
                if (!ResourceLocation.isValidNamespace(namespace)) {
                    problems.add(origin + ": '" + namespace + "' is not a valid namespace (a-z 0-9 _ . -) - its"
                            + " sounds are not packed");
                    continue;
                }
                if (hasIndex) {
                    byte[] bytes = Files.readAllBytes(index);
                    try {
                        if (!(JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)) instanceof JsonObject events)) {
                            throw new JsonParseException("not a JSON object of sound events");
                        }
                        if (place("assets/" + namespace + "/sounds.json", bytes, origin)) {
                            sounds++;
                        }
                        String own = "assets/" + namespace + "/sounds/";
                        String report = SoundNames.describe(namespace + "/sounds.json", namespace,
                                SoundNames.bareButOwn(events, namespace, path -> {
                                    try {
                                        return path.startsWith(own)
                                                && Files.isRegularFile(files.resolve(path.substring(own.length())))
                                                || placed.containsKey(path) || supplied(path);
                                    } catch (IOException exception) {
                                        return false;
                                    }
                                }));
                        if (report != null) {
                            problems.add(origin + ": " + report);
                        }
                    } catch (JsonParseException exception) {
                        problems.add(origin + ": sounds.json is not valid - " + exception.getMessage()
                                + "; the client would drop every sound of " + namespace + ", so it is left out");
                    }
                }
                if (!Files.isDirectory(files, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                List<Path> oggs;
                try (Stream<Path> walk = Files.walk(files)) {
                    oggs = walk.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                            .filter(path -> path.getFileName().toString().endsWith(".ogg"))
                            .sorted()
                            .toList();
                }
                for (Path ogg : oggs) {
                    byte[] bytes = Files.readAllBytes(ogg);
                    if (!startsWith(bytes, OGG_SIGNATURE)) {
                        problems.add(origin + ": " + shown(folder, ogg) + " is not an Ogg Vorbis file - left out");
                        continue;
                    }
                    Path relative = files.relativize(ogg);
                    if (place("assets/" + namespace + "/sounds/" + relative.toString()
                            .replace(relative.getFileSystem().getSeparator(), "/"), bytes, origin)) {
                        sounds++;
                    }
                }
            }
        }

        /** The vanilla layout for a namespace, created even while it is still empty. */
        private void scaffold(String namespace) throws IOException {
            if (namespaces.add(namespace)) {
                Path root = packDir.resolve("assets").resolve(namespace);
                for (String folder : List.of("items", "models", "textures")) {
                    Files.createDirectories(root.resolve(folder));
                }
            }
        }

        private void copyModel(Path sourceRoot, ResourceLocation model, String origin) throws IOException {
            if (!followed.add("model|" + sourceRoot + "|" + model)) {
                return;
            }
            Path source = sourceRoot.resolve("models").resolve(model.path() + ".json");
            if (!Files.isRegularFile(source)) {
                // Not this content pack's to copy when a merged pack has it: that copy goes in as it is.
                if (!supplied(model.assetPath("models", ".json"))) {
                    problems.add(origin + ": model " + model + " not found, expected " + shown(sourceRoot, source));
                }
                return;
            }
            byte[] bytes = Files.readAllBytes(source);
            if (place(model.assetPath("models", ".json"), bytes, origin)) {
                models++;
            }

            JsonObject json;
            try {
                JsonElement parsed = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
                if (!parsed.isJsonObject()) {
                    problems.add(origin + ": model " + model + " is not a JSON object");
                    return;
                }
                json = parsed.getAsJsonObject();
            } catch (JsonParseException exception) {
                problems.add(origin + ": model " + model + " is not valid JSON - " + exception.getMessage());
                return;
            }

            // Inside a model a bare location means minecraft:, as it does to the client.
            ResourceLocation parent = reference(json.get("parent"), model, origin);
            if (parent != null && parent.namespace().equals(model.namespace())) {
                copyModel(sourceRoot, parent, origin);
            }
            if (json.get("textures") instanceof JsonObject textureMap) {
                for (Map.Entry<String, JsonElement> entry : textureMap.entrySet()) {
                    ResourceLocation texture = reference(entry.getValue(), model, origin);
                    if (texture != null) {
                        drawnByModel(texture);
                    }
                    if (texture != null && texture.namespace().equals(model.namespace())) {
                        copyTexture(sourceRoot, texture, origin);
                    }
                }
            }
        }

        /**
         * Notes a texture a model draws with. The client stitches every texture in a {@code block/}
         * or {@code item/} folder - of any namespace - onto the blocks atlas, the sheet block and
         * item models are drawn from; one anywhere else ({@code furniture/}, {@code crop/}, a
         * Blockbench model's own) is not on it unless the pack names it, and the model is drawn
         * with the magenta and black "missing texture" squares.
         */
        private void drawnByModel(ResourceLocation texture) {
            String path = texture.path();
            if (!path.startsWith("block/") && !path.startsWith("item/")) {
                atlasSprites.add(texture.toString());
            }
        }

        private void inlineTextures(ModelSource.Inline inline, String origin) {
            try {
                if (JsonParser.parseString(inline.json()) instanceof JsonObject json
                        && json.get("textures") instanceof JsonObject textureMap) {
                    for (Map.Entry<String, JsonElement> entry : textureMap.entrySet()) {
                        ResourceLocation texture = reference(entry.getValue(), inline.location(), origin);
                        if (texture != null) {
                            drawnByModel(texture);
                        }
                    }
                }
            } catch (JsonParseException exception) {
                // Placed as it is; the client reports a broken model in its own log.
            }
        }

        /**
         * {@code assets/minecraft/atlases/blocks.json}: a {@code single} source for each texture a
         * model draws with outside {@code block/} and {@code item/}. The client adds the sources of
         * every pack's atlas file to vanilla's, so this one lists only those. A merged pack's own
         * atlas file keeps its sources, with these after them.
         */
        void atlas() throws IOException {
            if (atlasSprites.isEmpty()) {
                return;
            }
            String relative = "assets/minecraft/atlases/blocks.json";
            Path target = packDir.resolve(relative);
            JsonObject atlas = new JsonObject();
            JsonArray sources = new JsonArray();
            boolean merged = placed.containsKey(relative);
            if (merged) {
                try {
                    if (JsonParser.parseString(Files.readString(target)) instanceof JsonObject existing) {
                        atlas = existing;
                        if (existing.get("sources") instanceof JsonArray theirs) {
                            sources = theirs;
                        }
                    }
                } catch (JsonParseException exception) {
                    problems.add(placed.get(relative) + ": " + relative + " is not valid JSON - replaced by this"
                            + " plugin's atlas sources");
                }
            }
            JsonArray mine = new JsonArray();
            for (String sprite : atlasSprites) {
                JsonObject single = new JsonObject();
                single.addProperty("type", "minecraft:single");
                single.addProperty("resource", sprite);
                mine.add(single);
                if (!sources.contains(single)) {
                    sources.add(single);
                }
            }
            JsonObject ownAtlas = new JsonObject();
            ownAtlas.add("sources", mine);
            own.put(relative, json(ownAtlas));
            atlas.add("sources", sources);
            if (merged) {
                Files.write(target, json(atlas));
            } else {
                Files.createDirectories(target.getParent());
                place(relative, json(atlas), "atlas");
            }
        }

        private void copyTexture(Path sourceRoot, ResourceLocation texture, String origin) throws IOException {
            if (!followed.add("texture|" + sourceRoot + "|" + texture)) {
                return;
            }
            Path source = sourceRoot.resolve("textures").resolve(texture.path() + ".png");
            if (!Files.isRegularFile(source)) {
                if (!supplied(texture.assetPath("textures", ".png"))) {
                    problems.add(origin + ": texture " + texture + " not found, expected " + shown(sourceRoot, source));
                }
                return;
            }
            byte[] bytes = Files.readAllBytes(source);
            if (!startsWith(bytes, PNG_SIGNATURE)) {
                problems.add(origin + ": " + shown(sourceRoot, source) + " is not a PNG file");
                return;
            }
            if (place(texture.assetPath("textures", ".png"), bytes, origin)) {
                textures++;
            }
            // Animated textures carry their frame timing in a sidecar the client looks for by name.
            Path animation = source.resolveSibling(source.getFileName() + ".mcmeta");
            if (Files.isRegularFile(animation)) {
                place(texture.assetPath("textures", ".png.mcmeta"), Files.readAllBytes(animation), origin);
            }
        }

        private void generateModel(ModelSource.Generated model, String origin) throws IOException {
            JsonObject variables = new JsonObject();
            for (Map.Entry<String, ResourceLocation> texture : model.textures().entrySet()) {
                variables.addProperty(texture.getKey(), texture.getValue().toString());
            }
            JsonObject json = new JsonObject();
            json.addProperty("parent", model.parent().toString());
            json.add("textures", variables);
            if (place(model.location().assetPath("models", ".json"), json(json), origin)) {
                models++;
            }
        }

        /** A location named inside a model; {@code #name} variables and anything malformed yield null. */
        private ResourceLocation reference(JsonElement value, ResourceLocation model, String origin) {
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                return null;
            }
            String raw = value.getAsString();
            if (raw.startsWith("#")) {
                return null;
            }
            try {
                return ResourceLocation.parse(raw, ResourceLocation.MINECRAFT);
            } catch (IllegalArgumentException exception) {
                problems.add(origin + ": model " + model + " refers to '" + raw + "' - " + exception.getMessage());
                return null;
            }
        }

        /**
         * Writes one file into the pack, unless that path is already taken.
         *
         * <p>Two items asking for the same file with the same bytes is normal - a shared parent
         * model - and costs nothing. Different bytes for the same path means one item would
         * silently change how another looks, so the first keeps it and the second is reported.</p>
         *
         * @return true when the file was written by this call
         */
        private boolean place(String relative, byte[] bytes, String origin) throws IOException {
            Path target = packDir.resolve(relative);
            String owner = placed.putIfAbsent(relative, origin);
            if (owner != null) {
                if (!Arrays.equals(Files.readAllBytes(target), bytes)) {
                    problems.add(origin + ": " + relative + " is already supplied by " + owner
                            + " with different content - keeping the first");
                }
                return false;
            }
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
            if (!merging && JsonMerge.mergeable(relative)) {
                own.put(relative, bytes);
            }
            return true;
        }

        void writeText(String relative, String text) throws IOException {
            place(relative, text.getBytes(StandardCharsets.UTF_8), "pack");
        }

        private static byte[] json(JsonObject json) {
            return (GSON.toJson(json) + "\n").getBytes(StandardCharsets.UTF_8);
        }

        private static String shown(Path sourceRoot, Path file) {
            Path relative = sourceRoot.relativize(file);
            return sourceRoot.getFileName() + "/" + relative.toString().replace(relative.getFileSystem().getSeparator(), "/");
        }

        private static boolean startsWith(byte[] bytes, byte[] prefix) {
            return bytes.length >= prefix.length
                    && Arrays.equals(bytes, 0, prefix.length, prefix, 0, prefix.length);
        }
    }
}
