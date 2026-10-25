package com.arkcronist.content.core.quest;

import com.arkcronist.content.core.storage.DatabaseManager;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;

/**
 * The quest boards' notices and every player's progress with them, in the plugin's SQLite file.
 *
 * <p>Every call runs on the database's own thread and returns a future at once: a board being
 * opened, a notice being taken, a kill being counted never makes the server thread wait for the
 * disk. The steps that must not happen twice - taking a notice, completing it - are one statement
 * each whose condition is checked by SQLite itself, so a double click, or two players' clicks on the
 * same notice, can only succeed once. An admin's change to a notice - reading it, changing it,
 * writing it back - is one task too, so two changes sent at once both land, in the order sent.</p>
 */
public final class QuestBoardStore {

    /** A notice as stored. */
    public record Notice(QuestDefinition quest, boolean posted, @Nullable String author, long createdAt,
                         long updatedAt) {
    }

    /** A row that could not be read as a notice - left as it is, and reported. */
    public record Unreadable(String id, String reason) {
    }

    /**
     * What a board shows one player: the notices posted, and where they stand with each.
     *
     * @param progress by notice id
     */
    public record Board(List<Notice> notices, Map<String, QuestProgress> progress, List<Unreadable> unreadable) {

        public Board {
            notices = List.copyOf(notices);
            progress = Map.copyOf(progress);
            unreadable = List.copyOf(unreadable);
        }
    }

    /** What taking a notice came to. */
    public enum Taken {
        /** Taken now. */
        TAKEN,
        /** The player has it already. */
        ALREADY,
        /** Done once, and it is not repeatable. */
        DONE
    }

    private static final String NOTICES = DatabaseManager.NOTICE_TABLE;
    private static final String PROGRESS = DatabaseManager.PROGRESS_TABLE;

    private final DatabaseManager database;

    public QuestBoardStore(DatabaseManager database) {
        this.database = database;
    }

    // ---------------------------------------------------------------- notices

    /**
     * Writes a notice - new, or changed by an admin - keeping whether it is posted and when it was
     * first written.
     */
    public CompletableFuture<Void> save(QuestDefinition quest, @Nullable String author) {
        return database.run(connection -> {
            write(connection, quest, author);
            return null;
        });
    }

    /** Writes a new notice, not posted. @return false, writing nothing, when there is one by its id already */
    public CompletableFuture<Boolean> create(QuestDefinition quest, @Nullable String author) {
        return database.run(connection -> DatabaseManager.inTransaction(connection, () -> {
            if (read(connection, quest.id()).isPresent()) {
                return false;
            }
            write(connection, quest, author);
            return true;
        }));
    }

    /**
     * Changes a notice: read, changed and written back in one task on the database's thread, so no
     * other change comes in between.
     *
     * @param change may throw an {@link IllegalArgumentException} saying why the change cannot be
     *               made; nothing is written then
     * @return the notice as changed
     * @throws IllegalStateException through the future, when there is no such notice
     */
    public CompletableFuture<QuestDefinition> edit(String id, UnaryOperator<QuestDefinition> change) {
        return database.run(connection -> DatabaseManager.inTransaction(connection, () -> {
            Notice notice = read(connection, id)
                    .orElseThrow(() -> new IllegalStateException("no notice '" + id + "' - create it first"));
            QuestDefinition changed = change.apply(notice.quest());
            if (!changed.id().equals(id)) {
                throw new IllegalArgumentException("a change cannot rename a notice");
            }
            write(connection, changed, null);
            return changed;
        }));
    }

    /**
     * Hangs a notice on the boards, or takes it down. A notice that is not in the database yet but
     * has a {@code template} - one written in contents/ - is copied in first and posted.
     *
     * @return the notice
     * @throws IllegalStateException through the future, when there is no such notice nor template,
     *                               or a notice to post has no objective
     */
    public CompletableFuture<QuestDefinition> post(String id, boolean posted, @Nullable QuestDefinition template,
                                                   @Nullable String author) {
        return database.run(connection -> DatabaseManager.inTransaction(connection, () -> {
            Optional<Notice> notice = read(connection, id);
            QuestDefinition quest;
            if (notice.isPresent()) {
                quest = notice.get().quest();
            } else if (posted && template != null && template.id().equals(id)) {
                quest = template;
                write(connection, quest, author);
            } else {
                throw new IllegalStateException("no notice '" + id + "'");
            }
            if (posted && quest.objectives().isEmpty()) {
                throw new IllegalStateException("'" + id + "' has no objective yet - add one first");
            }
            setPosted(connection, id, posted);
            return quest;
        }));
    }

    /** Hangs a notice on the boards, or takes it down. @return whether the notice exists */
    public CompletableFuture<Boolean> setPosted(String id, boolean posted) {
        return database.run(connection -> setPosted(connection, id, posted));
    }

