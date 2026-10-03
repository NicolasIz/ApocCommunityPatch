package com.arkcronist.content.bukkit.furniture;

import com.arkcronist.content.core.definition.Placement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How a bed's model is drawn over the vanilla bed it stands on. */
class FurnitureServiceTest {

    private static final float MATTRESS = 9 / 16f;

    @Test
    void onlyABedIsDrawnLarger() {
        Placement.Furniture chair = furniture(Placement.Support.BARRIER, Placement.Display.DEFAULT);
        assertSame(chair.display(), FurnitureService.drawn(chair));
    }

    @Test
    void aBedModelCoversTheVanillaBedAndStaysOnTheFloor() {
        Placement.Furniture bed = furniture(Placement.Support.BED, Placement.Display.DEFAULT);

        Placement.Display drawn = FurnitureService.drawn(bed);

        // An item display's model is centred half a block up: the floor is at -0.5, the vanilla
        // mattress top at 9/16 - 0.5.
        assertEquals(-0.5f, -0.5f * drawn.scale().y() + drawn.translation().y(), 1e-6, "on the floor");
        float top = (MATTRESS - 0.5f) * drawn.scale().y() + drawn.translation().y();
        assertTrue(top > MATTRESS - 0.5f + 0.004f, "the model's mattress clears the vanilla one: " + top);
        float side = 0.5f * drawn.scale().x();
        assertTrue(side > 0.5f + 0.004f, "and its sides: " + side);
        float end = 1f * drawn.scale().z();
        assertTrue(end > 1f + 0.009f, "and its ends, a block from the middle: " + end);
    }

    @Test
    void scalingKeepsWhatTheFurnitureSet() {
        Placement.Display set = new Placement.Display("FIXED", new Placement.Vec3(0.25f, 0, 0),
                new Placement.Vec3(2, 2, 2), new Placement.Vec3(0, 45, 0));
        Placement.Display drawn = FurnitureService.drawn(furniture(Placement.Support.BED, set));

        assertEquals("FIXED", drawn.transform());
        assertEquals(set.rotation(), drawn.rotation());
        assertEquals(0.25f, drawn.translation().x());
        assertEquals(2 * FurnitureService.BED_ENCLOSE, drawn.scale().x(), 1e-6);
    }

    private static Placement.Furniture furniture(Placement.Support support, Placement.Display display) {
        return new Placement.Furniture(support, 0, false, display, null, null, null, null,
                support == Placement.Support.BED ? Placement.Bed.DEFAULT : null);
    }
}
