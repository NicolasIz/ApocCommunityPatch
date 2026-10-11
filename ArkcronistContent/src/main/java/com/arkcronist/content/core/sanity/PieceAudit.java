package com.arkcronist.content.core.sanity;

import com.arkcronist.content.core.furniture.Footprint;
import com.arkcronist.content.core.storage.BlockKey;
import com.arkcronist.content.core.storage.FurniturePiece;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * The integrity check's verdict on one piece of furniture as a whole - every block it stands on and
 * its display together - from one look at it.
 *
 * <ul>
 *   <li>{@link Verdict#BROKEN}: one or more of its support blocks are gone - a rollback, {@code /fill},
 *       a world editor. What is left of it is taken out: display, remaining invisible supports and
 *       rows, its storage kept for an admin.</li>
 *   <li>{@link Verdict#GHOST}: every support is there but its display is not, with the chunks'
 *       entities loaded - killed by {@code /kill}, an entity cleaner, a rollback of entities. It is
 *       drawn again, as it faced.</li>
 *   <li>{@link Verdict#STRAY}: the same, but it cannot be drawn again - which way it faced was never
 *       recorded and it covers more than one block, or it is half of a double chest. Its invisible
 *       supports would only be walls nobody can see or break; it is taken out like a broken one.</li>
 *   <li>{@link Verdict#UNKNOWN}: cannot be told now - a chunk or its entities not loaded.</li>
 * </ul>
 *
 * <p>Pure: the server thread looks, this decides; and a verdict is acted on only when two audits in
 * a row reach it the same way ({@link SanityJudge}).</p>
 */
public final class PieceAudit {

    public enum Verdict {
        HEALTHY, BROKEN, GHOST, STRAY, UNKNOWN
    }

    /**
     * What one look at a piece saw.
     *
     * @param parts          how many blocks it stands on
     * @param supports       how many of them still hold the support block it stands on
     * @param display        whether its display was found
     * @param blocksLoaded   whether every chunk it covers was loaded, so each block could be read
     * @param entitiesLoaded whether the entities of those chunks were loaded too - only then is a
     *                       display not found gone, rather than not loaded yet
     * @param redrawable     whether it can be drawn again: its facing is known (recorded, or it is
     *                       one block), and it is not half of a double chest
     */
    public record Seen(int parts, int supports, boolean display, boolean blocksLoaded, boolean entitiesLoaded,
                       boolean redrawable) {
    }

    private PieceAudit() {
    }

    public static Verdict judge(Seen seen) {
        if (seen.parts() <= 0 || !seen.blocksLoaded()) {
            return Verdict.UNKNOWN;
        }
        if (seen.supports() < seen.parts()) {
            return Verdict.BROKEN;
        }
        if (seen.display()) {
            return Verdict.HEALTHY;
        }
        if (!seen.entitiesLoaded()) {
            return Verdict.UNKNOWN;
        }
        return seen.redrawable() ? Verdict.GHOST : Verdict.STRAY;
    }

    /**
     * Which recorded piece each block belongs to: every block of every piece's footprint, as its
     * definition has it now and turned as the piece was placed, to that piece's anchor.
     *
     * @param footprints the footprint of a furniture id; null for one no longer defined, whose
     *                   pieces are left alone
     * @return packed block position to the packed position of its piece's anchor
     */
    public static Map<Long, Long> anchors(Map<Long, FurniturePiece> pieces,
                                          Function<String, Footprint> footprints) {
        Map<Long, Long> anchors = new HashMap<>();
        for (FurniturePiece piece : pieces.values()) {
            Footprint footprint = footprints.apply(piece.furnitureId());
            if (footprint == null) {
                continue;
            }
            for (int[] offset : footprint.offsets(piece.yaw())) {
                anchors.putIfAbsent(BlockKey.pack(piece.x() + offset[0], piece.y() + offset[1], piece.z() + offset[2]),
                        piece.key());
            }
        }
        return anchors;
    }
}
