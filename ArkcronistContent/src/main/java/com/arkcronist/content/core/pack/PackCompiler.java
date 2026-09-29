package com.arkcronist.content.core.pack;

import com.arkcronist.content.core.block.NoteBlockState;
import com.arkcronist.content.core.definition.ItemDefinition;
import com.arkcronist.content.core.definition.ModelSource;
import com.arkcronist.content.core.definition.ResourceLocation;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
     * @param problems          missing or unreadable files and conflicting paths, each naming the
     *                          item that ran into it
     */
    public record Result(int itemDefinitions, int models, int textures, int customBlockStates,
                         List<String> problems) {

        public Result {
            problems = List.copyOf(problems);
        }
    }

    private final PackSettings settings;

    public PackCompiler(PackSettings settings) {
        this.settings = settings;
    }

    /**
     * Deletes {@code packDir} and writes the pack for {@code items} into it.
     *
     * @param noteBlockStates the state each custom block is drawn through, by full id; empty leaves
     *                        the vanilla note block untouched
     */
    public Result compile(Path packDir, Collection<ItemDefinition> items,
                          Map<String, NoteBlockState> noteBlockStates) throws IOException {
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
        return new Result(run.itemDefinitions, run.models, run.textures, run.customBlockStates, run.problems);
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

            ModelSource source = item.model();
            if (source == null) {
                return;
            }
            String origin = item.fullId();
            switch (source) {
                case ModelSource.Provided provided -> {
                    // Another namespace is someone else's to supply - vanilla, or another content pack.
                    if (provided.location().namespace().equals(item.namespace())) {
                        copyModel(provided.sourceRoot(), provided.location(), origin);
                    }
                }
                case ModelSource.Generated generated -> {
                    generateModel(generated, origin);
                    for (ResourceLocation texture : generated.textures().values()) {
                        if (texture.namespace().equals(item.namespace())) {
                            copyTexture(generated.sourceRoot(), texture, origin);
                        }
                    }
                }
            }

            JsonObject target = new JsonObject();
            target.addProperty("type", "minecraft:model");
            target.addProperty("model", source.location().toString());
            JsonObject definition = new JsonObject();
            definition.add("model", target);
            if (place(item.itemModel().assetPath("items", ".json"), json(definition), origin)) {
                itemDefinitions++;
            }
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
