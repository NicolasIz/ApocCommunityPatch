package com.arkcronist.content.bukkit.cosmetic;

import com.arkcronist.content.core.cosmetic.BackPose;
import org.bukkit.entity.Player;

/**
 * How worn cosmetics reach the clients: as packets for a display only they know about, riding the
 * wearer ({@code PacketEvents}), or as a real display entity the server moves.
 */
public interface CosmeticRenderer {

    /** For the log: how cosmetics are being drawn. */
    String describe();

    /** Starts drawing {@code worn} on {@code wearer} - for them and for whoever already sees them. Main thread. */
    void wear(Worn worn, Player wearer);

    /** {@code viewer} has just been sent the wearer: draw the cosmetic for them too. Main thread. */
    void show(Worn worn, Player viewer);

    /** {@code viewer} no longer sees the wearer. Main thread. */
    void hide(Worn worn, Player viewer);

    /** The cosmetic's new pose. Called on {@code ArkContent-Physics}. */
    void update(Worn worn, BackPose.Transform transform);

    /** Once a tick, on the main thread: whatever has to happen there. */
    void tick();

    /** The cosmetic comes off: gone for everyone. Main thread. */
    void takeOff(Worn worn);

    void close();
}
