package com.arkcronist.content.bukkit.hud;

import com.arkcronist.content.core.hud.DefaultFontWidths;
import com.arkcronist.content.core.hud.Spaces;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * The one action bar a player has, shared: the HUD bars that live there all the time, and a short
 * message on top - a gun's ammunition - for a few seconds. Whoever sends sends both, so neither
 * wipes the other out.
 *
 * <p>The bars are drawn width-neutral (see {@link com.arkcronist.content.core.hud.HudRenderer}), and
 * the message is stepped back by half its width on both sides, so neither moves the other. Main
 * thread.</p>
 */
public final class ActionBars {

    private record Flash(Component message, long until) {
    }

    private final Map<UUID, Flash> flashes = new ConcurrentHashMap<>();
    /** 3.0: a frame drawn over everything else - a minigame's bar - for as long as it is set. */
    private final Map<UUID, Component> overlays = new ConcurrentHashMap<>();
    private final LongSupplier tick;
    /** The bars a player has on the action bar now, or null for none. */
    private volatile Function<Player, Component> bars = player -> null;
    /** 3.0: players whose client draws no custom font - Bedrock players - and see no bars. */
    private volatile java.util.function.Predicate<Player> plain = player -> false;

    /** @param tick the server's current tick */
    public ActionBars(LongSupplier tick) {
        this.tick = tick;
    }

    public void bars(Function<Player, Component> bars) {
        this.bars = bars;
    }

    /**
     * Players who are sent no bars: their client has no negative spaces to place them with. A
     * message or a minigame's frame still reaches them, as the plain text it is for them.
     */
    public void plainFor(java.util.function.Predicate<Player> plain) {
        this.plain = plain;
    }

    /** Shows {@code message} for {@code ticks}, over the bars. */
    public void flash(Player player, Component message, int ticks) {
        flashes.put(player.getUniqueId(), new Flash(message, tick.getAsLong() + ticks));
        send(player);
    }

    /**
     * Sets the frame drawn over the bars and any message, or clears it with null, and sends the
     * line. The frame has to be width-neutral, as the bars are, so neither moves the other.
     */
    public void overlay(Player player, @org.jetbrains.annotations.Nullable Component overlay) {
        if (overlay == null) {
            if (overlays.remove(player.getUniqueId()) == null) {
                return;
            }
        } else {
            overlays.put(player.getUniqueId(), overlay);
        }
        send(player);
    }

    /** Sends what the player's action bar holds now; nothing when it holds nothing. */
    public void send(Player player) {
        Component overlay = overlays.get(player.getUniqueId());
        boolean plainText = plain.test(player);
        Component bar = plainText ? null : bars.apply(player);
        if (overlay != null) {
            bar = bar == null ? overlay : overlay.append(bar);
        }
        Flash flash = flashes.get(player.getUniqueId());
        if (flash != null && flash.until() < tick.getAsLong()) {
            flashes.remove(player.getUniqueId(), flash);
            flash = null;
        }
        if (bar == null && flash == null) {
            return;
        }
        if (plainText && flash != null) {
            // A frame and a message, side by side: there are no spaces to lay one over the other.
            player.sendActionBar(bar == null ? flash.message() : bar.append(Component.text("  ")).append(flash.message()));
            return;
        }
        player.sendActionBar(bar == null ? flash.message() : flash == null ? bar : centred(flash.message(), bar));
    }

    /**
     * A message with bars on the same line. The client centres the line by its whole width, so
     * the message is stepped back by half its width on either side: the line's width comes to
     * nothing, the bars stay where they belong, and the message sits in the middle.
     */
    private static Component centred(Component message, Component bars) {
        int half = width(message, false) / 2;
        return Component.text(Spaces.of(-half)).append(message).append(Component.text(Spaces.of(-half)))
                .append(bars);
    }

    /** The message's width in the default font, bold where it is drawn bold. */
    static int width(Component component, boolean bold) {
        TextDecoration.State state = component.decoration(TextDecoration.BOLD);
        boolean isBold = state == TextDecoration.State.NOT_SET ? bold : state == TextDecoration.State.TRUE;
        int width = component instanceof TextComponent text ? DefaultFontWidths.width(text.content(), isBold) : 0;
        for (Component child : component.children()) {
            width += width(child, isBold);
        }
        return width;
    }

    /** Whether a message is showing - the bars then need not be resent on their own. */
    public boolean flashing(Player player) {
        Flash flash = flashes.get(player.getUniqueId());
        return flash != null && flash.until() >= tick.getAsLong();
    }

    public void forget(UUID player) {
        flashes.remove(player);
        overlays.remove(player);
    }
}
