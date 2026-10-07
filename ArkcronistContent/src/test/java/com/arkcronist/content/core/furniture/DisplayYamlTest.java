package com.arkcronist.content.core.furniture;

import com.arkcronist.content.core.definition.Placement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Writing the editor's values into a hand-written content file, and nothing else. */
class DisplayYamlTest {

    private static final DisplayTransform EDITED = new DisplayTransform(new Placement.Vec3(0, 0.45f, -0.1f),
            new Placement.Vec3(0.5f, 0.5f, 0.5f), new Placement.Vec3(0, 90, 0));

    @Test
    void theThreeValuesAreReplacedAndEveryCommentAndOtherLineStays() throws Exception {
        String original = """
                # Ruby furniture - hand written.
                namespace: demo
                items:
                  ruby_stool:
                    type: custom_furniture
                    display-name: "<red>Ruby Stool"   # shown in menus
                    resource:
                      model: furniture/stool
                    furniture:
                      support: BARRIER
                      display:
                        transform: HEAD
                        translation: [0, 0.5, 0]   # sits on the floor
                        scale:
                          - 0.45
                          - 0.45
                          - 0.45
                        # no rotation yet
                      interactable: seat
                  ruby_table:
                    type: custom_furniture
                    furniture:
                      display:
                        translation: [0, 0.2, 0]
                """;

        String edited = DisplayYaml.write(original, "ruby_stool", EDITED);

        assertEquals("""
                # Ruby furniture - hand written.
                namespace: demo
                items:
                  ruby_stool:
                    type: custom_furniture
                    display-name: "<red>Ruby Stool"   # shown in menus
                    resource:
                      model: furniture/stool
                    furniture:
                      support: BARRIER
                      display:
                        transform: HEAD
                        translation: [0, 0.45, -0.1]  # sits on the floor
                        scale: [0.5, 0.5, 0.5]
                        rotation: [0, 90, 0]
                        # no rotation yet
                      interactable: seat
                  ruby_table:
                    type: custom_furniture
                    furniture:
                      display:
                        translation: [0, 0.2, 0]
                """, edited);
    }

    /** The demo pedestal as shipped: aligned comments, a blank line and a comment after its display. */
    @Test
    void blankLinesStayAndAlignedCommentsKeepTheirColumn() throws Exception {
        String shipped = """
                items:
                  ruby_pedestal:
                    furniture:
                      support: BARRIER        # solid; LIGHT is walk-through and can glow
                      display:
                        transform: NONE       # draw the model exactly as authored
                        translation: [0, 0, 0]
                        scale: 1.0            # or [x, y, z]
                        rotation: [0, 0, 0]   # degrees around x, y, z

                  # The same model, smaller, on a light block.
                  ruby_lamp:
                    furniture:
                      display:
                        scale:
                          - 0.5

                          - 0.5
                          - 0.5
                        # under the shade
                """;
        DisplayTransform turned = new DisplayTransform(new Placement.Vec3(0, 0.3f, 0), Placement.Vec3.ONE,
                new Placement.Vec3(0, 90, 0));
        assertEquals("""
                items:
                  ruby_pedestal:
                    furniture:
                      support: BARRIER        # solid; LIGHT is walk-through and can glow
                      display:
                        transform: NONE       # draw the model exactly as authored
                        translation: [0, 0.3, 0]
                        scale: [1, 1, 1]      # or [x, y, z]
                        rotation: [0, 90, 0]  # degrees around x, y, z

                  # The same model, smaller, on a light block.
                  ruby_lamp:
                    furniture:
                      display:
                        scale:
                          - 0.5

                          - 0.5
                          - 0.5
                        # under the shade
                """, DisplayYaml.write(shipped, "ruby_pedestal", turned));
        // A list's items go, its blank line stays; the keys added follow the last value, ahead of it.
        assertEquals("""
                  ruby_lamp:
                    furniture:
                      display:
                        scale: [1, 1, 1]
                        translation: [0, 0.3, 0]
                        rotation: [0, 90, 0]

                        # under the shade
                """, DisplayYaml.write(shipped, "ruby_lamp", turned).substring(shipped.indexOf("  ruby_lamp:")));
    }

