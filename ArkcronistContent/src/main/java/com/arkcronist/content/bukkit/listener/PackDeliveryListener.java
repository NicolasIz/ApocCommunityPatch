package com.arkcronist.content.bukkit.listener;

import com.arkcronist.content.bukkit.EngineSettings;
import com.arkcronist.content.bukkit.pack.PackDelivery;
import com.arkcronist.content.core.pack.ShaderCheck;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.util.List;
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
        // A player a login plugin holds gets the pack when they log in.
        if (settings.delivery().sendOnJoin() && !delivery.held(event.getPlayer())) {
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
                    + " opens from outside the server's network" + switch (settings.hosting()) {
                        case BUILTIN -> "; http.public-address and the port forwarding are the usual suspects.";
                        case EXTERNAL -> " and serves the zip from the last rebuild, byte for byte.";
                        case UPLOAD -> "; the storage service may have expired or blocked the file. With"
                                + " upload.verify on, /arkcontent reload checks the link and uploads again if it is gone.";
                    });
            case FAILED_RELOAD -> logger.warning(player + " downloaded the resource pack but could not"
                    + " apply it. " + reloadCause(delivery.suspects()));
            case DECLINED -> logger.info(player + " declined the resource pack.");
            default -> {
            }
        }
    }

    /**
     * 3.0.8: what to look at when a client fails to apply the pack. One shader its compiler rejects
     * is enough - and the client then drops every pack the server sent, not only the broken one.
     */
    static String reloadCause(List<String> brokenShaders) {
        String log = "The client's own log (logs/latest.log, after 'Reloading ResourceManager') names the file it"
                + " rejected.";
        if (brokenShaders.isEmpty()) {
            return log;
        }
        return "This build keeps " + brokenShaders.size() + " shader(s) a " + ShaderCheck.clientVersion()
                + " client cannot use - the likely cause: " + String.join(", ", brokenShaders.subList(0,
                Math.min(4, brokenShaders.size()))) + (brokenShaders.size() > 4 ? "..." : "")
                + ". Fix them where they come from, or set pack.shaders: drop-broken to leave them out. " + log;
    }
}
