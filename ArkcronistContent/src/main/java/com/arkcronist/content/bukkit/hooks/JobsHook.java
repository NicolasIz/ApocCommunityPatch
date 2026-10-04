package com.arkcronist.content.bukkit.hooks;

import com.arkcronist.content.core.definition.JobReward;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * A jobs plugin that pays for working with this plugin's content - breaking a custom block or
 * harvesting a ripe custom crop - as much as its {@code jobs:} section says, to a player who has
 * each job:
 *
 * <pre>
 * ruby_block:
 *   type: custom_block
 *   block:
 *     jobs:
 *       Miner: {money: 2.5, xp: 4}
 * ruby_seeds:
 *   type: custom_crop
 *   crop:
 *     jobs:
 *       Farmer: {money: 1.5, xp: 3}
 * </pre>
 *
 * <p>To the jobs plugin these are note blocks and item displays, which its own configuration does
 * not pay for; this is how they are paid. Only players outside creative earn anything.</p>
 */
public interface JobsHook {

    /**
     * Main thread.
     *
     * @param block     where it happened: the custom block, or the crop's block
     * @param contentId the custom block or crop, for messages
     */
    void reward(Player player, Block block, String contentId, Map<String, JobReward> rewards);
}
