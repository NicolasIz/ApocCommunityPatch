package com.arkcronist.gen.bukkit;

import com.arkcronist.gen.bukkit.datapack.PackMeta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading the version range out of a pack.mcmeta.
 *
 * <p>The shapes below are the ones real datapacks are written in, and they disagree with each other
 * enough that getting this wrong has a specific, expensive failure: a pack is copied into the world
 * folder, the server declines to load it without saying anything, and the structures it was supposed
 * to add simply never appear.</p>
 */
class PackMetaTest {

    private static final int SERVER_1_21_8 = 81;

    @Test
    @DisplayName("a bare pack_format means that version and no other")
    void bareFormat() {
        PackMeta meta = PackMeta.read("""
                {"pack": {"pack_format": 81, "description": "Revamping villages"}}
                """);
        assertEquals(new PackMeta(81, 81), meta);
        assertTrue(meta.supports(81));
        assertFalse(meta.supports(80));
        assertFalse(meta.supports(88));
    }

    @Test
    @DisplayName("supported_formats gives a range, as an array or as an object")
    void ranges() {
        assertEquals(new PackMeta(48, 88), PackMeta.read("""
                {"pack": {"pack_format": 48, "supported_formats": [48, 88]}}
                """));
        assertEquals(new PackMeta(48, 88), PackMeta.read("""
                {"pack": {"pack_format": 48,
                          "supported_formats": {"min_inclusive": 48, "max_inclusive": 88}}}
                """));
    }

    @Test
    @DisplayName("min_format and max_format carry a major and a minor, and only the major counts")
    void majorMinor() {
        // The 1.21.9 form. [88, 0] is major 88, minor 0, and a server either is on major 88 or is
        // not - the minor never decides whether a pack loads.
        assertEquals(new PackMeta(88, 88), PackMeta.read("""
                {"pack": {"description": "Adds some new structures",
                          "min_format": [88, 0], "max_format": [88, 0]}}
                """));

        // The same thing written as one number with the minor after the point.
        assertEquals(new PackMeta(101, 101), PackMeta.read("""
                {"pack": {"description": "By lightninng", "pack_format": 101.1}}
                """));
    }

    @Test
    @DisplayName("an overlay's own range is not the pack's range")
    void overlaysAreNotThePack() {
        // This is the trap. A pack declares the versions it loads on, and each overlay declares the
        // versions that overlay applies over. Reading the file as a flat bag of keys picks up
        // whichever came last, and the overlay is always last.
        PackMeta meta = PackMeta.read("""
                {
                  "pack": {"description": "x", "min_format": [88, 0], "max_format": [88, 0]},
                  "overlays": {"entries": [
                    {"directory": "old", "min_format": [71, 0], "max_format": [80, 0]}
                  ]}
                }
                """);
        assertEquals(new PackMeta(88, 88), meta,
                "the overlay's range was read as the pack's own");
    }

    @Test
    @DisplayName("braces inside a description are text, not structure")
    void descriptionIsNotParsed() {
        // Incendium's description is a list of objects, each with its own braces and colons, sitting
        // between the opening of "pack" and the keys that matter.
        PackMeta meta = PackMeta.read("""
                {"pack": {
                    "pack_format": 71,
                    "description": [
                        {"text": "Incendium", "color": "#e27a1f"},
                        {"text": " - a {nested} \\" quote", "color": "gray"}
                    ],
                    "supported_formats": [71, 88]
                }}
                """);
        assertEquals(new PackMeta(71, 88), meta);
    }

    @Test
    @DisplayName("a verdict says which way a pack misses")
    void verdicts() {
        assertEquals(PackMeta.Verdict.COMPATIBLE,
                new PackMeta(71, 88).verdictFor(SERVER_1_21_8));
        assertEquals(PackMeta.Verdict.TOO_NEW,
                new PackMeta(101, 101).verdictFor(SERVER_1_21_8),
                "a pack for a later game must not read as one for an earlier one");
        assertEquals(PackMeta.Verdict.TOO_OLD,
                new PackMeta(48, 71).verdictFor(SERVER_1_21_8));
    }

    @Test
    @DisplayName("nothing usable reads as nothing, not as a guess")
    void unreadable() {
        assertNull(PackMeta.read(null));
        assertNull(PackMeta.read(""));
        assertNull(PackMeta.read("not json at all"));
        assertNull(PackMeta.read("{\"pack\": {\"description\": \"no format anywhere\"}}"));
        assertNull(PackMeta.read("{\"pack_format\": 81}"),
                "a format outside the pack object is not the pack's format");
    }

    @Test
    @DisplayName("the range reads the way a person would write it")
    void readable() {
        assertEquals("81", new PackMeta(81, 81).range());
        assertEquals("71-88", new PackMeta(71, 88).range());
    }
}
