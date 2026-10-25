package kr.toxicity.hud.api.bukkit.event;

import kr.toxicity.hud.api.plugin.ReloadState;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Signature of BetterHud 1.14.1's event, as far as ArkcronistContent uses it. Not packaged. */
public class PluginReloadedEvent extends Event {

    public PluginReloadedEvent(ReloadState state) {
        throw new UnsupportedOperationException("compile-time signature only");
    }

    public ReloadState getState() {
        throw new UnsupportedOperationException("compile-time signature only");
    }

    @Override
    public HandlerList getHandlers() {
        throw new UnsupportedOperationException("compile-time signature only");
    }

    public static HandlerList getHandlerList() {
        throw new UnsupportedOperationException("compile-time signature only");
    }
}
