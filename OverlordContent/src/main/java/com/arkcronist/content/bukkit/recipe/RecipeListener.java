package com.arkcronist.content.bukkit.recipe;

import com.arkcronist.content.bukkit.item.ItemFactory;
import com.arkcronist.content.core.recipe.RecipeDefinition;
import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockCookEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.SmithingInventory;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * 4.0: the plugin's recipes at the crafting table, the smithing table and in furnaces - and its
 * items kept out of vanilla's.
 *
 * <p>A custom item is made of a vanilla material - a ruby of paper, a sword of a diamond sword - and
 * to the game's recipes it is that material: a book from three rubies, a netherite sword from the
 * custom one. With {@code recipes.protect-custom-items} on (the default), a vanilla recipe
 * ({@code minecraft:} key) shows no result while a custom item is among its ingredients, and a
 * furnace or campfire does not cook one by a vanilla recipe. The plugin's own recipes take a custom
 * item only where they name one.</p>
 */
public final class RecipeListener implements Listener {

    private final RecipeService recipes;
    private final ItemFactory factory;
    private final boolean protect;

    public RecipeListener(RecipeService recipes, ItemFactory factory, boolean protect) {
        this.recipes = recipes;
        this.factory = factory;
        this.protect = protect;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        recipes.showTo(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDataReload(ServerResourcesReloadedEvent event) {
        recipes.reregister();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (!(event.getRecipe() instanceof Keyed keyed)) {
            return;
        }
        if (!allowed(keyed.getKey(), event.getView().getPlayer(), event.getInventory().getMatrix())) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        SmithingInventory inventory = event.getInventory();
        if (!(inventory.getRecipe() instanceof Keyed keyed)) {
            return;
        }
        ItemStack[] inputs = {inventory.getInputTemplate(), inventory.getInputEquipment(), inventory.getInputMineral()};
        if (!allowed(keyed.getKey(), event.getView().getPlayer(), inputs)) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCook(BlockCookEvent event) {
        Recipe recipe = event.getRecipe();
        NamespacedKey key = recipe instanceof Keyed keyed ? keyed.getKey() : null;
        if (protect && isVanilla(key) && isCustom(event.getSource())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCrafter(CrafterCraftEvent event) {
        NamespacedKey key = event.getRecipe().getKey();
        BlockState state = event.getBlock().getState(false);
        if (!(state instanceof Container container)) {
            return;
        }
        if (!allowed(key, null, container.getInventory().getContents())) {
            event.setCancelled(true);
        }
    }

    /**
     * Whether these ingredients may make the recipe under {@code key}: one of the plugin's - for
     * someone allowed to, with no more custom items than it has places for - or another's, with no
     * custom item in a vanilla one.
     *
     * @param crafter null for a crafter block, which has no one to ask about permissions
     */
    private boolean allowed(NamespacedKey key, @Nullable HumanEntity crafter, ItemStack[] ingredients) {
        Optional<RecipeDefinition> own = recipes.definition(key);
        if (own.isPresent()) {
            RecipeDefinition definition = own.get();
            if (crafter instanceof Player player && !recipes.mayCraft(player, definition)) {
                return false;
            }
            if (crafter == null && definition.permission() != null) {
                return false;
            }
            return customCount(ingredients) <= definition.shape().customSlots();
        }
        return !(protect && isVanilla(key) && customCount(ingredients) > 0);
    }

    private int customCount(ItemStack[] stacks) {
        int count = 0;
        for (ItemStack stack : stacks) {
            if (isCustom(stack)) {
                count++;
            }
        }
        return count;
    }

    /** Carries the plugin's id - even one no longer loaded, which is still not its material. */
    private boolean isCustom(@Nullable ItemStack stack) {
        return factory.hasId(stack);
    }

    private static boolean isVanilla(@Nullable NamespacedKey key) {
        return key != null && key.getNamespace().equals(NamespacedKey.MINECRAFT);
    }
}
