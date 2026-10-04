package com.arkcronist.content.bukkit;

import com.arkcronist.content.bukkit.block.BlockRegistry;
import com.arkcronist.content.bukkit.block.CustomBlock;
import com.arkcronist.content.bukkit.emoji.EmojiRegistry;
import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import com.arkcronist.content.bukkit.pack.PackDelivery;
import com.arkcronist.content.core.allocation.StableAllocator;
import com.arkcronist.content.core.block.NoteBlockAllocator;
import com.arkcronist.content.core.block.NoteBlockState;
import com.arkcronist.content.core.definition.EmojiDefinition;
import com.arkcronist.content.core.definition.ItemDefinition;
import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.importer.ImportReport;
import com.arkcronist.content.core.importer.ItemsAdderImporter;
import com.arkcronist.content.core.loader.ContentLoader;
import com.arkcronist.content.core.loader.ExampleUpdates;
import com.arkcronist.content.core.loader.LoadReport;
import com.arkcronist.content.core.pack.ExternalPack;
import com.arkcronist.content.core.pack.PackArtifact;
import com.arkcronist.content.core.pack.PackCompiler;
import com.arkcronist.content.core.pack.PackZipper;
import com.arkcronist.content.core.upload.PackUploader;
import com.arkcronist.content.core.upload.UploadSettings;

import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Rebuilds everything from the contents folder: items, pack folder, zip, hash - then puts the
 * result live.
 *
 * <p>Thread discipline is the point of this class. Every step that touches the disk runs on one
 * dedicated worker thread; only the last step, which swaps the item registry and sends the pack to
 * online players, is handed back to the server thread, and it does no I/O at all. A rebuild of any
 * size therefore costs the server's tick nothing but that final swap.</p>
 *
 * <pre>
 *   worker  : load contents/*.yml -> assign note block states and emoji characters
 *             -> compile pack/ -> zip resource_pack.zip -> SHA-1
 *             -> find its link: the built-in server, http.external-url, or an upload (upload:)
 *   main    : swap item, block and emoji registries and live pack, resend to online players
 * </pre>
 *
 * <p>An upload's network waits do not hold the worker either: the request goes out through the
 * HTTP client's own threads, and the stages after it are queued back onto the worker.</p>
 *
 * <p>The ItemsAdder import writes into contents/, so it runs on the same worker and in the same
 * queue: never while a rebuild is reading the files it writes.</p>
 *
 * <p>Rebuilds never overlap. Each one starts only after the previous has fully finished, including
 * its main-thread step, so two quick reloads cannot interleave their writes into the same folder.
 * A rebuild that fails leaves the previous items and pack live and untouched.</p>
 */
public final class ContentPipeline {

    /** The demo pack as the first versions shipped it. */
    private static final List<String> FIRST_EXAMPLES = List.of(
            "contents/demo/items.yml",
            "contents/demo/models/item/ruby.json",
            "contents/demo/textures/item/ruby.png",
            "contents/demo/textures/item/ruby_sword.png",
            "contents/demo/blocks.yml",
            "contents/demo/textures/block/ruby_block.png",
            "contents/demo/models/furniture/ruby_pedestal.json",
            "contents/demo/textures/furniture/pedestal_stone.png",
            "contents/demo/models/furniture/ruby_crate.json",
            "contents/demo/textures/furniture/ruby_crate.png",
            "contents/demo/furniture.yml",
            "contents/demo/models/furniture/ruby_chest.bbmodel",
            "contents/demo/models/furniture/ruby_bed.json",
            "contents/demo/textures/furniture/ruby_bed.png",
            "contents/demo/crops.yml",
            "contents/demo/textures/item/ruby_seeds.png",
            "contents/demo/textures/crop/ruby_stage_0.png",
            "contents/demo/textures/crop/ruby_stage_1.png",
            "contents/demo/textures/crop/ruby_stage_2.png",
            "contents/demo/textures/crop/ruby_stage_3.png",
            "contents/demo/emojis.yml",
            "contents/demo/textures/emoji/ruby.png",
            "contents/demo/textures/emoji/heart.png");

