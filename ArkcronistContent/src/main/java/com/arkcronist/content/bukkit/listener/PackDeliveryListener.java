package com.arkcronist.content.bukkit.listener;

import com.arkcronist.content.bukkit.EngineSettings;
import com.arkcronist.content.bukkit.pack.PackDelivery;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.util.logging.Logger;

/**
 * Sends the pack to each player who joins, and reports what their client made of it.
 */
public final class PackDeliveryListener implements Listener {

    private final PackDelivery delivery;
    private final EngineSettings settings;
    private final Logger logger;

    public PackDeliveryListener(PackDelivery delivery, EngineSettings settings, Logger logger) {
        this.delivery = delivery;
        this.settings = settings;
        this.logger = logger;
    }

    /**
     * Before the first build has finished there is nothing to send yet; the rebuild sends the pack
     * to everyone online once it goes live, so an early joiner is not missed.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (settings.delivery().sendOnJoin()) {
            delivery.send(event.getPlayer());
        }
    }

    /**
     * The only feedback there is when the pack does not reach a player. A failed download almost
     * always means the URL cannot be reached from outside, which the log should say plainly.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onStatus(PlayerResourcePackStatusEvent event) {
        if (!PackDelivery.PACK_ID.equals(event.getID())) {
            return;
        }
        String player = event.getPlayer().getName();
        switch (event.getStatus()) {
            case FAILED_DOWNLOAD, INVALID_URL -> logger.warning(player + " could not download the resource pack ("
                    + event.getStatus() + "). Check that " + delivery.currentUrl()
                    + " opens from outside the server's network; http.public-address and the port"
                    + " forwarding are the usual suspects.");
            case FAILED_RELOAD -> logger.warning(player + " downloaded the resource pack but could not"
                    + " apply it. The client log names the file it rejected.");
            case DECLINED -> logger.info(player + " declined the resource pack.");
            default -> {
            }
        }
    }
}
