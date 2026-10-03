package net.Indyuce.mmoitems.stat.type;

import io.lumine.mythic.lib.api.item.ItemTag;
import net.Indyuce.mmoitems.api.item.build.ItemStackBuilder;
import net.Indyuce.mmoitems.api.item.mmoitem.ReadMMOItem;
import net.Indyuce.mmoitems.gui.edition.EditionInventory;
import net.Indyuce.mmoitems.stat.data.random.RandomStatData;
import net.Indyuce.mmoitems.stat.data.type.StatData;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Signatures of MMOItems 6.10's ItemStat: its constructor, abstract methods and the getters used. Not packaged. */
public abstract class ItemStat<R extends RandomStatData<S>, S extends StatData> {

    public ItemStat(String id, Material material, String name, String[] lore, String[] types, Material... materials) {
        throw new UnsupportedOperationException("signature stub");
    }

    public abstract R whenInitialized(Object object);

    public abstract void whenApplied(ItemStackBuilder item, S data);

    public abstract ArrayList<ItemTag> getAppliedNBT(S data);

    public abstract void whenClicked(EditionInventory inventory, InventoryClickEvent event);

    public abstract void whenInput(EditionInventory inventory, String message, Object... info);

    public abstract void whenLoaded(ReadMMOItem mmoitem);

    public abstract S getLoadedNBT(ArrayList<ItemTag> tags);

    public abstract void whenDisplayed(List<String> lore, Optional<R> statData);

    public abstract S getClearStatData();

    public String getName() {
        throw new UnsupportedOperationException("signature stub");
    }

    public String getId() {
        throw new UnsupportedOperationException("signature stub");
    }

    public String getPath() {
        throw new UnsupportedOperationException("signature stub");
    }

    public String getNBTPath() {
        throw new UnsupportedOperationException("signature stub");
    }
}
