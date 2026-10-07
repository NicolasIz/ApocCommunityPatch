package com.arkcronist.content.core.pack;

import com.arkcronist.content.core.definition.FontImageDefinition;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Font images by name - menu backgrounds, icons in lore lines - so text written for ItemsAdder
 * ({@code :skills_menu_book:}, {@code %img_skills_menu_book%}) finds its character.
 *
 * <p>Two sources, the first winning:</p>
 * <ol>
 *   <li><b>{@code font_images:} definitions</b> ({@link FontImageDefinition}): ItemsAdder's names,
 *       imported with its configs. Each is matched to the character a merged pack - ItemsAdder's
 *       generated one - already draws its picture with ({@link #match}): the same picture, at the
 *       {@code y_position} and {@code scale_ratio} given, on the {@code symbol} given. One no pack
 *       draws gets a character of this plugin's pack.</li>
 *   <li><b>The pictures merged packs draw</b>, named after their file
 *       ({@code spectra_aurelium_skills:skills_menu_book.png} is {@code skills_menu_book}): what
 *       ItemsAdder's names are as a rule, for packs whose configs were not imported. A name several
 *       different characters share is left out ({@link #ambiguous()}).</li>
 * </ol>
 *
 * <p>Every image is reachable as {@code namespace:name}, and as plain {@code name} when no other
 * namespace has an image of that name.</p>
 */
public final class FontImages {

    public static final String DEFAULT_FONT = "minecraft:default";
    public static final FontImages EMPTY = new FontImages(Map.of(), Set.of(), 0);

    /**
     * One font image.
     *
     * @param font  the font it is drawn in, {@code namespace:name}
     * @param glyph the character that draws it
     * @param file  the picture, {@code namespace:path.png}
     */
    public record Image(String namespace, String name, String font, String glyph, int ascent, int height,
                        String file) {

        public boolean inDefaultFont() {
            return DEFAULT_FONT.equals(font);
        }

        public String key() {
            return namespace + ":" + name;
        }

        Image named(String namespace, String name) {
            return new Image(namespace, name, font, glyph, ascent, height, file);
        }
    }

    /** A font file of some pack: {@code namespace:name} and its JSON. */
    public record Font(String id, JsonObject json) {
    }

    private final Map<String, Image> byKey;
    private final Set<String> ambiguous;
    private final int defined;

    private FontImages(Map<String, Image> byKey, Set<String> ambiguous, int defined) {
        this.byKey = Map.copyOf(byKey);
        this.ambiguous = Set.copyOf(ambiguous);
        this.defined = defined;
    }

    // ---------------------------------------------------------------- reading packs

    /** Every font file of a pack's base assets, {@code assets/<namespace>/font/<name>.json}. */
    public static List<Font> fontsOf(PackSource source) throws IOException {
        List<Font> fonts = new ArrayList<>();
        for (String file : source.files()) {
            if (PackSource.overlayOf(file) != null) {
                continue;
            }
            String[] parts = file.split("/");
            if (parts.length != 4 || !parts[0].equals("assets") || !parts[2].equals("font") || !parts[3].endsWith(".json")) {
                continue;
            }
            byte[] bytes = source.read(file);
            if (bytes == null) {
                continue;
            }
            try {
                if (JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)) instanceof JsonObject json) {
                    fonts.add(new Font(parts[1] + ":" + parts[3].substring(0, parts[3].length() - 5), json));
                }
            } catch (JsonParseException broken) {
                // A font the client cannot read draws nothing either.
            }
        }
        return fonts;
    }

    /** Every picture {@code fonts} draw on a character of its own, named after its file. */
    public static List<Image> provided(Collection<Font> fonts) {
        List<Image> images = new ArrayList<>();
        for (Font font : fonts) {
            if (font.json().get("providers") instanceof JsonArray providers) {
                for (JsonElement element : providers) {
                    Image image = element instanceof JsonObject provider ? image(font.id(), provider) : null;
                    if (image != null) {
                        images.add(image);
                    }
                }
            }
        }
        return images;
    }

    private static @Nullable Image image(String font, JsonObject provider) {
        if (!(provider.get("type") instanceof JsonElement type) || !type.isJsonPrimitive()
                || !type.getAsString().equals("bitmap") || !(provider.get("file") instanceof JsonElement fileElement)
                || !fileElement.isJsonPrimitive() || !(provider.get("chars") instanceof JsonArray rows) || rows.size() != 1
                || !rows.get(0).isJsonPrimitive()) {
            return null;
        }
        String glyph = rows.get(0).getAsString();
        if (glyph.codePointCount(0, glyph.length()) != 1 || glyph.codePointAt(0) == 0) {
            return null;
        }
        String file = normalise(fileElement.getAsString());
        String path = file.substring(file.indexOf(':') + 1, file.length() - 4);
        String base = path.substring(path.lastIndexOf('/') + 1);
        if (base.isEmpty()) {
            return null;
        }
        int ascent = provider.get("ascent") instanceof JsonElement a && a.isJsonPrimitive() ? a.getAsInt() : 0;
        int height = provider.get("height") instanceof JsonElement h && h.isJsonPrimitive() ? h.getAsInt() : 8;
        return new Image(file.substring(0, file.indexOf(':')), base, font, glyph, ascent, height, file);
    }

    /** {@code namespace:path.png}, lower-case, as the client reads a provider's {@code file}. */
    static String normalise(String file) {
        String value = file.trim().toLowerCase(Locale.ROOT);
        if (value.indexOf(':') < 0) {
            value = "minecraft:" + value;
        }
        return value.endsWith(".png") ? value : value + ".png";
    }

    // ---------------------------------------------------------------- definitions

    /**
     * The character a merged pack already draws {@code definition}'s picture with: the same file,
     * at the {@code y_position} and {@code scale_ratio} given, on the {@code symbol} given when one
     * of them is on it - the default font's first.
     */
    public static Optional<Image> match(FontImageDefinition definition, List<Image> provided) {
        String file = normalise(definition.texture().toString());
        List<Image> candidates = provided.stream()
                .filter(image -> image.file().equals(file))
                .filter(image -> definition.ascent() == null || image.ascent() == definition.ascent())
                .filter(image -> definition.height() == null || image.height() == definition.height())
                .toList();
        if (definition.symbol() != null) {
            List<Image> onSymbol = candidates.stream()
                    .filter(image -> image.glyph().codePointAt(0) == definition.symbol()).toList();
            if (!onSymbol.isEmpty()) {
                candidates = onSymbol;
            }
        }
        List<Image> preferred = candidates.stream().filter(Image::inDefaultFont).toList();
        List<Image> pool = preferred.isEmpty() ? candidates : preferred;
        return pool.stream().findFirst().map(image -> image.named(definition.namespace(), definition.name()));
    }

    /**
     * The index: {@code defined} images - from definitions - over the merged packs' pictures named
     * after their files.
     */
    public static FontImages of(Collection<Image> defined, List<Image> provided) {
        Map<String, List<Image>> fromFiles = new LinkedHashMap<>();
        for (Image image : provided) {
            fromFiles.computeIfAbsent(image.key(), ignored -> new ArrayList<>()).add(image);
            fromFiles.computeIfAbsent(image.name(), ignored -> new ArrayList<>()).add(image);
        }
        Map<String, Image> byKey = new LinkedHashMap<>();
        Set<String> ambiguous = new TreeSet<>();
        for (Map.Entry<String, List<Image>> entry : fromFiles.entrySet()) {
            Image chosen = unique(entry.getValue());
            if (chosen != null) {
                byKey.put(entry.getKey(), chosen);
            } else if (entry.getKey().contains(":")) {
                ambiguous.add(entry.getKey());
            }
        }
        Map<String, List<Image>> plain = new LinkedHashMap<>();
        for (Image image : defined) {
            byKey.put(image.key(), image);
            ambiguous.remove(image.key());
            plain.computeIfAbsent(image.name(), ignored -> new ArrayList<>()).add(image);
        }
        for (Map.Entry<String, List<Image>> entry : plain.entrySet()) {
            Image chosen = unique(entry.getValue());
            if (chosen != null) {
                byKey.put(entry.getKey(), chosen);
            } else {
                // Two namespaces define it differently: only namespace:name tells them apart.
                byKey.remove(entry.getKey());
            }
        }
        return new FontImages(byKey, ambiguous, defined.size());
    }

    /** The one image a name stands for: the same character everywhere, the default font's first. */
    private static @Nullable Image unique(List<Image> images) {
        List<Image> preferred = images.stream().filter(Image::inDefaultFont).toList();
        List<Image> pool = preferred.isEmpty() ? images : preferred;
        Set<String> drawn = new TreeSet<>();
        pool.forEach(image -> drawn.add(image.font() + " " + image.glyph()));
        return drawn.size() == 1 ? pool.get(0) : null;
    }

    /** The height a PNG picture is, from its header; null when the file is not there or not a PNG. */
    public static @Nullable Integer pngHeight(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try (InputStream in = Files.newInputStream(file)) {
            byte[] header = in.readNBytes(24);
            if (header.length < 24 || (header[0] & 0xFF) != 0x89 || header[1] != 'P' || header[2] != 'N' || header[3] != 'G') {
                return null;
            }
            return ((header[20] & 0xFF) << 24) | ((header[21] & 0xFF) << 16) | ((header[22] & 0xFF) << 8)
                    | (header[23] & 0xFF);
        } catch (IOException unreadable) {
            return null;
        }
    }

    // ---------------------------------------------------------------- lookups

    /** An image by {@code name} or {@code namespace:name}, as ItemsAdder text writes it. */
    public Optional<Image> find(String name) {
        return Optional.ofNullable(byKey.get(name.toLowerCase(Locale.ROOT)));
    }

    /** How many images there are, each counted once. */
    public int size() {
        return (int) byKey.keySet().stream().filter(key -> key.contains(":")).count();
    }

    /** How many came from {@code font_images:} definitions. */
    public int defined() {
        return defined;
    }

    /** {@code namespace:name} of the file names left out because several different characters share them. */
    public Set<String> ambiguous() {
        return ambiguous;
    }
}
