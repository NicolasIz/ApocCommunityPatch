package com.arkcronist.content.bukkit.minigame;

import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemFactory;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * Starts, plays and guards the HUD minigames (3.0): a right click on a rare ore or a catch on a
 * minigame rod starts one, a left click stops the needle, and a rare ore is never mined the
 * ordinary way.
 */
public final class MinigameListener implements Listener {

    private final MinigameService minigames;
    private final ItemFactory items;

    public MinigameListener(MinigameService minigames, ItemFactory items) {
        this.minigames = minigames;
        this.items = items;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            if (minigames.playing(player)) {
                minigames.press(player);
                // Hitting the ore to stop the needle must not start mining it.
                event.setCancelled(true);
            }
            return;
        }
        if (action != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || player.isSneaking()) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block != null && block.getType() == Material.NOTE_BLOCK && minigames.startOre(player, block)) {
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
        }
    }

    /** A rare ore is mined by playing for it. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (minigames.guarded(event.getBlock(), event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(BlockDamageEvent event) {
        if (minigames.guarded(event.getBlock(), event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /** A rod with a minigame: the catch is held, and the game decides whether it is landed. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(event.getCaught() instanceof Item caught)) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack rodStack = event.getHand() == EquipmentSlot.OFF_HAND ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        Optional<CustomItem> rod = items.identify(rodStack);
        if (rod.isEmpty() || rod.get().definition().minigame() == null) {
            return;
        }
        if (minigames.startCatch(player, rod.get(), caught.getItemStack(), event.getExpToDrop())) {
            caught.remove();
            event.setExpToDrop(0);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        minigames.cancel(event.getPlayer());
        minigames.forget(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        minigames.cancel(event.getEntity());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        minigames.cancel(event.getPlayer());
    }
}
