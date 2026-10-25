package com.arkcronist.content.core.quest;

import com.arkcronist.content.core.quest.QuestDefinition.Kind;
import com.arkcronist.content.core.quest.QuestDefinition.Objective;
import com.arkcronist.content.core.storage.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The quest boards' notices and players' progress in a real SQLite file. */
class QuestBoardStoreTest {

    private static final UUID ALICE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BOB = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @TempDir
    Path temp;

    private DatabaseManager database;
    private QuestBoardStore store;

    @BeforeEach
    void open() throws Exception {
        database = new DatabaseManager(temp.resolve("content.db"), Logger.getLogger("test"));
        get(database.open());
        store = new QuestBoardStore(database);
    }

    @AfterEach
    void close() {
        database.close();
    }

    private static <T> T get(CompletableFuture<T> future) throws Exception {
        return future.get(10, TimeUnit.SECONDS);
    }

    private static QuestDefinition hunt() {
        return QuestDefinition.draft("zombie_hunt", "<green>Zombie Hunt")
                .withObjective(new Objective(Kind.KILL, "zombie", 10));
    }

    @Test
    void aNoticeHangsOnTheBoardOnlyOncePostedAndEditsKeepThat() throws Exception {
        get(store.save(hunt(), "Admin"));
        assertTrue(get(store.board(ALICE)).notices().isEmpty(), "a draft is on no board");
        assertTrue(get(store.setPosted("zombie_hunt", true)));
        assertFalse(get(store.setPosted("nothing", true)));
        QuestDefinition edited = hunt().withDescription(List.of("They come at night."));
        get(store.save(edited, null));
        QuestBoardStore.Board board = get(store.board(ALICE));
        assertEquals(1, board.notices().size());
        QuestBoardStore.Notice notice = board.notices().get(0);
        assertEquals(edited, notice.quest());
        assertTrue(notice.posted(), "an edit keeps it posted");
        assertEquals("Admin", notice.author(), "and keeps who first wrote it");
        assertTrue(get(store.notice("zombie_hunt")).isPresent());
        assertTrue(get(store.notice("nothing")).isEmpty());
    }

    @Test
    void takingTwiceOrCompletingTwiceSucceedsOnce() throws Exception {
        QuestDefinition quest = hunt();
        get(store.save(quest, null));
        QuestProgress fresh = QuestProgress.accepted(ALICE, quest, 1000, 0);
        assertEquals(QuestBoardStore.Taken.TAKEN, get(store.accept(fresh, false)));
        assertEquals(QuestBoardStore.Taken.ALREADY, get(store.accept(fresh, false)), "a double click");

        QuestProgress counted = fresh.record(quest, Kind.KILL, List.of("zombie"), 1).record(quest, Kind.KILL,
                List.of("zombie"), 1);
        get(store.saveCounts(List.of(counted)));
        assertEquals(List.of(2), get(store.progress(ALICE)).get("zombie_hunt").counts());

        QuestProgress done = counted.completed(5000);
        assertTrue(get(store.complete(done)));
        assertFalse(get(store.complete(done)), "paid once");
        // Counts written late, for a notice already handed in, change nothing.
        get(store.saveCounts(List.of(counted)));
        QuestProgress stored = get(store.progress(ALICE)).get("zombie_hunt");
        assertEquals(QuestProgress.State.COMPLETED, stored.state());
        assertEquals(1, stored.completions());
        assertEquals(5000, stored.completedAt());

        assertEquals(QuestBoardStore.Taken.DONE, get(store.accept(QuestProgress.accepted(ALICE, quest, 6000, 1), false)),
                "not repeatable");
        assertEquals(QuestBoardStore.Taken.TAKEN, get(store.accept(QuestProgress.accepted(ALICE, quest, 6000, 1), true)),
                "repeatable: started over");
        QuestProgress again = get(store.progress(ALICE)).get("zombie_hunt");
        assertTrue(again.active());
        assertEquals(1, again.completions(), "how many times it was done is kept");
        assertTrue(get(store.progress(BOB)).isEmpty());
    }

    @Test
    void givingUpGoesBackToDoneOrForgetsAndDeletingANoticeTakesEveryonesProgress() throws Exception {
        QuestDefinition quest = hunt();
        get(store.save(quest, null));
        get(store.accept(QuestProgress.accepted(ALICE, quest, 1, 0), true));
        assertTrue(get(store.abandon(ALICE, "zombie_hunt")));
        assertFalse(get(store.progress(ALICE)).containsKey("zombie_hunt"), "never done: forgotten");

        get(store.accept(QuestProgress.accepted(BOB, quest, 1, 0), true));
        get(store.complete(QuestProgress.accepted(BOB, quest, 1, 0).completed(2)));
        get(store.accept(QuestProgress.accepted(BOB, quest, 3, 1), true));
        assertTrue(get(store.abandon(BOB, "zombie_hunt")));
        assertEquals(QuestProgress.State.COMPLETED, get(store.progress(BOB)).get("zombie_hunt").state(),
                "done before: back to done");
        assertFalse(get(store.abandon(BOB, "zombie_hunt")));

        assertTrue(get(store.delete("zombie_hunt")));
        assertTrue(get(store.progress(BOB)).isEmpty());
        assertFalse(get(store.delete("zombie_hunt")));
    }

