package net.Indyuce.mmoitems.stat.type;

import io.lumine.mythic.lib.api.item.ItemTag;
import net.Indyuce.mmoitems.api.item.build.ItemStackBuilder;
import net.Indyuce.mmoitems.api.item.mmoitem.ReadMMOItem;
import net.Indyuce.mmoitems.gui.edition.EditionInventory;
import net.Indyuce.mmoitems.stat.data.StringData;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Signatures of MMOItems 6.10's StringStat. Not packaged. */
public class StringStat extends ItemStat<StringData, StringData> {

    public StringStat(String id, Material material, String name, String[] lore, String[] types, Material... materials) {
        super(id, material, name, lore, types, materials);
    }

    @Override
    public StringData whenInitialized(Object object) {
        throw new UnsupportedOperationException("signature stub");
    }

    @Override
    public void whenApplied(ItemStackBuilder item, StringData data) {
        throw new UnsupportedOperationException("signature stub");
    }

    @Override
    public ArrayList<ItemTag> getAppliedNBT(StringData data) {
        throw new UnsupportedOperationException("signature stub");
    }

    @Override
    public void whenClicked(EditionInventory inventory, InventoryClickEvent event) {
        throw new UnsupportedOperationException("signature stub");
    }

    @Override
    public void whenInput(EditionInventory inventory, String message, Object... info) {
        throw new UnsupportedOperationException("signature stub");
    }

    @Override
    public void whenLoaded(ReadMMOItem mmoitem) {
        throw new UnsupportedOperationException("signature stub");
    }

    @Override
    public StringData getLoadedNBT(ArrayList<ItemTag> tags) {
        throw new UnsupportedOperationException("signature stub");
    }

    @Override
    public void whenDisplayed(List<String> lore, Optional<StringData> statData) {
        throw new UnsupportedOperationException("signature stub");
    }

    @Override
    public StringData getClearStatData() {
        throw new UnsupportedOperationException("signature stub");
    }
}
