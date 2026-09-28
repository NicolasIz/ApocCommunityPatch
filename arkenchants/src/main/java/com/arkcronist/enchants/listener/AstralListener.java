package com.arkcronist.enchants.listener;

import com.arkcronist.enchants.ArkEnchants;
import com.arkcronist.enchants.item.Items;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.entity.Allay;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Astral books, and items that carry an astral enchant, stay with their owner: they cannot be dropped, put in
 * chests, shulkers, trade or shop menus, item frames or bundles, other players cannot pick them up, and they are
 * kept on death. Players with arkenchants.astral.bypass (admins) can move them to hand them out.
 */
public final class AstralListener implements Listener {

    /** Inventories that are still the player's own (or only transform the item in place). */
    private static final Set<InventoryType> OWN = EnumSet.of(InventoryType.CRAFTING, InventoryType.PLAYER,
            InventoryType.CREATIVE, InventoryType.ENDER_CHEST, InventoryType.ANVIL, InventoryType.SMITHING,
            InventoryType.GRINDSTONE, InventoryType.ENCHANTING, InventoryType.WORKBENCH);
    private static final Set<Material> BLOCKS = EnumSet.of(Material.CHISELED_BOOKSHELF, Material.DECORATED_POT,
            Material.LECTERN, Material.JUKEBOX, Material.VAULT);

    private final ArkEnchants plugin;

    public AstralListener(ArkEnchants plugin) {
        this.plugin = plugin;
    }

    private boolean on(Player p) {
        return plugin.settings().astralSoulbound && !p.hasPermission("arkenchants.astral.bypass");
    }

    private void deny(Player p) {
        plugin.send(p, "astral-locked");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (on(e.getPlayer()) && Items.isAstral(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !on(p)) {
            return;
        }
        ItemStack cursor = e.getCursor();
        ItemStack current = e.getCurrentItem();
        // bundles would carry them out of the inventory
        if (Items.isAstral(cursor) && isBundle(current) || isBundle(cursor) && Items.isAstral(current)) {
            e.setCancelled(true);
            deny(p);
            return;
        }
        Inventory top = e.getView().getTopInventory();
        if (OWN.contains(top.getType()) || com.arkcronist.enchants.gui.Stations.isStation(top)) {
            return;
        }
        Inventory clicked = e.getClickedInventory();
        boolean intoTop = false;
        if (clicked == top) {
            intoTop = Items.isAstral(cursor)
                    || e.getClick() == ClickType.NUMBER_KEY && e.getHotbarButton() >= 0
                    && Items.isAstral(p.getInventory().getItem(e.getHotbarButton()))
                    || e.getClick() == ClickType.SWAP_OFFHAND && Items.isAstral(p.getInventory().getItemInOffHand());
        } else if (clicked != null && e.isShiftClick()) {
            intoTop = Items.isAstral(current);
        }
        if (e.getClick() == ClickType.DOUBLE_CLICK && Items.isAstral(cursor)) {
            intoTop = false;
        }
        if (intoTop) {
            e.setCancelled(true);
            deny(p);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !on(p) || !Items.isAstral(e.getOldCursor())) {
            return;
        }
        Inventory top = e.getView().getTopInventory();
        if (OWN.contains(top.getType()) || com.arkcronist.enchants.gui.Stations.isStation(top)) {
            return;
        }
        for (int raw : e.getRawSlots()) {
            if (raw < top.getSize()) {
                e.setCancelled(true);
                deny(p);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntity(PlayerInteractEntityEvent e) {
        Entity t = e.getRightClicked();
        if (!(t instanceof ItemFrame || t instanceof Allay || t instanceof ArmorStand) || !on(e.getPlayer())) {
            return;
        }
        ItemStack hand = e.getPlayer().getInventory().getItem(e.getHand());
        if (Items.isAstral(hand)) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onStand(PlayerArmorStandManipulateEvent e) {
        if (on(e.getPlayer()) && Items.isAstral(e.getPlayerItem())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlock(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null
                || !BLOCKS.contains(e.getClickedBlock().getType()) || !on(e.getPlayer())) {
            return;
        }
        if (Items.isAstral(e.getItem())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    /** Only the owner picks them up (a new book with no owner yet binds to whoever takes it). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        ItemStack it = e.getItem().getItemStack();
        if (!Items.isAstral(it)) {
            return;
        }
        if (!(e.getEntity() instanceof Player p)) {
            e.setCancelled(true);
            return;
        }
        if (!Items.ownedBy(it, p) && !p.hasPermission("arkenchants.astral.bypass")) {
            e.setCancelled(true);
            return;
        }
        if (Items.owner(it) == null && !p.hasPermission("arkenchants.astral.bypass")) {
            Items.bind(it, p);
            e.getItem().setItemStack(it);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent e) {
        if (e.getKeepInventory() || !plugin.settings().astralKeepOnDeath) {
            return;
        }
        List<ItemStack> saved = new ArrayList<>();
        for (var it = e.getDrops().iterator(); it.hasNext(); ) {
            ItemStack d = it.next();
            if (Items.isAstral(d)) {
                saved.add(d);
                it.remove();
            }
        }
        if (!saved.isEmpty()) {
            plugin.engine().saved.add(e.getEntity().getUniqueId(), saved);
        }
    }

    /** Someone who logged off (or the server restarted) while dead gets their items when they are back. */
    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!p.isDead() && plugin.engine().saved.has(p.getUniqueId())) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && !p.isDead()) {
                    plugin.engine().saved.giveBack(p);
                }
            }, 20);
        }
    }

    private static boolean isBundle(ItemStack it) {
        return it != null && it.getType().name().endsWith("BUNDLE");
    }
}
