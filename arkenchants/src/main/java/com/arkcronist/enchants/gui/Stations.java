package com.arkcronist.enchants.gui;

import static com.arkcronist.enchants.gui.Menu.icon;

import com.arkcronist.enchants.ArkEnchants;
import com.arkcronist.enchants.item.Items;
import com.arkcronist.enchants.item.Workbench;
import com.arkcronist.enchants.model.Enchant;
import com.arkcronist.enchants.text.Colors;
import com.arkcronist.enchants.text.Percent;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Two menus that hold real items:
 * <ul>
 * <li>the enchanting table (/ake mesa): put the item and a book, scroll or dust in, press the button. It does
 * the same as dragging, and works on Bedrock (Geyser) where dragging onto an item is awkward;</li>
 * <li>the recycler (/ake reciclar): books you do not want turn into experience, sometimes magic dust.</li>
 * </ul>
 */
public final class Stations implements Listener {

    static final int ITEM = 11;
    static final int TOOL = 13;
    static final int GO = 15;
    static final int RECYCLE_ROWS = 5;
    static final int RECYCLE_GO = RECYCLE_ROWS * 9 + 4;

    /** Marks our inventories. */
    static final class Holder implements InventoryHolder {
        final boolean table;
        Inventory inv;

        Holder(boolean table) {
            this.table = table;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final ArkEnchants plugin;

    public Stations(ArkEnchants plugin) {
        this.plugin = plugin;
    }

    public static boolean isStation(Inventory top) {
        return top != null && top.getHolder() instanceof Holder;
    }

    // ------------------------------------------------------------------ open
    public void openTable(Player p) {
        Holder h = new Holder(true);
        Inventory inv = Bukkit.createInventory(h, 27, Colors.of("&5✦ &dMesa de Encantar"));
        h.inv = inv;
        ItemStack pane = icon(Material.PURPLE_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, pane);
        }
        inv.setItem(ITEM, null);
        inv.setItem(TOOL, null);
        inv.setItem(2, icon(Material.ITEM_FRAME, "&f&lTu objeto &7(abajo)", "&7Pon aquí debajo el arma,", "&7armadura, herramienta o libro."));
        inv.setItem(4, icon(Material.ENCHANTED_BOOK, "&d&lLibro o pergamino &7(abajo)", "&7Libro, pergamino, polvo mágico,",
                "&7orbe de ranuras o lágrima del fénix."));
        inv.setItem(GO, goButton());
        inv.setItem(22, icon(Material.BARRIER, "&cCerrar", "&7Lo que dejes dentro vuelve", "&7a tu inventario."));
        p.openInventory(inv);
    }

    private static ItemStack goButton() {
        return icon(Material.ENCHANTING_TABLE, "&a&l¡Encantar!", "&7Usa el libro o pergamino", "&7sobre tu objeto.", "",
                "&8Igual que arrastrarlo encima.");
    }

    public void openRecycler(Player p) {
        Holder h = new Holder(false);
        int size = (RECYCLE_ROWS + 1) * 9;
        Inventory inv = Bukkit.createInventory(h, size, Colors.of("&2♻ &aReciclar libros"));
        h.inv = inv;
        ItemStack pane = icon(Material.GREEN_STAINED_GLASS_PANE, " ");
        for (int i = RECYCLE_ROWS * 9; i < size; i++) {
            inv.setItem(i, pane);
        }
        inv.setItem(RECYCLE_ROWS * 9, icon(Material.BOOK, "&a&lCómo funciona", "&7Mete aquí los libros que no quieras.",
                "&7Cada uno te da experiencia según", "&7su rareza, y a veces un &ePolvo Mágico&7.", "",
                "&7Al cerrar sin reciclar, te los devuelve."));
        inv.setItem(RECYCLE_GO, recycleButton(inv));
        inv.setItem(size - 1, icon(Material.BARRIER, "&cCerrar"));
        p.openInventory(inv);
    }

    private ItemStack recycleButton(Inventory inv) {
        int xp = 0;
        int books = 0;
        for (int i = 0; i < RECYCLE_ROWS * 9; i++) {
            ItemStack it = inv.getItem(i);
            int v = value(it);
            if (v > 0) {
                xp += v * it.getAmount();
                books += it.getAmount();
            }
        }
        return icon(Material.EXPERIENCE_BOTTLE, "&a&lReciclar", "&7Libros: &f" + books, "&7Experiencia: &a" + xp + " puntos",
                "&7Polvo mágico: &e" + Percent.fmt(plugin.settings().recycleDustChance) + "% &7por libro", "", "&eClic para reciclar");
    }

    /** Experience points a book is worth, 0 for anything that cannot be recycled. */
    private int value(ItemStack it) {
        Items.Book b = Items.readBook(it);
        if (b == null) {
            return 0;
        }
        Enchant e = plugin.registry().get(b.enchant());
        if (e == null || e.group().equals("ASTRAL")) {
            return 0;
        }
        return plugin.settings().recycleXp.getOrDefault(e.group(), 0);
    }

    // ------------------------------------------------------------------ clicks
    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h) || !(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        Inventory top = e.getInventory();
        boolean inTop = e.getClickedInventory() == top;
        if (h.table) {
            tableClick(e, p, top, inTop);
        } else {
            recycleClick(e, p, top, inTop);
        }
    }

