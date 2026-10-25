package com.arkcronist.content.core.minigame;

import com.arkcronist.content.core.hud.Spaces;
import com.arkcronist.content.core.minigame.PrecisionGame.Outcome;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 3.0: the HUD precision minigame - the needle, the verdict, and the frame drawn from them. */
class PrecisionGameTest {

    private static final long MS = 1_000_000L;
    /** One sweep a second, a 20-pixel zone with a 4-pixel middle, three sweeps. */
    private static final MinigameDefinition GAME = new MinigameDefinition(1.0, 20, 4, 3, false, List.of(), List.of(),
            MinigameDefinition.OnMiss.KEEP, 20);

    @Test
    void theNeedleCrossesTheBarAndComesBackAtAnEvenSpeed() {
        PrecisionGame game = new PrecisionGame(GAME, 0, 50);
        assertEquals(0, game.needleAt(0), 1e-9);
        assertEquals(25, game.needleAt(250 * MS), 1e-9);
        assertEquals(100, game.needleAt(1000 * MS), 1e-9);
        assertEquals(50, game.needleAt(1500 * MS), 1e-9, "on its way back");
        assertEquals(0, game.needleAt(2000 * MS), 1e-9);
        assertTrue(game.over(3000 * MS));
    }

    @Test
    void thePressIsJudgedByHowFarFromTheMiddleTheNeedleStopped() {
        PrecisionGame game = new PrecisionGame(GAME, 0, 50);
        assertEquals(new PrecisionGame.Result(Outcome.CRITICAL, 0.0), game.judge(500 * MS, 0, 0));
        assertEquals(Outcome.CRITICAL, game.judge(1480 * MS, 0, 0).outcome(), "2 px off on the way back");
        assertEquals(Outcome.HIT, game.judge(560 * MS, 0, 0).outcome(), "6 px off");
        PrecisionGame.Result miss = game.judge(700 * MS, 0, 0);
        assertEquals(Outcome.MISS, miss.outcome());
        assertEquals(20.0, miss.error(), 1e-9);
        PrecisionGame.Result late = game.judge(3100 * MS, 0, 0);
        assertEquals(Outcome.TIMEOUT, late.outcome());
        assertNull(late.error());
    }

    /**
     * A press that arrives 120 ms after the needle passed the middle, from a player 120 ms away, is
     * judged where they saw it - but only up to the cap, so a slow connection cannot look ahead.
     */
    @Test
    void aPressIsJudgedWhereThePlayerSawTheNeedleUpToTheCap() {
        PrecisionGame game = new PrecisionGame(GAME, 0, 50);
        assertEquals(Outcome.MISS, game.judge(620 * MS, 0, 300 * MS).outcome());
        assertEquals(Outcome.CRITICAL, game.judge(620 * MS, 120 * MS, 300 * MS).outcome());
        assertEquals(Outcome.MISS, game.judge(1000 * MS, 500 * MS, 300 * MS).outcome(),
                "500 ms claimed, 300 ms allowed: judged at 700 ms, 20 px off");
        assertEquals(0, game.needleAt(-5), 1e-9);
        assertEquals(Outcome.MISS, game.judge(10 * MS, 400 * MS, 400 * MS).outcome(), "never before the start");
    }

    @Test
    void aRandomZoneAlwaysFitsInTheBar() {
        MinigameDefinition random = new MinigameDefinition(1.5, 30, 6, 4, true, List.of(), List.of(),
                MinigameDefinition.OnMiss.KEEP, 0);
        SplittableRandom seeds = new SplittableRandom(7);
        for (int i = 0; i < 2000; i++) {
            PrecisionGame game = new PrecisionGame(random, 0, seeds.split());
            assertTrue(game.zoneCentre() >= 15 && game.zoneCentre() <= 85, String.valueOf(game.zoneCentre()));
        }
    }

    @Test
    void definitionsOutsideTheBarAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new MinigameDefinition(1, 120, 4, 3, false, List.of(),
                List.of(), MinigameDefinition.OnMiss.KEEP, 0));
        assertThrows(IllegalArgumentException.class, () -> new MinigameDefinition(1, 10, 12, 3, false, List.of(),
                List.of(), MinigameDefinition.OnMiss.KEEP, 0));
        assertThrows(IllegalArgumentException.class, () -> new MinigameDefinition(0, 10, 2, 3, false, List.of(),
                List.of(), MinigameDefinition.OnMiss.KEEP, 0));
    }

    /**
     * The frame comes back to where it started - the HUD bars on the same line stay put - and each
     * glyph lands on its pixel: the bar centred, the zone at its place, the needle on its pixel.
     */
    @Test
    void aFrameIsWidthNeutralAndPutsEachGlyphOnItsPixel() {
        Map<Integer, MinigameRenderer.Piece> hit = new HashMap<>();
        Map<Integer, MinigameRenderer.Piece> critical = new HashMap<>();
        char next = '';
        for (int width = 1; width <= 64; width *= 2) {
            hit.put(width, new MinigameRenderer.Piece(next++, width + 1));
            critical.put(width, new MinigameRenderer.Piece(next++, width + 1));
        }
        MinigameRenderer.Glyphs glyphs = new MinigameRenderer.Glyphs('', 105, 104, 2, '', 4, 3, hit, critical);
        PrecisionGame game = new PrecisionGame(GAME, 0, 37);
        String frame = MinigameRenderer.frame(glyphs, game, 71.4);

        Map<Character, Integer> advances = new HashMap<>();
        advances.put('', 105);
        advances.put('', 4);
        hit.values().forEach(piece -> advances.put(piece.glyph(), piece.advance()));
        critical.values().forEach(piece -> advances.put(piece.glyph(), piece.advance()));
        Map<Character, Integer> drawnAt = new HashMap<>();
        int pen = 0;
        int zonePixels = 0;
        for (char c : frame.toCharArray()) {
            Integer advance = advances.get(c);
            if (advance == null) {
                pen += Spaces.width(String.valueOf(c));
                continue;
            }
            drawnAt.putIfAbsent(c, pen);
            for (Map.Entry<Integer, MinigameRenderer.Piece> piece : hit.entrySet()) {
                if (piece.getValue().glyph() == c) {
                    zonePixels += piece.getKey();
                }
            }
            pen += advance;
        }
        assertEquals(0, pen, "the line ends where it began");
        assertEquals(-52, drawnAt.get(''), "a 104-pixel bar centred on the middle");
        assertEquals(-50 + 71 - 1, drawnAt.get(''), "the 3-pixel needle centred on pixel 71");
        assertEquals(20, zonePixels, "the zone is exactly its width");
        assertEquals(-50 + 27, drawnAt.get(hit.get(16).glyph()), "the zone starts 10 px before its middle at 37");
    }

    @Test
    void thePlainFrameShowsTheNeedleAndTheZoneInCharacters() {
        PrecisionGame game = new PrecisionGame(GAME, 0, 50);
        String plain = MinigameRenderer.plain(game, 0);
        assertTrue(plain.startsWith("§8[§f§l|"), plain);
        assertTrue(plain.contains("§a-") && plain.contains("§6-") && plain.contains("§7-"), plain);
        assertEquals(25, plain.chars().filter(c -> c == '-' || c == '|').count());
    }
}
