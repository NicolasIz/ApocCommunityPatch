package com.arkcronist.content.core.animation;

import java.util.ArrayList;
import java.util.List;

/**
 * How far one animation has got, tick by tick: which of its {@link Animator.Frame frames} go out to
 * the displays on each server tick, and when it is over.
 *
 * <p>{@link #step()} is called once per tick, the first time in the tick the animation starts, and
 * hands back the frames due by then - most ticks, none: the clients are gliding towards the last
 * frame sent. A looping animation starts over the tick it reaches its length, so it repeats every
 * {@code length} ticks exactly; one that holds, or is played once, is over once its last frame is
 * out.</p>
 *
 * <p>Not thread-safe; it is only ever stepped from the server thread.</p>
 */
public final class Playback {

    private final List<Animator.Frame> frames;
    private final AnimatedModel.Loop loop;
    private final int length;
    private int tick;
    private int next;

    public Playback(AnimatedModel model, AnimatedModel.Clip clip) {
        this(Animator.frames(model, clip), clip.loop(), clip.length());
    }

    Playback(List<Animator.Frame> frames, AnimatedModel.Loop loop, int length) {
        this.frames = List.copyOf(frames);
        this.loop = loop;
        this.length = length;
    }

    /** The frames due this tick, in order; then on to the next tick. */
    public List<Animator.Frame> step() {
        if (loops() && tick >= length) {
            tick = 0;
            next = 0;
        }
        List<Animator.Frame> due = new ArrayList<>();
        while (next < frames.size() && frames.get(next).tick() <= tick) {
            due.add(frames.get(next++));
        }
        tick++;
        return due;
    }

    /** Whether every frame has gone out, never to go out again. A loop is never over. */
    public boolean finished() {
        return next >= frames.size() && !loops();
    }

    /** Ticks since it started, or since it last started over. */
    public int tick() {
        return tick;
    }

    private boolean loops() {
        return loop == AnimatedModel.Loop.LOOP && length > 0 && !frames.isEmpty();
    }
}
