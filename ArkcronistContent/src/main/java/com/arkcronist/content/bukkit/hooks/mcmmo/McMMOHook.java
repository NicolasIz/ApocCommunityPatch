package com.arkcronist.content.bukkit.hooks.mcmmo;

import com.arkcronist.content.bukkit.hooks.SkillXpHook;
import com.gmail.nossr50.api.ExperienceAPI;
import org.bukkit.entity.Player;

/**
 * mcMMO experience for custom blocks and crops: Mining for breaking a custom block, Herbalism for
 * harvesting a ripe custom crop, as their {@code skill-xp} says.
 *
 * <p>The amount is raw experience - mcMMO's own skill and global multipliers are not applied on
 * top - and it is given for an unknown reason, the one mcMMO uses for experience other plugins
 * hand out, so it counts like any other gain: levels, level-up rewards and notifications.</p>
 */
public final class McMMOHook implements SkillXpHook {

    @Override
    public void reward(Player player, Source source, String contentId, double xp) {
        ExperienceAPI.addRawXP(player, source == Source.CROP ? "HERBALISM" : "MINING", (float) xp, "UNKNOWN");
    }
}