    private void tableClick(InventoryClickEvent e, Player p, Inventory top, boolean inTop) {
        if (!inTop) {
            if (e.isShiftClick()) {
                // shift-click from the inventory: first the item slot, then the book slot
                e.setCancelled(true);
                ItemStack it = e.getCurrentItem();
                if (Items.empty(it)) {
                    return;
                }
                boolean isTool = Items.readBook(it) != null || Items.scrollKind(it) != null;
                int slot = isTool && top.getItem(TOOL) == null ? TOOL : top.getItem(ITEM) == null ? ITEM
                        : top.getItem(TOOL) == null ? TOOL : -1;
                if (slot >= 0) {
                    top.setItem(slot, it);
                    e.setCurrentItem(null);
                }
            } else if (e.getClick().name().contains("DOUBLE")) {
                e.setCancelled(true);
            }
            return;
        }
        int slot = e.getRawSlot();
        if (slot == ITEM || slot == TOOL) {
            if (e.getClick().name().contains("DOUBLE")) {
                e.setCancelled(true);
            }
            return;
        }
        e.setCancelled(true);
        if (slot == 22) {
            p.closeInventory();
            return;
        }
        if (slot != GO) {
            return;
        }
        ItemStack item = top.getItem(ITEM);
        ItemStack tool = top.getItem(TOOL);
        if (Items.empty(item) || Items.empty(tool)) {
            plugin.send(p, "table-empty");
            return;
        }
        Workbench.Use u = plugin.workbench().use(p, tool, item);
        if (!u.handled()) {
            plugin.send(p, "table-nothing");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
            return;
        }
        top.setItem(TOOL, u.tool());
        top.setItem(ITEM, u.target());
    }

    private void recycleClick(InventoryClickEvent e, Player p, Inventory top, boolean inTop) {
        int work = RECYCLE_ROWS * 9;
        if (!inTop) {
            // from the player's inventory only books go in
            if (e.isShiftClick() && value(e.getCurrentItem()) <= 0) {
                e.setCancelled(true);
            }
            if (e.getClick().name().contains("DOUBLE")) {
                e.setCancelled(true);
            }
            refreshLater(top);
            return;
        }
        int slot = e.getRawSlot();
        if (slot < work) {
            ItemStack cursor = e.getCursor();
            ItemStack incoming = e.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY && e.getHotbarButton() >= 0
                    ? p.getInventory().getItem(e.getHotbarButton())
                    : e.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND ? p.getInventory().getItemInOffHand() : cursor;
            if (!Items.empty(incoming) && value(incoming) <= 0 || e.getClick().name().contains("DOUBLE")) {
                e.setCancelled(true);
            }
            refreshLater(top);
            return;
        }
        e.setCancelled(true);
        if (slot == top.getSize() - 1) {
            p.closeInventory();
        } else if (slot == RECYCLE_GO) {
            recycle(p, top);
        }
    }

    private void recycle(Player p, Inventory top) {
        int xp = 0;
        int books = 0;
        int dust = 0;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < RECYCLE_ROWS * 9; i++) {
            ItemStack it = top.getItem(i);
            int v = value(it);
            if (v <= 0) {
                continue;
            }
            for (int n = 0; n < it.getAmount(); n++) {
                xp += v;
                books++;
                if (Percent.chance(plugin.settings().recycleDustChance, r)) {
                    dust++;
                }
            }
            top.setItem(i, null);
        }
        if (books == 0) {
            plugin.send(p, "recycle-empty");
            return;
        }
        p.giveExp(xp);
        var sc = plugin.settings().scrolls.get("dust");
        for (int i = 0; i < dust && sc != null; i++) {
            ItemStack d = Items.scroll("dust", Percent.roll(sc.min(), sc.max(), r));
            p.getInventory().addItem(d).values().forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
        }
        p.playSound(p.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1, 1);
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
        plugin.send(p, "recycled", "%books%", String.valueOf(books), "%xp%", String.valueOf(xp), "%dust%", String.valueOf(dust));
        top.setItem(RECYCLE_GO, recycleButton(top));
    }

    private void refreshLater(Inventory top) {
        Bukkit.getScheduler().runTask(plugin, () -> top.setItem(RECYCLE_GO, recycleButton(top)));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) {
            return;
        }
        for (int raw : e.getRawSlots()) {
            if (raw >= e.getInventory().getSize()) {
                continue;
            }
            boolean free = h.table ? raw == ITEM || raw == TOOL : raw < RECYCLE_ROWS * 9 && value(e.getOldCursor()) > 0;
            if (!free) {
                e.setCancelled(true);
                return;
            }
        }
        if (!h.table) {
            refreshLater(e.getInventory());
        }
    }

    /** Whatever is left inside goes back to the player. */
    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h) || !(e.getPlayer() instanceof Player p)) {
            return;
        }
        Inventory top = e.getInventory();
        int[] slots = h.table ? new int[]{ITEM, TOOL} : java.util.stream.IntStream.range(0, RECYCLE_ROWS * 9).toArray();
        for (int s : slots) {
            ItemStack it = top.getItem(s);
            if (!Items.empty(it)) {
                top.setItem(s, null);
                p.getInventory().addItem(it).values().forEach(rest -> p.getWorld().dropItem(p.getLocation(), rest));
            }
        }
    }
}
