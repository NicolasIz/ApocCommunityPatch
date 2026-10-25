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
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * One player's content browser: a 54-slot chest view of the loaded custom items, organised the way
 * {@code contents/} is - one folder per content pack, {@code dragones_epicos}, {@code arkespadas},
 * each opening onto its own items and sub-folders (see {@link ContentTree}).
 *
 * <pre>
 *   rows 1-5   the folder's sub-folders, then its items - real stacks from {@link ItemFactory} -
 *              in the slots menus.content-slots names (all 45 by default)
 *   row 6      [prev] [up] [all] [type] [close] [page] [ ] [ ] [next]
 * </pre>
 *
 * <p>Drawn over the plugin's book, the title is the open folder's name ("Categories" at the top),
 * centred on the book's ribbon, and so a new folder is a new inventory: Paper cannot change a title
 * that holds a picture and colours in place. Turning a page keeps the inventory.</p>
 *
 * <p>Each page is laid out on the {@code ArkContent-Worker} thread - which folder the trail ends
 * at, what it holds of the type shown, the stacks for the page - and put into the inventory on the
 * main thread a tick later. While one is on its way, further page turns are ignored, so two quick
 * clicks never skip a page; a newer layout always wins over an older one.</p>
 *
 * <p>The inventory belongs to no block or entity; this object is its holder, which is how the
 * listener recognises it, and it keeps what sits in each slot, so a click is resolved by id against
 * the registry of that moment rather than by trusting the stack in the slot.</p>
 *
 * <p>Opened as a {@link Shop shop}, it shows only the items with a {@code price}, the price under
 * each, and a click buys instead of taking: one, or a stack on a shift-click - as many as fit, paid
 * for before they are handed over.</p>
 */
public final class ContentMenu implements InventoryHolder {

    /** Needed to take copies out of the menu; browsing needs only {@link #BROWSE_PERMISSION}. */
    public static final String GIVE_PERMISSION = "arkcontent.give";
    public static final String BROWSE_PERMISSION = "arkcontent.menu";

    static final int SIZE = 54;
    static final int CONTROLS = 45;
    static final int PREVIOUS = 45;
    static final int UP = 46;
    static final int HOME = 47;
    static final int TYPE = 48;
    static final int CLOSE = 49;
    static final int PAGE_INFO = 50;
    static final int NEXT = 53;

    /**
     * The clicks that take a copy. Not {@link ClickType#isLeftClick()}: that also counts a
     * double-click, which arrives on top of the two clicks it is made of.
     */
    private static final Set<ClickType> TAKING = EnumSet.of(ClickType.LEFT, ClickType.RIGHT,
            ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT);

    /** What a click in the menu asked for; the listener carries out the parts that must wait a tick. */
    enum Outcome { NOTHING, CLOSE }

    /** What one of the content slots shows: a folder to open, or an item to take. */
    sealed interface Entry {
        record Folder(String path) implements Entry {
        }

        record Item(String id) implements Entry {
        }
    }

    /** What a page is asked for with: everything the worker needs, copied off the main thread's state. */
    record Request(List<String> trail, @Nullable ContentType type, int page, boolean shop, int pageSize) {
    }

    /**
     * A page as the worker laid it out.
     *
     * @param trail      the trail as it stands in the current tree: a folder a rebuild removed is gone
     * @param folderPath the folder shown; "" at the top
     * @param folderName its name; null at the top
     * @param upName     the name of the folder "Back" leads to
     * @param stacks     one per entry, in order
     */
    record Laid(List<String> trail, String folderPath, @Nullable String folderName, String upName, int page, int pages,
                int total, List<Entry> entries, List<ItemStack> stacks) {
    }

    private final ContentMenus menus;
    private final ItemRegistry registry;
    private final ItemFactory factory;
    /** Null for the browser; the economy, for the shop. */
    private final @Nullable Shop shop;
    private final List<Integer> contentSlots;
    private final boolean textured;
    private Inventory inventory;
    /** The folder whose name the inventory's title shows. */
    private String titled = "";
    private final Map<Integer, Entry> shown = new HashMap<>();

