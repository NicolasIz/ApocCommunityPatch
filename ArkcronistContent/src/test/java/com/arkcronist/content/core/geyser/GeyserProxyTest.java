package com.arkcronist.content.core.geyser;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 3.3: for a Geyser on a proxy - the file the extension reads, the extension itself, and how the
 * note block states for furniture are shared out when there are not enough for every piece.
 */
class GeyserProxyTest {

    @Test
    void theExportReadsBackAsItWasWritten() {
        BedrockConverter.BedrockItem sword = new BedrockConverter.BedrockItem("demo:sword", "ark_demo_sword",
                "minecraft:netherite_sword", 1003, "demo:sword", "ark_demo_sword", "Espada del\tseñor\noscuro, 100% & más", true);
        BedrockConverter.BedrockItem plain = new BedrockConverter.BedrockItem("demo:paper", "ark_demo_paper",
                "minecraft:paper", 1004, "demo:paper", "ark_no_picture", "Paper", false, true);
        Map<String, BedrockConverter.Material> materials = new LinkedHashMap<>();
        materials.put("*", new BedrockConverter.Material("ark_demo_stone", "opaque"));
        materials.put("up", new BedrockConverter.Material("ark_a=b,c&d", "alpha_test"));
        BedrockConverter.BedrockBlock block = new BedrockConverter.BedrockBlock("ark_demo_bench_c0_0_0_r1",
                "minecraft:note_block[instrument=harp,note=24,powered=true]", "geometry.arkcontent.ark_demo_bench_c0_0_0",
                materials, true, true, 12, 1.2f, -90, "Bench");

        String text = GeyserExport.write(List.of(sword, plain), List.of(block), 1);
        assertTrue(text.startsWith(GeyserExport.HEADER + "\n"));
        assertEquals(5, text.lines().count(), "header, carriers, two items, one block - no name splits a line");
        GeyserExport.Content read = GeyserExport.read(text.replace("\n", "\r\n"));
        assertEquals(List.of(sword, plain), read.items());
        assertEquals(List.of(block), read.blocks());
        assertEquals(1, read.carriers());
        assertThrows(IllegalArgumentException.class, () -> GeyserExport.read("something else\n"));
        assertTrue(read.items().get(1).byNumber(), "3.3.2: drawn by number, and said so");

        // A file written by 3.3.1 has nine fields an item: read as not drawn by number.
        String old = GeyserExport.HEADER + "\nitem\tdemo%3Apaper\tark_demo_paper\tminecraft%3Apaper\t1004\tdemo%3Apaper"
                + "\tark_demo_paper\tPaper\tfalse\n";
        BedrockConverter.BedrockItem before = GeyserExport.read(old).items().get(0);
        assertEquals(1004, before.customModelData());
        assertFalse(before.byNumber());
        assertEquals(BedrockConverter.Uses.NONE, before.uses());
    }

    /** 3.4: how an item is used - drunk, one to a slot, worn - goes to the proxy with it, and back. */
    @Test
    void howAnItemIsUsedGoesToTheProxy() {
        BedrockConverter.Uses canteen = new BedrockConverter.Uses(1.2f, 1, 6, null);
        BedrockConverter.Uses helmet = new BedrockConverter.Uses(0, 0, 0, "head");
        assertEquals("drink=1.2;stack=1;damage=6", canteen.encode());
        assertEquals("slot=head", helmet.encode());
        assertEquals("", BedrockConverter.Uses.NONE.encode());
        assertEquals(canteen, BedrockConverter.Uses.decode(canteen.encode()));
        assertEquals(BedrockConverter.Uses.NONE, BedrockConverter.Uses.decode(""));
        assertEquals(new BedrockConverter.Uses(0, 1, 0, null), BedrockConverter.Uses.decode("stack=1;later=what"),
                "what it does not know is left out");

        BedrockConverter.BedrockItem drink = new BedrockConverter.BedrockItem("medieval_props:cantimplora",
                "ark_cantimplora", "minecraft:paper", 1005, "medieval_props:cantimplora", "ark_cantimplora", "Cantimplora",
                false, false, canteen);
        BedrockConverter.BedrockItem worn = new BedrockConverter.BedrockItem("demo:ruby_helmet", "ark_ruby_helmet",
                "minecraft:netherite_helmet", 1006, "demo:ruby_helmet", "ark_ruby_helmet", "Yelmo", false, true, helmet);
        GeyserExport.Content read = GeyserExport.read(GeyserExport.write(List.of(drink, worn), List.of(), 0));
        assertEquals(List.of(drink, worn), read.items());
    }

