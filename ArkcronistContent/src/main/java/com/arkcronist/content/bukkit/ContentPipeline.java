package com.arkcronist.content.bukkit;

import com.arkcronist.content.bukkit.block.BlockRegistry;
import com.arkcronist.content.bukkit.block.CustomBlock;
import com.arkcronist.content.bukkit.emoji.EmojiRegistry;
import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import com.arkcronist.content.bukkit.liquid.LiquidRegistry;
import com.arkcronist.content.bukkit.pack.PackDelivery;
import com.arkcronist.content.core.advancement.AdvancementCompiler;
import com.arkcronist.content.core.allocation.StableAllocator;
import com.arkcronist.content.core.block.NoteBlockAllocator;
import com.arkcronist.content.core.block.NoteBlockState;
import com.arkcronist.content.core.definition.EmojiDefinition;
import com.arkcronist.content.core.definition.FontImageDefinition;
import com.arkcronist.content.core.definition.ItemDefinition;
import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.furniture.DisplayFile;
import com.arkcronist.content.core.furniture.DisplayTransform;
import com.arkcronist.content.core.hud.GlyphMetrics;
import com.arkcronist.content.core.hud.HudDefinition;
import com.arkcronist.content.core.hud.HudGlyph;
import com.arkcronist.content.core.hud.HudLayout;
import com.arkcronist.content.core.hud.HudRenderer;
import com.arkcronist.content.core.importer.ImportReport;
import com.arkcronist.content.core.importer.ItemsAdderImporter;
import com.arkcronist.content.core.liquid.TripwireState;
import com.arkcronist.content.core.loader.ContentLoader;
import com.arkcronist.content.core.loader.ExampleUpdates;
import com.arkcronist.content.core.loader.LoadReport;
import com.arkcronist.content.core.pack.ExternalPack;
import com.arkcronist.content.core.pack.FontImages;
import com.arkcronist.content.core.pack.ModelDataDispatch;
import com.arkcronist.content.core.pack.PackArtifact;
import com.arkcronist.content.core.pack.PackCompiler;
import com.arkcronist.content.core.pack.PackSource;
import com.arkcronist.content.core.pack.PackZipper;
import com.arkcronist.content.core.upload.PackUploader;
import com.arkcronist.content.core.upload.UploadSettings;

