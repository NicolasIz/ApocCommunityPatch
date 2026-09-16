package com.arkcronist.gen.bukkit;

import com.arkcronist.gen.bukkit.config.ArkConfig;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Adopting a world this generator did not make.
 *
 * <p>The point of the feature is that a world built by a terrain datapack, another generator, or by
 * hand can still have the custom mobs in it. Getting the lookup wrong has two failure modes and both
 * are silent: a world that should be adopted is not, and nothing spawns; or every world matches and
 * the mobs turn up in the lobby.</p>
 */
class AdoptedWorldsTest {

    private static ArkConfig configOf(String yaml) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(yaml);
        } catch (org.bukkit.configuration.InvalidConfigurationException exception) {
            throw new AssertionError("the test's own yaml does not parse", exception);
        }
        return new ArkConfig(config);
    }

    @Test
    @DisplayName("a named world resolves to the preset beside it")
    void resolvesNamedWorld() {
        ArkConfig config = configOf("""
                hostile-mobs:
                  adopt-worlds:
                    terralith_world: INSANE
                    old_survival: base
                """);
        assertEquals("INSANE", config.adoptedPreset("terralith_world"));
        // Written in lower case by the admin, and still a preset.
        assertEquals("BASE", config.adoptedPreset("old_survival"));
    }

    @Test
    @DisplayName("world names are matched however they are capitalised")
    void caseInsensitive() {
        // Bukkit world names are case sensitive on disk but admins do not type them that way.
        ArkConfig config = configOf("""
                hostile-mobs:
                  adopt-worlds:
                    Terralith_World: INSANE
                """);
        assertEquals("INSANE", config.adoptedPreset("terralith_world"));
        assertEquals("INSANE", config.adoptedPreset("TERRALITH_WORLD"));
    }

    @Test
    @DisplayName("a world nobody named is not adopted")
    void unnamedWorldIsNotAdopted() {
        ArkConfig config = configOf("""
                hostile-mobs:
                  adopt-worlds:
                    terralith_world: INSANE
                """);
        assertNull(config.adoptedPreset("world"),
                "the server's own world was adopted without being asked for");
        assertNull(config.adoptedPreset("lobby"));
    }

    @Test
    @DisplayName("no section at all adopts nothing")
    void absentSectionAdoptsNothing() {
        assertNull(configOf("hostile-mobs:\n  presets: [INSANE]\n").adoptedPreset("world"));
        assertNull(configOf("").adoptedPreset("world"));
    }

    @Test
    @DisplayName("an entry with no preset is ignored rather than matching everything")
    void blankPresetIsIgnored() {
        // An admin half-way through editing. The dangerous reading of a blank value is "any preset".
        ArkConfig config = configOf("""
                hostile-mobs:
                  adopt-worlds:
                    half_edited: ""
                """);
        assertNull(config.adoptedPreset("half_edited"));
    }
}
