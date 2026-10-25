package com.arkcronist.content.bukkit.bedrock;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * 3.3: who plays on Bedrock, asked of Floodgate - on a server behind a proxy with Geyser, where
 * Geyser's own API is not here to ask. By reflection: Floodgate is optional, and nothing of it is
 * compiled against. Its API is looked up the first time it is needed, not as this plugin enables:
 * the two load in no fixed order, and Floodgate's API only exists once Floodgate has enabled.
 */
public final class FloodgateBridge {

    private final ClassLoader loader;
    private volatile Object api;
    private volatile Method isFloodgatePlayer;

    public FloodgateBridge(ClassLoader loader) {
        this.loader = loader;
    }

    /** Whether the player came in through Geyser; false while Floodgate's API cannot be reached. Any thread. */
    public boolean isBedrockPlayer(UUID player) {
        if (isFloodgatePlayer == null && !resolve()) {
            return false;
        }
        try {
            return (boolean) isFloodgatePlayer.invoke(api, player);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return false;
        }
    }

    private synchronized boolean resolve() {
        if (isFloodgatePlayer != null) {
            return true;
        }
        try {
            Class<?> type = Class.forName("org.geysermc.floodgate.api.FloodgateApi", true, loader);
            Object found = type.getMethod("getInstance").invoke(null);
            if (found == null) {
                return false;
            }
            api = found;
            isFloodgatePlayer = type.getMethod("isFloodgatePlayer", UUID.class);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException absent) {
            return false;
        }
    }
}