    /** The extension runs inside Geyser: none of what it loads may need Bukkit, Paper or Gson. */
    @Test
    void theExtensionNeedsNothingButGeyserAndTheJdk() throws IOException {
        String yml = Files.readString(Path.of("src/main/resources/extension.yml"), StandardCharsets.UTF_8);
        assertTrue(yml.contains("id: arkcontent"));
        assertTrue(yml.contains("main: com.arkcronist.content.geyser.ArkContentGeyserExtension"));
        assertTrue(yml.contains("api: 2.8.0"), "loads on Geyser 2.8 and later");
        List<Path> sources = new ArrayList<>();
        try (Stream<Path> files = Files.list(Path.of("src/main/java/com/arkcronist/content/geyser"))) {
            files.forEach(sources::add);
        }
        sources.add(Path.of("src/main/java/com/arkcronist/content/core/geyser/GeyserExport.java"));
        for (Path source : sources) {
            String code = Files.readString(source, StandardCharsets.UTF_8);
            for (String forbidden : List.of("org.bukkit", "io.papermc", "com.google.gson", "net.kyori",
                    "com.arkcronist.content.bukkit")) {
                assertFalse(code.contains(forbidden), source.getFileName() + " uses " + forbidden);
            }
        }
    }

    private static List<String> keys(String piece, int count) {
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            keys.add(piece + "|" + i);
        }
        return keys;
    }

    @Test
    void wholePiecesSmallestFirstAndNoneCutInHalf() {
        Map<String, List<String>> pieces = new TreeMap<>();
        pieces.put("demo:well", keys("demo:well", 8));
        pieces.put("demo:chair", keys("demo:chair", 4));
        pieces.put("demo:throne", keys("demo:throne", 4));
        // Ten numbers, one of them a custom block's: the chair and the throne fit, the well does not.
        BedrockConverter.CarrierPlan plan = BedrockConverter.planCarriers(pieces, Map.of(), Set.of(3), 1, 10);
        assertEquals(Map.of("demo:well", 8), plan.leftOut());
        assertEquals(8, plan.assignments().size());
        assertFalse(plan.assignments().containsValue(3), "a custom block's state is never a carrier");
        assertTrue(plan.assignments().keySet().stream().noneMatch(key -> key.startsWith("demo:well")),
                "none of the well rather than half of it");
    }

    @Test
    void numbersStayWhereTheyWereAndRetiredOnesAreFreed() {
        Map<String, List<String>> pieces = new TreeMap<>();
        pieces.put("demo:chair", keys("demo:chair", 2));
        pieces.put("demo:table", keys("demo:table", 3));
        Map<String, Integer> previous = new TreeMap<>();
        previous.put("demo:chair|0", 5);
        previous.put("demo:chair|1", 6);
        previous.put("demo:gone|0", 1);
        previous.put("demo:gone|1", 2);
        BedrockConverter.CarrierPlan plan = BedrockConverter.planCarriers(pieces, previous, Set.of(), 1, 5 + 1);
        assertEquals(Map.of(), plan.leftOut(), "the gone piece's two numbers went to the table");
        assertEquals(5, plan.assignments().get("demo:chair|0"));
        assertEquals(6, plan.assignments().get("demo:chair|1"));
        assertFalse(plan.assignments().containsKey("demo:gone|0"), "nothing in the world holds a carrier");
        assertEquals(Set.of(1, 2, 3), Set.copyOf(List.of(plan.assignments().get("demo:table|0"),
                plan.assignments().get("demo:table|1"), plan.assignments().get("demo:table|2"))));
    }

    /**
     * 3.5: a piece Bedrock players were shown keeps its carriers when new content comes - before, a
     * smaller new piece took them, and a server adding small props lost its big ones.
     */
    @Test
    void aPieceShownBeforeKeepsItsCarriersAndNewOnesGetWhatIsLeft() {
        Map<String, List<String>> pieces = new TreeMap<>();
        pieces.put("demo:well", keys("demo:well", 4));
        pieces.put("demo:chair", keys("demo:chair", 2));
        pieces.put("demo:stool", keys("demo:stool", 1));
        Map<String, Integer> previous = new TreeMap<>();
        for (int i = 0; i < 4; i++) {
            previous.put("demo:well|" + i, i + 1);
        }
        // Five numbers: four the well's, as before; one left, which the stool fits and the chair does not.
        BedrockConverter.CarrierPlan plan = BedrockConverter.planCarriers(pieces, previous, Set.of(), 1, 5);
        assertEquals(Map.of("demo:chair", 2), plan.leftOut());
        for (int i = 0; i < 4; i++) {
            assertEquals(i + 1, plan.assignments().get("demo:well|" + i));
        }
        assertEquals(5, plan.assignments().get("demo:stool|0"));
        // Numbers held apart (another plan's) go last: the stool takes 5 though 1 is free and soft.
        BedrockConverter.CarrierPlan apart = BedrockConverter.planCarriers(Map.of("demo:stool", keys("demo:stool", 1)),
                Map.of(), Set.of(), 1, 5, Set.of(1, 2, 3, 4));
        assertEquals(5, apart.assignments().get("demo:stool|0"));
    }
}
