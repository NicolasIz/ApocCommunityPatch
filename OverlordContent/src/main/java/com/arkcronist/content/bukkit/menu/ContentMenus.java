package com.arkcronist.content.bukkit.menu;

import com.arkcronist.content.bukkit.EngineSettings;
import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemFactory;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import com.arkcronist.content.bukkit.text.FontImageRegistry;
import com.arkcronist.content.core.hud.DefaultFontWidths;
import com.arkcronist.content.core.hud.Spaces;
import com.arkcronist.content.core.menu.ContentTree;
import com.arkcronist.content.core.menu.GuiPicture;
import com.arkcronist.content.core.menu.MenuTitle;
import com.arkcronist.content.core.pack.FontImages;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Opens content browsers, and keeps the open ones honest: refreshed when the content is rebuilt,
 * closed when the plugin goes away.
 *
 * <p>A browser's pages are laid out on the {@code ArkContent-Worker} thread - the folder tree of
 * every loaded item, what a folder holds of the type shown, the stacks of a page - and only put
 * into the inventory on the main thread, which is the only one Paper lets touch it. The folder tree
 * is built once per rebuild and kept, so most page turns only cut a page out of it.</p>
 */
public final class ContentMenus {

    /** The colour vanilla draws a container's title in. */
    public static final TextColor TITLE_GREY = TextColor.color(0x404040);

    /** A folder tree, and the registry's id list it was built from: a new list means a rebuild. */
    private record Catalog(List<String> source, ContentTree.Node root) {
    }

    private final Plugin plugin;
    private final Executor worker;
    private final ItemRegistry registry;
    private final ItemFactory factory;
    private final EngineSettings.Menus settings;
    private final FontImageRegistry fontImages;
    private final Path contentsDir;
    /** Read and written on the worker only; volatile so a thread swap of the executor would still see it. */
    private volatile @Nullable Catalog browsing;
    private volatile @Nullable Catalog selling;

    public ContentMenus(Plugin plugin, Executor worker, ItemRegistry registry, ItemFactory factory,
                        EngineSettings.Menus settings, FontImageRegistry fontImages, Path contentsDir) {
        this.plugin = plugin;
        this.worker = worker;
        this.registry = registry;
        this.factory = factory;
        this.settings = settings;
        this.fontImages = fontImages;
        this.contentsDir = contentsDir;
    }

    /**
     * A container title drawn over a picture - a font image - when there is one by that name in the
     * pack: the picture's character in white (a coloured title would tint it), placed at the
     * inventory's corner by negative spaces, then {@code text} in vanilla's title grey.
     */
    public static Component textured(FontImageRegistry fontImages, boolean textured, String background, int width,
                                     Component text) {
        return textured(fontImages, textured, background, new GuiPicture(background, width, 0, 0, 0), text,
                TITLE_GREY, false);
    }

    /**
     * As {@link #textured(FontImageRegistry, boolean, String, int, Component)}, for a picture placed
     * where {@code picture} says - wider than the chest, or above it - with the text in
     * {@code colour}, centred over the picture when {@code centred}. Without the picture in the pack,
     * the text alone, in vanilla's grey: a light colour would not read on the chest's own background.
     */
    public static Component textured(FontImageRegistry fontImages, boolean textured, String background,
                                     GuiPicture picture, Component text, TextColor colour, boolean centred) {
        if (textured) {
            Optional<FontImages.Image> image = fontImages.current().find(background);
            if (image.isPresent()) {
                String glyph = image.get().glyph();
                Component drawn = Component.text(MenuTitle.background(glyph, picture.width(), picture.left()),
                                NamedTextColor.WHITE)
                        .decoration(TextDecoration.BOLD, false).decoration(TextDecoration.ITALIC, false);
                if (!image.get().inDefaultFont()) {
                    // Only the picture is in its own font; the spaces around it are in the default one.
                    drawn = Component.text(Spaces.of(picture.left() - MenuTitle.TITLE_X))
                            .append(Component.text(glyph, NamedTextColor.WHITE).font(Key.key(image.get().font())))
                            .append(Component.text(Spaces.of(MenuTitle.TITLE_X - picture.left() - (picture.width() + 1))));
                }
                Component title = text.colorIfAbsent(colour);
                if (centred) {
                    int advance = DefaultFontWidths.width(PlainTextComponentSerializer.plainText().serialize(text), false);
                    title = Component.text(MenuTitle.centring(advance, picture.centre())).append(title);
                }
                return Component.empty().append(drawn).append(title);
            }
        }
        return text;
    }

