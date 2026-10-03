package com.arkcronist.content.bukkit.hooks.skillapi;

import com.arkcronist.content.bukkit.hooks.SkillXpHook;
import com.sucy.skill.SkillAPI;
import com.sucy.skill.api.enums.ExpSource;
import com.sucy.skill.api.player.PlayerData;
import org.bukkit.entity.Player;

/**
 * SkillAPI and ProSkillAPI - the same {@code com.sucy.skill} API - class experience for custom
 * blocks and crops, as their {@code skill-xp} says. It is given as block-break experience, so a
 * class earns it if its {@code exp-sources} include breaking blocks, as for any mined block.
 */
public final class SkillAPIHook implements SkillXpHook {

    @Override
    public void reward(Player player, Source source, String contentId, double xp) {
        if (!SkillAPI.isLoaded()) {
            return;
        }
        PlayerData data = SkillAPI.getPlayerData(player);
        if (data != null) {
            data.giveExp(xp, ExpSource.BLOCK_BREAK);
        }
    }
}
