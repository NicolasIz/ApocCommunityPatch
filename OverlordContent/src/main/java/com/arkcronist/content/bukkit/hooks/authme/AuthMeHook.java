package com.arkcronist.content.bukkit.hooks.authme;

import com.arkcronist.content.bukkit.pack.PackDelivery;
import fr.xephi.authme.api.v3.AuthMeApi;
import fr.xephi.authme.events.LoginEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * AuthMe: the pack after the login, not on joining.
 *
 * <p>A player AuthMe holds at its login prompt would otherwise get the download screen on top of
 * it - and a required pack declined there disconnects them before they could log in. So while
 * AuthMe has not authenticated a player the pack waits, and goes out the moment AuthMe says they
 * logged in (by password or by a remembered session). It is what ItemsAdder's page for AuthMe has
 * an admin set up by hand, with a console command in AuthMe's {@code onLogin}.</p>
 */
public final class AuthMeHook implements Listener {

    private final PackDelivery delivery;

    public AuthMeHook(PackDelivery delivery) {
        this.delivery = delivery;
        delivery.holdWhile(player -> {
            try {
                return !AuthMeApi.getInstance().isAuthenticated(player);
            } catch (RuntimeException | LinkageError error) {
                // AuthMe gone or broken: better a pack on the login screen than none at all.
                return false;
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        delivery.send(player);
    }
}
