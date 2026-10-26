package com.arkcronist.content.core.geyser;

import com.arkcronist.content.core.block.BlockAxis;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 5.5: a log lying on its side shows its ends at the sides it points to on Bedrock. */
class LogsOnTheirSideTest {

    @Test
    void theEndsGoWhereTheLogPoints() {
        BedrockConverter.Material side = new BedrockConverter.Material("side", "opaque");
        BedrockConverter.Material top = new BedrockConverter.Material("top", "opaque");
        BedrockConverter.Material bottom = new BedrockConverter.Material("bottom", "opaque");
        Map<String, BedrockConverter.Material> upright = new LinkedHashMap<>();
        for (String face : new String[]{"north", "south", "east", "west"}) {
            upright.put(face, side);
        }
        upright.put("up", top);
        upright.put("down", bottom);
        upright.put("*", side);

        Map<String, BedrockConverter.Material> east = BedrockConverter.onItsSide(upright, BlockAxis.X);
        assertEquals(top, east.get("east"));
        assertEquals(bottom, east.get("west"));
        assertEquals(side, east.get("up"));
        assertEquals(side, east.get("north"));

        Map<String, BedrockConverter.Material> north = BedrockConverter.onItsSide(upright, BlockAxis.Z);
        assertEquals(top, north.get("north"));
        assertEquals(bottom, north.get("south"));
        assertEquals(side, north.get("down"));
        assertEquals(side, north.get("east"));
    }
}
