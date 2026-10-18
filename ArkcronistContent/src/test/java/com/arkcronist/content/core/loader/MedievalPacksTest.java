package com.arkcronist.content.core.loader;

import com.arkcronist.content.core.definition.ItemDefinition;
import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.furniture.Footprint;
import com.arkcronist.content.core.pack.PackCompiler;
import com.arkcronist.content.core.pack.PackSettings;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 3.5: the four new medieval packs - the mine, the smithy, the harbour and the farm - load and compile. */
class MedievalPacksTest {

    private static final Path SHIPPED = Path.of("src/main/resources/contents");
    private static final List<String> PACKS = List.of("medieval_mineria", "medieval_herreria", "medieval_pesca",
            "medieval_granja");

    @TempDir
    Path temp;

    private static List<ItemDefinition> pieces() throws IOException {
        LoadReport report = new ContentLoader().load(SHIPPED);
        assertEquals(List.of(), report.problems());
        return report.items().stream().filter(item -> PACKS.contains(item.namespace())).toList();
    }

    @Test
    void overThreeHundredPiecesAndEachDoesSomething() throws IOException {
        List<ItemDefinition> pieces = pieces();
        Map<String, Long> byPack = new TreeMap<>(pieces.stream()
                .collect(Collectors.groupingBy(ItemDefinition::namespace, Collectors.counting())));
        assertEquals(Map.of("medieval_granja", 83L, "medieval_herreria", 66L, "medieval_mineria", 87L,
                "medieval_pesca", 69L), byPack);
        for (ItemDefinition piece : pieces) {
            assertTrue(piece.placement() instanceof Placement.Furniture, piece.fullId());
            Placement.Furniture furniture = (Placement.Furniture) piece.placement();
            assertTrue(furniture.interactive(), piece.fullId() + " does something on a click");
            String pack = piece.namespace();
            assertTrue(Files.isRegularFile(SHIPPED.resolve(pack + "/models/furniture/" + piece.id() + ".json")), piece.fullId());
            assertTrue(Files.isRegularFile(SHIPPED.resolve(pack + "/textures/furniture/" + piece.id() + ".png")), piece.fullId());
        }
        Map<String, Placement.Furniture> byId = pieces.stream().collect(Collectors.toMap(ItemDefinition::fullId,
                piece -> (Placement.Furniture) piece.placement()));
        assertEquals("anvil", byId.get("medieval_herreria:yunque_hierro").ambience().station());
        assertEquals(new Footprint(2, 1), byId.get("medieval_herreria:yunque_maestro").footprint());
        assertEquals("blast", byId.get("medieval_herreria:horno_fundicion").ambience().cook());
        assertEquals(27, byId.get("medieval_mineria:vagoneta_hierro").storage().slots());
        assertTrue(byId.get("medieval_mineria:farol_minero").ambience().toggleLight());
        assertEquals(Map.of("thirst", 20.0), byId.get("medieval_pesca:barril_agua_dulce").ambience().hud());
        assertEquals(Placement.Support.LIGHT, byId.get("medieval_pesca:estrella_mar").support());
        assertTrue(byId.get("medieval_granja:fardo_pequeno").seat() != null);
        assertEquals("smoker", byId.get("medieval_pesca:pescado_ahumado").ambience().cook());
    }

    /** Every file of the four packs is listed for servers upgrading to 3.5, and every listed file is shipped. */
    @Test
    void theUpgradeListHasEveryFile() throws IOException {
        List<String> listed = Files.readAllLines(Path.of("src/main/resources/examples-3.5.0.txt")).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#")).toList();
        Set<String> shipped;
        try (var files = Files.walk(SHIPPED)) {
            shipped = files.filter(Files::isRegularFile).map(file -> "contents/" + SHIPPED.relativize(file).toString()
                            .replace('\\', '/'))
                    .filter(path -> PACKS.stream().anyMatch(pack -> path.startsWith("contents/" + pack + "/")))
                    .collect(Collectors.toSet());
        }
        assertEquals(shipped, Set.copyOf(listed));
        assertEquals(listed.size(), Set.copyOf(listed).size(), "no file twice");
    }

    @Test
    void theyCompileIntoThePack() throws IOException {
        List<ItemDefinition> pieces = pieces();
        Path pack = temp.resolve("pack");
        PackCompiler.Result result = new PackCompiler(new PackSettings(new JsonPrimitive("medieval"), 46, 46, 99))
                .compile(pack, pieces, Map.of());
        assertEquals(List.of(), result.problems());
        for (String name : PACKS) {
            try (var models = Files.list(pack.resolve("assets/" + name + "/models/furniture"))) {
                assertTrue(models.count() > 60, name);
            }
        }
    }
}
