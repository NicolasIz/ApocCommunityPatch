package com.arkcronist.content.core.emotes;

import com.arkcronist.content.core.animation.AnimatedModel;
import com.arkcronist.content.core.animation.Animator;
import com.arkcronist.content.core.animation.Playback;

import java.util.List;

/**
 * 5.0: how far one player's emote has got. Stepped once a tick by whatever plays it, it hands back
 * the frames due - most ticks none, the clients gliding towards the last one - and says when the
 * emote is over: a played-once animation at its end, a held or looping one after
 * {@link EmoteDefinition#maxTicks()}.
 *
 * <p>Not thread-safe: one thread steps it.</p>
 */
public final class EmoteClock {

    private final EmoteDefinition emote;
    private final Playback playback;
    private int elapsed;

    public EmoteClock(EmoteDefinition emote) {
        this.emote = emote;
        this.playback = new Playback(emote.frames(), emote.loop(), emote.length());
    }

    /** The frames due this tick, the first time in the tick it starts; then on to the next tick. */
    public List<Animator.Frame> step() {
        List<Animator.Frame> due = playback.step();
        elapsed++;
        return due;
    }

    /** Whether it has nothing more to show. */
    public boolean over() {
        return (emote.loop() == AnimatedModel.Loop.ONCE && playback.finished()) || elapsed >= emote.maxTicks();
    }

    /** Ticks since it started. */
    public int elapsed() {
        return elapsed;
    }

    public EmoteDefinition emote() {
        return emote;
    }
}
