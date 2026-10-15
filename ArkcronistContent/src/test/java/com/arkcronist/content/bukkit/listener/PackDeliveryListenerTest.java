package com.arkcronist.content.bukkit.listener;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 3.0.8: what the console says when a client downloads the pack and cannot apply it. */
class PackDeliveryListenerTest {

    @Test
    void theBrokenShadersOfTheBuildAreNamedFirst() {
        String cause = PackDeliveryListener.reloadCause(List.of(
                "betterhud_1_21_6/assets/minecraft/shaders/core/rendertype_text.fsh (BetterHud pack)"));

        assertTrue(cause.startsWith("This build keeps 1 shader(s) a 1.21.8 client cannot use - the likely cause:"
                + " betterhud_1_21_6/assets/minecraft/shaders/core/rendertype_text.fsh (BetterHud pack)."), cause);
        assertTrue(cause.contains("pack.shaders: drop-broken"), cause);
        assertTrue(cause.endsWith("names the file it rejected."), cause);
    }

    @Test
    void withNoneTheClientLogIsWhereToLook() {
        assertEquals("The client's own log (logs/latest.log, after 'Reloading ResourceManager') names the file it"
                + " rejected.", PackDeliveryListener.reloadCause(List.of()));
    }
}
