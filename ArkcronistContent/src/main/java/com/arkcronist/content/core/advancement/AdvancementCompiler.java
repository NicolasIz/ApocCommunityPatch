package com.arkcronist.content.core.advancement;

import com.arkcronist.content.core.definition.AdvancementDefinition;
import com.arkcronist.content.core.definition.AdvancementDefinition.Trigger;
import com.arkcronist.content.core.definition.Equipment;
import com.arkcronist.content.core.definition.ItemDefinition;
import com.arkcronist.content.core.definition.ResourceLocation;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Advancement definitions into the JSON the server reads for an advancement - the same format as
 * {@code data/minecraft/advancement/} in the 1.21.8 server jar - after checking what can only be
 * checked with every file read: that each parent exists and no chain of parents loops, that icons
 * and worn items are real items, and that a set to wear fits on one body.
 *
 * <p>What completes each one is a criterion the server already knows: joining is vanilla's tick
 * trigger, holding an item is vanilla's inventory_changed matched on the item's model, and wearing
 * a set - which vanilla has no trigger for - is an impossible criterion the plugin awards itself.</p>
 *
 * <p>No Bukkit here: titles arrive as MiniMessage and leave through {@code text}, which the plugin
 * points at Adventure.</p>
 */
public final class AdvancementCompiler {

    /** A root tab's background when its YAML names none: vanilla's stone tab. */
    public static final ResourceLocation DEFAULT_BACKGROUND =
            new ResourceLocation("minecraft", "gui/advancements/backgrounds/stone");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** One advancement ready to register: its definition and its JSON. */
    public record Compiled(AdvancementDefinition definition, String json) {
        public ResourceLocation key() {
            return definition.key();
        }
    }

    /**
     * @param advancements every advancement that can be registered, each after its parent
     * @param problems     what was dropped or patched, and why
     */
    public record Result(List<Compiled> advancements, List<String> problems) {
        public Result {
            advancements = List.copyOf(advancements);
            problems = List.copyOf(problems);
        }
    }

    private AdvancementCompiler() {
    }

    /**
     * @param items by full id ({@code demo:ruby})
     * @param text  MiniMessage to a text component
     */
    public static Result compile(Collection<AdvancementDefinition> definitions, Map<String, ItemDefinition> items,
                                 Function<String, JsonElement> text) {
        List<String> problems = new ArrayList<>();
        Map<ResourceLocation, AdvancementDefinition> byKey = new LinkedHashMap<>();
        for (AdvancementDefinition definition : definitions) {
            byKey.put(definition.key(), definition);
        }

        // Each advancement whose chain of parents reaches a root - its own, or a vanilla tab.
        Map<ResourceLocation, Integer> depth = new LinkedHashMap<>();
        for (AdvancementDefinition definition : definitions) {
            depth(definition, byKey, depth, new ArrayList<>(), problems);
        }

        List<Compiled> compiled = new ArrayList<>();
        definitions.stream()
                .filter(definition -> depth.get(definition.key()) != FAILED)
                .sorted(Comparator.comparingInt((AdvancementDefinition definition) -> depth.get(definition.key())))
                .forEach(definition -> {
                    JsonObject json = json(definition, items, text, problems);
                    if (json != null) {
                        compiled.add(new Compiled(definition, GSON.toJson(json) + "\n"));
                    }
                });
        // A child whose parent was dropped above would be registered with a missing parent.
        Set<ResourceLocation> kept = new HashSet<>();
        List<Compiled> ordered = new ArrayList<>();
        for (Compiled advancement : compiled) {
            ResourceLocation parent = advancement.definition().parent();
            if (parent != null && !ResourceLocation.MINECRAFT.equals(parent.namespace()) && !kept.contains(parent)) {
                problems.add(where(advancement.definition()) + "its parent " + parent + " could not be registered,"
                        + " so neither can it");
                continue;
            }
            kept.add(advancement.key());
            ordered.add(advancement);
        }
        return new Result(ordered, problems);
    }

