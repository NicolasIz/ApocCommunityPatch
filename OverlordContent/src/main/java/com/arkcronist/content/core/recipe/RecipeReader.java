package com.arkcronist.content.core.recipe;

import com.arkcronist.content.core.recipe.RecipeDefinition.Cooking;
import com.arkcronist.content.core.recipe.RecipeDefinition.Ingredient;
import com.arkcronist.content.core.recipe.RecipeDefinition.ItemRef;
import com.arkcronist.content.core.recipe.RecipeDefinition.Shape;
import com.arkcronist.content.core.recipe.RecipeDefinition.Shaped;
import com.arkcronist.content.core.recipe.RecipeDefinition.Shapeless;
import com.arkcronist.content.core.recipe.RecipeDefinition.Smithing;
import com.arkcronist.content.core.recipe.RecipeDefinition.Station;
import com.arkcronist.content.core.recipe.RecipeDefinition.Stonecutting;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 4.0: the recipes in a content file, under {@code recipes:}.
 *
 * <pre>
 * recipes:
 *   ruby_sword:
 *     type: shaped                 # shaped, shapeless, furnace, blasting, smoking, campfire,
 *     pattern:                     #   stonecutting or smithing
 *       - " R "
 *       - " R "
 *       - " S "
 *     ingredients:
 *       R: demo:ruby               # a custom item: namespace:id
 *       S: STICK                   # a vanilla item, or minecraft:stick
 *     result: demo:ruby_sword
 *   ruby_from_block:
 *     type: shapeless
 *     ingredients: [demo:ruby_block]
 *     result: demo:ruby 9          # or result: demo:ruby + amount: 9
 *   smelt_star_ore:
 *     type: blasting
 *     input: demo:star_ore
 *     result: demo:ruby
 *     experience: 0.7
 *     cook-seconds: 5
 *   planks_any:
 *     type: shapeless
 *     ingredients: ["#minecraft:planks", [demo:ruby, DIAMOND]]   # a tag; either of two
 * </pre>
 *
 * <p>Every recipe is also in every player's recipe book from the start ({@code recipe-book: false}
 * leaves it out); {@code permission:} keeps it to those who have it; {@code group:} shares one book
 * entry; {@code category:} picks the book's tab.</p>
 */
public final class RecipeReader {

    private static final Pattern WITH_AMOUNT = Pattern.compile("(\\S+)\\s+(\\d+)");
    private static final Pattern NAMESPACED = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern MATERIAL = Pattern.compile("[A-Za-z0-9_]+");
    private static final Set<String> CRAFTING_CATEGORIES = Set.of("building", "redstone", "equipment", "misc");
    private static final Set<String> COOKING_CATEGORIES = Set.of("food", "blocks", "misc");

    private RecipeReader() {
    }

    /** Every recipe under {@code node}; one that cannot be read is reported and left out. */
    public static List<RecipeDefinition> read(@Nullable Object node, String namespace, String where,
                                              List<String> problems) {
        List<RecipeDefinition> recipes = new ArrayList<>();
        if (node == null) {
            return recipes;
        }
        if (!(node instanceof Map<?, ?> section)) {
            problems.add(where + ": 'recipes' should be a section of recipe ids");
            return recipes;
        }
        for (Map.Entry<?, ?> entry : section.entrySet()) {
            String id = String.valueOf(entry.getKey());
            String prefix = where + " > recipe " + namespace + ":" + id + ": ";
            if (!RecipeDefinition.ID.matcher(id).matches()) {
                problems.add(prefix + "invalid id (allowed: a-z 0-9 _ . -)");
                continue;
            }
            if (!(entry.getValue() instanceof Map<?, ?> recipe)) {
                problems.add(prefix + "should be a section with at least 'type' and 'result'");
                continue;
            }
            RecipeDefinition read = recipe(namespace + ":" + id, recipe, where, prefix, problems);
            if (read != null) {
                recipes.add(read);
            }
        }
        return recipes;
    }

