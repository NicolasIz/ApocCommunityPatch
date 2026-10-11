package com.arkcronist.content.core.sanity;

import com.arkcronist.content.core.furniture.Footprint;
import com.arkcronist.content.core.sanity.PieceAudit.Seen;
import com.arkcronist.content.core.sanity.PieceAudit.Verdict;
import com.arkcronist.content.core.storage.BlockKey;
import com.arkcronist.content.core.storage.FurniturePiece;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 2.0's integrity check: a piece of furniture judged whole, from one look at it. */
class PieceAuditTest {

    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void aPieceStandingOnAllItsBlocksWithItsDisplayIsHealthy() {
        assertEquals(Verdict.HEALTHY, PieceAudit.judge(new Seen(6, 6, true, true, true, true)));
        // Its entities not loaded yet does not matter once the display was seen.
        assertEquals(Verdict.HEALTHY, PieceAudit.judge(new Seen(1, 1, true, true, false, false)));
    }

    /** One block of six gone - /fill on the top of the guillotine - and the whole piece is broken. */
    @Test
    void anyMissingSupportBreaksTheWholePieceDisplayOrNot() {
        assertEquals(Verdict.BROKEN, PieceAudit.judge(new Seen(6, 5, true, true, true, true)));
        assertEquals(Verdict.BROKEN, PieceAudit.judge(new Seen(6, 0, false, true, false, false)),
                "no entities loaded is no reason to keep rows whose blocks are gone");
        assertEquals(Verdict.BROKEN, PieceAudit.judge(new Seen(1, 0, true, true, true, true)));
    }

    /** Every support there, no display: drawn again if it can be, taken out if it cannot. */
    @Test
    void aPieceWithoutItsDisplayIsAGhostOnlyWhenItsEntitiesAreLoaded() {
        assertEquals(Verdict.GHOST, PieceAudit.judge(new Seen(3, 3, false, true, true, true)));
        assertEquals(Verdict.STRAY, PieceAudit.judge(new Seen(3, 3, false, true, true, false)));
        assertEquals(Verdict.UNKNOWN, PieceAudit.judge(new Seen(3, 3, false, true, false, true)),
                "entities load after blocks: a display not loaded yet is not a display gone");
    }

    @Test
    void nothingIsJudgedWhereABlockCouldNotBeRead() {
        assertEquals(Verdict.UNKNOWN, PieceAudit.judge(new Seen(6, 2, false, false, true, true)));
        assertEquals(Verdict.UNKNOWN, PieceAudit.judge(new Seen(0, 0, false, true, true, true)));
    }

    /**
     * Which recorded piece a block belongs to: a 2x3 guillotine placed facing west covers its anchor
     * and five blocks across and up, each mapped back to the anchor; a piece of something no longer
     * defined maps nothing.
     */
    @Test
    void everyBlockOfARecordedFootprintLeadsBackToItsAnchor() {
        FurniturePiece guillotine = new FurniturePiece(WORLD, 10, 64, -5, "medieval_props:guillotina_plaza", 90f);
        FurniturePiece gone = new FurniturePiece(WORLD, 0, 64, 0, "old:removed", 0f);
        Map<Long, Long> anchors = PieceAudit.anchors(Map.of(guillotine.key(), guillotine, gone.key(), gone),
                id -> id.equals("medieval_props:guillotina_plaza") ? new Footprint(2, 3) : null);
        assertEquals(6, anchors.size());
        // Facing west, the viewer's right is +z: the second column is at z -4.
        for (int up = 0; up < 3; up++) {
            for (int across = 0; across < 2; across++) {
                assertEquals(guillotine.key(), anchors.get(BlockKey.pack(10, 64 + up, -5 + across)));
            }
        }
        assertNull(anchors.get(gone.key()));
        assertFalse(anchors.containsKey(BlockKey.pack(10, 64, -6)), "not the left side");
    }
}
