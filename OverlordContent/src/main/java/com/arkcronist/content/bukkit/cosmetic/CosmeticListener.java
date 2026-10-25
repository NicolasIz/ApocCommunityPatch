package com.arkcronist.content.bukkit.cosmetic;

import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemFactory;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import io.papermc.paper.event.player.PlayerUntrackEntityEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/** What moves a worn cosmetic: the wearer's movement, who comes into sight, a right click to put one on. */
public final class CosmeticListener implements Listener {

    private final CosmeticService cosmetics;
    private final ItemFactory items;

    public CosmeticListener(CosmeticService cosmetics, ItemFactory items) {
        this.cosmetics = cosmetics;
        this.items = items;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        cosmetics.moved(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        cosmetics.moved(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        ItemStack hand = event.getPlayer().getInventory().getItemInMainHand();
        Optional<CustomItem> item = items.identify(hand);
        if (item.isEmpty() || item.get().definition().cosmetic() == null) {
            return;
        }
        // 3.4: sneaking, a backpack is opened instead.
        if (event.getPlayer().isSneaking() && item.get().definition().backpack() != null) {
            return;
        }
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        cosmetics.putOn(event.getPlayer(), item.get(), hand);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        cosmetics.joined(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cosmetics.left(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        cosmetics.redrawFor(event.getPlayer());
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        cosmetics.redrawFor(event.getPlayer());
    }

    @EventHandler
    public void onTrack(PlayerTrackEntityEvent event) {
        if (event.getEntity() instanceof Player wearer) {
            cosmetics.tracked(event.getPlayer(), wearer);
        }
    }

    @EventHandler
    public void onUntrack(PlayerUntrackEntityEvent event) {
        if (event.getEntity() instanceof Player wearer) {
            cosmetics.untracked(event.getPlayer(), wearer);
        }
    }
}
