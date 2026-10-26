package com.arkcronist.content.core.loader;

import com.arkcronist.content.core.animation.AnimatedModel;
import com.arkcronist.content.core.animation.BbModelReaderTest;
import com.arkcronist.content.core.block.BlockShape;
import com.arkcronist.content.core.definition.AdvancementDefinition;
import com.arkcronist.content.core.definition.ContentType;
import com.arkcronist.content.core.definition.Equipment;
import com.arkcronist.content.core.definition.ItemDefinition;
import com.arkcronist.content.core.definition.JobReward;
import com.arkcronist.content.core.definition.ModelSource;
import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.definition.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentLoaderTest {

    @TempDir
    Path contents;

    /** 1.9: what a double chest, a wide piece and a quest board accept, and what they turn down. */
    @Test
    void doubleChestsFootprintsAndBoardsAreCheckedAsTheyAreRead() throws IOException {
        Files.createDirectories(contents.resolve("pack/models/furniture"));
        Files.copy(Path.of("src/main/resources/contents/demo/models/furniture/ruby_chest_double.bbmodel"),
                contents.resolve("pack/models/furniture/big.bbmodel"));
        write("pack/furniture.yml", """
                items:
                  wide_chest:
                    type: custom_furniture
                    resource: {model: furniture/x}
                    furniture:
                      support: CHEST
                      slots: 54
                      double:
                        animated-model: furniture/big
                  plain_double:
                    type: custom_furniture
                    resource: {model: furniture/x}
                    furniture:
                      support: CHEST
                      double: {model: furniture/x_double}
                  no_look:
                    type: custom_furniture
                    resource: {model: furniture/x}
                    furniture:
                      support: CHEST
                      double: {storage-title: nothing}
                  barrier_double:
                    type: custom_furniture
                    resource: {model: furniture/x}
                    furniture:
                      double: {model: furniture/x_double}
                  board:
                    type: custom_furniture
                    resource: {model: furniture/x}
                    furniture:
                      interactable: quest_board
                      size: 3x2
                      board-title: "<gold>Notices"
                  tall_chest:
                    type: custom_furniture
                    resource: {model: furniture/x}
                    furniture:
                      support: CHEST
                      size: {width: 1, height: 2}
                  huge:
                    type: custom_furniture
                    resource: {model: furniture/x}
                    furniture:
                      size: 9x9
                      board-title: "nope"
                """);
        LoadReport report = new ContentLoader().load(contents);
        Map<String, ItemDefinition> items = new java.util.LinkedHashMap<>();
        report.items().forEach(item -> items.put(item.id(), item));
        Placement.Furniture wide = (Placement.Furniture) items.get("wide_chest").placement();
        assertEquals(27, wide.storage().slots(), "half a double chest at most");
        assertEquals("open_double", wide.doubleChest().animated().open());
        Placement.Furniture plain = (Placement.Furniture) items.get("plain_double").placement();
        assertEquals("pack:furniture/x_double", plain.doubleChest().model().location().toString());
        assertEquals(null, ((Placement.Furniture) items.get("no_look").placement()).doubleChest());
        assertEquals(null, ((Placement.Furniture) items.get("barrier_double").placement()).doubleChest());
        Placement.Furniture board = (Placement.Furniture) items.get("board").placement();
        assertEquals("<gold>Notices", board.board().title());
        assertEquals(new com.arkcronist.content.core.furniture.Footprint(3, 2), board.footprint());
        assertTrue(((Placement.Furniture) items.get("tall_chest").placement()).footprint().single());
        assertTrue(((Placement.Furniture) items.get("huge").placement()).footprint().single());
        List<String> problems = report.problems();
        assertTrue(problems.stream().anyMatch(p -> p.contains("wide_chest") && p.contains("27")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("no_look") && p.contains("chests stay single")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("barrier_double") && p.contains("support: CHEST")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("tall_chest") && p.contains("BARRIER or LIGHT")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("huge") && p.contains("1 to 6")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("huge") && p.contains("board-title")), problems.toString());
    }

    @Test
    void readsEveryAttributeOfAnItem() throws IOException {
        write("demo/items.yml", """
                namespace: demo
                items:
                  ruby_sword:
                    material: DIAMOND_SWORD
                    display-name: "<red>Ruby Sword"
                    lore:
                      - "<gray>Sharp"
                      - "<gray>Red"
                    resource:
                      texture: item/ruby_sword
                      parent: item/handheld
                    behaviour:
                      cancel-vanilla-use: true
                """);

        LoadReport report = new ContentLoader().load(contents);

        assertEquals(List.of(), report.problems());
        assertEquals(1, report.items().size());
        ItemDefinition item = report.items().get(0);
        assertEquals("demo:ruby_sword", item.fullId());
        assertEquals("DIAMOND_SWORD", item.material());
        assertEquals("<red>Ruby Sword", item.displayName());
        assertEquals(List.of("<gray>Sharp", "<gray>Red"), item.lore());
        ModelSource.Generated model = (ModelSource.Generated) item.model();
        assertEquals(new ResourceLocation("demo", "item/ruby_sword"), model.location());
        assertEquals(Map.of("layer0", new ResourceLocation("demo", "item/ruby_sword")), model.textures());
        assertEquals(new ResourceLocation("minecraft", "item/handheld"), model.parent());
        assertEquals(contents.resolve("demo"), model.sourceRoot());
        assertEquals(ContentType.ITEM, item.type());
        assertNull(item.placement());
        assertTrue(item.behaviour().cancelVanillaUse());
        assertFalse(item.behaviour().placeable());
        assertEquals(new ResourceLocation("demo", "ruby_sword"), item.itemModel());
    }

    @Test
    void itemModelNamesAnItemDefinitionFileTheItemIsPointedAtDirectly() throws IOException {
        write("holy_knight/imported/pack.yml", """
                namespace: holy_knight
                items:
                  rapier:
                    material: NETHERITE_SWORD
                    resource:
                      item-model: rapier
                  shield:
                    material: PAPER
                    resource:
                      item-model: weapons/shield.json
                      texture: item/shield
                  wall:
                    type: custom_block
                    resource:
                      item-model: wall
                """);

        LoadReport report = new ContentLoader().load(contents);

        Map<String, ItemDefinition> items = new java.util.HashMap<>();
        report.items().forEach(item -> items.put(item.id(), item));
        assertEquals(new ModelSource.Definition(contents.resolve("holy_knight"), new ResourceLocation("holy_knight", "rapier")),
                items.get("rapier").model());
        assertEquals(new ResourceLocation("holy_knight", "rapier"), items.get("rapier").itemModel());
        assertEquals(new ResourceLocation("holy_knight", "weapons/shield"), items.get("shield").itemModel());
        assertFalse(items.containsKey("wall"));
        assertEquals(List.of(
                "holy_knight/imported/pack.yml > holy_knight:shield: only 'item-model' is used - the item definition"
                        + " file decides everything the item draws",
                "holy_knight/imported/pack.yml > holy_knight:wall: 'item-model' draws items and furniture only; a"
                        + " custom_block needs resource.model, resource.texture or resource.textures",
                "holy_knight/imported/pack.yml > holy_knight:wall: a custom_block needs resource.model,"
                        + " resource.texture or resource.textures - without one it would look like its support block"),
                report.problems());
    }

    @Test
    void namespaceDefaultsToTheContentPackFolderAndFilesMayNest() throws IOException {
        write("gems/configs/weapons/blades.yml", """
                items:
                  opal_dagger:
                    material: IRON_SWORD
                """);

        LoadReport report = new ContentLoader().load(contents);

        assertEquals(List.of(), report.problems());
        ItemDefinition item = report.items().get(0);
        assertEquals("gems:opal_dagger", item.fullId());
        assertNull(item.model());
    }

    @Test
    void aDuplicateIdIsReportedAndTheFirstFileInPathOrderWins() throws IOException {
        write("demo/b.yml", "items: { ruby: { material: EMERALD } }");
        write("demo/a.yml", "items: { ruby: { material: PAPER } }");

        LoadReport report = new ContentLoader().load(contents);

        assertEquals(1, report.items().size());
        assertEquals("PAPER", report.items().get(0).material());
        assertEquals(1, report.problems().size());
        assertTrue(report.problems().get(0).contains("demo/b.yml"), report.problems().toString());
        assertTrue(report.problems().get(0).contains("already defined in demo/a.yml"), report.problems().toString());
    }

    @Test
    void oneBrokenItemDoesNotTakeTheOthersDownWithIt() throws IOException {
        write("demo/items.yml", """
                items:
                  no_material:
                    display-name: Nothing
                  Bad_Id:
                    material: PAPER
                  escape:
                    material: PAPER
                    resource:
                      texture: ../../../server.properties
                  fine:
                    material: PAPER
                """);

        LoadReport report = new ContentLoader().load(contents);

        assertEquals(List.of("demo:escape", "demo:fine"),
                report.items().stream().map(ItemDefinition::fullId).toList());
        // The escaping path is refused outright: the item survives, without the texture.
        assertNull(report.items().get(0).model());
        assertEquals(3, report.problems().size(), report.problems().toString());
        assertTrue(report.problems().stream().anyMatch(p -> p.contains("'material' is required")));
        assertTrue(report.problems().stream().anyMatch(p -> p.contains("Bad_Id") && p.contains("invalid id")));
        assertTrue(report.problems().stream().anyMatch(p -> p.contains("'texture'")));
    }

    @Test
    void invalidYamlIsReportedWithItsLine() throws IOException {
        write("demo/broken.yml", """
                items:
                  ruby:
                    material: PAPER
                    lore: [one, two
                """);
        write("demo/good.yml", "items: { opal: { material: PAPER } }");

        LoadReport report = new ContentLoader().load(contents);

        assertEquals(1, report.items().size());
        assertEquals(1, report.problems().size());
        assertTrue(report.problems().get(0).startsWith("demo/broken.yml line "), report.problems().get(0));
    }

    @Test
    void theMinecraftNamespaceIsRefused() throws IOException {
        write("vanilla/items.yml", """
                namespace: minecraft
                items:
                  diamond:
                    material: PAPER
                """);

        LoadReport report = new ContentLoader().load(contents);

        assertTrue(report.items().isEmpty());
        assertTrue(report.problems().get(0).contains("reserved"), report.problems().toString());
    }

    @Test
    void aModelWinsOverATextureAndSaysSo() throws IOException {
        write("demo/items.yml", """
                items:
                  ruby:
                    material: PAPER
                    resource:
                      model: item/ruby.json
                      texture: item/ruby
                """);

        LoadReport report = new ContentLoader().load(contents);

        assertEquals(new ModelSource.Provided(contents.resolve("demo"), new ResourceLocation("demo", "item/ruby")),
                report.items().get(0).model());
        assertEquals(1, report.problems().size());
    }

    @Test
    void readsACustomBlock() throws IOException {
        write("demo/blocks.yml", """
                items:
                  ruby_block:
                    type: custom_block
                    display-name: "<red>Block of Ruby"
                    resource:
                      texture: block/ruby_block
                  cracked_ruby_block:
                    type: custom_block
                    resource:
                      texture: block/ruby_block
                    block:
                      drop-self: false
                """);

        LoadReport report = new ContentLoader().load(contents);

        assertEquals(List.of(), report.problems());
        ItemDefinition block = report.items().get(0);
        assertEquals(ContentType.CUSTOM_BLOCK, block.type());
        assertEquals("NOTE_BLOCK", block.material());
        assertEquals(new Placement.Block(true), block.placement());
        assertTrue(block.behaviour().placeable());
        // One texture on a block means the same texture on all six faces.
        assertEquals(new ModelSource.Generated(contents.resolve("demo"), new ResourceLocation("demo", "block/ruby_block"),
                ModelSource.CUBE_ALL, Map.of("all", new ResourceLocation("demo", "block/ruby_block"))), block.model());
        assertEquals(new Placement.Block(false), report.items().get(1).placement());
    }

    @Test
    void readsShapedBlocks() throws IOException {
        write("demo/blocks.yml", """
                items:
                  oak_slab:
                    type: custom_block
                    resource:
                      textures:
                        side: block/oak_side
                        top: block/oak_top
                    block:
                      shape: slab
                  lattice:
                    type: custom_block
                    resource:
                      texture: block/lattice
                    block:
                      shape: transparent
                  gate:
                    type: custom_block
                    resource:
                      textures:
                        top: block/gate_top
                        bottom: block/gate_bottom
                        item: item/gate
                    block:
                      shape: door
                  ore_stairs:
                    type: custom_block
                    resource:
                      texture: block/ore
                    block:
                      shape: stairs
                      generation:
                        min-y: 0
                        max-y: 20
                  odd:
                    type: custom_block
                    resource:
                      texture: block/odd
                    block:
                      shape: fence
                  broken_door:
                    type: custom_block
                    resource:
                      model: block/x
                    block:
                      shape: door
                """);

        LoadReport report = new ContentLoader().load(contents);
        Map<String, ItemDefinition> items = new java.util.HashMap<>();
        report.items().forEach(item -> items.put(item.id(), item));

        ItemDefinition slab = items.get("oak_slab");
        assertEquals(BlockShape.SLAB, ((Placement.Block) slab.placement()).shape());
        // Until the build gives it its own, the first copper slab.
        assertEquals("WAXED_EXPOSED_CUT_COPPER_SLAB", slab.material());
        ModelSource.Generated model = (ModelSource.Generated) slab.model();
        assertEquals(new ResourceLocation("minecraft", "block/slab"), model.parent());
        assertEquals(new ResourceLocation("demo", "block/oak_top"), model.textures().get("bottom"));

        assertEquals(BlockShape.TRANSPARENT, ((Placement.Block) items.get("lattice").placement()).shape());
        assertEquals(ModelSource.CUBE_ALL, ((ModelSource.Generated) items.get("lattice").model()).parent());
        assertEquals(new ResourceLocation("demo", "item/gate"), items.get("gate").model().location());
        assertEquals(new ResourceLocation("demo", "item/gate"),
                ((ModelSource.Generated) items.get("gate").model()).textures().get("layer0"));
        assertEquals(List.of(), ((Placement.Block) items.get("ore_stairs").placement()).generation());
        assertEquals(BlockShape.CUBE, ((Placement.Block) items.get("odd").placement()).shape());
        assertFalse(items.containsKey("broken_door"));

        assertEquals(3, report.problems().size(), report.problems().toString());
        assertTrue(report.problems().stream().anyMatch(problem -> problem.contains("only a cube is laid into chunks")));
        assertTrue(report.problems().stream().anyMatch(problem -> problem.contains("unknown block shape 'fence'")));
        assertTrue(report.problems().stream().anyMatch(problem -> problem.contains("drawn from textures")));
    }

    @Test
    void readsCustomFurniture() throws IOException {
        write("demo/furniture.yml", """
                items:
                  lamp:
                    type: custom_furniture
                    resource:
                      model: furniture/lamp
                    furniture:
                      support: light
                      light: 12
                      face-player: false
                      display:
                        transform: fixed
                        translation: [0, 0.25, -0.5]
                        scale: 0.5
                        rotation: [0, 90, 0]
                  chair:
                    type: custom_furniture
                    resource:
                      model: furniture/chair
                """);

        LoadReport report = new ContentLoader().load(contents);

        assertEquals(List.of(), report.problems());
        ItemDefinition lamp = report.items().get(0);
        assertEquals(ContentType.CUSTOM_FURNITURE, lamp.type());
        assertEquals("LIGHT", lamp.material());
        assertEquals(new Placement.Furniture(Placement.Support.LIGHT, 12, false, new Placement.Display("FIXED",
                new Placement.Vec3(0, 0.25f, -0.5f), new Placement.Vec3(0.5f, 0.5f, 0.5f),
                new Placement.Vec3(0, 90, 0)), null, null, null), lamp.placement());

        ItemDefinition chair = report.items().get(1);
        assertEquals("BARRIER", chair.material());
        assertEquals(new Placement.Furniture(Placement.Support.BARRIER, 0, true, Placement.Display.DEFAULT, null, null, null),
                chair.placement());
    }

    @Test
    void furnitureCanNameAModelEngineBlueprintEitherWay() throws IOException {
        write("demo/furniture.yml", """
                items:
                  throne:
                    type: custom_furniture
                    resource:
                      model: furniture/throne
                    furniture:
                      modelengine-id: royal_throne
                  dragon_statue:
                    type: custom_furniture
                    resource:
                      model: furniture/dragon
                    furniture:
                      modelengine_id: "  dragon_idle  "
                """);

        LoadReport report = new ContentLoader().load(contents);

        assertEquals(List.of(), report.problems());
        assertEquals("royal_throne", ((Placement.Furniture) report.items().get(0).placement()).modelEngineId());
        assertEquals("dragon_idle", ((Placement.Furniture) report.items().get(1).placement()).modelEngineId());
    }

    @Test
    void badFurnitureSettingsFallBackAndAreReported() throws IOException {
        write("demo/furniture.yml", """
                items:
                  statue:
                    type: custom_furniture
                    material: STONE
                    resource:
                      model: furniture/statue
                    furniture:
                      support: GLASS
                      light: 20
                      display:
                        transform: sideways
                        scale: [1, 2]
                """);

        LoadReport report = new ContentLoader().load(contents);

        Placement.Furniture furniture = (Placement.Furniture) report.items().get(0).placement();
        assertEquals(Placement.Support.BARRIER, furniture.support());
        // 3.1: a barrier piece gives its light from the block above it, so the level is kept, clamped.
        assertEquals(15, furniture.light());
        assertEquals("NONE", furniture.display().transform());
        assertEquals(Placement.Vec3.ONE, furniture.display().scale());
        assertEquals("BARRIER", report.items().get(0).material());
        // material, support, light out of range, transform, scale
        assertEquals(5, report.problems().size(), report.problems().toString());
    }

    @Test
    void blocksAndFurnitureNeedALookAndTypesMustBeKnown() throws IOException {
        write("demo/items.yml", """
                items:
                  invisible_block:
                    type: custom_block
                  mystery:
                    type: custom_entity
                    material: PAPER
                  stray_section:
                    material: PAPER
                    furniture:
                      support: BARRIER
                """);

        LoadReport report = new ContentLoader().load(contents);

        assertEquals(List.of("demo:stray_section"), report.items().stream().map(ItemDefinition::fullId).toList());
        assertEquals(3, report.problems().size(), report.problems().toString());
        assertTrue(report.problems().stream().anyMatch(p -> p.contains("needs resource.model")));
        assertTrue(report.problems().stream().anyMatch(p -> p.contains("unknown type 'custom_entity'")));
        assertTrue(report.problems().stream().anyMatch(p -> p.contains("'furniture' is only read")));
    }

    @Test
    void texturesMapTakesBlockbenchNumberedVariables() throws IOException {
        write("demo/items.yml", """
                items:
                  log:
                    type: custom_block
                    resource:
                      parent: block/cube_column
                      textures:
                        end: block/log_top
                        side: block/log_side
                  gem:
                    material: PAPER
                    resource:
                      textures:
                        0: item/gem_base
                        1: item/gem_shine
                """);

        LoadReport report = new ContentLoader().load(contents);

        assertEquals(List.of(), report.problems());
        ModelSource.Generated log = (ModelSource.Generated) report.items().get(0).model();
        assertEquals(new ResourceLocation("minecraft", "block/cube_column"), log.parent());
        assertEquals(List.of("end", "side"), List.copyOf(log.textures().keySet()));
        ModelSource.Generated gem = (ModelSource.Generated) report.items().get(1).model();
        assertEquals(ModelSource.ITEM_GENERATED, gem.parent());
        assertEquals(new ResourceLocation("demo", "item/gem_shine"), gem.textures().get("1"));
    }

    @Test
    void aCropReadsItsStagesDropsAndGrowthSettings() throws IOException {
        write("demo/crops.yml", """
                namespace: demo
                items:
                  ruby_seeds:
                    type: custom_crop
                    display-name: "<red>Ruby Seeds"
                    resource:
                      texture: item/ruby_seeds
                    crop:
                      stage-seconds: 30
                      min-light: 0
                      soil: [farmland, soul_sand]
                      bone-meal: false
                      stages:
                        - crop/ruby_0
                        - texture: crop/ruby_1
                          parent: block/crop
                        - model: crop/ruby_grown
                      drops:
                        - item: demo:ruby
                          amount: 1-3
                          chance: 0.5
                        - item: WHEAT
                          amount: 2
                """);

        LoadReport report = new ContentLoader().load(contents);

        assertEquals(List.of(), report.problems());
        ItemDefinition seeds = report.items().get(0);
        assertEquals(ContentType.CUSTOM_CROP, seeds.type());
        assertEquals("PAPER", seeds.material());
        assertTrue(seeds.behaviour().placeable());
        Placement.Crop crop = (Placement.Crop) seeds.placement();
        assertEquals(30, crop.stageSeconds());
        assertEquals(0, crop.minLight());
        assertEquals(List.of("FARMLAND", "SOUL_SAND"), crop.soils());
        assertFalse(crop.boneMeal());
        assertEquals(2, crop.lastStage());
        assertEquals(new ModelSource.Generated(contents.resolve("demo"), new ResourceLocation("demo", "block/ruby_seeds_stage_0"),
                new ResourceLocation("minecraft", "block/cross"), Map.of("cross", new ResourceLocation("demo", "crop/ruby_0"))),
                crop.stages().get(0));
        assertEquals(Map.of("crop", new ResourceLocation("demo", "crop/ruby_1")),
                ((ModelSource.Generated) crop.stages().get(1)).textures());
        assertEquals(new ModelSource.Provided(contents.resolve("demo"), new ResourceLocation("demo", "crop/ruby_grown")),
                crop.stages().get(2));
        assertEquals(List.of(new Placement.Drop("demo:ruby", 1, 3, 0.5), new Placement.Drop("WHEAT", 2, 2, 1)), crop.drops());
        assertEquals(new ResourceLocation("demo", "ruby_seeds/stage_2"),
                Placement.Crop.stageItemModel(seeds.itemModel(), 2));
    }

    @Test
    void aCropWithoutItsOwnLookIsDrawnAsItsGrownStage() throws IOException {
        write("demo/crops.yml", """
                namespace: demo
                items:
                  berry:
                    type: custom_crop
                    material: DIAMOND
                    crop:
                      stages: [crop/berry_0, crop/berry_1]
                      stage-seconds: 0
                      drops: [{item: demo:berry, amount: 0-2, chance: 3}]
                  lonely:
                    type: custom_crop
                    crop:
                      stages: [crop/only_one]
                """);

        LoadReport report = new ContentLoader().load(contents);

        assertEquals(1, report.items().size());
        ItemDefinition berry = report.items().get(0);
        Placement.Crop crop = (Placement.Crop) berry.placement();
        assertEquals(crop.stages().get(1), berry.model());
        assertEquals(List.of("FARMLAND"), crop.soils());
        assertEquals(120, crop.stageSeconds());
        assertEquals(List.of(new Placement.Drop("demo:berry", 1, 1, 1)), crop.drops());
        assertTrue(report.problems().stream().anyMatch(p -> p.contains("'material' is ignored")), report.problems().toString());
        assertTrue(report.problems().stream().anyMatch(p -> p.contains("'stage-seconds' must be at least 1")));
        assertTrue(report.problems().stream().anyMatch(p -> p.contains("amount 0-2 makes no sense")));
        assertTrue(report.problems().stream().anyMatch(p -> p.contains("'chance' must be above 0")));
        assertTrue(report.problems().stream().anyMatch(p -> p.contains("demo:lonely: 'crop.stages' should list at least two")));
    }

    @Test
    void furnitureCanBeASeat() throws IOException {
        write("demo/chairs.yml", """
                namespace: demo
                items:
                  chair:
                    type: custom_furniture
                    resource: {model: furniture/chair}
                    furniture:
                      interactable: seat
                      seat-height: 0.4
                  stool:
                    type: custom_furniture
                    resource: {model: furniture/stool}
                    furniture: {interactable: SEAT}
                  table:
                    type: custom_furniture
                    resource: {model: furniture/table}
                    furniture: {interactable: bed, seat-height: 1}
                  throne:
                    type: custom_furniture
                    resource: {model: furniture/throne}
                    furniture: {size: 3x3x2, interactable: seat, seat-height: 1.0, seat-at: [0, -0.25]}
                  pew:
                    type: custom_furniture
                    resource: {model: furniture/pew}
                    furniture: {size: 3x1, interactable: seat, seat-at: [0, 9]}
                """);

        LoadReport report = new ContentLoader().load(contents);

        assertEquals(new Placement.Seat(0.4f), ((Placement.Furniture) report.items().get(0).placement()).seat());
        assertEquals(Placement.Seat.DEFAULT, ((Placement.Furniture) report.items().get(1).placement()).seat());
        assertNull(((Placement.Furniture) report.items().get(2).placement()).seat());
        // 3.2: one seat in the middle of a big piece, moved a quarter of a block back.
        assertEquals(new Placement.Seat(1.0f, true, 0f, -0.25f), ((Placement.Furniture) report.items().get(3).placement()).seat());
        assertEquals(Placement.Seat.DEFAULT, ((Placement.Furniture) report.items().get(4).placement()).seat(),
                "nine blocks off its middle: a seat on each block");
        assertTrue(report.problems().stream().anyMatch(p -> p.contains("'seat-at' should be [across, forward]")),
                report.problems().toString());
        assertEquals(3, report.problems().size(), report.problems().toString());
    }

    @Test
    void furnitureCanHoldAnInventory() throws IOException {
        write("demo/storage.yml", """
                namespace: demo
                items:
                  cabinet:
                    type: custom_furniture
                    resource: {model: furniture/cabinet}
                    furniture:
                      interactable: storage
                      slots: 54
                      storage-title: "<gold>Cabinet"
                  crate:
                    type: custom_furniture
                    resource: {model: furniture/crate}
                    furniture: {interactable: Storage}
                  drawer:
                    type: custom_furniture
                    resource: {model: furniture/drawer}
                    furniture: {interactable: storage, slots: 20}
                  shelf:
                    type: custom_furniture
                    resource: {model: furniture/shelf}
                    furniture: {interactable: seat, slots: 9, storage-title: Shelf}
                """);

        LoadReport report = new ContentLoader().load(contents);

        Placement.Furniture cabinet = (Placement.Furniture) report.items().get(0).placement();
        assertEquals(new Placement.Storage(54, "<gold>Cabinet"), cabinet.storage());
        assertNull(cabinet.seat(), "a container is not also a seat");
        assertEquals(new Placement.Storage(27, null), ((Placement.Furniture) report.items().get(1).placement()).storage());
        assertEquals(new Placement.Storage(27, null), ((Placement.Furniture) report.items().get(2).placement()).storage());
        Placement.Furniture shelf = (Placement.Furniture) report.items().get(3).placement();
        assertNull(shelf.storage());
        assertEquals(Placement.Seat.DEFAULT, shelf.seat());

        assertEquals(3, report.problems().size(), report.problems().toString());
        assertTrue(report.problems().stream().anyMatch(p -> p.contains("demo:drawer: 'slots' must be whole rows of nine")));
        assertTrue(report.problems().stream().anyMatch(p -> p.contains("demo:shelf: 'slots' is only read with interactable: storage")));
        assertTrue(report.problems().stream().anyMatch(p -> p.contains("demo:shelf: 'storage-title' is only read")));
    }

    /** 3.4: a canteen's sips, what each gives, where it is filled; and a backpack's size. */
    @Test
    void aCanteenAndABackpackAreRead() throws IOException {
        write("demo/gear.yml", """
                namespace: demo
                items:
                  canteen:
                    material: PAPER
                    drink:
                      sips: 6
                      seconds: 1.2
                      hud: {thirst: 5}
                      potions: [{effect: speed, seconds: 3}]
                      refill: [water, Medieval_Props:abrevadero, a fountain]
                      filled: "<aqua>Full"
                  plain_canteen:
                    material: PAPER
                    drink: {}
                  satchel:
                    material: PAPER
                    backpack: {slots: 20, title: "<gold>Satchel"}
                  bag:
                    material: PAPER
                    backpack: {}
                """);

        LoadReport report = new ContentLoader().load(contents);

        ItemDefinition canteen = report.items().get(0);
        assertEquals(6, canteen.drink().sips());
        assertEquals(1.2, canteen.drink().seconds(), 1e-9);
        assertEquals(Map.of("thirst", 5.0), canteen.drink().hud());
        assertEquals(1, canteen.drink().potions().size());
        assertEquals(List.of("water", "medieval_props:abrevadero"), canteen.drink().refill());
        assertTrue(canteen.drink().fillsAtWater());
        assertFalse(canteen.drink().fillsAtCauldron());
        assertTrue(canteen.drink().fillsAt("medieval_props:abrevadero"));
        assertEquals("<aqua>Full", canteen.drink().filled());
        assertNull(canteen.backpack());

        ItemDefinition plain = report.items().get(1);
        assertEquals(5, plain.drink().sips());
        assertTrue(plain.drink().fillsAtWater() && plain.drink().fillsAtCauldron(), "water and cauldrons when not said");

        assertEquals(18, report.items().get(2).backpack().slots(), "whole rows of nine");
        assertEquals("<gold>Satchel", report.items().get(2).backpack().title());
        assertEquals(27, report.items().get(3).backpack().slots());
        assertNull(report.items().get(3).backpack().title());

        assertTrue(report.problems().stream().anyMatch(p -> p.contains("demo:canteen: drink: 'refill' takes water")
                && p.contains("a fountain")), report.problems().toString());
        assertTrue(report.problems().stream().anyMatch(p -> p.contains("demo:plain_canteen: drink: a drink with neither")));
        assertTrue(report.problems().stream().anyMatch(p -> p.contains("demo:satchel: backpack: 'slots' is in whole rows")));
        assertEquals(3, report.problems().size(), report.problems().toString());
    }

    /** 3.4: storage that shows what it holds - a mannequin's armour, a rack's swords. */
    @Test
    void storageCanShowWhatItHolds() throws IOException {
        write("demo/show.yml", """
                namespace: demo
                items:
                  dummy:
                    type: custom_furniture
                    resource: {model: furniture/dummy}
                    furniture:
                      interactable: storage
                      slots: 9
                      show: {armor: true, offset: [0, 0.1, 0], yaw: 180}
                  rack:
                    type: custom_furniture
                    resource: {model: furniture/rack}
                    furniture:
                      interactable: storage
                      slots: 9
                      show:
                        items:
                          - {offset: [-0.3, 0.86, -0.22], rotation: [0, 0, -135], scale: 1.6}
                          - {offset: [0.3, 0.86, -0.22]}
                  empty_show:
                    type: custom_furniture
                    resource: {model: furniture/rack}
                    furniture: {interactable: storage, show: {}}
                  seat_show:
                    type: custom_furniture
                    resource: {model: furniture/rack}
                    furniture: {interactable: seat, show: {armor: true}}
                """);

        LoadReport report = new ContentLoader().load(contents);

        Placement.Show dummy = ((Placement.Furniture) report.items().get(0).placement()).storage().show();
        assertTrue(dummy.armor());
        assertEquals(new Placement.Vec3(0, 0.1f, 0), dummy.offset());
        assertEquals(180f, dummy.yaw());
        assertTrue(dummy.items().isEmpty());

        Placement.Show rack = ((Placement.Furniture) report.items().get(1).placement()).storage().show();
        assertFalse(rack.armor());
        assertEquals(List.of(new Placement.ShownItem(new Placement.Vec3(-0.3f, 0.86f, -0.22f),
                        new Placement.Vec3(0, 0, -135), 1.6f),
                new Placement.ShownItem(new Placement.Vec3(0.3f, 0.86f, -0.22f), Placement.Vec3.ZERO, 1f)), rack.items());

        assertTrue(report.problems().stream().anyMatch(p -> p.contains("demo:empty_show: show: shows nothing")),
                report.problems().toString());
        assertTrue(report.problems().stream().anyMatch(p -> p.contains("demo:seat_show: 'show' is only read with"
                + " interactable: storage")), report.problems().toString());
        assertNull(((Placement.Furniture) report.items().get(3).placement()).storage());
        assertEquals(2, report.problems().size(), report.problems().toString());
    }

    @Test
    void anAnimatedChestIsStorageDrawnByItsBones() throws IOException {
        write("demo/models/furniture/ruby_chest.bbmodel", BbModelReaderTest.chest());
        write("demo/chests.yml", """
                namespace: demo
                items:
                  ruby_chest:
                    type: custom_furniture
                    furniture:
                      support: CHEST
                      slots: 54
                      animated-model: furniture/ruby_chest
                """);

        LoadReport report = new ContentLoader().load(contents);

        ItemDefinition chest = report.items().getFirst();
        assertEquals("CHEST", chest.material());
        Placement.Furniture furniture = (Placement.Furniture) chest.placement();
        assertEquals(Placement.Support.CHEST, furniture.support());
        assertEquals(new Placement.Storage(54, null), furniture.storage(), "storage is implied, its settings read");
        assertEquals(List.of("base", "lid", "latch"), furniture.animated().model().bones().stream()
                .map(AnimatedModel.Bone::name).toList());
        assertEquals("open", furniture.animated().open());
        assertEquals("close", furniture.animated().close());
        // No resource: the item's icon is the whole Blockbench model at rest.
        ModelSource.Inline icon = assertInstanceOf(ModelSource.Inline.class, chest.model());
        assertEquals("demo:ruby_chest/icon", icon.location().toString());
        assertTrue(report.problems().stream().noneMatch(p -> !p.contains("not supported") && !p.contains("bezier")
                && !p.contains("Molang")), report.problems().toString());
    }

    @Test
    void animationNamesAreChecked() throws IOException {
        write("demo/models/furniture/ruby_chest.bbmodel", BbModelReaderTest.chest());
        write("demo/chests.yml", """
                namespace: demo
                items:
                  lidded:
                    type: custom_furniture
                    furniture:
                      support: CHEST
                      interactable: seat
                      animated-model: furniture/ruby_chest.bbmodel
                      animations: {open: swing, close: close}
                  missing:
                    type: custom_furniture
                    furniture: {animated-model: furniture/nowhere}
                  escaping:
                    type: custom_furniture
                    furniture: {animated-model: ../../etc/passwd}
                """);

        LoadReport report = new ContentLoader().load(contents);

        assertEquals(1, report.items().size(), "without a model or a resource, furniture is skipped");
        assertEquals("swing", ((Placement.Furniture) report.items().getFirst().placement()).animated().open());
        assertTrue(report.problems().stream().anyMatch(p -> p.contains("no animation 'swing'")), report.problems().toString());
        assertTrue(report.problems().stream().anyMatch(p -> p.contains("always storage, not a seat")), report.problems().toString());
        assertTrue(report.problems().stream().anyMatch(p -> p.contains("nowhere.bbmodel is not in")), report.problems().toString());
        assertTrue(report.problems().stream().anyMatch(p -> p.contains("demo:escaping") && p.contains("not a valid path")),
                report.problems().toString());
    }

    @Test
    void aBedIsAVanillaBedOfItsColour() throws IOException {
        write("demo/beds.yml", """
                namespace: demo
                items:
                  ruby_bed:
                    type: custom_furniture
                    resource: {model: furniture/ruby_bed}
                    furniture:
                      support: BED
                      bed-color: Red
                      interactable: seat
                      face-player: false
                  plain_bed:
                    type: custom_furniture
                    resource: {model: furniture/ruby_bed}
                    furniture: {support: bed, bed-color: chartreuse}
                """);

        LoadReport report = new ContentLoader().load(contents);

        ItemDefinition red = report.items().get(0);
        assertEquals("RED_BED", red.material());
        Placement.Furniture bed = (Placement.Furniture) red.placement();
        assertEquals(Placement.Support.BED, bed.support());
        assertNull(bed.seat(), "sleeping is the interaction");
        assertNull(bed.storage());
        assertEquals("WHITE_BED", report.items().get(1).material());
        assertTrue(report.problems().stream().anyMatch(p -> p.contains("BED support sleeps")), report.problems().toString());
        assertTrue(report.problems().stream().anyMatch(p -> p.contains("'face-player' is ignored")), report.problems().toString());
        assertTrue(report.problems().stream().anyMatch(p -> p.contains("'bed-color' must be a dye colour")), report.problems().toString());
    }

    @Test
    void skillExperienceAndPricesAreRead() throws IOException {
        write("demo/extras.yml", """
                namespace: demo
                items:
                  gem:
                    material: EMERALD
                    resource: {texture: item/gem}
                    price: 250.5
                  ore:
                    type: custom_block
                    resource: {texture: block/ore}
                    block: {skill-xp: 7}
                  seeds:
                    type: custom_crop
                    crop: {stages: [crop/a, crop/b], skill-xp: 3.5}
                  freebie:
                    material: STICK
                    price: -4
                """);

        LoadReport report = new ContentLoader().load(contents);

        assertEquals(250.5, report.items().get(0).price());
        assertEquals(7.0, ((Placement.Block) report.items().get(1).placement()).skillXp());
        assertEquals(3.5, ((Placement.Crop) report.items().get(2).placement()).skillXp());
        assertEquals(0.0, report.items().get(3).price(), "a bad price means not for sale");
        assertTrue(report.problems().stream().anyMatch(p -> p.contains("'price' should be a positive number")),
                report.problems().toString());
    }

    /** 5.0: block: generation: - one section or a list; what cannot be read is said, and given a default. */
    @Test
    void oreGenerationIsReadFromTheBlockSection() throws IOException {
        write("demo/ores.yml", """
                namespace: demo
                items:
                  ruby_ore:
                    type: custom_block
                    resource: {texture: block/ruby_ore}
                    block:
                      generation:
                        min-y: -48
                        max-y: 24
                        veins-per-chunk: 3
                        vein-size: 5
                        chance: 0.8
                        replace: [STONE, minecraft:deepslate]
                  star_ore:
                    type: custom_block
                    resource: {texture: block/star_ore}
                    block:
                      generation:
                        - {worlds: [Mining], environments: [nether, moon], biomes: [crimson_forest], min-y: 40,
                           max-y: 10, chance: 3}
                        - "not a section"
                  plain_block:
                    type: custom_block
                    resource: {texture: block/plain}
                """);

        LoadReport report = new ContentLoader().load(contents);
        Map<String, Placement.Block> blocks = report.items().stream().collect(java.util.stream.Collectors.toMap(
                ItemDefinition::id, item -> (Placement.Block) item.placement()));

        com.arkcronist.content.core.generation.OreGeneration ruby = blocks.get("ruby_ore").generation().get(0);
        assertEquals(-48, ruby.minY());
        assertEquals(24, ruby.maxY());
        assertEquals(3, ruby.veinsPerChunk());
        assertEquals(5, ruby.veinSize());
        assertEquals(0.8, ruby.chance());
        assertEquals(Set.of("minecraft:stone", "minecraft:deepslate"), ruby.replace());
        assertTrue(ruby.appliesTo("world", "NORMAL"));

        assertEquals(1, blocks.get("star_ore").generation().size(), "the entry that is not a section is left out");
        com.arkcronist.content.core.generation.OreGeneration star = blocks.get("star_ore").generation().get(0);
        assertEquals(Set.of("mining"), star.worlds());
        assertEquals(Set.of("nether"), star.environments());
        assertEquals(Set.of("minecraft:crimson_forest"), star.biomes());
        assertEquals(10, star.minY(), "swapped");
        assertEquals(40, star.maxY());
        assertEquals(1.0, star.chance(), "out of range: the default");
        assertEquals(com.arkcronist.content.core.generation.OreGeneration.STONES, star.replace());
        assertTrue(blocks.get("plain_block").generation().isEmpty());

        List<String> problems = report.problems();
        assertTrue(problems.stream().anyMatch(p -> p.contains("unknown environment 'moon'")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("'min-y' 40 is above 'max-y' 10")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("'chance' should be a number from 0 to 1")),
                problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("#2: should be a section")), problems.toString());
    }

    @Test
    void storageSizesAreWholeRows() {
        for (int slots : new int[]{9, 18, 27, 36, 45, 54}) {
            assertTrue(Placement.Storage.validSlots(slots), String.valueOf(slots));
        }
        for (int slots : new int[]{0, 8, 10, 26, 63, -9}) {
            assertFalse(Placement.Storage.validSlots(slots), String.valueOf(slots));
        }
    }

    @Test
    void shrinkingAStorageNeverHidesWhatItHolds() {
        Placement.Storage crate = new Placement.Storage(27, null);
        assertEquals(27, crate.sizeFor(0));
        assertEquals(27, crate.sizeFor(27));
        assertEquals(36, crate.sizeFor(28), "an item saved in slot 28, from when it had more rows");
        assertEquals(54, crate.sizeFor(54));
        assertEquals(9, new Placement.Storage(9, null).sizeFor(1));
        assertEquals(18, new Placement.Storage(9, null).sizeFor(10));
    }

    @Test
    void emojisAreReadFromAnyContentFileAndNamesAreShared() throws IOException {
        write("demo/emojis.yml", """
                namespace: demo
                emojis:
                  ruby: emoji/ruby
                  heart:
                    texture: emoji/heart
                    height: 10
                    ascent: 8
                    permission: vip.emoji
                  "Bad Name": emoji/x
                """);
        write("other/emojis.yml", """
                namespace: other
                emojis:
                  ruby: emoji/other_ruby
                  tall: {texture: emoji/tall, height: 4, ascent: 9}
                """);

        LoadReport report = new ContentLoader().load(contents);

        assertEquals(List.of("ruby", "heart", "tall"), report.emojis().stream().map(e -> e.name()).toList());
        assertEquals("demo", report.emojis().get(0).namespace());
        assertEquals(new ResourceLocation("demo", "emoji/heart"), report.emojis().get(1).texture());
        assertEquals(10, report.emojis().get(1).height());
        assertEquals("vip.emoji", report.emojis().get(1).permission());
        assertEquals(8, report.emojis().get(2).height());
        assertEquals(3, report.problems().size(), report.problems().toString());
        assertTrue(report.problems().stream().anyMatch(p -> p.contains("emoji ruby: already defined in demo/emojis.yml")));
    }

    @Test
    void aMissingFolderIsEmptyNotAnError() throws IOException {
        LoadReport report = new ContentLoader().load(contents.resolve("absent"));

        assertTrue(report.items().isEmpty());
        assertTrue(report.problems().isEmpty());
    }

    @Test
    void readsArmourWornThroughAnEquipmentAsset() throws IOException {
        write("demo/textures/entity/equipment/humanoid/ruby_armor.png", "png");
        write("demo/textures/entity/equipment/humanoid_leggings/ruby_armor.png", "png");
        write("demo/armor.yml", """
                items:
                  ruby_chestplate:
                    material: NETHERITE_CHESTPLATE
                    resource: {texture: item/ruby_chestplate}
                    equipment: {slot: chest, asset: ruby_armor}
                  ruby_leggings:
                    material: NETHERITE_LEGGINGS
                    resource: {texture: item/ruby_leggings}
                    equipment: {slot: LEGS, asset: demo:ruby_armor}
                  plain:
                    material: PAPER
                """);

        LoadReport report = new ContentLoader().load(contents);

        assertEquals(List.of(), report.problems());
        Equipment chest = report.items().get(0).equipment();
        assertEquals(Equipment.Slot.CHEST, chest.slot());
        assertEquals(new ResourceLocation("demo", "ruby_armor"), chest.asset());
        assertEquals(List.of("humanoid", "humanoid_leggings"), chest.layers(), "every layer found, in order");
        assertNull(chest.worn());
        assertEquals(contents.resolve("demo"), chest.sourceRoot());
        assertEquals(Equipment.Slot.LEGS, report.items().get(1).equipment().slot());
        assertNull(report.items().get(2).equipment(), "not worn unless it says so");
    }

    @Test
    void aHelmetCanBeWornAsItsOwnModel() throws IOException {
        write("demo/models/item/horned_helm.json", "{}");
        write("demo/armor.yml", """
                items:
                  helm:
                    material: NETHERITE_HELMET
                    resource: {texture: item/helm}
                    equipment: {slot: head, model: item/horned_helm}
                """);

        LoadReport report = new ContentLoader().load(contents);

        assertEquals(List.of(), report.problems());
        Equipment helm = report.items().get(0).equipment();
        assertEquals(Equipment.Slot.HEAD, helm.slot());
        assertNull(helm.asset(), "an asset would hide the model");
        ModelSource.Provided worn = (ModelSource.Provided) helm.worn();
        assertEquals(new ResourceLocation("demo", "item/horned_helm"), worn.location());
        assertEquals(contents.resolve("demo"), worn.sourceRoot());
    }

    @Test
    void equipmentMistakesAreReportedAgainstWhatThePlayerWouldSee() throws IOException {
        write("demo/textures/entity/equipment/humanoid/ruby_armor.png", "png");
        write("demo/models/item/horns.json", "{}");
        write("demo/armor.yml", """
                items:
                  tail:
                    material: PAPER
                    equipment: {slot: TAIL}
                  noslot:
                    material: PAPER
                    equipment: {asset: ruby_armor}
                  greaves:
                    material: NETHERITE_LEGGINGS
                    equipment: {slot: LEGS, asset: ruby_armor}
                  cuirass:
                    material: NETHERITE_CHESTPLATE
                    equipment: {slot: CHEST, model: item/horns}
                  both:
                    material: NETHERITE_HELMET
                    resource: {texture: item/both}
                    equipment: {slot: HEAD, asset: ruby_armor, model: item/horns}
                  bare:
                    material: NETHERITE_HELMET
                    equipment: {slot: HEAD, model: item/horns}
                  missing:
                    material: NETHERITE_HELMET
                    resource: {texture: item/missing}
                    equipment: {slot: HEAD, model: item/nowhere}
                  rock:
                    type: custom_block
                    resource: {texture: block/rock}
                    equipment: {slot: HEAD}
                """);

        LoadReport report = new ContentLoader().load(contents);
        List<String> problems = report.problems();
        Map<String, ItemDefinition> items = new java.util.HashMap<>();
        report.items().forEach(item -> items.put(item.id(), item));

        assertProblem(problems, "tail", "'equipment.slot' must be HEAD, CHEST, LEGS or FEET, not 'TAIL'");
        assertNull(items.get("tail").equipment(), "an item still loads, just not worn");
        assertProblem(problems, "noslot", "'equipment.slot' must be HEAD, CHEST, LEGS or FEET; the item is not worn");
        assertProblem(problems, "greaves",
                "textures/entity/equipment/humanoid_leggings/ruby_armor.png, which is not in demo - the piece would be invisible");
        assertEquals(List.of("humanoid"), items.get("greaves").equipment().layers());
        assertProblem(problems, "cuirass", "'equipment.model' is drawn on the head only; ignored on CHEST");
        assertNull(items.get("cuirass").equipment().worn());
        assertProblem(problems, "both", "do not go together on the head");
        assertEquals(new ResourceLocation("demo", "ruby_armor"), items.get("both").equipment().asset());
        assertNull(items.get("both").equipment().worn(), "the asset wins: it is what the client draws");
        assertProblem(problems, "bare", "'equipment.model' needs a 'resource' too");
        assertProblem(problems, "missing", "'equipment.model' item/nowhere.json is not in demo/models/");
        assertNull(items.get("missing").equipment().worn());
        assertProblem(problems, "rock", "'equipment' is only read on items worn as armour (type: item)");
        assertNull(items.get("rock").equipment());
    }

    @Test
    void readsAdvancements() throws IOException {
        write("demo/advancements.yml", """
                advancements:
                  arkcronist:
                    title: "<red>Arkcronist"
                    icon: ruby
                    background: gui/advancements/ruby
                  ruby_knight:
                    parent: arkcronist
                    title: "Cover yourself in ruby"
                    description: "<gray>All four pieces"
                    icon: ruby_chestplate
                    frame: Challenge
                    hidden: true
                    trigger:
                      wear: [ruby_helmet, demo:ruby_chestplate]
                    announce: "<player> did it"
                    reward: {experience: 100}
                  diamonds:
                    parent: minecraft:story/root
                    title: Shiny
                    icon: minecraft:diamond
                    trigger: {obtain: minecraft:diamond}
                  secret:
                    parent: arkcronist
                    title: Secret
                    icon: ruby
                """);

        LoadReport report = new ContentLoader().load(contents);

        assertEquals(List.of(), report.problems());
        Map<String, AdvancementDefinition> byId = new java.util.HashMap<>();
        report.advancements().forEach(advancement -> byId.put(advancement.fullId(), advancement));

        AdvancementDefinition root = byId.get("demo:arkcronist");
        assertNull(root.parent());
        assertEquals(new ResourceLocation("demo", "gui/advancements/ruby"), root.background());
        assertInstanceOf(AdvancementDefinition.Trigger.Join.class, root.trigger(), "a tab's root: granted on joining");
        assertFalse(root.toast(), "nobody wants a toast for joining");
        assertFalse(root.celebrate());
        assertEquals(contents.resolve("demo"), root.sourceRoot());

        AdvancementDefinition knight = byId.get("demo:ruby_knight");
        assertEquals(new ResourceLocation("demo", "arkcronist"), knight.parent());
        assertEquals(AdvancementDefinition.Frame.CHALLENGE, knight.frame());
        assertEquals(List.of(new ResourceLocation("demo", "ruby_helmet"), new ResourceLocation("demo", "ruby_chestplate")),
                ((AdvancementDefinition.Trigger.Wear) knight.trigger()).items());
        assertTrue(knight.hidden());
        assertTrue(knight.toast());
        assertTrue(knight.celebrate());
        assertEquals("<player> did it", knight.announce());
        assertEquals(100, knight.experience());
        assertEquals("<gray>All four pieces", knight.description());

        AdvancementDefinition diamonds = byId.get("demo:diamonds");
        assertEquals(new ResourceLocation("minecraft", "story/root"), diamonds.parent(), "hung under a vanilla tab");
        assertEquals(new ResourceLocation("minecraft", "diamond"),
                ((AdvancementDefinition.Trigger.Obtain) diamonds.trigger()).item());

        assertInstanceOf(AdvancementDefinition.Trigger.Manual.class, byId.get("demo:secret").trigger(),
                "a child with no trigger: /advancement grant only");
    }

    @Test
    void advancementMistakesAreReported() throws IOException {
        write("demo/advancements.yml", """
                advancements:
                  untitled:
                    icon: ruby
                  framed:
                    title: Framed
                    icon: ruby
                    frame: golden
                  backdrop:
                    parent: framed
                    title: Backdrop
                    icon: ruby
                    background: minecraft:block/stone
                  bad_trigger:
                    title: Bad
                    icon: ruby
                    trigger: {jump: 3}
                  twice:
                    title: Twice
                    icon: ruby
                    trigger: {wear: [ruby_helmet, ruby_helmet]}
                  stingy:
                    title: Stingy
                    icon: ruby
                    reward: {experience: -5}
                  "Bad Id":
                    title: Bad id
                    icon: ruby
                """);
        write("demo/more.yml", """
                advancements:
                  framed: {title: Again, icon: ruby}
                """);

        LoadReport report = new ContentLoader().load(contents);
        String problems = String.join("\n", report.problems());
        List<String> ids = report.advancements().stream().map(AdvancementDefinition::id).toList();

        assertTrue(problems.contains("advancement untitled: needs 'title' and 'icon'"), problems);
        assertTrue(problems.contains("advancement framed: 'frame' must be task, goal or challenge; using task"), problems);
        assertTrue(problems.contains("advancement backdrop: 'background' is drawn for the root of a tab only"), problems);
        assertTrue(problems.contains("advancement bad_trigger: 'trigger' must be join, manual"), problems);
        assertTrue(problems.contains("advancement twice: 'trigger.wear' names demo:ruby_helmet twice"), problems);
        assertTrue(problems.contains("advancement stingy: 'reward.experience' should be 0 or more"), problems);
        assertTrue(problems.contains("advancement Bad Id: invalid id"), problems);
        assertTrue(problems.contains("more.yml > advancement framed: already defined in demo/advancements.yml"), problems);
        assertEquals(List.of("framed", "backdrop", "stingy"), ids);
        assertNull(report.advancements().get(1).background());
    }

    @Test
    void readsJobRewardsOnBlocksAndCrops() throws IOException {
        write("demo/items.yml", """
                items:
                  ore:
                    type: custom_block
                    resource: {texture: block/ore}
                    block:
                      jobs:
                        Miner: {money: 2.5, xp: 4}
                        Builder: {xp: 1}
                        Greedy: {money: -3}
                        Idle: {}
                        Broken: 7
                  seeds:
                    type: custom_crop
                    crop:
                      stages: [crop/a, crop/b]
                      jobs:
                        Farmer: {money: 1.5, xp: 3}
                """);

        LoadReport report = new ContentLoader().load(contents);

        assertEquals(Map.of("Miner", new JobReward(2.5, 4), "Builder", new JobReward(0, 1)),
                ((Placement.Block) report.items().get(0).placement()).jobs());
        assertEquals(Map.of("Farmer", new JobReward(1.5, 3)), ((Placement.Crop) report.items().get(1).placement()).jobs());
        String problems = String.join("\n", report.problems());
        assertTrue(problems.contains("'jobs.Greedy': 'money' and 'xp' should be numbers, 0 or more - not paid"), problems);
        assertTrue(problems.contains("'jobs.Idle' pays neither 'money' nor 'xp'"), problems);
        assertTrue(problems.contains("'jobs.Broken' should be a section like {money: 2.5, xp: 4}"), problems);
    }

    /** 5.3: a run's three looks and a piece's hitboxes, and what they turn down. */
    @Test
    void runsAndHitboxesAreReadAndChecked() throws IOException {
        write("demo/runs.yml", """
                items:
                  counter:
                    type: custom_furniture
                    resource: {model: furniture/counter}
                    furniture:
                      support: barrier
                      connect: {left: furniture/counter_l, middle: furniture/counter_m, right: furniture/counter_r}
                      hitbox: model
                  bench:
                    type: custom_furniture
                    resource: {model: furniture/bench}
                    furniture:
                      support: light
                      connect: {group: Seats, left: furniture/b_l, middle: furniture/b_m, right: furniture/b_r}
                      hitbox:
                        - {width: 1.5, height: 0.5, offset: [0, 0, 0.25]}
                        - {width: 9, height: 1}
                  rug:
                    type: custom_furniture
                    resource: {model: furniture/rug}
                    furniture:
                      support: light
                      hitbox: false
                  half_counter:
                    type: custom_furniture
                    resource: {model: furniture/half}
                    furniture:
                      support: barrier
                      connect: {left: furniture/h_l, right: furniture/h_r}
                  chest_run:
                    type: custom_furniture
                    resource: {model: furniture/c}
                    furniture:
                      support: chest
                      connect: {left: furniture/c_l, middle: furniture/c_m, right: furniture/c_r}
                      hitbox: model
                """);

        LoadReport report = new ContentLoader().load(contents);
        Map<String, Placement.Furniture> pieces = new java.util.HashMap<>();
        report.items().forEach(item -> pieces.put(item.id(), (Placement.Furniture) item.placement()));

        Placement.Connect counter = pieces.get("counter").connect();
        assertEquals("demo:counter", counter.group(), "its own kind, without a group");
        assertEquals(new ResourceLocation("demo", "furniture/counter_l"), counter.left().location());
        assertEquals(new ResourceLocation("demo", "furniture/counter_r"), counter.model(Placement.Connect.Variant.RIGHT)
                .location());
        assertNull(counter.model(Placement.Connect.Variant.SINGLE), "alone, its own model");
        assertTrue(pieces.get("counter").hitbox().model());
        assertEquals("demo:seats", pieces.get("bench").connect().group());
        assertEquals(1, pieces.get("bench").hitbox().boxes().size(), "the box 9 wide is left out");
        Placement.Hitbox.Box box = pieces.get("bench").hitbox().boxes().get(0);
        assertEquals(1.5, box.width());
        assertEquals(0.25f, box.offset().z());
        assertFalse(pieces.get("rug").hitbox().model());
        assertTrue(pieces.get("rug").hitbox().boxes().isEmpty());
        assertNull(pieces.get("half_counter").connect());
        assertNull(pieces.get("chest_run").connect());
        assertNull(pieces.get("chest_run").hitbox());

        assertProblem(report.problems(), "bench", "at most 8 blocks");
        assertProblem(report.problems(), "half_counter", "needs 'middle'");
        assertProblem(report.problems(), "chest_run", "joins its neighbours");
        assertProblem(report.problems(), "chest_run", "clicked as the vanilla block it is");

        assertEquals(Placement.Connect.Variant.SINGLE, Placement.Connect.Variant.of(false, false));
        assertEquals(Placement.Connect.Variant.LEFT, Placement.Connect.Variant.of(false, true));
        assertEquals(Placement.Connect.Variant.MIDDLE, Placement.Connect.Variant.of(true, true));
        assertEquals(Placement.Connect.Variant.RIGHT, Placement.Connect.Variant.of(true, false));
        assertEquals(new ResourceLocation("demo", "counter/connect_middle"),
                Placement.Connect.itemModel(new ResourceLocation("demo", "counter"), Placement.Connect.Variant.MIDDLE));
    }

    /** 5.4: a block's tool, tier and hardness; what is left out, and what is turned down. */
    @Test
    void blocksReadTheirToolAndHardness() throws IOException {
        write("demo/mined.yml", """
                items:
                  plain:
                    type: custom_block
                    resource: {texture: block/a}
                  stone_like:
                    type: custom_block
                    resource: {texture: block/a}
                    block: {tool: pickaxe}
                  iron_like:
                    type: custom_block
                    resource: {texture: block/a}
                    block: {tool: pickaxe, tier: diamond, hardness: 5, requires-tool: true}
                  planks:
                    type: custom_block
                    resource: {texture: block/a}
                    block: {tool: axe, hardness: 2}
                  hard_glass:
                    type: custom_block
                    resource: {texture: block/a}
                    block: {hardness: unbreakable}
                  odd:
                    type: custom_block
                    resource: {texture: block/a}
                    block: {tool: sword, tier: shiny, requires-tool: true}
                """);

        LoadReport report = new ContentLoader().load(contents);
        Map<String, Placement.Block> blocks = new java.util.HashMap<>();
        report.items().forEach(item -> blocks.put(item.id(), (Placement.Block) item.placement()));

        assertNull(blocks.get("plain").mining(), "mined as the note block it is");
        com.arkcronist.content.core.block.Mining stone = blocks.get("stone_like").mining();
        assertEquals(com.arkcronist.content.core.block.Mining.Tool.PICKAXE, stone.tool());
        assertEquals(1.5, stone.hardness());
        assertTrue(stone.requiresTool(), "a pickaxe's block needs it, as stone");
        com.arkcronist.content.core.block.Mining iron = blocks.get("iron_like").mining();
        assertEquals(com.arkcronist.content.core.block.Mining.Tier.DIAMOND, iron.tier());
        assertEquals(5, iron.hardness());
        com.arkcronist.content.core.block.Mining planks = blocks.get("planks").mining();
        assertFalse(planks.requiresTool(), "wood drops by hand too");
        assertEquals(2, planks.hardness());
        assertTrue(blocks.get("hard_glass").mining().unbreakable());
        com.arkcronist.content.core.block.Mining odd = blocks.get("odd").mining();
        assertEquals(com.arkcronist.content.core.block.Mining.Tool.NONE, odd.tool());
        assertFalse(odd.requiresTool());

        assertProblem(report.problems(), "odd", "unknown tool 'sword'");
        assertProblem(report.problems(), "odd", "unknown tier 'shiny'");
        assertProblem(report.problems(), "odd", "requires-tool: true needs a tool");
    }

    @Test
    void saplingsReadTheTreeTheyGrowInto() throws IOException {
        write("demo/trees.yml", """
                items:
                  sapling:
                    type: custom_crop
                    resource: {texture: crop/s}
                    crop:
                      stages: [crop/s]
                      drops: [{item: demo:x}]
                      tree:
                        log: ruby_log
                        leaves: minecraft:oak_leaves
                        shape: spruce
                        height: [9, 6]
                        generation:
                          biomes: [forest]
                          per-chunk: 3
                          chance: 0.5
                  bush:
                    type: custom_crop
                    resource: {texture: crop/s}
                    crop:
                      stages: [crop/s]
                      tree: {log: oak_log, leaves: oak_leaves, shape: weeping, height: 40, radius: 9}
                  seeds:
                    type: custom_crop
                    resource: {texture: crop/s}
                    crop:
                      stages: [crop/s]
                  broken:
                    type: custom_crop
                    resource: {texture: crop/s}
                    crop:
                      stages: [crop/s]
                      tree: {leaves: oak_leaves}
                """);

        LoadReport report = new ContentLoader().load(contents);
        Map<String, Placement.Crop> crops = new java.util.HashMap<>();
        report.items().forEach(item -> crops.put(item.id(), (Placement.Crop) item.placement()));

        Placement.Crop sapling = crops.get("sapling");
        assertTrue(sapling.sapling());
        assertEquals(1, sapling.lastStage(), "after its one stage comes the tree");
        assertEquals(List.of(), sapling.drops(), "a sapling is never harvested");
        com.arkcronist.content.core.tree.TreeSpec tree = sapling.tree();
        assertEquals("demo:ruby_log", tree.log(), "a bare name is this pack's block, or the vanilla one");
        assertEquals("minecraft:oak_leaves", tree.leaves());
        assertEquals(com.arkcronist.content.core.tree.TreeSpec.Shape.CONE, tree.shape());
        assertEquals(6, tree.minHeight());
        assertEquals(9, tree.maxHeight());
        assertEquals(1, tree.generation().size());
        assertEquals(java.util.Set.of("minecraft:forest"), tree.generation().get(0).biomes());
        assertEquals(3, tree.generation().get(0).perChunk());
        assertTrue(sapling.soils().contains("GRASS_BLOCK"), "a sapling grows on grass, not farmland");

        com.arkcronist.content.core.tree.TreeSpec bush = crops.get("bush").tree();
        assertEquals(com.arkcronist.content.core.tree.TreeSpec.Shape.ROUND, bush.shape());
        assertEquals(com.arkcronist.content.core.tree.TreeSpec.MAX_HEIGHT, bush.maxHeight());
        assertTrue(bush.generation().isEmpty());

        assertFalse(crops.containsKey("seeds"), "a crop still needs two stages");
        assertProblem(report.problems(), "sapling", "its drops are ignored");
        assertProblem(report.problems(), "bush", "unknown shape 'weeping'");
        assertProblem(report.problems(), "bush", "from 1 to 32 blocks");
        assertProblem(report.problems(), "bush", "radius");
        assertProblem(report.problems(), "broken", "needs 'log' and 'leaves'");
    }

    private static void assertProblem(List<String> problems, String id, String text) {
        assertTrue(problems.stream().anyMatch(p -> p.contains(id) && p.contains(text)),
                id + ": expected '" + text + "' in " + problems);
    }

    private void write(String relative, String text) throws IOException {
        Path file = contents.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }
}
