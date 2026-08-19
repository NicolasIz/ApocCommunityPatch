package com.arkcronist.gen.bukkit;

import com.arkcronist.gen.core.prefab.PrefabRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Unpacks the bundled schematics into the plugin folder and loads whatever is there.
 *
 * <p>The jar ships a starter set of prefabs, but the folder is the source of truth. Bundled files
 * are written out once and never overwritten afterwards, so a server owner can edit or delete any of
 * them, and anything they add themselves is loaded on exactly the same footing - which is what makes
 * "drop {@code giant_cherry_01.schem} into {@code prefabs/trees/} and restart" a complete
 * installation procedure.</p>
 */
public final class PrefabInstaller {

    private static final String RESOURCE_ROOT = "prefabs/";

    private PrefabInstaller() {
    }

    /**
     * Extracts the bundled prefabs if they are missing, then loads the whole folder.
     *
     * <p>Runs before {@link BlockBridge#initialize(Logger)} so that every block state a prefab
     * mentions is already in the registry when the bridge resolves the table.</p>
     */
    public static PrefabRegistry install(Path dataFolder, Logger logger, boolean extractDefaults) {
        Path root = dataFolder.resolve("prefabs");
        if (extractDefaults) {
            try {
                extract(root, logger);
            } catch (IOException | RuntimeException exception) {
                logger.log(Level.WARNING, "Could not unpack the bundled prefabs", exception);
            }
        }
        PrefabRegistry registry = PrefabRegistry.fromDirectory(root,
                message -> logger.warning("Prefab: " + message));
        if (registry.size() == 0) {
            logger.warning("No prefabs found in " + root + " - trees and ships will not generate");
        } else {
            StringBuilder summary = new StringBuilder();
            for (Map.Entry<String, Integer> entry : new java.util.TreeMap<>(registry.counts()).entrySet()) {
                if (summary.length() > 0) {
                    summary.append(", ");
                }
                summary.append(entry.getValue()).append(' ').append(entry.getKey());
            }
            logger.info("Loaded " + registry.size() + " prefabs (" + summary + ") from " + root);
        }
        return registry;
    }

    /** Copies every {@code prefabs/**} resource out of the jar, skipping files that already exist. */
    private static void extract(Path root, Logger logger) throws IOException {
        List<String> names = bundledNames();
        if (names.isEmpty()) {
            return;
        }
        int written = 0;
        for (String name : names) {
            Path target = root.resolve(name.substring(RESOURCE_ROOT.length()));
            if (Files.exists(target)) {
                continue;
            }
            Files.createDirectories(target.getParent());
            try (InputStream stream = PrefabInstaller.class.getClassLoader().getResourceAsStream(name)) {
                if (stream == null) {
                    continue;
                }
                Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING);
                written++;
            }
        }
        if (written > 0) {
            logger.info("Unpacked " + written + " bundled prefab files into " + root);
        }
    }

    /**
     * Lists the bundled prefab resources.
     *
     * <p>The classpath gives no directory listing, so the plugin's own jar is opened and read. When
     * the classes are not in a jar - which is how the tests run - the resource folder is walked
     * instead.</p>
     */
    private static List<String> bundledNames() throws IOException {
        List<String> names = new ArrayList<>();
        java.net.URL source = PrefabInstaller.class.getProtectionDomain().getCodeSource() == null
                ? null
                : PrefabInstaller.class.getProtectionDomain().getCodeSource().getLocation();
        if (source == null) {
            return names;
        }
        Path location;
        try {
            location = Path.of(source.toURI());
        } catch (java.net.URISyntaxException exception) {
            return names;
        }
        if (Files.isDirectory(location)) {
            Path base = location.resolve(RESOURCE_ROOT);
            if (!Files.isDirectory(base)) {
                return names;
            }
            try (var walk = Files.walk(base)) {
                walk.filter(Files::isRegularFile)
                        .forEach(path -> names.add(RESOURCE_ROOT + base.relativize(path).toString().replace('\\', '/')));
            }
            return names;
        }
        try (ZipFile jar = new ZipFile(location.toFile())) {
            Enumeration<? extends ZipEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory() && entry.getName().startsWith(RESOURCE_ROOT)) {
                    names.add(entry.getName());
                }
            }
        } catch (UncheckedIOException exception) {
            throw new IOException(exception);
        }
        return names;
    }
}
