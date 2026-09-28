package com.arkcronist.enchants.listener;

import com.arkcronist.enchants.ArkEnchants;
import com.arkcronist.enchants.item.Applying;
import com.arkcronist.enchants.item.Items;
import com.arkcronist.enchants.item.Workbench;
import com.arkcronist.enchants.model.Enchant;
import com.arkcronist.enchants.model.Group;
import com.arkcronist.enchants.text.Percent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.GameMode;
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
        Workbench.Use u = plugin.workbench().use(p, e.getCursor(), e.getCurrentItem());
        if (!u.handled()) {
            return;
        }
        e.setCancelled(true);
        e.getView().setCursor(u.tool());
        e.setCurrentItem(u.target());
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
            Applying.Outcome o = Applying.check(ench, en.getValue(), merged, true, Items.maxSlots(result),
                    plugin.settings().upgradeOnSameLevel);
            if (o.result() == Applying.Result.OK) {
                merged.put(ench.id(), o.newLevel());
            }
        }
        if (plugin.settings().cursesPermanent) {
            // a cursed item used up on the anvil passes its curses on: an anvil never washes a curse away
            for (Map.Entry<String, Integer> en : b.entrySet()) {
                Enchant ench = plugin.registry().get(en.getKey());
                if (ench != null && ench.group().equals("CURSE")) {
                    merged.merge(ench.id(), en.getValue(), Math::max);
                }
            }
        }
        ItemStack out = result.clone();
        Items.setEnchants(out, merged);
        e.setResult(out);
    }
}
