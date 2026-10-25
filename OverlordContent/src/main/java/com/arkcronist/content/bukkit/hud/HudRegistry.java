package com.arkcronist.content.bukkit.hud;

import com.arkcronist.content.core.hud.HudLayout;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Every loaded HUD, by id. Swapped whole on a rebuild; read from any thread. */
public final class HudRegistry {

    private volatile Map<String, HudLayout> byId = Map.of();
    /** Bare ids that only one namespace uses: {@code thirst} for {@code demo:thirst}. */
    private volatile Map<String, HudLayout> byBareId = Map.of();

    public void replace(List<HudLayout> huds) {
        Map<String, HudLayout> ids = new HashMap<>();
        Map<String, HudLayout> bare = new HashMap<>();
        Map<String, Integer> bareCount = new HashMap<>();
        for (HudLayout hud : huds) {
            ids.put(hud.definition().fullId(), hud);
            bare.put(hud.definition().id(), hud);
            bareCount.merge(hud.definition().id(), 1, Integer::sum);
        }
        bareCount.forEach((id, count) -> {
            if (count > 1) {
                bare.remove(id);
            }
        });
        this.byId = Map.copyOf(ids);
        this.byBareId = Map.copyOf(bare);
    }

    /** By {@code namespace:id}, or by a bare id that only one namespace uses. */
    public Optional<HudLayout> find(String id) {
        HudLayout hud = byId.get(id);
        return Optional.ofNullable(hud != null ? hud : byBareId.get(id));
    }

    public Collection<HudLayout> all() {
        return byId.values();
    }
}
