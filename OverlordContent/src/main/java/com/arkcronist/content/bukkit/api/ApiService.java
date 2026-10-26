package com.arkcronist.content.bukkit.api;

import com.arkcronist.content.api.OverlordContentApi;
import com.arkcronist.content.bukkit.ArkContentPlugin;
import com.arkcronist.content.bukkit.hooks.ContentAccess;
import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.core.crop.PlantedCrop;
import com.arkcronist.content.core.definition.Placement;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** 5.4: {@link OverlordContentApi}, answered from the plugin's own registries and services. */
public final class ApiService implements OverlordContentApi {

    private final ArkContentPlugin plugin;
    private final ContentAccess content;
    private volatile boolean ready;

    public ApiService(ArkContentPlugin plugin, ContentAccess content) {
        this.plugin = plugin;
        this.content = content;
    }

    /** The content has been built: blocks and furniture are known. */
    public void markReady() {
        this.ready = true;
    }

    @Override
    public String version() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean ready() {
        return ready;
    }

    @Override
    public Set<String> itemIds() {
        return Set.copyOf(content.ids());
    }

    @Override
    public Optional<ItemStack> createItem(String id, int amount) {
        return content.item(id, Math.max(1, amount));
    }

    @Override
    public Optional<String> itemId(@Nullable ItemStack stack) {
        return content.id(stack);
    }

    @Override
    public Set<String> blockIds() {
        return Set.copyOf(content.blockIds());
    }

    @Override
    public Optional<String> blockAt(Block block) {
        return content.blockAt(block);
    }

    @Override
    public boolean placeBlock(Block block, String id) {
        return content.setBlock(block, id);
    }

    @Override
    public boolean removeBlock(Block block) {
        return content.removeBlock(block);
    }

    @Override
    public Set<String> furnitureIds() {
        Set<String> ids = new TreeSet<>();
        for (CustomItem item : plugin.items().all()) {
            if (item.placement() instanceof Placement.Furniture) {
                ids.add(item.id());
            }
        }
        return ids;
    }

    @Override
    public Optional<String> furnitureAt(Block block) {
        return plugin.furniture().identify(block);
    }

    @Override
    public Optional<String> furnitureOf(Entity entity) {
        var furniture = plugin.furniture();
        String id = furniture.furnitureId(entity);
        if (id != null) {
            return Optional.of(id);
        }
        if (furniture.isHitbox(entity)) {
            Block anchor = furniture.anchorOf(entity);
            return anchor == null ? Optional.empty() : furniture.identify(anchor);
        }
        return Optional.empty();
    }

    @Override
    public boolean placeFurniture(Block block, String id, float yaw) {
        Optional<CustomItem> item = plugin.items().find(id);
        if (item.isEmpty() || !(item.get().placement() instanceof Placement.Furniture spec)
                || spec.support() == Placement.Support.BED || !block.getType().isAir() && !block.isReplaceable()) {
            return false;
        }
        Material before = block.getType();
        block.setType(item.get().material(), false);
        if (!plugin.furniture().place(block, yaw, item.get(), spec)) {
            block.setType(before, false);
            return false;
        }
        return true;
    }

    @Override
    public boolean removeFurniture(Block block, boolean drop) {
        Optional<String> id = plugin.furniture().identify(block);
        if (id.isEmpty()) {
            return false;
        }
        plugin.furniture().remove(block, id.get(), drop);
        return true;
    }

    @Override
    public boolean playAnimation(Block block, String clip) {
        return plugin.furniture().animate(block, clip);
    }

    @Override
    public Optional<String> cropAt(Block block) {
        return plugin.crops().at(block).map(PlantedCrop::cropId);
    }

    @Override
    public Optional<Integer> cropStage(Block block) {
        return plugin.crops().at(block).map(PlantedCrop::stage);
    }

    @Override
    public void reload() {
        plugin.pipeline().rebuild();
    }
}