    @Test
    void aRowThatCannotBeReadIsListedApartAndLeftAlone() throws Exception {
        get(store.save(hunt(), null));
        get(store.setPosted("zombie_hunt", true));
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + temp.resolve("content.db"));
             Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO quest_notices VALUES ('broken', '{\"v\":99}', 1, NULL, 2, 2)");
        }
        QuestBoardStore.Board board = get(store.board(ALICE));
        assertEquals(List.of("zombie_hunt"), board.notices().stream().map(n -> n.quest().id()).toList());
        assertEquals(List.of("broken"), board.unreadable().stream().map(QuestBoardStore.Unreadable::id).toList());
        Map<String, QuestProgress> none = board.progress();
        assertTrue(none.isEmpty());
        assertNotEquals(0, get(store.allNotices()).unreadable().size());
    }

    @Test
    void changesSentAtOnceAllLandInTheOrderSent() throws Exception {
        // As a script or a console batch sends them: nobody waits for the one before.
        CompletableFuture<Boolean> created = store.create(QuestDefinition.draft("bounty", "Bounty"), "Admin");
        CompletableFuture<QuestDefinition> line = store.edit("bounty", quest -> quest.withDescription(List.of("Quickly.")));
        CompletableFuture<QuestDefinition> kill = store.edit("bounty",
                quest -> quest.withObjective(new Objective(Kind.KILL, "zombie", 2)));
        CompletableFuture<QuestDefinition> paid = store.edit("bounty",
                quest -> quest.withReward(new QuestDefinition.Reward.Money(50)));
        CompletableFuture<QuestDefinition> posted = store.post("bounty", true, null, "Admin");
        assertTrue(get(created));
        get(CompletableFuture.allOf(line, kill, paid, posted));
        QuestBoardStore.Notice notice = get(store.notice("bounty")).orElseThrow();
        assertEquals(List.of("Quickly."), notice.quest().description());
        assertEquals(List.of(new Objective(Kind.KILL, "zombie", 2)), notice.quest().objectives());
        assertEquals(List.of(new QuestDefinition.Reward.Money(50)), notice.quest().rewards());
        assertTrue(notice.posted());
        assertEquals(notice.quest(), get(posted));

        assertFalse(get(store.create(QuestDefinition.draft("bounty", "Another"), "Admin")), "the id is taken");
        assertEquals("Bounty", get(store.notice("bounty")).orElseThrow().quest().title());
    }

    @Test
    void aChangeThatFailsWritesNothingAndPostingNeedsAnObjective() throws Exception {
        get(store.create(QuestDefinition.draft("bounty", "Bounty"), null));
        ExecutionException refused = assertThrows(ExecutionException.class, () -> store.edit("bounty", quest -> {
            throw new IllegalArgumentException("a notice has at most 8 objectives");
        }).get(10, TimeUnit.SECONDS));
        assertEquals("a notice has at most 8 objectives", refused.getCause().getMessage());
        assertEquals(QuestDefinition.draft("bounty", "Bounty"), get(store.notice("bounty")).orElseThrow().quest());

        assertThrows(ExecutionException.class, () -> store.edit("nothing", quest -> quest).get(10, TimeUnit.SECONDS));
        ExecutionException empty = assertThrows(ExecutionException.class,
                () -> store.post("bounty", true, null, null).get(10, TimeUnit.SECONDS));
        assertTrue(empty.getCause().getMessage().contains("no objective"));
        assertFalse(get(store.notice("bounty")).orElseThrow().posted());
    }

    @Test
    void postingATemplateCopiesItInOnce() throws Exception {
        assertThrows(ExecutionException.class, () -> store.post("zombie_hunt", true, null, null).get(10, TimeUnit.SECONDS),
                "neither a notice nor a template");
        assertEquals(hunt(), get(store.post("zombie_hunt", true, hunt(), "Admin")));
        QuestBoardStore.Notice notice = get(store.notice("zombie_hunt")).orElseThrow();
        assertTrue(notice.posted());
        assertEquals("Admin", notice.author());
        // Edited since, the database's copy wins over the template's.
        get(store.edit("zombie_hunt", quest -> quest.withTitle("Edited")));
        assertEquals("Edited", get(store.post("zombie_hunt", true, hunt(), "Admin")).title());
        assertEquals("Edited", get(store.post("zombie_hunt", false, hunt(), "Admin")).title());
        assertFalse(get(store.notice("zombie_hunt")).orElseThrow().posted());
    }
}
