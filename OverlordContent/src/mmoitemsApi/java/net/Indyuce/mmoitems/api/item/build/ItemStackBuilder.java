package net.Indyuce.mmoitems.api.item.build;

import io.lumine.mythic.lib.api.item.ItemTag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** Signatures of MMOItems 6.10's ItemStackBuilder that a stat uses. Not packaged. */
public class ItemStackBuilder {

    public ItemStack getItemStack() {
        throw new UnsupportedOperationException("signature stub");
    }

    public ItemMeta getMeta() {
        throw new UnsupportedOperationException("signature stub");
    }

    public void addItemTag(List<ItemTag> tags) {
        throw new UnsupportedOperationException("signature stub");
    }
}