    /**
     * The recipes whose custom items all exist; each one that names an item no file defines is
     * reported and left out, as a recipe that could never be made, or makes nothing.
     */
    public static List<RecipeDefinition> withKnownItems(Collection<RecipeDefinition> recipes, Set<String> customItems,
                                                        List<String> problems) {
        List<RecipeDefinition> known = new ArrayList<>();
        for (RecipeDefinition recipe : recipes) {
            List<String> missing = recipe.customItems().stream().filter(id -> !customItems.contains(id)).toList();
            if (missing.isEmpty()) {
                known.add(recipe);
            } else {
                problems.add(recipe.where() + " > recipe " + recipe.fullId() + ": no item " + String.join(", ", missing)
                        + " is loaded - the recipe is left out");
            }
        }
        return known;
    }

    private static @Nullable RecipeDefinition recipe(String fullId, Map<?, ?> section, String where, String prefix,
                                                     List<String> problems) {
        Object type = section.get("type");
        if (type == null) {
            problems.add(prefix + "missing 'type' (shaped, shapeless, furnace, blasting, smoking, campfire,"
                    + " stonecutting or smithing)");
            return null;
        }
        Object resultNode = section.get("result");
        if (resultNode == null) {
            problems.add(prefix + "missing 'result'");
            return null;
        }
        String resultText = String.valueOf(resultNode).trim();
        int amount = 1;
        Matcher withAmount = WITH_AMOUNT.matcher(resultText);
        if (withAmount.matches()) {
            resultText = withAmount.group(1);
            amount = Integer.parseInt(withAmount.group(2));
        }
        Object amountNode = section.get("amount");
        if (amountNode != null) {
            if (!(amountNode instanceof Integer number)) {
                problems.add(prefix + "'amount' should be a whole number from 1 to 99");
                return null;
            }
            amount = number;
        }
        if (amount < 1 || amount > 99) {
            problems.add(prefix + "the result amount should be from 1 to 99");
            return null;
        }
        ItemRef result = ref(resultText, prefix + "result: ", problems);
        if (result == null) {
            return null;
        }
        if (result.kind() == ItemRef.Kind.TAG) {
            problems.add(prefix + "the result is one item, not a tag");
            return null;
        }

        Shape shape = switch (String.valueOf(type).trim().toLowerCase(Locale.ROOT)) {
            case "shaped" -> shaped(section, prefix, problems);
            case "shapeless" -> shapeless(section, prefix, problems);
            case "furnace" -> cooking(Station.FURNACE, section, prefix, problems);
            case "blasting", "blast_furnace" -> cooking(Station.BLASTING, section, prefix, problems);
            case "smoking", "smoker" -> cooking(Station.SMOKING, section, prefix, problems);
            case "campfire" -> cooking(Station.CAMPFIRE, section, prefix, problems);
            case "stonecutting", "stonecutter" -> {
                Ingredient input = ingredient(section.get("input"), prefix + "input: ", problems);
                yield input == null ? null : new Stonecutting(input);
            }
            case "smithing" -> smithing(section, prefix, problems);
            default -> {
                problems.add(prefix + "unknown type '" + type + "' (shaped, shapeless, furnace, blasting, smoking,"
                        + " campfire, stonecutting or smithing)");
                yield null;
            }
        };
        if (shape == null) {
            return null;
        }
        Object group = section.get("group");
        Object permission = section.get("permission");
        boolean recipeBook = !Boolean.FALSE.equals(section.get("recipe-book"));
        return new RecipeDefinition(fullId, result, amount, group == null ? null : String.valueOf(group).trim(),
                recipeBook, permission == null ? null : String.valueOf(permission).trim(), shape, where);
    }

