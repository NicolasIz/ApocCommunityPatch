package com.arkcronist.content.bukkit.hooks.iris;

import com.arkcronist.content.bukkit.block.BlockRegistry;
import com.arkcronist.content.bukkit.block.CustomBlock;
import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemFactory;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import com.volmit.iris.Iris;
import com.volmit.iris.core.link.ExternalDataProvider;
import com.volmit.iris.core.link.Identifier;
import com.volmit.iris.core.link.data.DataType;
import com.volmit.iris.core.service.ExternalDataSVC;
import com.volmit.iris.util.collection.KMap;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.List;
import java.util.MissingResourceException;
import java.util.Optional;

/**
 * This plugin's custom blocks - and items - in Iris world generation.
 *
 * <p>Registers a data provider with Iris, so an Iris pack can name a custom block by its own id
 * wherever it takes a block. An ore deposit of ruby blocks, in a biome's or region's JSON:</p>
 * <pre>
 * "ores": [{
 *   "palette": [{"block": "demo:ruby_block"}],
 *   "minHeight": -32, "maxHeight": 32, ...
 * }]
 * </pre>
 * <p>A custom block is a note block in its own state, so what Iris is handed is that state, and
 * it is placed as fast as stone - no entity, no callback. Items work the same way in Iris loot
 * tables. Iris calls this from its generator threads; the registries it reads are swapped whole
 * by each rebuild and never locked.</p>
 */
public final class IrisHook {

    public IrisHook(String pluginName, BlockRegistry blocks, ItemRegistry items, ItemFactory factory) {
        Iris.service(ExternalDataSVC.class).registerProvider(new Provider(pluginName, blocks, items, factory));
    }

    private static final class Provider extends ExternalDataProvider {

        private final BlockRegistry blocks;
        private final ItemRegistry items;
        private final ItemFactory factory;

        Provider(String pluginName, BlockRegistry blocks, ItemRegistry items, ItemFactory factory) {
            super(pluginName);
            this.blocks = blocks;
            this.items = items;
            this.factory = factory;
        }

        @Override
        public void init() {
        }

        @Override
        public BlockData getBlockData(Identifier blockId, KMap<String, String> state) throws MissingResourceException {
            Optional<CustomBlock> block = blocks.get(blockId.namespace() + ":" + blockId.key());
            if (block.isEmpty()) {
                throw new MissingResourceException("No custom block " + blockId, blockId.namespace(), blockId.key());
            }
            return block.get().data().clone();
        }

        @Override
        public ItemStack getItemStack(Identifier itemId, KMap<String, Object> customNbt) throws MissingResourceException {
            Optional<CustomItem> item = items.get(itemId.namespace() + ":" + itemId.key());
            if (item.isEmpty()) {
                throw new MissingResourceException("No custom item " + itemId, itemId.namespace(), itemId.key());
            }
            return factory.create(item.get(), 1);
        }

        @Override
        public Collection<Identifier> getTypes(DataType dataType) {
            return switch (dataType) {
                case BLOCK -> items.all().stream()
                        .filter(item -> blocks.get(item.id()).isPresent())
                        .map(item -> new Identifier(item.definition().namespace(), item.definition().id()))
                        .toList();
                case ITEM -> items.all().stream()
                        .map(item -> new Identifier(item.definition().namespace(), item.definition().id()))
                        .toList();
                default -> List.of();
            };
        }

        /** Only ids that exist here: another plugin may use the same namespace for its own blocks. */
        @Override
        public boolean isValidProvider(Identifier id, DataType dataType) {
            String full = id.namespace() + ":" + id.key();
            return switch (dataType) {
                case BLOCK -> blocks.get(full).isPresent();
                case ITEM -> items.get(full).isPresent();
                default -> false;
            };
        }
    }
}
