package com.arkcronist.content.bukkit.hooks.mimic;

import com.arkcronist.content.bukkit.hooks.ContentAccess;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.endlesscode.mimic.Mimic;
import ru.endlesscode.mimic.MimicApiLevel;
import ru.endlesscode.mimic.items.BukkitItemsRegistry;

import java.util.Collection;

/**
 * Mimic, the shared API through which RPG plugins - RPGInventory and the others written for it -
 * ask for items without knowing which plugin made them. This registry answers for this plugin's
 * items, as {@code arkcontent:namespace:id}:
 *
 * <pre>
 * /mimic items give Steve arkcontent:demo:ruby
 * </pre>
 */
public final class MimicHook implements BukkitItemsRegistry {

    static final String ID = "arkcontent";

    private final ContentAccess content;

    private MimicHook(ContentAccess content) {
        this.content = content;
    }

    /** Registers this plugin's items with Mimic. */
    public static Boolean register(ContentAccess content, Plugin plugin) {
        Mimic.getInstance().registerItemsRegistry(new MimicHook(content), MimicApiLevel.VERSION_0_8, plugin,
                ServicePriority.Normal);
        return Boolean.TRUE;
    }

    @Override
    public @NotNull String getId() {
        return ID;
    }

    @Override
    public @NotNull Collection<String> getKnownIds() {
        return content.ids();
    }

    @Override
    public boolean isItemExists(@NotNull String id) {
        return content.exists(id);
    }

    @Override
    public @Nullable String getItemId(@NotNull ItemStack stack) {
        return content.id(stack).orElse(null);
    }

    @Override
    public @Nullable ItemStack getItem(@NotNull String id, @Nullable Object payload, int amount) {
        return content.item(id, amount).orElse(null);
    }
}
