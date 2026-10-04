package com.arkcronist.content.core.loader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExampleUpdatesTest {

    private static final Map<String, List<String>> ADDED = Map.of(
            "1.4.0", List.of("contents/demo/armor.yml", "contents/demo/textures/item/ruby_helmet.png"),
            "1.10.0", List.of("contents/demo/wands.yml"),
            "1.5.0", List.of("contents/demo/pets.yml"));

    @TempDir
    Path plugin;

    @Test
    void anUpgradeBringsTheExamplesOfEveryVersionSinceOldestFirst() throws IOException {
        Files.createDirectories(plugin.resolve("contents/demo"));

        assertEquals(List.of("contents/demo/armor.yml", "contents/demo/textures/item/ruby_helmet.png",
                        "contents/demo/pets.yml", "contents/demo/wands.yml"),
                ExampleUpdates.toCopy(ADDED, ExampleUpdates.BEFORE_REMEMBERED, plugin));
        assertEquals(List.of("contents/demo/pets.yml", "contents/demo/wands.yml"),
                ExampleUpdates.toCopy(ADDED, "1.4.0", plugin), "1.4.0's were offered already");
        assertEquals(List.of(), ExampleUpdates.toCopy(ADDED, "1.10.0", plugin));
    }

    @Test
    void nothingIsOverwrittenAndADeletedPackStaysDeleted() throws IOException {
        Files.createDirectories(plugin.resolve("contents/demo"));
        Files.writeString(plugin.resolve("contents/demo/armor.yml"), "the owner's own armour");

        assertEquals(List.of("contents/demo/textures/item/ruby_helmet.png"),
                ExampleUpdates.toCopy(Map.of("1.4.0", ADDED.get("1.4.0")), "1.3.0", plugin));

        Files.delete(plugin.resolve("contents/demo/armor.yml"));
        Files.delete(plugin.resolve("contents/demo"));
        Files.createDirectories(plugin.resolve("contents/gems"));
        assertEquals(List.of(), ExampleUpdates.toCopy(ADDED, "1.3.0", plugin), "no demo folder, no demo");
    }

    @Test
    void versionsCompareNumberByNumber() {
        assertTrue(ExampleUpdates.compare("1.10.0", "1.9.2") > 0);
        assertTrue(ExampleUpdates.compare("1.4.0", "1.3.0") > 0);
        assertEquals(0, ExampleUpdates.compare("1.4", "1.4.0"));
        assertEquals(0, ExampleUpdates.compare("1.4.0-SNAPSHOT", "1.4.0"));
        assertTrue(ExampleUpdates.compare("1.3.0", " 1.4.0\n") < 0);
    }
}
