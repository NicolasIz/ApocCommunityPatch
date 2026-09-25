package com.arkcronist.enchants.gui;

import com.arkcronist.enchants.ArkEnchants;
import com.arkcronist.enchants.item.Items;
import com.arkcronist.enchants.model.Group;
import com.arkcronist.enchants.text.Colors;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** /enchanter: one mystery book per group, paid with experience levels. */
public final class EnchanterMenu implements Listener {

    private final ArkEnchants plugin;

    public EnchanterMenu(ArkEnchants plugin) {
        this.plugin = plugin;
    }

    private static final class Holder implements InventoryHolder {
        final List<Group> slots = new ArrayList<>();
        final java.util.Map<Integer, String> scrolls = new java.util.HashMap<>();
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    public void open(Player p) {
        List<Group> groups = new ArrayList<>(plugin.registry().groups());
        groups.removeIf(g -> !g.inEnchanter() || plugin.registry().inGroup(g.id()).isEmpty());
        int rows = Math.max(1, Math.min(6, (groups.size() + 8) / 9 + 3));
        Holder h = new Holder();
        Inventory inv = Bukkit.createInventory(h, rows * 9, Colors.of(plugin.settings().enchanterTitle));
        h.inv = inv;
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta pm = pane.getItemMeta();
        pm.displayName(Component.text(" "));
        pane.setItemMeta(pm);
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, pane);
            h.slots.add(null);
        }
        int start = 9 + Math.max(0, (9 - Math.min(9, groups.size())) / 2);
        for (int i = 0; i < groups.size(); i++) {
            Group g = groups.get(i);
            int slot = start + i;
            if (slot >= inv.getSize()) {
                break;
            }
            ItemStack icon = Items.mystery(g);
            ItemMeta m = icon.getItemMeta();
            List<Component> lore = new ArrayList<>(m.lore() == null ? List.of() : m.lore());
            lore.add(Component.empty());
            for (String l : plugin.getConfig().getStringList("enchanter.icon-lore")) {
                lore.add(Colors.of(Items.groupText(l, g)
                        .replace("%count%", String.valueOf(plugin.registry().inGroup(g.id()).size()))));
            }
            m.lore(lore);
            icon.setItemMeta(m);
            inv.setItem(slot, icon);
            h.slots.set(slot, g);
        }
        // scrolls on the last row
        var scrolls = plugin.settings().scrolls.values().stream().filter(sc -> sc.cost() > 0).toList();
        int first = inv.getSize() - 9 + Math.max(0, (9 - scrolls.size() * 2 + 1) / 2);
        for (int i = 0; i < scrolls.size(); i++) {
            var sc = scrolls.get(i);
            int slot = first + i * 2;
            ItemStack icon = Items.scroll(sc.kind(), sc.max());
            ItemMeta m = icon.getItemMeta();
            List<Component> lore = new ArrayList<>(m.lore() == null ? List.of() : m.lore());
            lore.add(Component.empty());
            for (String l : plugin.getConfig().getStringList("enchanter.scroll-lore")) {
                lore.add(Colors.of(l.replace("%cost%", String.valueOf(sc.cost()))
                        .replace("%min%", com.arkcronist.enchants.text.Percent.fmt(sc.min()))
                        .replace("%max%", com.arkcronist.enchants.text.Percent.fmt(sc.max()))));
            }
            m.lore(lore);
            m.displayName(Colors.of(sc.name().replace(" &7(%success%%)", "").replace("%success%", "?")));
            icon.setItemMeta(m);
            inv.setItem(slot, icon);
            h.scrolls.put(slot, sc.kind());
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getInventory()) {
            return;
        }
        int slot = e.getRawSlot();
        String kind = h.scrolls.get(slot);
        if (kind != null) {
            var sc = plugin.settings().scrolls.get(kind);
            if (!pay(p, sc.cost())) {
                return;
            }
            double rate = com.arkcronist.enchants.text.Percent.roll(sc.min(), sc.max(), java.util.concurrent.ThreadLocalRandom.current());
            p.getInventory().addItem(Items.scroll(kind, rate)).values().forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
            p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1, 1.3f);
            plugin.send(p, "bought", "%group%", sc.name().replace(" &7(%success%%)", "").replace("%success%", ""), "%cost%",
                    String.valueOf(sc.cost()));
            return;
        }
        Group g = slot >= 0 && slot < h.slots.size() ? h.slots.get(slot) : null;
        if (g == null) {
            return;
        }
        int cost = g.enchanterCost();
        if (p.getGameMode() != org.bukkit.GameMode.CREATIVE && p.getLevel() < cost) {
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
            plugin.send(p, "not-enough-xp", "%cost%", String.valueOf(cost));
            return;
        }
        if (p.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            p.setLevel(p.getLevel() - cost);
        }
        p.getInventory().addItem(Items.mystery(g)).values().forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
        p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1, 1);
        plugin.send(p, "bought", "%group%", g.color() + g.name(), "%cost%", String.valueOf(cost));
    }

    private boolean pay(Player p, int cost) {
        if (p.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return true;
        }
        if (p.getLevel() < cost) {
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
            plugin.send(p, "not-enough-xp", "%cost%", String.valueOf(cost));
            return false;
        }
        p.setLevel(p.getLevel() - cost);
        return true;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }
}
