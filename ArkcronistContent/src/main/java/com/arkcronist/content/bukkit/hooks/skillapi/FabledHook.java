package com.arkcronist.content.bukkit.hooks.skillapi;

import com.arkcronist.content.bukkit.hooks.SkillXpHook;
import org.bukkit.entity.Player;
import studio.magemonkey.fabled.Fabled;
import studio.magemonkey.fabled.api.enums.ExpSource;
import studio.magemonkey.fabled.api.player.PlayerData;

/**
 * Fabled - SkillAPI's successor, renamed - class experience for custom blocks and crops, given as
 * block-break experience like {@link SkillAPIHook}.
 */
public final class FabledHook implements SkillXpHook {

    @Override
    public void reward(Player player, Source source, String contentId, double xp) {
        PlayerData data = Fabled.getData(player);
        if (data != null) {
            data.giveExp(xp, ExpSource.BLOCK_BREAK);
        }
    }
}
