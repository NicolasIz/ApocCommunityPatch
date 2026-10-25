package com.arkcronist.content.bukkit.hooks;

import org.bukkit.entity.Player;

import java.util.OptionalInt;

/**
 * A plugin that knows a player's level in something - a skill, a job - for the quest boards'
 * requirements: {@code skills: {mining: 10}}, {@code jobs: {miner: 5}}.
 *
 * <p>Empty means this plugin has no such skill or job; a player without the job is level 0.
 * Main thread.</p>
 */
public interface LevelSource {

    /** The player's level in a skill: AuraSkills' {@code mining}, {@code fighting}... */
    default OptionalInt skillLevel(Player player, String skill) {
        return OptionalInt.empty();
    }

    /** The player's level in a job, by its id. */
    default OptionalInt jobLevel(Player player, String job) {
        return OptionalInt.empty();
    }
}
