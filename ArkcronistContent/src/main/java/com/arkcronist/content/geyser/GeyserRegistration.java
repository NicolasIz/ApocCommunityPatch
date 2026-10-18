package com.arkcronist.content.geyser;

import com.arkcronist.content.core.geyser.BedrockConverter;
import com.arkcronist.content.core.geyser.GeyserExport;
import org.geysermc.geyser.api.block.custom.CustomBlockData;
import org.geysermc.geyser.api.block.custom.component.BoxComponent;
import org.geysermc.geyser.api.block.custom.component.CustomBlockComponents;
import org.geysermc.geyser.api.block.custom.component.GeometryComponent;
import org.geysermc.geyser.api.block.custom.component.MaterialInstance;
import org.geysermc.geyser.api.block.custom.component.TransformationComponent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomBlocksEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomItemsEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineResourcePacksEvent;
import org.geysermc.geyser.api.item.custom.CustomItemData;
import org.geysermc.geyser.api.item.custom.CustomItemOptions;
import org.geysermc.geyser.api.pack.PackCodec;
import org.geysermc.geyser.api.pack.ResourcePack;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Hands Geyser the converted content through its API: the items, the blocks - the furniture's
 * carriers among them - and the .mcpack. Used by the hook on a server with Geyser-Spigot and, since
 * 3.3, by the extension this jar is for a Geyser on a proxy: nothing here touches Bukkit, so it
 * loads inside Geyser too.
 *
 * <p>Items are told apart by their {@code item_model} on Geyser 2.9 and later, and by their
 * {@code custom_model_data} number on 2.8 - the one for 1.21.8 - which has nothing else to go by.
 * Since 3.3.2, on 2.9 and later also by that number wherever the Java pack draws them by it: the
 * stacks ItemsAdder made carry only the number.</p>
 */
public final class GeyserRegistration {

    /** Geyser 2.9 and later: items by item model. */
    public static final boolean ITEMS_BY_MODEL = present("org.geysermc.geyser.api.item.custom.v2.CustomItemDefinition");
    /** 3.3.2: and, where its API has the predicate, by custom_model_data number too. */
    public static final boolean ITEMS_BY_NUMBER_TOO = ITEMS_BY_MODEL
            && present("org.geysermc.geyser.api.predicate.item.ItemRangeDispatchPredicate", "customModelData",
            int.class, float.class);

    private final Consumer<String> info;
    private final Consumer<String> warning;

    /** @param info and {@code warning}: where to say how it went, the hook's or the extension's logger */
    public GeyserRegistration(Consumer<String> info, Consumer<String> warning) {
        this.info = info;
        this.warning = warning;
    }

    /** Every item; returns how many Geyser took. */
    public int items(GeyserDefineCustomItemsEvent event, List<BedrockConverter.BedrockItem> all) {
        int registered = 0;
        int byNumber = 0;
        List<String> refused = new ArrayList<>();
        // Geyser 2.8 reverses the order items were registered in and gives a stack the first whose
        // number is at most the stack's: registered lowest number first, that is the stack's own.
        List<BedrockConverter.BedrockItem> items = ITEMS_BY_MODEL ? all : GeyserExport.lowestNumberFirst(all);
        for (BedrockConverter.BedrockItem item : items) {
            try {
                boolean done = ITEMS_BY_MODEL ? GeyserItemsByModel.register(event, item) : registerByNumber(event, item);
                if (done) {
                    registered++;
                } else {
                    refused.add(item.id());
                }
            } catch (RuntimeException | LinkageError failure) {
                refused.add(item.id() + " (" + failure + ")");
            }
            if (ITEMS_BY_NUMBER_TOO && item.byNumber()) {
                try {
                    if (GeyserItemsByModel.registerByNumber(event, item)) {
                        byNumber++;
                    }
                } catch (RuntimeException | LinkageError failure) {
                    refused.add(item.id() + " by number (" + failure + ")");
                }
            }
        }
        info.accept("[bedrock] Geyser: " + registered + " custom item(s), told apart by "
                + (ITEMS_BY_MODEL ? "item model" : "custom_model_data")
                + (byNumber > 0 ? "; " + byNumber + " of them also by custom_model_data, as the Java pack draws them" : "")
                + ".");
        if (!refused.isEmpty()) {
            warning.accept("[bedrock] Geyser refused " + refused.size() + " item(s): " + String.join(", ", refused));
        }
        return registered;
    }

