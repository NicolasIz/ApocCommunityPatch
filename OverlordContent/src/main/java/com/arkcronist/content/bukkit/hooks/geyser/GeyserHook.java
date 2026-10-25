package com.arkcronist.content.bukkit.hooks.geyser;

import com.arkcronist.content.bukkit.ContentPipeline;
import com.arkcronist.content.geyser.GeyserRegistration;
import org.bukkit.plugin.Plugin;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomBlocksEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomItemsEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineResourcePacksEvent;
import org.jetbrains.annotations.Nullable;

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
 * <p>What is registered, and how, is {@link GeyserRegistration}'s - shared with the extension this
 * jar is for a Geyser on a proxy (3.3). The pipeline gives every item a custom_model_data number
 * when Bedrock is on, since Geyser 2.8 tells items apart by nothing else.</p>
 */
public final class GeyserHook {

    /** How long Geyser's start waits for the first build before going on without it. */
    private static final long WAIT_SECONDS = 180;
    private final Logger logger;
    private final GeyserRegistration registration;
    private final CompletableFuture<ContentPipeline.BedrockContent> ready;
    private volatile @Nullable ContentPipeline.BedrockContent registered;
    private volatile boolean itemsRegistered;
    private volatile boolean blocksRegistered;

    public GeyserHook(Plugin plugin, CompletableFuture<ContentPipeline.BedrockContent> ready) {
        this.logger = plugin.getLogger();
        this.registration = new GeyserRegistration(logger::info, logger::warning);
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
        registration.items(event, content.build().items());
        itemsRegistered = true;
    }

    private void defineBlocks(GeyserDefineCustomBlocksEvent event) {
        ContentPipeline.BedrockContent content = await("custom blocks");
        if (content == null) {
            return;
        }
        registration.blocks(event, content.build().blocks(), content.build().carriers().size());
        blocksRegistered = true;
    }

    private void definePacks(GeyserDefineResourcePacksEvent event) {
        ContentPipeline.BedrockContent content = await("resource pack");
        if (content != null) {
            registration.pack(event, content.pack());
        }
    }

}
