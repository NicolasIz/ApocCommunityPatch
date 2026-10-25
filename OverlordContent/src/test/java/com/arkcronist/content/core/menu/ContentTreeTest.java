package com.arkcronist.content.core.menu;

import com.arkcronist.content.core.hud.Spaces;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The browser's folders, as a server's contents/ is laid out after importing ItemsAdder packs. */
class ContentTreeTest {

    private static ContentTree.Node tree() {
        Map<String, String> folders = new LinkedHashMap<>();
        folders.put("dragones_epicos:escama_umbraxis", "dragones_epicos/imported");
        folders.put("dragones_epicos:hueso_umbraxis", "dragones_epicos/imported");
        folders.put("arkespadas:espada_1", "arkespadas/imported");
        folders.put("ginko_fantasy_shop:btn_1", "ginko_fantasy_shop/imported/ginko_fantasy_shop/configs");
        folders.put("demo:ruby", "demo");
        folders.put("demo:ruby_chest", "demo");
        folders.put("demo:acid", "demo/liquids");
        folders.put("loose:thing", "");
        return ContentTree.build(folders);
    }

    @Test
    void eachContentPackIsAFolderAndChainsOfOneFolderAreMerged() {
        ContentTree.Node root = tree();
        assertEquals(List.of("arkespadas", "demo", "dragones_epicos", "ginko_fantasy_shop"),
                root.folders().stream().map(ContentTree.Node::name).toList());
        assertEquals(List.of("loose:thing"), root.items());
        assertEquals(8, root.total());

        ContentTree.Node dragons = root.folders().get(2);
        assertEquals("dragones_epicos/imported", dragons.path(), "imported/ holds the items: merged into its pack");
        assertEquals(List.of("dragones_epicos:escama_umbraxis", "dragones_epicos:hueso_umbraxis"), dragons.items());
        assertEquals(List.of(), dragons.folders());

        ContentTree.Node ginko = root.folders().get(3);
        assertEquals("ginko_fantasy_shop", ginko.name());
        assertEquals("ginko_fantasy_shop/imported/ginko_fantasy_shop/configs", ginko.path());
        assertEquals(List.of("ginko_fantasy_shop:btn_1"), ginko.items());

        ContentTree.Node demo = root.folders().get(1);
        assertEquals(List.of("demo:ruby", "demo:ruby_chest"), demo.items());
        assertEquals(List.of("liquids"), demo.folders().stream().map(ContentTree.Node::name).toList());
        assertEquals(3, demo.total());
    }

    @Test
    void aFolderIsFoundAgainByItsPathAfterARebuild() {
        ContentTree.Node root = tree();
        assertEquals("liquids", root.find("demo/liquids").name());
        assertEquals(root, root.find(""));
        assertNull(root.find("gone"));
        assertEquals(1, ContentTree.build(Map.of()).total() + 1, "an empty tree is just the root");
    }

    @Test
    void aBackgroundPictureStartsAtTheInventoryEdgeAndTheTitleWhereItWould() {
        String title = MenuTitle.background("", MenuTitle.CHEST_WIDTH);
        int before = title.indexOf('');
        assertEquals(-MenuTitle.TITLE_X, Spaces.width(title.substring(0, before)));
        // The picture moves the pen 177; with the spaces after it the text starts 8 in, as always.
        int after = Spaces.width(title.substring(before + 1));
        int pen = MenuTitle.TITLE_X + Spaces.width(title.substring(0, before)) + (MenuTitle.CHEST_WIDTH + 1) + after;
        assertEquals(MenuTitle.TITLE_X, pen);
    }
}
