package com.arkcronist.content.bukkit.block;

import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.inventory.Recipe;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * 5.3: vanilla's recipes for the waxed copper blocks that draw custom blocks, taken out while they
 * do - three waxed cut copper make a waxed slab, honeycomb waxes a slab, the stonecutter cuts one.
 * Placing what they make is refused anyway; without them nobody crafts a block they cannot place.
 * A copper block a rebuild no longer uses gets its recipes back. Main thread.
 */
public final class CopperRecipes {

    private final Server server;
    private final Logger logger;
    private final Map<NamespacedKey, Recipe> removed = new LinkedHashMap<>();

    public CopperRecipes(Server server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    /** Takes out the recipes making {@code claimed}, and puts back the ones taken that no longer do. */
    public void update(Set<Material> claimed) {
        int back = 0;
        for (Iterator<Map.Entry<NamespacedKey, Recipe>> it = removed.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<NamespacedKey, Recipe> entry = it.next();
            if (!claimed.contains(entry.getValue().getResult().getType())) {
                server.addRecipe(entry.getValue());
                it.remove();
                back++;
            }
        }
        List<Recipe> taken = new ArrayList<>();
        if (!claimed.isEmpty()) {
            for (Iterator<Recipe> it = server.recipeIterator(); it.hasNext(); ) {
                Recipe recipe = it.next();
                if (recipe instanceof Keyed keyed && keyed.getKey().getNamespace().equals(NamespacedKey.MINECRAFT)
                        && claimed.contains(recipe.getResult().getType())) {
                    taken.add(recipe);
                }
            }
        }
        for (Recipe recipe : taken) {
            NamespacedKey key = ((Keyed) recipe).getKey();
            if (server.removeRecipe(key)) {
                removed.put(key, recipe);
            }
        }
        if (!taken.isEmpty() || back > 0) {
            logger.info("[blocks] vanilla recipes for the copper blocks custom blocks are drawn through: "
                    + taken.size() + " taken out" + (back > 0 ? ", " + back + " given back" : "") + ".");
        }
    }

    /** Every recipe taken goes back - the plugin is disabling. */
    public void restore() {
        removed.values().forEach(server::addRecipe);
        removed.clear();
    }
}
