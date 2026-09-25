package com.arkcronist.enchants.listener;

import com.arkcronist.enchants.item.Keys;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

/**
 * DOUBLE_JUMP: the passive task lets the player "fly" while on the ground; pressing jump in the air turns that
 * flight attempt into a leap, and the next landing gives it back.
 */
public final class JumpListener implements Listener, Runnable {

    private final long validMs;

    public JumpListener(int passiveInterval) {
        this.validMs = passiveInterval * 50L * 2 + 500;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onToggle(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();
        if (!e.isFlying() || !active(p) || p.getPersistentDataContainer().has(Keys.GUARD)
                || p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) {
            return;
        }
        e.setCancelled(true);
        p.setFlying(false);
        p.setAllowFlight(false);
        double power = p.getPersistentDataContainer().getOrDefault(Keys.JUMP, PersistentDataType.DOUBLE, 1.0);
        Vector v = p.getLocation().getDirection().setY(0);
        v = v.lengthSquared() < 1e-4 ? new Vector() : v.normalize().multiply(0.45 * power);
        p.setVelocity(v.setY(0.55 + 0.25 * power));
        p.setFallDistance(0);
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 15, 0.3, 0.05, 0.3, 0.02);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_JUMP, 0.8f, 1.3f);
    }

    boolean active(Player p) {
        Long t = p.getPersistentDataContainer().get(Keys.JUMP_TIME, PersistentDataType.LONG);
        return t != null && System.currentTimeMillis() - t <= validMs;
    }

    @Override
    public void run() {
        for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            tick(p);
        }
    }

    /** Gives the jump back on landing and takes flight away once the item is gone. */
    void tick(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        if (!pdc.has(Keys.JUMP_TIME) || pdc.has(Keys.GUARD)
                || p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) {
            return;
        }
        if (!active(p)) {
            pdc.remove(Keys.JUMP_TIME);
            pdc.remove(Keys.JUMP);
            p.setAllowFlight(false);
            p.setFlying(false);
        } else if (((org.bukkit.entity.Entity) p).isOnGround() && !p.getAllowFlight()) {
            p.setAllowFlight(true);
        }
    }
}
