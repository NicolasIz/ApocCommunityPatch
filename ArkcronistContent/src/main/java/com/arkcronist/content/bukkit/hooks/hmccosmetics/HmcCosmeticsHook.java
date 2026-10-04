package com.arkcronist.content.bukkit.hooks.hmccosmetics;

import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemFactory;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import me.lojosho.hibiscuscommons.hooks.Hook;
import me.lojosho.hibiscuscommons.hooks.HookFlag;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * HMCCosmetics: hats, backpacks, balloons and the like made from this plugin's items.
 *
 * <p>HMCCosmetics does not build a resource pack of its own: it draws each cosmetic with an item
 * from a plugin that has one - ItemsAdder, Oraxen, Nexo - through its library, HibiscusCommons.
 * This hook makes this plugin one of them, under the id {@code arkcontent}:</p>
 *
 * <pre>
 * # plugins/HMCCosmetics/cosmetics/hats.yml
 * ruby_crown:
 *   slot: HELMET
 *   item:
 *     material: arkcontent:demo:ruby_helmet
 * </pre>
 *
 * <p>The cosmetic is then the very stack {@code /customgive} makes - its {@code item_model}, its
 * look - and its model is already in this plugin's pack, so a hat or a backpack needs no second
 * pack and nothing can collide.</p>
 */
public final class HmcCosmeticsHook extends Hook {

    /** What a cosmetic's material starts with: {@code arkcontent:<namespace>:<id>}. */
    public static final String ID = "arkcontent";

    private final ItemRegistry items;
    private final ItemFactory factory;

    /** Registers the hook with HibiscusCommons. */
    public static Boolean register(ItemRegistry items, ItemFactory factory) {
        new HmcCosmeticsHook(items, factory);
        return Boolean.TRUE;
    }

    private HmcCosmeticsHook(ItemRegistry items, ItemFactory factory) {
        // Registers itself with HibiscusCommons.
        super(ID, HookFlag.ITEM_SUPPORT);
        this.items = items;
        this.factory = factory;
        // HibiscusCommons detects a hook by a plugin of the same name; this one is detected by
        // being here.
        setDetected(true);
        setActive(true);
    }

    @Override
    public @Nullable ItemStack getItem(String id) {
        Optional<CustomItem> item = items.find(id);
        return item.map(found -> factory.create(found, 1)).orElse(null);
    }

    @Override
    public @Nullable String getItemString(ItemStack stack) {
        return factory.identify(stack).map(item -> ID + ":" + item.id()).orElse(null);
    }
}
