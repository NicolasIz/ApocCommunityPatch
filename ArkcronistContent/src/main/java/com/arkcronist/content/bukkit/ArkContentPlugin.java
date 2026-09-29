package com.arkcronist.content.bukkit;

import com.arkcronist.content.bukkit.command.ContentAdminCommand;
import com.arkcronist.content.bukkit.command.CustomGiveCommand;
import com.arkcronist.content.bukkit.item.ItemFactory;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import com.arkcronist.content.bukkit.listener.ItemCombatListener;
import com.arkcronist.content.bukkit.listener.ItemUseListener;
import com.arkcronist.content.bukkit.listener.PackDeliveryListener;
import com.arkcronist.content.bukkit.listener.WorldInterceptionListener;
import com.arkcronist.content.bukkit.pack.PackDelivery;
import com.arkcronist.content.core.http.PackHttpServer;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * ArkcronistContent: custom items from YAML, drawn by a resource pack the plugin compiles, zips,
 * hashes and serves itself.
 *
 * <p>This class only wires the parts together. What each part does, and on which thread, is
 * documented where it lives:</p>
 * <ul>
 *   <li>{@link ContentPipeline} - the rebuild, disk work on a worker, the swap on the main thread;</li>
 *   <li>{@link ItemRegistry} / {@link ItemFactory} - the loaded items, and stacks of them;</li>
 *   <li>{@link PackDelivery} / {@link PackHttpServer} - the live pack, and the web server for it;</li>
 *   <li>the listeners and commands - how players meet all of the above.</li>
 * </ul>
 *
 * <p>Other plugins reach the items through
 * {@code JavaPlugin.getPlugin(ArkContentPlugin.class).items()}, and their behaviour through the
 * {@code CustomItemEvent} subclasses.</p>
 */
public final class ArkContentPlugin extends JavaPlugin {

    private EngineSettings settings;
    private ItemRegistry items;
    private ItemFactory itemFactory;
    private PackDelivery delivery;
    /** Set by the worker once the port is bound; read from the main thread. */
    private volatile PackHttpServer http;
    private ContentPipeline pipeline;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.settings = EngineSettings.read(getConfig(), getServer().getIp(), getLogger());

        this.items = new ItemRegistry();
        this.itemFactory = new ItemFactory(this, items);
        this.delivery = new PackDelivery(settings);
        this.pipeline = new ContentPipeline(this, settings, items, delivery);

        PluginManager plugins = getServer().getPluginManager();
        plugins.registerEvents(new PackDeliveryListener(delivery, settings, getLogger()), this);
        plugins.registerEvents(new ItemUseListener(itemFactory), this);
        plugins.registerEvents(new ItemCombatListener(itemFactory), this);
        plugins.registerEvents(new WorldInterceptionListener(itemFactory), this);

        // A Paper plugin has no commands: section in its yml; commands are Brigadier trees
        // registered here, and re-registered by the server whenever it reloads its command tree.
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            commands.register(new CustomGiveCommand(items, itemFactory).build(),
                    "Give custom items to players");
            commands.register(new ContentAdminCommand(this).build(),
                    "Rebuild or inspect the custom content", List.of("acontent"));
        });

        if (settings.http().enabled()) {
            startHttp();
        }
        // Everything from here happens off the main thread; onEnable returns straight away.
        pipeline.rebuild();
    }

    /**
     * Opens the web server on the worker. Binding, and resolving the bind address if it is a host
     * name, are I/O like any other; queued ahead of the first rebuild, so the port is open by the
     * time there is a pack to serve.
     */
    private void startHttp() {
        EngineSettings.Http config = settings.http();
        CompletableFuture.runAsync(() -> {
            try {
                PackHttpServer server = new PackHttpServer(new InetSocketAddress(config.bindAddress(), config.port()),
                        config.threads(), delivery::current, getLogger());
                server.start();
                this.http = server;
                getLogger().info("Resource pack web server listening on " + config.bindAddress() + ":"
                        + server.port() + "; players download from " + config.publicAddress() + ":" + config.port());
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }, pipeline.worker()).exceptionally(error -> {
            Throwable cause = error.getCause() != null ? error.getCause() : error;
            getLogger().severe("Could not open the resource pack web server on " + config.bindAddress() + ":"
                    + config.port() + " - " + cause.getMessage() + ". Players will not be sent the pack."
                    + " Free the port or change http.port, then restart.");
            return null;
        });
    }

    @Override
    public void onDisable() {
        if (pipeline != null) {
            pipeline.shutdown();
        }
        PackHttpServer server = http;
        if (server != null) {
            server.stop();
        }
    }

    public EngineSettings settings() {
        return settings;
    }

    /** Every loaded custom item. The same registry across rebuilds; its contents are swapped. */
    public ItemRegistry items() {
        return items;
    }

    public ItemFactory itemFactory() {
        return itemFactory;
    }

    public PackDelivery delivery() {
        return delivery;
    }

    public ContentPipeline pipeline() {
        return pipeline;
    }

    public boolean httpRunning() {
        PackHttpServer server = http;
        return server != null && server.isRunning();
    }
}
