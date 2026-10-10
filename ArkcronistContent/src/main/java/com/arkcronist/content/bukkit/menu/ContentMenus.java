package com.arkcronist.content.bukkit.menu;

import com.arkcronist.content.bukkit.EngineSettings;
import com.arkcronist.content.bukkit.item.ItemFactory;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import com.arkcronist.content.bukkit.text.FontImageRegistry;
import com.arkcronist.content.core.hud.Spaces;
import com.arkcronist.content.core.menu.MenuTitle;
import com.arkcronist.content.core.pack.FontImages;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Opens content browsers, and keeps the open ones honest: refreshed when the content is rebuilt,
 * closed when the plugin goes away. Main thread only.
 */
public final class ContentMenus {

    /** The colour vanilla draws a container's title in. */
    public static final TextColor TITLE_GREY = TextColor.color(0x404040);

    private final Server server;
    private final ItemRegistry registry;
    private final ItemFactory factory;
    private final EngineSettings.Menus settings;
    private final FontImageRegistry fontImages;
    private final Path contentsDir;

    public ContentMenus(Server server, ItemRegistry registry, ItemFactory factory, EngineSettings.Menus settings,
                        FontImageRegistry fontImages, Path contentsDir) {
        this.server = server;
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
        if (textured) {
            Optional<FontImages.Image> image = fontImages.current().find(background);
            if (image.isPresent()) {
                Component picture = Component.text(MenuTitle.background(image.get().glyph(), width), NamedTextColor.WHITE)
                        .decoration(TextDecoration.BOLD, false).decoration(TextDecoration.ITALIC, false);
                if (!image.get().inDefaultFont()) {
                    // Only the picture is in its own font; the spaces around it are in the default one.
                    picture = Component.text(Spaces.of(-MenuTitle.TITLE_X))
                            .append(Component.text(image.get().glyph(), NamedTextColor.WHITE)
                                    .font(Key.key(image.get().font())))
                            .append(Component.text(Spaces.of(MenuTitle.TITLE_X - (width + 1))));
                }
                return Component.empty().append(picture).append(text.colorIfAbsent(TITLE_GREY));
            }
        }
        return text;
    }

    private Component title(String text) {
        return textured(fontImages, settings.textured(), settings.background(), settings.backgroundWidth(),
                Component.text(text));
    }

    public void open(Player player) {
        open(player, null);
    }

    /** The browser as a shop: only what has a price, bought rather than taken. */
    public void openShop(Player player, Shop shop) {
        open(player, shop);
    }

    private void open(Player player, @Nullable Shop shop) {
        ContentMenu menu = new ContentMenu(registry, factory, shop, settings.folders() ? contentsDir : null,
                title(shop == null ? "Custom content" : "Shop"));
        menu.render(player);
        player.openInventory(menu.getInventory());
    }

    /** After a rebuild: items may have come, gone or changed. */
    public void refreshOpen() {
        for (Player player : server.getOnlinePlayers()) {
            ContentMenu menu = of(player.getOpenInventory());
            if (menu != null) {
                menu.render(player);
            }
        }
    }

    /**
     * On disable. The listener that keeps items in the menu is about to be unregistered; a menu
     * left open after that would be an ordinary chest full of free custom items.
     */
    public void closeAll() {
        for (Player player : server.getOnlinePlayers()) {
            if (of(player.getOpenInventory()) != null) {
                player.closeInventory();
            }
        }
    }

    /** The content browser shown in {@code view}, if that is what it is. */
    static @Nullable ContentMenu of(InventoryView view) {
        return view.getTopInventory().getHolder(false) instanceof ContentMenu menu ? menu : null;
    }
}
