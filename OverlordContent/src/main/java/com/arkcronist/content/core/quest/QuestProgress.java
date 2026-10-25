package com.arkcronist.content.core.quest;

import com.arkcronist.content.core.quest.QuestDefinition.Kind;
import com.arkcronist.content.core.quest.QuestDefinition.Objective;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Where one player stands with one notice: taken and under way, or done - and, for creatures to
 * kill and blocks to break, how many so far. Kept in {@code quest_progress}, one row per player and
 * notice.
 *
 * @param counts      one per objective, in the notice's order; what a {@link Kind#DELIVER} counts is
 *                    never stored - the items are counted in the inventory when handed in
 * @param completions how many times the player has completed it, for repeatable notices
 */
public record QuestProgress(UUID player, String questId, State state, List<Integer> counts, long acceptedAt,
                            long completedAt, int completions) {

    public enum State {
        ACCEPTED,
        COMPLETED
    }

    public QuestProgress {
        counts = List.copyOf(counts);
    }

    /** Just taken: nothing done yet. */
    public static QuestProgress accepted(UUID player, QuestDefinition quest, long now, int completions) {
        List<Integer> zero = new ArrayList<>();
        quest.objectives().forEach(ignored -> zero.add(0));
        return new QuestProgress(player, quest.id(), State.ACCEPTED, zero, now, 0, completions);
    }

    public boolean active() {
        return state == State.ACCEPTED;
    }

    public int count(int objective) {
        return objective < counts.size() ? counts.get(objective) : 0;
    }

    /**
     * Counts something that happened - a creature killed, a block broken - against every objective
     * of that kind it matches, never past the objective's amount.
     *
     * @param names what the thing is called: {@code zombie}, {@code mythicmobs:de_umbraxis},
     *              {@code demo:ruby_block}, {@code diamond_ore}
     * @return the progress after it; the same object when nothing changed
     */
    public QuestProgress record(QuestDefinition quest, Kind kind, Collection<String> names, int amount) {
        if (!active() || kind == Kind.DELIVER) {
            return this;
        }
        List<Integer> next = new ArrayList<>(counts);
        while (next.size() < quest.objectives().size()) {
            next.add(0);
        }
        boolean changed = false;
        for (int i = 0; i < quest.objectives().size(); i++) {
            Objective objective = quest.objectives().get(i);
            if (objective.kind() != kind || next.get(i) >= objective.amount() || !matches(objective.target(), names)) {
                continue;
            }
            next.set(i, Math.min(objective.amount(), next.get(i) + amount));
            changed = true;
        }
        return changed ? new QuestProgress(player, questId, state, next, acceptedAt, completedAt, completions) : this;
    }

    /** Whether every kill and break objective is done; items to deliver are checked when handed in. */
    public boolean countedDone(QuestDefinition quest) {
        for (int i = 0; i < quest.objectives().size(); i++) {
            Objective objective = quest.objectives().get(i);
            if (objective.kind() != Kind.DELIVER && count(i) < objective.amount()) {
                return false;
            }
        }
        return true;
    }

    /** Completed, at {@code now}. */
    public QuestProgress completed(long now) {
        return new QuestProgress(player, questId, State.COMPLETED, counts, acceptedAt, now, completions + 1);
    }

    /** The counts as stored: {@code 3,0,12}. */
    public String encodedCounts() {
        StringBuilder text = new StringBuilder();
        for (int count : counts) {
            if (!text.isEmpty()) {
                text.append(',');
            }
            text.append(count);
        }
        return text.toString();
    }

    /** {@link #encodedCounts} read back; anything unreadable counts as 0. */
    public static List<Integer> decodeCounts(String text) {
        List<Integer> counts = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return counts;
        }
        for (String part : text.split(",")) {
            try {
                counts.add(Math.max(0, Integer.parseInt(part.trim())));
            } catch (NumberFormatException exception) {
                counts.add(0);
            }
        }
        return counts;
    }

    /**
     * Whether an objective's target names the thing: ignoring case, and with or without
     * {@code minecraft:} for vanilla names.
     */
    public static boolean matches(String target, Collection<String> names) {
        String wanted = normal(target);
        for (String name : names) {
            if (normal(name).equals(wanted)) {
                return true;
            }
        }
        return false;
    }

    private static String normal(String name) {
        String lower = name.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("minecraft:") ? lower.substring("minecraft:".length()) : lower;
    }
}