import com.google.gson.JsonElement;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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

    /**
     * The demo pack's first files, and those a later version only used from a file that was already
     * there (the 1.2 crate, in blocks.yml): copied on a first start only.
     */
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
            "contents/demo/textures/furniture/ruby_crate.png");

    /**
     * The examples each later version added. A server that already has a contents/ folder gets
     * these on upgrade (see {@link ExampleUpdates}); a new one gets them with the rest.
     */
    private static final Map<String, List<String>> ADDED_EXAMPLES = Map.of(
            "1.1.0", List.of(
                    "contents/demo/crops.yml",
                    "contents/demo/textures/item/ruby_seeds.png",
                    "contents/demo/textures/crop/ruby_stage_0.png",
                    "contents/demo/textures/crop/ruby_stage_1.png",
                    "contents/demo/textures/crop/ruby_stage_2.png",
                    "contents/demo/textures/crop/ruby_stage_3.png",
                    "contents/demo/emojis.yml",
                    "contents/demo/textures/emoji/ruby.png",
                    "contents/demo/textures/emoji/heart.png"),
            "1.3.0", List.of(
                    "contents/demo/furniture.yml",
                    "contents/demo/models/furniture/ruby_chest.bbmodel",
                    "contents/demo/models/furniture/ruby_bed.json",
                    "contents/demo/textures/furniture/ruby_bed.png"),
            "1.4.0", List.of(
                    "contents/demo/armor.yml",
                    "contents/demo/textures/entity/equipment/humanoid/ruby_armor.png",
                    "contents/demo/textures/entity/equipment/humanoid_leggings/ruby_armor.png",
                    "contents/demo/textures/item/ruby_helmet.png",
                    "contents/demo/textures/item/ruby_chestplate.png",
                    "contents/demo/textures/item/ruby_leggings.png",
                    "contents/demo/textures/item/ruby_boots.png",
                    "contents/demo/models/item/ruby_helmet_worn.json",
                    "contents/demo/textures/item/ruby_helmet_worn.png"),
            "1.5.0", List.of(
                    "contents/demo/advancements.yml",
                    "contents/demo/textures/gui/advancements/ruby.png"),
            "1.7.0", List.of(
                    "contents/demo/guns.yml",
                    "contents/demo/textures/item/ruby_pistol.png",
                    "contents/demo/textures/item/ruby_shotgun.png",
                    "contents/demo/textures/item/ruby_bullet.png",
                    "contents/demo/liquids.yml",
                    "contents/demo/textures/block/acid.png",
                    "contents/demo/textures/block/acid.png.mcmeta",
                    "contents/demo/textures/block/frost.png",
                    "contents/demo/textures/block/frost.png.mcmeta",
                    "contents/demo/textures/item/acid_bucket.png",
                    "contents/demo/textures/item/frost_bucket.png",
                    "contents/demo/huds.yml",
                    "contents/demo/textures/hud/thirst_full.png",
                    "contents/demo/textures/hud/thirst_half.png",
                    "contents/demo/textures/hud/thirst_empty.png",
                    "contents/demo/textures/hud/mana_full.png",
                    "contents/demo/textures/hud/mana_half.png",
                    "contents/demo/textures/hud/mana_empty.png"));

    /** Copied into contents/ the first time the plugin starts. */
    private static final List<String> EXAMPLES = Stream.concat(FIRST_EXAMPLES.stream(),
            ADDED_EXAMPLES.values().stream().flatMap(List::stream)).toList();

    /**
     * What one rebuild did.
     *
     * @param changed  whether the pack's content moved, and players were sent it again
     * @param problems everything that was skipped or looked wrong, already logged
     */
    public record Report(int items, int blocks, int emojis, int advancements, int files, String sha1Hex, int bytes,
                         boolean changed,
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
    /**
     * @param builtin served by the built-in web server: the link is made when the pack goes live,
     *                from the address as it is then - a port or public IP found meanwhile included
     */
    private record Hosted(@Nullable String url, String note, boolean keepPrevious, boolean builtin) {

        Hosted(@Nullable String url, String note, boolean keepPrevious) {
            this(url, note, keepPrevious, false);
        }
    }

    /**
     * Emoji characters: Unicode's private use area, which no font draws until a pack says so - its
     * first 4096 characters; the rest is for HUD icons and the space characters.
     */
    private static final StableAllocator EMOJI_CHARACTERS = new StableAllocator(0xE000, 0xEFFF, "emoji character",
            "to emojis no longer defined; remove those from emoji_characters.json once no sign or book uses them");

    /** HUD icons: the private use area above the emojis', up to where the space characters begin. */
    private static final StableAllocator HUD_CHARACTERS = new StableAllocator(0xF000, 0xF7FF, "HUD character",
            "to HUDs no longer defined; remove those from hud_characters.json");

    /** Liquids: one pair of tripwire states each, of the sixteen free pairs. */
    private static final StableAllocator LIQUID_SLOTS = new StableAllocator(0, TripwireState.LIQUIDS - 1,
            "pair of tripwire states", "to liquids no longer defined; remove those from liquid_states.json once"
            + " none is left poured in a world");

    /**
     * One rebuild's progress, handed from stage to stage.
     *
     * @param noteBlocks     the state each custom block got, by id
     * @param emojis         every emoji defined, and the character it got once assigned
     * @param liquids        the pair of tripwire states each liquid got, by id
     * @param huds           every HUD, with the characters its icons got
     */
    private record Build(Map<String, CustomItem> items, Map<String, NoteBlockState> noteBlocks,
                         List<EmojiDefinition> emojiDefinitions, Map<EmojiDefinition, Integer> emojis,
                         List<AdvancementCompiler.Compiled> advancements, Map<String, Integer> modelData,
                         List<HudDefinition> hudDefinitions, Map<String, Integer> liquids, List<HudLayout> huds,
                         List<HudGlyph> hudGlyphs, List<FontImageDefinition> fontImageDefinitions,
                         FontImages fontImages, List<String> problems, int files, PackArtifact artifact,
                         Hosted hosted) {

        Build withNoteBlocks(Map<String, NoteBlockState> noteBlocks) {
            return new Build(items, noteBlocks, emojiDefinitions, emojis, advancements, modelData, hudDefinitions,
                    liquids, huds, hudGlyphs, fontImageDefinitions, fontImages, problems, files, artifact, hosted);
        }

        Build withEmojis(Map<EmojiDefinition, Integer> emojis) {
            return new Build(items, noteBlocks, emojiDefinitions, emojis, advancements, modelData, hudDefinitions,
                    liquids, huds, hudGlyphs, fontImageDefinitions, fontImages, problems, files, artifact, hosted);
        }

        Build withFiles(int files) {
            return new Build(items, noteBlocks, emojiDefinitions, emojis, advancements, modelData, hudDefinitions,
                    liquids, huds, hudGlyphs, fontImageDefinitions, fontImages, problems, files, artifact, hosted);
        }

        Build withArtifact(PackArtifact artifact) {
            return new Build(items, noteBlocks, emojiDefinitions, emojis, advancements, modelData, hudDefinitions,
                    liquids, huds, hudGlyphs, fontImageDefinitions, fontImages, problems, files, artifact, hosted);
        }

        Build withModelData(Map<String, Integer> modelData) {
            return new Build(items, noteBlocks, emojiDefinitions, emojis, advancements, modelData, hudDefinitions,
                    liquids, huds, hudGlyphs, fontImageDefinitions, fontImages, problems, files, artifact, hosted);
        }

        Build withLiquids(Map<String, Integer> liquids) {
            return new Build(items, noteBlocks, emojiDefinitions, emojis, advancements, modelData, hudDefinitions,
                    liquids, huds, hudGlyphs, fontImageDefinitions, fontImages, problems, files, artifact, hosted);
        }

        Build withHuds(List<HudLayout> huds, List<HudGlyph> hudGlyphs) {
            return new Build(items, noteBlocks, emojiDefinitions, emojis, advancements, modelData, hudDefinitions,
                    liquids, huds, hudGlyphs, fontImageDefinitions, fontImages, problems, files, artifact, hosted);
        }

        Build withFontImages(FontImages fontImages) {
            return new Build(items, noteBlocks, emojiDefinitions, emojis, advancements, modelData, hudDefinitions,
                    liquids, huds, hudGlyphs, fontImageDefinitions, fontImages, problems, files, artifact, hosted);
        }

        Build withHosted(Hosted hosted) {
            return new Build(items, noteBlocks, emojiDefinitions, emojis, advancements, modelData, hudDefinitions,
                    liquids, huds, hudGlyphs, fontImageDefinitions, fontImages, problems, files, artifact, hosted);
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
    /** Resource packs merged whole into ours, every file as it is: what the importer absorbed, or anything dropped in. */
    private final Path packsDir;
    private final Path packDir;
    private final Path zipFile;
    private final Path noteBlockStateFile;
    private final Path emojiCharacterFile;
    private final Path modelDataFile;
    private final Path liquidStateFile;
    private final Path hudCharacterFile;
    /** The example sets offered to contents/ so far, one per line. */
    private final Path examplesOfferedFile;
    /** What 1.4.0 wrote instead: the one set it knew, 1.4.0. */
    private final Path legacyExamplesFile;
    /** Content files as they were before the furniture editor last wrote them. */
    private final Path editorBackupsDir;

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
        this.compiler = new PackCompiler(settings.pack().toPackSettings(), settings.pack().fixSoundNames());
        // Packs named in config.yml: CosmeticsCore's, a model pack bought separately, any other.
        Path server = plugin.getDataFolder().toPath().toAbsolutePath().getParent().getParent();
        for (String merge : settings.pack().merge()) {
            Path root = server.resolve(merge).normalize();
            ExternalPack pack = new ExternalPack(root.getFileName().toString(), root);
            if (Files.exists(root)) {
                externalPacks.put("config:" + merge, pack);
                logger.info("Merging the resource pack at " + merge + " into ours.");
            } else {
                logger.warning("pack.merge: " + merge + " does not exist (looked at " + root + ") - not merged.");
            }
        }

        Path data = plugin.getDataFolder().toPath();
        this.contentsDir = data.resolve("contents");
        this.importDir = data.resolve("import");
        this.packsDir = data.resolve("packs");
        this.packDir = data.resolve("pack");
        this.zipFile = data.resolve("output").resolve("resource_pack.zip");
        this.noteBlockStateFile = data.resolve("data").resolve("note_block_states.json");
        this.emojiCharacterFile = data.resolve("data").resolve("emoji_characters.json");
        this.modelDataFile = data.resolve("data").resolve("custom_model_data.json");
        this.liquidStateFile = data.resolve("data").resolve("liquid_states.json");
        this.hudCharacterFile = data.resolve("data").resolve("hud_characters.json");
        this.examplesOfferedFile = data.resolve("data").resolve("examples_offered.txt");
        this.legacyExamplesFile = data.resolve("data").resolve("examples_version.txt");
        this.editorBackupsDir = data.resolve("data").resolve("editor-backups");

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
            ImportReport report = new ItemsAdderImporter().run(importDir, contentsDir, packsDir);
            logImport(report);
            return report;
        });
    }

    /**
     * Writes the furniture editor's values into the content file {@code item} came from - see
     * {@link DisplayFile} - on the worker, in line with rebuilds: never while one is reading
     * contents/. It does not rebuild; the caller does, once this has completed. Main thread.
     *
     * @return completes on the worker with what was written
     */
    public CompletableFuture<DisplayFile.Saved> saveDisplay(ItemDefinition item, DisplayTransform transform) {
        return exclusive(() -> DisplayFile.save(contentsDir, editorBackupsDir, item.source(), item.id(), transform,
                Instant.now()));
    }

    /** The folder content files live in. */
    public Path contentsDir() {
        return contentsDir;
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
            logger.info("Nothing to import in " + importDir + ". Copy ItemsAdder's contents folder, one"
                    + " pack from it, or its generated resource pack zip in there and run /arkcontent import again.");
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
                + report.resources() + " asset file(s) copied byte for byte, "
                + (report.packs() == 0 ? "" : report.packs() + " generated pack(s) of " + report.packFiles()
                + " file(s) taken whole into " + packsDir + ", ")
                + report.skipped() + " item(s) skipped"
                + (report.problems().isEmpty() ? "" : ", " + report.problems().size() + " problem(s) above") + ".");
    }

    private CompletableFuture<Report> stages() {
        long started = System.nanoTime();
        return CompletableFuture.supplyAsync(this::loadItems, worker)
                .thenApplyAsync(this::assignNoteBlockStates, worker)
                .thenApplyAsync(this::assignEmojiCharacters, worker)
                .thenApplyAsync(this::assignModelData, worker)
                .thenApplyAsync(this::assignLiquidStates, worker)
                .thenApplyAsync(this::assignHudCharacters, worker)
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
            Map<String, ItemDefinition> definitions = new LinkedHashMap<>();
            items.values().forEach(item -> definitions.put(item.id(), item.definition()));
            AdvancementCompiler.Result advancements = AdvancementCompiler.compile(report.advancements(), definitions,
                    ContentPipeline::textComponent);
            problems.addAll(advancements.problems());
            return new Build(items, Map.of(), report.emojis(), Map.of(), advancements.advancements(), Map.of(),
                    report.huds(), Map.of(), List.of(), List.of(), report.fontImages(), FontImages.EMPTY, problems, 0,
                    null, null);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** MiniMessage to the JSON text component an advancement's title and description are written in. */
    private static JsonElement textComponent(String miniMessage) {
        return GsonComponentSerializer.gson().serializeToTree(MiniMessage.miniMessage().deserialize(miniMessage));
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
            StableAllocator.Allocation allocation = EMOJI_CHARACTERS.allocate(previous, names, fontCharactersInUse(), true);
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
            // The states a merged pack - an ItemsAdder pack - draws its own blocks with are left to it.
            Set<Integer> reserved = new java.util.HashSet<>();
            for (ExternalPack pack : mergedPacks()) {
                try (PackSource source = PackSource.open(pack)) {
                    reserved.addAll(com.arkcronist.content.core.pack.BlockStates.customNoteBlockStates(source));
                }
            }
            NoteBlockAllocator.Allocation allocation = NoteBlockAllocator.allocate(previous, blockIds, reserved);
            if (allocation.changed()) {
                NoteBlockAllocator.write(noteBlockStateFile, allocation.assignments());
            }
            build.problems().addAll(allocation.problems());
            return build.withNoteBlocks(allocation.active());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * A {@code custom_model_data} number for every item drawn from a plain vanilla material: the one
     * it had before, or the lowest free one from pack.custom-model-data.first. Kept in
     * data/custom_model_data.json, because a number is what other plugins' configs are written with.
     */
    private Build assignModelData(Build build) {
        EngineSettings.ModelData config = settings.pack().modelData();
        if (!config.enabled()) {
            return build;
        }
        try {
            List<String> ids = build.items().values().stream()
                    .filter(item -> item.hasModel() && ModelDataDispatch.dispatchable(item.definition().material()))
                    .map(CustomItem::id)
                    .toList();
            Map<String, Integer> previous = StableAllocator.read(modelDataFile);
            if (ids.isEmpty() && previous.isEmpty()) {
                return build;
            }
            StableAllocator numbers = new StableAllocator(config.first(), config.first() + 999_999,
                    "custom_model_data number", "delete its line from " + modelDataFile.getFileName());
            // A merged pack's own numbers - an ItemsAdder pack's, say - are never handed out again.
            Set<Integer> reserved = new java.util.HashSet<>();
            for (ExternalPack pack : mergedPacks()) {
                try (PackSource source = PackSource.open(pack)) {
                    reserved.addAll(ModelDataDispatch.numbersUsedBy(source));
                }
            }
            StableAllocator.Allocation allocation = numbers.allocate(previous, ids, reserved, true);
            if (allocation.changed()) {
                StableAllocator.write(modelDataFile, allocation.assignments());
            }
            build.problems().addAll(allocation.problems());
            return build.withModelData(allocation.active());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * A pair of tripwire states for every liquid: the pair it had before, or the lowest free one.
     * Kept in data/liquid_states.json, because a poured liquid is only its state in the world.
     */
    private Build assignLiquidStates(Build build) {
        try {
            List<String> ids = build.items().values().stream()
                    .filter(item -> item.placement() instanceof Placement.Liquid)
                    .map(CustomItem::id)
                    .toList();
            Map<String, Integer> previous = StableAllocator.read(liquidStateFile);
            if (ids.isEmpty() && previous.isEmpty()) {
                return build;
            }
            StableAllocator.Allocation allocation = LIQUID_SLOTS.allocate(previous, ids);
            if (allocation.changed()) {
                StableAllocator.write(liquidStateFile, allocation.assignments());
            }
            build.problems().addAll(allocation.problems());
            return build.withLiquids(allocation.active());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * A character for every HUD icon, kept in data/hud_characters.json like the emojis', and each
     * icon's width as the client will draw it - read from its image, since the bar steps back over
     * itself by exactly that much.
     */
    private Build assignHudCharacters(Build build) {
        try {
            // Font images first: those a merged pack draws already take its character; the rest
            // get one here, from the same stable allocation as HUD icons.
            List<FontImages.Image> provided = FontImages.provided(mergedFonts());
            List<FontImages.Image> definedImages = new ArrayList<>();
            List<FontImageDefinition> own = new ArrayList<>();
            for (FontImageDefinition definition : build.fontImageDefinitions()) {
                FontImages.match(definition, provided).ifPresentOrElse(definedImages::add, () -> own.add(definition));
            }
            Set<Integer> reserved = new java.util.HashSet<>(fontCharactersInUse());
            List<String> keys = new ArrayList<>();
            for (HudDefinition hud : build.hudDefinitions()) {
                hud.icons().forEach(icon -> keys.add(hud.iconKey(icon.name())));
            }
            Map<Integer, FontImageDefinition> onSymbol = new LinkedHashMap<>();
            for (FontImageDefinition definition : own) {
                Integer symbol = definition.symbol();
                if (symbol != null && !reserved.contains(symbol) && !onSymbol.containsKey(symbol)) {
                    onSymbol.put(symbol, definition);
                } else {
                    if (symbol != null) {
                        build.problems().add("font image " + definition.fullId() + ": symbol U+"
                                + Integer.toHexString(symbol).toUpperCase(Locale.ROOT) + " is drawn by something else"
                                + " already - it is given another character");
                    }
                    keys.add(fontImageKey(definition));
                }
            }
            reserved.addAll(onSymbol.keySet());
            Map<String, Integer> previous = StableAllocator.read(hudCharacterFile);
            if (keys.isEmpty() && previous.isEmpty() && onSymbol.isEmpty()) {
                return build.withFontImages(fontImageIndex(build, definedImages, provided));
            }
            StableAllocator.Allocation allocation = HUD_CHARACTERS.allocate(previous, keys, reserved, true);
            if (allocation.changed()) {
                StableAllocator.write(hudCharacterFile, allocation.assignments());
            }
            build.problems().addAll(allocation.problems());

            List<HudLayout> layouts = new ArrayList<>();
            List<HudGlyph> glyphs = new ArrayList<>();
            for (HudDefinition hud : build.hudDefinitions()) {
                Map<String, int[]> icons = new HashMap<>();
                for (HudDefinition.Icon icon : hud.icons()) {
                    Integer character = allocation.active().get(hud.iconKey(icon.name()));
                    if (character == null) {
                        continue;
                    }
                    String origin = "hud " + hud.fullId() + " " + icon.name();
                    icons.put(icon.name(), new int[]{character, iconAdvance(hud, icon, origin, build.problems())});
                    glyphs.add(new HudGlyph(origin, hud.namespace(), icon.texture(), hud.sourceRoot(), hud.height(),
                            hud.ascent(), character));
                }
                int[] full = icons.get("full");
                if (full == null) {
                    continue;
                }
                int[] half = icons.getOrDefault("half", new int[]{0, 0});
                int[] empty = icons.getOrDefault("empty", new int[]{0, 0});
                layouts.add(new HudLayout(hud, new HudRenderer.Glyphs((char) full[0], full[1], (char) half[0], half[1],
                        (char) empty[0], empty[1])));
            }
            for (FontImageDefinition definition : own) {
                Integer character = definition.symbol() != null && onSymbol.get(definition.symbol()) == definition
                        ? definition.symbol() : allocation.active().get(fontImageKey(definition));
                if (character == null) {
                    continue;
                }
                Integer height = definition.height() != null ? definition.height()
                        : FontImages.pngHeight(definition.sourceRoot().resolve("textures")
                        .resolve(definition.texture().path() + ".png"));
                int drawnHeight = height != null ? Math.min(height, 4096) : 8;
                int ascent = definition.ascent() != null ? Math.min(definition.ascent(), drawnHeight)
                        : Math.min(8, drawnHeight);
                glyphs.add(new HudGlyph("font image " + definition.fullId(), definition.namespace(),
                        definition.texture(), definition.sourceRoot(), drawnHeight, ascent, character));
                definedImages.add(new FontImages.Image(definition.namespace(), definition.name(),
                        FontImages.DEFAULT_FONT, new String(Character.toChars(character)), ascent, drawnHeight,
                        definition.texture() + ".png"));
            }
            return build.withHuds(layouts, glyphs).withFontImages(fontImageIndex(build, definedImages, provided));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** How wide the client draws an icon; for a texture this plugin cannot read, its height plus one. */
    private static int iconAdvance(HudDefinition hud, HudDefinition.Icon icon, String origin, List<String> problems) {
        if (icon.texture().namespace().equals(hud.namespace())) {
            Path file = hud.sourceRoot().resolve("textures").resolve(icon.texture().path() + ".png");
            try {
                return GlyphMetrics.advance(file, hud.height());
            } catch (IOException exception) {
                problems.add(origin + ": could not read " + file.getFileName() + " to measure it - "
                        + exception.getMessage() + "; the bar may be placed a few pixels off");
            }
        } else {
            problems.add(origin + ": " + icon.texture() + " is another pack's texture, so its width is not known;"
                    + " taken as " + (hud.height() + 1) + " pixels");
        }
        return hud.height() + 1;
    }

    /** The vanilla folder layout under pack/. Only items that survived loading get assets. */
    private Build compilePack(Build build) {
        try {
            List<ItemDefinition> definitions = build.items().values().stream()
                    .map(CustomItem::definition)
                    .toList();
            PackCompiler.Result result = compiler.compile(packDir, new PackCompiler.Input(definitions, build.noteBlocks(),
                    build.emojis(), build.advancements().stream().map(AdvancementCompiler.Compiled::definition).toList(),
                    build.modelData(), build.liquids(), build.hudGlyphs(), settings.pack().negativeSpaces(),
                    mergedPacks(), contentsDir));
            build.problems().addAll(result.problems());
            result.notes().forEach(note -> logger.info("[pack] " + note));
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
                    ? new Hosted(null, "served by the built-in web server", false, true)
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
                return build.withHosted(new Hosted(null,
                        "served by the built-in web server, the upload having failed", false, true));
            }
            build.problems().add(reason + ". Players keep the pack they have; the new items have no textures until"
                    + " an upload succeeds (/arkcontent reload tries again).");
            return build.withHosted(new Hosted(null, "not uploaded", true));
        }, worker);
    }

    /**
     * The whole demo pack on the first start; on an upgrade, the example sets not offered yet (see
     * {@link ExampleUpdates}) - never over a file that is there, never into a deleted pack folder.
     */
    private void extractExamples() throws IOException {
        if (!Files.exists(contentsDir)) {
            if (settings.extractExamples()) {
                for (String example : EXAMPLES) {
                    plugin.saveResource(example, false);
                }
            }
            Files.createDirectories(contentsDir);
            rememberOffered();
            return;
        }
        List<String> lines = new ArrayList<>();
        for (Path file : List.of(examplesOfferedFile, legacyExamplesFile)) {
            if (Files.isRegularFile(file)) {
                lines.addAll(Files.readAllLines(file));
            }
        }
        Set<String> offered = ExampleUpdates.parse(lines);
        if (!offered.containsAll(ADDED_EXAMPLES.keySet())) {
            if (settings.extractExamples()) {
                List<String> copy = ExampleUpdates.toCopy(ADDED_EXAMPLES, offered, plugin.getDataFolder().toPath());
                for (String example : copy) {
                    plugin.saveResource(example, false);
                }
                if (!copy.isEmpty()) {
                    logger.info("Added " + copy.size() + " example file(s) from newer versions to contents/: "
                            + String.join(", ", copy.stream().filter(path -> path.endsWith(".yml")).toList())
                            + " and what they use. Nothing already there was touched; delete what you do not"
                            + " want, it will not come back.");
                }
            }
            rememberOffered();
        }
        updateUntouchedExamples();
    }

    private void rememberOffered() throws IOException {
        Files.createDirectories(examplesOfferedFile.getParent());
        Files.write(examplesOfferedFile, ADDED_EXAMPLES.keySet().stream().sorted().toList());
        Files.deleteIfExists(legacyExamplesFile);
    }

    /**
     * An example still byte for byte as an earlier version shipped it was never edited: it is
     * replaced by this version's (the 1.5 chest and bed, the demo blocks with their job rewards).
     * One the owner changed is theirs and stays.
     */
    private void updateUntouchedExamples() throws IOException {
        if (!settings.extractExamples()) {
            return;
        }
        List<String> lines;
        try (java.io.InputStream history = plugin.getResource("examples-history.txt")) {
            if (history == null) {
                return;
            }
            lines = new String(history.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).lines().toList();
        }
        List<String> outdated = ExampleUpdates.outdated(ExampleUpdates.parseHistory(lines),
                plugin.getDataFolder().toPath());
        for (String example : outdated) {
            plugin.saveResource(example, true);
        }
        if (!outdated.isEmpty()) {
            logger.info("Updated " + outdated.size() + " example file(s) you had not changed to this version's: "
                    + String.join(", ", outdated) + ".");
        }
    }

    // ---------------------------------------------------------------- main thread

    private Report publish(Build build, long started) {
        plugin.itemFactory().modelData(build.modelData());
        registry.replace(build.items().values());
        List<CustomBlock> customBlocks = new ArrayList<>();
        for (Map.Entry<String, NoteBlockState> entry : build.noteBlocks().entrySet()) {
            CustomItem item = build.items().get(entry.getKey());
            customBlocks.add(new CustomBlock(item, entry.getValue(),
                    plugin.getServer().createBlockData(entry.getValue().asBlockData())));
        }
        blocks.replace(customBlocks);
        List<LiquidRegistry.Liquid> liquids = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : build.liquids().entrySet()) {
            CustomItem item = build.items().get(entry.getKey());
            if (item != null && item.placement() instanceof Placement.Liquid liquid) {
                int slot = entry.getValue();
                liquids.add(new LiquidRegistry.Liquid(item, liquid, slot,
                        plugin.getServer().createBlockData(TripwireState.source(slot).blockData()),
                        plugin.getServer().createBlockData(TripwireState.flowing(slot).blockData())));
            }
        }
        plugin.liquidRegistry().replace(liquids);
        plugin.hudRegistry().replace(build.huds());
        plugin.fontImages().replace(build.fontImages());
        emojis.replace(build.emojis());
        int advancements = plugin.advancements() == null ? 0 : plugin.advancements().publish(build.advancements());
        if (plugin.hooks() != null) {
            plugin.hooks().contentReloaded();
        }
        if (plugin.menus() != null) {
            plugin.menus().refreshOpen();
        }
        Hosted hosted = build.hosted();
        String url = hosted.builtin() ? plugin.webHost().address().packUrl(build.artifact().sha1Hex()) : hosted.url();
        // A failed upload leaves the live pack alone: its link still works, the new one would not.
        boolean changed = !(hosted.keepPrevious() && delivery.live() != null)
                && delivery.publish(build.artifact(), url);
        if (changed) {
            delivery.sendAll(plugin.getServer().getOnlinePlayers());
        }

        PackArtifact pack = build.artifact();
        Report report = new Report(build.items().size(), customBlocks.size(), build.emojis().size(), advancements,
                pack.entries(),
                pack.sha1Hex(), pack.size(),
                changed, url, hosted.note(), List.copyOf(build.problems()),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        log(report);
        return report;
    }

    private void log(Report report) {
        for (String problem : report.problems()) {
            logger.warning(problem);
        }
        logger.info(report.items() + " custom item(s) (" + report.blocks() + " block(s)), " + report.emojis()
                + " emoji(s), " + report.advancements() + " advancement(s), pack of "
                + report.files() + " file(s), "
                + (report.bytes() / 1024) + " KiB, sha1 " + report.sha1Hex()
                + (report.changed() ? "" : " (unchanged)")
                + " - built in " + report.millis() + " ms"
                + (report.problems().isEmpty() ? "" : ", " + report.problems().size() + " problem(s) above")
                + ". Pack " + report.hosting() + (report.url() != null ? ": " + report.url() : "."));
    }

    private static String fontImageKey(FontImageDefinition definition) {
        return "font_image:" + definition.fullId();
    }

    /** The font images by name, for titles and placeholders; how many there are goes in the log. */
    private FontImages fontImageIndex(Build build, List<FontImages.Image> defined, List<FontImages.Image> provided) {
        FontImages index = FontImages.of(defined, provided);
        if (index.size() > 0) {
            logger.info(index.size() + " font image(s) by name (" + index.defined() + " from font_images:, the rest"
                    + " named after their picture in a merged pack): :<name>:, :offset_<n>: and %img_<name>% work in"
                    + " menu titles and placeholders."
                    + (index.ambiguous().isEmpty() ? "" : " Left out - one picture, several characters: "
                    + String.join(", ", index.ambiguous().stream().limit(8).toList())
                    + (index.ambiguous().size() > 8 ? " and " + (index.ambiguous().size() - 8) + " more" : "") + "."));
        }
        return index;
    }

    /** Every font file of the merged packs, for font images. */
    private List<FontImages.Font> mergedFonts() throws IOException {
        List<FontImages.Font> fonts = new ArrayList<>();
        for (ExternalPack pack : mergedPacks()) {
            try (PackSource source = PackSource.open(pack)) {
                fonts.addAll(FontImages.fontsOf(source));
            }
        }
        return fonts;
    }

    /**
     * The characters the merged packs' default fonts draw already - an ItemsAdder pack's emojis and
     * icons - which no emoji or HUD icon of ours is put on.
     */
    private Set<Integer> fontCharactersInUse() throws IOException {
        Set<Integer> characters = new java.util.HashSet<>();
        for (ExternalPack pack : mergedPacks()) {
            try (PackSource source = PackSource.open(pack)) {
                characters.addAll(com.arkcronist.content.core.pack.FontCharacters.usedBy(source));
            }
        }
        return characters;
    }

    /**
     * Every pack merged into ours: those config.yml names, other plugins' and each folder or zip
     * in packs/ - where /arkcontent import puts the ItemsAdder packs it absorbs. Read on every
     * rebuild, so a pack dropped in or deleted there counts from the next one.
     */
    private List<ExternalPack> mergedPacks() throws IOException {
        List<ExternalPack> packs = new ArrayList<>(externalPacks.values());
        if (Files.isDirectory(packsDir)) {
            try (Stream<Path> list = Files.list(packsDir)) {
                list.filter(path -> !path.getFileName().toString().startsWith("."))
                        .filter(path -> Files.isDirectory(path) || path.getFileName().toString().endsWith(".zip"))
                        .sorted()
                        .forEach(path -> packs.add(new ExternalPack("packs/" + path.getFileName(), path)));
            }
        }
        return packs;
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
