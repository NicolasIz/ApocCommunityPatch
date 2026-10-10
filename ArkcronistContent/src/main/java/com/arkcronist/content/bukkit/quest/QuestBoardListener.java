package com.arkcronist.content.bukkit.quest;

import com.arkcronist.content.core.quest.QuestDefinition.Kind;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/**
 * The quest boards' events: nothing moves while a board is open - every click and drag cancelled,
 * first and last, as in the content browser - a player's progress is read as they join and written
 * as they leave, and kills and broken blocks are counted towards the notices they have taken.
 */
public final class QuestBoardListener implements Listener {

    private final Plugin plugin;
    private final QuestBoardService service;
    /** A custom block's id, for a block that is one. */
    private final Function<Block, Optional<String>> customBlock;

    public QuestBoardListener(Plugin plugin, QuestBoardService service, Function<Block, Optional<String>> customBlock) {
        this.plugin = plugin;
        this.service = service;
        this.customBlock = customBlock;
    }

    private static @Nullable QuestBoardMenu of(InventoryView view) {
        return view.getTopInventory().getHolder(false) instanceof QuestBoardMenu menu ? menu : null;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        QuestBoardMenu menu = of(event.getView());
        if (menu == null) {
            return;
        }
        event.setResult(Event.Result.DENY);
        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= QuestBoardMenu.SIZE
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (menu.click(player, event.getRawSlot(), event.getClick()) == QuestBoardMenu.Outcome.CLOSE) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (of(player.getOpenInventory()) == menu) {
                    player.closeInventory();
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void keepClickCancelled(InventoryClickEvent event) {
        if (of(event.getView()) != null) {
            event.setResult(Event.Result.DENY);
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (of(event.getView()) != null) {
            event.setResult(Event.Result.DENY);
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void keepDragCancelled(InventoryDragEvent event) {
        onDrag(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        service.joined(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        service.left(event.getPlayer());
    }

    /** A creature killed by a player: its type, and its MythicMobs type when it is one. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Player killer = dead.getKiller();
        if (killer == null || !counts(killer)) {
            return;
        }
        List<String> names = new ArrayList<>();
        names.add(dead.getType().getKey().getKey());
        String mythic = service.mythicType(dead);
        if (mythic != null) {
            names.add("mythicmobs:" + mythic.toLowerCase(Locale.ROOT));
        }
        service.record(killer, Kind.KILL, names);
    }

    /**
     * A block broken: its material, and its custom block's id when it is one. At the last priority
     * that still sees it before this plugin's own clean-up, so a custom block is still known.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!counts(player)) {
            return;
        }
        Block block = event.getBlock();
        List<String> names = new ArrayList<>();
        Optional<String> custom = customBlock.apply(block);
        if (custom.isPresent()) {
            names.add(custom.get());
        } else {
            names.add(block.getType().getKey().getKey());
        }
        service.record(player, Kind.BREAK, names);
    }

    /** Only play counts: not creative, not spectating. */
    private static boolean counts(Player player) {
        return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
    }
}
