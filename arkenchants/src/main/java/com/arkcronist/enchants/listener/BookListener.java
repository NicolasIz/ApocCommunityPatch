package com.arkcronist.enchants.listener;

import com.arkcronist.enchants.ArkEnchants;
import com.arkcronist.enchants.item.Applying;
import com.arkcronist.enchants.item.Items;
import com.arkcronist.enchants.model.Enchant;
import com.arkcronist.enchants.model.Group;
import com.arkcronist.enchants.text.Percent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

/**
 * Dropping something on an item in the inventory: books (apply an enchant), the soul tracker, and scrolls
 * (extract an enchant back into a book, protect the item, purify a curse). Also mystery books and anvils.
 */
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
        if (Items.empty(cursor) || Items.empty(target) || target.getAmount() != 1 || Items.readBook(target) != null
                || Items.scrollKind(target) != null || Items.mysteryGroup(target) != null) {
            return;
        }
        String kind = Items.scrollKind(cursor);
        if (kind != null) {
            scroll(e, p, kind, cursor, target);
            return;
        }
        Items.Book book = Items.readBook(cursor);
        if (book != null) {
            book(e, p, book, cursor, target);
        }
    }

    // ------------------------------------------------------------------ books
    private void book(InventoryClickEvent e, Player p, Items.Book book, ItemStack cursor, ItemStack target) {
        Enchant ench = plugin.registry().get(book.enchant());
        if (ench == null) {
            return;
        }
        e.setCancelled(true);
        Map<String, Integer> current = new LinkedHashMap<>(Items.enchants(target));
        Applying.Outcome o = Applying.check(ench, book.level(), current, Items.applicable(ench, target),
                plugin.settings().maxEnchants, plugin.settings().upgradeOnSameLevel);
        switch (o.result()) {
            case NOT_APPLICABLE -> plugin.send(p, "not-applicable", "%applies-to%", ench.appliesTo());
            case ALREADY -> plugin.send(p, "already-has");
            case CONFLICT -> plugin.send(p, "conflict", "%enchant%", o.detail());
            case MISSING_REQUIRED -> plugin.send(p, "requires", "%enchant%", o.detail());
            case NO_SLOTS -> plugin.send(p, "no-slots", "%max%", o.detail());
            case OK -> {
                consume(e, cursor);
                ThreadLocalRandom r = ThreadLocalRandom.current();
                if (Percent.chance(book.success(), r)) {
                    current.put(ench.id(), o.newLevel());
                    Items.setEnchants(target, current);
                    e.setCurrentItem(target);
                    good(p);
                    plugin.send(p, "applied", "%enchant%", Items.format("%group-color%%display% %level%", ench, o.newLevel()));
                } else if (Percent.chance(book.destroy(), r)) {
                    if (Items.isProtected(target)) {
                        Items.setProtected(target, false);
                        e.setCurrentItem(target);
                        bad(p);
                        plugin.send(p, "protection-saved");
                    } else {
                        e.setCurrentItem(null);
                        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1, 0.7f);
                        plugin.send(p, "destroyed");
                    }
                } else {
                    bad(p);
                    plugin.send(p, "failed", "%success%", Percent.fmt(book.success()));
                }
            }
        }
    }

    // ------------------------------------------------------------------ scrolls
    private void scroll(InventoryClickEvent e, Player p, String kind, ItemStack cursor, ItemStack target) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        switch (kind) {
            case "soul_tracker" -> {
                if (Items.tracks(target) || target.getType().getMaxStackSize() > 1) {
                    return;
                }
                e.setCancelled(true);
                Items.setSouls(target, 0, true);
                e.setCurrentItem(target);
                consume(e, cursor);
                good(p);
                plugin.send(p, "tracker-applied");
            }
            case "protect" -> {
                if (target.getType().getMaxStackSize() > 1) {
                    return;
                }
                e.setCancelled(true);
                if (Items.isProtected(target)) {
                    plugin.send(p, "already-protected");
                    return;
                }
                consume(e, cursor);
                Items.setProtected(target, true);
                e.setCurrentItem(target);
                good(p);
                plugin.send(p, "protected");
            }
            case "extract", "purify" -> {
                boolean curse = kind.equals("purify");
                Map<String, Integer> current = new LinkedHashMap<>(Items.enchants(target));
                List<String> options = new ArrayList<>();
                for (String id : current.keySet()) {
                    Enchant en = plugin.registry().get(id);
                    boolean isCurse = en != null && en.group().equals("CURSE");
                    // extraction takes only removable enchants; purifying takes only curses
                    if (en == null || (curse ? isCurse : !isCurse && en.removable())) {
                        options.add(id);
                    }
                }
                if (current.isEmpty()) {
                    return;
                }
                e.setCancelled(true);
                if (options.isEmpty()) {
                    plugin.send(p, curse ? "no-curse" : "nothing-to-extract");
                    return;
                }
                consume(e, cursor);
                double rate = Items.scrollRate(cursor);
                if (!Percent.chance(rate, r)) {
                    bad(p);
                    plugin.send(p, "scroll-failed", "%success%", Percent.fmt(rate));
                    return;
                }
                String id = options.get(r.nextInt(options.size()));
                int level = current.remove(id);
                Items.setEnchants(target, current);
                e.setCurrentItem(target);
                good(p);
                Enchant en = plugin.registry().get(id);
                if (curse || en == null) {
                    plugin.send(p, "purified", "%enchant%", en == null ? id : Items.format("%group-color%%display% %level%", en, level));
                    return;
                }
                Group g = plugin.registry().group(en.group());
                ItemStack b = Items.book(en, level, Percent.roll(g.successMin(), g.successMax(), r),
                        Percent.roll(g.destroyMin(), g.destroyMax(), r));
                p.getInventory().addItem(b).values().forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
                plugin.send(p, "extracted", "%enchant%", Items.format("%group-color%%display% %level%", en, level));
            }
            default -> {
            }
        }
    }

    private static void good(Player p) {
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
        p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 25, 0.4, 0.6, 0.4);
    }

    private static void bad(Player p) {
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.6f, 1.2f);
        p.getWorld().spawnParticle(Particle.SMOKE, p.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.02);
    }

    private static void consume(InventoryClickEvent e, ItemStack cursor) {
        if (cursor.getAmount() > 1) {
            cursor.setAmount(cursor.getAmount() - 1);
            e.getView().setCursor(cursor);
        } else {
            e.getView().setCursor(null);
        }
    }

    // ------------------------------------------------------------------ mystery books
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
        double success = Percent.roll(g.successMin(), g.successMax(), r);
        double destroy = Percent.roll(g.destroyMin(), g.destroyMax(), r);
        if (p.getGameMode() != GameMode.CREATIVE || hand.getAmount() > 1) {
            hand.setAmount(hand.getAmount() - 1);
        }
        p.getInventory().addItem(Items.book(ench, level, success, destroy)).values()
                .forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1.6f);
        plugin.send(p, "mystery-opened", "%enchant%", Items.format("%group-color%%display% %level%", ench, level),
                "%success%", Percent.fmt(success));
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
