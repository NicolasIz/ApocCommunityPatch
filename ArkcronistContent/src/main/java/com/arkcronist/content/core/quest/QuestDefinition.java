package com.arkcronist.content.core.quest;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * One notice of a quest board: what it asks, of whom, and what it pays.
 *
 * <pre>
 * title:        MiniMessage, the notice's name
 * description:  lines under it
 * requirements: an AuraSkills skill level, a job level (ExcellentJobs, Jobs Reborn), a permission
 * objectives:   items to hand in, creatures to kill, blocks to break - each a number of times
 * rewards:      money (Vault), items, console commands
 * </pre>
 *
 * <p>Plain data. Notices live in the database as JSON ({@link QuestJson}); a content pack can also
 * hold them as YAML templates ({@link QuestReader}) that an admin posts on the boards.</p>
 *
 * @param icon       a custom item id or a vanilla material for the notice's icon; null for paper
 * @param repeatable whether a player who completed it can take it again
 * @param boards     the furniture ids of the boards it hangs on; empty for every board
 */
public record QuestDefinition(String id, String title, List<String> description, @Nullable String icon,
                              List<Requirement> requirements, List<Objective> objectives, List<Reward> rewards,
                              boolean repeatable, List<String> boards) {

    /** What a notice id may be: it is typed in commands. */
    public static final Pattern ID = Pattern.compile("[a-z0-9_.\\-]{1,64}");
    /** More would not fit the notice's lore, nor a player's patience. */
    public static final int MAX_OBJECTIVES = 8;

    public QuestDefinition {
        description = List.copyOf(description);
        requirements = List.copyOf(requirements);
        objectives = List.copyOf(objectives);
        rewards = List.copyOf(rewards);
        boards = List.copyOf(boards);
    }

    /** A new, empty notice: what {@code /arkcontent board create} makes. */
    public static QuestDefinition draft(String id, String title) {
        return new QuestDefinition(id, title, List.of(), null, List.of(), List.of(), List.of(), false, List.of());
    }

    /** Whether it hangs on the board of furniture {@code boardId}. */
    public boolean on(String boardId) {
        return boards.isEmpty() || boards.contains(boardId);
    }

    public QuestDefinition withTitle(String title) {
        return new QuestDefinition(id, title, description, icon, requirements, objectives, rewards, repeatable, boards);
    }

    public QuestDefinition withDescription(List<String> description) {
        return new QuestDefinition(id, title, description, icon, requirements, objectives, rewards, repeatable, boards);
    }

    public QuestDefinition withIcon(@Nullable String icon) {
        return new QuestDefinition(id, title, description, icon, requirements, objectives, rewards, repeatable, boards);
    }

    public QuestDefinition withRequirement(Requirement requirement) {
        List<Requirement> all = new ArrayList<>(requirements);
        all.add(requirement);
        return new QuestDefinition(id, title, description, icon, all, objectives, rewards, repeatable, boards);
    }

    public QuestDefinition withObjective(Objective objective) {
        List<Objective> all = new ArrayList<>(objectives);
        all.add(objective);
        return new QuestDefinition(id, title, description, icon, requirements, all, rewards, repeatable, boards);
    }

    public QuestDefinition withReward(Reward reward) {
        List<Reward> all = new ArrayList<>(rewards);
        all.add(reward);
        return new QuestDefinition(id, title, description, icon, requirements, objectives, all, repeatable, boards);
    }

    public QuestDefinition withRepeatable(boolean repeatable) {
        return new QuestDefinition(id, title, description, icon, requirements, objectives, rewards, repeatable, boards);
    }

    public QuestDefinition withBoards(List<String> boards) {
        return new QuestDefinition(id, title, description, icon, requirements, objectives, rewards, repeatable, boards);
    }

    /** What a player must be before taking a notice. */
    public sealed interface Requirement {

        /** An AuraSkills skill - {@code mining}, {@code farming} - at {@code level} or more. */
        record Skill(String skill, int level) implements Requirement {
        }

        /** A job of ExcellentJobs or Jobs Reborn at {@code level} or more. */
        record Job(String job, int level) implements Requirement {
        }

        /** A permission node. */
        record Permission(String node) implements Requirement {
        }
    }

    /** What a notice asks. */
    public enum Kind {
        /** Items in the player's inventory, taken when the notice is completed. */
        DELIVER,
        /** Creatures killed after taking the notice: a vanilla type, or {@code mythicmobs:<type>}. */
        KILL,
        /** Blocks broken after taking the notice: a vanilla material, or a custom block's id. */
        BREAK;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * One thing to do, {@code amount} times.
     *
     * @param target a custom item or block id ({@code namespace:id}), a vanilla material or entity
     *               type ({@code DIAMOND}, {@code zombie}), or {@code mythicmobs:<type>}
     */
    public record Objective(Kind kind, String target, int amount) {
    }

    /** What completing a notice pays. */
    public sealed interface Reward {

        /** Through Vault. */
        record Money(double amount) implements Reward {
        }

        /** A custom item by id, or a vanilla material. */
        record Item(String item, int amount) implements Reward {
        }

        /**
         * An exact item stack - whatever an admin held when posting it - as Paper's serialised
         * bytes, base64.
         *
         * @param label what the notice calls it
         */
        record Stack(String base64, String label) implements Reward {
        }

        /** A console command; {@code %player%} is the player's name. */
        record Command(String command) implements Reward {
        }
    }
}
