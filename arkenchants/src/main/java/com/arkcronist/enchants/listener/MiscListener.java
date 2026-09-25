package com.arkcronist.enchants.listener;

import com.arkcronist.enchants.engine.Context;
import com.arkcronist.enchants.engine.Engine;
import com.arkcronist.enchants.item.Items;
import com.arkcronist.enchants.model.Trigger;
import java.util.List;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.Particle;
import org.bukkit.Sound;
import com.arkcronist.enchants.text.Colors;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Right click, fishing, broken items, and re-drawing lore another plugin wiped. */
public final class MiscListener implements Listener {

    private final Engine engine;

    public MiscListener(Engine engine) {
        this.engine = engine;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        ItemStack hand = e.getPlayer().getInventory().getItemInMainHand();
        if (Items.readBook(hand) != null || Items.mysteryGroup(hand) != null || Items.isTracker(hand)) {
            return;
        }
        charge(e.getPlayer(), hand);
        Context c = new Context(Trigger.RIGHT_CLICK, e.getPlayer());
        c.attacker = e.getPlayer();
        c.event = e;
        c.block = e.getClickedBlock();
        engine.fire(Trigger.RIGHT_CLICK, c, List.of(hand));
    }

    /** Holding right click while looking at a mob sends entity clicks instead of item uses. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClickEntity(PlayerInteractEntityEvent e) {
        if (e.getHand() == EquipmentSlot.HAND) {
            charge(e.getPlayer(), e.getPlayer().getInventory().getItemInMainHand());
        }
    }

    private void charge(Player p, ItemStack hand) {
        if (!engine.has(hand, Trigger.CHARGED_ATTACK)) {
            return;
        }
        int pct = engine.charges.click(p, System.currentTimeMillis());
        if (pct < 0) {
            p.sendActionBar(Colors.of(engine.settings().msg("charged")));
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.7f, 1.8f);
            p.getWorld().spawnParticle(Particle.ENCHANTED_HIT, p.getLocation().add(0, 1.2, 0), 20, 0.4, 0.5, 0.4, 0.2);
        } else if (pct < 100) {
            int bars = pct / 10;
            p.sendActionBar(Colors.of(engine.settings().msg("charging").replace("%bar%",
                    "&d" + "|".repeat(bars) + "&8" + "|".repeat(10 - bars)).replace("%percent%", String.valueOf(pct))));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        engine.clones.removeAll(e.getPlayer());
        engine.charges.forget(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        Trigger t = switch (e.getState()) {
            case CAUGHT_FISH -> Trigger.CATCH_FISH;
            case CAUGHT_ENTITY -> Trigger.HOOK_ENTITY;
            case BITE -> Trigger.BITE_HOOK;
            default -> null;
        };
        if (t == null) {
            return;
        }
        Player p = e.getPlayer();
        Context c = new Context(t, p);
        c.attacker = p;
        c.event = e;
        c.caught = e.getCaught();
        c.hook = e.getHook();
        if (e.getCaught() instanceof LivingEntity le) {
            c.victim = le;
        }
        c.exp = e.getExpToDrop();
        engine.fire(t, c, List.of(p.getInventory().getItemInMainHand(), p.getInventory().getItemInOffHand()));
    }

    @EventHandler
    public void onItemBreak(PlayerItemBreakEvent e) {
        Context c = new Context(Trigger.ITEM_BREAK, e.getPlayer());
        c.attacker = e.getPlayer();
        engine.fire(Trigger.ITEM_BREAK, c, List.of(e.getBrokenItem()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent e) {
        ItemStack it = e.getPlayer().getInventory().getItem(e.getNewSlot());
        if (it != null && (Items.convertLegacy(it) | Items.refresh(it))) {
            e.getPlayer().getInventory().setItem(e.getNewSlot(), it);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        ItemStack[] contents = e.getPlayer().getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null && (Items.convertLegacy(contents[i]) | Items.refresh(contents[i]))) {
                e.getPlayer().getInventory().setItem(i, contents[i]);
            }
        }
    }
}
