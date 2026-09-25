package com.arkcronist.enchants.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

/** The shadow clones each player has out, and the one enemy they are allowed to chase. */
public final class Clones {

    private final Map<UUID, List<Mob>> byOwner = new HashMap<>();
    private final Map<UUID, UUID> focus = new HashMap<>();

    public void add(Player owner, Mob clone) {
        byOwner.computeIfAbsent(owner.getUniqueId(), k -> new ArrayList<>()).add(clone);
    }

    public List<Mob> of(Player owner) {
        List<Mob> list = byOwner.get(owner.getUniqueId());
        if (list == null) {
            return List.of();
        }
        list.removeIf(m -> !m.isValid());
        return list;
    }

    public int count(Player owner) {
        return of(owner).size();
    }

    /** Points every clone of the owner at this enemy. */
    public void focus(Player owner, LivingEntity enemy) {
        if (enemy == null || !enemy.isValid()) {
            return;
        }
        focus.put(owner.getUniqueId(), enemy.getUniqueId());
        for (Mob m : of(owner)) {
            m.setTarget(enemy);
        }
    }

    public boolean allowed(UUID owner, LivingEntity target) {
        UUID f = focus.get(owner);
        return f != null && target != null && f.equals(target.getUniqueId());
    }

    public void removeAll(Player owner) {
        List<Mob> list = byOwner.remove(owner.getUniqueId());
        if (list != null) {
            list.forEach(org.bukkit.entity.Entity::remove);
        }
        focus.remove(owner.getUniqueId());
    }

    public void removeEverything() {
        byOwner.values().forEach(l -> l.forEach(org.bukkit.entity.Entity::remove));
        byOwner.clear();
        focus.clear();
    }
}
