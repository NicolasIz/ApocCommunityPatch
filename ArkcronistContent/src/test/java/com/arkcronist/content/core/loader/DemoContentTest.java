package com.arkcronist.content.core.loader;

import com.arkcronist.content.core.definition.ItemDefinition;
import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.pack.PackCompiler;
import com.arkcronist.content.core.pack.PackSettings;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The demo content the plugin ships loads and compiles without a single problem. */
class DemoContentTest {

    private static final Path SHIPPED = Path.of("src/main/resources/contents");

    @TempDir
    Path temp;

    @Test
    void theShippedDemoLoadsAndCompilesCleanly() throws IOException {
        LoadReport report = new ContentLoader().load(SHIPPED);
        assertEquals(java.util.List.of(), report.problems());

        Map<String, ItemDefinition> items = report.items().stream()
                .collect(Collectors.toMap(ItemDefinition::fullId, Function.identity()));

        Placement.Furniture chest = (Placement.Furniture) items.get("demo:ruby_chest").placement();
        assertEquals(Placement.Support.CHEST, chest.support());
        assertNotNull(chest.storage(), "a chest is storage");
        assertNotNull(chest.animated());
        assertTrue(chest.animated().model().clips().keySet().containsAll(java.util.List.of("open", "close", "shake")));
        assertNotNull(chest.animated().model().icon(), "its inventory icon is the model at rest");
        // The lid rides on the body: shaking the chest shakes its lid with it.
        var bones = chest.animated().model().bones();
        int body = bones.stream().map(bone -> bone.name()).toList().indexOf("body");
        assertEquals(body, bones.stream().filter(bone -> bone.name().equals("lid")).findFirst().orElseThrow().parent());
        assertEquals("CHEST", items.get("demo:ruby_chest").material());

        Placement.Furniture bed = (Placement.Furniture) items.get("demo:ruby_bed").placement();
        assertEquals(Placement.Support.BED, bed.support());
        assertEquals("RED_BED", items.get("demo:ruby_bed").material());

        Path pack = temp.resolve("pack");
        PackCompiler.Result result = new PackCompiler(new PackSettings(new JsonPrimitive("demo"), 46, 46, 99))
                .compile(pack, report.items(), Map.of());
        assertEquals(java.util.List.of(), result.problems());
        for (String bone : java.util.List.of("body", "lid")) {
            assertTrue(Files.isRegularFile(pack.resolve("assets/demo/items/ruby_chest/bone_" + bone + ".json")), bone);
            assertTrue(Files.isRegularFile(pack.resolve("assets/demo/models/ruby_chest/bone_" + bone + ".json")), bone);
        }
        assertTrue(Files.isRegularFile(pack.resolve("assets/demo/models/furniture/ruby_bed.json")));
    }
}
