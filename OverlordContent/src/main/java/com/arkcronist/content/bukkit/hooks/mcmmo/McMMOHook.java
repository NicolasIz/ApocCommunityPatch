package com.arkcronist.content.bukkit.hooks.mcmmo;

import com.arkcronist.content.bukkit.hooks.SkillXpHook;
import com.gmail.nossr50.api.ExperienceAPI;
import org.bukkit.entity.Player;

/**
 * mcMMO experience for custom blocks and crops: Mining for breaking a custom block, Herbalism for
 * harvesting a ripe custom crop, as their {@code skill-xp} says.
 *
 * <p>It counts like any other gain - levels, level-up rewards, notifications - and mcMMO scales it
 * like any other too: its per-skill formula modifier, and the experience perks of players who have
 * them ({@code mcmmo.perks.xp.*}, which operators have). A {@code skill-xp} of 15 earned an operator
 * 66 Mining experience on a default mcMMO 2.3.</p>
 */
public final class McMMOHook implements SkillXpHook {

    @Override
    public void reward(Player player, Source source, String contentId, double xp) {
        ExperienceAPI.addRawXP(player, source == Source.CROP ? "HERBALISM" : "MINING", (float) xp, "UNKNOWN");
    }
}
