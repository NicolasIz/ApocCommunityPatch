package com.arkcronist.content.core.emotes;

import com.arkcronist.content.core.animation.AnimatedModel;
import com.arkcronist.content.core.animation.Animator;

import java.util.List;

/**
 * 5.0: a player emote - an item's {@code emote:} section.
 *
 * <p>While a player plays it, their body is hidden and a rig stands in for it: a Blockbench model
 * whose bones - {@code head}, {@code body}, {@code right_arm}, {@code left_arm}, {@code right_leg},
 * {@code left_leg}, or any others - are drawn by one item display each, and moved by one of its
 * animations. The bone named {@code head} can show the player's own head, skin and all.</p>
 *
 * <p>The animation is turned into display frames once, when the content is built - on the worker
 * thread, never while anyone plays it: playing it is then only sending those frames on time, and
 * the clients glide between them on their own.</p>
 *
 * @param rig           the Blockbench model; its bones' models are in the pack under the item's
 * @param animation     the name of the animation played, one of the rig's
 * @param frames        that animation as display frames, in tick order
 * @param loop          what it does at its end: once ends the emote, hold keeps its last pose, loop
 *                      starts over - the last two until the player moves, or {@code maxTicks}
 * @param length        the animation's length, in ticks
 * @param playerHead    the bone named {@code head} shows the player's own head rather than its model
 * @param scale         the size of the whole rig; {@link #PLAYER_SCALE} is a player's
 * @param stopOnMove    walking, jumping or being pushed ends it
 * @param maxTicks      a held or looping emote ends by itself after this
 * @param cooldownTicks before the same player can play an emote again
 */
public record EmoteDefinition(AnimatedModel rig, String animation, List<Animator.Frame> frames,
                              AnimatedModel.Loop loop, int length, boolean playerHead, float scale,
                              boolean stopOnMove, int maxTicks, int cooldownTicks) {

    /** A player is drawn at 15/16 of the 32 pixels its model is: 1.875 blocks. */
    public static final float PLAYER_SCALE = 0.9375f;
    public static final String HEAD = "head";

    public EmoteDefinition {
        frames = List.copyOf(frames);
        if (!rig.clips().containsKey(animation) || scale <= 0 || maxTicks < 1 || cooldownTicks < 0) {
            throw new IllegalArgumentException(animation + " of " + rig.clips().keySet() + ", scale " + scale
                    + ", " + maxTicks + " ticks at most, cooldown " + cooldownTicks);
        }
    }

    /** {@code animation} of {@code rig}, its frames worked out now. */
    public static EmoteDefinition of(AnimatedModel rig, String animation, boolean playerHead, float scale,
                                     boolean stopOnMove, int maxTicks, int cooldownTicks) {
        AnimatedModel.Clip clip = rig.clips().get(animation);
        if (clip == null) {
            throw new IllegalArgumentException(rig.clips().keySet() + " has no animation " + animation);
        }
        return new EmoteDefinition(rig, animation, Animator.frames(rig, clip), clip.loop(), clip.length(),
                playerHead, scale, stopOnMove, maxTicks, cooldownTicks);
    }

    /** The index of the bone named {@code head}, or -1. */
    public int headBone() {
        for (int i = 0; i < rig.bones().size(); i++) {
            if (rig.bones().get(i).name().equals(HEAD)) {
                return i;
            }
        }
        return -1;
    }

    /** Whether the bone at {@code index} is drawn by a display: it has cubes, or it is the player's head. */
    public boolean drawn(int index) {
        return rig.bones().get(index).model() != null || (playerHead && index == headBone());
    }
}
