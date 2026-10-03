package com.arkcronist.content.bukkit.hooks;

import org.bukkit.entity.Player;

/**
 * A skills plugin that rewards working with this plugin's content: breaking a custom block, or
 * harvesting a ripe custom crop, with as much experience as its {@code skill-xp} says.
 *
 * <pre>
 * ruby_ore:
 *   type: custom_block
 *   block:
 *     skill-xp: 12        # Mining in AuraSkills and mcMMO; class experience in SkillAPI
 * ruby_plant:
 *   type: custom_crop
 *   crop:
 *     skill-xp: 6         # Farming in AuraSkills, Herbalism in mcMMO
 * </pre>
 *
 * <p>These blocks are note blocks and light blocks to the server, which no skills plugin pays for
 * on its own; this is how they are paid for. Only players outside creative earn anything.</p>
 */
public interface SkillXpHook {

    /** What earned the experience. */
    enum Source {
        /** A custom block, broken: a mining skill. */
        BLOCK,
        /** A ripe custom crop, harvested: a farming skill. */
        CROP
    }

    /** Main thread. */
    void reward(Player player, Source source, String contentId, double xp);
}