    /** The browser's title: the open folder's name, or "Categories" (the shop's: "Shop") at the top. */
    Component title(@Nullable String folderName, boolean shop) {
        String text = folderName == null ? (shop ? "Shop" : "Categories") : words(folderName);
        return textured(fontImages, settings.textured(), settings.background(), settings.picture(), Component.text(text),
                settings.titleColour(), settings.titleCentred());
    }

    /** {@code dragones_epicos} as {@code Dragones Epicos}. */
    static String words(String name) {
        StringBuilder out = new StringBuilder();
        for (String word : name.split("[_\\-\\s]+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.isEmpty() ? name : out.toString();
    }

    public void open(Player player) {
        new ContentMenu(this, null).open(player);
    }

    /** The browser as a shop: only what has a price, bought rather than taken. */
    public void openShop(Player player, Shop shop) {
        new ContentMenu(this, shop).open(player);
    }

    /** After a rebuild: items may have come, gone or changed. */
    public void refreshOpen() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            ContentMenu menu = of(player.getOpenInventory());
            if (menu != null) {
                menu.request(player);
            }
        }
    }

    /**
     * On disable. The listener that keeps items in the menu is about to be unregistered; a menu
     * left open after that would be an ordinary chest full of free custom items.
     */
    public void closeAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (of(player.getOpenInventory()) != null) {
                player.closeInventory();
            }
        }
    }

    /** The content browser shown in {@code view}, if that is what it is. */
    static @Nullable ContentMenu of(InventoryView view) {
        return view.getTopInventory().getHolder(false) instanceof ContentMenu menu ? menu : null;
    }

    // ---------------------------------------------------------------- for the menus

    Executor worker() {
        return worker;
    }

    /** Runs {@code task} on the main thread next tick, unless the plugin is going away. */
    void onMain(Runnable task) {
        if (plugin.isEnabled()) {
            plugin.getServer().getScheduler().runTask(plugin, task);
        }
    }

    Plugin plugin() {
        return plugin;
    }

    ItemRegistry registry() {
        return registry;
    }

    ItemFactory factory() {
        return factory;
    }

    EngineSettings.Menus settings() {
        return settings;
    }

    /**
     * The folder tree of what is loaded - all of it, or what is for sale - built from the registry
     * as it is now, and kept until the next rebuild swaps the registry. Worker thread.
     */
    ContentTree.Node tree(boolean shop) {
        List<String> ids = registry.ids();
        Catalog cached = shop ? selling : browsing;
        if (cached != null && cached.source() == ids) {
            return cached.root();
        }
        Map<String, String> folderOf = new LinkedHashMap<>();
        for (String id : ids) {
            registry.get(id).filter(item -> !shop || item.definition().price() > 0)
                    .ifPresent(item -> folderOf.put(item.id(), settings.folders() ? folderOf(item) : ""));
        }
        Catalog fresh = new Catalog(ids, ContentTree.build(folderOf));
        if (shop) {
            selling = fresh;
        } else {
            browsing = fresh;
        }
        return fresh.root();
    }

    /** The item's file's folder under contents/, with forward slashes; "" outside it. */
    private String folderOf(CustomItem item) {
        Path source = item.definition().source();
        Path parent = source == null ? null : source.toAbsolutePath().normalize().getParent();
        Path root = contentsDir.toAbsolutePath().normalize();
        if (parent == null || !parent.startsWith(root)) {
            return "";
        }
        return root.relativize(parent).toString().replace(parent.getFileSystem().getSeparator(), "/");
    }
}
