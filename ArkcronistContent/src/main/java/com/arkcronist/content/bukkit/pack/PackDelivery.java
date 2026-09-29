package com.arkcronist.content.bukkit.pack;

import com.arkcronist.content.bukkit.EngineSettings;
import com.arkcronist.content.core.pack.PackArtifact;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the pack that is live right now and sends it to players.
 *
 * <p>The web server reads {@link #current()} from its own threads and the main thread replaces it
 * after a rebuild, hence the atomic reference: a request sees one complete build or the next.</p>
 */
public final class PackDelivery {

    /**
     * The id the pack is sent under. Since 1.20.3 a client can hold several server packs at once,
     * keyed by id; keeping this one fixed means a rebuilt pack replaces the one a player already has
     * instead of being stacked on top of it.
     */
    public static final UUID PACK_ID =
            UUID.nameUUIDFromBytes("arkcronist-content:resource-pack".getBytes(StandardCharsets.UTF_8));

    private final EngineSettings settings;
    private final AtomicReference<PackArtifact> current = new AtomicReference<>();

    public PackDelivery(EngineSettings settings) {
        this.settings = settings;
    }

    /** The pack being served, or null before the first build has finished. Any thread. */
    public @Nullable PackArtifact current() {
        return current.get();
    }

    /**
     * Makes {@code artifact} the live pack.
     *
     * @return true when its content differs from the pack it replaces - that is, when players
     *         need to be sent it again
     */
    public boolean publish(PackArtifact artifact) {
        PackArtifact previous = current.getAndSet(artifact);
        return previous == null || !previous.sha1Hex().equals(artifact.sha1Hex());
    }

    /** The URL players are sent for the live pack, or null when there is nothing to send. */
    public @Nullable String currentUrl() {
        PackArtifact pack = current.get();
        return pack != null && settings.http().enabled() ? settings.http().packUrl(pack.sha1Hex()) : null;
    }

    /**
     * Sends the live pack. Main thread.
     *
     * <p>The client compares the hash with the packs it has cached and only downloads on a
     * mismatch, so sending an unchanged pack costs a player nothing but a packet.</p>
     */
    public void send(Player player) {
        PackArtifact pack = current.get();
        if (pack == null || !settings.http().enabled()) {
            return;
        }
        EngineSettings.Delivery delivery = settings.delivery();
        player.setResourcePack(PACK_ID, settings.http().packUrl(pack.sha1Hex()), pack.sha1(),
                delivery.prompt(), delivery.required());
    }

    /** Main thread. */
    public void sendAll(Collection<? extends Player> players) {
        for (Player player : players) {
            send(player);
        }
    }
}
