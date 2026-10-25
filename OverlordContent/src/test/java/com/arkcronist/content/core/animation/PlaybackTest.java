package com.arkcronist.content.core.animation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Playing an animation on the displays, tick by tick: what goes out when, and when it is over. */
class PlaybackTest {

    private final AnimatedModel chest = BbModelReaderTest.read(BbModelReaderTest.chest(), new ArrayList<>());

    @Test
    void theFirstFrameGoesOutInTheTickTheAnimationStarts() {
        Playback open = new Playback(chest, chest.clips().get("open"));

        List<Animator.Frame> first = open.step();

        assertEquals(1, first.size());
        assertEquals(0, first.getFirst().tick());
        assertEquals(10, first.getFirst().duration(), "the lid glides open over half a second, on the client");
    }

    @Test
    void anAnimationThatHoldsIsOverOnceItsLastFrameIsOutAndKeepsItsPose() {
        AnimatedModel.Clip clip = chest.clips().get("open");
        Playback open = new Playback(chest, clip);

        List<Animator.Frame> sent = open.step();

        assertTrue(open.finished(), "one glide, nothing more to send: the server stops ticking it");
        assertEquals(Animator.pose(chest, clip, clip.length()), sent.getLast().pose(), "left open");
        assertNotEquals(Animator.rest(chest), sent.getLast().pose());
    }

    @Test
    void anAnimationPlayedOnceEndsAtRestOnItsLastTick() {
        AnimatedModel.Clip clip = chest.clips().get("close");
        Playback close = new Playback(chest, clip);

        Map<Integer, List<Animator.Frame>> byTick = run(close, clip.length() + 5);

        assertTrue(close.finished());
        List<Animator.Frame> last = byTick.get(clip.length());
        assertEquals(Animator.rest(chest), last.getLast().pose());
        assertEquals(0, last.getLast().duration(), "back to rest at once, as Blockbench does");
        for (int tick = clip.length() + 1; tick < clip.length() + 5; tick++) {
            assertFalse(byTick.containsKey(tick), "nothing after the end, tick " + tick);
        }
    }

    @Test
    void everyFrameGoesOutOnItsOwnTickAndOnlyOnce() {
        AnimatedModel.Clip clip = chest.clips().get("wobble");
        List<Animator.Frame> frames = Animator.frames(chest, clip);
        Playback wobble = new Playback(chest, clip);

        Map<Integer, List<Animator.Frame>> byTick = run(wobble, clip.length());

        int sent = 0;
        for (Map.Entry<Integer, List<Animator.Frame>> entry : byTick.entrySet()) {
            for (Animator.Frame frame : entry.getValue()) {
                assertEquals(frame.tick(), (int) entry.getKey(), frame.toString());
                sent++;
            }
        }
        assertEquals(frames.size(), sent);
    }

    @Test
    void eachGlideEndsWhereTheNextBegins() {
        for (String name : List.of("open", "close", "wobble")) {
            AnimatedModel.Clip clip = chest.clips().get(name);
            List<Animator.Frame> frames = Animator.frames(chest, clip);
            for (int i = 0; i + 1 < frames.size(); i++) {
                assertEquals(frames.get(i + 1).tick(), frames.get(i).tick() + frames.get(i).duration(),
                        name + " frame " + i + ": the clients are never left without a target, nor given two");
            }
        }
    }

    @Test
    void aLoopStartsOverEveryLengthTicksAndNeverEnds() {
        AnimatedModel.Clip clip = chest.clips().get("wobble");
        Playback wobble = new Playback(chest, clip);

        Map<Integer, List<Animator.Frame>> byTick = run(wobble, clip.length() * 3);

        List<Integer> starts = byTick.entrySet().stream()
                .filter(entry -> entry.getValue().stream().anyMatch(frame -> frame.tick() == 0))
                .map(Map.Entry::getKey).sorted().toList();
        assertEquals(List.of(0, clip.length(), clip.length() * 2), starts);
        assertFalse(wobble.finished());
    }

    @Test
    void anAnimationWithNothingToSendIsOverAtOnce() {
        Playback empty = new Playback(List.of(), AnimatedModel.Loop.LOOP, 20);

        assertTrue(empty.step().isEmpty());
        assertTrue(empty.finished());
    }

    /** Steps {@code ticks} times; what went out, by the tick it went out on. */
    private static Map<Integer, List<Animator.Frame>> run(Playback playback, int ticks) {
        Map<Integer, List<Animator.Frame>> byTick = new HashMap<>();
        for (int tick = 0; tick < ticks; tick++) {
            List<Animator.Frame> due = playback.step();
            if (!due.isEmpty()) {
                byTick.put(tick, due);
            }
        }
        return byTick;
    }
}
