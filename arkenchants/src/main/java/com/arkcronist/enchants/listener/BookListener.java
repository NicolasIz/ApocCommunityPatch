package com.arkcronist.enchants.listener;

import com.arkcronist.enchants.ArkEnchants;
import com.arkcronist.enchants.item.Applying;
import com.arkcronist.enchants.item.Items;
import com.arkcronist.enchants.model.Enchant;
import com.arkcronist.enchants.model.Group;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Dropping a book (or soul tracker) on an item, opening mystery books, merging enchanted items on an anvil. */
public final class BookListener implements Listener {

    private final ArkEnchants plugin;

    public BookListener(ArkEnchants plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || e.getClick() != ClickType.LEFT && e.getClick() != ClickType.RIGHT) {
            return;
        }
        if (e.getClickedInventory() == null || e.getClickedInventory().getType() != InventoryType.PLAYER
                && e.getClickedInventory().getType() != InventoryType.CRAFTING) {
            return;
        }
        ItemStack cursor = e.getCursor();
        ItemStack target = e.getCurrentItem();
        if (Items.empty(cursor) || Items.empty(target)) {
            return;
        }
        if (Items.isTracker(cursor)) {
            if (Items.tracks(target) || target.getType().getMaxStackSize() > 1) {
                return;
            }
            e.setCancelled(true);
            Items.setSouls(target, 0, true);
            e.setCurrentItem(target);
            consume(e, cursor);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1.4f);
            plugin.send(p, "tracker-applied");
            return;
        }
        Object[] book = Items.readBook(cursor);
        if (book == null || Items.readBook(target) != null || target.getAmount() != 1) {
            return;
        }
        Enchant ench = plugin.registry().get((String) book[0]);
        if (ench == null) {
            return;
        }
        e.setCancelled(true);
        int level = (Integer) book[1];
        int success = (Integer) book[2];
        int destroy = (Integer) book[3];
        Map<String, Integer> current = new LinkedHashMap<>(Items.enchants(target));
        Applying.Outcome o = Applying.check(ench, level, current, Items.applicable(ench, target),
                plugin.settings().maxEnchants, plugin.settings().upgradeOnSameLevel);
        switch (o.result()) {
            case NOT_APPLICABLE -> plugin.send(p, "not-applicable", "%applies-to%", ench.appliesTo());
            case ALREADY -> plugin.send(p, "already-has");
            case CONFLICT -> plugin.send(p, "conflict", "%enchant%", o.detail());
            case MISSING_REQUIRED -> plugin.send(p, "requires", "%enchant%", o.detail());
            case NO_SLOTS -> plugin.send(p, "no-slots", "%max%", o.detail());
            case OK -> {
                consume(e, cursor);
                if (ThreadLocalRandom.current().nextInt(100) < success) {
                    current.put(ench.id(), o.newLevel());
                    Items.setEnchants(target, current);
                    e.setCurrentItem(target);
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
                    p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 25, 0.4, 0.6, 0.4);
                    plugin.send(p, "applied", "%enchant%", Items.format("%group-color%%display% %level%", ench, o.newLevel()));
                } else if (ThreadLocalRandom.current().nextInt(100) < destroy) {
                    e.setCurrentItem(null);
                    p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1, 0.7f);
                    plugin.send(p, "destroyed");
                } else {
                    p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.6f, 1.2f);
                    plugin.send(p, "failed");
                }
            }
        }
    }

    private static void consume(InventoryClickEvent e, ItemStack cursor) {
        if (cursor.getAmount() > 1) {
            cursor.setAmount(cursor.getAmount() - 1);
            e.getView().setCursor(cursor);
        } else {
            e.getView().setCursor(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onOpenMystery(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        String groupId = Items.mysteryGroup(hand);
        if (groupId == null) {
            return;
        }
        e.setCancelled(true);
        Group g = plugin.registry().group(groupId);
        Enchant ench = g == null ? null : plugin.registry().random(g.id(), ThreadLocalRandom.current());
        if (ench == null) {
            plugin.send(p, "empty-group");
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int level = 1 + r.nextInt(Math.max(1, ench.maxLevel()));
        int success = g.successMin() + r.nextInt(g.successMax() - g.successMin() + 1);
        int destroy = g.destroyMin() + r.nextInt(g.destroyMax() - g.destroyMin() + 1);
        if (p.getGameMode() != GameMode.CREATIVE || hand.getAmount() > 1) {
            hand.setAmount(hand.getAmount() - 1);
        }
        p.getInventory().addItem(Items.book(ench, level, success, destroy)).values()
                .forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1.6f);
        plugin.send(p, "mystery-opened", "%enchant%", Items.format("%group-color%%display% %level%", ench, level));
    }

    /** Two items with ArkEnchants on an anvil: the result keeps both, equal levels go one up. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onAnvil(PrepareAnvilEvent e) {
        ItemStack left = e.getInventory().getFirstItem();
        ItemStack right = e.getInventory().getSecondItem();
        ItemStack result = e.getResult();
        if (Items.empty(left) || Items.empty(right) || Items.empty(result) || Items.readBook(right) != null) {
            return;
        }
        Map<String, Integer> a = Items.enchants(left);
        Map<String, Integer> b = Items.enchants(right);
        if (b.isEmpty()) {
            return;
        }
        Map<String, Integer> merged = new LinkedHashMap<>(a);
        for (Map.Entry<String, Integer> en : b.entrySet()) {
            Enchant ench = plugin.registry().get(en.getKey());
            if (ench == null || !Items.applicable(ench, result)) {
                continue;
            }
            Applying.Outcome o = Applying.check(ench, en.getValue(), merged, true, plugin.settings().maxEnchants,
                    plugin.settings().upgradeOnSameLevel);
            if (o.result() == Applying.Result.OK) {
                merged.put(ench.id(), o.newLevel());
            }
        }
        ItemStack out = result.clone();
        Items.setEnchants(out, merged);
        e.setResult(out);
    }
}