    private static boolean setPosted(Connection connection, String id, boolean posted) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement("UPDATE " + NOTICES
                + " SET posted = ?, updated_at = ? WHERE id = ?")) {
            update.setInt(1, posted ? 1 : 0);
            update.setLong(2, System.currentTimeMillis());
            update.setString(3, id);
            return update.executeUpdate() > 0;
        }
    }

    /** Writes a notice's definition: over the row with its id, keeping posted and created_at, or as a new row. */
    private static void write(Connection connection, QuestDefinition quest, @Nullable String author) throws SQLException {
        String json = QuestJson.write(quest);
        long now = System.currentTimeMillis();
        try (PreparedStatement update = connection.prepareStatement("UPDATE " + NOTICES
                + " SET definition = ?, updated_at = ? WHERE id = ?")) {
            update.setString(1, json);
            update.setLong(2, now);
            update.setString(3, quest.id());
            if (update.executeUpdate() > 0) {
                return;
            }
        }
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO " + NOTICES
                + " (id, definition, posted, author, created_at, updated_at) VALUES (?, ?, 0, ?, ?, ?)")) {
            insert.setString(1, quest.id());
            insert.setString(2, json);
            insert.setString(3, author);
            insert.setLong(4, now);
            insert.setLong(5, now);
            insert.executeUpdate();
        }
    }

    /** Deletes a notice and everyone's progress with it, in one transaction. @return whether it existed */
    public CompletableFuture<Boolean> delete(String id) {
        return database.run(connection -> DatabaseManager.inTransaction(connection, () -> {
            try (PreparedStatement progress = connection.prepareStatement("DELETE FROM " + PROGRESS + " WHERE quest_id = ?")) {
                progress.setString(1, id);
                progress.executeUpdate();
            }
            try (PreparedStatement notice = connection.prepareStatement("DELETE FROM " + NOTICES + " WHERE id = ?")) {
                notice.setString(1, id);
                return notice.executeUpdate() > 0;
            }
        }));
    }

    public CompletableFuture<Optional<Notice>> notice(String id) {
        return database.run(connection -> read(connection, id));
    }

    private static Optional<Notice> read(Connection connection, String id) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("SELECT id, definition, posted, author, created_at,"
                + " updated_at FROM " + NOTICES + " WHERE id = ?")) {
            select.setString(1, id);
            try (ResultSet result = select.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(notice(result));
            }
        }
    }

    /** Every notice, posted or not, for admins; rows that cannot be read are listed apart. */
    public CompletableFuture<Board> allNotices() {
        return database.run(connection -> {
            List<Notice> notices = new ArrayList<>();
            List<Unreadable> unreadable = new ArrayList<>();
            readNotices(connection, false, notices, unreadable);
            return new Board(notices, Map.of(), unreadable);
        });
    }

    /** The posted notices and one player's progress, read together. */
    public CompletableFuture<Board> board(UUID player) {
        return database.run(connection -> {
            List<Notice> notices = new ArrayList<>();
            List<Unreadable> unreadable = new ArrayList<>();
            readNotices(connection, true, notices, unreadable);
            return new Board(notices, readProgress(connection, player), unreadable);
        });
    }

    private static void readNotices(Connection connection, boolean postedOnly, List<Notice> notices,
                                    List<Unreadable> unreadable) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("SELECT id, definition, posted, author, created_at,"
                + " updated_at FROM " + NOTICES + (postedOnly ? " WHERE posted = 1" : "") + " ORDER BY created_at, id")) {
            try (ResultSet result = select.executeQuery()) {
                while (result.next()) {
                    try {
                        notices.add(notice(result));
                    } catch (IllegalArgumentException broken) {
                        unreadable.add(new Unreadable(result.getString(1), broken.getMessage()));
                    }
                }
            }
        }
    }

    private static Notice notice(ResultSet result) throws SQLException {
        return new Notice(QuestJson.read(result.getString(2)), result.getInt(3) != 0, result.getString(4),
                result.getLong(5), result.getLong(6));
    }

    // ---------------------------------------------------------------- progress

    /** One player's progress with every notice they ever took, by notice id. */
    public CompletableFuture<Map<String, QuestProgress>> progress(UUID player) {
        return database.run(connection -> readProgress(connection, player));
    }

    private static Map<String, QuestProgress> readProgress(Connection connection, UUID player) throws SQLException {
        Map<String, QuestProgress> progress = new LinkedHashMap<>();
        try (PreparedStatement select = connection.prepareStatement("SELECT quest_id, state, progress, accepted_at,"
                + " completed_at, completions FROM " + PROGRESS + " WHERE player_uuid = ?")) {
            select.setString(1, player.toString());
            try (ResultSet result = select.executeQuery()) {
                while (result.next()) {
                    QuestProgress.State state;
                    try {
                        state = QuestProgress.State.valueOf(result.getString(2));
                    } catch (IllegalArgumentException unknown) {
                        continue;
                    }
                    progress.put(result.getString(1), new QuestProgress(player, result.getString(1), state,
                            QuestProgress.decodeCounts(result.getString(3)), result.getLong(4), result.getLong(5),
                            result.getInt(6)));
                }
            }
        }
        return progress;
    }

    /**
     * Takes a notice: a new row, or - for a repeatable notice done before - the done row started
     * over. Checked and written in one transaction.
     */
    public CompletableFuture<Taken> accept(QuestProgress fresh, boolean repeatable) {
        return database.run(connection -> DatabaseManager.inTransaction(connection, () -> {
            String state = null;
            int completions = 0;
            try (PreparedStatement select = connection.prepareStatement("SELECT state, completions FROM " + PROGRESS
                    + " WHERE player_uuid = ? AND quest_id = ?")) {
                select.setString(1, fresh.player().toString());
                select.setString(2, fresh.questId());
                try (ResultSet result = select.executeQuery()) {
                    if (result.next()) {
                        state = result.getString(1);
                        completions = result.getInt(2);
                    }
                }
            }
            if (QuestProgress.State.ACCEPTED.name().equals(state)) {
                return Taken.ALREADY;
            }
            if (state != null && !repeatable) {
                return Taken.DONE;
            }
            try (PreparedStatement upsert = connection.prepareStatement("INSERT OR REPLACE INTO " + PROGRESS
                    + " (player_uuid, quest_id, state, progress, accepted_at, completed_at, completions)"
                    + " VALUES (?, ?, 'ACCEPTED', ?, ?, 0, ?)")) {
                upsert.setString(1, fresh.player().toString());
                upsert.setString(2, fresh.questId());
                upsert.setString(3, fresh.encodedCounts());
                upsert.setLong(4, fresh.acceptedAt());
                upsert.setInt(5, completions);
                upsert.executeUpdate();
            }
            return Taken.TAKEN;
        }));
    }

    /** Writes how far players are with notices they have under way; finished or dropped ones are left alone. */
    public CompletableFuture<Void> saveCounts(Collection<QuestProgress> progress) {
        List<QuestProgress> rows = List.copyOf(progress);
        return database.run(connection -> DatabaseManager.inTransaction(connection, () -> {
            try (PreparedStatement update = connection.prepareStatement("UPDATE " + PROGRESS
                    + " SET progress = ? WHERE player_uuid = ? AND quest_id = ? AND state = 'ACCEPTED'")) {
                for (QuestProgress row : rows) {
                    update.setString(1, row.encodedCounts());
                    update.setString(2, row.player().toString());
                    update.setString(3, row.questId());
                    update.addBatch();
                }
                update.executeBatch();
            }
            return null;
        }));
    }

    /**
     * Marks a notice done - only if it is still under way, so it is paid once.
     *
     * @return whether it was under way, and is now done
     */
    public CompletableFuture<Boolean> complete(QuestProgress done) {
        return database.run(connection -> {
            try (PreparedStatement update = connection.prepareStatement("UPDATE " + PROGRESS
                    + " SET state = 'COMPLETED', progress = ?, completed_at = ?, completions = completions + 1"
                    + " WHERE player_uuid = ? AND quest_id = ? AND state = 'ACCEPTED'")) {
                update.setString(1, done.encodedCounts());
                update.setLong(2, done.completedAt());
                update.setString(3, done.player().toString());
                update.setString(4, done.questId());
                return update.executeUpdate() > 0;
            }
        });
    }

    /**
     * Gives up a notice under way: it goes back to done if it was done before, and is forgotten
     * otherwise. @return whether it was under way
     */
    public CompletableFuture<Boolean> abandon(UUID player, String questId) {
        return database.run(connection -> DatabaseManager.inTransaction(connection, () -> {
            try (PreparedStatement back = connection.prepareStatement("UPDATE " + PROGRESS
                    + " SET state = 'COMPLETED' WHERE player_uuid = ? AND quest_id = ? AND state = 'ACCEPTED'"
                    + " AND completions > 0")) {
                back.setString(1, player.toString());
                back.setString(2, questId);
                if (back.executeUpdate() > 0) {
                    return true;
                }
            }
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM " + PROGRESS
                    + " WHERE player_uuid = ? AND quest_id = ? AND state = 'ACCEPTED'")) {
                delete.setString(1, player.toString());
                delete.setString(2, questId);
                return delete.executeUpdate() > 0;
            }
        }));
    }

    /** Forgets a player's progress with a notice altogether, for admins. @return whether there was any */
    public CompletableFuture<Boolean> reset(UUID player, String questId) {
        return database.run(connection -> {
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM " + PROGRESS
                    + " WHERE player_uuid = ? AND quest_id = ?")) {
                delete.setString(1, player.toString());
                delete.setString(2, questId);
                return delete.executeUpdate() > 0;
            }
        });
    }
}