    /** Marks an advancement that cannot be registered, in the depth map. */
    private static final int FAILED = -1;

    /**
     * How many parents up to a root, or {@link #FAILED}: a parent is missing, or the chain loops,
     * or something above failed. Each failure is reported once, against the advancement it is in.
     */
    private static int depth(AdvancementDefinition definition, Map<ResourceLocation, AdvancementDefinition> byKey,
                             Map<ResourceLocation, Integer> memo, List<ResourceLocation> path, List<String> problems) {
        ResourceLocation key = definition.key();
        Integer known = memo.get(key);
        if (known != null) {
            return known;
        }
        int index = path.indexOf(key);
        if (index >= 0) {
            // A loop: everything on the way from here back to here.
            for (ResourceLocation member : path.subList(index, path.size())) {
                if (memo.putIfAbsent(member, FAILED) == null) {
                    problems.add("advancement " + member + ": its parents loop back to it - not registered");
                }
            }
            return FAILED;
        }
        ResourceLocation parent = definition.parent();
        int result;
        if (parent == null) {
            result = 0;
        } else if (ResourceLocation.MINECRAFT.equals(parent.namespace())) {
            // Vanilla's tabs are the server's: an advancement can hang under minecraft:story/root.
            result = 1;
        } else if (!byKey.containsKey(parent)) {
            problems.add(where(definition) + "parent " + parent + " is not defined - not registered");
            result = FAILED;
        } else {
            path.add(key);
            int up = depth(byKey.get(parent), byKey, memo, path, problems);
            path.remove(path.size() - 1);
            Integer marked = memo.get(key);
            if (marked != null) {
                return marked;
            }
            if (up == FAILED) {
                problems.add(where(definition) + "its parent " + parent + " could not be registered, so neither can it");
                result = FAILED;
            } else {
                result = up + 1;
            }
        }
        memo.put(key, result);
        return result;
    }

    private static JsonObject json(AdvancementDefinition definition, Map<String, ItemDefinition> items,
                                   Function<String, JsonElement> text, List<String> problems) {
        JsonObject criteria = new JsonObject();
        String criterion;
        Trigger trigger = definition.trigger();
        switch (trigger) {
            case Trigger.Join join -> {
                criterion = "joined";
                criteria.add(criterion, criterion("minecraft:tick", null));
            }
            case Trigger.Manual manual -> {
                criterion = "granted";
                criteria.add(criterion, criterion("minecraft:impossible", null));
            }
            case Trigger.Wear wear -> {
                if (!wearable(definition, wear, items, problems)) {
                    return null;
                }
                criterion = "worn";
                criteria.add(criterion, criterion("minecraft:impossible", null));
            }
            case Trigger.Obtain obtain -> {
                JsonObject predicate = itemPredicate(definition, obtain.item(), items, problems);
                if (predicate == null) {
                    return null;
                }
                JsonArray predicates = new JsonArray();
                predicates.add(predicate);
                JsonObject conditions = new JsonObject();
                conditions.add("items", predicates);
                criterion = "obtained";
                criteria.add(criterion, criterion("minecraft:inventory_changed", conditions));
            }
        }

        JsonObject display = new JsonObject();
        display.add("icon", icon(definition, items, problems));
        display.add("title", text.apply(definition.title()));
        display.add("description", text.apply(definition.description()));
        display.addProperty("frame", definition.frame().vanillaName());
        if (definition.parent() == null) {
            ResourceLocation background = definition.background() == null ? DEFAULT_BACKGROUND : definition.background();
            display.addProperty("background", background.toString());
        }
        display.addProperty("show_toast", definition.toast());
        // A line of our own replaces vanilla's; a tab everyone gets on joining announces nothing.
        display.addProperty("announce_to_chat", definition.announce() == null && !(trigger instanceof Trigger.Join));
        display.addProperty("hidden", definition.hidden());

        JsonObject json = new JsonObject();
        if (definition.parent() != null) {
            json.addProperty("parent", definition.parent().toString());
        }
        json.add("display", display);
        json.add("criteria", criteria);
        JsonArray requirement = new JsonArray();
        requirement.add(criterion);
        JsonArray requirements = new JsonArray();
        requirements.add(requirement);
        json.add("requirements", requirements);
        if (definition.experience() > 0) {
            JsonObject rewards = new JsonObject();
            rewards.addProperty("experience", definition.experience());
            json.add("rewards", rewards);
        }
        json.addProperty("sends_telemetry_event", false);
        return json;
    }

