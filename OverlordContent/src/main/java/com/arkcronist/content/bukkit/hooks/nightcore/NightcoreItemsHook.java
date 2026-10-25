package com.arkcronist.content.bukkit.hooks.nightcore;

import com.arkcronist.content.bukkit.hooks.ContentAccess;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import su.nightexpress.nightcore.integration.item.ItemBridge;
import su.nightexpress.nightcore.integration.item.adapter.IdentifiableItemAdapter;
import su.nightexpress.nightcore.integration.item.data.ItemIdData;

/**
 * nightcore, the library under NightExpress's plugins - ExcellentCrates, ExcellentShop,
 * ExcellentEnchants, ExcellentJobs and the rest. When an admin puts an item into one of them - a
 * crate reward from the hand, a shop product - nightcore asks each registered adapter whether the
 * item is one of its plugin's, and if so stores only the plugin and the id, building the item anew
 * each time it is given. It does so for ItemsAdder, Oraxen, Nexo and MMOItems; this adapter makes
 * it do so for this plugin too, under {@code arkcontent}.
 *
 * <p>So a ruby put into a crate stays a ruby - its look and behaviour taken from its definition
 * every time - rather than a copy of the stack as it was on the day it was added.</p>
 */
public final class NightcoreItemsHook {

    static final String NAME = "arkcontent";

    private NightcoreItemsHook() {
    }

    /** Registers the adapter with nightcore. */
    public static Boolean register(ContentAccess content) {
        ItemBridge.register(new Adapter(content));
        return Boolean.TRUE;
    }

    private static final class Adapter extends IdentifiableItemAdapter {

        private final ContentAccess content;

        Adapter(ContentAccess content) {
            super(NAME);
            this.content = content;
        }

        @Override
        public @Nullable ItemStack createItem(String id) {
            return content.item(id, 1).orElse(null);
        }

        @Override
        public @Nullable String getItemId(ItemStack stack) {
            return content.id(stack).orElse(null);
        }

        @Override
        public boolean canHandle(ItemStack stack) {
            return content.id(stack).isPresent();
        }

        @Override
        public boolean canHandle(ItemIdData data) {
            return content.exists(data.getItemId());
        }
    }
}
