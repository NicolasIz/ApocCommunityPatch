package com.arkcronist.content.core.definition;

/**
 * What one job pays for breaking a custom block or harvesting a ripe crop, through whichever jobs
 * plugin is installed - Jobs Reborn or ExcellentJobs:
 *
 * <pre>
 * block:
 *   jobs:
 *     Miner: {money: 2.5, xp: 4}     # the job's id in the jobs plugin
 * </pre>
 *
 * <p>Only a player who has that job is paid; the jobs plugin applies its own boosts, limits and
 * level-ups as if it had paid the action itself.</p>
 *
 * @param money balance, through the jobs plugin's economy (Vault)
 * @param xp    experience in that job
 */
public record JobReward(double money, double xp) {

    public JobReward {
        if (money < 0 || xp < 0 || Double.isNaN(money) || Double.isNaN(xp)) {
            throw new IllegalArgumentException("a job reward is 0 or more");
        }
    }
}
