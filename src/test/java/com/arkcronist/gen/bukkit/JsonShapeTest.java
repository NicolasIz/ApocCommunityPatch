package com.arkcronist.gen.bukkit;

import com.arkcronist.gen.bukkit.datapack.JsonShape;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deciding whether a datapack file will survive being read.
 *
 * <p>The cost of a false negative is a pack reported as broken when it is fine, which sends somebody
 * looking for a problem that is not there. The cost of a false positive is worse only because it is
 * the thing this exists to prevent: a file that will not parse, in a pack that says it replaces
 * something vanilla, silently removing that piece of the game from every world on the server.</p>
 */
class JsonShapeTest {

    @Test
    @DisplayName("ordinary datapack files are well formed")
    void accepts() {
        assertTrue(JsonShape.wellFormed("{}"));
        assertTrue(JsonShape.wellFormed("  { \"a\" : 1 }  "));
        assertTrue(JsonShape.wellFormed("""
                {
                  "type": "minecraft:smithing_transform",
                  "addition": "#minecraft:netherite_tool_materials",
                  "result": {"id": "minecraft:netherite_helmet", "count": 1},
                  "nested": [1, -2.5, 3e10, 4E-2, true, false, null, [], {}]
                }
                """));
        assertTrue(JsonShape.wellFormed("[]"));
        assertTrue(JsonShape.wellFormed("\"just a string\""));
        assertTrue(JsonShape.wellFormed("42"));
    }

    @Test
    @DisplayName("escapes inside strings are read, not tripped over")
    void escapes() {
        assertTrue(JsonShape.wellFormed("{\"a\": \"a \\\" quote and a \\\\ slash\"}"));
        assertTrue(JsonShape.wellFormed("{\"a\": \"\\u00e9\\n\\t\\/\"}"));
        // A brace inside a string is text, which is exactly what the pack.mcmeta reader had to
        // learn the hard way.
        assertTrue(JsonShape.wellFormed("{\"a\": \"{not an object}\"}"));

        assertFalse(JsonShape.wellFormed("{\"a\": \"\\q\"}"), "an invalid escape was accepted");
        assertFalse(JsonShape.wellFormed("{\"a\": \"\\u00g0\"}"), "a bad hex escape was accepted");
    }

    @Test
    @DisplayName("the shapes that actually turn up broken are rejected")
    void rejects() {
        // The real one: "Unterminated object at line 21" in a shipped loot table.
        assertFalse(JsonShape.wellFormed("{\"pools\": [{\"entries\": [{\"value\": {"));
        assertFalse(JsonShape.wellFormed(""));
        assertFalse(JsonShape.wellFormed("   "));
        assertFalse(JsonShape.wellFormed(null));
        assertFalse(JsonShape.wellFormed("not json at all"));
        assertFalse(JsonShape.wellFormed("{\"a\": 1,}"), "a trailing comma was accepted");
        assertFalse(JsonShape.wellFormed("[1, 2,]"), "a trailing comma was accepted");
        assertFalse(JsonShape.wellFormed("{a: 1}"), "an unquoted key was accepted");
        assertFalse(JsonShape.wellFormed("{'a': 1}"), "a single-quoted key was accepted");
        assertFalse(JsonShape.wellFormed("{\"a\" 1}"));
        assertFalse(JsonShape.wellFormed("{} trailing"));
    }

    @Test
    @DisplayName("numbers are read the way JSON defines them")
    void numbers() {
        assertTrue(JsonShape.wellFormed("-0.5e+3"));
        assertFalse(JsonShape.wellFormed("+1"), "a leading plus was accepted");
        assertFalse(JsonShape.wellFormed(".5"), "a bare decimal point was accepted");
        assertFalse(JsonShape.wellFormed("1."), "a trailing decimal point was accepted");
        assertFalse(JsonShape.wellFormed("1e"), "an exponent with no digits was accepted");
    }

    @Test
    @DisplayName("valid JSON with nonsense in it still reads as valid")
    void validButWrong() {
        // The four netherite armour recipes that cost a server its netherite armour were shaped
        // exactly like this. Well formed, and empty where the item id belongs. This check is about
        // files the game cannot read, not files it can read and then reject, and saying so is the
        // difference between a warning that can be trusted and one that cannot.
        assertTrue(JsonShape.wellFormed("{\"result\": {\"id\": \"\"}}"));
    }
}
