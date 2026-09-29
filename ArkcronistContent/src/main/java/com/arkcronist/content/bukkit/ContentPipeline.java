package com.arkcronist.content.bukkit;

import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import com.arkcronist.content.bukkit.pack.PackDelivery;
import com.arkcronist.content.core.definition.ItemDefinition;
import com.arkcronist.content.core.loader.ContentLoader;
import com.arkcronist.content.core.loader.LoadReport;
import com.arkcronist.content.core.pack.PackArtifact;
import com.arkcronist.content.core.pack.PackCompiler;
import com.arkcronist.content.core.pack.PackZipper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

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
 *   worker  : load contents/*.yml -> compile pack/ -> zip resource_pack.zip -> SHA-1
 *   main    : swap registry + live pack, resend to online players
 * </pre>
 *
 * <p>Rebuilds never overlap. Each one starts only after the previous has fully finished, including
 * its main-thread step, so two quick reloads cannot interleave their writes into the same folder.
 * A rebuild that fails leaves the previous items and pack live and untouched.</p>
 */
public final class ContentPipeline {

    /** Copied into contents/ the first time the plugin starts. */
    private static final List<String> EXAMPLES = List.of(
            "contents/demo/items.yml",
            "contents/demo/models/item/ruby.json",
            "contents/demo/textures/item/ruby.png",
            "contents/demo/textures/item/ruby_sword.png");

    /**
     * What one rebuild did.
     *
     * @param changed  whether the pack's content moved, and players were sent it again
     * @param problems everything that was skipped or looked wrong, already logged
     */
    public record Report(int items, int files, String sha1Hex, int bytes, boolean changed,
                         List<String> problems, long millis) {
    }

    /** One rebuild's progress, handed from stage to stage. */
    private record Build(Map<String, CustomItem> items, List<String> problems, int files, PackArtifact artifact) {

        Build withFiles(int files) {
            return new Build(items, problems, files, artifact);
        }

        Build withArtifact(PackArtifact artifact) {
            return new Build(items, problems, files, artifact);
        }
    }

    private final ArkContentPlugin plugin;
    private final EngineSettings settings;
    private final ItemRegistry registry;
    private final PackDelivery delivery;
    private final Logger logger;
    private final PackCompiler compiler;

    private final Path contentsDir;
    private final Path packDir;
    private final Path zipFile;

    private final ExecutorService worker;
    private final Executor mainThread;

    /** The rebuild in progress, or the last one. Main thread only. */
    private CompletableFuture<?> last = CompletableFuture.completedFuture(null);

    public ContentPipeline(ArkContentPlugin plugin, EngineSettings settings, ItemRegistry registry,
                           PackDelivery delivery) {
        this.plugin = plugin;
        this.settings = settings;
        this.registry = registry;
        this.delivery = delivery;
        this.logger = plugin.getLogger();
        this.compiler = new PackCompiler(settings.pack().toPackSettings());

        Path data = plugin.getDataFolder().toPath();
        this.contentsDir = data.resolve("contents");
        this.packDir = data.resolve("pack");
        this.zipFile = data.resolve("output").resolve("resource_pack.zip");

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

    private CompletableFuture<Report> stages() {
        long started = System.nanoTime();
        return CompletableFuture.supplyAsync(this::loadItems, worker)
                .thenApplyAsync(this::compilePack, worker)
                .thenComposeAsync(this::zipPack, worker)
                .thenApplyAsync(this::hashPack, worker)
                .thenApplyAsync(build -> publish(build, started), mainThread);
    }

    // ---------------------------------------------------------------- worker thread

    /** contents/*.yml into items, each checked against this server's materials. */
    private Build loadItems() {
        try {
            extractExamplesOnFirstRun();
            LoadReport report = new ContentLoader().load(contentsDir);

            List<String> problems = new ArrayList<>(report.problems());
            Map<String, CustomItem> items = new LinkedHashMap<>();
            for (ItemDefinition definition : report.items()) {
                CustomItem item = CustomItem.resolve(definition, problems);
                if (item != null) {
                    items.put(item.id(), item);
                }
            }
            return new Build(items, problems, 0, null);
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
            PackCompiler.Result result = compiler.compile(packDir, definitions);
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

    private void extractExamplesOnFirstRun() throws IOException {
        if (Files.exists(contentsDir)) {
            return;
        }
        if (settings.extractExamples()) {
            for (String example : EXAMPLES) {
                plugin.saveResource(example, false);
            }
        }
        Files.createDirectories(contentsDir);
    }

    // ---------------------------------------------------------------- main thread

    private Report publish(Build build, long started) {
        registry.replace(build.items().values());
        boolean changed = delivery.publish(build.artifact());
        if (changed) {
            delivery.sendAll(plugin.getServer().getOnlinePlayers());
        }

        PackArtifact pack = build.artifact();
        Report report = new Report(build.items().size(), pack.entries(), pack.sha1Hex(), pack.size(),
                changed, List.copyOf(build.problems()),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        log(report);
        return report;
    }

    private void log(Report report) {
        for (String problem : report.problems()) {
            logger.warning(problem);
        }
        String url = delivery.currentUrl();
        logger.info(report.items() + " custom item(s), pack of " + report.files() + " file(s), "
                + (report.bytes() / 1024) + " KiB, sha1 " + report.sha1Hex()
                + (report.changed() ? "" : " (unchanged)")
                + " - built in " + report.millis() + " ms"
                + (report.problems().isEmpty() ? "" : ", " + report.problems().size() + " problem(s) above")
                + (url != null ? ". Served at " + url : ". Built-in web server is off; host "
                        + zipFile + " yourself."));
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
