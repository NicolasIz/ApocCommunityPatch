package com.arkcronist.content.bukkit.furniture;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.SmokingRecipe;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 3.1: what a forge, a fire or an oven does with what a player holds out to it: one of it cooked by
 * the server's own recipes of a furnace, a blast furnace, a smoker or a campfire - datapack ones
 * included - with the experience a furnace would give. Main thread.
 */
final class Cooking {

    private Cooking() {
    }

    /** The recipe of that kind ({@code furnace}, {@code blast}, {@code smoker}, {@code campfire}) that cooks {@code held}. */
    static @Nullable CookingRecipe<?> recipe(ItemStack held, String kind) {
        Iterator<Recipe> recipes = Bukkit.recipeIterator();
        while (recipes.hasNext()) {
            if (!(recipes.next() instanceof CookingRecipe<?> cooking)) {
                continue;
            }
            boolean right = switch (kind) {
                case "furnace" -> cooking instanceof FurnaceRecipe;
                case "blast" -> cooking instanceof BlastingRecipe;
                case "smoker" -> cooking instanceof SmokingRecipe;
                case "campfire" -> cooking instanceof CampfireRecipe;
                default -> false;
            };
            if (right && cooking.getInputChoice().test(held)) {
                return cooking;
            }
        }
        return null;
    }

    /**
     * Cooks one of what {@code player} holds in their main hand, if a recipe of that kind takes it:
     * one less of it, its result in their inventory (or at their feet when it is full) and the
     * recipe's experience.
     *
     * @return the result, or null when nothing was cooked
     */
    static @Nullable ItemStack cook(Player player, String kind) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            return null;
        }
        CookingRecipe<?> recipe = recipe(held, kind);
        if (recipe == null) {
            return null;
        }
        ItemStack result = recipe.getResult().clone();
        held.setAmount(held.getAmount() - 1);
        player.getInventory().setItemInMainHand(held.getAmount() > 0 ? held : null);
        player.getInventory().addItem(result.clone()).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        float experience = recipe.getExperience();
        int whole = (int) Math.floor(experience);
        if (ThreadLocalRandom.current().nextFloat() < experience - whole) {
            whole++;
        }
        if (whole > 0) {
            player.giveExp(whole);
        }
        return result;
    }
}
