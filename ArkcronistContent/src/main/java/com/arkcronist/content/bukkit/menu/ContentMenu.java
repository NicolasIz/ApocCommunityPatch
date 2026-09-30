package com.arkcronist.content.bukkit.menu;

import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemFactory;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import com.arkcronist.content.core.definition.ContentType;
import com.arkcronist.content.core.menu.Page;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * One player's content browser: a 54-slot chest view of every loaded custom item, split into
 * items, blocks, furniture and crops, 45 to a page.
 *
 * <pre>
 *   rows 1-5   the items themselves - real stacks from {@link ItemFactory}, item_model and all
 *   row 6      [prev] [ ] [Items] [Blocks] [Furniture] [Crops] [page] [close] [next]
 * </pre>
 *
 * <p>The inventory belongs to no block or entity; this object is its holder, which is how the
 * listener recognises it, and it keeps which item sits in which slot, so a click is resolved by id
 * against the registry of that moment rather than by trusting the stack in the slot.</p>
 *
 * <p>Main thread only, like any inventory.</p>
 */
public final class ContentMenu implements InventoryHolder {

    /** Needed to take copies out of the menu; browsing needs only {@link #BROWSE_PERMISSION}. */
    public static final String GIVE_PERMISSION = "arkcontent.give";
    public static final String BROWSE_PERMISSION = "arkcontent.menu";

    static final int SIZE = 54;
    static final int PAGE_SIZE = 45;
    static final int PREVIOUS = 45;
    static final int PAGE_INFO = 51;
    static final int CLOSE = 52;
    static final int NEXT = 53;
    private static final Map<Integer, ContentType> TABS = Map.of(
            47, ContentType.ITEM, 48, ContentType.CUSTOM_BLOCK, 49, ContentType.CUSTOM_FURNITURE,
            50, ContentType.CUSTOM_CROP);

    private static final Component TITLE = Component.text("Custom content");

    /**
     * The clicks that take a copy. Not {@link ClickType#isLeftClick()}: that also counts a
     * double-click, which arrives on top of the two clicks it is made of.
     */
    private static final Set<ClickType> TAKING = EnumSet.of(ClickType.LEFT, ClickType.RIGHT,
            ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT);

    /** What a click in the menu asked for; the listener carries out the parts that must wait a tick. */
    enum Outcome { NOTHING, CLOSE }

    private final ItemRegistry registry;
    private final ItemFactory factory;
    private final Inventory inventory;
    /** The item id shown in each of the first 45 slots, or null for an empty one. */
    private final String[] shown = new String[PAGE_SIZE];

    private ContentType category = ContentType.ITEM;
    private int page;
    /** How many pages the category had at the last render. */
    private int pages = 1;

