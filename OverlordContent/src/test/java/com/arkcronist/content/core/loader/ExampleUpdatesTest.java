package com.arkcronist.content.core.loader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExampleUpdatesTest {

    private static final Map<String, List<String>> SETS = Map.of(
            "1.1.0", List.of("contents/demo/crops.yml", "contents/demo/textures/crop/stage_0.png"),
            "1.3.0", List.of("contents/demo/furniture.yml", "contents/demo/models/furniture/ruby_bed.json"),
            "1.4.0", List.of("contents/demo/armor.yml", "contents/demo/textures/item/ruby_helmet.png"));

    @TempDir
    Path plugin;

    @Test
    void aFolderFromBeforeSetsWereRememberedGetsTheSetsItNeverHad() throws IOException {
        write("contents/demo/items.yml");
        write("contents/demo/crops.yml");   // 1.1 was installed here; its stage texture, deleted since

        assertEquals(List.of("contents/demo/furniture.yml", "contents/demo/models/furniture/ruby_bed.json",
                        "contents/demo/armor.yml", "contents/demo/textures/item/ruby_helmet.png"),
                ExampleUpdates.toCopy(SETS, Set.of(), plugin));
    }

    @Test
    void anOfferedSetIsNotOfferedAgain() throws IOException {
        write("contents/demo/items.yml");
        // What 1.4.0 remembered: the one set it knew. The chest and bed it never offered still come.
        assertEquals(List.of("contents/demo/crops.yml", "contents/demo/textures/crop/stage_0.png",
                        "contents/demo/furniture.yml", "contents/demo/models/furniture/ruby_bed.json"),
                ExampleUpdates.toCopy(SETS, ExampleUpdates.parse(List.of("1.4.0", "")), plugin));
        assertEquals(List.of(), ExampleUpdates.toCopy(SETS, Set.of("1.1.0", "1.3.0", "1.4.0"), plugin));
    }

    @Test
    void aDeletedPackStaysDeleted() throws IOException {
        write("contents/gems/items.yml");
        assertEquals(List.of(), ExampleUpdates.toCopy(SETS, Set.of(), plugin), "no demo folder, no demo");
    }

    /** 1.9.5: a version that brings a pack of its own creates its folder - once. */
    @Test
    void aSetThatBringsANewPackCreatesItsFolder() throws IOException {
        write("contents/gems/items.yml");    // the demo was deleted: it stays deleted
        List<String> first = List.of("contents/demo/items.yml");
        Map<String, List<String>> sets = new java.util.HashMap<>(SETS);
        sets.put("1.9.5", List.of("contents/medieval_props/furniture.yml",
                "contents/medieval_props/textures/furniture/medieval_atlas.png"));
        assertEquals(List.of("contents/medieval_props/furniture.yml",
                        "contents/medieval_props/textures/furniture/medieval_atlas.png"),
                ExampleUpdates.toCopy(first, sets, Set.of("1.1.0"), plugin));
        // Offered once: deleted afterwards, it does not come back.
        assertEquals(List.of(), ExampleUpdates.toCopy(first, sets, Set.of("1.1.0", "1.3.0", "1.4.0", "1.9.5"), plugin));
        // And a later set adding to it, once it is gone, adds nothing.
        sets.put("1.9.6", List.of("contents/medieval_props/more.yml"));
        assertEquals(List.of(), ExampleUpdates.toCopy(first, sets, Set.of("1.1.0", "1.3.0", "1.4.0", "1.9.5"), plugin));
    }

    /** 3.6: 1.9.6 lists 1.9.5's textures again (repainted); an upgrade from before both copies each once. */
    @Test
    void aFileTwoSetsListIsCopiedOnce() throws IOException {
        write("contents/demo/items.yml");
        List<String> first = List.of("contents/demo/items.yml");
        Map<String, List<String>> sets = Map.of(
                "1.9.5", List.of("contents/medieval_props/furniture.yml", "contents/medieval_props/textures/forja.png"),
                "1.9.6", List.of("contents/medieval_props/textures/forja.png", "contents/medieval_props/more.yml"));
        assertEquals(List.of("contents/medieval_props/furniture.yml", "contents/medieval_props/textures/forja.png",
                        "contents/medieval_props/more.yml"),
                ExampleUpdates.toCopy(first, sets, Set.of(), plugin));
    }

    @Test
    void anExampleIsUpdatedOnlyWhileItIsAsShipped() throws IOException {
        write("contents/demo/blocks.yml");                       // "owner's": not a shipped version
        Path chest = plugin.resolve("contents/demo/models/furniture/ruby_chest.bbmodel");
        Files.createDirectories(chest.getParent());
        Files.writeString(chest, "the 1.3 chest");
        Map<String, Set<String>> history = ExampleUpdates.parseHistory(List.of(
                "# comment", "",
                "contents/demo/models/furniture/ruby_chest.bbmodel "
                        + ExampleUpdates.sha1("the 1.3 chest".getBytes(java.nio.charset.StandardCharsets.UTF_8)).toUpperCase(),
                "contents/demo/blocks.yml 0000000000000000000000000000000000000000",
                "contents/demo/gone.yml 1111111111111111111111111111111111111111"));

        assertEquals(List.of("contents/demo/models/furniture/ruby_chest.bbmodel"), ExampleUpdates.outdated(history, plugin));
    }

    private void write(String relative) throws IOException {
        Path file = plugin.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "owner's");
    }
}