    /**
     * The examples each later version added. A server that already has a contents/ folder gets
     * these on upgrade (see {@link ExampleUpdates}); a new one gets them with the rest.
     */
    private static final Map<String, List<String>> ADDED_EXAMPLES = Map.of(
            "1.4.0", List.of(
                    "contents/demo/armor.yml",
                    "contents/demo/textures/entity/equipment/humanoid/ruby_armor.png",
                    "contents/demo/textures/entity/equipment/humanoid_leggings/ruby_armor.png",
                    "contents/demo/textures/item/ruby_helmet.png",
                    "contents/demo/textures/item/ruby_chestplate.png",
                    "contents/demo/textures/item/ruby_leggings.png",
                    "contents/demo/textures/item/ruby_boots.png",
                    "contents/demo/models/item/ruby_helmet_worn.json",
                    "contents/demo/textures/item/ruby_helmet_worn.png"));

    /** Copied into contents/ the first time the plugin starts. */
    private static final List<String> EXAMPLES = Stream.concat(FIRST_EXAMPLES.stream(),
            ADDED_EXAMPLES.values().stream().flatMap(List::stream)).toList();

    /**
     * What one rebuild did.
     *
     * @param changed  whether the pack's content moved, and players were sent it again
     * @param problems everything that was skipped or looked wrong, already logged
     */
    public record Report(int items, int blocks, int emojis, int files, String sha1Hex, int bytes, boolean changed,
                         @Nullable String url, String hosting, List<String> problems, long millis) {
    }

    /**
     * Where one build of the pack can be downloaded.
     *
     * @param url          what players are sent; null for nowhere
     * @param note         how it got there, for the log
     * @param keepPrevious the upload failed and nothing else serves this build: the live pack stays
     *                     as it was, since its link still works
     */
    private record Hosted(@Nullable String url, String note, boolean keepPrevious) {
    }

    /** Emoji characters: Unicode's private use area, which no font draws until a pack says so. */
    private static final StableAllocator EMOJI_CHARACTERS = new StableAllocator(0xE000, 0xF8FF, "emoji character",
            "to emojis no longer defined; remove those from emoji_characters.json once no sign or book uses them");

    /**
     * One rebuild's progress, handed from stage to stage.
     *
     * @param noteBlocks the state each custom block got, by id
     * @param emojis     every emoji defined, and the character it got once assigned
     */
    private record Build(Map<String, CustomItem> items, Map<String, NoteBlockState> noteBlocks,
                         List<EmojiDefinition> emojiDefinitions, Map<EmojiDefinition, Integer> emojis,
                         List<String> problems, int files, PackArtifact artifact, Hosted hosted) {

        Build withNoteBlocks(Map<String, NoteBlockState> noteBlocks) {
            return new Build(items, noteBlocks, emojiDefinitions, emojis, problems, files, artifact, hosted);
        }

        Build withEmojis(Map<EmojiDefinition, Integer> emojis) {
            return new Build(items, noteBlocks, emojiDefinitions, emojis, problems, files, artifact, hosted);
        }

        Build withFiles(int files) {
            return new Build(items, noteBlocks, emojiDefinitions, emojis, problems, files, artifact, hosted);
        }

        Build withArtifact(PackArtifact artifact) {
            return new Build(items, noteBlocks, emojiDefinitions, emojis, problems, files, artifact, hosted);
        }

        Build withHosted(Hosted hosted) {
            return new Build(items, noteBlocks, emojiDefinitions, emojis, problems, files, artifact, hosted);
        }
    }

    private final ArkContentPlugin plugin;
    private final EngineSettings settings;
    private final ItemRegistry registry;
    private final BlockRegistry blocks;
    private final EmojiRegistry emojis;
    private final PackDelivery delivery;
    private final Logger logger;
    private final PackCompiler compiler;

    private final Path contentsDir;
    private final Path importDir;
    private final Path packDir;
    private final Path zipFile;
    private final Path noteBlockStateFile;
    private final Path emojiCharacterFile;
    /** The plugin version that last offered the demo examples to contents/. */
    private final Path examplesVersionFile;

    private final ExecutorService worker;
    private final Executor mainThread;
    /** Null unless upload: is on. */
    private final @Nullable PackUploader uploader;

