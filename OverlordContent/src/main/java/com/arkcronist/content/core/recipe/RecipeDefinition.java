package com.arkcronist.content.core.recipe;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 4.0: one recipe from a content file's {@code recipes:} section, as read - what goes in, what comes
 * out and at which station. Which items exist, and what a vanilla name means on this server, is the
 * server's to say when it is registered.
 *
 * @param fullId     {@code namespace:id}, unique among recipes
 * @param result     what it makes
 * @param amount     how many, 1 to 99 (no more than the result stacks to, checked when registered)
 * @param group      recipes of one group share one entry in the recipe book; null for none
 * @param recipeBook whether every player sees it in the recipe book from the start
 * @param permission who may make it; null for everyone
 * @param shape      the station and its inputs
 * @param where      the file it came from, for messages
 */
public record RecipeDefinition(String fullId, ItemRef result, int amount, @Nullable String group, boolean recipeBook,
                               @Nullable String permission, Shape shape, String where) {

    public static final Pattern ID = Pattern.compile("[a-z0-9_.-]+");

    public RecipeDefinition {
        if (amount < 1 || amount > 99) {
            throw new IllegalArgumentException("amount " + amount);
        }
    }

    public String namespace() {
        return fullId.substring(0, fullId.indexOf(':'));
    }

    public String id() {
        return fullId.substring(fullId.indexOf(':') + 1);
    }

    /** Every custom item the recipe names, the result included. */
    public List<String> customItems() {
        java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
        if (result.kind() == ItemRef.Kind.CUSTOM) {
            ids.add(result.id());
        }
        shape.ingredients().forEach(ingredient -> ingredient.options().stream()
                .filter(ref -> ref.kind() == ItemRef.Kind.CUSTOM).forEach(ref -> ids.add(ref.id())));
        return List.copyOf(ids);
    }

    /** What a recipe is made at, and from what. */
    public sealed interface Shape permits Shaped, Shapeless, Cooking, Stonecutting, Smithing {

        /** Each ingredient once per place it is used: a pattern's key as many times as it appears. */
        List<Ingredient> ingredients();

        /**
         * How many of the ingredients take a custom item - the most custom items a crafting grid may
         * hold for this recipe. More means one of them sits where only a vanilla item belongs: a
         * custom item made of paper is not paper.
         */
        default int customSlots() {
            return (int) ingredients().stream().filter(Ingredient::takesCustom).count();
        }
    }

    /**
     * A crafting table recipe with a pattern: up to three rows of up to three characters, all the
     * same length; a space is an empty slot, every other character a key in {@code key}.
     *
     * @param category the recipe book tab: building, redstone, equipment or misc; null for misc
     */
    public record Shaped(List<String> pattern, Map<Character, Ingredient> key, @Nullable String category)
            implements Shape {

        public Shaped {
            pattern = List.copyOf(pattern);
            key = Map.copyOf(key);
        }

        @Override
        public List<Ingredient> ingredients() {
            List<Ingredient> used = new java.util.ArrayList<>();
            for (String row : pattern) {
                for (char c : row.toCharArray()) {
                    if (c != ' ') {
                        used.add(key.get(c));
                    }
                }
            }
            return used;
        }
    }

    /** A crafting recipe in any order: one to nine ingredients. */
    public record Shapeless(List<Ingredient> ingredients, @Nullable String category) implements Shape {

        public Shapeless {
            ingredients = List.copyOf(ingredients);
        }
    }

    /** Cooked in a furnace, blast furnace, smoker or on a campfire. */
    public record Cooking(Station station, Ingredient input, double experience, int ticks, @Nullable String category)
            implements Shape {

        @Override
        public List<Ingredient> ingredients() {
            return List.of(input);
        }
    }

    /** Cut in a stonecutter. */
    public record Stonecutting(Ingredient input) implements Shape {

        @Override
        public List<Ingredient> ingredients() {
            return List.of(input);
        }
    }

    /**
     * Upgraded at a smithing table, as netherite upgrades diamond: the base becomes the result.
     *
     * @param keepComponents whether the result keeps the base's enchantments, name and damage
     */
    public record Smithing(Ingredient template, Ingredient base, Ingredient addition, boolean keepComponents)
            implements Shape {

        @Override
        public List<Ingredient> ingredients() {
            return List.of(template, base, addition);
        }
    }

    /** Where a {@link Cooking} recipe cooks, and how long vanilla takes there by default. */
    public enum Station {
        FURNACE(200), BLASTING(100), SMOKING(100), CAMPFIRE(600);

        private final int defaultTicks;

        Station(int defaultTicks) {
            this.defaultTicks = defaultTicks;
        }

        public int defaultTicks() {
            return defaultTicks;
        }
    }

    /** One slot of a recipe: any of these items. */
    public record Ingredient(List<ItemRef> options) {

        public Ingredient {
            if (options.isEmpty()) {
                throw new IllegalArgumentException("an ingredient needs at least one item");
            }
            options = List.copyOf(options);
        }

        /** A custom item fits this slot. */
        public boolean takesCustom() {
            return options.stream().anyMatch(ref -> ref.kind() == ItemRef.Kind.CUSTOM);
        }
    }

    /**
     * An item a recipe names: a custom item ({@code demo:ruby}), a vanilla one ({@code DIAMOND},
     * {@code minecraft:diamond}) or a vanilla item tag ({@code #minecraft:planks}).
     *
     * @param id {@code namespace:path}, lower case
     */
    public record ItemRef(Kind kind, String id) {

        public enum Kind { CUSTOM, VANILLA, TAG }

        @Override
        public String toString() {
            return (kind == Kind.TAG ? "#" : "") + id;
        }
    }
}
