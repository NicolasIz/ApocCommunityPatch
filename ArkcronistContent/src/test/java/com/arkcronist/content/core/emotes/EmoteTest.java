package com.arkcronist.content.core.emotes;

import com.arkcronist.content.core.animation.AnimatedModel;
import com.arkcronist.content.core.animation.Animator;
import com.arkcronist.content.core.animation.BbModelReader;
import com.arkcronist.content.core.definition.ResourceLocation;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The demo squire rig: read, turned into frames, and posed as its displays draw it. */
class EmoteTest {

    private static final Path DEMO = Path.of("src/main/resources/contents/demo");

    private static AnimatedModel squire() {
        List<String> problems = new ArrayList<>();
        AnimatedModel rig = BbModelReader.read(DEMO.resolve("models/emotes/squire.bbmodel"), DEMO,
                new ResourceLocation("demo", "wave"), "", problems);
        assertEquals(List.of(), problems);
        assertNotNull(rig);
        return rig;
    }

    private static int bone(AnimatedModel rig, String name) {
        for (int i = 0; i < rig.bones().size(); i++) {
            if (rig.bones().get(i).name().equals(name)) {
                return i;
            }
        }
        throw new AssertionError(name);
    }

    @Test
    void theSquireHasABodyAndFourAnimations() {
        AnimatedModel rig = squire();
        assertEquals(List.of("body", "head", "right_arm", "left_arm", "right_leg", "left_leg"),
                rig.bones().stream().map(AnimatedModel.Bone::name).toList());
        assertEquals(Map.of("wave", AnimatedModel.Loop.ONCE, "bow", AnimatedModel.Loop.ONCE,
                        "cheer", AnimatedModel.Loop.LOOP, "sit", AnimatedModel.Loop.HOLD),
                rig.clips().values().stream().collect(Collectors.toMap(AnimatedModel.Clip::name, AnimatedModel.Clip::loop)));
        // The head, the arms ride on the body: a bow carries them.
        int body = bone(rig, "body");
        assertEquals(body, rig.bones().get(bone(rig, "head")).parent());
        assertEquals(body, rig.bones().get(bone(rig, "right_arm")).parent());
        assertEquals(-1, rig.bones().get(bone(rig, "right_leg")).parent());
    }

    /** The frames are worked out once, when the emote is defined; playing one only steps through them. */
    @Test
    void aPlayedOnceEmoteEndsWithItsAnimation() {
        EmoteDefinition wave = EmoteDefinition.of(squire(), "wave", true, EmoteDefinition.PLAYER_SCALE, true, 600, 20);
        assertFalse(wave.frames().isEmpty());
        assertEquals(48, wave.length(), "2.4 s");
        EmoteClock clock = new EmoteClock(wave);
        List<Animator.Frame> sent = new ArrayList<>();
        int ticks = 0;
        while (!clock.over() && ticks < 1000) {
            sent.addAll(clock.step());
            ticks++;
        }
        assertEquals(49, ticks, "over the tick its last frame goes out");
        assertEquals(wave.frames(), sent, "every frame, once, in order");
    }

    @Test
    void aLoopGoesOnUntilItsLimitAndAHoldKeepsItsPose() {
        AnimatedModel rig = squire();
        EmoteDefinition cheer = EmoteDefinition.of(rig, "cheer", true, 1, true, 100, 0);
        EmoteClock clock = new EmoteClock(cheer);
        int frames = 0;
        for (int tick = 0; tick < 99; tick++) {
            frames += clock.step().size();
            assertFalse(clock.over(), "tick " + tick);
        }
        clock.step();
        assertTrue(clock.over(), "after max-seconds");
        assertTrue(frames > 4 * cheer.frames().size() - 4, frames + " frames: it started over every 24 ticks");

        EmoteDefinition sit = EmoteDefinition.of(rig, "sit", true, 1, true, 200, 0);
        EmoteClock sitting = new EmoteClock(sit);
        int sent = 0;
        for (int tick = 0; tick < 150; tick++) {
            sent += sitting.step().size();
        }
        assertFalse(sitting.over(), "held until the player moves");
        assertEquals(sit.frames().size(), sent, "and nothing more is sent while it holds");
    }

