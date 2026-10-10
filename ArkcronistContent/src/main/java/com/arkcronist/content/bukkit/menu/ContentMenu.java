package com.arkcronist.content.bukkit.menu;

import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemFactory;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import com.arkcronist.content.core.definition.ContentType;
import com.arkcronist.content.core.menu.ContentTree;
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
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * One player's content browser: a 54-slot chest view of the loaded custom items, organised the way
 * {@code contents/} is - one folder per content pack, {@code dragones_epicos}, {@code arkespadas},
 * each opening onto its own items and sub-folders (see {@link ContentTree}) - 45 entries to a page.
 *
 * <pre>
 *   rows 1-5   the folder's sub-folders, then its items - real stacks from {@link ItemFactory}
 *   row 6      [prev] [up] [type] [home] [page] [ ] [ ] [close] [next]
 * </pre>
 *
 * <p>The type button cycles what is listed: everything, or only items, blocks, furniture, crops or
 * liquids - a folder is listed while it holds any of them. With {@code menus.folders: false} it is
 * one flat list, as it was before 1.9.</p>
 *
 * <p>The inventory belongs to no block or entity; this object is its holder, which is how the
 * listener recognises it, and it keeps what sits in each slot, so a click is resolved by id against
 * the registry of that moment rather than by trusting the stack in the slot.</p>
 *
 * <p>Opened as a {@link Shop shop}, it shows only the items with a {@code price}, the price under
 * each, and a click buys instead of taking: one, or a stack on a shift-click - as many as fit, paid
 * for before they are handed over.</p>
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
    static final int UP = 46;
    static final int TYPE = 47;
    static final int HOME = 48;
    static final int PAGE_INFO = 49;
    static final int CLOSE = 52;
    static final int NEXT = 53;

    /**
     * The clicks that take a copy. Not {@link ClickType#isLeftClick()}: that also counts a
     * double-click, which arrives on top of the two clicks it is made of.
     */
    private static final Set<ClickType> TAKING = EnumSet.of(ClickType.LEFT, ClickType.RIGHT,
            ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT);

    /** What a click in the menu asked for; the listener carries out the parts that must wait a tick. */
    enum Outcome { NOTHING, CLOSE }

    /** What one of the 45 slots shows: a folder to open, or an item to take. */
    private sealed interface Entry {
        record Folder(ContentTree.Node node) implements Entry {
        }

        record Item(String id) implements Entry {
        }
    }

    private final ItemRegistry registry;
    private final ItemFactory factory;
    /** Null for the browser; the economy, for the shop. */
    private final @Nullable Shop shop;
    private final @Nullable Path contentsDir;
    private final boolean folders;
    private final Inventory inventory;
    private final Entry[] shown = new Entry[PAGE_SIZE];

    /** The folders opened, from the root down; the last is the one shown. */
    private final List<String> trail = new ArrayList<>(List.of(""));
    /** Null for everything. */
    private @Nullable ContentType type;
    private int page;
    /** How many pages the folder had at the last render. */
    private int pages = 1;

    /**
     * @param contentsDir where the content packs are, to tell which folder an item comes from; null
     *                    for one flat list
     */
    ContentMenu(ItemRegistry registry, ItemFactory factory, @Nullable Shop shop, @Nullable Path contentsDir,
                Component title) {
        this.registry = registry;
        this.factory = factory;
        this.shop = shop;
        this.contentsDir = contentsDir;
        this.folders = contentsDir != null;
        this.inventory = Bukkit.createInventory(this, SIZE, title);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    /** Fills every slot from the registry as it is now. */
    void render(Player viewer) {
        List<CustomItem> listed = registry.all().stream().filter(this::listed).toList();
        ContentTree.Node folder = currentFolder(listed);
        Predicate<CustomItem> ofType = item -> type == null || item.definition().type() == type;

        List<Entry> entries = new ArrayList<>();
        for (ContentTree.Node child : folder.folders()) {
            if (count(child, ofType) > 0) {
                entries.add(new Entry.Folder(child));
            }
        }
        for (String id : folder.items()) {
            registry.get(id).filter(ofType).ifPresent(item -> entries.add(new Entry.Item(item.id())));
        }
        Page<Entry> current = Page.of(entries, page, PAGE_SIZE);
        page = current.index();
        pages = current.count();

        inventory.clear();
        Arrays.fill(shown, null);
        for (int slot = 0; slot < current.entries().size(); slot++) {
            Entry entry = current.entries().get(slot);
            ItemStack stack = switch (entry) {
                case Entry.Folder child -> folderIcon(child.node(), ofType);
                case Entry.Item item -> registry.get(item.id()).map(this::display).orElse(null);
            };
            if (stack != null) {
                shown[slot] = entry;
                inventory.setItem(slot, stack);
            }
        }
        if (entries.isEmpty()) {
            inventory.setItem(22, icon(Material.STRUCTURE_VOID, "Nothing here", NamedTextColor.GRAY, false,
                    List.of(type == null ? "No content is loaded." : "No " + label(type).toLowerCase(Locale.ROOT)
                            + " here.")));
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
        if (trail.size() > 1) {
            inventory.setItem(UP, icon(Material.SPECTRAL_ARROW, "Back", NamedTextColor.YELLOW, false,
                    List.of("Up to " + (trail.size() > 2 ? nameOf(listed, trail.get(trail.size() - 2)) : "all packs"))));
            inventory.setItem(HOME, icon(Material.COMPASS, "All packs", NamedTextColor.YELLOW, false,
                    List.of("Back to the first page")));
        }
        List<String> types = new ArrayList<>();
        for (ContentType option : typeCycle()) {
            types.add((option == type ? "» " : "  ") + (option == null ? "Everything" : label(option)));
        }
        types.add("Click to change");
        inventory.setItem(TYPE, icon(type == null ? Material.HOPPER : tabIcon(type),
                "Showing: " + (type == null ? "everything" : label(type).toLowerCase(Locale.ROOT)),
                NamedTextColor.GOLD, type != null, types));

        List<String> help = new ArrayList<>();
        help.add(folder.path().isEmpty() ? (folders ? "All content packs" : "All content")
                : "Folder: contents/" + folder.path());
        help.add(current.total() + " entr" + (current.total() == 1 ? "y" : "ies") + ", " + PAGE_SIZE + " per page");
        if (shop != null) {
            help.add("Click an item: buy one");
            help.add("Shift-click: buy a stack");
        } else if (viewer.hasPermission(GIVE_PERMISSION)) {
            help.add("Click an item: take one");
            help.add("Shift-click: take a stack");
        } else {
            help.add("Browsing only: taking items needs " + GIVE_PERMISSION);
        }
        inventory.setItem(PAGE_INFO, icon(Material.BOOK, (folder.path().isEmpty() ? "Packs" : folder.name())
                        + " - page " + (current.index() + 1) + " of " + current.count(),
                NamedTextColor.AQUA, false, help));
        inventory.setItem(CLOSE, icon(Material.BARRIER, "Close", NamedTextColor.RED, false, List.of()));
    }

    /** A click on {@code slot} of the menu itself. The listener has already cancelled it. */
    Outcome click(Player player, int slot, ClickType click) {
        if (slot >= 0 && slot < PAGE_SIZE) {
            Entry entry = shown[slot];
            if (entry instanceof Entry.Folder folder && TAKING.contains(click)) {
                trail.add(folder.node().path());
                page = 0;
            } else if (entry instanceof Entry.Item item) {
                take(player, item.id(), click);
                return Outcome.NOTHING;
            } else {
                return Outcome.NOTHING;
            }
        } else if (slot == TYPE) {
            List<ContentType> cycle = typeCycle();
            int at = cycle.indexOf(type);
            type = cycle.get(Math.floorMod(at + (click.isRightClick() ? -1 : 1), cycle.size()));
            page = 0;
        } else if (slot == UP && trail.size() > 1) {
            trail.remove(trail.size() - 1);
            page = 0;
        } else if (slot == HOME && trail.size() > 1) {
            trail.subList(1, trail.size()).clear();
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
     * The folder the trail ends at, in the tree as it is now: after a rebuild that emptied or moved
     * a folder, the deepest one of the trail still there.
     */
    private ContentTree.Node currentFolder(List<CustomItem> listed) {
        ContentTree.Node root = ContentTree.build(foldersOf(listed));
        for (int i = trail.size() - 1; i > 0; i--) {
            ContentTree.Node found = root.find(trail.get(i));
            if (found != null) {
                trail.subList(i + 1, trail.size()).clear();
                return found;
            }
        }
        trail.subList(1, trail.size()).clear();
        return root;
    }

    /** Each item and its file's folder under contents/ - all in one, for a flat list. */
    private Map<String, String> foldersOf(List<CustomItem> items) {
        Map<String, String> folderOf = new LinkedHashMap<>();
        for (CustomItem item : items) {
            folderOf.put(item.id(), folders ? folderOf(item) : "");
        }
        return folderOf;
    }

    private String folderOf(CustomItem item) {
        Path source = item.definition().source();
        Path parent = source == null ? null : source.toAbsolutePath().normalize().getParent();
        Path root = contentsDir.toAbsolutePath().normalize();
        if (parent == null || !parent.startsWith(root)) {
            return "";
        }
        return root.relativize(parent).toString().replace(parent.getFileSystem().getSeparator(), "/");
    }

    private String nameOf(List<CustomItem> listed, String path) {
        ContentTree.Node node = ContentTree.build(foldersOf(listed)).find(path);
        return node == null ? path : node.name();
    }

    private boolean listed(CustomItem item) {
        return shop == null || item.definition().price() > 0;
    }

    /** How many items of the shown type a folder holds, sub-folders included. */
    private int count(ContentTree.Node node, Predicate<CustomItem> ofType) {
        int count = 0;
        for (String id : node.items()) {
            if (registry.get(id).filter(ofType).isPresent()) {
                count++;
            }
        }
        for (ContentTree.Node child : node.folders()) {
            count += count(child, ofType);
        }
        return count;
    }

    /** A folder: drawn as its first item, named after it, with what it holds underneath. */
    private ItemStack folderIcon(ContentTree.Node node, Predicate<CustomItem> ofType) {
        CustomItem first = firstItem(node, ofType);
        ItemStack stack = first == null ? ItemStack.of(Material.CHEST) : factory.create(first, 1);
        int items = count(node, ofType);
        stack.editMeta(meta -> {
            meta.itemName(Component.text(node.name(), NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true));
            meta.customName(null);
            List<Component> lore = new ArrayList<>();
            lore.add(line(items + " " + (type == null ? "item" + (items == 1 ? "" : "s")
                    : label(type).toLowerCase(Locale.ROOT)), NamedTextColor.GRAY));
            if (!node.folders().isEmpty()) {
                lore.add(line(node.folders().size() + " folder" + (node.folders().size() == 1 ? "" : "s"),
                        NamedTextColor.GRAY));
            }
            lore.add(line("contents/" + node.path(), NamedTextColor.DARK_GRAY));
            lore.add(Component.empty());
            lore.add(line("Click to open", NamedTextColor.YELLOW));
            meta.lore(lore);
        });
        return stack;
    }

    private @Nullable CustomItem firstItem(ContentTree.Node node, Predicate<CustomItem> ofType) {
        for (String id : node.items()) {
            Optional<CustomItem> item = registry.get(id).filter(ofType);
            if (item.isPresent()) {
                return item.get();
            }
        }
        for (ContentTree.Node child : node.folders()) {
            CustomItem found = firstItem(child, ofType);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static List<ContentType> typeCycle() {
        List<ContentType> cycle = new ArrayList<>();
        cycle.add(null);
        cycle.addAll(List.of(ContentType.ITEM, ContentType.CUSTOM_BLOCK, ContentType.CUSTOM_FURNITURE,
                ContentType.CUSTOM_CROP, ContentType.CUSTOM_LIQUID));
        return cycle;
    }

    /**
     * A fresh stack of the clicked item into the player's own inventory: one, or a full stack on a
     * shift-click. Never onto the cursor, and never dropped at their feet.
     */
    private void take(Player player, String id, ClickType click) {
        if (!TAKING.contains(click)) {
            return;
        }
        if (shop != null) {
            buy(player, id, click);
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

    /**
     * Buys the clicked item: as many as asked for that fit in the player's inventory, charged before
     * they are handed over, so nothing is ever paid for and lost, or handed over unpaid.
     */
    private void buy(Player player, String id, ClickType click) {
        Optional<CustomItem> item = registry.get(id);
        if (item.isEmpty() || item.get().definition().price() <= 0) {
            player.sendActionBar(Component.text(id + " is no longer for sale", NamedTextColor.RED));
            render(player);
            return;
        }
        int wanted = click.isShiftClick() ? item.get().material().getMaxStackSize() : 1;
        ItemStack stack = factory.create(item.get(), 1);
        int amount = Math.min(wanted, room(player, stack));
        if (amount == 0) {
            player.sendActionBar(Component.text("Your inventory is full", NamedTextColor.RED));
            return;
        }
        double cost = item.get().definition().price() * amount;
        if (!shop.charge(player, cost)) {
            return;
        }
        player.getInventory().addItem(factory.create(item.get(), amount));
        player.playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
        player.sendActionBar(Component.text("Bought " + amount + " x " + id + " for " + shop.format(cost),
                NamedTextColor.GREEN));
    }

    /** How many of {@code stack} the player's inventory can take: empty slots, and room on stacks of it. */
    static int room(Player player, ItemStack stack) {
        int max = stack.getMaxStackSize();
        int room = 0;
        for (ItemStack slot : player.getInventory().getStorageContents()) {
            if (slot == null || slot.isEmpty()) {
                room += max;
            } else if (slot.isSimilar(stack)) {
                room += Math.max(0, max - slot.getAmount());
            }
        }
        return room;
    }

    /** The real stack, plus its id underneath the lore, so an admin can type it. */
    private ItemStack display(CustomItem item) {
        ItemStack stack = factory.create(item, 1);
        stack.editMeta(meta -> {
            List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            if (!lore.isEmpty()) {
                lore.add(Component.empty());
            }
            lore.add(line(item.id(), NamedTextColor.DARK_GRAY));
            if (shop != null) {
                lore.add(line("Price: " + shop.format(item.definition().price()), NamedTextColor.GOLD));
            }
            meta.lore(lore);
        });
        return stack;
    }

    private static Component line(String text, NamedTextColor colour) {
        return Component.text(text, colour).decoration(TextDecoration.ITALIC, false);
    }

    static ItemStack icon(Material material, String name, NamedTextColor colour, boolean glint, List<String> lines) {
        ItemStack stack = ItemStack.of(material);
        stack.editMeta(meta -> {
            meta.itemName(Component.text(name, colour));
            if (!lines.isEmpty()) {
                meta.lore(lines.stream().map(line -> line(line, NamedTextColor.GRAY)).toList());
            }
            if (glint) {
                meta.setEnchantmentGlintOverride(true);
            }
        });
        return stack;
    }

    static ItemStack filler() {
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
            case CUSTOM_LIQUID -> Material.WATER_BUCKET;
        };
    }

    private static String label(ContentType type) {
        return switch (type) {
            case ITEM -> "Items";
            case CUSTOM_BLOCK -> "Blocks";
            case CUSTOM_FURNITURE -> "Furniture";
            case CUSTOM_CROP -> "Crops";
            case CUSTOM_LIQUID -> "Liquids";
        };
    }
}
