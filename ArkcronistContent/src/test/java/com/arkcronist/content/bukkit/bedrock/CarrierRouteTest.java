package com.arkcronist.content.bukkit.bedrock;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 3.3.5: where a Bedrock player's furniture carriers are written, so that CraftEngine does not rewrite them. */
class CarrierRouteTest {

    private static final String PE = "pe-encoder-packetevents";

    @Test
    void withCraftEngineOnTheTailSideTheyGoWherePacketEventsWritesItsOwn() {
        // As CraftEngine places itself: right after PacketEvents' encoder, toward the server's.
        List<String> pipeline = List.of("timeout", "splitter", "decompress", "craftengine_decoder",
                "pe-decoder-packetevents", "decoder", "prepender", "compress", PE, "craftengine_encoder", "encoder",
                "unbundler", "craftengine_player_channel_handler", "packet_handler");
        assertEquals(PE, CarrierRoute.startAt(pipeline, PE));
    }

    @Test
    void withoutCraftEngineTheyGoWherePacketEventsWritesItsOwn() {
        assertEquals(PE, CarrierRoute.startAt(List.of("splitter", "decoder", "prepender", "compress", PE,
                "encoder", "packet_handler"), PE));
    }

    @Test
    void withCraftEngineOnTheHeadSideTheyGoInItsPlaceWhenNothingElseIsBetween() {
        List<String> pipeline = List.of("prepender", "compress", "craftengine_encoder", "pe-encoder-other", PE,
                "encoder", "packet_handler");
        assertEquals(CarrierRoute.CRAFTENGINE_ENCODER, CarrierRoute.startAt(pipeline, PE));
    }

    @Test
    void neverPastSomethingThePacketNeeds() {
        // Writing in CraftEngine's place here would skip the compressor and ViaVersion.
        assertNull(CarrierRoute.startAt(List.of("prepender", "craftengine_encoder", "compress", PE, "encoder"), PE));
        assertNull(CarrierRoute.startAt(List.of("compress", "craftengine_encoder", "via-encoder", PE, "encoder"), PE));
        // And nowhere when PacketEvents is not in this player's connection.
        assertNull(CarrierRoute.startAt(List.of("prepender", "compress", "craftengine_encoder", "encoder"), PE));
    }
}