    private static JsonObject criterion(String trigger, JsonObject conditions) {
        JsonObject criterion = new JsonObject();
        criterion.addProperty("trigger", trigger);
        if (conditions != null) {
            criterion.add("conditions", conditions);
        }
        return criterion;
    }

    /** Every item worn, and no two on the same part of the body. */
    private static boolean wearable(AdvancementDefinition definition, Trigger.Wear wear, Map<String, ItemDefinition> items,
                                    List<String> problems) {
        Set<Equipment.Slot> slots = EnumSet.noneOf(Equipment.Slot.class);
        for (ResourceLocation id : wear.items()) {
            ItemDefinition item = items.get(id.toString());
            if (item == null) {
                problems.add(where(definition) + "'trigger.wear' names " + id + ", which is not one of the plugin's"
                        + " items - not registered");
                return false;
            }
            if (item.equipment() == null) {
                problems.add(where(definition) + id + " has no 'equipment' section, so it cannot be worn - not"
                        + " registered");
                return false;
            }
            if (!slots.add(item.equipment().slot())) {
                problems.add(where(definition) + "two of the items to wear go on " + item.equipment().slot()
                        + " - nobody can wear both, not registered");
                return false;
            }
        }
        return true;
    }

    private static JsonObject itemPredicate(AdvancementDefinition definition, ResourceLocation id,
                                            Map<String, ItemDefinition> items, List<String> problems) {
        JsonObject predicate = new JsonObject();
        if (ResourceLocation.MINECRAFT.equals(id.namespace())) {
            predicate.addProperty("items", id.toString());
            return predicate;
        }
        ItemDefinition item = items.get(id.toString());
        if (item == null) {
            problems.add(where(definition) + "'trigger.obtain' names " + id + ", which is not one of the plugin's"
                    + " items - not registered");
            return null;
        }
        // The model is what tells the plugin's item from the plain material it is made of.
        predicate.addProperty("items", vanillaId(item.material()));
        JsonObject components = new JsonObject();
        components.addProperty("minecraft:item_model", item.itemModel().toString());
        predicate.add("components", components);
        return predicate;
    }

    /** The icon: a vanilla item as it is, one of the plugin's items as its material drawn with its model. */
    private static JsonObject icon(AdvancementDefinition definition, Map<String, ItemDefinition> items,
                                   List<String> problems) {
        JsonObject icon = new JsonObject();
        ResourceLocation id = definition.icon();
        icon.addProperty("count", 1);
        if (ResourceLocation.MINECRAFT.equals(id.namespace())) {
            icon.addProperty("id", id.toString());
            return icon;
        }
        ItemDefinition item = items.get(id.toString());
        if (item == null) {
            problems.add(where(definition) + "'icon' " + id + " is not one of the plugin's items; showing paper");
            icon.addProperty("id", "minecraft:paper");
            return icon;
        }
        icon.addProperty("id", vanillaId(item.material()));
        JsonObject components = new JsonObject();
        components.addProperty("minecraft:item_model", item.itemModel().toString());
        icon.add("components", components);
        return icon;
    }

    /** A Bukkit material name as the item id the game knows it by. */
    static String vanillaId(String material) {
        return "minecraft:" + material.toLowerCase(Locale.ROOT);
    }

    private static String where(AdvancementDefinition definition) {
        return "advancement " + definition.fullId() + ": ";
    }
}
