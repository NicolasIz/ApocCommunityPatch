package com.arkcronist.gen.bukkit;

import com.arkcronist.gen.core.terrain.Preset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading what {@code -g ArkcronistGenerator:<id>} asked for.
 *
 * <p>This is typed once, into a console, and the world it makes is the world you keep. Getting it
 * wrong is not an error anybody sees at the time: the world generates, it just generates as
 * something other than what was asked for, and by the time that is obvious people have built in
 * it.</p>
 */
class GeneratorIdTest {

    @Test
    @DisplayName("a bare preset is the preset, with our own terrain")
    void barePreset() {
        GeneratorId id = GeneratorId.parse("INSANE", Preset.BASE);
        assertEquals(Preset.INSANE, id.preset());
        assertFalse(id.datapackTerrain());
        assertTrue(id.recognised());
    }

    @Test
    @DisplayName("nothing at all falls back without complaining")
    void empty() {
        for (String id : new String[]{null, "", "   "}) {
            GeneratorId parsed = GeneratorId.parse(id, Preset.CHAOTIC);
            assertEquals(Preset.CHAOTIC, parsed.preset(), "for id " + id);
            assertFalse(parsed.datapackTerrain());
            assertTrue(parsed.recognised(), "an absent id is not a typo");
        }
    }

    @Test
    @DisplayName("every spelling of 'let the datapacks do it' is accepted")
    void datapackWords() {
        // Typed once, at the end of a long day. A world made with the wrong spelling is a world
        // generated wrong, so the spellings somebody would reasonably reach for all work.
        for (String word : new String[]{"datapack", "datapacks", "vanilla", "noprefabs",
                "notprefabs", "NOTPREFAB", "DataPack"}) {
            GeneratorId parsed = GeneratorId.parse(word, Preset.BASE);
            assertTrue(parsed.datapackTerrain(), word + " was not understood");
            assertTrue(parsed.recognised(), word + " was reported as a typo");
        }
    }

    @Test
    @DisplayName("a preset and a mode can be asked for together, however they are joined")
    void combined() {
        for (String id : new String[]{"INSANE+datapack", "INSANE:datapack", "INSANE-datapack",
                "INSANE datapack", "datapack+INSANE", "insane_datapacks"}) {
            GeneratorId parsed = GeneratorId.parse(id, Preset.BASE);
            assertEquals(Preset.INSANE, parsed.preset(), "for id " + id);
            assertTrue(parsed.datapackTerrain(), "for id " + id);
            assertTrue(parsed.recognised(), "for id " + id);
        }
    }

    @Test
    @DisplayName("the preset still chooses the mobs in datapack mode")
    void presetSurvivesDatapackMode() {
        // It generates nothing here, but it is what the hostile mob tables are picked by, so an
        // INSANE datapack world must still come out INSANE rather than as the fallback.
        assertEquals(Preset.INSANE, GeneratorId.parse("INSANE+datapack", Preset.BASE).preset());
        assertEquals(Preset.BASE, GeneratorId.parse("datapack", Preset.BASE).preset(),
                "with no preset named, the fallback applies");
    }

    @Test
    @DisplayName("a word nobody recognises is reported, and does not silently become a preset")
    void typos() {
        GeneratorId parsed = GeneratorId.parse("insan", Preset.BASE);
        assertFalse(parsed.recognised(), "a typo was accepted in silence");
        assertEquals(Preset.BASE, parsed.preset());
        assertFalse(parsed.datapackTerrain());

        // The half that was understood still counts, so one typo does not throw away the rest.
        GeneratorId half = GeneratorId.parse("INSANE+datapck", Preset.BASE);
        assertEquals(Preset.INSANE, half.preset());
        assertFalse(half.datapackTerrain(), "a misspelled mode must not be guessed at");
        assertFalse(half.recognised());
    }
}
