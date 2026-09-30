package com.arkcronist.content.core.loader;

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
                new Placement.Vec3(0, 90, 0)), null, null), lamp.placement());

        ItemDefinition chair = report.items().get(1);
        assertEquals("BARRIER", chair.material());
        assertEquals(new Placement.Furniture(Placement.Support.BARRIER, 0, true, Placement.Display.DEFAULT, null, null),
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
