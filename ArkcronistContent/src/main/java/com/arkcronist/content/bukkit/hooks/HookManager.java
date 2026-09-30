package com.arkcronist.content.bukkit.hooks;

import com.arkcronist.content.bukkit.ArkContentPlugin;
import com.arkcronist.content.bukkit.hooks.iris.IrisHook;
import com.arkcronist.content.bukkit.hooks.modelengine.ModelEngineHook;
import com.arkcronist.content.bukkit.hooks.mythicarmor.MythicArmorHook;
import com.arkcronist.content.bukkit.hooks.mythicmobs.MythicMobsHook;
import com.arkcronist.content.bukkit.hooks.placeholderapi.ArkContentExpansion;
import com.arkcronist.content.bukkit.hooks.shopgui.ShopGuiPlusHook;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Integrations with other plugins, each switched on only when that plugin is there.
 *
 * <p>Every hook lives in its own package and is the only code that names the other plugin's
 * classes. Those classes are visible because paper-plugin.yml lists the plugin as an optional
 * dependency with {@code join-classpath}; when it is not installed they do not exist at all, so a
 * hook is created only after one of its classes has been found, and whatever goes wrong while
 * creating it - a missing class, an API that changed shape - is logged and leaves the rest of the
 * plugin running without that hook.</p>
 *
 * <p>Presence is decided by the classes rather than by the plugin being enabled: MythicMobs and
 * MythicArmor are made to load after this plugin, so that it can listen to them from the start,
 * and are not enabled yet when this runs.</p>
 */
public final class HookManager {

    private final ArkContentPlugin plugin;
    private final Logger logger;
    private final List<String> active = new ArrayList<>();
    private final List<ContentHook> contentHooks = new ArrayList<>();
    private ModelEngineBridge modelEngine;

    public HookManager(ArkContentPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /** Detects and starts every hook. Main thread, from onEnable. */
    public void enable() {
        MythicMobsHook mythicMobs = create("MythicMobs", "io.lumine.mythic.bukkit.events.MythicDropLoadEvent",
                () -> new MythicMobsHook(plugin.items(), plugin.itemFactory(), logger));
        if (mythicMobs != null) {
            listen(mythicMobs);
            contentHooks.add(mythicMobs);
        }

        modelEngine = create("ModelEngine", "com.ticxo.modelengine.api.ModelEngineAPI",
                () -> new ModelEngineHook(logger));

        MythicArmorHook mythicArmor = create("MythicArmor", "net.mythic.mythicArmor.api.MythicArmorGeneratePackEvent",
                () -> new MythicArmorHook(plugin, plugin.pipeline()));
        if (mythicArmor != null) {
            listen(mythicArmor);
        }

        // Loads before this plugin; registered at once, so TAB and DeluxeMenus find it from the start.
        create("PlaceholderAPI", "me.clip.placeholderapi.expansion.PlaceholderExpansion", () -> {
            ArkContentExpansion expansion = new ArkContentExpansion(plugin.getPluginMeta().getVersion(),
                    plugin.emojis(), plugin.items(), plugin.itemFactory());
            if (!expansion.register()) {
                throw new IllegalStateException("PlaceholderAPI refused the arkcontent expansion");
            }
            return expansion;
        });

        // Loads after this plugin: the hook listens for the moment ShopGUI+ asks for item providers.
        ShopGuiPlusHook shopGui = create("ShopGUI+", "net.brcdev.shopgui.event.ShopGUIPlusPostEnableEvent",
                () -> new ShopGuiPlusHook(plugin.items(), plugin.itemFactory(), logger));
        if (shopGui != null) {
            listen(shopGui);
        }

        // Iris loads first - it generates worlds - so its data service is running by now.
        create("Iris", "com.volmit.iris.core.link.ExternalDataProvider",
                () -> new IrisHook(plugin.getName(), plugin.blocks(), plugin.items(), plugin.itemFactory()));
    }

    /** The ModelEngine bridge, or null without ModelEngine. */
    public @Nullable ModelEngineBridge modelEngine() {
        return modelEngine;
    }

    /** Main thread, after every rebuild. */
    public void contentReloaded() {
        for (ContentHook hook : contentHooks) {
            try {
                hook.contentReloaded();
            } catch (RuntimeException | LinkageError error) {
                logger.log(Level.WARNING, "A plugin hook failed after the rebuild", error);
            }
        }
    }

    /** Names of the hooks that are running. */
    public List<String> active() {
        return List.copyOf(active);
    }

    private <T> T create(String name, String probeClass, Supplier<T> factory) {
        try {
            Class.forName(probeClass, false, getClass().getClassLoader());
        } catch (ClassNotFoundException | LinkageError absent) {
            return null;
        }
        try {
            T hook = factory.get();
            active.add(name);
            logger.info("Hooked into " + name + ".");
            return hook;
        } catch (RuntimeException | LinkageError error) {
            logger.log(Level.WARNING, name + " is installed but its hook could not start - carrying on without it."
                    + " A newer or older " + name + " than this plugin was built against may have changed its API.", error);
            return null;
        }
    }

    private void listen(Listener listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }
}
