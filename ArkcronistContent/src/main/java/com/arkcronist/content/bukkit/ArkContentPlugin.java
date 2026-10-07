package com.arkcronist.content.bukkit;

import com.arkcronist.content.bukkit.advancement.AdvancementListener;
import com.arkcronist.content.bukkit.advancement.AdvancementService;
import com.arkcronist.content.bukkit.block.BlockRegistry;
import com.arkcronist.content.bukkit.block.CustomBlockService;
import com.arkcronist.content.bukkit.command.ContentAdminCommand;
import com.arkcronist.content.bukkit.command.CustomGiveCommand;
import com.arkcronist.content.bukkit.command.EmojiCommand;
import com.arkcronist.content.bukkit.crop.CropListener;
import com.arkcronist.content.bukkit.crop.CropService;
import com.arkcronist.content.bukkit.crop.CropTicker;
import com.arkcronist.content.bukkit.editor.EditorListener;
import com.arkcronist.content.bukkit.editor.FurnitureEditor;
import com.arkcronist.content.bukkit.emoji.ChatEmojiListener;
import com.arkcronist.content.bukkit.emoji.EmojiRegistry;
import com.arkcronist.content.bukkit.furniture.AnimationPlayer;
import com.arkcronist.content.bukkit.furniture.FurnitureService;
import com.arkcronist.content.bukkit.furniture.SeatService;
import com.arkcronist.content.bukkit.furniture.StorageService;
import com.arkcronist.content.bukkit.gun.GunListener;
import com.arkcronist.content.bukkit.gun.GunService;
import com.arkcronist.content.bukkit.hooks.HookManager;
import com.arkcronist.content.bukkit.hud.ActionBars;
import com.arkcronist.content.bukkit.hud.HudListener;
import com.arkcronist.content.bukkit.hud.HudRegistry;
import com.arkcronist.content.bukkit.hud.HudService;
import com.arkcronist.content.bukkit.item.ItemFactory;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import com.arkcronist.content.bukkit.liquid.LiquidListener;
import com.arkcronist.content.bukkit.liquid.LiquidRegistry;
import com.arkcronist.content.bukkit.liquid.LiquidService;
import com.arkcronist.content.bukkit.listener.CustomBlockListener;
import com.arkcronist.content.bukkit.listener.FurnitureListener;
import com.arkcronist.content.bukkit.listener.ItemCombatListener;
import com.arkcronist.content.bukkit.listener.ItemUseListener;
import com.arkcronist.content.bukkit.listener.PackDeliveryListener;
import com.arkcronist.content.bukkit.listener.PlacedContentListener;
import com.arkcronist.content.bukkit.listener.WorldInterceptionListener;
import com.arkcronist.content.bukkit.menu.ContentMenuListener;
import com.arkcronist.content.bukkit.menu.ContentMenus;
import com.arkcronist.content.bukkit.pack.PackDelivery;
import com.arkcronist.content.bukkit.pack.PackWebHost;
import com.arkcronist.content.bukkit.protection.Protection;
import com.arkcronist.content.bukkit.sanity.SanityChecker;
import com.arkcronist.content.core.crop.CropStore;
import com.arkcronist.content.core.storage.DatabaseManager;
import com.arkcronist.content.core.storage.FurnitureTransform;
import com.arkcronist.content.core.storage.FurnitureTransformStore;
import com.arkcronist.content.core.storage.PlacedContentStore;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
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
 *   <li>{@link BlockRegistry} / {@link CustomBlockService} - custom blocks as note block states;</li>
 *   <li>{@link FurnitureService} / {@link SeatService} / {@link StorageService} - furniture as a
 *       support block plus an item display, sitting on it, and keeping things in it;</li>
 *   <li>{@link CropService} / {@link CropTicker} - crops, and their growth off the main thread;</li>
 *   <li>{@link EmojiRegistry} - chat emojis, drawn by the pack's font;</li>
 *   <li>{@link HookManager} - integrations with other plugins, each only when installed;</li>
 *   <li>{@link Protection} - WorldGuard and GriefPrevention, asked before this plugin changes a
 *       block on its own;</li>
 *   <li>{@link ContentMenus} - the in-game browser of everything loaded;</li>
 *   <li>{@link PlacedContentStore} / {@link DatabaseManager} - where blocks and furniture stand,
 *       in memory and in SQLite;</li>
 *   <li>{@link PackDelivery} / {@link PackWebHost} - the live pack, and the web server for it: its
 *       port, with fallbacks, and the public address players download from;</li>
 *   <li>{@link FurnitureEditor} - {@code /arkcontent editor}, furniture moved, scaled and turned live;</li>
 *   <li>{@link SanityChecker} - the cyclic audit that cleans up after rollbacks and fill commands;</li>
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
    private BlockRegistry blocks;
    private EmojiRegistry emojis;
    private DatabaseManager database;
    private PlacedContentStore placed;
    private FurnitureTransformStore transforms;
    private FurnitureEditor editor;
    private CropStore cropStore;
    private CropService crops;
    private CropTicker cropTicker;
    private SeatService seats;
    private StorageService storage;
    private FurnitureService furniture;
    private AnimationPlayer animations;
    private Protection protection;
    private PackDelivery delivery;
    private HookManager hooks;
    private ContentMenus menus;
    private AdvancementService advancements;
    private CustomBlockService blockService;
    private ActionBars actionBars;
    private GunService guns;
    private final LiquidRegistry liquidRegistry = new LiquidRegistry();
    private final HudRegistry hudRegistry = new HudRegistry();
    private LiquidService liquids;
    private HudService huds;
    private SanityChecker sanity;
    private PackWebHost webHost;
    private ContentPipeline pipeline;

    /**
     * Only what has to happen before other plugins enable: registering with plugins that read their
     * configuration as they do - MMOItems, for its item stat.
     */
    @Override
    public void onLoad() {
        this.hooks = new HookManager(this);
        hooks.load();
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.settings = EngineSettings.read(getConfig(), getServer().getIp(), getLogger());

        this.items = new ItemRegistry();
        this.itemFactory = new ItemFactory(this, items);
        this.blocks = new BlockRegistry();
        this.emojis = new EmojiRegistry();
        this.database = new DatabaseManager(getDataFolder().toPath().resolve("data").resolve("world_content.db"),
                getLogger());
        this.placed = new PlacedContentStore(database, getLogger());
        this.transforms = new FurnitureTransformStore(database, getLogger());
        this.cropStore = new CropStore(database, getLogger());
        this.delivery = new PackDelivery(settings);
        this.pipeline = new ContentPipeline(this, settings, items, blocks, emojis, delivery);
        this.webHost = new PackWebHost(this, settings, delivery, getServer().getIp());
        this.protection = new Protection(getLogger());

        // The items are live before any hook starts, and before other plugins read their configs.
        int preloaded = pipeline.preload();
        if (preloaded > 0) {
            getLogger().info(preloaded + " custom item(s) read; the resource pack is built next, in the background.");
        }
        // Hooks start before anything that uses them, and before the first rebuild reports to them.
        hooks.enable();

        CustomBlockService blockService = new CustomBlockService(getServer(), blocks, itemFactory, placed);
        this.blockService = blockService;
        blockService.yieldTo(hooks.foreignBlocks());
        this.animations = new AnimationPlayer(this);
        FurnitureService furniture = new FurnitureService(this, items, itemFactory, placed, hooks, animations);
        this.furniture = furniture;
        furniture.useTransforms(transforms);
        this.editor = new FurnitureEditor(this, furniture, transforms);
        this.seats = new SeatService(this);
        this.storage = new StorageService(this, database, new StorageService.Lid() {
            @Override
            public void opened(Block support) {
                furniture.storageOpened(support);
            }

            @Override
            public void closed(Block support, boolean animate) {
                furniture.storageClosed(support, animate);
            }
        });
        this.crops = new CropService(this, items, itemFactory, cropStore);
        this.cropTicker = new CropTicker(this, crops, settings.crops().tickSeconds());
        furniture.stopAt(block -> crops.at(block).isPresent());
        crops.stopAt(block -> furniture.identify(block).isPresent());
        this.menus = new ContentMenus(getServer(), items, itemFactory);
        this.advancements = new AdvancementService(this, items, itemFactory, database);
        this.actionBars = new ActionBars(() -> getServer().getCurrentTick());
        this.guns = new GunService(this, items, itemFactory, hooks, actionBars, settings.guns());
        hooks.addPlaceholders(guns::placeholder);
        this.liquids = new LiquidService(this, liquidRegistry, protection, database, settings.liquids());
        this.huds = new HudService(this, hudRegistry, database, actionBars, settings.huds(), hooks::placeholders);
        hooks.addPlaceholders(huds::placeholder);
        this.sanity = new SanityChecker(this, settings.sanity(), database, placed, transforms, furniture, blockService,
                liquids, liquidRegistry);

        PluginManager plugins = getServer().getPluginManager();
        plugins.registerEvents(new PackDeliveryListener(delivery, settings, getLogger()), this);
        plugins.registerEvents(new ItemUseListener(itemFactory), this);
        plugins.registerEvents(new ItemCombatListener(itemFactory), this);
        plugins.registerEvents(new WorldInterceptionListener(itemFactory), this);
        plugins.registerEvents(new CustomBlockListener(blockService, itemFactory, protection, hooks), this);
        plugins.registerEvents(new FurnitureListener(this, furniture, seats, storage, protection, itemFactory), this);
        plugins.registerEvents(new CropListener(this, crops, itemFactory, protection, hooks), this);
        plugins.registerEvents(new ChatEmojiListener(emojis), this);
        plugins.registerEvents(new PlacedContentListener(this, placed, transforms, blockService, furniture,
                getLogger()), this);
        plugins.registerEvents(new EditorListener(editor), this);
        plugins.registerEvents(webHost, this);
        plugins.registerEvents(new ContentMenuListener(this), this);
        plugins.registerEvents(new AdvancementListener(this, advancements), this);
        plugins.registerEvents(new GunListener(this, guns), this);
        plugins.registerEvents(new LiquidListener(liquids, liquidRegistry, itemFactory, protection), this);
        plugins.registerEvents(new HudListener(huds, itemFactory), this);

        openStorage();

        // A Paper plugin has no commands: section in its yml; commands are Brigadier trees
        // registered here, and re-registered by the server whenever it reloads its command tree.
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            commands.register(new CustomGiveCommand(items, itemFactory).build(),
                    "Give custom items to players");
            commands.register(new ContentAdminCommand(this).build(),
                    "Rebuild or inspect the custom content", List.of("acontent"));
            commands.register(new EmojiCommand(emojis).build(), "List the chat emojis", List.of("emoji"));
        });

        // Queued on the worker ahead of the first rebuild; a port that will not open never stops the start.
        webHost.start();
        // Everything from here happens off the main thread; onEnable returns straight away.
        pipeline.rebuild();
        cropTicker.start();
        storage.start();
        // Queued behind the database opening, like the rest of storage.
        liquids.start(getServer().getWorlds());
        huds.start();
        // The first audit an interval from now, driven from the database's thread.
        sanity.start();
    }

    /**
     * Opens the database and reads every loaded world's rows - all of it on the database's own
     * thread. The worlds are listed here, on the main thread; worlds loading later are read by
     * {@link PlacedContentListener}. Until a world's rows arrive, blocks are still recognised by
     * their state and furniture by its chunk link.
     */
    private void openStorage() {
        List<UUID> worlds = getServer().getWorlds().stream().map(World::getUID).toList();
        getServer().getWorlds().forEach(crops::worldLoaded);
        database.open().exceptionally(error -> {
            Throwable cause = error.getCause() != null ? error.getCause() : error;
            getLogger().severe("Could not open the placed content database: " + cause.getMessage()
                    + ". Custom blocks and furniture still work, but where they stand is not being saved.");
            return null;
        });
        // Queued behind open() on the same thread, so they run once the file is ready.
        placed.loadWorlds(worlds).whenComplete((ignored, error) -> {
            if (error == null) {
                getLogger().info(placed.size() + " placed custom block(s) and furniture loaded from "
                        + worlds.size() + " world(s).");
            } else {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                getLogger().warning("Could not read placed content: " + cause.getMessage()
                        + ". Blocks are still recognised by their state and furniture by its chunk link.");
            }
        });
        for (UUID world : worlds) {
            loadTransforms(world);
        }
        CompletableFuture.allOf(worlds.stream().map(world -> cropStore.loadWorld(world).exceptionally(error -> {
            getLogger().warning("Could not read the crops of world " + world + ": " + error.getMessage());
            return 0;
        })).toArray(CompletableFuture[]::new)).thenRun(() ->
                getLogger().info(cropStore.size() + " crop(s) loaded from " + worlds.size() + " world(s)."));
    }

    /**
     * Reads the pieces of furniture of one world that the editor gave a look of their own, and
     * redraws those already loaded - their displays may have loaded before the rows did.
     */
    public void loadTransforms(UUID world) {
        transforms.loadWorld(world).whenCompleteAsync((loaded, error) -> {
            if (error != null) {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                getLogger().warning("Could not read the furniture transforms of world " + world + ": "
                        + cause.getMessage() + ". Those pieces are drawn as their type says until the next start.");
                return;
            }
            World loadedWorld = getServer().getWorld(world);
            if (loadedWorld != null) {
                redraw(loadedWorld, loaded);
            }
        }, pipeline.mainThread()).exceptionally(error -> {
            getLogger().log(java.util.logging.Level.WARNING, "Could not redraw the furniture transforms of world "
                    + world, error);
            return null;
        });
    }

    private void redraw(World world, Collection<FurnitureTransform> loaded) {
        for (FurnitureTransform transform : List.copyOf(loaded)) {
            furniture.redraw(world, transform.x(), transform.y(), transform.z());
        }
    }

    @Override
    public void onDisable() {
        // First, while the worker and the database still run: open editors are saved, saves in flight waited for.
        if (editor != null) {
            editor.shutdown();
        }
        if (sanity != null) {
            sanity.stop();
        }
        if (menus != null) {
            menus.closeAll();
        }
        if (seats != null) {
            seats.releaseAll();
        }
        // Open storage is saved - queued ahead of the database closing below - and closed.
        if (storage != null) {
            storage.shutdown();
        }
        if (animations != null) {
            animations.stopAll();
        }
        // Rounds still in memory go onto the guns before the players' inventories are saved.
        if (guns != null) {
            guns.shutdown();
        }
        if (liquids != null) {
            liquids.shutdown();
        }
        // HUD values are queued for writing ahead of the database closing below.
        if (huds != null) {
            huds.shutdown();
        }
        if (hooks != null) {
            hooks.disable();
        }
        if (cropTicker != null) {
            cropTicker.stop();
        }
        // Progress within a stage lives in memory; queued here, it is written before the database closes.
        if (cropStore != null) {
            cropStore.worlds().forEach(cropStore::saveWorld);
        }
        if (pipeline != null) {
            pipeline.shutdown();
        }
        // The one wait on storage: queued writes are flushed rather than lost.
        if (database != null) {
            database.close();
        }
        if (webHost != null) {
            webHost.stop();
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

    /** Every loaded custom block, by id and by note block state. */
    public BlockRegistry blocks() {
        return blocks;
    }

    /** Custom blocks in the world; null until the plugin has enabled. */
    public CustomBlockService customBlocks() {
        return blockService;
    }

    /** Where custom blocks and furniture stand, answered from memory. */
    public PlacedContentStore placed() {
        return placed;
    }

    public PackDelivery delivery() {
        return delivery;
    }

    /** Integrations with other plugins, whichever are installed. */
    public HookManager hooks() {
        return hooks;
    }

    /** The installed protection plugins, asked as one. */
    public Protection protection() {
        return protection;
    }

    /** Storage furniture's inventories. */
    public StorageService storage() {
        return storage;
    }

    /** Placed furniture: its displays and animations. */
    public FurnitureService furniture() {
        return furniture;
    }

    /** The pieces of furniture given a look of their own in the editor. */
    public FurnitureTransformStore transforms() {
        return transforms;
    }

    /** The cyclic audit of loaded chunks. Null before enable. */
    public SanityChecker sanity() {
        return sanity;
    }

    /** {@code /arkcontent editor}. Null before enable. */
    public FurnitureEditor editor() {
        return editor;
    }

    /** Every loaded chat emoji. */
    public EmojiRegistry emojis() {
        return emojis;
    }

    /** Planted crops. */
    public CropService crops() {
        return crops;
    }

    /** The plugin's advancements on the server; null before enable. */
    public AdvancementService advancements() {
        return advancements;
    }

    /** Guns: shooting, magazines, reloading. Null before enable. */
    public GunService guns() {
        return guns;
    }

    /** Every loaded liquid, by id and by state. */
    public LiquidRegistry liquidRegistry() {
        return liquidRegistry;
    }

    /** Liquids poured in the world. Null before enable. */
    public LiquidService liquids() {
        return liquids;
    }

    /** Every loaded HUD. */
    public HudRegistry hudRegistry() {
        return hudRegistry;
    }

    /** HUD values and bars. Null before enable. */
    public HudService huds() {
        return huds;
    }

    /** The action bar, shared by HUD bars and short messages. Null before enable. */
    public ActionBars actionBars() {
        return actionBars;
    }

    /** Opens and refreshes the content browser. */
    public ContentMenus menus() {
        return menus;
    }

    public ContentPipeline pipeline() {
        return pipeline;
    }

    public boolean httpRunning() {
        return webHost != null && webHost.running();
    }

    /** The built-in web server: its port, and the address players download from. */
    public PackWebHost webHost() {
        return webHost;
    }
}