    ContentMenu(ItemRegistry registry, ItemFactory factory) {
        this.registry = registry;
        this.factory = factory;
        this.inventory = Bukkit.createInventory(this, SIZE, TITLE);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    /** Fills every slot from the registry as it is now. */
    void render(Player viewer) {
        List<CustomItem> all = inCategory(category);
        Page<CustomItem> current = Page.of(all, page, PAGE_SIZE);
        page = current.index();
        pages = current.count();

        inventory.clear();
        Arrays.fill(shown, null);
        for (int slot = 0; slot < current.entries().size(); slot++) {
            CustomItem item = current.entries().get(slot);
            shown[slot] = item.id();
            inventory.setItem(slot, display(item));
        }
        if (all.isEmpty()) {
            inventory.setItem(22, icon(Material.STRUCTURE_VOID, "Nothing here yet", NamedTextColor.GRAY, false,
                    List.of("No " + label(category).toLowerCase(Locale.ROOT) + " are loaded.")));
        }

        ItemStack filler = filler();
        for (int slot = PAGE_SIZE; slot < SIZE; slot++) {
            inventory.setItem(slot, filler);
        }
        if (current.hasPrevious()) {
            inventory.setItem(PREVIOUS, icon(Material.ARROW, "Previous page", NamedTextColor.YELLOW, false, List.of()));
        }
        if (current.hasNext()) {
            inventory.setItem(NEXT, icon(Material.ARROW, "Next page", NamedTextColor.YELLOW, false, List.of()));
        }
        for (Map.Entry<Integer, ContentType> tab : TABS.entrySet()) {
            ContentType type = tab.getValue();
            boolean selected = type == category;
            inventory.setItem(tab.getKey(), icon(tabIcon(type), label(type) + " (" + inCategory(type).size() + ")",
                    selected ? NamedTextColor.GOLD : NamedTextColor.WHITE, selected,
                    List.of(selected ? "Showing" : "Click to show")));
        }
        List<String> help = new ArrayList<>();
        help.add(current.total() + " " + label(category).toLowerCase(Locale.ROOT) + ", " + PAGE_SIZE + " per page");
        if (viewer.hasPermission(GIVE_PERMISSION)) {
            help.add("Click an item: take one");
            help.add("Shift-click: take a stack");
        } else {
            help.add("Browsing only: taking items needs " + GIVE_PERMISSION);
        }
        inventory.setItem(PAGE_INFO, icon(Material.BOOK, "Page " + (current.index() + 1) + " of " + current.count(),
                NamedTextColor.AQUA, false, help));
        inventory.setItem(CLOSE, icon(Material.BARRIER, "Close", NamedTextColor.RED, false, List.of()));
    }

    /** A click on {@code slot} of the menu itself. The listener has already cancelled it. */
    Outcome click(Player player, int slot, ClickType click) {
        if (slot >= 0 && slot < PAGE_SIZE) {
            take(player, slot, click);
            return Outcome.NOTHING;
        }
        ContentType tab = TABS.get(slot);
        if (tab != null && tab != category) {
            category = tab;
            page = 0;
        } else if (slot == PREVIOUS && page > 0) {
            page--;
        } else if (slot == NEXT && page < pages - 1) {
            page++;
        } else if (slot == CLOSE) {
            return Outcome.CLOSE;
        } else {
            return Outcome.NOTHING;
        }
        player.playSound(player, Sound.UI_BUTTON_CLICK, 0.4f, 1f);
        render(player);
        return Outcome.NOTHING;
    }

    /**
     * A fresh stack of the clicked item into the player's own inventory: one, or a full stack on a
     * shift-click. Never onto the cursor, and never dropped at their feet.
     */
    private void take(Player player, int slot, ClickType click) {
        String id = shown[slot];
        if (id == null || !TAKING.contains(click)) {
            return;
        }
        if (!player.hasPermission(GIVE_PERMISSION)) {
            player.sendActionBar(Component.text("Taking items needs " + GIVE_PERMISSION, NamedTextColor.RED));
            return;
        }
        Optional<CustomItem> item = registry.get(id);
        if (item.isEmpty()) {
            player.sendActionBar(Component.text(id + " is no longer loaded", NamedTextColor.RED));
            render(player);
            return;
        }
        int amount = click.isShiftClick() ? item.get().material().getMaxStackSize() : 1;
        int leftOver = player.getInventory().addItem(factory.create(item.get(), amount)).values().stream()
                .mapToInt(ItemStack::getAmount)
                .sum();
        int taken = amount - leftOver;
        if (taken == 0) {
            player.sendActionBar(Component.text("Your inventory is full", NamedTextColor.RED));
            return;
        }
        player.playSound(player, Sound.ENTITY_ITEM_PICKUP, 0.4f, 1.2f);
        player.sendActionBar(Component.text("Took " + taken + " x " + id + (leftOver > 0 ? " - inventory full" : ""),
                NamedTextColor.GREEN));
    }

    private List<CustomItem> inCategory(ContentType type) {
        return registry.all().stream()
                .filter(item -> item.definition().type() == type)
                .sorted(Comparator.comparing(CustomItem::id))
                .toList();
    }

    /** The real stack, plus its id underneath the lore, so an admin can type it. */
    private ItemStack display(CustomItem item) {
        ItemStack stack = factory.create(item, 1);
        stack.editMeta(meta -> {
            List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            if (!lore.isEmpty()) {
                lore.add(Component.empty());
            }
            lore.add(Component.text(item.id(), NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        });
        return stack;
    }

    private static ItemStack icon(Material material, String name, NamedTextColor colour, boolean glint, List<String> lines) {
        ItemStack stack = ItemStack.of(material);
        stack.editMeta(meta -> {
            meta.itemName(Component.text(name, colour));
            if (!lines.isEmpty()) {
                meta.lore(lines.stream()
                        .map(line -> (Component) Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
                        .toList());
            }
            if (glint) {
                meta.setEnchantmentGlintOverride(true);
            }
        });
        return stack;
    }

    private static ItemStack filler() {
        ItemStack stack = ItemStack.of(Material.GRAY_STAINED_GLASS_PANE);
        stack.editMeta(meta -> meta.setHideTooltip(true));
        return stack;
    }

    private static Material tabIcon(ContentType type) {
        return switch (type) {
            case ITEM -> Material.DIAMOND;
            case CUSTOM_BLOCK -> Material.BRICKS;
            case CUSTOM_FURNITURE -> Material.PAINTING;
            case CUSTOM_CROP -> Material.WHEAT;
        };
    }

    private static String label(ContentType type) {
        return switch (type) {
            case ITEM -> "Items";
            case CUSTOM_BLOCK -> "Blocks";
            case CUSTOM_FURNITURE -> "Furniture";
            case CUSTOM_CROP -> "Crops";
        };
    }
}
