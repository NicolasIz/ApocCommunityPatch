package com.arkcronist.content.core.sanity;

import com.arkcronist.content.core.storage.PlacedContent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Two audits in a row, the same finding: only then is anything removed. */
class SanityJudgeTest {

    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-000000000001");

    /** A row and the block found where it stands, as the checker records it. */
    private record Stale(PlacedContent row, String found) {
    }

    private static Stale stale(int x, String found) {
        return new Stale(new PlacedContent(WORLD, x, 64, 0, "demo:ruby_block", PlacedContent.Kind.BLOCK), found);
    }

    private static SanityJudge<Stale> judge() {
        return new SanityJudge<>(Stale::row);
    }

    @Test
    void aFindingIsConfirmedOnlyByTheNextAuditFindingTheSame() {
        SanityJudge<Stale> judge = judge();
        assertTrue(judge.judge(List.of(stale(1, "minecraft:stone"))).isEmpty(), "once is a suspect");
        assertEquals(1, judge.suspects());
        assertEquals(List.of(stale(1, "minecraft:stone")), judge.judge(List.of(stale(1, "minecraft:stone"))));
        assertEquals(0, judge.suspects(), "acted on, not kept");
        assertTrue(judge.judge(List.of(stale(1, "minecraft:stone"))).isEmpty(), "found again after: a new suspect");
    }

    @Test
    void foundDifferentlyOrNotAtAllStartsOver() {
        SanityJudge<Stale> judge = judge();
        judge.judge(List.of(stale(1, "minecraft:air"), stale(2, "minecraft:air"), stale(3, "minecraft:air")));
        // 1: a rollback is still putting blocks back - air, then stone. 2: fixed. 3: the same.
        List<Stale> confirmed = judge.judge(List.of(stale(1, "minecraft:stone"), stale(3, "minecraft:air")));
        assertEquals(List.of(stale(3, "minecraft:air")), confirmed);
        assertEquals(1, judge.suspects(), "1, as stone now");
        assertTrue(judge.judge(List.of()).isEmpty(), "a chunk not audited forgets its suspects");
        assertEquals(0, judge.suspects());
        assertTrue(judge.judge(List.of(stale(1, "minecraft:stone"))).isEmpty());
    }

    @Test
    void rowsAreGroupedByTheChunkTheyStandIn() {
        PlacedContent a = new PlacedContent(WORLD, 0, 64, 0, "demo:a", PlacedContent.Kind.BLOCK);
        PlacedContent b = new PlacedContent(WORLD, 15, 70, 15, "demo:b", PlacedContent.Kind.FURNITURE);
        PlacedContent c = new PlacedContent(WORLD, -1, 64, 16, "demo:c", PlacedContent.Kind.BLOCK);
        PlacedContent d = new PlacedContent(WORLD, -17, 64, -33, "demo:d", PlacedContent.Kind.BLOCK);
        Map<Long, List<PlacedContent>> chunks = AuditPlan.byChunk(List.of(a, b, c, d));
        assertEquals(List.of(a, b), chunks.get(AuditPlan.chunkKey(0, 0)));
        assertEquals(List.of(c), chunks.get(AuditPlan.chunkKey(-1, 1)));
        assertEquals(List.of(d), chunks.get(AuditPlan.chunkKey(-2, -3)));
        // Paper's Chunk#getChunkKey: x in the low 32 bits, z in the high.
        assertEquals((-2L & 0xFFFFFFFFL) | ((-3L & 0xFFFFFFFFL) << 32), AuditPlan.chunkKey(-2, -3));
    }
}
