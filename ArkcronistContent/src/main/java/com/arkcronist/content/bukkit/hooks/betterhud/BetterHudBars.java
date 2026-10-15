package com.arkcronist.content.bukkit.hooks.betterhud;

import com.arkcronist.content.bukkit.hud.HudRegistry;
import com.arkcronist.content.bukkit.hud.HudService;
import com.arkcronist.content.core.definition.ResourceLocation;
import com.arkcronist.content.core.hud.BetterHudExport;
import com.arkcronist.content.core.hud.HudDefinition;
import com.arkcronist.content.core.hud.HudLayout;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 3.1: this plugin's HUD bars drawn by BetterHud (see {@link BetterHudExport}).
 *
 * <p>On every build: the bars to hand over - those on the action bar, unless {@code betterhud: false}
 * says otherwise, and any with {@code betterhud:} - are written into BetterHud's folder, off the
 * main thread, and the action bar stops drawing them at once. When the files there changed and
 * BetterHud is already running, it is reloaded ({@code hud reload}) to read them; it then builds its
 * pack again, which {@link BetterHudHook} merges into this plugin's. Unchanged files mean no reload,
 * so the rebuild that merge starts ends there. With no bar to hand over, the files are removed.</p>
 *
 * <p>BetterHud reads the values through PlaceholderAPI: without it the bars stay on the action bar.</p>
 */
public final class BetterHudBars {

    private final Plugin plugin;
    private final HudRegistry registry;
    private final HudService huds;
    private final boolean enabled;
    private final Path folder;
    /** Bars that could not be handed over, already said: said once, not on every build. */
    private final Set<String> warned = new HashSet<>();

    public BetterHudBars(Plugin plugin, HudRegistry registry, HudService huds, boolean enabled, Path betterHudFolder) {
        this.plugin = plugin;
        this.registry = registry;
        this.huds = huds;
        this.enabled = enabled;
        this.folder = betterHudFolder;
    }

    /** After a build, with its HUDs in the registry. Main thread. */
    public void publish(Collection<HudLayout> layouts) {
        List<BetterHudExport.Bar> bars = new ArrayList<>();
        Set<String> handed = new HashSet<>();
        for (HudLayout layout : layouts.stream()
                .sorted(Comparator.comparing((HudLayout hud) -> hud.definition().fullId())).toList()) {
            HudDefinition hud = layout.definition();
            boolean wanted = hud.betterHud() != null ? hud.betterHud().drawn() : enabled && hud.actionBar();
            if (!wanted) {
                continue;
            }
            BetterHudExport.Bar bar = bar(layout);
            if (bar != null) {
                bars.add(bar);
                handed.add(hud.fullId());
            }
        }
        huds.drawnElsewhere(handed, this::running);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean changed;
            try {
                changed = write(BetterHudExport.files(bars, legacyOffset()));
            } catch (IOException | RuntimeException failure) {
                plugin.getLogger().warning("Could not write the HUD bars into BetterHud's folder (" + failure
                        + "): they are drawn on the action bar instead.");
                plugin.getServer().getScheduler().runTask(plugin, () -> huds.drawnElsewhere(Set.of(), () -> false));
                return;
            }
            if (!changed) {
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!bars.isEmpty()) {
                    plugin.getLogger().info("BetterHud draws " + bars.size() + " HUD bar(s) of this plugin's: "
                            + BetterHudExport.names(bars) + " (" + folder.getFileName() + "/huds/"
                            + BetterHudExport.NAME + ".yml).");
                }
                reload();
            });
        });
    }

    /**
     * Whether BetterHud lifts every HUD by the hotbar's height: {@code disable-legacy-offset: false}
     * in its config.yml, as PrimeHUD ships it. BetterHud's own default is true. Off the main thread.
     */
    private boolean legacyOffset() {
        Path config = folder.resolve("config.yml");
        if (!Files.isRegularFile(config)) {
            return false;
        }
        return !org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(config.toFile())
                .getBoolean("disable-legacy-offset", true);
    }

    /** A bar's icons, read; null - said once - when one is another pack's or cannot be read. */
    private @Nullable BetterHudExport.Bar bar(HudLayout layout) {
        HudDefinition hud = layout.definition();
        try {
            BufferedImage full = icon(hud, hud.full());
            BufferedImage half = hud.half() == null ? null : icon(hud, hud.half());
            BufferedImage empty = hud.empty() == null ? null : icon(hud, hud.empty());
            String placeholder = registry.find(hud.id()).filter(found -> found == layout).isPresent()
                    ? hud.id() : hud.fullId();
            return new BetterHudExport.Bar(layout, placeholder, full, half, empty);
        } catch (IOException failure) {
            if (warned.add(hud.fullId())) {
                plugin.getLogger().warning("HUD " + hud.fullId() + " stays on the action bar: " + failure.getMessage());
            }
            return null;
        }
    }

    private static BufferedImage icon(HudDefinition hud, ResourceLocation texture) throws IOException {
        if (!texture.namespace().equals(hud.namespace())) {
            throw new IOException("its icon " + texture + " is another pack's texture, which BetterHud cannot be given");
        }
        Path file = hud.sourceRoot().resolve("textures").resolve(texture.path() + ".png");
        BufferedImage image = ImageIO.read(file.toFile());
        if (image == null) {
            throw new IOException(file.getFileName() + " is not a PNG this plugin can read");
        }
        return image;
    }

    /**
     * Writes what differs, removes this plugin's files that are no longer wanted. Off the main thread.
     *
     * @return whether anything there changed
     */
    private synchronized boolean write(Map<String, byte[]> files) throws IOException {
        boolean changed = false;
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            Path path = folder.resolve(file.getKey());
            if (Files.isRegularFile(path) && Arrays.equals(Files.readAllBytes(path), file.getValue())) {
                continue;
            }
            Files.createDirectories(path.getParent());
            Files.write(path, file.getValue());
            changed = true;
        }
        for (String name : BetterHudExport.FILES) {
            if (!files.containsKey(name) && Files.deleteIfExists(folder.resolve(name))) {
                changed = true;
            }
        }
        Path assets = folder.resolve(BetterHudExport.ASSETS);
        if (Files.isDirectory(assets)) {
            List<Path> stale;
            try (Stream<Path> list = Files.list(assets)) {
                stale = list.filter(path -> !files.containsKey(BetterHudExport.ASSETS + path.getFileName())).toList();
            }
            for (Path path : stale) {
                Files.deleteIfExists(path);
                changed = true;
            }
            if (files.isEmpty()) {
                Files.deleteIfExists(assets);
            }
        }
        return changed;
    }

    /** Whether BetterHud is running: one that failed to start draws nothing, and the action bar keeps the bars. */
    private boolean running() {
        org.bukkit.plugin.Plugin betterHud = plugin.getServer().getPluginManager().getPlugin("BetterHud");
        return betterHud != null && betterHud.isEnabled();
    }

    /** BetterHud reads its folder as it starts; already running, it is told to read it again. */
    private void reload() {
        if (running()) {
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), "hud reload");
        }
    }
}
