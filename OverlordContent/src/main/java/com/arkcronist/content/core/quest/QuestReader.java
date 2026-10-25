package com.arkcronist.content.core.quest;

import com.arkcronist.content.core.quest.QuestDefinition.Kind;
import com.arkcronist.content.core.quest.QuestDefinition.Objective;
import com.arkcronist.content.core.quest.QuestDefinition.Requirement;
import com.arkcronist.content.core.quest.QuestDefinition.Reward;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Notice templates in a content file, under {@code quests:} - for notices too long to type in
 * commands. They hang on no board until an admin posts one: {@code /arkcontent board post <id>}.
 *
 * <pre>
 * quests:
 *   dragon_scales:
 *     title: "&lt;gold&gt;Scales of Umbraxis"
 *     description:
 *       - "&lt;gray&gt;The smith pays well for them."
 *     icon: dragones_epicos:escama_umbraxis
 *     repeatable: true
 *     requirements:
 *       skills: {fighting: 10}       # AuraSkills
 *       jobs: {hunter: 5}            # ExcellentJobs or Jobs Reborn
 *       permission: rank.knight
 *     objectives:
 *       - {deliver: dragones_epicos:escama_umbraxis, amount: 12}
 *       - {kill: mythicmobs:DE_Umbraxis}
 *       - {break: DIAMOND_ORE, amount: 5}
 *     rewards:
 *       money: 5000
 *       items: ["demo:ruby 3", "DIAMOND 2"]
 *       commands: ["lp user %player% permission set knight.badge"]
 * </pre>
 */
public final class QuestReader {

    private QuestReader() {
    }

    /** Every notice under {@code node}; one that cannot be read is reported and left out. */
    public static List<QuestDefinition> read(Object node, String where, List<String> problems) {
        List<QuestDefinition> quests = new ArrayList<>();
        if (node == null) {
            return quests;
        }
        if (!(node instanceof Map<?, ?> section)) {
            problems.add(where + ": 'quests' should be a section of notice ids");
            return quests;
        }
        for (Map.Entry<?, ?> entry : section.entrySet()) {
            String id = String.valueOf(entry.getKey()).toLowerCase(Locale.ROOT);
            String prefix = where + " > quest " + id + ": ";
            if (!QuestDefinition.ID.matcher(id).matches()) {
                problems.add(prefix + "invalid id (allowed: a-z 0-9 _ - ., up to 64)");
                continue;
            }
            if (!(entry.getValue() instanceof Map<?, ?> quest)) {
                problems.add(prefix + "should be a section with at least 'objectives'");
                continue;
            }
            QuestDefinition read = quest(id, quest, prefix, problems);
            if (read != null) {
                quests.add(read);
            }
        }
        return quests;
    }

