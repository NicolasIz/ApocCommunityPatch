package com.arkcronist.content.core.quest;

import com.arkcronist.content.core.quest.QuestDefinition.Kind;
import com.arkcronist.content.core.quest.QuestDefinition.Objective;
import com.arkcronist.content.core.quest.QuestDefinition.Requirement;
import com.arkcronist.content.core.quest.QuestDefinition.Reward;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A notice as it is kept in the database's {@code quest_notices.definition}: JSON, written by hand
 * rather than reflected, so the format is what is written here and nothing else - a field renamed
 * in the code does not quietly change what older rows mean.
 *
 * <pre>
 * {"v":1, "id":"dragon_scales", "title":"&lt;gold&gt;Dragon scales", "description":["..."],
 *  "icon":"dragones_epicos:escama_umbraxis", "repeatable":false, "boards":[],
 *  "requirements":[{"type":"skill","skill":"mining","level":10}],
 *  "objectives":[{"type":"deliver","target":"dragones_epicos:escama_umbraxis","amount":12}],
 *  "rewards":[{"type":"money","amount":500.0}]}
 * </pre>
 */
public final class QuestJson {

    public static final int VERSION = 1;

    private QuestJson() {
    }

    public static String write(QuestDefinition quest) {
        JsonObject json = new JsonObject();
        json.addProperty("v", VERSION);
        json.addProperty("id", quest.id());
        json.addProperty("title", quest.title());
        json.add("description", strings(quest.description()));
        if (quest.icon() != null) {
            json.addProperty("icon", quest.icon());
        }
        json.addProperty("repeatable", quest.repeatable());
        json.add("boards", strings(quest.boards()));
        JsonArray requirements = new JsonArray();
        for (Requirement requirement : quest.requirements()) {
            JsonObject entry = new JsonObject();
            switch (requirement) {
                case Requirement.Skill skill -> {
                    entry.addProperty("type", "skill");
                    entry.addProperty("skill", skill.skill());
                    entry.addProperty("level", skill.level());
                }
                case Requirement.Job job -> {
                    entry.addProperty("type", "job");
                    entry.addProperty("job", job.job());
                    entry.addProperty("level", job.level());
                }
                case Requirement.Permission permission -> {
                    entry.addProperty("type", "permission");
                    entry.addProperty("node", permission.node());
                }
            }
            requirements.add(entry);
        }
        json.add("requirements", requirements);
        JsonArray objectives = new JsonArray();
        for (Objective objective : quest.objectives()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("type", objective.kind().key());
            entry.addProperty("target", objective.target());
            entry.addProperty("amount", objective.amount());
            objectives.add(entry);
        }
        json.add("objectives", objectives);
        JsonArray rewards = new JsonArray();
        for (Reward reward : quest.rewards()) {
            JsonObject entry = new JsonObject();
            switch (reward) {
                case Reward.Money money -> {
                    entry.addProperty("type", "money");
                    entry.addProperty("amount", money.amount());
                }
                case Reward.Item item -> {
                    entry.addProperty("type", "item");
                    entry.addProperty("item", item.item());
                    entry.addProperty("amount", item.amount());
                }
                case Reward.Stack stack -> {
                    entry.addProperty("type", "stack");
                    entry.addProperty("bytes", stack.base64());
                    entry.addProperty("label", stack.label());
                }
                case Reward.Command command -> {
                    entry.addProperty("type", "command");
                    entry.addProperty("command", command.command());
                }
            }
            rewards.add(entry);
        }
        json.add("rewards", rewards);
        return json.toString();
    }

    /**
     * @throws IllegalArgumentException for JSON that is not a notice this version can read - the row
     *                                  is then left alone, never rewritten
     */
    public static QuestDefinition read(String text) {
        JsonObject json;
        try {
            if (!(JsonParser.parseString(text) instanceof JsonObject object)) {
                throw new IllegalArgumentException("not a JSON object");
            }
            json = object;
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException("not valid JSON: " + exception.getMessage(), exception);
        }
        int version = integer(json, "v", 1);
        if (version > VERSION) {
            throw new IllegalArgumentException("written by a newer version (format " + version + ")");
        }
        String id = string(json, "id", null);
        if (id == null || !QuestDefinition.ID.matcher(id).matches()) {
            throw new IllegalArgumentException("no valid id");
        }
        List<Requirement> requirements = new ArrayList<>();
        for (JsonObject entry : objects(json, "requirements")) {
            String type = string(entry, "type", "");
            switch (type) {
                case "skill" -> requirements.add(new Requirement.Skill(string(entry, "skill", ""), integer(entry, "level", 1)));
                case "job" -> requirements.add(new Requirement.Job(string(entry, "job", ""), integer(entry, "level", 1)));
                case "permission" -> requirements.add(new Requirement.Permission(string(entry, "node", "")));
                default -> throw new IllegalArgumentException("unknown requirement '" + type + "'");
            }
        }
        List<Objective> objectives = new ArrayList<>();
        for (JsonObject entry : objects(json, "objectives")) {
            String type = string(entry, "type", "");
            Kind kind;
            try {
                kind = Kind.valueOf(type.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException unknown) {
                throw new IllegalArgumentException("unknown objective '" + type + "'");
            }
            objectives.add(new Objective(kind, string(entry, "target", ""), Math.max(1, integer(entry, "amount", 1))));
        }
        List<Reward> rewards = new ArrayList<>();
        for (JsonObject entry : objects(json, "rewards")) {
            String type = string(entry, "type", "");
            switch (type) {
                case "money" -> rewards.add(new Reward.Money(entry.get("amount") instanceof JsonElement amount
                        && amount.isJsonPrimitive() ? amount.getAsDouble() : 0));
                case "item" -> rewards.add(new Reward.Item(string(entry, "item", ""), Math.max(1, integer(entry, "amount", 1))));
                case "stack" -> rewards.add(new Reward.Stack(string(entry, "bytes", ""), string(entry, "label", "an item")));
                case "command" -> rewards.add(new Reward.Command(string(entry, "command", "")));
                default -> throw new IllegalArgumentException("unknown reward '" + type + "'");
            }
        }
        List<String> boards = new ArrayList<>();
        if (json.get("boards") instanceof JsonArray array) {
            array.forEach(element -> boards.add(element.getAsString()));
        }
        List<String> description = new ArrayList<>();
        if (json.get("description") instanceof JsonArray array) {
            array.forEach(element -> description.add(element.getAsString()));
        }
        return new QuestDefinition(id, string(json, "title", id), description, string(json, "icon", null),
                requirements, objectives, rewards, json.get("repeatable") instanceof JsonElement repeat
                && repeat.isJsonPrimitive() && repeat.getAsBoolean(), boards);
    }

    private static JsonArray strings(List<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    private static List<JsonObject> objects(JsonObject json, String key) {
        List<JsonObject> objects = new ArrayList<>();
        if (json.get(key) instanceof JsonArray array) {
            for (JsonElement element : array) {
                if (!(element instanceof JsonObject object)) {
                    throw new IllegalArgumentException("'" + key + "' holds something that is not an object");
                }
                objects.add(object);
            }
        }
        return objects;
    }

    private static String string(JsonObject json, String key, String fallback) {
        return json.get(key) instanceof JsonElement element && element.isJsonPrimitive() ? element.getAsString() : fallback;
    }

    private static int integer(JsonObject json, String key, int fallback) {
        try {
            return json.get(key) instanceof JsonElement element && element.isJsonPrimitive() ? element.getAsInt() : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
