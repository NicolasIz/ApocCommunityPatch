package com.arkcronist.content.bukkit.pack;

import com.arkcronist.content.bukkit.EngineSettings;
import com.arkcronist.content.core.pack.PackArtifact;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the pack that is live right now, and the link players download it from, and sends both to
 * players.
 *
 * <p>The web server reads {@link #current()} from its own threads and the main thread replaces it
 * after a rebuild, hence the atomic reference: a request sees one complete build or the next. The
 * pack and its link are swapped together, so a player is never sent one build's hash with
 * another build's link - which the client would reject.</p>
 */
public final class PackDelivery {

    /**
     * The id the pack is sent under. Since 1.20.3 a client can hold several server packs at once,
     * keyed by id; keeping this one fixed means a rebuilt pack replaces the one a player already has
     * instead of being stacked on top of it.
     */
    public static final UUID PACK_ID =
            UUID.nameUUIDFromBytes("arkcronist-content:resource-pack".getBytes(StandardCharsets.UTF_8));

    /**
     * A pack and where it can be downloaded.
     *
     * @param url null when there is nowhere: hosting is off, or an upload failed with nothing to
     *            fall back on
     */
    public record Live(PackArtifact artifact, @Nullable String url) {
    }

    private final EngineSettings settings;
    private final AtomicReference<Live> current = new AtomicReference<>();

    public PackDelivery(EngineSettings settings) {
        this.settings = settings;
    }

    /** The pack being served, or null before the first build has finished. Any thread. */
    public @Nullable PackArtifact current() {
        Live live = current.get();
        return live == null ? null : live.artifact();
    }

    /** The live pack and its link together, or null before the first build. Any thread. */
    public @Nullable Live live() {
        return current.get();
    }

    /**
     * Makes {@code artifact}, downloadable at {@code url}, the live pack.
     *
     * @return true when its content or its link differs from what it replaces - that is, when
     *         players need to be sent it again
     */
    public boolean publish(PackArtifact artifact, @Nullable String url) {
        Live previous = current.getAndSet(new Live(artifact, url));
        return previous == null || !previous.artifact().sha1Hex().equals(artifact.sha1Hex())
                || !Objects.equals(previous.url(), url);
    }

    /** The URL players are sent for the live pack, or null when there is nothing to send. */
    public @Nullable String currentUrl() {
        Live live = current.get();
        return live == null ? null : live.url();
    }

    /**
     * Sends the live pack. Main thread.
     *
     * <p>The client compares the hash with the packs it has cached and only downloads on a
     * mismatch, so sending an unchanged pack costs a player nothing but a packet.</p>
     */
    public void send(Player player) {
        Live live = current.get();
        if (live == null || live.url() == null) {
            return;
        }
        EngineSettings.Delivery delivery = settings.delivery();
        player.setResourcePack(PACK_ID, live.url(), live.artifact().sha1(), delivery.prompt(), delivery.required());
    }

    /** Main thread. */
    public void sendAll(Collection<? extends Player> players) {
        for (Player player : players) {
            send(player);
        }
    }
}