    private static QuestDefinition quest(String id, Map<?, ?> section, String prefix, List<String> problems) {
        String title = section.get("title") == null ? id : String.valueOf(section.get("title"));
        List<String> description = strings(section.get("description"));
        String icon = section.get("icon") == null ? null : String.valueOf(section.get("icon")).trim();
        boolean repeatable = Boolean.TRUE.equals(section.get("repeatable"));
        List<String> boards = strings(section.get("boards"));

        List<Requirement> requirements = new ArrayList<>();
        if (section.get("requirements") instanceof Map<?, ?> wanted) {
            levels(wanted.get("skills"), prefix + "requirements.skills: ", problems)
                    .forEach((skill, level) -> requirements.add(new Requirement.Skill(skill, level)));
            levels(wanted.get("jobs"), prefix + "requirements.jobs: ", problems)
                    .forEach((job, level) -> requirements.add(new Requirement.Job(job, level)));
            List<String> permissions = new ArrayList<>(strings(wanted.get("permission")));
            permissions.addAll(strings(wanted.get("permissions")));
            permissions.forEach(node -> requirements.add(new Requirement.Permission(node)));
        } else if (section.get("requirements") != null) {
            problems.add(prefix + "'requirements' should be a section: skills, jobs, permission");
        }

        List<Objective> objectives = new ArrayList<>();
        if (section.get("objectives") instanceof List<?> list) {
            for (Object raw : list) {
                Objective objective = objective(raw, prefix, problems);
                if (objective != null) {
                    objectives.add(objective);
                }
            }
        }
        if (objectives.isEmpty()) {
            problems.add(prefix + "needs at least one objective (deliver, kill or break) - skipped");
            return null;
        }
        if (objectives.size() > QuestDefinition.MAX_OBJECTIVES) {
            problems.add(prefix + "has " + objectives.size() + " objectives; the first " + QuestDefinition.MAX_OBJECTIVES
                    + " are kept");
            objectives = objectives.subList(0, QuestDefinition.MAX_OBJECTIVES);
        }

        List<Reward> rewards = new ArrayList<>();
        if (section.get("rewards") instanceof Map<?, ?> pays) {
            if (pays.get("money") instanceof Number money && money.doubleValue() > 0) {
                rewards.add(new Reward.Money(money.doubleValue()));
            } else if (pays.get("money") != null) {
                problems.add(prefix + "'rewards.money' should be a positive number");
            }
            for (String item : strings(pays.get("items"))) {
                String[] parts = item.trim().split("\\s+");
                int amount = 1;
                if (parts.length > 1) {
                    try {
                        amount = Math.max(1, Integer.parseInt(parts[1]));
                    } catch (NumberFormatException exception) {
                        problems.add(prefix + "reward item '" + item + "' should be '<item> <amount>'");
                        continue;
                    }
                }
                rewards.add(new Reward.Item(parts[0], amount));
            }
            if (pays.get("items") instanceof List<?> itemList) {
                for (Object raw : itemList) {
                    if (raw instanceof Map<?, ?> item && item.get("item") != null) {
                        rewards.add(new Reward.Item(String.valueOf(item.get("item")),
                                item.get("amount") instanceof Number number ? Math.max(1, number.intValue()) : 1));
                    }
                }
            }
            strings(pays.get("commands")).forEach(command -> rewards.add(new Reward.Command(command)));
        } else if (section.get("rewards") != null) {
            problems.add(prefix + "'rewards' should be a section: money, items, commands");
        }
        return new QuestDefinition(id, title, description, icon, requirements, objectives, rewards, repeatable, boards);
    }

    private static Objective objective(Object raw, String prefix, List<String> problems) {
        if (!(raw instanceof Map<?, ?> entry)) {
            problems.add(prefix + "an objective should be {deliver|kill|break: <target>, amount: <n>}");
            return null;
        }
        for (Kind kind : Kind.values()) {
            Object target = entry.get(kind.key());
            if (target == null) {
                continue;
            }
            int amount = 1;
            if (entry.get("amount") instanceof Number number) {
                amount = number.intValue();
            } else if (entry.get("amount") != null) {
                problems.add(prefix + "objective amount should be a whole number");
            }
            if (amount < 1) {
                problems.add(prefix + "objective amount should be at least 1; using 1");
                amount = 1;
            }
            return new Objective(kind, String.valueOf(target).trim(), amount);
        }
        problems.add(prefix + "objective " + entry + " says neither deliver, kill nor break");
        return null;
    }

    private static Map<String, Integer> levels(Object node, String prefix, List<String> problems) {
        Map<String, Integer> levels = new java.util.LinkedHashMap<>();
        if (node == null) {
            return levels;
        }
        if (!(node instanceof Map<?, ?> section)) {
            problems.add(prefix + "should be a section of names and levels");
            return levels;
        }
        for (Map.Entry<?, ?> entry : section.entrySet()) {
            if (entry.getValue() instanceof Number number && number.intValue() >= 1) {
                levels.put(String.valueOf(entry.getKey()).toLowerCase(Locale.ROOT), number.intValue());
            } else {
                problems.add(prefix + entry.getKey() + " should be a level of 1 or more");
            }
        }
        return levels;
    }

    private static List<String> strings(Object node) {
        List<String> values = new ArrayList<>();
        if (node instanceof List<?> list) {
            for (Object value : list) {
                if (value != null && !(value instanceof Map<?, ?>)) {
                    values.add(String.valueOf(value));
                }
            }
        } else if (node != null && !(node instanceof Map<?, ?>)) {
            values.add(String.valueOf(node));
        }
        return values;
    }
}
