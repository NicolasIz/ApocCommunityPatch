package com.arkcronist.content.core.minigame;

import com.arkcronist.content.core.hud.Spaces;

import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * A frame of the minigame as action bar text: the bar, the zone and its critical middle laid over
 * it, and the needle - each a glyph of the pack's font, placed to the pixel with space characters,
 * the way HUD bars are. The line comes back to where it started, so it does not move the HUD bars
 * sharing the action bar, and the bar is centred on the middle of the screen.
 *
 * <p>The zone is drawn from pieces 1, 2, 4 ... 64 pixels wide, so any width and place takes a
 * handful of glyphs. For a player without the pack - on Bedrock - {@link #plain} draws the same
 * frame in plain characters.</p>
 */
public final class MinigameRenderer {

    /**
     * The glyphs, each with its advance - how far the client moves on after it, which is its
     * width plus one.
     *
     * @param border      pixels of frame before the inside of the bar begins
     * @param needleWidth the needle's own width, centred on its place
     * @param hit         a piece of zone of each width, by width
     * @param critical    the same for the critical middle
     */
    public record Glyphs(char frame, int frameAdvance, int frameWidth, int border, char needle, int needleAdvance,
                         int needleWidth, Map<Integer, Piece> hit, Map<Integer, Piece> critical) {

        public Glyphs {
            hit = Map.copyOf(hit);
            critical = Map.copyOf(critical);
        }
    }

    public record Piece(char glyph, int advance) {
    }

    private MinigameRenderer() {
    }

    /** One frame with the needle at {@code needle} pixels into the bar. */
    public static String frame(Glyphs glyphs, PrecisionGame game, double needle) {
        MinigameDefinition definition = game.definition();
        StringBuilder out = new StringBuilder(64);
        int left = -glyphs.frameWidth() / 2;
        int inside = left + glyphs.border();
        int pen = moveTo(out, 0, left);
        out.append(glyphs.frame());
        pen += glyphs.frameAdvance();
        pen = pieces(out, pen, inside + clamp(game.zoneLeft(), definition.zone()), definition.zone(), glyphs.hit());
        if (definition.critical() > 0) {
            pen = pieces(out, pen, inside + clamp(game.criticalLeft(), definition.critical()), definition.critical(),
                    glyphs.critical());
        }
        int needleAt = (int) Math.round(Math.max(0, Math.min(MinigameDefinition.BAR - 1, needle)));
        pen = moveTo(out, pen, inside + needleAt - glyphs.needleWidth() / 2);
        out.append(glyphs.needle());
        pen += glyphs.needleAdvance();
        moveTo(out, pen, 0);
        return out.toString();
    }

    /**
     * The same frame in plain characters, 25 cells across, with legacy colour codes: grey bar,
     * gold zone, green middle, white needle. For clients that cannot draw the glyphs.
     */
    public static String plain(PrecisionGame game, double needle) {
        int cells = 25;
        double cell = (double) MinigameDefinition.BAR / cells;
        int needleCell = (int) Math.min(cells - 1, Math.max(0, Math.floor(needle / cell)));
        StringBuilder out = new StringBuilder("§8[");
        for (int i = 0; i < cells; i++) {
            if (i == needleCell) {
                out.append("§f§l|§r");
                continue;
            }
            double middle = (i + 0.5) * cell;
            double off = Math.abs(middle - game.zoneCentre());
            MinigameDefinition definition = game.definition();
            out.append(off <= definition.critical() / 2.0 + cell / 2 && definition.critical() > 0 ? "§a"
                    : off <= definition.zone() / 2.0 ? "§6" : "§7").append('-');
        }
        return out.append("§8]").toString();
    }

    /** Pieces covering {@code width} pixels from {@code start}, widest first. */
    private static int pieces(StringBuilder out, int pen, int start, int width, Map<Integer, Piece> pieces) {
        NavigableMap<Integer, Piece> sizes = new TreeMap<>(pieces);
        int at = start;
        int left = width;
        while (left > 0) {
            Map.Entry<Integer, Piece> piece = sizes.floorEntry(left);
            if (piece == null) {
                break;
            }
            pen = moveTo(out, pen, at);
            out.append(piece.getValue().glyph());
            pen += piece.getValue().advance();
            at += piece.getKey();
            left -= piece.getKey();
        }
        return pen;
    }

    private static int clamp(int left, int width) {
        return Math.max(0, Math.min(MinigameDefinition.BAR - width, left));
    }

    private static int moveTo(StringBuilder out, int pen, int target) {
        if (target != pen) {
            out.append(Spaces.of(target - pen));
        }
        return target;
    }
}
