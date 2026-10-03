package com.arkcronist.content.core.loader;

import com.arkcronist.content.core.animation.AnimatedModel;
import com.arkcronist.content.core.animation.BbModelReaderTest;
import com.arkcronist.content.core.definition.ContentType;
import com.arkcronist.content.core.definition.ItemDefinition;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentLoaderTest {

    @TempDir
    Path contents;

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
        assertEquals(0, furniture.light());
        assertEquals("NONE", furniture.display().transform());
        assertEquals(Placement.Vec3.ONE, furniture.display().scale());
        assertEquals("BARRIER", report.items().get(0).material());
        // material, support, light out of range, light on a barrier, transform, scale
        assertEquals(6, report.problems().size(), report.problems().toString());
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
                """);

        LoadReport report = new ContentLoader().load(contents);

        assertEquals(new Placement.Seat(0.4f), ((Placement.Furniture) report.items().get(0).placement()).seat());
        assertEquals(Placement.Seat.DEFAULT, ((Placement.Furniture) report.items().get(1).placement()).seat());
        assertNull(((Placement.Furniture) report.items().get(2).placement()).seat());
        assertEquals(2, report.problems().size(), report.problems().toString());
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

    private void write(String relative, String text) throws IOException {
        Path file = contents.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }
}
