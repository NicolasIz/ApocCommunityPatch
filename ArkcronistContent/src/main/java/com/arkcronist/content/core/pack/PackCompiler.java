package com.arkcronist.content.core.pack;

import com.arkcronist.content.core.animation.AnimatedModel;
import com.arkcronist.content.core.block.NoteBlockState;
import com.arkcronist.content.core.definition.EmojiDefinition;
import com.arkcronist.content.core.definition.Equipment;
import com.arkcronist.content.core.definition.ItemDefinition;
import com.arkcronist.content.core.definition.ModelSource;
import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.definition.ResourceLocation;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

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
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /** What the pack draws for every note block state that is not a custom block's. */
    private static final String VANILLA_NOTE_BLOCK = "minecraft:block/note_block";

    /**
     * What a compile wrote.
     *
     * @param customBlockStates note block states mapped to a custom block's model
     * @param glyphs            emojis written into the font
     * @param externalFiles     files taken from other plugins' packs
     * @param problems          missing or unreadable files and conflicting paths, each naming the
     *                          item that ran into it
     */
    public record Result(int itemDefinitions, int models, int textures, int customBlockStates,
                         int glyphs, int externalFiles, List<String> problems) {

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
        Run run = new Run(packDir);
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
        run.font(glyphs);
        for (ExternalPack pack : externalPacks.stream().sorted(Comparator.comparing(ExternalPack::name)).toList()) {
            run.merge(pack);
        }
        return new Result(run.itemDefinitions, run.models, run.textures, run.customBlockStates,
                run.glyphs, run.externalFiles, run.problems);
    }

    /** State for one compile. */
    private static final class Run {

        private final Path packDir;
        private final List<String> problems = new ArrayList<>();
        /** Pack-relative path of each file written, and the item it was written for. */
        private final Map<String, String> placed = new HashMap<>();
        /** Source files already followed, so shared parents and cycles are read once. */
        private final Set<String> followed = new HashSet<>();
        private final Set<String> namespaces = new HashSet<>();

        private int itemDefinitions;
        private int models;
        private int textures;
        private int customBlockStates;
        private int glyphs;
        private int externalFiles;

        Run(Path packDir) {
            this.packDir = packDir;
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
            if (worn != null) {
                look(item.namespace(), worn, origin);
            }
            byte[] definition = worn == null
                    ? itemDefinition(source.location())
                    : wornItemDefinition(source.location(), worn.location());
            if (place(item.itemModel().assetPath("items", ".json"), definition, origin)) {
                itemDefinitions++;
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
                        if (texture.namespace().equals(namespace)) {
                            copyTexture(generated.sourceRoot(), texture, origin);
                        }
                    }
                }
                case ModelSource.Inline inline -> {
                    if (place(inline.location().assetPath("models", ".json"), inline.json().getBytes(StandardCharsets.UTF_8),
                            origin)) {
                        models++;
                    }
                }
            }
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

        private static byte[] itemDefinition(ResourceLocation model) {
            JsonObject target = new JsonObject();
            target.addProperty("type", "minecraft:model");
            target.addProperty("model", model.toString());
            JsonObject definition = new JsonObject();
            definition.add("model", target);
            return json(definition);
        }

        /**
         * {@code assets/minecraft/font/default.json}: one bitmap glyph per emoji, on a private-use
         * character. In the default font, so the character draws anywhere text does - chat, signs,
         * books, and other plugins' scoreboards and menus. The client merges this file with the
         * vanilla one rather than replacing it, so every other character is untouched. Written in
         * character order: the same emojis give the same bytes.
         */
        void font(Map<EmojiDefinition, Integer> characters) throws IOException {
            if (characters.isEmpty()) {
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
                JsonObject provider = new JsonObject();
                provider.addProperty("type", "bitmap");
                provider.addProperty("file", emoji.texture() + ".png");
                provider.addProperty("ascent", emoji.ascent());
                provider.addProperty("height", emoji.height());
                JsonArray chars = new JsonArray();
                chars.add(new String(Character.toChars(glyph.getValue())));
                provider.add("chars", chars);
                providers.add(provider);
                glyphs++;
            }
            JsonObject font = new JsonObject();
            font.add("providers", providers);
            Files.createDirectories(packDir.resolve("assets/minecraft/font"));
            place("assets/minecraft/font/default.json", json(font), "emojis");
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
         * Copies another plugin's {@code assets/} in. Runs after this plugin's own files, so a path
         * both supply keeps this plugin's version, and {@link #place} reports the clash.
         */
        void merge(ExternalPack pack) throws IOException {
            Path assets = pack.root().resolve("assets");
            if (!Files.isDirectory(assets)) {
                problems.add(pack.name() + " pack: no assets folder in " + pack.root() + " - nothing merged");
                return;
            }
            // Links are not followed: the pack is served publicly, and a link could point anywhere.
            List<Path> files;
            try (Stream<Path> walk = Files.walk(assets)) {
                files = walk.filter(file -> Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)).sorted().toList();
            }
            for (Path file : files) {
                Path relative = pack.root().relativize(file);
                String name = relative.toString().replace(relative.getFileSystem().getSeparator(), "/");
                if (place(name, Files.readAllBytes(file), pack.name() + " pack")) {
                    externalFiles++;
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
                problems.add(origin + ": model " + model + " not found, expected " + shown(sourceRoot, source));
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
                    if (texture != null && texture.namespace().equals(model.namespace())) {
                        copyTexture(sourceRoot, texture, origin);
                    }
                }
            }
        }

        private void copyTexture(Path sourceRoot, ResourceLocation texture, String origin) throws IOException {
            if (!followed.add("texture|" + sourceRoot + "|" + texture)) {
                return;
            }
            Path source = sourceRoot.resolve("textures").resolve(texture.path() + ".png");
            if (!Files.isRegularFile(source)) {
                problems.add(origin + ": texture " + texture + " not found, expected " + shown(sourceRoot, source));
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
