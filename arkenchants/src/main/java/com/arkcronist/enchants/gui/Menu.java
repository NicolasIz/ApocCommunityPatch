package com.arkcronist.enchants.gui;

import com.arkcronist.enchants.text.Colors;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** A chest menu where each slot can run an action. Nothing can be taken out of it. */
public final class Menu implements InventoryHolder {

    private final Inventory inv;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();

    public Menu(int rows, String title) {
        this.inv = Bukkit.createInventory(this, rows * 9, Colors.of(title));
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    public int size() {
        return inv.getSize();
    }

    public Menu set(int slot, ItemStack icon, Consumer<InventoryClickEvent> action) {
        inv.setItem(slot, icon);
        if (action != null) {
            actions.put(slot, action);
        } else {
            actions.remove(slot);
        }
        return this;
    }

    public Menu fill(Material pane) {
        ItemStack glass = icon(pane, " ");
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, glass);
            }
        }
        return this;
    }

    public void open(Player p) {
        p.openInventory(inv);
    }

    /** An icon with a coloured name and lore lines ('&' codes). */
    public static ItemStack icon(Material m, String name, String... lore) {
        return icon(new ItemStack(m), name, List.of(lore));
    }

    public static ItemStack icon(ItemStack base, String name, List<String> lore) {
        ItemStack it = base.clone();
        ItemMeta meta = it.getItemMeta();
        if (name != null) {
            meta.displayName(Colors.of(name));
        }
        List<Component> l = new ArrayList<>();
        for (String s : lore) {
            l.add(Colors.of(s));
        }
        meta.lore(l);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ENCHANTS);
        it.setItemMeta(meta);
        return it;
    }

    /** Routes clicks to the slot actions of any open Menu. */
    public static final class Clicks implements Listener {
        @EventHandler
        public void onClick(InventoryClickEvent e) {
            if (!(e.getInventory().getHolder() instanceof Menu m)) {
                return;
            }
            e.setCancelled(true);
            if (e.getClickedInventory() != e.getInventory()) {
                return;
            }
            Consumer<InventoryClickEvent> a = m.actions.get(e.getRawSlot());
            if (a != null) {
                a.accept(e);
            }
        }

        @EventHandler
        public void onDrag(InventoryDragEvent e) {
            if (e.getInventory().getHolder() instanceof Menu) {
                e.setCancelled(true);
            }
        }
    }
}
