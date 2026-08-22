package com.arkcronist.gen.resources;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The shipped YAML resources have to parse. A config.yml that SnakeYAML rejects
 * takes the whole plugin down at onEnable - the server never gets as far as a
 * world - so a broken quote or a comment line spliced in the wrong place is not
 * a cosmetic defect. These tests read the files exactly as Bukkit does.
 */
class ConfigResourceTest {

    private static Map<String, Object> parse(String resource) {
        Path onDisk = Path.of("src", "main", "resources", resource);
        assertTrue(Files.isRegularFile(onDisk), resource + " is missing from src/main/resources");

        String text = assertDoesNotThrow(() -> Files.readString(onDisk, StandardCharsets.UTF_8));
        Object root;
        try {
            root = new Yaml().load(text);
        } catch (RuntimeException ex) {
            fail(resource + " is not valid YAML: " + ex.getMessage());
            return Map.of();
        }
        assertNotNull(root, resource + " parsed to nothing");
        assertTrue(root instanceof Map, resource + " must be a mapping at the top level");
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) root;
        return map;
    }

    @Test
    void configYmlParses() {
        Map<String, Object> config = parse("config.yml");

        // The keys the plugin reads on startup. If a rename ever lands in the
        // code without landing here, this is where it shows up.
        for (String key : List.of("default-preset", "world", "performance",
                                  "prefabs", "structures", "terrain", "presets")) {
            assertTrue(config.containsKey(key), "config.yml lost its '" + key + "' section");
        }

        Object preset = config.get("default-preset");
        assertTrue(List.of("BASE", "CHAOTIC", "INSANE").contains(String.valueOf(preset)),
                   "default-preset must name a real preset, found: " + preset);

        Object presets = config.get("presets");
        assertTrue(presets instanceof Map, "presets: must be a mapping");
        @SuppressWarnings("unchecked")
        Map<String, Object> byName = (Map<String, Object>) presets;
        // ArkConfig reads "presets." + preset.name().toLowerCase(ROOT), so the section names are
        // lower case while default-preset above is not. Assert the form the code actually looks up.
        for (String name : List.of("BASE", "CHAOTIC", "INSANE")) {
            String key = name.toLowerCase(Locale.ROOT);
            assertTrue(byName.containsKey(key), "presets: lost " + key);
        }
    }

    @Test
    void pluginYmlParses() {
        Map<String, Object> plugin = parse("plugin.yml");
        for (String key : List.of("name", "version", "main", "api-version")) {
            assertTrue(plugin.containsKey(key), "plugin.yml lost its '" + key + "' key");
        }
    }

    /**
     * Bukkit reads the file out of the jar, not off the working directory, so the
     * copy on the classpath is the one that actually has to load.
     */
    @Test
    void configYmlOnTheClasspathParses() {
        try (InputStream in = ConfigResourceTest.class.getResourceAsStream("/config.yml")) {
            assertNotNull(in, "config.yml is not on the classpath");
            Object root = new Yaml().load(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            assertTrue(root instanceof Map, "packaged config.yml must be a mapping");
        } catch (Exception ex) {
            fail("packaged config.yml did not load: " + ex);
        }
    }
}
