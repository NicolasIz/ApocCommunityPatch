package com.arkcronist.content.core.furniture;

import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.definition.ResourceLocation;
import com.arkcronist.content.core.geyser.FurnitureLayout;
import com.arkcronist.content.core.geyser.JavaModel;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** 5.3: the boxes a piece is clicked by, fitted to its model and turned with it. */
class HitboxesTest {

    private static final double EPSILON = 1e-6;

    @Test
    void aBlockOfAModelIsOneBox() {
        List<Placement.Hitbox.Box> boxes = Hitboxes.cover(new double[]{-0.5, 0, -0.5}, new double[]{0.5, 1, 0.5});
        assertEquals(1, boxes.size());
        Placement.Hitbox.Box box = boxes.get(0);
        assertEquals(1, box.width(), EPSILON);
        assertEquals(1, box.height(), EPSILON);
        assertEquals(0, box.offset().x(), EPSILON);
        assertEquals(0, box.offset().y(), EPSILON);
        assertEquals(0, box.offset().z(), EPSILON);
    }

    @Test
    void aLongThinPieceIsARowOfSquares() {
        // A bench three blocks long and half a block deep: six boxes half a block across.
        List<Placement.Hitbox.Box> boxes = Hitboxes.cover(new double[]{-0.5, 0, -0.25}, new double[]{2.5, 0.5, 0.25});
        assertEquals(6, boxes.size());
        for (int i = 0; i < boxes.size(); i++) {
            assertEquals(0.5, boxes.get(i).width(), EPSILON);
            assertEquals(-0.25 + 0.5 * i, boxes.get(i).offset().x(), EPSILON);
            assertEquals(0, boxes.get(i).offset().z(), EPSILON);
        }
        // Along z, a long piece is cut along z; never into more than eight.
        List<Placement.Hitbox.Box> pole = Hitboxes.cover(new double[]{-0.05, 0, -4}, new double[]{0.05, 1, 4});
        assertEquals(8, pole.size());
        assertEquals(1, pole.get(0).width(), EPSILON);
        assertEquals(-3.5, pole.get(0).offset().z(), EPSILON);
    }

    @Test
    void aFlatPieceIsKeptHittable() {
        List<Placement.Hitbox.Box> rug = Hitboxes.cover(new double[]{-0.5, 0, -0.5}, new double[]{0.5, 1 / 16.0, 0.5});
        assertEquals(1, rug.size());
        assertEquals(0.2, rug.get(0).height(), EPSILON);
        assertEquals(1 / 32.0 - 0.1, rug.get(0).offset().y(), EPSILON);
    }

    @Test
    void boxesTurnWithThePieceAboutItsMiddle() {
        Placement.Vec3 east = new Placement.Vec3(0.25f, 0, 0);
        assertArrayEquals(new double[]{0.25, 0, 0}, Hitboxes.turned(east, Footprint.SINGLE, 0), EPSILON);
        // Facing west, what was on its east side is on its south side.
        assertArrayEquals(new double[]{0, 0, 0.25}, Hitboxes.turned(east, Footprint.SINGLE, 90), EPSILON);
        assertArrayEquals(new double[]{-0.25, 0, 0}, Hitboxes.turned(east, Footprint.SINGLE, 180), EPSILON);
        assertArrayEquals(new double[]{0, 0, -0.25}, Hitboxes.turned(east, Footprint.SINGLE, 270), EPSILON);

        // Two blocks wide: its middle is half a block to its right, wherever it faces.
        Footprint wide = new Footprint(2, 1);
        Placement.Vec3 middle = new Placement.Vec3(0.5f, 0, 0);
        assertArrayEquals(new double[]{0.5, 0, 0}, Hitboxes.turned(middle, wide, 0), EPSILON);
        assertArrayEquals(new double[]{-0.5, 0, 0}, Hitboxes.turned(middle, wide, 180), EPSILON);
        assertArrayEquals(new double[]{0, 0, 0.5}, Hitboxes.turned(middle, wide, 90), EPSILON);
    }

    @Test
    void theBoundsAreTheModelAsJavaDrawsIt() {
        // An item display turns its model half a turn: the model's west side is drawn on the east.
        JsonObject json = JsonParser.parseString("""
                {"textures": {"t": "demo:block/t"}, "elements": [
                  {"from": [0, 0, 0], "to": [4, 8, 16], "faces": {"north": {"texture": "#t"}}}]}
                """).getAsJsonObject();
        ResourceLocation location = new ResourceLocation("demo", "furniture/post");
        JavaModel model = JavaModel.resolve(location, Map.of(location, json)::get);
        double[][] bounds = FurnitureLayout.bounds(model, Footprint.SINGLE, Placement.Display.DEFAULT, false);
        assertArrayEquals(new double[]{0.25, 0, -0.5}, bounds[0], EPSILON);
        assertArrayEquals(new double[]{0.5, 0.5, 0.5}, bounds[1], EPSILON);

        // Scaled up, and moved, as the display says.
        Placement.Display bigger = new Placement.Display("NONE", new Placement.Vec3(0, 0.5f, 0),
                new Placement.Vec3(2, 2, 2), Placement.Vec3.ZERO);
        double[][] scaled = FurnitureLayout.bounds(model, Footprint.SINGLE, bigger, false);
        assertArrayEquals(new double[]{0.5, 0, -1}, scaled[0], EPSILON);
        assertArrayEquals(new double[]{1, 1, 1}, scaled[1], EPSILON);
    }
}