    /** Geyser 2.8: by custom_model_data, the API it has. */
    @SuppressWarnings({"deprecation", "removal"})
    private static boolean registerByNumber(GeyserDefineCustomItemsEvent event, BedrockConverter.BedrockItem item) {
        if (item.customModelData() == null) {
            return false;
        }
        CustomItemData data = CustomItemData.builder()
                .name(item.name())
                .displayName(item.displayName())
                .icon(item.icon())
                .allowOffhand(true)
                .displayHandheld(item.handheld())
                .customItemOptions(CustomItemOptions.builder().customModelData(item.customModelData()).build())
                .build();
        return event.register(item.javaItem(), data);
    }

    /**
     * Every block, each one Bedrock block standing in for one Java state; returns how many Geyser
     * took. Through the API 2.8 has: newer Geyser versions deprecate a few of these calls, and keep them.
     */
    @SuppressWarnings("deprecation")
    public int blocks(GeyserDefineCustomBlocksEvent event, List<BedrockConverter.BedrockBlock> blocks, long carriers) {
        int done = 0;
        List<String> refused = new ArrayList<>();
        for (BedrockConverter.BedrockBlock block : blocks) {
            try {
                CustomBlockComponents.Builder components = CustomBlockComponents.builder()
                        .geometry(GeometryComponent.builder().identifier(block.geometry()).build())
                        .collisionBox(block.collision() ? BoxComponent.fullBox() : BoxComponent.emptyBox())
                        .selectionBox(block.selection() ? BoxComponent.fullBox() : BoxComponent.emptyBox())
                        .destructibleByMining(block.destroySeconds())
                        .displayName(block.displayName());
                block.materials().forEach((key, material) -> components.materialInstance(key, MaterialInstance.builder()
                        .texture(material.texture())
                        .renderMethod(material.renderMethod())
                        .faceDimming(true)
                        .ambientOcclusion(!"blend".equals(material.renderMethod()))
                        .build()));
                if (block.light() > 0) {
                    components.lightEmission(block.light());
                }
                if (block.rotationY() != 0) {
                    components.transformation(new TransformationComponent(0, block.rotationY(), 0));
                }
                CustomBlockData data = CustomBlockData.builder()
                        .name(block.name())
                        .includedInCreativeInventory(false)
                        .components(components.build())
                        .build();
                event.register(data);
                event.registerOverride(block.javaState(), data.defaultBlockState());
                done++;
            } catch (RuntimeException | LinkageError failure) {
                refused.add(block.name() + " (" + failure + ")");
            }
        }
        info.accept("[bedrock] Geyser: " + done + " custom block(s), " + carriers + " of them furniture carriers.");
        if (!refused.isEmpty()) {
            warning.accept("[bedrock] Geyser refused " + refused.size() + " block(s): " + String.join(", ", refused));
        }
        return done;
    }

    /** The .mcpack Bedrock players are sent as they connect. */
    public boolean pack(GeyserDefineResourcePacksEvent event, Path pack) {
        try {
            event.register(ResourcePack.create(PackCodec.path(pack)));
            info.accept("[bedrock] Geyser sends " + pack.getFileName() + " to Bedrock players.");
            return true;
        } catch (RuntimeException failure) {
            warning.accept("[bedrock] Geyser would not take " + pack + ": " + failure);
            return false;
        }
    }

    /**
     * Asked of the loader of Geyser's own API: an extension's loader looks a missing class up in other
     * extensions too, and Geyser warns of that each time.
     */
    private static boolean present(String className) {
        try {
            Class.forName(className, false, org.geysermc.geyser.api.GeyserApi.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError missing) {
            return false;
        }
    }

    /** The class, and on it the method: an API that has the one may still lack the other. */
    private static boolean present(String className, String method, Class<?>... parameters) {
        try {
            Class.forName(className, false, org.geysermc.geyser.api.GeyserApi.class.getClassLoader())
                    .getMethod(method, parameters);
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError missing) {
            return false;
        }
    }
}
