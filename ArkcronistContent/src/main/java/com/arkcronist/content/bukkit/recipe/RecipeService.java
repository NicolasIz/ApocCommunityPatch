package com.arkcronist.content.bukkit.recipe;

import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemFactory;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import com.arkcronist.content.core.recipe.RecipeDefinition;
import com.arkcronist.content.core.recipe.RecipeDefinition.Cooking;
import com.arkcronist.content.core.recipe.RecipeDefinition.Ingredient;
import com.arkcronist.content.core.recipe.RecipeDefinition.ItemRef;
import com.arkcronist.content.core.recipe.RecipeDefinition.Shaped;
import com.arkcronist.content.core.recipe.RecipeDefinition.Shapeless;
import com.arkcronist.content.core.recipe.RecipeDefinition.Smithing;
import com.arkcronist.content.core.recipe.RecipeDefinition.Stonecutting;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.SmokingRecipe;
import org.bukkit.inventory.StonecuttingRecipe;
import org.bukkit.inventory.recipe.CookingBookCategory;
import org.bukkit.inventory.recipe.CraftingBookCategory;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * 4.0: the {@code recipes:} of the content files, on the server - in its crafting tables,
 * furnaces, stonecutters and smithing tables, and in the players' recipe books.
 *
 * <p>Each recipe is the server's own, under the key {@code arkcronistcontent:<namespace>/<id>}: the
 * client's recipe book lists it, places it in the grid, and the server matches and crafts it as it
 * does vanilla's. A custom ingredient is matched as the stack the plugin makes - so a stack whose
 * name or lore the YAML changed since, or that carries data of its own (a loaded gun, a backpack's
 * contents), is not that ingredient any more.</p>
 *
 * <p>Every rebuild takes the previous set off and puts the new one on, once, then sends players the
 * new book. A reload of the server's data ({@code /minecraft:reload}, or the plugin's own after an
 * advancement changed) clears recipes added at run time; {@link #reregister()} puts them back.</p>
 *
 * <p>Main thread only.</p>
 */
public final class RecipeService {

    private final Plugin plugin;
    private final ItemRegistry items;
    private final ItemFactory factory;
    private final Logger logger;

    private List<RecipeDefinition> desired = List.of();
    /** What is registered now, by key. */
    private final Map<NamespacedKey, RecipeDefinition> registered = new LinkedHashMap<>();

    public RecipeService(Plugin plugin, ItemRegistry items, ItemFactory factory) {
        this.plugin = plugin;
        this.items = items;
        this.factory = factory;
        this.logger = plugin.getLogger();
    }

    /**
     * Replaces the plugin's recipes with these, sends every online player the new recipe book and
     * shows them the ones they are allowed to see.
     *
     * @param problems where a recipe this server cannot make - an unknown vanilla item or tag, a
     *                 result that does not stack that high - is reported
     * @return how many are registered
     */
    public int publish(List<RecipeDefinition> recipes, List<String> problems) {
        this.desired = List.copyOf(recipes);
        register(problems);
        return registered.size();
    }

    /** After the server's data was reloaded, which dropped the recipes added at run time. */
    public void reregister() {
        List<String> problems = new ArrayList<>();
        register(problems);
        problems.forEach(logger::warning);
    }

    private void register(List<String> problems) {
        for (NamespacedKey key : registered.keySet()) {
            Bukkit.removeRecipe(key, false);
        }
        registered.clear();
        for (RecipeDefinition definition : desired) {
            String prefix = definition.where() + " > recipe " + definition.fullId() + ": ";
            NamespacedKey key = key(definition);
            Recipe recipe;
            try {
                recipe = build(key, definition, prefix, problems);
            } catch (IllegalArgumentException exception) {
                problems.add(prefix + "the server refused it - " + exception.getMessage());
                continue;
            }
            if (recipe == null) {
                continue;
            }
            if (Bukkit.addRecipe(recipe, false)) {
                registered.put(key, definition);
            } else {
                problems.add(prefix + "the server already has a recipe " + key);
            }
        }
        // One resend for the lot, rather than one per recipe.
        Bukkit.updateRecipes();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            showTo(player);
        }
    }

    /** Puts every recipe this player may make, and that is meant to be in the book, in their book. */
    public void showTo(Player player) {
        List<NamespacedKey> keys = new ArrayList<>();
        registered.forEach((key, definition) -> {
            if (definition.recipeBook() && mayCraft(player, definition)) {
                keys.add(key);
            }
        });
        if (!keys.isEmpty()) {
            player.discoverRecipes(keys);
        }
    }

    /** The plugin's recipe registered under this key, if it is one. */
    public Optional<RecipeDefinition> definition(@Nullable NamespacedKey key) {
        return key == null ? Optional.empty() : Optional.ofNullable(registered.get(key));
    }

    public boolean mayCraft(Player player, RecipeDefinition definition) {
        return definition.permission() == null || player.hasPermission(definition.permission());
    }

    public int registeredCount() {
        return registered.size();
    }

    public boolean isOwn(@Nullable NamespacedKey key) {
        return key != null && key.getNamespace().equals(plugin.getName().toLowerCase(Locale.ROOT));
    }

    private NamespacedKey key(RecipeDefinition definition) {
        return new NamespacedKey(plugin, definition.namespace() + "/" + definition.id());
    }

    private @Nullable Recipe build(NamespacedKey key, RecipeDefinition definition, String prefix,
                                   List<String> problems) {
        ItemStack result = stack(definition.result(), definition.amount(), prefix + "result: ", problems);
        if (result == null) {
            return null;
        }
        String group = definition.group() == null ? "" : definition.group();
        switch (definition.shape()) {
            case Shaped shaped -> {
                ShapedRecipe recipe = new ShapedRecipe(key, result);
                recipe.shape(shaped.pattern().toArray(String[]::new));
                for (Map.Entry<Character, Ingredient> entry : shaped.key().entrySet()) {
                    RecipeChoice choice = choice(entry.getValue(), false, prefix + "ingredient " + entry.getKey() + ": ", problems);
                    if (choice == null) {
                        return null;
                    }
                    recipe.setIngredient(entry.getKey(), choice);
                }
                recipe.setGroup(group);
                recipe.setCategory(craftingCategory(shaped.category()));
                return recipe;
            }
            case Shapeless shapeless -> {
                ShapelessRecipe recipe = new ShapelessRecipe(key, result);
                // Paper does not match a shapeless recipe whose vanilla ingredient, by material, would
                // also take its custom one (paper and a ruby made of paper): with a custom item among
                // them, every ingredient is matched as an exact stack - plain paper, not renamed.
                boolean exact = shapeless.customSlots() > 0;
                int n = 0;
                for (Ingredient ingredient : shapeless.ingredients()) {
                    RecipeChoice choice = choice(ingredient, exact, prefix + "ingredient " + (++n) + ": ", problems);
                    if (choice == null) {
                        return null;
                    }
                    recipe.addIngredient(choice);
                }
                recipe.setGroup(group);
                recipe.setCategory(craftingCategory(shapeless.category()));
                return recipe;
            }
            case Cooking cooking -> {
                RecipeChoice input = choice(cooking.input(), false, prefix + "input: ", problems);
                if (input == null) {
                    return null;
                }
                float experience = (float) cooking.experience();
                CookingRecipe<?> recipe = switch (cooking.station()) {
                    case FURNACE -> new FurnaceRecipe(key, result, input, experience, cooking.ticks());
                    case BLASTING -> new BlastingRecipe(key, result, input, experience, cooking.ticks());
                    case SMOKING -> new SmokingRecipe(key, result, input, experience, cooking.ticks());
                    case CAMPFIRE -> new CampfireRecipe(key, result, input, experience, cooking.ticks());
                };
                recipe.setGroup(group);
                if (cooking.category() != null) {
                    recipe.setCategory(CookingBookCategory.valueOf(cooking.category().toUpperCase(Locale.ROOT)));
                }
                return recipe;
            }
            case Stonecutting stonecutting -> {
                RecipeChoice input = choice(stonecutting.input(), false, prefix + "input: ", problems);
                if (input == null) {
                    return null;
                }
                StonecuttingRecipe recipe = new StonecuttingRecipe(key, result, input);
                recipe.setGroup(group);
                return recipe;
            }
            case Smithing smithing -> {
                RecipeChoice template = choice(smithing.template(), false, prefix + "template: ", problems);
                RecipeChoice base = choice(smithing.base(), false, prefix + "base: ", problems);
                RecipeChoice addition = choice(smithing.addition(), false, prefix + "addition: ", problems);
                if (template == null || base == null || addition == null) {
                    return null;
                }
                return new SmithingTransformRecipe(key, result, template, base, addition, smithing.keepComponents());
            }
        }
    }

    private static CraftingBookCategory craftingCategory(@Nullable String category) {
        return category == null ? CraftingBookCategory.MISC
                : CraftingBookCategory.valueOf(category.toUpperCase(Locale.ROOT));
    }

    /**
     * One slot. Vanilla items and tags only: matched by material, as vanilla's recipes are. Any custom
     * item among them, or {@code exact}: matched as exact stacks, the vanilla ones as their plain stack.
     */
    private @Nullable RecipeChoice choice(Ingredient ingredient, boolean exact, String prefix, List<String> problems) {
        Set<Material> materials = new LinkedHashSet<>();
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemRef ref : ingredient.options()) {
            switch (ref.kind()) {
                case CUSTOM -> {
                    ItemStack stack = stack(ref, 1, prefix, problems);
                    if (stack == null) {
                        return null;
                    }
                    stacks.add(stack);
                }
                case VANILLA -> {
                    Material material = material(ref, prefix, problems);
                    if (material == null) {
                        return null;
                    }
                    materials.add(material);
                }
                case TAG -> {
                    NamespacedKey tagKey = NamespacedKey.fromString(ref.id());
                    Tag<Material> tag = tagKey == null ? null : Bukkit.getTag(Tag.REGISTRY_ITEMS, tagKey, Material.class);
                    if (tag == null || tag.getValues().isEmpty()) {
                        problems.add(prefix + "this server has no item tag #" + ref.id());
                        return null;
                    }
                    materials.addAll(tag.getValues());
                }
            }
        }
        if (stacks.isEmpty() && !exact) {
            return new RecipeChoice.MaterialChoice(new ArrayList<>(materials));
        }
        materials.forEach(material -> stacks.add(ItemStack.of(material)));
        return new RecipeChoice.ExactChoice(stacks);
    }

    private @Nullable ItemStack stack(ItemRef ref, int amount, String prefix, List<String> problems) {
        ItemStack stack;
        if (ref.kind() == ItemRef.Kind.CUSTOM) {
            Optional<CustomItem> item = items.get(ref.id());
            if (item.isEmpty()) {
                problems.add(prefix + "no item " + ref.id() + " is loaded");
                return null;
            }
            stack = factory.create(item.get(), 1);
        } else {
            Material material = material(ref, prefix, problems);
            if (material == null) {
                return null;
            }
            stack = ItemStack.of(material);
        }
        if (amount > stack.getMaxStackSize()) {
            problems.add(prefix + ref + " stacks to " + stack.getMaxStackSize() + ", not " + amount
                    + "; the recipe makes " + stack.getMaxStackSize());
            amount = stack.getMaxStackSize();
        }
        stack.setAmount(amount);
        return stack;
    }

    private static @Nullable Material material(ItemRef ref, String prefix, List<String> problems) {
        Material material = Material.matchMaterial(ref.id());
        if (material == null || material.isAir() || !material.isItem()) {
            problems.add(prefix + "'" + ref.id() + "' is not an item on this server");
            return null;
        }
        return material;
    }
}
