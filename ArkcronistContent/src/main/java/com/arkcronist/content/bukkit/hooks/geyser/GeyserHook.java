package com.arkcronist.content.bukkit.hooks.geyser;

import com.arkcronist.content.bukkit.ContentPipeline;
import com.arkcronist.content.core.geyser.BedrockConverter;
import org.bukkit.plugin.Plugin;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.block.custom.CustomBlockData;
import org.geysermc.geyser.api.block.custom.component.BoxComponent;
import org.geysermc.geyser.api.block.custom.component.CustomBlockComponents;
import org.geysermc.geyser.api.block.custom.component.GeometryComponent;
import org.geysermc.geyser.api.block.custom.component.MaterialInstance;
import org.geysermc.geyser.api.block.custom.component.TransformationComponent;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomBlocksEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomItemsEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineResourcePacksEvent;
import org.geysermc.geyser.api.item.custom.CustomItemData;
import org.geysermc.geyser.api.item.custom.CustomItemOptions;
import org.geysermc.geyser.api.pack.PackCodec;
import org.geysermc.geyser.api.pack.ResourcePack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

/**
 * Hands Geyser what Bedrock players are shown - every custom item with its icon, every custom
 * block, the furniture as carrier blocks, and the .mcpack they are drawn with - as Geyser asks for
 * them while it starts. Nothing is written to Geyser's folders: its item and block mappings are
 * made in memory, by its API, from the content the pipeline converted.
 *
 * <p>Geyser asks once, as it starts - when the server has loaded, after every plugin enabled -
 * on the main thread, and keeps the answer until the server stops: content added by a later
 * rebuild reaches Bedrock players after a restart. The events are subscribed to as this plugin
 * enables, before Geyser fires them; each waits for the first build, which the worker finishes
 * without the main thread.</p>
 *
 * <p>Items are told apart by their {@code item_model} on Geyser 2.9 and later, and by their
 * {@code custom_model_data} number on 2.8 - the one for 1.21.8 - which has nothing else to go
 * by. The pipeline gives every item a number when Geyser is installed for that reason.</p>
 */
public final class GeyserHook {

    /** How long Geyser's start waits for the first build before going on without it. */
    private static final long WAIT_SECONDS = 180;
    /** Geyser 2.9 and later: items by item model. */
    private static final boolean ITEMS_BY_MODEL = present("org.geysermc.geyser.api.item.custom.v2.CustomItemDefinition");

    private final Logger logger;
    private final CompletableFuture<ContentPipeline.BedrockContent> ready;
    private volatile @Nullable ContentPipeline.BedrockContent registered;
    private volatile boolean itemsRegistered;
    private volatile boolean blocksRegistered;

    public GeyserHook(Plugin plugin, CompletableFuture<ContentPipeline.BedrockContent> ready) {
        this.logger = plugin.getLogger();
        this.ready = ready;
        var bus = GeyserApi.api().eventBus();
        EventRegistrar owner = EventRegistrar.of(plugin);
        bus.subscribe(owner, GeyserDefineCustomItemsEvent.class, this::defineItems);
        bus.subscribe(owner, GeyserDefineCustomBlocksEvent.class, this::defineBlocks);
        bus.subscribe(owner, GeyserDefineResourcePacksEvent.class, this::definePacks);
    }

    /** Whether the player plays on Bedrock, through Geyser. Any thread. */
    public boolean isBedrockPlayer(UUID player) {
        return GeyserApi.api().isBedrockPlayer(player);
    }

    /**
     * What Geyser was given as it started - what Bedrock players see until the next restart - or
     * null when it has not asked yet.
     */
    public @Nullable ContentPipeline.BedrockContent registered() {
        return registered;
    }

    /** Whether Geyser took the custom items and blocks: false when it started before they were built. */
    public boolean live() {
        return itemsRegistered && blocksRegistered;
    }

    private @Nullable ContentPipeline.BedrockContent await(String what) {
        ContentPipeline.BedrockContent content = registered;
        if (content != null) {
            return content;
        }
        long started = System.nanoTime();
        try {
            content = ready.get(WAIT_SECONDS, TimeUnit.SECONDS);
            long waited = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            if (waited > 50) {
                logger.info("[bedrock] Geyser waited " + waited + " ms for the first build before taking the " + what + ".");
            }
            registered = content;
            return content;
        } catch (TimeoutException timeout) {
            logger.warning("[bedrock] The first build took more than " + WAIT_SECONDS + " s: Geyser starts without the "
                    + what + ". Bedrock players see them after a restart.");
        } catch (ExecutionException failed) {
            logger.warning("[bedrock] No content for Bedrock players: the first build failed (" + failed.getCause()
                    + "). Geyser starts without the " + what + ".");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        return null;
    }

    private void defineItems(GeyserDefineCustomItemsEvent event) {
        ContentPipeline.BedrockContent content = await("custom items");
        if (content == null) {
            return;
        }
        int registeredItems = 0;
        List<String> refused = new ArrayList<>();
        // Geyser 2.8 reverses the order items were registered in and gives a stack the first whose
        // number is at most the stack's: registered lowest number first, that is the stack's own.
        List<BedrockConverter.BedrockItem> items = ITEMS_BY_MODEL ? content.build().items()
                : BedrockConverter.lowestNumberFirst(content.build().items());
        for (BedrockConverter.BedrockItem item : items) {
            try {
                boolean done = ITEMS_BY_MODEL ? GeyserItemsByModel.register(event, item) : registerByNumber(event, item);
                if (done) {
                    registeredItems++;
                } else {
                    refused.add(item.id());
                }
            } catch (RuntimeException | LinkageError failure) {
                refused.add(item.id() + " (" + failure + ")");
            }
        }
        itemsRegistered = true;
        logger.info("[bedrock] Geyser: " + registeredItems + " custom item(s), told apart by "
                + (ITEMS_BY_MODEL ? "item model" : "custom_model_data") + ".");
        if (!refused.isEmpty()) {
            logger.warning("[bedrock] Geyser refused " + refused.size() + " item(s): " + String.join(", ", refused));
        }
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

    /** Through the API 2.8 has: newer Geyser versions deprecate a few of these calls, and keep them. */
    @SuppressWarnings("deprecation")
    private void defineBlocks(GeyserDefineCustomBlocksEvent event) {
        ContentPipeline.BedrockContent content = await("custom blocks");
        if (content == null) {
            return;
        }
        int done = 0;
        List<String> refused = new ArrayList<>();
        for (BedrockConverter.BedrockBlock block : content.build().blocks()) {
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
        blocksRegistered = true;
        long carriers = content.build().carriers().size();
        logger.info("[bedrock] Geyser: " + done + " custom block(s), " + carriers + " of them furniture carriers.");
        if (!refused.isEmpty()) {
            logger.warning("[bedrock] Geyser refused " + refused.size() + " block(s): " + String.join(", ", refused));
        }
    }

    private void definePacks(GeyserDefineResourcePacksEvent event) {
        ContentPipeline.BedrockContent content = await("resource pack");
        if (content == null) {
            return;
        }
        try {
            ResourcePack pack = ResourcePack.create(PackCodec.path(content.pack()));
            event.register(pack);
            logger.info("[bedrock] Geyser sends " + content.pack().getFileName() + " to Bedrock players.");
        } catch (RuntimeException failure) {
            logger.warning("[bedrock] Geyser would not take " + content.pack() + ": " + failure);
        }
    }

    private static boolean present(String className) {
        try {
            Class.forName(className, false, GeyserHook.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError missing) {
            return false;
        }
    }
}