    @Test
    void missingSectionsAreAddedWithTheFilesOwnIndentation() throws Exception {
        String fourSpaces = """
                items:
                    lamp:
                        type: custom_furniture
                        resource:
                            model: furniture/lamp
                    other:
                        material: PAPER
                """;
        assertEquals("""
                items:
                    lamp:
                        type: custom_furniture
                        resource:
                            model: furniture/lamp
                        furniture:
                            display:
                                translation: [0, 0.45, -0.1]
                                scale: [0.5, 0.5, 0.5]
                                rotation: [0, 90, 0]
                    other:
                        material: PAPER
                """, DisplayYaml.write(fourSpaces, "lamp", EDITED));

        String noDisplay = "items:\n  lamp:\n    furniture:\n      support: LIGHT\n      light: 7\n";
        assertEquals("items:\n  lamp:\n    furniture:\n      support: LIGHT\n      light: 7\n      display:\n"
                + "        translation: [0, 0.45, -0.1]\n        scale: [0.5, 0.5, 0.5]\n        rotation: [0, 90, 0]\n",
                DisplayYaml.write(noDisplay, "lamp", EDITED));
    }

    @Test
    void aSectionOnOneLineStaysOnOneLine() throws Exception {
        String flow = "items:\n  lamp:\n    furniture:\n      display: {transform: HEAD, scale: 0.45}  # tiny\n"
                + "      support: LIGHT\n";
        assertEquals("items:\n  lamp:\n    furniture:\n      display: {transform: HEAD, scale: [0.5, 0.5, 0.5],"
                + " translation: [0, 0.45, -0.1], rotation: [0, 90, 0]}  # tiny\n      support: LIGHT\n",
                DisplayYaml.write(flow, "lamp", EDITED));

        String furnitureFlow = "items:\n  lamp:\n    furniture: {support: LIGHT}\n";
        assertEquals("items:\n  lamp:\n    furniture: {support: LIGHT, display: {translation: [0, 0.45, -0.1],"
                + " scale: [0.5, 0.5, 0.5], rotation: [0, 90, 0]}}\n", DisplayYaml.write(furnitureFlow, "lamp", EDITED));
    }

    @Test
    void quotedIdsWindowsLineEndingsAndNoFinalNewlineAreKept() throws Exception {
        String windows = "items:\r\n  'lamp.big':\r\n    furniture:\r\n      display:\r\n        rotation: [0, 0, 0]";
        String edited = DisplayYaml.write(windows, "lamp.big", EDITED);
        assertTrue(edited.contains("\r\n        rotation: [0, 90, 0]\r\n"), edited);
        assertTrue(!edited.endsWith("\n"), "no final newline, as before");
        assertTrue(!edited.replace("\r\n", "").contains("\n"), "every line ending stays \\r\\n");
    }

    @Test
    void whatTheEditorCannotFollowIsRefusedAndNothingIsWritten() {
        DisplayYaml.EditException missing = assertThrows(DisplayYaml.EditException.class,
                () -> DisplayYaml.write("items:\n  lamp:\n    material: PAPER\n", "chair", EDITED));
        assertEquals("item 'chair' is not under items: in this file", missing.getMessage());

        // Two lamps: the loader takes the second, the text editor would change the first.
        DisplayYaml.EditException twice = assertThrows(DisplayYaml.EditException.class, () -> DisplayYaml.write(
                "items:\n  lamp:\n    furniture: {}\n  lamp:\n    furniture:\n      support: LIGHT\n", "lamp", EDITED));
        assertTrue(twice.getMessage().contains("left as it was"), twice.getMessage());

        DisplayYaml.EditException multiLine = assertThrows(DisplayYaml.EditException.class, () -> DisplayYaml.write(
                "items:\n  lamp:\n    furniture:\n      display: {transform: HEAD,\n        scale: 2}\n", "lamp", EDITED));
        assertTrue(multiLine.getMessage().contains("line 4"), multiLine.getMessage());
    }
}
