package com.arkcronist.content.core.importer;

import com.arkcronist.content.core.definition.ContentType;
import com.arkcronist.content.core.definition.ItemDefinition;
import com.arkcronist.content.core.definition.ModelSource;
import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.definition.ResourceLocation;
import com.arkcronist.content.core.loader.ContentLoader;
import com.arkcronist.content.core.loader.LoadReport;
import com.arkcronist.content.core.pack.PackCompiler;
import com.arkcronist.content.core.pack.PackSettings;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemsAdderImporterTest {

    private static final PackSettings SETTINGS = new PackSettings(new JsonPrimitive("test"), 46, 46, 99);

    @TempDir
    Path temp;

    private Path importDir() {
        return temp.resolve("import");
    }

    private Path contents() {
        return temp.resolve("contents");
    }

    /**
     * A pack in ItemsAdder's recommended layout, with every kind of entry this importer translates.
     * What comes out has to load and compile with no complaint at all: that is the importer's whole
     * contract with the rest of the plugin.
     */
    @Test
    void anItemsAdderPackComesOutAsContentTheLoaderAndCompilerTakeAsIs() throws IOException {
        Path pack = importDir().resolve("contents/myitems");
        write(pack, "configs/items.yml", """
                info:
                  namespace: myitems
                items:
                  ruby:
                    display_name: "&cRuby"
                    permission: ruby
                    lore:
                      - '&7Cut from &lthe deep &7caves'
                    resource:
                      material: PAPER
                      generate: true
                      textures:
                        - item/ruby.png
                  ruby_sword:
                    name: display-name-ruby_sword
                    resource:
                      material: DIAMOND_SWORD
                      generate: true
                      textures: [item/ruby_sword.png]
                    durability:
                      max_durability: 200
                  floating_sword:
                    name: Floating Sword
                    resource:
                      material: DIAMOND_SWORD
                      generate: false
                      model_path: item/floating_sword
                """);
        write(pack, "configs/blocks.yml", """
                info:
                  namespace: myitems
                items:
                  red_block:
                    display_name: Red Block
                    resource:
                      material: PAPER
                      generate: true
                      textures:
                        - block/red_block_down.png
                        - block/red_block_east.png
                        - block/red_block_north.png
                        - block/red_block_south.png
                        - block/red_block_up.png
                        - block/red_block_west.png
                    behaviours:
                      block:
                        placed_model:
                          type: REAL_NOTE
                        drop_when_mined: false
                        hardness: 2
                  modern_block:
                    name: Modern Block
                    graphics:
                      texture: block/modern_block
                    behaviours:
                      block:
                        placed_model:
                          type: REAL_NOTE
                """);
        write(pack, "configs/furniture.yml", """
                info:
                  namespace: myitems
                items:
                  lava_lamp:
                    name: Lava Lamp
                    resource:
                      material: PAPER
                      generate: false
                      model_path: lava_lamp
                    behaviours:
                      furniture:
                        entity: item_display
                        light_level: 7
                        display_transformation:
                          transform: HEAD
                          right_rotation:
                            axis_angle:
                              angle: 180
                              axis: {x: 0, y: 1, z: 0}
                          translation: {x: 0, y: 0.92, z: 0}
                          scale: {x: 0.45, y: 0.45, z: 0.45}
                  table:
                    name: Table
                    resource:
                      material: PAPER
                      generate: false
                      model_path: item/table
                    behaviours:
                      furniture:
                        entity: armor_stand
                        solid: true
                        fixed_rotation: true
                        hitbox: {length: 2, width: 1, height: 1}
                recipes:
                  crafting_table: {}
                """);
        write(pack, "configs/dictionaries/en.yml", """
                info:
                  namespace: myitems_lang
                  dictionary-lang: en
                dictionary:
                  display-name-ruby_sword: "&bRuby Sword"
                """);
        write(pack, "configs/dictionaries/fr.yml", """
                info:
                  namespace: myitems_lang
                  dictionary-lang: fr
                dictionary:
                  display-name-ruby_sword: "Épée de rubis"
                """);
        for (String texture : List.of("item/ruby", "item/ruby_sword", "item/floating_sword_blade",
                "item/table_wood", "block/modern_block", "lamp_glass")) {
            write(pack, "textures/" + texture + ".png", png(texture));
        }
        for (String face : List.of("down", "east", "north", "south", "up", "west")) {
            write(pack, "textures/block/red_block_" + face + ".png", png(face));
        }
        write(pack, "textures/item/ruby.png.mcmeta", "{\"animation\": {\"frametime\": 4}}");
        // Exported from Blockbench without the namespace: to the client that means minecraft.
        write(pack, "models/item/floating_sword.json", """
                {"parent": "item/handheld", "textures": {"0": "item/floating_sword_blade", "particle": "#0"}}
                """);
        write(pack, "models/item/table.json", """
                {"textures": {"0": "myitems:item/table_wood"}, "elements": []}
                """);
        write(pack, "models/lava_lamp.json", """
                {"textures": {"glass": "myitems:lamp_glass", "base": "block/stone"}}
                """);

        ImportReport report = new ItemsAdderImporter().run(importDir(), contents());

        // The bare path is reported, never rewritten: the model is the client's to read as written.
        assertEquals(List.of("contents/myitems/configs/items.yml > myitems:floating_sword: model"
                + " myitems:item/floating_sword: texture '0' is 'item/floating_sword_blade' with no namespace, which"
                + " the client reads as minecraft:item/floating_sword_blade - the model is copied unchanged; write"
                + " myitems:item/floating_sword_blade in it if it shows purple and black"), report.problems());
        assertEquals(7, report.items());
        assertEquals(0, report.skipped());
        assertEquals(3, report.converted());
        assertEquals(List.of("myitems/imported/contents/myitems/configs/blocks.yml",
                "myitems/imported/contents/myitems/configs/furniture.yml",
                "myitems/imported/contents/myitems/configs/items.yml"), report.written());
        // 12 textures, 1 animation, 3 models.
        assertEquals(16, report.resources());
        assertNote(report, "myitems:ruby_sword: not imported - durability");
        assertNote(report, "myitems:red_block: not imported - behaviours.block.hardness");
        assertNote(report, "furniture.yml: not imported - recipes");
        assertNote(report, "myitems:table: hitbox is larger than one block");

        LoadReport loaded = new ContentLoader().load(contents());
        assertEquals(List.of(), loaded.problems());
        Map<String, ItemDefinition> items = loaded.items().stream()
                .collect(Collectors.toMap(ItemDefinition::fullId, Function.identity()));

        ItemDefinition ruby = items.get("myitems:ruby");
        assertEquals("PAPER", ruby.material());
        assertEquals("<red>Ruby", ruby.displayName());
        assertEquals(List.of("<gray>Cut from <bold>the deep <reset><gray>caves"), ruby.lore());
        ModelSource.Generated rubyModel = (ModelSource.Generated) ruby.model();
        assertEquals(Map.of("layer0", new ResourceLocation("myitems", "item/ruby")), rubyModel.textures());
        assertEquals(ModelSource.ITEM_GENERATED, rubyModel.parent());

        ItemDefinition sword = items.get("myitems:ruby_sword");
        // The English dictionary wins over the French one, whichever is read first.
        assertEquals("<aqua>Ruby Sword", sword.displayName());
        assertEquals(new ResourceLocation("minecraft", "item/handheld"), ((ModelSource.Generated) sword.model()).parent());

        assertEquals(new ModelSource.Provided(contents().resolve("myitems"), new ResourceLocation("myitems", "item/floating_sword")),
                items.get("myitems:floating_sword").model());

        ItemDefinition redBlock = items.get("myitems:red_block");
        assertEquals(ContentType.CUSTOM_BLOCK, redBlock.type());
        assertEquals(new Placement.Block(false), redBlock.placement());
        ModelSource.Generated cube = (ModelSource.Generated) redBlock.model();
        assertEquals(new ResourceLocation("minecraft", "block/cube"), cube.parent());
        assertEquals(new ResourceLocation("myitems", "block/red_block_up"), cube.textures().get("up"));
        assertEquals(new ResourceLocation("myitems", "block/red_block_west"), cube.textures().get("west"));
        assertEquals(new ResourceLocation("myitems", "block/red_block_north"), cube.textures().get("particle"));

        ModelSource.Generated modern = (ModelSource.Generated) items.get("myitems:modern_block").model();
        assertEquals(ModelSource.CUBE_ALL, modern.parent());
        assertEquals(Map.of("all", new ResourceLocation("myitems", "block/modern_block")), modern.textures());

        Placement.Furniture lamp = (Placement.Furniture) items.get("myitems:lava_lamp").placement();
        assertEquals(Placement.Support.LIGHT, lamp.support());
        assertEquals(7, lamp.light());
        assertTrue(lamp.facePlayer());
        assertEquals("HEAD", lamp.display().transform());
        assertEquals(new Placement.Vec3(0, 0.92f, 0), lamp.display().translation());
        assertEquals(new Placement.Vec3(0.45f, 0.45f, 0.45f), lamp.display().scale());
        assertEquals(new Placement.Vec3(0, 180, 0), lamp.display().rotation());

        Placement.Furniture table = (Placement.Furniture) items.get("myitems:table").placement();
        assertEquals(Placement.Support.BARRIER, table.support());
        assertFalse(table.facePlayer());
        assertEquals("HEAD", table.display().transform());

        // Every model is the same bytes as in import/, texture variables and all; so is every texture.
        for (String model : List.of("item/floating_sword", "item/table", "lava_lamp")) {
            assertArrayEquals(Files.readAllBytes(pack.resolve("models/" + model + ".json")),
                    Files.readAllBytes(contents().resolve("myitems/models/" + model + ".json")), model);
        }
        assertArrayEquals(Files.readAllBytes(pack.resolve("textures/item/ruby.png.mcmeta")),
                Files.readAllBytes(contents().resolve("myitems/textures/item/ruby.png.mcmeta")));
        // The texture it most likely meant is copied too, so adding the namespace is all it takes.
        assertTrue(Files.exists(contents().resolve("myitems/textures/item/floating_sword_blade.png")));

        PackCompiler.Result compiled = new PackCompiler(SETTINGS).compile(temp.resolve("pack"), loaded.items(), Map.of());
        assertEquals(List.of(), compiled.problems());
        assertTrue(Files.exists(temp.resolve("pack/assets/myitems/textures/item/ruby.png.mcmeta")));
        assertArrayEquals(Files.readAllBytes(pack.resolve("models/item/floating_sword.json")),
                Files.readAllBytes(temp.resolve("pack/assets/myitems/models/item/floating_sword.json")));
    }

    @Test
    void assetsAreFoundInEveryLayoutItemsAdderAccepts() throws IOException {
        String config = """
                info:
                  namespace: %s
                items:
                  gem:
                    resource:
                      material: PAPER
                      generate: true
                      textures: [item/gem.png]
                """;
        write(importDir(), "two/configs/a.yml", config.formatted("two"));
        write(importDir(), "two/resourcepack/assets/two/textures/item/gem.png", png("2"));
        write(importDir(), "three/configs/a.yml", config.formatted("three"));
        write(importDir(), "three/resourcepack/three/textures/item/gem.png", png("3"));
        write(importDir(), "four/configs/a.yml", config.formatted("four"));
        write(importDir(), "four/assets/four/textures/item/gem.png", png("4"));
        write(importDir(), "five/configs/a.yml", config.formatted("five"));
        write(importDir(), "five/five/textures/item/gem.png", png("5"));
        // Loose files dropped straight into import/, no configs/ folder at all.
        write(importDir(), "loose/gems.yml", config.formatted("loose"));
        write(importDir(), "loose/textures/item/gem.png", png("loose"));

        ImportReport report = new ItemsAdderImporter().run(importDir(), contents());

        assertEquals(List.of(), report.problems());
        assertEquals(5, report.items());
        Map<String, String> markers = Map.of("two", "2", "three", "3", "four", "4", "five", "5", "loose", "loose");
        for (Map.Entry<String, String> expected : markers.entrySet()) {
            assertArrayEquals(png(expected.getValue()),
                    Files.readAllBytes(contents().resolve(expected.getKey() + "/textures/item/gem.png")), expected.getKey());
        }
        assertEquals(List.of(), new ContentLoader().load(contents()).problems());
    }

    @Test
    void aBrokenFileOrItemIsReportedAndEverythingElseIsStillImported() throws IOException {
        write(importDir(), "pack/configs/broken.yml", """
                info:
                  namespace: pack
                items:
                  oops: [unclosed
                """);
        write(importDir(), "pack/configs/good.yml", """
                info:
                  namespace: pack
                items:
                  fine:
                    resource: {material: PAPER, generate: true, textures: [item/fine.png]}
                  not_a_section: 12
                  sneaky:
                    resource: {material: PAPER, generate: true, textures: [../../../etc/passwd]}
                  missing_texture:
                    resource: {material: PAPER, generate: true, textures: [item/nowhere.png]}
                  headless_block:
                    behaviours: {block: {placed_model: {type: REAL_NOTE}}}
                  retired:
                    enabled: false
                    resource: {material: PAPER}
                  copy:
                    variant_of: fine
                """);
        write(importDir(), "pack/textures/item/fine.png", png("fine"));
        write(importDir(), "ItemsAdder/config.yml", "resource-pack:\n  hosting: self\n");

        ImportReport report = new ItemsAdderImporter().run(importDir(), contents());

        assertEquals(3, report.items(), report.toString());
        assertEquals(4, report.skipped(), report.toString());
        assertProblem(report, "pack/configs/broken.yml line ");
        assertProblem(report, "pack:not_a_section: not a section");
        assertProblem(report, "pack:sneaky: '../../../etc/passwd' - invalid path");
        assertProblem(report, "pack:missing_texture: texture pack:item/nowhere not found under import/");
        assertProblem(report, "pack:headless_block: a block needs a model or textures");
        assertProblem(report, "pack:copy: variant_of is not supported");
        assertNote(report, "pack:retired: disabled in ItemsAdder");
        assertNote(report, "ItemsAdder/config.yml: not an ItemsAdder content file");
        assertTrue(Files.exists(contents().resolve("pack/textures/item/fine.png")));
    }

    @Test
    void importingTwiceChangesNothingAndOtherFilesAreNeverOverwritten() throws IOException {
        write(importDir(), "pack/configs/items.yml", """
                info:
                  namespace: pack
                items:
                  gem:
                    resource: {material: PAPER, generate: true, textures: [item/gem.png]}
                  shard:
                    resource: {material: PAPER, generate: true, textures: [item/shard.png]}
                """);
        write(importDir(), "pack/textures/item/gem.png", png("gem"));
        write(importDir(), "pack/textures/item/shard.png", png("shard"));
        // Already in contents/, from somewhere else: the import must leave it alone.
        write(contents(), "pack/textures/item/shard.png", png("hand-drawn"));

        ImportReport first = new ItemsAdderImporter().run(importDir(), contents());
        Path converted = contents().resolve("pack/imported/pack/configs/items.yml");
        String firstText = Files.readString(converted);
        ImportReport second = new ItemsAdderImporter().run(importDir(), contents());

        assertEquals(1, first.resources());
        assertEquals(0, second.resources());
        assertEquals(firstText, Files.readString(converted));
        assertTrue(firstText.startsWith(ItemsAdderImporter.MARKER + "\n"));
        assertArrayEquals(png("hand-drawn"), Files.readAllBytes(contents().resolve("pack/textures/item/shard.png")));
        assertProblem(first, "pack/textures/item/shard.png already exists with different content - kept it");

        // A content file someone wrote by hand at the same path is not the importer's to replace.
        Files.writeString(converted, "namespace: pack\nitems: {}\n");
        ImportReport third = new ItemsAdderImporter().run(importDir(), contents());
        assertEquals("namespace: pack\nitems: {}\n", Files.readString(converted));
        assertProblem(third, "was not written by the importer - left alone");
    }

    @Test
    void nothingOutsideImportIsEverCopied() throws IOException {
        Path secret = temp.resolve("server-secret.png");
        Files.write(secret, png("secret"));
        write(importDir(), "pack/configs/items.yml", """
                info:
                  namespace: pack
                items:
                  gem:
                    resource: {material: PAPER, generate: true, textures: [item/gem.png]}
                  linked_folder:
                    resource: {material: PAPER, generate: true, textures: [outside/secret.png]}
                """);
        Files.createDirectories(importDir().resolve("pack/textures/item"));
        Files.createSymbolicLink(importDir().resolve("pack/textures/item/gem.png"), secret);
        Files.createSymbolicLink(importDir().resolve("pack/textures/outside"), temp);

        ImportReport report = new ItemsAdderImporter().run(importDir(), contents());

        assertProblem(report, "texture pack:item/gem not found");
        assertProblem(report, "texture pack:outside/secret not found");
        assertFalse(Files.exists(contents().resolve("pack/textures/item/gem.png")));
        assertFalse(Files.exists(contents().resolve("pack/textures/outside/secret.png")));
    }

    @Test
    void anEmptyOrMissingImportFolderIsCreatedAndReportsNothing() throws IOException {
        ImportReport report = new ItemsAdderImporter().run(importDir(), contents());

        assertTrue(report.empty());
        assertTrue(Files.isDirectory(importDir()));
        assertFalse(Files.exists(contents()));
    }

    @Test
    void itemsInTheMinecraftNamespaceAreRefused() throws IOException {
        write(importDir(), "vanilla/configs/items.yml", """
                info:
                  namespace: minecraft
                items:
                  diamond:
                    resource: {material: DIAMOND, generate: true, textures: [item/diamond.png]}
                """);

        ImportReport report = new ItemsAdderImporter().run(importDir(), contents());

        assertEquals(0, report.items());
        assertProblem(report, "would replace vanilla ones");
    }

    // ---------------------------------------------------------------- fixtures

    private static void assertProblem(ImportReport report, String text) {
        assertTrue(report.problems().stream().anyMatch(problem -> problem.contains(text)),
                () -> "no problem containing '" + text + "' in " + report.problems());
    }

    private static void assertNote(ImportReport report, String text) {
        assertTrue(report.notes().stream().anyMatch(note -> note.contains(text)),
                () -> "no note containing '" + text + "' in " + report.notes());
    }

    /** A PNG signature and a marker: enough for the compiler's check, and distinct per texture. */
    private static byte[] png(String marker) {
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        byte[] text = marker.getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[signature.length + text.length];
        System.arraycopy(signature, 0, bytes, 0, signature.length);
        System.arraycopy(text, 0, bytes, signature.length, text.length);
        return bytes;
    }

    private static void write(Path root, String relative, String text) throws IOException {
        write(root, relative, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void write(Path root, String relative, byte[] bytes) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
    }
}
