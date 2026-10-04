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

    private void write(String relative) throws IOException {
        Path file = plugin.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "owner's");
    }
}
