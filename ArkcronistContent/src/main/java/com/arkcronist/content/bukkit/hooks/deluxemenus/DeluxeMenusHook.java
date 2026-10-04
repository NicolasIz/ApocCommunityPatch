package com.arkcronist.content.bukkit.hooks.deluxemenus;

import com.arkcronist.content.bukkit.hooks.ContentAccess;
import com.extendedclip.deluxemenus.DeluxeMenus;
import com.extendedclip.deluxemenus.config.DeluxeMenusConfig;
import com.extendedclip.deluxemenus.hooks.ItemHook;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * DeluxeMenus: menu icons that are this plugin's items, the way DeluxeMenus takes ItemsAdder's
 * ({@code itemsadder-<id>}) or Nexo's - by a material with the plugin's prefix:
 *
 * <pre>
 * items:
 *   ruby:
 *     material: arkcontent-demo:ruby
 *     slot: 13
 * </pre>
 *
 * <p>The icon is the item itself, model and all; a {@code has item} requirement on
 * {@code arkcontent-demo:ruby} is met by the real item and by nothing that only looks like it.</p>
 *
 * <p>DeluxeMenus checks every menu's materials as it enables, against a list of prefixes it copies
 * from its item hooks once, and makes its table of hooks in the same step - so there is no moment to
 * add this one before its menus are read, and a menu using {@code arkcontent-} is turned down then.
 * So the hook goes into the table, its prefix into that list, and if any menu file names
 * {@code arkcontent-}, DeluxeMenus is reloaded (its own {@code /dm reload}, which keeps both) one
 * tick later, when every plugin has enabled.</p>
 *
 * <p>DeluxeMenus has no API artifact; the two types used here are compiled against signatures
 * copied from 1.14.1, as for Iris and MMOItems.</p>
 */
public final class DeluxeMenusHook implements ItemHook {

    static final String KEY = "arkcontent";

    private final ContentAccess content;

    private DeluxeMenusHook(ContentAccess content) {
        this.content = content;
    }

    /** Adds the hook to DeluxeMenus' item hooks, next to its own ItemsAdder and Nexo ones. */
    public static Boolean register(ContentAccess content, Plugin plugin) {
        Server server = plugin.getServer();
        DeluxeMenus menus = (DeluxeMenus) server.getPluginManager().getPlugin("DeluxeMenus");
        if (menus == null) {
            throw new IllegalStateException("DeluxeMenus is not loaded");
        }
        menus.getItemHooks().put(KEY, new DeluxeMenusHook(content));
        if (!DeluxeMenusConfig.VALID_MATERIAL_PREFIXES.contains(KEY + "-")) {
            DeluxeMenusConfig.VALID_MATERIAL_PREFIXES.add(KEY + "-");
        }
        if (menus.isEnabled() && namesThisPlugin(menus.getDataFolder().toPath().resolve("gui_menus"))) {
            server.getScheduler().runTask(plugin, () -> {
                plugin.getLogger().info("Reloading DeluxeMenus so that its menus with " + KEY
                        + "- materials are read again, now that it knows them.");
                server.dispatchCommand(server.getConsoleSender(), "deluxemenus reload");
            });
        }
        return Boolean.TRUE;
    }

    /** Whether any menu file asks for one of this plugin's items. */
    private static boolean namesThisPlugin(Path folder) {
        if (!Files.isDirectory(folder)) {
            return false;
        }
        try (Stream<Path> files = Files.walk(folder)) {
            return files.filter(Files::isRegularFile).anyMatch(file -> {
                try {
                    return Files.readString(file).contains(KEY + "-");
                } catch (IOException | UncheckedIOException exception) {
                    return false;
                }
            });
        } catch (IOException | UncheckedIOException exception) {
            return false;
        }
    }

    /** @param arguments the material after {@code arkcontent-}: an item id */
    @Override
    public ItemStack getItem(String... arguments) {
        // What DeluxeMenus draws for an id its hook does not know.
        return arguments.length == 0 ? new ItemStack(Material.STONE)
                : content.item(arguments[0], 1).orElseGet(() -> new ItemStack(Material.STONE));
    }

    @Override
    public boolean itemMatchesIdentifiers(ItemStack item, String... arguments) {
        if (arguments.length == 0) {
            return false;
        }
        // Same id, written in full or bare.
        return content.id(item).filter(id -> content.item(arguments[0], 1)
                .flatMap(content::id).filter(id::equals).isPresent()).isPresent();
    }

    @Override
    public String getPrefix() {
        return KEY + "-";
    }
}
