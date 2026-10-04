package com.arkcronist.content.bukkit.hooks.itembridge;

import com.arkcronist.content.bukkit.hooks.ContentAccess;
import com.jojodmo.itembridge.ItemBridge;
import com.jojodmo.itembridge.ItemBridgeListener;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * ItemBridge: one way to name an item from any plugin, for the plugins that take their items
 * through it. This plugin's are {@code arkcontent:namespace:id}, items and blocks alike:
 *
 * <pre>
 * /ib give Steve arkcontent:demo:ruby
 * </pre>
 *
 * <p>A plugin that places blocks through ItemBridge places this plugin's custom blocks - recorded
 * as any other - and can ask which one stands somewhere.</p>
 */
public final class ItemBridgeHook implements ItemBridgeListener {

    static final String KEY = "arkcontent";

    private final ContentAccess content;

    private ItemBridgeHook(ContentAccess content) {
        this.content = content;
    }

    /** Registers this plugin with ItemBridge under {@code arkcontent}. */
    public static Boolean register(ContentAccess content, Plugin plugin) {
        new ItemBridge(plugin, KEY).registerListener(new ItemBridgeHook(content));
        return Boolean.TRUE;
    }

    @Override
    public @Nullable ItemStack fetchItemStack(@NotNull String item) {
        return content.item(item, 1).orElse(null);
    }

    @Override
    public @Nullable String getItemName(@NotNull ItemStack stack) {
        return content.id(stack).orElse(null);
    }

    @Override
    public @NotNull Collection<String> getAvailableItems() {
        return content.ids();
    }

    @Override
    public @NotNull Collection<String> getAvailableBlocks() {
        return content.blockIds();
    }

    @Override
    public boolean setBlock(@NotNull Location location, @NotNull String id) {
        return content.setBlock(location.getBlock(), id);
    }

    @Override
    public boolean removeBlock(@NotNull Location location) {
        return content.removeBlock(location.getBlock());
    }

    @Override
    public @Nullable String getBlock(@NotNull Location location) {
        return content.blockAt(location.getBlock()).orElse(null);
    }
}
