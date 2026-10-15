package com.arkcronist.content.core.hud;

import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * 3.1.1: the status bars a BetterHud setup already draws - PrimeHUD's health and hunger bars, each a
 * frame image {@code health_bar} / {@code hunger_bar} with a fill over it - found in its folder, so
 * this plugin's bars are drawn as more of them, in rows of their own under them, instead of as a
 * second HUD of their own.
 *
 * <p>Read from BetterHud's {@code images/}, {@code layouts/} and {@code huds/}: where the two frames
 * sit in their layouts (their columns; the lowest row of status bars - {@code armor_bar} and
 * {@code air_bar} too - and the step between rows), how big the health frame is, and where the HUD
 * that shows them is placed. Without both frames, 12 pixels tall, there is no panel.</p>
 */
public final class BetterHudPanel {

    /** The frames a panel of status bars is recognised by, and those whose rows ours go under. */
    private static final List<String> STATUS = List.of("health_bar", "hunger_bar", "armor_bar", "air_bar", "absorp_bar");
    /** How tall a status bar's frame is, PrimeHUD's: the art this plugin draws is for that height. */
    public static final int FRAME_HEIGHT = 12;
    /** Between two rows of status bars, when BetterHud's own show only one. */
    private static final int DEFAULT_STEP = 15;

    private BetterHudPanel() {
    }

    /**
     * Where this plugin's bars go.
     *
     * @param gui      the HUD's {@code gui}, as BetterHud's own status bars' HUD has it
     * @param pixel    its {@code pixel}
     * @param left     the left column's {@code x}, the health bar's
     * @param right    the right column's {@code x}, the hunger bar's
     * @param firstRow the {@code y} of the first row free under BetterHud's status bars
     * @param step     from one row to the next
     * @param width    a frame's width, the health bar's
     */
    public record Panel(HudDefinition.Spot gui, HudDefinition.Spot pixel, int left, int right, int firstRow, int step,
                        int width) {

        /** The {@code y} of row {@code n} of this plugin's, from 0. */
        public int row(int n) {
            return firstRow + n * step;
        }
    }

    /** The panel of status bars in BetterHud's folder, if it has one. Reads files: off the main thread. */
    public static Optional<Panel> find(Path betterHud) {
        try {
            Map<String, Object> images = merged(betterHud.resolve("images"));
            Map<String, Object> layouts = merged(betterHud.resolve("layouts"));
            Map<String, Object> huds = merged(betterHud.resolve("huds"));
            return find(images, layouts, huds, shown(betterHud.resolve("config.yml")),
                    file -> size(betterHud.resolve("assets").resolve(file)));
        } catch (IOException | YAMLException | ClassCastException failure) {
            return Optional.empty();
        }
    }

    /**
     * As {@link #find(Path)}, from BetterHud's files already read; {@code shown} are the HUDs its
     * config.yml gives every player ({@code default-hud}), and {@code size} gives an asset's
     * {width, height}.
     */
    static Optional<Panel> find(Map<String, Object> images, Map<String, Object> layouts, Map<String, Object> huds,
                                java.util.Set<String> shown, java.util.function.Function<String, int[]> size) {
        Map<String, int[]> at = new HashMap<>();
        Map<String, String> layoutOf = new HashMap<>();
        for (Map.Entry<String, Object> layout : layouts.entrySet()) {
            if (!(layout.getValue() instanceof Map<?, ?> body) || !(body.get("images") instanceof Map<?, ?> list)) {
                continue;
            }
            for (Object entry : list.values()) {
                if (entry instanceof Map<?, ?> image && image.get("name") instanceof String name
                        && STATUS.contains(name) && !at.containsKey(name)) {
                    at.put(name, new int[]{integer(image.get("x")), integer(image.get("y"))});
                    layoutOf.put(name, layout.getKey());
                }
            }
        }
        int[] health = at.get("health_bar");
        int[] hunger = at.get("hunger_bar");
        if (health == null || hunger == null || !(images.get("health_bar") instanceof Map<?, ?> frame)
                || !(frame.get("file") instanceof String file)) {
            return Optional.empty();
        }
        int[] frameSize = size.apply(file);
        if (frameSize == null || frameSize[1] != FRAME_HEIGHT || frameSize[0] < 16) {
            return Optional.empty();
        }
        TreeSet<Integer> rows = new TreeSet<>();
        at.values().forEach(spot -> rows.add(spot[1]));
        // The step between rows: the smallest gap between two of them, a frame or more apart.
        int step = 0;
        Integer previous = null;
        for (int row : rows) {
            int gap = previous == null ? 0 : row - previous;
            if (gap >= FRAME_HEIGHT && (step == 0 || gap < step)) {
                step = gap;
            }
            previous = row;
        }
        if (step == 0) {
            step = DEFAULT_STEP;
        }
        HudDefinition.Spot[] place = placeOf(huds, layoutOf.get("health_bar"), shown);
        if (place == null) {
            return Optional.empty();
        }
        return Optional.of(new Panel(place[0], place[1], health[0], hunger[0], rows.last() + step, step, frameSize[0]));
    }