    /** Other plugins' packs to merge into ours, by name. Set from the main thread, read by the worker. */
    private final Map<String, ExternalPack> externalPacks = new ConcurrentHashMap<>();

    /** The rebuild in progress, or the last one. Main thread only. */
    private CompletableFuture<?> last = CompletableFuture.completedFuture(null);

    public ContentPipeline(ArkContentPlugin plugin, EngineSettings settings, ItemRegistry registry,
                           BlockRegistry blocks, EmojiRegistry emojis, PackDelivery delivery) {
        this.plugin = plugin;
        this.settings = settings;
        this.registry = registry;
        this.blocks = blocks;
        this.emojis = emojis;
        this.delivery = delivery;
        this.logger = plugin.getLogger();
        this.compiler = new PackCompiler(settings.pack().toPackSettings());

        Path data = plugin.getDataFolder().toPath();
        this.contentsDir = data.resolve("contents");
        this.importDir = data.resolve("import");
        this.packDir = data.resolve("pack");
        this.zipFile = data.resolve("output").resolve("resource_pack.zip");
        this.noteBlockStateFile = data.resolve("data").resolve("note_block_states.json");
        this.emojiCharacterFile = data.resolve("data").resolve("emoji_characters.json");
        this.examplesVersionFile = data.resolve("data").resolve("examples_version.txt");

        this.worker = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ArkContent-Worker");
            thread.setDaemon(true);
            return thread;
        });
        // Once the plugin is disabled the scheduler refuses new tasks; a rebuild finishing during
        // shutdown simply never goes live.
        this.mainThread = task -> {
            if (plugin.isEnabled()) {
                plugin.getServer().getScheduler().runTask(plugin, task);
            }
        };
        UploadSettings upload = settings.upload();
        this.uploader = upload == null ? null : new PackUploader(upload, PackUploader.defaultClient(upload),
                data.resolve("data").resolve("upload.json"), worker,
                plugin.getName() + "/" + plugin.getPluginMeta().getVersion());
    }

    /** The thread that does all of this plugin's disk work. */
    public Executor worker() {
        return worker;
    }

    /** Runs a task on the server thread. */
    public Executor mainThread() {
        return mainThread;
    }

    /**
     * Starts a rebuild once any running one has finished. Call from the main thread.
     *
     * @return completes on the main thread once the new items and pack are live
     */
    public CompletableFuture<Report> rebuild() {
        CompletableFuture<Report> next = last
                .handle((ignored, error) -> null)
                .thenComposeAsync(ignored -> stages(), worker);
        next.whenComplete((report, error) -> {
            if (error != null) {
                logger.log(Level.SEVERE, "Rebuild failed - the previous items and pack stay live.", error);
            }
        });
        last = next;
        return next;
    }

    /**
     * Converts the ItemsAdder packs in import/ into content files under contents/ - see
     * {@link ItemsAdderImporter}. The reading, converting and copying all happen on the worker, in
     * line with rebuilds: never while one is reading contents/. It does not rebuild; the caller
     * decides whether to. Call from the main thread.
     *
     * @return completes on the worker, with the report already logged
     */
    public CompletableFuture<ImportReport> importFromItemsAdder() {
        return exclusive(() -> {
            ImportReport report = new ItemsAdderImporter().run(importDir, contentsDir);
            logImport(report);
            return report;
        });
    }

    /**
     * Runs {@code task} on the worker once any rebuild in progress has finished; the next rebuild
     * waits for it in turn. Main thread.
     */
    private <T> CompletableFuture<T> exclusive(Callable<T> task) {
        CompletableFuture<T> next = last
                .handle((ignored, error) -> null)
                .thenApplyAsync(ignored -> {
                    try {
                        return task.call();
                    } catch (Exception exception) {
                        throw new CompletionException(exception);
                    }
                }, worker);
        last = next;
        return next;
    }

    private void logImport(ImportReport report) {
        if (report.empty()) {
            logger.info("Nothing to import in " + importDir + ". Copy ItemsAdder's contents folder, or one"
                    + " pack from it, in there and run /arkcontent import again.");
            return;
        }
        for (String note : report.notes()) {
            logger.info("[import] " + note);
        }
        for (String problem : report.problems()) {
            logger.warning("[import] " + problem);
        }
        for (String file : report.written()) {
            logger.info("[import] wrote contents/" + file);
        }
        logger.info("ItemsAdder import: " + report.items() + " item(s) from " + report.converted() + " file(s), "
                + report.resources() + " model/texture file(s) copied, " + report.skipped() + " item(s) skipped"
                + (report.problems().isEmpty() ? "" : ", " + report.problems().size() + " problem(s) above") + ".");
    }

    private CompletableFuture<Report> stages() {
        long started = System.nanoTime();
        return CompletableFuture.supplyAsync(this::loadItems, worker)
                .thenApplyAsync(this::assignNoteBlockStates, worker)
                .thenApplyAsync(this::assignEmojiCharacters, worker)
                .thenApplyAsync(this::compilePack, worker)
                .thenComposeAsync(this::zipPack, worker)
                .thenApplyAsync(this::hashPack, worker)
                .thenComposeAsync(this::host, worker)
                .thenApplyAsync(build -> publish(build, started), mainThread);
    }

    /**
     * The items, read straight from contents/ on the main thread and made live at once, ahead of the
     * first rebuild: other plugins that read their own configuration as the server starts - shop
     * plugins, AuraSkills' menus - find this plugin's items there from the first tick, not seconds
     * later when the pack is built. Only the YAML is read here, which takes milliseconds; blocks,
     * emojis and the pack wait for the rebuild, which also reports any problems this skips over.
     *
     * @return how many items were loaded; 0 if the contents could not be read, which the rebuild
     *         will then report
     */
    public int preload() {
        try {
            Build build = loadItems();
            registry.replace(build.items().values());
            return build.items().size();
        } catch (RuntimeException exception) {
            logger.log(Level.FINE, "Could not preload the items; the rebuild will report why.", exception);
            return 0;
        }
    }

    // ---------------------------------------------------------------- worker thread

    /** contents/*.yml into items, each checked against this server's materials. */
    private Build loadItems() {
        try {
            extractExamples();
            LoadReport report = new ContentLoader().load(contentsDir);

            List<String> problems = new ArrayList<>(report.problems());
            Map<String, CustomItem> items = new LinkedHashMap<>();
            for (ItemDefinition definition : report.items()) {
                CustomItem item = CustomItem.resolve(definition, problems);
                if (item != null) {
                    items.put(item.id(), item);
                }
            }
            checkCrops(items, problems);
            return new Build(items, Map.of(), report.emojis(), Map.of(), problems, 0, null, null);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * What a crop drops and what it grows on name materials and items the server has to know. Only
     * reported: a drop that does not resolve is skipped at harvest, a soil that does not is never
     * planted on.
     */
    private static void checkCrops(Map<String, CustomItem> items, List<String> problems) {
        for (CustomItem item : items.values()) {
            if (!(item.placement() instanceof Placement.Crop crop)) {
                continue;
            }
            for (Placement.Drop drop : crop.drops()) {
                boolean custom = items.containsKey(drop.item());
                Material material = custom ? null : Material.matchMaterial(drop.item());
                if (!custom && (material == null || !material.isItem() || material.isAir())) {
                    problems.add(item.id() + ": drop '" + drop.item() + "' is neither a custom item nor a material");
                }
            }
            for (String soil : crop.soils()) {
                Material material = Material.matchMaterial(soil);
                if (material == null || !material.isBlock()) {
                    problems.add(item.id() + ": soil '" + soil + "' is not a block");
                }
            }
        }
    }

    /**
     * A private-use character for every emoji: the one it had before, or the lowest free one. Kept
     * in data/emoji_characters.json, because the character is what a sign or a book saves - hand it
     * to another emoji and every sign showing a ruby shows that instead.
     */
    private Build assignEmojiCharacters(Build build) {
        try {
            List<String> names = build.emojiDefinitions().stream().map(EmojiDefinition::name).toList();
            Map<String, Integer> previous = StableAllocator.read(emojiCharacterFile);
            if (names.isEmpty() && previous.isEmpty()) {
                return build;
            }
            StableAllocator.Allocation allocation = EMOJI_CHARACTERS.allocate(previous, names);
            if (allocation.changed()) {
                StableAllocator.write(emojiCharacterFile, allocation.assignments());
            }
            build.problems().addAll(allocation.problems());
            Map<EmojiDefinition, Integer> characters = new LinkedHashMap<>();
            for (EmojiDefinition emoji : build.emojiDefinitions()) {
                Integer character = allocation.active().get(emoji.name());
                if (character != null) {
                    characters.put(emoji, character);
                }
            }
            return build.withEmojis(characters);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * A note block state for every custom block: the one it had before, or the lowest free one.
     * The assignments are kept in data/note_block_states.json, because a placed custom block is only
     * its state - hand the state to another block and every placed one changes with it.
     */
    private Build assignNoteBlockStates(Build build) {
        try {
            List<String> blockIds = build.items().values().stream()
                    .filter(item -> item.placement() instanceof Placement.Block)
                    .map(CustomItem::id)
                    .toList();
            Map<String, Integer> previous = NoteBlockAllocator.read(noteBlockStateFile);
            if (blockIds.isEmpty() && previous.isEmpty()) {
                return build;
            }
            NoteBlockAllocator.Allocation allocation = NoteBlockAllocator.allocate(previous, blockIds);
            if (allocation.changed()) {
                NoteBlockAllocator.write(noteBlockStateFile, allocation.assignments());
            }
            build.problems().addAll(allocation.problems());
            return build.withNoteBlocks(allocation.active());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** The vanilla folder layout under pack/. Only items that survived loading get assets. */
    private Build compilePack(Build build) {
        try {
            List<ItemDefinition> definitions = build.items().values().stream()
                    .map(CustomItem::definition)
                    .toList();
            PackCompiler.Result result = compiler.compile(packDir, definitions, build.noteBlocks(), build.emojis(),
                    List.copyOf(externalPacks.values()));
            build.problems().addAll(result.problems());
            return build;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private CompletableFuture<Build> zipPack(Build build) {
        return PackZipper.zipAsync(packDir, zipFile, worker).thenApply(build::withFiles);
    }

    private Build hashPack(Build build) {
        try {
            return build.withArtifact(PackArtifact.load(zipFile, build.files()));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** Decides the link players are sent for this build - uploading the zip first, when upload: is on. */
    private CompletableFuture<Build> host(Build build) {
        String sha1 = build.artifact().sha1Hex();
        return switch (settings.hosting()) {
            case BUILTIN -> CompletableFuture.completedFuture(build.withHosted(settings.http().enabled()
                    ? new Hosted(settings.http().packUrl(sha1), "served by the built-in web server", false)
                    : new Hosted(null, "the built-in web server is off and no other hosting is set; host "
                    + zipFile + " yourself", false)));
            case EXTERNAL -> CompletableFuture.completedFuture(build.withHosted(new Hosted(
                    settings.http().externalUrl(sha1), "hosted at http.external-url - upload " + zipFile
                    + " there after each rebuild", false)));
            case UPLOAD -> upload(build);
        };
    }

    private CompletableFuture<Build> upload(Build build) {
        PackArtifact pack = build.artifact();
        long started = System.nanoTime();
        // An unchanged pack keeps its link (data/upload.json): checked, not uploaded again.
        return uploader.publish(pack.bytes(), pack.sha1Hex()).handleAsync((result, error) -> {
            if (error == null) {
                String how = result.reused() ? "already uploaded" : "uploaded in "
                        + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) + " ms"
                        + (result.attempts() > 1 ? " (" + result.attempts() + " attempts)" : "");
                return build.withHosted(new Hosted(result.url(), how + (result.verified() ? ", download checked" : ""),
                        false));
            }
            Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
            String reason = "Upload failed: " + cause.getMessage();
            if (plugin.httpRunning()) {
                build.problems().add(reason + ". Players are sent the built-in web server's link instead.");
                return build.withHosted(new Hosted(settings.http().packUrl(pack.sha1Hex()),
                        "served by the built-in web server, the upload having failed", false));
            }
            build.problems().add(reason + ". Players keep the pack they have; the new items have no textures until"
                    + " an upload succeeds (/arkcontent reload tries again).");
            return build.withHosted(new Hosted(null, "not uploaded", true));
        }, worker);
    }

    /**
     * The whole demo pack on the first start; on an upgrade, the examples added since the version
     * that last offered them - never over a file that is there, never into a deleted pack folder.
     */
    private void extractExamples() throws IOException {
        String version = plugin.getPluginMeta().getVersion();
        if (!Files.exists(contentsDir)) {
            if (settings.extractExamples()) {
                for (String example : EXAMPLES) {
                    plugin.saveResource(example, false);
                }
            }
            Files.createDirectories(contentsDir);
        } else {
            String last = Files.isRegularFile(examplesVersionFile)
                    ? Files.readString(examplesVersionFile).trim()
                    : ExampleUpdates.BEFORE_REMEMBERED;
            if (ExampleUpdates.compare(version, last) <= 0) {
                return;
            }
            if (settings.extractExamples()) {
                List<String> copy = ExampleUpdates.toCopy(ADDED_EXAMPLES, last, plugin.getDataFolder().toPath());
                for (String example : copy) {
                    plugin.saveResource(example, false);
                }
                if (!copy.isEmpty()) {
                    logger.info("Added " + copy.size() + " example file(s) new since " + last + " to contents/: "
                            + String.join(", ", copy.stream().filter(path -> path.endsWith(".yml")).toList())
                            + " and what they use. Nothing already there was touched.");
                }
            }
        }
        Files.createDirectories(examplesVersionFile.getParent());
        Files.writeString(examplesVersionFile, version + "\n");
    }

    // ---------------------------------------------------------------- main thread

    private Report publish(Build build, long started) {
        registry.replace(build.items().values());
        List<CustomBlock> customBlocks = new ArrayList<>();
        for (Map.Entry<String, NoteBlockState> entry : build.noteBlocks().entrySet()) {
            CustomItem item = build.items().get(entry.getKey());
            customBlocks.add(new CustomBlock(item, entry.getValue(),
                    plugin.getServer().createBlockData(entry.getValue().asBlockData())));
        }
        blocks.replace(customBlocks);
        emojis.replace(build.emojis());
        if (plugin.hooks() != null) {
            plugin.hooks().contentReloaded();
        }
        if (plugin.menus() != null) {
            plugin.menus().refreshOpen();
        }
        Hosted hosted = build.hosted();
        // A failed upload leaves the live pack alone: its link still works, the new one would not.
        boolean changed = !(hosted.keepPrevious() && delivery.live() != null)
                && delivery.publish(build.artifact(), hosted.url());
        if (changed) {
            delivery.sendAll(plugin.getServer().getOnlinePlayers());
        }

        PackArtifact pack = build.artifact();
        Report report = new Report(build.items().size(), customBlocks.size(), build.emojis().size(), pack.entries(),
                pack.sha1Hex(), pack.size(),
                changed, hosted.url(), hosted.note(), List.copyOf(build.problems()),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        log(report);
        return report;
    }

    private void log(Report report) {
        for (String problem : report.problems()) {
            logger.warning(problem);
        }
        logger.info(report.items() + " custom item(s) (" + report.blocks() + " block(s)), " + report.emojis()
                + " emoji(s), pack of "
                + report.files() + " file(s), "
                + (report.bytes() / 1024) + " KiB, sha1 " + report.sha1Hex()
                + (report.changed() ? "" : " (unchanged)")
                + " - built in " + report.millis() + " ms"
                + (report.problems().isEmpty() ? "" : ", " + report.problems().size() + " problem(s) above")
                + ". Pack " + report.hosting() + (report.url() != null ? ": " + report.url() : "."));
    }

    /**
     * Merges another plugin's pack into ours from now on, and rebuilds so players get it. A second
     * call with the same name replaces the first. Main thread.
     */
    public void mergeExternalPack(ExternalPack pack) {
        externalPacks.put(pack.name(), pack);
        logger.info("Merging the " + pack.name() + " resource pack from " + pack.root() + " into ours.");
        rebuild();
    }

    /** Stops the worker. Called from onDisable. */
    public void shutdown() {
        worker.shutdownNow();
        try {
            if (!worker.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warning("The content worker did not stop within 5 seconds.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
