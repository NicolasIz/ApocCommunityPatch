package com.arkcronist.content.bukkit.emote;

import com.arkcronist.content.core.emotes.EmotePose;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;

/**
 * 5.0: how an emote's rig reaches the clients - as packets for displays only they know about
 * (PacketEvents), sent straight from {@code ArkContent-Emotes}, or as real displays the server
 * moves.
 */
public interface EmoteRenderer {

    /** For the log and /arkcontent info: how emotes are drawn. */
    String describe();

    /** The rig appears, in its latest pose, for {@code viewers} - the player and whoever sees them. Main thread. */
    void start(Rig rig, Collection<? extends Player> viewers);

    /** {@code viewer} has just been sent the player: the rig appears for them too. Main thread. */
    void show(Rig rig, Player viewer);

    /** {@code viewer} no longer sees the player. Main thread. */
    void hide(Rig rig, Player viewer);

    /** Every display glides to its part of {@code pose} over {@code duration} ticks. See {@link #offMainThread()}. */
    void pose(Rig rig, List<EmotePose.Part> pose, int duration);

    /** The rig is gone for everyone. Main thread. */
    void remove(Rig rig);

    /** Whether {@link #pose} may be called from {@code ArkContent-Emotes}; otherwise only from the main thread. */
    boolean offMainThread();

    void close();
}