    /**
     * The {@code gui} and {@code pixel} of the HUD that shows a layout - {@code gui:} and
     * {@code pixel:}, or {@code x}/{@code y} - preferring one players are given: listed in
     * {@code default-hud} or {@code default: true}. BetterHud's own example HUD ({@code test_hud})
     * uses the same layout names as PrimeHUD's and is left in the folder, unshown.
     */
    private static @Nullable HudDefinition.Spot[] placeOf(Map<String, Object> huds, String layout,
                                                          java.util.Set<String> shown) {
        HudDefinition.Spot[] fallback = null;
        for (Map.Entry<String, Object> hud : new java.util.TreeMap<>(huds).entrySet()) {
            if (!(hud.getValue() instanceof Map<?, ?> body) || !(body.get("layouts") instanceof Map<?, ?> list)) {
                continue;
            }
            for (Object entry : list.values()) {
                if (entry instanceof Map<?, ?> used && layout.equals(used.get("name"))) {
                    HudDefinition.Spot gui = used.get("gui") instanceof Map<?, ?> g
                            ? new HudDefinition.Spot(integer(g.get("x")), integer(g.get("y")))
                            : new HudDefinition.Spot(integer(used.get("x")), integer(used.get("y")));
                    HudDefinition.Spot pixel = used.get("pixel") instanceof Map<?, ?> p
                            ? new HudDefinition.Spot(integer(p.get("x")), integer(p.get("y")))
                            : new HudDefinition.Spot(0, 0);
                    HudDefinition.Spot[] place = new HudDefinition.Spot[]{gui, pixel};
                    if (shown.contains(hud.getKey()) || Boolean.TRUE.equals(body.get("default"))) {
                        return place;
                    }
                    if (fallback == null) {
                        fallback = place;
                    }
                }
            }
        }
        return fallback;
    }

    /** The HUDs BetterHud's config.yml gives every player: its {@code default-hud} list. */
    static java.util.Set<String> shown(Path config) {
        if (!Files.isRegularFile(config)) {
            return java.util.Set.of();
        }
        try (Reader reader = Files.newBufferedReader(config, StandardCharsets.UTF_8)) {
            Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(reader);
            if (loaded instanceof Map<?, ?> map && map.get("default-hud") instanceof List<?> list) {
                java.util.Set<String> names = new java.util.HashSet<>();
                list.forEach(name -> names.add(String.valueOf(name)));
                return names;
            }
        } catch (IOException | YAMLException unreadable) {
            // Without it, any HUD that shows the layout will do.
        }
        return java.util.Set.of();
    }

    private static int integer(Object value) {
        if (value instanceof Number number) {
            return (int) Math.round(number.doubleValue());
        }
        if (value instanceof String text) {
            try {
                return (int) Math.round(Double.parseDouble(text.trim()));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    /** Every top-level entry of every .yml in a folder (files whose name starts with '-' are skipped, as BetterHud skips them). */
    static Map<String, Object> merged(Path folder) throws IOException {
        Map<String, Object> all = new HashMap<>();
        if (!Files.isDirectory(folder)) {
            return all;
        }
        List<Path> files;
        try (Stream<Path> list = Files.walk(folder, 4)) {
            files = new ArrayList<>(list.filter(path -> path.getFileName().toString().endsWith(".yml")
                    && !path.getFileName().toString().startsWith("-")).sorted().toList());
        }
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        for (Path file : files) {
            // Ours is left out: the panel is BetterHud's own bars, not the ones this plugin wrote.
            if (file.getFileName().toString().equals(BetterHudExport.NAME + ".yml")) {
                continue;
            }
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                Object loaded = yaml.load(reader);
                if (loaded instanceof Map<?, ?> map) {
                    map.forEach((key, value) -> all.putIfAbsent(String.valueOf(key), value));
                }
            } catch (YAMLException broken) {
                // BetterHud says what is wrong with its own files; one that does not read is skipped here.
            }
        }
        return all;
    }

    /** A PNG's {width, height}, read from its header; null when it is not there or not one. */
    private static @Nullable int[] size(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                return new int[]{reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        } catch (IOException unreadable) {
            return null;
        }
    }
}
