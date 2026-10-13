package com.arkcronist.content.bukkit.hooks.betterhud;

import com.arkcronist.content.bukkit.ContentPipeline;
import com.arkcronist.content.core.pack.ExternalPack;
import kr.toxicity.hud.api.bukkit.event.PluginReloadedEvent;
import kr.toxicity.hud.api.plugin.ReloadState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * BetterHud's HUD, in the pack this plugin sends.
 *
 * <p>BetterHud draws its HUD with a resource pack of its own - its fonts, its images and the text
 * shader that places them anywhere on the screen - and sends none itself unless it hosts one. Each
 * time it builds that pack, as it starts and on {@code /hud reload}, it hands the files over with
 * {@link PluginReloadedEvent}: they are written to {@code data/betterhud/<time>/} and merged into
 * this plugin's pack, which is rebuilt, so players get a single pack with both - without a folder of
 * BetterHud's being read while BetterHud rewrites it.</p>
 *
 * <p>BetterHud's files win over the copies of the same paths in other merged packs - an ItemsAdder
 * export's {@code rendertype_text} shader, say: without its own, its HUD is not drawn. They are a
 * live pack, as MythicArmor's is. This plugin's own files still win any path both supply.</p>
 */
public final class BetterHudHook implements Listener {

    /** Builds kept: the newest, and the one a rebuild already running may still be reading. */
    private static final int KEEP = 2;

    private final Plugin plugin;
    private final ContentPipeline pipeline;
    private final Path folder;

    public BetterHudHook(Plugin plugin, ContentPipeline pipeline) {
        this.plugin = plugin;
        this.pipeline = pipeline;
        this.folder = plugin.getDataFolder().toPath().resolve("data").resolve("betterhud");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onReloaded(PluginReloadedEvent event) {
        if (!(event.getState() instanceof ReloadState.Success success)) {
            return;
        }
        Map<String, byte[]> files = Map.copyOf(success.resourcePack());
        // Megabytes of files: written off the main thread, then merged on it.
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Path written;
            try {
                written = write(files);
            } catch (IOException | RuntimeException failure) {
                plugin.getLogger().warning("Could not take BetterHud's resource pack (" + failure
                        + "): its HUD is left out of this plugin's pack until it builds it again.");
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin,
                    () -> pipeline.mergeExternalPack(new ExternalPack("BetterHud", written, true)));
        });
    }

    /** The pack's files under a new folder of their own; older ones but the last are removed. */
    private Path write(Map<String, byte[]> files) throws IOException {
        Files.createDirectories(folder);
        Path target = folder.resolve(Long.toString(System.currentTimeMillis()));
        for (int n = 1; Files.exists(target); n++) {
            target = folder.resolve(System.currentTimeMillis() + "-" + n);
        }
        Files.createDirectories(target);
        int taken = 0;
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            String path = file.getKey().replace('\\', '/');
            Path out = target.resolve(path).normalize();
            // Paths are BetterHud's, inside its pack: anything that would leave the folder is not one.
            if (path.isBlank() || path.startsWith("/") || !out.startsWith(target) || out.equals(target)) {
                continue;
            }
            Files.createDirectories(out.getParent());
            Files.write(out, file.getValue());
            taken++;
        }
        plugin.getLogger().info("BetterHud built its resource pack: " + taken + " file(s), merged into ours.");
        prune();
        return target;
    }

    private void prune() throws IOException {
        List<Path> builds;
        try (Stream<Path> list = Files.list(folder)) {
            builds = list.filter(Files::isDirectory)
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                    .toList();
        }
        for (Path old : builds.subList(Math.min(KEEP, builds.size()), builds.size())) {
            try (Stream<Path> walk = Files.walk(old)) {
                for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }
}
