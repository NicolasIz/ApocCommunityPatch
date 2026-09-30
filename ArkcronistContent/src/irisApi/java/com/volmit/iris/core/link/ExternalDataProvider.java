package com.volmit.iris.core.link;

import com.volmit.iris.core.link.data.DataType;
import com.volmit.iris.util.collection.KMap;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.MissingResourceException;

/**
 * Signatures of Iris 3.9.2's ExternalDataProvider that a provider overrides or calls. Not
 * packaged: at runtime this is Iris's own class, so every signature here matches it exactly.
 */
public abstract class ExternalDataProvider implements Listener {

    private final String pluginId;

    public ExternalDataProvider(String pluginId) {
        this.pluginId = pluginId;
    }

    public String getPluginId() {
        return pluginId;
    }

    public abstract void init();

    public BlockData getBlockData(Identifier blockId, KMap<String, String> state) throws MissingResourceException {
        throw new MissingResourceException("Failed to find BlockData!", blockId.namespace(), blockId.key());
    }

    public ItemStack getItemStack(Identifier itemId, KMap<String, Object> customNbt) throws MissingResourceException {
        throw new MissingResourceException("Failed to find ItemData!", itemId.namespace(), itemId.key());
    }

    public abstract Collection<Identifier> getTypes(DataType dataType);

    public abstract boolean isValidProvider(Identifier id, DataType dataType);
}