    /** The folders opened, from the root down; the last is the one shown. */
    private final List<String> trail = new ArrayList<>(List.of(""));
    /** Null for everything. */
    private @Nullable ContentType type;
    private int page;
    /** How many pages the folder had at the last layout. */
    private int pages = 1;
    /** Each layout asked for gets the next number; only the newest one is put in the inventory. */
    private int generation;
    private boolean pending;

    ContentMenu(ContentMenus menus, @Nullable Shop shop) {
        this.menus = menus;
        this.registry = menus.registry();
        this.factory = menus.factory();
        this.shop = shop;
        this.contentSlots = menus.settings().contentSlots();
        this.textured = menus.settings().textured();
        this.inventory = Bukkit.createInventory(this, SIZE, menus.title(null, shop != null));
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    /** Shows the book at once; its entries follow when the worker has laid out the first page. */
    void open(Player player) {
        player.openInventory(inventory);
        request(player);
    }

    // ---------------------------------------------------------------- laying out a page

    /** Asks the worker for the page the menu now points at. Main thread. */
    void request(Player viewer) {
        int ticket = ++generation;
        pending = true;
        Request request = new Request(List.copyOf(trail), type, page, shop != null, contentSlots.size());
        UUID id = viewer.getUniqueId();
        CompletableFuture.supplyAsync(() -> lay(menus, request), menus.worker())
                .whenComplete((laid, error) -> menus.onMain(() -> {
                    if (ticket != generation) {
                        return;
                    }
                    pending = false;
                    Player player = Bukkit.getPlayer(id);
                    if (player == null || ContentMenus.of(player.getOpenInventory()) != this) {
                        return;
                    }
                    if (error != null) {
                        menus.plugin().getLogger().log(Level.WARNING, "Could not lay out the content browser", error);
                        player.sendActionBar(Component.text("The menu could not be drawn - see the console",
                                NamedTextColor.RED));
                        return;
                    }
                    apply(player, laid);
                }));
    }

    /**
     * The page for {@code request}, from the registry as it is now: which folder the trail ends at
     * (after a rebuild that emptied or moved one, the deepest of the trail still there), what it
     * holds of the type shown, and the stacks. Worker thread: reads only the registry, which a
     * rebuild swaps whole, and builds new stacks; touches no inventory.
     */
    static Laid lay(ContentMenus menus, Request request) {
        ItemRegistry registry = menus.registry();
        ContentTree.Node root = menus.tree(request.shop());
        List<String> kept = new ArrayList<>(List.of(""));
        ContentTree.Node folder = root;
        for (int i = request.trail().size() - 1; i > 0; i--) {
            ContentTree.Node found = root.find(request.trail().get(i));
            if (found != null) {
                folder = found;
                kept = new ArrayList<>(request.trail().subList(0, i + 1));
                break;
            }
        }
        ContentType type = request.type();
        Predicate<CustomItem> ofType = item -> type == null || item.definition().type() == type;
        List<Entry> entries = new ArrayList<>();
        Map<String, ContentTree.Node> nodes = new HashMap<>();
        for (ContentTree.Node child : folder.folders()) {
            if (count(registry, child, ofType) > 0) {
                entries.add(new Entry.Folder(child.path()));
                nodes.put(child.path(), child);
            }
        }
        for (String id : folder.items()) {
            registry.get(id).filter(ofType).ifPresent(item -> entries.add(new Entry.Item(item.id())));
        }
        Page<Entry> current = Page.of(entries, request.page(), request.pageSize());
        List<Entry> onPage = new ArrayList<>();
        List<ItemStack> stacks = new ArrayList<>();
        for (Entry entry : current.entries()) {
            ItemStack stack = switch (entry) {
                case Entry.Folder child -> folderIcon(menus, nodes.get(child.path()), ofType, type);
                case Entry.Item item -> registry.get(item.id()).map(found -> display(menus.factory(), found)).orElse(null);
            };
            if (stack != null) {
                onPage.add(entry);
                stacks.add(stack);
            }
        }
        String upName = "all packs";
        if (kept.size() > 2) {
            ContentTree.Node up = root.find(kept.get(kept.size() - 2));
            upName = up == null ? kept.get(kept.size() - 2) : up.name();
        }
        return new Laid(List.copyOf(kept), folder.path(), folder.path().isEmpty() ? null : folder.name(), upName,
                current.index(), current.count(), current.total(), onPage, stacks);
    }

    /** Puts a laid-out page into the inventory - a new one, titled, when the folder changed. Main thread. */
    private void apply(Player player, Laid laid) {
        trail.clear();
        trail.addAll(laid.trail());
        page = laid.page();
        pages = laid.pages();
        if (!laid.folderPath().equals(titled)) {
            Inventory next = Bukkit.createInventory(this, SIZE, menus.title(laid.folderName(), shop != null));
            fill(next, player, laid);
            inventory = next;
            titled = laid.folderPath();
            player.openInventory(next);
        } else {
            fill(inventory, player, laid);
        }
    }

    private void fill(Inventory target, Player viewer, Laid laid) {
        target.clear();
        shown.clear();
        for (int i = 0; i < laid.entries().size() && i < contentSlots.size(); i++) {
            Entry entry = laid.entries().get(i);
            ItemStack stack = laid.stacks().get(i);
            if (shop != null && entry instanceof Entry.Item item) {
                priced(stack, item.id());
            }
            int slot = contentSlots.get(i);
            target.setItem(slot, stack);
            shown.put(slot, entry);
        }
        if (laid.entries().isEmpty()) {
            int middle = contentSlots.contains(22) ? 22 : contentSlots.get(contentSlots.size() / 2);
            target.setItem(middle, button("page", Material.STRUCTURE_VOID, "Nothing here", NamedTextColor.GRAY, false,
                    List.of(type == null ? "No content is loaded." : "No " + label(type).toLowerCase(Locale.ROOT)
                            + " here.")));
        }
        if (!textured) {
            // Over vanilla's grey, the unused slots are filled; over the book they stay blank paper.
            ItemStack filler = filler();
            for (int slot = 0; slot < SIZE; slot++) {
                if (target.getItem(slot) == null && (slot >= CONTROLS || !contentSlots.contains(slot))) {
                    target.setItem(slot, filler);
                }
            }
        }
        if (laid.page() > 0) {
            target.setItem(PREVIOUS, button("previous", Material.ARROW, "Previous page", NamedTextColor.YELLOW, false,
                    List.of("Page " + laid.page() + " of " + laid.pages())));
        }
        if (laid.page() < laid.pages() - 1) {
            target.setItem(NEXT, button("next", Material.ARROW, "Next page", NamedTextColor.YELLOW, false,
                    List.of("Page " + (laid.page() + 2) + " of " + laid.pages())));
        }
        if (laid.trail().size() > 1) {
            target.setItem(UP, button("up", Material.SPECTRAL_ARROW, "Back", NamedTextColor.YELLOW, false,
                    List.of("Up to " + laid.upName())));
            target.setItem(HOME, button("home", Material.COMPASS, "All packs", NamedTextColor.YELLOW, false,
                    List.of("Back to the first page")));
        }
        List<String> types = new ArrayList<>();
        for (ContentType option : typeCycle()) {
            types.add((option == type ? "» " : "  ") + (option == null ? "Everything" : label(option)));
        }
        types.add("Click to change");
        target.setItem(TYPE, button("filter", type == null ? Material.HOPPER : tabIcon(type),
                "Showing: " + (type == null ? "everything" : label(type).toLowerCase(Locale.ROOT)),
                NamedTextColor.GOLD, type != null, types));
        target.setItem(CLOSE, button("close", Material.BARRIER, "Close", NamedTextColor.RED, false, List.of()));

        List<String> help = new ArrayList<>();
        help.add(laid.folderPath().isEmpty() ? (menus.settings().folders() ? "All content packs" : "All content")
                : "Folder: contents/" + laid.folderPath());
        help.add(laid.total() + " entr" + (laid.total() == 1 ? "y" : "ies") + ", " + contentSlots.size() + " per page");
        if (shop != null) {
            help.add("Click an item: buy one");
            help.add("Shift-click: buy a stack");
        } else if (viewer.hasPermission(GIVE_PERMISSION)) {
            help.add("Click an item: take one");
            help.add("Shift-click: take a stack");
        } else {
            help.add("Browsing only: taking items needs " + GIVE_PERMISSION);
        }
        String where = laid.folderName() == null ? (shop != null ? "Shop" : "Categories")
                : ContentMenus.words(laid.folderName());
        target.setItem(PAGE_INFO, button("page", Material.BOOK, where + " - page " + (laid.page() + 1) + " of "
                + laid.pages(), NamedTextColor.AQUA, false, help));
    }

    // ---------------------------------------------------------------- clicks

    /** A click on {@code slot} of the menu itself. The listener has already cancelled it. */
    Outcome click(Player player, int slot, ClickType click) {
        if (slot == CLOSE) {
            return Outcome.CLOSE;
        }
        Entry entry = shown.get(slot);
        if (entry instanceof Entry.Item item) {
            // The page shown is the one clicked, even while the next is on its way.
            take(player, item.id(), click);
            return Outcome.NOTHING;
        }
        if (pending) {
            return Outcome.NOTHING;
        }
        if (entry instanceof Entry.Folder folder && TAKING.contains(click)) {
            trail.add(folder.path());
            page = 0;
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
        } else {
            return Outcome.NOTHING;
        }
        player.playSound(player, slot == PREVIOUS || slot == NEXT ? Sound.ITEM_BOOK_PAGE_TURN : Sound.UI_BUTTON_CLICK,
                0.5f, 1f);
        request(player);
        return Outcome.NOTHING;
    }

    /** How many items of the shown type a folder holds, sub-folders included. */
    private static int count(ItemRegistry registry, ContentTree.Node node, Predicate<CustomItem> ofType) {
        int count = 0;
        for (String id : node.items()) {
            if (registry.get(id).filter(ofType).isPresent()) {
                count++;
            }
        }
        for (ContentTree.Node child : node.folders()) {
            count += count(registry, child, ofType);
        }
        return count;
    }

    /** A folder: drawn as its first item, named after it, with what it holds underneath. */
    private static ItemStack folderIcon(ContentMenus menus, ContentTree.Node node, Predicate<CustomItem> ofType,
                                        @Nullable ContentType type) {
        CustomItem first = firstItem(menus.registry(), node, ofType);
        ItemStack stack = first == null ? ItemStack.of(Material.CHEST) : menus.factory().create(first, 1);
        int items = count(menus.registry(), node, ofType);
        stack.editMeta(meta -> {
            meta.itemName(Component.text(ContentMenus.words(node.name()), NamedTextColor.GOLD)
                    .decoration(TextDecoration.BOLD, true));
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

    private static @Nullable CustomItem firstItem(ItemRegistry registry, ContentTree.Node node,
                                                  Predicate<CustomItem> ofType) {
        for (String id : node.items()) {
            Optional<CustomItem> item = registry.get(id).filter(ofType);
            if (item.isPresent()) {
                return item.get();
            }
        }
        for (ContentTree.Node child : node.folders()) {
            CustomItem found = firstItem(registry, child, ofType);
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
            request(player);
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
            request(player);
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
    private static ItemStack display(ItemFactory factory, CustomItem item) {
        ItemStack stack = factory.create(item, 1);
        stack.editMeta(meta -> {
            List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            if (!lore.isEmpty()) {
                lore.add(Component.empty());
            }
            lore.add(line(item.id(), NamedTextColor.DARK_GRAY));
            meta.lore(lore);
        });
        return stack;
    }

    /** A shop's price under the item: the economy is asked on the main thread, never the worker. */
    private void priced(ItemStack stack, String id) {
        registry.get(id).ifPresent(item -> stack.editMeta(meta -> {
            List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            lore.add(line("Price: " + shop.format(item.definition().price()), NamedTextColor.GOLD));
            meta.lore(lore);
        }));
    }

    private static Component line(String text, NamedTextColor colour) {
        return Component.text(text, colour).decoration(TextDecoration.ITALIC, false);
    }

    /**
     * A button: over the book, a sheet of paper drawn as the plugin's own icon
     * ({@code arkcontent:menu/<icon>}, in the pack); over vanilla's grey, {@code fallback}.
     */
    private ItemStack button(String icon, Material fallback, String name, NamedTextColor colour, boolean glint,
                             List<String> lines) {
        ItemStack stack = icon(textured ? Material.PAPER : fallback, name, colour, glint, lines);
        if (textured) {
            stack.editMeta(meta -> meta.setItemModel(new NamespacedKey("arkcontent", "menu/" + icon)));
        }
        return stack;
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
