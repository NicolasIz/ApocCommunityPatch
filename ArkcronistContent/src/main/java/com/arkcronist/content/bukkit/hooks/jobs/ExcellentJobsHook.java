package com.arkcronist.content.bukkit.hooks.jobs;

import com.arkcronist.content.bukkit.hooks.JobsHook;
import com.arkcronist.content.core.definition.JobReward;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import su.nightexpress.excellentjobs.JobsAPIProvider;
import su.nightexpress.excellentjobs.job.JobManager;
import su.nightexpress.excellentjobs.job.model.Job;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.logging.Logger;

/**
 * ExcellentJobs 2: the API it registers with Bukkit's services. Experience goes through its
 * leveling manager's {@code addXP}, which levels the player up as its own objectives would. It has
 * no public call for paying money outside an objective, so the money goes to the player through
 * Vault - the economy ExcellentJobs pays with too.
 */
public final class ExcellentJobsHook implements JobsHook {

    private final Server server;
    /** Vault: deposits, and says whether it could. */
    private final BiPredicate<Player, Double> deposit;
    private final Logger logger;
    private final Set<String> warned = new HashSet<>();

    public ExcellentJobsHook(Server server, BiPredicate<Player, Double> deposit, Logger logger) {
        this.server = server;
        this.deposit = deposit;
        this.logger = logger;
    }

    @Override
    public void reward(Player player, Block block, String contentId, Map<String, JobReward> rewards) {
        // Looked up each time: ExcellentJobs enables after this plugin, and a reload replaces it.
        JobsAPIProvider api = server.getServicesManager().load(JobsAPIProvider.class);
        if (api == null) {
            return;
        }
        JobManager jobs = api.getJobManager();
        for (Map.Entry<String, JobReward> entry : rewards.entrySet()) {
            Job job = jobs.getJobById(entry.getKey());
            if (job == null) {
                job = jobs.getJobById(entry.getKey().toLowerCase(Locale.ROOT));
            }
            if (job == null) {
                if (warned.add("job " + entry.getKey())) {
                    logger.warning(contentId + " pays the job '" + entry.getKey() + "', which ExcellentJobs does not"
                            + " have; its ids are the file names in plugins/ExcellentJobs/jobs/.");
                }
                continue;
            }
            if (!jobs.isEmployed(player, job)) {
                continue;
            }
            JobReward reward = entry.getValue();
            if (reward.xp() > 0) {
                api.getLevelingManager().addXP(player, job, reward.xp());
            }
            if (reward.money() > 0 && !deposit.test(player, reward.money()) && warned.add("money")) {
                logger.warning("ExcellentJobs rewards with money need Vault and an economy plugin; " + contentId
                        + "'s money was not paid.");
            }
        }
    }
}