    private static @Nullable Shape shaped(Map<?, ?> section, String prefix, List<String> problems) {
        if (!(section.get("pattern") instanceof List<?> rows) || rows.isEmpty() || rows.size() > 3) {
            problems.add(prefix + "'pattern' should be a list of one to three rows, like \" R \"");
            return null;
        }
        List<String> pattern = new ArrayList<>();
        for (Object row : rows) {
            pattern.add(String.valueOf(row));
        }
        int width = pattern.getFirst().length();
        if (width < 1 || width > 3 || pattern.stream().anyMatch(row -> row.length() != width)) {
            problems.add(prefix + "every row of 'pattern' should have the same length, one to three characters"
                    + " (a space for an empty slot)");
            return null;
        }
        if (pattern.stream().allMatch(row -> row.isBlank())) {
            problems.add(prefix + "'pattern' is all empty slots");
            return null;
        }
        if (!(section.get("ingredients") instanceof Map<?, ?> keys)) {
            problems.add(prefix + "'ingredients' should give each character of the pattern an item, like R: demo:ruby");
            return null;
        }
        Map<Character, Ingredient> key = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : keys.entrySet()) {
            String symbol = String.valueOf(entry.getKey());
            if (symbol.length() != 1 || symbol.equals(" ")) {
                problems.add(prefix + "'ingredients' key '" + symbol + "' should be one character, not a space");
                return null;
            }
            Ingredient ingredient = ingredient(entry.getValue(), prefix + "ingredient " + symbol + ": ", problems);
            if (ingredient == null) {
                return null;
            }
            key.put(symbol.charAt(0), ingredient);
        }
        for (String row : pattern) {
            for (char c : row.toCharArray()) {
                if (c != ' ' && !key.containsKey(c)) {
                    problems.add(prefix + "the pattern uses '" + c + "', which 'ingredients' does not name");
                    return null;
                }
            }
        }
        for (Character symbol : key.keySet()) {
            if (pattern.stream().noneMatch(row -> row.indexOf(symbol) >= 0)) {
                problems.add(prefix + "ingredient '" + symbol + "' is not in the pattern");
            }
        }
        String category = category(section, CRAFTING_CATEGORIES, prefix, problems);
        return new Shaped(pattern, key, category);
    }

    private static @Nullable Shape shapeless(Map<?, ?> section, String prefix, List<String> problems) {
        if (!(section.get("ingredients") instanceof List<?> list) || list.isEmpty()) {
            problems.add(prefix + "'ingredients' should be a list of one to nine items");
            return null;
        }
        List<Ingredient> ingredients = new ArrayList<>();
        for (Object entry : list) {
            // "demo:ruby 3" is three of them; a list is any one of its items.
            int times = 1;
            Object value = entry;
            if (entry instanceof String text) {
                Matcher withAmount = WITH_AMOUNT.matcher(text.trim());
                if (withAmount.matches()) {
                    value = withAmount.group(1);
                    times = Integer.parseInt(withAmount.group(2));
                }
            }
            Ingredient ingredient = ingredient(value, prefix + "ingredient " + (ingredients.size() + 1) + ": ", problems);
            if (ingredient == null) {
                return null;
            }
            for (int i = 0; i < times; i++) {
                ingredients.add(ingredient);
            }
        }
        if (ingredients.size() > 9) {
            problems.add(prefix + "a crafting grid holds nine ingredients, not " + ingredients.size());
            return null;
        }
        return new Shapeless(ingredients, category(section, CRAFTING_CATEGORIES, prefix, problems));
    }

    private static @Nullable Shape cooking(Station station, Map<?, ?> section, String prefix, List<String> problems) {
        Ingredient input = ingredient(section.get("input"), prefix + "input: ", problems);
        if (input == null) {
            return null;
        }
        double experience = 0.1;
        Object xp = section.get("experience");
        if (xp != null) {
            if (!(xp instanceof Number number) || number.doubleValue() < 0 || number.doubleValue() > 1000) {
                problems.add(prefix + "'experience' should be a number from 0 to 1000; using 0.1");
            } else {
                experience = number.doubleValue();
            }
        }
        int ticks = station.defaultTicks();
        Object seconds = section.get("cook-seconds");
        if (seconds != null) {
            if (!(seconds instanceof Number number) || number.doubleValue() < 0.05 || number.doubleValue() > 3600) {
                problems.add(prefix + "'cook-seconds' should be a number from 0.05 to 3600; using "
                        + station.defaultTicks() / 20);
            } else {
                ticks = (int) Math.max(1, Math.round(number.doubleValue() * 20));
            }
        }
        return new Cooking(station, input, experience, ticks, category(section, COOKING_CATEGORIES, prefix, problems));
    }

    private static @Nullable Shape smithing(Map<?, ?> section, String prefix, List<String> problems) {
        Ingredient template = ingredient(section.get("template"), prefix + "template: ", problems);
        Ingredient base = ingredient(section.get("base"), prefix + "base: ", problems);
        Ingredient addition = ingredient(section.get("addition"), prefix + "addition: ", problems);
        if (template == null || base == null || addition == null) {
            return null;
        }
        return new Smithing(template, base, addition, !Boolean.FALSE.equals(section.get("keep-components")));
    }

    private static @Nullable String category(Map<?, ?> section, Set<String> allowed, String prefix,
                                             List<String> problems) {
        Object category = section.get("category");
        if (category == null) {
            return null;
        }
        String name = String.valueOf(category).trim().toLowerCase(Locale.ROOT);
        if (!allowed.contains(name)) {
            problems.add(prefix + "'category' should be one of " + String.join(", ", allowed.stream().sorted().toList())
                    + "; the recipe book's own is used");
            return null;
        }
        return name;
    }

    /** One slot: an item, or a list of items any of which fits. */
    private static @Nullable Ingredient ingredient(@Nullable Object node, String prefix, List<String> problems) {
        if (node == null) {
            problems.add(prefix + "missing");
            return null;
        }
        List<ItemRef> options = new ArrayList<>();
        for (Object value : node instanceof List<?> list ? list : List.of(node)) {
            ItemRef ref = ref(String.valueOf(value).trim(), prefix, problems);
            if (ref == null) {
                return null;
            }
            options.add(ref);
        }
        if (options.isEmpty()) {
            problems.add(prefix + "an empty list fits nothing");
            return null;
        }
        return new Ingredient(options);
    }

    /**
     * {@code demo:ruby} is a custom item, {@code DIAMOND} or {@code minecraft:diamond} a vanilla
     * one, {@code #minecraft:planks} (or {@code #planks}) a vanilla item tag.
     */
    static @Nullable ItemRef ref(String text, String prefix, List<String> problems) {
        if (text.startsWith("#")) {
            String tag = text.substring(1).toLowerCase(Locale.ROOT);
            if (!tag.contains(":")) {
                tag = "minecraft:" + tag;
            }
            if (!NAMESPACED.matcher(tag).matches()) {
                problems.add(prefix + "'" + text + "' is not a tag name like #minecraft:planks");
                return null;
            }
            return new ItemRef(ItemRef.Kind.TAG, tag);
        }
        if (text.contains(":")) {
            String id = text.toLowerCase(Locale.ROOT);
            if (!NAMESPACED.matcher(id).matches()) {
                problems.add(prefix + "'" + text + "' is not an item id like demo:ruby or minecraft:stick");
                return null;
            }
            return new ItemRef(id.startsWith("minecraft:") ? ItemRef.Kind.VANILLA : ItemRef.Kind.CUSTOM, id);
        }
        if (!MATERIAL.matcher(text).matches()) {
            problems.add(prefix + "'" + text + "' is not an item: write demo:ruby for a custom item, STICK or"
                    + " minecraft:stick for a vanilla one");
            return null;
        }
        return new ItemRef(ItemRef.Kind.VANILLA, "minecraft:" + text.toLowerCase(Locale.ROOT));
    }
}
