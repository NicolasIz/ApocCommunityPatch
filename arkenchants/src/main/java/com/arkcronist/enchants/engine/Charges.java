package com.arkcronist.enchants.engine;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * Holding right click charges the weapon. The client repeats the click about every 4 ticks while the button is
 * down, so clicks closer than {@link #GAP_MS} count as one hold. Once full, the charge waits a few seconds for
 * the next hit, which fires CHARGED_ATTACK.
 */
public final class Charges {

    static final long GAP_MS = 400;

    /** {holdStart, lastClick, readyUntil} per player. */
    private final Map<UUID, long[]> state = new HashMap<>();
    private long chargeMs = 1200;
    private long readyMs = 4000;

    public void configure(double chargeSeconds, double readySeconds) {
        chargeMs = Math.max(200, (long) (chargeSeconds * 1000));
        readyMs = Math.max(500, (long) (readySeconds * 1000));
    }

    /** Registers a right click; returns the charge in percent, or -1 the moment it becomes full. */
    public int click(Player p, long now) {
        long[] s = state.computeIfAbsent(p.getUniqueId(), k -> new long[3]);
        if (s[2] >= now) {
            s[1] = now;
            return 100;
        }
        if (s[1] == 0 || now - s[1] > GAP_MS) {
            s[0] = now;
        }
        s[1] = now;
        long held = now - s[0];
        if (held >= chargeMs) {
            s[2] = now + readyMs;
            return -1;
        }
        return (int) (held * 100 / chargeMs);
    }

    public int percent(Player p) {
        long[] s = state.get(p.getUniqueId());
        long now = System.currentTimeMillis();
        if (s == null) {
            return 0;
        }
        if (s[2] >= now) {
            return 100;
        }
        return now - s[1] > GAP_MS ? 0 : (int) Math.min(99, (now - s[0]) * 100 / chargeMs);
    }

    /** Uses up a full charge; true when there was one. */
    public boolean consume(Player p, long now) {
        long[] s = state.get(p.getUniqueId());
        if (s == null || s[2] < now) {
            return false;
        }
        state.remove(p.getUniqueId());
        return true;
    }

    public void forget(Player p) {
        state.remove(p.getUniqueId());
    }
}
