package com.arkcronist.content.bukkit.bedrock;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 3.3.5: where in a player's connection a furniture carrier is written so that it reaches the
 * client as it was made.
 *
 * <p>CraftEngine shows players every note block of another instrument as a harp one, rewriting the
 * block states of what goes out in its handler {@value #CRAFTENGINE_ENCODER}, between the server's
 * {@code encoder} and the handlers that compress and frame it. A carrier is one of those states, and
 * rewritten it is another piece's: a trough drawn as a forge. Written in the context of a handler,
 * a packet only passes the handlers between it and the head of the pipeline, so it is written where
 * PacketEvents writes its own silent packets - CraftEngine places itself on the tail side of that -
 * or, were CraftEngine on the head side, in CraftEngine's own place, provided nothing else lies
 * between the two.</p>
 */
public final class CarrierRoute {

    /** CraftEngine's handler for packets as bytes, where its block state mappings are applied. */
    public static final String CRAFTENGINE_ENCODER = "craftengine_encoder";

    private CarrierRoute() {
    }

    /**
     * The handler to write carriers in the context of; null when there is none that skips
     * CraftEngine without skipping something the packet needs.
     *
     * @param names              the player's pipeline, head first
     * @param packetEventsEncoder PacketEvents' encoder there
     */
    public static @Nullable String startAt(List<String> names, String packetEventsEncoder) {
        int packetEvents = names.indexOf(packetEventsEncoder);
        if (packetEvents < 0) {
            return null;
        }
        int craftEngine = names.indexOf(CRAFTENGINE_ENCODER);
        if (craftEngine < 0 || craftEngine > packetEvents) {
            return packetEventsEncoder;
        }
        for (int i = craftEngine + 1; i < packetEvents; i++) {
            String between = names.get(i);
            if (!between.startsWith("pe-encoder-") && !between.startsWith("craftengine")) {
                return null;
            }
        }
        return CRAFTENGINE_ENCODER;
    }
}
