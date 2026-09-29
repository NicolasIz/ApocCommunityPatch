package com.arkcronist.content.core.loader;

import com.arkcronist.content.core.definition.ItemAssets;
import com.arkcronist.content.core.definition.ItemDefinition;
import com.arkcronist.content.core.definition.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
        assertEquals(new ResourceLocation("demo", "item/ruby_sword"), item.assets().texture());
        assertEquals(new ResourceLocation("minecraft", "item/handheld"), item.assets().parent());
        assertNull(item.assets().model());
        assertEquals(contents.resolve("demo"), item.assets().sourceRoot());
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
        assertEquals(contents.resolve("gems"), item.assets().sourceRoot());
        assertEquals(ItemAssets.GENERATED, item.assets().parent());
        assertFalse(item.assets().hasLook());
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
        assertNull(report.items().get(0).assets().texture());
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

        ItemAssets assets = report.items().get(0).assets();
        assertEquals(new ResourceLocation("demo", "item/ruby"), assets.model());
        assertNull(assets.texture());
        assertEquals(1, report.problems().size());
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
