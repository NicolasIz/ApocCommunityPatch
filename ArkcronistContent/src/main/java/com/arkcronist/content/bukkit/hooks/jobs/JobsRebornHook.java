package com.arkcronist.content.bukkit.hooks.jobs;

import com.arkcronist.content.bukkit.hooks.JobsHook;
import com.arkcronist.content.core.definition.JobReward;
import com.gamingmesh.jobs.Jobs;
import com.gamingmesh.jobs.actions.BlockActionInfo;
import com.gamingmesh.jobs.container.ActionType;
import com.gamingmesh.jobs.container.CurrencyType;
import com.gamingmesh.jobs.container.Job;
import com.gamingmesh.jobs.container.JobsPlayer;
import com.gamingmesh.jobs.economy.BufferedPayment;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Jobs Reborn: the payment goes through {@code Jobs.perform}, the same call Jobs makes for an
 * action of its own. So its pre-payment and experience events fire (other plugins and boosts can
 * change the amounts), its daily limits apply, the money goes through its buffered economy, the
 * experience can level the player up - with its messages and level-up commands - and the action is
 * in its logs, as a block broken.
 */
public final class JobsRebornHook implements JobsHook {

    private final Logger logger;
    private final Set<String> unknown = new HashSet<>();

    public JobsRebornHook(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void reward(Player player, Block block, String contentId, Map<String, JobReward> rewards) {
        JobsPlayer jobsPlayer = Jobs.getPlayerManager().getJobsPlayer(player);
        if (jobsPlayer == null) {
            return;
        }
        for (Map.Entry<String, JobReward> entry : rewards.entrySet()) {
            Job job = Jobs.getJob(entry.getKey());
            if (job == null) {
                if (unknown.add(entry.getKey())) {
                    logger.warning(contentId + " pays the job '" + entry.getKey() + "', which Jobs Reborn does not have;"
                            + " its names are those of plugins/Jobs/jobConfig.yml.");
                }
                continue;
            }
            if (!jobsPlayer.isInJob(job)) {
                continue;
            }
            Map<CurrencyType, Double> payment = new EnumMap<>(CurrencyType.class);
            payment.put(CurrencyType.MONEY, entry.getValue().money());
            payment.put(CurrencyType.EXP, entry.getValue().xp());
            Jobs.perform(jobsPlayer, new BlockActionInfo(block, ActionType.BREAK), new BufferedPayment(player, payment),
                    job, block, null, null);
        }
    }
}
