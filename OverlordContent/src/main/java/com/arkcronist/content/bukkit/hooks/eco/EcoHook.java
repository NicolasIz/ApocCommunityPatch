package com.arkcronist.content.bukkit.hooks.eco;

import com.arkcronist.content.bukkit.hooks.ContentAccess;
import com.willfp.eco.core.items.CustomItem;
import com.willfp.eco.core.items.Items;
import com.willfp.eco.core.items.TestableItem;
import com.willfp.eco.core.items.provider.ItemProvider;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * eco, the library under Auxilor's plugins - EcoItems, EcoArmor, EcoEnchants, EcoMobs, Reforges,
 * StatTrackers, Talismans, EcoSkills, EcoShop and the rest: wherever one of them takes an item in
 * its configuration (a recipe, a drop, a shop entry, an upgrade stone), it looks it up by eco's
 * item lookup, which other plugins can add a namespace to. This hook adds {@code arkcontent}:
 *
 * <pre>
 * # an EcoItems recipe, an EcoMobs drop, an EcoShop item...
 * item: arkcontent:demo__ruby
 * </pre>
 *
 * <p>eco splits a lookup on its colons, so the item's own {@code namespace:id} is written with
 * {@code __} in place of the colon - eco turns it back before asking - as for any namespaced
 * integration of eco's. What eco gets back is matched by the item's id, not by how it looks.</p>
 */
public final class EcoHook {

    static final String NAMESPACE = "arkcontent";

    private EcoHook() {
    }

    /** Adds the {@code arkcontent} namespace to eco's item lookup. */
    public static Boolean register(ContentAccess content) {
        Items.registerItemProvider(new Provider(content));
        return Boolean.TRUE;
    }

    private static final class Provider extends ItemProvider {

        private final ContentAccess content;

        Provider(ContentAccess content) {
            super(NAMESPACE);
            this.content = content;
        }

        /** @param key what followed {@code arkcontent:}, with {@code __} already turned into {@code :} */
        @Override
        public @Nullable TestableItem provideForKey(String key) {
            Optional<ItemStack> stack = content.item(key, 1);
            if (stack.isEmpty()) {
                return null;
            }
            String id = content.id(stack.get()).orElseThrow();
            return new CustomItem(new NamespacedKey(NAMESPACE, id.replace(":", "__")),
                    test -> content.id(test).filter(id::equals).isPresent(), stack.get());
        }
    }
}
