package com.arkcronist.content.bukkit.hud;

import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemFactory;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** A player's HUD values follow them in and out, and what they eat or drink adds to them. */
public final class HudListener implements Listener {

    private final HudService huds;
    private final ItemFactory factory;

    public HudListener(HudService huds, ItemFactory factory) {
        this.huds = huds;
        this.factory = factory;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        huds.load(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        huds.quit(event.getPlayer());
    }

    /** {@code consume:} - a potion for thirst, a custom drink by its id. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        huds.consumed(event.getPlayer(), event.getItem().getType().name(),
                factory.identify(event.getItem()).map(CustomItem::id).orElse(null));
    }
}