    /**
     * At rest, the squire stands as a player does: feet on the ground, the player's own head on its
     * neck, 1.875 blocks tall. The skull's display is lifted so its base sits on the neck.
     */
    @Test
    void atRestThePlayersHeadSitsOnTheNeck() {
        AnimatedModel rig = squire();
        EmoteDefinition emote = EmoteDefinition.of(rig, "wave", true, EmoteDefinition.PLAYER_SCALE, true, 600, 20);
        List<EmotePose.Part> rest = EmotePose.of(emote, Animator.rest(rig));
        EmotePose.Part head = rest.get(emote.headBone());
        // The neck is 24 pixels up; the skull's display origin half a block above it, both at the emote's size.
        assertEquals((24 / 16f + EmotePose.SKULL_DROP) * EmoteDefinition.PLAYER_SCALE, head.ty(), 1e-4);
        assertEquals(0, head.tx(), 1e-4);
        assertEquals(EmoteDefinition.PLAYER_SCALE, head.sx(), 1e-6, "the skull at the rig's size");
        EmotePose.Part body = rest.get(bone(rig, "body"));
        assertEquals(12 / 16f * EmoteDefinition.PLAYER_SCALE, body.ty(), 1e-4, "the body hangs from the hips");

        EmoteDefinition faceless = EmoteDefinition.of(rig, "wave", false, 1, true, 600, 20);
        assertEquals(24 / 16f, EmotePose.of(faceless, Animator.rest(rig)).get(faceless.headBone()).ty(), 1e-4,
                "without the player's head, the rig's own head model, at its pivot");
    }

    /**
     * Blockbench's north is the front, and the displays' frame mirrors X and Z: an arm raised
     * forward ends up in front - at +Z, the way the player looks - and raised to the side, the right
     * arm goes out to the player's right, -X in that frame.
     */
    @Test
    void wavingRaisesTheRightArmOverTheRightShoulder() {
        AnimatedModel rig = squire();
        EmoteDefinition wave = EmoteDefinition.of(rig, "wave", false, 1, true, 600, 20);
        AnimatedModel.Clip clip = rig.clips().get("wave");
        int arm = bone(rig, "right_arm");
        EmotePose.Part raised = EmotePose.of(wave, Animator.pose(rig, clip, 7)).get(arm);
        Vector3f hand = raised.rotation().transform(new Vector3f(0, -10 / 16f, 0)).add(raised.translation());
        assertTrue(hand.y > 1.9, "the hand is above the head: " + hand);
        assertTrue(hand.x < raised.tx(), "out to the right: " + hand);

        EmoteDefinition bow = EmoteDefinition.of(rig, "bow", false, 1, true, 600, 20);
        EmotePose.Part bent = EmotePose.of(bow, Animator.pose(rig, rig.clips().get("bow"), 16))
                .get(bone(rig, "head"));
        assertTrue(bent.tz() > 0.1, "a bow brings the head forward: " + bent.translation());
        assertTrue(bent.ty() < 1.4, "and down: " + bent.translation());
    }

    @Test
    void aBoneWithoutAModelHasNoDisplayUnlessItIsThePlayersHead() {
        AnimatedModel rig = squire();
        EmoteDefinition emote = EmoteDefinition.of(rig, "sit", true, 1, true, 600, 20);
        for (int i = 0; i < rig.bones().size(); i++) {
            assertTrue(emote.drawn(i), rig.bones().get(i).name());
        }
        assertThrows(IllegalArgumentException.class, () -> EmoteDefinition.of(rig, "moonwalk", true, 1, true, 600, 20));
    }
}
